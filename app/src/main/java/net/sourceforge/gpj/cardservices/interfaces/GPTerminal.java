/*******************************************************************************
 * Copyright (c) 2014 Michael Hölzl <mihoelzl@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Michael Hölzl <mihoelzl@gmail.com> - initial implementation
 ******************************************************************************/
package net.sourceforge.gpj.cardservices.interfaces;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

/**
 * Interface for GlobalPlatform terminal implementations.
 * Extends CardTerminal with GP-specific functionality.
 */
public interface GPTerminal {

        /**
         * Get the underlying CardTerminal.
         */
        CardTerminal getCardTerminal();

        /**
         * Transmit an APDU command and get the response.
         */
        byte[] transmitApdu(byte[] apdu) throws CardException;

        /**
         * Check if a card is currently connected.
         */
        boolean isConnected();

        /**
         * Disconnect from the current card.
         */
        void disconnect();
}
