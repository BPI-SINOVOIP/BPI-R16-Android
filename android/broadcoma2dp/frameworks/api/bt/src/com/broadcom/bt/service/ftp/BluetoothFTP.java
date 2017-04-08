/* Copyright 2009-2012 Broadcom Corporation
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
package com.broadcom.bt.service.ftp;

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

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the APIs to control the Bluetooth FTP Server
 * Profile.
 *
 * An BluetoothFTP object acts as a proxy to the BluetoothFTP server service and
 * provide helper methods to receive asynchronous events from the service.
 *
 *<p>BluetoothFTP is a proxy object for controlling the Bluetooth
 * Service via IPC. Use {@link BluetoothFTP#getProfileProxy} to get
 * the BluetoothFTP proxy object.
 *
 *<p>Each method is protected with its appropriate permission.
 *
 */

public final class BluetoothFTP implements BluetoothProfile {
    private static final String TAG = "BluetoothFTP";
    private static final boolean DBG = true;

    /**
     * Name of this service.
     */
    public static final String SERVICE_NAME = "bluetooth_ftp";

    /**
     * The prefix of all Bluetooth FTP action.
     *
     *
     */
    static final String ACTION_PREFIX = "android.broadcom.ftpserver.";

    /**
     * The prefix of all Bluetooth FTP extra.
     *
     *
     */
    public static final String FTP_EXTRA_PREFIX =
            "android.broadcom.ftpserver.extra.";

    /**
     * The length of the action prefix.
     *
     *
     */
    static final int ACTION_PREFIX_LENGTH = ACTION_PREFIX.length();

    /**
     *
     * Broadcast Intent action representing the FTP server access request event.
     *
     * <p/>
     * Note: this action just reuses
     * BluetoothIntent.BT_SERVICE_ACCESS_ACTION.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>EXTRA_SVC_NAME</th>
     * <td>String</td>
     * <td>Name of service. This should be set the value of
     * {@link #SERVICE_NAME}.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_RMT_DEV_NAME}</th>
     * <td>String</td>
     * <td>Name of peer Bluetooth device.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_RMT_DEV_ADDR}</th>
     * <td>String</td>
     * <td>Address of peer Bluetooth device.</td>
     * </tr>
     *
     * <tr>
     * <th>{@link #EXTRA_OPERATION}</th>
     * <td>byte</td>
     * <td>Identifier for the operation. Possible values are
     * {@link #FTPS_OPER_CHG_DIR}, {@link #FTPS_OPER_DEL_DIR},
     * {@link #FTPS_OPER_DEL_FILE}, {@link #FTPS_OPER_GET},
     * {@link #FTPS_OPER_MK_DIR}, {@link #FTPS_OPER_PUT}.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_FILEPATH}</th>
     * <td>String</td>
     * <td>The full path of the file resource.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_TOTAL_BYTES}</th>
     * <td>int</td>
     * <td>Total number of bytes of the resource.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_ACCESS_REQUEST = ACTION_PREFIX
            + "ON_FTS_REQUEST_REQUEST";

    /**
     * Broadcast Intent action representing a FTP server connection open event.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_RMT_DEV_ADDR}</th>
     * <td>String</td>
     * <td>Address of peer Bluetooth device.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_OPENED = ACTION_PREFIX
            + "ON_FTS_OPENED";

    /**
     * Broadcast Intent representing the FTP server connection close event.
     *
     * This broadcast intent does not contain any Intent Extra parameters.
     */
    public static final String ACTION_ON_FTS_CLOSED = ACTION_PREFIX
            + "ON_FTPS_CLOSED";

    /**
     * Broadcast Intent action representing a FTP server transfer progress
     * event.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_TOTAL_BYTES}</th>
     * <td>int</td>
     * <td>Total bytes to transfer.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_BYTES_TRANSFERRED}</th>
     * <td>int</td>
     * <td>Current bytes transferred.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_XFR_PROGRESS = ACTION_PREFIX
            + "ON_FTS_XFR_PROGRESS";

    /**
     * Broadcast Intent representing a FTP server GET operation completed event.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_FILEPATH}</th>
     * <td>String</td>
     * <td>The filepath of the resource.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_STATUS}</th>
     * <td>int</td>
     * <td>The operation status. Possible values are {@link #FTPS_STATUS_OK},
     * {@link #FTPS_STATUS_FAIL}.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_GET_COMPLETE = ACTION_PREFIX
            + "ON_FTS_GET_COMPLETE";

    /**
     * Broadcast Intent for FTP server PUT operation completed event.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_FILEPATH}</th>
     * <td>String</td>
     * <td>The filepath of the resource.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_STATUS}</th>
     * <td>int</td>
     * <td>The operation status. Possible values are {@link #FTPS_STATUS_OK},
     * {@link #FTPS_STATUS_FAIL}.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_PUT_COMPLETE = ACTION_PREFIX
            + "ON_FTPS_PUT_COMPLETE";

    /**
     * Broadcast Intent represent a FTP server DELETE operation completed event.
     *
     * <p/>
     * This broadcast intent contains the following Intent Extra parameters:
     * <table>
     * <tr>
     * <th>Extra Param Name</th>
     * <th>Data Type</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_FILEPATH}</th>
     * <td>String</td>
     * <td>The filepath of the resource.</td>
     * </tr>
     * <tr>
     * <th>{@link #EXTRA_STATUS}</th>
     * <td>int</td>
     * <td>The operation status. Possible values are {@link #FTPS_STATUS_OK},
     * {@link #FTPS_STATUS_FAIL}.</td>
     * </tr>
     * </table>
     */
    public static final String ACTION_ON_FTS_DEL_COMPLETE = ACTION_PREFIX
            + "ON_FTS_DEL_COMPLETE";

    /**
     * Intent used to broadcast the change in connection state of the A2DP
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile.</li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    //@SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_STATE_CHANGED =
            ACTION_PREFIX+"CONNECTION_STATE_CHANGED";

    /**
     * Intent Extra parameter: bytes transferred field.
     */
    public static final String EXTRA_BYTES_TRANSFERRED = "BYTES_TRANSFERRED";

    /**
     * Intent Extra parameter: total bytes field.
     *
     */
    public static final String EXTRA_TOTAL_BYTES = "TOTAL_BYTES";

    /**
     * Intent Extra parameter: file path field.
     *
     */
    public static final String EXTRA_FILEPATH = "FILEPATH";

    /**
     * Intent Extra parameter: status field.
     *
     */
    public static final String EXTRA_STATUS = "STATUS";

    /**
     * Intent Extra parameter: remote device name field.
     *
     */
    public static final String EXTRA_RMT_DEV_NAME = FTP_EXTRA_PREFIX
            + "RMT_DEV_NAME";

    /**
     * Intent Extra parameter: remote device address field.
     *
     */
    public static final String EXTRA_RMT_DEV_ADDR = FTP_EXTRA_PREFIX
            + "RMT_DEV_ADDR";

    /**
     * Intent Extra parameter: operation field.
     *
     */
    public static final String EXTRA_OPERATION = FTP_EXTRA_PREFIX
            + "OPERATION";

    /**
     * Bluetooth FTP server transfer status: OK.
     *
     */
    public static final int FTPS_STATUS_OK = 0;

    /**
     * Bluetooth FTP server transfer status: FAIL.
     *
     */
    public static final int FTPS_STATUS_FAIL = 1;

    /**
     * Operation Code for Bluetooth FTP put operation.
     *
     */
    public static final byte FTPS_OPER_PUT = 1;

    /**
     * Operation Code for Bluetooth FTP get operation.
     *
     */
    public static final byte FTPS_OPER_GET = 2;

    /**
     * Operation Code for Bluetooth FTP delete operation.
     *
     */
    public static final byte FTPS_OPER_DEL_FILE = 3;

    /**
     * Operation Code for Bluetooth FTP delete directory operation.
     *
     */
    public static final byte FTPS_OPER_DEL_DIR = 4;

    /**
     * Operation Code for Bluetooth FTP change directory operation
     *
     */
    public static final byte FTPS_OPER_CHG_DIR = 5;

    /**
     * Operation Code for Bluetooth FTP mkdir operation
     *
     */
    public static final byte FTPS_OPER_MK_DIR = 6;

    /**
     * Operation Code for Bluetooth FTP copy action command
     *
     */
    public static final byte FTPS_OPER_COPY = 7;

    /**
     * Operation Code for Bluetooth FTP move action command
     *
     */
    public static final byte FTPS_OPER_MOVE = 8;

    /**
     * Operation Code for Bluetooth FTP set permission action command
     * NOTE: this operation is not supported if the FTP server root
     * directory is the removable storage (/sdcard or /mnt/sdcard)
     */
    public static final byte FTPS_OPER_SET_PERM = 9;

    /* FTP Profile identifier ID. This identifier will be returned to
    the ServiceListener which is registered during
    {@link BluetoothProfile.ServiceListener} */
    public static final int FTP_SERVER = 7;

    public static final String BLUETOOTH_ADMIN_PERM =
            android.Manifest.permission.BLUETOOTH_ADMIN;
    public static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;


    private Context mContext;
    private ServiceListener mServiceListener;
    private IBluetoothFTP mService;
    private BluetoothAdapter mAdapter;
    private IBluetoothManager mgr;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "Proxy object connected");
            mService = IBluetoothFTP.Stub.asInterface(service);

            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(FTP_SERVER, BluetoothFTP.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(FTP_SERVER);
            }
            if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
                if (DBG) Log.d(TAG, "BluetoothFTP Try Rebinding back to FTP service");
                if (!mContext.bindService(new Intent(IBluetoothFTP.class.getName()),
                    mConnection, 0)) {
                    Log.e(TAG, "Could not bind to Bluetooth FTP Service");
               }
           }
        }
    };

    final private IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
        new IBluetoothStateChangeCallback.Stub() {
            public void onBluetoothStateChange(boolean up) {
                if (DBG) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                if (!up) {
                    if (DBG) Log.d(TAG,"Unbinding service...");
                    synchronized (mConnection) {
                        try {
                            mService = null;
                            mContext.unbindService(mConnection);
                        } catch (Exception re) {
                            Log.e(TAG,"",re);
                        }
                    }
                } else {
                    synchronized (mConnection) {
                        try {
                            if (mService == null) {
                                if (DBG) Log.d(TAG,"Binding service...");
                                if (!mContext.bindService(new Intent(IBluetoothFTP.class.getName()), mConnection, 0)) {
                                    Log.e(TAG, "Could not bind to Bluetooth FTP Server Service");
                                }
                            }
                        } catch (Exception re) {
                            Log.e(TAG,"",re);
                        }
                    }
                }
            }
        };

    /**
     * IPC callback implementation to receive events, if callbacks are used
     *
     */

    private final IBluetoothFTPCallback mCallback = new IBluetoothFTPCallback.Stub() {

        @Override
        public void onFtpServerAccessRequested(String fileName, int fileSize,
                String remoteDeviceName, byte opCode, String remoteAddress)
                throws RemoteException {
            /*IFTPEventHandler handler = mEventHandler;
            if (handler != null) {
                handler.onFtpServerAccessRequested(fileName, fileSize,
                    remoteDeviceName, opCode, remoteAddress);
            }*/

        }

        @Override
        public void onFtpServerAuthen(String userId, byte useridLength,
                boolean useridRequired) throws RemoteException {
            /*
             * IFTPEventHandler handler = mEventHandler; if (handler != null) {
             * handler.onFtpServerAuthen(userId, useridLength, useridRequired);
             * }
             */
        }

        @Override
        public void onFtpServerClosed() throws RemoteException {
            mContext.sendOrderedBroadcast(
                new Intent(BluetoothFTP.ACTION_ON_FTS_CLOSED), BLUETOOTH_PERM);
        }

        @Override
        public void onFtpServerDelCompleted(String fileName, byte status)
                throws RemoteException {
            Intent i = new Intent(BluetoothFTP.ACTION_ON_FTS_DEL_COMPLETE);
            i.putExtra(BluetoothFTP.EXTRA_FILEPATH, fileName);
            i.putExtra(BluetoothFTP.EXTRA_STATUS, status);
            mContext.sendOrderedBroadcast(i, BLUETOOTH_PERM);
        }

        @Override
        public void onFtpServerEnabled() throws RemoteException {
        }

        @Override
        public void onFtpServerFileTransferInProgress(int fileSize,
                int numOfByteSinceLastReported) throws RemoteException {
            Intent i = new Intent(BluetoothFTP.ACTION_ON_FTS_XFR_PROGRESS);
            i.putExtra(BluetoothFTP.EXTRA_TOTAL_BYTES, fileSize);
            i.putExtra(BluetoothFTP.EXTRA_BYTES_TRANSFERRED,
                numOfByteSinceLastReported);
            mContext.sendOrderedBroadcast(i, BLUETOOTH_PERM);
        }

        @Override
        public void onFtpServerGetCompleted(String fileName, byte status)
                throws RemoteException {
            Intent i = new Intent(BluetoothFTP.ACTION_ON_FTS_PUT_COMPLETE);
            i.putExtra(BluetoothFTP.EXTRA_FILEPATH, fileName);
            i.putExtra(BluetoothFTP.EXTRA_STATUS, status);
            mContext.sendOrderedBroadcast(i, BLUETOOTH_PERM);
        }

        @Override
        public void onFtpServerOpened(String remoteAddress)
            throws RemoteException {
            Intent i = new Intent(BluetoothFTP.ACTION_ON_FTS_OPENED);
            i.putExtra(BluetoothFTP.EXTRA_RMT_DEV_ADDR, remoteAddress);
            mContext.sendOrderedBroadcast(i, BLUETOOTH_PERM);
        }

        @Override
        public void onFtpServerPutCompleted(String fileName, byte status)
                throws RemoteException {
            Intent i = new Intent(BluetoothFTP.ACTION_ON_FTS_PUT_COMPLETE);
            i.putExtra(BluetoothFTP.EXTRA_FILEPATH, fileName);
            i.putExtra(BluetoothFTP.EXTRA_STATUS, status);
            mContext.sendOrderedBroadcast(i, BLUETOOTH_PERM);
        }

    };

    /**
     * Get the profile proxy object associated with the FTP Server profile.
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
        BluetoothFTP ftpserver = new BluetoothFTP(context, listener);
        return true;
    }

    /**
     * Create a BluetoothFTP proxy object for providing helper methods
     * to receive asynchronous events from the service.
     *
     */
    public BluetoothFTP(Context context, ServiceListener l) {
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        mgr = IBluetoothManager.Stub.asInterface(ServiceManager.checkService(
                    BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE));
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }

        if (!context.bindService(new Intent(IBluetoothFTP.class.getName()), mConnection, 0)) {
            Log.e(TAG, "Could not bind to Bluetooth Headset Service");
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<BluetoothDevice> getConnectedDevices() {
        if (DBG) log("getConnectedDevices()");
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
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (DBG) log("getDevicesMatchingStates()");
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
     */
    public int getConnectionState(BluetoothDevice device) {
        if (DBG) log("getState(" + device + ")");
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

    /**
     * Internal method to initialize the proxy with the profile service.
     *
     * @hide
     */
    protected boolean init(IBinder service) {
        try {
            mService = (IBluetoothFTP) IBluetoothFTP.Stub.asInterface(service);
            if (mCallback != null) {
                mService.registerCallback(mCallback);
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Unable to initialize proxy with service", t);
            return false;
        }
    }

    /**
     * Disconnects from the profile service and releases all internal resources
     * used by the proxy.
     *
     */
    public synchronized void close() {

        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }
        synchronized (mConnection) {
            if (mService != null && mCallback != null) {
                try {
                    mService.unregisterCallback(mCallback);
                    mService = null;
                    mContext.unbindService(mConnection);
                } catch (Exception re) {
                    Log.e(TAG,"",re);
                }
           }
        }
        mServiceListener = null;
    }

    /**
     * This function is not implemented in current release.
     *
     * @hide
     */
    public void ftpServerAuthenRsp(String password, String userId) {
        try {
            mService.ftpServerAuthenRsp(password, userId);
        } catch (Throwable t) {
            Log.e(TAG, "Error calling ftpServerAuthenRsp", t);
        }
    }

    /**
     * API to accept or deny an FTP server access request.
     *
     * @param opcode
     *            The identifier for the FTP server operation. Possible values
     *            are: {@link #FTPS_OPER_CHG_DIR},{@link #FTPS_OPER_DEL_DIR},
     *            {@link #FTPS_OPER_DEL_FILE}, {@link #FTPS_OPER_GET},
     *            {@link #FTPS_OPER_MK_DIR}, {@link #FTPS_OPER_PUT}
     * @param access
     *            Whether to grant access or deny. True to grant and False to
     *            deny.
     * @param filepath
     *            The filepath of the resource to grant or deny access to.
     */
    public void ftpServerAccessRsp(byte opcode, boolean access, String filepath) {
        try {
            if (DBG) {
                Log.d("TAG", "ftpServerAccessRsp(" + opcode + ", " + access
                        + "," + filepath + ")");
            }
            mService.ftpServerAccessRsp(opcode, access, filepath);
        } catch (Throwable t) {
            Log.e(TAG, "Error calling ftpServerAccessRsp", t);
        }
    }

    /**
     * Internal method used to determine if access request check is needed
     *
     * @hide
     */
    public boolean requiresAccessProcessing() {
        return true;
    }

    /**
     * Internal method used set access request by a common settings app
     *
     * @hide
     */
    public void setAccess(int opCode, boolean allow, Object name) {
        ftpServerAccessRsp((byte) opCode, allow, (String) name);
    }

    private boolean isEnabled() {
       if (mAdapter.getState() == BluetoothAdapter.STATE_ON) return true;
       return false;
    }

    private boolean isDisabled() {
       if (mAdapter.getState() == BluetoothAdapter.STATE_OFF) return true;
       return false;
    }

    private boolean isValidDevice(BluetoothDevice device) {
       if (device == null) return false;

       if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) return true;
       return false;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    /**
     * Method used by Java GC to cleanup resources
     */
    /*protected void finalize() {
        super.finalize();
    }*/

}
