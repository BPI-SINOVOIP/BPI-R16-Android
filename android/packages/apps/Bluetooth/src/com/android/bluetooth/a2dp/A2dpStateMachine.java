/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 Broadcom Corporation
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

/**
 * Bluetooth A2dp StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                         (Pending)
 *                           |    ^
 *                 CONNECTED |    | CONNECT
 *                           V    |
 *                        (Connected)
 */
package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.content.Intent;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.speech.RecognizerIntent;


import com.broadcom.bt.service.avrcp.BluetoothAVRCP;
import com.broadcom.bt.common.BluetoothConstants;

import android.content.res.Resources;
import android.widget.Toast;
import com.android.bluetooth.R;

final class A2dpStateMachine extends StateMachine {
    private static final String TAG = "A2dpStateMachine";
    private static final boolean DBG = true;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;

    //BRCM Enhancements: CPType ++
    static final int FETCH_CP   = 3;
    static final int CONFIGURE_CP = 4;
    //BRCM Enhancements: CPType --

    private static final int STACK_EVENT = 101;
    private static final int CONNECT_TIMEOUT = 201;

    private static final int AVRCP_HANDLER_EVENT_TIMOUT = 150;
    private static final int AVRCP_PLAY_POSITION_DEFAULT_DELAY = 2000;

    private Disconnected mDisconnected;
    private Pending mPending;
    private Connected mConnected;

    private A2dpService mService;
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private final AudioManager mAudioManager;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private final WakeLock mWakeLock;

    private int nCurrentRoleShortUUID;

    private static final int MSG_CONNECTION_STATE_CHANGED = 0;
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "com.broadcom.bt.avrcp_tg.profile.action.CONNECTION_STATE_CHANGED";

    // AUTOMOTIVE -- create separate a2dp sink & source
    // or have both coexist in one statemachine
    private static final ParcelUuid[] A2DP_SOURCE_UUIDS = {
        BluetoothUuid.AudioSource
    };

    private static final ParcelUuid[] A2DP_SINK_UUIDS = {
        BluetoothUuid.AudioSink
    };

    // mCurrentDevice is the device connected before the state changes
    // mTargetDevice is the device to be connected
    // mIncomingDevice is the device connecting to us, valid only in Pending state
    //                when mIncomingDevice is not null, both mCurrentDevice
    //                  and mTargetDevice are null
    //                when either mCurrentDevice or mTargetDevice is not null,
    //                  mIncomingDevice is null
    // Stable states
    //   No connection, Disconnected state
    //                  both mCurrentDevice and mTargetDevice are null
    //   Connected, Connected state
    //              mCurrentDevice is not null, mTargetDevice is null
    // Interim states
    //   Connecting to a device, Pending
    //                           mCurrentDevice is null, mTargetDevice is not null
    //   Disconnecting device, Connecting to new device
    //     Pending
    //     Both mCurrentDevice and mTargetDevice are not null
    //   Disconnecting device Pending
    //                        mCurrentDevice is not null, mTargetDevice is null
    //   Incoming connections Pending
    //                        Both mCurrentDevice and mTargetDevice are null
    private BluetoothDevice mCurrentDevice = null;
    private BluetoothDevice mTargetDevice = null;
    private BluetoothDevice mIncomingDevice = null;
    private BluetoothDevice mPlayingA2dpDevice = null;
    private BluetoothDevice mPlayingA2dpOffloadDevice = null;
    //BRCM RC Enhancement
    private BluetoothDevice mRCConnectedDevice = null;

    //BRCM Enhancements: CPType ++
    private static int mCPType = -1; /*By default CP TYPE is not enabled */
    //BRCM Enhancements: CPType --

    static {
        classInitNative();
    }

    private A2dpStateMachine(A2dpService svc, Context context) {
        super(TAG);

        Log.d(TAG, "Starting state machine");

        mService = svc;
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        initNative();

        mDisconnected = new Disconnected();
        mPending = new Pending();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mPending);
        addState(mConnected);

        setInitialState(mDisconnected);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothA2dpService");

        mIntentBroadcastHandler = new IntentBroadcastHandler();

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    }

    static A2dpStateMachine make(A2dpService svc, Context context) {
        Log.d("A2dpStateMachine", "make");
        A2dpStateMachine a2dpSm = new A2dpStateMachine(svc, context);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        if (mCurrentDevice != null) {
            // Stack takes care of this device disconenction
            Log.d(TAG,"A2dp profile service cleaning up with connected device="+mCurrentDevice);
            // Since this is cleanup send device connected to clients
            if (nCurrentRoleShortUUID == mService.A2DP_SINK_SERVICE_UUID) {
                mAudioManager.setBluetoothA2dpInputOn(false);
            }
            broadcastConnectionState(mCurrentDevice,
            BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_CONNECTED);
        }

        cleanupNative();
    }

        private class Disconnected extends State {
        @Override
        public void enter() {
            log("Enter Disconnected: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Disconnected process message: " + message.what);
            if (mCurrentDevice != null || mTargetDevice != null  || mIncomingDevice != null) {
                loge("ERROR: current, target, or mIncomingDevice not null in Disconnected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);

                    if (!connectA2dpNative(getByteAddress(device)) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                    // TODO(BT) remove CONNECT_TIMEOUT when the stack
                    //          sends back events consistently
                    sendMessageDelayed(CONNECT_TIMEOUT, 30000);
                    break;
                case DISCONNECT:
                    //RC Enhancement
                    if(mRCConnectedDevice != null) {
                        /* Special case to handle RC connected devices without A2DP Connected
                        in DISCONNECT state*/
                        BluetoothDevice tmpDevice = (BluetoothDevice) message.obj;
                        if (!mRCConnectedDevice.equals(tmpDevice)) {
                            break;
                        }
                        if (!mService.disconnectRcNative(getByteAddress(tmpDevice))) {
                            Log.e(TAG, " Failed to disconnected RC Device : "+tmpDevice);
                            break;
                        }
                    }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_RC_CONNECTION_STATE_CHANGED:
                            processRCConnectionStateChanged(event.valueInt, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        @Override
        public void exit() {
            log("Exit Disconnected: " + getCurrentMessage().what);
        }

        // in Disconnected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
            case CONNECTION_STATE_DISCONNECTED:
                logw("Ignore HF DISCONNECTED event, device: " + device);
                break;
            case CONNECTION_STATE_CONNECTING:
				/*
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpStateMachine.this) {
                        mIncomingDevice = device;
                        transitionTo(mPending);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                            AdapterService.PROFILE_CONN_REJECTED,BluetoothProfile.A2DP);
                    }
                }*/
                logi("Incoming A2DP accepted");
                broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                         BluetoothProfile.STATE_DISCONNECTED);
                synchronized (A2dpStateMachine.this) {
                    mIncomingDevice = device;
                    transitionTo(mPending);
                }
                break;
            case CONNECTION_STATE_CONNECTED:
                logw("A2DP Connected from Disconnected state");
				/*
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpStateMachine.this) {
                        mCurrentDevice = device;
                        transitionTo(mConnected);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                            AdapterService.PROFILE_CONN_REJECTED,BluetoothProfile.A2DP);
                    }
                }*/
				logi("Incoming A2DP accepted");
                broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                         BluetoothProfile.STATE_DISCONNECTED);
                synchronized (A2dpStateMachine.this) {
                    mCurrentDevice = device;
                    transitionTo(mConnected);
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                logw("Ignore HF DISCONNECTING event, device: " + device);
                break;
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }
    }

    private class Pending extends State {
        @Override
        public void enter() {
            log("Enter Pending: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Pending process message: " + message.what);

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    deferMessage(message);
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(CONNECTION_STATE_DISCONNECTED,
                                             getByteAddress(mTargetDevice));
                    break;
                case DISCONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice != null && mTargetDevice != null &&
                        mTargetDevice.equals(device) ) {
                        // cancel connection to the mTargetDevice
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpStateMachine.this) {
                            mTargetDevice = null;
                        }
                    } else {
                        deferMessage(message);
                    }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            removeMessages(CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_RC_CONNECTION_STATE_CHANGED:
                            processRCConnectionStateChanged(event.valueInt, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Pending state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_DISCONNECTING);
                        synchronized (A2dpStateMachine.this) {
                            mCurrentDevice = null;
                        }

                        if (mTargetDevice != null) {
                            if (!connectA2dpNative(getByteAddress(mTargetDevice))) {
                                broadcastConnectionState(mTargetDevice,
                                                         BluetoothProfile.STATE_DISCONNECTED,
                                                         BluetoothProfile.STATE_CONNECTING);
                                synchronized (A2dpStateMachine.this) {
                                    mTargetDevice = null;
                                    transitionTo(mDisconnected);
                                }
                            }
                        } else {
                            synchronized (A2dpStateMachine.this) {
                                mIncomingDevice = null;
                                transitionTo(mDisconnected);
                            }
                        }
                    }
                    else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                        // outgoing connection failed
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpStateMachine.this) {
                            mTargetDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                        broadcastConnectionState(mIncomingDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpStateMachine.this) {
                            mIncomingDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Unknown device Disconnected: " + device);
                    }
                    break;
            case CONNECTION_STATE_CONNECTED:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // disconnection failed
                    broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTING);
                    if (mTargetDevice != null) {
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                    }
                    synchronized (A2dpStateMachine.this) {
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpStateMachine.this) {
                        mCurrentDevice = mTargetDevice;
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    broadcastConnectionState(mIncomingDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpStateMachine.this) {
                        mCurrentDevice = mIncomingDevice;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                } else {
                    loge("Unknown device Connected: " + device);
                    // something is wrong here, but sync our state with stack
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpStateMachine.this) {
                        mCurrentDevice = device;
                        mTargetDevice = null;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                }
                break;
            case CONNECTION_STATE_CONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    log("current device tries to connect back");
                    // TODO(BT) ignore or reject
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    // The stack is connecting to target device or
                    // there is an incoming connection from the target device at the same time
                    // we already broadcasted the intent, doing nothing here
                    log("Stack and target device are connecting");
                }
                else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("Another connecting event on the incoming device");
                } else {
                    // We get an incoming connecting request while Pending
                    // TODO(BT) is stack handing this case? let's ignore it for now
                    log("Incoming connection while pending, ignore");
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // we already broadcasted the intent, doing nothing here
                    if (DBG) {
                        log("stack is disconnecting mCurrentDevice");
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    loge("TargetDevice is getting disconnected");
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("IncomingDevice is getting disconnected");
                } else {
                    loge("Disconnecting unknow device: " + device);
                }
                break;
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }

    }

    private class Connected extends State {
        @Override
        public void enter() {
            log("Enter Connected: " + getCurrentMessage().what);
            // Upon connected, the audio starts out as stopped
            broadcastAudioState(mCurrentDevice, BluetoothA2dp.STATE_NOT_PLAYING,
                                BluetoothA2dp.STATE_PLAYING);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connected process message: " + message.what);
            if (mCurrentDevice == null) {
                loge("ERROR: mCurrentDevice is null in Connected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice.equals(device)) {
                        break;
                    }

                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);
                    processAudioStateEvent(AUDIO_STATE_STOPPED, mCurrentDevice);
                    if (!disconnectA2dpNative(getByteAddress(mCurrentDevice))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                }
                    break;
                case DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    processAudioStateEvent(AUDIO_STATE_STOPPED, device);
                    broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING,
                                   BluetoothProfile.STATE_CONNECTED);
                    if (!disconnectA2dpNative(getByteAddress(device))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                       BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }
                    transitionTo(mPending);
                }
                    break;
                //BRCM enhancements: CPType ++
                case FETCH_CP:
                {
                    broadcastCPType(mPlayingA2dpDevice, mCPType);
                }
                    break;
                case CONFIGURE_CP:
                    int cp_type;
                    switch ((int)message.arg1)
                    {
                        case BluetoothA2dp.CP_TYPE_COPY_NEVER:
                            cp_type = 0;
                            break;
                        case BluetoothA2dp.CP_TYPE_COPY_ONCE:
                            cp_type = 1;
                            break;
                        case BluetoothA2dp.CP_TYPE_COPY_FREE:
                            cp_type = 2;
                            break;
                        default:
                            Log.e(TAG, "Unsupported CP TYPE");
                            return false;
                    }
                    if (!configureCPNative(cp_type))
                    {
                       Log.e(TAG, "Error configuring Content Protection Type");
                    }
                    break;
                //BRCM enhancements: CPType --
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) Log.d(TAG, "stack event: " + event.type);
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioStateEvent(event.valueInt, event.device);
                            break;
                        //BRCM RC Enhancements
                        case EVENT_TYPE_RC_CONNECTION_STATE_CHANGED:
                            processRCConnectionStateChanged(event.valueInt, event.device);
                            break;
                        //BRCM enhancements: CPType ++
                        case EVENT_TYPE_CP_TYPE:
                            processCPType(event.valueInt);
                            break;
                        //BRCM enhancements: CPType ++
                        default:
                            Log.e(TAG, "Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Connected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    if (mCurrentDevice.equals(device)) {
                        broadcastConnectionState(
                            mCurrentDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTED);
                        synchronized (A2dpStateMachine.this) {
                            mCurrentDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Disconnected from unknown device: " + device);
                    }
                    break;
              default:
                  loge("Connection State Device: " + device + " bad state: " + state);
                  break;
            }
        }
        private void processAudioStateEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                loge("Audio State Device:" + device + "is different from ConnectedDevice:" +
                                                           mCurrentDevice);
                return;
            }
            nCurrentRoleShortUUID = getA2dpProfileServiceUuid();
            Log.d(TAG, "processAudioStateEvent nCurrentRoleShortUUID = " + nCurrentRoleShortUUID +
                       " state = " + state);
            switch (state) {
                case AUDIO_STATE_STARTED:
                    if (mPlayingA2dpDevice == null) {
                       mPlayingA2dpDevice = device;

                       if (nCurrentRoleShortUUID == mService.A2DP_SINK_SERVICE_UUID) {
                           mAudioManager.setBluetoothA2dpInputOn(true);
                       }
                       broadcastAudioState(device, BluetoothA2dp.STATE_PLAYING,
                                           BluetoothA2dp.STATE_NOT_PLAYING);
                    }
                    break;

                case AUDIO_STATE_STOPPED:
                    if(mPlayingA2dpDevice != null) {
                        mPlayingA2dpDevice = null;

                        if (nCurrentRoleShortUUID == mService.A2DP_SINK_SERVICE_UUID) {
                            mAudioManager.setBluetoothA2dpInputOn(false);
                        }

                        broadcastAudioState(device, BluetoothA2dp.STATE_NOT_PLAYING,
                                            BluetoothA2dp.STATE_PLAYING);
                    }
                    else if (mPlayingA2dpOffloadDevice != null) {
                        mPlayingA2dpOffloadDevice = null;
                        mAudioManager.setParameters("a2dp_sink=off");
                    }
                    break;

                case AUDIO_STATE_STARTED_OFFLOAD:
                    if (mPlayingA2dpOffloadDevice == null) {
                       mPlayingA2dpOffloadDevice = device;

                       mAudioManager.setParameters("a2dp_sink=on");
                    }
                    break;

                default:
                  loge("Audio State Device: " + device + " bad state: " + state);
                  break;
            }
        }

        //BRCM enhancements: CPType ++
        private void processCPType(int cp_type)
        {
            log("processCPType: Current CP_TYPE is " + cp_type);
            // if cp_type < 0, CP is disabled. Otherwise the value represents the type of CP
            mCPType = cp_type;
        }
        //BRCM enhancements: CPType --
    }

    int getConnectionState(BluetoothDevice device) {
        if (getCurrentState() == mDisconnected) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }

        synchronized (this) {
            IState currentState = getCurrentState();
            if (currentState == mPending) {
                if ((mTargetDevice != null) && mTargetDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING;
                }
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_DISCONNECTING;
                }
                if ((mIncomingDevice != null) && mIncomingDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING; // incoming connection
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            if (currentState == mConnected) {
                if (mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTED;
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            } else {
                loge("Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (getCurrentState() == mConnected) {
                devices.add(mCurrentDevice);
            }
        }
        return devices;
    }

    boolean isPlaying(BluetoothDevice device) {
        synchronized(this) {
            if (device.equals(mPlayingA2dpDevice)) {
                return true;
            }
        }
        return false;
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        int priority = mService.getPriority(device);
        boolean ret = false;
        //check if this is an incoming connection in Quiet mode.
        if((adapterService == null) ||
           ((adapterService.isQuietModeEnabled() == true) &&
           (mTargetDevice == null))){
            ret = false;
        }
        // check priority and accept or reject the connection. if priority is undefined
        // it is likely that our SDP has not completed and peer is initiating the
        // connection. Allow this connection, provided the device is bonded
        else if((BluetoothProfile.PRIORITY_OFF < priority) ||
                ((BluetoothProfile.PRIORITY_UNDEFINED == priority) &&
                (device.getBondState() != BluetoothDevice.BOND_NONE))){
            ret= true;
        }
        return ret;
    }

    synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;

        /* Special case for handling RC connected device alone.*/
        if(mRCConnectedDevice != null && mCurrentDevice == null) {
            Log.d(TAG, "RC connected device without A2DP found");
            deviceList.add(mRCConnectedDevice);
            return deviceList;
        }

        Log.d(TAG, "getDevicesMatchingConnectionStates nCurrentRoleShortUUID = "
            + nCurrentRoleShortUUID);

        for (BluetoothDevice device : bondedDevices) {
            ParcelUuid[] featureUuids = device.getUuids();
            nCurrentRoleShortUUID = getA2dpProfileServiceUuid();

            ParcelUuid[] serviceUuids = (nCurrentRoleShortUUID == mService.A2DP_SINK_SERVICE_UUID) ?
                                        A2DP_SOURCE_UUIDS : A2DP_SINK_UUIDS;

            if (!BluetoothUuid.containsAnyUuid(featureUuids, serviceUuids)) {
                Log.d(TAG,"getDevicesMatchingConnectionStates Skiping device:"+ device);
                continue;
            }
            connectionState = getConnectionState(device);
            for(int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        return deviceList;
    }
    //BRCM RC Enhancements:
    private void processRCConnectionStateChanged(int state, BluetoothDevice device) {
            if(state == RC_CONNECTION_STATE_CONNECTED) {
                Log.d(TAG, "RC_CONNECTION_STATE_CONNECTED device:"+device);
                mRCConnectedDevice = device;
                broadcastRCConnectionState(mRCConnectedDevice,
                    BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED);
                broadcastRCConnectionState(mRCConnectedDevice,
                    BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
            }
            else if(state == RC_CONNECTION_STATE_DISCONNECTED) {
                Log.d(TAG, "RC_CONNECTION_STATE_DISCONNECTED device:"+device);
                broadcastRCConnectionState(device,BluetoothProfile.STATE_DISCONNECTING,
                    BluetoothProfile.STATE_CONNECTED);
                broadcastRCConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTING);
                mRCConnectedDevice = null;
            }
            else
                Log.e(TAG, "Invalid connect state for RC_CONN_STATE_CHANGED Event");
     }

    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device,
        int newState, int prevState) {
        int delay = mAudioManager.setBluetoothA2dpDeviceConnectionState(device, newState);

        mWakeLock.acquire();
        mIntentBroadcastHandler.sendMessageDelayed(mIntentBroadcastHandler.obtainMessage(
                                                        MSG_CONNECTION_STATE_CHANGED,
                                                        prevState,
                                                        newState,
                                                        device),
                                                        delay);
    }

    //BRCM RC Enhancement
    private void broadcastRCConnectionState(BluetoothDevice device,
        int newState, int prevState) {
        Intent intent = new Intent(ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        log("RC Connection state " + device + ": " + prevState + "->" + newState);
        mService.notifyProfileConnectionStateChanged(device, BluetoothConstants.PROFILE_ID_AVRCP,
            newState, prevState);
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, A2dpService.BLUETOOTH_PERM);

        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }
    //BRCM Enhancements: CPType ++
    private void broadcastCPType(BluetoothDevice device, int cp_type) {
        Intent intent = new Intent(BluetoothA2dp.ACTION_CP_TYPE);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        if (cp_type == 0) {
           intent.putExtra(BluetoothA2dp.EXTRA_CP_TYPE, BluetoothA2dp.CP_TYPE_COPY_NEVER);
        } else if (cp_type == 1) {
            intent.putExtra(BluetoothA2dp.EXTRA_CP_TYPE, BluetoothA2dp.CP_TYPE_COPY_ONCE);
        } else if (cp_type == 2) {
            intent.putExtra(BluetoothA2dp.EXTRA_CP_TYPE, BluetoothA2dp.CP_TYPE_COPY_FREE);
        } else {
            intent.putExtra(BluetoothA2dp.EXTRA_CP_TYPE, BluetoothA2dp.CP_TYPE_DISABLED);
        }
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, A2dpService.BLUETOOTH_PERM);

        log("A2DP Playing CP_TYPE : device: " + device + " cp_type:" + cp_type);
    }
    //BRCM Enhancements: CPType --
    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void onConnectionStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }
    //BRCM RC Enhancements
    protected void onRCConnectionStateChanged(int state, byte[] address){
        StackEvent event = new StackEvent(EVENT_TYPE_RC_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }
    //BRCM Enhancements: CPType ++
    private void onCPTypeCallback(boolean cp_enabled, int cp_type)
    {
        StackEvent event = new StackEvent(EVENT_TYPE_CP_TYPE);
        Log.d(TAG, "onCPTypeCallback cp_enabled:" + cp_enabled + " type:" + cp_type);
        /* if cp_enabled is false, the cp_type is -1, Otherwise it reflects the current CP_TYPE */
        event.valueInt = (cp_enabled == true) ? cp_type : -1;
        event.device = null;
        sendMessage(STACK_EVENT, event);
    }
    //BRCM Enhancements: CPType --

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }
    //JNI quiry the current a2dp service UUID to trigger connect or disconnect of the  current a2dp
    //service(source/sink)
    public int getA2dpProfileServiceUuid() {
        return mService.getA2dpProfileServiceUuid();
    }

    public boolean isRcDeviceConnected(BluetoothDevice device) {
        if(device == null || mRCConnectedDevice == null)
            return false;
        if(mRCConnectedDevice.equals(device))
            return true;
        return false;
    }

    private class StackEvent {
        int type = EVENT_TYPE_NONE;
        int valueInt = 0;
        BluetoothDevice device = null;

        private StackEvent(int type) {
            this.type = type;
        }
    }
    /** Handles A2DP connection state change intent broadcasts. */
    private class IntentBroadcastHandler extends Handler {

        private void onConnectionStateChanged(BluetoothDevice device,
                int prevState, int state) {
            Intent intent = new Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
            log("Connection state " + device + ": " + prevState + "->" + state);
            mService.notifyProfileConnectionStateChanged(device,
                BluetoothProfile.A2DP, state, prevState);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECTION_STATE_CHANGED:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    mWakeLock.release();
                    break;
            }
        }
    }


    // Event types for STACK_EVENT message
    final private static int EVENT_TYPE_NONE = 0;
    final private static int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    final private static int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    //BRCM Enhancements: CPType ++
    final private static int EVENT_TYPE_CP_TYPE = 3;
    //BRCM Enhancements: CPType --
    //BRCM RC Enhancements:
    final private static int EVENT_TYPE_RC_CONNECTION_STATE_CHANGED = 4;

   // Do not modify without updating the HAL bt_av.h and bt_avk.h files.

    // match up with btav_connection_state_t enum of bt_av.h
    // and btavk_connection_state_t enum of bt_avk.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_DISCONNECTING = 3;

    // match up with btav_audio_state_t enum of bt_av.h
    // and btavk_audio_state_t enum of bt_avk.h
    final static int AUDIO_STATE_REMOTE_SUSPEND = 0;
    final static int AUDIO_STATE_STOPPED = 1;
    final static int AUDIO_STATE_STARTED = 2;
    final static int AUDIO_STATE_STARTED_OFFLOAD = 3;

    //BRCM RC Enhancement
    // match up with btrc_connection_state_t enum of bt_rc.h
    final static int RC_CONNECTION_STATE_DISCONNECTED = 0;
    final static int RC_CONNECTION_STATE_CONNECTED = 1;

    private native static void classInitNative();
    private native void initNative();
    private native void cleanupNative();
    private native boolean connectA2dpNative(byte[] address);
    private native boolean disconnectA2dpNative(byte[] address);
    //BRCM Enhancements: CPType ++
    private native boolean configureCPNative(int cp_type);
    //BRCM Enhancements: CPType --


}
