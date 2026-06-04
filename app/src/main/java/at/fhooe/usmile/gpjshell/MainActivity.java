/*******************************************************************************
 * Copyright (c) 2014 Michael Hölzl <mihoelzl@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Michael Hölzl <mihoelzl@gmail.com> - initial implementation
 *     Thomas Sigmund - data base, key set, channel set selection and GET DATA integration
 *     2025 Adaptation - NFC Reader Mode (no root required), AndroidX migration
 ******************************************************************************/
package at.fhooe.usmile.gpjshell;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

import net.sourceforge.gpj.cardservices.AID;
import net.sourceforge.gpj.cardservices.AIDRegistryEntry;
import net.sourceforge.gpj.cardservices.GlobalPlatformService;
import net.sourceforge.gpj.cardservices.interfaces.GPTerminal;
import net.sourceforge.gpj.cardservices.interfaces.NfcTerminal;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import at.fhooe.usmile.gpjshell.db.ChannelSetDataSource;
import at.fhooe.usmile.gpjshell.db.KeysetDataSource;
import at.fhooe.usmile.gpjshell.objects.GPChannelSet;
import at.fhooe.usmile.gpjshell.objects.GPConstants;
import at.fhooe.usmile.gpjshell.objects.GPKeyset;

public class MainActivity extends AppCompatActivity implements ReaderCallback {

        private static final String LOG_TAG = "GPDroid";

        private NfcAdapter mNfcAdapter;
        private boolean mReaderModeEnabled = false;

        private Spinner mKeysetSpinner;
        private Spinner mChannelSpinner;
        private Spinner mReaderSpinner;
        private TextView mLog;
        private TextView mSelectedCap;

        private KeysetDataSource mKeysetSource;
        private ChannelSetDataSource mChannelSetSource;
        private Map<String, GPKeyset> mKeysets;
        private Map<String, GPChannelSet> mChannelSets;

        private GPKeyset mSelectedKeyset;
        private GPChannelSet mSelectedChannelSet;

        private AppPreferences mAppPrefs;

        private Queue<Long> mInstallStartTimes = new ConcurrentLinkedQueue<Long>();

        private static LogMe mStaticLog = null;

        public static final int DIALOG_MF_WAIT_FOR_FINISH = 0;
        public static final int DIALOG_WAIT_FOR_TAG = 1;

        public enum APDU_COMMAND {
                APDU_LIST_APPLETS, APDU_INSTALL_APPLET, APDU_DELETE_SELECTED_APPLET, APDU_GET_DATA
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                mAppPrefs = new AppPreferences(this);
                mStaticLog = new LogMe();

                mLog = (TextView) findViewById(R.id.log);
                mSelectedCap = (TextView) findViewById(R.id.text1);

                // Initialize NFC
                mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
                if (mNfcAdapter == null) {
                        Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                }

                // Initialize database sources
                mKeysetSource = new KeysetDataSource(this);
                mChannelSetSource = new ChannelSetDataSource(this);

                // Initialize spinners
                mKeysetSpinner = (Spinner) findViewById(R.id.keyset_spinner);
                mChannelSpinner = (Spinner) findViewById(R.id.channel_spinner);
                mReaderSpinner = (Spinner) findViewById(R.id.reader_spinner);

                setupReaderSpinner();
                setupKeysetSpinner();
                setupChannelSpinner();

                // Setup buttons
                setupButtons();

                // Handle intent if app was started via NFC tag discovery
                handleIntent(getIntent());
        }

        @Override
        protected void onResume() {
                super.onResume();
                enableReaderMode();
        }

        @Override
        protected void onPause() {
                super.onPause();
                disableReaderMode();
        }

        @Override
        protected void onNewIntent(Intent intent) {
                super.onNewIntent(intent);
                handleIntent(intent);
        }

        /**
         * NFC Reader Callback - called when a tag is discovered in reader mode.
         * This replaces the old root-requiring seeksmulator approach.
         */
        @Override
        public void onTagDiscovered(Tag tag) {
                log().d(LOG_TAG, "Tag discovered: " + tag.toString());

                IsoDep isoDep = IsoDep.get(tag);
                if (isoDep != null) {
                        NfcTerminal terminal = NfcTerminal.getInstance(getApplicationContext());
                        if (terminal.passTag(tag)) {
                                log().d(LOG_TAG, "IsoDep tag passed to NfcTerminal");
                        } else {
                                log().e(LOG_TAG, "Failed to pass tag to NfcTerminal");
                        }
                }

                // Also handle MifareClassic tags
                MifareClassic mifare = MifareClassic.get(tag);
                if (mifare != null) {
                        log().d(LOG_TAG, "MifareClassic tag detected");
                }
        }

        /**
         * Enable NFC Reader Mode - no root required, uses standard Android NFC API.
         * This keeps the NFC connection alive while the app is in foreground.
         */
        private void enableReaderMode() {
                if (mNfcAdapter != null) {
                        Bundle options = new Bundle();
                        // 30000ms timeout for presence check
                        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 30000);
                        int flags = NfcAdapter.FLAG_READER_NFC_A
                                        | NfcAdapter.FLAG_READER_NFC_B
                                        | NfcAdapter.FLAG_READER_NFC_F
                                        | NfcAdapter.FLAG_READER_NFC_V
                                        | NfcAdapter.FLAG_READER_NFC_BARCODE
                                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
                        mNfcAdapter.enableReaderMode(this, this, flags, options);
                        mReaderModeEnabled = true;
                        log().d(LOG_TAG, "NFC Reader Mode enabled (no root required)");
                }
        }

        private void disableReaderMode() {
                if (mNfcAdapter != null && mReaderModeEnabled) {
                        mNfcAdapter.disableReaderMode(this);
                        mReaderModeEnabled = false;
                        log().d(LOG_TAG, "NFC Reader Mode disabled");
                }
        }

        private void handleIntent(Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                        if (tag != null) {
                                onTagDiscovered(tag);
                        }
                }
        }

        private void setupReaderSpinner() {
                List<String> readers = new ArrayList<String>();
                readers.add("NFC Reader (IsoDep)");
                ArrayAdapter<String> readerAdapter = new ArrayAdapter<String>(this,
                                android.R.layout.simple_spinner_item, readers);
                readerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mReaderSpinner.setAdapter(readerAdapter);
        }

        private void setupKeysetSpinner() {
                mKeysetSource.open();
                mKeysets = mKeysetSource.getAllKeysets();
                mKeysetSource.close();

                List<String> keysetNames = new ArrayList<String>(mKeysets.keySet());
                ArrayAdapter<String> keysetAdapter = new ArrayAdapter<String>(this,
                                android.R.layout.simple_spinner_item, keysetNames);
                keysetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mKeysetSpinner.setAdapter(keysetAdapter);

                mKeysetSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view,
                                        int position, long id) {
                                String selected = (String) parent.getItemAtPosition(position);
                                mSelectedKeyset = mKeysets.get(selected);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                });

                if (!keysetNames.isEmpty()) {
                        mSelectedKeyset = mKeysets.get(keysetNames.get(0));
                }
        }

        private void setupChannelSpinner() {
                mChannelSetSource.open();
                mChannelSets = mChannelSetSource.getAllChannelSets();
                mChannelSetSource.close();

                List<String> channelNames = new ArrayList<String>(mChannelSets.keySet());
                ArrayAdapter<String> channelAdapter = new ArrayAdapter<String>(this,
                                android.R.layout.simple_spinner_item, channelNames);
                channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mChannelSpinner.setAdapter(channelAdapter);

                mChannelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view,
                                        int position, long id) {
                                String selected = (String) parent.getItemAtPosition(position);
                                mSelectedChannelSet = mChannelSets.get(selected);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                });

                if (!channelNames.isEmpty()) {
                        mSelectedChannelSet = mChannelSets.get(channelNames.get(0));
                }
        }

        private void setupButtons() {
                // Add keyset button
                Button btnAddKeyset = (Button) findViewById(R.id.btn_add_keyset);
                btnAddKeyset.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, AddKeysetActivity.class);
                                startActivityForResult(intent, 0);
                        }
                });

                // Remove keyset button
                Button btnRemoveKeyset = (Button) findViewById(R.id.btn_remove_keyset);
                btnRemoveKeyset.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                if (mSelectedKeyset != null) {
                                        mKeysetSource.open();
                                        mKeysetSource.remove(mSelectedKeyset.getUniqueID());
                                        mKeysetSource.close();
                                        setupKeysetSpinner();
                                }
                        }
                });

                // Add channel set button
                Button btnAddChannel = (Button) findViewById(R.id.btn_add_channelset);
                btnAddChannel.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, AddChannelSetActivity.class);
                                startActivityForResult(intent, 1);
                        }
                });

                // Remove channel set button
                Button btnRemoveChannel = (Button) findViewById(R.id.btn_remove_channelset);
                btnRemoveChannel.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                if (mSelectedChannelSet != null) {
                                        mChannelSetSource.open();
                                        mChannelSetSource.remove(mSelectedChannelSet.getChannelNameString());
                                        mChannelSetSource.close();
                                        setupChannelSpinner();
                                }
                        }
                });

                // Choose applet button
                Button btnChooseApplet = (Button) findViewById(R.id.button3);
                btnChooseApplet.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent();
                                intent.setType("*/*");
                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                startActivityForResult(Intent.createChooser(intent, "Select CAP file"), 2);
                        }
                });

                // List applets button
                Button btnListApplets = (Button) findViewById(R.id.btn_list_applets);
                btnListApplets.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                listApplets();
                        }
                });

                // Install applet button
                Button btnInstallApplet = (Button) findViewById(R.id.btn_install_applet);
                btnInstallApplet.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                installApplet();
                        }
                });

                // Get Data button
                Button btnGetData = (Button) findViewById(R.id.btn_get_data);
                btnGetData.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, GetDataActivity.class);
                                startActivityForResult(intent, 3);
                        }
                });

                // Mifare test button
                Button btnMifareTest = (Button) findViewById(R.id.btn_test_mf);
                btnMifareTest.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                runMifareTest();
                        }
                });

                // Applet install test button
                Button btnAppletTest = (Button) findViewById(R.id.btn_applet_test);
                btnAppletTest.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, AppletInstallTest.class);
                                startActivity(intent);
                        }
                });

                // Echo test button
                Button btnEchoTest = (Button) findViewById(R.id.btn_echo_test);
                btnEchoTest.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                Intent intent = new Intent(MainActivity.this, ApduEchoTest.class);
                                startActivity(intent);
                        }
                });
        }

        private void listApplets() {
                if (mSelectedKeyset == null) {
                        log().e(LOG_TAG, "No keyset selected");
                        return;
                }
                if (mSelectedChannelSet == null) {
                        log().e(LOG_TAG, "No channel set selected");
                        return;
                }

                GPTerminal terminal = NfcTerminal.getInstance(getApplicationContext());
                GPCommand cmd = new GPCommand(APDU_COMMAND.APDU_LIST_APPLETS, 0, null, (byte) 0, null);
                GPConnection.getInstance(getApplicationContext()).performCommand(terminal,
                                mSelectedKeyset, mSelectedChannelSet, cmd);

                Intent intent = new Intent(this, AppletListActivity.class);
                startActivity(intent);
        }

        private void installApplet() {
                if (mSelectedKeyset == null) {
                        log().e(LOG_TAG, "No keyset selected");
                        return;
                }
                if (mSelectedChannelSet == null) {
                        log().e(LOG_TAG, "No channel set selected");
                        return;
                }

                String capUrl = mAppPrefs.getSelectedCap();
                if (capUrl == null || capUrl.isEmpty()) {
                        log().e(LOG_TAG, "No CAP file selected");
                        return;
                }

                GPTerminal terminal = NfcTerminal.getInstance(getApplicationContext());
                GPCommand cmd = new GPCommand(APDU_COMMAND.APDU_INSTALL_APPLET, 0, null, (byte) 0, capUrl);
                GPConnection.getInstance(getApplicationContext()).performCommand(terminal,
                                mSelectedKeyset, mSelectedChannelSet, cmd);
        }

        private void runMifareTest() {
                // Mifare test requires a tag to be present
                NfcTerminal terminal = NfcTerminal.getInstance(getApplicationContext());
                if (!terminal.isConnected()) {
                        log().e(LOG_TAG, "No NFC tag connected. Please tap a Mifare card.");
                        return;
                }
                // Mifare test is started from the tag detection
                log().d(LOG_TAG, "Mifare test: waiting for tag...");
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                super.onActivityResult(requestCode, resultCode, data);

                if (requestCode == 0 && resultCode == RESULT_OK) {
                        // Keyset added
                        setupKeysetSpinner();
                } else if (requestCode == 1 && resultCode == RESULT_OK) {
                        // Channel set added
                        setupChannelSpinner();
                } else if (requestCode == 2 && resultCode == RESULT_OK) {
                        // CAP file selected
                        if (data != null && data.getData() != null) {
                                String capUrl = data.getData().toString();
                                mAppPrefs.saveSelectedCap(capUrl);
                                mSelectedCap.setText(capUrl);
                                log().d(LOG_TAG, "CAP file selected: " + capUrl);
                        }
                } else if (requestCode == 3 && resultCode == RESULT_OK) {
                        // Get Data result
                        if (data != null) {
                                int p1 = data.getIntExtra("p1", 0);
                                int p2 = data.getIntExtra("p2", 0);
                                getData(p1, p2);
                        }
                }
        }

        private void getData(int p1, int p2) {
                if (mSelectedKeyset == null || mSelectedChannelSet == null) {
                        log().e(LOG_TAG, "No keyset or channel set selected");
                        return;
                }

                GPTerminal terminal = NfcTerminal.getInstance(getApplicationContext());
                GPCommand cmd = new GPCommand(APDU_COMMAND.APDU_GET_DATA, 0,
                                new byte[] { (byte) p1, (byte) p2 }, (byte) 0, null);
                GPConnection.getInstance(getApplicationContext()).performCommand(terminal,
                                mSelectedKeyset, mSelectedChannelSet, cmd);
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
                getMenuInflater().inflate(R.menu.main, menu);
                return true;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_settings) {
                        return true;
                }
                return super.onOptionsItemSelected(item);
        }

        public static LogMe log() {
                return mStaticLog;
        }

        public class LogMe {
                private void log(String _tag, String _text) {
                        final String[] lines = _text.split("\n");
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                        for (String line : lines) {
                                                mLog.append(Html.fromHtml("<font color=\"#ff0000\">" + _tag
                                                                + "</font> : " + line + "<br>"));
                                        }
                                }
                        });
                }

                public void e(String _tag, String _text) {
                        log(_tag, _text);
                }

                public void e(String _tag, String _text, Exception _e) {
                        log(_tag, _text + _e.getMessage());
                }

                public void d(String _tag, String _text) {
                        log(_tag, _text);
                }

                public void i(String _tag, String _text) {
                        log(_tag, _text);
                }
        }

        public void mifareTestFinished() {
                // Mifare test finished callback
        }

        public void stopTimer() {
                long start = mInstallStartTimes.remove();
                mStaticLog.d(LOG_TAG, "timer stopped: "
                                + (SystemClock.elapsedRealtime() - start));
        }
}
