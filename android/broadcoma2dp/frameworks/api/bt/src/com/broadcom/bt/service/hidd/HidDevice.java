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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.broadcom.bt.common.BluetoothConstants;

/**
 * This class provides the APIs to control the Bluetooth HID Device
 * Profile.
 *
 * An HidDevice object acts as a proxy to the Bluetooth HID-Device server service and
 * provide helper methods to receive asynchronous events from the service.
 *
 *<p>HidDevice is a proxy object for controlling the Bluetooth
 * Service via IPC. Use {@link HidDevice#getProfileProxy} to get
 * the HidDevice proxy object.
 *
 *<p>Each method is protected with its appropriate permission.
 *
 */

public final class HidDevice implements BluetoothProfile {

    private static final boolean D = true;
    private static final boolean V = true;
    private static final String TAG = "HIDDevice";

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;

    /**
     * Name of this service.
     * @hide
     */
    public static final String SERVICE_NAME = "bluetooth_hidd";

    /**
     * The prefix of all Bluetooth FTP action.
     *
     * @hide
     */
    static final String ACTION_PREFIX = "android.broadcom.hiddevice.";

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private IBluetoothHidDeviceCallback mCb;
    private SDPRecord mSDPRecord;
    private IBluetoothHidDevice mService;
    private IBluetoothManager mgr;
    private ServiceListener mServiceListener;
    /**
     * @hide
     */
    protected int mDeviceClass;

    private boolean mIsDeviceInitiated;
    /**
     * @hide
     */
    protected boolean mDiscoverabilityStarted;
    private Object mDiscoverabilityLock = new Object();

    /**
     * Remote HID host
     *
     * @hide
     */
    protected BluetoothDevice mHidHost;

    /**
     * Raw Data from HID Device
     */
    public static final byte HID_DEVICE_TYPE_RAW = 0x0;
    /**
     * Keyboard HID Device
     */
    public static final byte HID_DEVICE_TYPE_KEYBOARD = 0x1;
    /**
     * Mouse HID Device
     */
    public static final byte HID_DEVICE_TYPE_MOUSE = 0x2;
    /**
     * Combo HID Device
     */
    public static final byte HID_DEVICE_TYPE_COMBO = 0x3;

    /**
     * Indicates HID Device error code for timeout
     */
    public static final byte HID_DEVICE_ERR_TIMEOUT = 100;
    /**
     * Indicates HID Device generic error code
     */
    public static final byte HID_DEVICE_ERR_GENERIC = 0;

    // Management Handler msgs
    private static final int MSG_ENABLE_HIDDEV              = 1;
    private static final int MSG_FINISHED                   = 2;
    private static final int MSG_ERR_CLT_TIMEOUT            = 30; // Client connect timeout
    private static final int MSG_ERR_SVR_TIMEOUT            = 31; // Server connect timeout
    private static final int MSG_SENDEVT_ONENABLED          = 50; // Send onEnabled to app
    private static final int MSG_SENDEVT_ONENABLE_ERR       = 51;// send onEnableError to app
    private static final int MSG_SENDEVT_ONDISABLED         = 52; // Send onEnabled to app
    private static final int MSG_SENDEVT_ONDISABLE_ERR      = 53;// send onEnableError to  app
    private static final int MSG_SENDEVT_ONCONNECTION_ERR   = 54;
    private static final int MSG_SENDEVT_ONDISCONNECT_ERR   = 55;
    private static final int MSG_SENDEVT_ONCONNECTED        = 56;
    private static final int MSG_SENDEVT_ONCONNECTIONLOST   = 57;
    private static final int MSG_SENDEVT_ONDISCONNECTED     = 58;

    // Event Handler Messages
    private static final int MSG_EVT_GET_RPT                = 100;
    private static final int MSG_EVT_SET_RPT                = 101;
    private static final int MSG_EVT_RVC_RPT                = 102;
    private static final int MSG_EVT_SET_PROT               = 103;
    private static final int MSG_EVT_BYTES_READ             = 104;
    private static final int MSG_EVT_HS                     = 105;
    private static final int MSG_EVT_HID_CTRL               = 106;

    /**
     * @hide
     */
    protected boolean mIsFinishing = false;
    /**
     * @hide
     */
    protected boolean mIsSvcFinished = false;
    /**
     * @hide
     */
    protected boolean mIsConnFinished = false;

    private int mSdpHandle;
    private int mEnableTimeoutMs = -1;

    /**
     * HID Device plugin state values from stack
     */
    public enum HID_SVC_STATE {
        /**
            Device is disabled state */
        DISABLED,
        /**
            Device is enabled state */
        ENABLED,
        /**
            Device is in the state of getting enabled */
        ENABLING,
        /**
            Device is in the state of getting disabled */
        DISABLING
    };

    /**
     * HID Device connection state values from stack
     */
    public enum HID_CONN_STATE {
        /**
            Device is already connected to a HID Host */
        CONNECTED,
        /**
            Device is in the processing of connecting to a HID Host */
        CONNECTING,
        /**
            Device has disconnected from a HID Host */
        DISCONNECTED,
        /**
            Device is in the processing of disconnecting from a HID Host */
        DISCONNECTING,
        /**
            Device is LISTENING/CONNECTING state. A Hid Host can connect back at this state */
        CONNECTABLE
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (D) Log.d(TAG, "Proxy object connected");
            mService = IBluetoothHidDevice.Stub.asInterface(service);

            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothConstants.PROFILE_ID_HID_DEVICE,
                    HidDevice.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (D) Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(BluetoothConstants.PROFILE_ID_HID_DEVICE);
            }
            if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
                if (D) Log.d(TAG, "HidDevice Try Rebinding back to HID Device service");
                if (!mContext.bindService(new Intent(IBluetoothHidDevice.class.getName()),
                    mConnection, 0)) {
                    Log.e(TAG, "Could not bind to Bluetooth HID Device Service");
                }
            }
        }
    };

    final private IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
        new IBluetoothStateChangeCallback.Stub() {
            public void onBluetoothStateChange(boolean up) {
                if (D) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                if (!up) {
                    if (D) Log.d(TAG,"Unbinding service...");
                    synchronized (mConnection) {
                        try {
                            mService = null;
                            if(mContext != null) {
                                mContext.unbindService(mConnection);
                                mContext = null;
                            }
                        } catch (Exception re) {
                            Log.e(TAG,"",re);
                        }
                    }
                } else {
                    synchronized (mConnection) {
                        try {
                            if (mService == null) {
                                if (D) Log.d(TAG,"Binding service...");
                                if (!mContext.bindService(new Intent(
                                    IBluetoothHidDevice.class.getName()), mConnection, 0)) {
                                    Log.e(TAG, "Could not bind to Bluetooth HID Device Service");
                                }
                            }
                        } catch (Exception re) {
                            Log.e(TAG,"",re);
                        }
                    }
                }
            }
        };

    /* PUBLIC APIs */

    /**
     * Get the profile proxy object associated with the HidDevice profile.
     *
     * Clients must implements
     * {@link BluetoothProfile.ServiceListener} to get notified of
     * the connection status and to get the proxy object.
     *
     * @param context Context of the application
     * @param listener The service Listener for connection callbacks.
     * @return true on success, false on error
     */
    public static boolean getProfileProxy(Context context,
        BluetoothProfile.ServiceListener listener) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter == null) {
            Log.w(TAG, "BluetoothAdapter is null.");
            return false;
        }
        HidDevice hidDevice = new HidDevice(context, listener);
        return true;
    }

    /**
     * Close the connection of the profile proxy to the Service.
     *
     * <p> Clients should call this when they are no longer using
     * the proxy obtained from {@link #getProfileProxy}.
     *
     * @param proxy Profile proxy object
     */
    public static void closeProfileProxy(BluetoothProfile proxy) {
        if (proxy == null) return;
        Log.d(TAG, "closeProfileProxy()");

        HidDevice hidDevice = (HidDevice)proxy;
        hidDevice.close();
    }

    /*package*/ HidDevice(Context ctx, ServiceListener l, SDPRecord sdpRecord, int deviceClass)
            throws IllegalArgumentException, IOException {
        this(ctx, l);
        if (ctx == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        setSDPRecord(sdpRecord);
        setDeviceClass(deviceClass);
    }

    /**
     * Default constructor for a HID device. Prior to connecting to a HID host,
     * or listening for connections, an SDPRecord must be registered
     */
    /*package*/ HidDevice(Context ctx, ServiceListener l) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        mContext = ctx;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mServiceListener = l;

        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE);
        if (b != null) {
            IBluetoothManager mgr = IBluetoothManager.Stub.asInterface(b);
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException re) {
                Log.e(TAG, "Unable to register BluetoothStateChangeCallback", re);
            }
        } else {
            Log.e(TAG, "Unable to get BluetoothManager interface.");
        }

        //Bind to the service only if the Bluetooth is ON
        if(mAdapter.isEnabled()){
            if (!mContext.bindService(new Intent(IBluetoothHidDevice.class.getName()), mConnection,
                0)) {
                Log.e(TAG, "Could not bind to Bluetooth HID Device Service");
            }
        }
    }

    /**Cleans up HID Device class
    * @hide
    */
    public synchronized void finish() {
        Log.d(TAG, "finish() entered");
        if (mIsFinishing) {
            return;
        } else {
            mIsFinishing = true;
        }

        stopDiscoverable();

        // Start disable and check finish state
        disable(); // async
    }

    /**
     * Disconnects from the profile service and releases all internal resources
     * used by the proxy. To be invoked internally
     *
     */
    /* package */ synchronized void close() {
        Log.d(TAG, "close() entered");

        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }
        synchronized (mConnection) {
            if (mService != null) {
                try {
                    mService.unregisterCallback(null);
                    mService = null;
                    if(mContext != null) {
                        mContext.unbindService(mConnection);
                        mContext = null;
                    }
                } catch (Exception re) {
                    Log.e(TAG,"",re);
                }
           }
        }
        mServiceListener = null;
    }

    /**
     * {@inheritDoc}
     * @hide
     */
    public List<BluetoothDevice> getConnectedDevices() {
        if (D) Log.d(TAG, "getConnectedDevices()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getConnectedDevices();
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     * @hide
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (D) Log.d(TAG, "getDevicesMatchingStates()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getDevicesMatchingConnectionStates(states);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     * @hide
     */
    public int getConnectionState(BluetoothDevice device) {
        if (D) Log.d(TAG, "getState(" + device + ")");
        if (mService != null && isEnabled() && isValidDevice(device)) {
            try {
                return mService.getConnectionState(device);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    private boolean isValidDevice(BluetoothDevice device) {
       if (device == null) return false;
       if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) return true;
       return false;
    }

    /**
     * Sets the HID device callback object that will be receiving HID device
     * evens
     *
     * @param cb
     *            The callback object that will be receiving HID device events
     */
    public void setCallback(IBluetoothHidDeviceCallback cb) {
        mCb = cb;
        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return;
        }
        try {
            mService.registerCallback(mCb);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return;
        }
    }

    /**
     * Sends virtual unplug to host
     *
     * @param device
     */
    public void virtualUnplug(BluetoothDevice device) {

        if(mService == null) {
            Log.d(TAG, "virtualUnplug() HID Device service not ready");
            return;
        }
        try {
            mService.virtualUnplug(device);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return;
        }
    }

    /**
     * Clears the HID device callback object
     */
    public void clearCallback() {
        mCb = null;
        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return;
        }
        try {
            mService.unregisterCallback(null);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return;
        }
    }

    /**
     * Set the Device Class for this HID device
     * Not supported currently.
     *
     * @param deviceClass Represents the COD to be set
     * @hide
     */
    public void setDeviceClass(int deviceClass) {
        mDeviceClass = deviceClass;
        // TODO : Determine if Device class needs to be sent to stack
    }

    /**
     * Set the SDPRecord for this HID device
     *
     * @param sdpRecord
     *
     * @throws IllegalArgumentException
     */
    public void setSDPRecord(SDPRecord sdpRecord)
            throws IllegalArgumentException {
        Log.d(TAG, "setSDPRecord()");
        try {
            if(mService != null && mService.getDeviceState() == HID_SVC_STATE.DISABLED.ordinal()) {
                throw new IllegalArgumentException(
                        "Cannot update SDPRecord while HID device is disabled");
            }
            if(sdpRecord == null) {
                mService.clearSDPRecord();
                return;
            }

            // Initialize the SDP record
            sdpRecord.init();
            mSDPRecord = sdpRecord;

            // Unregister previous SDP record
            if (mSdpHandle > 0) {
                unregisterSDPRecord();
            }
        } catch (IOException ie) {
            Log.e(TAG, "Error initializing SDP record", ie);
            throw new IllegalArgumentException("Invalid SDP record content");
        } catch (RemoteException re) {
            Log.e(TAG, "RemoteException setting SDP record", re);
        }
    }

    /**
     * Enables the HID device.
     *
     * Once enabled, HID Device will be in listening mode for incoming connections.
     * It is not neccessary to explicitly set the HID Device to {@link HID_CONN_STATE#CONNECTABLE}
     *  mode. This method does not attempt to initiate a connection to the HID host.
     *
     * If the HID device is already enabled, this method returns false and does
     * nothing.
     *
     * This method report the device status by invoking
     * {@link IBluetoothHidDeviceCallback#onEnable()} or
     * {@link IBluetoothHidDeviceCallback#onEnableError(int)} callback methods for the registered
     * {@link IBluetoothHidDeviceCallback} interface.
     *
     * @throws IllegalArgumentException
     *             if the HID device is not properly configured
     * @throws IOException
     *             if an error occurs while registering the SDP record
     * @return true if enable succeeds, false otherwise
     */
    public synchronized boolean enable(int timeoutMs)
            throws IllegalArgumentException, IOException {
        if (D) {
            Log.d(TAG, "enable(): entered");
        }

        if (mIsFinishing) {
            Log.w(TAG, "HID device is finishing.....Cannot enable...");
            return false;
        }

        if (BluetoothAdapter.getDefaultAdapter().getState() != BluetoothAdapter.STATE_ON) {
            Log.w(TAG, "Bluetooth not enabled...");
            return false;
        }

        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }
        try {
            mService.enable(timeoutMs);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        if (D) {
            Log.d(TAG, "enable(): leaving");
        }

        return true;
    }

    /**
     * Disables the HID device. This function unregisters the SDP record for
     * this device.
     *
     * If the HID device is already disabled, this method returns false and does
     * nothing.
     *
     * If the HID device is currently connected, this method will first end the
     * connection to the HID host before unregistering the SDP record.
     *
     * This method report the device status by invoking
     * {@link IBluetoothHidDeviceCallback#onDisable()} or
     * {@link IBluetoothHidDeviceCallback#onDisableError(int)} callback methods for the registered
     * {@link IBluetoothHidDeviceCallback} interface.
     * 
     * @throws IOException
     *             if an error occurs while disconnecting or unregistering the
     *             SDP record
     * @return true if disable succeeds, false otherwise
     */

    public synchronized boolean disable() {
        boolean status;
        if (D) {
            Log.d(TAG, "disable(): entered");
        }

        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }
        try {
            // Disconnect any existing active HID connections
            if(mService != null && mService.getDeviceConnectionState() ==
                HID_CONN_STATE.CONNECTED.ordinal()) {
                status= disconnect(-1);
                if(!status) Log.w(TAG, "Failed to disconnect from HID Host");
            }
            status= mService.disable(-1);
            if(!status) {
                Log.w(TAG, "Failed to disable HID Device");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        } catch(IOException ie) {
            Log.e(TAG, "Error disabling HID Device", ie);
            return false;
        }
        return true;
    }

    /**
     * Starts a connection to a HID host. The HID host must be specified.
     *
     * This method is a non-blocking call: there are no guarantees that the
     * connections to the HID host have been established when this function
     * returns.
     *
     * This method report the connection status to the HID host by invoking the
     * {@link IBluetoothHidDeviceCallback#onConnected(BluetoothDevice, boolean)} or
     * {@link IBluetoothHidDeviceCallback#onConnectError(BluetoothDevice, boolean, int)}
     * callback methods for the registered {@link IBluetoothHidDeviceCallback} interface.
     *
     * If the HID device is already connected to a HID host, this method returns
     * false, otherwise this method returns true to indicate connection
     * establishment has been started.
     *
     * @param host
     *            : The HID host
     * @param timeoutMs
     *            : The number of MS to wait for a connection, or -1 to wait
     *            indefinitely
     * @throws IllegalArgumentException
     *             if the HID host is not specified or if the HID device is not
     *             enabled
     * @throws IOException
     *             if either an L2CAP interrupt or control channel cannot be
     *             created to the HID host
     * @return boolean false if Bluetooth is not turned on (or) HID Device service is
     *              not enabled/started yet (or) the HID device is already connected to
     *               a HID host, true otherwise
     */
    public boolean connect(final BluetoothDevice host, int timeoutMs)
            throws IllegalArgumentException, IOException {
        if (D) {
            Log.d(TAG, "connect(): entered");
        }

        if (host == null) {
            IllegalArgumentException e = new IllegalArgumentException(
                    "HID host not specified");
            Log.e(TAG, "HID host not specified", e);
            throw e;
        }


        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }
        try {
            if (mService.getDeviceConnectionState() != HID_CONN_STATE.DISCONNECTED.ordinal()) {
                IllegalArgumentException e = new IllegalArgumentException(
                        "HID device cannot start connection while in state  "
                                + mService.getDeviceConnectionState());
                Log.e(TAG, "HID host not enabled", e);
                throw e;
            } else {
                mHidHost = host;
                mIsDeviceInitiated = true;
            }
            mService.connect(mHidHost, -1);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }
        if (D) {
            Log.d(TAG, "connect(): leaving");
        }
        return true;
    }

    /**
     * Starts a listener to listen for incoming connections from a HID host.
     *
     * This method is a non-blocking call: there are no guarantees that the
     * listener for theHID host has finished starting when this function
     * returns.
     *
     * This method report the connection status to the HID host by invoking the
     * {@link IBluetoothHidDeviceCallback#onConnected(BluetoothDevice, boolean)} or
     * {@link IBluetoothHidDeviceCallback#onConnectError(BluetoothDevice, boolean, int)}
     * callback methods for the registered {@link IBluetoothHidDeviceCallback} interface.
     *
     * If the HID device is already connected to a HID host, this method returns
     * false, otherwise this method returns true to indicate listener is
     * starting
     *
     * @throws IllegalArgumentException
     *             if the HID device is not enabled
     * @throws IOException
     *             if either an L2CAP interrupt or control channel cannot be
     *             created to the HID host
     * @return boolean false if the HID device is already connected to a HID
     *         host, true otherwise
     * @deprecated No longer needed as Stack takes care of handling LISTEN/CONNECTABLE state.
     * @hide
     */
    @Deprecated
    public boolean listen() throws IOException {
        return true;
    }

    /**
    * Cancels the HID Device to listen for incoming connection requests from HID Host.
    *
    * @deprecated No longer needed as Stack takes care of handling LISTEN/CONNECTABLE state.
    * @hide
    */
    @Deprecated
    public boolean cancelListen() {
        // No longer needed as Stack takes care of handling LISTEN/CONNECTABLE state.
        return true;
    }

    /**
     * Disconnects from the HID host
     *
     * This method is a non-blocking call: there are no guarantees that the
     * disconnect completes before this function returns.
     *
     * This method report the disconnect status to the HID host by invoking the
     * {@link IBluetoothHidDeviceCallback#onDisconnected(BluetoothDevice, boolean)} or
     * {@link IBluetoothHidDeviceCallback#onDisconnectError(BluetoothDevice, boolean, int)}
     * callback methods for the registered {@link IBluetoothHidDeviceCallback} interface.
     *
     * If the HID device is already disconnected from a HID host, this method
     * returns false, otherwise this method returns true to indicate listener is
     * starting
     *
     * @param timeoutMs
     *            : The number of MS to wait for a disconnect, or -1 to wait
     *            indefinitely
     * @return boolean false if the HID device is already disconnected, true if
     *         disconnect started
     */
    public boolean disconnect(int timeoutMs) throws IOException {
        if (D) {
            Log.d(TAG, "disconnect(): entered");
        }
        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }

        try {
            if (mService.getDeviceConnectionState() != HID_CONN_STATE.CONNECTED.ordinal()) {
                Log.e(TAG,
                        "Unable to diconnect: HID device cannot be disconnected while in state "
                                + mService.getDeviceConnectionState());
                return false;
            }
            return mService.disconnect(mHidHost, -1);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to disconnect: ", t);
            return false;
        }

    }

    /**
     * Send a HID report on the HID interrupt channel. For all valid device types,
     * the first byte should be the corresponding report ID in the HID report sent.
     * The rest of the byte stream should be a valid HID report.
     *
     * For HID Keyboard ({@link #HID_DEVICE_TYPE_KEYBOARD}, report ID is 0x01.
     * For HID Mouse ({@link #HID_DEVICE_TYPE_MOUSE}), report ID is 0x02.
     * For HID Raw datal ({@link #HID_DEVICE_TYPE_RAW}), report ID is 0x0a - 0x12.
     *
     * This method is blocking
     *
     * @param deviceType
     *            Type of device : {@link #HID_DEVICE_TYPE_KEYBOARD},
     *              {@link #HID_DEVICE_TYPE_MOUSE},
     *              {@link #HID_DEVICE_TYPE_RAW}
     * @param reportData
     *            the byte contents of the report data
     * @throws IOException
     *             if the send fails
     */
    public void sendReport(int deviceType, byte[] reportData) throws IOException {
        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return;
        }
        try {
            mService.sendReport(deviceType, reportData);
        } catch(RemoteException ee) {
            Log.e(TAG,"",ee);
            return;
        }
    }

    /**
     * Start the remote device discovery process.
     *
     * This function will first send the SDP values to the BTA stack to
     * configure the SDP Record and then start the discovery process.
     *
     * @param timeSec
     *            Timeout in seconds
    *
     * @return Intent The discoverable Intent to be used by client to initiate
     *         discovery process
     */
    public synchronized Intent startDiscoverable(int timeSec) {
        Log.v(TAG, "setDiscoverable() entered");

        synchronized (mDiscoverabilityLock) {

            if (!registerDeviceClass()) {
                Log.e(TAG, "registerDeviceClass failed");
                return null;
            }

            // Register SDPRecord
            if (!registerSDPRecord()) {
                Log.e(TAG, "registerSDPRecord failed");
                unregisterDeviceClass();
                return null;
            }
            mDiscoverabilityStarted = true;
        }
        // Start discoverability
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeSec);
        return discoverableIntent;
    }

    /**
     * Cancel the current device discovery process.
     *
     * This function will first remove the SDP record set by {@link #startDiscoverable(int)}
     * in the BTA stack and then cancel the discovery process.
     *
     * @return boolean true, if discovery process is cancelled successfully;
     *         false otherwise
     */
    public boolean stopDiscoverable() {
        if (V) {
            Log.d(TAG, "stopDiscoverable() entered");
        }
        boolean hasError = false;
        synchronized (mDiscoverabilityLock) {

            if (!mDiscoverabilityStarted) {
                Log.w(TAG, "HID discoverability not started...Skipping stop..");
                return false;
            }
            if (!unregisterSDPRecord()) {
                Log.e(TAG, "unregisterSDPRecord failed");
                hasError = true;
            }

            if (!unregisterDeviceClass()) {
                Log.e(TAG, "unregisterDeviceClass failed");
                hasError = true;
            }

            if (!hasError) {
                mDiscoverabilityStarted = false;
                // Disable changing Scan mode to CONNECTABLE only
                // when stopDiscoverable() to avoid overriding the default
                // Discoverability behaviour in Android.
                /*if(BluetoothAdapter.getDefaultAdapter().getScanMode() ==
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    BluetoothAdapter.getDefaultAdapter()
                        .setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
                }*/
            }
        }
        return !(hasError);
    }

    /**
    * Send a HID report in response to a HID report request from the HID host.
    * The report will be sent on the control channel
    *
    * This method is blocking
    *
    * @param reportData
    *            the byte contents of the report data
    * @throws IOException
    *             if the send fails
    * @deprecated Not supported anymore as Stack handles the same internally
    * @hide
    */
    @Deprecated
    public void sendReportResponse(byte[] reportData) throws IOException {
        // Not supported anymore
        return;
    }

    /**
    * Send a HID Handshake result type on the control channel
    *
    * This method is blocking
    *
    * @param hsResultType
    *            the HID Handshake message type.
    *
    * @deprecated Not supported anymore as Stack handles the same internally
    * @hide
    */
    @Deprecated
    public void sendHandshakeResult(final byte hsResultType) throws IOException {
        // Not supported anymore
        return;
    }

    /**
     * Function to check if HID Device is connected to a HID Host currently.
     *
     * @return boolean true, if device is connected; false otherwise
     */
    public boolean isConnected() {
        try {
            return isEnabled() && (mService != null) && (mService.getDeviceConnectionState()
                == HID_CONN_STATE.CONNECTED.ordinal());
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Function to check if HID Device is in ENABLED state
     *
     * @return boolean true, if device is enabled; false otherwise
     */
    public boolean isEnabled() {
        try {
            if (mAdapter.getState() == BluetoothAdapter.STATE_ON && (mService != null) &&
                (mService.getDeviceState() == HID_SVC_STATE.ENABLED.ordinal()))
                return true;
        } catch (RemoteException e) {
        }
        return false;
    }

    /* Protected methods */

    /* Private methods */

    /**
     * Registers the SDP Records for this HID device. Returns false if register
     * failed Returns true if already registered or register succeeds
     */
    private boolean registerSDPRecord() {
        if (mSDPRecord == null) {
            Log.e(TAG, "Unable to set discover. SDP record not set");
            return false;
        }

        if (mSdpHandle > 0) {
            Log.w(TAG, "SDP record already registered");
            return true;
        }

        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }
        try {
            return mService.setSDPRecord(mDeviceClass, mSDPRecord.toXMLBytes());
        } catch(RemoteException ee) {
            Log.e(TAG,"",ee);
            return false;
        } catch (IOException ie) {
            Log.e(TAG, "Error registering SDP Record", ie);
            return false;
        } catch (IllegalArgumentException ie) {
            Log.e(TAG, "Error registering SDP Record", ie);
            return false;
        }
    }

    /**
     * Unregisters the SDP Records for this HID device returns false if
     * unregister fails returns true if unregister succeeds or no record was
     * registered
     */
    private boolean unregisterSDPRecord() {
        if (V) {
            Log.d(TAG, "unregisterSDPRecord() entered.");
        }

        if(mService == null) {
            Log.d(TAG, "setCallback() HID Device service not ready");
            return false;
        }
        try {
            return mService.clearSDPRecord();
        } catch(RemoteException ee) {
            Log.e(TAG,"",ee);
            return false;
        }
    }

    /**
     * Registers the device class for this HID device. Returns true if device
     * class is not set OR if device class set succeeds
     */
    private boolean registerDeviceClass() {
        if (mDeviceClass == 0) {
            Log.e(TAG, "Device class not set, skipping register device class");
        }
        return true;
    }

    /**
     * Unregisters the Device Class Records for this HID device Returns true if
     * device class is not set OR if device class unset succeeds
     */
    private boolean unregisterDeviceClass() {
        if (mDeviceClass == 0) {
            Log.e(TAG, "Device class not set...Skipping unregisterDeviceClass");
        }
        return true;
    }

}

