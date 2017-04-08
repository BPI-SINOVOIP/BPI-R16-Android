/* Copyright 2013 Broadcom Corporation
 **
 ** This program is the proprietary software of Broadcom Corporation and/or its
 ** licensors, and may only be used, duplicated, modified or distributed
 ** pursuant to the terms and conditions of a separate, written license
 ** agreement executed between you and Broadcom (an "Authorized License").
 ** Except as set forth in an Authorized License, Broadcom grants no license
 ** (express or implied), right to use, or waiver of any kind with respect to
 ** the Software, and Broadcom expressly reserves all rights in and to the
 ** Software and all intellectual property rights therein.
 ** IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 ** SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 ** ALL USE OF THE SOFTWARE.
 **
 ** Except as expressly set forth in the Authorized License,
 **
 ** 1.     This program, including its structure, sequence and organization,
 **        constitutes the valuable trade secrets of Broadcom, and you shall
 **        use all reasonable efforts to protect the confidentiality thereof,
 **        and to use this information only in connection with your use of
 **        Broadcom integrated circuit products.
 **
 ** 2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 **        "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 **        REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 **        OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 **        DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 **        NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 **        ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 **        CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 **        OF USE OR PERFORMANCE OF THE SOFTWARE.
 **
 ** 3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 **        ITS LICENSORS BE LIABLE FOR
 **        (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 **              DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 **              YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 **              HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 **        (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 **              SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 **              LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 **              ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 */
package com.broadcom.bt.service.hidd;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
// FIXME : To remove this after adding BluetoothHidDevice API class
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides Bluetooth HID Device profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class HidDeviceService extends ProfileService {
    private static final String TAG = "HIDDeviceService";
    private static final boolean V = true;
    private boolean mNativeAvailable;
    private static boolean mHidDeviceDisabled = true, mIsConnectCmdPending = false;
    private static boolean mHidDeviceRestarted = false;
    private IBluetoothHidDeviceCallback mCallback;
    private BluetoothDevice mHidHostDevice;
    private static final int MESSAGE_CONNECT                 = 1;
    private static final int MESSAGE_DISCONNECT              = 2;
    private static final int MESSAGE_VIRTUAL_UNPLUG          = 3;
    private static final int MESSAGE_SEND_REPORT_DATA        = 4;
    private static final int MESSAGE_SET_SDP_RECORD          = 5;
    private static final int MESSAGE_ENABLE_DEVICE           = 6;
    private static final int MESSAGE_DISABLE_DEVICE          = 7;
    private static final int MESSAGE_CLEAR_SDP_RECORD        = 8;

    private static final int MESSAGE_ENABLE_EVENT_TIMEOUT       = 20;
    private static final int MESSAGE_DISABLE_EVENT_TIMEOUT      = 21;
    private static final int MESSAGE_CONNECT_EVENT_TIMEOUT      = 22;
    private static final int MESSAGE_DISCONNECT_EVENT_TIMEOUT   = 23;
    private static final int MESSAGE_REENABLE_EVENT_TIMEOUT     = 24;

    private static final int MESSAGE_ON_CONN_STATE_CHANGED   = 101;
    private static final int MESSAGE_ON_DEV_STATUS_CHANGED   = 102;
    private static final int MESSAGE_ON_HOST_UNPLUG          = 103;
    private static final int MESSAGE_ON_OUT_REPORT_RECEIVED  = 104;

    private Message delayMsg;
    // Constants matching Hal header file bt_hd.h
    // bthd_connection_state_t
    private static final int HIDD_CONN_STATE_CONNECTED      = 0;
    private static final int HIDD_CONN_STATE_CONNECTING     = 1;
    private static final int HIDD_CONN_STATE_DISCONNECTED   = 2;
    private static final int HIDD_CONN_STATE_DISCONNECTING  = 3;
    private static final int HIDD_CONN_STATE_CONNECTABLE    = 4;

    // bthd_device_status_t
    private static final int HIDD_DEV_STATE_DISABLED        = 0;
    private static final int HIDD_DEV_STATE_ENABLED         = 1;
    private static final int HIDD_DEV_STATE_ENABLE_FAIL     = 2;
    private static final int HIDD_DEV_STATE_DISABLE_FAIL    = 3;

    private static final int HIDD_DEFAULT_EVENT_TIMEOUT_MS  = 5000;

    /* Assuming that HID Device connects to only one Host currently. */
    private Map<BluetoothDevice, Integer> mHidHostDevices;
    /* List of recently unplugged HID hosts. Need to maintain inorder to update
    Bond state correctly for incoming Virtual Unplug requests.*/
    private List<BluetoothDevice> mHidUnpluggedHosts;

    protected HidDevice.HID_CONN_STATE mConnState = HidDevice.HID_CONN_STATE.DISCONNECTED;
    protected HidDevice.HID_SVC_STATE mSvcState = HidDevice.HID_SVC_STATE.DISABLED;
    private static HidDeviceService sHidDeviceService;

    static {
        classInitNative();
    }


    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "mHandle message:"+msg.what);
            switch (msg.what) {
                case MESSAGE_CONNECT:
                {
                    int timeoutMS = msg.arg1;
                    mHandler.removeMessages(MESSAGE_CONNECT_EVENT_TIMEOUT);

                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    mIsConnectCmdPending = true;
                    HidDevice.HID_CONN_STATE tmpConnState = mConnState;
                    setConnState(HidDevice.HID_CONN_STATE.CONNECTING);
                    if (!connectNative(Utils.getByteAddress(device)) ) {
                        setConnState(tmpConnState);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);

                        mIsConnectCmdPending = false;
                        Log.d(TAG, "mHandle MESSAGE_CONNECT failed");
                        try {
                            if(mCallback != null) mCallback.onConnectError(device, true, -100);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Exception sending onConnectError()", e);
                        }
                        break;
                    }
                    mHandler.sendEmptyMessageDelayed(MESSAGE_CONNECT_EVENT_TIMEOUT,
                        ((timeoutMS>0)?timeoutMS:HIDD_DEFAULT_EVENT_TIMEOUT_MS));

                    mHidHostDevice = device;
                }
                    break;
                case MESSAGE_DISCONNECT:
                {
                    int timeoutMS = msg.arg1;
                    mHandler.removeMessages(MESSAGE_DISCONNECT_EVENT_TIMEOUT);

                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    HidDevice.HID_CONN_STATE tmpConnState = mConnState;
                    setConnState(HidDevice.HID_CONN_STATE.DISCONNECTING);
                    if (!disconnectNative(Utils.getByteAddress(device)) ) {
                        setConnState(tmpConnState);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                        Log.d(TAG, "mHandle MESSAGE_DISCONNECT failed");
                        try {
                            if(mCallback != null) mCallback.onDisconnectError(device, true, -100);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Exception sending onDisconnectError()", e);
                        }
                        break;
                    }
                    if (-1 != timeoutMS) {
                        mHandler.sendEmptyMessageDelayed(MESSAGE_DISCONNECT_EVENT_TIMEOUT,
                            ((timeoutMS>0)? timeoutMS:HIDD_DEFAULT_EVENT_TIMEOUT_MS));
                    }
                }
                    break;
                case MESSAGE_ENABLE_DEVICE:
                {
                    Log.d(TAG, "MESSAGE_ENABLE_DEVICE");
                    int timeoutMS = msg.arg1;
                    enableHidDeviceNative();
                    mHandler.removeMessages(MESSAGE_ENABLE_EVENT_TIMEOUT);
                    mHandler.sendEmptyMessageDelayed(MESSAGE_ENABLE_EVENT_TIMEOUT,
                        ((timeoutMS>0)?timeoutMS:HIDD_DEFAULT_EVENT_TIMEOUT_MS));

                }
                break;
                case MESSAGE_DISABLE_DEVICE:
                {
                    Log.d(TAG, "MESSAGE_DISABLE_DEVICE");
                    int timeoutMS = msg.arg1;
                    // Reset pending connect cmd flag before disconnecting
                    setSvcState(HidDevice.HID_SVC_STATE.DISABLING);
                    mIsConnectCmdPending = false;
                    disableHidDeviceNative();
                    mHandler.removeMessages(MESSAGE_DISABLE_EVENT_TIMEOUT);
                    mHandler.sendEmptyMessageDelayed(MESSAGE_DISABLE_EVENT_TIMEOUT,
                        ((timeoutMS>0)?timeoutMS:HIDD_DEFAULT_EVENT_TIMEOUT_MS));

                }
                break;

                case MESSAGE_VIRTUAL_UNPLUG:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if(!virtualUnplugNative(Utils.getByteAddress(device))) {
                        Log.e(TAG, "Error: virtual unplug native returns false");
                    }
                }
                break;
                case MESSAGE_SEND_REPORT_DATA:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    Bundle data = msg.getData();
                    //FIXME
                    byte[] report = data.getByteArray("data");
                    int reportType = data.getInt("type");
                    if(!sendDataNative(Utils.getByteAddress(device), reportType, report)) {
                        Log.e(TAG, "Error: send data native returns false");
                    }
                }
                break;
                case MESSAGE_SET_SDP_RECORD:
                {
                    Bundle data = msg.getData();
                    //FIXME
                    byte[] report = data.getByteArray("sdp");
                    if(!setSdpRecordNative(report) ) {
                        Log.e(TAG, "Error: set SDP Record native returns false");
                    }
                }
                break;

                case MESSAGE_CLEAR_SDP_RECORD:
                {
                    if(!clearSdpRecordNative() ) {
                        Log.e(TAG, "Error: clear SDP Record native returns false");
                    }
                }
                break;

                case MESSAGE_ON_CONN_STATE_CHANGED:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int halState = msg.arg1;
                    Log.d(TAG, "MESSAGE_ON_CONN_STATE_CHANGED :"+halState);
                    // FIXME : Replace the hard-coded numbers with meaningful INT Constants
                    try {
                        if(halState == HIDD_CONN_STATE_CONNECTED) {
                            mHandler.removeMessages(MESSAGE_CONNECT_EVENT_TIMEOUT);
                            if(mCallback != null) mCallback.onConnected(device,
                                (checkConnState(HidDevice.HID_CONN_STATE.CONNECTING)?true:false));
                            setConnState(HidDevice.HID_CONN_STATE.CONNECTED);
                            mHidHostDevice = device;
                            mIsConnectCmdPending = false;
                            broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED);
                        } else if(halState == HIDD_CONN_STATE_DISCONNECTED) {
                            mHandler.removeMessages(MESSAGE_DISCONNECT_EVENT_TIMEOUT);
                            if(mCallback != null) mCallback.onDisconnected(device,
                                (checkConnState(HidDevice.HID_CONN_STATE.DISCONNECTING)
                                ?true:false));
                            setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                            broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                            mHidHostDevice = null;
                        } else if(halState == HIDD_CONN_STATE_CONNECTABLE) {
                            setConnState(HidDevice.HID_CONN_STATE.CONNECTABLE);
                        }
                    } catch(Exception e) {
                        Log.e(TAG, "Error in MESSAGE_ON_CONN_STATE_CHANGED", e);
                    }
                }
                break;

                case MESSAGE_ON_HOST_UNPLUG:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int status = msg.arg1;
                    Log.d(TAG, "MESSAGE_ON_HOST_UNPLUG :"+status);
                    try {
                        mHidUnpluggedHosts.add(device);
                        if(mCallback != null) mCallback.onVirtualUnplug(device, true);
                        mHidHostDevice = null;
                        mHidHostDevices.remove(mHidHostDevice);
                    } catch(Exception e) {
                        Log.e(TAG, "Error in MESSAGE_ON_HOST_UNPLUG", e);
                    }
                    // TODO
                    //broadcastVirtualUnplugStatus(device, status);
                }
                break;
                case MESSAGE_ON_DEV_STATUS_CHANGED:
                {
                    int status = msg.arg1;
                    Log.d(TAG, "MESSAGE_ON_DEV_STATUS_CHANGED :"+status+", mIsConnectCmdPending:"+
                        mIsConnectCmdPending);
                    if(mIsConnectCmdPending && (status == HIDD_DEV_STATE_ENABLED ||
                        status == HIDD_DEV_STATE_DISABLED)) {
                        Log.d(TAG, "Skip resetting of state machine if connect() cmd is pending");
                        break;
                    }
                    if(mHidDeviceRestarted && status == HIDD_DEV_STATE_DISABLED) {
                        Log.d(TAG, "Skip sending onDisabled() callback when HID Device "
                            +"is restarting.");
                        break;
                    }
                    try {
                        if(status == HIDD_DEV_STATE_ENABLED) {
                            if(mHidDeviceRestarted) {
                                Log.d(TAG, "HID Device has restarted..");
                                mHandler.removeMessages(MESSAGE_REENABLE_EVENT_TIMEOUT);
                                mHidDeviceRestarted = false;
                            }
                            mHandler.removeMessages(MESSAGE_ENABLE_EVENT_TIMEOUT);
                            mHidDeviceDisabled = false;
                            if(mCallback != null) mCallback.onEnable();
                            setSvcState(HidDevice.HID_SVC_STATE.ENABLED);
                            setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                        } else if(status == HIDD_DEV_STATE_DISABLED) {
                            mHandler.removeMessages(MESSAGE_DISABLE_EVENT_TIMEOUT);
                            mHidDeviceDisabled = true;
                            if(mCallback != null) mCallback.onDisable();
                            setSvcState(HidDevice.HID_SVC_STATE.DISABLED);
                            /* To make sure HIDD state machine does not go to bad state, reset
                            mConnState to DISCONNECTED.*/
                            setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                            if(mHidHostDevice != null) {
                                broadcastConnectionState(mHidHostDevice,
                                    BluetoothProfile.STATE_DISCONNECTED);
                                mHandler.removeMessages(MESSAGE_DISCONNECT_EVENT_TIMEOUT);
                                mHidHostDevice = null;
                            }
                        } else if(status == HIDD_DEV_STATE_ENABLE_FAIL) {
                            mHandler.removeMessages(MESSAGE_ENABLE_EVENT_TIMEOUT);
                            if(mCallback != null) mCallback.onEnableError(0);
                            mIsConnectCmdPending = false;
                            mHidDeviceDisabled = true;
                            mHidDeviceRestarted = false;
                            setSvcState(HidDevice.HID_SVC_STATE.DISABLED);
                        } else if(status == HIDD_DEV_STATE_DISABLE_FAIL) {
                            mHandler.removeMessages(MESSAGE_DISABLE_EVENT_TIMEOUT);
                            if(mCallback != null) mCallback.onDisableError(0);
                            mIsConnectCmdPending = false;
                            setSvcState(HidDevice.HID_SVC_STATE.ENABLED);
                        }

                    } catch(Exception e) {
                        Log.e(TAG, "Error in MESSAGE_ON_DEV_STATUS_CHANGED", e);
                    }
                }
                break;
                case MESSAGE_ON_OUT_REPORT_RECEIVED:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    Bundle data = msg.getData();
                    byte[] report = data.getByteArray("data");
                    // TODO
                    //broadcastReportData(device, report);
                }
                break;
                /* Timeout events*/
                case MESSAGE_ENABLE_EVENT_TIMEOUT:
                {
                    if(V) Log.v(TAG, "MESSAGE_EVENT_TIMEOUT for MESSAGE_ENABLE_DEVICE");
                    try {
                        if(mCallback != null)
                            mCallback.onEnableError(HidDevice.HID_DEVICE_ERR_TIMEOUT);
                    } catch(RemoteException e) {
                        Log.e(TAG, "Error in MESSAGE_ENABLE_EVENT_TIMEOUT", e);
                    }
                    setSvcState(HidDevice.HID_SVC_STATE.DISABLED);
                    mHidDeviceDisabled = true;
                    setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                }
                break;
                case MESSAGE_DISABLE_EVENT_TIMEOUT:
                {
                    if(V) Log.v(TAG, "MESSAGE_EVENT_TIMEOUT for MESSAGE_DISABLE_DEVICE");
                    try {
                        if(mCallback != null)
                            mCallback.onDisableError(HidDevice.HID_DEVICE_ERR_TIMEOUT);
                    } catch(RemoteException e) {
                        Log.e(TAG, "Error in MESSAGE_DISABLE_EVENT_TIMEOUT", e);
                    }
                    setSvcState(HidDevice.HID_SVC_STATE.DISABLED);
                    setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                }
                break;
                case MESSAGE_CONNECT_EVENT_TIMEOUT:
                {
                    if(V) Log.v(TAG, "MESSAGE_EVENT_TIMEOUT for MESSAGE_CONNECT");
                    try {
                        if(mCallback != null) mCallback.onConnectError(mHidHostDevice, true,
                            HidDevice.HID_DEVICE_ERR_TIMEOUT);
                    } catch(RemoteException e) {
                        Log.e(TAG, "Error in MESSAGE_CONNECT_EVENT_TIMEOUT", e);
                    }
                    setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                }
                break;
                case MESSAGE_DISCONNECT_EVENT_TIMEOUT:
                {
                    if(V) Log.v(TAG, "MESSAGE_EVENT_TIMEOUT for MESSAGE_DISCONNECT");
                    try {
                        if(mCallback != null) mCallback.onDisconnectError(mHidHostDevice, true,
                            HidDevice.HID_DEVICE_ERR_TIMEOUT);
                    } catch(RemoteException e) {
                        Log.e(TAG, "Error in MESSAGE_DISCONNECT_EVENT_TIMEOUT", e);
                    }
                    if(checkState_Disabled())
                        setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                    else
                        setConnState(HidDevice.HID_CONN_STATE.CONNECTED);
                }
                break;
                case MESSAGE_REENABLE_EVENT_TIMEOUT:
                {
                    if(V) Log.v(TAG, "MESSAGE_EVENT_TIMEOUT for MESSAGE_RE-ENABLE");
                    try {
                        if(mCallback != null)
                            mCallback.onEnableError(HidDevice.HID_DEVICE_ERR_TIMEOUT);
                    } catch(RemoteException e) {
                        Log.e(TAG, "Error in MESSAGE_REENABLE_EVENT_TIMEOUT", e);
                    }
                    setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
                }
                break;

                default:
                    Log.d(TAG, "Unhandled message "+msg.what+" for mHandler");
                break;
            }
        }
    };

    /**
     * Returns the name of the profile
     */
    public String getName() {
        return "**"+TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothHidDeviceBinder(this);
    }

    protected synchronized boolean start() {
        if (V) Log.v(TAG, "HID-Device Service start");
        mHidHostDevices = Collections.synchronizedMap(new HashMap<BluetoothDevice, Integer>());
        mHidUnpluggedHosts = new ArrayList<BluetoothDevice>();

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        try {
            initHidDeviceNative();
            mNativeAvailable=true;
        } catch (Exception e) {
            Log.e(TAG, "HidDeviceService.start() failed", e);
            mNativeAvailable=false;
            return false;
        }
        setHidDeviceService(this);
        return true;
     }

    /**
     * Called to stop the HID Device
     */
    protected boolean stop() {
        if (V) log("Stopping Bluetooth HID Device Service");
        return true;
    }

    /**
     * Called to cleanup the HID Device
     */
    protected boolean cleanup() {
        Log.d(TAG, "cleanup HID Device Service");
        //Cleanup native
        if(mNativeAvailable) {
            cleanupHidDeviceNative();
            mNativeAvailable=false;
        }
        if(mHidHostDevices != null)
            mHidHostDevices.clear();
        if(mHidUnpluggedHosts != null)
            mHidUnpluggedHosts.clear();

        if(mBinder != null)
            mBinder.cleanup();
        mBinder = null;
        //mHandler.removeCallbacksAndMessages(null);
        clearHidDeviceService();

        Log.d(TAG, "cleanup done");
        return true;
    }

    public boolean isAvailable() {
        return mNativeAvailable && super.isAvailable();
    }

    public static synchronized HidDeviceService getHidDeviceService(){
        if (sHidDeviceService != null && sHidDeviceService.isAvailable()) {
            if (V) Log.d(TAG, "getHidDeviceService(): returning " + sHidDeviceService);
            return sHidDeviceService;
        }
        if (V) {
            if (sHidDeviceService == null) {
                Log.d(TAG, "getHidDeviceService(): service is NULL");
            } else if (!(sHidDeviceService.isAvailable())) {
                Log.d(TAG,"getHidDeviceService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setHidDeviceService(HidDeviceService instance) {
        if (instance != null && instance.isAvailable()) {
            if (V) Log.d(TAG, "setHidDeviceService(): set to: " + sHidDeviceService);
            sHidDeviceService = instance;
        } else {
            if (V) {
                if (sHidDeviceService == null) {
                    Log.d(TAG, "setHidDeviceService(): service not available");
                } else if (!sHidDeviceService.isAvailable()) {
                    Log.d(TAG,"setHidDeviceService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearHidDeviceService() {
        sHidDeviceService = null;
    }

    private class BluetoothHidDeviceBinder extends IBluetoothHidDevice.Stub implements
        IProfileServiceBinder {
        private HidDeviceService mService;

        public BluetoothHidDeviceBinder(HidDeviceService svc) {
            Log.d(TAG, "BluetoothHidDeviceBinder()");
            mService = svc;
        }

        public boolean cleanup()  {
            Log.d(TAG, "BluetoothHidDeviceBinder.cleanup()");
            mService = null;
            return true;
        }

        private HidDeviceService getService() {
            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public int getConnectionState(BluetoothDevice device) {
            HidDeviceService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return getDevicesMatchingConnectionStates(
                    new int[] {BluetoothProfile.STATE_CONNECTED});
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HidDeviceService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getDeviceConnectionState() {
            HidDeviceService service = getService();
            if (service == null) return HidDevice.HID_CONN_STATE.DISCONNECTED.ordinal();
            return service.getDeviceConnectionState();
        }

        public int getDeviceState() {
            HidDeviceService service = getService();
            if (service == null) return HidDevice.HID_SVC_STATE.DISABLED.ordinal();
            return service.getDeviceState();
        }

        public void registerCallback(IBluetoothHidDeviceCallback cb) {
            HidDeviceService service = getService();
            if (service == null) return;
            service.registerCallback(cb);
        }

        public void unregisterCallback(IBluetoothHidDeviceCallback cb) {
            HidDeviceService service = getService();
            if (service == null) return;
            service.unregisterCallback(cb);
        }

        public boolean enable(int timeoutMS) {
            Log.d(TAG, "BluetoothHidDeviceBinder.enable : ");
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.enable(timeoutMS);
        }

        public boolean disable(int timeoutMS) {
            Log.d(TAG, "BluetoothHidDeviceBinder.disable : ");
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.disable(timeoutMS);
        }

        public boolean setDiscoverableMode(boolean discoverableON) {
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.setDiscoverableMode(discoverableON);
        }

        public boolean connect(BluetoothDevice device, int timeoutMS) {
            Log.d(TAG, "BluetoothHidDeviceBinder.connect : " + device);
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.connect(device, timeoutMS);
        }

        public boolean disconnect(BluetoothDevice device, int timeoutMS) {
            Log.d(TAG, "BluetoothHidDeviceBinder.disconnect : " + device);
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.disconnect(device, timeoutMS);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HidDeviceService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        public boolean virtualUnplug(BluetoothDevice device) {
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.virtualUnplug(device);
        }

        public boolean sendReport(int reportType, byte[] data) {
            Log.d(TAG, "BluetoothHidDeviceBinder.sendReport : type:"+reportType);
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.sendReport(reportType, data);
        }

        public boolean setSDPRecord(int deviceClass, byte[] sdpData) {
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.setSDPRecord(deviceClass, sdpData);
        }

        public boolean clearSDPRecord() {
            HidDeviceService service = getService();
            if (service == null) return false;
            return service.clearSDPRecord();
        }
    }

    //APIs

    public void registerCallback(IBluetoothHidDeviceCallback cb) {
        Log.d(TAG, "HidDeviceService.registerCallback : "+cb);
        mCallback = cb;
    }

    public void unregisterCallback(IBluetoothHidDeviceCallback cb) {
        Log.d(TAG, "HidDeviceService.unregisterCallback : "+mCallback);
        mCallback = null;
    }

    boolean enable(int timeoutMS) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "HidDeviceService.enable : "+" , timeout:"+timeoutMS);

        if (!checkState_Disabled()) {
            Log.w(TAG, "HID device not disabled...Cannot enable...");
            return false;
        }

        Message msg =  new Message();
        msg.what = MESSAGE_ENABLE_DEVICE;
        msg.arg1 = timeoutMS;
        // Set state
        setSvcState(HidDevice.HID_SVC_STATE.ENABLING);
        mHandler.sendMessage(msg);

        return true;
    }

    boolean disable(int timeoutMS) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "HidDeviceService.disable : "+" , timeout:"+timeoutMS);

        synchronized (mSvcState) {
            if (mSvcState == HidDevice.HID_SVC_STATE.DISABLED) {
                Log.w(TAG, "HID device already disabled");
                return false;
            }
            if(checkConnState(HidDevice.HID_CONN_STATE.CONNECTED) && (mHidHostDevice != null)) {
                Log.d(TAG, "Disconnecting from connected HID Host");
                Message msg =  new Message();
                msg.what = MESSAGE_DISCONNECT;
                msg.obj = mHidHostDevice;
                //For disconnect do not set timeout as timeout will be set for disable
                msg.arg1 = -1;
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(MESSAGE_DISABLE_DEVICE);

        }
        return true;
    }

    boolean connect(BluetoothDevice device, int timeoutMS) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "HidDeviceService.connect : "+device+" , timeout:"+timeoutMS);
        if (getConnectionState(device) != BluetoothInputDevice.STATE_DISCONNECTED) {
            Log.e(TAG, "Hid Device not disconnected: " + device);
            return false;
        }
        Message msg =  new Message();
        msg.what = MESSAGE_CONNECT;
        msg.obj = device;
        msg.arg1 = timeoutMS;
        mHandler.sendMessage(msg);
        return true;
    }

    boolean disconnect(BluetoothDevice device, int timeoutMS) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "HidDeviceService.disconnect : "+device+" , timeout:"+timeoutMS);
        if(device == null && mHidHostDevice == null) {
            Log.w(TAG, "HidHost not found...Earlier state : "+mConnState+
                ". Setting state to disconnected");
            setConnState(HidDevice.HID_CONN_STATE.DISCONNECTED);
            try {
                if(mCallback != null) mCallback.onDisconnectError((BluetoothDevice)null, true, 0);
            } catch(RemoteException ee) {
                Log.e(TAG, "Failure in disconnect", ee);
            }
            return false;
        } else {
            Message msg =  new Message();
            msg.what = MESSAGE_DISCONNECT;
            msg.obj = (device == null)? mHidHostDevice:device;
            msg.arg1 = timeoutMS;
            mHandler.sendMessage(msg);

        }
        return true;
    }

    public boolean setDiscoverableMode(boolean discoverableON) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        if(discoverableON) {
            // Start discoverability
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(discoverableIntent);
            return true;
        } else {
            // TODO
            return true;
        }
    }

    int getConnectionState(BluetoothDevice device) {
        if (mHidHostDevices.get(device) == null) {
            return BluetoothInputDevice.STATE_DISCONNECTED;
        }
        return mHidHostDevices.get(device);
    }

    int getDeviceConnectionState() {
        synchronized (mConnState) {
            return mConnState.ordinal();
        }
    }

    public int getDeviceState() {
        synchronized (mSvcState) {
            return mSvcState.ordinal();
        }
    }
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> inputDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mHidHostDevices.keySet()) {
            int inputDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == inputDeviceState) {
                    inputDevices.add(device);
                    break;
                }
            }
        }
        return inputDevices;
    }

    /* Function to check if the BluetoothDevice is an already un-plugged host */
    public boolean isDeviceUnplugged(BluetoothDevice device) {
        if(mHidUnpluggedHosts.size() == 0) {
            Log.w(TAG, "No unplugged devices");
            return false;
        }
        if(!mHidUnpluggedHosts.contains(device)) {
            Log.w(TAG, "Device ("+device+") not present in unplugged list");
            return false;
        }
        if (V) Log.d(TAG, "Device ("+device+") found in unplugged list");
        return true;
    }

    /* Function to remove the BluetoothDevice from the Unplugged device list */
    public void removeDeviceFromUnpluggedList(BluetoothDevice device) {
        if(mHidUnpluggedHosts.size() == 0) {
            Log.w(TAG, "No unplugged devices");
            return;
        }
        if(mHidUnpluggedHosts.contains(device)) {
            if (V) Log.d(TAG, "Removing device ("+device+") from Virtual Unplugged list");
            mHidUnpluggedHosts.remove(device);
            return;
        }
        if (V) Log.d(TAG, "Failed to remove device ("+device+") from Virtual Unplugged list");
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothInputDevicePriorityKey(device.getAddress()),
            priority);
        if (V) Log.d(TAG,"Saved priority " + device + " = " + priority);
        return true;
    }

    public  int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothInputDevicePriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    public boolean virtualUnplug(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_VIRTUAL_UNPLUG,device);
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean sendReport(int reportType, byte[] reportData) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        if(!checkConnState(HidDevice.HID_CONN_STATE.CONNECTED)) {
            Log.e(TAG, "sendReport called in state "+getDeviceConnectionState());
            return false;
        }
        if(mHidHostDevice == null) {
            Log.e(TAG, "sendReport() Hid Host device is null");
            return false;
        }
        Log.d(TAG, "HidDeviceService.sendReport : type:"+reportType+", device :"+mHidHostDevice);
        Message msg = mHandler.obtainMessage(MESSAGE_SEND_REPORT_DATA);
        msg.obj = mHidHostDevice;
        Bundle data = new Bundle();
        // TODO : Replace hard-quoted strings with static-defined String constants
        data.putByteArray("data", reportData);
        data.putInt("type", reportType);
        msg.setData(data);
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean setSDPRecord(int deviceClass, byte[] sdpData) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        Message msg = mHandler.obtainMessage(MESSAGE_SET_SDP_RECORD);
        Bundle data = new Bundle();
        // TODO : Replace hard-quoted strings with static-defined String constants
        msg.arg1 = deviceClass;
        data.putByteArray("sdp", sdpData);
        msg.setData(data);
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean clearSDPRecord() {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        // TODO : Implement Clear SDP Record
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_CLEAR_SDP_RECORD));
        return true;
    }

    private void onVirtualUnplug(byte[] address, int status) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_HOST_UNPLUG);
        msg.obj = address;
        msg.arg1 = status;
        mHandler.sendMessage(msg);
    }

    private void onConnectStateChanged(byte[] address, int state) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_CONN_STATE_CHANGED);
        msg.obj = address;
        msg.arg1 = state;
        mHandler.sendMessage(msg);
    }

    private void onDeviceStatusChanged(int status) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_DEV_STATUS_CHANGED);
        msg.arg1 = status;
        mHandler.sendMessage(msg);
    }

    private void onReportDataReceived(byte[] address, byte[] reportData) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_OUT_REPORT_RECEIVED);
        msg.obj = address;
        Bundle data = new Bundle();
        data.putByteArray("data", reportData);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState) {
        Integer prevStateInteger = mHidHostDevices.get(device);
        int prevState = (prevStateInteger == null) ? BluetoothProfile.STATE_DISCONNECTED :
                                                     prevStateInteger;
        if (prevState == newState) {
            Log.w(TAG, "no state change: " + newState);
            return;
        }
        mHidHostDevices.put(device, newState);
        notifyProfileConnectionStateChanged(device, BluetoothProfile.HID_DEVICE,
                                            newState, prevState);

    }

    private static int convertHalState(int halState) {
        switch (halState) {
            case HIDD_CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case HIDD_CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case HIDD_CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case HIDD_CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "bad hid-device connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    @Override
    protected void finalize() {
        if(V) {
            synchronized (HidDeviceService.class) {
                Log.d(TAG, "FINALIZED. Class= " + this);
            }
        }
        super.finalize();
    }

    private void setSvcState(HidDevice.HID_SVC_STATE nextState) {
        synchronized (mSvcState) {
            if(V) Log.v(TAG, "CONN STATE "+mSvcState+" --> "+nextState);
            mSvcState = nextState;
        }
    }

    private boolean checkState_Disabled() {
        synchronized (mSvcState) {
            if (mSvcState == HidDevice.HID_SVC_STATE.DISABLED) {
                return true;
            } else {
                Log.w(TAG, "Checked if state is disabled...But it is currently ("
                                + mSvcState.name() + ")");
                return false;
            }
        }
    }

    /*
     * Checks if the current state is as specified. Returns false if not and
     * logs warning message
     */
    private boolean checkSvcState(HidDevice.HID_SVC_STATE expectedState) {
        synchronized (mSvcState) {
            if (mSvcState != expectedState) {
                Log.w(TAG, "Checked if state is ("+(expectedState == null?"UNKNOWN":
                        expectedState.name() + ")")+", but is actually ("+mSvcState.name()+")");
                return false;
            }
            return true;
        }
    }

    private void setConnState(HidDevice.HID_CONN_STATE nextState) {
        synchronized (mConnState) {
            if(V) Log.v(TAG, "CONN STATE "+mConnState+" --> "+nextState);
            mConnState = nextState;
        }
    }

    /*
     * Checks if the current state is as specified. Returns false if not and
     * logs warning message
     */
    private boolean checkConnState(HidDevice.HID_CONN_STATE expectedState) {
        synchronized (mConnState) {
            if (mConnState != expectedState) {
                Log.w(TAG, "Checked conn state for ("+(expectedState == null ? "UNKNOWN"
                        :expectedState.name()+")")+", but actual = ("+mConnState.name()+")");
                return false;
            }
            return true;
        }
    }

    // Native HIDD methods
    private native static void classInitNative();
    private native void initHidDeviceNative();
    private native void cleanupHidDeviceNative();
    private native boolean enableHidDeviceNative();
    private native boolean disableHidDeviceNative();
    private native boolean connectNative(byte[] btAddress);
    private native boolean disconnectNative(byte[] btAddress);
    private native boolean virtualUnplugNative(byte[] btAddress);
    private native boolean sendDataNative(byte[] btAddress, int reporType, byte[] reportData);
    private native boolean setSdpRecordNative(byte[] sdpRecord);
    private native boolean clearSdpRecordNative();

}

