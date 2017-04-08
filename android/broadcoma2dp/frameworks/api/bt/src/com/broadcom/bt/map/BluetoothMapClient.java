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
package com.broadcom.bt.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;

import com.broadcom.bt.util.bmsg.BMessage;
import com.broadcom.bt.util.io.filefilter.OrFileFilter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Main API used by MAP client application to interface with a MAP server.
 *
 * <p> The MAP client provides Proxy object and public APIs communicate with MAP Server.
 * An application obtains an instance to this class by calling
 * {@link #getProxy(android.content.Context, android.bluetooth.BluetoothProfile.ServiceListener)
 * getProxy}.
 * All API calls in MAP client are asynchronous calls. An application that invokes any
 * of the public APIs will not block waiting for the MAP client to return.
 * The application must register an instance of
 * {@link IBluetoothMapClientEventHandler} to receive callbacks to API requests.
 *
 */
public class BluetoothMapClient implements BluetoothProfile, ServiceConnection {
    /**
     * @hide
     */
    static final boolean DBG = true;

    /**
     * Constant for specifying native character encoding in a BMessage.
     */
    public static final byte CHARSET_NATIVE = 0;

    /**
     * Constant for specifying UTF-8 encoding in a BMessage.
     */
    public static final byte CHARSET_UTF8 = 1;

    private static final String TAG = "BtMap.BluetoothMapClient";

    private static final byte STATUS_TYPE_READ = 0;
    private static final byte STATUS_TYPE_DELETED = 1;

    private static final int BIND_STATE_UNBOUND = 0;
    private static final int BIND_STATE_BINDING = 1;
    private static final int BIND_STATE_BOUND = 2;
    private static final int BIND_STATE_UNBINDING = 3;

    private static final Uri URI_CONTENT_PROVIDER = Uri.parse("content://com.broadcom.bt.map");

    private static String getDebugStackTrace(StackTraceElement st) {
        return "" + st.getClassName() + "." + st.getMethodName() + "(): [" + st.getLineNumber()
                + ", " + Thread.currentThread().getName() + "]";
    }

    private static void debugPrintStackTrace() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        if (st != null) {
            Log.d(TAG, "Called from..");
            for (int i = 3; i < 5 && i < st.length; i++) {
                Log.d(TAG, getDebugStackTrace(st[i]));
            }
        }
    }

    private Random mRandom = new Random();

    private Context mContext;
    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;

    private IBluetoothMapClient mService;
    private IBluetoothManager mManagerService;
    private boolean mIsClosed;
    private boolean mPendingClose;// This flag is set if a close() is requested
                                  // while binding

    private int mBindingState;
    private BluetoothDevice mMse;

    private String mClientId;
    private IBluetoothMapClientEventHandler mEventHandler;

    private IBluetoothStateChangeCallback mStateChangeCallback = new IBluetoothStateChangeCallback.Stub() {

        @Override
        public void onBluetoothStateChange(boolean on) throws RemoteException {
            // Handle enable request to bind again.
            if (on) {
                Log.d(TAG, "onBluetoothStateChange(on)");
                synchronized (BluetoothMapClient.this) {
                    if (mBindingState != BIND_STATE_UNBOUND) {
                        Log.d(TAG, "onBluetoothStateChange(on): "
                                + "bind state is not  BIND_STATE_UNBOUND..Skipping bind...");
                    } else {
                        mBindingState = BIND_STATE_BINDING;
                        Log.d(TAG, "onBluetoothStateChange(on): binding to MapService...");
                        Log.e(TAG, "mConnection=" + this);

                        try {
                            if (!mContext.bindService(
                                    new Intent(IBluetoothMapClient.class.getName()),
                                    BluetoothMapClient.this, 0)) {
                                mBindingState = BIND_STATE_UNBOUND;
                                Log.e(TAG, "Could not bind to Bluetooth Map Service");
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error binding to connection", t);
                            Log.e(TAG, "mConnection=" + BluetoothMapClient.this);
                            Log.e(TAG, "mContext=" + mContext);
                        }
                    }
                }
            } else {
                if (DBG) {
                    Log.d(TAG, "onBluetoothStateChange(off)...");
                    debugPrintStackTrace();
                }
                synchronized (BluetoothMapClient.this) {
                    try {
                        if (mBindingState == BIND_STATE_UNBOUND) {
                            Log.d(TAG, "onBluetoothStateChange(off): "
                                    + "state = BIND_STATE_UNBOUND.. Skipping unbind..");
                            return;
                        } else if (mBindingState == BIND_STATE_BINDING) {
                            Log.d(TAG, "onBluetoothStateChange(off): "
                                    + "state = BIND_STATE_BINDING.. Setting mPendingClose..");
                            mPendingClose = true;
                            return;

                        }
                        mBindingState = BIND_STATE_UNBOUND;
                        mContext.unbindService(BluetoothMapClient.this);
                        mService = null;
                    } catch (Throwable t) {
                        Log.e(TAG, "Error unbinding from connection", t);
                        mBindingState = BIND_STATE_BOUND;
                    }
                }
            }
        }
    };

    private IBluetoothMapClientCallback mCallback = new IBluetoothMapClientCallback.Stub() {

        @Override
        public void onServerConnectionStateChange(BluetoothDevice server, int instanceId,
                boolean isConnect, boolean success) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    if (isConnect) {
                        eventHandler.onServerConnected(server, instanceId, success);
                    } else {
                        eventHandler.onServerDisconnected(server, instanceId, success);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onNotificationServerStateChange(boolean isStart, boolean success)
                throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    if (isStart) {
                        eventHandler.onNotificationServerStarted(success);

                    } else {
                        eventHandler.onNotificationServerStopped(success);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }

            }
        }

        @Override
        public void onFolderPathSet(BluetoothDevice server, int serverInstanceId, boolean success,
                String currentFolderPath) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onFolderPathSet(server, serverInstanceId, success,
                            currentFolderPath);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }

            }
        }

        @Override
        public void onFolderListResult(BluetoothDevice server, int serverInstanceId,
                boolean success, int listLength, String uri, boolean returnAsObject)
                throws RemoteException {
            Uri contentUri = null;
            if (uri != null && uri.length() > 0) {
                try {
                    contentUri = Uri.parse(uri);
                } catch (Throwable t) {
                    Log.w(TAG, "Error parsing Map Content Uri", t);
                }
            }
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    if (returnAsObject) {
                        eventHandler.onFolderListResult(server, serverInstanceId, success,
                                listLength, parseXMLFolderList(contentUri));
                    } else {
                        eventHandler.onFolderListResult(server, serverInstanceId, success,
                                listLength, contentUri);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        private BluetoothFolderInfo[] parseXMLFolderList(Uri contentUri) {
            BluetoothFolderInfo[] folderInfo = null;
            if (contentUri != null) {
                try {
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
                    InputStream is = mContext.getContentResolver().openInputStream(contentUri);
                    Document doc = docBuilder.parse(is);
                    doc.getDocumentElement().normalize();

                    Log.d(TAG, "parseXMLFolderList() root: " +
                            doc.getDocumentElement().getNodeName());
                    NodeList nodes = doc.getElementsByTagName("folder");
                    folderInfo = new BluetoothFolderInfo[nodes.getLength()];

                    for (int i = 0; i < folderInfo.length; i++) {
                        Element element = (Element) nodes.item(i);
                        folderInfo[i] = new BluetoothFolderInfo();
                        folderInfo[i].mVirtualName = element.getAttribute("name");
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error parsing folder list XML doc", ex);
                }
            }
            return folderInfo;
        }

        @Override
        public void onGetMseInstancesResult(BluetoothDevice server, BluetoothMseInfo[] mseInfo)
                throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onGetMseInstancesResult(server, mseInfo);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onMessageListResult(BluetoothDevice server, int serverInstanceId,
                boolean success, int listLength, boolean hasNewMessages, String uri,
                boolean returnAsObject) throws RemoteException {
            Uri contentUri = null;
            if (uri != null && uri.length() > 0) {
                try {
                    contentUri = Uri.parse(uri);
                } catch (Throwable t) {
                    Log.w(TAG, "Error parsing Map Content Uri", t);
                }
            }
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    if (returnAsObject) {
                        eventHandler.onMessageListResult(server, serverInstanceId, success,
                                listLength, hasNewMessages, parseXMLMessageList(contentUri));
                    } else {
                        eventHandler.onMessageListResult(server, serverInstanceId, success,
                                listLength, hasNewMessages, contentUri);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        private BluetoothMessageInfo[] parseXMLMessageList(Uri contentUri) {
            BluetoothMessageInfo[] messageInfo = null;
            if (contentUri != null) {
                try {
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
                    InputStream is = mContext.getContentResolver().openInputStream(contentUri);
                    Document doc = docBuilder.parse(is);
                    doc.getDocumentElement().normalize();

                    Log.d(TAG, "parseXMLMessageList() root: " +
                            doc.getDocumentElement().getNodeName());
                    NodeList nodes = doc.getElementsByTagName("msg");
                    messageInfo = new BluetoothMessageInfo[nodes.getLength()];

                    for (int i = 0; i < messageInfo.length; i++) {
                        Element element = (Element) nodes.item(i);
                        messageInfo[i] = new BluetoothMessageInfo();
                        messageInfo[i].mMsgHandle = element.getAttribute("handle");
                        messageInfo[i].setSubject(element.getAttribute("subject"), 0);
                        messageInfo[i].mDateTime = element.getAttribute("datetime");
                        BluetoothPersonInfo sender = new BluetoothPersonInfo();
                        sender.mDisplayName = element.getAttribute("sender_name");
                        messageInfo[i].setSender(element.getAttribute("sender_addressing"), sender);
                        BluetoothPersonInfo recipient = new BluetoothPersonInfo();
                        recipient.mDisplayName = element.getAttribute("recipient_name");
                        messageInfo[i].addRecipient(element.getAttribute("recipient_addressing"),
                                recipient);
                        String type = element.getAttribute("type");
                        if (type == null) {
                            messageInfo[i].mMsgType = 0;
                        } else if (type.equals("EMAIL")) {
                            messageInfo[i].mMsgType = BluetoothMessageInfo.MSG_TYPE_EMAIL;
                        } else if (type.equals("SMS_GSM")) {
                            messageInfo[i].mMsgType = BluetoothMessageInfo.MSG_TYPE_SMS_GSM;
                        } else if (type.equals("SMS_CDMA")) {
                            messageInfo[i].mMsgType = BluetoothMessageInfo.MSG_TYPE_SMS_CDMA;
                        } else if (type.equals("MMS")) {
                            messageInfo[i].mMsgType = BluetoothMessageInfo.MSG_TYPE_MMS;
                        }
                        String size = element.getAttribute("size");
                        messageInfo[i].mMsgSize = Integer.parseInt(size);
                        String read = element.getAttribute("read");
                        if (read != null && read.equals("yes")) {
                            messageInfo[i].mIsRead = true;
                        }
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error parsing message list XML doc", ex);
                }
            }
            return messageInfo;
        }

        @Override
        public void onGetMessageResult(BluetoothDevice server, int serverInstanceId,
                String messageHandle, boolean success, String uri, boolean returnAsObject)
                throws RemoteException {
            Uri contentUri = null;
            if (uri != null && uri.length() > 0) {
                try {
                    contentUri = Uri.parse(uri);
                } catch (Throwable t) {
                    Log.w(TAG, "Error parsing Map Content Uri", t);
                }
            }
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    if (returnAsObject) {
                        BMessage bMessage = null;
                        ParcelFileDescriptor pfd = null;
                        if (contentUri != null) {
                            try {
                                pfd = mContext.getContentResolver().openFileDescriptor(contentUri, "r");
                                if (pfd != null) {
                                    bMessage = BMessage.parse(pfd.getFd());
                                    pfd.close();
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "onGetMessageResult: error parsing for bMessage from "
                                        + contentUri, t);
                            }
                        }
                        if (bMessage == null) {
                            Log.e(TAG, "Null bMessage");
                            eventHandler.onGetMessageResult(server, serverInstanceId,
                                    messageHandle, false, (BMessage) null);
                        } else {
                            eventHandler.onGetMessageResult(server, serverInstanceId,
                                    messageHandle, success, bMessage);
                        }

                    } else {
                        eventHandler.onGetMessageResult(server, serverInstanceId, messageHandle,
                                success, contentUri);

                    }
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onUpdateInboxResult(BluetoothDevice server, int serverInstanceId,
                boolean success) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onUpdateInboxResult(server, serverInstanceId, success);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onNotificationRegistrationStateChange(BluetoothDevice server, int instanceId,
                boolean isRegistration, boolean success) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onNotificationRegistrationStateChange(server, instanceId,
                            isRegistration, success);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onMessageStatusUpdated(BluetoothDevice server, int serverInstanceId,
                int statusType, String messageHandle, boolean success) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onMessageStatusUpdated(server, serverInstanceId, statusType,
                            messageHandle, success);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onPushMessageResult(BluetoothDevice server, int serverInstanceId,
                boolean success, String messageHandle) throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                try {
                    eventHandler.onPushMessageResult(server, serverInstanceId, success,
                            messageHandle);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        @Override
        public void onNotification(BluetoothDevice server, int serverInstanceId, String uri)
                throws RemoteException {
            IBluetoothMapClientEventHandler eventHandler = mEventHandler;
            if (eventHandler != null) {
                Uri contentUri = null;
                if (uri != null && uri.length() > 0) {
                    try {
                        contentUri = Uri.parse(uri);
                    } catch (Throwable t) {
                        Log.w(TAG, "Error parsing Map Content Uri", t);
                    }
                }
                try {
                    eventHandler.onNotification(server, serverInstanceId, contentUri);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

    };

    /**
     * Callback method used internally.
     *
     * @hide
     */
    public void onServiceConnected(ComponentName className, IBinder service) {
        if (DBG)
            Log.d(TAG, "BluetoothMap Proxy object connected");
        boolean doClose = false;
        synchronized (BluetoothMapClient.this) {
            mService = IBluetoothMapClient.Stub.asInterface(service);
            try {
                mService.registerCallback(mClientId, mCallback);
            } catch (Throwable t) {
                Log.e(TAG, "onServiceConnected(): Unable to register callback", t);
            }
            mBindingState = BIND_STATE_BOUND;
            doClose = mPendingClose;
            mPendingClose = false;
        }

        if (mServiceListener != null) {
            mServiceListener.onServiceConnected(MAP_CLIENT, BluetoothMapClient.this);
        }

        if (doClose) {
            // Can be set if close() was called while service is binding;
            close();
            mPendingClose = false;
        }

    }

    /**
     * Callback method used internally.
     *
     * @hide
     */
    public void onServiceDisconnected(ComponentName className) {
        if (DBG)
            Log.d(TAG, "BluetoothMap Proxy object disconnected");
        synchronized (BluetoothMapClient.this) {
            mService = null;
            mBindingState = BIND_STATE_UNBOUND;
            // mServiceConnected=false;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(MAP_CLIENT);
            }
        }
    }

    /**
     * Retrieves an instance of the BluetoothMapClient object.
     *
     * @param ctx
     *            application context.
     * @param listener
     *            a callback object invoked when the proxy object is connected
     *            or disconnected to the MapClient service.
     * @return null if unable to retrieve a BluetoothMapClient instance proxy
     *         retrieval.
     */
    public static BluetoothMapClient getProxy(Context ctx, ServiceListener listener) {
        if (DBG) {
            Log.d(TAG, "getProxy() ctx = " + (ctx == null ? "null" : ctx) + "listener ="
                    + (listener == null ? "null" : listener));
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            if (st != null) {
                Log.d(TAG, "Called from..");
                for (int i = 3; i < 5 && i < st.length; i++) {
                    Log.d(TAG, getDebugStackTrace(st[i]));
                }
            }
        }
        BluetoothMapClient p = null;
        try {
            p = new BluetoothMapClient(ctx, listener);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to get MAP Proxy", t);
            return null;
        }
        return p;
    }

    /**
     * Create a BluetoothPan proxy object for interacting with the local
     * Bluetooth Service which handles the Pan profile.
     *
     * @hide
     *
     */
    BluetoothMapClient(Context context, ServiceListener l) throws RemoteException,
            UnsupportedOperationException {
        mContext = context;
        mServiceListener = l;
        String s = toString();
        mClientId = "" + android.os.Process.myPid() + "_" + s.substring(s.lastIndexOf("@") + 1);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE);
        if (b != null) {
            mManagerService = IBluetoothManager.Stub.asInterface(b);
            try {
                mManagerService.registerStateChangeCallback(mStateChangeCallback);
            } catch (RemoteException re) {
                Log.w(TAG, "Unable to register BluetoothStateChangeCallback", re);
                throw new RemoteException("Bluetooth is not available");
            }
        } else {
            throw new UnsupportedOperationException("Bluetooth is not available");
        }
        // Bind to service only if BT is enabled

        synchronized (BluetoothMapClient.this) {
            if (isEnabled()) {
                Log.d(TAG,
                        "BluetoothMapClient(): Bluetooth enabled. Binding to MapClientService...");
                if (mBindingState != BIND_STATE_UNBOUND) {
                    Log.d(TAG,
                            "BluetoothMapClient(): Binding state not unbound...Skipping binding...");
                } else {
                    mBindingState = BIND_STATE_BINDING;
                    if (!context.bindService(new Intent(IBluetoothMapClient.class.getName()), this,
                            0)) {
                        Log.e(TAG, "Could not bind to Bluetooth Map Service");
                        mBindingState = BIND_STATE_UNBOUND;
                    }
                }
            } else {
                Log.d(TAG, "BluetoothMap(): Bluetooth not enabled. Skipping MapService binding...");
            }

        }
    }

    /**
     * Cleanup resources and close connections to the Bluetooth MAP Server.
     *
     * This method will cause the client application disconnect from all MAP server instances.
     * instances and close the connection to the MapClient service.
     */
    public synchronized void close() {
        if (DBG) {
            Log.d(TAG, "close()");
            debugPrintStackTrace();
        }

        if (mIsClosed) {
            Log.w(TAG, "BluetoothMapClient proxy is already closed");
            return;
        }

        if (mBindingState == BIND_STATE_UNBOUND) {
            Log.w(TAG, "close(): service not bound...Skipping unbind...");
        } else if (mBindingState == BIND_STATE_BINDING) {
            Log.w(TAG, "close(): service binding in progress. Setting pending close flag..");
        } else {
            try {
                mBindingState = BIND_STATE_UNBINDING;
                mService.unregisterCallback(mClientId, mCallback);
                mContext.unbindService(this);
                mService = null;
            } catch (Throwable t) {
                Log.d(TAG, "Error unbinding service", t);
            }
            mBindingState = BIND_STATE_UNBOUND;
        }

        mServiceListener = null;
        try {
            mManagerService.unregisterStateChangeCallback(mStateChangeCallback);
        } catch (Throwable re) {
            Log.w(TAG, "Unable to register BluetoothStateChangeCallback", re);
        }

        mIsClosed = true;
    }

    /**
     * This method is not used
     *
     * @hide
     */
    public List<BluetoothDevice> getConnectedDevices() {
        return null;
    }

    /**
     * This method is not used
     *
     * @hide
     */
    public int getConnectionState(BluetoothDevice device) {
        return 0;
    }

    /**
     * This method is not used
     *
     * @hide
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return null;
    }

    private boolean isEnabled() {
        if (mAdapter.getState() == BluetoothAdapter.STATE_ON)
            return true;
        return false;
    }

    // -------------API methods-------------------
    /**
     * Register a callback object to receive events from the MapClientService.
     *
     * @param eventHandler
     *            The event callback object that MapClientService will be invoked
     *            when an event occurs.
     */
    public void registerEventHandler(IBluetoothMapClientEventHandler eventHandler) {
        mEventHandler = eventHandler;
    }

    /**
     * Unregister the event callback object from the MapClientService.
     */
    public void unregisterEventHandler() {
        mEventHandler = null;
    }

    /**
     * Start the MCE notification server, and advertise the notification server
     * in the local device's Bluetooth SDP record.
     *
     * <p/>
     * This request is asynchronous. The result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onNotificationServerStarted(boolean)}.
     *
     * @param name
     *            Name of the notification server to be advertised in SDP record.
     *            Eg: "NotificationServer".
     */
    public boolean startNotificationServer(String name) {
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return svc.startNotificationServer(name);
            } catch (Throwable t) {
                Log.e(TAG, "startNotificationServer() error", t);
            }
        }
        return false;

    }

    /**
     * Stop the MCE notification server.
     *
     * The corresponding SDP record will also be removed.
     *
     * <p/>
     * This request is asynchronous. The result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onNotificationServerStopped(boolean)}.
     *
     * @return
     */
    public boolean stopNotificationServer() {
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return svc.stopNotificationServer();
            } catch (Throwable t) {
                Log.e(TAG, "stopNotificationServer() error", t);
            }
        }
        return false;

    }

    /**
     * Set the remote Bluetooth MAP Server (MSE).
     * This call has to be performed before connecting to
     * any instance on the MAP server.
     *
     * <p/>
     * Once the remote MAP server is set, it cannot be changed for the
     * lifetime of the BluetoothMapClient object.
     *
     * @param server
     *            The remote Bluetooth MAP server.
     * @return true if remote MAP server is successfully set.
     *         false otherwise.
     */
    public boolean setServer(BluetoothDevice server) {
        if (mMse == null) {
            mMse = server;
            return true;
        }

        if (!mMse.equals(server)) {
            Log.w(TAG, "setServer(): server address already set to " + mMse);
            return false;
        }

        return true;
    }

    private boolean isServerValid() {
        boolean isValid = (mMse != null);
        if (!isValid) {
            Log.w(TAG, "Invalid MAP server");
        }
        return isValid;
    }

    /**
     * Connect to the remote MAP Server's MSE instance.
     *
     * <p/>
     * This request is asynchronous. The final connection status is returned in
     * callback method {@link
     * IBluetoothMapClientEventHandler#onServerConnected(BluetoothDevice, int,
     * boolean)}.
     * The instances supported by MAP server can be fetched using the method
     * {@link #getMseInstances()}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true if the connect request successfully started.
     */
    public boolean connect(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "connect() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.connect(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "stopNotificationServer() error", t);
            }
        }
        return false;
    }

    /**
     * Disconnect from a remote MAP Server's MSE instance.
     *
     * <p/>
     * This request is asynchronous. The final connection status is returned in
     * callback method {@link
     * IBluetoothMapClientEventHandler#onServerDisconnected(BluetoothDevice,
     * int, boolean)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true if the disconnect request successfully started.
     */
    public boolean disconnect(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "disconnect() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.disconnect(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "stopNotificationServer() error", t);
            }
        }
        return false;
    }

    /**
     * Disconnect from all MAP server instances on the remote MAP server.
     *
     * @return if the disconnect request successfully started.
     * @deprecated not currently supported
     */
    @Deprecated
    public boolean disconnectAll() {
        if (!isServerValid()) {
            Log.e(TAG, "disconnect() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                mService.disconnectAll();
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "stopNotificationServer() error", t);
            }
        }
        return false;
    }

    /**
     * Abort the current MAP Client OBEX operation.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true if the request successfully started.
     */
    public boolean abortOperation(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "abortOperation() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.abortOperation(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "abortOperation() error", t);
            }
        }
        return false;
    }

    /**
     * Set the current folder path of the specified MAP server instance.
     * Eg: "telecom/msg/inbox" in case of SMS instance.
     *
     * <p/>
     * This request is asynchronous. The status of this request is returned in
     * callback method {@link
     * IBluetoothMapClientEventHandler#onFolderPathSet(BluetoothDevice, int,
     * boolean, String)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param folderPath
     *            the folder path. The folder path can either be an absolute
     *            path, or a relative path.
     * @return true if the request successfully started.
     */
    public boolean setFolderPath(int serverInstanceId, String folderPath) {
        if (!isServerValid()) {
            Log.e(TAG, "setFolderPath() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.setFolderPath(mClientId, mMse, serverInstanceId, folderPath);
            } catch (Throwable t) {
                Log.e(TAG, "setFolderPath() error", t);
            }
        }
        return false;
    }

    /**
     * Returns the current folder path of the specified MAP server instance.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return the current folder path of the MAP server instance.
     */

    public String getFolderPath(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "getFolderPath() failed.");
            return null;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getFolderPath(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "getFolderPath() error", t);
            }
        }
        return null;
    }

/**
     * Get list of folders in current folder for specified MAP server instance.
     *
     * <p/>
     * This request is asynchronous. The final folder list result is returned in
     * callback method {@link
     * IBluetoothMapClientEventHandler#onFolderListResult(BluetoothDevice, int, boolean, int, BluetoothFolderInfo[])}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param maxLength the maximum number of folders to return, or -1 to specify return all.
     * @param offset if > 0, folders preceding the index offset are skipped in the result.
     * @return true, if the get request successfully started.
     *         false, otherwise.
     */

    public boolean getFolderListObject(int serverInstanceId, int maxLength, int offset) {
        if (!isServerValid()) {
            Log.e(TAG, "getFolderListObject() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getFolderList(mClientId, mMse, serverInstanceId, maxLength, offset,
                        true);
            } catch (Throwable t) {
                Log.e(TAG, "getFolderListObject() error", t);
            }
        }
        return false;
    }

/**
     * Get list of folders in current folder for specified MAP server instance.
     * Returns the result in an XML file specified by Uri.
     *
     * <p/>
     * This request is asynchronous. The final folder list result is returned in
     * callback method {@link
     *  IBluetoothMapClientEventHandler#onFolderListResult(BluetoothDevice, int, boolean,
            int, Uri)}
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param maxLength the maximum number of folders to return, or -1 to specify return all.
     * @param offset if > 0, folders preceding the index offset are skipped in the result.
     * @return true, if the get request successfully started.
     *         false, otherwise.
     */

    public boolean getFolderList(int serverInstanceId, int maxLength, int offset) {
        if (!isServerValid()) {
            Log.e(TAG, "getFolderList() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getFolderList(mClientId, mMse, serverInstanceId, maxLength, offset,
                        false);
            } catch (Throwable t) {
                Log.e(TAG, "getFolderList() error", t);
            }
        }
        return false;
    }

    /**
     * Get the message list info for messages in the current folder path of the
     * specified MAP server instance. The message list will be returned as an object.
     *
     * <p/>
     * This request is asynchronous. The final message list result is returned in
     * callback method {@link 
     * IBluetoothMapClientEventHandler#onGetMessageResult(BluetoothDevice, int, String, boolean, Uri) }
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param maxLength
     *            the maximum number of messages to return, or -1 to specify
     *            return all.
     * @param offset
     *            if > 0, messages preceding the index offset are skipped in the
     *            result.
     * @return true if the request successfully started.
     */
    public boolean getMessageListObject(int serverInstanceId, int maxLength, int offset,
            BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter) {
        if (!isServerValid()) {
            Log.e(TAG, "getMessageListObject() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getMessageList(mClientId, mMse, serverInstanceId, maxLength,
                        offset, listFilter, paramFilter, true);
            } catch (Throwable t) {
                Log.e(TAG, "getMessageListObject() error", t);
            }
        }
        return false;
    }

    /**
     * Get the message list info for messages in the current folder path of the
     * specified MAP server instance. Returns the result in an XML file.
     *
     * <p/>
     * This request is asynchronous. The final folder list result is returned in
     * callback method {@link 
     * IBluetoothMapClientEventHandler#onGetMessageResult(BluetoothDevice,
     *  int, String, boolean, BMessage)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param maxLength
     *            the maximum number of messages to return, or -1 to specify
     *            return all.
     * @param offset
     *            if > 0, messages preceding the index offset are skipped in the
     *            result.
     * @return true if the request successfully started.
     */
    public boolean getMessageList(int serverInstanceId, int maxLength, int offset,
            BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter) {
        if (!isServerValid()) {
            Log.e(TAG, "getMessageList() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getMessageList(mClientId, mMse, serverInstanceId, maxLength,
                        offset, listFilter, paramFilter, false);
            } catch (Throwable t) {
                Log.e(TAG, "getMessageList() error", t);
            }
        }
        return false;
    }

    private boolean validateCharset(int charset) {
        // Check valid charset
        if (charset != CHARSET_NATIVE && charset != CHARSET_UTF8) {
            Log.e(TAG, "invalid charset: " + charset);
            return false;
        }
        return true;
    }

    /**
     * Returns a BMessage for the specified message in the MAP server instance. The
     * BMessage is returned in a file on the file system.
     *
     * <p/>
     * This request is asynchronous. The BMessage is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onGetMessageResult(BluetoothDevice, int,
     * String, boolean, Uri) }.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param messageHandle
     *            the unique indentifier for the message in the MAP server instance.
     * @param charset
     *            the character set encoding to use in the BMessage. Specify
     *            {@link BluetoothMapClient#CHARSET_NATIVE} or
     *            {@link BluetoothMapClient#CHARSET_UTF8}.
     * @param includeAttachments
     *            if true, returns attachments in the BMessage.
     * @return true if the request successfully started.
     */
    public boolean getMessage(int serverInstanceId, String messageHandle, byte charset,
            boolean includeAttachments) {
        if (!validateCharset(charset) || !isServerValid()) {
            Log.e(TAG, "getMessage() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getMessage(mClientId, mMse, serverInstanceId, messageHandle,
                        charset, includeAttachments, false);
            } catch (Throwable t) {
                Log.e(TAG, "getMessage() error", t);
            }
        }
        return false;
    }

    /**
     * Returns a BMessage for the specified message in the MAP server instance. The
     * BMessage is returned as a BMessage Java object.
     *
     * <p/>
     * This request is asynchronous. The BMessage is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onGetMessageResult(BluetoothDevice, int,
     * String, boolean, BMessage) }.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param messageHandle
     *            the unique indentifier for the message in the MAP server instance.
     * @param charset
     *            the character set encoding to use in the BMessage. Specify
     *            {@link BluetoothMapClient#CHARSET_NATIVE} or
     *            {@link BluetoothMapClient#CHARSET_UTF8}.
     * @param includeAttachments
     *            if true, returns attachments in the BMessage.
     * @return true if the request successfully started.
     */
    public boolean getMessageAsObject(int serverInstanceId, String messageHandle, byte charset,
            boolean includeAttachments) {

        if (!validateCharset(charset) || !isServerValid()) {
            Log.e(TAG, "getMessageAsObject() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getMessage(mClientId, mMse, serverInstanceId, messageHandle,
                        charset, includeAttachments, true);
            } catch (Throwable t) {
                Log.e(TAG, "getMessageAsObject() error", t);
            }
        }
        return false;
    }

    /**
     * Update the inbox folder of the specified MAP server instance.
     * MAP server shall contact the network to retrieve new messages if available.
     * <p/>
     * This request is asynchronous. The result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onUpdateInboxResult(BluetoothDevice,
     * int, boolean)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true if the request successfully started.
     */
    public boolean updateInbox(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "updateInbox() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.updateInbox(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "updateInbox() error", t);
            }
        }
        return false;
    }

/**
     * Set the delete status of the specified message on the MAP server instance.
     * <p/>
     * This request is asynchronous. The result is returned in callback method
     * {@link IBluetoothMapClientEventHandler#onMessageStatusUpdated(BluetoothDevice, int, int, String, boolean)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param messageHandle
     *            the unique identifier for the message.
     * @return true if the request successfully started.
     */
    public boolean setMessageDeletedStatus(int serverInstanceId, String messageHandle,
            boolean setDeleted) {
        if (!isServerValid()) {
            Log.e(TAG, "setMessageDeletedStatus() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.setMessageStatus(mClientId, mMse, serverInstanceId, messageHandle,
                        STATUS_TYPE_DELETED, setDeleted);
            } catch (Throwable t) {
                Log.e(TAG, "setMessageDeletedStatus() error", t);
            }
        }
        return false;
    }

/**
     * Set the read status of the specified message on the MAP server instance.
     * <p/>
     * This request is asynchronous. The result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onMessageStatusUpdated(BluetoothDevice, int, int, String, boolean)}.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param messageHandle
     *            the unique identifier for the message.
     * @return true if the request successfully started.
     */
    public boolean setMessageReadStatus(int serverInstanceId, String messageHandle, boolean setRead) {
        if (!isServerValid()) {
            Log.e(TAG, "setMessageReadStatus() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.setMessageStatus(mClientId, mMse, serverInstanceId, messageHandle,
                        STATUS_TYPE_READ, setRead);
            } catch (Throwable t) {
                Log.e(TAG, "setMessageReadStatus() error", t);
            }
        }
        return false;
    }

    private String createNewPushBMessageFileName() {
        return "push_" + mClientId + "_" + System.currentTimeMillis() + "_" + mRandom.nextInt(9)
                + ".bmsg";
    }

    private synchronized Uri createNewPushBMessageContentUri() {
        return Uri.withAppendedPath(URI_CONTENT_PROVIDER, createNewPushBMessageFileName());
    }

/**
     * Push the specified BMessage to MAP server instance's current folder on MAP server.
     *
     * <p/>
     * This request is asynchronous. The push message result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onPushMessageResult(BluetoothDevice, int, boolean, String)}

     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param bMessage
     * the BMessage to push to the MAP server instance.
     * @param charset The character set encoding used in the BMessage.
     * Specify either {@link BluetoothMapClient#CHARSET_NATIVE}
     * or {@link BluetoothMapClient#CHARSET_UTF8}.
     * @param isRetry if true, indicates this push request is a retry.
     * @param autoSend if true, and if the push is to the "outbox" folder,
     * indicates to the MAP server instance to automatically send the messae to the recipient.
     * @return true if the request successfully started.
     */
    public boolean pushMessage(int serverInstanceId, BMessage bMessage, byte charset,
            boolean isRetry, boolean autoSend) {
        if (!isServerValid()) {
            Log.e(TAG, "pushMessage() failed.");
            return false;
        }

        IBluetoothMapClient svc = mService;
        if (svc != null) {
            Uri contentUri = createNewPushBMessageContentUri();
            ;
            ParcelFileDescriptor pfd = null;
            boolean uploadSuccess = false;
            try {
                pfd = mContext.getContentResolver().openFileDescriptor(contentUri, "w");
                int fd = pfd.getFd();
                if (fd > 0) {
                    uploadSuccess = bMessage.write(fd);
                }
            } catch (Throwable t) {
                Log.w(TAG, "pushMessage(): error uploading BMessage to MapContentProvider", t);
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Throwable t) {
                    Log.d(TAG, "pushMessage(): error closing BMessage file descriptor", t);
                }
            }
            if (!uploadSuccess) {
                Log.d(TAG, "Unable to upload BMessage to MapContentProvider");
                return false;
            }

            try {
                return mService.pushMessage(mClientId, mMse, serverInstanceId,
                        contentUri.toString(), charset, isRetry, autoSend);
            } catch (Throwable t) {
                Log.e(TAG, "setMessageReadStatus() error", t);
            }
        }

        return false;
    }

/**
     * Push the specified File (Bmessage format) to MAP server instance's current folder.
     *
     * <p/>
     * This request is asynchronous. The push message result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onPushMessageResult(BluetoothDevice, int, boolean, String)}.

     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param file
     * the file containing the BMessage to push to the MAP server instance.
     * @param charset The character set encoding used in the BMessage.
     * Specify either {@link BluetoothMapClient#CHARSET_NATIVE}
     * or {@link BluetoothMapClient#CHARSET_UTF8.
     * @param isRetry if true, indicates this push request is a retry.
     * @param autoSend if true, and if the push is to the "outbox" folder,
     * indicates to the MAP server instance to automatically send the messae ot the recipient.
     * @return true, if the request successfully started.
     */
    public boolean pushMessage(int serverInstanceId, File file, byte charset, boolean isRetry,
            boolean autoSend) {
        if (!isServerValid()) {
            Log.e(TAG, "pushMessage() failed.");
            return false;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (Throwable t) {
            Log.e(TAG, "pushMessage(): unable to upload BMessage file to Map Content Provider", t);
        }
        boolean success = false;
        if (in != null) {
            success = pushMessage(serverInstanceId, in, charset, isRetry, autoSend);
        }
        if (in != null) {
            try {
                in.close();
            } catch (Throwable t) {
                Log.d(TAG, "pushMessage(): error closing BMessage file", t);
            }
        }
        return success;
    }

/**
     * Push the specified BMessage inputstream to MAP server instance's current folder.
     *
     * <p/>
     * This request is asynchronous. The push message result is returned in callback method
     * {@link
     * IBluetoothMapClientEventHandler#onPushMessageResult(BluetoothDevice, int, boolean, String)}.

     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @param in
     * the InputStream containing the BMessage to push to the MAP server instance.
     * @param charset The character set encoding used in the BMessage.
     * Specify either {@link BluetoothMapClient#CHARSET_NATIVE}
     * or {@link BluetoothMapClient#CHARSET_UTF8}.
     * @param isRetry if true, indicates this push request is a retry.
     * @param autoSend if true, and if the push is to the "outbox" folder,
     * indicates to the MAP server instance to automatically send the messae ot the recipient.
     * @return true, if the request successfully started.
     */
    public boolean pushMessage(int serverInstanceId, InputStream in, byte charset, boolean isRetry,
            boolean autoSend) {
        if (!isServerValid()) {
            Log.e(TAG, "pushMessage() failed.");
            return false;
        }

        if (in == null) {
            Log.e(TAG, "pushMessage(): BMessage stream is null");
            return false;
        }

        IBluetoothMapClient svc = mService;
        if (svc != null) {
            Uri contentUri = createNewPushBMessageContentUri();
            byte[] buffer = new byte[200];
            int bytesRead = 0;
            OutputStream os = null;
            ParcelFileDescriptor fd = null;
            boolean uploadSuccess = false;
            try {
                fd = mContext.getContentResolver().openFileDescriptor(contentUri, "cwt");
                os = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
                while ((bytesRead = in.read(buffer, 0, 200)) >= 0) {
                    os.write(buffer, 0, bytesRead);
                }
                uploadSuccess = true;
            } catch (Throwable t) {
                Log.w(TAG, "pushMessage(): error uploading BMessage to MapContentProvider", t);
            }
            if (fd != null) {
                try {
                    fd.close();
                } catch (Throwable t) {
                    Log.d(TAG, "pushMessage(): error closing BMessage stream", t);
                }
            }

            if (!uploadSuccess) {
                Log.d(TAG, "Unable to upload BMessage to MapContentProvider");
                return false;
            }

            try {
                return mService.pushMessage(mClientId, mMse, serverInstanceId,
                        contentUri.toString(), charset, isRetry, autoSend);
            } catch (Throwable t) {
                Log.e(TAG, "setMessageReadStatus() error", t);
            }
        }
        return false;
    }

    /**
     * Register MAP client to receive MAP notifications from the specified MAP server instance.
     * <p>
     * Results in
     * {@link IBluetoothMapClientEventHandler#onNotificationRegistrationStateChange(BluetoothDevice server,
     *  int instanceId, boolean isRegistration, boolean success)} callback.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true, if the registration successfully started.
     */
    public boolean registerForNotification(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "registerForNotification() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.registerForNotification(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "registerForNotification() error", t);
            }
        }
        return false;
    }

    /**
     * Unregister MAP client from receiving MAP notifications from the
     * specified MAP server instance on MAP server.
     *
     * @param serverInstanceId
     *            the unique identifier for the remote MAP server instance.
     * @return true, if the unregister successfully started.
     */
    public boolean unregisterForNotification(int serverInstanceId) {
        if (!isServerValid()) {
            Log.e(TAG, "unregisterForNotification() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.unregisterForNotification(mClientId, mMse, serverInstanceId);
            } catch (Throwable t) {
                Log.e(TAG, "unregisterForNotification() failed.() error", t);
            }
        }
        return false;
    }

    /**
     * Returns the MAP server instance information of the remote MAP Server.
     * <p/>
     * This request is asynchronous. The MAP server instance information is returned in
     * callback method {@link
     * IBluetoothMapClientEventHandler#onGetMseInstancesResult(BluetoothDevice,
     * BluetoothMseInfo[])}.
     *
     * @return true, if the request successfully started.
     */
    public boolean getMseInstances() {
        if (!isServerValid()) {
            Log.e(TAG, "getMseInstances() failed.");
            return false;
        }
        IBluetoothMapClient svc = mService;
        if (svc != null) {
            try {
                return mService.getMseInstances(mClientId, mMse);
            } catch (Throwable t) {
                Log.e(TAG, "getMseInstances() failed.() error", t);
            }
        }
        return false;
    }
}
