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
package com.broadcom.bt.pbap;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.StringTokenizer;

import javax.obex.ApplicationParameter;
import javax.obex.Authenticator;
import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.PasswordAuthentication;
import javax.obex.ResponseCodes;

import com.broadcom.bt.util.HandlerThread;
import com.broadcom.bt.util.IOUtils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

/**
 * Main API used by PBAP client application to interface with a PBAP server.
 *
 * <p>The PBAP Client library uses the Bluetooth RFCOMM Socket API that is standard in the Android
 * Framework. Although it can in theory run on any Android device, it is considered Broadcom IP and
 * be restricted to only devices that use Broadcom BPlus.
 * The PbapClient includes a message handler running on a child thread that is automatically
 * started when the PbapClient object is constructed. All API calls in the PbapClient are
 * asynchronous calls. An application that invokes any of the public APIs will not block waiting
 * for the PbapClient to return. The application must register an instance of the
 * {@link IBluetoothPbapClientEventHandler} to receive callbacks to API requests.
 *
 * @author fredc@broadcom.com
 *
 */
public class BluetoothPbapClient implements Authenticator {
    private static final boolean DBG = true;
    private static final String TAG = "BluetoothPbapClient";

    private static final int PBAPC_PROFILE_ID=8888888;
    private static final int DEFAULT_AUTH_TIMEOUT_MS = 10000;

    /** @hide */
    public static final String PBAPC_STATE_CHANGED_ACTION = "com.broadcom.bt.pbap.action.PBAPC_STATE_CHANGED";
    /** The profile is in disconnected state */
    public static final int STATE_DISCONNECTED = BluetoothAdapter.STATE_DISCONNECTED;
    /** The profile is in connected state */
    public static final int STATE_CONNECTED = BluetoothAdapter.STATE_CONNECTED;

    /** IETF vCard 2.1 specification*/
    public static final byte VCARD_VERSION_21 = ApplicationParameter.TRIPLET_VALUE.FORMAT.VCARD_VERSION_21;
    /** IETF vCard 3.0 specification*/
    public static final byte VCARD_VERSION_30 = ApplicationParameter.TRIPLET_VALUE.FORMAT.VCARD_VERSION_30;
    /** vCard default version is 2.1 specification*/
    public static final byte DEFAULT_VCARD_VERSION = VCARD_VERSION_21;

    /** Return search vCard results in alphabetical order*/
    public static final byte SEARCH_ORDER_ALPHABETICAL = 0;
    /** Return search vCard results in indexed order*/
    public static final byte SEARCH_ORDER_INDEXED = 1;
    /** Return search vCard results in phonetical order*/
    public static final byte SEARCH_ORDER_PHONETICAL = 2;
    /** Return search vCard results in any order*/
    public static final byte SEARCH_ORDER_NOT_SET = -1;

    /** Search phonebook vCards by name*/
    public static final byte SEARCH_ATTRIBUTE_NAME = 0;
    /** Search phonebook vCards by number*/
    public static final byte SEARCH_ATTRIBUTE_NUMBER = 1;
    /** Search phonebook vCards by sound*/
    public static final byte SEARCH_ATTRIBUTE_SOUND = 2;
    /** Search attribute not set*/
    public static final byte SEARCH_ATTRIBUTE_NOT_SET = -1;

    /** Starting index of list of vCards retreived*/
    public static final int MAX_LIST_COUNT_RETURN_COUNT = 0;
    /** Max index of list of vCards retreived*/
    public static final int MAX_LIST_COUNT_MAX = 65535;
    /** Max index of the list of vCards is not set*/
    public static final int MAX_LIST_COUNT_NOT_SET = -1;

    /** Phonebook listing offset not set*/
    public static final int LIST_OFFSET_NOT_SET = -1;
    /** Phonebook listing offset where missed call records is not requested*/
    public static final int MISSED_CALLS_NOT_SET = -1;
    /** Phonebook listing size when maxListCount is non zero in {@link #pullPhonebook},
     * {@link #pullVcardListing}
     */
    public static final int PHONEBOOK_SIZE_NOT_SET = -1;

    /** Download the entire content of phonebook */
    public static final String PB_PATH = "/telecom/pb";

    /** Download the incoming call history of phonebook */
    public static final String ICH_PATH = "/telecom/ich";

    /** Download the outgoing call history of phonebook */
    public static final String OCH_PATH = "/telecom/och";

    /** Download the missed call history of phonebook */
    public static final String MCH_PATH = "/telecom/mch";

    /** Download the combined call history of phonebook */
    public static final String CCH_PATH = "/telecom/cch";

    /** Download the entire content of phonebook's SIM */
    public static final String SIM_PB_PATH = "/SIM1/telecom/pb";

    /** Download the incoming call history of phonebook's SIM */
    public static final String SIM_ICH_PATH = "/SIM1/telecom/ich";

    /** Download the outgoing call history of phonebook's SIM */
    public static final String SIM_OCH_PATH = "/SIM1/telecom/och";

    /** Download the missed call history of phonebook's SIM */
    public static final String SIM_MCH_PATH = "/SIM1/telecom/mch";

    /** Download the combined call history of phonebook's SIM */
    public static final String SIM_CCH_PATH = "/SIM1/telecom/cch";

    private static final int BUFFER_SIZE = 500;

    private static final byte[] NO_BYTES = new byte[0];
    // 128 bit UUID for PBAP
    private static final byte[] PBAP_TARGET = new byte[] { 0x79, 0x61, 0x35, (byte) 0xf0,
            (byte) 0xf0, (byte) 0xc5, 0x11, (byte) 0xd8, 0x09, 0x66, 0x08, 0x00, 0x20, 0x0c,
            (byte) 0x9a, 0x66 };

    private static final HashSet<String> sPathValidator;
    static {
        sPathValidator = new HashSet<String>();
        sPathValidator.add(PB_PATH);
        sPathValidator.add(ICH_PATH);
        sPathValidator.add(OCH_PATH);
        sPathValidator.add(MCH_PATH);
        sPathValidator.add(CCH_PATH);
        sPathValidator.add(SIM_PB_PATH);
        sPathValidator.add(SIM_ICH_PATH);
        sPathValidator.add(SIM_OCH_PATH);
        sPathValidator.add(SIM_MCH_PATH);
        sPathValidator.add(SIM_CCH_PATH);
    }

    /**
     * Returns true is the vCard version specified is valid
     *
     * @param version
     * @return
     */
    private static boolean isValidVcardVersion(byte version) {
        return (version == VCARD_VERSION_21 || version == VCARD_VERSION_30);
    }

    /**
     * REturns true if the search order specified is valid
     *
     * @param searchOrder
     * @return
     */
    private static boolean isValidSearchOrder(byte searchOrder) {
        return searchOrder >= SEARCH_ORDER_NOT_SET && searchOrder <= SEARCH_ORDER_PHONETICAL;
    }

    /**
     * Returns true is the search attribute specified is valid
     *
     * @param searchAttribute
     * @return
     */
    private static boolean isValidSearchAttribute(byte searchAttribute) {
        return searchAttribute >= SEARCH_ATTRIBUTE_NOT_SET
                && searchAttribute <= SEARCH_ATTRIBUTE_SOUND;
    }

    /**
     * Convert an integer to a byte array with specified length
     *
     * @param value
     * @param length
     * @return
     */
    private static byte[] toByteArray(int value, int length) {
        byte b[] = new byte[length];
        for (int i = length - 1; i >= 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value = value >> 8;
        }
        return b;
    }

    /**
     * Returns true if the OBEX response header is Continue response code
     *
     * @param header
     * @return
     */
    private static boolean isResponseContinue(HeaderSet header) {
        if (header == null) {
            return false;
        }
        Log.d(TAG, "isResponseContinue(): responseCode= " + header.responseCode);
        if (header.responseCode != ResponseCodes.OBEX_HTTP_CONTINUE) {
            Log.w(TAG, "isResponseContinue(): NOT CONTINUE");
            return false;
        }
        return true;
    }

    /**
     * Returns true if the OBEX response header is a OK response code
     *
     * @param header
     * @return
     */
    private static boolean isResponseOk(HeaderSet header) {
        if (header == null) {
            return false;
        }
        Log.d(TAG, "isResponseOk(): responseCode= " + header.responseCode);
        if (header.responseCode != ResponseCodes.OBEX_HTTP_OK) {
            Log.w(TAG, "isResponseOk(): NOT OK");
            return false;
        }
        return true;
    }

    private class PbapClientBroadcastReceiver extends BroadcastReceiver {
        public void registerReceiver(Context context) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            context.registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(TAG, "PbapClientBroadcastReceiver(): handling ACL_DISCONNECTED...");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && mPbapServer.equals(device) && isConnected()) {
                    handleDisconnect();
                }
            }
        }

    }

    /**
     * Message Handler for processing API requests asynchronously
     *
     * @author fredc
     *
     */
    private class CommandHandler extends HandlerThread.Handler {
        private static final int MSG_CONNECT = 1;
        private static final int MSG_DISCONNECT = 2;
        private static final int MSG_SET_PATH = 3;
        private static final int MSG_PULL_PHONEBOOK = 4;
        private static final int MSG_PULL_VCARD_LISTING = 5;
        private static final int MSG_PULL_VCARD_ENTRY = 6;
        private static final int MSG_AUTH_CHALLENGE = 7;
        private static final int MSG_AUTH = 8;
        private static final int MSG_AUTH_TIMEOUT = 9;

        public boolean init() {
            Log.d(TAG, "Initializing mCommandHandler");
            synchronized (BluetoothPbapClient.this) {
                mCommandHandler = this;
                if (mPendingConnect) {
                    mPendingConnect = false;
                    connect();
                }
            }
            return true;
        }

        public void handleMessage(Message m) {
            switch (m.what) {
            case MSG_CONNECT:
                handleConnect();
                break;
            case MSG_DISCONNECT:
                handleDisconnect();
                break;
            case MSG_SET_PATH:
                handleSetPath((String) m.obj);
                break;
            case MSG_PULL_PHONEBOOK: {
                Bundle b = m.getData();
                if (b != null) {
                    String path = b.getString("p");
                    byte vcardVersion = b.getByte("v", DEFAULT_VCARD_VERSION);
                    int maxListCount = b.getInt("m", -1);
                    int listStartOffset = b.getInt("l", LIST_OFFSET_NOT_SET);
                    String outFilepath = b.getString("f");
                    handlePullPhonebook(path, (BluetoothAttributeMask) m.obj, vcardVersion, maxListCount,
                            listStartOffset, outFilepath);
                }
            }
                break;
            case MSG_PULL_VCARD_LISTING: {
                Bundle b = m.getData();
                if (b != null) {
                    String name = b.getString("n");
                    byte order = b.getByte("o", SEARCH_ORDER_NOT_SET);
                    int maxListCount = b.getInt("m", -1);
                    int listStartOffset = b.getInt("l", LIST_OFFSET_NOT_SET);
                    String outputFilepath = b.getString("f");
                    byte searchAttribute = b.getByte("a", SEARCH_ATTRIBUTE_NOT_SET);
                    String searchValue = b.getString("v");
                    handlePullVcardListing(name, order, searchAttribute, searchValue, maxListCount,
                            listStartOffset, outputFilepath);
                }
            }
                break;
            case MSG_PULL_VCARD_ENTRY: {
                Bundle b = m.getData();
                if (b != null) {
                    String name = b.getString("n");
                    byte vcardVersion = b.getByte("v", DEFAULT_VCARD_VERSION);
                    String outputFilepath = b.getString("f");
                    handlePullVcardEntry(name, (BluetoothAttributeMask) m.obj, vcardVersion, outputFilepath);
                }
            }
                break;
            case MSG_AUTH_CHALLENGE: {
                Log.d(TAG, "CommandHandler: MSG_AUTH_CHALLENGE");

                synchronized (mAuthLock) {
                    Message timeoutM = obtainMessage(MSG_AUTH_TIMEOUT);
                    timeoutM.arg1 = m.arg1;
                    this.sendMessageDelayed(timeoutM, mAuthTimeoutMs <= 0 ? DEFAULT_AUTH_TIMEOUT_MS
                            : mAuthTimeoutMs);
                    if (mAuth != null) {
                        mAuth.onAuthenticationChallenge(mPbapServer, (String) m.obj, m.arg1 == 1,
                                m.arg2 == 1);
                    }
                }
            }
                break;
            case MSG_AUTH: {
                Log.d(TAG, "CommandHandler: MSG_AUTH");
                synchronized (mAuthLock) {
                    Message timeoutM = obtainMessage(MSG_AUTH_TIMEOUT);
                    timeoutM.arg1 = m.what;
                    this.sendMessageDelayed(timeoutM, mAuthTimeoutMs <= 0 ? DEFAULT_AUTH_TIMEOUT_MS
                            : mAuthTimeoutMs);

                    if (mAuth != null) {
                        mAuth.onAuthentication(mPbapServer, new String((byte[]) m.obj));
                    }
                }
            }
                break;
            case MSG_AUTH_TIMEOUT: {
                Log.d(TAG, "CommandHandler: MSG_AUTH_TIMEOUT");
                synchronized (mAuthLock) {
                    int authType = m.arg1;
                    if (mAuth != null) {
                        if (authType == MSG_AUTH) {
                            mAuth.onAuthenticationTimeout(mPbapServer);
                        } else if (authType == MSG_AUTH_CHALLENGE) {
                            mAuth.onAuthenticationChallengeTimeout(mPbapServer);

                        }
                    }
                    cancelAuthentication();
                }
            }
                break;
            }
        }
    }

    private Context mContext; // Only needed for broadcast intents
    private HandlerThread mCommandHandlerThread;
    private CommandHandler mCommandHandler;
    private BluetoothDevice mPbapServer;
    private IBluetoothPbapClientEventHandler mEventHandler;
    private ClientSession mObexClientSession;
    private ClientOperation mPendingOperation;
    private BluetoothPbapRfcommTransport mTransport;
    private boolean mPendingConnect;
    private final Object mAuthLock = new Object();
    private IBluetoothPbapClientAuthenticator mAuth;
    private int mAuthTimeoutMs;
    private PasswordAuthentication mPwdAuth;
    private PbapClientBroadcastReceiver mReceiver;
    private boolean mAbort = false;

    // -------------------Public API---------------------------------------
    /**
     * Create a PBAP client connection to a specified PBAP Server.
     *
     * @param ctx Application context
     * @param server A BluetoothDevice containing the address of the remote device with
     * the PBAP Server.
     * @param eventHandler A callback object that the PbapClient will invoke asychronously
     * in response to each request.
     */
    public BluetoothPbapClient(Context ctx, BluetoothDevice server, IBluetoothPbapClientEventHandler eventHandler) {
        mContext = ctx;
        mPbapServer = server;
        mEventHandler = eventHandler;
        mReceiver = new PbapClientBroadcastReceiver();
        mReceiver.registerReceiver(ctx);

        mCommandHandlerThread = new HandlerThread("PbapClient-CommandHandlerThread") {
            @Override
            public Handler createHandler() {
                return new CommandHandler();
            }
        };
        mCommandHandlerThread.start();
    }

    /**
     * Cleans up resources associated with this client. Application must call this before exiting
     * for proper shutdown. If there is an already-running session finish ensures that the session
     * is closed gracefully.
     */
    public void finish() {
        Log.d(TAG, "BluetoothPbapClient finish()");

        if (mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (Throwable t) {
                Log.w(TAG, "Error unregistering receiver", t);
            }
        }
        if (mObexClientSession != null) {
            try {
                Log.d(TAG, "Closing obex client session");
                mObexClientSession.close();
                mObexClientSession = null;
            } catch (Throwable tt) {
                Log.w(TAG, "Unable to close obex client session");
            }
        }
        if (mTransport != null) {
            try {
                Log.d(TAG, "Closing transport");
                mTransport.close();
                mTransport = null;
                sendConnectionStateChangeEvent(STATE_CONNECTED, STATE_DISCONNECTED);
            } catch (Throwable tt) {
                Log.w(TAG, "Error closing PBAP obex transport", tt);
            }
        }
        if (mCommandHandlerThread != null) {
            try {
                mCommandHandlerThread.finish();
                mCommandHandlerThread = null;
            } catch (Throwable t) {
                Log.w(TAG, "finish(): error stopping handler thread", t);
            }
        }
    }

    /**
     * Returns the remote PBAP server, as a {@link BluetoothDevice} associated with this client.
     *
     * @return {@link BluetoothDevice} instance of the remote PBAP server.
     */
    public BluetoothDevice getPbapServer() {
        return mPbapServer;
    }

    /**
     * Returns true if connected to PBAP Server.
     *
     * @return false if transport is not valid, true if
     *         connection is valid.
     */
    public synchronized boolean isConnected() {
        try {
            return mTransport != null && mTransport.isConnected();
        } catch (Throwable t) {
            Log.e(TAG, "isConnected(): transport returned error...", t);
            return false;
        }
    }

    /**
     * Connect to the PBAP server. Connection status result is
     * returned in the {@link IBluetoothPbapClientEventHandler#onConnected} callback.
     */
    public synchronized void connect() {
        if (mCommandHandler == null) {
            mPendingConnect = true;
            return;
        }
        mCommandHandler.sendEmptyMessage(CommandHandler.MSG_CONNECT);
    }

    /**
     * Disconnect from the PBAP server. disconnect status result is returned in
     * {@link IBluetoothPbapClientEventHandler#onDisconnected} callback.
     */
    public synchronized void disconnect() {
        if (mCommandHandler == null) {
            Log.e(TAG, "disconnect(): command handler not available");
            return;
        }
        Log.d(TAG, "Dispatching disconnect() request");
        mCommandHandler.sendEmptyMessage(CommandHandler.MSG_DISCONNECT);
    }

    /**
     * Set the PbapServer browse path. The setPath status will be reported in the
     * {@link IBluetoothPbapClientEventHandler#onPathSet} callback.
     *
     * @param path The PBAP server path, must be one of the following values
     * {@link #PB_PATH}, {@link #ICH_PATH}, {@link #OCH_PATH},
     * {@link #CCH_PATH}, {@link #SIM_PB_PATH}, {@link #SIM_ICH_PATH}, {@link #SIM_OCH_PATH},
     * {@link #SIM_CCH_PATH}.
     */
    public void setPath(String path) {
        if (mCommandHandler == null) {
            Log.e(TAG, "setPath(): command handler not available");
            return;
        }
        mCommandHandler.sendMessage(mCommandHandler
                .obtainMessage(CommandHandler.MSG_SET_PATH, path));
    }

    /**
     *  Pull the phonebook specified by path. The contents of the phonebook will be stored in the
     *  vCard file at the location specified by outFilepath.The response is returned in the
     * {@link IBluetoothPbapClientEventHandler#onPullPhonebookCompleted} callback.
     *
     * @param path The PBAP server path, must be one of the following values
     * {@link #PB_PATH}, {@link #ICH_PATH}, {@link #OCH_PATH},
     * {@link #CCH_PATH}, {@link #SIM_PB_PATH}, {@link #SIM_ICH_PATH}, {@link #SIM_OCH_PATH},
     * {@link #SIM_CCH_PATH}.
     * @param mask (optional) A {@link BluetoothAttributeMask} bit mask that describes the
     * content fields to be returned in the result.
     * @param vcardVersion specifies if the response is vCard 2.1 or 3.0: Possible values are
     * {@link #VCARD_VERSION_21}, {@link #VCARD_VERSION_30}, {@link #DEFAULT_VCARD_VERSION}.
     * @param maxListCount specifies n number of entries to return based on the following criteria:
     * <p>
     * <ul>
     * <li>if n>0, returns n entries </li>
     * <li>if n=0, server will not return any content but instead return the total number of entries in the
     * {@link IBluetoothPbapClientEventHandler#onPullPhonebookCompleted phonebookSize} parameter of
     * {@link IBluetoothPbapClientEventHandler#onPullPhonebookCompleted} callback</li>
     * <li>if n<0, the server will return default number of entries (default is 65535)</li>
     * </ul>
     * </p>
     * @param listStartOffset The offset from which the records should be returned.
     * If <0, the offset is defaulted to 0.
     * @param outFilepath The destination file location where the vCard result should be stored.
     */
    public synchronized void pullPhonebook(String path, BluetoothAttributeMask mask, byte vcardVersion,
            int maxListCount, int listStartOffset, String outFilepath) {
        if (mCommandHandler == null) {
            Log.e(TAG, "pullPhonebook(): command handler not available");
            return;
        }

        Message m = mCommandHandler.obtainMessage(CommandHandler.MSG_PULL_PHONEBOOK);
        Bundle b = m.getData();
        b.putString("p", path);
        b.putByte("v", vcardVersion);
        b.putInt("m", maxListCount);
        b.putInt("l", listStartOffset);
        b.putString("f", outFilepath);
        m.obj = mask;
        mCommandHandler.sendMessage(m);
    }

    /**
     * Pull vcard listing with the specified name at the current path set by the setPath() function.
     * The vCard entry contents will be stored in a vCard file at the location specified by
     * outFilepath.The response is returned in the
     * {@link IBluetoothPbapClientEventHandler#onPullVcardListingCompleted} callback.
     *
     * @param name The name of the phonebook path to get a listing from. It can be one of the following:
     * <p>
     * <ul>
     * <li>Relative name, wherein you can specify a phonebook name relative to current path set
     * using {@link #setPath}. For ex. set root path to "\telecom", then specify "\mch" as name to
     * finally pull the listing from "\telecom\mch"</li>
     * <li>Absolute name, wherein you can specify an absolute path.
     * For ex. When the current path is set to root, you can specify "\telecom\mch" pull
     * the listing from server phone's missed call history</li>
     * <li>Empty name, i.e pass "" to set the phonebook browse path name to whatever path is set via
     * {@link #setPath}}
     * </li>
     * </ul>
     * </p>
     * @param searchOrder (optional) The order entries should be returned. Valid values are
     * {@link #SEARCH_ORDER_ALPHABETICAL},{@link #SEARCH_ORDER_INDEXED},
     * {@link #SEARCH_ORDER_PHONETICAL}, {@link #SEARCH_ORDER_NOT_SET}
     * @param searchAttribute  (optional) The attribute to search for. Valid values are
     * {@link #SEARCH_ATTRIBUTE_NAME}, {@link #SEARCH_ATTRIBUTE_NUMBER},
     * {@link #SEARCH_ATTRIBUTE_SOUND}, {@link #SEARCH_ATTRIBUTE_NOT_SET}
     * @param searchValue The value to search for. Note that this value is case sensitive.
     * For ex. If search value is "a", PBAP server does not return contact names starting from "A".
     * @param maxListCount specifies n number of entries to return based on the following criteria:
     * <p>
     * <ul>
     * <li>if n>0, returns n entries </li>
     * <li>if n=0, server will not return any content but instead return the total number of entries in the
     * {@link IBluetoothPbapClientEventHandler#onPullPhonebookCompleted phonebookSize} parameter of
     * {@link IBluetoothPbapClientEventHandler#onPullPhonebookCompleted} callback</li>
     * <li>if n<0, the server will return default number of entries (default is 65535)</li>
     * </ul>
     * </p>
     * @param listStartOffset The offset from which the records should be returned.
     * If <0, the offset is defaulted to 0.
     * @param outFilepath The destination file location where the vCard result should be stored.
     */
    public synchronized void pullVcardListing(String name, byte searchOrder, byte searchAttribute,
            String searchValue, int maxListCount, int listStartOffset, String outFilepath) {
        if (mCommandHandler == null) {
            Log.e(TAG, "pullVcardListing(): command handler not available");
            return;
        }

        Message m = mCommandHandler.obtainMessage(CommandHandler.MSG_PULL_VCARD_LISTING);
        Bundle b = m.getData();
        b.putString("n", name);
        b.putByte("o", searchOrder);
        b.putInt("m", maxListCount);
        b.putInt("l", listStartOffset);
        b.putString("f", outFilepath);
        b.putByte("a", searchAttribute);
        b.putString("v", searchValue);
        mCommandHandler.sendMessage(m);
    }

    /**
     * Abort the ongoing PBAP pull operation.
     */
    public void abort() {
        if (mPendingOperation != null)  mAbort = true;
    }

    /**
     * Adding PBAP client into the local SDP record
     *
     * @return false if failed to add to sdp record, true on success.
     *
     */
    public static boolean addSdp() {
        if (DBG) Log.d(TAG, "Adding PBAP client SDP record...");
        return BluetoothAdapter.getDefaultAdapter().setSdpRecord(true, 0X112E);
    }

    /**
     * Remove PBAP client into the local SDP record
     *
     * @return false if failed to remove from sdp record, true on success.
     */
    public static boolean removeSdp() {
        if (DBG) Log.d(TAG, "Removing PBAP client SDP record...");
        return BluetoothAdapter.getDefaultAdapter().setSdpRecord(false, 0X112E);
    }

    // -------------------Implementation----------------------------------

    /**
     * Called by command handler to handle a connect request
     */
    private synchronized void handleConnect() {
        if (isConnected()) {
            Log.w(TAG, "handleConnect(): already connected");
            sendConnectedEvent(false);
            return;
        }
        BluetoothSocket mSocket = null;
        mTransport = null;
        try {
            mSocket = mPbapServer.createRfcommSocketToServiceRecord(BluetoothUuid.PBAP_PSE
                    .getUuid());
            // mSocket = mPbapServer.createRfcommSocket(19);
            mSocket.connect();
            mTransport = new BluetoothPbapRfcommTransport(mSocket);
            mObexClientSession = new ClientSession(mTransport);
            if (mAuth != null) {
                mObexClientSession.setAuthenticator(this);
            }

            HeaderSet header = new HeaderSet();
            header.setHeader(HeaderSet.TARGET, PBAP_TARGET);
            HeaderSet result = mObexClientSession.connect(header);
            if (isResponseOk(result)) {
                sendConnectedEvent(true);
                return;
            } else {
                Log.w(TAG, "Obex connection failed with reponse code " + result.responseCode);
            }
            // Update the connection state
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create rfcomm socket to PBAP server", t);
        }

        // If we got here, the connection failed...Cleanup resources
        if (mObexClientSession != null) {
            try {
                mObexClientSession.close();
                mObexClientSession = null;
            } catch (Throwable tt) {
                Log.w(TAG, "Unable to close obex client session");
            }
        }
        if (mTransport != null) {
            try {
                mTransport.close();
                mTransport = null;
            } catch (Throwable tt) {
                Log.w(TAG, "Error closing PBAP obex transport", tt);
            }
        }

        sendConnectedEvent(false);
    }

    private void sendConnectionStateChangeEvent(int prevState, int state) {
        Log.d(TAG, "sendConnectionStateChangeEvent()");
        BluetoothAdapter.getDefaultAdapter().sendConnectionStateChange(mPbapServer,
            PBAPC_PROFILE_ID, state, prevState);
    }

    private void sendConnectedEvent(boolean success) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onConnected(mPbapServer, success);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling event handler onConnected()", t);
            }
            if (success) {
                sendConnectionStateChangeEvent(STATE_DISCONNECTED, STATE_CONNECTED);

            }
        }
    }

    /**
     * Called by command handler to handle a disconnect request
     */
    private synchronized void handleDisconnect() {
        if (!isConnected()) {
            Log.w(TAG, "handleDisconnect(): already disconnected");
            sendDisconnectedEvent(false);
            return;
        }
        Log.d(TAG, "handleDisconnect()");
        if (mObexClientSession != null) {
            try {
                mObexClientSession.close();
                mObexClientSession = null;
            } catch (Throwable t) {
                Log.e(TAG, "Unable to close rfcomm socket to PBAP server", t);
                sendDisconnectedEvent(false);
                return;
            }
        }
        if (mTransport != null) {
            try {
                mTransport.close();
            } catch (Throwable tt) {
                Log.w(TAG, "Error closing PBAP obex transport", tt);
            }
        }
        sendDisconnectedEvent(true);
    }

    private void sendDisconnectedEvent(boolean success) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onDisconnected(mPbapServer, success);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling pathevent handler onDisconnected()", t);
            }
            if (success) {
                sendConnectionStateChangeEvent(STATE_CONNECTED, STATE_DISCONNECTED);
            }
        }
    }

    private void handleSetPath(String path) {
        Log.w(TAG, "handleSetPath: path: " + path);

        // validate the path
        if (!sPathValidator.contains(path)) {
            Log.w(TAG, "handleSetPath: invalid path: " + path);
            sendSetPathEvent(false, path);
            return;
        }

        HeaderSet header = new HeaderSet();
        HeaderSet result = null;
        // set path to root
        try {
            //header.setHeader(HeaderSet.NAME, "");
            header.setEmptyHeader(HeaderSet.NAME);
            result = mObexClientSession.setPath(header, false, false);
            if (!isResponseOk(result)) {
                Log.e(TAG, "Unable to set path to root..");
                sendSetPathEvent(false, path);
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to set path to root", t);
            sendSetPathEvent(false, path);
            handleDisconnect();
            return;
        }

        StringTokenizer st = new StringTokenizer(path, "/");
        String childPath = null;
        try {
            while (st.hasMoreTokens()) {
                childPath = st.nextToken();
                Log.d(TAG, "Setting child path to: " + childPath);
                header.setHeader(HeaderSet.NAME, childPath);
                result = mObexClientSession.setPath(header, false, false);
                if (!isResponseOk(result)) {
                    sendSetPathEvent(false, path);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to set child path to " + childPath, t);
            sendSetPathEvent(false, path);
            handleDisconnect();
            return;
        }
        sendSetPathEvent(true, path);
    }

    private void sendSetPathEvent(boolean success, String path) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onPathSet(mPbapServer, success, path);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling event handler onPathSet()", t);
            }
        }
    }

    private void handlePullPhonebook(String path, BluetoothAttributeMask mask, byte vcardVersion,
            int maxListCount, int listStartOffset, String outputFilepath) {
        if (DBG) {
            Log.d(TAG, "handlePullPhonebook(): path=" + path + ", mask="
                    + (mask == null ? "" : mask.toDebugString()) + ", vcardVersion=" + vcardVersion
                    + ", maxListCount=" + maxListCount + ", listStartOffset=" + listStartOffset
                    + ",outputFilepath=" + outputFilepath);
        }

        if(path == null){
            Log.e(TAG, "handlePullPhonebook(): invalid server path: " + path);
            sendPullPhonebookEvent(path, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            return;
        }

        // Ensure path ends with .vcf
        if (!path.endsWith(".vcf")) {
            path = path + ".vcf";
        }

        // Ensure path does not start with leading "/"
        if (path.startsWith("/") && path.length() > 1) {
            path = path.substring(1);
        }

        // Check file is writable
        File outfile = new File(outputFilepath);
        if (!IOUtils.isValidParentPath(outfile)) {
            Log.e(TAG, "handlePullPhonebook(): invalid outfile path: " + outputFilepath);
            sendPullPhonebookEvent(path, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            return;
        }

        // Check valid obex client session
        if (mObexClientSession == null) {
            Log.e(TAG, "handlePullPhonebook(): Obex session not created...");
            sendPullPhonebookEvent(path, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            return;
        }

        if (!isValidVcardVersion(vcardVersion)) {
            Log.e(TAG, "handlePullPhonebook(): setting vCard version to default: "
                    + DEFAULT_VCARD_VERSION);
            vcardVersion = DEFAULT_VCARD_VERSION;
        }

        if (maxListCount < 0 || maxListCount > MAX_LIST_COUNT_MAX) {
            Log.e(TAG, "handlePullPhonebook(): setting maxListCount to default: "
                    + MAX_LIST_COUNT_MAX);
            maxListCount = MAX_LIST_COUNT_MAX;
        }

        HeaderSet header = new HeaderSet();
        header.setHeader(HeaderSet.NAME, path);
        header.setHeader(HeaderSet.TYPE, "x-bt/phonebook");
        ApplicationParameter appParam = new ApplicationParameter();

        // Optional: filter
        if (mask != null) {
            appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.FILTER_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.FILTER_LENGTH, mask.getBytes());
        }
        // Required: format
        appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.FORMAT_TAGID,
                ApplicationParameter.TRIPLET_LENGTH.FORMAT_LENGTH, new byte[] { vcardVersion });

        // Required: max list count
        appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.MAXLISTCOUNT_TAGID,
                ApplicationParameter.TRIPLET_LENGTH.MAXLISTCOUNT_LENGTH,
                toByteArray(maxListCount, ApplicationParameter.TRIPLET_LENGTH.MAXLISTCOUNT_LENGTH));

        // List offset
        if (listStartOffset >= 0) {
            appParam.addAPPHeader(
                    ApplicationParameter.TRIPLET_TAGID.LISTSTARTOFFSET_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH,
                    toByteArray(listStartOffset,
                            ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH));
        }
        header.setHeader(HeaderSet.APPLICATION_PARAMETER, appParam.getAPPparam());

        ClientOperation op = null;
        DataInputStream is = null;
        FileOutputStream fileos = null;
        int missedCalls = MISSED_CALLS_NOT_SET;
        int phonebookSize = PHONEBOOK_SIZE_NOT_SET;
        boolean isMchRequest = path.contains(MCH_PATH);
        boolean success = false;
        try {
            mAbort = false;
            op = (ClientOperation) mObexClientSession.get(header);
            if (op == null) {
                Log.e(TAG, "handlePullPhonebook(): unable to create OBEX get request");
                sendPullPhonebookEvent(path, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                        outputFilepath);
                return;
            }
            // Set the pending operation
            mPendingOperation = op;

            // Send request
            op.setFitOnOneGetPacket(true);
            op.getResponseCode();

            int bytesWritten = 0;
            fileos = new FileOutputStream(outfile);
            HeaderSet result = op.getReceivedHeader();
            if (isResponseContinue(result)) {

                // Read header
                byte[] appParamBytes = (byte[]) result.getHeader(HeaderSet.APPLICATION_PARAMETER);
                if (appParamBytes == null) {
                    if (DBG) {
                        Log.d(TAG, "No app param bytes!");
                    }
                } else {
                    AppParamValue v = new AppParamValue();
                    v.parse(appParamBytes);
                    if (maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) {
                        phonebookSize = v.mPhonebookSize;
                    }
                    if (isMchRequest) {
                        missedCalls = v.mMissedCalls;
                    }
                }
                is = op.openDataInputStream();
                do {
                    if (mAbort) {
                        op.abort();
                        break;
                    }
                    bytesWritten += copyAndReturn(is, fileos, BUFFER_SIZE);
                    op.continueOperation(true, true);
                    result = op.getReceivedHeader();
                } while (isResponseContinue(result));
                if (isResponseOk(result) && !mAbort) {
                    bytesWritten += copyAndReturn(is, fileos, BUFFER_SIZE);
                }
            }

            // Clear pending operation
            mPendingOperation = null;

            if (isResponseOk(result) && !mAbort) {
                byte[] appParamBytes = (byte[]) result.getHeader(HeaderSet.APPLICATION_PARAMETER);
                if (appParamBytes == null) {
                    if (DBG) {
                        Log.d(TAG, "No app param bytes!");
                    }
                } else {
                    AppParamValue v = new AppParamValue();
                    v.parse(appParamBytes);
                    if (maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) {
                        phonebookSize = v.mPhonebookSize;
                        Log.d(TAG, "Phonebook size = " + phonebookSize);
                    }
                    if (isMchRequest) {
                        missedCalls = v.mMissedCalls;
                        Log.d(TAG, "Missed calls = " + missedCalls);
                    }
                }

                if (bytesWritten <= 0) {
                    Log.d(TAG, "Reading from OK packet");
                    if(is == null)
                        is = op.openDataInputStream();
                    bytesWritten = IOUtils.copy(is, fileos, BUFFER_SIZE, true, false);

                }
                success = true;
            }
            IOUtils.safeClose(is);
            is = null;
            mAbort = false;
            IOUtils.safeClose(fileos);
            fileos = null;
            op.close();
            op = null;
            sendPullPhonebookEvent(path, success,
                    ((maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) ? phonebookSize
                            : PHONEBOOK_SIZE_NOT_SET), (isMchRequest ? missedCalls
                            : MISSED_CALLS_NOT_SET), outputFilepath);
        } catch (Throwable t) {
            Log.e(TAG, "handlePullPhonebook(): error ", t);
            // Clear the pending operation
            mPendingOperation = null;

            // IOUtils.safeClose(os);
            IOUtils.safeClose(is);
            IOUtils.safeClose(fileos);
            if (op != null) {
                try {
                    op.close();
                    op = null;
                } catch (Throwable tt) {
                    Log.w(TAG, "handlePullPhonebook(): error closing operation", tt);
                }
            }
            sendPullPhonebookEvent(path, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            handleDisconnect();
        }
    }

    private void sendPullPhonebookEvent(String path, boolean success, int phonebookSize,
            int newMissedCalls, String outputFilepath) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onPullPhonebookCompleted(mPbapServer, path, success, phonebookSize,
                        newMissedCalls, outputFilepath);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling event handler onPullPhonebookCompleted()", t);
            }
        }
    }

    private void sendPullVcardListingEvent(String name, boolean success, int phonebookSize,
            int newMissedCalls, String outputFilepath) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onPullVcardListingCompleted(mPbapServer, name, success,
                        phonebookSize, newMissedCalls, outputFilepath);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling event handler onPullPhonebookCompleted()", t);
            }
        }
    }

    /**
     * Pull a vCard Listing from the PBAP server.
     */
    private synchronized void handlePullVcardListing(String name, byte searchOrder,
            byte searchAttribute, String searchValue, int maxListCount, int listStartOffset,
            String outputFilepath) {
        if (DBG) {
            Log.d(TAG, "handlePullVcardListing(): " + "name=" + (name == null ? "(null)" : name)
                    + ", searchOrder=" + searchOrder + ", searchAttribute=" + searchAttribute
                    + ", searchValue=" + (searchValue == null ? "(null)" : searchValue)
                    + ", maxListCount=" + maxListCount + ", listStartOffset=" + listStartOffset);
        }
        // Check valid search order
        if (!isValidSearchOrder(searchOrder)) {
            searchOrder = SEARCH_ORDER_NOT_SET;
        }

        if (!isValidSearchAttribute(searchAttribute)) {
            searchAttribute = SEARCH_ATTRIBUTE_NOT_SET;
        }

        // Check file is writable
        File outfile = new File(outputFilepath);
        if (!IOUtils.isValidParentPath(outfile)) {
            Log.e(TAG, "handlePullVcardListing(): invalid outfile path: " + outputFilepath);
            sendPullVcardListingEvent(name, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            return;
        }

        if (maxListCount < 0 || maxListCount > MAX_LIST_COUNT_MAX) {
            Log.e(TAG, "handlePullVcardListing(): setting maxListCount to default: "
                    + MAX_LIST_COUNT_MAX);
            maxListCount = MAX_LIST_COUNT_MAX;
        }

        HeaderSet header = new HeaderSet();
        // Null/empty path means current path
        header.setHeader(HeaderSet.NAME, (name == null ? "" : name));
        header.setHeader(HeaderSet.TYPE, "x-bt/vcard-listing");
        ApplicationParameter appParam = new ApplicationParameter();

        // Optional: search order
        if (searchOrder != SEARCH_ORDER_NOT_SET) {
            appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.ORDER_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.ORDER_LENGTH, new byte[] { searchOrder });
        }

        // Optional: search attribute/value
        if (searchAttribute != SEARCH_ATTRIBUTE_NOT_SET) {
            appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.SEARCH_ATTRIBUTE_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.SEARCH_ATTRIBUTE_LENGTH,
                    new byte[] { searchAttribute });
            // Also set search value
            if (searchValue != null && searchValue.length() > 0) {
                // Don't send a zero length request
                byte[] searchBytes = null;
                try {
                    searchBytes = searchValue.getBytes("UTF-8");
                } catch (Throwable t) {
                    Log.e(TAG, "Unable to get search string in UTF-8 encoding", t);
                }
                if (searchBytes != null)
                    Log.e(TAG, "Adding search value header.  Length = " + searchBytes.length);
                appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.SEARCH_VALUE_TAGID,
                        (byte) searchBytes.length, searchBytes);
            }
        }

        // Required: max list count
        appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.MAXLISTCOUNT_TAGID,
                ApplicationParameter.TRIPLET_LENGTH.MAXLISTCOUNT_LENGTH,
                toByteArray(maxListCount, ApplicationParameter.TRIPLET_LENGTH.MAXLISTCOUNT_LENGTH));

        // List offset
        if (listStartOffset >= 0) {
            appParam.addAPPHeader(
                    ApplicationParameter.TRIPLET_TAGID.LISTSTARTOFFSET_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH,
                    toByteArray(listStartOffset,
                            ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH));
        }
        header.setHeader(HeaderSet.APPLICATION_PARAMETER, appParam.getAPPparam());

        ClientOperation op = null;
        DataInputStream is = null;
        FileOutputStream fileos = null;
        int missedCalls = MISSED_CALLS_NOT_SET;
        int phonebookSize = PHONEBOOK_SIZE_NOT_SET;
        boolean isMchRequest = true;// FIXME: assume always yes (we can't
                                    // tell
                                    // since we don't
        boolean success = false;
        // remember the path
        try {
            mAbort = false;
            op = (ClientOperation) mObexClientSession.get(header);
            if (op == null) {
                Log.e(TAG, "handlePullVcardListing(): unable to create OBEX get request");
                sendPullVcardListingEvent(name, false, PHONEBOOK_SIZE_NOT_SET,
                        MISSED_CALLS_NOT_SET, outputFilepath);
                return;
            }
            // Set the pending operation
            mPendingOperation = op;

            // Send request
            op.setFitOnOneGetPacket(true);
            op.getResponseCode();

            int bytesWritten = 0;
            fileos = new FileOutputStream(outfile);
            HeaderSet result = op.getReceivedHeader();
            if (isResponseContinue(result)) {

                // Read header
                byte[] appParamBytes = (byte[]) result.getHeader(HeaderSet.APPLICATION_PARAMETER);
                if (appParamBytes == null) {
                    if (DBG) {
                        Log.d(TAG, "No app param bytes!");
                    }
                } else {
                    AppParamValue v = new AppParamValue();
                    v.parse(appParamBytes);
                    if (maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) {
                        phonebookSize = v.mPhonebookSize;
                    }
                    if (isMchRequest) {
                        missedCalls = v.mMissedCalls;
                    }
                }
                is = op.openDataInputStream();
                do {
                    if (mAbort) {
                        op.abort();
                        break;
                    }
                    bytesWritten += copyAndReturn(is, fileos, BUFFER_SIZE);
                    op.continueOperation(true, true);
                    result = op.getReceivedHeader();
                } while (isResponseContinue(result));
                if (isResponseOk(result) && !mAbort) {
                    bytesWritten += copyAndReturn(is, fileos, BUFFER_SIZE);
                }
            }

            // Clear the pending operation
            mPendingOperation = null;

            if (isResponseOk(result) && !mAbort) {
                byte[] appParamBytes = (byte[]) result.getHeader(HeaderSet.APPLICATION_PARAMETER);
                if (appParamBytes == null) {
                    if (DBG) {
                        Log.d(TAG, "No app param bytes!");
                    }
                } else {
                    AppParamValue v = new AppParamValue();
                    v.parse(appParamBytes);
                    if (maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) {
                        phonebookSize = v.mPhonebookSize;
                        Log.d(TAG, "Phonebook size = " + phonebookSize);
                    }
                    if (isMchRequest) {
                        missedCalls = v.mMissedCalls;
                        Log.d(TAG, "Missed calls = " + missedCalls);
                    }
                }

                if (bytesWritten <= 0) {
                    Log.d(TAG, "Reading from OK packet");
                    if (is == null)
                        is = op.openDataInputStream();
                    bytesWritten = IOUtils.copy(is, fileos, BUFFER_SIZE, true, false);
                }
                success = true;
            }
            IOUtils.safeClose(is);
            is = null;
            mAbort = false;
            IOUtils.safeClose(fileos);
            fileos = null;
            op.close();
            op = null;
            sendPullVcardListingEvent(name, success,
                    ((maxListCount <= MAX_LIST_COUNT_RETURN_COUNT) ? phonebookSize
                            : PHONEBOOK_SIZE_NOT_SET), (isMchRequest ? missedCalls
                            : MISSED_CALLS_NOT_SET), outputFilepath);
        } catch (Throwable t) {
            Log.e(TAG, "handlePullVcardListing(): error ", t);
            // Clear the pending operation
            mPendingOperation = null;

            IOUtils.safeClose(is);
            IOUtils.safeClose(fileos);
            if (op != null) {
                try {
                    op.close();
                    op = null;
                } catch (Throwable tt) {
                    Log.w(TAG, "handlePullVcardListing(): error closing operation", tt);
                }
            }
            sendPullVcardListingEvent(name, false, PHONEBOOK_SIZE_NOT_SET, MISSED_CALLS_NOT_SET,
                    outputFilepath);
            handleDisconnect();
        }
    }

    /**
     * Pull a vcard entry with the specified name at the current path set by the setPath() function.
     * The vCard entry contents will be stored in a vCard file at the location specified by
     * outFilepath.The response is returned in the
     * {@link IBluetoothPbapClientEventHandler#onPullVcardEntryEvent} callback.
     *
     * @param name The vcf object name to be retrieved. The listing xml contains contact's name
     * and corresponding vcf file name associated to the contact as handle. Pass the handle value
     * for ex. "35.vcf". Note that this value can be either absolute or relative:
     * <p>
     * <ul>
     * <li>Relative name, specify a phonebook entry relative to current path set using {@link #setPath}.
     * For ex. set root path to "\telecom", then specify "\mch\35.vcf"
     * as name to finally pull "\telecom\mch\35.vcf" entry</li>
     * <li>Absolute name, wherein you can specify an absolute path.
     * For ex. When the current path is set to root, you can specify "\telecom\mch\35.vcf" pull
     * the entry from phone's missed call history</li>
     * </ul>
     * </p>
     * @param mask (optional) A {@link BluetoothAttributeMask} bit mask that describes the
     * content fields to be returned in the result.
     * @param vcardVersion specifies if the response is vCard 2.1 or 3.0: Possible values are
     * {@link #VCARD_VERSION_21}, {@link #VCARD_VERSION_30}, {@link #DEFAULT_VCARD_VERSION}.
     * @param outputFilepath The destination file location where the vCard result should be stored.
     */
    public synchronized void pullVcardEntry(String name, BluetoothAttributeMask mask, byte vcardVersion,
            String outputFilepath) {
        if (mCommandHandler == null) {
            Log.e(TAG, "pullPhonebook(): command handler not available");
            return;
        }

        Message m = mCommandHandler.obtainMessage(CommandHandler.MSG_PULL_VCARD_ENTRY);
        Bundle b = m.getData();
        b.putString("n", name);
        b.putByte("v", vcardVersion);
        b.putString("f", outputFilepath);
        m.obj = mask;
        mCommandHandler.sendMessage(m);
    }

    private void sendPullVcardEntryEvent(String name, boolean success, String outputFilepath) {
        if (mEventHandler != null) {
            try {
                mEventHandler.onPullVcardEntryEvent(mPbapServer, name, success, outputFilepath);
            } catch (Throwable t) {
                Log.w(TAG, "Eror calling event handler onPullPhonebookCompleted()", t);
            }
        }
    }

    private void handlePullVcardEntry(String name, BluetoothAttributeMask mask, byte vcardVersion,
            String outputFilepath) {
        boolean success = false;
        if (DBG) {
            Log.d(TAG, "handlePullVcardEntry(): name=" + name + ", mask="
                    + (mask == null ? "" : mask.toDebugString()) + ", vcardVersion=" + vcardVersion
                    + ",outputFilepath=" + outputFilepath);
        }

        // Check file is writable
        File outfile = new File(outputFilepath);
        if (!IOUtils.isValidParentPath(outfile)) {
            Log.e(TAG, "handlePullVcardEntry(): invalid outfile path: " + outputFilepath);
            sendPullVcardEntryEvent(name, false, outputFilepath);
            return;
        }

        if (!isValidVcardVersion(vcardVersion)) {
            Log.e(TAG, "handlePullVcardEntry(): setting vCard version to default: "
                    + DEFAULT_VCARD_VERSION);
            vcardVersion = DEFAULT_VCARD_VERSION;
        }

        HeaderSet header = new HeaderSet();
        header.setHeader(HeaderSet.NAME, name);
        header.setHeader(HeaderSet.TYPE, "x-bt/vcard");
        ApplicationParameter appParam = new ApplicationParameter();

        // Optional: filter
        if (mask != null) {
            appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.FILTER_TAGID,
                    ApplicationParameter.TRIPLET_LENGTH.FILTER_LENGTH, mask.getBytes());
        }

        // Required: format
        appParam.addAPPHeader(ApplicationParameter.TRIPLET_TAGID.FORMAT_TAGID,
                ApplicationParameter.TRIPLET_LENGTH.FORMAT_LENGTH, new byte[] { vcardVersion });

        header.setHeader(HeaderSet.APPLICATION_PARAMETER, appParam.getAPPparam());

        ClientOperation op = null;
        DataInputStream is = null;
        FileOutputStream fileos = null;
        try {
            op = (ClientOperation) mObexClientSession.get(header);
            if (op == null) {
                Log.e(TAG, "handlePullVcardEntry(): unable to create OBEX get request");
                sendPullVcardEntryEvent(name, false, outputFilepath);
                return;
            }

            // Set the pending operation
            mPendingOperation = op;

            // Send request
            op.setFitOnOneGetPacket(true);
            op.getResponseCode();

            int bytesWritten = 0;
            fileos = new FileOutputStream(outfile);
            HeaderSet result = op.getReceivedHeader();
            if (isResponseContinue(result)) {
                do {
                    Log.d(TAG, "Reading from continue packet");
                    is = op.openDataInputStream();
                    bytesWritten = IOUtils.copy(is, fileos, BUFFER_SIZE, true, false);
                    is = null;
                    Log.d(TAG, "Done reading from continue packet");
                    op.continueOperation(true, true);
                    result = op.getReceivedHeader();
                } while (isResponseContinue(result));
            }

            // Clear the pending operation
            mPendingOperation = null;

            if (isResponseOk(result)) {
                if (bytesWritten <= 0) {
                    Log.d(TAG, "Reading from OK packet");
                    is = op.openDataInputStream();
                    bytesWritten = IOUtils.copy(is, fileos, BUFFER_SIZE, true, false);
                    is = null;
                }
                success = true;
            }
            IOUtils.safeClose(fileos);
            fileos = null;
            op.close();
            op = null;
            sendPullVcardEntryEvent(name, success, outputFilepath);

        } catch (Throwable t) {
            Log.e(TAG, "handlePullVcardEntry(): error ", t);
            // Clear the pending operation
            mPendingOperation = null;

            IOUtils.safeClose(is);
            IOUtils.safeClose(fileos);
            if (op != null) {
                try {
                    op.close();
                    op = null;
                } catch (Throwable tt) {
                    Log.w(TAG, "handlePullVcardEntry(): error closing operation", tt);
                }
            }
            sendPullVcardEntryEvent(name, false, outputFilepath);
            handleDisconnect();
        }
    }

    /**
     * @hide
     */
    @Override
    public PasswordAuthentication onAuthenticationChallenge(String description,
            boolean isUserIdRequired, boolean isFullAccess) {
        Log.d(TAG, "onAuthenticationChallenge");
        synchronized (mAuthLock) {
            mPwdAuth = null;
            if (mAuth == null) {
                return null;
            }
            try {
                Message m = mCommandHandler.obtainMessage(CommandHandler.MSG_AUTH_CHALLENGE,
                        isUserIdRequired ? 1 : 0, isFullAccess ? 1 : 0, description);
                mCommandHandler.dispatchMessage(m);
                Log.d(TAG, "Wait for auth challenge....");
                mAuthLock.wait();
            } catch (Throwable t) {
                Log.e(TAG, "Error waiting for auth: ", t);
            }
            Log.d(TAG, "Returning auth challenge....");
            return mPwdAuth;
        }
    }

    /**
     * @hide
     */
    @Override
    public byte[] onAuthenticationResponse(byte[] userName) {
        Log.d(TAG, "onAuthenticationResponse");

        synchronized (mAuthLock) {
            mPwdAuth = null;
            if (mAuth == null) {
                return null;
            }
            try {
                Message m = mCommandHandler.obtainMessage(CommandHandler.MSG_AUTH, userName);
                mCommandHandler.dispatchMessage(m);
                Log.d(TAG, "Wait for auth result....");
                mAuthLock.wait();
            } catch (Throwable t) {
                Log.e(TAG, "Error waiting for auth: ", t);
            }
            Log.d(TAG, "Returning auth response....");
            return (mPwdAuth == null ? null : mPwdAuth.getPassword());
        }
    }

    /**
     * Set callback function to handle authentication request. Authentication procedures are invoked
     * through in the {@link IBluetoothPbapClientAuthenticator} callback interfaces.
     *
     * @param authHandler register {@link IBluetoothPbapClientAuthenticator} callback.
     *
     * @param authTimeoutMs set authetication timeout. Upon timeout either
     * {@link IBluetoothPbapClientAuthenticator#onAuthenticationTimeout} or
     * {@link IBluetoothPbapClientAuthenticator#onAuthenticationChallengeTimeout} callback is called.
     */
    public void setAuthHandler(IBluetoothPbapClientAuthenticator authHandler, int authTimeoutMs) {
        mAuthTimeoutMs = authTimeoutMs;
        mAuth = authHandler;
    }

    /**
     * Respond to authentication challenge request. Authentication procedures are invoked
     * through in the {@link IBluetoothPbapClientAuthenticator} callback interfaces.
     *
     * @param userName provide username, mandatory if isUserIdRequired is set in
     * {@link IBluetoothPbapClientAuthenticator#onAuthenticationChallenge}
     * @param password provide password
     */
    public void setAuthenticationChallengeResult(String userName, String password) {
        Log.d(TAG, "setAuthenticationChallengeResult()");
        synchronized (mAuthLock) {
            mCommandHandler.removeMessages(CommandHandler.MSG_AUTH_TIMEOUT);
            try {
                mPwdAuth = new PasswordAuthentication((userName == null ? NO_BYTES
                        : userName.getBytes("UTF-8")), password == null ? NO_BYTES
                        : password.getBytes("UTF-8"));
            } catch (Throwable t) {
                Log.e(TAG, "Error setting auth challenge result", t);
            }
            Log.d(TAG, "Notifying auth challenge....");
            mAuthLock.notifyAll();
        }
    }
    /**
     * Cancel ongoing OBEX Authentication procedure
     */
    public void cancelAuthentication() {
        Log.d(TAG, "cancelAuthentication()");
        synchronized (mAuthLock) {
            mCommandHandler.removeMessages(CommandHandler.MSG_AUTH_TIMEOUT);
            mPwdAuth = null;
            mAuthLock.notifyAll();
        }
    }

    private int copyAndReturn(DataInputStream is, FileOutputStream os, int bufferSize) {
        int totalBytes = 0;
        try {
            int bytesRead = 0;
            int available = 0;
            byte[] buf = new byte[bufferSize];
            while ((available = is.available()) > 0) {
                if (available < bufferSize) {
                    bytesRead = is.read(buf, 0, available);
                } else {
                    bytesRead = is.read(buf, 0, bufferSize);
                }
                os.write(buf, 0, bytesRead);
                totalBytes += bytesRead;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error copying data ", t);
        }
        return totalBytes;
    }
}
