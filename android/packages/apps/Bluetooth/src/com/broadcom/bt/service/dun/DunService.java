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

package com.broadcom.bt.service.dun;


import android.app.Service;
import android.bluetooth.BluetoothDevice;
import com.broadcom.bt.service.dun.IBluetoothDun;
import com.broadcom.bt.service.dun.BluetoothDun;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothTetheringDataTracker;
import android.bluetooth.IBluetooth;
import com.broadcom.bt.service.dun.IBluetoothDun;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Provides Bluetooth Dun profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class DunService extends ProfileService {
    private static final String TAG = "DunService";
    private static final boolean DBG = true;

 
    private boolean mNativeAvailable;

    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 11;

    private BluetoothDevice mDevice;
    private int mState;

    static {
        classInitNative();
    }

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothDunBinder(this);
    }

    protected boolean start() {
        initializeNative();
        mNativeAvailable=true;
        return true;
    }

    protected boolean stop() {
        mHandler.removeCallbacksAndMessages(null);
        return true;
    }

    protected boolean cleanup() {
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }
        if(mDevice != null) {
            mDevice = null;
        }
        return true;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECT:
                {
                    if (DBG) log("dun client modem is not supported");

                }
                break;
                case MESSAGE_DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!disconnectDunNative(Utils.getByteAddress(device)) ) {
                        handleDunDeviceStateChange(device, BluetoothProfile.STATE_DISCONNECTING);
                        handleDunDeviceStateChange(device, BluetoothProfile.STATE_DISCONNECTED);
                    }
                }
                break;
                case MESSAGE_CONNECT_STATE_CHANGED:
                {
                    BluetoothDevice device = getDevice((byte [])msg.obj);
                    int state = msg.arg1;
                    if (DBG) log("MESSAGE_CONNECT_STATE_CHANGED: " + device + " state: " + state);
                    switch (state)
                    {
                        case CONN_STATE_MODEM_CHANGED:
                            if(DBG) log("state CONN_STATE_MODEM_CHANGED");
                            break;
                        case CONN_STATE_DATA_AVAILABLE:
                            if(DBG) log("state CONN_STATE_DATA_AVAILABLE");
                            break;
                        default:
                            handleDunDeviceStateChange(device, convertHalState(state));
                    }
                }
                break;
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothDunBinder extends IBluetoothDun.Stub implements IProfileServiceBinder {
        private DunService mService;
        public BluetoothDunBinder(DunService svc) {
            mService = svc;
        }
        public boolean cleanup() {
            mService = null;
            return true;
        }
        private DunService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"Dun call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public boolean connect(BluetoothDevice device) {
            DunService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }
        public boolean disconnect(BluetoothDevice device) {
            DunService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }
        public int getConnectionState(BluetoothDevice device) {
            DunService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            DunService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            DunService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }
    };

    boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) log("dun client modem is not supported");
        return false;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT, device);
        mHandler.sendMessage(msg);
        return true;
    }

    int getConnectionState(BluetoothDevice device) {
        if (mDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mState;
    }

    List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_CONNECTED});
        return devices;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        if(mDevice != null)
        {
            for (int state : states) {
                if (state == mState) {
                    devices.add(mDevice);
                    break;
                }
            }
        }
        return devices;
    }

    private void onConnectStateChanged(byte[] address, int state, int error) {
        if (DBG) log("onConnectStateChanged: " + state);
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED, state, error, (Object)address);
        mHandler.sendMessage(msg);
    }
    private void onControlStateChanged(int state, int error) {
        if (DBG)
            log("onControlStateChanged: " + state + ", error: " + error);
    }

    private static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "unknown dun connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    void handleDunDeviceStateChange(BluetoothDevice device,
                                    int state) {
        if(DBG) Log.d(TAG, "handleDunDeviceStateChange: device: " + device +  ", state: " + state);
        int prevState;
        String ifaceAddr = null;
        if (device == null) {
            prevState = BluetoothProfile.STATE_DISCONNECTED;
        } else {
            prevState = mState;
        }
        Log.d(TAG, "handleDunDeviceStateChange preState: " + prevState + " state: " + state);
        if (prevState == state) return;
        mDevice = device;
        mState = state;
        notifyProfileConnectionStateChanged(device, BluetoothProfile.DUN, state, prevState);
        Intent intent = new Intent(BluetoothDun.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDun.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothDun.EXTRA_STATE, state);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private int getDunDeviceConnectionState(BluetoothDevice device) {
        if (mDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mState;
    }


    // Constants matching Hal header file bt_hh.h
    // bthh_connection_state_t
    private final static int CONN_STATE_CONNECTED = 0;
    private final static int CONN_STATE_CONNECTING = 1;
    private final static int CONN_STATE_DISCONNECTING = 2;
    private final static int CONN_STATE_DISCONNECTED = 3;
    private final static int CONN_STATE_MODEM_CHANGED = 4;
    private final static int CONN_STATE_DATA_AVAILABLE = 5;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean connectDunNative(byte[] btAddress);
    private native boolean disconnectDunNative(byte[] btAddress);
    private native boolean enableDunNative(int enable, int server);

}
