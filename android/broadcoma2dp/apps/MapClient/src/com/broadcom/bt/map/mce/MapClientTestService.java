/*******************************************************************************
 *
 *  Copyright (C) 2013 Broadcom Corporation
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
package com.broadcom.bt.map.mce;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import com.broadcom.bt.map.BluetoothMapClient;
import com.broadcom.bt.map.BluetoothFolderInfo;
import com.broadcom.bt.map.IBluetoothMapClientEventHandler;
import com.broadcom.bt.map.BluetoothMessageInfo;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMseInfo;
import com.broadcom.bt.util.bmsg.BMessage;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class MapClientTestService extends Service {
    private static final String TAG = "BtMap.MapClientTestService";
    private static final String ACTION_PREFIX = "com.broadcom.bt.map.mce.";
    private static final String ACTION_START_CLIENT = ACTION_PREFIX + "START_CLIENT";
    private static final String ACTION_STOP_CLIENT = ACTION_PREFIX + "STOP_CLIENT";
    private static final String ACTION_START_MNS = ACTION_PREFIX + "START_MNS";
    private static final String ACTION_STOP_MNS = ACTION_PREFIX + "STOP_MNS";
    private static final String ACTION_CONNECT = ACTION_PREFIX + "CONNECT";
    private static final String ACTION_DISCONNECT = ACTION_PREFIX + "DISCONNECT";
    private static final String ACTION_DISCONNECT_ALL = ACTION_PREFIX + "DISCONNECT_ALL";
    private static final String ACTION_SET_FOLDER_PATH = ACTION_PREFIX + "SET_FOLDER_PATH";
    private static final String ACTION_GET_FOLDER_PATH = ACTION_PREFIX + "GET_FOLDER_PATH";
    private static final String ACTION_GET_FOLDER_LIST = ACTION_PREFIX + "GET_FOLDER_LIST";
    private static final String ACTION_GET_MESSAGE_LIST = ACTION_PREFIX + "GET_MESSAGE_LIST";

    private static final String ACTION_GET_MESSAGE = ACTION_PREFIX + "GET_MESSAGE";
    private static final String ACTION_SET_MESSAGE_DELETED_STATUS = ACTION_PREFIX
            + "SET_MESSAGE_DELETED_STATUS";
    private static final String ACTION_SET_MESSAGE_READ_STATUS = ACTION_PREFIX
            + "SET_MESSAGE_READ_STATUS";
    private static final String ACTION_PUSH_MESSAGE = ACTION_PREFIX + "PUSH_MESSAGE";

    private static final String ACTION_ABORT = ACTION_PREFIX + "ABORT";
    private static final String ACTION_UPDATE_INBOX = ACTION_PREFIX + "UPDATE_INBOX";
    private static final String ACTION_REGISTER_FOR_NOTIFICATION = ACTION_PREFIX
            + "REGISTER_FOR_NOTIFICATION";
    private static final String ACTION_UNREGISTER_FOR_NOTIFICATION = ACTION_PREFIX
            + "UNREGISTER_FOR_NOTIFICATION";

    private static final String ACTION_GET_MSE_INSTANCES = ACTION_PREFIX + "GET_MSE_INSTANCES";

    private static final String ACTION_KEEP_ALIVE = ACTION_PREFIX + "KEEP_ALIVE";

    private static final String EXTRA_CLIENT_ID = "CLIENT_ID";
    private static final String EXTRA_NAME = "NAME";
    private static final String EXTRA_SERVER_ADDR = "SERVER_ADDR";
    private static final String EXTRA_SERVER_INSTANCE_ID = "SERVER_INSTANCE_ID";
    private static final String EXTRA_FOLDER_PATH = "FOLDER_PATH";
    private static final String EXTRA_MAXLISTCOUNT = "MAXLISTCOUNT";
    private static final String EXTRA_OFFSET = "OFFSET";
    private static final String EXTRA_RETURN_TYPE = "RETURN_TYPE";
    private static final String EXTRA_MESSAGE_HANDLE = "MESSAGE_HANDLE";
    private static final String EXTRA_CHARSET = "CHARSET";
    private static final String EXTRA_INCLUDE_ATTACHMENTS = "INCLUDE_ATTACHMENTS";
    private static final String EXTRA_STATUS_SET = "STATUS_SET";
    private static final String EXTRA_FILEPATH = "FILEPATH";
    private static final String EXTRA_IS_RETRY = "IS_RETRY";
    private static final String EXTRA_IS_AUTOSEND = "IS_AUTOSEND";

    private static final String EXTRA_FILTER_MSG_MASK = "FILTER_MSG_MASK";
    private static final String EXTRA_FILTER_SUBJECT_LENGTH = "FILTER_SUBJECT_LENGTH";
    private static final String EXTRA_FILTER_PERIOD_BEGIN = "FILTER_PERIOD_BEGIN";
    private static final String EXTRA_FILTER_PERIOD_END = "FILTER_PERIOD_END";
    private static final String EXTRA_FILTER_READ_STATUS = "FILTER_READ_STATUS";
    private static final String EXTRA_FILTER_PRIORITY_STATUS = "FILTER_PRIORITY_STATUS";
    private static final String EXTRA_FILTER_ORIGINATOR = "FILTER_ORIGINATOR";
    private static final String EXTRA_FILTER_RECIPIENT = "FILTER_RECIPIENT";

    private static final int EVENT_TYPE_KEEP_ALIVE = 1;

    private class MapClientServiceEventHandler implements IBluetoothMapClientEventHandler {
        private String mClientId = "";

        public MapClientServiceEventHandler(String clientId) {
            mClientId = clientId;
        }

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

        }

        @Override
        public void onServerDisconnected(BluetoothDevice server, int instanceId, boolean success) {
            Log.d(TAG, "onServerDisconnected() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success);
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
                    is = MapClientTestService.this.getContentResolver().openInputStream(contentUri);
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
                }
            }
            Log.d(TAG, "==============================================");
        }

        @Override
        public void onFolderPathSet(BluetoothDevice server, int instanceId, boolean success,
                String folderPath) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onGetMseInstancesResult(BluetoothDevice server, BluetoothMseInfo[] mseInfo) {
            int length = (mseInfo == null ? 0 : mseInfo.length);
            Log.d(TAG, "onGetMseInstancesResult(): length= " + length);
            for (int i = 0; i < length; i++) {
                BluetoothMseInfo m = mseInfo[i];
                Log.d(TAG, "BluetoothMseInfo #" + i + ": serverInstanceId=" + m.mServerInstanceId
                        + ", messageTypes=" + m.mMessageTypes + ", name=" + m.mName);
                Log.d(TAG, "Supports SMS?      " + m.supportsSmsMessages());
                Log.d(TAG, "Supports SMS GSM ? " + m.supportsSmsGsmMessages());
                Log.d(TAG, "Supports SMS CMDA? " + m.supportsSmsCdmaMessages());
                Log.d(TAG, "Supports MMS?      " + m.supportsMmsMessages());
                Log.d(TAG, "Supports EMAIL?    " + m.supportsEmailMessages());
            }
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
                    is = MapClientTestService.this.getContentResolver().openInputStream(contentUri);
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
                    is = MapClientTestService.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                    }
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

        }

        @Override
        public void onUpdateInboxResult(BluetoothDevice server, int serverInstanceId,
                boolean success) {

        }

        @Override
        public void onNotificationRegistrationStateChange(BluetoothDevice server, int instanceId,
                boolean isRegistration, boolean success) {

        }

        public void onMessageStatusUpdated(BluetoothDevice server, int serverInstanceId,
                int statusType, String messageHandle, boolean success) {

        }

        public void onPushMessageResult(BluetoothDevice server, int serverInstanceId,
                boolean success, String messageHandle) {
            Log.d(TAG, "onPushMessageResult() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", success=" + success + ", messageHandle="
                    + messageHandle);
        }

        @Override
        public void onMessageListResult(BluetoothDevice server, int instanceId, boolean success,
                int listLength, boolean hasNewMessages, BluetoothMessageInfo[] messageInfo) {
            Log.d(TAG, "onMessageListResult() server=" + server + ", instanceId=" + instanceId
                    + ", success=" + success + ", listLength=" + listLength + ", hasNewMessages="
                    + hasNewMessages + ", messageInfo=" + messageInfo);
            Log.d(TAG, "============================= Message List ==============================");
            if (messageInfo != null) {
                for (int i = 0; i < messageInfo.length; i++) {
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
            }
            Log.d(TAG, "=========================================================================");
        }

        @Override
        public void onNotification(BluetoothDevice server, int serverInstanceId, Uri contentUri) {
            Log.d(TAG, "onNotification() server=" + server + ", serverInstanceId="
                    + serverInstanceId + ", contentUri=" + contentUri);
            if (contentUri != null) {
                InputStream is = null;
                try {
                    is = MapClientTestService.this.getContentResolver().openInputStream(contentUri);
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String lineRead = null;
                    while ((lineRead = r.readLine()) != null) {
                        Log.d(TAG, lineRead);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to open Notification", t);
                }
            } else {
                Log.w(TAG, "No Notification!");
            }
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    HashMap<String, BluetoothMapClient> mClientMap = new HashMap<String, BluetoothMapClient>();
    HashMap<String, MapClientServiceEventHandler> mClientCallbackMap = new HashMap<String, MapClientTestService.MapClientServiceEventHandler>();

    private class EventHandler extends Handler {

        public EventHandler() {
            sendKeepAliveMessage();
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_TYPE_KEEP_ALIVE) {
                if (mClientMap.isEmpty()) {
                    stopSelf();
                } else {
                    Intent intent = new Intent(ACTION_KEEP_ALIVE);
                    intent.setClassName("com.broadcom.bt.map",
                            "com.broadcom.bt.map.mce.MapClientTestService");
                    startService(intent);
                }
                sendKeepAliveMessage();
            }
        }

        private void sendKeepAliveMessage() {
            Message message = new Message();
            message.what = EVENT_TYPE_KEEP_ALIVE;
            sendMessageDelayed(message, 300000);
        }
    }

    EventHandler mEventHandler = new EventHandler();

    public void onCreate() {
        super.onCreate();
    }

    public void onDestroy() {

        super.onDestroy();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(TAG, " Action == " + action);
        if (ACTION_START_CLIENT.equals(action)) {
            onStartClient(intent);
        } else if (ACTION_STOP_CLIENT.equals(action)) {
            onStopClient(intent);
        } else if (ACTION_START_MNS.equals(action)) {
            onStartNotificationServer(intent);
        } else if (ACTION_STOP_MNS.equals(action)) {
            onStopNotificationServer(intent);
        } else if (ACTION_CONNECT.equals(action)) {
            onConnect(intent);
        } else if (ACTION_DISCONNECT.equals(action)) {
            onDisconnect(intent);
        } else if (ACTION_DISCONNECT_ALL.equals(action)) {
            onDisconnectAll(intent);
        } else if (ACTION_SET_FOLDER_PATH.equals(action)) {
            onSetFolderPath(intent);
        } else if (ACTION_GET_FOLDER_PATH.equals(action)) {
            onGetFolderPath(intent);
        } else if (ACTION_GET_FOLDER_LIST.equals(action)) {
            onGetFolderList(intent);
        } else if (ACTION_GET_MESSAGE_LIST.equals(action)) {
            onGetMessageList(intent);
        } else if (ACTION_GET_MESSAGE.equals(action)) {
            onGetMessage(intent);
        } else if (ACTION_PUSH_MESSAGE.equals(action)) {
            onPushMessage(intent);
        } else if (ACTION_UPDATE_INBOX.equals(action)) {
            onUpdateInbox(intent);
        } else if (ACTION_SET_MESSAGE_READ_STATUS.equals(action)) {
            onSetMessageReadStatus(intent);
        } else if (ACTION_SET_MESSAGE_DELETED_STATUS.equals(action)) {
            onSetMessageDeletedStatus(intent);
        } else if (ACTION_REGISTER_FOR_NOTIFICATION.equals(action)) {
            onRegisterForNotification(intent);
        } else if (ACTION_UNREGISTER_FOR_NOTIFICATION.equals(action)) {
            onUnregisterForNotification(intent);
        } else if (ACTION_ABORT.equals(action)) {
            onAbort(intent);
        } else if (ACTION_GET_MSE_INSTANCES.equals(action)) {
            onGetMseInstances(intent);
        } else if (ACTION_KEEP_ALIVE.equals(action)) {
            //Log.d(TAG, " Keep alive received");
        } else {
            printHelp();
        }
        return Service.START_NOT_STICKY;
    }

    private final void printHelp() {
        StringBuilder help = new StringBuilder();
        String className = getClass().getName();
        String pkgName = getApplicationContext().getPackageName();

        help.append("==================PBAP Client Test Service===============================\n");
        help.append("Usage:\n");
        help.append("adb shell am startservice \n");
        help.append("    -a <action> [action params] \n");
        help.append("    -es " + EXTRA_CLIENT_ID + " <" + EXTRA_CLIENT_ID + "> \n");
        help.append("    " + pkgName + "/" + className + "\n\n");
        help.append("....where <" + EXTRA_CLIENT_ID
                + ">  is a unique id to identify a client instancen\n");
        help.append("....where <action> [action_params] are as follows\n\n");
        help.append("\n\n");

        printHelp_StartClient(help);
        printHelp_StopClient(help);
        printHelp_StartNotificationServer(help);
        printHelp_StopNotificationServer(help);
        printHelp_Connect(help);
        printHelp_Disconnect(help);
        printHelp_DisconnectAll(help);
        printHelp_Abort(help);
        printHelp_SetFolderPath(help);
        printHelp_GetFolderPath(help);
        printHelp_GetFolderList(help);
        printHelp_GetMessageList(help);
        printHelp_GetMessage(help);
        printHelp_PushMessage(help);
        printHelp_UpdateInbox(help);
        printHelp_SetMessageReadStatus(help);
        printHelp_SetMessageDeletedStatus(help);
        printHelp_RegisterForNotification(help);
        printHelp_UnregisterForNotification(help);
        printHelp_GetMseInstances(help);
        help.append("============================================================================");
        Log.d(TAG, help.toString());
    }

    private String parseClientId(Intent intent) {
        String clientId = intent.getStringExtra(EXTRA_CLIENT_ID);
        if (clientId == null) {
            Log.w(TAG, "CLIENT_ID not found!");
        }
        return clientId;
    }

    private int parseServerInstanceId(Intent intent) {
        String instanceIdStr = intent.getStringExtra(EXTRA_SERVER_INSTANCE_ID);

        int instanceId = -1;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (Throwable t) {
            Log.e(TAG, "", t);
        }

        if (instanceId < 0) {
            Log.w(TAG, "Invalid instance id " + instanceIdStr);
        }
        return instanceId;
    }

    private BluetoothMapClient getClient(Intent intent) {
        final String clientId = parseClientId(intent);
        if (clientId == null) {
            return null;
        }
        BluetoothMapClient client = mClientMap.get(clientId);
        if (client == null) {
            Log.w(TAG, "Client not found with CLIENT_ID " + clientId);
        }
        return client;
    }

    private void onStartClient(Intent intent) {
        final String clientId = parseClientId(intent);
        if (clientId == null) {
            return;
        }

        // Check if a client already exists
        BluetoothMapClient client = mClientMap.get(clientId);
        if (client != null) {
            Log.w(TAG, " BluetoothMapClient already exists for clientId " + clientId);
            return;
        }
        BluetoothMapClient.getProxy(this, new ServiceListener() {

            @Override
            public void onServiceDisconnected(int profile) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (proxy != null) {
                    BluetoothMapClient client = (BluetoothMapClient) proxy;
                    Log.d(TAG, "Created BluetoothMapClient: " + clientId);
                    mClientMap.put(clientId, client);
                    MapClientServiceEventHandler cb = new MapClientServiceEventHandler(clientId);
                    mClientCallbackMap.put(clientId, cb);
                    client.registerEventHandler(cb);
                }
            }
        });
    }

    private void printHelp_StartClient(StringBuilder help) {
        help.append("Start Client:\n");
        help.append("    " + ACTION_START_CLIENT + "\n\n");
    }

    private void onStopClient(Intent intent) {
        final String clientId = parseClientId(intent);
        if (clientId == null) {
            return;
        }
        BluetoothMapClient client = mClientMap.remove(clientId);
        MapClientServiceEventHandler cb = mClientCallbackMap.remove(clientId);
        if (client != null) {
            Log.d(TAG, "Stopping client: " + clientId);
            client.unregisterEventHandler();
            client.close();
        }
    }

    private void printHelp_StopClient(StringBuilder help) {
        help.append("Stop Client:\n");
        help.append("    " + ACTION_STOP_CLIENT + "\n\n");
    }

    private void onStartNotificationServer(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }

        String name = intent.getStringExtra(EXTRA_NAME);
        if (name == null) {
            Log.w(TAG, "Notification Server Name not set. Exitting");
            return;
        }
        Log.d(TAG, "Starting notification server " + name);
        if (!client.startNotificationServer(name)) {
            Log.e(TAG, "Unable to start notification server");
        }
    }

    private void printHelp_StartNotificationServer(StringBuilder help) {
        help.append("Start Notification Server:\n");
        help.append("    " + ACTION_START_MNS + " -es " + EXTRA_NAME + " <" + EXTRA_NAME + ">\n\n");
        help.append("    where <" + EXTRA_NAME + "> is the name to be advertised in SDP record\n\n");
    }

    private void onStopNotificationServer(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }

        Log.d(TAG, "Stopping notification server...");
        if (!client.stopNotificationServer()) {
            Log.e(TAG, "Unable to stop notification server");

        }
    }

    private void printHelp_StopNotificationServer(StringBuilder help) {
        help.append("Stop Notification Server:\n");
        help.append("    " + ACTION_STOP_MNS + "\n\n");
    }

    private void onConnect(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        String serverAddr = intent.getStringExtra(EXTRA_SERVER_ADDR);
        BluetoothDevice server = null;
        try {
            server = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(serverAddr);
        } catch (Throwable t) {
            Log.e(TAG, "", t);
        }
        if (server == null) {
            Log.w(TAG, "Invalid server " + serverAddr);
            return;
        }

        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        Log.d(TAG, "Connecting to server: " + server + ", instanceId:" + serverInstanceId);
        if (!client.setServer(server)) {
            Log.e(TAG, "Unable to set MAP server");
            return;
        }

        if (!client.connect(serverInstanceId)) {
            Log.e(TAG, "Unable to connect to MAP server");
        }
    }

    private void printHelp_Connect(StringBuilder help) {
        help.append("Connect to MAP MSE:\n");
        help.append("    -es " + EXTRA_SERVER_ADDR + " <" + EXTRA_SERVER_ADDR + "> \n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_CONNECT + "\n\n");
    }

    private void onDisconnect(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        Log.d(TAG, "Disconnecting from server instanceId:" + serverInstanceId);

        if (!client.disconnect(serverInstanceId)) {
            Log.e(TAG, "Unable to disconnect from MAP server");
        }
    }

    private void printHelp_Disconnect(StringBuilder help) {
        help.append("Disconnect from MAP MSE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_DISCONNECT + "\n\n");
    }

    private void onDisconnectAll(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }

        Log.d(TAG, "Disconnect from all MSE instances: ");

        if (!client.disconnectAll()) {
            Log.e(TAG, "Unable to disconnect all from MAP server");
        }
    }

    private void printHelp_DisconnectAll(StringBuilder help) {
        help.append("Disconnect from all MAP MSE instances:\n");
        help.append("    " + ACTION_DISCONNECT_ALL + "\n\n");
    }

    private void onSetFolderPath(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int instanceId = parseServerInstanceId(intent);
        if (instanceId < 0) {
            return;
        }

        String folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH);
        if (folderPath == null) {
            Log.w(TAG, "onSetFolderPath(): Invalid folder path");
            return;
        }
        Log.d(TAG, "Setting folder path for server instanceId:" + instanceId + " to " + folderPath);

        if (!client.setFolderPath(instanceId, folderPath)) {
            Log.e(TAG, "Unable to set folder path");
        }
    }

    private void printHelp_SetFolderPath(StringBuilder help) {
        help.append("Set Folder Path for a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_FOLDER_PATH + " <" + EXTRA_FOLDER_PATH + "> \n");
        help.append("    " + ACTION_SET_FOLDER_PATH + "\n");
    }

    private void onGetFolderPath(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        Log.d(TAG, "Getting folder path from server instanceId:" + serverInstanceId);

        String folderPath = client.getFolderPath(serverInstanceId);
        Log.d(TAG, "Folder path = " + folderPath);
    }

    private void printHelp_GetFolderPath(StringBuilder help) {
        help.append("Get current Folder Path for a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_GET_FOLDER_PATH + "\n");
    }

    private void onGetFolderList(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        int maxListCount = -1;
        try {
            maxListCount = Integer.parseInt(intent.getStringExtra(EXTRA_MAXLISTCOUNT));
        } catch (Throwable t) {

        }

        int offset = -1;
        try {
            offset = Integer.parseInt(intent.getStringExtra(EXTRA_OFFSET));
        } catch (Throwable t) {

        }

        int returnType = 0;
        try {
            returnType = Integer.parseInt(intent.getStringExtra(EXTRA_RETURN_TYPE));
        } catch (Throwable t) {

        }

        Log.d(TAG, "Getting folder list from server instanceId:" + serverInstanceId);

        if (returnType == 0) {
            client.getFolderList(serverInstanceId, maxListCount, offset);
        } else {
            client.getFolderListObject(serverInstanceId, maxListCount, offset);
        }
    }

    private void printHelp_GetFolderList(StringBuilder help) {
        help.append("Get folder list of current folder for a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_MAXLISTCOUNT + " <" + EXTRA_MAXLISTCOUNT + "> \n");
        help.append("    -es " + EXTRA_OFFSET + " <" + EXTRA_OFFSET + "> \n");
        help.append("    " + ACTION_GET_FOLDER_LIST + "\n");
    }

    private void onGetMessageList(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        int maxListCount = -1;
        try {
            maxListCount = Integer.parseInt(intent.getStringExtra(EXTRA_MAXLISTCOUNT));
        } catch (Throwable t) {

        }

        int offset = -1;
        try {
            offset = Integer.parseInt(intent.getStringExtra(EXTRA_OFFSET));
        } catch (Throwable t) {

        }

        int returnType = 0;
        try {
            returnType = Integer.parseInt(intent.getStringExtra(EXTRA_RETURN_TYPE));
        } catch (Throwable t) {

        }

        BluetoothMessageListFilter filter = new BluetoothMessageListFilter();
        byte b = 0;
        String f = null;

        // msg mask filter
        try {

            b = Byte.parseByte(intent.getStringExtra(EXTRA_FILTER_MSG_MASK));
            if (b > 0) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_MSG_MASK + " set to " + b);
                filter.mMsgMask = b;
            }
        } catch (Throwable t) {

        }

        // Read status
        try {
            b = Byte.parseByte(intent.getStringExtra(EXTRA_FILTER_READ_STATUS));
            if (b == 1 || b == 2) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_READ_STATUS + " set to " + b);
                filter.mReadStatus = b;
            }
        } catch (Throwable t) {

        }

        // Priority status
        try {
            b = Byte.parseByte(intent.getStringExtra(EXTRA_FILTER_PRIORITY_STATUS));
            if (b == 1 || b == 2) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_PRIORITY_STATUS + " set to " + b);
                filter.mPriorityStatus = b;
            }
        } catch (Throwable t) {

        }

        // Begin
        try {
            f = intent.getStringExtra(EXTRA_FILTER_PERIOD_BEGIN);
            if (f != null && f.length() > 0 && !f.equalsIgnoreCase("NULL")) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_PERIOD_BEGIN + " set to " + f);
                filter.mPeriodBegin = f;
            }
        } catch (Throwable t) {

        }

        // End
        try {
            f = intent.getStringExtra(EXTRA_FILTER_PERIOD_END);
            if (f != null && f.length() > 0 && !f.equalsIgnoreCase("NULL")) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_PERIOD_END + " set to " + f);
                filter.mPeriodEnd = f;
            }
        } catch (Throwable t) {

        }

        // Originator
        try {
            f = intent.getStringExtra(EXTRA_FILTER_ORIGINATOR);
            if (f != null && f.length() > 0 && !f.equalsIgnoreCase("NULL")) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_ORIGINATOR + " set to " + f);
                filter.mOriginator = f;
            }
        } catch (Throwable t) {

        }

        // Recipient
        try {
            f = intent.getStringExtra(EXTRA_FILTER_RECIPIENT);
            if (f != null && f.length() > 0 && !f.equalsIgnoreCase("NULL")) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_RECIPIENT + " set to " + f);

                filter.mRecipient = f;
            }
        } catch (Throwable t) {

        }

        // SUBJECT LENGTH
        try {
            b = Byte.parseByte(intent.getStringExtra(EXTRA_FILTER_SUBJECT_LENGTH));
            if (b > 0) {
                Log.d(TAG, "onGetMessageList(): " + EXTRA_FILTER_SUBJECT_LENGTH + " set to " + b);
                filter.mSubjectLength = b;
            }
        } catch (Throwable t) {

        }

        Log.d(TAG, "Getting message path from server instanceId:" + serverInstanceId);

        if (returnType == 0) {
        client.getMessageList(serverInstanceId, maxListCount, offset, filter, null);
        } else {
            client.getMessageListObject(serverInstanceId, maxListCount, offset, filter, null);
        }
    }

    private void printHelp_GetMessageList(StringBuilder help) {
        help.append("Get message list of current folder for a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_MAXLISTCOUNT + " <" + EXTRA_MAXLISTCOUNT + "> \n");
        help.append("    -es " + EXTRA_OFFSET + " <" + EXTRA_OFFSET + "> \n");
        help.append("    -es " + EXTRA_RETURN_TYPE + " <" + EXTRA_RETURN_TYPE + "> \n");
        help.append("    -es " + EXTRA_FILTER_MSG_MASK + " <" + EXTRA_FILTER_MSG_MASK + "> \n");
        help.append("    -es " + EXTRA_FILTER_SUBJECT_LENGTH + " <" + EXTRA_FILTER_SUBJECT_LENGTH
                + "> \n");
        help.append("    -es " + EXTRA_FILTER_PERIOD_BEGIN + " <" + EXTRA_FILTER_PERIOD_BEGIN
                + "> \n");
        help.append("    -es " + EXTRA_FILTER_PERIOD_END + " <" + EXTRA_FILTER_PERIOD_END + "> \n");
        help.append("    -es " + EXTRA_FILTER_READ_STATUS + " <" + EXTRA_FILTER_READ_STATUS
                + "> \n");
        help.append("    -es " + EXTRA_FILTER_PRIORITY_STATUS + " <" + EXTRA_FILTER_PRIORITY_STATUS
                + "> \n");
        help.append("    -es " + EXTRA_FILTER_ORIGINATOR + " <" + EXTRA_FILTER_ORIGINATOR + "> \n");
        help.append("    -es " + EXTRA_FILTER_RECIPIENT + " <" + EXTRA_FILTER_RECIPIENT + "> \n");
        help.append("    " + ACTION_GET_MESSAGE_LIST + "\n");
    }

    private void onAbort(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        client.abortOperation(serverInstanceId);
    }

    private void printHelp_Abort(StringBuilder help) {
        help.append("Abort current Obex operation:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_ABORT + "\n");
    }

    private void onGetMessage(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        byte charset = 0;
        try {
            charset = Byte.parseByte(intent.getStringExtra(EXTRA_CHARSET));
        } catch (Throwable t) {

        }

        String messageHandle = intent.getStringExtra(EXTRA_MESSAGE_HANDLE);
        try {
            charset = Byte.parseByte(intent.getStringExtra(EXTRA_CHARSET));
        } catch (Throwable t) {

        }
        boolean includeAttachments = false;
        try {
            includeAttachments = 1 == Integer.parseInt(intent
                    .getStringExtra(EXTRA_INCLUDE_ATTACHMENTS));
        } catch (Throwable t) {

        }

        Log.d(TAG, "Getting message from server instanceId:" + serverInstanceId);

        client.getMessage(serverInstanceId, messageHandle, charset, includeAttachments);
    }

    private void printHelp_GetMessage(StringBuilder help) {
        help.append("Get message from a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_MESSAGE_HANDLE + " <" + EXTRA_MESSAGE_HANDLE + "> \n");
        help.append("    -es " + EXTRA_CHARSET + " <" + EXTRA_CHARSET + "> \n");
        help.append("    -es " + EXTRA_INCLUDE_ATTACHMENTS + " <" + EXTRA_INCLUDE_ATTACHMENTS
                + "> \n");
        help.append("    " + ACTION_GET_MESSAGE + "\n");
    }

    private void onPushMessage(Intent intent) {

        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        byte charset = 0;
        try {
            charset = Byte.parseByte(intent.getStringExtra(EXTRA_CHARSET));
        } catch (Throwable t) {

        }

        String filepath = intent.getStringExtra(EXTRA_FILEPATH);
        try {
            charset = Byte.parseByte(intent.getStringExtra(EXTRA_CHARSET));
        } catch (Throwable t) {

        }

        boolean isRetry = false;
        try {
            isRetry = 1 == Integer.parseInt(intent.getStringExtra(EXTRA_IS_RETRY));
        } catch (Throwable t) {

        }

        boolean isAutoSend = false;
        try {
            isAutoSend = 1 == Integer.parseInt(intent.getStringExtra(EXTRA_IS_AUTOSEND));
        } catch (Throwable t) {

        }
        Log.d(TAG, "Pushing message to server instanceId:" + serverInstanceId);

        client.pushMessage(serverInstanceId, new File(filepath), charset, isRetry, isAutoSend);
    }

    private void printHelp_PushMessage(StringBuilder help) {
        help.append("Push message to a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_FILEPATH + " <" + EXTRA_FILEPATH + "> \n");
        help.append("    -es " + EXTRA_CHARSET + " <" + EXTRA_CHARSET + "> \n");
        help.append("    -es " + EXTRA_IS_RETRY + " <" + EXTRA_IS_RETRY + "> \n");
        help.append("    -es " + EXTRA_IS_AUTOSEND + " <" + EXTRA_IS_AUTOSEND + "> \n");
        help.append("    " + ACTION_PUSH_MESSAGE + "\n");
    }

    private void onUpdateInbox(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        client.updateInbox(serverInstanceId);
    }

    private void printHelp_UpdateInbox(StringBuilder help) {
        help.append("Update the Inbox of a MAP MSE INSTANCE:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_UPDATE_INBOX + "\n");

    }

    private void onSetMessageReadStatus(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        String messageHandle = intent.getStringExtra(EXTRA_MESSAGE_HANDLE);

        boolean isSet = false;
        try {
            isSet = 1 == Integer.parseInt(intent.getStringExtra(EXTRA_STATUS_SET));
        } catch (Throwable t) {

        }

        Log.d(TAG, "Setting message read status from server instanceId:" + serverInstanceId);

        client.setMessageReadStatus(serverInstanceId, messageHandle, isSet);
    }

    private void printHelp_SetMessageReadStatus(StringBuilder help) {
        help.append("Set a message's read status:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_MESSAGE_HANDLE + " <" + EXTRA_MESSAGE_HANDLE + "> \n");
        help.append("    -es " + EXTRA_STATUS_SET + " <" + EXTRA_STATUS_SET + "> \n");
        help.append("    " + ACTION_REGISTER_FOR_NOTIFICATION + "\n");

    }

    private void onSetMessageDeletedStatus(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        String messageHandle = intent.getStringExtra(EXTRA_MESSAGE_HANDLE);

        boolean isSet = false;
        try {
            isSet = 1 == Integer.parseInt(intent.getStringExtra(EXTRA_STATUS_SET));
        } catch (Throwable t) {

        }

        Log.d(TAG, "Setting message deleted status from server instanceId:" + serverInstanceId);

        client.setMessageDeletedStatus(serverInstanceId, messageHandle, isSet);
    }

    private void printHelp_SetMessageDeletedStatus(StringBuilder help) {
        help.append("Set a message's delete status:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    -es " + EXTRA_MESSAGE_HANDLE + " <" + EXTRA_MESSAGE_HANDLE + "> \n");
        help.append("    -es " + EXTRA_STATUS_SET + " <" + EXTRA_STATUS_SET + "> \n");
        help.append("    " + ACTION_REGISTER_FOR_NOTIFICATION + "\n");

    }

    private void onRegisterForNotification(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        client.registerForNotification(serverInstanceId);
    }

    private void printHelp_RegisterForNotification(StringBuilder help) {
        help.append("Register MAP MSE INSTANCE for notifications:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_REGISTER_FOR_NOTIFICATION + "\n");
    }

    private void onUnregisterForNotification(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }
        int serverInstanceId = parseServerInstanceId(intent);
        if (serverInstanceId < 0) {
            return;
        }

        client.unregisterForNotification(serverInstanceId);
    }

    private void printHelp_UnregisterForNotification(StringBuilder help) {
        help.append("Unregister MAP MSE INSTANCE for notifications:\n");
        help.append("    -es " + EXTRA_SERVER_INSTANCE_ID + " <" + EXTRA_SERVER_INSTANCE_ID
                + "> \n");
        help.append("    " + ACTION_UNREGISTER_FOR_NOTIFICATION + "\n");
    }

    private void onGetMseInstances(Intent intent) {
        BluetoothMapClient client = getClient(intent);
        if (client == null) {
            return;
        }

        String serverAddr = intent.getStringExtra(EXTRA_SERVER_ADDR);
        BluetoothDevice server = null;
        try {
            server = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(serverAddr);
        } catch (Throwable t) {
            Log.e(TAG, "", t);
        }
        if (server == null) {
            Log.w(TAG, "Invalid server " + serverAddr);
            return;
        }
        client.setServer(server);
        client.getMseInstances();
    }

    private void printHelp_GetMseInstances(StringBuilder help) {
        help.append("Get MSE instances information:\n");
        help.append("    " + ACTION_GET_MSE_INSTANCES + "\n");
    }

}
