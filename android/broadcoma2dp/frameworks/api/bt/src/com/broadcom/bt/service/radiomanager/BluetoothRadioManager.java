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

package com.broadcom.bt.service.radiomanager;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import android.os.ServiceManager;
import android.os.ParcelUuid;
import android.bluetooth.IBluetoothRadioMgrCallback;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetooth;
import android.bluetooth.BluetoothAdapter;
import android.os.ServiceManager;
import android.os.Binder;
import android.os.IBinder;


import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.lang.Integer;


/**
 * Provides interface with Bluetooth Radio functionality for different radio clients
 * devices.
 * @hide
 */

public final class BluetoothRadioManager {
    private static final String TAG = "BluetoothRadioManager";
    private static final boolean DBG = true;

     /**
         * Indicates the Radio type FM
         * @hide
         **/
    public static final int RADIO_TYPE_FM    = 0x00;
     /**
         * Indicates the Radio type LE
         * @hide
         **/
    public static final int RADIO_TYPE_BLE   = 0x01;

      /**
          * Indicates the Radio type LE
          * @hide
          **/
     public static final int RADIO_TYPE_ANT   = 0x02;

     /**
         * Indicates the Radio type OTHER
         * @hide
         **/
    public static final int RADIO_TYPE_OTHER = 0x03;

    /**
        * Indicates the Radio type OTHER
        * @hide
        **/
    public static final int RADIO_TYPE_ALL =  0xFF;

    private int mRadioCount = 0;
    private int mFMCount = 0;
    private int mBleCount = 0;
    private int mAntCount = 0;
    private ParcelUuid mUUID;
    private boolean mAirplaneOn  = false;

    private final BluetoothAdapter mAdapter;

    private ArrayList <BluetoothGattServiceStateChangeCallback> mGattSrvStChangeCallback;
    private final Map <BluetoothRadioStateChangeCallback, Integer> mRadioStChangeCallback;


    private static BluetoothRadioManager sRadioManager = null;
    private  IBluetoothManager mManagerService = null;
    private IBluetooth mService;
    private IBluetoothGatt mBluetoothGatt = null;
    private int mRadioType;

    /**
     * Lazily initialized singleton. Guaranteed final after first object
     * constructed.
     */
    public static synchronized BluetoothRadioManager getRadioManager() {
        if (sRadioManager == null) {
            IBinder b = ServiceManager.getService("bluetooth_manager");
            if (b != null) {
                IBluetoothManager managerService = IBluetoothManager.Stub.asInterface(b);
                sRadioManager = new BluetoothRadioManager(managerService);
            } else {
                Log.e(TAG, "Bluetooth binder is null");
            }
        }
        return sRadioManager;

    }


    /**
     * Use {@link #getRadioManager} to get the BluetoothRadioManager instance
      @hide
     */
    BluetoothRadioManager(IBluetoothManager managerService) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mManagerService = managerService;
        if (mManagerService != null) {
            try {
                mService = mManagerService.registerAdapter(mManagerCallback);
                mManagerService.registerRadioMgrCallback(mRadioMgrCallback);
            }catch (RemoteException e) {Log.e(TAG, "", e);}
        }
        mRadioStChangeCallback = new HashMap <BluetoothRadioStateChangeCallback, Integer>();
        mGattSrvStChangeCallback = new ArrayList<BluetoothGattServiceStateChangeCallback>();
       mUUID = new ParcelUuid(UUID.randomUUID());
    }

       /**
    * Adds Radio  reference.
    * if Radio is not On, This turns on the  combo chip.
    * Can be used as generic API for any other radio turn on also.
    * @hide
    */

        public boolean addRadioReference(int radioType, BluetoothRadioStateChangeCallback cb) {
        boolean enabled = isRadioEnabled();
        boolean ret = false;

        mRadioType = radioType;
        switch(mRadioType)
        {
            case RADIO_TYPE_FM:
                if (mFMCount > 0) {
                   if (DBG) Log.d(TAG, " FM Radio Already enabled ");
                   return false;
                }
                else
                    mFMCount++;
                break;

            case RADIO_TYPE_ANT:
               if (mAntCount > 0) {
                   if (DBG) Log.d(TAG, " ANT Radio Already enabled ");
                   return false;
                }
                else
                    mAntCount++;
                break;

            case RADIO_TYPE_BLE:
                if (mBleCount> 0) {
                   if (DBG) Log.d(TAG, " BLE Radio Already enabled ");
                       return false;
                  }
                  else
                      mBleCount++;
                break;

             default:
                 if (DBG) Log.d(TAG, " Unsupported Radio type. " + mRadioType);
                return false;
        }

        if ( (cb != null) &&(!mRadioStChangeCallback.containsKey(cb))) {
            if (DBG) Log.d(TAG,"addRadioReference: Add CB = " + cb + "Radio Type = " + mRadioType);
            mRadioStChangeCallback.put(cb, mRadioType);
        }
        try {

            if (enabled) {
                     mService.registerRadioClient(mManagerCallback,mUUID, mRadioType);
             }
            /* Call into EnabeRadio API. If Radio is already enabled,
            invoke callback else wait for radio enable */
            ret = mManagerService.enableRadio(mRadioType);
            if ((ret)&&(enabled) &&(cb != null))
                cb.onRadioStateChange(true);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return ret;
    }

        /**
     * Adds LE Radio reference.
     * if Radio is not On, This turns on the  combo chip.
     * Can be used as generic API for any other radio turn on also.
     * @hide
     */
        public boolean addLeRadioReference(BluetoothGattServiceStateChangeCallback cb) {
        if ( (cb != null )&& (!mGattSrvStChangeCallback.contains(cb))) {
            if (DBG) Log.d(TAG, "addLeRadioReference: Add CB  " + cb +
                "mBluetoothGatt = " + mBluetoothGatt);
            mGattSrvStChangeCallback.add(cb);
        }
        addRadioReference(BluetoothRadioManager.RADIO_TYPE_BLE, null);
        if ((isRadioEnabled() == true) && (mBluetoothGatt == null)) {
            // start binding into Gatt Service
            // connect to GattService
            try {
                mBluetoothGatt = mManagerService.getBluetoothGatt();
                if (mBluetoothGatt == null) {
                    mManagerService.enableGatt();
                }else if (cb != null) {
                    cb.onGattServiceStateChange(true, mBluetoothGatt);
                }
            }catch (RemoteException e) {
                Log.e(TAG, "", e);
                return false;
            }
        }else if ((isRadioEnabled() == true) && (mBluetoothGatt != null) && (cb != null)) {
            cb.onGattServiceStateChange(true, mBluetoothGatt);
        }
        return true;
    }

    /**
     * Return true if Radio is currently enabled and ready
     *
     * @hide
     */
    public boolean isRadioEnabled() {
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.isRadioEnabled();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Return true if Radio is currently enabled and Gatt service is available
     *
     * @hide
     */
    public boolean isGattServiceReady() {
        boolean bRadioEnabled = false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null)
                    bRadioEnabled = mService.isRadioEnabled();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}

        if ((bRadioEnabled == true) && (mBluetoothGatt != null))
            return true;
        else
            return false;
    }

/**
 * Releases Radio reference
 * Can be used as generic API for any other radio turn Off also.
 * @hide
 */
    public boolean releaseRadioReference(int radioType, BluetoothRadioStateChangeCallback cb) {

        if (DBG) Log.d(TAG, "releaseRadioReference Radio Type = " + radioType);

        switch(radioType)
        {
            case RADIO_TYPE_FM:
                if (mFMCount > 0)
                    mFMCount--;
                else
                    return false;
                break;

            case RADIO_TYPE_ANT:
                if (mAntCount> 0)
                    mAntCount--;
                else
                    return false;
                break;

            case RADIO_TYPE_BLE:
                if (mBleCount > 0)
                    mBleCount--;
                else
                    return false;
                break;

             default:
                 if (DBG) Log.d(TAG, " Unsupported Radio type. " + radioType);
                return false;
        }

        if ((cb != null) &&(mRadioStChangeCallback.containsKey(cb))) {
            cb.onRadioStateChange(false);
            mRadioStChangeCallback.remove(cb);
        }
        try {
            return mManagerService.disableRadio(radioType);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Releases LE Radio reference
     * @hide
     */
    public boolean releaseLeRadioReference(BluetoothGattServiceStateChangeCallback cb) {
        boolean ret = false;
        if ((cb != null) && (mGattSrvStChangeCallback.contains(cb))) {
            ret = releaseRadioReference(BluetoothRadioManager.RADIO_TYPE_BLE, null);
        }else {
            return false;
        }
        if (ret == true) {
            cb.onGattServiceStateChange(false, null);
            mGattSrvStChangeCallback.remove(cb);
        }
        return ret;
    }

    protected void finalize() throws Throwable {
         try {
            mManagerService.unregisterRadioMgrCallback (mRadioMgrCallback);
            mManagerService.unregisterAdapter(mManagerCallback);
            mService.unregisterRadioClient(mUUID);
         } finally {
             super.finalize();
         }
     }


    public interface BluetoothRadioStateChangeCallback {
        public void onRadioStateChange(boolean up);
    }


    /**
     *  interface to be used by RadioManager client to get notified about  GattService status
     * @hide
     */

    public interface BluetoothGattServiceStateChangeCallback {
        public void onGattServiceStateChange(boolean up, IBluetoothGatt iGatt);
    }

    /**
     *   Callback implementation of  IBluetoothManagerCallback
     * @hide
     */

    final private IBluetoothManagerCallback mManagerCallback =
        new IBluetoothManagerCallback.Stub() {

            public void onBluetoothServiceUp(IBluetooth bluetoothService) {
                if (DBG) Log.d(TAG, "onBluetoothServiceUp: " + bluetoothService);
                mService = bluetoothService;
                mAirplaneOn = false;
                try {
                    mService.registerRadioClient(mManagerCallback,mUUID, mRadioType);
                }catch (RemoteException e)  {Log.e(TAG, "", e);}
            }

            public void onBluetoothServiceDown() {
                if (DBG) Log.d(TAG, "onBluetoothServiceDown: " + mService);
                mService = null;
                mBluetoothGatt = null;
                if (((mFMCount != 0) ||(mBleCount != 0)||(mAntCount != 0)) &&
                    (mAirplaneOn == false)){
                    reEnableRadio();
                }

            }
    };


    private void reEnableRadio() {
       if (DBG) Log.d(TAG, "onUserSwitched:");

        // When User is Switched,  BMS will take care of bringing BT service down
        // If there is any Radio client in the new User account, Enable Radio for them
        for (int i = 0; i < mGattSrvStChangeCallback.size(); i++)
            try {
                mManagerService.enableRadio(BluetoothRadioManager.RADIO_TYPE_BLE);
            } catch (RemoteException e) {Log.e(TAG, "", e);}

        Iterator it = mRadioStChangeCallback.entrySet().iterator();
        while (it.hasNext()) {
            if (DBG) Log.d(TAG, "Entry found in mGattSrvStChangeCallback");
            Map.Entry pairs = (Map.Entry)it.next();
            BluetoothRadioStateChangeCallback cb =
                (BluetoothRadioStateChangeCallback)pairs.getKey();
            try {
                if (cb != null) {
                    if (DBG) Log.d(TAG, "Calling enable Radio for radio Type = " +
                                  pairs.getValue());
                        mManagerService.enableRadio(((Integer)pairs.getValue()).intValue());
                    }
                }catch (Exception e)  { Log.e(TAG,"",e);}
            }
    }
        /**
         *   Callback implementation of  IBluetoothRadioMgrCallback
         * @hide
         */
        final private IBluetoothRadioMgrCallback mRadioMgrCallback =
        new IBluetoothRadioMgrCallback.Stub() {

            public void onGattServiceStateChange(boolean up, IBluetoothGatt bluetoothGatt) {
                if (DBG) Log.d(TAG, "onGattServiceStateChange: ");
                synchronized (mRadioMgrCallback) {
                    mBluetoothGatt = bluetoothGatt;
                    for (BluetoothGattServiceStateChangeCallback cb : mGattSrvStChangeCallback ){
                        try {
                            if (cb != null) {
                                cb.onGattServiceStateChange(up, mBluetoothGatt);
                            } else {
                                Log.d(TAG, "onGattServiceUp: cb is null!!!");
                            }
                        } catch (Exception e)  { Log.e(TAG,"",e);}
                    }
                }
            }

            public void onBTRadioStateChange(boolean up) {
                if (DBG) Log.d(TAG, "onBTRadioStateChange:  up = " + up);

                // Notifiy Radio state change callbacks
                Iterator it = mRadioStChangeCallback.entrySet().iterator();
                while (it.hasNext()) {
                        Map.Entry pairs = (Map.Entry)it.next();
                        BluetoothRadioStateChangeCallback cb =
                            (BluetoothRadioStateChangeCallback)pairs.getKey();
                        try {
                            if (cb != null)
                                cb.onRadioStateChange(up);
                        }catch (Exception e)  { Log.e(TAG,"",e);}
               }
               // check if GATT Service needs to be bound
               if (up == true) {
                    try {
                        mBluetoothGatt = mManagerService.getBluetoothGatt();
                    }catch(RemoteException e) {Log.e(TAG,"",e);}
                    if ((mGattSrvStChangeCallback.isEmpty()== false) && (mBluetoothGatt == null)) {
                        try {
                            mManagerService.enableGatt();
                        } catch (RemoteException e) {Log.e(TAG, "", e);}
                    } else if ((mGattSrvStChangeCallback.isEmpty()== false) &&
                               (mBluetoothGatt != null)) {
                        // Both Radio and Gatt are up. Notfity Gatt service clients
                        for (BluetoothGattServiceStateChangeCallback cb :mGattSrvStChangeCallback){
                            try {
                                if (cb != null) {
                                    cb.onGattServiceStateChange(up, mBluetoothGatt);
                                } else {
                                    Log.d(TAG, "onGattServiceUp: cb is null!!!");
                                }
                            } catch (Exception e)  { Log.e(TAG,"",e);}
                        }

                    }
                }else {
                    // Do nothing. BT service will stop GattService as well
                    //and as part of it we should receive Gatt service down
                }
            }

        public void onAirplaneModeChange(boolean on) {
            if (DBG) Log.d(TAG, "onAirplaneModeChange:  on = " + on);
            mAirplaneOn = on;
            if (on != true) {
                // Disable all radio reference for Gatt service clients
                for (int i = 0; i < mGattSrvStChangeCallback.size(); i++)
                    try {
                        mManagerService.enableRadio(BluetoothRadioManager.RADIO_TYPE_BLE);
                    } catch (RemoteException e) {Log.e(TAG, "", e);}
                /** Below code is commented since both ANT and FM handle
                                 Airplane mode in the application
                                  un-coment when it needs to be Centralized
                */
/*
                Iterator it = mRadioStChangeCallback.entrySet().iterator();
                while (it.hasNext()) {
                        if (DBG) Log.d(TAG, "Entry found in mGattSrvStChangeCallback");
                        Map.Entry pairs = (Map.Entry)it.next();
                        BluetoothRadioStateChangeCallback cb =
                              (BluetoothRadioStateChangeCallback)pairs.getKey();
                        try {
                            if (cb != null) {
                                if (DBG) Log.d(TAG, "Calling enable Radio for radio Type = " +
                                                    pairs.getValue());
                                mManagerService.enableRadio(((Integer)pairs.getValue()).intValue());
                            }
                        }catch (Exception e)  { Log.e(TAG,"",e);}
                    }
*/
            } else {
                try {
                    mManagerService.disableRadio(BluetoothRadioManager.RADIO_TYPE_ALL);
                } catch (RemoteException e) {Log.e(TAG, "", e);}
            }
        }

    public void onUserSwitched() {
        reEnableRadio();
        }
    };
}
