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

/**
 * @hide
 */

package com.android.bluetooth.btservice;

import android.app.Application;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.IInterface;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hdp.HealthService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.RemoteDevices.DeviceProperties;
import com.broadcom.bt.service.IProfileStateChangeListener;
import com.broadcom.bt.service.ProfileConfig;
import com.broadcom.bt.util.StringUtil;
import com.broadcom.bt.service.radiomanager.BluetoothRadioManager;
import com.broadcom.bt.service.hfdevice.HfDeviceService;
import com.broadcom.bt.hfdevice.BluetoothHfDevice;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.List;
import android.content.pm.PackageManager;
import android.os.ServiceManager;

public class AdapterService extends Service {
    private static final String TAG = "BluetoothAdapterService";
    private static final boolean DBG = true;
    private static final boolean TRACE_REF = true;

    private static int mRadioCount = 0;
    private static int mQuietModeCnt = 0;
    private static int mFmCnt = 0;
    private static int mAntCnt = 0;
    private Map <ParcelUuid, RadioClientDeathRecipient>mRadioClientDeathRecipient =
                                                 new HashMap<ParcelUuid,RadioClientDeathRecipient>();

    private AudioManager mAudioManager;

    //For Debugging only
    private static int sRefCount=0;

    public static final String ACTION_LOAD_ADAPTER_PROPERTIES =
        "com.android.bluetooth.btservice.action.LOAD_ADAPTER_PROPERTIES";
    public static final String ACTION_SERVICE_STATE_CHANGED =
        "com.android.bluetooth.btservice.action.STATE_CHANGED";
    public static final String EXTRA_ACTION="action";
    public static final int PROFILE_CONN_CONNECTED  = 1;
    public static final int PROFILE_CONN_REJECTED  = 2;

    public static int DEFAULT_MODE  = 0;
    public static int HEADSET_MODE  = 1;
    private Context mContext;


    static final String BLUETOOTH_ADMIN_PERM =
        android.Manifest.permission.BLUETOOTH_ADMIN;
    static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private static final int ADAPTER_SERVICE_TYPE=Service.START_STICKY;

    static {
        classInitNative();
    }

    private static AdapterService sAdapterService;
    public static synchronized AdapterService getAdapterService(){
        if (sAdapterService != null && !sAdapterService.mCleaningUp) {
            if (DBG) Log.d(TAG, "getAdapterService(): returning " + sAdapterService);
            return sAdapterService;
        }
        if (DBG)  {
            if (sAdapterService == null) {
                Log.d(TAG, "getAdapterService(): service not available");
            } else if (sAdapterService.mCleaningUp) {
                Log.d(TAG,"getAdapterService(): service is cleaning up");
            }
        }
        return null;
    }

    private static synchronized void setAdapterService(AdapterService instance) {
        if (instance != null && !instance.mCleaningUp) {
            if (DBG) Log.d(TAG, "setAdapterService(): set to: " + sAdapterService);
            sAdapterService = instance;
        } else {
            if (DBG)  {
                if (sAdapterService == null) {
                    Log.d(TAG, "setAdapterService(): service not available");
                } else if (sAdapterService.mCleaningUp) {
                    Log.d(TAG,"setAdapterService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearAdapterService() {
        sAdapterService = null;
    }

    private AdapterProperties mAdapterProperties;
    private AdapterState mAdapterStateMachine;
    private BondStateMachine mBondStateMachine;
    private JniCallbacks mJniCallbacks;
    private RemoteDevices mRemoteDevices;
    private boolean mProfilesStarted;
    private boolean mNativeAvailable;
    private boolean mCleaningUp;
    private HashMap<String,Integer> mProfileServicesState = new HashMap<String,Integer>();
    private RemoteCallbackList<IBluetoothCallback> mCallbacks;
    //Only BluetoothManagerService should be registered
    private int mCurrentRequestId;
    private boolean mQuietmode = false;
    private boolean mInitDone   = false;

    public AdapterService() {
        super();
        if (TRACE_REF) {
            synchronized (AdapterService.class) {
                sRefCount++;
                Log.d(TAG, "REFCOUNT: CREATED. INSTANCE_COUNT" + sRefCount);
            }
        }
    }

    public void onProfileConnectionStateChanged(BluetoothDevice device, int profileId,
            int newState, int prevState) {
        Message m = mHandler.obtainMessage(MESSAGE_PROFILE_CONNECTION_STATE_CHANGED);
        m.obj = device;
        m.arg1 = profileId;
        m.arg2 = newState;
        Bundle b = new Bundle(1);
        b.putInt("prevState", prevState);
        m.setData(b);
        mHandler.sendMessage(m);
    }

    public void initProfilePriorities(BluetoothDevice device, ParcelUuid[] mUuids) {
        if(mUuids == null) return;
        Message m = mHandler.obtainMessage(MESSAGE_PROFILE_INIT_PRIORITIES);
        m.obj = device;
        m.arg1 = mUuids.length;
        Bundle b = new Bundle(1);
        for(int i=0; i<mUuids.length; i++) {
            b.putParcelable("uuids" + i, mUuids[i]);
        }
        m.setData(b);
        mHandler.sendMessage(m);
    }

    private void processInitProfilePriorities (BluetoothDevice device, ParcelUuid[] uuids){
        HidService hidService = HidService.getHidService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        HfDeviceService hfDeviceService = HfDeviceService.getHfDeviceService();

        // Set profile priorities only for the profiles discovered on the remote device.
        // This avoids needless auto-connect attempts to profiles non-existent on the remote device
        if ((hidService != null) &&
            (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hid)) &&
            (hidService.getPriority(device) == BluetoothProfile.PRIORITY_UNDEFINED)){
            hidService.setPriority(device,BluetoothProfile.PRIORITY_ON);
        }


        if ((headsetService != null) &&
            ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP) ||
                    BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree)) &&
            (headsetService.getPriority(device) == BluetoothProfile.PRIORITY_UNDEFINED))){
            headsetService.setPriority(device,BluetoothProfile.PRIORITY_ON);
        }
        else if ((hfDeviceService != null) &&
            ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP_AG) ||
                    BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree_AG)) &&
            (hfDeviceService.getPriority(device) == BluetoothProfile.PRIORITY_UNDEFINED))){
            hfDeviceService.setPriority(device,BluetoothProfile.PRIORITY_ON);
        }


        if((a2dpService != null) &&
            (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AudioSink) ||
               BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AudioSource) ||
                    BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AdvAudioDist)) &&
            (a2dpService.getPriority(device) == BluetoothProfile.PRIORITY_UNDEFINED)){
            a2dpService.setPriority(device,BluetoothProfile.PRIORITY_ON);
        }


    }

    private void processProfileStateChanged(BluetoothDevice device, int profileId,
            int newState, int prevState) {

        if (((profileId == BluetoothProfile.A2DP) ||(profileId == BluetoothProfile.HEADSET)||
            (profileId == BluetoothProfile.HF_DEVICE))&&

           (newState == BluetoothProfile.STATE_CONNECTED)){
            if (DBG) debugLog( "Profile connected. Schedule missing profile connection if any");
            connectOtherProfile(device, PROFILE_CONN_CONNECTED,profileId);
            setProfileAutoConnectionPriority(device, profileId);
        }

        IBluetooth.Stub binder = mBinder;
        if (binder != null) {
            try {
                binder.sendConnectionStateChange(device, profileId, newState,prevState);
            } catch (RemoteException re) {
                Log.e(TAG, "",re);
            }
        }
    }

    //BRCM enhancement: advanced settings/ enable/disable profiles
    IProfileStateChangeListener mProfileStateChangeListener;

    /**
     * Register a profile state change listener
     * @param listener
     */
    public void setProfileStateChangeListener(IProfileStateChangeListener listener) {
        synchronized (mProfileServicesState) {
            mProfileStateChangeListener = listener;
        }
    }

    public void unsetProfileStateChangeListener() {
        synchronized (mProfileServicesState) {
            mProfileStateChangeListener=null;
        }
    }
    /**
     * Returns true if the specified profile is started
     * @param profileName
     * @return
     */
    public boolean isProfileStarted(String profileName) {
        synchronized (mProfileServicesState) {
            Integer profileState = mProfileServicesState.get(profileName);
            if (profileState == null) {
                Log.w(TAG,"isProfileEnabled(): profile not found "
                        + StringUtil.toNonNullString(profileName));
                return false;
            }
            Log.w(TAG,"isProfileEnabled(): profile " + StringUtil.toNonNullString(profileName)
                    + ", state= " + profileState);
            return profileState == BluetoothAdapter.STATE_ON;
        }
    }

    /**
     * Turn on/off a specified profile. Returns true of the request is valid.
     * @param profileName
     * @param setEnabled
     * @return
     */
    public boolean setProfileState(String profileName, boolean setEnabled) {
        synchronized (mProfileServicesState) {
            Integer profileState = mProfileServicesState.get(profileName);
            if (profileState == null) {
                Log.w(TAG,"setProfileState(): profile not found "
                        + StringUtil.toNonNullString(profileName));
                mProfileServicesState.put(profileName,BluetoothAdapter.STATE_OFF);
                profileState = BluetoothAdapter.STATE_OFF;
            }
            if (setEnabled && (profileState != BluetoothAdapter.STATE_OFF) ||
                !setEnabled &&(profileState !=BluetoothAdapter.STATE_ON)) {
                Log.w(TAG,"setProfileState(): error setting profile state: "
                        + StringUtil.toNonNullString(profileName) + " to enabled=" +setEnabled
                        +" Current state=" + profileState);
                return false;
            }
            //Set the pending state
            mProfileServicesState.put(profileName,setEnabled?
                    BluetoothAdapter.STATE_TURNING_ON: BluetoothAdapter.STATE_TURNING_OFF);
            Intent intent = new Intent();
            intent.setClassName(this, profileName);
            intent.putExtra(EXTRA_ACTION,ACTION_SERVICE_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE,setEnabled?
                    BluetoothAdapter.STATE_ON:BluetoothAdapter.STATE_OFF);
            Log.d(TAG,"setProfileState(): setting profile "
                    + StringUtil.toNonNullString(profileName) +" to state = " + profileState);
            startService(intent);

            return true;
        }
    }
    //BRCM enhancement: advanced settings

    public void onProfileServiceStateChanged(String serviceName, int state) {
        Message m = mHandler.obtainMessage(MESSAGE_PROFILE_SERVICE_STATE_CHANGED);
        m.obj=serviceName;
        m.arg1 = state;
        mHandler.sendMessage(m);
    }

    private void processProfileServiceStateChanged(String serviceName, int state) {
        boolean doUpdate=false;
        boolean isTurningOn;
        boolean isTurningOff;
        boolean isRadioTurningOn;
        boolean isRadioTurningOff;
        boolean isQuietModeServiceTurningOn;
        boolean isQuietModeServiceTurningOff;

        synchronized (mProfileServicesState) {
            Integer prevState = mProfileServicesState.get(serviceName);
            if (prevState != null && prevState != state) {
                mProfileServicesState.put(serviceName,state);
                doUpdate=true;
            }
            //BRCM enhancement: advanced settings turn on/off profiles
            //dispatch event
            if (mProfileStateChangeListener != null) {
                Message m = mHandler.obtainMessage(MESSAGE_UPDATE_PROFILE_STATE_LISTENER);
                m.obj=serviceName;
                m.arg1 = state;
                m.arg2 = prevState;
                mHandler.sendMessage(m);
            }
            //BRCM enhancement

        }
        if (DBG) Log.d(TAG,"onProfileServiceStateChange: serviceName=" + serviceName
                + ", state = " + state +", doUpdate = " + doUpdate);


        if (!doUpdate) {
            return;
        }

        processDeviceModeSwitchProfileServiceState(serviceName,state);

        synchronized (mAdapterStateMachine) {
            isTurningOff = mAdapterStateMachine.isTurningOff();
            isTurningOn = mAdapterStateMachine.isTurningOn();
            isRadioTurningOn = mAdapterStateMachine.isTurningOnRadio();
            isRadioTurningOff = mAdapterStateMachine.isTurningOffRadio();
            isQuietModeServiceTurningOn = mAdapterStateMachine.isQuietModeServiceTurningOn();
            isQuietModeServiceTurningOff = mAdapterStateMachine.isQuietModeServiceTurningOff();

        }

        Log.d(TAG, "processProfileServiceStateChanged(): serviceName="
                + StringUtil.toNonNullString(serviceName) + ", state=" + state
                + ", Bluetooth isTurningOff=" + isTurningOff
                + ",Bluetooth isTurningOn=" + isTurningOn);

        if (isTurningOff) {
            //Process stop or disable pending
            //Check if all services are stopped if so, do cleanup
            //if (DBG) Log.d(TAG,"Checking if all profiles are stopped...");
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i = mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    if (BluetoothAdapter.STATE_OFF != entry.getValue()) {
                        if (ProfileConfig.isQuietModeProfile(entry.getKey()) &&
                            getQuietmodeRadioCount() > 0) {
                            Log.d(TAG, "Quiet mode profile " + entry.getKey() +
                                  " running since Radio Cnt > 0. ");
                            continue;
                        } else if (ProfileConfig.isProfileConfiguredEnabled(entry.getKey())
                                   == false) {
                             Log.w(TAG, "Profile supported, But not enabled " + entry.getKey());
                        continue;
                        }
                        else {
                            Log.d(TAG, "Profile still running: " + entry.getKey());
                            return;
                        }
                    }
                }
            }
            if (DBG) Log.d(TAG, "All profile services stopped...");
            //Send message to state machine
            mProfilesStarted=false;
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(AdapterState.STOPPED));
        } else if (isTurningOn) {
            //Process start pending
            //Check if all services are started if so, update state
            //if (DBG) Log.d(TAG,"Checking if all profiles are running...");
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i = mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    if (BluetoothAdapter.STATE_ON != entry.getValue()) {
                        Log.d(TAG, "Profile still not running:" + entry.getKey());
                        return;
                    }
                }
            }
            if (DBG) Log.d(TAG, "All profile services started.");
            mProfilesStarted=true;
            //Send message to state machine
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(AdapterState.STARTED));
        }else if (isRadioTurningOn || isQuietModeServiceTurningOn) {
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i = mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    if ((BluetoothAdapter.STATE_ON != entry.getValue())&&
                        (ProfileConfig.isQuietModeProfile(entry.getKey()))){
                        Log.d(TAG, "Profile still not running:" + entry.getKey());
                        return;
                    }
                }
            }
            if (DBG) Log.d(TAG, "All quiet profile services started.");
            //Send message to state machine
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(AdapterState.QUIET_MODE_SERVICES_STARTED));

        }else if (isRadioTurningOff || isQuietModeServiceTurningOff) {
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i = mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    if ((BluetoothAdapter.STATE_OFF != entry.getValue())&&
                        (ProfileConfig.isQuietModeProfile(entry.getKey()))){
                        Log.d(TAG, "Quiet Profile still  running:" + entry.getKey());
                        return;
                    }
                }
            }
            if (DBG) Log.d(TAG, "All quiet profile services stopped.");
            //Send message to state machine
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(AdapterState.QUIET_MODE_SERVICES_STOPPED));

        }
    }

    boolean startQuietModeServices(boolean bStart) {

        debugLog("startQuietModeServices:bStart =" + bStart +" QModeRCnt=" + mQuietModeCnt);
        boolean profileActionInitiated = false;

        if (bStart == true) {
            initSubModules();
            if (mQuietModeCnt == 0) // Nothing to do
            return profileActionInitiated;
       }
        Class[] supportedProfileServices = ProfileConfig.getSupportedProfiles();
        if (bStart == true) {
            synchronized (mProfileServicesState) {
                for (int i=0; i < supportedProfileServices.length;i++) {
                    String profileName = supportedProfileServices[i].getName();
                    if((isProfileStarted(profileName) == false) &&
                       (ProfileConfig.isQuietModeProfile(profileName) == true)){
                        //Add the profile to the list first and start the profile.
                        if (mProfileServicesState.get(profileName) == null)
                            mProfileServicesState.put(profileName,BluetoothAdapter.STATE_OFF);
                            //start the profiles
                            debugLog("startQuietModeServices:Starting  " + profileName);
                            setProfileState(profileName, true);
                            profileActionInitiated = true;
                        }
                    }
                }
            } else {
            //stop the profiles
            for (int i=0; i < supportedProfileServices.length;i++) {
                String profileName = supportedProfileServices[i].getName();
                if((isProfileStarted(profileName) == true) &&
                   (ProfileConfig.isQuietModeProfile(profileName) == true)){
                    debugLog("startQuietModeServices:Stopping " + profileName);
                    setProfileState(profileName, false);
                    profileActionInitiated = true;
                }
            }
        }
        return profileActionInitiated;
    }

    public int getQuietmodeRadioCount() {
        if (DBG) Log.d(TAG, "getQuietmodeRadioCount = " + mQuietModeCnt);
        return mQuietModeCnt;
    }

    private BroadcastReceiver mIntentSdpRegister = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            if (action.equals(BluetoothHfDevice.ACTION_REGISTER_HFDEVICE_SDP)) {
                boolean isRegisterSDP = intent.getBooleanExtra(
                        BluetoothHfDevice.EXTRA_REGISTER_HFDEVICE_SDP, false);
                int currState = mProfileServicesState.get(HfDeviceService.class.getName());
                Log.d(TAG, "isRegisterSDP = "+isRegisterSDP+"currState"+currState);
                if ((currState == BluetoothAdapter.STATE_ON) && (isRegisterSDP))
                    Log.e(TAG,"Wrong Request .HF device SDP already Registered");
                else if ((currState == BluetoothAdapter.STATE_OFF) && (!isRegisterSDP))
                    Log.e(TAG,"Wrong Request .HF device SDP already Unregistered");
                setProfileState(HfDeviceService.class.getName(), isRegisterSDP);
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) debugLog("onCreate");
        mBinder = new AdapterServiceBinder(this);
        mAdapterProperties = new AdapterProperties(this);
        mAdapterStateMachine =  AdapterState.make(this, mAdapterProperties);
        mJniCallbacks =  new JniCallbacks(mAdapterStateMachine, mAdapterProperties);
        initNative();
        mNativeAvailable=true;
        mCallbacks = new RemoteCallbackList<IBluetoothCallback>();
        //Load the name and address
        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_BDADDR);
        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_BDNAME);

        IntentFilter intentFilter1 = new IntentFilter();
        intentFilter1.addAction(BluetoothHfDevice.ACTION_REGISTER_HFDEVICE_SDP);
        registerReceiver(mIntentSdpRegister, intentFilter1);

    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) debugLog("onBind");
        return mBinder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        if (DBG) debugLog("onUnbind, calling cleanup");
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mIntentSdpRegister);
        debugLog("****onDestroy()********");
    }

    void initSubModules (){
        if (mInitDone == false) {
            mRemoteDevices = new RemoteDevices(this);
            mAdapterProperties.init(mRemoteDevices);
            mBondStateMachine = BondStateMachine.make(this, mAdapterProperties, mRemoteDevices);
            mJniCallbacks.init(mBondStateMachine,mRemoteDevices);
            setAdapterService(this);
            mInitDone = true;
        }
    }

    void processStart() {
        if (DBG) debugLog("processStart()");
        //Broadcom change: add advanced settings
        Class[] supportedProfileServices = ProfileConfig.getSupportedProfiles();
        //Initialize data objects
        for (int i=0; i < supportedProfileServices.length;i++) {
            String profileName = supportedProfileServices[i].getName();
            if (ProfileConfig.isProfileConfiguredEnabled(profileName)){
                if (isProfileStarted(profileName) == false) {
                    mProfileServicesState.put(profileName, BluetoothAdapter.STATE_OFF);
                }
            } else {
                Log.w(TAG,"processStart(): profile not enabled: "  + profileName);
            }
        }
        //Broadcom change: end

        initSubModules();

        if (DBG) {debugLog("processStart(): Make Bond State Machine");}
        mBondStateMachine = BondStateMachine.make(this, mAdapterProperties, mRemoteDevices);

        checkAndSetDeviceModeProperty();
        //Start profile services
        if (!mProfilesStarted && supportedProfileServices.length >0) {
            //Startup all profile services
            setProfileServiceState(supportedProfileServices,BluetoothAdapter.STATE_ON);
        }else {
            if (DBG) {debugLog("processStart(): Profile Services alreay started");}
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(AdapterState.STARTED));
        }
    }

    void startBluetoothDisable() {
        mAdapterStateMachine.sendMessage
            (mAdapterStateMachine.obtainMessage(AdapterState.BEGIN_DISABLE));
    }

    private void checkAndSetDeviceModeProperty() {

         /* The last set device mode value before BT turn off is retrieved from
         sharedpreference while BT turning on Set this property before
        enabling BT AdapterService,JNI layer and BTIF layer use this
        value as the default device mode*/
         Context context = getApplicationContext();
         SharedPreferences settings = PreferenceManager.
             getDefaultSharedPreferences(context);
         int deviceMode = settings.getInt("DEVICEMODE",-1);
         mAdapterStateMachine.setDeviceMode(deviceMode);
         Log.d(TAG,"deviceMode from shared preference   " + deviceMode );
         if( deviceMode == -1 )
         {
             Resources r = context.getResources();
             boolean isDevicemode = r
                     .getBoolean(com.android.bluetooth.R.bool.phone_mode);
             if( isDevicemode )
                mAdapterStateMachine.setDeviceMode(DEFAULT_MODE);
             else
                mAdapterStateMachine.setDeviceMode(HEADSET_MODE);
         }
         Log.d(TAG,"AdapterState deviceMode" + deviceMode );
         setDeviceModeProperty();

    }

    boolean stopProfileServices() {
        //BRCM enhancement: profile advanced settings
        //Class[] supportedProfileServices = Config.getSupportedProfiles();
        Class[] supportedProfileServices = ProfileConfig.getSupportedProfiles();
        if (mProfilesStarted && supportedProfileServices.length>0) {
            setProfileServiceState(supportedProfileServices,BluetoothAdapter.STATE_OFF);
            return true;
        } else {
            if (DBG) {
                debugLog("stopProfileServices(): No profiles services to stop or already stopped.");
            }
            return false;
        }
    }

     void updateAdapterState(int prevState, int newState){
        if (mCallbacks !=null) {
            int n=mCallbacks.beginBroadcast();
            Log.d(TAG,"Broadcasting updateAdapterState() to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothStateChange(prevState,newState);
                }  catch (RemoteException e) {
                    Log.e(TAG, "Unable to call onBluetoothStateChange() on callback #" + i, e);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    void cleanup () {
        if (DBG)debugLog("cleanup()");
        if (mCleaningUp) {
            Log.w(TAG,"*************service already starting to cleanup..."
                    +"Ignoring cleanup request.........");
            return;
        }

        mCleaningUp = true;

        if (mAdapterStateMachine != null) {
            mAdapterStateMachine.doQuit();
            mAdapterStateMachine.cleanup();
        }

        if (mBondStateMachine != null) {
            mBondStateMachine.doQuit();
            mBondStateMachine.cleanup();
        }

        if (mRemoteDevices != null) {
            mRemoteDevices.cleanup();
        }

        if (mNativeAvailable) {
            Log.d(TAG, "Cleaning up adapter native....");
            cleanupNative();
            Log.d(TAG, "Done cleaning up adapter native....");
            mNativeAvailable=false;
        }

        if (mAdapterProperties != null) {
            mAdapterProperties.cleanup();
        }

        if (mJniCallbacks != null) {
            mJniCallbacks.cleanup();
        }

        if (mProfileServicesState != null) {
            mProfileServicesState.clear();
        }

        mRadioCount = 0;
        mQuietModeCnt= 0;

        clearAdapterService();
        unregisterAllRadioClients();

        if (mBinder != null) {
            mBinder.cleanup();
            mBinder = null;  //Do not remove. Otherwise Binder leak!
        }

        if (mCallbacks !=null) {
            mCallbacks.kill();
        }

        mInitDone = false;
        if (DBG)debugLog("cleanup() done");

        if (DBG) debugLog("bluetooth process exit normally after clean up...");
        System.exit(0);
    }

    private static final int MESSAGE_PROFILE_SERVICE_STATE_CHANGED =1;
    private static final int MESSAGE_PROFILE_CONNECTION_STATE_CHANGED=20;
    private static final int MESSAGE_CONNECT_OTHER_PROFILES = 30;
    private static final int MESSAGE_PROFILE_INIT_PRIORITIES=40;
    private static final int CONNECT_OTHER_PROFILES_TIMEOUT= 6000;
    private static final int CONNECT_OTHER_PROFILES_DEVICE_MODE_TIMEOUT= 8000;

    private static final int MESSAGE_UPDATE_PROFILE_STATE_LISTENER= 10000;



    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (DBG) debugLog("Message: " + msg.what);

            switch (msg.what) {
                case MESSAGE_PROFILE_SERVICE_STATE_CHANGED: {
                    if(DBG) debugLog("MESSAGE_PROFILE_SERVICE_STATE_CHANGED");
                    processProfileServiceStateChanged((String) msg.obj, msg.arg1);
                }
                    break;
                case MESSAGE_PROFILE_CONNECTION_STATE_CHANGED: {
                    if (DBG) debugLog( "MESSAGE_PROFILE_CONNECTION_STATE_CHANGED");
                    processProfileStateChanged((BluetoothDevice) msg.obj,msg.arg1,msg.arg2,
                            msg.getData().getInt("prevState",BluetoothAdapter.ERROR));
                }
                    break;
                case MESSAGE_PROFILE_INIT_PRIORITIES: {
                    if (DBG) debugLog( "MESSAGE_PROFILE_INIT_PRIORITIES");
                    ParcelUuid[] mUuids = new ParcelUuid[msg.arg1];
                    for(int i=0; i<mUuids.length; i++) {
                        mUuids[i] = msg.getData().getParcelable("uuids" + i);
                    }
                    processInitProfilePriorities((BluetoothDevice) msg.obj,
                            mUuids);
                }
                    break;
                case MESSAGE_CONNECT_OTHER_PROFILES: {
                    if (DBG) debugLog( "MESSAGE_CONNECT_OTHER_PROFILES");
                    processConnectOtherProfiles((BluetoothDevice) msg.obj,msg.arg1,msg.arg2);
                }
                    break;
                //Broadcom enhancement: advanced settings: profile on/off
                case MESSAGE_UPDATE_PROFILE_STATE_LISTENER: {
                    if (DBG) debugLog( "MESSAGE_UPDATE_PROFILE_STATE_LISTENER");
                    try {
                        IProfileStateChangeListener listener = mProfileStateChangeListener;
                        if (listener != null) {
                            listener.onProfileStateChanged((String)msg.obj,msg.arg1,msg.arg2);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG,
                                "MESSAGE_UPDATE_PROFILE_STATE_LISTENER: error calling listener",t);
                    }
                }
                    break;
                //Broadcom enhancement
            }
        }
    };

    @SuppressWarnings("rawtypes")
    private void setProfileServiceState(Class[] services, int state) {
        if (state != BluetoothAdapter.STATE_ON && state != BluetoothAdapter.STATE_OFF) {
            Log.w(TAG,"setProfileServiceState(): invalid state...Leaving...");
            return;
        }

        int expectedCurrentState= BluetoothAdapter.STATE_OFF;
        int pendingState = BluetoothAdapter.STATE_TURNING_ON;
        if (state == BluetoothAdapter.STATE_OFF) {
            expectedCurrentState= BluetoothAdapter.STATE_ON;
            pendingState = BluetoothAdapter.STATE_TURNING_OFF;
        }

        synchronized (mProfileServicesState) {
            for (int i=0; i <services.length;i++) {
                String serviceName = services[i].getName();
                Integer serviceState = mProfileServicesState.get(serviceName);
                if(serviceState != null && serviceState != expectedCurrentState) {
                    Log.w(TAG, "Unable to " +
                          (state == BluetoothAdapter.STATE_OFF? "start" : "stop" )
                            +" service " + serviceName+". Invalid state: " + serviceState);
                    continue;
                }

                /* don't turn off if Radio is enabled &&  service to be turned off is Gatt*/
                Log.w(TAG, "check For Quiet mode profile " + state +" RadioCount =" +
                      getQuietmodeRadioCount() + " srv name=" +serviceName);
                if ((state  == BluetoothAdapter.STATE_OFF) &&
                    (getQuietmodeRadioCount() > 0) &&
                    (ProfileConfig.isQuietModeProfile(serviceName))) {
                        Log.w(TAG, "Skipping to stop Quiet mode profile " + serviceName);
                        continue;
                    }else if (ProfileConfig.isProfileConfiguredEnabled(serviceName) == false) {
                         Log.w(TAG, "Profile supported, But not enabled " + serviceName);
                        continue;
                 }else {
                    Log.w(TAG, "Not skipping " + serviceName );
                }
                if (DBG) {
                    Log.w(TAG, (state == BluetoothAdapter.STATE_OFF? "Stopping" : "Starting" )
                            +" service " + serviceName);
                }

                mProfileServicesState.put(serviceName,pendingState);
                Intent intent = new Intent(this,services[i]);
                intent.putExtra(EXTRA_ACTION,ACTION_SERVICE_STATE_CHANGED);
                intent.putExtra(BluetoothAdapter.EXTRA_STATE,state);
                startService(intent);
            }
        }
    }

    private boolean isAvailable() {
        return !mCleaningUp;
    }

    /**
     * Handlers for incoming service calls
     */
    private AdapterServiceBinder mBinder;

    /**
     * The Binder implementation must be declared to be a static class, with
     * the AdapterService instance passed in the constructor. Furthermore,
     * when the AdapterService shuts down, the reference to the AdapterService
     * must be explicitly removed.
     *
     * Otherwise, a memory leak can occur from repeated starting/stopping the
     * service...Please refer to android.os.Binder for further details on
     * why an inner instance class should be avoided.
     *
     */
    private static class AdapterServiceBinder extends IBluetooth.Stub {
        private AdapterService mService;

        public AdapterServiceBinder(AdapterService svc) {
            mService = svc;
        }
        public boolean cleanup() {
            mService = null;
            return true;
        }

        public AdapterService getService() {
            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public boolean isEnabled() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) return false;
            return service.isEnabled();
        }

        public boolean isRadioEnabled() {
            AdapterService service = getService();
            if (service == null) return false;
            return service.isRadioEnabled();
        }

        public int getState() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) return  BluetoothAdapter.STATE_OFF;
            return service.getState();
        }

        public boolean enable() {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"enable(): not allowed for non-active user and non system user");
                return false;
	    }

            AdapterService service = getService();
            if (service == null) return false;
            return service.enable();
        }

        public boolean enableNoAutoConnect() {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"enableNoAuto(): not allowed for non-active user and non system user");
                return false;
	    }

            AdapterService service = getService();
            if (service == null) return false;
            return service.enableNoAutoConnect();
        }

        public boolean disable() {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"disable(): not allowed for non-active user and non system user");
                return false;
	    }

            AdapterService service = getService();
            if (service == null) return false;
            return service.disable();
        }

        public boolean enableRadio(int radioType) {
            AdapterService service = getService();
            if (service == null) return false;
            return service.enableRadio(radioType);
        }

        public boolean disableRadio(int radioType) {
            AdapterService service = getService();
            if (service == null) return false;
            return service.disableRadio(radioType);
        }

        public boolean registerRadioClient (IBluetoothManagerCallback callback ,ParcelUuid uuid, int radioType) {
            AdapterService service = getService();
            if (service == null) return false;
            return service.registerRadioClient(callback, uuid, radioType);
        }

        public boolean unregisterRadioClient(ParcelUuid uuid) {
            AdapterService service = getService();
            if (service == null) return false;
            return service.unregisterRadioClient(uuid);
        }

        public void initWakeLock(ParcelFileDescriptor pfd_lock, ParcelFileDescriptor pfd_unlock) {
            Log.d(TAG, "initWakeLock, pfd lock: " + pfd_lock + ", pfd unlock: " + pfd_unlock);
            if(Binder.getCallingUid() == Process.SYSTEM_UID) {
                AdapterService service = getService();
                if(service != null) {
                    service.initWakeLock(pfd_lock, pfd_unlock);
                    return;
                }
            } else Log.w(TAG,"initWakeLock(): not allowed for non-active user and non system user");
            try {
                pfd_lock.close();
                pfd_unlock.close();
            } catch(Exception e){
            }
        }

        public String getAddress() {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"getAddress(): not allowed for non-active user and non system user");
                return null;
	    }

            AdapterService service = getService();
            if (service == null) return null;
            return service.getAddress();
        }

        public ParcelUuid[] getUuids() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getUuids(): not allowed for non-active user");
                return new ParcelUuid[0];
            }

            AdapterService service = getService();
            if (service == null) return new ParcelUuid[0];
            return service.getUuids();
        }

        public String getName() {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"getName(): not allowed for non-active user and non system user");
                return null;
	    }

            AdapterService service = getService();
            if (service == null) return null;
            return service.getName();
        }

        public boolean setName(String name) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setName(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setName(name);
        }

        public int getScanMode() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getScanMode(): not allowed for non-active user");
                return BluetoothAdapter.SCAN_MODE_NONE;
            }

            AdapterService service = getService();
            if (service == null) return BluetoothAdapter.SCAN_MODE_NONE;
            return service.getScanMode();
        }

        public boolean setScanMode(int mode, int duration) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setScanMode(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setScanMode(mode,duration);
        }

        public int getDiscoverableTimeout() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getDiscoverableTimeout(): not allowed for non-active user");
                return 0;
            }

            AdapterService service = getService();
            if (service == null) return 0;
            return service.getDiscoverableTimeout();
        }

        public boolean setDiscoverableTimeout(int timeout) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setDiscoverableTimeout(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setDiscoverableTimeout(timeout);
        }

        public boolean startDiscovery() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"startDiscovery(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.startDiscovery();
        }

        public boolean cancelDiscovery() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"cancelDiscovery(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.cancelDiscovery();
        }
        public boolean isDiscovering() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"isDiscovering(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.isDiscovering();
        }

        public BluetoothDevice[] getBondedDevices() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) return new BluetoothDevice[0];
            return service.getBondedDevices();
        }

        public int getAdapterConnectionState() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) return BluetoothAdapter.STATE_DISCONNECTED;
            return service.getAdapterConnectionState();
        }

        public int getProfileConnectionState(int profile) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getProfileConnectionState: not allowed for non-active user");
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            AdapterService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getProfileConnectionState(profile);
        }

        public boolean createBond(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"createBond(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.createBond(device);
        }

        public boolean cancelBondProcess(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"cancelBondProcess(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.cancelBondProcess(device);
        }

        public boolean removeBond(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"removeBond(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.removeBond(device);
        }

        public int getBondState(BluetoothDevice device) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) return BluetoothDevice.BOND_NONE;
            return service.getBondState(device);
        }

        public String getRemoteName(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getRemoteName(): not allowed for non-active user");
                return null;
            }

            AdapterService service = getService();
            if (service == null) return null;
            return service.getRemoteName(device);
        }

        public int getRemoteType(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getRemoteType(): not allowed for non-active user");
                return BluetoothDevice.DEVICE_TYPE_UNKNOWN;
            }

            AdapterService service = getService();
            if (service == null) return BluetoothDevice.DEVICE_TYPE_UNKNOWN;
            return service.getRemoteType(device);
        }

        public String getRemoteAlias(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getRemoteAlias(): not allowed for non-active user");
                return null;
            }

            AdapterService service = getService();
            if (service == null) return null;
            return service.getRemoteAlias(device);
        }

        public boolean setRemoteAlias(BluetoothDevice device, String name) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setRemoteAlias(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setRemoteAlias(device, name);
        }

        public int getRemoteClass(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getRemoteClass(): not allowed for non-active user");
                return 0;
            }

            AdapterService service = getService();
            if (service == null) return 0;
            return service.getRemoteClass(device);
        }

        public ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"getRemoteUuids(): not allowed for non-active user");
                return new ParcelUuid[0];
            }

            AdapterService service = getService();
            if (service == null) return new ParcelUuid[0];
            return service.getRemoteUuids(device);
        }

        public boolean fetchRemoteUuids(BluetoothDevice device) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"fetchRemoteUuids(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.fetchRemoteUuids(device);
        }

        public boolean setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setPin(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setPin(device, accept, len, pinCode);
        }

        public boolean setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setPasskey(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setPasskey(device, accept, len, passkey);
        }

        public boolean setPairingConfirmation(BluetoothDevice device, boolean accept) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"setPairingConfirmation(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.setPairingConfirmation(device, accept);
        }

        public boolean authorizeService(BluetoothDevice device, ParcelUuid ServiceUuid,
                                           boolean authorize, boolean autoReply) {
            AdapterService service = getService();
            if (service == null) return false;
            return service.authorizeService(device, ServiceUuid,authorize,autoReply);
       }

        public void sendConnectionStateChange(BluetoothDevice
                device, int profile, int state, int prevState) {
            AdapterService service = getService();
            if (service == null) return;
            service.sendConnectionStateChange(device, profile, state, prevState);
        }

        public ParcelFileDescriptor connectSocket(BluetoothDevice device, int type,
                                                  ParcelUuid uuid, int port, int flag) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"connectSocket(): not allowed for non-active user");
                return null;
            }

            AdapterService service = getService();
            if (service == null) return null;
            return service.connectSocket(device, type, uuid, port, flag);
        }

        public ParcelFileDescriptor createSocketChannel(int type, String serviceName,
                                                        ParcelUuid uuid, int port, int flag) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"createSocketChannel(): not allowed for non-active user");
                return null;
            }

            AdapterService service = getService();
            if (service == null) return null;
            return service.createSocketChannel(type, serviceName, uuid, port, flag);
        }

        public boolean configHciSnoopLog(boolean enable) {
            if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!Utils.checkCaller())) {
                Log.w(TAG,"configHciSnoopLog(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.configHciSnoopLog(enable);
        }

        public boolean sspDebugConfigure(boolean enable) {
             if (!Utils.checkCaller()) {
                 Log.w(TAG,"sspDebugConfigure(): not allowed for non-active user");
                 return false;
             }

             AdapterService service = getService();
             if (service == null) return false;
             return service.sspDebugConfigure(enable);
        }

        public boolean dutModeConfigure(boolean enable) {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"dutModeConfigure(): not allowed for non-active user");
                return false;
            }

            AdapterService service = getService();
            if (service == null) return false;
            return service.dutModeConfigure(enable);
       }

        public void registerCallback(IBluetoothCallback cb) {
            AdapterService service = getService();
            if (service == null) return ;
            service.registerCallback(cb);
         }

         public void unregisterCallback(IBluetoothCallback cb) {
             AdapterService service = getService();
             if (service == null) return ;
             service.unregisterCallback(cb);
         }

         public boolean setSdpRecord(boolean enable, int uuid) {
             if (!Utils.checkCaller()) {
                 Log.w(TAG,"setPin(): not allowed for non-active user");
                 return false;
             }

             AdapterService service = getService();
             if (service == null) return false;
             return service.setSdpRecord(enable, uuid);
         }
    };


    //----API Methods--------
     boolean isEnabled() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getState() == BluetoothAdapter.STATE_ON;
    }

     public boolean isRadioEnabled() {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
         if (mAdapterStateMachine.isRadioOn() &&mRadioCount > 0)
            return true;
         else
            return false;
     }

     int getState() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        if (mAdapterProperties == null){
            return  BluetoothAdapter.STATE_OFF;
        }
        else {
            if (DBG) debugLog("getState(): mAdapterProperties: " + mAdapterProperties);
            return mAdapterProperties.getState();
        }
    }

     boolean enable() {
        //return enable (false);
        return enable (true);
    }

     boolean enableRadio(int radioType) {
       enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
               "Need BLUETOOTH ADMIN permission");
       if (DBG) debugLog("enableRadio() called with RadioType = " + radioType);

       mRadioCount++;
       switch(radioType) {
         case BluetoothRadioManager.RADIO_TYPE_BLE:
             mQuietModeCnt++;
             updateRadioCountNative (radioType,mQuietModeCnt);
             break;

         case BluetoothRadioManager.RADIO_TYPE_FM:
             mFmCnt++;
             updateRadioCountNative (radioType,mFmCnt);
             break;

         case BluetoothRadioManager.RADIO_TYPE_ANT:
             mAntCnt++;
             updateRadioCountNative (radioType,mAntCnt);
             break;

         default:
             break;
       }
      if ( mRadioCount == 1) {// Is Radio not turned on yet..?
          Message m = mAdapterStateMachine.obtainMessage(AdapterState.USER_TURN_ON_RADIO);
          mAdapterStateMachine.sendMessage(m);
      } else if ((mQuietModeCnt == 1) &&
                 (radioType == BluetoothRadioManager.RADIO_TYPE_BLE) &&
                 (getState() != BluetoothAdapter.STATE_ON)) {
          // Radio is already enabled,
          //but BT Quiet mode is getting enabled for first time
          Message m  =
                 mAdapterStateMachine.obtainMessage(AdapterState.START_QUIET_MODE_SERVICES);
          mAdapterStateMachine.sendMessage(m);
     }
      return true;
     }

     boolean disableRadio(int radioType) {
         enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                 "Need BLUETOOTH ADMIN permission");
         if (DBG) debugLog("disableRadio() called...");

         mRadioCount--;

       switch(radioType) {
         case BluetoothRadioManager.RADIO_TYPE_BLE:
             mQuietModeCnt--;
              break;

         case BluetoothRadioManager.RADIO_TYPE_FM:
             mFmCnt--;
             updateRadioCountNative (radioType,mFmCnt);
             break;

         case BluetoothRadioManager.RADIO_TYPE_ANT:
             mAntCnt--;
             updateRadioCountNative (radioType,mAntCnt);
             break;
         case BluetoothRadioManager.RADIO_TYPE_ALL:
            mRadioCount  = 0;
            break;

         default:
             break;
       }
       // Update the stack

       updateRadioCountNative (radioType,mQuietModeCnt);

             if (mRadioCount == 0) {
           Message m =
                  mAdapterStateMachine.obtainMessage(AdapterState.USER_TURN_OFF_RADIO);
           mAdapterStateMachine.sendMessage(m);
      } else if ((mQuietModeCnt == 0) && (getState() != BluetoothAdapter.STATE_ON)) {
                 Message m  =
                        mAdapterStateMachine.obtainMessage(AdapterState.STOP_QUIET_MODE_SERVICES);
                 mAdapterStateMachine.sendMessage(m);
      }

       return true;
     }


      class RadioClientDeathRecipient implements IBinder.DeathRecipient {
          private int mRadioType;

          public RadioClientDeathRecipient(int radioType) {
               mRadioType = radioType;
          }
          public void binderDied() {
              if (DBG) Log.d(TAG, "Binder is dead - DecrementRadioCount for radioType = "
                            + mRadioType);
              disableRadio(mRadioType);
          }
      }

      public boolean registerRadioClient (IBluetoothManagerCallback callback ,
                                             ParcelUuid uuid, int radioType) {
          RadioClientDeathRecipient deathRecepient =
              new RadioClientDeathRecipient(radioType);
          if (mRadioClientDeathRecipient.containsKey(uuid)){
              if (DBG) Log.d(TAG, "Death Recepient already registered ");
              return false;
          }
          try {
              IBinder binder = ((IInterface)callback).asBinder();
              binder.linkToDeath(deathRecepient, 0);
              mRadioClientDeathRecipient.put(uuid,deathRecepient);
          } catch (RemoteException e) {
              Log.e(TAG, "Unable to link deathRecipient for app id");
          }
          return true;
       }

       public boolean unregisterRadioClient(ParcelUuid uuid) {
           if (!mRadioClientDeathRecipient.containsKey(uuid)){
               if (DBG) Log.d(TAG, "Death Recepient not registered ");
               return false;
           }
           Iterator <Map.Entry<ParcelUuid,RadioClientDeathRecipient>> it
                       = mRadioClientDeathRecipient.entrySet().iterator();
           while (it.hasNext())
           {
               Map.Entry<ParcelUuid,RadioClientDeathRecipient> entry   = it.next();
               ParcelUuid indexUuid = entry.getKey();

               RadioClientDeathRecipient deathRecipient = entry.getValue();
               if ((deathRecipient != null) &&(uuid.equals(indexUuid))){
                   try {
                       IBinder binder = ((IInterface)this).asBinder();
                        binder.unlinkToDeath(deathRecipient, 0);
                   } catch (Exception e) {
                       Log.e(TAG, "Unable to unlink deathRecipient for app id");
                   }
                   it.remove();
              }
           }
           return true;
       }

      public boolean enableNoAutoConnect() {
         return enable (true);
     }

     public synchronized boolean enable(boolean quietMode) {
         enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                        "Need BLUETOOTH ADMIN permission");
         if (DBG)debugLog("Enable called with quiet mode status =  " + mQuietmode);
         mQuietmode  = quietMode;
         Message m =
                 mAdapterStateMachine.obtainMessage(AdapterState.USER_TURN_ON);
         mAdapterStateMachine.sendMessage(m);
         return true;
     }

     boolean disable() {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        if (DBG) debugLog("disable() called...");
        Message m =
                mAdapterStateMachine.obtainMessage(AdapterState.USER_TURN_OFF);
        mAdapterStateMachine.sendMessage(m);
        return true;
    }
    void initWakeLock(ParcelFileDescriptor pfd_lock, ParcelFileDescriptor pfd_unlock) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                "Need BLUETOOTH ADMIN permission");
        initWakeLockNative(pfd_lock.detachFd(), pfd_unlock.detachFd());
    }
     String getAddress() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        String addrString = null;
        byte[] address = mAdapterProperties.getAddress();
        return Utils.getAddressStringFromByte(address);
    }

     ParcelUuid[] getUuids() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getUuids();
    }

     String getName() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                       "Need BLUETOOTH permission");

        try {
            return mAdapterProperties.getName();
        } catch (Throwable t) {
            Log.d(TAG, "Unexpected exception while calling getName()",t);
        }
        return null;
    }

     boolean setName(String name) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        return mAdapterProperties.setName(name);
    }

     int getScanMode() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getScanMode();
    }

     boolean setScanMode(int mode, int duration) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        setDiscoverableTimeout(duration);

        int newMode = convertScanModeToHal(mode);
        return mAdapterProperties.setScanMode(newMode);
    }

     int getDiscoverableTimeout() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getDiscoverableTimeout();
    }

     boolean setDiscoverableTimeout(int timeout) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.setDiscoverableTimeout(timeout);
    }

     boolean startDiscovery() {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        return startDiscoveryNative();
    }

     boolean cancelDiscovery() {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");

        return cancelDiscoveryNative();
    }

     boolean isDiscovering() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.isDiscovering();
    }

     BluetoothDevice[] getBondedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        debugLog("Get Bonded Devices being called");
        return mAdapterProperties.getBondedDevices();
    }

     int getAdapterConnectionState() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getConnectionState();
    }

     int getProfileConnectionState(int profile) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        return mAdapterProperties.getProfileConnectionState(profile);
    }

     boolean createBond(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
            "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp != null && deviceProp.getBondState() != BluetoothDevice.BOND_NONE) {
            return false;
        }

        // Pairing is unreliable while scanning, so cancel discovery
        // Note, remove this when native stack improves
        cancelDiscoveryNative();

        Message msg = mBondStateMachine.obtainMessage(BondStateMachine.CREATE_BOND);
        msg.obj = device;
        mBondStateMachine.sendMessage(msg);
        return true;
    }

      public boolean isQuietModeEnabled() {
          if (DBG) debugLog("Quiet mode Enabled = " + mQuietmode);
          return mQuietmode;
     }

     public void autoConnect(){
        if (getState() != BluetoothAdapter.STATE_ON){
             errorLog("BT is not ON. Exiting autoConnect");
             return;
         }
         if (isQuietModeEnabled() == false) {
            if (DBG) debugLog( "Initiate auto connection on BT on...");
             autoConnectHeadset();
             autoConnectA2dp();
         }
         else {
             if (DBG) debugLog( "BT is in Quiet mode. Not initiating  auto connections");
         }
    }

     private void autoConnectHeadset(){
         if (autoConnectHfDevice())
            return;

        HeadsetService  hsService = HeadsetService.getHeadsetService();

        BluetoothDevice bondedDevices[] = getBondedDevices();
        if ((bondedDevices == null) ||(hsService == null)) {
            return;
        }
        for (BluetoothDevice device : bondedDevices) {
            if (hsService.getPriority(device) == BluetoothProfile.PRIORITY_AUTO_CONNECT ){
                 Log.d(TAG,"Auto Connecting Headset Profile with device " + device.toString());
                 hsService.connect(device);
             }
        }
    }

     private boolean autoConnectHfDevice(){
         HfDeviceService  hfDeviceService = HfDeviceService.getHfDeviceService();
         boolean isAutoConnectInitiated = false;
         BluetoothDevice bondedDevices[] = getBondedDevices();
         if ((bondedDevices == null) ||(hfDeviceService == null)) {
             return isAutoConnectInitiated;
         }
         for (BluetoothDevice device : bondedDevices) {
             if (hfDeviceService.getPriority(device) == BluetoothProfile.PRIORITY_AUTO_CONNECT ){
                 Log.d(TAG,"Auto Connecting Headset Profile with device " + device.toString());
                 hfDeviceService.connect(device);
                 isAutoConnectInitiated = true;
             }
         }
         return isAutoConnectInitiated;
     }

     private void autoConnectA2dp(){
        A2dpService a2dpSservice = A2dpService.getA2dpService();
        BluetoothDevice bondedDevices[] = getBondedDevices();
        if ((bondedDevices == null) ||(a2dpSservice == null)) {
            return;
        }
        for (BluetoothDevice device : bondedDevices) {
            if (a2dpSservice.getPriority(device) == BluetoothProfile.PRIORITY_AUTO_CONNECT ){
                Log.d(TAG,"Auto Connecting A2DP Profile with device " + device.toString());
                a2dpSservice.connect(device);
                }
        }
    }

     public void connectOtherProfile(BluetoothDevice device,
                     int firstProfileStatus,int profileId){
        if ((mHandler.hasMessages(MESSAGE_CONNECT_OTHER_PROFILES) == false)){
            if(isQuietModeEnabled()== false){
                Message m = mHandler.obtainMessage(MESSAGE_CONNECT_OTHER_PROFILES);
                m.obj = device;
                m.arg1 = (int)firstProfileStatus;
                m.arg2 = (int)profileId;
                if (DEFAULT_MODE == mAdapterStateMachine.getDeviceMode())
                 mHandler.sendMessageDelayed(m,CONNECT_OTHER_PROFILES_TIMEOUT);
                else
                 mHandler.sendMessageDelayed(m,CONNECT_OTHER_PROFILES_DEVICE_MODE_TIMEOUT);
            }
        }
        //Related profile already connected .Remove timer.
        else
            mHandler.removeMessages(MESSAGE_CONNECT_OTHER_PROFILES);
   }

     /**
      * Return  the switched device mode.
      * @param
      * @return current device mode
      */
     public int getDeviceMode() {
        Log.d(TAG,"getDeviceMode  deviceMode "+  mAdapterStateMachine.getDeviceMode());
            return mAdapterStateMachine.getDeviceMode();
     }

     /**
      * Switch the device mode TV mode and Headset Mode. Returns true of the request is valid.
      * @param devicemode
      * @return
      */
    public boolean setDeviceMode(int deviceMode) {
        if (getState()!= BluetoothAdapter.STATE_ON){
           return false;
        }

        Message msg = mAdapterStateMachine.
            obtainMessage(AdapterState.USER_DEVICE_MODE_SWITCH);
        msg.arg1 = deviceMode;
        mAdapterStateMachine.sendMessage(msg);
        return true;
    }


    public boolean setProfileStateForDeviceModeSwitch(int deviceMode, boolean enable) {
        boolean profileStateSet = false;
        if (DEFAULT_MODE == deviceMode) {
            Class[] supportedProfileServices = ProfileConfig.getSupportedProfiles();
                for (int i=0; i < supportedProfileServices.length;i++) {
                    String profileName = supportedProfileServices[i].getName();
                    if (ProfileConfig.isPhoneModeProfile(profileName)){
                        ProfileConfig.saveProfileSetting(profileName, enable);
                        setProfileState(profileName, enable);
                        if (!profileStateSet)
                            profileStateSet = true;
                    } else {
                        Log.w(TAG,"Profile not configured for Device Mode Cfg: "  + profileName);
                    }
                }
       } else if (HEADSET_MODE == deviceMode) {
            Class[] supportedProfileServices = ProfileConfig.getSupportedProfiles();
            for (int i=0; i < supportedProfileServices.length;i++) {
                String profileName = supportedProfileServices[i].getName();
                if (ProfileConfig.isDeviceModeProfile(profileName)){
                        ProfileConfig.saveProfileSetting(profileName, enable);
                        setProfileState(profileName, enable);
                        if (!profileStateSet)
                            profileStateSet = true;
                } else {
                    Log.w(TAG,"Profile not configured for Device Mode Cfg: "  + profileName);
                }
            }
        }
       return profileStateSet;
    }


    public void broadcastDeviceModeSwitchStatus () {
        Log.d(TAG, "Send ACTION_UUID_CHANGED");
        Intent intent = new Intent(BluetoothAdapter.ACTION_UUID_CHANGED);
        sendBroadcast(intent, BLUETOOTH_PERM);
        try {
            IProfileStateChangeListener listener = mProfileStateChangeListener;
            if (listener != null) {
                listener.onDeviceModeSwitchComplete();
            }
        } catch (Throwable t) {
            Log.e(TAG,
                    "onDeviceModeSwitchComplete: error calling listener",t);
        }
    }

    private void processDeviceModeSwitchProfileServiceState(String serviceName,
                                                                int state) {
        boolean isDeviceModeSwitchTurningOff;
        boolean isDeviceModeSwitchTurningOn;
        int pendingDeviceModeState;
        synchronized (mAdapterStateMachine) {
            isDeviceModeSwitchTurningOff = mAdapterStateMachine.isDeviceModeSwitchTurningOff();
            isDeviceModeSwitchTurningOn = mAdapterStateMachine.isDeviceModeSwitchTurningOn();
            pendingDeviceModeState = mAdapterStateMachine.getPendingDeviceMode();
        }

        Log.d(TAG, "processDeviceModeSwitchProfileServiceState(): serviceName="
                + StringUtil.toNonNullString(serviceName) + ", state=" + state
                + ", DeviceMode isDeviceModeSwitchTurningOff=" + isDeviceModeSwitchTurningOff
                + ",DeviceMode isDeviceModeSwitchTurningOn=" + isDeviceModeSwitchTurningOn);

        if (isDeviceModeSwitchTurningOff) {
            //Check if all Device Switch Mode services are stopped if so, do cleanup
            //if (DBG) Log.d(TAG,"Checking if all Device Switch Mode profiles are stopped...");
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i = mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    boolean isDeviceSwitchModeProfile;

                    if (HEADSET_MODE  == pendingDeviceModeState)
                        isDeviceSwitchModeProfile =
                                    ProfileConfig.isPhoneModeProfile(entry.getKey());
                    else //if (DEFAULT_MODE == pendingDeviceModeState)
                        isDeviceSwitchModeProfile =
                                ProfileConfig.isDeviceModeProfile(entry.getKey());

                    if ((BluetoothAdapter.STATE_OFF != entry.getValue()) &&
                                    isDeviceSwitchModeProfile) {
                            Log.d(TAG, "DeviceSwitchModeProfile still running: " +
                                    entry.getKey());
                            return;
                    }
                }
            }
            if (DBG) Log.d(TAG, "All DeviceSwitchModeProfile services stopped...");
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(
                AdapterState.DEVICE_MODE_SWITCH_SERVICES_TURNED_OFF));

        } else if (isDeviceModeSwitchTurningOn) {
            //Check if all Device Switch Mode services are started if so, update state
            //if (DBG) Log.d(TAG,"Checking if all Device Switch Mode profiles are running...");
            synchronized (mProfileServicesState) {
                Iterator<Map.Entry<String,Integer>> i =
                        mProfileServicesState.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String,Integer> entry = i.next();
                    boolean isDeviceSwitchModeProfile;

                    if (HEADSET_MODE  == pendingDeviceModeState)
                        isDeviceSwitchModeProfile =
                                    ProfileConfig.isDeviceModeProfile(entry.getKey());
                    else //if (DEFAULT_MODE == PendingDeviceModeState)
                        isDeviceSwitchModeProfile =
                                ProfileConfig.isPhoneModeProfile(entry.getKey());

                    if ((BluetoothAdapter.STATE_ON != entry.getValue()) &&
                                isDeviceSwitchModeProfile) {
                        Log.d(TAG, "DeviceSwitchModeProfile still not running:" +
                                entry.getKey());
                        return;
                    }
                }
            }
            if (DBG) Log.d(TAG, "All DeviceSwitchModeProfile services started.");
            mAdapterStateMachine.sendMessage
                (mAdapterStateMachine.obtainMessage(
                AdapterState.DEVICE_MODE_SWITCH_SERVICES_TURNED_ON));
        }

    }
    /**
     * sends a delayed message for completion of the disconnection of the profiles
     * This function sets the current a2dp role in state machine  and property is set
     * in Adapter properties
     * @param
     * @return
     */
        public void setDeviceModeProperty() {
           int devicemode =  getDeviceMode();
           Resources resources = getResources();
           int profileDeviceMode = resources
                       .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_a2dp);

           Log.d(TAG,"setDeviceModeProperty() DeviceMode " + devicemode
                   +"profileDeviceMode="+profileDeviceMode);

           A2dpService a2dpService = A2dpService.getA2dpService();
           if( (devicemode == DEFAULT_MODE) && // Default = AV source
               (ProfileConfig.CFG_MODE_DEVICE_OR_PHONE == profileDeviceMode))
               A2dpService.setA2dpProfileServiceUuid(a2dpService.A2DP_SOURCE_SERVICE_UUID);
           else if( devicemode == HEADSET_MODE && // Headset = AV Sink
               (ProfileConfig.CFG_MODE_DEVICE_OR_PHONE == profileDeviceMode))
               A2dpService.setA2dpProfileServiceUuid(a2dpService.A2DP_SINK_SERVICE_UUID);
           else // If not switchable = AV source
               A2dpService.setA2dpProfileServiceUuid(a2dpService.A2DP_SOURCE_SERVICE_UUID);


    }


       /**
        * device mode change should be attempted only after all the connected profiles are
        * disconnected.
        * @param
        * @return
        */
    public synchronized void disconnectDeviceModeProfiles() {

        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService  hsService = HeadsetService.getHeadsetService();
        HfDeviceService hfDeviceService = HfDeviceService.getHfDeviceService();
        Resources resources = getResources();

        int a2dpProfileDeviceMode = resources
                    .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_a2dp);
        int hfDeviceProfileDeviceMode = resources
                    .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_hfdevice);
        int headsetProfileDeviceMode = resources
                    .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_hs_hfp);

        /*Profile connections should be disconnected before mode switch*/
        Log.d(TAG,"disconnectDeviceModeProfiles()"+a2dpProfileDeviceMode+
        hfDeviceProfileDeviceMode+headsetProfileDeviceMode);

        if ( null != hfDeviceService
                 && (ProfileConfig.CFG_MODE_DUAL != hfDeviceProfileDeviceMode)) {
            List<BluetoothDevice> hfDeviceConnDevList= hfDeviceService.getConnectedDevices();
            for(BluetoothDevice device:hfDeviceConnDevList) {
            Log.d(TAG,"disconnectDeviceModeProfile() disconnecting HF device "
                    +  device);
              hfDeviceService.disconnect(device);
            }
        }


        if ( null != a2dpService
             && (ProfileConfig.CFG_MODE_DUAL != a2dpProfileDeviceMode)) {
            List<BluetoothDevice> a2dpConnDevList= a2dpService.getConnectedDevices();
            for(BluetoothDevice device:a2dpConnDevList) {
              Log.d(TAG,"disconnectDeviceModeProfile() disconnecting a2dp device "
                      +  device);
                  a2dpService.disconnect(device);
            }
        }

        if ( null != hsService
            && (ProfileConfig.CFG_MODE_DUAL != headsetProfileDeviceMode)) {
            List<BluetoothDevice> hfConnDevList= hsService.getConnectedDevices();
            for(BluetoothDevice device:hfConnDevList) {
              Log.d(TAG,"disconnectDeviceModeProfile() disconnecting Headeset AG "
                      +  device);
                hsService.disconnect(device);
            }
        }


    }

     public synchronized boolean  isDeviceModeProfilesDisconnected (){


         mAudioManager = (AudioManager)getApplicationContext().
                         getSystemService(getApplicationContext().AUDIO_SERVICE);

         A2dpService a2dpService = A2dpService.getA2dpService();
         HeadsetService hsService = HeadsetService.getHeadsetService();
         HfDeviceService hfDeviceService = HfDeviceService.getHfDeviceService();
         Resources resources = getResources();

         int a2dpProfileDeviceMode = resources
                     .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_a2dp);
         int hfDeviceProfileDeviceMode = resources
                     .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_hfdevice);
         int headsetProfileDeviceMode = resources
                     .getInteger((Integer)R.integer.profile_cfg_run_in_device_mode_hs_hfp);

         // if any of the profile service is  null,device mode change cannot be attempted
         Log.d(TAG,"isDeviceModeProfilesDisconnected()"+ "a2dpProfileDeviceMode = "+
         a2dpProfileDeviceMode+ " hfDeviceProfileDeviceMode = "+ hfDeviceProfileDeviceMode
         + " headsetProfileDeviceMode = "+ headsetProfileDeviceMode);


        if (a2dpService != null
                && (ProfileConfig.CFG_MODE_DUAL != a2dpProfileDeviceMode)){

            List<BluetoothDevice> a2dpConnDevList= a2dpService.getDevicesMatchingConnectionStates(
                   new int[] {BluetoothProfile.STATE_CONNECTED,
                              BluetoothProfile.STATE_CONNECTING,
                              BluetoothProfile.STATE_DISCONNECTING});
            Log.d(TAG,"A2dp isempty = "+ a2dpConnDevList.isEmpty()+ " isBluetoothA2dpOn = "
                + mAudioManager.isBluetoothA2dpOn()+ " isBluetoothA2dpInputOn = "
                + mAudioManager.isBluetoothA2dpInputOn());
            if((!a2dpConnDevList.isEmpty())
                || mAudioManager.isBluetoothA2dpOn()
                || mAudioManager.isBluetoothA2dpInputOn())
                return false;
        }



        if (hsService != null
            && (ProfileConfig.CFG_MODE_DUAL != headsetProfileDeviceMode)){

            List<BluetoothDevice> hfConnDevList= hsService.getDevicesMatchingConnectionStates(
                   new int[] {BluetoothProfile.STATE_CONNECTED,
                              BluetoothProfile.STATE_CONNECTING,
                              BluetoothProfile.STATE_DISCONNECTING});
            Log.d(TAG,"HS isempty = "+ hfConnDevList.isEmpty());

            if(!hfConnDevList.isEmpty())
                return false;
        }


        if (hfDeviceService != null
                && (ProfileConfig.CFG_MODE_DUAL != hfDeviceProfileDeviceMode)){
            List<BluetoothDevice> hfDeviceConnDevList=
                hfDeviceService.getDevicesMatchingConnectionStates(
                       new int[] {BluetoothProfile.STATE_CONNECTED,
                                  BluetoothProfile.STATE_CONNECTING,
                                  BluetoothProfile.STATE_DISCONNECTING});
            Log.d(TAG,"HFDevice isempty = "+ hfDeviceConnDevList.isEmpty());
            if(!hfDeviceConnDevList.isEmpty())
                return false;
        }

        return true;

    }

     private void processConnectOtherProfiles (BluetoothDevice device,
                                                 int firstProfileStatus,int profileId ){
        if (getState()!= BluetoothAdapter.STATE_ON){
            return;
        }
        HeadsetService  hsService = HeadsetService.getHeadsetService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HfDeviceService hfDeviceService = HfDeviceService.getHfDeviceService();

        // if any of the profile service is  null, second profile connection not required
        if (((hsService == null) && (hfDeviceService == null))
            ||(a2dpService == null )){
            return;
        }
        List<BluetoothDevice> a2dpConnDevList= a2dpService.getConnectedDevices();
        List<BluetoothDevice> hfConnDevList = null;
        List<BluetoothDevice> hfDeviceConnDevList = null;

        boolean isA2dpEmpty;

        if (null != hsService)
            hfConnDevList= hsService.getConnectedDevices();

        if (null != hfDeviceService)
            hfDeviceConnDevList= hfDeviceService.getConnectedDevices();

        if (null == a2dpService || a2dpService.getConnectedDevices().isEmpty())
            isA2dpEmpty = true;
        else
            isA2dpEmpty = false;

        if (null != a2dpService)
            a2dpConnDevList = a2dpService.getConnectedDevices();


        // Check if the device is in disconnected state and if so return
        // We need to connect other profile only if one of the profile is still in connected state
        // This is required to avoide a race condition in which profiles would
        // automaticlly connect if the disconnection is initiated within 6 seconds of connection
        //First profile connection being rejected is an exception

        if( PROFILE_CONN_CONNECTED  == firstProfileStatus ){
            if(((null != hsService && hfConnDevList.isEmpty()) ||
                (null != hfDeviceService && hfDeviceConnDevList.isEmpty()))
                && a2dpConnDevList.isEmpty()) {
                return;
        //If the profileId requesting for ConnectOtherProfiles is not in PROFILE_CONN_CONNECTED
        //state after 6s timeout,should not attempt the connection.
            }else if ((profileId == BluetoothProfile.A2DP) && (null != a2dpService) &&
                                          a2dpConnDevList.isEmpty()){
                return;
            }
            else if((profileId == BluetoothProfile.HEADSET) && (null != hsService) &&
                                      hfConnDevList.isEmpty()){
                return;
            }
            else if((profileId == BluetoothProfile.HF_DEVICE) && (null != hfDeviceService) &&
                                     hfDeviceConnDevList.isEmpty()){
                return;
            }

        }
        if((null !=hsService) &&(!hfConnDevList.contains(device)) &&
            (hsService.getPriority(device) >= BluetoothProfile.PRIORITY_ON)){
            hsService.connect(device);
        }

        if((null !=hfDeviceService) && (!hfDeviceConnDevList.contains(device)) &&
            (hfDeviceService.getPriority(device) >= BluetoothProfile.PRIORITY_ON)){
            hfDeviceService.connect(device);
        }

        if(((null != a2dpService) && isA2dpEmpty) &&
            (a2dpService.getPriority(device) >= BluetoothProfile.PRIORITY_ON)) {
            a2dpService.connect(device);
        }
    }

     private void adjustOtherHeadsetPriorities(HeadsetService  hsService,
                                                    BluetoothDevice connectedDevice) {
        int priorityDevies = 0;
        for (BluetoothDevice device : getBondedDevices()) {
           if (hsService.getPriority(device) >= BluetoothProfile.PRIORITY_AUTO_CONNECT &&
               !device.equals(connectedDevice)) {
               ++priorityDevies;
               if(priorityDevies > hsService.MAX_HFP_CONNECTIONS) {
                hsService.setPriority(device, BluetoothProfile.PRIORITY_ON);
               }
           }
        }
     }

     private void adjustOtherHfDevicePriorities(HfDeviceService hfDeviceService,
                                                    BluetoothDevice connectedDevice) {
        for (BluetoothDevice device : getBondedDevices()) {
           if (hfDeviceService.getPriority(device) >= BluetoothProfile.PRIORITY_AUTO_CONNECT &&
               !device.equals(connectedDevice)) {
                hfDeviceService.setPriority(device, BluetoothProfile.PRIORITY_ON);
           }
        }
     }

     private void adjustOtherSinkPriorities(A2dpService a2dpService,
                                                BluetoothDevice connectedDevice) {
         for (BluetoothDevice device : getBondedDevices()) {
             if (a2dpService.getPriority(device) >= BluetoothProfile.PRIORITY_AUTO_CONNECT &&
                 !device.equals(connectedDevice)) {
                 a2dpService.setPriority(device, BluetoothProfile.PRIORITY_ON);
             }
         }
     }

     void setProfileAutoConnectionPriority (BluetoothDevice device, int profileId){
         if (profileId == BluetoothProfile.HEADSET) {
             HeadsetService  hsService = HeadsetService.getHeadsetService();
             if ((hsService != null) &&
                (BluetoothProfile.PRIORITY_AUTO_CONNECT != hsService.getPriority(device))){
                 adjustOtherHeadsetPriorities(hsService, device);
                 hsService.setPriority(device,BluetoothProfile.PRIORITY_AUTO_CONNECT);
             }
         }
         else if (profileId ==  BluetoothProfile.A2DP) {
             A2dpService a2dpService = A2dpService.getA2dpService();
             if ((a2dpService != null) &&
                (BluetoothProfile.PRIORITY_AUTO_CONNECT != a2dpService.getPriority(device))){
                 adjustOtherSinkPriorities(a2dpService, device);
                 a2dpService.setPriority(device,BluetoothProfile.PRIORITY_AUTO_CONNECT);
             }
         }
         else if (profileId == BluetoothProfile.HF_DEVICE) {
             HfDeviceService hfDeviceService = HfDeviceService.getHfDeviceService();
             if ((hfDeviceService != null) &&
                (BluetoothProfile.PRIORITY_AUTO_CONNECT != hfDeviceService.getPriority(device))){
                 adjustOtherHfDevicePriorities(hfDeviceService, device);
                 hfDeviceService.setPriority(device,BluetoothProfile.PRIORITY_AUTO_CONNECT);
             }
         }
    }

     boolean cancelBondProcess(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        byte[] addr = Utils.getBytesFromAddress(device.getAddress());
        return cancelBondNative(addr);
    }

     boolean removeBond(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != BluetoothDevice.BOND_BONDED) {
            return false;
        }
        Message msg = mBondStateMachine.obtainMessage(BondStateMachine.REMOVE_BOND);
        msg.obj = device;
        mBondStateMachine.sendMessage(msg);
        return true;
    }

     int getBondState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return BluetoothDevice.BOND_NONE;
        }
        return deviceProp.getBondState();
    }

     String getRemoteName(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return null;
        return deviceProp.getName();
    }

     int getRemoteType(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return BluetoothDevice.DEVICE_TYPE_UNKNOWN;
        return deviceProp.getDeviceType();
    }

     String getRemoteAlias(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return null;
        return deviceProp.getAlias();
    }

     boolean setRemoteAlias(BluetoothDevice device, String name) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return false;
        deviceProp.setAlias(name);
        return true;
    }

     int getRemoteClass(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return 0;

        return deviceProp.getBluetoothClass();
    }

     ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) return null;
        return deviceProp.getUuids();
    }

     boolean fetchRemoteUuids(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        mRemoteDevices.fetchUuids(device);
        return true;
    }

     boolean setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != BluetoothDevice.BOND_BONDING) {
            return false;
        }

        byte[] addr = Utils.getBytesFromAddress(device.getAddress());
        return pinReplyNative(addr, accept, len, pinCode);
    }

     boolean setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != BluetoothDevice.BOND_BONDING) {
            return false;
        }

        byte[] addr = Utils.getBytesFromAddress(device.getAddress());
        return sspReplyNative(addr, AbstractionLayer.BT_SSP_VARIANT_PASSKEY_ENTRY, accept,
                Utils.byteArrayToInt(passkey));
    }

     boolean setPairingConfirmation(BluetoothDevice device, boolean accept) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH ADMIN permission");
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null || deviceProp.getBondState() != BluetoothDevice.BOND_BONDING) {
            return false;
        }

        byte[] addr = Utils.getBytesFromAddress(device.getAddress());
        return sspReplyNative(addr, AbstractionLayer.BT_SSP_VARIANT_PASSKEY_CONFIRMATION,
                accept, 0);
    }

       public boolean authorizeService(BluetoothDevice device, ParcelUuid ServiceUuid,
                                          boolean authorize, boolean autoReply) {

           enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

           byte[] addr = Utils.getBytesFromAddress(device.getAddress());
           int serviceId = Utils.getServiceIdFromProfileUUID(ServiceUuid);

           return authorizeServiceNative(addr, serviceId, authorize,autoReply);
      }

     void sendConnectionStateChange(BluetoothDevice
            device, int profile, int state, int prevState) {
        // TODO(BT) permission check?
        // Since this is a binder call check if Bluetooth is on still
        if (getState() == BluetoothAdapter.STATE_OFF) return;

        mAdapterProperties.sendConnectionStateChange(device, profile, state, prevState);

    }

     ParcelFileDescriptor connectSocket(BluetoothDevice device, int type,
                                              ParcelUuid uuid, int port, int flag) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int fd = connectSocketNative(Utils.getBytesFromAddress(device.getAddress()),
                   type, Utils.uuidToByteArray(uuid), port, flag);
        if (fd < 0) {
            errorLog("Failed to connect socket");
            return null;
        }
        return ParcelFileDescriptor.adoptFd(fd);
    }

     ParcelFileDescriptor createSocketChannel(int type, String serviceName,
                                                    ParcelUuid uuid, int port, int flag) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int fd =  createSocketChannelNative(type, serviceName,
                                 Utils.uuidToByteArray(uuid), port, flag);
        if (fd < 0) {
            errorLog("Failed to create socket channel");
            return null;
        }
        return ParcelFileDescriptor.adoptFd(fd);
    }

     boolean sspDebugConfigure(boolean enable) {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
         return sspDebugConfigureNative(enable);
     }
    boolean configHciSnoopLog(boolean enable) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return configHciSnoopLogNative(enable);
    }

     boolean dutModeConfigure(boolean enable) {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
         return dutModeConfigureNative(enable);
     }

     void registerCallback(IBluetoothCallback cb) {
         mCallbacks.register(cb);
      }

      void unregisterCallback(IBluetoothCallback cb) {
         mCallbacks.unregister(cb);
      }

    boolean setSdpRecord(boolean enable, int uuid) {
       enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
       return setSdpRecordNative(enable, uuid);
    }

    private static int convertScanModeToHal(int mode) {
        switch (mode) {
            case BluetoothAdapter.SCAN_MODE_NONE:
                return AbstractionLayer.BT_SCAN_MODE_NONE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                return AbstractionLayer.BT_SCAN_MODE_CONNECTABLE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return AbstractionLayer.BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE;
        }
       // errorLog("Incorrect scan mode in convertScanModeToHal");
        return -1;
    }

    static int convertScanModeFromHal(int mode) {
        switch (mode) {
            case AbstractionLayer.BT_SCAN_MODE_NONE:
                return BluetoothAdapter.SCAN_MODE_NONE;
            case AbstractionLayer.BT_SCAN_MODE_CONNECTABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE;
            case AbstractionLayer.BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
        }
        //errorLog("Incorrect scan mode in convertScanModeFromHal");
        return -1;
    }

    private boolean unregisterAllRadioClients() {
        Iterator <Map.Entry<ParcelUuid,RadioClientDeathRecipient>> it
                    = mRadioClientDeathRecipient.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<ParcelUuid,RadioClientDeathRecipient> entry   = it.next();
            RadioClientDeathRecipient deathRecipient = entry.getValue();
            if (deathRecipient != null){
                try {
                    IBinder binder = ((IInterface)this).asBinder();
                     binder.unlinkToDeath(deathRecipient, 0);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to link deathRecipient for app id");
                }
           }
            it.remove();
        }
        return true;
    }

    private void debugLog(String msg) {
        Log.d(TAG +"(" +hashCode()+")", msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG +"(" +hashCode()+")", msg);
    }

    private native static void classInitNative();
    private native boolean initNative();
    private native void cleanupNative();
    /*package*/ native boolean enableRadioNative();
    /*package*/ native boolean disableRadioNative();
    /*package*/ native boolean enableNative();
    /*package*/ native boolean disableNative();
    /*package*/ native boolean setAdapterPropertyNative(int type, byte[] val);
    /*package*/ native boolean getAdapterPropertiesNative();
    /*package*/ native boolean getAdapterPropertyNative(int type);
    /*package*/ native boolean setAdapterPropertyNative(int type);
    /*package*/ native boolean
        setDevicePropertyNative(byte[] address, int type, byte[] val);
    /*package*/ native boolean getDevicePropertyNative(byte[] address, int type);

    /*package*/ native boolean createBondNative(byte[] address);
    /*package*/ native boolean removeBondNative(byte[] address);
    /*package*/ native boolean cancelBondNative(byte[] address);

    private native boolean startDiscoveryNative();
    private native boolean cancelDiscoveryNative();

    private native boolean pinReplyNative(byte[] address, boolean accept, int len, byte[] pin);
    private native boolean sspReplyNative(byte[] address, int type, boolean
            accept, int passkey);
    private native boolean setSdpRecordNative(boolean enable, int uuid);
    private native boolean authorizeServiceNative (byte[] address, int service, boolean authorize,
                                                      boolean auto);

    private native boolean sspDebugConfigureNative(boolean enable);

    /*package*/ native boolean getRemoteServicesNative(byte[] address);

    // TODO(BT) move this to ../btsock dir
    private native int connectSocketNative(byte[] address, int type,
                                           byte[] uuid, int port, int flag);
    private native int createSocketChannelNative(int type, String serviceName,
                                                 byte[] uuid, int port, int flag);

    /*package*/ native boolean configHciSnoopLogNative(boolean enable);

    private native boolean dutModeConfigureNative(boolean enable);

    private native boolean updateRadioCountNative(int type, int count);

    private native void initWakeLockNative(int lock_fd, int unlock_fd);

    public native void forceDisableNative();

    @Override
    protected void finalize() {
        cleanup();
        if (TRACE_REF) {
            synchronized (AdapterService.class) {
                sRefCount--;
                Log.d(TAG, "REFCOUNT: FINALIZED. INSTANCE_COUNT= " + sRefCount);
            }
        }
    }
}
