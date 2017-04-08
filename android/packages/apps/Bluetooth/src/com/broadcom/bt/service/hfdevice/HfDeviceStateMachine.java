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

/**
 * Bluetooth Handset StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                         (Pending)
 *                           |    ^
 *                 CONNECTED |    | CONNECT
 *                           V    |
 *                        (Connected)
 *                           |    ^
 *             CONNECT_AUDIO |    | DISCONNECT_AUDIO
 *                           V    |
 *                         (AudioOn)
 */
package com.broadcom.bt.service.hfdevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth;
import android.content.Context;
import android.media.AudioManager;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.util.Log;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;

import com.broadcom.bt.hfdevice.BluetoothHfDevice;
import com.broadcom.bt.hfdevice.BluetoothClccInfo;
import com.broadcom.bt.hfdevice.BluetoothHfDevice.PeerHfFeatures;


final class HfDeviceStateMachine extends StateMachine {
    private static final String TAG = "HfDeviceStateMachine";
    private static final boolean DBG = true;
    // For Debugging only
    private static int sRefCount = 0;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    static final int CONNECT_AUDIO = 3;
    static final int DISCONNECT_AUDIO = 4;
    static final int VOICE_RECOGNITION_START = 5;
    static final int VOICE_RECOGNITION_STOP = 6;

    static final int SET_VOLUME = 7;
    static final int DIAL = 8;
    static final int REDIAL = 9;
    static final int HANGUP = 11;
    static final int HOLD = 12;
    static final int SEND_AT_DTMF = 13;
    static final int ANSWER = 14;
    static final int SEND_VND_CMD = 15;
    static final int READ_PHONE_BOOK = 16;
    static final int AT_CLCC = 17;
    static final int QUERY_OPERATOR_SELECTION_INFO = 18;
    static final int QUERY_SUBSCRIBER_INFO = 19;
    static final int SEND_KEY_PRESS_EVENT = 20;
    static final int SEND_BIA_EVENT = 21;

    // Indicator field flags for message passing
    public static final int ROAM    =  0x0001;
    public static final int SERVICE =  0x0002;
    public static final int SIGNAL  =  0x0004;
    public static final int BATTERY =  0x0008;

    private static final int STACK_EVENT = 101;


    private static final int CONNECT_TIMEOUT = 201;

    /* Flag mask to disable to the inband ring tone */
    private static final int INBAND_DISABLE =  0xfff7;



    private static final ParcelUuid[] AG_UUIDS = { BluetoothUuid.HSP_AG,
            BluetoothUuid.Handsfree_AG, };

    private Disconnected mDisconnected;
    private Pending mPending;
    private Connected mConnected;
    private AudioOn mAudioOn;

    private HfDeviceService mService;

    private boolean mVoiceRecognitionStarted = false;
    private int mPendingVrEvent = -1;

    private HfDeviceStateData mDeviceStateData;
    private int mAudioState;
    private BluetoothAdapter mAdapter;
    private boolean mNativeAvailable;
    private List<BluetoothClccInfo> mClccInfo = new ArrayList<BluetoothClccInfo>();
    private boolean isClccReqInProcess = false;
    private boolean isPendingClcc = false;
    private PhoneBookAtCommandHandler mPhoneBookAtCommandHandler = null;

    private int mPeerFeatures = 0;
    private int mLocalFeatures = 0;

    private AudioManager mAudioManager;
    private boolean hasAudioFocused = false;

    public boolean isHspConnection () {
        if (!isConnected())
            return false;

        if (mPeerFeatures == 0)
            return true;
        else
            return false;
    }

    public boolean isPhoneBookAtHandlerBusy() {
        return mPhoneBookAtCommandHandler.isBusy();
    }
    public void setHfFeatures(int locatFeatures, int peerFeatures) {
        mPeerFeatures = locatFeatures;
        mLocalFeatures = peerFeatures;
    }

    public int getLocalFeatures() {
        return mLocalFeatures;
    }

    public int getPeerFeatures() {
        return mPeerFeatures;
    }

    public boolean canStartVR(){
        return (mVoiceRecognitionStarted == false) &&
                (mPendingVrEvent != BluetoothHfDevice.VR_STATE_ACTIVE);
    }

    public boolean canStopVR(){
        return (mVoiceRecognitionStarted == false) &&
                (mPendingVrEvent != BluetoothHfDevice.VR_STATE_INACTIVE);
    }


    // mCurrentDevice is the device connected before the state changes
    // mTargetDevice is the device to be connected
    // mIncomingDevice is the device connecting to us, valid only in Pending
    // state
    // when mIncomingDevice is not null, both mCurrentDevice
    // and mTargetDevice are null
    // when either mCurrentDevice or mTargetDevice is not null,
    // mIncomingDevice is null
    // Stable states
    // No connection, Disconnected state
    // both mCurrentDevice and mTargetDevice are null
    // Connected, Connected state
    // mCurrentDevice is not null, mTargetDevice is null
    // Interim states
    // Connecting to a device, Pending
    // mCurrentDevice is null, mTargetDevice is not null
    // Disconnecting device, Connecting to new device
    // Pending
    // Both mCurrentDevice and mTargetDevice are not null
    // Disconnecting device Pending
    // mCurrentDevice is not null, mTargetDevice is null
    // Incoming connections Pending
    // Both mCurrentDevice and mTargetDevice are null
    private BluetoothDevice mCurrentDevice = null;
    private BluetoothDevice mTargetDevice = null;
    private BluetoothDevice mIncomingDevice = null;

    static {
        classInitNative();
    }

    private HfDeviceStateMachine(HfDeviceService context) {
        super(TAG);
        mService = context;
        mVoiceRecognitionStarted = false;

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mAudioState = BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        initializeNative();
        mNativeAvailable = true;

        mDisconnected = new Disconnected();
        mPending = new Pending();
        mConnected = new Connected();
        mAudioOn = new AudioOn();


        addState(mDisconnected);
        addState(mPending);
        addState(mConnected);
        addState(mAudioOn);

        mPhoneBookAtCommandHandler =  new PhoneBookAtCommandHandler();

        setInitialState(mDisconnected);
    }

    static HfDeviceStateMachine make(HfDeviceService context) {
        Log.d(TAG, "make");
        HfDeviceStateMachine hssm = new HfDeviceStateMachine(context);
        hssm.start();
        return hssm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {

        if (isConnected()) {
            // Since this is cleanup send device connected to clients
            mService.broadcastConnectionState(BluetoothHfDevice.ERROR_AG_FAILURE,
                    mCurrentDevice,BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_CONNECTED, 0, 0);
        }

        Log.d(TAG,"cleanup");
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable = false;
        }
    }

    private class Disconnected extends State {

        public void enter() {
            log("Enter Disconnected: " + getCurrentMessage().what);
            mDeviceStateData = new HfDeviceStateData();
            mPhoneBookAtCommandHandler.cleanup();
            mPendingVrEvent = -1;
            mVoiceRecognitionStarted = false;
            checkAndAbandonAudioFocus(true);
        }

        public boolean processMessage(Message message) {{
            log("Disconnected process message: " + message.what);
            if (mCurrentDevice != null || mTargetDevice != null || mIncomingDevice != null) {
                Log.e(TAG, "ERROR: current, target, or mIncomingDevice not null in Disconnected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR, device,
                            BluetoothProfile.STATE_CONNECTING,
                            BluetoothProfile.STATE_DISCONNECTED,
                            0, 0);

                    if (!connectNative(getByteAddress(device)) ) {
                        mService.broadcastConnectionState(BluetoothHfDevice.ERROR_AG_FAILURE,
                                device,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTING,
                                0, 0);
                        break;
                    }

                    synchronized (HfDeviceStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                    // TODO(BT) remove CONNECT_TIMEOUT when the stack
                    //          sends back events consistently
                    sendMessageDelayed(CONNECT_TIMEOUT, 30000);
                    break;
                case DISCONNECT:
                    // ignore
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        log("event type: " + event.type);
                    }
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device,
                                                    event.valueInt2, event.valueInt3);
                            break;
                        default:
                            Log.e(TAG, "Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }}


        public void exit() {
            log("Exit Disconnected: " + getCurrentMessage().what);
        }

        // in Disconnected state
        private void processConnectionEvent(int state, BluetoothDevice device,
                                                    int peerFeatures, int localFeatures) {
            switch (state) {
            case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTED:
                Log.d(TAG, "Ignore HF DISCONNECTED event, device: " + device);
                break;
            case HfDeviceHalConstants.CONNECTION_STATE_CONNECTING:
                if (okToConnect(device)){
                    Log.i(TAG,"Incoming Ag accepted");
                    mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                             device, BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTED,
                                             peerFeatures, localFeatures);
                    synchronized (HfDeviceStateMachine.this) {
                        mIncomingDevice = device;
                        transitionTo(mPending);
                    }
                } else {
                    Log.i(TAG,"Incoming Ag rejected. priority=" + mService.getPriority(device)+
                              " bondState=" + device.getBondState());
                    //reject the connection and stay in Disconnected state itself
                    disconnectNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if ( adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED,
                                                           BluetoothProfile.HF_DEVICE);
                    }
                }
                break;
            case HfDeviceHalConstants.CONNECTION_STATE_CONNECTED:
                Log.w(TAG, "HFP Connected from Disconnected state");
                if (okToConnect(device)){
                    // Peer features and Local features will be availble after SLC connection
                    Log.i(TAG,"Incoming Ag accepted waiting for SLC establishment");
                    mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                            device, BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTED,
                                             peerFeatures, localFeatures);
                    synchronized (HfDeviceStateMachine.this) {
                        mIncomingDevice = device;
                        if(getCurrentState() != mPending)
                            transitionTo(mPending);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    Log.i(TAG,"Incoming Ag rejected. priority=" + mService.getPriority(device) +
                              " bondState=" + device.getBondState());
                    disconnectNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if ( adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED,
                                                           BluetoothProfile.HF_DEVICE);
                    }
                }

                break;
            case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTING:
                Log.d(TAG, "Ignore HF DISCONNECTING event, device: " + device);
                break;
            default:
                Log.e(TAG, "Incorrect state: " + state);
                break;
            }
        }
    }

    private class Pending extends State {

        public void enter() {
            log("Enter Pending: " + getCurrentMessage().what);
        }


        public boolean processMessage(Message message) {
            log("Pending process message: " + message.what);

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                case CONNECT_AUDIO:
                    deferMessage(message);
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTED,
                                             getByteAddress(mTargetDevice), 0, 0);
                    break;
                case DISCONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice != null && mTargetDevice != null &&
                        mTargetDevice.equals(device) ) {
                        // cancel connection to the mTargetDevice
                        mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR, device,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_CONNECTING,
                                0, 0);

                        synchronized (HfDeviceStateMachine.this) {
                            mTargetDevice = null;
                        }
                    } else {
                        deferMessage(message);
                    }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        log("event type: " + event.type);
                    }
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            removeMessages(CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device,
                                                    event.valueInt2, event.valueInt3);
                            break;
                        // Stack now sends the event before SLC connection.
                        // TO DO stack to send the event after SLC connection is done.
                        case EVENT_TYPE_DEVICE_STATUS_STATE_CHANGED:
                            processDeviceStateChanged(event.valueInt, event.valueInt2,
                                                event.valueInt3, event.valueInt4);
                            break;
                        case EVENT_TYPE_CALL_STATE_CHANGED:
                            processCallState(event.valueInt, event.valueInt2, event.valueInt3);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_WBS_STATE_CHANGED:
                            processWBSEvent(event.valueInt);
                            break;
                        case EVENT_TYPE_RING:
                            processRingEvent();
                            break;

                        case EVENT_TYPE_IN_BAND_RING_STATUS:
                            processInBandRingStatusEvent(event.valueInt);

                        case EVENT_BIA_STATUS:
                            processBIAStatusEvent(event.valueInt);

                            break;

                        default:
                            Log.e(TAG, "Unexpected event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Pending state
        private void processConnectionEvent(int state, BluetoothDevice device,
                                                 int peerFeatures, int localFeatures) {
               switch (state) {
                    case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTED:
                        processWBSEvent(0); /* disable WBS audio parameters */
                        if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {

                            mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                    mCurrentDevice,
                                    BluetoothProfile.STATE_DISCONNECTED,
                                    BluetoothProfile.STATE_DISCONNECTING,
                                    peerFeatures, localFeatures);

                            synchronized (HfDeviceStateMachine.this) {
                                mCurrentDevice = null;
                            }

                            if (mTargetDevice != null) {
                                if (!connectNative(getByteAddress(mTargetDevice))) {

                                    mService.broadcastConnectionState(
                                            BluetoothHfDevice.ERROR_AG_FAILURE,
                                            mTargetDevice,
                                            BluetoothProfile.STATE_DISCONNECTED,
                                            BluetoothProfile.STATE_CONNECTING,
                                            0, 0);


                                    synchronized (HfDeviceStateMachine.this) {
                                        mTargetDevice = null;
                                        transitionTo(mDisconnected);
                                    }
                                }
                            } else {
                                synchronized (HfDeviceStateMachine.this) {
                                    mIncomingDevice = null;
                                    transitionTo(mDisconnected);
                                }
                            }
                        } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                            // outgoing connection failed


                            mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                    mTargetDevice,
                                    BluetoothProfile.STATE_DISCONNECTED,
                                    BluetoothProfile.STATE_CONNECTING,
                                    peerFeatures, localFeatures);

                            synchronized (HfDeviceStateMachine.this) {
                                mTargetDevice = null;
                                transitionTo(mDisconnected);
                            }
                        } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {

                            mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                    mIncomingDevice,
                                    BluetoothProfile.STATE_DISCONNECTED,
                                    BluetoothProfile.STATE_CONNECTING,
                                    peerFeatures, localFeatures);

                            synchronized (HfDeviceStateMachine.this) {
                                mIncomingDevice = null;
                                transitionTo(mDisconnected);
                            }
                        } else {
                            Log.e(TAG, "Unknown device Disconnected: " + device);
                        }
                        break;
                case HfDeviceHalConstants.CONNECTION_STATE_CONNECTED:
                    // device connected but wait for SLC connected to send to app
                    Log.d(TAG, "Connected but waiting for SLC connection : " + device);
                            break;

                case HfDeviceHalConstants.CONNECTION_STATE_SLC_CONNECTED:
                    processWBSEvent(0); /* disable WBS audio parameters */
                    if (HfDeviceHalConstants.INBAND == (HfDeviceHalConstants.INBAND & mPeerFeatures)) {
                        processInBandRingStatusEvent(BluetoothHfDevice.INBAND_STATE_ON);
                    }

                    if (mTargetDevice != null && mTargetDevice.equals(device)) {

                        mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                mTargetDevice,
                                BluetoothProfile.STATE_CONNECTED,
                                BluetoothProfile.STATE_CONNECTING,
                                peerFeatures, localFeatures);

                        synchronized (HfDeviceStateMachine.this) {
                            mCurrentDevice = mTargetDevice;
                            mTargetDevice = null;
                            transitionTo(mConnected);
                        }
                    } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {

                        mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR,
                                mIncomingDevice,
                                BluetoothProfile.STATE_CONNECTED,
                                BluetoothProfile.STATE_CONNECTING,
                                peerFeatures, localFeatures);

                        synchronized (HfDeviceStateMachine.this) {
                            mCurrentDevice = mIncomingDevice;
                            mIncomingDevice = null;
                            transitionTo(mConnected);
                        }
                    } else {
                        Log.e(TAG, "Unknown device Connected: " + device);
                        // wrong state transitiion
                        }

                    break;
                case HfDeviceHalConstants.CONNECTION_STATE_CONNECTING:
                    if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                        log("current device tries to connect back");
                        // TODO(BT) ignore or reject
                    } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                        // The stack is connecting to target device or
                        // there is an incoming connection from the target device at the same time
                        // we already broadcasted the intent, doing nothing here
                        if (DBG) {
                            log("Stack and target device are connecting");
                        }
                    }
                    else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                        Log.e(TAG, "Another connecting event on the incoming device");
                    } else {
                        // We get an incoming connecting request while Pending
                        // TODO(BT) is stack handing this case? let's ignore it for now
                        log("Incoming connection while pending, ignore");
                    }
                    break;
                case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTING:
                    if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                        // we already broadcasted the intent, doing nothing here
                        if (DBG) {
                            log("stack is disconnecting mCurrentDevice");
                        }
                    } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                        Log.e(TAG, "TargetDevice is getting disconnected");
                    } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                        Log.e(TAG, "IncomingDevice is getting disconnected");
                    } else {
                        Log.e(TAG, "Disconnecting unknow device: " + device);
                    }
                    break;
                default:
                    Log.e(TAG, "Incorrect state: " + state);
                    break;
                }
            }

        // in Pending state
        private void processAudioEvent(int state, BluetoothDevice device) {

            Log.d(TAG, "processAudioEvent: state=" + state + " mAudioState=" + mAudioState);
            switch (state) {
                case HfDeviceHalConstants.AUDIO_STATE_CONNECTED:
                    mAudioState = BluetoothHfDevice.STATE_AUDIO_CONNECTED;
                    checkandRequestAudioFocus();
                    mAudioManager.setBluetoothScoOn(true);
                    mService.broadcastAudioState(device, BluetoothHfDevice.STATE_AUDIO_CONNECTED,
                        BluetoothHfDevice.STATE_AUDIO_CONNECTING);
                    // Since audio connection happens in pending state,
                    // wait for SLC connection to happen and then transition to mAudioOn
                    break;
                case HfDeviceHalConstants.AUDIO_STATE_CONNECTING:
                    mAudioState = BluetoothHfDevice.STATE_AUDIO_CONNECTING;
                    mService.broadcastAudioState(device, BluetoothHfDevice.STATE_AUDIO_CONNECTING,
                        BluetoothHfDevice.STATE_AUDIO_DISCONNECTED);
                    break;
                default:
                    Log.e(TAG, "Audio State Device: " + device + " bad state: " + state);
                    break;
            }

        }
    }

    AudioManager.OnAudioFocusChangeListener afChangeListener =
                    new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
        Log.d(TAG,"onAudioFocusChange"+focusChange);
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
                hasAudioFocused = false;
                if(isInCall())
                    checkandRequestAudioFocus();
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                hasAudioFocused = true;
                // nothing to do
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                hasAudioFocused = false;
                if(isInCall())
                    checkandRequestAudioFocus();
            }
        }
    };

    private class Connected extends State {

        public void enter() {
            log("Enter Connected: " + getCurrentMessage().what);
            // If audio connection has happened during pending state itself
            if (mAudioState == BluetoothHfDevice.STATE_AUDIO_CONNECTED)
                transitionTo(mAudioOn);
        }

        public boolean processMessage(Message message) {
            log("Connected process message: " + message.what);
            if (DBG) {
                if (mCurrentDevice == null) {
                    log("ERROR: mCurrentDevice is null in Connected");
                    return NOT_HANDLED;
                }
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                {
                    log("ERROR: device is already connected ");
                }
                    break;
                case DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR, device,
                            BluetoothProfile.STATE_DISCONNECTING,
                            BluetoothProfile.STATE_CONNECTED,
                            0, 0);

                    if (!disconnectNative(getByteAddress(device))) {
                        mService.broadcastConnectionState(BluetoothHfDevice.ERROR_AG_FAILURE, device,
                                 BluetoothProfile.STATE_CONNECTED,
                                 BluetoothProfile.STATE_DISCONNECTED,
                                 0, 0);
                        break;
                    }
                    transitionTo(mPending);
                }
                    break;
                case CONNECT_AUDIO:
                     connectAudioNative(getByteAddress(mCurrentDevice));
                     break;
                case QUERY_OPERATOR_SELECTION_INFO:
                    if (!sendCopsNative()) {
                         mService.broadcastOperatorSelectionEvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                        0, null);
                    }
                    break;
                case QUERY_SUBSCRIBER_INFO:
                    if (!sendCnumNative()) {
                         mService.broadcastSubscriberInfoEvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    null, -1);
                    }
                    break;
                case VOICE_RECOGNITION_START:
                    if (!startVoiceRecognitionNative()) {
                         mService.broadcastVREvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.VR_STATE_INACTIVE);
                    }
                    mPendingVrEvent = BluetoothHfDevice.VR_STATE_ACTIVE;
                    break;
                case VOICE_RECOGNITION_STOP:
                    if (!stopVoiceRecognitionNative()) {
                         mService.broadcastVREvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.VR_STATE_ACTIVE);
                    }
                    mPendingVrEvent = BluetoothHfDevice.VR_STATE_INACTIVE;
                    break;
                case SET_VOLUME:
                    if (!setVolumeNative((int) message.arg1, (int) message.arg2)) {
                        Log.e(TAG," Set volume failed");
                    }
                    break;
                case ANSWER:
                case HANGUP:
                case DIAL:
                case REDIAL:
                case HOLD:
                case AT_CLCC:
                case SEND_AT_DTMF:
                    handleCallEvents(message);
                    break;
                case SEND_VND_CMD:
                    if (!sendVndATCmdNative((String) message.obj)) {
                        mService.broadcastVndCmdEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
                    }
                    break;
                case READ_PHONE_BOOK:
                    if (!mPhoneBookAtCommandHandler.
                            startDownload(mService, HfDeviceStateMachine.this,
                            (String) message.obj, message.arg1)) {
                        mService.broadcastonPhoneBookReadEvent(
                            BluetoothHfDevice.ERROR_AG_FAILURE, null);
                    }
                    break;
                case SEND_KEY_PRESS_EVENT:
                     sendKeyPressEventNative();
                     break;
                case SEND_BIA_EVENT:
                     handleSendBIA((Integer) message.obj);
                     break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                        log("event type: " + event.type);
                    }
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device,
                                                    event.valueInt2, event.valueInt3);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_CALL_STATE_CHANGED:
                            processCallState(event.valueInt, event.valueInt2, event.valueInt3);
                            break;
                        case EVENT_TYPE_DEVICE_STATUS_STATE_CHANGED:
                            processDeviceStateChanged(event.valueInt, event.valueInt2,
                                                event.valueInt3, event.valueInt4);
                            break;
                        case EVENT_TYPE_CLIP_AVAILABLE:
                            processClipEvent(event.valueString);
                            break;
                        case EVENT_TYPE_CALL_WAITING:
                            processCallWaitingEvent(event.valueString);
                            break;
                        case EVENT_TYPE_AT_CLCC:
                            processAtClcc(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_VENDOR_AT:
                            processVndATCmdEvent(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_AT_CNUM:
                            processAtCnum(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_AT_COPS:
                            processAtCops(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_VR_STATE_CHANGED:
                            processVREvent(event.valueInt, event.valueInt2);
                            break;
                        case EVENT_TYPE_VOLUME_CHANGED:
                            processVolumeEvent(event.valueInt, event.valueInt2);
                            break;

                        case EVENT_TYPE_WBS_STATE_CHANGED:
                            processWBSEvent(event.valueInt);
                            break;
                        case EVENT_TYPE_RING:
                            processRingEvent();
                            break;

                        case EVENT_TYPE_IN_BAND_RING_STATUS:
                            processInBandRingStatusEvent(event.valueInt);

                        case EVENT_BIA_STATUS:
                            processBIAStatusEvent(event.valueInt);

                            break;

                        default:
                            Log.e(TAG, "Unknown stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Connected state
        private void processConnectionEvent(int state, BluetoothDevice device,
                                                int peerFeatures, int localFeatures) {
            switch (state) {
                case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTED:
                    processWBSEvent(0); /* disable WBS audio parameters */
                    if (mCurrentDevice.equals(device)) {

                        mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR, device,
                                 BluetoothProfile.STATE_DISCONNECTED,
                                 BluetoothProfile.STATE_CONNECTED,
                                 peerFeatures, localFeatures);

                        synchronized (HfDeviceStateMachine.this) {
                            mCurrentDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        Log.e(TAG, "Disconnected from unknown device: " + device);
                    }
                    break;
              default:
                  Log.e(TAG, "Connection State Device: " + device + " bad state: " + state);
                  break;
            }
        }

        // in Connected state
        private void processAudioEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                Log.e(TAG, "Audio changed on disconnected device: " + device);
                return;
            }

            Log.d(TAG, "processAudioEvent: state=" + state + " mAudioState=" + mAudioState);
            switch (state) {
                case HfDeviceHalConstants.AUDIO_STATE_CONNECTED:
                    mAudioState = BluetoothHfDevice.STATE_AUDIO_CONNECTED;
                    checkandRequestAudioFocus();
                    mAudioManager.setBluetoothScoOn(true);
                    mService.broadcastAudioState(device, BluetoothHfDevice.STATE_AUDIO_CONNECTED,
                        BluetoothHfDevice.STATE_AUDIO_CONNECTING);
                    transitionTo(mAudioOn);
                    break;
                case HfDeviceHalConstants.AUDIO_STATE_CONNECTING:
                    mAudioState = BluetoothHfDevice.STATE_AUDIO_CONNECTING;
                    mService.broadcastAudioState(device, BluetoothHfDevice.STATE_AUDIO_CONNECTING,
                        BluetoothHfDevice.STATE_AUDIO_DISCONNECTED);
                    break;
                default:
                    Log.e(TAG, "Audio State Device: " + device + " bad state: " + state);
                    break;
            }
         }
    }

    private class AudioOn extends State {

        public void enter() {
            log("Enter AudioOn: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("AudioOn process message: " + message.what);
            if (DBG) {
                if (mCurrentDevice == null) {
                    log("ERROR: mCurrentDevice is null in AudioOn");
                    return NOT_HANDLED;
                }
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    deferMessage(obtainMessage(DISCONNECT, message.obj));
                }
                // fall through
                case DISCONNECT_AUDIO:
                    if (!disconnectAudioNative(getByteAddress(mCurrentDevice))) {
                        Log.w(TAG, "disconnectAudioNative failed");
                        mAudioState = BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
                        mAudioManager.setBluetoothScoOn(false);
                        mService.broadcastAudioState(mCurrentDevice,
                            BluetoothHfDevice.STATE_AUDIO_DISCONNECTED,
                            BluetoothHfDevice.STATE_AUDIO_CONNECTED);
                    }
                    break;
                case QUERY_OPERATOR_SELECTION_INFO:
                    if (!sendCopsNative()) {
                         mService.broadcastOperatorSelectionEvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                        0, null);
                    }
                    break;
                case QUERY_SUBSCRIBER_INFO:
                    if (!sendCnumNative()) {
                         mService.broadcastSubscriberInfoEvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    null, -1);
                    }
                    break;
                case VOICE_RECOGNITION_START:
                    if (!startVoiceRecognitionNative()) {
                         mService.broadcastVREvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.VR_STATE_INACTIVE);
                    }
                    mPendingVrEvent = BluetoothHfDevice.VR_STATE_ACTIVE;
                    break;
                case VOICE_RECOGNITION_STOP:
                    if (!stopVoiceRecognitionNative()) {
                         mService.broadcastVREvent(
                                        BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.VR_STATE_ACTIVE);
                    }
                    mPendingVrEvent = BluetoothHfDevice.VR_STATE_INACTIVE;
                    break;
                case ANSWER:
                case HANGUP:
                case DIAL:
                case REDIAL:
                case HOLD:
                case AT_CLCC:
                case SEND_AT_DTMF:
                    handleCallEvents(message);
                    break;
                case SEND_VND_CMD:
                    if (!sendVndATCmdNative((String) message.obj)) {
                        mService.broadcastVndCmdEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
                    }
                    break;
                case SET_VOLUME:
                    if (!setVolumeNative((int) message.arg1, (int) message.arg2)) {
                        Log.e(TAG," Set volume failed");
                    }
                    break;
                case READ_PHONE_BOOK:
                    if (!mPhoneBookAtCommandHandler.
                            startDownload(mService, HfDeviceStateMachine.this,
                            (String) message.obj, message.arg1)) {
                        mService.broadcastonPhoneBookReadEvent(
                            BluetoothHfDevice.ERROR_AG_FAILURE, null);
                    }
                    break;
                case SEND_KEY_PRESS_EVENT:
                     sendKeyPressEventNative();
                     break;
                case SEND_BIA_EVENT:
                  handleSendBIA((Integer) message.obj);
                  break;

                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    if (DBG) {
                      log("event type: " + event.type);
                    }

                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device,
                                                    event.valueInt2, event.valueInt3);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_CALL_STATE_CHANGED:
                            processCallState(event.valueInt, event.valueInt2, event.valueInt3);
                            break;
                        case EVENT_TYPE_DEVICE_STATUS_STATE_CHANGED:
                            processDeviceStateChanged(event.valueInt, event.valueInt2,
                                                event.valueInt3, event.valueInt4);
                            break;
                        case EVENT_TYPE_CLIP_AVAILABLE:
                            processClipEvent(event.valueString);
                            break;
                        case EVENT_TYPE_CALL_WAITING:
                            processCallWaitingEvent(event.valueString);
                            break;
                        case EVENT_TYPE_AT_CLCC:
                            processAtClcc(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_AT_CNUM:
                            processAtCnum(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_AT_COPS:
                            processAtCops(event.valueInt, event.valueString);
                            break;

                        case EVENT_TYPE_VENDOR_AT:
                            processVndATCmdEvent(event.valueInt, event.valueString);
                            break;
                        case EVENT_TYPE_VOLUME_CHANGED:
                            processVolumeEvent(event.valueInt, event.valueInt2);
                            break;
                        case EVENT_TYPE_VR_STATE_CHANGED:
                            processVREvent(event.valueInt, event.valueInt2);
                            break;

                        case EVENT_TYPE_WBS_STATE_CHANGED:
                            processWBSEvent(event.valueInt);
                            break;

                        case EVENT_TYPE_RING:
                            processRingEvent();
                            break;

                        case EVENT_TYPE_IN_BAND_RING_STATUS:
                            processInBandRingStatusEvent(event.valueInt);

                        case EVENT_BIA_STATUS:
                            processBIAStatusEvent(event.valueInt);
                            break;

                        default:
                            Log.e(TAG, "Unknown stack event: " + event.type);
                        break;
                    }

                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        private void processConnectionEvent(int state, BluetoothDevice device,
                                                   int peerFeatures, int localFeatures) {
            switch (state) {
                case HfDeviceHalConstants.CONNECTION_STATE_DISCONNECTED:
                    if (mCurrentDevice.equals(device)) {
                        processAudioEvent (HfDeviceHalConstants.AUDIO_STATE_DISCONNECTED, device);
                        mService.broadcastConnectionState(BluetoothHfDevice.NO_ERROR, device,
                                 BluetoothProfile.STATE_DISCONNECTED,
                                 BluetoothProfile.STATE_CONNECTED,
                                 peerFeatures, localFeatures);

                        synchronized (HfDeviceStateMachine.this) {
                            mCurrentDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        Log.e(TAG, "Disconnected from unknown device: " + device);
                    }
                    break;
              default:
                  Log.e(TAG, "Connection State Device: " + device + " bad state: " + state);
                  break;
            }

        }

        private void processAudioEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                Log.e(TAG, "Audio changed on disconnected device: " + device);
                return;
            }

            Log.d(TAG, "processAudioEvent: state=" + state + " mAudioState=" + mAudioState);
            switch (state) {
                case HfDeviceHalConstants.AUDIO_STATE_DISCONNECTED:
                    if (mAudioState != BluetoothHfDevice.STATE_AUDIO_DISCONNECTED) {
                        mAudioState = BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;

                        mAudioManager.setBluetoothScoOn(false);
                        checkAndAbandonAudioFocus(false);

                        mService.broadcastAudioState(device,
                            BluetoothHfDevice.STATE_AUDIO_DISCONNECTED,
                            BluetoothHfDevice.STATE_AUDIO_CONNECTED);

                    }
                    transitionTo(mConnected);
                    break;
                case HfDeviceHalConstants.AUDIO_STATE_DISCONNECTING:
                    break;
                default:
                    Log.e(TAG, "Audio State Device: " + device + " bad state: " + state);
                    break;
            }

        }

    }

    // HFP Connection state of the device could be changed by the state machine
    // in separate thread while this method is executing.
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
                    return BluetoothProfile.STATE_CONNECTING; // incoming
                                                                // connection
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            if (currentState == mConnected || currentState == mAudioOn) {
                if (mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTED;
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            } else {
                Log.e(TAG, "Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized (this) {
            if (isConnected()) {
                devices.add(mCurrentDevice);
            }
        }
        return devices;
    }



    int getAudioState(BluetoothDevice device) {
        synchronized (this) {
            if (mCurrentDevice == null || !mCurrentDevice.equals(device)) {
                return BluetoothHfDevice.STATE_AUDIO_DISCONNECTED;
            }
        }
        return mAudioState;
    }


    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                ParcelUuid[] featureUuids = device.getUuids();
                if (!BluetoothUuid.containsAnyUuid(featureUuids, AG_UUIDS)) {
                    continue;
                }
                connectionState = getConnectionState(device);
                for (int i = 0; i < states.length; i++) {
                    if (connectionState == states[i]) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }


    private void processCallState(int numActive, int callSetupState, int numHeld) {

        mDeviceStateData.setNumActiveCall(numActive);
        mDeviceStateData.setNumHeldCall(numHeld);
        mDeviceStateData.setCallSetupState(callSetupState);

        log("broadcastCallStateChange "+"callSetupState:"+callSetupState+
            "numActive:"+numActive+"numHeld"+numHeld);

        if (isInCall()) {
            if(!hasAudioFocused)
                checkandRequestAudioFocus();
        } else if (mAudioState == BluetoothHfDevice.STATE_AUDIO_DISCONNECTED){
            checkAndAbandonAudioFocus(false);
        }

        mService.broadcastCallStateChange
            (BluetoothHfDevice.NO_ERROR, callSetupState, numActive, numHeld,
                                        null, -1);
    }

    private void processClipEvent(String clipString) {
        Log.d(TAG, "CLIP string = "+ clipString);

        int numActive = mDeviceStateData.getNumActiveCall();
        int callSetupState = mDeviceStateData.getCallSetupState();
        int numHeld = mDeviceStateData.getNumHeldCall();
        int nType = -1;

        String incomingCallNumber = clipString;
        incomingCallNumber = incomingCallNumber.substring(incomingCallNumber.indexOf("\"")+1,
            incomingCallNumber.length());
        incomingCallNumber = incomingCallNumber.substring(0,incomingCallNumber.indexOf("\""));

        String strType = clipString;
        strType = strType.substring(strType.indexOf(",")+1,strType.length());

        int index = strType.indexOf(",");
        if (-1 != index)
            strType = strType.substring(0, index);


        if (null!= strType) {
            nType = Integer.parseInt(strType);
        }
        log("broadcastCallStateChange "+"callSetupState:"+callSetupState+"numActive:"+numActive+
            "CLIP="+incomingCallNumber+"nType"+nType);
        mService.broadcastCallStateChange(BluetoothHfDevice.NO_ERROR,
                                        callSetupState, numActive, numHeld,
                                        incomingCallNumber, nType);
    }

    private void processCallWaitingEvent(String ccwaString) {
        Log.d(TAG, "CCWA string = "+ ccwaString);

        int numActive = mDeviceStateData.getNumActiveCall();
        int callSetupState = mDeviceStateData.getCallSetupState();
        int numHeld = mDeviceStateData.getNumHeldCall();
        int nType = -1;

        String waitingCallNumber = ccwaString;
        waitingCallNumber = waitingCallNumber.substring(waitingCallNumber.indexOf("\"")+1,
            waitingCallNumber.length());
        waitingCallNumber = waitingCallNumber.substring(0,waitingCallNumber.indexOf("\""));

        // parse format handle "num",type,class
        String strType = ccwaString;
        strType = strType.substring(strType.indexOf(",")+1,strType.length());

        int index = strType.indexOf(",");
        if (-1 != index)
            strType = strType.substring(0, index);


        if (null!= strType) {
            nType = Integer.parseInt(strType);
        }
        log("broadcastCallStateChange "+"callSetupState:"+callSetupState+"numActive:"+numActive+
            "CCWA="+waitingCallNumber+"nType"+nType);
        mService.broadcastCallStateChange(BluetoothHfDevice.NO_ERROR,
                        BluetoothHfDevice.CALL_SETUP_STATE_WAITING, numActive, numHeld,
                                        waitingCallNumber, nType);
    }

    private void processDeviceStateChanged(int ntStatus, int svcType,
                                        int signal, int battCharge) {
        mDeviceStateData.setService(ntStatus);
        mDeviceStateData.setRoam(svcType);
        mDeviceStateData.setSignal(signal);
        mDeviceStateData.setBatteryCharge(battCharge);
        mService.broadcastIndicatorsUpdate(getDeviceIndicators(mCurrentDevice));
    }

    private synchronized void processVndATCmdEvent(int status, String vndCmdString) {
        Log.d(TAG, "Vnd AT cmd string = "+ vndCmdString);

        if (null != vndCmdString)
            vndCmdString = vndCmdString.trim();

        if (PhoneBookAtCommandHandler.isBusy()) {
            if ((null != vndCmdString) && vndCmdString.startsWith("+CSCS"))
                mPhoneBookAtCommandHandler.handleCSCSCommandRsp(vndCmdString.substring(5));
            else if ((null != vndCmdString) && vndCmdString.startsWith("+CPBS"))
                mPhoneBookAtCommandHandler.handleCPBSCommandRsp(vndCmdString.substring(5));
            else if ((null != vndCmdString) && vndCmdString.startsWith("+CPBR"))
                mPhoneBookAtCommandHandler.handleCPBRCommandRsp(vndCmdString.substring(5));
            else
                mPhoneBookAtCommandHandler.handleStatusEvent(status);
        } else {
            mService.broadcastVndCmdEvent(status, vndCmdString);
        }

    }

    private void processVolumeEvent(int volType, int volume) {

        log("broadcastVolumeEvent "+"volType:"+volType+
            "volume:"+volume);
        mService.broadcastVolumeEvent (volType, volume);
    }

    private void processVREvent(int status, int vrState) {
        if (BluetoothHfDevice.VR_STATE_INACTIVE == vrState)
            mVoiceRecognitionStarted = false;
        else if (BluetoothHfDevice.VR_STATE_ACTIVE == vrState)
            mVoiceRecognitionStarted = true;
        else if (-1 == vrState)
            vrState = mPendingVrEvent;

        if (mVoiceRecognitionStarted)
            checkandRequestAudioFocus();
        else
            checkAndAbandonAudioFocus(false);

        mPendingVrEvent = -1;
        log("broadcastVREvent "+"status:"+status+
            "vrState:"+vrState);
        mService.broadcastVREvent (status, vrState);
    }

    private void processWBSEvent( int wbsState) {
        log("processWBSEvent wbsState:"+wbsState);
        mService.broadcastWBSEvent ( wbsState);
    }

    private void processRingEvent() {
        log("processRingEvent ");
        if(isHspConnection())
            mService.broadcastRingEvent();
        else
            log("RING event ignored as there is no HSP connection");

    }


    private void processInBandRingStatusEvent( int inBandRingStatus) {
        log("processInBandRingStatusEvent inBandRingStatus:"+inBandRingStatus);

        if (inBandRingStatus == BluetoothHfDevice.INBAND_STATE_ON)
            setInBandRingToneStatus(true);
        else
            setInBandRingToneStatus(false);

        mService.broadcastInBandRingStatusEvent(inBandRingStatus);
    }

    private void processBIAStatusEvent( int status) {
        log("processBIAStatusEvent status:"+status);
        mService.broadcastBIAStatus( status);
    }

    private void handleSendBIA(int indicators) {

        int enableRoam = 0,enableService = 0;
        int enableSignal = 0,enableBattery = 0;

        if (0!= (indicators & ROAM))
            enableRoam = 1;
        if (0!= (indicators & SERVICE))
            enableService = 1;
        if (0!= (indicators & SIGNAL))
            enableSignal = 1;
        if (0!= (indicators & BATTERY))
            enableBattery = 1;

        sendBIANative(enableRoam, enableService,
                    enableSignal, enableBattery);
    }

    private void handleCallEvents(Message message) {
        switch(message.what) {
            case ANSWER:
                if (!answerNative()) {
                    mService.broadcastCallStateChange(BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.CALL_SETUP_STATE_IDLE, 0, 0,
                                                    null, -1);
                }
                break;
            case HANGUP:
                if (!hangupNative()) {
                    mService.broadcastCallStateChange(BluetoothHfDevice.ERROR_AG_FAILURE,
                                                        BluetoothHfDevice.CALL_SETUP_STATE_IDLE,
                                                        mDeviceStateData.getNumActiveCall(),
                                                        mDeviceStateData.getNumHeldCall(),
                                                        null, -1);
                }
                break;
            case DIAL:
                    String number = (String) message.obj;
                    if((null!= number) && (number.charAt(number.length() - 1) != ';'))
                        number = number+ ";";
                    if (!dialNative(number)) {
                        mService.broadcastCallStateChange(BluetoothHfDevice.ERROR_AG_FAILURE,
                                                        BluetoothHfDevice.CALL_SETUP_STATE_IDLE,
                                                        mDeviceStateData.getNumActiveCall(),
                                                        mDeviceStateData.getNumHeldCall(),
                                                        null, -1);
                }
                break;

            case REDIAL:
                if (!dialNative(null)) {
                    mService.broadcastCallStateChange(BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.CALL_SETUP_STATE_IDLE,
                                                    mDeviceStateData.getNumActiveCall(),
                                                    mDeviceStateData.getNumHeldCall(),
                                                    null, -1);
                }
                break;
            case HOLD:
                int holdType = (Integer) message.obj;
                if (!holdNative(holdType)) {
                    mService.broadcastCallStateChange(BluetoothHfDevice.ERROR_AG_FAILURE,
                                                    BluetoothHfDevice.CALL_SETUP_STATE_IDLE,
                                                    mDeviceStateData.getNumActiveCall(),
                                                    mDeviceStateData.getNumHeldCall(),
                                                    null, -1);
                }
                break;
            case AT_CLCC:
                Log.d(TAG,"Clearing call list CLCC"+mClccInfo.size());
                if (!isClccReqInProcess) {
                    isClccReqInProcess = true;
                    if (!sendClccNative()) {
                        mService.broadcastClccEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
                        isClccReqInProcess = false;
                    }
                } else {
                    //This will ensure we store the latest clcc request
                    isPendingClcc = true;
                }
                break;
            case SEND_AT_DTMF:
                char dtmfCode = (Character) message.obj;
                if (!sendDTMFNative(dtmfCode))
                    Log.e(TAG,"DTMF send failed");
                Log.d(TAG, "DTMF code sent"+dtmfCode);
                break;

            default:
                break;

        }

    }
    private void processAtCops(int status, String copsString) {
        Log.d(TAG, "COPS string = "+ copsString);
        int mode = -1;
        String operatorName = null;

        if (null != copsString) {
            StringTokenizer stringTokenizer = new StringTokenizer(copsString,",");
            int i = 0;
            String tokenString;

            while(stringTokenizer.hasMoreTokens()) {

                tokenString = stringTokenizer.nextToken();
                if (null != tokenString)
                {
                    tokenString = tokenString.trim();
                    if (i == 0) {
                        // not supported token
                    } else if ( i == 1) {
                        mode = Integer.parseInt(tokenString);
                    } else if (i == 2) {
                        operatorName = tokenString.substring(1 ,tokenString.length()-1);
                    }
                }
                i = i + 1;
            }

        }

        mService.broadcastOperatorSelectionEvent(status, mode, operatorName);

    }

    private void processAtCnum(int status, String cnumString) {
        Log.d (TAG,"Status="+status+" cnumString"+cnumString);
        String number = null;
        int type = -1;

        if (null != cnumString) {
            StringTokenizer stringTokenizer = new StringTokenizer(cnumString,",");
            int i = 0;

            while(stringTokenizer.hasMoreTokens()) {
                String tokenString = null;
                tokenString = stringTokenizer.nextToken();
                tokenString = tokenString.trim();
                Log.d(TAG,"tokenString = "+tokenString+"i="+i);
                if (null != tokenString)
                {
                    if (i == 0) {
                        number = tokenString.substring(1 ,tokenString.length()-1);
                    } else if (i == 1) {
                        Log.d(TAG,tokenString);
                        type = Integer.parseInt(tokenString);
                    }
                }
                i = i + 1;
            }

        }

        Log.d(TAG,"number ="+number+"type="+type);

        mService.broadcastSubscriberInfoEvent(status, number, type);

    }

    private synchronized void processAtClcc(int status, String clccString) {

        Log.d (TAG,"Status="+status+" clccString"+clccString+" size="+mClccInfo.size());
        if (null != clccString) {
            BluetoothClccInfo clccInfo = new BluetoothClccInfo(clccString);
            mClccInfo.add(clccInfo);
        } else {

            // Create a copy and send to callback
            List<BluetoothClccInfo> cloneClccInfo = new ArrayList<BluetoothClccInfo>();
            for(int i = 0; i < mClccInfo.size(); i++) {
                BluetoothClccInfo  clone = new BluetoothClccInfo(mClccInfo.get(i));
                cloneClccInfo.add(clone);
            }

           //Clear it for next request
            mClccInfo.clear();
            isClccReqInProcess = false;
            mService.broadcastClccEvent(status, cloneClccInfo);

            if (isPendingClcc) {
                isClccReqInProcess = true;
                if (!sendClccNative()) {
                    mService.broadcastClccEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
                    isClccReqInProcess = false;
                }
                isPendingClcc = false;
            }

        }


    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter
                .getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    boolean isConnected() {
        IState currentState = getCurrentState();
        return (currentState == mConnected || currentState == mAudioOn);
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

    @Override
    protected void log(String msg) {
            if (DBG) {
                super.log(msg);
            }
        }


    boolean isAudioOn() {
        return (getCurrentState() == mAudioOn);
    }

    private synchronized void checkandRequestAudioFocus () {

        Log.d(TAG, "checkandRequestAudioFocus hasAudioFocused="+hasAudioFocused);

        if (hasAudioFocused == false) {
             int result = mAudioManager.requestAudioFocus(afChangeListener,
                                              // Use the voice call
                                              AudioManager.STREAM_VOICE_CALL,
                                              // Request temporary focus.
                                              AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocused = true;
                Log.d(TAG,"AudioManager.AUDIOFOCUS_REQUEST_GRANTED");
            } else {
                Log.e(TAG, "AudioManager.AUDIOFOCUS rejected result ="+result);
            }

        } else {
            Log.d(TAG, "Has audio focus already");
        }
    }

    private synchronized void checkAndAbandonAudioFocus (boolean forceAbandon) {

        Log.d(TAG, "checkAndAbandonAudioFocus hasAudioFocused="+hasAudioFocused+
            "forceAbandon="+"isInCall"+isInCall());

        if ((forceAbandon == false) && (isInCall()))
            return;

        if (hasAudioFocused == true) {
            mAudioManager.abandonAudioFocus(afChangeListener);
            hasAudioFocused = false;
        } else {
            Log.d(TAG, "Audio not focused already");
        }
    }

    // Active,hold and in progress call
    private boolean isInCall () {

        boolean isIncall = false;
        int numActive = mDeviceStateData.getNumActiveCall();
        int callSetupState = mDeviceStateData.getCallSetupState();
        int numHeld = mDeviceStateData.getNumHeldCall();

        if (((numActive+numHeld)!= 0) ||
            (callSetupState != BluetoothHfDevice.CALL_SETUP_STATE_IDLE))
            isIncall = true;

        Log.d(TAG,"isIncall = "+isIncall);

        return isIncall;
    }

    public int[] getDeviceIndicators(BluetoothDevice device) {
        int[] devInd = new int[BluetoothHfDevice.INDICATOR_TYPE_MAX];
        devInd[BluetoothHfDevice.INDICATOR_TYPE_SERVICE] = mDeviceStateData.getService();
        devInd[BluetoothHfDevice.INDICATOR_TYPE_BATTERY] = mDeviceStateData.getBatteryCharge();
        devInd[BluetoothHfDevice.INDICATOR_TYPE_SIGNAL] = mDeviceStateData.getSignal();
        devInd[BluetoothHfDevice.INDICATOR_TYPE_ROAM] = mDeviceStateData.getRoam();

        return devInd;
    }

    public int[] getCallStateInfo(BluetoothDevice device) {
        int[] callState = new int[BluetoothHfDevice.INDEX_CALL_STATE_INFO_MAX];
        callState[BluetoothHfDevice.INDEX_NUM_OF_ACTV] = mDeviceStateData.getNumActiveCall();
        callState[BluetoothHfDevice.INDEX_CALL_SETUP_STATE] = mDeviceStateData.getCallSetupState();
        callState[BluetoothHfDevice.INDEX_CALL_NUM_OF_HELD] = mDeviceStateData.getNumHeldCall();

        return callState;
    }

    public void setInBandRingToneStatus(boolean enable){
        if (enable)
            mPeerFeatures = mPeerFeatures | HfDeviceHalConstants.INBAND;
        else
            mPeerFeatures = mPeerFeatures & INBAND_DISABLE;

        return;
    }

    // Event types for STACK_EVENT message
    final private static int EVENT_TYPE_NONE = 0;
    final private static int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    final private static int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    final private static int EVENT_TYPE_CALL_STATE_CHANGED = 3;
    final private static int EVENT_TYPE_VR_STATE_CHANGED = 4;
    final private static int EVENT_TYPE_DEVICE_STATUS_STATE_CHANGED = 5;
    final private static int EVENT_TYPE_VOLUME_CHANGED = 6;
    final private static int EVENT_TYPE_DIAL_CALL = 7;
    final private static int EVENT_TYPE_AT_CNUM = 11;
    final private static int EVENT_TYPE_AT_CIND = 12;
    final private static int EVENT_TYPE_AT_COPS = 13;
    final private static int EVENT_TYPE_AT_CLCC = 14;
    final private static int EVENT_TYPE_CLIP_AVAILABLE = 15;
    final private static int EVENT_TYPE_CALL_WAITING = 16;
    final private static int EVENT_TYPE_VENDOR_AT = 17;
    final private static int EVENT_TYPE_WBS_STATE_CHANGED = 18;
    final private static int EVENT_TYPE_RING = 19;
    final private static int EVENT_TYPE_IN_BAND_RING_STATUS = 20;
    final private static int EVENT_BIA_STATUS = 21;

    private class StackEvent {
        int type = EVENT_TYPE_NONE;
        int valueInt = 0;
        int valueInt2 = 0;
        int valueInt3 = 0;
        int valueInt4 = 0;
        String valueString = null;
        BluetoothDevice device = null;

        private StackEvent(int type) {
            this.type = type;
        }
    }

    private void onConnectionStateChanged(int state, byte[] address,
                            int peerFeatures, int localFeatures) {
        StackEvent event = new StackEvent(EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.valueInt2 = peerFeatures;
        event.valueInt3 = localFeatures;

        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onDeviceStatusChanged(int ntStatus, int svcType,
                                        int signal, int battCharge) {
        StackEvent event = new StackEvent(EVENT_TYPE_DEVICE_STATUS_STATE_CHANGED);
        event.valueInt = ntStatus;
        event.valueInt2 = svcType;
        event.valueInt3 = signal;
        event.valueInt4 = battCharge;
        sendMessage(STACK_EVENT, event);

    }

    private void onCallStatusChanged(int numActive,
                            int callSetup, int numHeld) {
        StackEvent event = new StackEvent(EVENT_TYPE_CALL_STATE_CHANGED);
        event.valueInt = numActive;
        event.valueInt2 = callSetup;
        event.valueInt3 = numHeld;
        sendMessage(STACK_EVENT, event);
    }

    private void onClipEvent(String clipString) {
        StackEvent event = new StackEvent(EVENT_TYPE_CLIP_AVAILABLE);
        event.valueString = clipString;
        // For incoming call the app needs to launch itself and register with service.
        // If the clip event happens inbetween this time,
        // process clip event after a delay so that the app app has ample time to register.
        // Also we cant wait cant wait sending incoming call indication till CLIP happens,
        // as CLIP is optioinal.
        sendMessageDelayed(STACK_EVENT, event,1000);
    }

    private void onCcwaEvent(String ccwaString) {
        StackEvent event = new StackEvent(EVENT_TYPE_CALL_WAITING);
        event.valueString = ccwaString;
        sendMessage(STACK_EVENT, event);
    }

    private void onClccEvent(int status, String clccString) {
        StackEvent event = new StackEvent(EVENT_TYPE_AT_CLCC);
        event.valueInt = status;
        event.valueString = clccString;
        sendMessage(STACK_EVENT, event);
    }

    private void onCnumEvent(int status, String cnumString) {
        StackEvent event = new StackEvent(EVENT_TYPE_AT_CNUM);
        event.valueInt = status;
        event.valueString = cnumString;
        sendMessage(STACK_EVENT, event);
    }

    private void onCopsEvent(int status, String copsString) {
        StackEvent event = new StackEvent(EVENT_TYPE_AT_COPS);
        event.valueInt = status;
        event.valueString = copsString;
        sendMessage(STACK_EVENT, event);
    }

    private void onVndAtCmdEvent(int status, String vndAtString) {
        StackEvent event = new StackEvent(EVENT_TYPE_VENDOR_AT);
        event.valueInt = status;
        event.valueString = vndAtString;
        sendMessage(STACK_EVENT, event);
    }

    private void onVolumeEvent(int type, int volume) {
        StackEvent event = new StackEvent(EVENT_TYPE_VOLUME_CHANGED);
        event.valueInt = type;
        event.valueInt2 = volume;
        sendMessage(STACK_EVENT, event);
    }

    private void onVREvent(int status, int vrState) {
        StackEvent event = new StackEvent(EVENT_TYPE_VR_STATE_CHANGED);
        event.valueInt = status;
        event.valueInt2 = vrState;
        sendMessage(STACK_EVENT, event);
    }

    private void onWBSEvent( int wbsState) {
        StackEvent event = new StackEvent(EVENT_TYPE_WBS_STATE_CHANGED);
        event.valueInt = wbsState;
        sendMessage(STACK_EVENT, event);
    }

    private void onRingEvent() {
        StackEvent event = new StackEvent(EVENT_TYPE_RING);
        sendMessage(STACK_EVENT, event);
    }


    private void onInBandRingStatusEvent(int inBandState) {
        StackEvent event = new StackEvent(EVENT_TYPE_IN_BAND_RING_STATUS);
        event.valueInt = inBandState;
    }

    private void onBIAStatus( int status) {
        StackEvent event = new StackEvent(EVENT_BIA_STATUS);
        event.valueInt = status;
        sendMessage(STACK_EVENT, event);
    }

    private native static void classInitNative();

    private native void initializeNative();

    private native void cleanupNative();

    private native boolean connectNative(byte[] address);

    private native boolean disconnectNative(byte[] address);

    private native boolean connectAudioNative(byte[] address);

    private native boolean disconnectAudioNative(byte[] address);

    private native boolean answerNative();

    private native boolean hangupNative();

    private native boolean dialNative(String number);

    private native boolean sendClccNative();

    private native boolean holdNative(int holdType);

    public native boolean sendVndATCmdNative(String vndCmdString);

    private native boolean setVolumeNative (int volType, int volume);

    private native boolean sendCopsNative();

    private native boolean sendCnumNative();

    private native boolean sendDTMFNative(char dtmfCode);

    private native boolean startVoiceRecognitionNative();

    private native boolean stopVoiceRecognitionNative();

    //TODO remove this api dependency as sniff mode is handled stack
    public native boolean configPbDownloadMode(int mode);

    public native boolean sendKeyPressEventNative();

    public native boolean sendBIANative(int enableRoam,
                int enableService, int enableSignal, int enableBattery);

}
