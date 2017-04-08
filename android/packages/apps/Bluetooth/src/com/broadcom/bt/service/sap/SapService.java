/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/

package com.broadcom.bt.service.sap;

import android.bluetooth.BluetoothDevice;
import com.broadcom.bt.service.sap.BluetoothSap;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetooth;
import com.broadcom.bt.service.sap.IBluetoothSap;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.bluetooth.Utils;
import android.content.pm.PackageManager;
import com.android.bluetooth.btservice.ProfileService;

/**
 * Provides Bluetooth Sim Access Profile (Server role), as a service in
 * the Bluetooth application.
 * @hide
 */
public class SapService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "SapService";

    private Map<BluetoothDevice, Integer> mSapDevices;
    private boolean mNativeAvailable;

    private static final int MESSAGE_DISCONNECT = 1;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 2;

    static {
        classInitNative();
    }

    public String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothSapBinder(this);
    }

    protected boolean start() {
        mSapDevices = Collections.synchronizedMap(new HashMap<BluetoothDevice, Integer>());
        initializeNative();
        mNativeAvailable=true;
        return true;
    }

    protected boolean stop() {
        if (DBG) log("Stopping Bluetooth SapService");
        return true;
    }

    protected boolean cleanup() {
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }

        if(mSapDevices != null) {
            mSapDevices.clear();
            mSapDevices = null;
        }
        return true;
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!disconnectSapNative(DISCONNECT_TYPE_GRACEFUL) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }
                }
                    break;
                case MESSAGE_CONNECT_STATE_CHANGED:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int halState = msg.arg1;
                    Integer prevStateInteger = mSapDevices.get(device);
                    int prevState = (prevStateInteger == null) ?
                        BluetoothSap.STATE_DISCONNECTED :prevStateInteger;
                    if(DBG) Log.d(TAG, "MESSAGE_CONNECT_STATE_CHANGED newState:"+
                        convertHalState(halState)+", prevState:"+prevState);
                    broadcastConnectionState(device, convertHalState(halState));
                }
                break;
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothSapBinder extends IBluetoothSap.Stub implements IProfileServiceBinder{
        private SapService mService;
        public BluetoothSapBinder(SapService svc) {
            mService = svc;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        private SapService getService() {
            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean disconnect(BluetoothDevice device) {
            SapService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public int getConnectionState(BluetoothDevice device) {
            SapService service = getService();
            if (service == null) return BluetoothSap.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return getDevicesMatchingConnectionStates(
                    new int[] {BluetoothProfile.STATE_CONNECTED});
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            SapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    int getConnectionState(BluetoothDevice device) {
        if (mSapDevices.get(device) == null) {
            return BluetoothSap.STATE_DISCONNECTED;
        }
        return mSapDevices.get(device);
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
         List<BluetoothDevice> sapDevices = new ArrayList<BluetoothDevice>();

         for (BluetoothDevice device: mSapDevices.keySet()) {
            int sapDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == sapDeviceState) {
                    sapDevices.add(device);
                    break;
                }
            }
       }
       return sapDevices;
    }

    private void onConnectStateChanged(byte[] address, int state) {
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED);
        msg.obj = address;
        msg.arg1 = state;
        mHandler.sendMessage(msg);
    }

    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device, int newState) {
        Integer prevStateInteger = mSapDevices.get(device);
        int prevState = (prevStateInteger == null) ? BluetoothSap.STATE_DISCONNECTED :
                                                     prevStateInteger;
        if (prevState == newState) {
            Log.w(TAG, "no state change: " + newState);
            return;
        }
        mSapDevices.put(device, newState);

        /* Notifying the connection state change of the profile before sending the intent for
           connection state change, as it was causing a race condition, with the UI not being
           updated with the correct connection state. */
        if (DBG) log("Connection state " + device + ": " + prevState + "->" + newState);
        notifyProfileConnectionStateChanged(device, BluetoothSap.SAP,
                                            newState, prevState);
        Intent intent = new Intent(BluetoothSap.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }


    private static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            default:
                Log.e(TAG, "bad sap connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    // Constants matching Hal header file bt_sap.h
    // btsap_connection_state_t
    private final static int CONN_STATE_CONNECTED = 0;
    private final static int CONN_STATE_DISCONNECTED = 1;
    private final static int CONN_STATE_UNKNOWN = 3;

    private final static int DISCONNECT_TYPE_GRACEFUL = 0;
    private final static int DISCONNECT_TYPE_IMMEDIATE = 1;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean disconnectSapNative(int discType);
}
