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

public class GPCommand {
        
        public enum GP_APDU_COMMAND {
                APDU_DISPLAYAPPLETS_ONCARD, APDU_INSTALL, APDU_DELETE_SELECTED_APPLET, APDU_DELETE_SENT_APPLET, APDU_GET_DATA, APDU_CMD_OPEN
        }
        
        private GP_APDU_COMMAND mCmd;
        private Object mCommandParameter;
        private byte[] mInstallParams;
        private byte mPrivileges;
        private int mSeekReader;
        private String mSeekReaderName;
        
        public GPCommand(MainActivity.APDU_COMMAND cmd, int seekReader, byte[] installParams, byte privileges, Object commandParameter) {
                switch(cmd) {
                case APDU_LIST_APPLETS:
                        mCmd = GP_APDU_COMMAND.APDU_DISPLAYAPPLETS_ONCARD;
                        break;
                case APDU_INSTALL_APPLET:
                        mCmd = GP_APDU_COMMAND.APDU_INSTALL;
                        break;
                case APDU_DELETE_SELECTED_APPLET:
                        mCmd = GP_APDU_COMMAND.APDU_DELETE_SELECTED_APPLET;
                        break;
                case APDU_GET_DATA:
                        mCmd = GP_APDU_COMMAND.APDU_GET_DATA;
                        break;
                default:
                        mCmd = GP_APDU_COMMAND.APDU_CMD_OPEN;
                }
                mSeekReader = seekReader;
                mInstallParams = installParams;
                mPrivileges = privileges;
                mCommandParameter = commandParameter;
                mSeekReaderName = "NFC Reader";
        }
        
        public GP_APDU_COMMAND getCmd() {
                return mCmd;
        }
        
        public Object getCommandParameter() {
                return mCommandParameter;
        }
        
        public byte[] getInstallParams() {
                return mInstallParams;
        }
        
        public byte getPrivileges() {
                return mPrivileges;
        }
        
        public int getSeekReader() {
                return mSeekReader;
        }
        
        public String getSeekReaderName() {
                return mSeekReaderName;
        }
}
