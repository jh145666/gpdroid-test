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

public class TimerLog {
        
        private long mStartTime;
        private String mName;
        
        public TimerLog(String name) {
                mName = name;
        }
        
        public void start() {
                mStartTime = System.currentTimeMillis();
        }
        
        public long stop() {
                return System.currentTimeMillis() - mStartTime;
        }
        
        public void stopAndLog() {
                long elapsed = stop();
                System.out.println(mName + ": " + elapsed + "ms");
        }
}
