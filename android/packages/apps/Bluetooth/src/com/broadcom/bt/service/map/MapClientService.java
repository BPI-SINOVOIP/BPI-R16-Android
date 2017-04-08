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

package com.broadcom.bt.service.map;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteCallbackList;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.broadcom.bt.map.BluetoothMapClient;
import com.broadcom.bt.map.IBluetoothMapClientCallback;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMessageParameterFilter;
import com.broadcom.bt.map.BluetoothMseInfo;

public class MapClientService extends ProfileService {
    private static final String TAG = MapClientServiceConfig.TAG_PREFIX + "MapClientService";
    private static final boolean DBG = MapClientServiceConfig.DBG;

    private static final int MESSAGE_STATUS_TYPE_READ = 0;
    private static final int MESSAGE_STATUS_TYPE_DELETED = 1;

    private static final Random sRandom = new Random();

    private class Connection {
      private String mDeviceAddress;
      private int mSessionId;
    }
    private List<Connection> mConnections = new ArrayList<Connection>();

    private static synchronized String createTransactionId() {
        return "" + System.currentTimeMillis() + "_" + sRandom.nextInt(9);
    }

    private static byte[] toNativeMessageByteArray(String messageHandle) {
        if (messageHandle == null || messageHandle.length() == 0) {
            return null;

        }
        for (int i = messageHandle.length(); i < 16; i++) {
            messageHandle = "0" + messageHandle;
        }
        byte[] b = new byte[8];
        for (int i = 0; i < b.length; i++) {
            long lByte = 0;
            String strByte = messageHandle.substring(i * 2, i * 2 + 2);
            try {
                lByte = Long.parseLong(strByte, 16);
            } catch (Throwable t) {
                return null;
            }
            b[i] = (byte) (lByte & 0xFF);
        }
        return b;
    }

    private static String fromNativeMessageByteArray(byte[] messageHandle) {
        if (messageHandle == null || messageHandle.length != 8) {
            Log.e(TAG, "fromNativeMessageByteArray(): bad messageHandle!!!!!!");
            return "";
        }

        return String.format("%02x%02x%02x%02x%02x%02x%02x%02x", messageHandle[0],
                messageHandle[1], messageHandle[2], messageHandle[3], messageHandle[4],
                messageHandle[5], messageHandle[6], messageHandle[7]);
    }

    static BluetoothMessageListFilter validateMessageListFilter(BluetoothMessageListFilter listFilter) {
        return listFilter;
    }

    static long validateMessageParamFilter(BluetoothMessageParameterFilter paramFilter) {
        return paramFilter.mParameterMask;
    }

    static MapClientService sService = null;

    private static native void classInitNative();

    static {
        classInitNative();
    }

    class MapEventHandler extends Handler {
        private static final int EVENT_NOTIFICATION_SERVER_STATE_CHANGED = 2000;
        private static final int EVENT_CONNECTION_STATE_CHANGED = 2001;
        private static final int EVENT_FOLDER_PATH_SET = 2002;
        private static final int EVENT_FOLDER_LIST_RESULT = 2003;
        private static final int EVENT_MESSAGE_LIST_RESULT = 2004;
        private static final int EVENT_GET_MESSAGE_RESULT = 2005;
        private static final int EVENT_MESSAGE_STATUS_RESULT = 2006;
        private static final int EVENT_NOTIFICATION_REGISTRATION_UPDATED = 2007;
        private static final int EVENT_NOTIFICATION = 2008;
        private static final int EVENT_PUSH_MESSAGE = 2009;
        private static final int EVENT_UPDATE_INBOX = 2010;
        private static final int EVENT_GET_MSE_INSTANCES_RESULT = 3000;

        private Message createEventMessage(int messageType, String clientId,
                BluetoothDevice server, int serverInstanceId) {
            Message m = obtainMessage(messageType);
            m.obj = server;
            m.arg1 = serverInstanceId;
            Bundle data = m.getData();
            data.putString("c", clientId);
            return m;
        }

        /**
         * @param m
         * @param server
         * @param serverInstanceId
         * @param clientId
         */
        private Message createEventMessage(int messageType, String clientId,
                BluetoothDevice server, int serverInstanceId, boolean success) {
            Message m = obtainMessage(messageType);
            m.obj = server;
            m.arg1 = serverInstanceId;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putBoolean("s", success);
            return m;
        }

        private String getClientId(Message m, Bundle data) {
            return data.getString("c");
        }

        private int getServerInstanceId(Message m, Bundle data) {
            return m.arg1;
        }

        private BluetoothDevice getServer(Message m, Bundle data) {
            return (BluetoothDevice) m.obj;
        }

        private boolean getSuccess(Message m, Bundle data) {
            return data.getBoolean("s", false);
        }

        void sendGetMseInstancesResultEvent(String clientId, BluetoothDevice server,
                BluetoothMseInfo[] mseinfo) {
            if (clientId == null) {
                Log.w(TAG, "sendGetMseInstancesResultEvent(): no client to send events back to...");
                return;
            }
            Message m = obtainMessage(EVENT_GET_MSE_INSTANCES_RESULT);
            m.obj = server;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putParcelableArray("m", mseinfo);
            sendMessage(m);
        }

        void sendNotificationServerStateChangedEvent(boolean isStartEvent, boolean success) {
            sendMessage(obtainMessage(EVENT_NOTIFICATION_SERVER_STATE_CHANGED,
                    isStartEvent ? 1 : 0, success ? 1 : 0));
        }

        void sendConnectionStateChangedEvent(String clientId, BluetoothDevice server,
                int serverInstanceId, boolean isConnectEvent, boolean success) {
            if (clientId == null) {
                Log.w(TAG, "sendConnectionStateChangedEvent(): no client to send events back to...");
                return;
            }
            Message m = createEventMessage(EVENT_CONNECTION_STATE_CHANGED, clientId, server,
                    serverInstanceId, success);
            m.getData().putBoolean("e", isConnectEvent);
            sendMessage(m);
        }

        void sendFolderPathSetEvent(String clientId, BluetoothDevice server, int serverInstanceId,
                boolean success, String currentFolderPath) {
            if (clientId == null) {
                Log.w(TAG, "sendFolderPathSetEvent(): no client to send events back to...");
                return;
            }
            Message m = createEventMessage(EVENT_FOLDER_PATH_SET, clientId, server,
                    serverInstanceId, success);
            m.getData().putString("p", currentFolderPath);
            sendMessage(m);
        }

        void sendFolderListResult(String clientId, BluetoothDevice server, int serverInstanceId,
                boolean success, int listSize, String filepath, boolean returnAsObject) {
            Log.d(TAG, "sendFolderListResult(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", success=" + success
                    + ", listSize=" + listSize + ", filePath=" + filepath + ", returnAsObject="
                    + returnAsObject);
            // Convert the full filepath to MapContentProvider content uri
            String contentUri = "";
            if (filepath != null && filepath.length() > 0) {
                File f = new File(filepath);
                contentUri = Uri.withAppendedPath(MapContentProvider.CONTENT_URL, f.getName())
                        .toString();
            }

            Message m = createEventMessage(EVENT_FOLDER_LIST_RESULT, clientId, server,
                    serverInstanceId, success);
            Bundle data = m.getData();
            data.putInt("l", listSize);
            data.putString("p", contentUri);
            data.putBoolean("o", returnAsObject);
            sendMessage(m);
        }

        void sendMessageListResult(String clientId, BluetoothDevice server, int serverInstanceId,
                boolean success, int listSize, boolean hasNewMessages, String filepath,
                boolean returnAsObject) {
            Log.d(TAG, "sendMessageListResult(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", success=" + success
                    + ", listSize=" + listSize + ", hasNewMessages=" + hasNewMessages
                    + ", filePath=" + filepath + ", returnAsObject=" + returnAsObject);
            // Convert the full filepath to MapContentProvider content uri
            String contentUri = "";
            if (filepath != null && filepath.length() > 0) {
                File f = new File(filepath);
                contentUri = Uri.withAppendedPath(MapContentProvider.CONTENT_URL, f.getName())
                        .toString();
            }
            Message m = createEventMessage(EVENT_MESSAGE_LIST_RESULT, clientId, server,
                    serverInstanceId, success);
            Bundle data = m.getData();
            data.putInt("l", listSize);
            data.putString("p", contentUri);
            data.putBoolean("o", returnAsObject);
            data.putBoolean("n", hasNewMessages);
            sendMessage(m);
        }

        void sendGetMessageResult(String clientId, BluetoothDevice server, int serverInstanceId,
                String messageHandle, boolean success, String filepath, boolean returnAsObject) {
            Log.d(TAG, "sendGetMessageResult(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", messageHandle=" + messageHandle
                    + ", success=" + success + ", filepath=" + filepath + ", returnAsObject="
                    + returnAsObject);
            // Convert the full filepath to MapContentProvider content uri
            String contentUri = "";
            if (filepath != null && filepath.length() > 0) {
                File f = new File(filepath);
                contentUri = Uri.withAppendedPath(MapContentProvider.CONTENT_URL, f.getName())
                        .toString();
            }
            Message m = createEventMessage(EVENT_GET_MESSAGE_RESULT, clientId, server,
                    serverInstanceId, success);
            Bundle data = m.getData();
            data.putString("p", contentUri);
            data.putBoolean("o", returnAsObject);
            data.putString("h", messageHandle);
            sendMessage(m);
        }

        void sendMessageStatusUpdated(String clientId, BluetoothDevice server,
                int serverInstanceId, byte statusType, String messageHandle, boolean success) {
            Log.d(TAG, "sendMessageStatusUpdated(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", statusType=" + statusType
                    + ", messageHandle=" + messageHandle + ", success=" + success);
            Message m = createEventMessage(EVENT_MESSAGE_STATUS_RESULT, clientId, server,
                    serverInstanceId, success);
            Bundle data = m.getData();
            data.putByte("t", statusType);
            data.putString("h", messageHandle);
            sendMessage(m);
        }

        void sendNotificationRegistrationUpdated(String clientId, BluetoothDevice server,
                int serverInstanceId, boolean isRegistrationRequest, boolean success) {
            Log.d(TAG, "sendNotificationRegistrationResult(): clientId=" + clientId + ", server="
                    + server + ", serverInstanceId=" + serverInstanceId + ", isRegistrationRequest"
                    + isRegistrationRequest + ", success=" + success);
            Message m = createEventMessage(EVENT_NOTIFICATION_REGISTRATION_UPDATED, clientId,
                    server, serverInstanceId, success);
            Bundle data = m.getData();
            data.putBoolean("r", isRegistrationRequest);
            sendMessage(m);
        }

        void sendPushMessageResult(String clientId, BluetoothDevice server, int serverInstanceId,
                boolean success, String messageHandle) {
            Log.d(TAG, "sendPushMessageResult(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", success=" + success
                    + ", messsageHandle=" + messageHandle);
            Message m = createEventMessage(EVENT_PUSH_MESSAGE, clientId, server, serverInstanceId,
                    success);
            Bundle data = m.getData();
            data.putString("h", messageHandle);
            sendMessage(m);
        }

        void sendUpdateInboxResult(String clientId, BluetoothDevice server, int serverInstanceId,
                boolean success) {
            Log.d(TAG, "sendUpdateInboxResult(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId + ", success=" + success);
            Message m = createEventMessage(EVENT_UPDATE_INBOX, clientId, server, serverInstanceId,
                    success);
            sendMessage(m);

        }

        void sendNotification(String clientId, BluetoothDevice server, int serverInstanceId,
                String contentUri) {
            Log.d(TAG, "sendNotification(): clientId=" + clientId + ", server=" + server
                    + ", serverInstanceId=" + serverInstanceId);
            Message m = createEventMessage(EVENT_NOTIFICATION, clientId, server, serverInstanceId);
            m.getData().putString("p", contentUri);
            sendMessage(m);
        }

        public void handleMessage(Message m) {
            Bundle data = m.getData();

            if (mCallbacks != null) {
                synchronized (mCallbacks) {
                    int cbcount = mCallbacks.beginBroadcast();

                    switch (m.what) {
                    case EVENT_NOTIFICATION_SERVER_STATE_CHANGED: {
                        for (int i = 0; i < cbcount; i++) {
                            IBluetoothMapClientCallback cb = mCallbacks.getBroadcastItem(i);
                            if (cb == null) {
                                continue;
                            }
                            try {
                                cb.onNotificationServerStateChange(m.arg1 == 1, m.arg2 == 1);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_CONNECTION_STATE_CHANGED: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending connection state change event to client "
                                        + clientId);
                                cb.onServerConnectionStateChange(server, serverInstanceId,
                                        data.getBoolean("e", false), success);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_FOLDER_PATH_SET: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        String currentFolderPath = data.getString("p", "");
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending folder path state change event to client "
                                        + clientId);
                                cb.onFolderPathSet(server, serverInstanceId, success,
                                        currentFolderPath);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                        break;
                    }

                    case EVENT_FOLDER_LIST_RESULT: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending folder list result to client " + clientId);
                                cb.onFolderListResult(server, serverInstanceId, success,
                                        data.getInt("l", 0), data.getString("p", ""),
                                        data.getBoolean("o", false));
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;

                    case EVENT_MESSAGE_LIST_RESULT: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending message list result to client " + clientId);
                                cb.onMessageListResult(server, serverInstanceId, success,
                                        data.getInt("l", 0), data.getBoolean("n", false),
                                        data.getString("p", ""), data.getBoolean("o", false));
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }

                    }
                        break;
                    case EVENT_GET_MESSAGE_RESULT: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending message result to client " + clientId);
                                cb.onGetMessageResult(server, serverInstanceId,
                                        data.getString("h", ""), success, data.getString("p", ""),
                                        data.getBoolean("o", false));
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }

                    }
                        break;
                    case EVENT_MESSAGE_STATUS_RESULT: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending message status result to client " + clientId);
                                cb.onMessageStatusUpdated(server, serverInstanceId,
                                        data.getByte("t", (byte) 0), data.getString("h", ""),
                                        success);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_NOTIFICATION_REGISTRATION_UPDATED: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending notification registration update to client "
                                        + clientId);
                                cb.onNotificationRegistrationStateChange(server, serverInstanceId,
                                        data.getBoolean("r", false), success);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }

                    }
                        break;
                    case EVENT_NOTIFICATION: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending notification to client " + clientId);
                                cb.onNotification(server, serverInstanceId, data.getString("p", ""));
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_PUSH_MESSAGE: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending push message result to client " + clientId);
                                cb.onPushMessageResult(server, serverInstanceId, success,
                                        data.getString("h", ""));
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_UPDATE_INBOX: {
                        String clientId = getClientId(m, data);
                        int serverInstanceId = getServerInstanceId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending message list result to client " + clientId);
                                cb.onUpdateInboxResult(server, serverInstanceId, success);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    case EVENT_GET_MSE_INSTANCES_RESULT: {
                        String clientId = getClientId(m, data);
                        BluetoothDevice server = getServer(m, data);
                        boolean success = getSuccess(m, data);
                        IBluetoothMapClientCallback cb = findCallbackWithClientId(clientId, cbcount);
                        if (cb != null) {
                            try {
                                Log.d(TAG, "Sending message list result to client " + clientId);
                                Parcelable[] p = data.getParcelableArray("m");
                                BluetoothMseInfo[] mseinfo = new BluetoothMseInfo[p == null ? 0 : p.length];
                                for (int i = 0; i < mseinfo.length; i++) {
                                    mseinfo[i] = (BluetoothMseInfo) p[i];
                                }
                                cb.onGetMseInstancesResult(server, mseinfo);
                            } catch (Throwable t) {
                                Log.w(TAG, "Error calling IBluetoothMapClientCallback", t);
                            }
                        }
                    }
                        break;
                    }
                    mCallbacks.finishBroadcast();

                }
            }
        }
    }

    class MapServiceHandler extends QueuedMessageHandler {

        private static final int MSG_NTS_START = 1;
        private static final int MSG_NTS_STOP = 2;
        private static final int MSG_CONNECT = 10;
        private static final int MSG_DISCONNECT = 11;
        private static final int MSG_DISCONNECT_ALL = 12;
        private static final int MSG_ABORT = 15;

        private static final int MSG_SET_FOLDER = 20;
        private static final int MSG_GET_FOLDER_LIST = 30;
        private static final int MSG_GET_MESSAGE_LIST = 40;
        private static final int MSG_GET_MESSAGE = 50;
        private static final int MSG_UPDATE_INBOX = 60;
        private static final int MSG_SET_MESSAGE_STATUS = 70;
        private static final int MSG_PUSH_MESSAGE = 80;
        private static final int MSG_REGISTER_NOTIFICATION = 90;
        private static final int MSG_UNREGISTER_NOTIFICATION = 100;

        private static final int MSG_GET_MSE_INSTANCES = 200;

        // Event messages are all urgent

        public String getTransactionIdFromRequest(Message m) {
            if (m == null) {
                return "";
            }
            return m.getData().getString("txid", "");
        }

        protected String getMessageName(int what) {
            switch (what) {
            case MSG_NTS_START:
                return "MSG_NTS_START";
            case MSG_NTS_STOP:
                return "MSG_NTS_STOP";
            case MSG_CONNECT:
                return "MSG_CONNECT";
            case MSG_DISCONNECT:
                return "MSG_DISCONNECT";
            case MSG_DISCONNECT_ALL:
                return "MSG_DISCONNECT_ALL";
            case MSG_SET_FOLDER:
                return "MSG_SET_FOLDER";
            case MSG_GET_FOLDER_LIST:
                return "MSG_GET_FOLDER_LISTING";
            case MSG_GET_MESSAGE_LIST:
                return "MSG_GET_MESSAGE_LIST";
            case MSG_GET_MESSAGE:
                return "MSG_GET_MESSAGE";
            case MSG_UPDATE_INBOX:
                return "MSG_UPDATE_INBOX";
            case MSG_SET_MESSAGE_STATUS:
                return "MSG_SET_MESSAGE_STATUS";
            case MSG_PUSH_MESSAGE:
                return "MSG_PUSH_MESSAGE";
            case MSG_REGISTER_NOTIFICATION:
                return "MSG_REGISTER_NOTIFICATION";
            case MSG_UNREGISTER_NOTIFICATION:
                return "MSG_UNREGISTER_NOTIFICATION";
            case MSG_GET_MSE_INSTANCES:
                return "MSG_GET_MSE_INSTANCES";
            case MSG_ABORT:
                return "MSG_ABORT";
            default:
                return "UNKNOWN_MSG(" + what + ")";
            }
        }

        /**
         * @param m
         * @param server
         * @param serverInstanceId
         * @param clientId
         */
        private Message createRequest(int messageType, String clientId, BluetoothDevice server,
                int serverInstanceId) {
            Message m = obtainMessage(messageType);
            m.obj = server;
            m.arg1 = serverInstanceId;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putString("txid", createTransactionId());
            return m;

        }

        private boolean createRequestAndSend(int messageType, String clientId,
                BluetoothDevice server, int serverInstanceId) {
            Message m = obtainMessage(messageType);
            m.obj = server;
            m.arg1 = serverInstanceId;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putString("txid", createTransactionId());
            return sendMessage(m);
        }

        private void addTransactionId(Message m) {
            Bundle data = m.getData();
            data.putString("txid", createTransactionId());
        }

        boolean startNotificationServer(String name) {
            Message m = obtainMessage(MSG_NTS_START);
            m.obj = name;
            m.arg1 = -1;
            addTransactionId(m);
            sendMessage(m);
            return true;
        }

        boolean stopNotificationServer() {
            Message m = obtainMessage(MSG_NTS_STOP);
            m.arg1 = -1;
            addTransactionId(m);
            sendMessage(m);
            return true;
        }

        boolean connect(String clientId, BluetoothDevice server, int serverInstanceId) {
            if (mConnections.size() > 0) return false;
            return createRequestAndSend(MSG_CONNECT, clientId, server, serverInstanceId);
        }

        boolean disconnect(String clientId, BluetoothDevice server, int serverInstanceId) {
            return createRequestAndSend(MSG_DISCONNECT, clientId, server, serverInstanceId);
        }

        boolean disconnectAll() {
            sendEmptyMessage(MSG_DISCONNECT_ALL);
            return true;
        }

        boolean setFolderPath(String clientId, BluetoothDevice server, int serverInstanceId,
                String path) {
            // Validate this is a valid folder path

            if (path == null) {
                Log.d(TAG, "setFolderPath(): path is null: not doing anything...");
                return false;
            }
            path = path.trim();

            if (path.length() <= 0) {
                Log.d(TAG, "setFolderPath(): path is null: not doing anything...");
                return false;
            }

            boolean isAbsolutePath = false;
            if (path.charAt(0) == '/') {
                isAbsolutePath = true;
            }

            LinkedList<String> folderPath = new LinkedList<String>();
            if (isAbsolutePath) {
                folderPath.add("/");
                String pathLC = path.toLowerCase();
                if (!pathLC.startsWith("/telecom")) {
                    Log.d(TAG, "setFolderPath(): adding telecom to path");
                    folderPath.add("telecom");
                    folderPath.add("msg");
                }
            }
            String[] pathElements = path.split("\\/");
            if (pathElements != null) {
                for (int i = 0; i < pathElements.length; i++) {
                    if (pathElements[i].length() == 0) {
                        Log.d(TAG, "setFolderPath(): ignoring '' in path");
                        continue;
                    } else if (pathElements[i].equals(".")) {
                        Log.d(TAG, "setFolderPath(): ignoring '.' in path");
                        continue;
                    }
                    Log.d(TAG, "setFolderPath(): adding " + pathElements[i] + " to path");
                    folderPath.add(pathElements[i]);
                }
            }
            MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
            synchronized (mgr) {
                MapServerConnection conn = mgr.getConnectionByInstanceId(server, serverInstanceId);
                if (conn != null) {
                    conn.setPendingFolderPath(folderPath);
                } else {
                    Log.w(TAG, "setFolderPath(): connection not found..Returning...");
                    return false;
                }
            }

            return createRequestAndSend(MSG_SET_FOLDER, clientId, server, serverInstanceId);
        }

        void continueFolderPath(String clientId, BluetoothDevice server, int serverInstanceId,
                String transactionId) {
            Message m = createRequest(MSG_SET_FOLDER, clientId, server, serverInstanceId);
            m.getData().putString("txid", transactionId);
            setUrgentMessage(m);
            sendMessage(m);
        }

        boolean getFolderList(String clientId, BluetoothDevice server, int serverInstanceId,
                int maxLength, int offset, boolean returnAsObject) {
            Message m = createRequest(MSG_GET_FOLDER_LIST, clientId, server, serverInstanceId);
            Bundle data = m.getData();
            data.putInt("m", maxLength);
            data.putInt("o", offset);
            data.putBoolean("r", returnAsObject);
            return sendMessage(m);
        }

        boolean getMessageList(String clientId, BluetoothDevice server, int serverInstanceId,
                int maxLength, int offset, BluetoothMessageListFilter listFilter,
                BluetoothMessageParameterFilter paramFilter, boolean returnAsObject) {
            Message m = createRequest(MSG_GET_MESSAGE_LIST, clientId, server, serverInstanceId);
            Bundle data = m.getData();
            data.putInt("m", maxLength);
            data.putInt("o", offset);
            data.putParcelable("l", listFilter);
            data.putParcelable("p", paramFilter);
            data.putBoolean("r", returnAsObject);
            return sendMessage(m);
        }

        boolean getMessage(String clientId, BluetoothDevice server, int serverInstanceId,
                String messageHandle, byte charset, boolean includeAttachments,
                boolean returnAsObject) {
            Message m = createRequest(MSG_GET_MESSAGE, clientId, server, serverInstanceId);
            Bundle data = m.getData();
            data.putString("h", messageHandle);
            data.putByte("cs", charset);
            data.putBoolean("a", includeAttachments);
            data.putBoolean("r", returnAsObject);
            return sendMessage(m);
        }

        boolean updateInbox(String clientId, BluetoothDevice server, int serverInstanceId) {
            return createRequestAndSend(MSG_UPDATE_INBOX, clientId, server, serverInstanceId);
        }

        boolean setMessageStatus(String clientId, BluetoothDevice server, int serverInstanceId,
                String messageHandle, byte statusType, boolean isSet) {
            Message m = createRequest(MSG_SET_MESSAGE_STATUS, clientId, server, serverInstanceId);
            Bundle data = m.getData();
            data.putString("h", messageHandle);
            data.putByte("t", statusType);
            data.putBoolean("s", isSet);
            return sendMessage(m);
        }

        boolean pushMessage(String clientId, BluetoothDevice server, int serverInstanceId,
                String contentUri, byte charset, boolean isRetry, boolean autoSend) {
            Message m = createRequest(MSG_PUSH_MESSAGE, clientId, server, serverInstanceId);
            Bundle data = m.getData();
            data.putString("u", contentUri);
            data.putByte("cs", charset);
            data.putBoolean("r", isRetry);
            data.putBoolean("s", autoSend);
            return sendMessage(m);
        }

        boolean registerForNotification(String clientId, BluetoothDevice server,
                int serverInstanceId) {
            return createRequestAndSend(MSG_REGISTER_NOTIFICATION, clientId, server,
                    serverInstanceId);
        }

        boolean unregisterForNotification(String clientId, BluetoothDevice server,
                int serverInstanceId) {
            return createRequestAndSend(MSG_UNREGISTER_NOTIFICATION, clientId, server,
                    serverInstanceId);
        }

        boolean abortOperation(String clientId, BluetoothDevice server, int serverInstanceId) {
            Message m = obtainMessage(MSG_ABORT);
            m.obj = server;
            m.arg1 = serverInstanceId;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putString("txid", createTransactionId());
            setPassThruMessage(m);
            return sendMessage(m);
        }

        boolean getMseInstances(String clientId, BluetoothDevice server) {
            Message m = obtainMessage(MSG_GET_MSE_INSTANCES);
            m.obj = server;
            m.arg1 = -1;
            Bundle data = m.getData();
            data.putString("c", clientId);
            data.putString("txid", createTransactionId());
            return sendMessage(m);
        }

        protected boolean processTimeoutMessage(Message m, int timeoutType) {
            Bundle data = m.getData();
            String clientId = data.getString("c", null);
            String transactionId = data.getString("tx", null);
            BluetoothDevice server = null;
            int serverInstanceId = m.arg1;
            if (timeoutType != MSG_NTS_START && timeoutType != MSG_NTS_STOP) {
                server = (BluetoothDevice) m.obj;
            }
            if (serverInstanceId >= 0) {
                MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
                synchronized (mgr) {
                    mgr.removePendingRequest(clientId, server, serverInstanceId, transactionId);
                }
            }

            switch (timeoutType) {
            case MSG_NTS_START: {
                mEventHandler.sendNotificationServerStateChangedEvent(true, false);
            }
                break;
            case MSG_NTS_STOP: {
                mEventHandler.sendNotificationServerStateChangedEvent(true, false);
            }
                break;

            case MSG_CONNECT: {
                mEventHandler.sendConnectionStateChangedEvent(clientId, server, serverInstanceId,
                        true, false);
            }
                break;
            case MSG_DISCONNECT: {
                mEventHandler.sendConnectionStateChangedEvent(clientId, server, serverInstanceId,
                        false, false);
            }
                break;
            case MSG_DISCONNECT_ALL: {
                // sendNotificationServerStateChangedEvent(true, false);
            }
            case MSG_SET_FOLDER: {
                String currentPath = "";
                if (clientId != null) {
                    MapServerConnection conn = MapServerConnectionManager.getInstance()
                            .getConnectionByInstanceId(server, serverInstanceId);
                    if (conn != null) {
                        currentPath = conn.getFolderPath();
                    }
                }
                mEventHandler.sendFolderPathSetEvent(clientId, server, serverInstanceId, false,
                        currentPath);
            }
                break;
            case MSG_GET_FOLDER_LIST: {
                mEventHandler.sendFolderListResult(clientId, server, serverInstanceId, false, 0,
                        "", data.getBoolean("r", true));
            }
                break;
            case MSG_GET_MESSAGE_LIST: {
                mEventHandler.sendMessageListResult(clientId, server, serverInstanceId, false, 0,
                        false, "", data.getBoolean("r", true));
            }
                break;
            case MSG_GET_MESSAGE: {
                String messageHandle = data.getString("h", "");
                mEventHandler.sendGetMessageResult(clientId, server, serverInstanceId,
                        messageHandle, false, "", data.getBoolean("r", true));
            }
                break;
            case MSG_PUSH_MESSAGE: {
                mEventHandler.sendPushMessageResult(clientId, server, serverInstanceId, false, "");
            }
                break;
            case MSG_UPDATE_INBOX: {
                mEventHandler.sendUpdateInboxResult(clientId, server, serverInstanceId, false);
            }
                break;
            case MSG_SET_MESSAGE_STATUS: {
                String messageHandle = data.getString("h", "");
                byte statusType = data.getByte("t", (byte) -1);
                mEventHandler.sendMessageStatusUpdated(clientId, server, serverInstanceId,
                        statusType, messageHandle, false);
            }
                break;
            case MSG_REGISTER_NOTIFICATION: {
                mEventHandler.sendNotificationRegistrationUpdated(clientId, server,
                        serverInstanceId, true, false);
            }
                break;
            case MSG_UNREGISTER_NOTIFICATION: {
                mEventHandler.sendNotificationRegistrationUpdated(clientId, server,
                        serverInstanceId, false, false);
            }
                break;
            case MSG_GET_MSE_INSTANCES:
                // FIXME:
                // mEventHandler.sendNotificationGetMseInstancesResult(clientId,server,false,null);
                break;
            }
            mHandler.processNextQueuedMessage();
            return true;
        }

        private int prepareAsyncSessionRequest(BluetoothDevice server, int serverInstanceId,
                String transactionId) {
            int sessionId = MapServerConnection.INVALID_SESSION_ID;
            MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
            MapServerConnection conn = null;
            synchronized (mgr) {
                conn = mgr.getConnectionByInstanceId(server, serverInstanceId);
                if (conn == null || !conn.isValidSession()) {
                    Log.w(TAG, "prepareAsyncSessionRequest: session not found...");
                } else {
                    sessionId = conn.mSessionId;
                    conn.addPendingRequest(transactionId);
                }
            }
            return sessionId;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public boolean processMessage(Message m) {
            boolean isRequestFinished = false;
            if (DBG) {
                Log.d(TAG, "handleMessage(): " + getMessageName(m.what));
            }
            switch (m.what) {
            case MSG_NTS_START: {
                String name = (String) m.obj;
                startTimeoutTimer(MapClientServiceConfig.TIMEOUT_START_STOP_MS);
                startNotificationServerNative(name);
            }
                break;

            case MSG_NTS_STOP: {
                startTimeoutTimer(MapClientServiceConfig.TIMEOUT_START_STOP_MS);
                stopNotificationServerNative();
            }
                break;
            case MSG_CONNECT: {
                String clientId = m.getData().getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
                synchronized (mgr) {
                    MapServerConnection conn = new MapServerConnection(clientId, server,
                            serverInstanceId, false);
                    mgr.addOrUpdateConnection(conn);
                    conn.addPendingRequest(transactionId);
                }
                startTimeoutTimer(MapClientServiceConfig.TIMEOUT_CONNECT_DISCONNECT_MS);
                connectNative(Utils.getByteAddress(server), serverInstanceId);
            }
                break;
            case MSG_DISCONNECT: {
                String clientId = m.getData().getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                int sessionId = MapServerConnection.INVALID_SESSION_ID;
                boolean hasError = false;
                // Lookup the sessionid
                MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
                synchronized (mgr) {
                    MapServerConnection conn = MapServerConnectionManager.getInstance()
                            .getConnectionByInstanceId(server, serverInstanceId);
                    if (conn == null) {
                        Log.w(TAG, "Unable to disconnect server " + server + ", serverInstanceId="
                                + serverInstanceId + ". Connection is null");
                        hasError = true;
                    } else if (!conn.mIsConnected || !conn.isValidSession()) {
                        Log.w(TAG, "Unable to disconnect server " + server + ", serverInstanceId="
                                + serverInstanceId + ". Not connected: sessionId="
                                + conn.mSessionId + ", isConnected=" + conn.mIsConnected);
                        if (conn.mCleanupPending) {
                            MapServerConnectionManager.getInstance().removeConnection(server,
                                    serverInstanceId);
                        }
                        hasError = true;
                    } else {
                        sessionId = conn.mSessionId;
                        conn.addPendingRequest(transactionId);
                    }
                }
                if (hasError) {
                    isRequestFinished = true;
                    mEventHandler.sendConnectionStateChangedEvent(clientId, server,
                            serverInstanceId, false, false);
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_CONNECT_DISCONNECT_MS);
                    disconnectNative(sessionId);
                }
            }
                break;
            case MSG_DISCONNECT_ALL: {
            }
                break;

            case MSG_SET_FOLDER: {
                String clientId = m.getData().getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                // Lookup the sessionid
                MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
                MapServerConnection conn = null;
                String folderName = null;
                int sessionId = MapServerConnection.INVALID_SESSION_ID;

                boolean hasError = false;
                synchronized (mgr) {
                    conn = mgr.getConnectionByInstanceId(server, serverInstanceId);
                    if (conn == null || !conn.isValidSession()) {
                        Log.w(TAG, getMessageName(m.what)
                                + ": unable to set folder path for server=" + server
                                + ", serverInstanceId=" + serverInstanceId + ": sessionId="
                                + sessionId + ", folderName=" + folderName);
                        hasError = true;
                    } else {
                        sessionId = conn.mSessionId;
                        folderName = conn.mFolderManager.peekPendingFolderName();
                        if (folderName == null || folderName.length() <= 0) {
                            Log.w(TAG, getMessageName(m.what)
                                    + ": no more folder names to set for server" + server
                                    + ", serverInstanceId=" + serverInstanceId + ": sessionId="
                                    + sessionId);
                            hasError = true;
                        } else {
                            conn.addPendingRequest(transactionId);
                        }
                    }
                }
                if (hasError) {
                    isRequestFinished = true;
                    mEventHandler.sendFolderPathSetEvent(clientId, server, serverInstanceId, false,
                            (conn != null) ? conn.getFolderPath() : "");
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_SET_FOLDER_MS);
                    Log.d(TAG, "Setting folder name to " + folderName);
                    if (folderName.equals("/")) {
                        setFolderPathNative(sessionId, true, "");
                    } else {
                        setFolderPathNative(sessionId, false, folderName);
                    }
                }
            }
                break;
            case MSG_GET_FOLDER_LIST: {
                Bundle data = m.getData();
                BluetoothDevice server = (BluetoothDevice) m.obj;
                boolean sendAsObject = data.getBoolean("r", true);
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");

                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendFolderListResult(data.getString("c", ""), server,
                            serverInstanceId, false, 0, "", sendAsObject);
                } else {
                    int maxListCount = data
                            .getInt("m", MapClientServiceConfig.MAX_FOLDER_LIST_SIZE);
                    if (maxListCount < 0) {
                        maxListCount = MapClientServiceConfig.MAX_FOLDER_LIST_SIZE;
                    }
                    int offset = data.getInt("o", 0);
                    if (offset < 0) {
                        offset = 0;
                    }
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_GET_FOLDER_LIST_MS);
                    getFolderListNative(sessionId, maxListCount, offset);
                }
            }
                break;
            case MSG_GET_MESSAGE_LIST: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                boolean sendAsObject = data.getBoolean("r", true);
                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendMessageListResult(clientId, server, serverInstanceId, false,
                            0, false, "", sendAsObject);
                } else {
                    int maxListCount = data.getInt("m",
                            MapClientServiceConfig.MAX_MESSAGE_LIST_SIZE);
                    if (maxListCount < 0) {
                        maxListCount = MapClientServiceConfig.MAX_MESSAGE_LIST_SIZE;
                    }
                    int offset = data.getInt("o", 0);
                    if (offset < 0) {
                        offset = 0;
                    }

                    BluetoothMessageListFilter listFilter = data.getParcelable("l");
                    BluetoothMessageParameterFilter paramFilter = data.getParcelable("p");
                    if (listFilter == null) {
                        listFilter = MapClientServiceConfig.getDefaultMessageListFilter();
                    } else {
                        listFilter = validateMessageListFilter(listFilter);
                    }

                    long paramFilterValue = MapClientServiceConfig
                            .getDefaultMessageParameterFilter();
                    if (paramFilter != null) {
                        paramFilterValue = validateMessageParamFilter(paramFilter);
                    }

                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_GET_MESSAGE_LIST_MS);
                    getMessageListNative(sessionId, maxListCount, offset, listFilter,
                            paramFilterValue);
                }
            }
                break;
            case MSG_GET_MESSAGE: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                boolean sendAsObject = data.getBoolean("r", true);
                String messageHandleStr = data.getString("h", "");
                byte[] messageHandle = toNativeMessageByteArray(messageHandleStr);
                if (messageHandle == null) {
                    Log.w(TAG, "MSG_GET_MESSAGE: messageHandle is null...");
                    isRequestFinished = true;
                    mEventHandler.sendGetMessageResult(clientId, server, serverInstanceId,
                            messageHandleStr, false, "", sendAsObject);
                    break;
                }
                for (int i = 0; i < messageHandle.length; i++) {
                    Log.d(TAG, "byte " + i + " = " + Integer.toHexString(messageHandle[i]));
                }

                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendGetMessageResult(clientId, server, serverInstanceId,
                            messageHandleStr, false, "", sendAsObject);
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_GET_MESSAGE_MS);
                    getMessageNative(sessionId, messageHandle,
                            data.getByte("cs", MapClientServiceConfig.DEFAULT_CHARSET),
                            data.getBoolean("a", false));
                }
            }
                break;
            case MSG_UPDATE_INBOX: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendUpdateInboxResult(clientId, server, serverInstanceId, false);
                    break;
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_UPDATE_INBOX_MS);
                    updateInboxNative(sessionId);
                }
            }
                break;
            case MSG_SET_MESSAGE_STATUS: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                byte statusType = data.getByte("t", (byte) -1);
                String messageHandleStr = data.getString("h", "");
                byte[] messageHandle = toNativeMessageByteArray(messageHandleStr);
                boolean isSet = data.getBoolean("s", false);

                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendMessageStatusUpdated(clientId, server, serverInstanceId,
                            statusType, messageHandleStr, false);
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_SET_MESSAGE_STATUS_MS);
                    setMessageStatusNative(sessionId, statusType, isSet, messageHandle);
                }
            }
                break;
            case MSG_PUSH_MESSAGE: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                String contentUri = data.getString("u", "");
                String fileName = "";
                try {
                    Uri uri = Uri.parse(contentUri);
                    fileName = uri.getLastPathSegment();
                } catch (Throwable t) {
                    Log.d(TAG, "Error parsing file", t);
                }

                File fileToPush = fileName == null ? null : new File(
                        MapClientServiceConfig.getDefaultTmpDir(), fileName);
                if (fileToPush == null || !fileToPush.isFile()) {
                    Log.w(TAG, "MSG_GET_MESSAGE: messageHandle is null...");
                    mEventHandler.sendPushMessageResult(data.getString("c", ""), server,
                            serverInstanceId, false, "");
                    isRequestFinished = true;
                    break;
                }

                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendPushMessageResult(clientId, server, serverInstanceId, false,
                            "");
                    break;
                } else {
                    pushMessageNative(sessionId, fileToPush.getAbsolutePath(),
                            data.getByte("cs", BluetoothMapClient.CHARSET_UTF8),
                            data.getBoolean("r", false), data.getBoolean("s", true));
                }
            }
                break;
            case MSG_REGISTER_NOTIFICATION: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");

                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendNotificationRegistrationUpdated(clientId, server,
                            serverInstanceId, true, false);
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_REGISTER_NOTIFICATION_MS);
                    registerForNotificationNative(sessionId);
                }
            }
                break;
            case MSG_UNREGISTER_NOTIFICATION: {
                Bundle data = m.getData();
                String clientId = data.getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                String transactionId = m.getData().getString("txid", "");
                int sessionId = prepareAsyncSessionRequest(server, serverInstanceId, transactionId);
                if (sessionId == MapServerConnection.INVALID_SESSION_ID) {
                    isRequestFinished = true;
                    mEventHandler.sendNotificationRegistrationUpdated(clientId, server,
                            serverInstanceId, false, false);
                } else {
                    startTimeoutTimer(MapClientServiceConfig.TIMEOUT_REGISTER_NOTIFICATION_MS);
                    unregisterForNotificationNative(sessionId);
                }
            }
                break;
            case MSG_ABORT: {
                // Abort operation is not async..It expects no response...
                Bundle data = m.getData();
                BluetoothDevice server = (BluetoothDevice) m.obj;
                int serverInstanceId = m.arg1;
                abortOperationNative(Utils.getByteAddress(server), serverInstanceId);
            }
                break;
            case MSG_GET_MSE_INSTANCES: {
                String clientId = m.getData().getString("c", "");
                BluetoothDevice server = (BluetoothDevice) m.obj;
                startTimeoutTimer(MapClientServiceConfig.TIMEOUT_GET_MSE_INSTANCES_MS);
                getMseInstancesNative(Utils.getByteAddress(server));
            }
                break;
            }

            if (isRequestFinished) {
                mHandler.processNextQueuedMessage();
            }
            return true;
        }
    }

    private boolean mNativeAvailable;
    private boolean mIsStopping;
    MapServiceHandler mHandler = new MapServiceHandler();
    MapEventHandler mEventHandler = new MapEventHandler();
    /**
     * Remote callback interfaces
     */
    private RemoteCallbackList<IBluetoothMapClientCallback> mCallbacks = new RemoteCallbackList<IBluetoothMapClientCallback>() {
        @Override
        public void onCallbackDied(IBluetoothMapClientCallback callback, Object cookie) {
            Log.d(TAG, "Client " + cookie + " disconnected...");
            cleanupClientConnection((String) cookie);
        }
    };

    private IBluetoothMapClientCallback findCallbackWithClientId(String clientId, int cbcount) {
        for (int i = 0; i < cbcount; i++) {
            String cookie = (String) mCallbacks.getBroadcastCookie(i);
            if (clientId.equals(cookie)) {
                IBluetoothMapClientCallback cb = mCallbacks.getBroadcastItem(i);
                return cb;
            }
        }
        return null;
    }

    private void cleanupClientConnection(String clientId) {
        Log.d(TAG, "cleanupClientConnection(): clientId=" + clientId);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();

        LinkedList<MapServerConnection> connlist = null;
        synchronized (mgr) {
            connlist = mgr.setConnectionsPendingCleanup(clientId);
        }

        if (connlist != null && connlist.size() > 0) {
            Iterator<MapServerConnection> i = connlist.iterator();
            while (i.hasNext()) {
                MapServerConnection conn = i.next();
                mHandler.disconnect(clientId, conn.mServer, conn.mServerInstanceId);
            }
        }
    }

    public boolean registerCallback(String clientId, IBluetoothMapClientCallback cb) {
        Log.d(TAG, "Registering client with clientId " + clientId);
        if (cb != null && mCallbacks != null) {
            return mCallbacks.register(cb, clientId);
        }
        return false;
    }

    public boolean unregisterCallback(String clientId, IBluetoothMapClientCallback cb) {
        Log.d(TAG, "Unregistering client with clientId " + clientId);
        if (cb != null && mCallbacks != null) {
            mCallbacks.unregister(cb);
        }
        cleanupClientConnection(clientId);
        return true;
    }

    protected IProfileServiceBinder initBinder() {
        return new MapClientServiceBinder(this);
    }

    protected synchronized boolean start() {
        Log.d(TAG, "start()");
        String version = "";
        try {
            version = getString(R.string.profile_map_version);
        } catch (Throwable t) {
            Log.w(TAG, "Error getting version", t);
        }
        Log.d(TAG, "*********Starting MAP Client Service...Version = " + version);

        // Initialize JNI
        initializeNative();
        mNativeAvailable = true;

        // Initialize temporary directory/path
        MapClientServiceConfig.initTmpDirectories(getFilesDir());

        // Init Map Content Provider
        MapContentProvider.init(MapClientServiceConfig.getDefaultTmpDir());

        sService = this;

        MapServerConnectionManager.init(this);
        return true;
    }

    private native void initializeNative();

    protected synchronized boolean stop() {
        Log.d(TAG, "stop()");
        mIsStopping = true;
        if (DBG)
            log("Stopping Bluetooth MapClientService.");
        sService = null;
        mCallbacks.kill();
        return true;
    }

    protected boolean cleanup() {
        if (DBG)
            log("Cleanup Bluetooth MapClientService");
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable = false;
        }
        MapServerConnectionManager.cleanup();
        return true;
    }

    private native void cleanupNative();

    public synchronized boolean isAvailable() {
        return !mIsStopping && super.isAvailable();
    }

    /**
     * Called from JNI to report profile service state
     *
     * @param isEnabled
     */
    private void onProfileStateChanged(boolean isEnabled) {
        Log.d(TAG, "onProfileStateChanged: isEnabled=" + isEnabled);
    }

    private native void startNotificationServerNative(String name);

    private native void stopNotificationServerNative();

    /**
     * Called from JNI to report notification server state change
     *
     * @param isEnabled
     */
    private void onNotificationServerStateChanged(boolean isStarted, int sessionId) {
        Log.d(TAG, "onNotificationServerStateChanged: isStarted=" + isStarted + ", sessionId="
                + sessionId);
        mHandler.stopTimeoutTimer(isStarted ? MapServiceHandler.MSG_NTS_START
                : MapServiceHandler.MSG_NTS_STOP);
        mEventHandler.sendNotificationServerStateChangedEvent(isStarted, true);
        mHandler.processNextQueuedMessage();
    }

    private native void connectNative(byte[] serverAddr, int instanceId);

    private native void disconnectNative(int sessionId);

    private native void abortOperationNative(byte[] serverAddr, int instanceId);

    private void onConnectionStateChanged(boolean isConnectedEvent, int serverInstanceId,
            int sessionId, String serverName, byte[] bdAddr) {

        BluetoothDevice server = null;
        try {
            server = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdAddr);
        } catch (Throwable t) {
            Log.e(TAG, "onConnectionStateChanged() error", t);
            return;
        }
        if (DBG) {
            Log.d(TAG, "onConnectionStateChanged(): isConnectedEvent=" + isConnectedEvent
                    + ", mseInstanceId=" + serverInstanceId + ", sessionId=" + sessionId
                    + ",serverName" + serverName + ",server=" + server.getAddress());
        }
        updateMapClientConnectionState(server, sessionId, isConnectedEvent);
        mHandler.stopTimeoutTimer(isConnectedEvent ? MapServiceHandler.MSG_CONNECT
                : MapServiceHandler.MSG_DISCONNECT);

        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();

        if (isConnectedEvent) {
            // For connect: we server,serverInstanceId, sessionId are valid
            // Update connection state and sessionId then send event
            mgr.updateConnectionStateByInstanceId(server, serverInstanceId, sessionId, true);
            mEventHandler.sendConnectionStateChangedEvent(
                    mgr.getClientId(server, serverInstanceId), server, serverInstanceId,
                    isConnectedEvent, true);

        } else {
            // For disconnect: we can only count on the server address and
            // sessionId being valid. The serverInstanceId must be looked up
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);

            if (conn == null) {
                Log.d(TAG, "onConnectionStateChanged(): connection not found with server=" + server
                        + ", sessionId=" + sessionId);
            } else if (conn.mCleanupPending) {
                // Delete connection object if it is marked for cleanup
                // Send event first, then remove connection
                mEventHandler.sendConnectionStateChangedEvent(conn.mClientId, server,
                        conn.mServerInstanceId, isConnectedEvent, true);
                mgr.removeConnection(server, conn.mServerInstanceId);
            } else {
                // Update connection state by sessionId then send event
                mgr.updateConnectionStateBySessionId(server, sessionId, false);
                mEventHandler.sendConnectionStateChangedEvent(conn.mClientId, server,
                        conn.mServerInstanceId, isConnectedEvent, true);
            }
        }
        mHandler.processNextQueuedMessage();
    }

    private void updateMapClientConnectionState(BluetoothDevice server, int sessionId,
            boolean isConnected) {
        int numConn = mConnections.size();
        int oldState = numConn > 0 ? BluetoothProfile.STATE_CONNECTED :
                                     BluetoothProfile.STATE_DISCONNECTED;
        int i;
        for (i = 0; i < numConn; i++) {
            Connection conn = mConnections.get(i);
            if (conn.mDeviceAddress.equals(server.getAddress()) && conn.mSessionId == sessionId) {
                break;
            }
        }

        if (i == numConn) {
            Log.d(TAG,"updateMapClientConnectionState(): connection not found...");
            // Connection not found
            if (isConnected) {
                Connection conn = new Connection();
                conn.mDeviceAddress = server.getAddress();
                conn.mSessionId = sessionId;
                mConnections.add(conn);
            }
        } else {
            // Connection found
            if (!isConnected) {
                mConnections.remove(i);
            }
        }

        int newState =  mConnections.size() > 0 ? BluetoothProfile.STATE_CONNECTED :
                                                  BluetoothProfile.STATE_DISCONNECTED;
        if (newState != oldState) {
            notifyProfileConnectionStateChanged(server, BluetoothProfile.MAP_CLIENT, newState,
                    oldState);
        }
    }

    public String getFolderPath(String clientId, BluetoothDevice server, int serverInstanceId) {
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();

        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionByInstanceId(server, serverInstanceId);
            if (conn != null) {
                return conn.getFolderPath();
            }
        }
        Log.w(TAG, "getFolderPath(): connection not found for server=" + server
                + ", serverInstanceId=" + serverInstanceId);
        return null;
    }

    public native void setFolderPathNative(int sessionId, boolean setToRoot, String folderName);

    /*
     * { onFolderPathSet(sessionId, true); return true; }
     */

    private void onFolderPathSet(int sessionId, boolean success) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_SET_FOLDER);

        Log.d(TAG, "onFolderPathSet(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        String currentFolderPath = null;
        boolean hasMoreFolders = false;
        boolean hasError = false;
        String transactionId = null;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onFolderPathSet(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            transactionId = mHandler.getTransactionIdFromRequest(mHandler.getPendingMessage());
            // Check the connection has the current request pending
            if (!conn.hasPendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer part of the connection...");
                return;
            }
            conn.mFolderManager.addPendingFolderNameToCurrentPath();
            hasMoreFolders = conn.mFolderManager.hasPendingFolderName();
            if (!hasMoreFolders) {
                currentFolderPath = conn.mFolderManager.getCurrentPath();
            }
        }

        if (hasMoreFolders) {
            Log.d(TAG, "onFolderPathSet(): processing more folders...");
            mHandler.continueFolderPath(clientId, server, serverInstanceId, transactionId);
        } else {
            Log.d(TAG, "onFolderPathSet(): no more folders...Returning result");
            mEventHandler.sendFolderPathSetEvent(clientId, server, serverInstanceId, success,
                    currentFolderPath);
        }
        Log.d(TAG, "Processing nexted queued message...");
        mHandler.processNextQueuedMessage();
    }

    private native void getFolderListNative(int sessionId, int maxLength, int offset);

    private void onGetFolderListResult(int sessionId, boolean success, int listSize, String filePath) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_GET_FOLDER_LIST);

        Log.d(TAG, "onGetFolderListingResult(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        boolean sendAsObject = true;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onGetFolderListResult(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }

            sendAsObject = pendingRequest.getData().getBoolean("r", false);
        }
        mEventHandler.sendFolderListResult(clientId, server, serverInstanceId, success, listSize,
                filePath, sendAsObject);
        mHandler.processNextQueuedMessage();
    }

    private native void getMessageListNative(int sessionId, int maxLength, int offset,
            BluetoothMessageListFilter listFilter, long paramFilter);

    private void onGetMessageListResult(int sessionId, boolean success, int listSize,
            boolean hasNewMessages, String filePath) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_GET_MESSAGE_LIST);

        Log.d(TAG, "onGetMessageListResult(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        boolean sendAsObject = true;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onGetMessageListResult(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }

            sendAsObject = pendingRequest.getData().getBoolean("r", false);
        }
        mEventHandler.sendMessageListResult(clientId, server, serverInstanceId, success, listSize,
                hasNewMessages, filePath, sendAsObject);
        mHandler.processNextQueuedMessage();
    }

    private native void getMessageNative(int sessionId, byte[] messageHandle, byte charset,
            boolean includeAttachments);

    private void onGetMessageResult(int sessionId, boolean success, byte[] messageHandle,
            String filePath) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_GET_MESSAGE);

        Log.d(TAG, "onGetMessageResult(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        boolean sendAsObject = true;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onGetMessageResult(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }
            sendAsObject = pendingRequest.getData().getBoolean("r", false);
        }
        String messageHandleStr = fromNativeMessageByteArray(messageHandle);
        mEventHandler.sendGetMessageResult(clientId, server, serverInstanceId, messageHandleStr,
                success, filePath, sendAsObject);
        mHandler.processNextQueuedMessage();
    }

    private native void pushMessageNative(int sessionId, String filePath, byte charset,
            boolean isRetry, boolean autoSend);

    private void onPushMessageResult(int sessionId, boolean success, byte[] messageHandle) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_PUSH_MESSAGE);

        Log.d(TAG, "onPushMessageResult(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onPushMessageResult(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }
        }
        String messageHandleStr = success ? fromNativeMessageByteArray(messageHandle) : "";
        mEventHandler.sendPushMessageResult(clientId, server, serverInstanceId, success,
                messageHandleStr);
        mHandler.processNextQueuedMessage();
    }

    private native void updateInboxNative(int sessionId);

    private void onUpdateInboxResult(int sessionId, boolean success) {
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_UPDATE_INBOX);

        Log.d(TAG, "onUpdateInboxResult(): sessionId=" + sessionId + ", success=" + success);
        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onUpdateInboxResult(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }
        }
        mEventHandler.sendUpdateInboxResult(clientId, server, serverInstanceId, success);
        mHandler.processNextQueuedMessage();
    }

    private native void registerForNotificationNative(int sessionId);

    private native void unregisterForNotificationNative(int sessionId);

    private void onNotificationRegistrationUpdated(int sessionId, boolean success) {

        Log.d(TAG, "onNotificationRegistrationUpdated(): sessionId=" + sessionId
                + ",isRegistrationRequest=" + ", success=" + success);

        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        int requestType = -1;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onNotificationRegistrationUpdated(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }
            requestType = pendingRequest.what;
        }

        boolean isRegistrationRequest = requestType == MapServiceHandler.MSG_REGISTER_NOTIFICATION;
        if (isRegistrationRequest) {
            mHandler.stopTimeoutTimer(MapServiceHandler.MSG_REGISTER_NOTIFICATION);
        } else {
            mHandler.stopTimeoutTimer(MapServiceHandler.MSG_UNREGISTER_NOTIFICATION);
        }
        mEventHandler.sendNotificationRegistrationUpdated(clientId, server, serverInstanceId,
                isRegistrationRequest, success);
        mHandler.processNextQueuedMessage();

    }

    private native void setMessageStatusNative(int sessionId, byte statusType, boolean isSet,
            byte[] messageHandle);

    private void onMessageStatusUpdated(int sessionId, boolean success) {
        byte statusType = 0;
        String messageHandleStr = null;
        Log.d(TAG, "onMessageStatusUpdated(): sessionId=" + sessionId + ", success=" + success);
        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_SET_MESSAGE_STATUS);

        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        int serverInstanceId = MapServerConnection.INVALID_INSTANCE_ID;
        synchronized (mgr) {
            MapServerConnection conn = mgr.getConnectionBySessionId(sessionId);
            if (conn == null) {
                Log.w(TAG, "onMessageStatusUpdated(): invalid sessionId");
                return;
            }
            clientId = conn.mClientId;
            server = conn.mServer;
            serverInstanceId = conn.mServerInstanceId;
            Message pendingRequest = mHandler.getPendingMessage();
            String transactionId = mHandler.getTransactionIdFromRequest(pendingRequest);
            if (!conn.removePendingRequest(transactionId)) {
                Log.e(TAG, "Request '" + transactionId + "' is no longer pending...");
                return;
            }
            Bundle data = pendingRequest.getData();
            statusType = data.getByte("t", (byte) -1);
            messageHandleStr = data.getString("h", "");
        }
        mEventHandler.sendMessageStatusUpdated(clientId, server, serverInstanceId, statusType,
                messageHandleStr, success);
        mHandler.processNextQueuedMessage();
    }

    private void onNotificationConnectionStateChanged(boolean isConnectedEvent, int sessionId,
            String removeDeviceName, byte[] bdAddr) {

        BluetoothDevice remoteDevice = null;
        try {
            remoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdAddr);
        } catch (Throwable t) {
            Log.e(TAG, "onNotificationConnectionStateChanged() error", t);
            return;
        }
        if (DBG) {
            Log.d(TAG, "onNotificationConnectionStateChanged(): isConnectedEvent="
                    + isConnectedEvent + ", sessionId=" + sessionId + ",remoteDeviceName"
                    + removeDeviceName + ",remoteDevice=" + remoteDevice.getAddress());
        }

        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        synchronized (mgr) {
            if (isConnectedEvent) {
                mgr.setNotificationConnected(String.valueOf(sessionId), remoteDevice);
            } else {
                mgr.setNotificationDisconnected(String.valueOf(sessionId));
            }
        }
    }

    private void onNotification(int serverInstanceId, int sessionId, String filepath) {

        Log.d(TAG, "onNotification(): serverInstanceId=" + serverInstanceId + ", sessionId="
                + sessionId + ",isRegistrationRequest=" + ", filepath=" + filepath);

        MapServerConnectionManager mgr = MapServerConnectionManager.getInstance();
        String clientId = null;
        BluetoothDevice server = null;
        synchronized (mgr) {
            server = mgr.getNotificationRemoteDevice(String.valueOf(sessionId));
            if (server == null) {
                Log.w(TAG, "onNotification(): invalid sessionId");
                return;
            }
            clientId = mgr.getClientId(server, serverInstanceId);
        }

        // Convert the full filepath to MapContentProvider content uri
        String contentUri = "";
        if (filepath != null) {
            File f = new File(filepath);
            contentUri = Uri.withAppendedPath(MapContentProvider.CONTENT_URL, f.getName())
                    .toString();
        }
        if (clientId != null) {
            mEventHandler.sendNotification(clientId, server, serverInstanceId, contentUri);
        }
    }

    native void getMseInstancesNative(byte[] serverAddr);

    private void onGetMseInstancesResult(BluetoothMseInfo[] mseInfo) {

        mHandler.stopTimeoutTimer(MapServiceHandler.MSG_GET_MSE_INSTANCES);

        Message msg = mHandler.mPendingMessage;
        String clientId = msg.getData().getString("c", null);
        BluetoothDevice server = (BluetoothDevice) msg.obj;

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
        mEventHandler.sendGetMseInstancesResultEvent(clientId, server, mseInfo);
        mHandler.processNextQueuedMessage();
    }
}
