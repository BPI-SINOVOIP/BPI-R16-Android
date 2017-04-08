/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2012 Broadcom Corporation
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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import android.content.SharedPreferences;
import android.content.res.Resources;
import com.android.bluetooth.Utils;
import android.preference.PreferenceManager;
import com.broadcom.bt.settings.BluetoothAdvancedSettings;
import com.broadcom.bt.settings.HeaderAdapter.HeaderViewHolder;

/**
 * This state machine handles Bluetooth Adapter State.
 * States:
 *      {@link OnState} : Bluetooth is on at this state
 *      {@link OffState}: Bluetooth is off at this state. This is the initial
 *      state.
 *      {@link PendingCommandState} : An enable / disable operation is pending.
 * TODO(BT): Add per process on state.
 */

final class AdapterState extends StateMachine {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private static final String TAG = "BluetoothAdapterState";

    static final int USER_TURN_ON = 1;
    static final int STARTED=2;
    static final int ENABLED_READY = 3;

    static final int USER_TURN_OFF = 20;
    static final int BEGIN_DISABLE = 21;
    static final int ALL_DEVICES_DISCONNECTED = 22;

    static final int DISABLED = 24;
    static final int STOPPED=25;

    static final int START_TIMEOUT = 100;
    static final int ENABLE_TIMEOUT = 101;
    static final int DISABLE_TIMEOUT = 103;
    static final int STOP_TIMEOUT = 104;
    static final int SET_SCAN_MODE_TIMEOUT = 105;

    static final int USER_TURN_OFF_DELAY_MS=500;

    static final int ENABLED_RADIO = 200;
    static final int BEGIN_ENABLE_RADIO = 201;
    // For other radio can be handled in similar to FM or same as FM
    static final int USER_TURN_ON_RADIO = 202;
    static final int USER_TURN_OFF_RADIO = 203;
    static final int DISABLED_RADIO = 204;
    static final int BEGIN_DISABLE_RADIO = 205;
    static final int START_QUIET_MODE_SERVICES = 206;
    static final int STOP_QUIET_MODE_SERVICES = 207;
    static final int QUIET_MODE_SERVICES_STARTED = 208;
    static final int QUIET_MODE_SERVICES_STOPPED = 209;

    static final int USER_DEVICE_MODE_SWITCH = 300;
    static final int DEVICE_MODE_DISCONNECT_PROFILES = 301;
    static final int DEVICE_MODE_CHECK_DISCONNECTED_PROFILES = 302;
    static final int DEVICE_MODE_SWITCH_SERVICES_TURN_OFF = 303;
    static final int DEVICE_MODE_SWITCH_SERVICES_TURN_ON = 304;
    static final int DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF = 305;
    static final int DEVICE_MODE_SWITCH_SERVICES_TURNED_ON = 306;

    static final int DEVICE_MODE_SWITCH_DISCONNECT_CHECK_MAX = 5;

    //TODO: tune me
    private static final int ENABLE_TIMEOUT_DELAY = 10000;
    // timeout needs to be higher than typical link supervision timeout of 5-10s
    private static final int DISABLE_TIMEOUT_DELAY = 12000;
    private static final int START_TIMEOUT_DELAY = 5000;
    private static final int STOP_TIMEOUT_DELAY = 5000;
    private static final int PROPERTY_OP_DELAY =2000;
    private AdapterService mAdapterService;
    private AdapterProperties mAdapterProperties;
    private PendingCommandState mPendingCommandState = new PendingCommandState();
    private OnState mOnState = new OnState();
    private OffState mOffState = new OffState();
    // Irrespective of the Bluetooth State  isRadioOn is used to maintain the Radio state
    private boolean isRadioOn = false;
    private boolean forceDisable = false;
    private int mPrevState = BluetoothAdapter.STATE_OFF;



    private int mDeviceMode = -1;
    private int mPendingDeviceModeState = -1;
    public synchronized int getDeviceMode()
    {
        return mDeviceMode;
    }

    public synchronized void setDeviceMode(int devicemode)
    {
        mDeviceMode =devicemode;
    }

    public synchronized int getPendingDeviceMode()
    {
        return mPendingDeviceModeState;
    }

    public synchronized boolean isDeviceModeSwitchTurningOn() {
        boolean isDeviceModeSwitchTurningOn=  mPendingCommandState.isDeviceModeSwitchTurningOn();
        if (VDBG) Log.d(TAG,"isDeviceModeSwitchTurningOn()="+
            isDeviceModeSwitchTurningOn);
        return isDeviceModeSwitchTurningOn;
    }

    public synchronized boolean isDeviceModeSwitchTurningOff() {
        boolean isDeviceModeSwitchTurningOff= mPendingCommandState.isDeviceModeSwitchTurningOff();
        if (VDBG) Log.d(TAG,"isDeviceModeSwitchTurningOff()="
            + isDeviceModeSwitchTurningOff);
        return isDeviceModeSwitchTurningOff;
    }

    public boolean isRadioOn() {
        return isRadioOn;
    }

    private BluetoothAdvancedSettings mAdvSettings;
    private Context mContext;

    public boolean isTurningOn() {
        boolean isTurningOn=  mPendingCommandState.isTurningOn();
        if (VDBG) Log.d(TAG,"isTurningOn()=" + isTurningOn);
        return isTurningOn;
    }

    public boolean isTurningOff() {
        boolean isTurningOff= mPendingCommandState.isTurningOff();
        if (VDBG) Log.d(TAG,"isTurningOff()=" + isTurningOff);
        return isTurningOff;
    }

    public boolean isTurningOnRadio() {
        boolean isTurningOnRadio=  mPendingCommandState.isTurningOnRadio();
        if (VDBG) Log.d(TAG,"isTurningOnRadio()=" + isTurningOnRadio);
        return isTurningOnRadio;
    }

    public boolean isTurningOffRadio() {
        boolean isTurningOffRadio= mPendingCommandState.isTurningOffRadio();
        if (VDBG) Log.d(TAG,"isTurningOffRadio()=" + isTurningOffRadio);
        return isTurningOffRadio;
    }

    public boolean isQuietModeServiceTurningOn() {
        if (VDBG) Log.d(TAG,"isQuietModeServiceTurningOn()=" +
            mOffState.isQuietModeServicesStarting());
        return mOffState.isQuietModeServicesStarting();
    }

    public boolean isQuietModeServiceTurningOff() {
        if (VDBG) Log.d(TAG,"isQuietModeServiceTurningOff()=" +
            mOffState.isQuietModeServicesStopping());
        return mOffState.isQuietModeServicesStopping();
    }

    private AdapterState(AdapterService service, AdapterProperties adapterProperties) {
        super("BluetoothAdapterState:");
        addState(mOnState);
        addState(mOffState);
        addState(mPendingCommandState);
        mAdapterService = service;
        mAdapterProperties = adapterProperties;
        setInitialState(mOffState);
    }

    public static AdapterState make(AdapterService service, AdapterProperties adapterProperties) {
        Log.d(TAG, "make");
        AdapterState as = new AdapterState(service, adapterProperties);
        as.start();
        return as;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        if(mAdapterProperties != null)
            mAdapterProperties = null;
        if(mAdapterService != null)
            mAdapterService = null;
    }

    private class OffState extends State {

        private boolean mQuietModeServiceTurningOff = false;
        private boolean mQuietModeServiceTurningOn = false;

        public void setQuietModeServicesStarting(boolean start) {
            mQuietModeServiceTurningOn = start;
        }

        public boolean isQuietModeServicesStarting() {
            return mQuietModeServiceTurningOn;
        }

        public void setQuietModeServicesStopping(boolean stop) {
            mQuietModeServiceTurningOff = stop;
        }

        public boolean isQuietModeServicesStopping() {
            return mQuietModeServiceTurningOff;
        }


        @Override
        public void enter() {
            infoLog("Entering OffState");
            mQuietModeServiceTurningOn = false;
            mQuietModeServiceTurningOff = false;
        }

        @Override
        public boolean processMessage(Message msg) {
            AdapterService adapterService = mAdapterService;
            if (adapterService == null) {
                Log.e(TAG,"receive message at OffState after cleanup:" +
                          msg.what);
                return false;
            }
            switch(msg.what) {
               case USER_TURN_ON:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF, MSG = USER_TURN_ON");
                   notifyAdapterStateChange(BluetoothAdapter.STATE_TURNING_ON);
                   mPendingCommandState.setTurningOn(true);
                   transitionTo(mPendingCommandState);
                   sendMessageDelayed(START_TIMEOUT, START_TIMEOUT_DELAY);
                   adapterService.processStart();
                   break;
               case USER_TURN_ON_RADIO:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF, MSG = USER_TURN_ON_RADIO");
                   //Enable
                    mPendingCommandState.setTurningOnRadio(true);
                    transitionTo(mPendingCommandState);
                    sendMessage(BEGIN_ENABLE_RADIO);
                    break;

               case USER_TURN_OFF:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF, MSG = USER_TURN_OFF");
                   //TODO: Handle case of service started and stopped without enable
                   break;
               case USER_TURN_OFF_RADIO:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF,MSG = USER_TURN_OFF_RADIO, requestId= "
                                  + msg.arg1);

                   mPendingCommandState.setTurningOffRadio(true);
                   transitionTo(mPendingCommandState);

                   Message m = obtainMessage(BEGIN_DISABLE_RADIO);
                   m.arg1 = msg.arg1;
                   sendMessage(m);

                   break;

               case START_QUIET_MODE_SERVICES:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF,MSG = START_QUIET_MODE_SERVICES,requestId="
                                  + msg.arg1);
                   if (mAdapterService.getQuietmodeRadioCount() > 0) {
                       mQuietModeServiceTurningOn = true;
                       if (mAdapterService.startQuietModeServices(true) == true) {
                         //TODO initiate a timer to go back to Off state
                          transitionTo(mPendingCommandState);
                       } else
                           mQuietModeServiceTurningOn = false;
                   }
                   break;

               case STOP_QUIET_MODE_SERVICES:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=OFF,MSG = STOP_QUIET_MODE_SERVICES,requestId="
                                  + msg.arg1);
                   mQuietModeServiceTurningOff = true;
                   if (mAdapterService.startQuietModeServices(false) == true) {
                      //TODO initiate a timer to go back to Off state
                       transitionTo(mPendingCommandState);
                   }else
                       mQuietModeServiceTurningOff = false;
                   break;


               default:
                   if (DBG) Log.d(TAG,"ERROR:UNEXPECTED MSG:CURRENT_STATE=OFF,MSG = " + msg.what );
                   return false;
            }
            return true;
        }
    }

    private class OnState extends State {
        @Override
        public void enter() {
            // BRCM
            infoLog("Entering On State from state = " + mPrevState);
            // If OnState entry is because of turning ON radio the auto connect
            // should not be started as it is BT ON specific behaviour.
            if (mPendingCommandState.isTurningOnRadio())
                mPendingCommandState.setTurningOnRadio(false);
            //auto connect to be intiated only if state trnsition is STATE_TURNING_ON => STATE_ON
            else if (mPrevState == BluetoothAdapter.STATE_TURNING_ON)
                    mAdapterService.autoConnect();
            // /BRCM
        }

        @Override
        public boolean processMessage(Message msg) {
            AdapterProperties adapterProperties = mAdapterProperties;
            if (adapterProperties == null) {
                Log.e(TAG,"receive message at OnState after cleanup:" +
                          msg.what);
                return false;
            }

            switch(msg.what) {
               case USER_TURN_OFF:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=ON, MSG = USER_TURN_OFF");
                   notifyAdapterStateChange(BluetoothAdapter.STATE_TURNING_OFF);
                   mPendingCommandState.setTurningOff(true);
                   transitionTo(mPendingCommandState);

                   // Invoke onBluetoothDisable which shall trigger a
                   // setScanMode to SCAN_MODE_NONE
                   Message m = obtainMessage(SET_SCAN_MODE_TIMEOUT);
                   sendMessageDelayed(m, PROPERTY_OP_DELAY);
                   adapterProperties.onBluetoothDisable();
                   break;

               case USER_TURN_OFF_RADIO:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=ON, MSG = USER_TURN_OFF_RADIO");
                    mPendingCommandState.setTurningOffRadio(true);
                    transitionTo(mPendingCommandState);
                    Message m1 = obtainMessage(BEGIN_DISABLE_RADIO);
                    m1.arg1 = msg.arg1;
                    sendMessage(m1);
                    break;

               case USER_TURN_ON:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=ON, MSG = USER_TURN_ON");
                   Log.i(TAG,"Bluetooth already ON, ignoring USER_TURN_ON");
                   break;
               case USER_TURN_ON_RADIO:
                   if (DBG) Log.d(TAG,"CURRENT_STATE=ON, MSG = USER_TURN_ON_RADIO");
                   mPendingCommandState.setTurningOnRadio(true);
                   transitionTo(mPendingCommandState);
                   sendMessage(BEGIN_ENABLE_RADIO);
                   break;

               case USER_DEVICE_MODE_SWITCH:
                    // Start the device mode switching in Pending mode
                   if (DBG) Log.d(TAG,"CURRENT_STATE=ON, MSG = USER_DEVICE_MODE_SWITCH");
                   if (mPendingDeviceModeState != -1) {
                       Log.d(TAG,"Error in setting USER_DEVICE_MODE_SWITCH");
                       return false;
                   }
                   mPendingDeviceModeState = msg.arg1;
                   transitionTo(mPendingCommandState);
                   sendMessage(DEVICE_MODE_DISCONNECT_PROFILES);
                   break;

               default:
                   if (DBG) Log.d(TAG,"ERROR:UNEXPECTED MSG:CURRENT_STATE=ON, MSG = " + msg.what );
                   return false;
            }
            return true;
        }
    }

    private class PendingCommandState extends State {
        private boolean mIsTurningOn;
        private boolean mIsTurningOff;
        private boolean mIsTurningOnRadio;
        private boolean mIsTurningOffRadio;
        private boolean mIsDeviceModeSwitchTurningOn = false;
        private boolean mIsDeviceModeSwitchTurningOff = false;
        private int mDisconnectCheckCount = 0;


        private int mRequestId;

        public void enter() {
            infoLog("Entering PendingCommandState State: isTurningOn()=" + isTurningOn() +
                    ", isTurningOff()=" + isTurningOff());
        }

        public void setTurningOn(boolean isTurningOn) {
            mIsTurningOn = isTurningOn;
        }

        public boolean isTurningOn() {
            return mIsTurningOn;
        }

        public void setTurningOff(boolean isTurningOff) {
            mIsTurningOff = isTurningOff;
        }

        public boolean isTurningOff() {
            return mIsTurningOff;
        }

        public void setTurningOnRadio(boolean isTurningOnRadio) {
            mIsTurningOnRadio = isTurningOnRadio;
        }

        public boolean isTurningOnRadio() {
            return mIsTurningOnRadio;
        }

        public void setTurningOffRadio(boolean isTurningOffRadio) {
            mIsTurningOffRadio = isTurningOffRadio;
        }

        public boolean isTurningOffRadio() {
            return mIsTurningOffRadio;
        }

        public void setDeviceModeSwitchTurningOn(boolean isTurningOn) {
            mIsDeviceModeSwitchTurningOn = isTurningOn;
        }

        public boolean isDeviceModeSwitchTurningOn() {
            return mIsDeviceModeSwitchTurningOn;
        }

        public void setDeviceModeSwitchTurningOff(boolean isTurningOff) {
            mIsDeviceModeSwitchTurningOff = isTurningOff;
        }

        public boolean isDeviceModeSwitchTurningOff() {
            return mIsDeviceModeSwitchTurningOff;
        }

        @Override
        public boolean processMessage(Message msg) {

            boolean isTurningOn= isTurningOn();
            boolean isTurningOff = isTurningOff();
            boolean isTurningOnRadio= isTurningOnRadio();
            boolean isTurningOffRadio = isTurningOffRadio();
            mPrevState = mAdapterProperties.getState();

            AdapterService adapterService = mAdapterService;
            AdapterProperties adapterProperties = mAdapterProperties;
            if ((adapterService == null) || (adapterProperties == null)) {
                Log.e(TAG,"receive message at Pending State after cleanup:" +
                          msg.what);
                return false;
            }

            switch (msg.what) {
                case USER_TURN_ON:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = USER_TURN_ON"
                            + ", isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (isTurningOn) {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Alreadying turning on bluetooth..." +
                              "Ignoring USER_TURN_ON...");
                    } else {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Deferring request USER_TURN_ON");
                        deferMessage(msg);
                    }
                    break;
                case USER_TURN_OFF:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = USER_TURN_ON"
                            + ", isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (isTurningOff) {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Alreadying turning off bluetooth..." +
                              "Ignoring USER_TURN_OFF...");
                    } else {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Deferring request USER_TURN_OFF");
                        deferMessage(msg);
                    }
                    break;
                case USER_TURN_ON_RADIO:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = USER_TURN_ON_RADIO" +
                                   ", isTurningOn=" + isTurningOnRadio + ", isTurningOff=" +
                                   isTurningOffRadio);
                    if (isTurningOnRadio) {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Alreadying turning on Radio... " +
                              "Ignoring USER_TURN_ON_RADIO...");
                    } else {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Deferring request USER_TURN_ON_RADIO");
                        deferMessage(msg);
                    }
                    break;
                case USER_TURN_OFF_RADIO:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = USER_TURN_OFF_RADIO" +
                       ", isTurningOn=" + isTurningOnRadio + ",isTurningOff=" + isTurningOffRadio);
                    if (isTurningOffRadio) {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Alreadying turning off Radio..."+
                              "Ignoring USER_TURN_OFF_RADIO...");
                    } else {
                        Log.i(TAG,"CURRENT_STATE=PENDING: Deferring request USER_TURN_OFF_RADIO");
                        deferMessage(msg);
                    }
                    break;

                case STARTED:   {
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = STARTED, isTurningOn=" +
                                  isTurningOn + ", isTurningOff=" + isTurningOff);
                    //Remove start timeout
                    removeMessages(START_TIMEOUT);

                    //Enable
                    boolean ret = adapterService.enableNative();
                    if (!ret) {
                        Log.e(TAG, "Error while turning Bluetooth On");
                        notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                        transitionTo(mOffState);
                    } else {
                        sendMessageDelayed(ENABLE_TIMEOUT, ENABLE_TIMEOUT_DELAY);
                    }
                }
                    break;

                case BEGIN_ENABLE_RADIO:   {
                    if (DBG)Log.d(TAG,"CURRENT_STATE=PENDING, MSG = BEGIN_ENABLE_RADIO," +
                        " isTurningOnRadio="+ isTurningOnRadio + ", isTurningOffRadio=" +
                        isTurningOffRadio);
                    //Enable
                    boolean ret = mAdapterService.enableRadioNative();
                    if (!ret) {
                        Log.e(TAG, "Error while turning Radio On");
                        if (BluetoothAdapter.STATE_OFF == mAdapterProperties.getState())
                            transitionTo(mOffState);
                        else
                            transitionTo(mOnState);
                        mPendingCommandState.setTurningOnRadio(false);
                    } else {
                        sendMessageDelayed(ENABLE_TIMEOUT, ENABLE_TIMEOUT_DELAY);
                    }

                }
                    break;

                case ENABLED_READY:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = ENABLE_READY, isTurningOn="
                                   + isTurningOn + ", isTurningOff=" + isTurningOff);
                    removeMessages(ENABLE_TIMEOUT);
                    adapterProperties.onBluetoothReady();
                    mPendingCommandState.setTurningOn(false);
                    transitionTo(mOnState);
                    notifyAdapterStateChange(BluetoothAdapter.STATE_ON);
                    break;

                case ENABLED_RADIO:
                    if (DBG) Log.d(TAG,
                        "CURRENT_STATE=PENDING, MSG = ENABLED_RADIO, isTurningOnRadio="
                        + isTurningOnRadio + ", isTurningOffRadio=" + isTurningOffRadio);
                    removeMessages(ENABLE_TIMEOUT);
                    isRadioOn = true;

                    if (BluetoothAdapter.STATE_OFF == mAdapterProperties.getState()){
                        if (mAdapterService.getQuietmodeRadioCount() > 0) {
                            if (mAdapterService.startQuietModeServices(true) == true) {
                                sendMessageDelayed(START_TIMEOUT, START_TIMEOUT_DELAY);
                                break;
                            }
                        }
                        mPendingCommandState.setTurningOnRadio(false);
                        transitionTo(mOffState);
                        notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_ON);
                    }
                    else {
                        transitionTo(mOnState);
                        // Retain TurningOnRadio info till new state enter happens
                        // for skipping auto connect for radio turn ON
                        notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_ON);
                    }
                    break;

                case SET_SCAN_MODE_TIMEOUT:
                     Log.w(TAG,"Timeout will setting scan mode..Continuing with disable...");
                     //Fall through
                case BEGIN_DISABLE: {
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MESSAGE = BEGIN_DISABLE, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    removeMessages(SET_SCAN_MODE_TIMEOUT);
                    sendMessageDelayed(DISABLE_TIMEOUT, DISABLE_TIMEOUT_DELAY);
                    boolean ret = adapterService.disableNative();
                    if (!ret) {
                        removeMessages(DISABLE_TIMEOUT);
                        Log.e(TAG, "Error while turning Bluetooth Off");
                        //FIXME: what about post enable services
                        mPendingCommandState.setTurningOff(false);
                        notifyAdapterStateChange(BluetoothAdapter.STATE_ON);
                    }
                }
                    break;
                case BEGIN_DISABLE_RADIO: {
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = BEGIN_DISABLE_RADIO " +
                        " isturningonRadio=" + isTurningOnRadio + ", isTurningOffRadio=" +
                        isTurningOffRadio);
                    sendMessageDelayed(DISABLE_TIMEOUT, DISABLE_TIMEOUT_DELAY);
                    mPendingCommandState.setTurningOffRadio(true);
                    boolean ret = mAdapterService.disableRadioNative();
                    if (!ret){
                        removeMessages(DISABLE_TIMEOUT);
                        Log.e(TAG, "Error while turning Radio Off");
                        mPendingCommandState.setTurningOffRadio(false);
                        if (BluetoothAdapter.STATE_OFF == mAdapterProperties.getState())
                            transitionTo(mOffState);
                        else
                           transitionTo(mOffState);
                    }
                 }
                    break;

                case DISABLED:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MESSAGE = DISABLED, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (isTurningOn) {
                        removeMessages(ENABLE_TIMEOUT);
                        errorLog("Error enabling Bluetooth - hardware init failed");
                        mPendingCommandState.setTurningOn(false);
                        transitionTo(mOffState);
                        // BRCM
                        mAdapterService.stopProfileServices();
                        // /BRCM
                        notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                        break;
                    }
                    removeMessages(DISABLE_TIMEOUT);
                    sendMessageDelayed(STOP_TIMEOUT, STOP_TIMEOUT_DELAY);
                    if (adapterService.stopProfileServices()) {
                        Log.d(TAG,"Stopping profile services that were post enabled");
                        break;
                    }
                    //Fall through if no services or services already stopped
                case STOPPED:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = STOPPED, isTurningOn=" +
                                   isTurningOn + ", isTurningOff=" + isTurningOff);
                    removeMessages(STOP_TIMEOUT);
                    setTurningOff(false);
                    if (forceDisable== true) {
                        forceDisable = false;
                        mAdapterService.forceDisableNative();
                    }
                    transitionTo(mOffState);
                    notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                    break;
                case DISABLED_RADIO:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG =DISABLED_RADIO,isTurningOnRadio="
                        + isTurningOnRadio + ", isTurningOffRadio=" + isTurningOffRadio());
                    if (isTurningOnRadio) {
                        removeMessages(ENABLE_TIMEOUT);
                        errorLog("Error enabling radio - hardware init failed");
                        mPendingCommandState.setTurningOnRadio(false);
                    }
                    removeMessages(DISABLE_TIMEOUT);
                    if (BluetoothAdapter.STATE_OFF == mAdapterProperties.getState()){
                         if (mAdapterService.startQuietModeServices(false) == true) {
                            sendMessageDelayed(STOP_TIMEOUT, STOP_TIMEOUT_DELAY);
                            break;
                         }
                    }
                    setTurningOffRadio(false);
                    isRadioOn = false;
                    if (BluetoothAdapter.STATE_ON == mAdapterProperties.getState()) {
                        transitionTo(mOnState);
                        notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_OFF);
                    } else {
                        transitionTo(mOffState);
                        notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_OFF);
                        //mAdapterService.startShutdown(requestId1);
                    }
                    break;

                case QUIET_MODE_SERVICES_STARTED:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = QUIET_MODE_SERVICES_STARTED," +
                        "isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    removeMessages(START_TIMEOUT);
                    if (BluetoothAdapter.STATE_OFF == mAdapterProperties.getState()){
                         if (isTurningOnRadio()) {
                            notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_ON);
                            mPendingCommandState.setTurningOnRadio(false);
                         }
                         transitionTo(mOffState);
                    }
                    else {
                       transitionTo(mOnState);
                       // Retain TurningOnRadio info till new state enter happens
                       // for skipping auto connect for radio turn ON
                       if (isTurningOnRadio())
                           notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_ON);
                    }
                    break;

                case QUIET_MODE_SERVICES_STOPPED:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = QUIET_MODE_SERVICES_STOPPED," +
                        "isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    removeMessages(STOP_TIMEOUT);
                    if (isTurningOffRadio() == true) {
                        setTurningOffRadio(false);
                        isRadioOn = false;
                        notifyAdapterRadioStateChange(BluetoothAdapter.STATE_RADIO_OFF);
                    }
                    if (BluetoothAdapter.STATE_ON == mAdapterProperties.getState()) {
                        transitionTo(mOnState);
                    } else {
                        transitionTo(mOffState);
                    }
                    break;

                case START_TIMEOUT:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = START_TIMEOUT, isTurningOn=" +
                                   isTurningOn + ", isTurningOff=" + isTurningOff);
                    errorLog("Error enabling Bluetooth");
                    mPendingCommandState.setTurningOn(false);
                    transitionTo(mOffState);
                    notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                    break;
                case ENABLE_TIMEOUT:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = ENABLE_TIMEOUT, isTurningOn=" +
                                   isTurningOn + ", isTurningOff=" + isTurningOff);
                    errorLog("Error enabling Bluetooth");
                    mPendingCommandState.setTurningOn(false);
                    mPendingCommandState.setTurningOnRadio(false);
                    isRadioOn = false;
                    transitionTo(mOffState);
                    notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                    break;
                case STOP_TIMEOUT:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING,MSG = STOP_TIMEOUT, isTurningOn=" +
                                   isTurningOn + ", isTurningOff=" + isTurningOff);
                    errorLog("Error stopping Bluetooth profiles");
                    mPendingCommandState.setTurningOff(false);
                    if (forceDisable == true) {
                        forceDisable = false;
                        mAdapterService.forceDisableNative();
                    }
                    transitionTo(mOffState);
                    notifyAdapterStateChange(BluetoothAdapter.STATE_OFF);
                    break;
                case DISABLE_TIMEOUT:
                    if (DBG) Log.d(TAG,"CURRENT_STATE=PENDING, MSG = DISABLE_TIMEOUT, isTurningOn=" +
                                   isTurningOn + ", isTurningOff=" + isTurningOff);
                    errorLog("Error disabling Bluetooth");
                    if ((isTurningOff == true) &&(mAdapterService.isRadioEnabled() == false))
                    {
                        // Moving back to ON state takes system to non-recoverable state due to
                        //state mismatch in stack and JAVA layer
                        // Ignore stack disable failure/incomplete disable and move to  DISABLED
                        //state when there is no Radio based applications still using BT
                        errorLog("Native disable failed. Moving ahead with JAVA disable");
                        forceDisable = true;
                        sendMessage(DISABLED);
                    }
                    else
                    {
                        mPendingCommandState.setTurningOff(false);
                        mPendingCommandState.setTurningOffRadio(false);
                        transitionTo(mOnState);
                        notifyAdapterStateChange(BluetoothAdapter.STATE_ON);
                    }
                    break;

                case DEVICE_MODE_DISCONNECT_PROFILES:
                    Log.d(TAG,"DEVICE_MODE_DISCONNECT_PROFILES");
                    mDisconnectCheckCount = 0;
                    mAdapterService.disconnectDeviceModeProfiles();
                    sendMessageDelayed(
                        DEVICE_MODE_CHECK_DISCONNECTED_PROFILES, 300);
                    break;

                case DEVICE_MODE_CHECK_DISCONNECTED_PROFILES:
                    boolean isDisconnected =
                            mAdapterService.isDeviceModeProfilesDisconnected();
                    Log.d(TAG,"DEVICE_MODE_CHECK_DISCONNECTED_PROFILES isDisconnected="+
                                isDisconnected+"mDisconnectCheckCount="+mDisconnectCheckCount);
                    mDisconnectCheckCount++;
                    if (!isDisconnected &&
                            (mDisconnectCheckCount < DEVICE_MODE_SWITCH_DISCONNECT_CHECK_MAX)) {
                        Log.d(TAG,"Device mode profile still not disconnected");
                        sendMessageDelayed
                            (DEVICE_MODE_CHECK_DISCONNECTED_PROFILES, 300);
                    } else {
                        mPendingCommandState.setDeviceModeSwitchTurningOff(true);
                        sendMessage(DEVICE_MODE_SWITCH_SERVICES_TURN_OFF);
                    }
                    break;

                case DEVICE_MODE_SWITCH_SERVICES_TURN_OFF:
                    // Initiate turn OFF the currnet Mode(Device/Phone) mode services
                    Log.d(TAG,"DEVICE_MODE_SWITCH_SERVICES_TURN_OFF "
                        +"mPendingDeviceModeState="+mPendingDeviceModeState);
                    if (AdapterService.HEADSET_MODE == mPendingDeviceModeState) {
                        // Turn off Default mode
                        if(!mAdapterService.
                            setProfileStateForDeviceModeSwitch(
                                AdapterService.DEFAULT_MODE, false)) {
                            Log.e(TAG, "No services to turn OFF HEADSET_MODE");
                            sendMessage(DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF);
                        }
                    } else if (AdapterService.DEFAULT_MODE == mPendingDeviceModeState) {
                        // Turn off Headset mode
                        if(!mAdapterService.
                            setProfileStateForDeviceModeSwitch(
                                AdapterService.HEADSET_MODE, false)) {
                            Log.e(TAG, "No services to turn OFF DEFAULT_MODE");
                            sendMessage(DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF);
                        }
                    }
                    mIsDeviceModeSwitchTurningOff = true;
                    break;

                case DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF:
                    // Adapter service notifies after the service turned off
                    // Also continue now initiate turn ON the services for the new Mode(Device/Phone)
                    Log.d(TAG,"DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF  "
                        +"mPendingDeviceModeState="+mPendingDeviceModeState);
                    mIsDeviceModeSwitchTurningOff = false;
                    //fallthrough.Now turn on the service required for Device mode switch
                case DEVICE_MODE_SWITCH_SERVICES_TURN_ON:
                    Log.d(TAG,"DEVICE_MODE_SWITCH_SERVICES_TURN_ON "
                        +"mPendingDeviceModeState="+mPendingDeviceModeState);
                    mDeviceMode = mPendingDeviceModeState;

                    if (AdapterService.HEADSET_MODE == mPendingDeviceModeState) {
                        // Turn ON Headset mode services
                        if(!mAdapterService.
                            setProfileStateForDeviceModeSwitch(
                                AdapterService.HEADSET_MODE, true)) {
                            Log.e(TAG, "No services to turn OFF DEFAULT_MODE");
                            sendMessage(DEVICE_MODE_SWITCH_SERVICES_TURN_ON);
                        }
                    } else if (AdapterService.DEFAULT_MODE == mPendingDeviceModeState) {
                        // Turn ON Default mode services
                        if(!mAdapterService.
                            setProfileStateForDeviceModeSwitch(
                                AdapterService.DEFAULT_MODE, true)) {
                            Log.e(TAG, "No services to turn OFF DEFAULT_MODE");
                            sendMessage(DEVICE_MODE_SWITCH_SERVICES_TURN_ON);
                        }
                    }
                    mAdapterService.setDeviceModeProperty();
                    mIsDeviceModeSwitchTurningOn = true;
                    break;

                case DEVICE_MODE_SWITCH_SERVICES_TURNED_ON:
                    // Adapter service notifies after the service turned ON
                    // Now restore from  pending state to Previous ON state
                    Log.d(TAG,"DEVICE_MODE_SWITCH_SERVICES_TURNED_ON  "
                        +"mPendingDeviceModeState="+mPendingDeviceModeState);
                    mPendingDeviceModeState = -1;
                    mIsDeviceModeSwitchTurningOn = false;
                    mAdapterService.broadcastDeviceModeSwitchStatus();
                    int btState = mAdapterService.getState();
                    if (btState == BluetoothAdapter.STATE_ON)
                        transitionTo(mOnState);
                    else
                        transitionTo(mOffState);
                    break;

                default:
                    if (DBG) Log.d(TAG,"ERROR:UNEXPECTED MSG:CURRENT_STATE=PENDING,MSG = " +
                                   msg.what);
                    return false;
            }
            return true;
        }
    }


    private void notifyAdapterStateChange(int newState) {
        AdapterService adapterService = mAdapterService;
        AdapterProperties adapterProperties = mAdapterProperties;
        if ((adapterService == null) || (adapterProperties == null)) {
            Log.e(TAG,"notifyAdapterStateChange after cleanup:" + newState);
            return;
        }

        int oldState = adapterProperties.getState();
        adapterProperties.setState(newState);
        infoLog("Bluetooth adapter state changed: " + oldState + "-> " + newState);
        adapterService.updateAdapterState(oldState, newState);
    }

    private void notifyAdapterRadioStateChange(int newState) {
        infoLog("Bluetooth adapter radio state changed: " + newState);
        // Use the already defined callback prototype to send the bt_state and new_fm_state
        // TBD to use seperate callback while integration wtih FM app
        mAdapterService.updateAdapterState(mAdapterProperties.getState(), newState);
    }

    void stateChangeCallback(int status) {
        if (status == AbstractionLayer.BT_STATE_OFF) {
            sendMessage(DISABLED);
        } else if (status == AbstractionLayer.BT_STATE_ON) {
            // We should have got the property change for adapter and remote devices.
            sendMessage(ENABLED_READY);
        } else if (status == AbstractionLayer.BT_RADIO_OFF) {
            sendMessage(DISABLED_RADIO);
        } else if (status == AbstractionLayer.BT_RADIO_ON) {
            sendMessage(ENABLED_RADIO);
        } else {
            errorLog("Incorrect status in stateChangeCallback");
        }
    }

    private void infoLog(String msg) {
        if (DBG) Log.i(TAG, msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

}
