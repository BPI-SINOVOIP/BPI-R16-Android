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

import java.util.HashMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Test service to allow a user to call PBAP client operations using adb shell
 * am startservice
 *
 * @author fredc@broadcom.com
 *
 */
public class PbapClientTestService extends Service implements IBluetoothPbapClientEventHandler,
        IBluetoothPbapClientAuthenticator {
    private static final String TAG = "PbapClient.PbapClientTestService";
    private static final String ACTION_AUTH_TEST = "com.broadcom.bt.pbap.auth.test";
    private static final String ACTION_ABORT="com.broadcom.bt.pbap.abort";
    private static final String ACTION_CONNECT = "com.broadcom.bt.pbap.connect";
    private static final String ACTION_DISCONNECT = "com.broadcom.bt.pbap.disconnect";
    private static final String ACTION_SET_PATH = "com.broadcom.bt.pbap.set_path";
    private static final String ACTION_PULL_PHONEBOOK = "com.broadcom.bt.pbap.pull_pb";
    private static final String ACTION_PULL_VCARD_LISTING = "com.broadcom.bt.pbap.pull_vlist";
    private static final String ACTION_PULL_VCARD_ENTRY = "com.broadcom.bt.pbap.pull_ventry";
    private static final String ACTION_ADD_SDP = "com.broadcom.bt.pbap.add_sdp";
    private static final String ACTION_REMOVE_SDP = "com.broadcom.bt.pbap.remove_sdp";
    private static final String EXTRA_SERVER_BDADDR = "bdaddr";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_MAX_LIST_COUNT = "maxListCount";
    private static final String EXTRA_LIST_START_OFFSET = "listStartOffset";
    private static final String EXTRA_VCARD_VERSION = "vcardVersion";
    private static final String EXTRA_ATTRIBUTE_MASK = "attrMask";
    private static final String EXTRA_OUTFILE_PATH = "outFilepath";
    private static final String EXTRA_SEARCH_ATTRIBUTE = "searchAttr";
    private static final String EXTRA_SEARCH_ORDER = "searchOrder";
    private static final String EXTRA_SEARCH_VALUE = "searchValue";

    private static final int NOTIFICATION_ID_AUTH = -8000002;

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
            // TODO Auto-generated method stub
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

    HashMap<String, BluetoothPbapClient> mClientMap = new HashMap<String, BluetoothPbapClient>();
    private PbapClientBroadcastReceiver mReceiver;

    public void onCreate() {
        super.onCreate();
        mReceiver = new PbapClientBroadcastReceiver();
        registerReceiver(mReceiver, mReceiver.createFilter());
    }

    public void onDestroy() {
        if (mReceiver != null) {
            try {
                unregisterReceiver(mReceiver);
            } catch (Throwable t) {
                Log.e(TAG, "Error unregistering receiver", t);
            }
            mReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(TAG, " Action == " + action);
        if (ACTION_CONNECT.equals(action)) {
            onConnect(intent);
        } else if (ACTION_DISCONNECT.equals(action)) {
            onDisconnect(intent);
        } else if (ACTION_SET_PATH.equals(action)) {
            onSetPath(intent);
        } else if (ACTION_PULL_PHONEBOOK.equals(action)) {
            onPullPhonebook(intent);
        } else if (ACTION_PULL_VCARD_LISTING.equals(action)) {
            onPullVcardListing(intent);
        } else if (ACTION_PULL_VCARD_ENTRY.equals(action)) {
            onPullVcardEntry(intent);
        } else if (ACTION_AUTH_TEST.equals(action)) {
            onAuthTest(intent);
        } else if (ACTION_ABORT.equals(action)) {
            onAbort(intent);
        } else if (ACTION_ADD_SDP.equals(action)) {
            onAddSdp(intent);
        } else if (ACTION_REMOVE_SDP.equals(action)) {
            onRemoveSdp(intent);
        } else {
            printHelp();
        }
        return Service.START_NOT_STICKY;
    }

    private BluetoothPbapClient getClient(Intent intent) {
        String bdaddr = intent.getStringExtra(EXTRA_SERVER_BDADDR);
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

        // Check if the client is already created
        BluetoothPbapClient client = mClientMap.get(bdaddr);
        if (client == null) {
            Log.e(TAG, "getClient(): PBAP server not connected: " + bdaddr);
            return null;
        }
        return client;
    }

    private void printHelpAttributeMask(StringBuilder help) {
        help.append("        (BIT ORDER: 64 ..... 0)\n");
        help.append("        BIT 0 : VERSION:     vCard Version\n");
        help.append("        BIT 1 : FN:          Formatted Name\n");
        help.append("        BIT 2 : N:           Structured Presentation of Name \n");
        help.append("        BIT 3 : PHOTO:       Associated Image or Photo\n");
        help.append("        BIT 4 : BDAY:        Birthday\n");
        help.append("        BIT 5 : ADR:         Delivery Address\n");
        help.append("        BIT 6 : LABEL:       Delivery\n");
        help.append("        BIT 7 : TEL:         Telephone Number\n");
        help.append("        BIT 8 : EMAIL:       Electronic Mail Address\n");
        help.append("        BIT 9 : MAILER:      Electronic Mail\n");
        help.append("        BIT 10: TZ:          Timezone\n");
        help.append("        BIT 11: GEO:         Geographic Position\n");
        help.append("        BIT 12: TITLE:       Job\n");
        help.append("        BIT 13: ROLE:        Role within the Organization\n");
        help.append("        BIT 14: LOGO:        Organization Logo\n");
        help.append("        BIT 15: AGENT:       vCard of Person Representing\n");
        help.append("        BIT 16: ORG:         Name of Organization\n");
        help.append("        BIT 17: NOTE:        Comments\n");
        help.append("        BIT 18: REV:         Revision\n");
        help.append("        BIT 19: SOUND:       Pronounciation of Name\n");
        help.append("        BIT 20: URL:         Uniform Resource Locator\n");
        help.append("        BIT 21: UID:         Unique ID\n");
        help.append("        BIT 22: KEY:         Public Encryption Key\n");
        help.append("        BIT 23: NICKNAME:    Nickname\n");
        help.append("        BIT 24: CATEGORIES:  Categories\n");
        help.append("        BIT 25: PROID:       Product ID\n");
        help.append("        BIT 26: CLASS:       Class Information\n");
        help.append("        BIT 27: SORT-STRING: String used for sort operations\n");
        help.append("        BIT 28: X-IRMC-CALL-DATETIME: Timestamp\n");
        help.append("     BIT 29-38: RESERVED...Set to 0's \n");
        help.append("        BIT 39: Proprietary Filter: Indicates use of proprietary filter\n");
        help.append("     BIT 40-63: Reserved for proprietary filter use\n");
    }

    private void printHelp() {
        StringBuilder help = new StringBuilder();
        String className = getClass().getName();
        String pkgName = getApplicationContext().getPackageName();

        help.append("==================PBAP Client Test Service===============================\n");
        help.append("Usage:\n");
        help.append("adb shell am startservice \n");
        help.append("    -a <action> [action params] \n");
        help.append("    -es " + EXTRA_SERVER_BDADDR + " <" + EXTRA_SERVER_BDADDR + "> \n");
        help.append("    " + pkgName + "/" + className + "\n\n");
        help.append("....where <action> [action_params] are as follows\n\n");
        help.append("connect to PBAP server:\n");
        help.append("    " + ACTION_CONNECT + "\n\n");
        help.append("disconnect from PBAP server:\n");
        help.append("    " + ACTION_DISCONNECT + "\n\n");
        help.append("set PBAP path:\n");
        help.append("    " + ACTION_SET_PATH + " -es " + EXTRA_PATH + " <" + EXTRA_PATH + ">\n\n");
        help.append("    Valid <" + EXTRA_PATH + "> values are:\n");
        help.append("        " + BluetoothPbapClient.PB_PATH + "\n");
        help.append("        " + BluetoothPbapClient.ICH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.OCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.MCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.CCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_PB_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_ICH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_OCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_MCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_CCH_PATH + "\n");
        help.append("\n");
        Log.d(TAG, help.toString());
        help = new StringBuilder();
        help.append("pull phonebook:\n");
        help.append("    " + ACTION_PULL_PHONEBOOK + " -es " + EXTRA_PATH + " <" + EXTRA_PATH
                + "> \n");
        help.append("                                 " + "-es " + EXTRA_VCARD_VERSION + " <"
                + EXTRA_VCARD_VERSION + ">\n");
        help.append("                                 " + "-es " + EXTRA_ATTRIBUTE_MASK + " <"
                + EXTRA_ATTRIBUTE_MASK + ">\n");
        help.append("                                 " + "-es " + EXTRA_MAX_LIST_COUNT + " <"
                + EXTRA_MAX_LIST_COUNT + ">\n");
        help.append("                                 " + "-es " + EXTRA_LIST_START_OFFSET + " <"
                + EXTRA_LIST_START_OFFSET + ">\n");
        help.append("                                 " + "-es " + EXTRA_OUTFILE_PATH + " <"
                + EXTRA_OUTFILE_PATH + ">\n\n");
        help.append("    Valid <" + EXTRA_PATH + "> values are:\n");
        help.append("        " + BluetoothPbapClient.PB_PATH + "\n");
        help.append("        " + BluetoothPbapClient.ICH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.OCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.MCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.CCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_PB_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_ICH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_OCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_MCH_PATH + "\n");
        help.append("        " + BluetoothPbapClient.SIM_CCH_PATH + "\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_VCARD_VERSION + "> values are:\n");
        help.append("        " + BluetoothPbapClient.VCARD_VERSION_21 + " : vCard 2.1\n");
        help.append("        " + BluetoothPbapClient.VCARD_VERSION_30 + " : vCard 3.0\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_MAX_LIST_COUNT + "> values are:\n");
        help.append("        " + BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET + " : not set\n");
        help.append("        " + BluetoothPbapClient.MAX_LIST_COUNT_RETURN_COUNT
                + " : for returning list count\n");
        help.append("        1-" + BluetoothPbapClient.MAX_LIST_COUNT_MAX + " : for n entries\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_LIST_START_OFFSET + "> values are:\n");
        help.append("        " + BluetoothPbapClient.LIST_OFFSET_NOT_SET + "   : not set\n");
        help.append("         0-n : for starting at offset n \n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_ATTRIBUTE_MASK + "> values are: \n");
        help.append("        -1 : not set\n");
        help.append("         0 : return all attributes\n\n");
        help.append("        OR a 64 bit number, with the following bit(s) set:\n");
        printHelpAttributeMask(help);
        help.append("\n");
        Log.d(TAG, help.toString());
        help = new StringBuilder();
        help.append("pull vcard listing:\n");
        help.append("    " + ACTION_PULL_VCARD_LISTING + " -es " + EXTRA_NAME + " <" + EXTRA_NAME
                + "> \n");
        help.append("                                    " + "[-es " + EXTRA_SEARCH_ORDER + " <"
                + EXTRA_SEARCH_ORDER + ">]\n");
        help.append("                                    " + "[-es " + EXTRA_SEARCH_ATTRIBUTE
                + " <" + EXTRA_SEARCH_ATTRIBUTE + ">]\n");
        help.append("                                    " + "[-es " + EXTRA_SEARCH_VALUE + " <"
                + EXTRA_SEARCH_VALUE + ">]\n");
        help.append("                                    " + "-es " + EXTRA_MAX_LIST_COUNT + " <"
                + EXTRA_MAX_LIST_COUNT + ">\n");
        help.append("                                    " + "-es " + EXTRA_LIST_START_OFFSET
                + " <" + EXTRA_LIST_START_OFFSET + ">\n");
        help.append("                                    " + "-es outFilepath <outFilepath>\n\n");
        help.append("    Valid <" + EXTRA_NAME
                + "> value is any directory name relative to the current path\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_SEARCH_ORDER + "> values are:\n");
        help.append("       " + BluetoothPbapClient.SEARCH_ORDER_NOT_SET + " : not set\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ORDER_ALPHABETICAL + " : alphabetical\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ORDER_INDEXED + " : indexed\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ORDER_PHONETICAL + " : phonetic\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_SEARCH_ATTRIBUTE + "> values are:\n");
        help.append("       " + BluetoothPbapClient.SEARCH_ATTRIBUTE_NOT_SET + " : not set\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ATTRIBUTE_NAME + " : name\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ATTRIBUTE_NUMBER + " : number\n");
        help.append("        " + BluetoothPbapClient.SEARCH_ATTRIBUTE_SOUND + " : sound\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_MAX_LIST_COUNT + "> values are:\n");
        help.append("       " + BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET + " : not set\n");
        help.append("        " + BluetoothPbapClient.MAX_LIST_COUNT_RETURN_COUNT
                + " : for returning list count\n");
        help.append("        1-" + BluetoothPbapClient.MAX_LIST_COUNT_MAX + " : for n entries\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_LIST_START_OFFSET + "> values are:\n");
        help.append("        " + BluetoothPbapClient.LIST_OFFSET_NOT_SET + "   : not set\n");
        help.append("         0-n : for starting at offset n \n");
        help.append("\n");
        help.append("\n");
        help.append("pull vcard entry:\n");
        help.append("    " + ACTION_PULL_VCARD_ENTRY + " -es " + EXTRA_NAME + " <" + EXTRA_NAME
                + "> \n");
        help.append("                                 " + "-es " + EXTRA_VCARD_VERSION + " <"
                + EXTRA_VCARD_VERSION + ">\n");
        help.append("                                 " + "-es " + EXTRA_ATTRIBUTE_MASK + " <"
                + EXTRA_ATTRIBUTE_MASK + ">\n");
        help.append("                                 " + "-es outFilepath <outFilepath>\n\n");
        help.append("    Valid <" + EXTRA_NAME
                + "> value is any entry name relative to the current path\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_VCARD_VERSION + "> values are:\n");
        help.append("        " + BluetoothPbapClient.VCARD_VERSION_21 + " : vCard 2.1\n");
        help.append("        " + BluetoothPbapClient.VCARD_VERSION_30 + " : vCard 3.0\n");
        help.append("\n");
        help.append("    Valid <" + EXTRA_ATTRIBUTE_MASK + "> values are :\n");
        help.append("        -1 : not set\n");
        help.append("         0 : return all attributes\n\n");
        help.append("        OR a 64 bit number, with the following bit(s) set:\n");
        printHelpAttributeMask(help);
        help.append("abort running operation:\n");
        help.append("    " + ACTION_ABORT+"\n");
        help.append("============================================================================");
        Log.d(TAG, help.toString());

    }

    private void onConnect(Intent intent) {
        String bdaddr = intent.getStringExtra(EXTRA_SERVER_BDADDR);
        if (bdaddr == null) {
            Log.e(TAG, "onConnect(): Invalid bdaddr: " + bdaddr);
            return;
        }
        bdaddr = bdaddr.toUpperCase();
        BluetoothDevice pbapServer = null;
        try {
            pbapServer = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdaddr);
        } catch (Throwable t) {
            Log.e(TAG, "onConnect(): unable to create BluetoothDevice with address: " + bdaddr, t);
            return;
        }

        // Check if the client is already created
        BluetoothPbapClient client = mClientMap.get(bdaddr);
        if (client == null) {
            client = new BluetoothPbapClient(this,pbapServer, this);
            client.setAuthHandler(this, 10000);
            mClientMap.put(bdaddr, client);
        }
        client.connect();
    }

    private void onDisconnect(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onDisconnect(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }

        client.disconnect();
    }

    private void onSetPath(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onSetPath(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }
        client.setPath(intent.getStringExtra(EXTRA_PATH));
    }

    private void onPullPhonebook(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onPullPhonebook(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }
        String path = intent.getStringExtra(EXTRA_PATH);
        String maxListCountStr = intent.getStringExtra(EXTRA_MAX_LIST_COUNT);
        int maxListCount = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            maxListCount = Integer.parseInt(maxListCountStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing maxListCount", t);
        }
        String listStartOffsetStr = intent.getStringExtra(EXTRA_LIST_START_OFFSET);
        int listStartOffset = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            listStartOffset = Integer.parseInt(listStartOffsetStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing listStartOffset", t);
        }
        String vcardVersionStr = intent.getStringExtra(EXTRA_VCARD_VERSION);
        byte vcardVersion = BluetoothPbapClient.DEFAULT_VCARD_VERSION;
        try {
            vcardVersion = Byte.parseByte(vcardVersionStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing vcardVersion", t);
        }

        BluetoothAttributeMask mask = null;
        String attrMaskStr = intent.getStringExtra(EXTRA_ATTRIBUTE_MASK);
        if (attrMaskStr != null && !attrMaskStr.equals("-1")) {
            try {
                long attrMask = Long.parseLong(attrMaskStr);
                mask = new BluetoothAttributeMask();
                mask.parse(attrMask);
            } catch (Throwable t) {
                Log.e(TAG, "Error parsing attrMask", t);
            }
        }

        String outFilepath = intent.getStringExtra(EXTRA_OUTFILE_PATH);
        if (outFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }
        Log.d(TAG, "onPullPhonebook(): path=" + (path == null ? "(null)" : path)
                + ", vcardVersion=" + vcardVersion + ", maxlistCount=" + maxListCount
                + ", listStartOffset=" + listStartOffset + ", outFilepath=" + outFilepath);
        client.pullPhonebook(path, mask, vcardVersion, maxListCount, listStartOffset, outFilepath);

    }

    private void onPullVcardListing(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onPullVcardListing(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }
        String name = intent.getStringExtra(EXTRA_NAME);
        String maxListCountStr = intent.getStringExtra(EXTRA_MAX_LIST_COUNT);
        int maxListCount = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            maxListCount = Integer.parseInt(maxListCountStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing maxListCount", t);
        }
        String listStartOffsetStr = intent.getStringExtra(EXTRA_LIST_START_OFFSET);
        int listStartOffset = BluetoothPbapClient.MAX_LIST_COUNT_NOT_SET;
        try {
            listStartOffset = Integer.parseInt(listStartOffsetStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing listStartOffset", t);
        }
        String searchOrderStr = intent.getStringExtra(EXTRA_SEARCH_ORDER);
        byte searchOrder = BluetoothPbapClient.SEARCH_ORDER_NOT_SET;
        try {
            searchOrder = Byte.parseByte(searchOrderStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing searchOrder", t);
        }

        String searchAttrStr = intent.getStringExtra(EXTRA_SEARCH_ATTRIBUTE);
        byte searchAttribute = 0;
        try {
            searchAttribute = Byte.parseByte(searchAttrStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing searchAttr", t);
        }

        String searchValue = intent.getStringExtra(EXTRA_SEARCH_VALUE);

        String outFilepath = intent.getStringExtra(EXTRA_OUTFILE_PATH);
        if (outFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }
        client.pullVcardListing(name, searchOrder, searchAttribute, searchValue, maxListCount,
                listStartOffset, outFilepath);

    }

    private void onPullVcardEntry(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onPullVcardEntry(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }
        String name = intent.getStringExtra(EXTRA_NAME);

        String vcardVersionStr = intent.getStringExtra(EXTRA_VCARD_VERSION);
        byte vcardVersion = BluetoothPbapClient.DEFAULT_VCARD_VERSION;
        try {
            vcardVersion = Byte.parseByte(vcardVersionStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing vcardVersion", t);
        }

        String attrMaskStr = intent.getStringExtra(EXTRA_ATTRIBUTE_MASK);
        long attrMask = 0;
        try {
            attrMask = Long.parseLong(attrMaskStr);
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing attrMask", t);
        }
        BluetoothAttributeMask mask = new BluetoothAttributeMask();
        mask.parse(attrMask);
        String outputFilepath = intent.getStringExtra(EXTRA_OUTFILE_PATH);
        if (outputFilepath == null) {
            Log.e(TAG, "Invalid out file path");
            return;
        }
        client.pullVcardEntry(name, mask, vcardVersion, outputFilepath);
    }

    private void onAbort(Intent intent) {
        // Check if the client is already created
        BluetoothPbapClient client = getClient(intent);
        if (client == null) {
            Log.e(TAG,
                    "onAbort(): PBAP server not connected: "
                            + intent.getStringExtra(EXTRA_SERVER_BDADDR));
            return;
        }
        client.abort();
    }

    private void onAddSdp(Intent intent) {
        BluetoothPbapClient.addSdp();
    }

    private void onRemoveSdp(Intent intent) {
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
    }

    @Override
    public void onPathSet(BluetoothDevice device, boolean success, String path) {
        Log.d(TAG, "onPathSet(): " + device + ", success=" + success + ", path=" + path);
    }

    @Override
    public void onPullPhonebookCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath) {
        Log.d(TAG, "onPullPhonebookCompleted(): " + device + ", path=" + path + ", success="
                + success + ", phonebookSize=" + phonebookSize + ", newMissedCalls="
                + newMissedCalls + ", outputFilepath=" + outputFilepath);

    }

    @Override
    public void onPullVcardListingCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath) {

    }

    @Override
    public void onPullVcardEntryEvent(BluetoothDevice device, String name, boolean success,
            String outputFilepath) {

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
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

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
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID_AUTH);
    }

    private void onAuthResponse(int authType, BluetoothDevice remoteDevice,
            boolean isUserIdRequired, String userName, String pass) {
        Log.d(TAG, "onAuthResponse()");
        BluetoothPbapClient client = mClientMap.get(remoteDevice.getAddress());
        if (client != null) {
            client.setAuthenticationChallengeResult(userName, pass);
        }

    }

    private void onCancelAuth(int authType, BluetoothDevice remoteDevice) {
        Log.d(TAG, "onCancelAuth()");
        removeAuthNotification();
        BluetoothPbapClient client = mClientMap.get(remoteDevice.getAddress());
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
