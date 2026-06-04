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

import java.nio.ByteBuffer;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * Card channel implementation using Android's IsoDep API.
 * Provides the javax.smartcardio.CardChannel interface over NFC.
 */
public class NfcSmartcardChannel extends CardChannel {

        private final NfcSmartcard mCard;
        private final NfcTerminal mTerminal;
        private int mChannelNumber;

        public NfcSmartcardChannel(NfcSmartcard card, NfcTerminal terminal) {
                mCard = card;
                mTerminal = terminal;
                mChannelNumber = 0; // Basic channel
        }

        /**
         * Set the channel number (after MANAGE CHANNEL command).
         */
        public void setChannelNumber(int channelNumber) {
                mChannelNumber = channelNumber;
        }

        @Override
        public Card getCard() {
                return mCard;
        }

        @Override
        public int getChannelNumber() {
                return mChannelNumber;
        }

        @Override
        public ResponseAPDU transmit(CommandAPDU command) throws CardException {
                byte[] commandBytes = command.getBytes();

                // If using a logical channel (channel number > 0),
                // encode the channel number into the CLA byte
                if (mChannelNumber > 0) {
                        commandBytes[0] = (byte) ((commandBytes[0] & 0xFC) | (mChannelNumber & 0x03));
                }

                byte[] responseBytes = mCard.transmitCommand(commandBytes);

                if (responseBytes == null || responseBytes.length < 2) {
                        throw new CardException("Invalid response from card: empty or too short");
                }

                return new ResponseAPDU(responseBytes);
        }

        @Override
        public int transmit(ByteBuffer command, ByteBuffer response) throws CardException {
                byte[] commandBytes = new byte[command.remaining()];
                command.get(commandBytes);

                byte[] responseBytes = mCard.transmitCommand(commandBytes);

                if (responseBytes == null || responseBytes.length < 2) {
                        throw new CardException("Invalid response from card");
                }

                response.put(responseBytes);
                return responseBytes.length;
        }

        @Override
        public void close() throws CardException {
                // For the basic channel, we don't actually close it
                // Logical channels should be closed via MANAGE CHANNEL command
                if (mChannelNumber > 0) {
                        // Send MANAGE CHANNEL close command
                        byte[] closeCmd = new byte[] {
                                (byte) 0x00, (byte) 0x70, (byte) 0x80,
                                (byte) (mChannelNumber & 0xFF)
                        };
                        mCard.transmitCommand(closeCmd);
                        mChannelNumber = 0;
                }
        }
}
