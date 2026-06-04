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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.os.Environment;

public class TCPFileResultListener {
        
        private static final String LOG_TAG = "TCPFileResultListener";
        private Context mContext;
        
        public TCPFileResultListener(Context context) {
                mContext = context;
        }
        
        public void saveFile(String url, String fileName) throws IOException {
                InputStream input = null;
                OutputStream output = null;
                
                try {
                        if (url.startsWith("content://")) {
                                input = mContext.getContentResolver().openInputStream(
                                                android.net.Uri.parse(url));
                        } else {
                                URLConnection connection = new URL(url).openConnection();
                                input = connection.getInputStream();
                        }
                        
                        File dir = new File(Environment.getExternalStorageDirectory(), "GPDroid");
                        if (!dir.exists()) {
                                dir.mkdirs();
                        }
                        File file = new File(dir, fileName);
                        output = new FileOutputStream(file);
                        
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                        }
                } finally {
                        if (input != null) input.close();
                        if (output != null) output.close();
                }
        }
}
