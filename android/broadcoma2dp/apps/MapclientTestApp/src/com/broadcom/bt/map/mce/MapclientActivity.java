/******************************************************************************
 *
 *  Copyright (C) 2009-2014 Broadcom Corporation
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/

package com.broadcom.bt.map.mce;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.app.Activity;
import android.app.Notification;
import android.app.Notification.BigTextStyle;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.RingtoneManager;
import android.net.ParseException;
import android.net.Uri;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothProfile;

import com.broadcom.bt.map.BluetoothMapClient;
import com.broadcom.bt.map.BluetoothFolderInfo;
import com.broadcom.bt.map.IBluetoothMapClientEventHandler;
import com.broadcom.bt.map.BluetoothMessageInfo;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMseInfo;
import com.broadcom.bt.util.bmsg.*;



public class MapclientActivity extends Activity implements ServiceListener, OnClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "MapclientActivity";

    private BluetoothAdapter mBluetoothAdapter;
    public static BluetoothMapClient mBluetoothMapClient;
    private String serverAddress;
    private BluetoothDevice mServer;
    public static int mceState = MapclientTestAppConstants.MCE_STATE_UNKNOWN;
    private int msgCount;
    private static Uri notificationMsgUri;

    // preferences from settings menu
    int mseInstance = -1;
    String mseFolderPath;
    Boolean mseNotificationEnabled;

    BMessage mBMessage;

    // message list for SMS
    public static List<BluetoothMessageInfo> messageInfoList;
    public static HashMap<String, BluetoothMessageInfo> messageMap = new HashMap<String, BluetoothMessageInfo>();

    // mse instance list
    public static BluetoothMseInfo[] mseInfo;
    private String[] mseInstanceNames;
    private int[] mseInstanceIds;
    // selected mse instance index
    private int selectedItem = -1;

    // layout in a view to display set Default Sms app option
    private RelativeLayout mSetDefaultSmsLayout;

    // textview to display current preferences
    TextView tvServerName;
    TextView tvMSEInstanceName;
    TextView tvMSEFolderPath;

    // buttons for each operation
    Button btnSelectBTServer;
    Button btnConnectToServer;
    Button btnDisconnectFromServer;
    Button btnPushMsg;
    Button btnGetFolderPath;
    Button btnGetMsgList;
    Button btnUpdateInbox;
    Button btnExit;


    ProgressDialog mProgressDialog;
    SharedPreferences prefs;
    String defaultSmsApp = "com.broadcom.bt.map.mce";


    // UI event states for MapclientActivity
    private static final int UI_INIT_COMPLETE = 0;
    private static final int UI_SELECT_SERVER = 1;
    private static final int UI_SELECTED_SERVER = 2;
    private static final int UI_CONNECTED_TO_SERVER = 3;
    private static final int UI_UNABLE_TO_CONNECT_TO_SERVER = 4;
    private static final int UI_DISCONNECTED_FROM_SERVER = 5;
    private static final int UI_GET_MESSAGE_COUNT = 6;
    private static final int UI_SHOW_MESSAGE_LIST = 7;
    private static final int UI_ALL_MESSAGES_RECEIVED = 8;
    private static final int UI_MESSAGE_SEND_SUCCESS = 9;
    private static final int UI_MESSAGE_SEND_FAILED = 10;
    private static final int UI_FOLDER_PATH_SET = 11;
    private static final int UI_NOTIFICATION_ENABLED = 12;
    private static final int UI_NOTIFICATION_DISABLED = 13;
    private static final int UI_SHOW_NOTIFICATION = 14;
    private static final int UI_TIMEOUT = 15;
    private static final int UI_MSE_INSTANCES_FETCH_COMPLETE = 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapclient);

        Log.d(TAG, "onCreate");
        changeState(MapclientTestAppConstants.MCE_STATE_UNKNOWN);
        // Bind all UI components to variables

        /******* Default SMS App check *******/
        mSetDefaultSmsLayout = (RelativeLayout)findViewById(R.id.set_default_sms_layout);

        tvServerName = (TextView) findViewById(R.id.deviceName);
        tvServerName.setText("");

        tvMSEInstanceName = (TextView) findViewById(R.id.mceInstanceName);
        tvMSEFolderPath = (TextView) findViewById(R.id.mceFolderPath);

        btnSelectBTServer = (Button) findViewById(R.id.btSelectedDevice);
        btnSelectBTServer.setOnClickListener((OnClickListener) this);

        btnConnectToServer = (Button) findViewById(R.id.btConnectedToDevice);
        btnConnectToServer.setOnClickListener((OnClickListener) this);
        btnConnectToServer.setEnabled(false);

        btnDisconnectFromServer = (Button) findViewById(R.id.btDisconnectFromServer);
        btnDisconnectFromServer.setOnClickListener((OnClickListener) this);
        btnDisconnectFromServer.setEnabled(false);

        btnPushMsg = (Button) findViewById(R.id.pushMsg);
        btnPushMsg.setOnClickListener((OnClickListener) this);

        btnGetFolderPath = (Button) findViewById(R.id.getFolderPath);
        btnGetFolderPath.setOnClickListener((OnClickListener) this);

        btnGetMsgList = (Button) findViewById(R.id.getMsgList);
        btnGetMsgList.setOnClickListener((OnClickListener) this);

        btnUpdateInbox = (Button) findViewById(R.id.updateInbox);
        btnUpdateInbox.setOnClickListener((OnClickListener) this);

        btnExit = (Button) findViewById(R.id.exit);
        btnExit.setOnClickListener((OnClickListener) this);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Device does not support bluetooth");
            Toast.makeText(getApplicationContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
            finish();
        }
        isBTEnabled();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        mProgressDialog = new ProgressDialog(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // set the mse parameters
        mseInstance = Integer.parseInt((prefs.getString("mseInstance", Integer.toString(MapclientTestAppConstants.MCE_INSTANCE_SMS))));
        mseFolderPath = prefs.getString("mseFolderPath", MapclientTestAppConstants.MCE_DEFAULT_FOLDER_PATH);
        mseNotificationEnabled = prefs.getBoolean("mceNotificationEnabled", false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_mapclient, menu);
        Log.d(TAG, "onCreateOptionsMenu");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch(item.getItemId()) {
        case R.id.menu_settings:
            Log.d(TAG, "clicked on application settings");
            startActivity(new Intent(this, PrefsActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        releaseResources();
    }

    @Override
    protected void onRestart() {
        // TODO Auto-generated method stub
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        Log.d(TAG, "onResume");
        // check if bluetooth is ON
        if (!mBluetoothAdapter.isEnabled()) {
            finish();
        } else {
            checkForDefaultSmsApp();
            updateUI();
        }
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if(key.equals("mseInstance")) {
            mseInstance = Integer.parseInt((prefs.getString("mseInstance", Integer.toString(MapclientTestAppConstants.MCE_INSTANCE_SMS))));
        } else if(key.equals("mseFolderPath")){
            mseFolderPath = prefs.getString("mseFolderPath", MapclientTestAppConstants.MCE_DEFAULT_FOLDER_PATH);
            // if state is connected, set the folderpath
            if(MapclientActivity.mceState == MapclientTestAppConstants.MCE_STATE_CONNECTED
                    && MapclientActivity.mBluetoothMapClient.setFolderPath(mseInstance, mseFolderPath))
                Log.d(TAG, "setFolderPath to: " + mseFolderPath);
            else
                Log.d(TAG, "Not connected. Unable to setFolderPath");
        } else if(key.equals("mceNotificationEnabled")){
            mseNotificationEnabled = prefs.getBoolean("mceNotificationEnabled", false);
            if(mseNotificationEnabled) {
                Log.d(TAG, "Request to enable notification");
                MapclientActivity.mBluetoothMapClient.startNotificationServer("MapNotificationServer");
                MapclientActivity.mBluetoothMapClient.registerForNotification(mseInstance);
            }
            else {
                Log.d(TAG, "Request to disable notification");
                MapclientActivity.mBluetoothMapClient.unregisterForNotification(mseInstance);
                MapclientActivity.mBluetoothMapClient.stopNotificationServer();
            }
        }
    }


    private class MapClientServiceEventHandler implements IBluetoothMapClientEventHandler {

        public MapClientServiceEventHandler() {}

        @Override
        public void onNotificationServerStarted(boolean success) {
            Log.d(TAG, "onNotificationServerStarted(): success=" + success);
        }

        @Override
        public void onNotificationServerStopped(boolean success) {
            Log.d(TAG, "onNotificationServerStopped(): success=" + success);
        }

        @Override
        public void onServerConnected(BluetoothDevice server, int instanceId, boolean success) {
            Log.d(TAG, "onServerConnected() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success);
            if(success == true) {
                mServer = server;
                mseInstance = instanceId;

                Message msg = Message.obtain();
                msg.what = UI_CONNECTED_TO_SERVER;
                viewUpdateHandler.sendMessage(msg);
            }
            else {
                Message msg = Message.obtain();
                msg.what = UI_DISCONNECTED_FROM_SERVER;
                viewUpdateHandler.sendMessage(msg);
            }

        }

        @Override
        public void onServerDisconnected(BluetoothDevice server, int instanceId, boolean success) {
            Log.d(TAG, "onServerDisconnected() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success);
            if(success == true) {
                Message msg = Message.obtain();
                msg.what = UI_DISCONNECTED_FROM_SERVER;
                viewUpdateHandler.sendMessage(msg);
            }
            else {
//                Toast.makeText(this, "Unable to connect to: " + server.getName(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onFolderListResult(BluetoothDevice server, int instanceId, boolean success,
                int listLength, Uri contentUri) {
            Log.d(TAG, "onFolderListResult() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success + ", listLength=" + listLength + ", contentUri="
                    + contentUri);
            if (contentUri != null) {
                InputStream is = null;
                try {
                    is = MapclientActivity.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to open Folder List", t);
                }
            } else {
                Log.w(TAG, "No Folder List!");
            }
        }

        @Override
        public void onFolderListResult(BluetoothDevice server, int instanceId, boolean success,
                int listLength, BluetoothFolderInfo[] folderInfo) {
            Log.d(TAG, "onFolderListResult() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success + ", listLength=" + listLength + ", folderInfo="
                    + folderInfo);
            Log.d(TAG, "================= Folder List ================");
            if (folderInfo != null) {
                for (int i = 0; i < folderInfo.length; i++) {
                    Log.d(TAG, folderInfo[i].mVirtualName);
                    Log.d(TAG, folderInfo[i].mFolderName);
                }
            }
            Log.d(TAG, "==============================================");
        }

        @Override
        public void onFolderPathSet(BluetoothDevice server, int instanceId, boolean success,
                String folderPath) {
            // TODO Auto-generated method stub
            Message msg = Message.obtain();
            msg.what = UI_FOLDER_PATH_SET;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onGetMseInstancesResult(BluetoothDevice server, BluetoothMseInfo[] mseInfo) {
            int length = (mseInfo == null ? 0 : mseInfo.length);
            Log.d(TAG, "onGetMseInstancesResult(): length= " + length);
            mseInstanceNames = new String[length];
            mseInstanceIds = new int[length];
            for (int i = 0; i < length; i++) {
                BluetoothMseInfo m = mseInfo[i];
                mseInstanceNames[i] = m.mName;
                mseInstanceIds[i] = m.mServerInstanceId;
                Log.d(TAG, "BluetoothMseInfo #" + i + ": serverInstanceId=" + m.mServerInstanceId
                        + ", messageTypes=" + m.mMessageTypes + ", name=" + m.mName);
                Log.d(TAG, "Supports SMS?      " + m.supportsSmsMessages());
                Log.d(TAG, "Supports SMS GSM ? " + m.supportsSmsGsmMessages());
                Log.d(TAG, "Supports SMS CMDA? " + m.supportsSmsCdmaMessages());
                Log.d(TAG, "Supports MMS?      " + m.supportsMmsMessages());
                Log.d(TAG, "Supports EMAIL?    " + m.supportsEmailMessages());
            }
            MapclientActivity.mseInfo = mseInfo;

            // send init complete msg to ui event handler
            Message msg = Message.obtain();
            msg.what = UI_MSE_INSTANCES_FETCH_COMPLETE;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onMessageListResult(BluetoothDevice server, int instanceId, boolean success,
                int listLength, boolean hasNewMessages, Uri contentUri) {
            Log.d(TAG, "onMessageListResult() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success + ", listLength=" + listLength + "hasNewMessages="
                    + hasNewMessages + ", contentUri=" + contentUri);
            if (contentUri != null) {
                InputStream is = null;
                try {
                    is = MapclientActivity.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to open Message List", t);
                }
            } else {
                Log.w(TAG, "No Message List!");
            }
        }

        @Override
        public void onGetMessageResult(BluetoothDevice server, int serverInstanceId,
                String messageHandle, boolean success, Uri contentUri) {
            Log.d(TAG, "onGetMessageResult() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", success=" + success + ", messageHandle="
                    + messageHandle + ", contentUri=" + contentUri);
            if (contentUri != null) {
                InputStream is = null;
                try {
                    is = MapclientActivity.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    String folderpath = "";
                    String name = "";
                    String tel = "";
                    String msgBody = "";
                    String status = "";
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                        StringTokenizer strTokenizer = new StringTokenizer(lineRead, ":");
                        // parse each field
                        if(lineRead.contains("TEL")) {
                            strTokenizer.nextElement();
                            if(strTokenizer.hasMoreElements())
                                tel = strTokenizer.nextElement().toString();
                            Log.d(TAG, "onGetMessageAsFile tel:" + tel);
                        } else if(lineRead.contains("FOLDER")) {
                            strTokenizer.nextToken();
                            if(strTokenizer.hasMoreElements())
                                folderpath = strTokenizer.nextToken();
                            Log.d(TAG, "onGetMessageAsFile folderpath:" + folderpath);
                        } else if(lineRead.contains("FN")) {
                            strTokenizer.nextToken();
                            if(strTokenizer.hasMoreElements())
                                name = strTokenizer.nextToken();
                            Log.d(TAG, "onGetMessageAsFile name:" + name);
                        } else if(lineRead.contains("BEGIN:MSG")) {
                            lineRead = r.readLine();
                            while(!lineRead.contains("END:MSG")) {
                                msgBody += lineRead;
                                lineRead = r.readLine();
                            }
                            Log.d(TAG, "onGetMessageAsFile msgbody:" + msgBody);
                        } else if(lineRead.contains("STATUS")) {
                            strTokenizer.nextToken();
                            if(strTokenizer.hasMoreElements())
                                status = strTokenizer.nextToken();
                            Log.d(TAG, "onGetMessageAsFile status:" + status);
                        }
                    }
                    writeMsgToContentProvider(folderpath, name, tel, name, tel, msgBody, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to open BMessage", t);
                }
            } else {
                Log.w(TAG, "No BMessage!");
            }
        }

        @Override
        public void onGetMessageResult(BluetoothDevice server, int serverInstanceId,
                String messageHandle, boolean success, BMessage bMessage) {
            Log.d(TAG, "onGetMessageResultAsObject() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", success=" + success + ", messageHandle="
                    + messageHandle);
                if(success) {
                    BMessageEnvelope mBMessageEnvelope = bMessage.getEnvelope();
                    BMessageBody mBMessageBody = mBMessageEnvelope.getBody();
                    BMessageVCard mBMessageVCard = mBMessageEnvelope.getRecipient();
                    BMessageBodyContent mBMessageBodyContent = mBMessageBody.getContent();
                    BMessageVCardProperty mBMessageVCardPropertyName = (mBMessageVCard != null) ?
                        mBMessageVCard.getProperty(BMessageConstants.BTA_MA_VCARD_PROP_N) : null;
                    BMessageVCardProperty mBMessageVCardPropertyTel = (mBMessageVCard != null) ?
                        mBMessageVCard.getProperty(BMessageConstants.BTA_MA_VCARD_PROP_TEL) : null;

                    Log.d(TAG, "Message type: " + bMessage.getMessageType() + "\n"+
                            " folderpath: " + bMessage.getFolder() + "\n"+
                            "Content: " + mBMessageBodyContent.getFirstMessageContent() + "\n");

//                    String contentUri;
                    BluetoothMessageInfo messageInfo = MapclientActivity.messageMap.get(messageHandle);
                    if(messageInfo == null) {
                        Log.d(TAG, "no msg handle stored in map structure");
                        return;
                    }
                    ContentValues values = new ContentValues();
                    values.put("body", mBMessageBodyContent.getFirstMessageContent());

                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmSS");
                        Date mdate = sdf.parse(messageInfo.mDateTime.replaceFirst("T", ""));
                        Log.d(TAG, "onGetMessageResultAsObject() mDateTime " + mdate.getTime());
                        values.put("date", mdate.getTime());
                    } catch (Exception e) {
                        Log.d(TAG, e.getMessage());
                        Log.d(TAG, "onGetMessageResultAsObject() mDateTime " + messageInfo.mDateTime);
                        values.put("date", messageInfo.mDateTime);
                    }

                    values.put("subject", messageInfo.getSubject(false));
                    values.put("read", messageInfo.mIsRead);

                    String folderpath = "/" +  bMessage.getFolder();
                    Log.d(TAG, "Inserting msg to folder: " + folderpath);
                    String recipients;
                    if(folderpath.equals("/telecom/msg/inbox")) {
                        values.put("type", 1);
                        values.put("address", messageInfo.getSenderAddress(false));//sender address
                        recipients = messageInfo.getSenderAddress(false);
                        values.put("thread_id", getMessageThreadId(Arrays.asList(recipients.split(","))));
                        values.put("person", messageInfo.getSenderDisplayName(false));
                        getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
                    } else if(folderpath.equals("/telecom/msg/outbox")) {
                        getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
                    } else if(folderpath.equals("/telecom/msg/sent")) {
                        values.put("type", 2);
                        values.put("address", messageInfo.getRecipientAddress(false));//recipient address
                        recipients = messageInfo.getRecipientAddress(false);
                        values.put("thread_id", getMessageThreadId(Arrays.asList(recipients.split(","))));
                        values.put("person", messageInfo.getRecipientDisplayName(false));
                        Log.d(TAG, "sent msg recipient address: " + messageInfo.getRecipientAddress(false));
                        getContentResolver().insert(Uri.parse("content://sms/sent"), values);
                    } else if(folderpath.equals("/telecom/msg/deleted")) {
                        getContentResolver().insert(Uri.parse("content://sms/deleted"), values);
                    } else if(folderpath.equals("/telecom/msg/draft")) {
                        values.put("type", 3);
                        values.put("address", messageInfo.getRecipientAddress(false));//recipient address
                        recipients = messageInfo.getRecipientAddress(false);
                        values.put("thread_id", getMessageThreadId(Arrays.asList(recipients.split(","))));
                        values.put("person", messageInfo.getRecipientDisplayName(false));
                        Log.d(TAG, "draft msg recipient address: " + messageInfo.getRecipientAddress(false));
                        getContentResolver().insert(Uri.parse("content://sms/draft"), values);
                    }

                    // decrement msg count
                    msgCount--;
                    if(msgCount <= 0) {
                        mProgressDialog.dismiss();
                        Message msg = Message.obtain();
                        msg.what = UI_ALL_MESSAGES_RECEIVED;
                        viewUpdateHandler.sendMessage(msg);
                    }
                } else {
                    Log.d(TAG, "Failed to receive BMessage");
                }
        }

        public void writeMsgToContentProvider(String folderpath, String senderAddress, String recipientAddress, String senderPersonName,
                String recipientPersonName, String msgBody, String status) {
            ContentValues values = new ContentValues();
            values.put("body", msgBody);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            values.put("date", sdf.format(new Date()));
            values.put("subject", msgBody);
            values.put("read", status);
            if(folderpath.equals("telecom/msg/inbox")) {
                values.put("type", 1);
                values.put("address", senderAddress);//sender address
                values.put("thread_id", getMessageThreadId(Arrays.asList(senderAddress.split(","))));
                values.put("person", senderPersonName);
                getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
            } else if(folderpath.equals("telecom/msg/outbox")) {
                getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
            } else if(folderpath.equals("telecom/msg/sent")) {
                values.put("type", 2);
                values.put("address", recipientAddress);//recipient address
                values.put("thread_id", getMessageThreadId(Arrays.asList(recipientAddress.split(","))));
                values.put("person", recipientPersonName);
                Log.d(TAG, "sent msg recipient address: " + recipientAddress);
                getContentResolver().insert(Uri.parse("content://sms/sent"), values);
            } else if(folderpath.equals("telecom/msg/deleted")) {
                getContentResolver().insert(Uri.parse("content://sms/deleted"), values);
            } else if(folderpath.equals("telecom/msg/draft")) {
                values.put("type", 3);
                values.put("address", recipientAddress);//recipient address
                values.put("thread_id", getMessageThreadId(Arrays.asList(recipientAddress.split(","))));
                values.put("person", recipientPersonName);
                Log.d(TAG, "draft msg recipient address: " + recipientAddress);
                getContentResolver().insert(Uri.parse("content://sms/draft"), values);
            } else {
                Log.d(TAG, "writeMsgToContentProvider: unknown folderpath");
            }
        }

        public Long getMessageThreadId(List<String> recipients) {
            Uri threadIdUri = Uri.parse("content://mms-sms/threadID");
            Uri.Builder builder = threadIdUri.buildUpon();
            for(String recipient : recipients){
                builder.appendQueryParameter("recipient", recipient);
            }
            Uri uri = builder.build();
            Long threadId = (long) 0;
            Cursor cursor = getContentResolver().query(uri, new String[]{"_id"}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        threadId = cursor.getLong(0);
                        Log.d(TAG, "Threadid " + threadId);
                        }
                } finally {
                        cursor.close();
                }
            }
            return threadId;
        }

        @Override
        public void onUpdateInboxResult(BluetoothDevice server, int serverInstanceId,
                boolean success) {
            Log.d(TAG, "onUpdateInboxResult " + "serverInstanceId = " + serverInstanceId + " success = " + success);
        }

        @Override
        public void onNotificationRegistrationStateChange(BluetoothDevice server, int instanceId,
                boolean isRegistration, boolean success) {
                Log.d(TAG, "isNotificationRegistered = " + isRegistration + " success = " + success);

                Message msg = Message.obtain();
                if (success) {
                    if (isRegistration)
                        msg.what = UI_NOTIFICATION_ENABLED;
                    else
                        msg.what = UI_NOTIFICATION_DISABLED;
                    viewUpdateHandler.sendMessage(msg);
                }
        }

        public void onMessageStatusUpdated(BluetoothDevice server, int serverInstanceId,
                int statusType, String messageHandle, boolean success) {

        }

        public void onPushMessageResult(BluetoothDevice server, int serverInstanceId,
                boolean success, String messageHandle) {
            Log.d(TAG, "onPushMessageResult() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", success=" + success + ", messageHandle="
                    + messageHandle);
            Message msg = Message.obtain();
            if(success)
                msg.what = UI_MESSAGE_SEND_SUCCESS;
            else
                msg.what = UI_MESSAGE_SEND_FAILED;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onMessageListResult(BluetoothDevice server, int instanceId, boolean success,
                int listLength, boolean hasNewMessages, BluetoothMessageInfo[] messageInfo) {
            Log.d(TAG, "onMessageListResult() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success + ", listLength=" + listLength + ", hasNewMessages="
                    + hasNewMessages + ", messageInfo=" + messageInfo);
            if(success == true) {
                Log.d(TAG, "============================= Message List ==============================");
                if (messageInfo != null) {
                    HashMap<String, BluetoothMessageInfo> messageMap = MapclientActivity.messageMap;
                    messageMap.clear();
//                    List<BluetoothMessageInfo> messageInfoList = new ArrayList<BluetoothMessageInfo>();
                    MapclientActivity.messageInfoList = new ArrayList<BluetoothMessageInfo>();
                    for (int i = 0; i < messageInfo.length; i++) {
//                        messageMap.put(messageInfo[i].mMsgHandle, messageInfo[i]);
                        messageMap.put(String.format("%16s", messageInfo[i].mMsgHandle).replace(' ', '0'), messageInfo[i]);
                        MapclientActivity.messageInfoList.add(messageInfo[i]);
                        Log.d(TAG, "Message handle=" + messageInfo[i].mMsgHandle +
                                ", subject=" + messageInfo[i].getSubject(false) +
                                ", time=" + messageInfo[i].mDateTime);
                        Log.d(TAG, "        sender=" + messageInfo[i].getSenderDisplayName(false) +
                                ", sender address=" + messageInfo[i].getSenderAddress(false) +
                                ", recipient=" + messageInfo[i].getRecipientDisplayName(false) +
                                ", recipient address=" + messageInfo[i].getRecipientAddress(false));
                        Log.d(TAG, "        type=" + messageInfo[i].mMsgType +
                                ", size=" + messageInfo[i].mMsgSize +
                                ", is read=" + messageInfo[i].mIsRead);
                    }
                    msgCount = messageMap.size();
                    if(MapclientActivity.notificationMsgUri != null)
                         sendNotification(MapclientActivity.notificationMsgUri);
                        else {
                               Message msg = Message.obtain();
                               msg.what = UI_SHOW_MESSAGE_LIST;
                               viewUpdateHandler.sendMessage(msg);
                    }
                }
                Log.d(TAG, "=========================================================================");
            }
        }

        @Override
        public void onNotification(BluetoothDevice server, int serverInstanceId, Uri contentUri) {
            Log.d(TAG, "onNotification() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", contentUri=" + contentUri);
            if (contentUri != null) {
                InputStream is = null;
                try {
                    is = MapclientActivity.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to open Notification", t);
                }
                Message msg = Message.obtain();
                msg.what = UI_SHOW_NOTIFICATION;
                Bundle b = new Bundle();
                b.putParcelable("contentUri", contentUri);
                msg.setData(b);
                viewUpdateHandler.sendMessage(msg);
            }
        }

    }


    @Override
    public void onServiceDisconnected(int profile) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onServiceDisconnected");
        changeState(MapclientTestAppConstants.MCE_STATE_DISCONNECTED);
        Toast.makeText(this, "Bluetooth MAP server disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        Log.d(TAG, "onServiceConnected");
        if (proxy != null) {
//            mBluetoothMapClient = (BluetoothMapClient) proxy;
//            MapClientServiceEventHandler cb = new MapClientServiceEventHandler();
//            mBluetoothMapClient.registerEventHandler(cb);
            MapclientActivity.mBluetoothMapClient = (BluetoothMapClient) proxy;
            MapClientServiceEventHandler cb = new MapClientServiceEventHandler();
            MapclientActivity.mBluetoothMapClient.registerEventHandler(cb);
            Log.d(TAG, "Registered BluetoothMapClient Event handler");
            changeState(MapclientTestAppConstants.MCE_STATE_INIT);
        }
        else {
            Toast.makeText(this, "Unable to init MAP client", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "Result code: " + Integer.toString(resultCode));
        switch (requestCode) {
        case MapclientTestAppConstants.REQCODE_BLUETOOTH_RESULT:
            Log.i(TAG, "requestCode = REQCODE_BLUETOOTH_RESULT");

            if(resultCode == RESULT_OK) {
                Log.i(TAG, "RESULT_OK");

                // Retrieve the Info
                Bundle extras = data.getExtras();

                if(extras != null) {
                    serverAddress = extras
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Log.d(TAG, "serverAddress: " + serverAddress);
                    btnConnectToServer.setEnabled(true);
                    tvServerName.setText("Selected Device : " + serverAddress);
                    Toast.makeText(this, "Selected Device: " + serverAddress, Toast.LENGTH_SHORT).show();
                    btnDisconnectFromServer.setEnabled(true);

                    try {
                        mServer = mBluetoothAdapter.getRemoteDevice(serverAddress);
                    } catch(Throwable t) {
                        Log.e(TAG, "Error: getting bluetooth service device object failed");
                        return;
                    }
                    if(mServer == null) {
                        Log.e(TAG, "Unable to fetch Server BluetoothDevice.");
                        return;
                    }
                    mProgressDialog.setMessage("Setting Target Server...");
                    // connect to selected MAP server
                    if (!MapclientActivity.mBluetoothMapClient.setServer(mServer)) {
                        displayMsg("Error: Unable to connect to MAP server");
                        Log.e(TAG, "Unable to connect to MAP server");
                        return;
                    }

//                    Message msg = Message.obtain();
//                    msg.what = UI_INIT_COMPLETE;
//                    viewUpdateHandler.sendMessage(msg);
                }
            }
            else {
                Log.e(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
                Toast.makeText(this, "Failed(" + resultCode +")", Toast.LENGTH_SHORT).show();
            }

            break;
        case MapclientTestAppConstants.REQCODE_PUSH_MESSAGE:
            Log.i(TAG, "requestCode = REQCODE_PUSH_MESSAGE");
            if(resultCode == RESULT_OK) {
             // Retrieve the Info
                Bundle extras = data.getExtras();
                if(extras != null) {
                    String address = extras
                            .getString(CreateSMSActivity.EXTRAS_RECEIPIENT);
                    String msgbody = extras
                            .getString(CreateSMSActivity.EXTRAS_MSGBODY);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                    String currentDateandTime = sdf.format(new Date());
                    Log.d(TAG, "push msg address = " + address);
                    Log.d(TAG, "push msg body = " + msgbody);
                    mProgressDialog.setMessage("Sending Message...");
                    mProgressDialog.show();
                    try {
                        File pushFile = createSMS(address, BluetoothMapClient.CHARSET_UTF8, msgbody, 0);
                        Boolean pushMsgSuccess = MapclientActivity.mBluetoothMapClient
                                .pushMessage(mseInstance, pushFile, BluetoothMapClient.CHARSET_UTF8,
                                    true, true);
                        if(!pushMsgSuccess)
                            displayMsg("Push Message initiation failed");
                    } catch (Exception e) {
                        mProgressDialog.dismiss();
                        displayMsg("Send Message failed");
                    }
                }
            }
            break;
        default:
            Log.e(TAG, "requestCode UNKNOWN!");
            break;
        }
    }

    private void pickBTDevice() {
        Intent btDevicePickerIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(btDevicePickerIntent, MapclientTestAppConstants.REQCODE_BLUETOOTH_RESULT);
    }

    private void init() {
        MapclientActivity.mBluetoothMapClient = BluetoothMapClient.getProxy(this, this);
        Log.d(TAG, "Init complete");
    }

    private void fetchMseInstances() {
        // get MSE Instances
        if(!MapclientActivity.mBluetoothMapClient.getMseInstances()) {
            Log.d(TAG, "Unable to fetch MSE Instances");
            changeState(MapclientTestAppConstants.MCE_STATE_ERROR);
            displayMsg("Error: Unable to fetch MSE Instances");
            return;
        }
        viewUpdateHandler.sendEmptyMessageDelayed(UI_TIMEOUT, MapclientTestAppConstants.MSE_CALLBACK_TIMEOUT);
    }

    private void selectMseInstance() {
        // Display dialog to select MSE Instance to connect
        selectedItem = -1;
        AlertDialog.Builder dialogMseSelect = new AlertDialog.Builder(this);
        dialogMseSelect.setTitle("Select MSE Instance");
        dialogMseSelect.setSingleChoiceItems(mseInstanceNames , -1,
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int item) {
                selectedItem = item;
                Log.d(TAG, "selectedItem: " + selectedItem);
                }
        });
        dialogMseSelect.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    connect();
                }
        });
        dialogMseSelect.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selectedItem = -1;
                }
        });

        AlertDialog alert = dialogMseSelect.create();
        alert.show();
    }

    private void connect() {
        if(selectedItem != -1) {
            mseInstance = mseInstanceIds[selectedItem];

            mProgressDialog.setMessage("Connecting to MSE Instance...");
            mProgressDialog.show();
            Log.d(TAG, "Connecting to server: " + mServer.getName() + ", instanceId:" +
                    mseInstance);

            viewUpdateHandler.sendEmptyMessageDelayed(UI_TIMEOUT, MapclientTestAppConstants.MSE_CALLBACK_TIMEOUT);

            try {
                // check if selected MSE instance supports SMS
                for(int i=0; i < mseInfo.length; i++) {
                    if (MapclientActivity.mseInfo[i].mServerInstanceId == mseInstance) {
                        if (MapclientActivity.mseInfo[i].supportsSmsMessages() && MapclientActivity.mseInfo[i].supportsSmsGsmMessages())
                            break;
                        else {
                            mProgressDialog.dismiss();
                            displayMsg("selected MSE instance does not support SMS");
                            return;
                        }
                    }
                }
            } catch (Exception e1) {
                displayMsg("Unable to connect to server. MSE Instance not set");
                return;

            }

            try {
                if (!MapclientActivity.mBluetoothMapClient.connect(mseInstance)) {
                    Log.e(TAG, "Unable to connect to MAP server");
                    mProgressDialog.dismiss();
                    Toast.makeText(this, "Unable to connect to MAP server, mseInstance: " +
                            mseInstance, Toast.LENGTH_SHORT).show();
                    mProgressDialog.dismiss();
                    return;
                }
                mseNotificationEnabled = prefs.getBoolean("mceNotificationEnabled", false);
                if(mseNotificationEnabled) {
                    Log.d(TAG, "Request to enable notification");
                    MapclientActivity.mBluetoothMapClient.startNotificationServer("MapNotificationServer");
                    MapclientActivity.mBluetoothMapClient.registerForNotification(mseInstance);
                }
                else {
                    Log.d(TAG, "Request to disable notification");
                    MapclientActivity.mBluetoothMapClient.unregisterForNotification(mseInstance);
                    MapclientActivity.mBluetoothMapClient.stopNotificationServer();
                }
                tvMSEInstanceName.setText("MSE Instance name: " + mseInstanceNames[selectedItem]);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                mProgressDialog.dismiss();
                Toast.makeText(this, "Error in connecting to server.", Toast.LENGTH_SHORT).show();
                changeState(MapclientTestAppConstants.MCE_STATE_INIT);
            }
        }

    }

    private void disconnect() {
        mProgressDialog.setMessage("Disconnecting from MSE Instance...");
        mProgressDialog.show();
        Log.d(TAG, "Disconnecting from server: " + mServer.getName() + ", instanceId:" +
                mseInstance);

        try {
            if(MapclientActivity.mceState == MapclientTestAppConstants.MCE_STATE_CONNECTED) {
                MapclientActivity.mBluetoothMapClient.disconnect(mseInstance);
                viewUpdateHandler.sendEmptyMessageDelayed(UI_TIMEOUT, MapclientTestAppConstants.MSE_CALLBACK_TIMEOUT);
            }
            else
                mProgressDialog.dismiss();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            mProgressDialog.dismiss();
            Toast.makeText(this, "Error in disconnecting from server.", Toast.LENGTH_SHORT).show();
            changeState(MapclientTestAppConstants.MCE_STATE_ERROR);
        }
    }

    private void isBTEnabled() {
     // check if bluetooth is ON
        if(mBluetoothAdapter.isEnabled()) {
            // Initialize bluetooth map client
            init();
        }
        else {
            Toast.makeText(getApplicationContext(), "Turn ON Bluetooth", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void displayMsg(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void updateUI() {
        Log.d(TAG, "updateUI");
        switch(MapclientActivity.mceState) {
        case MapclientTestAppConstants.MCE_STATE_INIT:
            Log.d(TAG, "updateUI MCE_STATE_INIT");
            if(serverAddress != null)
                btnConnectToServer.setEnabled(true);
            else
                btnConnectToServer.setEnabled(false);
            btnSelectBTServer.setEnabled(true);
            btnDisconnectFromServer.setEnabled(false);
            btnPushMsg.setEnabled(false);
            btnGetFolderPath.setEnabled(false);
            btnUpdateInbox.setEnabled(false);
            btnGetMsgList.setEnabled(false);
            break;
        case MapclientTestAppConstants.MCE_STATE_CONNECTED:
            Log.d(TAG, "updateUI MCE_STATE_CONNECTED");
            btnSelectBTServer.setEnabled(false);
            btnDisconnectFromServer.setEnabled(true);
            btnConnectToServer.setEnabled(false);
            tvServerName.setText("Connected To : " + mServer.getName());
            btnPushMsg.setEnabled(true);
            btnGetFolderPath.setEnabled(true);
            btnUpdateInbox.setEnabled(true);
            btnGetMsgList.setEnabled(true);
            if(selectedItem != -1 && mseInstanceNames != null) {
                tvMSEInstanceName.setText("MSE Instance Name: " + mseInstanceNames[selectedItem]);
            }
            Toast.makeText(this, "Connected to server: " + mServer.getName(), Toast.LENGTH_SHORT).show();
            break;
        case MapclientTestAppConstants.MCE_STATE_DISCONNECTED:
            Log.d(TAG, "updateUI MCE_STATE_DISCONNECTED");
            btnSelectBTServer.setEnabled(true);
            btnDisconnectFromServer.setEnabled(false);
            btnPushMsg.setEnabled(false);
            btnGetFolderPath.setEnabled(false);
            btnUpdateInbox.setEnabled(false);
            btnGetMsgList.setEnabled(false);
            if(serverAddress != null) {
                btnConnectToServer.setEnabled(true);
                tvServerName.setText("Selected Device : " + mServer.getName());
                tvMSEInstanceName.setText("");
            }
            else {
                btnConnectToServer.setEnabled(false);
                tvServerName.setText("");
            }
            tvMSEInstanceName.setText("");
            mseInstance = -1;
            mseInstanceIds = null;
            mseInstanceNames = null;
            Toast.makeText(this, "Disconnected from Server: " + mServer.getName(), Toast.LENGTH_SHORT).show();
            break;
        case MapclientTestAppConstants.MCE_STATE_UNKNOWN: return;
        }

        Map<String,?> keys = prefs.getAll();
        if(keys == null) {
            Log.d(TAG, "SharedPreferences keys are null");
        }
        tvMSEFolderPath.setText("FolderPath: " + mseFolderPath);
    }

    private void changeState(int toState) {
        MapclientActivity.mceState = toState;
        switch(toState) {
        case MapclientTestAppConstants.MCE_STATE_UNKNOWN: Log.d(TAG, "mcestate = MCE_STATE_UNKNOWN"); break;
        case MapclientTestAppConstants.MCE_STATE_ERROR: Log.d(TAG, "mcestate = MCE_STATE_ERROR"); break;
        case MapclientTestAppConstants.MCE_STATE_DISCONNECTED: Log.d(TAG, "mcestate = MCE_STATE_DISCONNECTED"); break;
        case MapclientTestAppConstants.MCE_STATE_INIT: Log.d(TAG, "mcestate = MCE_STATE_INIT"); break;
        case MapclientTestAppConstants.MCE_STATE_READY: Log.d(TAG, "mcestate = MCE_STATE_READY"); break;
        case MapclientTestAppConstants.MCE_STATE_CONNECTED: Log.d(TAG, "mcestate = MCE_STATE_CONNECTED"); break;
        case MapclientTestAppConstants.MCE_STATE_BUSY: Log.d(TAG, "mcestate = MCE_STATE_BUSY"); break;
        }

        updateUI();
    }


    private void messageSent(Boolean status) {
        if(status)
            Toast.makeText(this, "Message Sent", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(this, "Failed to send Message", Toast.LENGTH_SHORT).show();
        mProgressDialog.dismiss();
    }

    private File createSMS(String tel, int charSet, String body, int msgStatus) {
        String type = "SMS_GSM";
        String folderpath = mseFolderPath;
        String characterSet;
        String messageStatus;
        switch(charSet) {
        case BluetoothMapClient.CHARSET_NATIVE: characterSet = "NATIVE"; break;
        case BluetoothMapClient.CHARSET_UTF8: characterSet = "UTF-8"; break;
        default: characterSet = "UTF-8";
        }
        switch(msgStatus) {
        case 0: messageStatus = "UNREAD"; break;
        case 1: messageStatus = "READ"; break;
        default: messageStatus = "UNREAD";
        }
        String messageText = "BEGIN:BMSG" + "\r\n" +
                "VERSION:1.0" + "\r\n" +
                "STATUS:" + messageStatus + "\r\n" +
                "TYPE:" + "SMS_GSM" + "\r\n" +
                "FOLDER:" + folderpath + "\r\n" +
                "BEGIN:VCARD" + "\r\n" +
                "VERSION:2.1" + "\r\n" +
                "N:" + "\r\n" +
//                "TEL:" + tel + "\r\n" +
                "END:VCARD" + "\r\n" +
                "BEGIN:BENV" + "\r\n" +
                "BEGIN:VCARD" + "\r\n" +
                "VERSION:2.1" + "\r\n" +
                "N:Ch;Fr" + "\r\n" +
                "FN:Fr Ch" + "\r\n" +
                "TEL:" + tel + "\r\n" +
                "END:VCARD" + "\r\n" +
                "BEGIN:BBODY" + "\r\n" +
                "CHARSET:" + characterSet + "\r\n" +
//                "ENCODING:" + "G-7BIT" + "\r\n" +
                "LENGTH:" + Integer.toString(body.length()) + "\r\n" +
                "BEGIN:MSG" + "\r\n" +
                 body + "\r\n" +
                "END:MSG" + "\r\n" +
                "END:BBODY" + "\r\n" +
                "END:BENV" + "\r\n" +
                "END:BMSG";

        File bMsgFile = new File("/sdcard" + "/push.bmsg");
        OutputStreamWriter writer = null;
        try {
            bMsgFile.createNewFile();
            FileOutputStream outStream = new FileOutputStream(bMsgFile);
            writer = new OutputStreamWriter(outStream);
            BufferedWriter outBuf = new BufferedWriter(writer);
            outBuf.write(messageText);
            outBuf.close();
            } catch (Throwable e) {
                Log.d(TAG, "unable to create push.bmsg");
                Log.d(TAG, e.getMessage());
            }
        return bMsgFile;
    }

    /**
    *
    * Handler handles all the GUI update events
    *
    */
    protected Handler viewUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG,"handleMessage()");
            switch(msg.what)
            {
                case UI_INIT_COMPLETE:
                    this.removeMessages(UI_TIMEOUT);
                    btnSelectBTServer.setEnabled(true);
                    btnConnectToServer.setEnabled(false);
                    btnDisconnectFromServer.setEnabled(false);
                    changeState(MapclientTestAppConstants.MCE_STATE_INIT);
                    break;
                case UI_SELECTED_SERVER:
                    btnConnectToServer.setEnabled(true);
                    tvServerName.setText("Connect To : " + mServer.getName());
                case UI_FOLDER_PATH_SET:
                    updateUI();
                    break;
                case UI_CONNECTED_TO_SERVER:
                    this.removeMessages(UI_TIMEOUT);
                    changeState(MapclientTestAppConstants.MCE_STATE_CONNECTED);
                    MapclientActivity.mBluetoothMapClient.setFolderPath(mseInstance, mseFolderPath);
                    mProgressDialog.dismiss();
                    break;
                case UI_UNABLE_TO_CONNECT_TO_SERVER:
                    this.removeMessages(UI_TIMEOUT);
                    changeState(MapclientTestAppConstants.MCE_STATE_DISCONNECTED);
                    Log.d(TAG, "Unable to connect to server");
                    mProgressDialog.dismiss();
                    break;
                case UI_DISCONNECTED_FROM_SERVER:
                    this.removeMessages(UI_TIMEOUT);
                    changeState(MapclientTestAppConstants.MCE_STATE_DISCONNECTED);
                    mProgressDialog.dismiss();
                    break;
                case UI_SHOW_MESSAGE_LIST:
                    if(messageMap.size() > 0) {
                        for(String messageHandle: messageMap.keySet()) {
                            MapclientActivity.mBluetoothMapClient.getMessageAsObject(mseInstance, messageHandle,
                                    BluetoothMapClient.CHARSET_UTF8, false);
                        }
                        viewUpdateHandler.sendEmptyMessageDelayed(UI_TIMEOUT, MapclientTestAppConstants.MSE_CALLBACK_TIMEOUT);
                        displayMsg("Messages list received");
                    }
                    else {
                        mProgressDialog.dismiss();
                        displayMsg("No messages received from current folder");
                    }
                    break;
                case UI_ALL_MESSAGES_RECEIVED:
                    this.removeMessages(UI_TIMEOUT);
                    displayMsg("Messages received in Message App");
                    break;
                case UI_MESSAGE_SEND_SUCCESS:
                    messageSent(true);
                    break;
                case UI_MESSAGE_SEND_FAILED:
                    messageSent(false);
                    break;
                case UI_NOTIFICATION_ENABLED:
                    displayMsg("Notification enabled");
                    break;
                case UI_NOTIFICATION_DISABLED:
                    displayMsg("Notification disabled");
                    break;
                case UI_SHOW_NOTIFICATION:
                    Log.d(TAG, "received notification: " + (Uri) (msg.getData().getParcelable("contentUri")));
                    notificationMsgUri = (Uri) (msg.getData().getParcelable("contentUri"));
                    BluetoothMessageListFilter filter = new BluetoothMessageListFilter();
                    MapclientActivity.mBluetoothMapClient.getMessageListObject(mseInstance, -1, -1, filter, null);
                    break;
                case UI_TIMEOUT:
                    mProgressDialog.dismiss();
                    Log.d(TAG, "Request timedout !!!");
                    displayMsg("Request timedout !!!");
                    break;
                case UI_MSE_INSTANCES_FETCH_COMPLETE:
                    displayMsg("Fetched MSE Instances");
                    this.removeMessages(UI_TIMEOUT);
                    mProgressDialog.dismiss();
                    if(mseInfo != null) selectMseInstance();
                    break;
                default:
                    Log.e(TAG, "Unknown UI handle event received");
            }
        }
    };

    /**
    *
    * Listener for button click events
    *
    */
    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        Button btn = (Button) v;
        if(btn == btnSelectBTServer) {
            pickBTDevice();
        }
        else if(btn == btnConnectToServer) {
            fetchMseInstances();
        }
        else if(btn == btnDisconnectFromServer) {
            disconnect();
        }
        else if(btn == btnPushMsg) {
            Log.d(TAG, "Creating Push Message");
            Intent createMessageIntent = new Intent(this,  CreateSMSActivity.class);
            startActivityForResult(createMessageIntent, MapclientTestAppConstants.REQCODE_PUSH_MESSAGE);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String currentDateandTime = sdf.format(new Date());
        }
        else if(btn == btnGetFolderPath) {
            String currentFolderPath = MapclientActivity.mBluetoothMapClient.getFolderPath(mseInstance);
            displayMsg("Current FolderPath: " + currentFolderPath);
        }
        else if(btn == btnGetMsgList) {
            notificationMsgUri = null;
            BluetoothMessageListFilter filter = new BluetoothMessageListFilter();
            MapclientActivity.mBluetoothMapClient.getMessageListObject(mseInstance, -1, -1, filter, null);
            mProgressDialog.setMessage("Get Messages in Progress");
            mProgressDialog.show();
        }
        else if(btn == btnUpdateInbox) {
            MapclientActivity.mBluetoothMapClient.updateInbox (mseInstance);
        }

        else if(btn == btnExit) {
//            releaseResources();
            finish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void sendNotification(Uri contentUri) {
     // Prepare intent which is triggered if the
        // notification is selected
        Notification noti;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String eventType = "None";
        String messageHandle = "None";
        String folderpath = "None";
        String msgType = "None";
        Log.d(TAG, "sendNotification : " + contentUri);

        // Parse XML from contentUri
        XmlPullParser parser = Xml.newPullParser();
        try {
            InputStream in = MapclientActivity.this.getContentResolver().openInputStream(contentUri);
            InputStreamReader isr = new InputStreamReader(in);
            parser.setInput(isr);
            parser.nextTag();
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                // Starts by looking for the entry tag
                if (tag.equals("event")) {
                    eventType = parser.getAttributeValue(null, "type");
                    messageHandle = parser.getAttributeValue(null, "handle");
                    folderpath = parser.getAttributeValue(null, "folder");
                    msgType = parser.getAttributeValue(null, "msg_type");
                    Log.d(TAG, "Notification: " + eventType + messageHandle + folderpath + msgType);
                }
                parser.nextTag();
            }

        } catch (Exception e) {
            Log.d(TAG, "sendNotification : File not found");
        }

        if(eventType.equals("NewMessage") || eventType.equals("SendingSuccess")) {
            MapclientActivity.mBluetoothMapClient.getMessageAsObject(mseInstance, messageHandle,
                    BluetoothMapClient.CHARSET_UTF8, false);
            Intent defineIntent = new Intent(Intent.ACTION_VIEW);
            defineIntent.setClassName("com.android.mms", "com.android.mms.ui.ConversationList");
            PendingIntent pIntent = PendingIntent.getActivity(this, 0, defineIntent, 0);

         // Build notification
            noti = new Notification.Builder(this)
                .setContentTitle("MAP Client Notification:")
                .setContentText("Event Type: " + eventType)
                        .setSubText("FolderPath: " + folderpath + " MsgHandler: " + messageHandle + " MsgType: " + msgType)
                        .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pIntent).build();
            // Hide the notification after its selected
            noti.flags |= Notification.FLAG_AUTO_CANCEL;
        }
        else {
         // Build notification
            noti = new Notification.Builder(this)
                .setContentTitle("MAP Client Notification:")
                .setContentText("Event Type: " + eventType)
                        .setSubText("FolderPath: " + folderpath + " MsgHandler: " + messageHandle + " MsgType: " + msgType)
                        .setSmallIcon(R.drawable.ic_launcher).build();
            // Hide the notification after its selected
            noti.flags |= Notification.FLAG_AUTO_CANCEL;
        }

        // create notification alert sound
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        noti.sound = alarmSound;

        notificationManager.notify(0, noti);
    }

    @Override
    public void onBackPressed() {
        this.moveTaskToBack(true);
    }

    private void releaseResources() {
        Log.d(TAG, "releaseResources()");
        if(MapclientActivity.mBluetoothMapClient != null) {
            MapclientActivity.mBluetoothMapClient.unregisterEventHandler();
            MapclientActivity.mBluetoothMapClient.close();
        }
        MapclientActivity.mBluetoothMapClient = null;
        mBluetoothAdapter = null;
    }

    /**
     * Request the user to change the default SMS app to this app
     */
    private void checkForDefaultSmsApp() {

        // Only do these checks/changes on KitKat+, the "mSetDefaultSmsLayout"
        // has its visibility set to "gone" in the xml layout so it won't show
        // at all on earlier Android versions.
        if (MapClientUtils.hasKitKat()) {
            Log.d(TAG, "kitkat+ , checking for default sms app");
            defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
            if (MapClientUtils.isDefaultSmsApp(this)) {
                // This app is the default, removing the
                // "make this app the default" layout
                Log.d(TAG, "MapClient is the default sms app");
                mSetDefaultSmsLayout.setVisibility(View.GONE);
            } else {
                // Not the default, show the "make this app the default" layout
                Log.d(TAG, "MapClient is not the default sms app");
                mSetDefaultSmsLayout.setVisibility(View.VISIBLE);

                Button button = (Button)findViewById(R.id.set_default_sms_button);
                button.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "onclick: calling setDefaultSmsApp");
                        MapClientUtils.setDefaultSmsApp(MapclientActivity.this);
                    }
                });
            }
        } else {
            Log.d(TAG, "Lower version then Kitkat, No need to check for default sms app");
        }
    } // end checkForDefaultSmsApp

}
