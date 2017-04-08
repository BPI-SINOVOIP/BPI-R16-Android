/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2012 Broadcom Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothA2dp;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.os.Message;
import android.content.res.Resources;
import com.android.bluetooth.R;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.broadcom.bt.common.BluetoothConstants;
import com.broadcom.bt.service.ProfileConfig;
import com.broadcom.bt.service.ProfileConfig.ProfileCfg;

/**
 * Provides Bluetooth A2DP profile, as a service in the Bluetooth application.
 * @hide
 */
public class A2dpService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG="A2dpService";

    private A2dpStateMachine mStateMachine;
    private Avrcp mAvrcp;
    private static A2dpService sAd2dpService;

    private static int sA2dpRoleShortUUID =0;
    public static final int A2DP_SOURCE_SERVICE_UUID  = 0x110a;
    public static final int A2DP_SINK_SERVICE_UUID  = 0x110b;
    AdapterService mService;

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothA2dpBinder(this);
    }

    protected synchronized boolean  start() {
        mStateMachine = A2dpStateMachine.make(this, this);
        mAvrcp = Avrcp.make(this,this);
        setA2dpService(this);
        return true;
    }

    protected synchronized boolean stop() {
        mStateMachine.doQuit();
        mAvrcp.doQuit();
        return true;
    }

    protected synchronized boolean cleanup() {
        if (mStateMachine!= null) {
            mStateMachine.cleanup();
            sA2dpRoleShortUUID = 0;
        }
        if (mAvrcp != null) {
            mAvrcp.cleanup();
            mAvrcp = null;
        }
        clearA2dpService();
        return true;
    }

    //API Methods

    public static synchronized A2dpService getA2dpService(){
        if (sAd2dpService != null && sAd2dpService.isAvailable()) {
            if (DBG) Log.d(TAG, "getA2DPService(): returning " + sAd2dpService);
            return sAd2dpService;
        }
        if (DBG)  {
            if (sAd2dpService == null) {
                Log.d(TAG, "getA2dpService(): service is NULL");
            } else if (!(sAd2dpService.isAvailable())) {
                Log.d(TAG,"getA2dpService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setA2dpService(A2dpService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setA2dpService(): set to: " + sAd2dpService);
            sAd2dpService = instance;
        } else {
            if (DBG)  {
                if (sAd2dpService == null) {
                    Log.d(TAG, "setA2dpService(): service not available");
                } else if (!sAd2dpService.isAvailable()) {
                    Log.d(TAG,"setA2dpService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearA2dpService() {
        sAd2dpService = null;
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        if (getPriority(device) == BluetoothProfile.PRIORITY_OFF) {
            return false;
        }

        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState == BluetoothProfile.STATE_CONNECTED ||
            connectionState == BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        mStateMachine.sendMessage(A2dpStateMachine.CONNECT, device);
        return true;
    }

    public boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
            connectionState != BluetoothProfile.STATE_CONNECTING &&
            !mStateMachine.isRcDeviceConnected(device)) {
            return false;
        }

        mStateMachine.sendMessage(A2dpStateMachine.DISCONNECT, device);
        return true;
    }

    boolean getContentProtectionType(BluetoothDevice device)
    {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        if (mStateMachine.isPlaying(device))
        {
            mStateMachine.sendMessage(A2dpStateMachine.FETCH_CP);
        }
        else
        {
            return false;
        }
        return true;
    }
    boolean setContentProtectionType(BluetoothDevice device, int cp_type)
    {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
            connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        Message msg = mStateMachine.obtainMessage(A2dpStateMachine.CONFIGURE_CP);
        msg.arg1 = cp_type;
        mStateMachine.sendMessage(msg);

        return true;
    }

    protected void onRCConnectionStateChanged(int state, byte[] address) {
        mStateMachine.onRCConnectionStateChanged(state, address);
    }

    protected boolean disconnectRcNative(byte[] address){
        return mAvrcp.disconnectRcNative(address);
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if (mStateMachine == null) return new ArrayList<BluetoothDevice>();
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
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

    /*Whenever there is a device mode switch a2dp profile  support in that mode is updated*/
    public static void setA2dpProfileServiceUuid( int serviceuuid){
        sA2dpRoleShortUUID = serviceuuid;
        if (DBG) Log.d(TAG, "setA2dpProfileServiceId(): sA2dpRoleShortUUID " + sA2dpRoleShortUUID);
    }

    /*Current a2dp role is returned.*/
    public int getA2dpProfileServiceUuid(){
        int devicemode = 0;
        int profileDeviceMode = 0;
        Resources resources = getResources();
        if( sA2dpRoleShortUUID == 0 )
        {
            mService = AdapterService.getAdapterService();
            devicemode = mService.getDeviceMode();
            profileDeviceMode = resources
                        .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_a2dp);

            if( (devicemode == mService.DEFAULT_MODE) && // Default = AV source
                (ProfileConfig.CFG_MODE_DEVICE_OR_PHONE == profileDeviceMode))
                sA2dpRoleShortUUID = A2DP_SOURCE_SERVICE_UUID;
            else if( devicemode == mService.HEADSET_MODE && // Headset = AV Sink
                (ProfileConfig.CFG_MODE_DEVICE_OR_PHONE == profileDeviceMode))
                sA2dpRoleShortUUID = A2DP_SINK_SERVICE_UUID;
            else // If not switchable = AV source
                sA2dpRoleShortUUID = A2DP_SOURCE_SERVICE_UUID;
            Log.d(TAG, "getA2dpProfileServiceUuid(): sA2dpRoleShortUUID "
                                 + sA2dpRoleShortUUID+"profileDeviceMode="+profileDeviceMode);

         }
        if (DBG) Log.d(TAG, "getA2dpProfileServiceUuid(): sA2dpRoleShortUUID "
                     + sA2dpRoleShortUUID+"profileDeviceMode="+profileDeviceMode);
        return sA2dpRoleShortUUID;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()),
            priority);
        if (DBG) Log.d(TAG,"Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    /* Absolute volume implementation */
    public boolean isAvrcpAbsoluteVolumeSupported() {
        return mAvrcp.isAbsoluteVolumeSupported();
    }

    public void adjustAvrcpAbsoluteVolume(int direction) {
        mAvrcp.adjustVolume(direction);
    }

    public void setAvrcpAbsoluteVolume(int volume) {
        mAvrcp.setAbsoluteVolume(volume);
    }

    synchronized boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                       "Need BLUETOOTH permission");
        if (DBG) Log.d(TAG, "isA2dpPlaying(" + device + ")");
        return mStateMachine.isPlaying(device);
    }

    //Binder object: Must be static class or memory leak may occur
    private static class BluetoothA2dpBinder extends IBluetoothA2dp.Stub
        implements IProfileServiceBinder {
        private A2dpService mService;

        private A2dpService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"A2dp call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        BluetoothA2dpBinder(A2dpService svc) {
            mService = svc;
        }

        public boolean cleanup()  {
            mService = null;
            return true;
        }

        public boolean connect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public boolean getContentProtectionType(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.getContentProtectionType(device);
        }

        public boolean setContentProtectionType(BluetoothDevice device, int cp_type) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.setContentProtectionType(device, cp_type);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            A2dpService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            A2dpService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        public boolean isAvrcpAbsoluteVolumeSupported() {
            A2dpService service = getService();
            if (service == null) return false;
            return service.isAvrcpAbsoluteVolumeSupported();
        }

        public void adjustAvrcpAbsoluteVolume(int direction) {
            A2dpService service = getService();
            if (service == null) return;
            service.adjustAvrcpAbsoluteVolume(direction);
        }

        public void setAvrcpAbsoluteVolume(int volume) {
            A2dpService service = getService();
            if (service == null) return;
            service.setAvrcpAbsoluteVolume(volume);
        }

        public boolean isA2dpPlaying(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) return false;
            return service.isA2dpPlaying(device);
        }
    };
}
