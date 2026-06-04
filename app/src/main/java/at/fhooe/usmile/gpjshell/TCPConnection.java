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
package at.fhooe.usmile.gpjshell;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.smartcardio.CardException;

import android.util.Log;

public class TCPConnection {
        
        private static final String LOG_TAG = "TCPConnection";
        private Socket mSocket;
        private String mHost;
        private int mPort;
        
        public TCPConnection(String host, int port) {
                mHost = host;
                mPort = port;
        }
        
        public void connect() throws UnknownHostException, IOException {
                mSocket = new Socket(mHost, mPort);
                Log.d(LOG_TAG, "Connected to " + mHost + ":" + mPort);
        }
        
        public void disconnect() {
                if (mSocket != null && !mSocket.isClosed()) {
                        try {
                                mSocket.close();
                        } catch (IOException e) {
                                Log.e(LOG_TAG, "Error closing socket", e);
                        }
                }
        }
        
        public boolean isConnected() {
                return mSocket != null && mSocket.isConnected() && !mSocket.isClosed();
        }
        
        public Socket getSocket() {
                return mSocket;
        }
}
