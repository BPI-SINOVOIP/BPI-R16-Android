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

package com.android.bluetooth.a2dp;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.IRemoteControlDisplay;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.lang.ref.WeakReference;

//BRCM RC Enhancements
import com.broadcom.bt.common.BluetoothConstants;


class FolderItemsCmd {
    byte mScope;
    int  mStartItem;
    int  mEndItem;
    byte mNumAttr;
    byte[] mAttrIDs;
    String[] mAttrText;

    public FolderItemsCmd(byte scope, int start_item,
             int end_item, byte num_attr, byte[] attr_ids,String[] attr_text) {
        mScope = scope;
        mStartItem = start_item;
        mEndItem = end_item;
        mNumAttr = num_attr;
        mAttrIDs = attr_ids;
        mAttrText = attr_text;
    }
}

class FolderItemsRsp {
    byte mStatus;
    short mUIDCounter;
    byte item_type;
    byte[] mPlayerTypes;
    int[] mPlayerSubTypes;
    byte[] mPlayStatusValues;
    short[] mFeatureBitMaskValues;
    String[] mPlayerNameList,mPackageNameList;
    int mNumItems;

    public FolderItemsRsp( byte Status,short UIDCounter,
    int num_items, byte[] playerTypes,int[] playerSubTypes,
    byte[] playStatusValues,short[] featureBitMaskValues,
    String[] playerNameList, String packageNameList[]) {
        mStatus=Status;
        mUIDCounter=UIDCounter;
        mNumItems=num_items;
        mPlayerTypes=new byte[num_items];
        mPlayerTypes=playerTypes;
        mPlayerSubTypes=new int[num_items];
        mPlayerSubTypes=playerSubTypes;
        mPlayStatusValues=new byte[num_items];
        mPlayStatusValues=playStatusValues;
        mFeatureBitMaskValues=new short[num_items*16];
        for(int i=0; i < (num_items *16); i++) {
            mFeatureBitMaskValues[i]=featureBitMaskValues[i];
        }
        mPlayerNameList = playerNameList;
        mPackageNameList = packageNameList;
    }
}
//BRCM AVRCP 1.4 Enhancements --

/**
 * support Bluetooth AVRCP profile.
 * support metadata, play status and event notification
 */
final class Avrcp {
    private static final boolean DEBUG = true;
    private static final String TAG = "Avrcp";
    //private A2dpService mService;
    private Context mContext;
    private static A2dpService mService;
    private final AudioManager mAudioManager;
    private AvrcpMessageHandler mHandler;
    private IRemoteControlDisplayWeak mRemoteControlDisplay;
    private int mClientGeneration;
    private Metadata mMetadata;
    private int mTransportControlFlags;
    private int mCurrentPlayState;
    private int mPlayStatusChangedNT;
    //BRCM PlayerSettings ++
    private int mAppSettingsChangedNT;
    //BRCM PlayerSettings --
    private int mTrackChangedNT;
    private long mTrackNumber;
    private long mCurrentPosMs;
    private long mPlayStartTimeMs;
    private long mSongLengthMs;
    private long mPlaybackIntervalMs;
    private int mPlayPosChangedNT;
    private long mNextPosMs;
    private long mPrevPosMs;
    private long mSkipStartTime;
    private Timer mTimer;
    private int mFeatures;
    private int mAbsoluteVolume;
    private int mLastSetVolume;
    private int mLastDirection;
    private final int mVolumeStep;
    private final int mAudioStreamMax;
    private boolean mVolCmdInProgress;
    private int mAbsVolRetryTimes;
    private int mSkipAmount;
    private int mCurrPlayerID;
    private int mUIDCounter;
    private Map<Integer, Integer> mPlayerList;
    private FolderItemsRsp mRspObj;
    //BRCM AVRCP 1.4 Enhancements --

    /* AVRC IDs from avrc_defs.h */
    private static final int AVRC_ID_REWIND = 0x48;
    private static final int AVRC_ID_FAST_FOR = 0x49;

    /* BTRC features */
    public static final int BTRC_FEAT_METADATA = 0x01;
    public static final int BTRC_FEAT_ABSOLUTE_VOLUME = 0x02;
    public static final int BTRC_FEAT_BROWSE = 0x04;

    //BRCM AVRCP 1.4 Enhancements --
    /** It would be better, if these values can be used across all Java classes **/
    /** BTRC Error codes **/
    public static final byte BTRC_STS_BAD_CMD = 0; /* Invalid command */
    public static final byte BTRC_STS_BAD_PARAM = 1; /* Invalid parameter */
    public static final byte BTRC_STS_NOT_FOUND = 2; /* Specified parameter is wrong or not found */
    public static final byte BTRC_STS_INTERNAL_ERR =3; /* Internal Error */
    public static final byte BTRC_STS_NO_ERROR = 4; /* Operation Success */
    public static final byte BTRC_STS_UID_CHANGED = 5; /* UIDs changed */
    public static final byte BTRC_STS_RESERVED    = 6; /* Reserved */
    public static final byte BTRC_STS_INV_DIRN = 7; /* Invalid direction */
    public static final byte BTRC_STS_INV_DIRECTORY  = 8; /* Invalid directory */
    public static final byte BTRC_STS_INV_ITEM       = 9; /* Invalid Item */
    public static final byte BTRC_STS_INV_SCOPE   = 0x0a; /* Invalid scope */
    public static final byte BTRC_STS_INV_RANGE   = 0x0b; /* Invalid range */
    public static final byte BTRC_STS_DIRECTORY   = 0x0c; /* UID is a directory */
    public static final byte BTRC_STS_MEDIA_IN_USE  = 0x0d; /* Media in use */
    public static final byte BTRC_STS_PLAY_LIST_FULL = 0x0e; /* Playing list full */
    public static final byte BTRC_STS_SRCH_NOT_SPRTD = 0x0f; /* Search not supported */
    public static final byte BTRC_STS_SRCH_IN_PROG   = 0x10; /* Search in progress */
    public static final byte BTRC_STS_INV_PLAYER     = 0x11; /* Invalid player */
    public static final byte BTRC_STS_PLAY_NOT_BROW  = 0x12; /* Player not browsable */
    public static final byte BTRC_STS_PLAY_NOT_ADDR  = 0x13; /* Player not addressed */
    public static final byte BTRC_STS_INV_RESULTS    = 0x14; /* Invalid results */
    public static final byte BTRC_STS_NO_AVBL_PLAY   = 0x15; /* No available players */
    public static final byte BTRC_STS_ADDR_PLAY_CHGD = 0x16; /* Addressed player changed */
    //BRCM AVRCP 1.4 Enhancements --

    /* AVRC response codes, from avrc_defs */
    private static final int AVRC_RSP_NOT_IMPL = 8;
    private static final int AVRC_RSP_ACCEPT = 9;
    private static final int AVRC_RSP_REJ = 10;
    private static final int AVRC_RSP_IN_TRANS = 11;
    private static final int AVRC_RSP_IMPL_STBL = 12;
    private static final int AVRC_RSP_CHANGED = 13;
    private static final int AVRC_RSP_INTERIM = 15;

    private static final int MESSAGE_GET_RC_FEATURES = 1;
    private static final int MESSAGE_GET_PLAY_STATUS = 2;
    private static final int MESSAGE_GET_ELEM_ATTRS = 3;
    private static final int MESSAGE_REGISTER_NOTIFICATION = 4;
    private static final int MESSAGE_PLAY_INTERVAL_TIMEOUT = 5;
    private static final int MESSAGE_VOLUME_CHANGED = 6;
    private static final int MESSAGE_ADJUST_VOLUME = 7;
    private static final int MESSAGE_SET_ABSOLUTE_VOLUME = 8;
    private static final int MESSAGE_ABS_VOL_TIMEOUT = 9;
    private static final int MESSAGE_FAST_FORWARD = 10;
    private static final int MESSAGE_REWIND = 11;
    private static final int MESSAGE_CHANGE_PLAY_POS = 12;
    //BRCM AVRCP 1.4 Enhancements ++
    private static final int MESSAGE_GET_FOLDER_ITEMS = 13;
    private static final int MESSAGE_SET_ADDR_PLAYER = 14;
    //BRCM AVRCP 1.4 Enhancements --
    private static final int MSG_UPDATE_STATE = 100;
    private static final int MSG_SET_METADATA = 101;
    private static final int MSG_SET_TRANSPORT_CONTROLS = 102;
    private static final int MSG_SET_ARTWORK = 103;
    private static final int MSG_SET_GENERATION_ID = 104;

    //BRCM RC Enhancements
    private static final int AVRCP_PLAY_POSITION_DEFAULT_DELAY = 2000;

    //BRCM PlayerSettings ++
    private static final int MSG_APP_SETTINGS_TIMER_EVENT = 105;
    private static final int AVRCP_HANDLER_EVENT_TIMEOUT = 500;

    private boolean mReceiverRegistered = false;

    /* Added repeat/shuffle status */
    public static final int REPEAT_OFF    = 0x01;
    private static final int BUTTON_TIMEOUT_TIME = 2000;
    private static final int BASE_SKIP_AMOUNT = 2000;
    private static final int KEY_STATE_PRESS = 1;
    private static final int KEY_STATE_RELEASE = 0;
    private static final int SKIP_PERIOD = 400;
    private static final int SKIP_DOUBLE_INTERVAL = 3000;
    private static final long MAX_MULTIPLIER_VALUE = 128L;
    private static final int CMD_TIMEOUT_DELAY = 2000;
    private static final int MAX_ERROR_RETRY_TIMES = 3;
    private static final int AVRCP_MAX_VOL = 127;
    private static final int AVRCP_BASE_VOLUME_STEP = 1;

    public static final int REPEAT_SINGLE = 0x02;
    public static final int REPEAT_ALL    = 0x03;
    public static final int REPEAT_GROUP  = 0x04;

    public static final int SHUFFLE_OFF   = 0x01;
    public static final int SHUFFLE_ON    = 0x02;
    public static final int SHUFFLE_GROUP = 0x03;

    /* App Settings attributes - Defined in bt_rc.h */
    public static final byte PLAYER_ATTR_EQUALIZER   = 0x01;
    public static final byte PLAYER_ATTR_REPEAT      = 0x02;
    public static final byte PLAYER_ATTR_SHUFFLE     = 0x03;
    public static final byte PLAYER_ATTR_SCAN        = 0x04;

    public static final byte[] AVRC_SUPPORTED_APP_SETTINGS = {
        PLAYER_ATTR_REPEAT,
        PLAYER_ATTR_SHUFFLE
    };

    public static final String[] SUPPORTED_REPEAT_ATTR_TXT = {
        "Repeat",       /* ATTR NAME */
        "Off",          /* REPEAT_OFF */
        "Single track", /* REPEAT_SINGLE */
        "All tracks"    /* REPEAT_ALL */
    };

    public static final int[] SUPPORTED_REPEAT_ATTR_VAL = {
        REPEAT_OFF,
        REPEAT_SINGLE,
        REPEAT_ALL
    };

    public static final String[] SUPPORTED_SHUFFLE_ATTR_TXT = {
        "Shuffle",      /* ATTR NAME */
        "Off",          /* SHUFFLE_OFF */
        "All tracks"    /* SHUFFLE_ON */
    };

    public static final int[] SUPPORTED_SHUFFLE_ATTR_VAL = {
        SHUFFLE_OFF,
        SHUFFLE_ON
    };
    /* Added flags for tracking repeat and shuffle status */
    private int mRepeat        = REPEAT_OFF;
    private int mShuffle       = SHUFFLE_OFF;

    /* Indices to represent each of the supported media players.
    * To add any new media player support, add a new index and
    update the String arrays below with the respective action strings*/
    public static final int IDX_ANDROID_MEDIA_PLAYER = 0;
    public static final int IDX_GOOGLE_MEDIA_PLAYER = 1;
    public static final int IDX_AMAZON_MEDIA_PLAYER = 2;
    public static final int IDX_HTC_MEDIA_PLAYER = 3;
    public static final int IDX_DOUBLETWIST_MEDIA_PLAYER = 4;

    /* Set default index for Android media player */
    private static int mPlayerIndex = IDX_HTC_MEDIA_PLAYER;//Currently hardcoded to HTC --> TODO //BRCM TEMP

    public static final String[] AVRCP_ACTION_REPEAT_CHANGED = {
        "com.android.music.settingchanged",
        "com.android.music.settingchanged",
        "com.amazon.mp3.repeatstatechanged",
        "com.android.music.settingchanged",
        null
    };

    public static final String[] AVRCP_ACTION_APP_SETTING_REQUEST = {
         "com.android.music.settingrequest",
         "com.android.music.settingrequest",
         null,
         "com.android.music.settingrequest",
         null,
     };


    /* HashMap for mapping intent specific extras to required attributes. */
    private HashMap<String, String> intentExtras = new HashMap<String, String>();
   //BRCM PlayerSettings --

    //BRCM AVRCP 1.4 Enhancements ++
    private static final short UID_COUNTER = 0x1357;
    private static final byte MEDIA_PLAYER_ITEM = 1;
    private static final byte PLAYER_TYPE_AUDIO = 1;
    private static final byte PLAYER_TYPE_BROADCAST_AUDIO = 2;

    private static final boolean TestEnabled = false;

    private static final short AVRC_PF_PLAY_BIT_NO        = 40;
    private static final short AVRC_PF_PLAY_MASK        = 0x01;
    private static final short AVRC_PF_PLAY_OFF            = 5;

    private static final short AVRC_PF_STOP_BIT_NO        = 41;
    private static final short AVRC_PF_STOP_MASK        = 0x02;
    private static final short AVRC_PF_STOP_OFF            = 5;

    private static final short AVRC_PF_PAUSE_BIT_NO       = 42;
    private static final short AVRC_PF_PAUSE_MASK       = 0x04;
    private static final short AVRC_PF_PAUSE_OFF           = 5;

    private static final short AVRC_PF_REWIND_BIT_NO      = 44;
    private static final short AVRC_PF_REWIND_MASK      = 0x10;
    private static final short AVRC_PF_REWIND_OFF          = 5;

    private static final short AVRC_PF_FAST_FWD_BIT_NO    = 45;
    private static final short AVRC_PF_FAST_FWD_MASK    = 0x20;
    private static final short AVRC_PF_FAST_FWD_OFF        = 5;

    private static final short AVRC_PF_FORWARD_BIT_NO     = 47;
    private static final short AVRC_PF_FORWARD_MASK     = 0x80;
    private static final short AVRC_PF_FORWARD_OFF         = 5;

    private static final short AVRC_PF_BACKWARD_BIT_NO    = 48;
    private static final short AVRC_PF_BACKWARD_MASK    = 0x01;
    private static final short AVRC_PF_BACKWARD_OFF        = 6;

    private static final short AVRC_PF_ADV_CTRL_BIT_NO    = 58;
    private static final short AVRC_PF_ADV_CTRL_MASK    = 0x04;
    private static final short AVRC_PF_ADV_CTRL_OFF        = 7;

    private static final short AVRC_PF_BROWSE_BIT_NO      = 59;
    private static final short AVRC_PF_BROWSE_MASK      = 0x08;
    private static final short AVRC_PF_BROWSE_OFF          = 7;

    private static final short AVRC_PF_ADD2NOWPLAY_BIT_NO = 61;
    private static final short AVRC_PF_ADD2NOWPLAY_MASK = 0x20;
    private static final short AVRC_PF_ADD2NOWPLAY_OFF     = 7;

    private static final short AVRC_PF_UID_UNIQUE_BIT_NO  = 62;
    private static final short AVRC_PF_UID_UNIQUE_MASK  = 0x40;
    private static final short AVRC_PF_UID_UNIQUE_OFF      = 7;

    private static final short AVRC_PF_NOW_PLAY_BIT_NO    = 65;
    private static final short AVRC_PF_NOW_PLAY_MASK    = 0x02;
    private static final short AVRC_PF_NOW_PLAY_OFF        = 8;

    private static final String
        ADDR_PLAYER_CHANGED = "com.broadcom.avrcp.EVENT_ADDRESS_PLAYER_CHANGED";

    // Broadcast receiver for device connections intent broadcasts
    // TODO : Handle the broadcast events
    private final BroadcastReceiver mAVRCPReceiver = new AvrcpServiceBroadcastReceiver();
    //BRCM AVRCP 1.4 Enhancements --

    static {
        classInitNative();
    }

    private Avrcp(A2dpService svc,Context context) {
        mService =svc;
        mMetadata = new Metadata();
        mCurrentPlayState = RemoteControlClient.PLAYSTATE_NONE; // until we get a callback
        mPlayStatusChangedNT = NOTIFICATION_TYPE_CHANGED;
        //BRCM PlayerSettings ++
        mAppSettingsChangedNT = NOTIFICATION_TYPE_CHANGED;
        //BRCM PlayerSettings --
        mTrackChangedNT = NOTIFICATION_TYPE_CHANGED;
        mTrackNumber = -1L;
        mCurrentPosMs = 0L;
        mPlayStartTimeMs = -1L;
        mSongLengthMs = 0L;
        mPlaybackIntervalMs = 0L;
        mPlayPosChangedNT = NOTIFICATION_TYPE_CHANGED;
        mFeatures = 0;
        mAbsoluteVolume = -1;
        mLastSetVolume = -1;
        mLastDirection = 0;
        mVolCmdInProgress = false;
        mAbsVolRetryTimes = 0;
    //BRCM AVRCP 1.4 Enhancements ++
        mUIDCounter = UID_COUNTER;
        mCurrPlayerID = 1;
        mContext = context;
        mRspObj = null;

        initNative();

        //BRCM PlayerSettings  ++
        unregisterIntentReceiver();
        registerIntentReceiver();
        //BRCM PlayerSettings --

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioStreamMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mVolumeStep = Math.max(AVRCP_BASE_VOLUME_STEP, AVRCP_MAX_VOL/mAudioStreamMax);
    //BRCM AVRCP 1.4 Enhancements ++
        mPlayerList = Collections.synchronizedMap(new HashMap<Integer,Integer>());

        context.registerReceiver(mAVRCPReceiver,
            new IntentFilter(ADDR_PLAYER_CHANGED));
        // Register for package removal intent broadcasts for media button receiver persistence
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        pkgFilter.addDataScheme("package");
        context.registerReceiver(mAVRCPReceiver, pkgFilter);
    //BRCM AVRCP 1.4 Enhancements --
    }

    //BRCM PlayerSettings  ++
    private void registerIntentReceiver() {

        IntentFilter filter = fillIntentFilter();
        mContext.registerReceiver(mReceiver, filter);
        mReceiverRegistered = true;
    }

     private void unregisterIntentReceiver() {
        if (mReceiverRegistered) {
            try {
                Log.d(TAG,"Unregistering previous receiver");
                mContext.unregisterReceiver(mReceiver);
                mReceiverRegistered = false;
            } catch (Throwable t) {
                Log.w(TAG, "Unable to unregister receiver",t);
            }
        }
    }
    //BRCM PlayerSettings  --

    private void start() {
        HandlerThread thread = new HandlerThread("BluetoothAvrcpHandler");
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new AvrcpMessageHandler(looper);
        mRemoteControlDisplay = new IRemoteControlDisplayWeak(mHandler);
        mAudioManager.registerRemoteControlDisplay(mRemoteControlDisplay);
        mAudioManager.remoteControlDisplayWantsPlaybackPositionSync(
                      mRemoteControlDisplay, true);
        HandleGetFolderItemResponse(false);
    }

    static Avrcp make(A2dpService svc, Context context) {
        if (DEBUG) Log.v(TAG, "make");
        Avrcp ar = new Avrcp(svc, context);
        ar.start();
        return ar;
    }

    public void doQuit() {
        mHandler.removeCallbacksAndMessages(null);
        Looper looper = mHandler.getLooper();
        if (looper != null) {
            looper.quit();
        }
        unregisterIntentReceiver();
        mAudioManager.unregisterRemoteControlDisplay(mRemoteControlDisplay);
        mRemoteControlDisplay = null;
        mHandler = null;
        mRspObj = null;
        mContext.unregisterReceiver(mAVRCPReceiver);
    }

    public void cleanup() {
        cleanupNative();
    //BRCM PlayerSettings  ++
    unregisterIntentReceiver();
    //BRCM PlayerSettings  --
    }

    //BRCM PlayerSettings ++
    private IntentFilter fillIntentFilter() {
        IntentFilter filter = new IntentFilter();
        if(DEBUG) Log.d(TAG, "fillIntentFilter()");

        for(int i = 0; i < AVRCP_ACTION_REPEAT_CHANGED.length; i++) {
            if(AVRCP_ACTION_REPEAT_CHANGED[i] != null
                && !(filter.hasAction(AVRCP_ACTION_REPEAT_CHANGED[i])))
                filter.addAction(AVRCP_ACTION_REPEAT_CHANGED[i]);
        }

        return filter;
    }
    private void fillIntentExtras(Intent intent) {
        if(DEBUG) Log.d(TAG, "fillIntentExtras() "+intent);
        String action = intent.getAction();
        Log.d(TAG,"fillIntentExtras - received action - "+action);

    //if (action.equals(AVRCP_ACTION_META_CHANGED[IDX_HTC_MEDIA_PLAYER]) || --> No player specific check!!
    //--> TODO //BRCM Temp
          //  action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_HTC_MEDIA_PLAYER]))
        {
            intentExtras.clear();
            /* Changes in intentExtras for HTC player */
            intentExtras.put("Repeat", "repeat");           /* ------> TODO "repeat"      */
            intentExtras.put("Shuffle", "shuffle");         /* ------> TODO "shuffle"     */
        }
    }

    private boolean findRepeatOrShuffleChangedAction(String str) {
        if(DEBUG) Log.d(TAG, "findRepeatChangedAction()-"+str);
        return findAction(AVRCP_ACTION_REPEAT_CHANGED, str);
    }

    private boolean findAction(String[] arr, String str) {
        Log.d(TAG, "findAction() - "+str);
        boolean result = false;
        for(int i=0; i< arr.length; i++) {
             if(arr[i] != null && str.equals(arr[i])) {
                result = true;
                return result;
            }
        }
        return result;
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            Log.d(TAG, "received intent: " + action);
            int type = 0;

    /* Map the required attributes with Extras corresponding to the media player's intent*/
            fillIntentExtras(intent);

        if (findRepeatOrShuffleChangedAction(action))
            {
                mHandler.removeMessages(MSG_APP_SETTINGS_TIMER_EVENT);
                if (updateApplicationSettings(intent))
                {
                    if(mAppSettingsChangedNT == NOTIFICATION_TYPE_INTERIM)
                    {
                        mAppSettingsChangedNT = NOTIFICATION_TYPE_CHANGED;
                        registerNotificationRspAppSettingsChagedNative(mAppSettingsChangedNT,
                            mRepeat, mShuffle);
                    }
                }
            }
       }
    };
    //BRCM PlayerSettings --


    private static class IRemoteControlDisplayWeak extends IRemoteControlDisplay.Stub {
        private WeakReference<Handler> mLocalHandler;
        IRemoteControlDisplayWeak(Handler handler) {
            mLocalHandler = new WeakReference<Handler>(handler);
        }

        @Override
        public void setPlaybackState(int generationId, int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            Handler handler = mLocalHandler.get();
            if (handler != null) {
                handler.obtainMessage(MSG_UPDATE_STATE, generationId, state,
                                      new Long(currentPosMs)).sendToTarget();
            }
        }

        @Override
        public void setMetadata(int generationId, Bundle metadata) {
            Handler handler = mLocalHandler.get();
            if (handler != null) {
                handler.obtainMessage(MSG_SET_METADATA, generationId, 0, metadata).sendToTarget();
            }
        }

        @Override
        public void setTransportControlInfo(int generationId, int flags, int posCapabilities) {
            Handler handler = mLocalHandler.get();
            if (handler != null) {
                handler.obtainMessage(MSG_SET_TRANSPORT_CONTROLS, generationId, flags)
                        .sendToTarget();
            }
        }

        @Override
        public void setArtwork(int generationId, Bitmap bitmap) {
        }

        @Override
        public void setAllMetadata(int generationId, Bundle metadata, Bitmap bitmap) {
            Handler handler = mLocalHandler.get();
            if (handler != null) {
                handler.obtainMessage(MSG_SET_METADATA, generationId, 0, metadata).sendToTarget();
                handler.obtainMessage(MSG_SET_ARTWORK, generationId, 0, bitmap).sendToTarget();
            }
        }

        @Override
        public void setCurrentClientId(int clientGeneration, PendingIntent mediaIntent,
                boolean clearing) throws RemoteException {
            Handler handler = mLocalHandler.get();
            if (handler != null) {
                handler.obtainMessage(MSG_SET_GENERATION_ID,
                    clientGeneration, (clearing ? 1 : 0), mediaIntent).sendToTarget();
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            // no-op: this RemoteControlDisplay is not subject to being disabled.
        }
    }

    /** Handles Avrcp messages. */
    private final class AvrcpMessageHandler extends Handler {
        private AvrcpMessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_STATE:
                if (mClientGeneration == msg.arg1) {
                    updatePlayPauseState(msg.arg2, ((Long)msg.obj).longValue());
                }
                break;

            case MSG_SET_METADATA:
                if (mClientGeneration == msg.arg1) updateMetadata((Bundle) msg.obj);
                break;

            case MSG_SET_TRANSPORT_CONTROLS:
                if (mClientGeneration == msg.arg1) updateTransportControls(msg.arg2);
                break;

            case MSG_SET_ARTWORK:
                if (mClientGeneration == msg.arg1) {
                }
                break;

            case MSG_SET_GENERATION_ID:
                if (DEBUG) Log.v(TAG, "New genId = " + msg.arg1 + ", clearing = " + msg.arg2);
                mClientGeneration = msg.arg1;
                break;

            case MESSAGE_GET_RC_FEATURES:
                String address = (String) msg.obj;
                if (DEBUG) Log.v(TAG, "MESSAGE_GET_RC_FEATURES: address="+address+
                                                             ", features="+msg.arg1);
                mFeatures = msg.arg1;
                mAudioManager.avrcpSupportsAbsoluteVolume(address, isAbsoluteVolumeSupported());
                break;

            case MESSAGE_GET_PLAY_STATUS:
                if (DEBUG) Log.v(TAG, "MESSAGE_GET_PLAY_STATUS");
                getPlayStatusRspNative(convertPlayStateToPlayStatus(mCurrentPlayState),
                                       (int)mSongLengthMs, (int)getPlayPosition());
                break;

            case MESSAGE_GET_ELEM_ATTRS:
            {
                String[] textArray;
                int[] attrIds;
                byte numAttr = (byte) msg.arg1;
                ArrayList<Integer> attrList = (ArrayList<Integer>) msg.obj;
                if (DEBUG) Log.v(TAG, "MESSAGE_GET_ELEM_ATTRS:numAttr=" + numAttr);
                attrIds = new int[numAttr];
                textArray = new String[numAttr];
                for (int i = 0; i < numAttr; ++i) {
                    attrIds[i] = attrList.get(i).intValue();
                    textArray[i] = getAttributeString(attrIds[i]);
                }
                getElementAttrRspNative(numAttr, attrIds, textArray);
                break;
            }

     //BRCM AVRCP 1.4 Enhancements ++
           case MESSAGE_GET_FOLDER_ITEMS:
            {
                if (DEBUG) Log.v(TAG, "MESSAGE_GET_FOLDER_ITEMS_CMD");
                FolderItemsCmd FolderObj=(FolderItemsCmd)msg.obj;
                int NumAttr=FolderObj.mNumAttr;
                int StartItem=FolderObj.mStartItem, EndItem=FolderObj.mEndItem;
                int Scope=FolderObj.mScope;
                if (DEBUG) Log.v(TAG, "MESSAGE_GET_FOLDER_ITEMS_CMD: numAttr="
                    +NumAttr+", StartItem="+StartItem+
                    ", EndItem="+EndItem+",Scope="+Scope);
                HandleGetFolderItemResponse(true);
                break;
            }

            case MESSAGE_SET_ADDR_PLAYER:
            {
                int status = BTRC_STS_NO_ERROR;
                int type =(int)NOTIFICATION_TYPE_CHANGED;
                if (DEBUG)
                    Log.v(TAG, "MESSAGE_SET_ADDR_PLAYER_CMD=" + msg.arg1);

                if (msg.arg1 > mRspObj.mPackageNameList.length) {
                    Log.e(TAG,"Invalid Player ID:" + msg.arg1);
                    status = BTRC_STS_INV_PLAYER;
                    setAddressedPlayerRspNative(status);
                    break;
                }

                mCurrPlayerID = msg.arg1;

                if(mRspObj == null || mRspObj.mPackageNameList == null ||
                    mRspObj.mPackageNameList.length == 0) {
                   status = BTRC_STS_NO_AVBL_PLAY;
                   setAddressedPlayerRspNative(status);
                }
                else {

                String packageName =
                        mRspObj.mPackageNameList[mCurrPlayerID - 1];

                if(packageName.length() > 0)
                {
                    Log.d(TAG,"Package name :" + packageName + "Player ID:"
                            + mCurrPlayerID);
                    final PackageManager pm = mContext.getPackageManager();
                    Intent openAppIntent =
                            pm.getLaunchIntentForPackage(packageName);
                    mContext.startActivity(openAppIntent);
                    setAddressedPlayerRspNative(status);
                    registerNotificationRspAddrPlayerChangedNative(type,
                            (int)mCurrPlayerID, (int)mUIDCounter);
                }
                else {
                        Log.e(TAG,"Blank package name obtained for Player ID:"
                                + mCurrPlayerID);
                        status = BTRC_STS_INV_PLAYER;
                        setAddressedPlayerRspNative(status);
                }
                break;
            }
            }

            case MESSAGE_REGISTER_NOTIFICATION:
                if (DEBUG) Log.v(TAG, "MESSAGE_REGISTER_NOTIFICATION:event=" + msg.arg1 +
                                      " param=" + msg.arg2);
                processRegisterNotification(msg.arg1, msg.arg2);
                break;

            case MESSAGE_PLAY_INTERVAL_TIMEOUT:
                if (DEBUG) Log.v(TAG, "MESSAGE_PLAY_INTERVAL_TIMEOUT");
                mPlayPosChangedNT = NOTIFICATION_TYPE_CHANGED;
                registerNotificationRspPlayPosNative(mPlayPosChangedNT, (int)getPlayPosition());
                break;
        //BRCM PlayerSettings ++
        case MSG_APP_SETTINGS_TIMER_EVENT:
        Log.d(TAG, "App settings update timed out. Sending cached info");

        if(mAppSettingsChangedNT == NOTIFICATION_TYPE_INTERIM)
        {
            mAppSettingsChangedNT = NOTIFICATION_TYPE_CHANGED;
            registerNotificationRspAppSettingsChagedNative(mAppSettingsChangedNT,
                mRepeat, mShuffle);
        }
        break;
         //BRCM PlayerSettings --

            case MESSAGE_VOLUME_CHANGED:
                if (DEBUG) Log.v(TAG, "MESSAGE_VOLUME_CHANGED: volume=" + msg.arg1 +
                                                              " ctype=" + msg.arg2);

                if (msg.arg2 == AVRC_RSP_ACCEPT || msg.arg2 == AVRC_RSP_REJ) {
                    if (mVolCmdInProgress == false) {
                        Log.e(TAG, "Unsolicited response, ignored");
                        break;
                    }
                    removeMessages(MESSAGE_ABS_VOL_TIMEOUT);
                    mVolCmdInProgress = false;
                    mAbsVolRetryTimes = 0;
                }
                if (mAbsoluteVolume != msg.arg1 && (msg.arg2 == AVRC_RSP_ACCEPT ||
                                                    msg.arg2 == AVRC_RSP_CHANGED ||
                                                    msg.arg2 == AVRC_RSP_INTERIM)) {
                    notifyVolumeChanged(msg.arg1);
                    mAbsoluteVolume = msg.arg1;
                } else if (msg.arg2 == AVRC_RSP_REJ) {
                    Log.e(TAG, "setAbsoluteVolume call rejected");
                }
                break;

            case MESSAGE_ADJUST_VOLUME:
                if (DEBUG) Log.d(TAG, "MESSAGE_ADJUST_VOLUME: direction=" + msg.arg1);
                if (mVolCmdInProgress) {
                    if (DEBUG) Log.w(TAG, "There is already a volume command in progress.");
                    break;
                }
                // Wait on verification on volume from device, before changing the volume.
                if ((msg.arg1 == -1 || msg.arg1 == 1)) {
                    int setVol = Math.min(AVRCP_MAX_VOL,
                                 Math.max(0, mAbsoluteVolume + msg.arg1*mVolumeStep));
                    if (setVolumeNative(setVol)) {
                        sendMessageDelayed(obtainMessage(MESSAGE_ABS_VOL_TIMEOUT),
                                           CMD_TIMEOUT_DELAY);
                        mVolCmdInProgress = true;
                        mLastDirection = msg.arg1;
                        mLastSetVolume = setVol;
                    }
                } else {
                    Log.e(TAG, "Unknown direction in MESSAGE_ADJUST_VOLUME");
                }
                break;

            case MESSAGE_SET_ABSOLUTE_VOLUME:
                if (DEBUG) Log.v(TAG, "MESSAGE_SET_ABSOLUTE_VOLUME");
                if (mVolCmdInProgress) {
                    if (DEBUG) Log.w(TAG, "There is already a volume command in progress.");
                    break;
                }
                if (setVolumeNative(msg.arg1)) {
                    sendMessageDelayed(obtainMessage(MESSAGE_ABS_VOL_TIMEOUT), CMD_TIMEOUT_DELAY);
                    mVolCmdInProgress = true;
                    mLastSetVolume = msg.arg1;
                }
                break;

            case MESSAGE_ABS_VOL_TIMEOUT:
                if (DEBUG) Log.v(TAG, "MESSAGE_ABS_VOL_TIMEOUT: Volume change cmd timed out.");
                mVolCmdInProgress = false;
                if (mAbsVolRetryTimes >= MAX_ERROR_RETRY_TIMES) {
                    mAbsVolRetryTimes = 0;
                } else {
                    mAbsVolRetryTimes += 1;
                    if (setVolumeNative(mLastSetVolume)) {
                        sendMessageDelayed(obtainMessage(MESSAGE_ABS_VOL_TIMEOUT),
                                           CMD_TIMEOUT_DELAY);
                        mVolCmdInProgress = true;
                    }
                }
                break;

            case MESSAGE_FAST_FORWARD:
            case MESSAGE_REWIND:
                int skipAmount;
                if (msg.what == MESSAGE_FAST_FORWARD) {
                    if (DEBUG) Log.v(TAG, "MESSAGE_FAST_FORWARD");
                    skipAmount = BASE_SKIP_AMOUNT;
                } else {
                    if (DEBUG) Log.v(TAG, "MESSAGE_REWIND");
                    skipAmount = -BASE_SKIP_AMOUNT;
                }

                if (hasMessages(MESSAGE_CHANGE_PLAY_POS) &&
                        (skipAmount != mSkipAmount)) {
                    Log.w(TAG, "missing release button event:" + mSkipAmount);
                }

                if ((!hasMessages(MESSAGE_CHANGE_PLAY_POS)) ||
                        (skipAmount != mSkipAmount)) {
                    mSkipStartTime = SystemClock.elapsedRealtime();
                }

                removeMessages(MESSAGE_CHANGE_PLAY_POS);
                if (msg.arg1 == KEY_STATE_PRESS) {
                    mSkipAmount = skipAmount;
                    changePositionBy(mSkipAmount * getSkipMultiplier());
                    Message posMsg = obtainMessage(MESSAGE_CHANGE_PLAY_POS);
                    posMsg.arg1 = 1;
                    sendMessageDelayed(posMsg, SKIP_PERIOD);
                }
                break;

            case MESSAGE_CHANGE_PLAY_POS:
                if (DEBUG) Log.v(TAG, "MESSAGE_CHANGE_PLAY_POS:" + msg.arg1);
                changePositionBy(mSkipAmount * getSkipMultiplier());
                if (msg.arg1 * SKIP_PERIOD < BUTTON_TIMEOUT_TIME) {
                    Message posMsg = obtainMessage(MESSAGE_CHANGE_PLAY_POS);
                    posMsg.arg1 = msg.arg1 + 1;
                    sendMessageDelayed(posMsg, SKIP_PERIOD);
                }
                break;
            }
        }
    }

    //BRCM PlayerSettings ++
    void dumpExtras (Intent intent) {
         Log.w(TAG,"In dumpExtras - Received intent - " + intent.getAction());
         Bundle extras = intent.getExtras();
         if(extras != null) {
             if(DEBUG) Log.d(TAG, "dumpExtras() :"+extras);
             Set<String> ks = extras.keySet();
             Iterator<String> iterator = ks.iterator();
             while (iterator.hasNext()) {
                 Log.d(TAG+" - KEY", iterator.next());
             }
         }
     }


    /* Update Repeat/Shuffle status */
    public boolean updateApplicationSettings(Intent applicationsettingsIntent) {
        if(DEBUG) Log.d(TAG, "updateApplicationSettings() : "+applicationsettingsIntent);

        try {
            dumpExtras(applicationsettingsIntent);
            /* Added flags for holding repeat/shuffle status */
            int repeat  = REPEAT_OFF;
            int shuffle = SHUFFLE_OFF;

            if (applicationsettingsIntent
                .hasExtra("shuffle_enabled_key")) {
                if (applicationsettingsIntent.getBooleanExtra(intentExtras.get("Shuffle"), false))
                    shuffle = SHUFFLE_ON;
                else
                    shuffle = SHUFFLE_OFF;
            }
            else if (applicationsettingsIntent.hasExtra(intentExtras.get("Shuffle"))) {
                shuffle = applicationsettingsIntent.getIntExtra(intentExtras.get("Shuffle"), 0);
                if(DEBUG) Log.d(TAG, "updateApplicationSettings() shuffle:"+shuffle);

                if (shuffle >= 1) //if (shuffle == 2)
                    shuffle = SHUFFLE_ON;
              //  else if(shuffle == 1)
              //      shuffle = SHUFFLE_GROUP;
                else
                    shuffle = SHUFFLE_OFF;
            }
            else
                shuffle = mShuffle;

            if (shuffle != mShuffle) {
                mShuffle = shuffle;
            }

            if(applicationsettingsIntent.hasExtra(intentExtras.get("Repeat"))) {
                repeat = applicationsettingsIntent.getIntExtra(intentExtras.get("Repeat"), 0);
                if(DEBUG) Log.d(TAG, "updateApplicationSettings() repeat:"+repeat);

                if (repeat == 1)
                    repeat = REPEAT_SINGLE;
                //else if(repeat == 2)
                else if(repeat >= 2)
                    repeat = REPEAT_ALL;
                else
                    repeat = REPEAT_OFF;
            }
            else
                repeat = mRepeat;

            if(repeat != mRepeat) {
        mRepeat = repeat;
        }

            Log.d(TAG, "updateApplicationSettings: Repeat: " +String.valueOf(mRepeat) +
                " Shuffle: " + String.valueOf(mShuffle));
        }
        catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in updateApplicationSettings()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return false;
        }
        return true;
    }
    //BRCM PlayerSettings --

    private void updatePlayPauseState(int state, long currentPosMs) {
        if (DEBUG) Log.v(TAG,
                "updatePlayPauseState, old=" + mCurrentPlayState +
                ", state=" + state + ", currentPosMs=" + currentPosMs);
        boolean oldPosValid = (mCurrentPosMs !=
                               RemoteControlClient.PLAYBACK_POSITION_INVALID);
        int oldPlayStatus = convertPlayStateToPlayStatus(mCurrentPlayState);
        int newPlayStatus = convertPlayStateToPlayStatus(state);

        if ((mCurrentPlayState == RemoteControlClient.PLAYSTATE_PLAYING) &&
            (mCurrentPlayState != state) && oldPosValid) {
            mCurrentPosMs = getPlayPosition();
        }

        mCurrentPlayState = state;
        if (currentPosMs != RemoteControlClient.PLAYBACK_POSITION_INVALID) {
            mCurrentPosMs = currentPosMs;
        }
        if (state == RemoteControlClient.PLAYSTATE_PLAYING) {
            mPlayStartTimeMs = SystemClock.elapsedRealtime();
        }

        boolean newPosValid = (mCurrentPosMs !=
                               RemoteControlClient.PLAYBACK_POSITION_INVALID);
        long playPosition = getPlayPosition();
        mHandler.removeMessages(MESSAGE_PLAY_INTERVAL_TIMEOUT);
        /* need send play position changed notification when play status is changed */
        if ((mPlayPosChangedNT == NOTIFICATION_TYPE_INTERIM) &&
            ((oldPlayStatus != newPlayStatus) || (oldPosValid != newPosValid) ||
             (newPosValid && ((playPosition >= mNextPosMs) || (playPosition <= mPrevPosMs))))) {
            mPlayPosChangedNT = NOTIFICATION_TYPE_CHANGED;
            registerNotificationRspPlayPosNative(mPlayPosChangedNT, (int)playPosition);
        }
        if ((mPlayPosChangedNT == NOTIFICATION_TYPE_INTERIM) && newPosValid &&
            (state == RemoteControlClient.PLAYSTATE_PLAYING)) {
            Message msg = mHandler.obtainMessage(MESSAGE_PLAY_INTERVAL_TIMEOUT);
            mHandler.sendMessageDelayed(msg, mNextPosMs - playPosition);
        }

        if ((mPlayStatusChangedNT == NOTIFICATION_TYPE_INTERIM) && (oldPlayStatus != newPlayStatus)) {
            mPlayStatusChangedNT = NOTIFICATION_TYPE_CHANGED;
            registerNotificationRspPlayStatusNative(mPlayStatusChangedNT, newPlayStatus);
        }
    }

    //BRCM AVRCP 1.4 Enhancements --
    private void HandleGetFolderItemResponse(boolean bSendMsg){
        byte rsp_status=BTRC_STS_NO_ERROR,item_type=MEDIA_PLAYER_ITEM;
        short uid_counter=UID_COUNTER;
        byte[] playerTypes, playStatusValues;
        short[]featureBitMaskValues;
        int[] playerSubTypes;
        String[] textArray,packageNameArray;
        int num_items=0;

        // We do not get the values from framework layer. So hard-code the values here
        // for these types
        final short[] audio_feature_bits =
            new short[] { AVRC_PF_PLAY_BIT_NO, AVRC_PF_STOP_BIT_NO,
                AVRC_PF_PAUSE_BIT_NO, AVRC_PF_REWIND_BIT_NO,
                AVRC_PF_FAST_FWD_BIT_NO,AVRC_PF_FORWARD_BIT_NO,
                AVRC_PF_BACKWARD_BIT_NO,AVRC_PF_ADV_CTRL_BIT_NO,
                AVRC_PF_BROWSE_BIT_NO,AVRC_PF_ADD2NOWPLAY_BIT_NO,
                AVRC_PF_UID_UNIQUE_BIT_NO,AVRC_PF_NOW_PLAY_BIT_NO};

        final PackageManager pm = mContext.getPackageManager();
        List<String> appDisplayableNameList = new ArrayList<String>();
        Intent intentPackageList = new Intent(Intent.ACTION_VIEW);
        File file = new File((String) ("MusicFile"));
        intentPackageList.setDataAndType(Uri.fromFile(file), "audio/*");
        List<ResolveInfo> activitiesList = pm.queryIntentActivities(intentPackageList, 0 );
        Collections.sort(activitiesList, new ResolveInfo.DisplayNameComparator(pm));

        if(!activitiesList.isEmpty()) {
            for (ResolveInfo info : activitiesList) {
                 ApplicationInfo appInfo = info.activityInfo.applicationInfo;

              if(appInfo.loadLabel(pm).toString().length() > 0) {
                 appDisplayableNameList.add(appInfo.loadLabel(pm).toString());
              }
              else {
                 appDisplayableNameList.add(appInfo.packageName);
              }

              Log.d(TAG, "Installed Audio app's package: "
                + appInfo.packageName);
              Log.d(TAG, "Installed Audio app's Label: "
                + appInfo.loadLabel(pm).toString());
            }
        } else {
                Log.e(TAG, "activities list empty");
        }

        List<String> mPlayerList, mPlayStatusValues;
        int tempIndex;
        String tempStr;
        ResolveInfo info;
        ApplicationInfo appInfo;

        mPlayerList = mAudioManager.getPlayerList();
        mPlayStatusValues = mAudioManager.getPlayerPlayBackState();
        num_items = activitiesList.size();

        playerSubTypes = new int[num_items];
        featureBitMaskValues = new short[num_items*16];
        playerTypes=new byte[num_items];
        textArray = new String[num_items];
        packageNameArray = new String[num_items];
        playStatusValues = new byte[num_items];

        for (int i = 0; i < num_items; i++) {
             playerTypes[i] = PLAYER_TYPE_AUDIO;
             // We do not obtain player subtype from framework layer
             playerSubTypes[i] = 0;
             textArray[i] = appDisplayableNameList.get(i);
             info = activitiesList.get(i);
             appInfo = info.activityInfo.applicationInfo;
             packageNameArray[i] = appInfo.packageName;
             tempIndex = -1;
             for (int j = 0; j < num_items; j++) {
                 if(mPlayerList.contains(packageNameArray[i])) {
                    tempIndex = mPlayerList.indexOf(packageNameArray[i]);
                    playStatusValues[i] = (byte)(Integer.parseInt(
                            mPlayStatusValues.get(tempIndex)));
                    break;
                 }
             }

            if(tempIndex == -1) {
               playStatusValues[i] = PLAYSTATUS_STOPPED;
            }

            Log.d (TAG, "\n");
            Log.d (TAG, "   +++ Player " + i + " +++   ");
            Log.d (TAG, "textArray[" + i + "]: " + textArray[i]);
            Log.d (TAG, "PackageName[" + i + "]: " + packageNameArray[i]);
            Log.d (TAG, "playerTypes[" + i + "]: " + playerTypes[i]);
            Log.d (TAG, "playStatusValues[" + i + "]: " + playStatusValues[i]);
          }

          for (int i = 0; i < num_items; ++i) {
          // pass thru
          if(PLAYER_TYPE_AUDIO == playerTypes[i]) {
              for (int inner = 0; inner < audio_feature_bits.length; inner++){
                  // gives which octet this belongs to
                  byte octet = (byte)(audio_feature_bits[inner] / 8);
                  // gives the bit position within the octet
                  byte bit   = (byte)(audio_feature_bits[inner] % 8);
                  featureBitMaskValues[(i*16) + octet] |= (1 << bit);
              }
            }
         }

        mRspObj = new FolderItemsRsp(rsp_status,uid_counter,
                 num_items,playerTypes,playerSubTypes,playStatusValues,
                 featureBitMaskValues,textArray,packageNameArray);
        mRspObj.item_type=item_type;
        if(bSendMsg)
            SendFolderItems(mRspObj);
    }

    private void SendFolderItems(FolderItemsRsp RspObj){
         if (DEBUG) Log.v(TAG, "MESSAGE_GET_FOLDER_ITEMS_RSP:numAttr="
                        + RspObj.mNumItems);

         getFolderItemsMediaRspNative(RspObj.mStatus,RspObj.mUIDCounter,
                    RspObj.item_type,RspObj.mNumItems,RspObj.mPlayerTypes,
                    RspObj.mPlayerSubTypes,RspObj.mPlayStatusValues,
                    RspObj.mFeatureBitMaskValues,RspObj.mPlayerNameList);
    }
    //BRCM AVRCP 1.4 Enhancements --

    private void updateTransportControls(int transportControlFlags) {
        mTransportControlFlags = transportControlFlags;
    }

    class Metadata {
        private String artist;
        private String trackTitle;
        private String albumTitle;
        private long trackNumber;
        private long numberOfTracks;

        public Metadata() {
            artist = null;
            trackTitle = null;
            albumTitle = null;
            trackNumber = -1L;
            numberOfTracks = 0L;
        }

        public String toString() {
            return "Metadata[artist=" + artist + " trackTitle=" + trackTitle + " albumTitle=" +
                   albumTitle + "]";
        }
    }

    private String getMdString(Bundle data, int id) {
        return data.getString(Integer.toString(id));
    }

    private long getMdLong(Bundle data, int id) {
        return data.getLong(Integer.toString(id));
    }

    private void updateMetadata(Bundle data) {
        String oldMetadata = mMetadata.toString();
        mMetadata.artist = getMdString(data, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        mMetadata.trackTitle = getMdString(data, MediaMetadataRetriever.METADATA_KEY_TITLE);
        mMetadata.albumTitle = getMdString(data, MediaMetadataRetriever.METADATA_KEY_ALBUM);
        mMetadata.numberOfTracks = getMdLong(data, MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
        mMetadata.trackNumber = getMdLong(data,
            MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
        if (!oldMetadata.equals(mMetadata.toString())) {
            if (mTrackChangedNT == NOTIFICATION_TYPE_INTERIM) {
                mTrackChangedNT = NOTIFICATION_TYPE_CHANGED;
                sendTrackChangedRsp();
            }

            if (mCurrentPosMs != RemoteControlClient.PLAYBACK_POSITION_INVALID) {
                mCurrentPosMs = 0L;
                if (mCurrentPlayState == RemoteControlClient.PLAYSTATE_PLAYING) {
                    mPlayStartTimeMs = SystemClock.elapsedRealtime();
                }
            }
            /* need send play position changed notification when track is changed */
            if (mPlayPosChangedNT == NOTIFICATION_TYPE_INTERIM) {
                mPlayPosChangedNT = NOTIFICATION_TYPE_CHANGED;
                registerNotificationRspPlayPosNative(mPlayPosChangedNT,
                                                     (int)getPlayPosition());
                mHandler.removeMessages(MESSAGE_PLAY_INTERVAL_TIMEOUT);
            }
        }
        if (DEBUG) Log.v(TAG, "mMetadata=" + mMetadata.toString());

        mSongLengthMs = getMdLong(data, MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (DEBUG) Log.v(TAG, "duration=" + mSongLengthMs);
    }

    private void getRcFeatures(byte[] address, int features) {
        Message msg = mHandler.obtainMessage(MESSAGE_GET_RC_FEATURES, features, 0,
                                             Utils.getAddressStringFromByte(address));
        mHandler.sendMessage(msg);
    }

    private void getPlayStatus() {
        Message msg = mHandler.obtainMessage(MESSAGE_GET_PLAY_STATUS);
        mHandler.sendMessage(msg);
    }

    private void getElementAttr(byte numAttr, int[] attrs) {
        int i;
        ArrayList<Integer> attrList = new ArrayList<Integer>();
        for (i = 0; i < numAttr; ++i) {
            attrList.add(attrs[i]);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_ELEM_ATTRS, (int)numAttr, 0, attrList);
        mHandler.sendMessage(msg);
    }

    private void registerNotification(int eventId, int param) {
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_NOTIFICATION, eventId, param);
        mHandler.sendMessage(msg);
    }

    private void processRegisterNotification(int eventId, int param) {
        switch (eventId) {
            case EVT_PLAY_STATUS_CHANGED:
                mPlayStatusChangedNT = NOTIFICATION_TYPE_INTERIM;
                registerNotificationRspPlayStatusNative(mPlayStatusChangedNT,
                                       convertPlayStateToPlayStatus(mCurrentPlayState));
                break;

            case EVT_TRACK_CHANGED:
                mTrackChangedNT = NOTIFICATION_TYPE_INTERIM;
                sendTrackChangedRsp();
                break;

            case EVT_PLAY_POS_CHANGED:
                long songPosition = getPlayPosition();
                mPlayPosChangedNT = NOTIFICATION_TYPE_INTERIM;
                mPlaybackIntervalMs = (long)param * 1000L;
                if (mCurrentPosMs != RemoteControlClient.PLAYBACK_POSITION_INVALID) {
                    mNextPosMs = songPosition + mPlaybackIntervalMs;
                    mPrevPosMs = songPosition - mPlaybackIntervalMs;
                    if (mCurrentPlayState == RemoteControlClient.PLAYSTATE_PLAYING) {
                        Message msg = mHandler.obtainMessage(MESSAGE_PLAY_INTERVAL_TIMEOUT);
                        mHandler.sendMessageDelayed(msg, mPlaybackIntervalMs);
                    }
                }
                registerNotificationRspPlayPosNative(mPlayPosChangedNT, (int)songPosition);
                break;
            //BRCM PlayerSettings ++
            case EVT_APP_SETTINGS_CHANGED:
                {
                    mAppSettingsChangedNT = NOTIFICATION_TYPE_INTERIM;
                    registerNotificationRspAppSettingsChagedNative(mAppSettingsChangedNT,
                        mRepeat, mShuffle);

                }
                break;
            //BRCM PlayerSettings --

    //BRCM AVRCP 1.4 Enhancements ++
            case EVT_AVBL_PLAYERS_CHANGED:
                 registerNotificationRspAvalPlayerChangedNative(NOTIFICATION_TYPE_INTERIM);
                 break;

            case EVT_ADDR_PLAYER_CHANGED:
                 registerNotificationRspAddrPlayerChangedNative(NOTIFICATION_TYPE_INTERIM,
                        (int)mCurrPlayerID, (int)mUIDCounter);
                 break;
    //BRCM AVRCP 1.4 Enhancements --
        }
    }

    private void handlePassthroughCmd(int id, int keyState) {
        switch (id) {
            case AVRC_ID_REWIND:
                rewind(keyState);
                break;
            case AVRC_ID_FAST_FOR:
                fastForward(keyState);
                break;
        }
    }

    private void fastForward(int keyState) {
        Message msg = mHandler.obtainMessage(MESSAGE_FAST_FORWARD, keyState, 0);
        mHandler.sendMessage(msg);
    }

    private void rewind(int keyState) {
        Message msg = mHandler.obtainMessage(MESSAGE_REWIND, keyState, 0);
        mHandler.sendMessage(msg);
    }

    private void changePositionBy(long amount) {
        long currentPosMs = getPlayPosition();
        if (currentPosMs == -1L) return;
        long newPosMs = Math.max(0L, currentPosMs + amount);
        mAudioManager.setRemoteControlClientPlaybackPosition(mClientGeneration,
                newPosMs);
    }

    private int getSkipMultiplier() {
        long currentTime = SystemClock.elapsedRealtime();
        long multi = (long) Math.pow(2, (currentTime - mSkipStartTime)/SKIP_DOUBLE_INTERVAL);
        return (int) Math.min(MAX_MULTIPLIER_VALUE, multi);
    }

    private void sendTrackChangedRsp() {
        byte[] track = new byte[TRACK_ID_SIZE];
        // CSP 723494 : Send default track number when no record is set.
        /* track is stored in big endian format */
        for (int i = 0; i < TRACK_ID_SIZE; ++i) {
            track[i] = (mMetadata.trackNumber == -1L)?(byte) 0xff :
                (byte) (mMetadata.trackNumber >> (56 - 8 * i));
        }
        registerNotificationRspTrackChangeNative(mTrackChangedNT, track);
    }

    private long getPlayPosition() {
        long songPosition = -1L;
        if (mCurrentPosMs != RemoteControlClient.PLAYBACK_POSITION_INVALID) {
            if (mCurrentPlayState == RemoteControlClient.PLAYSTATE_PLAYING) {
                songPosition = SystemClock.elapsedRealtime() -
                               mPlayStartTimeMs + mCurrentPosMs;
            } else {
                songPosition = mCurrentPosMs;
            }
        }
        if (DEBUG) Log.v(TAG, "position=" + songPosition);
        return songPosition;
    }

    private String getAttributeString(int attrId) {
        String attrStr = null;
        switch (attrId) {
            case MEDIA_ATTR_TITLE:
                attrStr = mMetadata.trackTitle;
                break;

            case MEDIA_ATTR_ARTIST:
                attrStr = mMetadata.artist;
                break;

            case MEDIA_ATTR_ALBUM:
                attrStr = mMetadata.albumTitle;
                break;

            case MEDIA_ATTR_TRACK_NUM:
                if (mMetadata.trackNumber!= -1L) {
                    attrStr = Long.toString(mMetadata.trackNumber);
                }
                break;

            case MEDIA_ATTR_NUM_TRACKS:
                if (mMetadata.numberOfTracks != 0L) {
                    attrStr = Long.toString(mMetadata.numberOfTracks);
                }
                break;

            case MEDIA_ATTR_PLAYING_TIME:
                if (mSongLengthMs != 0L) {
                    attrStr = Long.toString(mSongLengthMs);
                }
                break;

        }
        if (attrStr == null) {
            attrStr = new String();
        }
        if (DEBUG) Log.v(TAG, "getAttributeString:attrId=" + attrId + " str=" + attrStr);
        return attrStr;
    }

    // BRCM PlayerSettings ++
    private int getRepeatMode() {
        if(DEBUG) Log.d(TAG, "getRepeatMode()="+mRepeat);
        return mRepeat;
    }

    private void setRepeatMode(int mode) {
        if(DEBUG) Log.d(TAG, "setRepeatMode()-"+mode);
        if(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex] != null) {
            Log.d(TAG, "Sending broadcast for :"+AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            Intent intent = new Intent(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            intent.putExtra(intentExtras.get("Repeat"), mode);
            mContext.sendBroadcast(intent);
        } else {
            Log.e(TAG, "No Repeat Intent supported for player index :"+mPlayerIndex);
        }

             mHandler.sendMessageDelayed(mHandler.obtainMessage(
                MSG_APP_SETTINGS_TIMER_EVENT),AVRCP_HANDLER_EVENT_TIMEOUT);

    }

    private int getShuffleMode() {
        if(DEBUG) Log.d(TAG, "getShuffleMode()-"+mShuffle);
        return mShuffle;
    }

    private void setShuffleMode(int shuffle) {
        if(DEBUG) Log.d(TAG, "setShuffleMode()-"+shuffle);

        if(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex] != null) {
            Log.d(TAG, "Sending broadcast for :"+AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            Intent intent = new Intent(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            intent.putExtra(intentExtras.get("Shuffle"), shuffle);
            mContext.sendBroadcast(intent);
        } else {
            Log.e(TAG, "No Shuffle Intent supported for player index :"+mPlayerIndex);
        }

          mHandler.sendMessageDelayed(mHandler.obtainMessage(
          MSG_APP_SETTINGS_TIMER_EVENT),AVRCP_HANDLER_EVENT_TIMEOUT);
    }
    // BRCM PlayerSettings --

    private int convertPlayStateToPlayStatus(int playState) {
        int playStatus = PLAYSTATUS_ERROR;
        switch (playState) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
            case RemoteControlClient.PLAYSTATE_BUFFERING:
                playStatus = PLAYSTATUS_PLAYING;
                break;

            case RemoteControlClient.PLAYSTATE_STOPPED:
            case RemoteControlClient.PLAYSTATE_NONE:
                playStatus = PLAYSTATUS_STOPPED;
                break;

            case RemoteControlClient.PLAYSTATE_PAUSED:
                playStatus = PLAYSTATUS_PAUSED;
                break;

            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
                playStatus = PLAYSTATUS_FWD_SEEK;
                break;

            case RemoteControlClient.PLAYSTATE_REWINDING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
                playStatus = PLAYSTATUS_REV_SEEK;
                break;

            case RemoteControlClient.PLAYSTATE_ERROR:
                playStatus = PLAYSTATUS_ERROR;
                break;

        }
        return playStatus;
    }

    //BRCM RC Enhancements
    private void onRCConnectionStateChanged(int state, byte[] address) {
        mService.onRCConnectionStateChanged(state,address);
    }

    /**
     * This is called from AudioService. It will return whether this device supports abs volume.
     * NOT USED AT THE MOMENT.
     */
    public boolean isAbsoluteVolumeSupported() {
        return ((mFeatures & BTRC_FEAT_ABSOLUTE_VOLUME) != 0);
    }

    /**
     * We get this call from AudioService. This will send a message to our handler object,
     * requesting our handler to call setVolumeNative()
     */
    public void adjustVolume(int direction) {
        Message msg = mHandler.obtainMessage(MESSAGE_ADJUST_VOLUME, direction, 0);
        mHandler.sendMessage(msg);
    }

    public void setAbsoluteVolume(int volume) {
        int avrcpVolume = convertToAvrcpVolume(volume);
        avrcpVolume = Math.min(AVRCP_MAX_VOL, Math.max(0, avrcpVolume));
        mHandler.removeMessages(MESSAGE_ADJUST_VOLUME);
        Message msg = mHandler.obtainMessage(MESSAGE_SET_ABSOLUTE_VOLUME, avrcpVolume, 0);
        mHandler.sendMessage(msg);

    }

    /* Called in the native layer as a btrc_callback to return the volume set on the carkit in the
     * case when the volume is change locally on the carkit. This notification is not called when
     * the volume is changed from the phone.
     *
     * This method will send a message to our handler to change the local stored volume and notify
     * AudioService to update the UI
     */
    private void volumeChangeCallback(int volume, int ctype) {
        Message msg = mHandler.obtainMessage(MESSAGE_VOLUME_CHANGED, volume, ctype);
        mHandler.sendMessage(msg);
    }

    //BRCM AVRCP 1.4 Enhancements ++
     private void getFolderItemsCallback(byte scope, int start_item,
             int end_item, byte num_attr, byte[] attr_ids,String[] attr_text) {
       FolderItemsCmd FolderObj=new FolderItemsCmd(scope,start_item,end_item,
                                    num_attr,attr_ids,attr_text);
       Message msg = mHandler.obtainMessage(MESSAGE_GET_FOLDER_ITEMS);
       msg.obj=FolderObj;
       msg.arg1 = 0; // false
       mHandler.sendMessage(msg);
    }

    private void setAddrPlayerCallback(int player_id) {
        Message msg = mHandler.obtainMessage(MESSAGE_SET_ADDR_PLAYER,
                        player_id, 0);
        mHandler.sendMessage(msg);
    }
    //BRCM AVRCP 1.4 Enhancements --

    private void notifyVolumeChanged(int volume) {
        volume = convertToAudioStreamVolume(volume);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume,
                      AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
    }

    private int convertToAudioStreamVolume(int volume) {
        // Rescale volume to match AudioSystem's volume
        return (int) Math.ceil((double) volume*mAudioStreamMax/AVRCP_MAX_VOL);
    }

    private int convertToAvrcpVolume(int volume) {
        return (int) Math.ceil((double) volume*AVRCP_MAX_VOL/mAudioStreamMax);
    }

    private class AvrcpServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "AvrcpServiceBroadcastReceiver-> Action: " + action);
            if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_DATA_CLEARED)) {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // a package is being removed, not replaced
                    String packageName = intent.getData().getSchemeSpecificPart();
                    if (packageName != null) {
                        NotifyRemotePackageRemoved(packageName, true);
                    }
                }
            } else if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)) {
                String packageName = intent.getData().getSchemeSpecificPart();
                Log.d(TAG,"AvrcpServiceBroadcastReceiver-> packageName: "
                       + packageName);
                if (packageName != null) {
                    NotifyRemotePackageRemoved(packageName, false);
                }
            } else if(action.equals(ADDR_PLAYER_CHANGED)) {
                Log.d(TAG, "intent received: EVENT_ADDRESS_PLAYER_CHANGED");

            try {
                 Bundle extras = intent.getExtras();
                 if (extras == null) {
                     Log.e (TAG, "extras null");
                     return;
                 }

                 String currPackageName =
                        extras.getString("currAddrPlayer");

                 if (currPackageName == null) {
                     Log.e (TAG, "currAddrPlayer null");
                     return;
                 }

                 Log.d(TAG, "EVENT_ADDRESS_PLAYER_CHANGED: "
                       + currPackageName);

                 if(mRspObj == null || mRspObj.mPackageNameList == null
                    || mRspObj.mPackageNameList.length == 0) {
                     Log.e (TAG, "packageName list is null or empty");
                     return;
                 }

                 List<String> packageList = new ArrayList<String>();
                 packageList = Arrays.asList(mRspObj.mPackageNameList);

                 if(packageList.contains(currPackageName)) {
                    mCurrPlayerID = (packageList.indexOf(currPackageName))+1;
                    Log.d(TAG, "Player list size:" + packageList.size() +
                        " Current Player ID:" + mCurrPlayerID);
                    if(mCurrPlayerID <= packageList.size()) {
                       registerNotificationRspAddrPlayerChangedNative
                       (NOTIFICATION_TYPE_CHANGED,
                       (int)mCurrPlayerID,(int)mUIDCounter);
                    }
                 } else {
                    Log.e(TAG, "Package name:" + currPackageName
                        + "not available in list");
                 }
             } catch (NullPointerException e) {
                    Log.e (TAG, "Exception: null pointer exception due to: " , e);
            }
            }
        }
    }

    private void NotifyRemotePackageRemoved(String packageName, boolean removed) {
        Log.d(TAG, "packageName: " + packageName + " removed: " + removed);
        List<String> packageList = new ArrayList<String>();
        int nIndex=0;

        try {
            if(mRspObj!=null && mRspObj.mPackageNameList != null
                && mRspObj.mPackageNameList.length > 0) {
                packageList = Arrays.asList(mRspObj.mPackageNameList);
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "mPackageNameList empty ");
        }

        try {
        if(removed) {
          if (!packageList.isEmpty()) {
            if(packageList.contains(packageName)) {
                registerNotificationRspAvalPlayerChangedNative(NOTIFICATION_TYPE_CHANGED);
                nIndex = packageList.indexOf(packageName);
                mCurrPlayerID = nIndex + 1;
                registerNotificationRspAddrPlayerChangedNative(NOTIFICATION_TYPE_CHANGED,
                        mCurrPlayerID,mUIDCounter);
            }
          }
        } else {

            final PackageManager pm = mContext.getPackageManager();
            Intent intentPackageList = new Intent(Intent.ACTION_VIEW);
            File file = new File((String) ("MusicFile"));
            intentPackageList.setDataAndType(Uri.fromFile(file), "audio/*");
            List<ResolveInfo> activitiesList =
                pm.queryIntentActivities(intentPackageList, 0 );

            if(!activitiesList.isEmpty()) {
                for (ResolveInfo info : activitiesList) {
                     ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                     if (packageName.equals(appInfo.packageName)) {
                        Log.d(TAG, "Installed Audio app's package: " + appInfo.packageName);
                        registerNotificationRspAvalPlayerChangedNative(NOTIFICATION_TYPE_CHANGED);
                        break;
                     }
                }
            }
          }
        } catch (NullPointerException e) {
            Log.d(TAG, "List empty ");
        }
    }

    // Do not modify without updating the HAL bt_rc.h files.

    // match up with btrc_play_status_t enum of bt_rc.h
    final static int PLAYSTATUS_STOPPED = 0;
    final static int PLAYSTATUS_PLAYING = 1;
    final static int PLAYSTATUS_PAUSED = 2;
    final static int PLAYSTATUS_FWD_SEEK = 3;
    final static int PLAYSTATUS_REV_SEEK = 4;
    final static int PLAYSTATUS_ERROR = 255;

    // match up with btrc_media_attr_t enum of bt_rc.h
    final static int MEDIA_ATTR_TITLE = 1;
    final static int MEDIA_ATTR_ARTIST = 2;
    final static int MEDIA_ATTR_ALBUM = 3;
    final static int MEDIA_ATTR_TRACK_NUM = 4;
    final static int MEDIA_ATTR_NUM_TRACKS = 5;
    final static int MEDIA_ATTR_GENRE = 6;
    final static int MEDIA_ATTR_PLAYING_TIME = 7;

    // match up with btrc_event_id_t enum of bt_rc.h
    final static int EVT_PLAY_STATUS_CHANGED = 1;
    final static int EVT_TRACK_CHANGED = 2;
    final static int EVT_TRACK_REACHED_END = 3;
    final static int EVT_TRACK_REACHED_START = 4;
    final static int EVT_PLAY_POS_CHANGED = 5;
    final static int EVT_BATT_STATUS_CHANGED = 6;
    final static int EVT_SYSTEM_STATUS_CHANGED = 7;
    final static int EVT_APP_SETTINGS_CHANGED = 8;
    //BRCM AVRCP 1.4 Enhancements ++
    final static int EVT_AVBL_PLAYERS_CHANGED = 0xa;
    final static int EVT_ADDR_PLAYER_CHANGED = 0xb;
    //BRCM AVRCP 1.4 Enhancements --

    // match up with btrc_notification_type_t enum of bt_rc.h
    final static int NOTIFICATION_TYPE_INTERIM = 0;
    final static int NOTIFICATION_TYPE_CHANGED = 1;

    // match up with BTRC_UID_SIZE of bt_rc.h
    final static int TRACK_ID_SIZE = 8;

    private native static void classInitNative();
    private native void initNative();
    private native void cleanupNative();
    private native boolean getPlayStatusRspNative(int playStatus, int songLen, int songPos);
    private native boolean getElementAttrRspNative(byte numAttr, int[] attrIds,
        String[] textArray);
    private native boolean registerNotificationRspPlayStatusNative(int type, int playStatus);
    private native boolean registerNotificationRspTrackChangeNative(int type, byte[] track);
    private native boolean registerNotificationRspPlayPosNative(int type, int playPos);
    private native boolean registerNotificationRspAvalPlayerChangedNative(int type);
    private native boolean registerNotificationRspAddrPlayerChangedNative(int type,
        int Player_id, int uid_counter);
    private native boolean setVolumeNative(int volume);
    //BRCM PlayerSettings ++
    private native boolean registerNotificationRspAppSettingsChagedNative(int type,
        int repeat, int shuffle);
    //BRCM PlayerSettings --
    //BRCM Rc Enhancements ++
    protected native boolean disconnectRcNative(byte[] address);
    //BRCM Rc Enhancements --
    //BRCM AVRCP 1.4 Enhancements ++
    private native boolean getFolderItemsMediaRspNative(int rsp_status,
        int uid_counter,byte item_type,int num_items,byte[] PlayerTypes,
        int[]PlayerSubTypes,byte[]PlayStatusValues,short[]FeatureBitMaskValues,
        String[] textArray);
    private native boolean setAddressedPlayerRspNative(int rsp_status);
    //BRCM AVRCP 1.4 Enhancements --
}
