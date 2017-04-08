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
package com.broadcom.bt.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.IBluetoothGattServerCallback;
import android.bluetooth.IBluetoothGattExtrasCallback;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Public API for the Bluetooth Gatt Profile.
 *
 * <p>This class provides Bluetooth Gatt functionality to enable communication
 * with Bluetooth Smart or Smart Ready devices.
 *
 * <p>BluetoothGatt is a proxy object for controlling the Bluetooth Service
 * via IPC.  Use {@link BluetoothAdapter#getProfileProxy} to get the
 * BluetoothGatt proxy object.
 *
 * <p>To connect to a remote peripheral device, create a {@link BluetoothGattCallback}
 * and call {@link #registerApp} to register your application. Gatt capable
 * devices can be discovered using the {@link #startScan} function or the
 * regular Bluetooth device discovery process.
 */
public final class BluetoothGattExtras {
    private static final String TAG = "BtGatt.BluetoothGattExtras";
    private static final boolean DBG = true;

    private BluetoothAdapter mAdapter;
    private IBluetoothManager mManagerService;
    private IBluetoothGatt mService;
    private BluetoothGattExtrasCallback mCallback;
    private int mClientIf = 0;
    private int mServerIf = 0;

    /**
     * Bluetooth GATT interface callbacks
     */
    private final IBluetoothGattCallback mBluetoothGattCallback =
        new IBluetoothGattCallback.Stub() {
            /**
             * Application interface registered - app is ready to go
             * @hide
             */
            public void onClientRegistered(int status, int clientIf) {
                if (DBG) Log.d(TAG, "onClientRegistered() - status=" + status
                    + " clientIf=" + clientIf);
                mClientIf = clientIf;

                try {
                    mService.registerExtras(clientIf, mBluetoothGattExtrasCallback);
                } catch (RemoteException e) {
                    Log.e(TAG,"",e);
                }

                registerServer();
            }

            public void onClientConnectionState(int status, int clientIf, boolean connected,
                             String address) {}

            public void onScanResult(String address, int rssi, byte[] advData) {}

            public void onGetService(String address, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid) {}

            public void onGetIncludedService(String address, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int inclSrvcType, int inclSrvcInstId,
                             ParcelUuid inclSrvcUuid) {}

            public void onGetCharacteristic(String address, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid,
                             int charProps) {}

            public void onGetDescriptor(String address, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid,
                             int descrInstId, ParcelUuid descUuid) {}

            public void onSearchComplete(String address, int status) {}

            public void onCharacteristicRead(String address, int status, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid, byte[] value) {}

            public void onCharacteristicWrite(String address, int status, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid) {}

            public void onNotify(String address, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid,
                             byte[] value) {}

            public void onDescriptorRead(String address, int status, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid,
                             int descrInstId, ParcelUuid descrUuid,
                             byte[] value) {}

            public void onDescriptorWrite(String address, int status, int srvcType,
                             int srvcInstId, ParcelUuid srvcUuid,
                             int charInstId, ParcelUuid charUuid,
                             int descrInstId, ParcelUuid descrUuid) {}

            public void onExecuteWrite(String address, int status) {}

            public void onReadRemoteRssi(String address, int rssi, int status) {}

            public void onMonitorRssi(String address, int rssi) {}

            public void onListen(int status) {
                if (DBG) Log.d(TAG, "onListen() - status=" + status);
            }

        };

    /**
     * Bluetooth GATT SERVER interface callbacks
     */
    private final IBluetoothGattServerCallback mBluetoothGattServerCallback =
        new IBluetoothGattServerCallback.Stub() {
            /**
             * Application interface registered - app is ready to go
             * @hide
             */
            public void onServerRegistered(int status, int serverIf) {
                if (DBG) Log.d(TAG, "onServerRegistered() - status=" + status
                    + " serverIf=" + serverIf);
                mServerIf = serverIf;

                try {
                    mService.registerExtras(serverIf, mBluetoothGattExtrasCallback);
                } catch (RemoteException e) {
                    Log.e(TAG,"",e);
                }

                if (mCallback != null) mCallback.onExtrasReady();
            }

            public void onScanResult(String address, int rssi, byte[] advData) {}

            public void onServerConnectionState(int status, int serverIf, boolean connected,
                             String address) {}

            public void onServiceAdded(int status, int srvcType, int srvcInstId,
                             ParcelUuid srvcId) {}

            public void onCharacteristicReadRequest(String address, int transId,
                             int offset, boolean isLong, int srvcType, int srvcInstId,
                             ParcelUuid srvcId, int charInstId, ParcelUuid charId) {}

            public void onCharacteristicWriteRequest(String address, int transId,
                             int offset, int length, boolean isPrep, boolean needRsp,
                             int srvcType, int srvcInstId, ParcelUuid srvcId,
                             int charInstId, ParcelUuid charId, byte[] value) {}

            public void onDescriptorReadRequest(String address, int transId,
                             int offset, boolean isLong, int srvcType, int srvcInstId,
                             ParcelUuid srvcId, int charInstId, ParcelUuid charId,
                             ParcelUuid descrId) {}

            public void onDescriptorWriteRequest(String address, int transId,
                             int offset, int length, boolean isPrep, boolean needRsp,
                             int srvcType, int srvcInstId, ParcelUuid srvcId,
                             int charInstId, ParcelUuid charId, ParcelUuid descrId,
                             byte[] value) {}

            public void onExecuteWrite(String address, int transId, boolean execWrite) {}
        };

    /**
     * Bluetooth GATT EXTRAS interface callbacks
     */
    private final IBluetoothGattExtrasCallback mBluetoothGattExtrasCallback =
        new IBluetoothGattExtrasCallback.Stub() {
            public void onMonitorRssi(String address, int rssi) {
                if (DBG) Log.d(TAG, "onMonitorRssi() - address=" + address + " rssi=" + rssi);
                try {
                    mCallback.onMonitorRssi(mAdapter.getRemoteDevice(address), rssi);
                } catch (Exception ex) {
                    Log.w(TAG, "Unhandled exception: " + ex);
                }
            }
        };

    /**
     * Create a BluetoothGatt proxy object.
     */
    public BluetoothGattExtras(BluetoothGattExtrasCallback callback, boolean enableGatt) {
        mCallback = callback;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE);
        if (b != null) {
            try {
                mManagerService = IBluetoothManager.Stub.asInterface(b);
                mService = mManagerService.getBluetoothGatt();
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }

        registerClient();
    }

    /**
     * Close the connection to the gatt service.
     */
    public void close() {
        if (DBG) Log.d(TAG, "close()");

        try {
            if (mService != null) {
                if (mClientIf != 0) {
                    mService.unregisterClient(mClientIf);
                    mService.unregisterExtras(mClientIf);
                }

                if (mServerIf != 0) {
                    mService.unregisterServer(mServerIf);
                    mService.unregisterExtras(mServerIf);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
        }
    }

    /**
     * Returns whether the BluetoothGattExtras API has been initialized.
     */
    public boolean isReady() {
        return (mService != null && mClientIf != 0 && mServerIf != 0);
    }

    /**
     * Monitor the RSSI for a connected device.
     *
     * <p>An application can set a low, middle and high RSSI threshold for a
     * given connected device. If any of the RSSI trigger values are crossed,
     * the @{link BluetoothGattCallback#onMonitorRssi} callback is invoked.
     *
     * <p>RSSI values of -127 to 127 may be passed in. If present, the low
     * value has to be smaller than the mid/high values etc.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Connected device
     * @param lowRssi Low RSSI threshold, or null to disable
     * @param midRssi Medium RSSI threshold, or null to disable
     * @param highRssi High RSSI threshold, or null to disable
     * @return true, if the RSSI monitoring has been requested
     */
    public boolean monitorRssi(BluetoothDevice device, Integer lowRssi,
                               Integer midRssi, Integer highRssi) {
        if (DBG) Log.d(TAG, "monitorRssi() - device: " + device.getAddress());
        if (mService == null || mClientIf == 0) return false;


        if (lowRssi != null && (lowRssi < -127 || lowRssi > 127)) return false;
        if (midRssi != null && (midRssi < -127 || midRssi > 127)) return false;
        if (highRssi != null && (highRssi < -127 || highRssi > 127)) return false;

        if (lowRssi != null && midRssi != null && lowRssi >= midRssi) return false;
        if (lowRssi != null && highRssi != null && lowRssi >= highRssi) return false;
        if (midRssi != null && highRssi != null && midRssi >= highRssi) return false;

        // Use out-of range values to signal unused values
        if (lowRssi == null) lowRssi = 255;
        if (midRssi == null) midRssi = 255;
        if (highRssi == null) highRssi = 255;

        try {
            mService.monitorRssi(mClientIf, device.getAddress(), lowRssi,
                                 midRssi, highRssi);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Add an advertising filter to filter by Bluetooth device address.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param Bluetooth device address to filter for
     * @return true, if the filter condition has been added
     */
    public boolean addFilter(String addr, byte type) {
        if (DBG) Log.d(TAG, "addFilter() - device: " + addr);
        if (mService == null) return false;

        try {
            mService.scanFilterAddDeviceAddress(addr, type);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Add an advertising filter to filter by solicited UUID.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param solicitUuid UUID to filter for
     * @return true, if the filter condition has been added
     */
    public boolean addFilter(UUID solicitUuid) {
        if (DBG) Log.d(TAG, "addFilter() - uuid: " + solicitUuid);
        if (mService == null) return false;

        try {
            mService.scanFilterAddSolicitUuid(new ParcelUuid(solicitUuid));
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Add an advertising filter to filter by local device name.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param name Local device name to filter for
     * @return true, if the filter condition has been added
     */
    public boolean addFilter(String name) {
        if (DBG) Log.d(TAG, "addFilter() - name: " + name);
        if (mService == null) return false;

        try {
            mService.scanFilterAddLocalName(name);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Add an advertising filter to filter by manufacturer data.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param company Company id
     * @param data Manufacturer data (20 bytes maximum)
     * @return true, if the filter condition has been added
     */
    public boolean addFilter(int company, byte[] data) {
        if (DBG) Log.d(TAG, "addFilter() - company: " + company);
        if (mService == null) return false;

        try {
            mService.scanFilterAddManufacturerData(company, data);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Add an advertising filter to filter by service changed indication.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param service_changed Set to true to filter for service changed.
     * @return true, if the filter condition has been added
     */
    public boolean addFilter(boolean service_changed) {
        if (DBG) Log.d(TAG, "addFilter() - service_changed: " + service_changed);
        if (mService == null) return false;
        if (!service_changed) return false;

        try {
            mService.scanFilterAddServiceChanged();
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Clear advertising filter conditions.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @return true, if the filter conditions have been cleared
     */
    public boolean clearFilter() {
        if (DBG) Log.d(TAG, "clearFilter()");
        if (mService == null) return false;

        try {
            mService.scanFilterClear();
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    /**
     * Starts or stops sending of advertisement packages to listen for connection
     * requests from a central devices.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Remote device
     */
    public void listen(boolean start) {
        if (DBG) Log.d(TAG, "listen() - start: " + start);
        /** Listen mode should be supported only when Bluetooth is turned on to ensure that BR/EDR
           Page scan can be enabled to accept GATT connections from Dual mode Central devices.
           Enabling Listen Mode when BT is Off (in Quiet mode) will work only with remote LE only
           devices as BR/EDR scans are turned off in Quiet mode.
        */
        if (mService == null || mServerIf == 0 || mAdapter.getState()!= BluetoothAdapter.STATE_ON)
            return;

        try {
            mService.serverListen(mServerIf, start);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
        }
    }

    /**
     * Sets the advertising data contained in the adv. response packet.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param advData true to set adv. data, false to set scan response
     * @param includeName Inlucde the name in the adv. response
     * @param includeTxPower Include TX power value
     * @param minInterval Minimum desired scan interval (optional)
     * @param maxInterval Maximum desired scan interval (optional)
     * @param appearance The appearance flags for the device (optional)
     * @param manufacturerData Manufacturer specific data including company ID (optional)
     */
    public void setAdvData(boolean advData, boolean includeName, boolean includeTxPower,
                           Integer minInterval, Integer maxInterval,
                           Integer appearance, Byte[] manufacturerData) {
        if (DBG) Log.d(TAG, "setAdvData()");
        if (mService == null) return;

        byte[] data = new byte[0];
        if (manufacturerData != null) {
            data = new byte[manufacturerData.length];
            for(int i = 0; i != manufacturerData.length; ++i) {
                data[i] = manufacturerData[i];
            }
        }

        try {
            mService.setAdvData(mClientIf, !advData,
                includeName, includeTxPower,
                minInterval != null ? minInterval : 0,
                maxInterval != null ? maxInterval : 0,
                appearance != null ? appearance : 0, data);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
        }
    }

    private boolean registerClient() {
        if (mService == null) return false;
        if (mClientIf != 0) return true;

        UUID uuid = UUID.randomUUID();
        if (DBG) Log.d(TAG, "registerClient() - UUID=" + uuid);

        try {
            mService.registerClient(new ParcelUuid(uuid), mBluetoothGattCallback);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }

    private boolean registerServer() {
        if (mService == null) return false;
        if (mServerIf != 0) return true;

        UUID uuid = UUID.randomUUID();
        if (DBG) Log.d(TAG, "registerServer() - UUID=" + uuid);

        try {
            mService.registerServer(new ParcelUuid(uuid), mBluetoothGattServerCallback);
        } catch (RemoteException e) {
            Log.e(TAG,"",e);
            return false;
        }

        return true;
    }
}
