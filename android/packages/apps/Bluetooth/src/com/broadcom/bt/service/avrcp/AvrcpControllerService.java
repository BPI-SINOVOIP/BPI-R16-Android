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

package com.broadcom.bt.service.avrcp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerCallback;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * Provides Bluetooth AVRCP profile Controller role, as a service in
 * the Bluetooth application.
 * @hide
 */
public class AvrcpControllerService extends ProfileService {
    private static final boolean DBG = true;
    public static final String TAG = "AvrcpControllerService";

    private static final int CHARSET_UTF_8 = 106;

    private static AvrcpControllerService sAvrcpControllerService = null;
    private RemoteCallbackList<IBluetoothAvrcpControllerCallback> mCallbacks =
                new RemoteCallbackList<IBluetoothAvrcpControllerCallback>();
    private AvrcpControllerServiceHandler mHandler = new AvrcpControllerServiceHandler();
    private List<BluetoothDevice> mTargets = new ArrayList<BluetoothDevice>(0);
    private List<Message> mCommands = new ArrayList<Message>(0);
    private List<Message> mWaitList = new ArrayList<Message>(0);
    private int mState = BluetoothProfile.STATE_DISCONNECTED;
    private BluetoothAvrcpBrowseItem[] mBrowseItems = null;

    private AudioManager mAudioManager;
    private int mAudioVolume = -1;
    private int mAudioVolumeMax;
    private static final int AVRCP_MAX_VOL = 127;

    static {
        classInitNative();
    }

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        if (DBG) Log.d(TAG, "initBinder");
        return new AvrcpControllerServiceBinder(this);
    }

    protected boolean start() {
        if (DBG) Log.d(TAG, "start");
        setAvrcpControllerService(this);
        initializeNative();

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mAudioVolumeMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioVolumeChanged(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

        registerReceiver(mAVRCPReceiver, new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION));

        return true;
    }

    protected boolean stop() {
        if (DBG) Log.d(TAG, "stop");

        unregisterReceiver(mAVRCPReceiver);

        return true;
    }

    protected boolean cleanup() {
        if (DBG) Log.d(TAG, "cleanup");
        cleanupNative();
        mTargets.clear();
        mCommands.clear();
        mWaitList.clear();
        clearAvrcpControllerService();
        mState = BluetoothProfile.STATE_DISCONNECTED;
        return true;
    }

    /**
     * Binder class that handles incoming service calls
     */
    private static class AvrcpControllerServiceBinder
    extends IBluetoothAvrcpController.Stub implements IProfileServiceBinder {
        private AvrcpControllerService mService;

        public AvrcpControllerServiceBinder(AvrcpControllerService svc) {
            mService = svc;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        private AvrcpControllerService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"AVRCP call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean registerCallback(IBluetoothAvrcpControllerCallback cb)
                throws RemoteException {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.registerCallback(cb);
        }

        public boolean unregisterCallback(IBluetoothAvrcpControllerCallback cb)
                throws RemoteException {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.unregisterCallback(cb);

        }

        public int getConnectionState(BluetoothDevice device) {
            AvrcpControllerService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            AvrcpControllerService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            AvrcpControllerService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        public boolean connect(BluetoothDevice target) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.connect(target);
        }

        public boolean disconnect(BluetoothDevice target) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.disconnect(target);
        }

        public int getTargetFeatures(BluetoothDevice target) {
            AvrcpControllerService service = getService();
            if (service == null) return 0;
            return service.getTargetFeatures(target);
        }

        public boolean sendPassThroughCommand(BluetoothDevice target,
                            byte command, byte buttonState) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.sendPassThroughCommand(target, command, buttonState);
        }

        public boolean listPlayerApplicationSettingAttributes(BluetoothDevice target) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.listPlayerApplicationSettingAttributes(target);
        }

        public boolean listPlayerApplicationSettingValues(BluetoothDevice target,
                            byte attribute) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.listPlayerApplicationSettingValues(target, attribute);
        }

        public boolean getCurrentPlayerApplicationSettingValue(BluetoothDevice target,
                            byte[] attributes) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getCurrentPlayerApplicationSettingValue(target, attributes);
        }

        public boolean setPlayerApplicationSettingValue(BluetoothDevice target,
                            byte[] attributes, byte[] values) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.setPlayerApplicationSettingValue(target, attributes, values);
        }

        public boolean getPlayerApplicationSettingAttributeText(BluetoothDevice target,
                            byte[] attributes) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getPlayerApplicationSettingAttributeText(target, attributes);
        }

        public boolean getPlayerApplicationSettingValueText(BluetoothDevice target,
                            byte attribute, byte[] values) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getPlayerApplicationSettingValueText(target, attribute, values);
        }

        public boolean getElementAttributes(BluetoothDevice target, int[] attributes) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getElementAttributes(target, attributes);
        }

        public boolean getPlayStatus(BluetoothDevice target) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getPlayStatus(target);
        }

        public boolean setAddressedPlayer(BluetoothDevice target, int playerId) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.setAddressedPlayer(target, playerId);
        }

        public boolean setBrowsedPlayer(BluetoothDevice target, int playerId) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.setBrowsedPlayer(target, playerId);
        }

        public boolean changePath(BluetoothDevice target, byte direction, long folderUid) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.changePath(target, direction, folderUid);
        }

        public boolean getFolderItems(BluetoothDevice target, byte scope, int startItem,
                            int endItem, int[] attributes) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getFolderItems(target, scope, startItem, endItem, attributes);
        }

        public boolean getItemAttributes(BluetoothDevice target, byte scope, long itemUid,
                            int[] attributes) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.getItemAttributes(target, scope, itemUid, attributes);
        }

        public boolean search(BluetoothDevice target, String searchString) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.search(target, searchString);
        }

        public boolean playItem(BluetoothDevice target, byte scope, long itemUid) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.playItem(target, scope, itemUid);
        }

        public boolean addToNowPlaying(BluetoothDevice target, byte scope, long itemUid) {
            AvrcpControllerService service = getService();
            if (service == null) return false;
            return service.addToNowPlaying(target, scope, itemUid);
        }

    };

    /**
    * API methods
    * These methods are invoked from Binder, they then send commands to service handler
    */
    public static synchronized AvrcpControllerService getAvrcpControllerService(){
        if (sAvrcpControllerService != null && sAvrcpControllerService.isAvailable()) {
            if (DBG) Log.d(TAG,"getAvrcpControllerService(): returning "+sAvrcpControllerService);
            return sAvrcpControllerService;
        }
        else {
            if (sAvrcpControllerService == null) {
                Log.w(TAG, "getAvrcpControllerService(): service is NULL");
            } else if (!(sAvrcpControllerService.isAvailable())) {
                Log.w(TAG,"getAvrcpControllerService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setAvrcpControllerService(AvrcpControllerService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setAvrcpControllerService(): set to: " + instance);
            sAvrcpControllerService = instance;
        } else {
            if (instance == null) {
                Log.w(TAG, "setAvrcpControllerService(): service is NULL");
            } else if (!instance.isAvailable()) {
                Log.w(TAG,"setAvrcpControllerService(): service is cleaning up");
            }
        }
    }

    private static synchronized void clearAvrcpControllerService() {
        if (DBG) Log.d(TAG, "clearAvrcpControllerService()");
        sAvrcpControllerService = null;
    }

    public boolean registerCallback(IBluetoothAvrcpControllerCallback cb) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) Log.d(TAG, "registerCallback(" + cb + ")");
        return mCallbacks.register(cb);
    }

    public boolean unregisterCallback(IBluetoothAvrcpControllerCallback cb) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) Log.d(TAG, "unregisterCallback(" + cb + ")");
        return mCallbacks.unregister(cb);
    }

    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (findConnectedDevice(device) != null)
            return BluetoothProfile.STATE_CONNECTED;
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mTargets;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        /* not needed: As avrcp connection is handled as part of a2dp */
        return false;
    }

    public int getPriority(BluetoothDevice device) {
        return BluetoothProfile.PRIORITY_UNDEFINED;
    }

    public boolean connect(BluetoothDevice target) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_CONNECT, target);
        boolean result = false;
        switch (mState) {
        case BluetoothProfile.STATE_DISCONNECTED:
            result = mHandler.sendMessage(m);
            break;
        case BluetoothProfile.STATE_CONNECTED:
            for (BluetoothDevice device : mTargets) {
                disconnect(device);
            }
            // fall through
        case BluetoothProfile.STATE_DISCONNECTING:
            result = mWaitList.add(m);
            break;
        default:
            break;
        }
        return result;
    }

    public boolean disconnect(BluetoothDevice target) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_DISCONNECT, target);
        if (findConnectedDevice(target) != null)
            return mHandler.sendMessage(m);
        return false;
    }

    public int getTargetFeatures(BluetoothDevice target) {
        byte[] address = Utils.getByteAddress(target);
        return getTargetFeaturesNative(address);
    }

    public boolean sendPassThroughCommand(BluetoothDevice target,
                        byte command, byte buttonState) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_PASS_THROUGH,
                                (int)command, (int)buttonState, target);
        return mHandler.sendMessage(m);
    }

    public boolean listPlayerApplicationSettingAttributes(BluetoothDevice target) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_LIST_SETTING_ATTRS, target);
        return mHandler.sendMessage(m);
    }

    public boolean listPlayerApplicationSettingValues(BluetoothDevice target,
                        byte attribute) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_LIST_SETTING_VALUES,
                                (int)attribute, 0, target);
        return mHandler.sendMessage(m);
    }

    public boolean getCurrentPlayerApplicationSettingValue(BluetoothDevice target,
                        byte[] attributes) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_SETTING_VALUE, target);
        Bundle data = m.getData();
        data.putByteArray("a", attributes);
        return mHandler.sendMessage(m);
    }

    public boolean setPlayerApplicationSettingValue(BluetoothDevice target,
                        byte[] attributes, byte[] values) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_SET_SETTING_VALUE, target);
        Bundle data = m.getData();
        data.putByteArray("a", attributes);
        data.putByteArray("v", values);
        return mHandler.sendMessage(m);
    }

    public boolean getPlayerApplicationSettingAttributeText(BluetoothDevice target,
                        byte[] attributes) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_SETTING_ATTR_TXT, target);
        Bundle data = m.getData();
        data.putByteArray("a", attributes);
        return mHandler.sendMessage(m);
    }

    public boolean getPlayerApplicationSettingValueText(BluetoothDevice target,
                        byte attribute, byte[] values) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_SETTING_VALUE_TXT,
                                (int)attribute, 0, target);
        Bundle data = m.getData();
        data.putByteArray("v", values);
        return mHandler.sendMessage(m);
    }

    public boolean getElementAttributes(BluetoothDevice target, int[] attributes) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_ELEMENT_ATTRS, target);
        Bundle data = m.getData();
        data.putIntArray("a", attributes);
        return mHandler.sendMessage(m);
    }

    public boolean getPlayStatus(BluetoothDevice target) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_PLAY_STATUS, target);
        return mHandler.sendMessage(m);
    }

    public boolean setAddressedPlayer(BluetoothDevice target, int playerId) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_SET_ADDRESSED_PLAYER,
                                playerId, 0, target);
        return mHandler.sendMessage(m);
    }

    public boolean setBrowsedPlayer(BluetoothDevice target, int playerId) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_SET_BROWSED_PLAYER,
                                playerId, 0, target);
        return mHandler.sendMessage(m);
    }

    public boolean changePath(BluetoothDevice target, byte direction, long folderUid) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_CHANGE_PATH,
                                (int)direction, 0, target);
        Bundle data = m.getData();
        data.putLong("uid", folderUid);
        return mHandler.sendMessage(m);
    }

    public boolean getFolderItems(BluetoothDevice target, byte scope, int startItem,
                        int endItem, int[] attributes) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_FOLDER_ITEMS,
                                (int)scope, 0, target);
        Bundle data = m.getData();
        data.putInt("si", startItem);
        data.putInt("ei", endItem);
        data.putIntArray("a", attributes);
        return mHandler.sendMessage(m);
    }

    public boolean getItemAttributes(BluetoothDevice target, byte scope, long itemUid,
                        int[] attributes) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_GET_ITEM_ATTRIBUTES, target);
        Bundle data = m.getData();
        data.putByte("s", scope);
        data.putLong("uid", itemUid);
        data.putIntArray("a", attributes);
        return mHandler.sendMessage(m);
    }

    public boolean search(BluetoothDevice target, String searchString) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_SEARCH, target);
        Bundle data = m.getData();
        data.putString("ss", searchString);
        return mHandler.sendMessage(m);
    }

    public boolean playItem(BluetoothDevice target, byte scope, long itemUid) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_PLAY_ITEM, target);
        Bundle data = m.getData();
        data.putByte("s", scope);
        data.putLong("uid", itemUid);
        return mHandler.sendMessage(m);
    }

    public boolean addToNowPlaying(BluetoothDevice target, byte scope, long itemUid) {
        Message m = mHandler.obtainMessage(mHandler.MSG_CMD_ADD_TO_NOW_PLAYING, target);
        Bundle data = m.getData();
        data.putByte("s", scope);
        data.putLong("uid", itemUid);
        return mHandler.sendMessage(m);
    }

    private void audioVolumeChanged(int volume) {
        if (mAudioVolume == volume) return;

        mAudioVolume = volume;
        Message m = mHandler.obtainMessage(mHandler.MSG_SVC_VOLUME_CHANGED, volume, 0, null);
        mHandler.sendMessage(m);
    }


    /**
    * Event callbacks from JNI/BTIF
    * These methods are invoked by JNI and then send events to service handler
    */
    private void onConnectionStateChanged(byte[] address, boolean success, int state) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_CONN_STATE_CHANGE, 0, state, address);
        Bundle data = m.getData();
        data.putBoolean("s", success);
        mHandler.sendMessage(m);
    }

    private void onListPlayerAttributeResponse(byte[] address, byte label, int status,
                    byte[] attributes) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_LIST_SETTING_ATTRS_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putByteArray("a", attributes);
        }
        mHandler.sendMessage(m);
    }

    private void onListPlayerValuesResponse(byte[] address, byte label, int status,
                    byte[] values) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_LIST_SETTING_VALUES_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putByteArray("v", values);
        }
        mHandler.sendMessage(m);
    }

    private void onGetPlayerValueResponse(byte[] address, byte label, int status,
                    byte[] attributes, byte[] values) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_SETTING_VALUE_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putByteArray("a", attributes);
            data.putByteArray("v", values);
        }
        mHandler.sendMessage(m);
    }

    private void onGetPlayerAttributesTextResponse(byte[] address, byte label, int status,
                    byte[] attributes, int[] charsets, Object[] attrTexts) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_SETTING_ATTR_TXT_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putByteArray("a", attributes);
            data.putStringArray("t", convertStringArray(attrTexts, charsets));
        }
        mHandler.sendMessage(m);
    }

    private void onGetPlayerValuesTextResponse(byte[] address, byte label, int status,
                    byte[] values, int[] charsets, Object[] valueTexts) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_SETTING_VALUE_TXT_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putByteArray("v", values);
            data.putStringArray("t", convertStringArray(valueTexts, charsets));
        }
        mHandler.sendMessage(m);
    }

    private void onGetElementAttributeResponse(byte[] address, byte label, int status,
                    int[] attributes, int[] charsets, Object[] valueTexts) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_ELEMENT_ATTRS_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putIntArray("a", attributes);
            data.putStringArray("t", convertStringArray(valueTexts, charsets));
        }
        mHandler.sendMessage(m);
    }

    private void onPlayStatusResponse(byte[] address, byte label, int status, int songLen,
                    int songPos, int playStatus) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_PLAY_STATUS_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putInt("sl", songLen);
            data.putInt("sp", songPos);
            data.putInt("ps", playStatus);
        }
        mHandler.sendMessage(m);
    }

    private void onSetBrowsedPlayerResponse(byte[] address, byte label, int status, int numItem,
                    int charset, Object[] folderPath) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_SET_BROWSED_PLAYER_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putInt("ni", numItem);
            data.putStringArray("fp", convertStringArray(folderPath, charset));
        }
        mHandler.sendMessage(m);
    }

    private void onChangePathResponse(byte[] address, byte label, int status, int numItem) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_CHANGE_PATH_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putInt("ni", numItem);
        }
        mHandler.sendMessage(m);
    }

    private void onGetFolderItemsResponseStart(int numItems) {
        mBrowseItems = new BluetoothAvrcpBrowseItem[numItems];
    }

    private void onPlayerItemResponse(int idx, int charset, byte[] dispName, int id,
                    byte majorType, int subType, int playStatus, byte[] features) {
        mBrowseItems[idx] = new BluetoothAvrcpBrowseItem();
        mBrowseItems[idx].mItemType = BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_PLAYER;
        mBrowseItems[idx].mDisplayableName = byteArrayToString(dispName, charset);
        mBrowseItems[idx].mPlayerId = id;
        mBrowseItems[idx].mPlayerMajorType = majorType;
        mBrowseItems[idx].mPlayerSubType = subType;
        mBrowseItems[idx].mPlayStatus = (byte)playStatus;
        for (int i = 0; i < features.length; i++) {
            mBrowseItems[idx].mPlayerFeatures[i] = features[i];
        }
    }

    private void onFolderItemResponse(int idx, int charset, byte[] dispName, byte[] uid,
                    byte type, byte isPlayable) {
        mBrowseItems[idx] = new BluetoothAvrcpBrowseItem();
        mBrowseItems[idx].mItemType = BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER;
        mBrowseItems[idx].mDisplayableName = byteArrayToString(dispName, charset);
        mBrowseItems[idx].mFolderUid = byteArrayToLong(uid);
        mBrowseItems[idx].mFolderType = type;
        mBrowseItems[idx].mIsPlayable = (isPlayable == 0) ? false : true;
    }

    private void onElementItemResponse(int idx, int charset, byte[] dispName, byte[] uid,
                    byte type, int[] attributes, int[] charsets, Object[] valueTexts) {
        mBrowseItems[idx] = new BluetoothAvrcpBrowseItem();
        mBrowseItems[idx].mItemType = BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT;
        mBrowseItems[idx].mDisplayableName = byteArrayToString(dispName, charset);
        mBrowseItems[idx].mElementUid = byteArrayToLong(uid);
        mBrowseItems[idx].mMediaType = type;
        mBrowseItems[idx].mAttributes = attributes;
        mBrowseItems[idx].mAttributeValues = convertStringArray(valueTexts, charsets);
    }

    private void onGetFolderItemsResponseEnd(byte[] address, byte label, int status) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_FOLDER_ITEMS_RSP,
                                status, (int)label, address);
        mHandler.sendMessage(m);
    }

    private void onGetItemAttributesResponse(byte[] address, byte label, int status,
                    int[] attributes, int[] charsets, Object[] valueTexts) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_GET_ITEM_ATTRS_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putIntArray("a", attributes);
            data.putStringArray("t", convertStringArray(valueTexts, charsets));
        }
        mHandler.sendMessage(m);
    }

    private void onSearchResponse(byte[] address, byte label, int status, int numItem) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_SEARCH_RSP,
                                status, (int)label, address);
        if (isSuccess(status)){
            Bundle data = m.getData();
            data.putInt("ni", numItem);
        }
        mHandler.sendMessage(m);
    }

    private void onPlayItemResponse(byte[] address, byte label, int status) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_PLAY_ITEM_RSP,
                                status, (int)label, address);
        mHandler.sendMessage(m);
    }

    private void onAddToNowPlayingResponse(byte[] address, byte label, int status) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_ADD_TO_NOW_PLAYING_RSP,
                                status, (int)label, address);
        mHandler.sendMessage(m);
    }

    private void onSetAbsoluteVolumeCommand(byte[] address, byte label, byte volume) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_SET_ABS_VOL,
                                (int)volume, (int)label, address);
        mHandler.sendMessage(m);
    }

    private void onPlayStatusChangedNotification(byte[] address, int playStatus) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_PLAYBACK_STATUS_CHANGED,
                                0, playStatus, address);
        mHandler.sendMessage(m);
    }

    private void onTrackChangedNotification(byte[] address, byte[] trackId) {
        int iTrackId = trackIdToInt(trackId);
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_TRACK_CHANGED,
                                0, iTrackId, address);
        mHandler.sendMessage(m);
    }

    private void onTrackReachedEndNotification(byte[] address) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_TRACK_REACHED_END, address);
        mHandler.sendMessage(m);
    }

    private void onTrackReachedStartNotification(byte[] address) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_TRACK_REACHED_START, address);
        mHandler.sendMessage(m);
    }

    private void onPlayPositionChangedNotification(byte[] address, int songPos) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_PLAYBACK_POSITION_CHANGED,
                                0, songPos, address);
        mHandler.sendMessage(m);
    }

    private void onAppSettingsChangedNotification(byte[] address, byte[] attributes,
                    byte[] values) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_SETTING_CHANGED, address);
        Bundle data = m.getData();
        data.putByteArray("a", attributes);
        data.putByteArray("v", values);
        mHandler.sendMessage(m);
    }

    private void onAddressedPlayerChangedNotification(byte[] address, int playerId) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_ADDRESSED_PLAYER_CHANGED,
                                0, playerId, address);
        mHandler.sendMessage(m);
    }

    private void onAvailablePlayersChangedNotification(byte[] address) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_AVAILABLE_PLAYERS_CHANGED, address);
        mHandler.sendMessage(m);
    }

    private void onNowPlayingChangedNotification(byte[] address) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_NOW_PLAYING_CHANGED, address);
        mHandler.sendMessage(m);
    }

    private void onUIDsChangedNotification(byte[] address) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_UIDS_CHANGED, address);
        mHandler.sendMessage(m);
    }

    private void onCommandTimeout(byte[] address, byte label) {
        Message m = mHandler.obtainMessage(mHandler.MSG_EVT_COMMAND_TIMEOUT,
                                0, (int)label, address);
        mHandler.sendMessage(m);
    }

    /**
    * Service handler - processes commands and events from app and stack
    */
    class AvrcpControllerServiceHandler extends Handler {
        /** Message - Commands from app */
        public static final int MSG_CMD_BASE = 1000;
        public static final int MSG_CMD_CONNECT = 1001;
        public static final int MSG_CMD_DISCONNECT = 1002;
        public static final int MSG_CMD_PASS_THROUGH = 1003;
        public static final int MSG_CMD_LIST_SETTING_ATTRS = 1004;
        public static final int MSG_CMD_LIST_SETTING_VALUES = 1005;
        public static final int MSG_CMD_GET_SETTING_VALUE = 1006;
        public static final int MSG_CMD_SET_SETTING_VALUE = 1007;
        public static final int MSG_CMD_GET_SETTING_ATTR_TXT = 1008;
        public static final int MSG_CMD_GET_SETTING_VALUE_TXT = 1009;
        public static final int MSG_CMD_GET_ELEMENT_ATTRS = 1010;
        public static final int MSG_CMD_GET_PLAY_STATUS = 1011;
        public static final int MSG_CMD_SET_ADDRESSED_PLAYER = 1012;
        public static final int MSG_CMD_SET_BROWSED_PLAYER = 1013;
        public static final int MSG_CMD_CHANGE_PATH = 1014;
        public static final int MSG_CMD_GET_FOLDER_ITEMS = 1015;
        public static final int MSG_CMD_GET_ITEM_ATTRIBUTES = 1016;
        public static final int MSG_CMD_SEARCH = 1017;
        public static final int MSG_CMD_PLAY_ITEM = 1018;
        public static final int MSG_CMD_ADD_TO_NOW_PLAYING = 1019;
        public static final int MSG_CMD_MAX = 1099;

        /** Message - Events from stack */
        public static final int MSG_EVT_BASE = 2000;
        public static final int MSG_EVT_CONN_STATE_CHANGE = 2001;
        public static final int MSG_EVT_LIST_SETTING_ATTRS_RSP = 2002;
        public static final int MSG_EVT_LIST_SETTING_VALUES_RSP = 2003;
        public static final int MSG_EVT_GET_SETTING_VALUE_RSP = 2004;
        public static final int MSG_EVT_GET_SETTING_ATTR_TXT_RSP = 2005;
        public static final int MSG_EVT_GET_SETTING_VALUE_TXT_RSP = 2006;
        public static final int MSG_EVT_GET_ELEMENT_ATTRS_RSP = 2007;
        public static final int MSG_EVT_GET_PLAY_STATUS_RSP = 2008;
        public static final int MSG_EVT_SET_BROWSED_PLAYER_RSP = 2009;
        public static final int MSG_EVT_CHANGE_PATH_RSP = 2010;
        public static final int MSG_EVT_GET_FOLDER_ITEMS_RSP = 2011;
        public static final int MSG_EVT_GET_ITEM_ATTRS_RSP = 2012;
        public static final int MSG_EVT_SEARCH_RSP = 2013;
        public static final int MSG_EVT_PLAY_ITEM_RSP = 2014;
        public static final int MSG_EVT_ADD_TO_NOW_PLAYING_RSP = 2015;
        public static final int MSG_EVT_SET_ABS_VOL = 2016;
        public static final int MSG_EVT_PLAYBACK_STATUS_CHANGED = 2050;
        public static final int MSG_EVT_TRACK_CHANGED = 2051;
        public static final int MSG_EVT_TRACK_REACHED_END = 2052;
        public static final int MSG_EVT_TRACK_REACHED_START = 2053;
        public static final int MSG_EVT_PLAYBACK_POSITION_CHANGED = 2054;
        public static final int MSG_EVT_SETTING_CHANGED = 2055;
        public static final int MSG_EVT_ADDRESSED_PLAYER_CHANGED = 2056;
        public static final int MSG_EVT_AVAILABLE_PLAYERS_CHANGED = 2057;
        public static final int MSG_EVT_NOW_PLAYING_CHANGED = 2058;
        public static final int MSG_EVT_UIDS_CHANGED = 2059;
        public static final int MSG_EVT_COMMAND_TIMEOUT = 2090;
        public static final int MSG_EVT_MAX = 2099;

        /** Message - Service */
        public static final int MSG_SVC_BASE = 3000;
        public static final int MSG_SVC_TIMER_CONNECT = 3001;
        public static final int MSG_SVC_TIMER_DISCONNECT = 3002;
        public static final int MSG_SVC_VOLUME_CHANGED = 3003;
        public static final int MSG_SVC_MAX = 3099;

        /** Timers */
        public static final int TIMEOUT_CONNECT = 10000;
        public static final int TIMEOUT_DISCONNECT = 12000;

        public void handleCommandTimeoutMessage(Message m) {
            if (DBG) Log.d(TAG, "handleCommandTimeoutMessage(" + getMessageName(m.what) + ")");
            Bundle data = m.getData();

            if (isMessageCommand(m.what)) {
                BluetoothDevice target = (BluetoothDevice)m.obj;
                byte[] address = Utils.getByteAddress(target);

                switch (m.what) {
                case MSG_CMD_LIST_SETTING_ATTRS:
                    listPlayerAttributeResponseCallback(target, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_LIST_SETTING_VALUES:
                    listPlayerValuesResponseCallback(target, (byte)m.arg1, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_GET_SETTING_VALUE: {
                    byte[] attributes = data.getByteArray("a");
                    getPlayerValueResponseCallback(target, attributes, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    }
                    break;
                case MSG_CMD_GET_SETTING_ATTR_TXT: {
                    byte[] attributes = data.getByteArray("a");
                    getPlayerAttributesTextResponseCallback(target, attributes, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    }
                    break;
                case MSG_CMD_GET_SETTING_VALUE_TXT: {
                    byte[] values = data.getByteArray("v");
                    getPlayerValuesTextResponseCallback(target, (byte)m.arg1, values, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    }
                    break;
                case MSG_CMD_GET_ELEMENT_ATTRS: {
                    int[] attributes = data.getIntArray("a");
                    getElementAttributeResponseCallback(target, attributes, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    }
                    break;
                case MSG_CMD_GET_PLAY_STATUS:
                    getPlayStatusResponseCallback(target, 0, 0, (byte)0,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_SET_BROWSED_PLAYER:
                    setBrowsedPlayerResponseCallback(target, 0, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_CHANGE_PATH:
                    changePathResponseCallback(target, (byte)0, 0,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_GET_FOLDER_ITEMS:
                    getFolderItemsResponseCallback(target, (byte)m.arg1, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_GET_ITEM_ATTRIBUTES:
                    getItemAttributesResponseCallback(target, null, null,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_SEARCH:
                    searchResponseCallback(target, 0,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_PLAY_ITEM:
                    playItemResponseCallback(target,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                case MSG_CMD_ADD_TO_NOW_PLAYING:
                    addToNowPlayingResponseCallback(target,
                            BluetoothAvrcpController.STS_NO_RESPONSE);
                    break;
                }
            }
        }

        public void handleMessage(Message m) {
            if (DBG) Log.d(TAG, "handleMessage(" + getMessageName(m.what) + ")");
            Bundle data = m.getData();

            if (isMessageCommand(m.what)) {
                BluetoothDevice target = (BluetoothDevice)m.obj;
                byte[] address = Utils.getByteAddress(target);
                byte label;

                switch (m.what) {
                case MSG_CMD_CONNECT:
                    mState = BluetoothProfile.STATE_CONNECTING;
                    connectNative(address);
                    startTimer(MSG_SVC_TIMER_CONNECT, TIMEOUT_CONNECT);
                    break;
                case MSG_CMD_DISCONNECT:
                    mState = BluetoothProfile.STATE_DISCONNECTING;
                    disconnectNative(address);
                    startTimer(MSG_SVC_TIMER_DISCONNECT, TIMEOUT_DISCONNECT);
                    break;
                case MSG_CMD_PASS_THROUGH:
                    passThroughCommandNative(address, m.arg1, m.arg2, (byte)0, null);
                    break;
                case MSG_CMD_LIST_SETTING_ATTRS:
                    label = listPlayerAttributesNative(address);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        listPlayerAttributeResponseCallback(target, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    break;
                case MSG_CMD_LIST_SETTING_VALUES:
                    label = listPlayerValuesNative(address, (byte)m.arg1);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        listPlayerValuesResponseCallback(target, (byte)m.arg1, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    break;
                case MSG_CMD_GET_SETTING_VALUE: {
                    byte[] attributes = data.getByteArray("a");
                    label = getPlayerValuesNative(address, (byte)attributes.length, attributes);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getPlayerValueResponseCallback(target, attributes, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_SET_SETTING_VALUE: {
                    byte[] attributes = data.getByteArray("a");
                    byte[] values = data.getByteArray("v");
                    setPlayerValuesNative(address, (byte)attributes.length, attributes, values);
                    }
                    break;
                case MSG_CMD_GET_SETTING_ATTR_TXT: {
                    byte[] attributes = data.getByteArray("a");
                    label = getPlayerAttributesTextNative(address, (byte)attributes.length,
                                                          attributes);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getPlayerAttributesTextResponseCallback(target, attributes, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_GET_SETTING_VALUE_TXT: {
                    byte[] values = data.getByteArray("v");
                    label = getPlayerValuesTextNative(address, (byte)m.arg1, (byte)values.length,
                                                      values);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getPlayerValuesTextResponseCallback(target, (byte)m.arg1, values, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_GET_ELEMENT_ATTRS: {
                    int[] attributes = data.getIntArray("a");
                    label = getElementAttributesNative(address, longToByteArray((long)0),
                                                       (byte)attributes.length, attributes);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getElementAttributeResponseCallback(target, attributes, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_GET_PLAY_STATUS:
                    label = getPlayStatusNative(address);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getPlayStatusResponseCallback(target, 0, 0, (byte)0,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    break;
                case MSG_CMD_SET_ADDRESSED_PLAYER:
                    setAddressedPlayerNative(address, m.arg1);
                    break;
                case MSG_CMD_SET_BROWSED_PLAYER:
                    label = setBrowsedPlayerNative(address, m.arg1);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        setBrowsedPlayerResponseCallback(target, 0, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    break;
                case MSG_CMD_CHANGE_PATH: {
                    long folderUid = data.getLong("uid");
                    label = changePathNative(address, (byte)m.arg1, longToByteArray(folderUid));
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        changePathResponseCallback(target, (byte)0, 0,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_GET_FOLDER_ITEMS: {
                    int startItem = data.getInt("si");
                    int endItem = data.getInt("ei");
                    int[] attributes = data.getIntArray("a");
                    if (attributes != null)
                        label = getFolderItemsNative(address, (byte)m.arg1, startItem, endItem,
                                                     (byte)attributes.length, attributes);
                    else
                        label = getFolderItemsNative(address, (byte)m.arg1, startItem, endItem,
                                                     (byte)0, new int[0]);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getFolderItemsResponseCallback(target, (byte)m.arg1, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_GET_ITEM_ATTRIBUTES: {
                    byte scope = data.getByte("s");
                    long itemUid = data.getLong("uid");
                    int[] attributes = data.getIntArray("a");
                    label = getItemAttributesNative(address, scope, longToByteArray(itemUid),
                                                    (byte)attributes.length, attributes);
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        getItemAttributesResponseCallback(target, null, null,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_SEARCH: {
                    String searchString = data.getString("ss");
                    label = searchNative(address, getCharset(searchString), searchString.length(),
                                            searchString.getBytes());
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        searchResponseCallback(target, 0,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_PLAY_ITEM: {
                    byte scope = data.getByte("s");
                    long itemUid = data.getLong("uid");
                    label = playItemNative(address, scope, longToByteArray(itemUid));
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        playItemResponseCallback(target,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                case MSG_CMD_ADD_TO_NOW_PLAYING: {
                    byte scope = data.getByte("s");
                    long itemUid = data.getLong("uid");
                    label = addToNowPlayingNative(address, scope, longToByteArray(itemUid));
                    if(isValidLabel(label))
                        saveCommand(m, label);
                    else
                        addToNowPlayingResponseCallback(target,
                                BluetoothAvrcpController.STS_CMD_NOT_SENT);
                    }
                    break;
                }
            } else if (isMessageEvent(m.what)) {
                BluetoothDevice target =
                    BluetoothAdapter.getDefaultAdapter().getRemoteDevice((byte[])m.obj);
                Message cmd;

                switch (m.what) {
                case MSG_EVT_CONN_STATE_CHANGE:
                    updateConnectionState(target, m.arg2, data.getBoolean("s"));
                    connectionStateChangedCallback(target, m.arg2, data.getBoolean("s"));
                    break;
                case MSG_EVT_LIST_SETTING_ATTRS_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    listPlayerAttributeResponseCallback(target, data.getByteArray("a"), m.arg1);
                    break;
                case MSG_EVT_LIST_SETTING_VALUES_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    listPlayerValuesResponseCallback(target, (byte)cmd.arg1,
                                data.getByteArray("v"), m.arg1);
                    break;
                case MSG_EVT_GET_SETTING_VALUE_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getPlayerValueResponseCallback(target, data.getByteArray("a"),
                                data.getByteArray("v"), m.arg1);
                    break;
                case MSG_EVT_GET_SETTING_ATTR_TXT_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getPlayerAttributesTextResponseCallback(target, data.getByteArray("a"),
                                data.getStringArray("t"), m.arg1);
                    break;
                case MSG_EVT_GET_SETTING_VALUE_TXT_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getPlayerValuesTextResponseCallback(target, (byte)cmd.arg1,
                                data.getByteArray("v"), data.getStringArray("t"), m.arg1);
                    break;
                case MSG_EVT_GET_ELEMENT_ATTRS_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getElementAttributeResponseCallback(target, data.getIntArray("a"),
                                data.getStringArray("t"), m.arg1);
                    break;
                case MSG_EVT_GET_PLAY_STATUS_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getPlayStatusResponseCallback(target, data.getInt("sl"), data.getInt("sp"),
                                (byte)data.getInt("ps"), m.arg1);
                    break;
                case MSG_EVT_SET_BROWSED_PLAYER_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    setBrowsedPlayerResponseCallback(target, data.getInt("ni"),
                                data.getStringArray("fp"), m.arg1);
                    break;
                case MSG_EVT_CHANGE_PATH_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    changePathResponseCallback(target, (byte)cmd.arg1, data.getInt("ni"), m.arg1);
                    break;
                case MSG_EVT_GET_FOLDER_ITEMS_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getFolderItemsResponseCallback(target, (byte)cmd.arg1, mBrowseItems, m.arg1);
                    break;
                case MSG_EVT_GET_ITEM_ATTRS_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    getItemAttributesResponseCallback(target, data.getIntArray("a"),
                                data.getStringArray("t"), m.arg1);
                    break;
                case MSG_EVT_SEARCH_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    searchResponseCallback(target, data.getInt("ni"), m.arg1);
                    break;
                case MSG_EVT_PLAY_ITEM_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    playItemResponseCallback(target, m.arg1);
                    break;
                case MSG_EVT_ADD_TO_NOW_PLAYING_RSP:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    addToNowPlayingResponseCallback(target, m.arg1);
                    break;
                case MSG_EVT_SET_ABS_VOL:
                    setAbsoluteVolumeCallback(target, (byte)m.arg1, (byte)m.arg2);
                    break;
                case MSG_EVT_PLAYBACK_STATUS_CHANGED:
                    playStatusChangedNotificationCallback(target, (byte)m.arg2);
                    break;
                case MSG_EVT_TRACK_CHANGED:
                    trackChangedNotificationCallback(target, m.arg2);
                    break;
                case MSG_EVT_TRACK_REACHED_END:
                    trackReachedEndNotificationCallback(target);
                    break;
                case MSG_EVT_TRACK_REACHED_START:
                    trackReachedStartNotificationCallback(target);
                    break;
                case MSG_EVT_PLAYBACK_POSITION_CHANGED:
                    playPositionChangedNotificationCallback(target, m.arg2);
                    break;
                case MSG_EVT_SETTING_CHANGED:
                    appSettingsChangedNotificationCallback(target, data.getByteArray("a"),
                                data.getByteArray("v"));
                    break;
                case MSG_EVT_ADDRESSED_PLAYER_CHANGED:
                    addressedPlayerChangedNotificationCallback(target, m.arg2);
                    break;
                case MSG_EVT_AVAILABLE_PLAYERS_CHANGED:
                    availablePlayersChangedNotificationCallback(target);
                    break;
                case MSG_EVT_NOW_PLAYING_CHANGED:
                    nowPlayingContentChangedNotificationCallback(target);
                    break;
                case MSG_EVT_UIDS_CHANGED:
                    UIDsChangedNotificationCallback(target);
                    break;
                case MSG_EVT_COMMAND_TIMEOUT:
                    cmd = retrieveCommand((byte)m.arg2);
                    if (cmd == null) break;
                    handleCommandTimeoutMessage(cmd);
                    break;
                }
            } else if (isMessageService(m.what)) {
                switch (m.what) {
                case MSG_SVC_TIMER_CONNECT:
                case MSG_SVC_TIMER_DISCONNECT:
                    mState = BluetoothProfile.STATE_DISCONNECTED;
                    sendWaitCommands();
                    break;
                case MSG_SVC_VOLUME_CHANGED:
                    updateAbsoluteVolumeNative((byte)convertToAvrcpVolume(m.arg1));
                    break;
                }
            }
        }

        private boolean isMessageCommand(int msg) {
            if (msg > MSG_CMD_BASE && msg < MSG_CMD_MAX)
                return true;
            return false;
        }

        private boolean isMessageEvent(int msg) {
            if (msg > MSG_EVT_BASE && msg < MSG_EVT_MAX)
                return true;
            return false;
        }

        private boolean isMessageService(int msg) {
            if (msg > MSG_SVC_BASE && msg < MSG_SVC_MAX)
                return true;
            return false;
        }

        private String getMessageName(int what) {
            if (isMessageCommand(what)) {
                switch (what) {
                case MSG_CMD_CONNECT:
                    return "MSG_CMD_CONNECT";
                case MSG_CMD_DISCONNECT:
                    return "MSG_CMD_DISCONNECT";
                case MSG_CMD_PASS_THROUGH:
                    return "MSG_CMD_PASS_THROUGH";
                case MSG_CMD_LIST_SETTING_ATTRS:
                    return "MSG_CMD_LIST_SETTING_ATTRS";
                case MSG_CMD_LIST_SETTING_VALUES:
                    return "MSG_CMD_LIST_SETTING_VALUES";
                case MSG_CMD_GET_SETTING_VALUE:
                    return "MSG_CMD_GET_SETTING_VALUE";
                case MSG_CMD_SET_SETTING_VALUE:
                    return "MSG_CMD_SET_SETTING_VALUE";
                case MSG_CMD_GET_SETTING_ATTR_TXT:
                    return "MSG_CMD_GET_SETTING_ATTR_TXT";
                case MSG_CMD_GET_SETTING_VALUE_TXT:
                    return "MSG_CMD_GET_SETTING_VALUE_TXT";
                case MSG_CMD_GET_ELEMENT_ATTRS:
                    return "MSG_CMD_GET_ELEMENT_ATTRS";
                case MSG_CMD_GET_PLAY_STATUS:
                    return "MSG_CMD_GET_PLAY_STATUS";
                case MSG_CMD_SET_ADDRESSED_PLAYER:
                    return "MSG_CMD_SET_ADDRESSED_PLAYER";
                case MSG_CMD_SET_BROWSED_PLAYER:
                    return "MSG_CMD_SET_BROWSED_PLAYER";
                case MSG_CMD_CHANGE_PATH:
                    return "MSG_CMD_CHANGE_PATH";
                case MSG_CMD_GET_FOLDER_ITEMS:
                    return "MSG_CMD_GET_FOLDER_ITEMS";
                case MSG_CMD_GET_ITEM_ATTRIBUTES:
                    return "MSG_CMD_GET_ITEM_ATTRIBUTES";
                case MSG_CMD_SEARCH:
                    return "MSG_CMD_SEARCH";
                case MSG_CMD_PLAY_ITEM:
                    return "MSG_CMD_PLAY_ITEM";
                case MSG_CMD_ADD_TO_NOW_PLAYING:
                    return "MSG_CMD_ADD_TO_NOW_PLAYING";
                }
            } else if (isMessageEvent(what)) {
                switch (what) {
                case MSG_EVT_CONN_STATE_CHANGE:
                    return "MSG_EVT_CONN_STATE_CHANGE";
                case MSG_EVT_LIST_SETTING_ATTRS_RSP:
                    return "MSG_EVT_LIST_SETTING_ATTRS_RSP";
                case MSG_EVT_LIST_SETTING_VALUES_RSP:
                    return "MSG_EVT_LIST_SETTING_VALUES_RSP";
                case MSG_EVT_GET_SETTING_VALUE_RSP:
                    return "MSG_EVT_GET_SETTING_VALUE_RSP";
                case MSG_EVT_GET_SETTING_ATTR_TXT_RSP:
                    return "MSG_EVT_GET_SETTING_ATTR_TXT_RSP";
                case MSG_EVT_GET_SETTING_VALUE_TXT_RSP:
                    return "MSG_EVT_GET_SETTING_VALUE_TXT_RSP";
                case MSG_EVT_GET_ELEMENT_ATTRS_RSP:
                    return "MSG_EVT_GET_ELEMENT_ATTRS_RSP";
                case MSG_EVT_GET_PLAY_STATUS_RSP:
                    return "MSG_EVT_GET_PLAY_STATUS_RSP";
                case MSG_EVT_SET_BROWSED_PLAYER_RSP:
                    return "MSG_EVT_SET_BROWSED_PLAYER_RSP";
                case MSG_EVT_CHANGE_PATH_RSP:
                    return "MSG_EVT_CHANGE_PATH_RSP";
                case MSG_EVT_GET_FOLDER_ITEMS_RSP:
                    return "MSG_EVT_GET_FOLDER_ITEMS_RSP";
                case MSG_EVT_GET_ITEM_ATTRS_RSP:
                    return "MSG_EVT_GET_ITEM_ATTRS_RSP";
                case MSG_EVT_SEARCH_RSP:
                    return "MSG_EVT_SEARCH_RSP";
                case MSG_EVT_PLAY_ITEM_RSP:
                    return "MSG_EVT_PLAY_ITEM_RSP";
                case MSG_EVT_ADD_TO_NOW_PLAYING_RSP:
                    return "MSG_EVT_ADD_TO_NOW_PLAYING_RSP";
                case MSG_EVT_SET_ABS_VOL:
                    return "MSG_EVT_SET_ABS_VOL";
                case MSG_EVT_PLAYBACK_STATUS_CHANGED:
                    return "MSG_EVT_PLAYBACK_STATUS_CHANGED";
                case MSG_EVT_TRACK_CHANGED:
                    return "MSG_EVT_TRACK_CHANGED";
                case MSG_EVT_TRACK_REACHED_END:
                    return "MSG_EVT_TRACK_REACHED_END";
                case MSG_EVT_TRACK_REACHED_START:
                    return "MSG_EVT_TRACK_REACHED_START";
                case MSG_EVT_PLAYBACK_POSITION_CHANGED:
                    return "MSG_EVT_PLAYBACK_POSITION_CHANGED";
                case MSG_EVT_SETTING_CHANGED:
                    return "MSG_EVT_SETTING_CHANGED";
                case MSG_EVT_ADDRESSED_PLAYER_CHANGED:
                    return "MSG_EVT_ADDRESSED_PLAYER_CHANGED";
                case MSG_EVT_AVAILABLE_PLAYERS_CHANGED:
                    return "MSG_EVT_AVAILABLE_PLAYERS_CHANGED";
                case MSG_EVT_NOW_PLAYING_CHANGED:
                    return "MSG_EVT_NOW_PLAYING_CHANGED";
                case MSG_EVT_UIDS_CHANGED:
                    return "MSG_EVT_UIDS_CHANGED";
                }
            }else {
                switch (what) {
                case MSG_SVC_TIMER_CONNECT:
                    return "MSG_SVC_TIMER_CONNECT";
                case MSG_SVC_TIMER_DISCONNECT:
                    return "MSG_SVC_TIMER_DISCONNECT";
                case MSG_SVC_VOLUME_CHANGED:
                    return "MSG_SVC_VOLUME_CHANGED";
                }
            }
            return "";
        }
    }

    private class AvrcpControllerServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "AvrcpControllerServiceBroadcastReceiver(" + intent + ") ");

            if (intent.getAction().equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                Log.w(TAG, "VOLUME_CHANGED_ACTION AvrcpControllerServiceBroadcastReceiver("
                           + intent + ") ");

                final int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE,
                        -1);
                if (streamType != AudioManager.STREAM_MUSIC) {
                    return;
                }

                final int newVolume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE,
                                                         0);
                final int oldVolume = intent.getIntExtra(
                        AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, 0);

                Log.w(TAG, "AvrcpControllerServiceBroadcastReceiver() oldVolume: " + oldVolume +
                           " newVolume: " + newVolume +
                           " Converted: " + convertToAvrcpVolume(newVolume));

                audioVolumeChanged(newVolume);

            }
        }
    }


    /**
    * Native method prototypes
    */
    private native static void classInitNative();

    private native void initializeNative();

    private native void cleanupNative();

    private native boolean connectNative(byte[] address);

    private native boolean disconnectNative(byte[] address);

    private native int getTargetFeaturesNative(byte[] address);

    private native boolean passThroughCommandNative(byte[] address, int cmd, int state,
                                byte data_len, byte[] ba_data);

    private native byte listPlayerAttributesNative(byte[] address);

    private native byte listPlayerValuesNative(byte[] address, byte attribute);

    private native byte getPlayerValuesNative(byte[] address, byte num_attributes,
                                byte[] attributes);

    private native boolean setPlayerValuesNative(byte[] address, byte num_attributes,
                                byte[] attributes, byte[] values);

    private native byte getPlayerAttributesTextNative(byte[] address, byte num_attributes,
                                byte[] attributes);

    private native byte getPlayerValuesTextNative(byte[] address, byte attribute,
                                byte num_values, byte[] values);

    private native byte getElementAttributesNative(byte[] address, byte[] element_id,
                                byte num_attributes, int[] attributes);

    private native byte getPlayStatusNative(byte[] address);

    private native boolean setAddressedPlayerNative(byte[] address, int player_id);

    private native byte setBrowsedPlayerNative(byte[] address, int player_id);

    private native byte changePathNative(byte[] address, byte direction, byte[] folder_uid);

    private native byte getFolderItemsNative(byte[] address, byte scope, int start_item,
                                int end_item, byte num_attributes, int[] attributes);

    private native byte getItemAttributesNative(byte[] address, byte scope, byte[] item_uid,
                                byte num_attributes, int[] attributes);

    private native byte searchNative(byte[] address, int charset, int length, byte[] string);

    private native byte playItemNative(byte[] address, byte scope, byte[] item_uid);

    private native byte addToNowPlayingNative(byte[] address, byte scope, byte[] item_uid);

    private native byte setAbsoluteVolumeResponseNative(byte[] address, byte volume,
                            byte label, int status);

    private native byte updateAbsoluteVolumeNative(byte volume);

    private final BroadcastReceiver mAVRCPReceiver = new AvrcpControllerServiceBroadcastReceiver();

    /**
    * Event callbacks to apps
    * These methods are invoked from service handler to send event to applications that had
    * registered callback functions
    */
    private void connectionStateChangedCallback(BluetoothDevice target,
                    int state, boolean success) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onConnectionStateChange(target, state, success);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void listPlayerAttributeResponseCallback(BluetoothDevice target,
                    byte[] attributes, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onListPlayerApplicationSettingAttributesRsp(target, attributes, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void listPlayerValuesResponseCallback(BluetoothDevice target,
                    byte attribute, byte[] values, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onListPlayerApplicationSettingValuesRsp(target, attribute, values, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getPlayerValueResponseCallback(BluetoothDevice target,
                    byte[] attributes, byte[] values, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetCurrentPlayerApplicationSettingValueRsp(target, attributes,
                            values, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getPlayerAttributesTextResponseCallback(BluetoothDevice target,
                    byte[] attributes, String[] texts, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetPlayerApplicationSettingAttributeTextRsp(target, attributes,
                            texts, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getPlayerValuesTextResponseCallback(BluetoothDevice target,
                    byte attribute, byte[] values, String[] texts, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetPlayerApplicationSettingValueTextRsp(target, attribute,
                            values, texts, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getElementAttributeResponseCallback(BluetoothDevice target,
                    int[] attributes, String[] texts, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetElementAttributesRsp(target, attributes, texts, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getPlayStatusResponseCallback(BluetoothDevice target, int songLen,
                    int songPos, byte playStatus, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetPlayStatusRsp(target, songLen, songPos, playStatus, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void setBrowsedPlayerResponseCallback(BluetoothDevice target, int numberOfItems,
                    String[] folderPath, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onSetBrowsedPlayerRsp(target, numberOfItems, folderPath, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void changePathResponseCallback(BluetoothDevice target, byte direction,
                    int numberOfItems, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onChangePathRsp(target, direction, numberOfItems, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getFolderItemsResponseCallback(BluetoothDevice target, byte scope,
                    BluetoothAvrcpBrowseItem[] items, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetFolderItemsRsp(target, scope, items, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void getItemAttributesResponseCallback(BluetoothDevice target,
                    int[] attributes, String[] valueTexts, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onGetItemAttributesRsp(target, attributes, valueTexts, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void searchResponseCallback(BluetoothDevice target, int numberOfItems,
                    int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onSearchRsp(target, numberOfItems, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void playItemResponseCallback(BluetoothDevice target, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onPlayItemRsp(target, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void addToNowPlayingResponseCallback(BluetoothDevice target, int status) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onAddToNowPlayingRsp(target, status);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void setAbsoluteVolumeCallback(BluetoothDevice target, byte volume, byte label) {
        byte[] address = Utils.getByteAddress(target);
        int audioVolume = convertToAudioStreamVolume(volume);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioVolume,
                                      AudioManager.FLAG_SHOW_UI);
        setAbsoluteVolumeResponseNative(address, volume, label,
                    BluetoothAvrcpController.STS_NO_ERROR);
    }

    private void playStatusChangedNotificationCallback(BluetoothDevice target, byte playStatus) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onPlaybackStatusChanged(target, playStatus);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void trackChangedNotificationCallback(BluetoothDevice target, int trackId) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onTrackChanged(target, trackId);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void trackReachedEndNotificationCallback(BluetoothDevice target) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onTrackReachedEnd(target);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void trackReachedStartNotificationCallback(BluetoothDevice target) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onTrackReachedStart(target);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void playPositionChangedNotificationCallback(BluetoothDevice target, int songPos) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onPlaybackPositionChanged(target, songPos);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void appSettingsChangedNotificationCallback(BluetoothDevice target, byte[] attributes,
                    byte[] values) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onPlayerAppSettingChanged(target, attributes, values);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void addressedPlayerChangedNotificationCallback(BluetoothDevice target, int playerId) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onAddressedPlayerChanged(target, playerId);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void availablePlayersChangedNotificationCallback(BluetoothDevice target) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onAvailablePlayersChanged(target);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void nowPlayingContentChangedNotificationCallback(BluetoothDevice target) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onNowPlayingContentChanged(target);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void UIDsChangedNotificationCallback(BluetoothDevice target) {
        int cbcount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbcount; i++) {
            IBluetoothAvrcpControllerCallback cb = mCallbacks.getBroadcastItem(i);
            if (cb != null) {
                try {
                    cb.onUIDsChanged(target);
                } catch (Throwable t) {
                    Log.w(TAG, "Error calling IBluetoothAvrcpControllerCallback", t);
                }
            }
        }
        mCallbacks.finishBroadcast();
    }

    /**
    * Private utility functions
    */
    private void updateConnectionState(BluetoothDevice target, int state, boolean success) {
        if (DBG) Log.d(TAG, "updateConnectionState device:" + target + ", state:" + state +
                       ", success:" + success);
        BluetoothDevice dev = findConnectedDevice(target);
        int prevState = (dev != null ?
                         BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED);
        int newState = (state == BluetoothAvrcpController.AVRC_CONTROL_STATE_CONNECTED ?
                        BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED);

        if (newState != mState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mTargets.add(target);
                stopTimer(mHandler.MSG_SVC_TIMER_CONNECT);
            } else {
                mTargets.remove(dev);
                mCommands.clear();
                if (success)
                    stopTimer(mHandler.MSG_SVC_TIMER_DISCONNECT);
                else
                    stopTimer(mHandler.MSG_SVC_TIMER_CONNECT);
            }
            mState = (mTargets.size() > 0 ? BluetoothProfile.STATE_CONNECTED :
                                            BluetoothProfile.STATE_DISCONNECTED);
            sendWaitCommands();

            notifyProfileConnectionStateChanged(target, BluetoothProfile.AVRCP_CT,
                                                newState, prevState);

            Intent intent = new Intent(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, target);
            sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    private BluetoothDevice findConnectedDevice(BluetoothDevice device) {
        String devAddr = device.getAddress();
        for (BluetoothDevice target : mTargets) {
            if (devAddr.equals(target.getAddress()))
                return target;
        }
        return null;
    }

    private boolean isSuccess(int status) {
        return (status == BluetoothAvrcpController.STS_NO_ERROR);
    }

    private int trackIdToInt(byte[] id) {
        return ((int)id[4] << 24 + (int)id[5] << 16 + (int)id[6] << 8 + id[7]);
    }

    private byte[] longToByteArray(long l) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (l & 0xFF);
            l = l >> 8;
        }
        return b;
    }

    private long byteArrayToLong(byte[] b) {
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l = l << 8;
            l |= (long) (b[i] & 0xFF);
        }
        return l;
    }

    private String[] convertStringArray(Object[] texts, int charset) {
        int size = texts.length;
        String[] array = new String[size];
        for (int i = 0; i < size; i++) {
            array[i] = byteArrayToString((byte[])texts[i], charset);
        }
        return array;
    }

    private String[] convertStringArray(Object[] texts, int[] charsets) {
        int size = texts.length;
        String[] array = new String[size];
        for (int i = 0; i < size; i++) {
            array[i] = byteArrayToString((byte[])texts[i], charsets[i]);
        }
        return array;
    }

    private String byteArrayToString(byte[] text, int charset) {
        return new String(text/*, getCharsetName(charset)*/);
    }

    private int getCharset(String str) {
        return CHARSET_UTF_8;
    }

    private String getCharsetName(int charset) {
        switch (charset) {
        case 3:     return "US-ASCII";
        case 4:     return "ISO-8859-1";
        case 106:   return "UTF-8";
        case 1013:  return "UTF-16BE";
        case 1014:  return "UTF-16LE";
        case 1015:  return "UTF-16";
        }
        return "????";
    }

    private boolean isValidLabel(byte label) {
        return (label != -1);
    }

    private void saveCommand(Message m, byte label) {
        // discard old command with same label
        retrieveCommand(label);
        // save command
        m.arg2 = (int)label;
        mCommands.add(Message.obtain(m));
    }

    private Message retrieveCommand(byte label) {
        for (Message m : mCommands) {
            if (m.arg2 == (int)label) {
                mCommands.remove(m);
                return m;
            }
        }
        return null;
    }

    private void startTimer(int timer, int timeoutMs) {
        Message m = mHandler.obtainMessage(timer);
        mHandler.sendMessageDelayed(m, timeoutMs);
    }

    private void stopTimer(int timer) {
        mHandler.removeMessages(timer);
    }

    private void sendWaitCommands() {
        for (Message m : mWaitList) {
            mHandler.sendMessage(m);
        }
        mWaitList.clear();
    }

    private int convertToAudioStreamVolume(int volume) {
        return (int) Math.ceil((double) volume * mAudioVolumeMax / AVRCP_MAX_VOL);
    }

    private int convertToAvrcpVolume(int volume) {
        return (int) Math.ceil((double) volume * AVRCP_MAX_VOL / mAudioVolumeMax);
    }

}
