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

/** Bluetooth AVRCP Controller APIs */
package com.broadcom.bt.avrcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

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

import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

/**
 * This class provides the APIs to control the Bluetooth AVRCP Controller Profile.
 *
 * A BluetoothAvrcpController object acts as a proxy to the Bluetooth AVRCP Controller
 * service and provide helper methods to receive asynchronous events from the service.
 *
 *<p>BluetoothAvrcpController is a proxy object for controlling the Bluetooth
 * Service via IPC. Use {@link BluetoothAvrcpController#getProxy} to get
 * the BluetoothAvrcpController proxy object.
 *
 *<p>Application can receive callback events by calling
 * {@link BluetoothAvrcpController#registerEventHandler}.
 *
 *<p> Connect/disconnect of AVRCP connection is handled as part of the A2DP sink, and
 * are thus unsupported in this class.
 *
 *<p>BluetoothAvrcpController provides a way to:
 *<ul>
 *<li>Send Passthrough commands (AVRCP 1.0)</li>
 *<li>Set/Retrieve Metadata and Application Settings (AVRCP 1.3)</li>
 *<li>Receive change (Play status/Track/...) notification events</li>
 *</ul>
 *
 *<p>Each method is protected with its appropriate permission.
 *
 */

public final class BluetoothAvrcpController implements BluetoothProfile {
    private static final String TAG = "BluetoothAvrcpCT";
    private static final boolean DBG = true;

    /** Target device features (bit mask) */
    /* No feature supported (AVRCP 1.0) */
    public static final int FEATURE_NONE = 0;
    /* Metadata feature supported (AVRCP 1.3) */
    public static final int FEATURE_METADATA = 0x01;
    /* Browse feature supported (AVRCP 1.4) */
    public static final int FEATURE_BROWSE = 0x04;

    /* Stack returned feature bits */
    private static final int AVRCP_FT_METADATA = 0x0040;
    private static final int AVRCP_FT_BROWSE = 0x0010;

    /** Status and Error Codes (returned in command response callbacks) */
    /* Invalid command */
    public static final int STS_INVALID_CMD = 0x00;
    /* Invalid parameter */
    public static final int STS_INVALID_PARAM = 0x01;
    /* Parameter content error */
    public static final int STS_PARAM_ERR = 0x02;
    /* Internal error */
    public static final int STS_INTERNAL_ERR = 0x03;
    /* Operation completed without error.  This is returned when the operation was successful. */
    public static final int STS_NO_ERROR = 0x04;
    /* UID Changed */
    public static final int STS_UID_CHANGED = 0x05;
    /* Invalid Direction (ChangePath) */
    public static final int STS_INVALID_DIRECTION = 0x07;
    /* Not a Directory (ChangePath) */
    public static final int STS_NOT_DIRECTORY = 0x08;
    /* Does Not Exist (ChangePath, PlayItem, AddToNowPlaying, GetItemAttributes) */
    public static final int STS_NOT_EXIST = 0x09;
    /* Invalid Scope (GetFolderItems, PlayItem, AddToNowPlayer, GetItemAttributes) */
    public static final int STS_INVALID_SCOPE = 0x0a;
    /* Range Out of Bounds (GetFolderItems) */
    public static final int STS_BAD_RANGE = 0x0b;
    /* Folder Item is not playable (PlayItem, AddToNowPlaying) */
    public static final int STS_FOLDER_NOT_PLAYABLE = 0x0c;
    /* Media in Use (PlayItem, AddToNowPlaying) */
    public static final int STS_MEDIA_IN_USE = 0x0d;
    /* Now Playing List Full (AddToNowPlaying) */
    public static final int STS_LIST_FULL = 0x0e;
    /* The Browsed Media Player does not support search (Search) */
    public static final int STS_SEARCH_NOT_SUPPORTED = 0x0f;
    /* Search already in Progress (Search) */
    public static final int STS_SEARCH_IN_PROGRESS = 0x10;
    /* Invalid Player Id (SetAddressedPlayer, SetBrowsedPlayer) */
    public static final int STS_INVALID_PLAYER_ID = 0x11;
    /* Player Not Browsable (SetBrowsedPlayer) */
    public static final int STS_NOT_BROWSABLE = 0x12;
    /* Player Not Addressed (Search, SetBrowsedPlayer) */
    public static final int STS_NOT_ADDRESSED = 0x13;
    /* No valid Search Results (GetFolderItems) */
    public static final int STS_BAD_SEARCH_RESULT = 0x14;
    /* No available players */
    public static final int STS_NO_PLAYER = 0x15;
    /* Addressed Player Changed (Register Notification) */
    public static final int STS_ADDR_PLAYER_CHANGED = 0x16;
    /* Failed to send command to remote device */
    public static final int STS_CMD_NOT_SENT = 0x100;
    /* No reponse from remote device */
    public static final int STS_NO_RESPONSE = 0x101;

    /** Attributes */
    /** Player Attribute - Equalizer */
    public static final byte ATTRIBUTE_EQUALIZER = 1;
    /** Player Attribute - Repeat */
    public static final byte ATTRIBUTE_REPEAT = 2;
    /** Player Attribute - Shuffle */
    public static final byte ATTRIBUTE_SHUFFLE = 3;
    /** Player Attribute - Scan */
    public static final byte ATTRIBUTE_SCAN = 4;

    /** Attribute values */
    /** Player Attribute Value - Equalizer OFF */
    public static final byte ATTR_VALUE_EQUALIZER_OFF = 1;
    /** Player Attribute Value - Equalizer ON */
    public static final byte ATTR_VALUE_EQUALIZER_ON = 2;

    /** Player Attribute Value - Repeat OFF */
    public static final byte ATTR_VALUE_REPEAT_OFF = 1;
    /** Player Attribute Value - Single track repeat*/
    public static final byte ATTR_VALUE_REPEAT_SINGLE_TRACK = 2;
    /** Player Attribute Value - All track repeat */
    public static final byte ATTR_VALUE_REPEAT_ALL_TRACK = 3;
    /** Player Attribute Value - Group repeat */
    public static final byte ATTR_VALUE_REPEAT_GROUP = 4;

    /** Player Attribute Value - Shuffle OFF */
    public static final byte ATTR_VALUE_SHUFFLE_OFF = 1;
    /** Player Attribute Value - All tracks shuffle */
    public static final byte ATTR_VALUE_SHUFFLE_ALL_TRACK = 2;
    /** Player Attribute Value - Group shuffle */
    public static final byte ATTR_VALUE_SHUFFLE_GROUP = 3;

    /** Player Attribute Value - Scan OFF */
    public static final byte ATTR_VALUE_SCAN_OFF = 1;
    /** Player Attribute Value - All tracks scan */
    public static final byte ATTR_VALUE_SCAN_ALL_TRACK = 2;
    /** Player Attribute Value - Group scan */
    public static final byte ATTR_VALUE_SCAN_GROUP = 3;

    /** Media attributes */
    /** Media Attribute - Title of the media */
    public static final int MEDIA_ATTRIBUTE_TITLE = 1;
    /** Media Attribute - Name of the artist */
    public static final int MEDIA_ATTRIBUTE_ARTIST = 2;
    /** Media Attribute - Name of the album */
    public static final int MEDIA_ATTRIBUTE_ALBUM = 3;
    /** Media Attribute - Track number of the media */
    public static final int MEDIA_ATTRIBUTE_TRACK_NUM = 4;
    /** Media Attribute - Total number of the media */
    public static final int MEDIA_ATTRIBUTE_NUM_TRACKS = 5;
    /** Media Attribute - Genre */
    public static final int MEDIA_ATTRIBUTE_GENRE = 6;
    /** Media Attribute - Playing time in millisecond */
    public static final int MEDIA_ATTRIBUTE_PLAYING_TIME = 7;

    /** Play status */
    /** Play status - Stopped */
    public static final byte PLAY_STATUS_STOPPED = 0;
    /** Play status - Playing */
    public static final byte PLAY_STATUS_PLAYING = 1;
    /** Play status - Paused */
    public static final byte PLAY_STATUS_PAUSED = 2;
    /** Play status - Seek Forward */
    public static final byte PLAY_STATUS_FWD_SEEK = 3;
    /** Play status - Seek Rewind */
    public static final byte PLAY_STATUS_REV_SEEK = 4;
    /** Play status - Error */
    public static final byte PLAY_STATUS_ERROR = -1;

    /** Pass through commands */
    private static final byte PASS_THRU_CMD_PLAY = 0x44;
    private static final byte PASS_THRU_CMD_STOP = 0x45;
    private static final byte PASS_THRU_CMD_PAUSE = 0x46;
    private static final byte PASS_THRU_CMD_REWIND = 0x48;
    private static final byte PASS_THRU_CMD_FASTFORWARD = 0x49;
    private static final byte PASS_THRU_CMD_FORWARD = 0x4B;
    private static final byte PASS_THRU_CMD_BACKWARD = 0x4C;

    /** Button states */
    private static final byte BUTTON_STATE_PRESSED = 0;
    private static final byte BUTTON_STATE_RELEASED = 1;

    /** Notification events */
    private static final byte EVENT_PLAYBACK_STATUS_CHANGED = 1;
    private static final byte EVENT_TRACK_CHANGED = 2;
    private static final byte EVENT_TRACK_REACHED_END = 3;
    private static final byte EVENT_TRACK_REACHED_START = 4;
    private static final byte EVENT_PLAYBACK_POS_CHANGED = 5;
    private static final byte EVENT_PLAYER_APPLICATION_SETTING_CHANGED = 8;

    /** Scopes */
    public static final byte SCOPE_MEDIA_PLAYER_LIST = 0;
    public static final byte SCOPE_VIRTUAL_FILESYSTEM = 1;
    public static final byte SCOPE_SEARCH = 2;
    public static final byte SCOPE_NOW_PLAYING = 3;

    /** Change path directions */
    public static final byte CHANGE_PATH_DIRECTION_UP = 0;
    public static final byte CHANGE_PATH_DIRECTION_DOWN = 1;

    /** AVRC Controller Disconnected State
     * @hide
     */
    public static final int AVRC_CONTROL_STATE_DISCONNECTED = 0;
    /** AVRC Controller Connected State
     * @hide
     */
    public static final int AVRC_CONTROL_STATE_CONNECTED    = 1;
    /**
     * Intent used to broadcast the change in connection state of the AVRCP Controller
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile. </li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "com.broadcom.bt.avrcp.profile.action.CONNECTION_STATE_CHANGED";

    private Context mContext;
    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;
    private IBluetoothAvrcpController mService;
    private IBluetoothAvrcpControllerEventHandler mEventHandler;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "Proxy object connected");
            mService = IBluetoothAvrcpController.Stub.asInterface(service);
            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(AVRCP_CT, BluetoothAvrcpController.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(AVRCP_CT);
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
                    if (DBG) Log.d(TAG, "Unbinding service...");
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
                                if (DBG) Log.d(TAG, "Binding service...");
                                if (!mContext.bindService(new
                                        Intent(IBluetoothAvrcpController.class.getName()),
                                        mConnection, 0)) {
                                    Log.e(TAG, "Could not bind to Bluetooth AVRCP CT Service");
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
    public static boolean getProxy(Context ctx, ServiceListener l) {

        boolean status = false;
        BluetoothAvrcpController proxy = null;

        if (DBG) Log.d(TAG, "getProxy");
        try {
            proxy = new BluetoothAvrcpController(ctx, l);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to get BluetoothAvrcpController", t);
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
    public  void closeProxy() {
        if (DBG) Log.d(TAG, "closeProxy");
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

    /**
     * Create a BluetoothAvrcpController proxy object.
     */
    BluetoothAvrcpController(Context context, ServiceListener l) {
        if (DBG) Log.d(TAG, "BluetoothAvrcpController");
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
        Log.d(TAG, "BluetoothAvrcpController() call bindService");
        if (!context.bindService(new Intent(IBluetoothAvrcpController.class.getName()),
                                 mConnection, 0)) {
            Log.e(TAG, "Could not bind to Bluetooth AVRCP CT Service");
        }
    }

    /**
     * Register a callback event handler to receive events. ALL events will be
     * sent from the profile service to handler.
     * NOTE: calling registerEventHandler again with with a new handler will
     * cause events from the stack to be delivered to the new handler.
     *
     * @param handler
     *            The event callback object that AVRCP Profile Service will
     *            invoke when an event occurs
     * @return Returns true if the callback was successfully added to the list.
     * Returns false if it was not added
     */
    public boolean registerEventHandler(IBluetoothAvrcpControllerEventHandler handler) {
        if (DBG) Log.d(TAG, "registerEventHandler()");
        if (mService == null) return false;

        mEventHandler = handler;
        try {
            return mService.registerCallback(mCallback);
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Unregister the event handler.
     * This function performs the following:
     * <ol>
     * <li>Stops event delivery to thecurrently registered
     * {IBluetoothAvrcpControllerEventHandler}</li>
     * <li>Unregisters the event delivery channel between the profile service
     * and this proxy object. This method
     * unregisters the internal remote callback object it uses</li>
     * </ol>
     *
     * @return Returns true if the callback was successfully unregistered.
     * Returns false either if service is not active or if unregister fails
     */
    public boolean unregisterEventHandler() {
        if (DBG) Log.d(TAG, "unregisterEventHandler()");
        if (mService == null) return false;

        mEventHandler = null;
        try {
            return mService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Get the current connection state of the profile
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Remote bluetooth device.
     * @return State of the profile connection. One of
     *               {@link #STATE_CONNECTED}
     *               {@link #STATE_DISCONNECTED}
     */
    public int getConnectionState(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "getConnectionState(" + device + ")");
        if (mService != null && isEnabled() && isValidDevice(device)) {
            try {
                return mService.getConnectionState(device);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
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
        if (DBG) Log.d(TAG, "getConnectedDevices()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getConnectedDevices();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * This method is not used
     *
     * @hide
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return null;
    }

    /**
     * Set priority of the profile
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
        if (DBG) Log.d(TAG, "setPriority(" + device + ", " + priority + ")");
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
        if (DBG) Log.d(TAG, "getPriority(" + device + ")");
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
     * Request to initiate a connection to a TG and updates the status
     * through {@link IBluetoothAvrcpControllerEventHandler#onConnectionStateChange}
     *
     * NOTE: Connect/disconnect of AVRCP connection is handled as part of the
     * A2DP sink, and are thus unsupported here.
     *
     * @param target
     *            Target device to connect to, or null to auto-connect last connected.
     * @return false if there was a problem initiating the connection
     *         procedure.
     * @hide
     */
    public boolean connect(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "connect(" + target + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.connect(target);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Intiate disconnection and updates the status through
     * {@link IBluetoothAvrcpControllerEventHandler#onConnectionStateChange}
     *
     * NOTE: Connect/disconnect of AVRCP connection is handled as part of the
     * A2DP sink, and are thus unsupported here.
     *
     * @param target
     *            Target device to disconnect from
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     * @hide
     */
    public boolean disconnect(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "disconnect(" + target + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.disconnect(target);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Get target supported features.
     * @param target
     *            Target device to get features from
     * @returns a bit map of remote supported features.
     */
    public int getTargetFeatures(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "getTargetFeatures(" + target + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                int btFeatures = mService.getTargetFeatures(target);
                int features = 0;

                if ((btFeatures & AVRCP_FT_METADATA) != 0)
                    features |= FEATURE_METADATA;
                if ((btFeatures & AVRCP_FT_BROWSE) != 0)
                    features |= FEATURE_BROWSE;

                return features;
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return 0;
    }

    /**
     * Send PLAY command to the target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean play(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "play(" + target + ")");
        boolean success = sendPassThroughCommand(target, PASS_THRU_CMD_PLAY, BUTTON_STATE_PRESSED);
        if (success)
            success = sendPassThroughCommand(target, PASS_THRU_CMD_PLAY, BUTTON_STATE_RELEASED);
        return success;
    }

    /**
     * Send STOP command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean stop(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "stop(" + target + ")");
        boolean success = sendPassThroughCommand(target, PASS_THRU_CMD_STOP, BUTTON_STATE_PRESSED);
        if (success)
            success = sendPassThroughCommand(target, PASS_THRU_CMD_STOP, BUTTON_STATE_RELEASED);
        return success;
    }

    /**
     * Send PAUSE command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean pause(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "pause(" + target + ")");
        boolean success = sendPassThroughCommand(target, PASS_THRU_CMD_PAUSE, BUTTON_STATE_PRESSED);
        if (success)
            success = sendPassThroughCommand(target, PASS_THRU_CMD_PAUSE, BUTTON_STATE_RELEASED);
        return success;
    }

    /**
     * Send REWIND command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @param isPressed
     *            button is pressed (TRUE) or released (FALSE)
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean rewind(BluetoothDevice target, boolean isPressed) {
        if (DBG) Log.d(TAG, "rewind(" + target + ", " + isPressed + ")");
        return sendPassThroughCommand(target, PASS_THRU_CMD_REWIND,
                    isPressed ? BUTTON_STATE_PRESSED : BUTTON_STATE_RELEASED);
    }

    /**
     * Send FAST FORWARD command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @param isPressed
     *            button is pressed (TRUE) or released (FALSE)
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean fastforward(BluetoothDevice target, boolean isPressed) {
        if (DBG) Log.d(TAG, "fastforward(" + target + ", " + isPressed + ")");
        return sendPassThroughCommand(target, PASS_THRU_CMD_FASTFORWARD,
                    isPressed ? BUTTON_STATE_PRESSED : BUTTON_STATE_RELEASED);
    }

    /**
     * Send FORWARD (next track) command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean forward(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "forward(" + target + ")");
        boolean success = sendPassThroughCommand(target, PASS_THRU_CMD_FORWARD, BUTTON_STATE_PRESSED);
        if (success)
            success = sendPassThroughCommand(target, PASS_THRU_CMD_FORWARD, BUTTON_STATE_RELEASED);
        return success;
    }

    /**
     * Send BACKWARD (previous track) command to target.
     * There is no response for this command. If the target supports play state
     * change notification, it will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlaybackStatusChanged}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean backward(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "backward(" + target + ")");
        boolean success = sendPassThroughCommand(target, PASS_THRU_CMD_BACKWARD, BUTTON_STATE_PRESSED);
        if (success)
            success = sendPassThroughCommand(target, PASS_THRU_CMD_BACKWARD, BUTTON_STATE_RELEASED);
        return success;
    }

    /**
     * Request the target device to provide target supported player application setting attributes.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onListPlayerApplicationSettingAttributesRsp}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean listPlayerApplicationSettingAttributes(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "listPlayerApplicationSettingAttributes(" + target + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.listPlayerApplicationSettingAttributes(target);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Requests the target device to list the set of possible values for the requested player
     * application setting attribute.  The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onListPlayerApplicationSettingValuesRsp}
     * @param target
     *            Target device to send command to
     * @param attribute
     *            ID of the attribute that the values to be listed
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean listPlayerApplicationSettingValues(BluetoothDevice target,
                        byte attribute) {
        if (DBG) Log.d(TAG, "listPlayerApplicationSettingValues(" + target + ", "
                        + attribute + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.listPlayerApplicationSettingValues(target, attribute);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Requests the target device to provide the current set values on the target for the provided
     * player application setting attributes list.  The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetCurrentPlayerApplicationSettingValueRsp}
     * @param target
     *            Target device to send command to
     * @param attributes
     *            IDs of the attributes the values are retrieving from
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getCurrentPlayerApplicationSettingValue(BluetoothDevice target,
                        byte[] attributes) {
        if (DBG) Log.d(TAG, "getCurrentPlayerApplicationSettingValue(" + target + ", "
                        + Arrays.toString(attributes) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getCurrentPlayerApplicationSettingValue(target, attributes);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Requests to set the player application setting list of player application setting values on
     * the target device.  The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlayerAppSettingChanged}
     * @param target
     *            Target device to send command to
     * @param attributes
     *            IDs of attributes to be set in target device
     * @param values
     *            values to be set in target device
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean setPlayerApplicationSettingValue(BluetoothDevice target,
                        byte[] attributes, byte[] values) {
        if (DBG) Log.d(TAG, "setPlayerApplicationSettingValue(" + target + ", "
                        + Arrays.toString(attributes) + ", " + Arrays.toString(values) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.setPlayerApplicationSettingValue(target, attributes, values);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Requests the target device to provide supported player application setting
     * attribute displayable text.  The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetPlayerApplicationSettingAttributeTextRsp}
     * @param target
     *            Target device to send command to
     * @param attributes
     *            IDs of the attributes the text is retrieving from
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getPlayerApplicationSettingAttributeText(BluetoothDevice target,
                        byte[] attributes) {
        if (DBG) Log.d(TAG, "getPlayerApplicationSettingAttributeText(" + target + ", "
                        + Arrays.toString(attributes) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getPlayerApplicationSettingAttributeText(target, attributes);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Request the target device to provide target supported player application setting
     * value displayable text.  The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetPlayerApplicationSettingValueTextRsp}
     * @param target
     *            Target device to send command to
     * @param attribute
     *            ID of the attribute
     * @param values
     *            IDs of the values the text is retrieving from
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getPlayerApplicationSettingValueText(BluetoothDevice target,
                        byte attribute, byte[] values) {
        if (DBG) Log.d(TAG, "getPlayerApplicationSettingValueText(" + target + ", "
                        + attribute + ", " + Arrays.toString(values) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getPlayerApplicationSettingValueText(target, attribute, values);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Requests the target device to provide the attributes of the current track.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetElementAttributesRsp}
     * @param target
     *            Target device to send command to
     * @param attributes
     *            IDs of the attributes
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getElementAttributes(BluetoothDevice target, int[] attributes) {
        if (DBG) Log.d(TAG, "getElementAttributes(" + target + ", "
                        + Arrays.toString(attributes) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getElementAttributes(target, attributes);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Get the status of the currently playing media at the target device.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetPlayStatusRsp}
     * @param target
     *            Target device to send command to
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getPlayStatus(BluetoothDevice target) {
        if (DBG) Log.d(TAG, "getPlayStatus(" + target + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getPlayStatus(target);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Informs the target which media player the controller wishes to control.
     * There is no response for this command.  Target will send back event through
     * {@link IBluetoothAvrcpControllerEventHandler#onAddressedPlayerChanged}
     * @param target
     *            Target device to send command to
     * @param playerId
     *            Unique Media Player ID returned in
     *            {@link IBluetoothAvrcpControllerEventHandler#onGetFolderItemsRsp}
     *            after {@link #getFolderItems} is called with scope set to
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean setAddressedPlayer(BluetoothDevice target, int playerId) {
        if (DBG) Log.d(TAG, "setAddressedPlayer(" + target + ", " + playerId + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.setAddressedPlayer(target, playerId);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Informs the target to which player browsing commands should be routed.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onSetBrowsedPlayerRsp}
     * @param target
     *            Target device to send command to
     * @param playerId
     *            Unique Media Player ID returned in
     *            {@link IBluetoothAvrcpControllerEventHandler#onGetFolderItemsRsp}
     *            after {@link #getFolderItems} is called with scope set to
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean setBrowsedPlayer(BluetoothDevice target, int playerId) {
        if (DBG) Log.d(TAG, "setBrowsedPlayer(" + target + ", " + playerId + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.setBrowsedPlayer(target, playerId);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Navigates the Browsed Player's virtual file system. This command allows the controller
     * to navigate one level up or down in the virtual file system.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onChangePathRsp}
     * @param target
     *            Target device to send command to
     * @param direction
     *            {@link #CHANGE_PATH_DIRECTION_UP} or {@link #CHANGE_PATH_DIRECTION_DOWN}
     * @param folderUid
     *            The UID of the folder to navigate to.  This may be retrieved via
     *            {@link #getFolderItems}.
     *            If the direction is UP then this parameter should be set to 0.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean changePath(BluetoothDevice target, byte direction, long folderUid) {
        if (DBG) Log.d(TAG, "changePath(" + target + ", " + direction + ", " + folderUid + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.changePath(target, direction, folderUid);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Retrieves a listing of the contents of a folder.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetFolderItemsRsp}
     * @param target
     *            Target device to send command to
     * @param scope
     *            One of
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     *            {@link #SCOPE_VIRTUAL_FILESYSTEM}
     *            {@link #SCOPE_SEARCH}
     *            {@link #SCOPE_NOW_PLAYING}
     * @param startItem
     *            The offset of the first returned item.  The first item in a folder is at
     *            offset 0.
     * @param endItem
     *            The offset of the last returned item.
     * @param attributes
     *            A list of requested attributes for each item returned.  Set to NULL if all
     *            attributes are reuqested.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getFolderItems(BluetoothDevice target, byte scope, int startItem,
                        int endItem, int[] attributes) {
        if (DBG) Log.d(TAG, "getFolderItems(" + target + ", " + scope + ", " + startItem
                        + ", " + endItem + ", " + Arrays.toString(attributes) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getFolderItems(target, scope, startItem, endItem, attributes);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Retrieves the metadata attributes for a particular media element item or folder item.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onGetItemAttributesRsp}
     * @param target
     *            Target device to send command to
     * @param scope
     *            One of
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     *            {@link #SCOPE_VIRTUAL_FILESYSTEM}
     *            {@link #SCOPE_SEARCH}
     *            {@link #SCOPE_NOW_PLAYING}
     * @param itemUid
     *            The UID of the media element item or folder item.  This may be retrieved via
     *            {@link #getFolderItems}.
     * @param attributes
     *            A list of requested attributes of the item.  Set to NULL if all attributes
     *            are reuqested.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean getItemAttributes(BluetoothDevice target, byte scope, long itemUid,
                        int[] attributes) {
        if (DBG) Log.d(TAG, "getItemAttributes(" + target + ", " + scope + ", " + itemUid +
                        ", " + Arrays.toString(attributes) + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.getItemAttributes(target, scope, itemUid, attributes);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Performs search from the current folder in the Browsed Player's virtual file system.
     * The search applies to the current folder and all folders below that.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onSearchRsp}
     * @param target
     *            Target device to send command to
     * @param searchString
     *            The string to be searched.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean search(BluetoothDevice target, String searchString) {
        if (DBG) Log.d(TAG, "search(" + target + ", " + searchString + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.search(target, searchString);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Starts playing an item indicated by the UID.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onPlayItemRsp}
     * @param target
     *            Target device to send command to
     * @param scope
     *            One of
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     *            {@link #SCOPE_VIRTUAL_FILESYSTEM}
     *            {@link #SCOPE_SEARCH}
     *            {@link #SCOPE_NOW_PLAYING}
     * @param itemUid
     *            The UID of the media element item or folder item, if supported, to be played.
     *            This may be retrieved via {@link #getFolderItems}.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean playItem(BluetoothDevice target, byte scope, long itemUid) {
        if (DBG) Log.d(TAG, "playItem(" + target + ", " + scope + ", " + itemUid + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.playItem(target, scope, itemUid);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Adds an item indicated by the UID to the Now Playing queue.
     * The response will be sent back through
     * {@link IBluetoothAvrcpControllerEventHandler#onAddToNowPlayingRsp}
     * @param target
     *            Target device to send command to
     * @param scope
     *            One of
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     *            {@link #SCOPE_VIRTUAL_FILESYSTEM}
     *            {@link #SCOPE_SEARCH}
     *            {@link #SCOPE_NOW_PLAYING}
     * @param itemUid
     *            The UID of the media element item or folder item, if supported, to be added to
     *            the now playing folder.  This may be retrieved via {@link #getFolderItems}.
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    public boolean addToNowPlaying(BluetoothDevice target, byte scope, long itemUid) {
        if (DBG) Log.d(TAG, "addToNowPlaying(" + target + ", " + scope + ", " + itemUid + ")");
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.addToNowPlaying(target, scope, itemUid);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /* callbacks */
    /**
     * The class containing all the AVRCP CT callback function handlers. These
     * functions will be called by the AcrcpControllerService module when callback
     * events occur. They in turn relay the callback information back to the
     * main applications callback handler.
     */
    private final IBluetoothAvrcpControllerCallback mCallback =
        new IBluetoothAvrcpControllerCallback.Stub() {
        /**
         * Returns the connection state change.
         *
         * @param target
         *            the Target device that has connection state change
         * @param newState
         *            one of the STATE_
         * @param success
         *            true if it was a successful state change
         *            false if the previous connect/disconnect request failed
         */
        public void onConnectionStateChange(BluetoothDevice target,
                        int newState, boolean success) {
            if (DBG) Log.d(TAG, "onConnectionStateChange(" + target + ", " + newState + ","
                            + success + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onConnectionStateChange(target, newState, success);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
         }

        /**
         * Returns the response of command ListPlayerApplicationSettingAttributes.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attributes
         *            list of attribute ID
         * @param status
         *            AVRCP response error/status code
         */
        public void onListPlayerApplicationSettingAttributesRsp(BluetoothDevice target,
                        byte[] attributes, int status) {
            if (isSuccess(status)) {
                if (DBG) Log.d(TAG, "onListPlayerApplicationSettingAttributesRsp(" + target + ", "
                                + Arrays.toString(attributes) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onListPlayerApplicationSettingAttributesRsp Failed(" + target
                                + ", " + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onListPlayerApplicationSettingAttributesRsp(target,
                                    attributes, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command ListPlayerApplicationSettingValues.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attribute
         *            the attribute that the values are returned for
         * @param values
         *            list of value ID
         * @param status
         *            AVRCP response error/status code
         */
        public void onListPlayerApplicationSettingValuesRsp(BluetoothDevice target,
                        byte attribute, byte[] values, int status) {
            if (isSuccess(status)) {
                if (DBG) Log.d(TAG, "onListPlayerApplicationSettingValuesRsp(" + target + ", " +
                               attribute + ", " + Arrays.toString(values) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onListPlayerApplicationSettingValuesRsp Failed(" + target +
                               ", " + attribute + ", " + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onListPlayerApplicationSettingValuesRsp(target,
                                    attribute, values, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetCurrentPlayerApplicationSettingValue.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attributes
         *            list of attribute ID
         * @param values
         *            list of current setting values
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetCurrentPlayerApplicationSettingValueRsp(BluetoothDevice target,
                        byte[] attributes, byte[] values, int status) {
            if (isSuccess(status)) {
                if (DBG) Log.d(TAG, "onGetCurrentPlayerApplicationSettingValueRsp(" + target + ", "
                                + Arrays.toString(attributes) + ", " + Arrays.toString(values)
                                + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onGetCurrentPlayerApplicationSettingValueRsp Failed(" + target
                                + ", " + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetCurrentPlayerApplicationSettingValueRsp(target,
                                    attributes, values, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetPlayerApplicationSettingAttributeText.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attributes
         *            list of attribute ID
         * @param attributeTexts
         *            list of attribute text
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetPlayerApplicationSettingAttributeTextRsp(BluetoothDevice target,
                        byte[] attributes, String[] attributeTexts, int status) {
            if (isSuccess(status)) {
                if (DBG) Log.d(TAG, "onGetPlayerApplicationSettingAttributeTextRsp(" + target + ", "
                                + Arrays.toString(attributes) + ", "
                                + Arrays.toString(attributeTexts) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onGetPlayerApplicationSettingAttributeTextRsp FAILED(" + target
                               + ", " + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetPlayerApplicationSettingAttributeTextRsp(target,
                                    attributes, attributeTexts, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetPlayerApplicationSettingValueText.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attribute
         *            the attribute that the values are returned for
         * @param values
         *            list of value ID
         * @param valueTexts
         *            list of value text
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetPlayerApplicationSettingValueTextRsp(BluetoothDevice target,
                        byte attribute, byte[] values, String[] valueTexts, int status) {
            if (isSuccess(status)) {
                if (DBG) Log.d(TAG, "onGetPlayerApplicationSettingValueTextRsp(" + target + ", "
                                + attribute + ", " + Arrays.toString(values) + ", "
                                + Arrays.toString(valueTexts) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onGetPlayerApplicationSettingValueTextRsp Failed(" + target
                                + ", " + attribute + ", " + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetPlayerApplicationSettingValueTextRsp(target,
                                    attribute, values, valueTexts, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetElementAttributes.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attributes
         *            list of attribute ID
         * @param valueTexts
         *            list of value text
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetElementAttributesRsp(BluetoothDevice target,
                        int[] attributes, String[] valueTexts, int status) {
            if (isSuccess(status)){
                if (DBG) Log.d(TAG, "onGetElementAttributesRsp(" + target + ", "
                                + Arrays.toString(attributes) + ", "
                                + Arrays.toString(valueTexts) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onGetElementAttributesRsp Failed(" + target + ", "
                                + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetElementAttributesRsp(target, attributes,
                                    valueTexts, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetPlayStatus.
         *
         * @param target
         *            the Target device that sent back the response
         * @param songLength
         *            leng of the current song
         * @param songPosition
         *            play position in the current song
         * @param playStatus
         *            play status
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetPlayStatusRsp(BluetoothDevice target, int songLength,
                        int songPosition, byte playStatus, int status) {
            if (DBG) Log.d(TAG, "onGetPlayStatusRsp(" + target + ", " + songLength + ", "
                            + songPosition + ", " + playStatus + ", " + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetPlayStatusRsp(target, songLength,
                                    songPosition, playStatus, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command SetBrowsedPlayer.
         *
         * @param target
         *            the Target device that sent back the response
         * @param numberOfItems
         *            number of items in the current folder
         * @param folderPath
         *            path of the current folder
         * @param status
         *            AVRCP response error/status code
         */
        public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                        String[] folderPath, int status) {
            if (DBG) Log.d(TAG, "onSetBrowsedPlayerRsp(" + target + ", " + numberOfItems + ", "
                            + folderPath + ", " + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onSetBrowsedPlayerRsp(target, numberOfItems,
                                    folderPath, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command ChangePath.
         *
         * @param target
         *            the Target device that sent back the response
         * @param direction
         *            {@link #CHANGE_PATH_DIRECTION_UP} or {@link #CHANGE_PATH_DIRECTION_DOWN}
         * @param numberOfItems
         *            number of items in the folder which has been changed to
         * @param status
         *            AVRCP response error/status code
         */
        public void onChangePathRsp(BluetoothDevice target, byte direction,
                        int numberOfItems, int status) {
            if (DBG) Log.d(TAG, "onChangePathRsp(" + target + ", " + direction + ", "
                            + numberOfItems + ", " + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onChangePathRsp(target, direction, numberOfItems, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetFolderItems.
         *
         * @param target
         *            the Target device that sent back the response
         * @param items
         *            list of items
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                        BluetoothAvrcpBrowseItem[] items, int status) {
            if (DBG) Log.d(TAG, "onGetFolderItemsRsp(" + target + ", " + scope + ", " + status
                            + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetFolderItemsRsp(target, scope, items, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command GetItemAttributes.
         *
         * @param target
         *            the Target device that sent back the response
         * @param attributes
         *            list of attribute ID
         * @param valueTexts
         *            list of value text
         * @param status
         *            AVRCP response error/status code
         */
        public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                        String[] valueTexts, int status) {
            if (isSuccess(status)){
                if (DBG) Log.d(TAG, "onGetItemAttributesRsp(" + target + ", "
                                + Arrays.toString(attributes) + ", "
                                + Arrays.toString(valueTexts) + ", " + status + ")");
            }
            else {
                if (DBG) Log.e(TAG, "onGetItemAttributesRsp Failed(" + target + ", "
                                + status + ")");
            }
            if (mEventHandler != null) {
                try {
                    mEventHandler.onGetItemAttributesRsp(target, attributes, valueTexts, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command Search.
         *
         * @param target
         *            the Target device that sent back the response
         * @param numberOfItems
         *            number of items found in the search
         * @param status
         *            AVRCP response error/status code
         */
        public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status) {
            if (DBG) Log.d(TAG, "onSearchRsp(" + target + ", " + numberOfItems + ", "
                            + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onSearchRsp(target, numberOfItems, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command PlayItem.
         *
         * @param target
         *            the Target device that sent back the response
         * @param status
         *            AVRCP response error/status code
         */
        public void onPlayItemRsp(BluetoothDevice target, int status) {
            if (DBG) Log.d(TAG, "onPlayItemRsp(" + target + ", " + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onPlayItemRsp(target, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns the response of command AddToNowPlaying.
         *
         * @param target
         *            the Target device that sent back the response
         * @param status
         *            AVRCP response error/status code
         */
        public void onAddToNowPlayingRsp(BluetoothDevice target, int status) {
            if (DBG) Log.d(TAG, "onAddToNowPlayingRsp(" + target + ", " + status + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onAddToNowPlayingRsp(target, status);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns PLAYBACK STATUS CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         * @param playStatus
         *            one of the PLAY_STATUS_
         */
        public void onPlaybackStatusChanged(BluetoothDevice target, byte playStatus) {
            if (DBG) Log.d(TAG, "onPlaybackStatusChanged(" + target + ", " + playStatus + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onPlaybackStatusChanged(target, playStatus);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns TRACK CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         * @param trackId
         *            index of the current track
         */
        public void onTrackChanged(BluetoothDevice target, long trackId) {
            if (DBG) Log.d(TAG, "onTrackChanged(" + target + ", " + trackId + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onTrackChanged(target, trackId);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns TRACK REACHED END event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         */
        public void onTrackReachedEnd(BluetoothDevice target) {
            if (DBG) Log.d(TAG, "onTrackReachedEnd(" + target + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onTrackReachedEnd(target);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns TRACK REACHED START event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         */
        public void onTrackReachedStart(BluetoothDevice target) {
            if (DBG) Log.d(TAG, "onTrackReachedStart(" + target + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onTrackReachedStart(target);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns PLAYBACK POSITION CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         * @param playbackPosition
         *            current playback position in millisecond
         */
        public void onPlaybackPositionChanged(BluetoothDevice target, int playbackPosition) {
            if (DBG) Log.d(TAG, "onTrackChanged(" + target + ", " + playbackPosition + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onPlaybackPositionChanged(target, playbackPosition);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns PLAYER APPLICATION SETTING CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         * @param attributes
         *            list of attribute ID
         * @param values
         *            list of new setting values
         */
        public void onPlayerAppSettingChanged(BluetoothDevice target,
                        byte[] attributes, byte[] values) {
            if (DBG) Log.d(TAG, "onPlayerAppSettingChanged(" + target + ", "
                            + Arrays.toString(attributes) + ", " + Arrays.toString(values) + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onPlayerAppSettingChanged(target, attributes, values);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns ADDRESSED PLAYER CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         * @param playerId
         *            Unique Media Player ID
         */
        public void onAddressedPlayerChanged(BluetoothDevice target, int playerId) {
            if (DBG) Log.d(TAG, "onAddressedPlayerChanged(" + target + ", " + playerId + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onAddressedPlayerChanged(target, playerId);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns AVAILABLE PLAYERS CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         */
        public void onAvailablePlayersChanged(BluetoothDevice target) {
            if (DBG) Log.d(TAG, "onAvailablePlayersChanged(" + target + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onAvailablePlayersChanged(target);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns NOW PLAYING CONTENT CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         */
        public void onNowPlayingContentChanged(BluetoothDevice target) {
            if (DBG) Log.d(TAG, "onNowPlayingContentChanged(" + target + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onNowPlayingContentChanged(target);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

        /**
         * Returns UIDS CHANGED event from target device.
         *
         * @param target
         *            the Target device that sent back the notification event
         */
        public void onUIDsChanged(BluetoothDevice target) {
            if (DBG) Log.d(TAG, "onUIDsChanged(" + target + ")");
            if (mEventHandler != null) {
                try {
                    mEventHandler.onUIDsChanged(target);
                } catch (Throwable t) {
                    Log.e(TAG, "", t);
                }
            }
        }

    };

    /**
     * Send a pass through command to target.
     * @hide
     * @param target
     *            Target device to send command to
     * @param command
     *            pass through command
     * @param buttonState
     *            button state (pressed/released)
     * @return false if this proxy object is
     *         not currently connected to the AVRCP CT service.
     */
    private boolean sendPassThroughCommand(BluetoothDevice target,
                        byte command, byte buttonState) {
        if (mService != null && isEnabled() && isValidDevice(target)) {
            try {
                return mService.sendPassThroughCommand(target, command, buttonState);
            } catch (RemoteException e) {
              Log.e(TAG, Log.getStackTraceString(new Throwable()));
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Check if Bluetooth is enabled.
     * @hide
     */
    private boolean isEnabled() {
        return (mAdapter.getState() == BluetoothAdapter.STATE_ON);
    }

    /**
     * Check if device address is valid.
     * @hide
     */
    private boolean isValidDevice(BluetoothDevice device) {
        if (device == null) return false;
        return BluetoothAdapter.checkBluetoothAddress(device.getAddress());
    }

    /**
     * Check if operation was successful from error/status code in command response.
     *
     * @param status
     *            AVRCP response error/status code
     * @return true/false
     */
    public static boolean isSuccess(int status) {
        return (status == STS_NO_ERROR);
    }


}
