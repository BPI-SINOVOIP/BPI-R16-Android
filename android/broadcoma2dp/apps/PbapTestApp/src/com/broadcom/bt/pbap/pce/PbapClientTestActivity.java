/*******************************************************************************
 *
 *  Copyright (C) 2012 Broadcom Corporation
 *
 *  This program is the proprietary software of Broadcom Corporation and/or its
 *  licensors, and may only be used, duplicated, modified or distributed
 *  pursuant to the terms and conditions of a separate, written license
 *  agreement executed between you and Broadcom (an "Authorized License").
 *  Except as set forth in an Authorized License, Broadcom grants no license
 *  (express or implied), right to use, or waiver of any kind with respect to
 *  the Software, and Broadcom expressly reserves all rights in and to the
 *  Software and all intellectual property rights therein.
 *  IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 *  SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 *  ALL USE OF THE SOFTWARE.
 *
 *  Except as expressly set forth in the Authorized License,
 *
 *  1.     This program, including its structure, sequence and organization,
 *         constitutes the valuable trade secrets of Broadcom, and you shall
 *         use all reasonable efforts to protect the confidentiality thereof,
 *         and to use this information only in connection with your use of
 *         Broadcom integrated circuit products.
 *
 *  2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 *         "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 *         REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 *         OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 *         DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 *         NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 *         ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 *         OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 *         ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *******************************************************************************/

package com.broadcom.bt.pbap.pce;

import android.R.string;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.vcard.exception.VCardException;

import com.broadcom.bt.pbap.BluetoothPbapClient;
import com.broadcom.bt.pbap.BluetoothAttributeMask;
import com.broadcom.bt.pbap.AppParamValue;
import com.broadcom.bt.pbap.IBluetoothPbapClientAuthenticator;
import com.broadcom.bt.pbap.IBluetoothPbapClientEventHandler;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PbapClientTestActivity extends Activity implements IBluetoothPbapClientEventHandler,
IBluetoothPbapClientAuthenticator, OnClickListener {
    private static final String TAG = "PbapclientActivity";

    private static final String EXTRA_SERVER_BDADDR = "bdaddr";

    // UI event states
    private static final int UI_INIT_COMPLETE = 0;

    private static final int UI_SELECT_SERVER = 1;

    private static final int UI_SELECTED_SERVER = 2;

    private static final int UI_CONNECTED_TO_SERVER = 3;

    private static final int UI_UNABLE_TO_CONNECT_TO_SERVER = 4;

    private static final int UI_DISCONNECTED_FROM_SERVER = 5;

    private static final int UI_FOLDER_PATH_SET = 10;

    private static final int UI_PULL_PHONEBOOK = 11;

    private static final int UI_PULL_VCARD_ENTRY = 12;

    private static final int UI_PULL_VCARD_LISTING = 13;

    private static final int UI_PULL_ABORT = 14;

    private static final String PATH_SDCARD = Environment.getExternalStorageDirectory()
            .getAbsolutePath();

    private static final String OUT_FILE_PULL_PB = PATH_SDCARD + "/pb.vcf";

    private static final String OUT_FILE_PULL_VC_ENTRY = PATH_SDCARD + "/entry.vcf";

    private static final String OUT_FILE_PULL_VC_LISTING = PATH_SDCARD + "/listing.xml";

    private static final int NOTIFICATION_ID_AUTH = -8000002;

    private static final int REQCODE_BLUETOOTH_RESULT = 0;

    private static final String ATTR_MASK_NO_PHOTO = "4294967287"; //0xFFFFFFF7

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothPbapClient mPbapClient;

    private Set<BluetoothDevice> pairedDevices;

    private String serverAddress;

    private String serverFriendlyName;

    private int state;

    private String mServerPath;

    private String mPullPbPath;

    private String mSelectedNameForPullVcard;

    private TextView tvServerName;

    private Button btnSelectBTServer;

    private Button btnConnectToServer;

    private Spinner spinnerSetPath;

    // Pull Phonebook UI
    private Spinner spinnerPullPbAbsPath;

    private TextView tvMaxListCountPb;

    private SeekBar seekBarMaxListCountPb;

    private Button btnPullPhonebook;

    private int maxListCountValuePb = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;

    // Browse PB UI - for pullVcardEntry & pullVcardListing
    private Spinner spinnerBrowseAbsPath;

    private Button btnImportToPb;

    private Button btnImportToCallLog;

    private Spinner spinnerSearchOrder;

    private Spinner spinnerSearchAttribute;

    private TextView tvMaxListCountPbListing;

    private SeekBar seekBarMaxListCountPbListing;

    private int maxListCountValuePbListing;

    private AutoCompleteTextView autotvListing;

    ArrayAdapter<String> vcardListingAdapter;

    final Map<String, String> vcardNameAndHandle = new HashMap<String, String>();

    private String mBrowsePath;

    //Preference and settings UI values
    private boolean prefAddSdp;

    private boolean prefPhotoDnld;

    private String prefVCardVerStr;

    // Abort UI
    private Button btnAbort;

    // SetPathUI
    private Button btnSetPath;

    private CallLogUtils mCallLogUtils = new CallLogUtils();

    private class PbapClientBroadcastReceiver extends BroadcastReceiver {
        public IntentFilter createFilter() {
            IntentFilter f = new IntentFilter(PbapAuthDialog.ACTION_AUTH_RESPONSE);
            f.addAction(PbapAuthDialog.ACTION_CANCEL_AUTH);
            return f;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int authType = intent.getIntExtra(PbapAuthDialog.EXTRA_AUTH_TYPE,
                    PbapAuthDialog.TYPE_AUTH_CHAL);
            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d(TAG, "onReceive: action=" + action);
            if (PbapAuthDialog.ACTION_CANCEL_AUTH.equals(action)) {
                onCancelAuth(authType, remoteDevice);
            } else if (PbapAuthDialog.ACTION_AUTH_RESPONSE.equals(action)) {
                onAuthResponse(authType, remoteDevice,
                        intent.getBooleanExtra(PbapAuthDialog.EXTRA_USERID_REQUIRED, false),
                        intent.getStringExtra(PbapAuthDialog.EXTRA_USERNAME),
                        intent.getStringExtra(PbapAuthDialog.EXTRA_SESSION_KEY));
            }
        }
    }

    // HashMap<String, BluetoothPbapClient> mClientMap = new HashMap<String,
    // BluetoothPbapClient>();
    private PbapClientBroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pbapclient);

        tvServerName = (TextView)findViewById(R.id.deviceName);

        btnSelectBTServer = (Button)findViewById(R.id.btSelectedDevice);
        btnSelectBTServer.setOnClickListener(this);

        btnConnectToServer = (Button)findViewById(R.id.btConnectedToDevice);
        btnConnectToServer.setOnClickListener(this);
        btnConnectToServer.setEnabled(false);

        spinnerSetPath = (Spinner)findViewById(R.id.spinnerSetPath);
        ArrayList<PbapOption> setPathOptions = new ArrayList<PbapOption>();
        setPathOptions.add(new PbapOption("PB_PATH", BluetoothPbapClient.PB_PATH));
        setPathOptions.add(new PbapOption("ICH_PATH", BluetoothPbapClient.ICH_PATH));
        setPathOptions.add(new PbapOption("OCH_PATH", BluetoothPbapClient.OCH_PATH));
        setPathOptions.add(new PbapOption("MCH_PATH", BluetoothPbapClient.MCH_PATH));
        setPathOptions.add(new PbapOption("CCH_PATH", BluetoothPbapClient.CCH_PATH));
        setPathOptions.add(new PbapOption("SIM_PB_PATH", BluetoothPbapClient.SIM_PB_PATH));
        setPathOptions.add(new PbapOption("SIM_ICH_PATH", BluetoothPbapClient.SIM_ICH_PATH));
        setPathOptions.add(new PbapOption("SIM_OCH_PATH", BluetoothPbapClient.SIM_OCH_PATH));
        setPathOptions.add(new PbapOption("SIM_MCH_PATH", BluetoothPbapClient.SIM_MCH_PATH));
        setPathOptions.add(new PbapOption("SIM_CCH_PATH", BluetoothPbapClient.SIM_CCH_PATH));

        ArrayAdapter<PbapOption> setPathAdapter = new ArrayAdapter<PbapOption>(this,
                android.R.layout.simple_spinner_item, setPathOptions);
        setPathAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSetPath.setAdapter(setPathAdapter);

        spinnerPullPbAbsPath = (Spinner)findViewById(R.id.spinnerPullPbPath);
        ArrayList<PbapOption> pullPathOptions = new ArrayList<PbapOption>();
        pullPathOptions.add(new PbapOption("PB_PATH", BluetoothPbapClient.PB_PATH));
        pullPathOptions.add(new PbapOption("ICH_PATH", BluetoothPbapClient.ICH_PATH));
        pullPathOptions.add(new PbapOption("OCH_PATH", BluetoothPbapClient.OCH_PATH));
        pullPathOptions.add(new PbapOption("MCH_PATH", BluetoothPbapClient.MCH_PATH));
        pullPathOptions.add(new PbapOption("CCH_PATH", BluetoothPbapClient.CCH_PATH));
        pullPathOptions.add(new PbapOption("SIM_PB_PATH", BluetoothPbapClient.SIM_PB_PATH));
        pullPathOptions.add(new PbapOption("SIM_ICH_PATH", BluetoothPbapClient.SIM_ICH_PATH));
        pullPathOptions.add(new PbapOption("SIM_OCH_PATH", BluetoothPbapClient.SIM_OCH_PATH));
        pullPathOptions.add(new PbapOption("SIM_MCH_PATH", BluetoothPbapClient.SIM_MCH_PATH));
        pullPathOptions.add(new PbapOption("SIM_CCH_PATH", BluetoothPbapClient.SIM_CCH_PATH));

        ArrayAdapter<PbapOption> pbPathAdapter = new ArrayAdapter<PbapOption>(this,
                android.R.layout.simple_spinner_item, pullPathOptions);
        pbPathAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPullPbAbsPath.setAdapter(pbPathAdapter);

        tvMaxListCountPb = (TextView)findViewById(R.id.textViewMaxListCountpb);
        seekBarMaxListCountPb = (SeekBar)findViewById(R.id.seekBarMaxListCountpb);
        seekBarMaxListCountPb.setMax(1001); // allow max 1000
        seekBarMaxListCountPb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxListCountValuePb = progress - 1; // max list count can be -1,
                // changing scale
                tvMaxListCountPb.setText("maxListCount: " + maxListCountValuePb);
            }
        });

        btnPullPhonebook = (Button)findViewById(R.id.buttonPullPhonebook);
        btnPullPhonebook.setOnClickListener(this);

        btnImportToPb = (Button)findViewById(R.id.buttonImportToPb);
        btnImportToPb.setOnClickListener(this);

        btnImportToCallLog = (Button)findViewById(R.id.buttonImportToCalllog);
        btnImportToCallLog.setOnClickListener(this);

        spinnerBrowseAbsPath = (Spinner)findViewById(R.id.spinnerBrowsePath);
        ArrayList<PbapOption> browsePathOptions = new ArrayList<PbapOption>();
        browsePathOptions.add(new PbapOption("CURRENT PATH", ""));
        browsePathOptions.add(new PbapOption("PB_PATH", BluetoothPbapClient.PB_PATH));
        browsePathOptions.add(new PbapOption("ICH_PATH", BluetoothPbapClient.ICH_PATH));
        browsePathOptions.add(new PbapOption("OCH_PATH", BluetoothPbapClient.OCH_PATH));
        browsePathOptions.add(new PbapOption("MCH_PATH", BluetoothPbapClient.MCH_PATH));
        browsePathOptions.add(new PbapOption("CCH_PATH", BluetoothPbapClient.CCH_PATH));
        browsePathOptions.add(new PbapOption("SIM_PB_PATH", BluetoothPbapClient.SIM_PB_PATH));
        browsePathOptions.add(new PbapOption("SIM_ICH_PATH", BluetoothPbapClient.SIM_ICH_PATH));
        browsePathOptions.add(new PbapOption("SIM_OCH_PATH", BluetoothPbapClient.SIM_OCH_PATH));
        browsePathOptions.add(new PbapOption("SIM_MCH_PATH", BluetoothPbapClient.SIM_MCH_PATH));
        browsePathOptions.add(new PbapOption("SIM_CCH_PATH", BluetoothPbapClient.SIM_CCH_PATH));

        ArrayAdapter<PbapOption> browsePathAdapter = new ArrayAdapter<PbapOption>(this,
                android.R.layout.simple_spinner_item, browsePathOptions);
        browsePathAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBrowseAbsPath.setAdapter(browsePathAdapter);
        spinnerBrowseAbsPath.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View v, int pos, long l) {
                PbapOption selectedBrowsePath = (PbapOption)spinnerBrowseAbsPath
                        .getItemAtPosition(pos);
                mBrowsePath = selectedBrowsePath.getString();
                Log.d(TAG, "selectedBrowsePath: " + mBrowsePath);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        spinnerSearchOrder = (Spinner)findViewById(R.id.spinnerSearchOrder);
        ArrayList<PbapOption> listSearchOrderOptions = new ArrayList<PbapOption>();
        listSearchOrderOptions.add(new PbapOption("SEARCH_ORDER_ALPHABETICAL", new Integer(
                BluetoothPbapClient.SEARCH_ORDER_ALPHABETICAL)));
        listSearchOrderOptions.add(new PbapOption("SEARCH_ORDER_INDEXED", new Integer(
                BluetoothPbapClient.SEARCH_ORDER_INDEXED)));
        listSearchOrderOptions.add(new PbapOption("SEARCH_ORDER_PHONETICAL", new Integer(
                BluetoothPbapClient.SEARCH_ORDER_PHONETICAL)));
        listSearchOrderOptions.add(new PbapOption("SEARCH_ORDER_NOT_SET", new Integer(
                BluetoothPbapClient.SEARCH_ORDER_NOT_SET)));
        ArrayAdapter<PbapOption> searchOrderAdapter = new ArrayAdapter<PbapOption>(this,
                android.R.layout.simple_spinner_item, listSearchOrderOptions);
        searchOrderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSearchOrder.setAdapter(searchOrderAdapter);

        spinnerSearchAttribute = (Spinner)findViewById(R.id.spinnerSearchAttribute);
        ArrayList<PbapOption> listSearchAttrOptions = new ArrayList<PbapOption>();
        listSearchAttrOptions.add(new PbapOption("SEARCH_ATTRIBUTE_NAME", new Integer(
                BluetoothPbapClient.SEARCH_ATTRIBUTE_NAME)));
        listSearchAttrOptions.add(new PbapOption("SEARCH_ATTRIBUTE_NUMBER", new Integer(
                BluetoothPbapClient.SEARCH_ATTRIBUTE_NUMBER)));
        listSearchAttrOptions.add(new PbapOption("SEARCH_ATTRIBUTE_SOUND", new Integer(
                BluetoothPbapClient.SEARCH_ATTRIBUTE_SOUND)));
        listSearchAttrOptions.add(new PbapOption("SEARCH_ATTRIBUTE_NOT_SET", new Integer(
                BluetoothPbapClient.SEARCH_ATTRIBUTE_NOT_SET)));
        ArrayAdapter<PbapOption> searchAttrAdapter = new ArrayAdapter<PbapOption>(this,
                android.R.layout.simple_spinner_item, listSearchAttrOptions);
        searchAttrAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSearchAttribute.setAdapter(searchAttrAdapter);

        tvMaxListCountPbListing = (TextView)findViewById(R.id.textViewMaxListCountListing);
        seekBarMaxListCountPbListing = (SeekBar)findViewById(R.id.seekBarMaxListCountListing);
        seekBarMaxListCountPbListing.setMax(1001); // allow max 1000
        seekBarMaxListCountPbListing.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // max list count can be -1
                maxListCountValuePbListing = progress - 1;
                // changing scale
                tvMaxListCountPbListing.setText("maxListCount: " + maxListCountValuePbListing);
            }
        });

        vcardListingAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line);

        vcardListingAdapter.setNotifyOnChange(true); // automatically sync
        autotvListing = (AutoCompleteTextView)findViewById(R.id.autoCmplTvListing);
        autotvListing.setThreshold(1);
        autotvListing.setAdapter(vcardListingAdapter);

        TextWatcher textChecker = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                Log.d(TAG, "beforeTextChanged s=" + s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d(TAG, "onTextChanged s=" + s + " count=" + count);
                // Download vcard listing for all vCards containing the first
                // alphabet entered in the search box. Autocomplete feature
                // stores all the names and provides auto suggestions to users
                // given the list.
                if (s.length() >= 1) {
                    Log.d(TAG, "calling update listing..");
                    final PbapOption selectedSearchOrder;
                    final PbapOption selectedSearchAttr;

                    if ((selectedSearchOrder = (PbapOption)spinnerSearchOrder.getSelectedItem()) == null) {
                        Log.e(TAG, "Error setting selectedSearchOrder");
                    }

                    if ((selectedSearchAttr = (PbapOption)spinnerSearchAttribute.getSelectedItem()) == null) {
                        Log.e(TAG, "Error setting selectedSearchAttr");
                    }

                    String searchValue = s.toString();
                    pullVcardListing(serverAddress, mBrowsePath, Integer.toString(maxListCountValuePb),
                            "-1", selectedSearchOrder.getInteger().toString(), selectedSearchAttr
                            .getInteger().toString(), searchValue, OUT_FILE_PULL_VC_LISTING);
                    vcardNameAndHandle.clear();
                }
                // Store the selected text from autocomplete box, we'll pull
                // vCard for this.
                mSelectedNameForPullVcard = s.toString();
            }

            @Override
            public void afterTextChanged(Editable e) {
                // Log.d(TAG, "onTextChanged e=" + e.toString());
            }
        };
        autotvListing.addTextChangedListener(textChecker);

        btnAbort = (Button)findViewById(R.id.buttonAbort);
        btnAbort.setOnClickListener(this);

        btnSetPath = (Button)findViewById(R.id.buttonSetpath);
        btnSetPath.setOnClickListener(this);

        mBluetoothAdapter = ((PbapClientTestApp)getApplication()).mBluetoothAdapter;
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Device does not support bluetooth");
            Toast.makeText(getApplicationContext(), "Device does not support bluetooth",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        mReceiver = new PbapClientBroadcastReceiver();
        Log.d(TAG, "Registering receiver");
        registerReceiver(mReceiver, mReceiver.createFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // check if bluetooth is ON
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Turn ON Bluetooth", Toast.LENGTH_LONG).show();
            finish();
        } else {
            serverAddress = ((PbapClientTestApp)getApplication()).getRemoteServerAddr();
            serverFriendlyName = ((PbapClientTestApp)getApplication()).getRemoteServerName();
            state = ((PbapClientTestApp)getApplication()).getApplicationState();
            Log.d(TAG, "onResume ApplicationState: " + state + " serverAddress: " + serverAddress);

            if (serverAddress == null) {
                changeState(PbapClientTestApp.PCE_STATE_INIT);
            } else {
                // TBD - Resume activity from connected state
                // Have to verify if the application state is indeed correct
                // as the state might have changed
                // if (!(state == PbapClientTestApp.PCE_STATE_CONNECTED) &&
                // isConnected())
                // changeState(PbapClientTestApp.PCE_STATE_INIT); //We are no
                // more connected
                changeState(PbapClientTestApp.PCE_STATE_READY);
            }
            tvServerName.setText("Server: "
                    + (serverFriendlyName == null ? "(null)" : serverFriendlyName) + " ("
                    + (serverAddress == null ? "null" : serverAddress) + ")");

            //Get preference settings
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefAddSdp = sharedPrefs.getBoolean("pceAddSdp", false);
            prefPhotoDnld = sharedPrefs.getBoolean("pcePhotoDownloadConfig", false);
            prefVCardVerStr = sharedPrefs.getString("pceVcardType",
                    Integer.toString(BluetoothPbapClient.DEFAULT_VCARD_VERSION));

            Log.d(TAG, "onResume preference AddSdp:" + prefAddSdp + " prefPhotoDnld:" + prefPhotoDnld
                    + " prefVCardVerStr:" + prefVCardVerStr);

            if(prefAddSdp == true)
                addSdp();

            updateUI();
        }
    }

    @Override
    protected void onPause() {
        removeSdp();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        removeSdp();
        if (mReceiver != null) {
            try {
                Log.d(TAG, "Unregistering receiver");
                unregisterReceiver(mReceiver);
            } catch (Throwable t) {
                Log.e(TAG, "Error unregistering receiver", t);
            }
            mReceiver = null;
        }
        Log.d(TAG, "onDestroy deleting client");

        BluetoothPbapClient client = getClient(serverAddress);
        if (client != null) {
            client.finish();
        }
        mPbapClient = null;

        super.onDestroy();
    }

    private void updateUI() {
        switch (((PbapClientTestApp)getApplication()).getApplicationState()) {
            case PbapClientTestApp.PCE_STATE_INIT:
            case PbapClientTestApp.PCE_STATE_READY:
                btnSelectBTServer.setEnabled(true);
                btnConnectToServer.setEnabled(true);
                break;
            case PbapClientTestApp.PCE_STATE_CONNECTED:
                btnSelectBTServer.setEnabled(false);
                btnConnectToServer.setEnabled(true);
                btnConnectToServer.setText("Disconnect");
                Log.d(TAG, "Connected To serverAddress: " + serverAddress + " serverFriendlyName: "
                        + serverFriendlyName);
                tvServerName.setText("Server: "
                        + (serverFriendlyName == null ? "(null)" : serverFriendlyName) + " ("
                        + (serverAddress == null ? "null" : serverAddress) + ")");
                break;
            case PbapClientTestApp.PCE_STATE_DISCONNECTED:
                btnSelectBTServer.setEnabled(true);
                btnConnectToServer.setEnabled(true);
                btnConnectToServer.setText("Connect");
                Log.d(TAG, "Disconnected from serverAddress: " + serverAddress
                        + " serverFriendlyName: " + serverFriendlyName);
                tvServerName.setText("Server: "
                        + (serverFriendlyName == null ? "(null)" : serverFriendlyName) + " ("
                        + (serverAddress == null ? "null" : serverAddress) + ")");
                break;
        }
    }

    private void changeState(int toState) {
        state = toState;
        ((PbapClientTestApp)getApplication()).setApplicationState(toState);
        updateUI();
    }

    @Override
    public void onClick(View v) {
        Button btn = (Button)v;

        if (btn == btnSelectBTServer) {
            pickBTDevice();
        } else if (btn == btnConnectToServer) {
            BluetoothPbapClient client = getClient(serverAddress);
            boolean ret = false;
            if (client == null) {
                Log.d(TAG, "Connecting to:" + serverAddress);
                ret = connect(serverAddress);
            } else {
                if (!client.isConnected()) {
                    Log.d(TAG, "Not connected, connecting to:" + serverAddress);
                    ret = connect(serverAddress);
                } else {
                    Log.d(TAG, "Disconnecting from:" + serverAddress);
                    ret = disconnect(serverAddress);
                }
            }
            Log.d(TAG, "Connect ret:" + ret);
            // Processing connect/disconnect, diable UI
            if (ret == true)
                btnConnectToServer.setEnabled(false);
            else
                btnConnectToServer.setEnabled(true);
        } else if (btn == btnPullPhonebook) {

            btnPullPhonebook.setEnabled(false);
            PbapOption selectedPullPbPath = (PbapOption)spinnerPullPbAbsPath.getSelectedItem();
            mPullPbPath = selectedPullPbPath.getString();

            Log.d(TAG, "selectedPullPbPath name: " + selectedPullPbPath.getName() + " path: "
                    + mPullPbPath);
            try {
                FileOutputStream fOut = openFileOutput("pce_pb", Context.MODE_PRIVATE);
                fOut.close();
            } catch (Exception e) {
                Toast.makeText(PbapClientTestActivity.this, e.getMessage(), Toast.LENGTH_SHORT)
                .show();
            }

            String maskStr;
            if (prefPhotoDnld) {
                maskStr = "-1";
            } else {
                maskStr = ATTR_MASK_NO_PHOTO;
            }

            pullPhonebook(serverAddress, mPullPbPath,
                    Integer.toString(maxListCountValuePb), "-1", prefVCardVerStr, maskStr,
                    OUT_FILE_PULL_PB);

        } else if (btn == btnImportToPb) {

            String handleName;

            if (vcardNameAndHandle.isEmpty()) {
                Log.e(TAG, mSelectedNameForPullVcard + " does not match any vcard");
                return;
            }

            for(Map.Entry<String, String> entry : vcardNameAndHandle.entrySet()) {
                handleName = entry.getValue();
                // Upon onPullVcardEntryEvent, we will put the .vcf into phonebook
                pullVcardEntry(serverAddress, handleName, Integer.toString(maxListCountValuePb),
                        "-1", prefVCardVerStr, "-1", OUT_FILE_PULL_VC_ENTRY);
            }

        } else if (btn == btnImportToCallLog) {

            String handleName;

            if (vcardNameAndHandle.isEmpty()) {
                Log.e(TAG, mSelectedNameForPullVcard + " does not match any vcard");
                return;
            }

            for(Map.Entry<String, String> entry : vcardNameAndHandle.entrySet()) {
                handleName = entry.getValue();
                // Upon onPullVcardEntryEvent, we will put the .vcf into phonebook
                pullVcardEntry(serverAddress, handleName, Integer.toString(maxListCountValuePb),
                        "-1", prefVCardVerStr, "-1", OUT_FILE_PULL_VC_ENTRY);
            }

        } else if (btn == btnAbort) {
            abort(serverAddress);
        } else if (btn == btnSetPath) {
            PbapOption selectedPath;
            mServerPath = null;
            if ((selectedPath = (PbapOption)spinnerSetPath.getSelectedItem()) != null) {
                if ((serverAddress != null) && (mPbapClient != null)) {
                    Log.d(TAG, "setPath to:" + selectedPath.getString());
                    if (setPath(serverAddress, selectedPath.getString())) {
                        btnSetPath.setEnabled(false);
                    } else
                        Toast.makeText(getApplicationContext(), "Set Path Failed",
                                Toast.LENGTH_LONG).show();
                }
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pbapclient, menu);
        Log.d(TAG, "onCreateOptionsMenu");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Log.d(TAG, "clicked on application settings");
                startActivity(new Intent(this, PrefsActivity.class));
                return true;
            case R.id.clear_all_contacts:
                Log.d(TAG, "clicked on application clear_all_contacts");
                clearAllContacts();
                return true;
            case R.id.clear_all_calllogs:
                Log.d(TAG, "clicked on application clear_all_calllogs");
                clearAllCallLogs();
                return true;
            case R.id.clear_all_outgoingcalls:
                Log.d(TAG, "clicked on application clear_all_outgoingcalls");
                clearCallLogs(CallLog.Calls.OUTGOING_TYPE);
                return true;
            case R.id.clear_all_incomingcalls:
                Log.d(TAG, "clicked on application clear_all_incomingcalls");
                clearCallLogs(CallLog.Calls.INCOMING_TYPE);
                return true;
            case R.id.clear_all_missedcalls:
                Log.d(TAG, "clicked on application clear_all_missedcalls");
                clearCallLogs(CallLog.Calls.MISSED_TYPE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private synchronized void clearAllContacts() {
        // Clear all the existing contacts
        // Show a wait dialog
        final ProgressDialog progDlg = ProgressDialog.show(this, "Deleting all contacts",
                "Please wait..", true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver cr = getContentResolver();
                Cursor cur = cr
                        .query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
                while (cur.moveToNext()) {
                    try {
                        String lookupKey = cur.getString(cur
                                .getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
                        Uri uri = Uri.withAppendedPath(
                                ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                        System.out.println("The uri is " + uri.toString());
                        cr.delete(uri, null, null);
                    } catch (Exception e) {
                        System.out.println(e.getStackTrace());
                    }
                }
                cur.close();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progDlg.dismiss();
                    }
                });
            }
        }).start();
    }

    private synchronized void clearAllCallLogs() {

        final ProgressDialog progDlg = ProgressDialog.show(this, "Deleting all call logs",
                "Please wait..", true);
        new Thread(new Runnable() {
            @Override
            public void run() {

                mCallLogUtils.clearAllCallLogs(getContentResolver());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(progDlg.isShowing())
                            progDlg.dismiss();
                    }
                });
            }
        }).start();
    }

    private synchronized void clearCallLogs(final int type) {

        final ProgressDialog progDlg = ProgressDialog.show(this, "Deleting call logs",
                "Please wait..", true);
        new Thread(new Runnable() {
            @Override
            public void run() {

                mCallLogUtils.clearCallLogs(getContentResolver(), type);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progDlg.dismiss();
                    }
                });
            }
        }).start();
    }

    private void updateVcardListing(String xmlPath) {

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();

        try {
            InputStream inputStream = new FileInputStream(xmlPath);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("card");
            Log.d(TAG, "nodeList.getLength: " + nodeList.getLength());

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                Element fstElmnt = (Element)node;
                String name = fstElmnt.getAttribute("name");
                String handle = fstElmnt.getAttribute("handle");
                Log.d(TAG, "got element: name=" + name + " handle=" + handle);
                if (!vcardNameAndHandle.containsKey(name))
                    vcardNameAndHandle.put(name, handle);
            }

            if (vcardNameAndHandle.size() > 0) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        vcardListingAdapter.clear();
                        Log.d(TAG, "refreshing adapter size:" + vcardNameAndHandle.size()
                                + " values:" + vcardNameAndHandle.keySet());
                        vcardListingAdapter.addAll(new ArrayList<String>(vcardNameAndHandle
                                .keySet()));
                        vcardListingAdapter.notifyDataSetChanged();
                        autotvListing.showDropDown();
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pickBTDevice() {
        Intent btDevicePickerIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(btDevicePickerIntent, REQCODE_BLUETOOTH_RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "Result code: " + Integer.toString(resultCode));

        switch (requestCode) {
            case REQCODE_BLUETOOTH_RESULT:
                Log.i(TAG, "requestCode = REQCODE_BLUETOOTH_RESULT");

                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "RESULT_OK");

                    // Retrieve the Info
                    Bundle extras = data.getExtras();

                    if (extras != null) {
                        Log.i(TAG, "Bundle ok");
                        serverAddress = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                        serverFriendlyName = extras.getString(DeviceListActivity.EXTRA_DEVICE_NAME);
                        Log.d(TAG, "serverAddress: " + serverAddress);
                        Log.d(TAG, "serverFriendlyName: " + serverFriendlyName);
                        btnConnectToServer.setEnabled(true);
                        tvServerName.setText("Server: "
                                + (serverFriendlyName == null ? "(null)" : serverFriendlyName)
                                + " (" + (serverAddress == null ? "null" : serverAddress) + ")");
                        ((PbapClientTestApp)getApplication()).setRemoteServerAddr(serverAddress);
                        ((PbapClientTestApp)getApplication())
                        .setRemoteServerName(serverFriendlyName);
                        changeState(PbapClientTestApp.PCE_STATE_READY);
                    }
                } else {
                    Log.e(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
                    Toast.makeText(this, "Failed(" + resultCode + ")", Toast.LENGTH_SHORT).show();
                }

                break;

            default:
                Log.e(TAG, "requestCode UNKNOWN!");
                break;
        }
    }

    /**
     * Handler handles all the GUI update events
     */
    protected Handler viewUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            boolean status = (msg.arg1 != 0);
            final String path = (String)msg.obj;
            Log.d(TAG, "handleMessage(): msg.what:" + msg.what + " status:" + status +
                    " path:" + path);

            switch (msg.what) {
                case UI_FOLDER_PATH_SET: {
                    btnSetPath.setEnabled(true);
                    if ((status == false) || (mServerPath == null))
                        return;
                    if (mServerPath.contains(BluetoothPbapClient.PB_PATH)
                            || mServerPath.contains(BluetoothPbapClient.SIM_PB_PATH)) {
                        btnImportToPb.setEnabled(true);
                        btnImportToCallLog.setEnabled(false);
                    } else {
                        btnImportToPb.setEnabled(false);
                        btnImportToCallLog.setEnabled(true);
                    }
                }
                break;
                case UI_CONNECTED_TO_SERVER: {
                    changeState(PbapClientTestApp.PCE_STATE_CONNECTED);
                }
                break;
                case UI_UNABLE_TO_CONNECT_TO_SERVER: {
                    changeState(PbapClientTestApp.PCE_STATE_DISCONNECTED);
                    Log.d(TAG, "Unable to connect to server");
                }
                break;
                case UI_DISCONNECTED_FROM_SERVER: {
                    Log.d(TAG, "onDisconnected");
                    changeState(PbapClientTestApp.PCE_STATE_DISCONNECTED);
                }
                break;
                case UI_PULL_PHONEBOOK: {
                    // If we have set path to phoneboook, then we will import
                    // "all" entries to local phonebook, else goes to calllist
                    Log.d(TAG, "UI_PULL_PHONEBOOK mPullPbPath: " + mPullPbPath);

                    if (status != false) {
                        if (mPullPbPath.contains(BluetoothPbapClient.PB_PATH)
                                || mPullPbPath.contains(BluetoothPbapClient.SIM_PB_PATH)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Intent showContacts = new Intent(Intent.ACTION_VIEW);
                                    showContacts.setDataAndType(
                                            Uri.fromFile(new File(OUT_FILE_PULL_PB)),
                                            "text/x-vcard");
                                    showContacts.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(showContacts);
                                }
                            });
                        } else {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // The type of call i.e ich, och, mch will
                                        // be determined automatically by parsing .vcf
                                        // using "X-IRMC-CALL-DATETIME" property
                                        // [IOP issue] when IRMC property is missing, determine
                                        // call type from path.
                                        int callTypeVcf = 0;
                                        if(path.contains("ich"))
                                            callTypeVcf = CallLog.Calls.INCOMING_TYPE;
                                        else if(path.contains("och"))
                                            callTypeVcf = CallLog.Calls.OUTGOING_TYPE;
                                        else if(path.contains("mch"))
                                            callTypeVcf = CallLog.Calls.MISSED_TYPE;
                                        mCallLogUtils.addVCFtoCallLog(getContentResolver(),
                                                OUT_FILE_PULL_PB, callTypeVcf);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } catch (VCardException vce) {
                                        vce.printStackTrace();
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Intent showCallLog = new Intent();
                                            showCallLog.setAction(Intent.ACTION_VIEW);
                                            showCallLog.setType(CallLog.Calls.CONTENT_TYPE);
                                            showCallLog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(showCallLog);
                                        }
                                    });
                                }
                            }).start();
                        }
                    }
                    btnPullPhonebook.setEnabled(true);
                }
                break;
                case UI_PULL_VCARD_ENTRY: {
                    // If we have set path to phoneboook, then we will import
                    // this "one" entry local phonebook, else goes to call list
                    // The path can be either relative to current path
                    // set using setPath or absolute path used for browse.
                    boolean isPhoneBookPath = false;

                    if (status == false)
                        return;
                    if (((mServerPath != null) && (mServerPath
                            .contains(BluetoothPbapClient.PB_PATH) || mServerPath
                            .contains(BluetoothPbapClient.SIM_PB_PATH)))
                            || ((mBrowsePath != null) && (mBrowsePath
                                    .contains(BluetoothPbapClient.PB_PATH) || mBrowsePath
                                    .contains(BluetoothPbapClient.SIM_PB_PATH))))
                        isPhoneBookPath = true;

                    if (isPhoneBookPath) {
                        Log.d(TAG, "making vcard entry to phonebook");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent showContacts = new Intent(Intent.ACTION_VIEW);
                                showContacts.setDataAndType(
                                        Uri.fromFile(new File(OUT_FILE_PULL_VC_ENTRY)),
                                        "text/x-vcard");
                                showContacts.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(showContacts);
                            }
                        });
                    } else {
                        Log.d(TAG, "making vcard entry to call logs");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Log.d(TAG, "getContentResolver():" + getContentResolver());
                                    int callTypeVcf = 0;
                                    if(path.contains("ich"))
                                        callTypeVcf = CallLog.Calls.INCOMING_TYPE;
                                    else if(path.contains("och"))
                                        callTypeVcf = CallLog.Calls.OUTGOING_TYPE;
                                    else if(path.contains("mch"))
                                        callTypeVcf = CallLog.Calls.MISSED_TYPE;
                                    mCallLogUtils.addVCFtoCallLog(getContentResolver(),
                                            OUT_FILE_PULL_VC_ENTRY, callTypeVcf);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (VCardException vce) {
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent showCallLog = new Intent();
                                        showCallLog.setAction(Intent.ACTION_VIEW);
                                        showCallLog.setType(CallLog.Calls.CONTENT_TYPE);
                                        showCallLog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        startActivity(showCallLog);
                                    }
                                });

                            }
                        }).start();
                    }
                }
                break;
                case UI_PULL_VCARD_LISTING: {
                    if (status == false)
                        return;
                    updateVcardListing(OUT_FILE_PULL_VC_LISTING);
                }
                break;
                case UI_PULL_ABORT:
                    break;
                default:
                    Log.e(TAG, "Unknown UI handle event received");
            }
        }
    };

    private BluetoothPbapClient getClient(String bdaddr) {

        if (bdaddr == null) {
            Log.d(TAG, "getClient(): Invalid bdaddr: " + bdaddr);
            return null;
        }
        bdaddr = bdaddr.toUpperCase();
        @SuppressWarnings("unused")
        BluetoothDevice pbapServer = null;
        try {
            pbapServer = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdaddr);
        } catch (Throwable t) {
            Log.e(TAG, "getClient(): Unable to create BluetoothDevice with address: " + bdaddr);
            return null;
        }

        if (mPbapClient == null)
            Log.e(TAG, "getClient(): PBAP server not connected: " + bdaddr);

        return mPbapClient;
    }

    private boolean connect(String bdaddr) {

        if (bdaddr == null) {
            Log.e(TAG, "onConnect(): Invalid bdaddr: " + bdaddr);
            return false;
        }

        // If client is already created, is it for the same server?
        if (mPbapClient != null) {
            BluetoothDevice currpbapServer = mPbapClient.getPbapServer();
            if (!bdaddr.equalsIgnoreCase(currpbapServer.getAddress())) {
                Log.e(TAG,
                        "Remote PBAP server NOT same as the one associated with current client, close");
                mPbapClient.finish();
                mPbapClient = null;
            }
        }

        bdaddr = bdaddr.toUpperCase();
        BluetoothDevice pbapServer = null;
        try {
            pbapServer = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdaddr);
        } catch (Throwable t) {
            Log.e(TAG, "onConnect(): unable to create BluetoothDevice with address: " + bdaddr, t);
            return false;
        }

        // Check if the client is already created
        if (mPbapClient == null) {
            mPbapClient = new BluetoothPbapClient(this, pbapServer, this);
            mPbapClient.setAuthHandler(this, 10000);
        }
        mPbapClient.connect();
        return true;
    }

    private boolean disconnect(String bdaddr) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "onDisconnect(): PBAP server not connected: " + bdaddr);
            return false;
        }
        client.disconnect();
        return true;
    }

    private boolean setPath(String bdaddr, String path) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "setPath(): PBAP server not connected: " + bdaddr + " path: " + path);
            return false;
        }
        client.setPath(path);
        Log.e(TAG, "setPath(): PBAP server path: " + path);
        return true;
    }

    private void pullPhonebook(String bdaddr, String path, String maxListCountStr,
            String listStartOffsetStr, String vcardVersionStr, String attrMaskStr,
            String outFilepath) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "onPullPhonebook(): PBAP server not connected: " + bdaddr);
            return;
        }

        int maxListCount = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            maxListCount = Integer.parseInt(maxListCountStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing maxListCount", t);
        }

        int listStartOffset = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            listStartOffset = Integer.parseInt(listStartOffsetStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing listStartOffset", t);
        }

        byte vcardVersion = BluetoothPbapClient.DEFAULT_VCARD_VERSION;
        try {
            vcardVersion = Byte.parseByte(vcardVersionStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing vcardVersion", t);
        }

        BluetoothAttributeMask mask = null;

        if (attrMaskStr != null && !attrMaskStr.equals("-1")) {
            try {
                long attrMask = Long.parseLong(attrMaskStr);
                mask = new BluetoothAttributeMask();
                mask.parse(attrMask);
            } catch (Throwable t) {
                Log.e(TAG, "Error parsing attrMask", t);
            }
        }

        if (outFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }
        Log.d(TAG, "pullPhonebook(): path=" + (path == null ? "(null)" : path) + ", vcardVersion="
                + vcardVersion + ", maxlistCount=" + maxListCount + ", listStartOffset="
                + listStartOffset + ", outFilepath=" + outFilepath);
        client.pullPhonebook(path, mask, vcardVersion, maxListCount, listStartOffset, outFilepath);

    }

    private void pullVcardListing(String bdaddr, String name, String maxListCountStr,
            String listStartOffsetStr, String searchOrderStr, String searchAttrStr,
            String searchValue, String outFilepath) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "onPullVcardListing(): PBAP server not connected: " + bdaddr);
            return;
        }

        int maxListCount = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            maxListCount = Integer.parseInt(maxListCountStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing maxListCount", t);
        }

        int listStartOffset = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            listStartOffset = Integer.parseInt(listStartOffsetStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing listStartOffset", t);
        }

        byte searchOrder = BluetoothPbapClient.SEARCH_ORDER_NOT_SET;
        try {
            searchOrder = Byte.parseByte(searchOrderStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing searchOrder", t);
        }

        byte searchAttribute = 0;
        try {
            searchAttribute = Byte.parseByte(searchAttrStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing searchAttr", t);
        }

        if (outFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }

        Log.d(TAG, "pullVcardListing(): name=" + (name == null ? "(null)" : name)
                + ", searchOrder=" + searchOrder + ", searchAttribute=" + searchAttribute
                + ", searchValue=" + searchValue + ", maxListCount=" + maxListCount
                + ", listStartOffset=" + listStartOffset + ", outFilepath=" + outFilepath);
        client.pullVcardListing(name, searchOrder, searchAttribute, searchValue, maxListCount,
                listStartOffset, outFilepath);

    }

    private void pullVcardEntry(String bdaddr, String name, String maxListCountStr,
            String listStartOffsetStr, String vcardVersionStr, String attrMaskStr,
            String outFilepath) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "onPullVcardEntry(): PBAP server not connected: " + bdaddr);
            return;
        }

        byte vcardVersion = BluetoothPbapClient.DEFAULT_VCARD_VERSION;
        try {
            vcardVersion = Byte.parseByte(vcardVersionStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing vcardVersion", t);
        }

        long attrMask = 0;
        try {
            attrMask = Long.parseLong(attrMaskStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing attrMask", t);
        }
        BluetoothAttributeMask mask = new BluetoothAttributeMask();
        mask.parse(attrMask);

        if (outFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }
        Log.d(TAG, "pullVcardEntry(): name=" + (name == null ? "(null)" : name) + ", mask=" + mask
                + ", vcardVersion=" + vcardVersion + ", outFilepath=" + outFilepath);
        client.pullVcardEntry(name, mask, vcardVersion, outFilepath);
    }

    private void abort(String bdaddr) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(bdaddr);
        if (client == null) {
            Log.e(TAG, "abort(): PBAP server not connected: " + bdaddr);
            return;
        }
        Log.d(TAG, "aborting pbap operation: " + bdaddr);
        client.abort();
    }

    private void addSdp() {
        Log.d(TAG, "adding pbap to SDP");
        BluetoothPbapClient.addSdp();
    }

    private void removeSdp() {
        Log.d(TAG, "removing pbap from SDP");
        BluetoothPbapClient.removeSdp();
    }

    @Override
    public void onConnected(BluetoothDevice device, boolean success) {
        Log.d(TAG, "onConnected(): " + device + ", success=" + success);
        String toastString = null;
        if (success) {
            toastString = getString(R.string.status_connected, device.getAddress());
        } else {
            toastString = getString(R.string.status_connect_error, device.getAddress());
        }
        Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_SHORT).show();

        if (success == true) {
            Message msg = Message.obtain();
            msg.what = UI_CONNECTED_TO_SERVER;
            viewUpdateHandler.sendMessage(msg);
        } else {
            Message msg = Message.obtain();
            msg.what = UI_DISCONNECTED_FROM_SERVER;
            viewUpdateHandler.sendMessage(msg);
        }

    }

    @Override
    public void onDisconnected(BluetoothDevice device, boolean success) {
        Log.d(TAG, "onDisconnected(): " + device + ", success=" + success);
        String toastString = null;
        if (success) {
            toastString = getString(R.string.status_disconnected, device.getAddress());
        } else {
            toastString = getString(R.string.status_disconnect_error, device.getAddress());
        }
        Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_SHORT).show();

        Log.d(TAG, "onDisconnected(): " + device.getAddress() + ", success=" + success);
        // We should reset UI at disconnect regardless
        Message msg = Message.obtain();
        msg.what = UI_DISCONNECTED_FROM_SERVER;
        viewUpdateHandler.sendMessage(msg);
    }

    @Override
    public void onPathSet(BluetoothDevice device, boolean success, String path) {
        Log.d(TAG, "onPathSet(): " + device + ", success=" + success + ", path=" + path);
        String status;
        if (true == success) {
            status = "setPath Success";
            mServerPath = path;
        } else {
            status = "setPath Failure";
            mServerPath = null;
        }

        Message msg = Message.obtain();
        msg.what = UI_FOLDER_PATH_SET;
        msg.arg1 = (success) ? 1 : 0;
        msg.obj = path;
        viewUpdateHandler.sendMessage(msg);

        Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPullPhonebookCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath) {
        Log.d(TAG, "onPullPhonebookCompleted(): " + device + ", path=" + path + ", success="
                + success + ", phonebookSize=" + phonebookSize + ", newMissedCalls="
                + newMissedCalls + ", outputFilepath=" + outputFilepath);
        String status;
        if (true == success)
            status = "Pull phonebook Success";
        else
            status = "Pull phonebook Failure";

        Message msg = Message.obtain();
        msg.what = UI_PULL_PHONEBOOK;
        msg.arg1 = (success) ? 1 : 0;
        msg.obj = path;
        viewUpdateHandler.sendMessage(msg);

        Toast.makeText(getApplicationContext(), status + path, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPullVcardListingCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath) {
        String status;
        if (true == success)
            status = "Pull vcard list Success";
        else
            status = "Pull vcard list Failure";

        Log.d(TAG, "onPullVcardListingCompleted path:" + path);

        Message msg = Message.obtain();
        msg.what = UI_PULL_VCARD_LISTING;
        msg.arg1 = (success) ? 1 : 0;
        msg.obj = path;
        viewUpdateHandler.sendMessage(msg);

        Toast.makeText(getApplicationContext(), status + path, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPullVcardEntryEvent(BluetoothDevice device, String name, boolean success,
            String outputFilepath) {

        String status;
        if (true == success)
            status = "Pull vcard entry Success";
        else
            status = "Pull vcard entry Failure";

        Log.d(TAG, "onPullVcardEntryEvent name:" + name);

        Message msg = Message.obtain();
        msg.what = UI_PULL_VCARD_ENTRY;
        msg.arg1 = (success) ? 1 : 0;
        msg.obj = mServerPath;
        viewUpdateHandler.sendMessage(msg);

        Toast.makeText(getApplicationContext(), status + name, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthenticationChallenge(BluetoothDevice device, String description,
            boolean isUserIdRequired, boolean isFullAccess) {
        Log.d(TAG, "onAuthenticationChallenge()");
        addAuthNotification(PbapAuthDialog.ACTION_AUTH_CHAL, device, description, isFullAccess,
                isUserIdRequired, null);
    }

    @Override
    public void onAuthentication(BluetoothDevice device, String username) {
        Log.d(TAG, "onAuthentication()");
        addAuthNotification(PbapAuthDialog.ACTION_AUTH, device, null, false, false, username);
    }

    private void onAuthTest(Intent intent) {
        Log.d(TAG, "onAuthTest()");
        String bdaddr = intent.getStringExtra(EXTRA_SERVER_BDADDR);
        if (bdaddr == null) {
            Log.e(TAG, "onAuthTest(): Invalid bdaddr: " + bdaddr);
            return;
        }
        bdaddr = bdaddr.toUpperCase();
        BluetoothDevice pbapServer = null;
        try {
            pbapServer = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdaddr);
        } catch (Throwable t) {
            Log.e(TAG, "onAuthTest(): unable to create BluetoothDevice with address: " + bdaddr, t);
            return;
        }
        onAuthenticationChallenge(pbapServer, "blab", true, true);
    }

    @SuppressWarnings("deprecation")
    private void addAuthNotification(String action, BluetoothDevice remoteDevice,
            String description, boolean isFullAccess, boolean isUserIdRequired, String username) {
        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(this, PbapAuthDialog.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        clickIntent.setAction(action);
        clickIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice);
        clickIntent.putExtra(PbapAuthDialog.EXTRA_DESCRIPTION, description);
        clickIntent.putExtra(PbapAuthDialog.EXTRA_FULLACCESS, isFullAccess);
        clickIntent.putExtra(PbapAuthDialog.EXTRA_USERID_REQUIRED, isUserIdRequired);
        clickIntent.putExtra(PbapAuthDialog.EXTRA_USERNAME, username);

        // Create an intent triggered by clicking on the
        // "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(this, getClass());
        deleteIntent.setAction(PbapAuthDialog.ACTION_CANCEL_AUTH);
        deleteIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice);

        String notificationMessage = null;
        if (isUserIdRequired && username == null) {
            notificationMessage = getString(R.string.auth_notif_message_with_username,
                    remoteDevice.getName());
        } else {
            notificationMessage = getString(R.string.auth_notif_message, remoteDevice.getName());
        }
        Notification notification = new Notification(android.R.drawable.stat_sys_data_bluetooth,
                getString(R.string.auth_notif_ticker), System.currentTimeMillis());
        notification.setLatestEventInfo(this, getString(R.string.auth_notif_title),
                notificationMessage, PendingIntent.getActivity(this, 0, clickIntent, 0));

        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        notification.defaults = Notification.DEFAULT_SOUND;
        notification.deleteIntent = PendingIntent.getBroadcast(this, 0, deleteIntent, 0);
        nm.notify(NOTIFICATION_ID_AUTH, notification);

    }

    private void removeAuthNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID_AUTH);
    }

    private void onAuthResponse(int authType, BluetoothDevice remoteDevice,
            boolean isUserIdRequired, String userName, String pass) {
        Log.d(TAG, "onAuthResponse()");
        BluetoothPbapClient client = getClient(remoteDevice.getAddress());
        if (client != null) {
            client.setAuthenticationChallengeResult(userName, pass);
        }

    }

    private void onCancelAuth(int authType, BluetoothDevice remoteDevice) {
        Log.d(TAG, "onCancelAuth()");
        removeAuthNotification();
        BluetoothPbapClient client = getClient(remoteDevice.getAddress());
        if (client != null) {
            client.cancelAuthentication();
        }
    }

    @Override
    public void onAuthenticationChallengeTimeout(BluetoothDevice device) {
        Log.d(TAG, "onAuthenticationChallengeTimeout()");

        removeAuthNotification();
    }

    @Override
    public void onAuthenticationTimeout(BluetoothDevice device) {
        Log.d(TAG, "onAuthenticationTimeout()");
        removeAuthNotification();

    }

}
