/*******************************************************************************
 * Copyright (c) 2014 Michael Hölzl <mihoelzl@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Michael Hölzl <mihoelzl@gmail.com> - initial implementation
 *     2025 Adaptation - Rewritten to use Android NFC Reader Mode API (no root)
 *
 * This class replaces the original root-requiring seeksmulator-based NFC
 * terminal with a standard Android NFC API implementation using IsoDep.
 * It works on all Android devices with NFC support, no root required.
 ******************************************************************************/
package net.sourceforge.gpj.cardservices.interfaces;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

/**
 * NFC Terminal implementation using Android's standard IsoDep API.
 * No root access required - works with NfcAdapter.Reader Mode.
 *
 * Usage: The Activity enables NFC Reader Mode, and when a tag is discovered,
 * it calls passTag() to provide the tag to this terminal.
 */
public class NfcTerminal extends CardTerminal implements GPTerminal {

        private static final String LOG_TAG = "NfcTerminal";

        private static NfcTerminal sInstance = null;
        private Context mContext;

        private IsoDep mIsoDep;
        private Tag mCurrentTag;
        private volatile boolean mConnected = false;
        private volatile boolean mCardPresent = false;

        // Blocking queue for tag arrival notification
        private final BlockingQueue<Tag> mTagQueue = new ArrayBlockingQueue<>(1);

        // Timeout for waiting for a tag (milliseconds)
        private static final long TAG_WAIT_TIMEOUT = 60000;

        /**
         * Private constructor - use getInstance() instead.
         */
        private NfcTerminal(Context context) {
                mContext = context.getApplicationContext();
        }

        /**
         * Get singleton instance of NfcTerminal.
         */
        public static synchronized NfcTerminal getInstance(Context context) {
                if (sInstance == null) {
                        sInstance = new NfcTerminal(context);
                }
                return sInstance;
        }

        /**
         * Pass a discovered NFC tag to this terminal.
         * Called from the Activity's onTagDiscovered() callback.
         *
         * @param tag The discovered NFC tag
         * @return true if the tag was successfully passed to the terminal
         */
        public boolean passTag(Tag tag) {
                if (tag == null) {
                        return false;
                }

                Log.d(LOG_TAG, "Tag passed to NfcTerminal: " + tag.toString());

                // Close any existing connection
                if (mIsoDep != null && mConnected) {
                        try {
                                mIsoDep.close();
                        } catch (IOException e) {
                                Log.w(LOG_TAG, "Error closing previous IsoDep connection", e);
                        }
                        mConnected = false;
                }

                mCurrentTag = tag;
                mIsoDep = IsoDep.get(tag);

                if (mIsoDep != null) {
                        try {
                                mIsoDep.connect();
                                mConnected = true;
                                mCardPresent = true;

                                // Set timeout for transceive operations (30 seconds)
                                mIsoDep.setTimeout(30000);

                                Log.d(LOG_TAG, "IsoDep connected successfully. Tag ID: "
                                                + bytesToHex(tag.getId()));

                                // Notify any waiting threads
                                mTagQueue.offer(tag);

                                return true;
                        } catch (IOException e) {
                                Log.e(LOG_TAG, "Failed to connect IsoDep", e);
                                mConnected = false;
                                mCardPresent = false;
                                return false;
                        }
                } else {
                        Log.w(LOG_TAG, "Tag does not support IsoDep");
                        return false;
                }
        }

        /**
         * Send an APDU command and receive the response.
         * This is the core method that replaces the root-requiring seeksmulator.
         *
         * @param commandAPDU The APDU command bytes to send
         * @return The response bytes from the card
         * @throws CardException If communication fails
         */
        public byte[] transmitApdu(byte[] commandAPDU) throws CardException {
                if (!mConnected || mIsoDep == null) {
                        throw new CardException("No NFC tag connected. Please tap a card.");
                }

                try {
                        Log.d(LOG_TAG, "TX: " + bytesToHex(commandAPDU));
                        byte[] response = mIsoDep.transceive(commandAPDU);
                        Log.d(LOG_TAG, "RX: " + bytesToHex(response));
                        return response;
                } catch (IOException e) {
                        mConnected = false;
                        mCardPresent = false;
                        throw new CardException("APDU transmission failed: " + e.getMessage(), e);
                }
        }

        /**
         * Check if a card/tag is currently connected.
         */
        public boolean isConnected() {
                return mConnected && mIsoDep != null && mIsoDep.isConnected();
        }

        /**
         * Disconnect from the current tag.
         */
        public void disconnect() {
                if (mIsoDep != null && mConnected) {
                        try {
                                mIsoDep.close();
                        } catch (IOException e) {
                                Log.w(LOG_TAG, "Error closing IsoDep", e);
                        }
                }
                mConnected = false;
                mCardPresent = false;
                mIsoDep = null;
                mCurrentTag = null;
        }

        /**
         * Get the IsoDep connection for direct access.
         */
        public IsoDep getIsoDep() {
                return mIsoDep;
        }

        /**
         * Get the current tag.
         */
        public Tag getCurrentTag() {
                return mCurrentTag;
        }

        // ==================== GPTerminal interface ====================

        @Override
        public CardTerminal getCardTerminal() {
                return this;
        }

        // ==================== CardTerminal interface ====================

        @Override
        public Card connect(String protocol) throws CardException {
                if (!mConnected || mIsoDep == null) {
                        throw new CardException("No NFC tag connected. Please tap a card.");
                }
                // Return a NfcSmartcard wrapping the IsoDep connection
                return new NfcSmartcard(this);
        }

        @Override
        public String getName() {
                return "NFC IsoDep Terminal";
        }

        @Override
        public boolean isCardPresent() throws CardException {
                return mCardPresent && isConnected();
        }

        @Override
        public boolean waitForCardPresent(long timeout) throws CardException {
                if (mCardPresent && isConnected()) {
                        return true;
                }

                try {
                        Tag tag = mTagQueue.poll(timeout, TimeUnit.MILLISECONDS);
                        if (tag != null) {
                                return true;
                        }
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                }
                return false;
        }

        @Override
        public boolean waitForCardAbsent(long timeout) throws CardException {
                long deadline = System.currentTimeMillis() + timeout;
                while (System.currentTimeMillis() < deadline) {
                        if (!mCardPresent || !isConnected()) {
                                return true;
                        }
                        try {
                                Thread.sleep(200);
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return false;
                        }
                }
                return false;
        }

        // ==================== Utility methods ====================

        /**
         * Convert byte array to hex string for logging.
         */
        public static String bytesToHex(byte[] bytes) {
                if (bytes == null) return "null";
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                        sb.append(String.format("%02X ", b));
                }
                return sb.toString().trim();
        }

        /**
         * Convert hex string to byte array.
         */
        public static byte[] hexToBytes(String hex) {
                if (hex == null) return null;
                hex = hex.replaceAll("\\s+", "");
                int len = hex.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                        data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                        + Character.digit(hex.charAt(i + 1), 16));
                }
                return data;
        }
}
