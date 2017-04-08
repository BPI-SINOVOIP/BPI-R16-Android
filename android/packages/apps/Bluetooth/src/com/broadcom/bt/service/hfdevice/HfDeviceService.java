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

package com.broadcom.bt.service.hfdevice;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import android.bluetooth.BluetoothAdapter;


import com.broadcom.bt.hfdevice.BluetoothHfDevice;
import com.broadcom.bt.hfdevice.IBluetoothHfDevice;
import com.broadcom.bt.hfdevice.IBluetoothHfDeviceCallback;
import com.broadcom.bt.hfdevice.BluetoothClccInfo;
import com.broadcom.bt.hfdevice.BluetoothPhoneBookInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * Provides Bluetooth Headset and Handsfree profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class HfDeviceService extends ProfileService {
    private static final boolean DBG = true;
    public static final String TAG = "HfDeviceService";

    private HfDeviceStateMachine mStateMachine;
    private static HfDeviceService sHfDeviceService;
    /* The list of all registered client callbacks, if callbacks are being used */
    private RemoteCallbackList<IBluetoothHfDeviceCallback> mCallbacks;

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothHfDeviceBinder(this);
    }

    protected boolean start() {
        mStateMachine = HfDeviceStateMachine.make(this);
        mCallbacks = new RemoteCallbackList<IBluetoothHfDeviceCallback>();
        setHfDeviceService(this);

        return true;
    }

    protected boolean stop() {
        return true;
    }

    protected boolean cleanup() {
        if (mStateMachine != null) {
            mStateMachine.cleanup();
            mStateMachine.doQuit();
        }

        clearHfDeviceService();
        return true;
    }


    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothHfDeviceBinder
    extends IBluetoothHfDevice.Stub implements IProfileServiceBinder {
        private HfDeviceService mService;

        public BluetoothHfDeviceBinder(HfDeviceService svc) {
            mService = svc;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        private HfDeviceService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"Headset call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean connect(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            HfDeviceService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HfDeviceService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean startVoiceRecognition(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.startVoiceRecognition(device);
        }

        public boolean stopVoiceRecognition(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.stopVoiceRecognition(device);
        }


        public int getAudioState(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
            return service.getAudioState(device);
        }

        public boolean connectAudio() {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.connectAudio();
        }

        public boolean disconnectAudio() {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.disconnectAudio();
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HfDeviceService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }


        public int[] getDeviceIndicators(BluetoothDevice device)
                throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return null;
            return service.getDeviceIndicators(device);
        }

        public int[] getCallStateInfo(BluetoothDevice device)
                throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return null;
            return service.getCallStateInfo(device);
        }

        public boolean setVolume(int type, int volume) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.setVolume(type, volume);
        }

        public boolean dial(String number) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.dial(number);
        }

        public boolean redial() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.redial();
        }

        public boolean answer() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.answer();
        }

        public boolean hangup() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.hangup();
        }

        public boolean hold(int holdType) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.hold(holdType);
        }

        public boolean sendDTMFcode(char dtmfcode) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.sendDTMFcode(new Character(dtmfcode));
        }

        public boolean sendVendorCmd(String atCmd) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.sendVendorCmd(atCmd);
        }

        public boolean queryOperatorSelectionInfo() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.queryOperatorSelectionInfo();
        }

        public boolean querySubscriberInfo() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.querySubscriberInfo();
        }

        public boolean getCLCC() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.getCLCC();
        }

        public boolean readPhoneBookList(String phoneMemType, int maxReadLimit)
                throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;

            if ((-1 > maxReadLimit) ||(0 == maxReadLimit))
                return false;
            return service.readPhoneBookList(phoneMemType, maxReadLimit);
        }

        public void registerEventHandler(IBluetoothHfDeviceCallback cb)
                throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) Log.d(TAG,"Service is null in unregisterEventHandler");
            service.registerEventHandler(cb);
            return;
        }

        public void unRegisterEventHandler(IBluetoothHfDeviceCallback cb)
                throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) Log.d(TAG,"Service is null in unregisterEventHandler");
            service.unRegisterEventHandler(cb);
            return;

        }

        public int getPeerFeatures() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return 0;
            return service.getPeerFeatures();
        }

        public int getLocalFeatures() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return 0;
            return service.getLocalFeatures();
        }

        public boolean sendKeyPressEvent() throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.sendKeyPressEvent();
        }

    public boolean sendBIA(boolean bEnableRoam, boolean bEnableService,
                boolean bEnableSignal, boolean bEnableBattery) throws RemoteException {
            HfDeviceService service = getService();
            if (service == null) return false;
            return service.sendBIA(bEnableRoam, bEnableService,
                                bEnableSignal, bEnableBattery);
        }

    };

    //API methods
    public static synchronized HfDeviceService getHfDeviceService(){
        if (sHfDeviceService != null && sHfDeviceService.isAvailable()) {
            if (DBG) Log.d(TAG, "getHfDeviceService(): returning " + sHfDeviceService);
            return sHfDeviceService;
        }
        if (DBG)  {
            if (sHfDeviceService == null) {
                Log.d(TAG, "getHfDeviceService(): service is NULL");
            } else if (!(sHfDeviceService.isAvailable())) {
                Log.d(TAG,"getHfDeviceService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setHfDeviceService(HfDeviceService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setHfDeviceService(): set to: " + sHfDeviceService);
            sHfDeviceService = instance;
        } else {
            if (DBG)  {
                if (sHfDeviceService == null) {
                    Log.d(TAG, "setHfDeviceService(): service not available");
                } else if (!sHfDeviceService.isAvailable()) {
                    Log.d(TAG,"setHfDeviceService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearHfDeviceService() {
        sHfDeviceService = null;
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState == BluetoothProfile.STATE_CONNECTED ||
            connectionState == BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        mStateMachine.sendMessage(HfDeviceStateMachine.CONNECT, device);
        return true;
    }

     public boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
            connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        mStateMachine.sendMessage(HfDeviceStateMachine.DISCONNECT, device);
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (mStateMachine == null) return new ArrayList<BluetoothDevice>(0);
        return mStateMachine.getConnectedDevices();
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getDevicesMatchingConnectionStates(states);
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getConnectionState(device);
    }

    boolean connectAudio() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (!mStateMachine.isConnected()) {
            return false;
        }

        if (mStateMachine.isAudioOn()) {
            return false;
        }
        mStateMachine.sendMessage(HfDeviceStateMachine.CONNECT_AUDIO);
        return true;
    }

    boolean disconnectAudio() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
      if (!mStateMachine.isAudioOn()) {
          return false;
      }
        mStateMachine.sendMessage(HfDeviceStateMachine.DISCONNECT_AUDIO);
        return true;
    }

    int getAudioState(BluetoothDevice device) {
        return mStateMachine.getAudioState(device);
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothHfDevicePriorityKey(device.getAddress()),
            priority);
        if (DBG) Log.d(TAG, "Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothHfDevicePriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }


    boolean startVoiceRecognition(BluetoothDevice device) {

        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
            connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        if (!mStateMachine.canStartVR())
            return false;

        mStateMachine.sendMessage(HfDeviceStateMachine.VOICE_RECOGNITION_START);
        return true;
    }

    boolean stopVoiceRecognition(BluetoothDevice device) {

        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        // It seem that we really need to check the AudioOn state.
        // But since we allow startVoiceRecognition in STATE_CONNECTED and
        // STATE_CONNECTING state, we do these 2 in this method
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
            connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        if (!mStateMachine.canStopVR())
            return false;

        mStateMachine.sendMessage(HfDeviceStateMachine.VOICE_RECOGNITION_STOP);

        return true;
    }

    boolean setVolume(int type, int volume){

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Message msg = mStateMachine.obtainMessage(HfDeviceStateMachine.SET_VOLUME);
        msg.arg1 = type; // type
        msg.arg2 = volume; //vol
        mStateMachine.sendMessage(msg);

        return true;
    }

    int[] getDeviceIndicators(BluetoothDevice device){

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getDeviceIndicators(device);
    }
    int[] getCallStateInfo(BluetoothDevice device){

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getCallStateInfo(device);
    }



    boolean dial(String number) {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.DIAL, number);
        return true;
    }


    boolean redial() {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.REDIAL);
        return true;
    }

    boolean answer() {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.ANSWER);
        return true;
    }


    boolean hangup() {

        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.HANGUP);
        return true;
    }

    boolean hold(int holdType) {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.HOLD, new Integer(holdType));
        return true;
    }

    boolean sendDTMFcode(char dtmfcode) {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.SEND_AT_DTMF, new Character(dtmfcode));
        return true;
    }

    boolean sendVendorCmd(String atCmd) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        if (mStateMachine.isHspConnection())
            return false;

        // TBD do not allow other vendor commands when phonebook download is in progress
        if (mStateMachine.isPhoneBookAtHandlerBusy()) {
            Log.e(TAG, "Vendor command not handled as phonebook download is in progress");
            return false;
        }

        mStateMachine.sendMessage(HfDeviceStateMachine.SEND_VND_CMD, atCmd);
        return true;
    }

    boolean queryOperatorSelectionInfo() {
        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.QUERY_OPERATOR_SELECTION_INFO);
        return true;
    }

     boolean querySubscriberInfo() {
         if (mStateMachine.isHspConnection())
             return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.QUERY_SUBSCRIBER_INFO);
        return true;
     }

     boolean getCLCC() {
         if (mStateMachine.isHspConnection())
             return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.AT_CLCC);
        return true;
     }

     boolean sendKeyPressEvent() {
         if (!mStateMachine.isHspConnection())
             return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.SEND_KEY_PRESS_EVENT);
        return true;
     }

     public boolean sendBIA(boolean bEnableRoam, boolean bEnableService,
                 boolean bEnableSignal, boolean bEnableBattery) {

         if (mStateMachine.isHspConnection())
             return false;

        int indicators = 0;
        if (bEnableRoam)
            indicators = indicators | HfDeviceStateMachine.ROAM;
        if (bEnableService)
            indicators = indicators | HfDeviceStateMachine.SERVICE;
        if (bEnableSignal)
            indicators = indicators | HfDeviceStateMachine.SIGNAL;
        if (bEnableBattery)
            indicators = indicators | HfDeviceStateMachine.BATTERY;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mStateMachine.sendMessage(HfDeviceStateMachine.SEND_BIA_EVENT,
                            new Integer(indicators));
        return true;
     }

     boolean readPhoneBookList(String phoneMemType, int maxReadLimit){

        if (mStateMachine.isHspConnection())
            return false;

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

    // TBD do not allow multiple phonebook download at a time
    if (mStateMachine.isPhoneBookAtHandlerBusy()) {
        Log.e(TAG, "readPhoneBookList() not handled as another phonebook download is in progress");
        return false;
    }

        Message msg = mStateMachine.obtainMessage(HfDeviceStateMachine.READ_PHONE_BOOK);
        msg.arg1 = maxReadLimit;
        msg.obj = phoneMemType;

        mStateMachine.sendMessage(msg);
        return true;
     }

    void registerEventHandler(IBluetoothHfDeviceCallback cb) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mCallbacks.register(cb);
    }
    void unRegisterEventHandler(IBluetoothHfDeviceCallback cb) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mCallbacks.unregister(cb);
    }

    int getLocalFeatures(){
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getLocalFeatures();
    }

    int getPeerFeatures(){
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getPeerFeatures();
    }

    void broadcastConnectionState(int errCode,
            BluetoothDevice remoteDevice, int newState, int prevState,
            int peerFeatures, int localFeatures) {

        if (newState == BluetoothProfile.STATE_DISCONNECTED ||
            newState == BluetoothProfile.STATE_CONNECTED) {
            mStateMachine.setHfFeatures(peerFeatures, localFeatures);
        }
        // Broadcast to all clients
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onConnectionStateChange(errCode,
                            remoteDevice, newState, prevState,
                            peerFeatures, localFeatures);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastConnectionState()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }

        log("Connection state " + remoteDevice + ": " + prevState + "->" + newState);


        notifyProfileConnectionStateChanged(remoteDevice, BluetoothProfile.HF_DEVICE,
                                                     newState, prevState);
        Intent intent = new Intent(BluetoothHfDevice.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice);
        intent.putExtra(BluetoothHfDevice.EXTRA_LOCAL_FEATURES, localFeatures);
        intent.putExtra(BluetoothHfDevice.EXTRA_PEER_FEATURES, peerFeatures);
        sendBroadcast(intent, HfDeviceService.BLUETOOTH_PERM);


    }

    void broadcastAudioState(BluetoothDevice device, int newState, int prevState){
        // Broadcast to all clients
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onAudioStateChange(newState, prevState);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastAudioState()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }

        Intent intent = new Intent(BluetoothHfDevice.ACTION_AUDIO_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        sendBroadcast(intent, HfDeviceService.BLUETOOTH_PERM);
        log("HF device Audio state " + device + ": " + prevState + "->" + newState);

    }

    void broadcastIndicatorsUpdate(int[] indValue){
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onIndicatorsUpdate(indValue);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastIndicatorsUpdate()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastCallStateChange(int status, int callSetupState, int numActive, int numHeld,
                    String number ,int addrType){
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onCallStateChange(status, callSetupState,
                             numActive, numHeld, number , addrType);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onCallStateChange()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }

        // Broadcast incoming call state change, so that
        // app can launch itself if required
        List<BluetoothDevice> conDevices = getConnectedDevices();
        BluetoothDevice device = null;
        if (conDevices.size() != 0)
            device = getConnectedDevices().get(0);

        Log.d(TAG,"Broadcast call state changes to app");

        Intent intent = new Intent(BluetoothHfDevice.ACTION_CALL_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, callSetupState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        sendBroadcast(intent, HfDeviceService.BLUETOOTH_PERM);

    }

    void broadcastClccEvent(int status, List<BluetoothClccInfo> clcc){
        // Broadcast to all clients
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onCLCCRsp(status, clcc);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastClccEvent()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }


    void broadcastonPhoneBookReadEvent(int status, List<BluetoothPhoneBookInfo> phoneBookInfo){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onPhoneBookReadRsp(status, phoneBookInfo);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastClccEvent()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastSubscriberInfoEvent(int status, String number ,int addrType){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onSubscriberInfoRsp(status, number , addrType);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcastSubscriberInfoEvent()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastOperatorSelectionEvent(int status,int mode, String operatorString){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onOperatorSelectionRsp(status,
                                            mode, operatorString);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast Vnd command response()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastVndCmdEvent(int status, String vndCmdString){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onVendorAtRsp(status, vndCmdString);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast Vnd command response()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastVolumeEvent(int volType, int volume){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onVolumeChange(volType, volume);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast Vnd command response()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastVREvent(int status, int vrState){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onVRStateChange(status, vrState);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast VR status event ()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastWBSEvent(int wbsState){

        Log.d(TAG,"Broadcast wbs state stat event");

        Intent intent = new Intent(BluetoothHfDevice.ACTION_WBS_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, wbsState);
        sendBroadcast(intent, HfDeviceService.BLUETOOTH_PERM);

    }

    void broadcastRingEvent(){
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onRingEvent();
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onRingEvt()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }

        // Broadcast incoming call state change, so that
        // app can launch itself if required
        List<BluetoothDevice> conDevices = getConnectedDevices();
        BluetoothDevice device = null;
        if (conDevices.size() != 0)
            device = getConnectedDevices().get(0);

        Log.d(TAG,"Broadcast ring event to app");

        Intent intent = new Intent(BluetoothHfDevice.ACTION_RING_EVENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        sendBroadcast(intent, HfDeviceService.BLUETOOTH_PERM);

    }

    void broadcastInBandRingStatusEvent(int inBandRingStatus){

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onInBandRingStatusEvent(inBandRingStatus);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast VR status event ()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void broadcastBIAStatus(int status){

        Log.d(TAG,"Broadcast Indicator activation status= "+ status);

        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBIAStatus(status);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: broadcast BIA status ()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

}
