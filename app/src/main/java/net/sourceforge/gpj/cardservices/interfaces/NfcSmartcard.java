/*******************************************************************************
 * Copyright (c) 2014 Michael Hölzl <mihoelzl@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Michael Hölzl <mihoelzl@gmail.com> - initial implementation
 *     2025 Adaptation - Rewritten to use Android IsoDep API (no root)
 ******************************************************************************/
package net.sourceforge.gpj.cardservices.interfaces;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;

/**
 * Smartcard implementation using Android's IsoDep API.
 * Wraps an IsoDep connection to provide the javax.smartcardio.Card interface.
 */
public class NfcSmartcard extends Card {

        private static final String LOG_TAG = "NfcSmartcard";

        private final NfcTerminal mTerminal;
        private final NfcSmartcardChannel mChannel;
        private ATR mAtr;

        public NfcSmartcard(NfcTerminal terminal) {
                mTerminal = terminal;
                mChannel = new NfcSmartcardChannel(this, terminal);

                // Build ATR from tag's historical bytes if available
                IsoDep isoDep = terminal.getIsoDep();
                if (isoDep != null) {
                        byte[] historicalBytes = isoDep.getHistoricalBytes();
                        if (historicalBytes != null && historicalBytes.length > 0) {
                                // Build a simple ATR from historical bytes
                                // ATR format: TS T0 TA1 TB1 TC1 TD1 ... historical bytes TCK
                                byte[] atrBytes = new byte[historicalBytes.length + 2];
                                atrBytes[0] = (byte) 0x3B; // TS - direct convention
                                atrBytes[1] = (byte) ((historicalBytes.length & 0x0F) | 0x80); // T0 - historical bytes length + TD1 present
                                System.arraycopy(historicalBytes, 0, atrBytes, 2, historicalBytes.length);
                                mAtr = new ATR(atrBytes);
                        } else {
                                // Default ATR for ISO 14443 cards
                                mAtr = new ATR(new byte[] { 0x3B, 0x00 });
                        }
                } else {
                        mAtr = new ATR(new byte[] { 0x3B, 0x00 });
                }

                Log.d(LOG_TAG, "NfcSmartcard created, ATR: " + NfcTerminal.bytesToHex(mAtr.getBytes()));
        }

        @Override
        public ATR getATR() {
                return mAtr;
        }

        @Override
        public String getProtocol() {
                // IsoDep uses T=CL (contactless) protocol, mapped to T=1 for GP
                return "T=1";
        }

        @Override
        public CardChannel getBasicChannel() {
                return mChannel;
        }

        @Override
        public CardChannel openLogicalChannel() throws CardException {
                // For GlobalPlatform, we use the basic channel for most operations
                // Logical channels can be opened via APDU commands
                throw new CardException("Logical channels must be opened via APDU MANAGE CHANNEL command");
        }

        @Override
        public void beginExclusive() throws CardException {
                // No-op for NFC - only one connection at a time
        }

        @Override
        public void endExclusive() throws CardException {
                // No-op for NFC
        }

        @Override
        public void disconnect(boolean reset) throws CardException {
                if (reset && mTerminal.getIsoDep() != null) {
                        try {
                                mTerminal.getIsoDep().close();
                        } catch (IOException e) {
                                throw new CardException("Error disconnecting: " + e.getMessage(), e);
                        }
                }
                mTerminal.disconnect();
        }

        /**
         * Transmit APDU command through the terminal.
         */
        public byte[] transmitCommand(byte[] apdu) throws CardException {
                return mTerminal.transmitApdu(apdu);
        }

        /**
         * Get the underlying terminal.
         */
        public NfcTerminal getTerminal() {
                return mTerminal;
        }
}
