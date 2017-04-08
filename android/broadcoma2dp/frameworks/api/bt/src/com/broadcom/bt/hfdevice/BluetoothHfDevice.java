/******************************************************************************
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/


package com.broadcom.bt.hfdevice;

import java.util.ArrayList;
import java.util.List;

import com.broadcom.bt.hfdevice.IBluetoothHfDevice;
import com.broadcom.bt.hfdevice.BluetoothCallStateInfo;
import com.broadcom.bt.hfdevice.IBluetoothHfDeviceCallback;
import com.broadcom.bt.hfdevice.IBluetoothHfDevice.Stub;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;

import android.os.ServiceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class provides the APIs to control the Bluetooth HFP-HF Profile.
 *
 * A BluetoothHfDevice object acts as a proxy to the Bluetooth HFDevice
 * service and provides helper methods to receive asynchronous events from the service.
 *
 *<p>BluetoothHfDevice is a proxy object for controlling the Service
 * via IPC. Use {@link BluetoothHfDevice#getProxy} to get
 * the BluetoothHfDevice proxy object.
 *
 *<p>Application can receive callback events by calling
 * {@link BluetoothHfDevice#registerEventHandler}.
 *
 *<p>BluetoothHfDevice provides a way to:
 *<ul>
 *<li>Connect/disconnect to/from HFP-AG </li>
 *<li>Connect/disconnect audio connection to the connected HFP-AG</li>
 *<li>Initiate/control calls on the HFP-AG</li>
 *<li>Receive indicator updates from HFP-AG</li>
 *</ul>
 *
 *<p>Each method is protected with its appropriate permission.
 *
 */
public final class BluetoothHfDevice implements BluetoothProfile  {
    private static final String TAG = "BluetoothHfDevice";
    private static final boolean DBG = true;

    /**
     * Headset state when SCO audio is not connected.
     * This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_AUDIO_STATE_CHANGED} intent.
     */
    public static final int STATE_AUDIO_DISCONNECTED = 0;
    /**
     * Headset state when SCO audio is connecting.
     * This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_AUDIO_STATE_CHANGED} intent.
     */
    public static final int STATE_AUDIO_CONNECTING    = 1;
    /**
     * Headset state when SCO audio is connected.
     * This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_AUDIO_STATE_CHANGED} intent.
     */
    public static final int STATE_AUDIO_CONNECTED = 2;
    /**
     * Headset state when SCO audio is disconnecting.
     * This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_AUDIO_STATE_CHANGED} intent.
     */
    public static final int STATE_AUDIO_DISCONNECTING = 3;

    /** Call state when there is an active call */
    public static final int CALL_STATE_ACTIVE = 0;
    /** Call state for a held call */
    public static final int CALL_STATE_HELD = 1;
    /** Call state when a call is dialed */
    public static final int CALL_SETUP_STATE_DIALING = 2;
    /** Call state when a the number is alerted */
    public static final int CALL_SETUP_STATE_ALERTING = 3;
    /** Call state when there is an incoming call */
    public static final int CALL_SETUP_STATE_INCOMING = 4;
    /** Call state when there is a call waiting call */
    public static final int CALL_SETUP_STATE_WAITING =5;
    /** Call state when there is no call setup is in progress */
    public static final int CALL_SETUP_STATE_IDLE = 6;

    /** Device status indicator indices to access service information
        from the integer array returned by the function {@link #getDeviceIndicators}. */
    public static final int INDICATOR_TYPE_SERVICE = 0;
    /** Device status indicator indices to access roaming information
        from the integer array returned by the function {@link #getDeviceIndicators} */
    public static final int INDICATOR_TYPE_ROAM = 1;
    /** Device status indicator indices to access signal information
        from the integer array returned by the function {@link #getDeviceIndicators} */
    public static final int INDICATOR_TYPE_SIGNAL = 2;
    /** Device status indicator indices to access battery information
        from the integer array returned by the function {@link #getDeviceIndicators} */
    public static final int INDICATOR_TYPE_BATTERY = 3;
    /** @hide */
    public static final int INDICATOR_TYPE_MAX= 4;


    /** @hide */
    public static final int INDEX_NUM_OF_ACTV = 0;
    /** @hide */
    public static final int INDEX_CALL_SETUP_STATE  = 1;
    /** @hide */
    public static final int INDEX_CALL_NUM_OF_HELD = 2;
    /** @hide */
    public static final int INDEX_CALL_STATE_INFO_MAX = 3;

    /** Network service available */
    public static final int SERVICE_NOT_AVAILABLE = 0;
    /** Network status unavailable */
    public static final int SERVICE_AVAILABLE = 1;


    /**  Service type home  */
    public static final int SERVICE_TYPE_HOME = 0;
    /**  Service type roaming */
    public static final int SERVICE_TYPE_ROAMING = 1;


    /** Call directions for outgoing call */
    public static final int CALL_DIRECTION_OUTGOING = 0;
    /** Call directions for incoming call */
    public static final int CALL_DIRECTION_INCOMING = 1;

    /** Call type for voice call */
    public static final int CALL_TYPE_VOICE = 0;
    /** Call type for data call */
    public static final int CALL_TYPE_DATA = 1;
    /** Call type for fax call */
    public static final int CALL_TYPE_FAX = 2;

    /** Single party call */
    public static final int CALL_MPTY_TYPE_SINGLE = 0;
    /** Muti  party call */
    public static final int CALL_MPTY_TYPE_MULTI =1;

    /** Call address type unknown */
    public static final int CALL_ADDRTYPE_UNKNOWN = 129;//0x81,
    /** Call address type international */
    public static final int CALL_ADDRTYPE_INTERNATIONAL = 145;//0x91

    /** WideBand Speech disabled by peer (AG) */
    public static final int WBS_NONE =0;
    /** WideBand Speech enabled by peer (AG) */
    public static final int WBS_YES = 1;

    /** Voice Recognition State inactive*/
    public static final int VR_STATE_INACTIVE = 0;
    /** Voice Recognition State active*/
    public static final int VR_STATE_ACTIVE = 1;

    /** In Band ringtone setting status OFF*/
    public static final int INBAND_STATE_OFF = 0;
    /** In Band ringtone setting status ON*/
    public static final int INBAND_STATE_ON = 1;

    /** Volume type speaker */
    public static final int VOLUME_TYPE_SPK = 0;
    /** Volume type microphone */
    public static final int VOLUME_TYPE_MIC = 1;


    /** Call hold handling: Hangup held, reject incoming/waiting*/
    public static final int HANGUP_HELD = 0;
    /** Call hold handling: Hangup active calls and accept waiting/held call */
    public static final int HANGUP_ACTIVE_ACCEPT_HELD= 1;
    /** Call hold handling: Swap active and held calls */
    public static final int SWAP_CALLS = 2;
    /**  Call hold handling: Conference calls */
    public static final int CONFERENCE = 3;

    /** Phonebook memory storage type SIM */
    public static final String PHONE_MEM_TYPE_SIM = "SM"; //SIM book
    /** @hide phone storage type*/
    public static final String PHONE_MEM_TYPE_FDN = "FD"; // SIM fixes dialing
    /** @hide phone storage type*/
    public static final String PHONE_MEM_TYPE_MSISDN = "ON"; //SIM owner
    /** @hide phone storage type*/
    public static final String PHONE_MEM_TYPE_EN = "EN"; //SIM emergency
    /** Phonebook memory storage type dialed calls*/
    public static final String PHONE_MEM_TYPE_LAST_DIALED  = "DC";
    /** Phonebook memory storage type Missed calls*/
    public static final String PHONE_MEM_TYPE_MISSED  = "MC";
    /** Phonebook memory storage type phone contacts */
    public static final String PHONE_MEM_TYPE_PHONEBOOK = "ME";
    /** @hide phone storage type*/
    public static final String PHONE_MEM_TYPE_MT = "MT"; //Combined ME and SIM phonebook
    /** Phonebook memory storage type received calls*/
    public static final String PHONE_MEM_TYPE_RECEIVED  = "RC"; //
    /** @hide phone storage type*/
    public static final String PHONE_MEM_TYPE_SDN = "SN"; //Service dialing phonebook


    /** Extended Audio Gateway Error code (CMEE): Phone Failure */
    public static final int ERROR_AG_FAILURE = 0;
    /** Extended Audio Gateway Error code (CMEE): No connection to phone */
    public static final int ERROR_NO_CONNECTION_TO_PHONE = 1;
    /** Extended Audio Gateway Error code (CMEE): Operation not allowed */
    public static final int ERROR_OPERATION_NOT_ALLOWED = 3;
    /** Extended Audio Gateway Error code (CMEE): Operation not supported */
    public static final int ERROR_OPERATION_NOT_SUPPORTED = 4;
    /** Extended Audio Gateway Error code (CMEE): PH-SIM PIN required */
    public static final int ERROR_PIN_REQUIRED = 5;
    /** Extended Audio Gateway Error code (CMEE): SIM not inserted */
    public static final int ERROR_SIM_MISSING = 10;
    /** Extended Audio Gateway Error code (CMEE): SIM PIN required */
    public static final int ERROR_SIM_PIN_REQUIRED = 11;
    /** Extended Audio Gateway Error code (CMEE): SIM PUK required */
    public static final int ERROR_SIM_PUK_REQUIRED = 12;
    /** Extended Audio Gateway Error code (CMEE): SIM failure */
    public static final int ERROR_SIM_FAILURE = 13;
    /** Extended Audio Gateway Error code (CMEE): SIM busy */
    public static final int ERROR_SIM_BUSY = 14;
    /** Extended Audio Gateway Error code (CMEE): Incorrect password */
    public static final int ERROR_WRONG_PASSWORD = 16;
    /** Extended Audio Gateway Error code (CMEE): SIM PIN2 required */
    public static final int ERROR_SIM_PIN2_REQUIRED = 17;
    /** Extended Audio Gateway Error code (CMEE): SIM PUK2 required */
    public static final int ERROR_SIM_PUK2_REQUIRED = 18;
    /** Extended Audio Gateway Error code (CMEE): Memory full */
    public static final int ERROR_MEMORY_FULL = 20;
    /** Extended Audio Gateway Error code (CMEE): Invalid index */
    public static final int ERROR_INVALID_INDEX = 21;
    /** Extended Audio Gateway Error code (CMEE): Memory failure */
    public static final int ERROR_MEMORY_FAILURE = 23;
    /** Extended Audio Gateway Error code (CMEE): Text string too long */
    public static final int ERROR_TEXT_TOO_LONG = 24;
    /** Extended Audio Gateway Error code (CMEE): Invalid characters in text string */
    public static final int ERROR_TEXT_HAS_INVALID_CHARS = 25;
    /** Extended Audio Gateway Error code (CMEE): Dial string too long */
    public static final int ERROR_DIAL_STRING_TOO_LONG = 26;
    /** Extended Audio Gateway Error code (CMEE): Invalid characters in dial string */
    public static final int ERROR_DIAL_STRING_HAS_INVALID_CHARS = 27;
    /** Extended Audio Gateway Error code (CMEE): No network service */
    public static final int ERROR_NO_SERVICE = 30;
    /** Extended Audio Gateway Error code (CMEE): Network not allowed - emergency service only */
    public static final int ERROR_ONLY_911_ALLOWED = 32;

    /** Error code for success */
    public static final int NO_ERROR = 255;

    /** Phonebook read operation completed */
    public static final int PHONEBOOK_READ_COMPLETED = 253;
    /** Phonebook read operation progress update */
    public static final int PHONEBOOK_READ_PROGRESS_UPDATE = 254;


    /**
     * Intent used to broadcast the change in connection state of the Hf Device
     * profile.
     *
     * <p>This intent will have 5 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile. </li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     *   <li> {@link #EXTRA_LOCAL_FEATURES} - Valid only for {@link #STATE_CONNECTED}. </li>
     *   <li> {@link #EXTRA_PEER_FEATURES} - Valid only for {@link #STATE_CONNECTED}. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "com.broadcom.bt.hfdevice.profile.action.CONNECTION_STATE_CHANGED";

    /**
     * Intent used to broadcast the change in the Audio Connection state of the
     * Hf Device profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile. </li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_AUDIO_CONNECTED}, {@link #STATE_AUDIO_DISCONNECTED},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission
     * to receive.
     */

    public static final String ACTION_AUDIO_STATE_CHANGED =
        "com.broadcom.bt.hfdevice.profile.action.AUDIO_STATE_CHANGED";

    /**
     * Intent used to broadcast the change in the Call state of the
     * Hf Device profile.
     *
     * <p>This intent will have 2 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} can be any of CALL_STATE_
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission
     * to receive.
     */
    public static final String ACTION_CALL_STATE_CHANGED =
               "com.broadcom.bt.hfdevice.profile.action.CALL_STATE_CHANGED";


    /**
     * Intent used to broadcast the Ring event when HSP connection is active
     *
     * <p>This intent will have 1 extra:
     * <ul>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission
     * to receive.
     */
    public static final String ACTION_RING_EVENT =
               "com.broadcom.bt.hfdevice.profile.action.RING_EVENT";

     /**
      * Intent that can be broadcast to enable/disable HFDevice SDP record.
      * <p>This intent will have the following extra:
      * <ul>
      *   <li> {@link #EXTRA_REGISTER_HFDEVICE_SDP} - Boolean to enable/disable </li>
      * </ul>
      */
    public static final String ACTION_REGISTER_HFDEVICE_SDP =
               "com.broadcom.bt.action.REGISTER_HFDEVICE_SDP";

    /**
     * Intent broadcast to notify HFDevice service state change.
     * <p>This intent may or may not have extra(EXTRA_STATE).
     * This extra is sent by HF_DEVICE profile with its profile ID to notify
     * that the REGISTER/UNREGISTER SDP is successfull.
     * Settings app will have to reloadthe local UUIDs after receive this Intent.
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} can be any of CALL_STATE_

     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission
     * to receive.
     */
    public static final String ACTION_UUID_CHANGED =
               BluetoothAdapter.ACTION_UUID_CHANGED;

    /**
     * Intent used to broadcast the WBS state of the existing SLC connection
     * This intent is broadcasted only when both AG and HF supports WBS.
     * This will be broadcasted for whenever codec negotiation happens.
     * The app has to remember the codec negotiated for the existing SLC
     * connection.
     * <p>This intent will have the following extra:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of WBS. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} can be any of {@link #WBS_NONE},{@link #WBS_YES}
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission
     * to receive.
     */
    public static final String ACTION_WBS_STATE_CHANGED =
               "com.broadcom.bt.hfdevice.profile.action.WBS_STATE_CHANGED";

    /**
     * A boolean extra field in {@link #ACTION_REGISTER_HFDEVICE_SDP}
     * intents to enable/disable HFDevice SDP record.
     */
    public static final String EXTRA_REGISTER_HFDEVICE_SDP = "EXTRA.REGISTER.SDP";


    /**
     * A integer extra field in {@link #ACTION_CONNECTION_STATE_CHANGED}
     * The integer extra vaue can be read using the helper api {@link #getLocalFeatures}.
     */
    public static final String EXTRA_LOCAL_FEATURES = "EXTRA.LOCAL.FEATURES";


    /**
     * A integer extra field in {@link #ACTION_CONNECTION_STATE_CHANGED}
     * This integer extra value can be read using the helper api {@link #getPeerFeatures}.
     */
    public static final String EXTRA_PEER_FEATURES = "EXTRA.PEER.FEATURES";


    private Context mContext;
    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;
    private IBluetoothHfDevice mService;
    private IBluetoothHfDeviceEventHandler mCallback;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "Proxy object connected");
            mService = IBluetoothHfDevice.Stub.asInterface(service);
            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothProfile.HF_DEVICE, BluetoothHfDevice.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(BluetoothProfile.HF_DEVICE);
            }
        }
    };


    /**
     * Bluetooth state change handlers
     */
    private final IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
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
                                if (!mContext.bindService(new
                                        Intent(IBluetoothHfDevice.class.getName()),
                                        mConnection, 0)) {
                                    Log.e(TAG, "Could not bind to Bluetooth GATT Service");
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
     * Retrieve an instance of the Proxy object.
     *
     * @param ctx Application context
     * @param l The callback object that will contain the proxy instance
     *            returned
     * @return false if unable to initialize proxy retrieval, true if
     *         initialization succeeded
     */
    public static boolean getProxy(Context ctx, ServiceListener l){

        boolean status = false;
        BluetoothHfDevice proxy = null;

        try {
            proxy = new BluetoothHfDevice(ctx, l);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to get BluetoothHfDevice", t);
            return false;
        }
        return true;

    }

    /**
     * Close the connection of the profile proxy to the Service.
     *
     * <p> Clients should call this when they are no longer using
     * the proxy obtained from {@link #getProxy}.
     */
    public  void closeProxy(){
        unregisterEventHandler();
        mServiceListener = null;

        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE);
        if (b != null) {
            IBluetoothManager mgr = IBluetoothManager.Stub.asInterface(b);
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException re) {
                Log.e(TAG, "Unable to unregister BluetoothStateChangeCallback", re);
            }
        }

        synchronized (mConnection) {
            if (mService != null) {
                try {
                    mService = null;
                    mContext.unbindService(mConnection);
                } catch (Exception re) {
                    Log.e(TAG,"",re);
                }
            }
        }
    }

    /** Get the local Bluetooth HFP supported features
     ** @return {@link LocalHfFeatures}
     */
     public LocalHfFeatures getLocalFeatures(){
        if (DBG) log("getLocalFeatures");
        int localFeatures = 0;
        if (mService != null && isEnabled()) {
            try {
                localFeatures = mService.getLocalFeatures();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new LocalHfFeatures(localFeatures);
     }


     /** Get the remote Bluetooth HFP supported features
     ** @return {@link PeerHfFeatures}
     */
     public PeerHfFeatures getPeerFeatures(){
         if (DBG) log("getPeerFeatures");
         int peerFeatures = 0;
         if (mService != null && isEnabled()) {
             try {
                 peerFeatures = mService.getPeerFeatures();
             } catch (RemoteException e) {
                 Log.e(TAG, Log.getStackTraceString(new Throwable()));
             }
         }
         if (mService == null) Log.w(TAG, "Proxy not attached to service");
         return new PeerHfFeatures(peerFeatures);
     }

    /** Pass the localFeatures(int) value got in
     ** {@link IBluetoothHfDeviceEventHandler#onConnectionStateChange}
     ** or {@link #ACTION_CONNECTION_STATE_CHANGED}
     ** This api allows getting the LocalHfFeatures
     ** without having the instance of {@link BluetoothHfDevice}
     ** @return {@link LocalHfFeatures}
     */
     public static LocalHfFeatures getLocalFeatures(int localFeatures){
        if (DBG) log("getLocalFeatures Helper class");
        return new LocalHfFeatures(localFeatures);
     }


     /** Pass the peerFeatures(int) value got in
      ** {@link IBluetoothHfDeviceEventHandler#onConnectionStateChange}
      ** or {@link #ACTION_CONNECTION_STATE_CHANGED}
      ** This api allows getting the PeerHfFeatures
      ** without having the instance of {@link BluetoothHfDevice}
      ** @return {@link PeerHfFeatures}
      */
     public static PeerHfFeatures getPeerFeatures(int peerFeatures){
         if (DBG) log("getPeerFeatures Helper class");
         return new PeerHfFeatures(peerFeatures);
     }

    /**
     * Create a BluetoothHfDevice proxy object.
     */
    BluetoothHfDevice(Context context, ServiceListener l){
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
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
        Log.d(TAG, "BluetoothHfDevice() call bindService");
        if (!context.bindService(new Intent(IBluetoothHfDevice.class.getName()),
                                 mConnection, 0)) {
            Log.e(TAG, "Could not bind to Bluetooth HfDevice Service");
        }
    }

    /**
     * Register a callback event handler to receive events. ALL events will be
     * sent from the profile service to handler.
     * NOTE: calling registerEventHandler again with with a new handler will
     * cause events from the stack to be delivered to the new handler.
     *
     * @param handler
     *            The event callback object that HFDevice Profile Service will
     *            invoke when an event occurs
     * @return Returns true if the callback was successfully added to the list.
     * Returns false if it was not added
     */
    public void registerEventHandler(IBluetoothHfDeviceEventHandler handler){
        if (DBG) log( "registerEventHandler()");
        if (mService == null) return ;

        mCallback = handler;
        try {
            mService.registerEventHandler(mHfDeviceCallback);
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(new Throwable()));
        }

        return ;
    }


    /**
     * Unregister the event handler.
     * This function performs the following:
     * <ol>
     * <li>Stops event delivery to thecurrently registered
     * {IBluetoothHfDeviceEventHandler}</li>
     * <li>Unregisters the event delivery channel between the profile service
     * and this proxy object. This method
     * unregisters the internal remote callback object it uses</li>
     * </ol>
     */
    public void unregisterEventHandler(){
        if (DBG) Log.d(TAG, "unregisterEventHandler()");
        if (mService == null) return ;

        mCallback = null;
        try {
            mService.unRegisterEventHandler(mHfDeviceCallback);
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(new Throwable()));
        }
        return ;
    }


    /**
     * Get the current connection state of the profile
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Remote bluetooth device.
     * @return State of the profile connection. One of
     *               {@link #STATE_CONNECTED}, {@link #STATE_CONNECTING},
     *               {@link #STATE_DISCONNECTED}, {@link #STATE_DISCONNECTING}
     */
    public int getConnectionState(BluetoothDevice device) {
        if (DBG) log("getConnectionState(" + device + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.getConnectionState(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Get connected devices for this specific profile.
     *
     * <p> Return the set of devices which are in state {@link #STATE_CONNECTED}
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @return List of devices. The list will be empty on error.
     */
    public List<BluetoothDevice> getConnectedDevices() {
        if (DBG) log("getConnectedDevices()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getConnectedDevices();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * Get a list of devices that match any of the given connection
     * states.
     *
     * <p> If none of the devices match any of the given states,
     * an empty list will be returned.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param states Array of states. States can be one of
     *              {@link #STATE_CONNECTED}, {@link #STATE_CONNECTING},
     *              {@link #STATE_DISCONNECTED}, {@link #STATE_DISCONNECTING},
     * @return List of devices. The list will be empty on error.
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (DBG) log("getDevicesMatchingStates()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getDevicesMatchingConnectionStates(states);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * Get the current SCO/Audio state.
     *
     * @return One of the values from {@link #STATE_AUDIO_DISCONNECTED}, {@link #STATE_AUDIO_CONNECTING},
     *          {@link #STATE_AUDIO_CONNECTED} and {@link #STATE_AUDIO_DISCONNECTING}
     */
    public int getAudioState(BluetoothDevice device) {
        if (DBG) log("getAudioState(" + device + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.getAudioState(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
    }


    /**
     * Set priority of the profile.
     *
     * <p> The device should already be paired.
     *  Priority can be one of {@link #PRIORITY_ON} or
     * {@link #PRIORITY_OFF},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Paired bluetooth device
     * @param priority
     * @return true if priority is set, false on error
     * @hide
     */
    public boolean setPriority(BluetoothDevice device, int priority) {
        if (DBG) log("setPriority(" + device + ", " + priority + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            if (priority != BluetoothProfile.PRIORITY_OFF &&
                priority != BluetoothProfile.PRIORITY_ON) {
              return false;
            }
            try {
                return mService.setPriority(device, priority);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return false;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Get the priority of the profile.
     *
     * <p> The priority can be any of:
     * {@link #PRIORITY_AUTO_CONNECT}, {@link #PRIORITY_OFF},
     * {@link #PRIORITY_ON}, {@link #PRIORITY_UNDEFINED}
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Bluetooth device
     * @return priority of the device
     * @hide
     */
    public int getPriority(BluetoothDevice device) {
        if (DBG) log("getPriority(" + device + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.getPriority(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return PRIORITY_OFF;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return PRIORITY_OFF;
    }


    /**
     * Returns the cached AG device indicator values.
     *
     * @return
     *         The integer array has  current indicators as received from AT+CIND
     *         Status info regarding Service, Roam, Signal and Battery can be extracted
     *         from array using {@link #INDICATOR_TYPE_SERVICE}, {@link #INDICATOR_TYPE_ROAM},
     *         {@link #INDICATOR_TYPE_SIGNAL} and {@link #INDICATOR_TYPE_BATTERY} respectively.
     */
    public int[] getDeviceIndicators(BluetoothDevice device){
        if (DBG) log("getDeviceIndicators()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getDeviceIndicators(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return new int[3];
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new int[3];
    }


    /**
     * Returns the current call state.
     *
     * @return {@link BluetoothCallStateInfo} containing call setup state (CALL_STATE_ ),
     * number of active and held calls.
     */
    public BluetoothCallStateInfo getCallStateInfo(BluetoothDevice device){
        if (DBG) log("getDeviceIndicators()");
        if (mService != null && isEnabled()) {
            try {
                int[]arrCallStateInfo = mService.getCallStateInfo(device);
                return new BluetoothCallStateInfo(arrCallStateInfo[0],
                            arrCallStateInfo[1], arrCallStateInfo[2]);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return new BluetoothCallStateInfo(0,0,0);
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new BluetoothCallStateInfo(0,0,0);
    }

    /**
     * Initiate connection to a profile of the remote bluetooth device.
     *
     * <p> Currently, the system supports only 1 connection to the
     * headset/handsfree profile. The API will automatically disconnect connected
     * devices before connecting.
     *
     * <p> This API returns false in scenarios like the profile on the
     * device is already connected or Bluetooth is not turned on.
     * When this API returns true, it is guaranteed that
     * connection state callback {@link IBluetoothHfDeviceEventHandler#onConnectionStateChange}
     * will be invoked. Users can get the connection state of the profile from this callback.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error,
     *               true otherwise
     */
    public boolean connect(BluetoothDevice device) {
        if (DBG) log("connect(" + device + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.connect(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
                return false;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Initiate disconnection from a profile.
     *
     * <p> This API will return false in scenarios like the profile on the
     * Bluetooth device is not in connected state etc.
     * When this API returns true, it is guaranteed that connection state callback 
     * {@link IBluetoothHfDeviceEventHandler#onConnectionStateChange} will be invoked.
     * Users can get the disconnection state of the profile from this callback.
     *
     * <p> If the disconnection is initiated by a remote device, the state
     * will transition from {@link #STATE_CONNECTED} to
     * {@link #STATE_DISCONNECTED}. If the disconnect is initiated by the
     * host (local) device the state will transition from
     * {@link #STATE_CONNECTED} to state {@link #STATE_DISCONNECTING} to
     * state {@link #STATE_DISCONNECTED}. The transition to
     * {@link #STATE_DISCONNECTING} can be used to distinguish between the
     * two scenarios.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error,
     *               true otherwise
     */
    public boolean disconnect(BluetoothDevice device) {
        if (DBG) log("disconnect(" + device + ")");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.disconnect(device);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
              return false;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Initiates an audio connection.
     * It sets up SCO channel with remote connected AG device.
     *
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onAudioStateChange}
     *
     * @return true if successful
     *         false if there was some error such as
     *               there is no connected headset
     */
    public boolean connectAudio() {
        if (mService != null && isEnabled()) {
            try {
                return mService.connectAudio();
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Initiates an audio disconnection.
     * It tears down the SCO channel from remote connected AG device.
     *
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onAudioStateChange}
     *
     * @return true if successful
     *         false if there was some error such as
     *               there is no connected SCO channel
     * @hide
     */
    public boolean disconnectAudio() {
        if (mService != null && isEnabled()) {
            try {
                return mService.disconnectAudio();
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Start Bluetooth voice recognition. This methods sends the voice
     * recognition AT command to the AG which triggers the
     * audio connection.
     *
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onVRStateChange} and
     * {@link IBluetoothHfDeviceEventHandler#onAudioStateChange}
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Bluetooth remote device
     * @return false if there is no AG connected or if the
     *               connected AG doesn't support voice recognition
     *               or on error, true otherwise
     */
    public boolean startVoiceRecognition(BluetoothDevice device) {
        if (DBG) log("startVoiceRecognition()");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.startVoiceRecognition(device);
            } catch (RemoteException e) {
                Log.e(TAG,  Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }


    /**
     * Stop Bluetooth Voice Recognition mode. This triggers shut down of the
     * Bluetooth audio path.
     *
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onVRStateChange}
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Bluetooth remote device
     * @return false if there is no AG connected
     *               or on error, true otherwise
     */
    public boolean stopVoiceRecognition(BluetoothDevice device) {
        if (DBG) log("stopVoiceRecognition()");
        if (mService != null && isEnabled() &&
            isValidDevice(device)) {
            try {
                return mService.stopVoiceRecognition(device);
            } catch (RemoteException e) {
                Log.e(TAG,  Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Set volume gain.
     *
     * @param volType
     *            Type of volume change {@link #VOLUME_TYPE_MIC}/{@link #VOLUME_TYPE_SPK}
     * @param volume
     *            Current volume level (0-15)
     */

    public boolean setVolume(int volType, int volume) {
        if (DBG) log("setVolume()");
        if (mService != null && isEnabled()) {
            try {
                return mService.setVolume(volType, volume);
            } catch (RemoteException e) {
                Log.e(TAG,  Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Place an outgoing call to the specified number string.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCallStateChange}
     * @param number
     *            Number to dial.

     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean dial(String number){
        if (DBG) log("dial"+"number"+number);
        if (mService != null && isEnabled()) {
            try {
                return mService.dial(number);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Redial the last dialed number.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCallStateChange}
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean redial(){
        if (DBG) log("redial");
        if (mService != null && isEnabled()) {
            try {
                return mService.redial();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Hangup active call and incoming call.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCallStateChange}
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean hangup(){
        if (DBG) log("hangup");
        if (mService != null && isEnabled()) {
            try {
                return mService.hangup();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Call hold and multiparty handling.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCallStateChange}
     *
     * @param holdType Can be any of {@link #HANGUP_HELD}, {@link #HANGUP_ACTIVE_ACCEPT_HELD},
     * {@link #SWAP_CALLS} or {@link #CONFERENCE}
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean hold(int holdType){
        if (DBG) log("hold"+"type"+holdType);
        if (mService != null && isEnabled()) {
            try {
                return mService.hold(holdType);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Answer an incoming call.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCallStateChange}
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean answer() {
        if (DBG) log("answer");
        if (mService != null && isEnabled()) {
            try {
                return mService.answer();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Send DTMF code when there is an active call.
     * <p>Valid values are (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, A, B, C, #, *)
     *
     * @param dtmfcode DTMF code value
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean sendDTMFcode(char dtmfcode){
        if (DBG) log("sendDTMFcode"+"dtmfcode"+dtmfcode);
        if (mService != null && isEnabled()) {
            try {
                return mService.sendDTMFcode(dtmfcode);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Send pre-formatted AT command strings (example: AT+CPIN).
     * Updates the response through {@link IBluetoothHfDeviceEventHandler#onVendorAtRsp}
     *
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean sendVendorCmd(String atCmd){
        if (DBG) log("sendVendorCmd"+"atCmd"+atCmd);
        if (mService != null && isEnabled()) {
            try {
                return mService.sendVendorCmd(atCmd);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Query operator selection info.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onOperatorSelectionRsp}
     * @return false if this proxy object is
     *         not currently connected to the Hf Device service.
     */
    public boolean queryOperatorSelectionInfo(){
        if (DBG) log("queryOperatorSelectionInfo");
        if (mService != null && isEnabled()) {
            try {
                return mService.queryOperatorSelectionInfo();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Query subscriber info.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onSubscriberInfoRsp}
     * @return false if this proxy object is
     *         not currently connected to the Hf Device service.
     */
    public boolean querySubscriberInfo(){
        if (DBG) log("querySubscriberInfo");
        if (mService != null && isEnabled()) {
            try {
                return mService.querySubscriberInfo();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /**
     * Get List of Current Calls from AG.
     * Updates the status through {@link IBluetoothHfDeviceEventHandler#onCLCCRsp}
     * App should avoid calling getCLCC() when there is already a getCLCC call pending.
     * @return false if this proxy object is
     *         not currently connected to the Hf Device service.
     */
    public boolean getCLCC(){
        if (DBG) log("getCurrentCallList");
        if (mService != null && isEnabled()) {
            try {
                return mService.getCLCC();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }


    /** Read phone book entries.
     * Updates the status through onPhoneBookReadRsp().
     * @param phoneMemType
     *            Select the phone storage for reading. Can be any of {@link #PHONE_MEM_TYPE_SIM},
     *            {@link #PHONE_MEM_TYPE_PHONEBOOK}, {@link #PHONE_MEM_TYPE_LAST_DIALED}, {@link #PHONE_MEM_TYPE_MISSED},
     *            {@link #PHONE_MEM_TYPE_RECEIVED}.
     * @param maxReadLimit
     *             maxReadLimit gives the user a option to  limit the item count to query from AG side.
     *             For eg: The AG may have 100 contact but if the app sets the MaxLimit = 10
     *             then only 10 items will queried and returned to app.
     *             If app does not want to bother about the count and want to download all the contacts in AG side
     *             then set maxReadLimit = -1(default which will download all the available contacts for the memory).
     * @return false if this proxy object is
     *         not currently connected to the Hf Device service.
     */
    public boolean readPhoneBookList(String phoneMemType, int maxReadLimit) {
        if (DBG) log("readPhoneBookList"+"phoneMemType"+phoneMemType+
            "maxReadLimit"+maxReadLimit);
        if (mService != null && isEnabled()) {
            try {
                return mService.readPhoneBookList(phoneMemType, maxReadLimit);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Send HSP key pressed event to AG
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean sendKeyPressedEvent(){
        if (DBG) log("sendKeyPressedEvent");
        if ((mService != null) && isEnabled()) {
            try {
                return mService.sendKeyPressEvent();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Send indicator activation/deactivation.
     * Updates the status through {@link IHfDeviceEventHandler#onBIAStatus}
     * @param bEnableRoam
     *            Enable/disable(true/false) the roaming indicator update.
     * @param bEnableService
     *            Enable/disable(true/false) the Service indicator update.
     * @param bEnableSignal
     *            Enable/disable(true/false) the Signal indicator update.
     * @param bEnableBattery
     *            Enable/disable(true/false) the Battery indicator update.
     * @return false if object is currently not connected to the HfDevice
     *         service.
     */
    public boolean sendBIA(boolean bEnableRoam, boolean bEnableService,
                               boolean bEnableSignal, boolean bEnableBattery){
        if (DBG) log("sendBIA"+"bEnableRoam"+bEnableRoam
                  +"bEnableService"+bEnableService+"bEnableSignal"+bEnableSignal
                  +"bEnableBattery"+bEnableBattery);
        if ((mService != null) && isEnabled()) {
            try {
                return mService.sendBIA(bEnableRoam, bEnableService,
                                bEnableSignal, bEnableBattery);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) Log.d(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /* callbacks */
    /**
     * The class containing all the HfDevice callback function handlers. These
     * functions will be called by the HfDeviceService module when callback
     * events occur. They in turn relay the callback information back to the
     * main applications callback handler.
     */
    private final IBluetoothHfDeviceCallback mHfDeviceCallback =
        new IBluetoothHfDeviceCallback.Stub() {
        /**
         * Callback for connection state change.
         *
         * @param deviceState
         *            One of the STATE_
         */
        public void onConnectionStateChange(int errCode, BluetoothDevice remoteDevice,
                int newState, int prevState, int peerFeatures, int localFeatures){
            if (mCallback != null)
                mCallback.onConnectionStateChange(errCode, remoteDevice,
                newState, prevState);
         }

        /**
         * Callback for audio state change.
         *
         * @param deviceState
         *            One of the AUDIO_STATE_
         */
        public void onAudioStateChange(int newState, int prevState){
            if (mCallback != null)
                mCallback.onAudioStateChange(newState, prevState);
        }

        /**
         * Callback for device indicators update ( Battery, signal, roam, service)
         *
         * @param indValue, array containing all indicators of INDICATOR_TYPE_ as index.
         *            Contains the individual indicators
         */
        public void onIndicatorsUpdate(int[] indValue){
            if (mCallback != null)
                mCallback.onIndicatorsUpdate(indValue);
        }

        /**
         * Callback for call state change.
         *
         * @param callState
         *            One of the values from CALL_STATE_
         *            number,addrType: This is valid only for valid call states
         *            like CALL_SETUP_STATE_INCOMING.
         */
        public void onCallStateChange(int status, int callState, int numActive, int numHeld,
                                    String number ,int addrType){
            if (mCallback != null)
                mCallback.onCallStateChange(status, callState, numActive,
                numHeld, number, addrType);
        }

        /**
         * Callback for VR state change
         *
         * @param vrState
         *            One of the values from VR_STATE_
         */
        public void onVRStateChange(int status, int vrState){
            if (mCallback != null)
                mCallback.onVRStateChange(status, vrState);
        }

        /**
         * Callback for volume change
         *
         * @param volType
         *            Type of volume change {@link #VOLUME_TYPE_MIC}/{@link #VOLUME_TYPE_SPK}
         * @param volume
         *            Current volume (0-15)
         */
        public void onVolumeChange(int volType, int volume){
            if (mCallback != null)
                mCallback.onVolumeChange(volType, volume);
        }


        /**
         * Callback for readPhoneBookList() response
         *
         * @param BluetoothPhoneBookInfo
         *         List of phone book entries  with index, number , addrtype,name.
         *         getXXXX() can be used to get info from the class BluetoothPhoneBookInfo
         */
         public void onPhoneBookReadRsp(int status, List<BluetoothPhoneBookInfo> phoneNum) {
            if (mCallback != null)
                mCallback.onPhoneBookReadRsp(status, phoneNum);
        }



        /**
         * Callback for querySubscriberInfo() response
         *
         * @param number
         * @param addrType
         */
        public void onSubscriberInfoRsp(int status, String number ,int addrType) {
            if (mCallback != null)
                mCallback.onSubscriberInfoRsp(status, number, addrType);
        }

        /**
         * Callback for queryOperatorSelectionInfo response
         *
         * @param cops
         *            String containing the operator name.
         */
        public void onOperatorSelectionRsp(int status, int mode, String operatorName){
            if (mCallback != null)
                mCallback.onOperatorSelectionRsp(status, mode, operatorName);
        }

        /**
         * Callback for Extended error result code
         *
         * @param errorResultCode
         *            Containing the extended result code
         */
        public void onExtendedErrorResult(int errorResultCode){
            if (mCallback != null)
                mCallback.onExtendedErrorResult(errorResultCode);
        }


        /**
         * Callback for getCurrentCallList response
         *
         * @param clcc
         *         List of current call info with index, call_direction_ , call_state,
         *         call_mode_,call_mpty, number, call_addrtype
         *         getXXXX() can be used to get info from the class BluetoothClccInfo
         */
      public void onCLCCRsp(int status, List<BluetoothClccInfo> clcc) {
            if (mCallback != null)
                mCallback.onCLCCRsp(status, clcc);
         }

        /**
         * Callback for Vendor/app pre-formatted AT strings.
         * Note that if app sends a pre-formatted AT command for which a
         * callback
         * is already defined above, then the response will be sent in the
         * pre-defined callback.
         *
         * @param atRsp
         *            String containing the AT response
         */
        public void onVendorAtRsp(int status, String atRsp){
            if (mCallback != null)
                mCallback.onVendorAtRsp(status, atRsp);
        }

        /**
         * Callback for RING event(send by AG) when a HSP connection exist.
         * This will usually happen for incoming call in AG. Continous RING event
         * may be sent from AG side till the call is answered.In such case
         *.the app should take care such that it ignores the
         * the subsequent RING event if it is not necessary.
         */
        public void onRingEvent(){
            if (mCallback != null)
                mCallback.onRingEvent();
        }

        /**
         * Callback to report the ON/OFF status for the InBand Ring tone feature.
         * @param status
         *            With either one of the values INBAND_STATE_OFF or INBAND_STATE_ON
         */
        public void onInBandRingStatusEvent(int inBandRingStatus){
            if (mCallback != null)
                mCallback.onInBandRingStatusEvent(inBandRingStatus);
        }

         /**
         * Callback to report the status for the API sendBIA.
         * @param status
         *            With either one of the values ERROR_AG_FAILURE/NO_ERROR
         */
        public void onBIAStatus(int status){
            if (mCallback != null)
                mCallback.onBIAStatus(status);
        }
     };


    /** Helper class for local HF supported features */

    public static class LocalHfFeatures {

        private static final int ECNR =  0x0001;  /* Echo cancellation and/or noise reduction */
        private static final int THREEWAY =  0x0002;  /* Call waiting and three-way calling */
        private static final int CLIP =  0x0004;  /* Caller ID presentation capability  */
        private static final int VREC =  0x0008;  /* Voice recoginition activation capability  */
        private static final int RVOL =  0x0010;  /* Remote volume control capability  */
        private static final int ECS =   0x0020;  /* Enhanced Call Status  */
        private static final int ECC =   0x0040;  /* Enhanced Call Control  */
        private static final int CODEC = 0x0080;  /* Codec negotiation */
        private static final int VOIP =  0x0100;  /* VoIP call */
        private static final int UNAT =  0x1000;  /* Pass unknown AT command responses */

        int mLocalFeatures;
        /** Call waiting and three-way calling support.
         * @return true if local device supports call waiting and three way calling
         *         false otherwise
         */
        public boolean isThreeWayCallSupported(){
            return (THREEWAY == (THREEWAY & mLocalFeatures));
        }

        /** Echo cancellation and/or noise reduction support.
         * @return true if local device supports Echo cancellation and/or noise reduction
         *         false otherwise
         */
        public boolean isECNRSupported(){
            return (ECNR == (ECNR & mLocalFeatures));
        }

        /** Caller ID presentation capability.
         * @return true if local device supports Caller ID presentation.
         *         false otherwise
         */
        public boolean isCLIPSupported(){
            return (CLIP == (CLIP & mLocalFeatures));
        }

        /** Voice recoginition activation capability.
         * @return true if local device supports Voice recognition activation.
         *         false otherwise
         */
        public boolean isVRSupported(){
            return (VREC == (VREC & mLocalFeatures));
        }

        /** Remote volume control capability.
         * @return true if local device supports Remote volume control.
         *         false otherwise
         */
        public boolean isRemoteVolumeControlSupported(){
            return (RVOL == (RVOL & mLocalFeatures));
        }

        /** Enhanced Call Status capability.
         * @return true if local device supports Enhanced Call Status.
         *         false otherwise
         */
        public boolean isECStatusSupported(){
            return (ECS == (ECS & mLocalFeatures));
        }

        /** Enhanced Call Control capability.
         * @return true if local device supports Enhanced Call Control.
         *         false otherwise
         */
        public boolean isECControlSupported(){
            return (ECC == (ECC & mLocalFeatures));
        }

        /** Codec negotiation capability (WBS).
         * @return true if local device supports Codec negotiation.
         *         false otherwise
         */
        public boolean isCodecNegotiationSupported(){
            return (CODEC == (CODEC & mLocalFeatures));
        }

        /** VoIP call capability.
         * @return true if local device supports VoIP call.
         *         false otherwise
         */
        public boolean isVoipCallSupported(){
            return (VOIP == (VOIP & mLocalFeatures));
        }

        /** Unknown AT command handling
         *  @hide
         */
        public boolean isUnknownAtCommandSupported(){
            return (UNAT == (UNAT & mLocalFeatures));
        }


        /** Local HF supported features */
        LocalHfFeatures (int localFeatures){
            mLocalFeatures = localFeatures;
        }

        /** Dump local support features
         *  @hide
         */
        public void printLog() {
            Log.d(TAG+"LocalHfFeatures","isThreeWayCallSupported()="+isThreeWayCallSupported()+"\n"+
                "isECNRSupported()="+isECNRSupported()+"\n"+
                "isCLIPSupported()="+isCLIPSupported()+"\n"+
                "isVRSupported()="+isVRSupported()+"\n"+
                "isRemoteVolumeControlSupported()="+isRemoteVolumeControlSupported()+"\n"+
                "isECStatusSupported()="+isECStatusSupported()+"\n"+
                "isECControlSupported()="+isECControlSupported()+"\n"+
                "isCodecNegotiationSupported()="+isCodecNegotiationSupported()+"\n"+
                "isVoipCallSupported()="+isVoipCallSupported()+
                "isUnknownAtCommandSupported()="+isUnknownAtCommandSupported());
        }


}

    /** Helper class for remote AG supported features */
    public static class PeerHfFeatures {

        private static final int THREEWAY =  0x0001;    /* Three-way calling */
        private static final int ECNR =  0x0002;    /* Echo cancellation and/or noise reduction */
        private static final int VREC =  0x0004;    /* Voice recognition */
        private static final int INBAND= 0x0008;    /* In-band ring tone */
        private static final int VTAG =  0x0010;    /* Attach a phone number to a voice tag */
        private static final int REJECT= 0x0020;    /* Ability to reject incoming call */
        private static final int ECS =   0x0040;    /* Enhanced call status */
        private static final int ECC =   0x0080;    /* Enhanced call control */
        private static final int EERC =  0x0100;    /* Extended error result codes */
        private static final int CODEC = 0x0200;    /* Codec Negotiation */
        private static final int VOIP =  0x0400;    /* VoIP call */

        /* When no fields are set it is assumed as HSP conn */
        private static final int HSP_ROLE =  0x0000;

        int mPeerFeatures;

        public boolean isHSPConnection(){
            return (HSP_ROLE == mPeerFeatures);
        }


        /** Call waiting and three-way calling support.
         * @return true if remote device supports call waiting and three way calling
         *         false otherwise
         */
        public boolean isThreeWayCallSupported(){
            return (THREEWAY == (THREEWAY & mPeerFeatures));
        }

        /** Echo cancellation and/or noise reduction support.
         * @return true if remote device supports Echo cancellation and/or noise reduction
         *         false otherwise
         */
        public boolean isECNRSupported(){
            return (ECNR == (ECNR & mPeerFeatures));
        }

        /** Voice recoginition activation capability.
         * @return true if remote device supports Voice recognition activation.
         *         false otherwise
         */
        public boolean isVRSupported(){
            return (VREC == (VREC & mPeerFeatures));
        }

        /** In-band ring tone capability.
         * @return true if remote device supports In-band ring tone.
         *         false otherwise
         */
        public boolean isInBandToneSupported(){
            return (INBAND == (INBAND & mPeerFeatures));
        }

        /** Attach a phone number to a voice tag.
         * @return true if remote device supports attaching a phone number to a voice tag.
         *         false otherwise
         */
        public boolean isVtagSupported(){
            return (VTAG == (VTAG & mPeerFeatures));
        }

        /** Ability to reject incoming call.
         * @return true if remote device supports rejecting an incoming call.
         *         false otherwise
         */
        public boolean isRejectIncomingSupported(){
            return (REJECT == (REJECT & mPeerFeatures));
        }

        /** Enhanced call status capability.
         * @return true if remote device supports Enhanced call status.
         *         false otherwise
         */
        public boolean isECStatusSupported(){
            return (ECS == (ECS & mPeerFeatures));
        }

        /** Enhanced call control capability.
         * @return true if remote device supports Enhanced call control.
         *         false otherwise
         */
        public boolean isECControlSupported(){
            return (ECC == (ECC & mPeerFeatures));
        }

        /** Extended error result codes capability.
         * @return true if remote device supports Extended error result codes.
         *         false otherwise
         */
        public boolean isEERCSupported(){
            return (EERC == (EERC & mPeerFeatures));
        }

        /** Codec negotiation capability (WBS).
         * @return true if remote device supports codec negotiation.
         *         false otherwise
         */
        public boolean isCodecNegotiationSupported(){
            return (CODEC == (CODEC & mPeerFeatures));
        }

        /** VoIP call capability.
         * @return true if remote device supports VoIP call.
         *         false otherwise
         */
        public boolean isVoipCallSupported(){
            return (VOIP == (VOIP & mPeerFeatures));
        }

        /** Remote AG supported features */
        public PeerHfFeatures(int peerFeatures){
            mPeerFeatures = peerFeatures;
        }

        /** Dump local support features
         *  @hide
         */
        public void printLog() {
            Log.d(TAG+"PeerHfFeatures","isThreeWayCallSupported()="+isThreeWayCallSupported()+"\n"+
                "isECNRSupported()="+isECNRSupported()+"\n"+
                "isVRSupported()="+isVRSupported()+"\n"+
                "isInBandToneSupported()="+isInBandToneSupported()+"\n"+
                "isVtagSupported()="+isVtagSupported()+"\n"+
                "isRejectIncomingSupported()="+isRejectIncomingSupported()+"\n"+
                "isECStatusSupported()="+isECStatusSupported()+"\n"+
                "isECControlSupported()="+isECControlSupported()+"\n"+
                "isEERCSupported()="+isEERCSupported()+"\n"+
                "isCodecNegotiationSupported()="+isCodecNegotiationSupported()+"\n"+
                "isVoipCallSupported()="+isVoipCallSupported()+"\n"+
                "isHSPConnection()= "+ isHSPConnection());
        }
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


}
