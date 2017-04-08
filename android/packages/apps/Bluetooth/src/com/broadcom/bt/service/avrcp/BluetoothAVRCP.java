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
package com.broadcom.bt.service.avrcp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @hide
*/
public class BluetoothAVRCP {

    private static final String TAG = "BluetoothAVRCP";
    private static final boolean DBG = false;

    static final String ACTION_PREFIX = "com.broadcom.bt.app.avrcp.action.";

    static final int ACTION_PREFIX_LENGTH = ACTION_PREFIX.length();

    /** Intent in the event of Bluetooth Service Opened (Connected) */
    public static final String ACTION_ON_AVRCP_CONNECTED = ACTION_PREFIX
            + "ON_AVRCP_CONNECTED";

    /** Intent in the event of Bluetooth Service Closed (Disconnected) */
    public static final String ACTION_ON_AVRCP_DISCONNECTED = ACTION_PREFIX
            + "ON_AVRCP_DISCONNECTED";

    public static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    public static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    /* Indices to represent each of the supported media players.
    * To add any new media player support, add a new index and
    update the String arrays below with the respective action strings*/
    public static final int IDX_ANDROID_MEDIA_PLAYER = 0;
    public static final int IDX_GOOGLE_MEDIA_PLAYER = 1;
    public static final int IDX_AMAZON_MEDIA_PLAYER = 2;
    public static final int IDX_HTC_MEDIA_PLAYER = 3;
    public static final int IDX_DOUBLETWIST_MEDIA_PLAYER = 4;

    /**
     * Intent used to broadcast the change in connection state of the AVRCP
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
     */
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "com.broadcom.bt..avrcp.profile.action.CONNECTION_STATE_CHANGED";

    /* Set default index for Android media player */
    private static int mPlayerIndex = IDX_ANDROID_MEDIA_PLAYER;
    public static final String[] AVRCP_ACTION_PLAYSTATE_CHANGED = {
        "com.android.music.playstatechanged",
        "com.android.music.playstatechanged",
        "com.amazon.mp3.playstatechanged",
        "com.htc.music.playstatechanged",
        "com.doubleTwist.androidPlayer.playstatechanged",
    };

    public static final String[] AVRCP_ACTION_META_CHANGED = {
        "com.android.music.metachanged",
        "com.android.music.metachanged",
        "com.amazon.mp3.metachanged",
        "com.htc.music.metachanged",
        "com.doubleTwist.androidPlayer.metachanged"
    };

    /* Repeat and Shuffle state change intents for supported players */
    /* Amazon mp3 player sends "com.amazon.mp3.playstatechanged" for shuffle state change.
    DoubleTwist player sends "com.doubleTwist.androidPlayer.playstatechanged" for repeat state change.
       Hence registering only for the other two intents. */

    public static final String[] AVRCP_ACTION_REPEAT_CHANGED = {
        "com.android.music.settingchanged",
        "com.android.music.settingchanged",
        "com.amazon.mp3.repeatstatechanged",
        null,
        null
    };
    public static final String[] AVRCP_ACTION_QUEUE_CHANGED = {
        "com.android.music.queuechanged",
        "com.android.music.queuechanged",
        null,
        null,
        "com.doubleTwist.androidPlayer.queuechanged"
    };

    public static final String[] AVRCP_ACTION_PLAYSTATUS_REQUEST = {
        "com.android.music.playstaterequest",
        "com.android.music.playstaterequest",
        null,
        null,
        null,
    };
    public static final String[] AVRCP_ACTION_APP_SETTING_REQUEST = {
        "com.android.music.settingrequest",
        "com.android.music.settingrequest",
        null,
        null,
        null,
    };

    public static final String PLAYSTATUS_REQUEST = "com.android.music.playstatusrequest";
    public static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";
    public static final int    PLAYPOS_TIMER_EVENT = 0;
    public static final int    PLAYSTATUS_TIMER_EVENT = 1;
    public static final int    APP_SETTINGS_TIMER_EVENT = 2;
    public static final int    GET_PLAYPOS_REQ_EVENT = 3;
    public static final int    CONN_STATE_CHANGED_EVENT = 4;

    public final static String DEFAULT_METADATA_STRING = "Unknown";
    public final static String DEFAULT_METADATA_NUM    = "0";
    public final static long   DEFAULT_TRACK_NUM = -1;

    public static final int PLAYSTATE_STOPPED = 0x00;    /* Stopped */
    public static final int PLAYSTATE_PLAYING = 0x01;    /* Playing */
    public static final int PLAYSTATE_PAUSED  = 0x02;    /* Paused  */
    public static final int PLAYSTATE_FWD_SEEK = 0x03;  /* Fwd Seek*/
    public static final int PLAYSTATE_REV_SEEK = 0x04;  /* Rev Seek*/
    public static final int PLAYSTATE_INVALID = 0xFF;

    /* Added repeat/shuffle status */
    public static final int REPEAT_OFF    = 0x01;
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

    private String mTitle = DEFAULT_METADATA_STRING;
    private String mArtistName = DEFAULT_METADATA_STRING;
    private String mAlbumName  = DEFAULT_METADATA_STRING;
    private long   mTrackNum = DEFAULT_TRACK_NUM;
    private long   mTotalTracks      = 0;
    private long   mDuration         = 0;
    private int    mPlayStatus       = PLAYSTATE_STOPPED;
    private long   mPosition         = 0;

    private long   mTrackID = -1;

    /* Added flags for tracking repeat and shuffle status */
    private int mRepeat          = REPEAT_OFF;
    private int mShuffle         = SHUFFLE_OFF;

    /* HashMap for mapping intent specific extras to required attributes. */
    private HashMap<String, String> intentExtras = new HashMap<String, String>();
    private Context mContext;

    public BluetoothAVRCP(Context context) {
        Log.d(TAG, "BluetoothAVRCP()");
        mContext = context;
    }

    public void cleanup() {
        Log.d(TAG, "cleanup()");
        mContext = null;
        intentExtras.clear();
    }

    public boolean findMetaChangedAction(String str) {
        if(DBG) Log.d(TAG, "findMetaChangedAction()-"+str);
        return findAction(AVRCP_ACTION_META_CHANGED, str);
    }

    public boolean findPlayStateChangedAction(String str) {
        if(DBG) Log.d(TAG, "findPlayStateChangedAction()-"+str);
        return findAction(AVRCP_ACTION_PLAYSTATE_CHANGED, str);
    }

    public boolean findRepeatOrShuffleChangedAction(String str) {
        if(DBG) Log.d(TAG, "findRepeatChangedAction()-"+str);
        return findAction(AVRCP_ACTION_REPEAT_CHANGED, str);
    }

    public boolean findQueueChangedAction(String str) {
        if(DBG) Log.d(TAG, "findQueueChangedAction()-"+str);
        return findAction(AVRCP_ACTION_QUEUE_CHANGED, str);
    }

    private boolean findAction(String[] arr, String str) {
        Log.d(TAG, "findAction() - "+str);
        boolean result = false;
        for(int i=0; i< arr.length; i++) {
             if(arr[i] != null && str.equals(arr[i])) {
                mPlayerIndex = i;
                result = true;
                return result;
            }
        }
        return result;
    }

    void dumpExtras (Intent intent) {
        Log.w(TAG,"In dumpExtras - Received intent - " + intent.getAction());
        Bundle extras = intent.getExtras();
        if(extras != null) {
            if(DBG) Log.d(TAG, "dumpExtras() :"+extras);
            Set<String> ks = extras.keySet();
            Iterator<String> iterator = ks.iterator();
            while (iterator.hasNext()) {
                Log.d(TAG+" - KEY", iterator.next());
            }
        }
    }

    public IntentFilter fillIntentFilter() {
        IntentFilter filter = new IntentFilter();
        if(DBG) Log.d(TAG, "fillIntentFilter()");
        resetMetadata();

        /* Register for the intents of the supported players */
        for(int i = 0; i < AVRCP_ACTION_META_CHANGED.length; i++) {
            if(AVRCP_ACTION_META_CHANGED[i] != null
                && !(filter.hasAction(AVRCP_ACTION_META_CHANGED[i])))
                filter.addAction(AVRCP_ACTION_META_CHANGED[i]);
        }

        for(int i = 0; i < AVRCP_ACTION_PLAYSTATE_CHANGED.length; i++) {
            if(AVRCP_ACTION_PLAYSTATE_CHANGED[i] != null
                && !(filter.hasAction(AVRCP_ACTION_PLAYSTATE_CHANGED[i])))
                filter.addAction(AVRCP_ACTION_PLAYSTATE_CHANGED[i]);
        }

        for(int i = 0; i < AVRCP_ACTION_REPEAT_CHANGED.length; i++) {
            if(AVRCP_ACTION_REPEAT_CHANGED[i] != null
                && !(filter.hasAction(AVRCP_ACTION_REPEAT_CHANGED[i])))
                filter.addAction(AVRCP_ACTION_REPEAT_CHANGED[i]);
        }

        for(int i = 0; i < AVRCP_ACTION_QUEUE_CHANGED.length; i++) {
            if(AVRCP_ACTION_QUEUE_CHANGED[i] != null
                && !(filter.hasAction(AVRCP_ACTION_QUEUE_CHANGED[i])))
                filter.addAction(AVRCP_ACTION_QUEUE_CHANGED[i]);
        }

        filter.addAction(PLAYSTATUS_RESPONSE);

        return filter;
    }

    public String getTitle() {
        if(DBG) Log.d(TAG, "getTitle()="+mTitle);
        return mTitle;
    }

    public String getArtistName() {
        if(DBG) Log.d(TAG, "getArtistName()="+mArtistName);
        return mArtistName;
    }

    public String getAlbumName() {
        if(DBG) Log.d(TAG, "getAlbumName()="+mAlbumName);
        return mAlbumName;
    }

    public String getTrackNum() {
        if(DBG) Log.d(TAG, "getTrackNum()="+mTrackNum);
        return String.valueOf(mTrackNum);
    }

    public String getTotalTracks() {
        if(DBG) Log.d(TAG, "getTotalTracks()="+mTotalTracks);
        return String.valueOf(mTotalTracks);
    }

    public String getDurationText() {
        if(DBG) Log.d(TAG, "getDurationText()="+mDuration);
        return String.valueOf(mDuration);
    }

    public long getDuration() {
        if(DBG) Log.d(TAG, "getDuration()="+mDuration);
        return mDuration;
    }

    public int getPlayStatus() {
        if(DBG) Log.d(TAG, "getPlayStatus()="+mPlayStatus);
        return mPlayStatus;
    }

    public void resetAvrcpPlaybackData() {
        mPlayStatus = PLAYSTATE_STOPPED;
        mPosition   = 0;
        mDuration   = 0;
    }

    public void resetMetadata() {
        mTitle          = "";
        mArtistName     = "";
        mAlbumName      = "";
        mTrackNum       = DEFAULT_TRACK_NUM;
        mTotalTracks    = 0;
        resetAvrcpPlaybackData();
    }

    public long getPlayPosition() {
        if(DBG) Log.d(TAG, "getPlayPosition()="+mPosition);
        return mPosition;
    }

    public long getTrackNumLong() {
        if(DBG) Log.d(TAG, "getTrackNumLong()="+mTrackNum);
        return Long.reverseBytes(mTrackNum);
    }
    public int getRepeatMode() {
        if(DBG) Log.d(TAG, "getRepeatMode()="+mRepeat);
        return mRepeat;
    }

    public void setRepeatMode(int mode) {
        if(DBG) Log.d(TAG, "setRepeatMode()-"+mode);
        if(DBG) Log.d(TAG, "setRepeatMode() for index:"+mPlayerIndex);
        if(mPlayerIndex < 0) {
            Log.e(TAG, "Player not yet selected. Hence returning");
            return;
        }
        if(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex] != null) {
            Log.d(TAG, "Sending broadcast for :"+AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            Intent intent = new Intent(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            intent.putExtra(intentExtras.get("Repeat"), mode);
            mContext.sendBroadcast(intent);
        } else {
            Log.e(TAG, "No Repeat Intent supported for player index :"+mPlayerIndex);
        }
    }

    public int getShuffleMode() {
        if(DBG) Log.d(TAG, "getShuffleMode()-"+mShuffle);
        return mShuffle;
    }

    public void setShuffleMode(int shuffle) {
        if(DBG) Log.d(TAG, "setShuffleMode()-"+shuffle);
        if(DBG) Log.d(TAG, "setShuffleMode() for index:"+mPlayerIndex);
        if(mPlayerIndex < 0) {
            Log.e(TAG, "Player not yet selected. Hence returning");
            return;
        }
        if(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex] != null) {
            Log.d(TAG, "Sending broadcast for :"+AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            Intent intent = new Intent(AVRCP_ACTION_APP_SETTING_REQUEST[mPlayerIndex]);
            intent.putExtra(intentExtras.get("Shuffle"), shuffle);
            mContext.sendBroadcast(intent);
        } else {
            Log.e(TAG, "No Shuffle Intent supported for player index :"+mPlayerIndex);
        }
    }

    public int getPlayerIndex() {
        if(DBG) Log.d(TAG, "getPlayerIndex()="+mPlayerIndex);
        return mPlayerIndex;
    }
    public boolean hasTrackInfoChanged(Intent intent) {
        if(DBG) Log.d(TAG, "hasTrackInfoChanged()");
        long trackNum;
        try {
            if(intent.hasExtra(intentExtras.get("Title"))) {
                String title = intent.getStringExtra(intentExtras.get("Title"));
                if(title == null) {
                    Log.e(TAG, "No Title present in intent");
                    return false;
                }
            } else {
                Log.e(TAG, "No Title present in intent");
                return false;
            }

            if(!(intent.hasExtra(intentExtras.get("TrackNum")))) {
                Log.e(TAG, "No Track ID present in intent");
                return false;
            }
            Object track = (Object) intent.getExtra(intentExtras.get("TrackNum"), 0);
            if (track == null) {
                Log.e(TAG, "TrackNum returned null");
                return false;
            }
            else if(track instanceof Long)
                trackNum = ((Long)track).longValue();
            else if(track instanceof Integer)
                trackNum = ((Integer)track).intValue();
            else if(track instanceof Short)
                trackNum = ((Short)track).shortValue();
            else
                trackNum = 0;

            if(trackNum == 0) {
                Log.e(TAG, "trackNum is 0. ");
                return false;
            }
            //Also check for condition where Track ID has not changed;
            //but play state has changed
            if(trackNum == mTrackNum ) {
                Log.d(TAG, "Track ID has not changed : "+trackNum);
                if(!checkPlayStateInIntent(intent))
                    return false;
            }
        }
        catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
            return false;
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in hasTrackInfoChanged()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return false;
        }
        Log.d(TAG, "Info has changed in intent!");
        return true;
    }
    public void sendPlayStateRequest() {
        if(DBG) Log.d(TAG, "sendPlayStateRequest() for index:"+mPlayerIndex);
        if(mPlayerIndex < 0) {
            Log.e(TAG, "Player not yet selected. Hence returning");
            return;
        }
        if(AVRCP_ACTION_PLAYSTATUS_REQUEST[mPlayerIndex] != null) {
            Log.d(TAG, "Sending broadcast for :"+AVRCP_ACTION_PLAYSTATUS_REQUEST[mPlayerIndex]);
            mContext.sendBroadcast(new Intent(AVRCP_ACTION_PLAYSTATUS_REQUEST[mPlayerIndex]));
        } else {
            Log.e(TAG, "No request string supported for player index :"+mPlayerIndex);
        }
    }
    /* Maps the required attributes with Extras corresponding to the media player's intent.
     * Any media player that needs support has to fill here the intentExtras hashmap
     * structure based on its intent Extras.
     */
    public void fillIntentExtras(Intent intent) {
        if(DBG) Log.d(TAG, "fillIntentExtras() "+intent);
        String action = intent.getAction();
        Log.d(TAG,"fillIntentExtras - received action - "+action);
        if (action.equals(AVRCP_ACTION_META_CHANGED[IDX_ANDROID_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_META_CHANGED[IDX_GOOGLE_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_ANDROID_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_GOOGLE_MEDIA_PLAYER]))
        {
            intentExtras.clear();
            /* Changes in intentExtras for Android/Google music player */
            intentExtras.put("Title", "track");
            intentExtras.put("Artist", "artist");
            intentExtras.put("Album", "album");
            intentExtras.put("TrackNum", "tracknum");
            intentExtras.put("TotalTracks", "numberoftracks");
            intentExtras.put("Duration", "duration");
            intentExtras.put("Position", "position");
            /* Android music player sends playing, google music sends playstate */
            if (intent.hasExtra("playing"))
                intentExtras.put("PlayState", "playing");
            else if (intent.hasExtra("playstate"))
                intentExtras.put("PlayState", "playstate");
            intentExtras.put("Repeat", "repeat");    /* ------> TODO "repeat" */
            intentExtras.put("Shuffle", "shuffle");  /* ------> TODO "shuffle" */
        }
        else if (action.equals(AVRCP_ACTION_META_CHANGED[IDX_AMAZON_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_AMAZON_MEDIA_PLAYER]))
        {
            intentExtras.clear();
            /* Changes in intentExtras for Amazon player */
            intentExtras.put("Title", "com.amazon.mp3.track");
            intentExtras.put("Artist", "com.amazon.mp3.artist");
            intentExtras.put("Album", "com.amazon.mp3.album");
            intentExtras.put("TrackNum", "com.amazon.mp3.id");
            intentExtras.put("TotalTracks", "track_count_int_key");
            intentExtras.put("Duration", "duration");   /* ------> TODO "duration" */
            intentExtras.put("Position", "track_position_int_key");
            intentExtras.put("PlayState", "com.amazon.mp3.playstate");
            intentExtras.put("Repeat", "repeat_state_key");
            intentExtras.put("Shuffle", "shuffle_enabled_key");
        }
        else if (action.equals(AVRCP_ACTION_META_CHANGED[IDX_HTC_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_HTC_MEDIA_PLAYER]))
        {
            intentExtras.clear();
            /* Changes in intentExtras for HTC player */
            intentExtras.put("Title", "track");
            intentExtras.put("Artist", "artist");
            intentExtras.put("Album", "album");
            intentExtras.put("TrackNum", "id");
            intentExtras.put("TotalTracks", "totaltracks"); /* ------> TODO "totaltracks" */
            intentExtras.put("Duration", "duration");       /* ------> TODO "duration"    */
            intentExtras.put("Position", "position");       /* ------> TODO "position"    */
            intentExtras.put("PlayState", "isplaying");
            intentExtras.put("Repeat", "repeat");           /* ------> TODO "repeat"      */
            intentExtras.put("Shuffle", "shuffle");         /* ------> TODO "shuffle"     */
        }
        else if (action.equals(AVRCP_ACTION_META_CHANGED[IDX_DOUBLETWIST_MEDIA_PLAYER]) ||
            action.equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_DOUBLETWIST_MEDIA_PLAYER]))
       {
           intentExtras.clear();
           /* Changes in intentExtras for DoubleTwist player */
           intentExtras.put("Title", "track");
           intentExtras.put("Artist", "artist");
           intentExtras.put("Album", "album");
           intentExtras.put("TrackNum", "song_id");
           /* ------> TODO "totaltracks"  */
           intentExtras.put("TotalTracks", "totaltracks");
           /* ------> TODO "duration"     */
           intentExtras.put("Duration", "duration");
           /* ------> TODO "position"     */
           intentExtras.put("Position", "position");
           intentExtras.put("PlayState","playing");
           intentExtras.put("Repeat", "repeat");
           intentExtras.put("Shuffle", "shuffle");
       }
    }

    /* Function to get the Play state value. First identify the data type as all supported players
    send play states in different datatypes.
    Returns  PLAYSTATE_INVALID if error while parsing intent
                else valid playstate PLAYSTATE_PLAYING, PLAYSTATE_PAUSED, PLAYSTATE_STOPPED,
                PLAYSTATE_FWD_SEEK, PLAYSTATE_REV_SEEK */
    public int getPlayStateValue(Intent intent) {
        String strPlayStatus = "";
        int tmpPlayStatus = -1;
        boolean tmp_bPlayStatus = false, is_playstatus_string = false;
        Object obj_playstatus;
        if (intent.hasExtra(intentExtras.get("PlayState"))) {
            try {
                obj_playstatus = (Object) intent.getExtra(intentExtras.get("PlayState"));

                if(obj_playstatus == null) {
                    Log.w(TAG, "Playstate is null in intent");
                    return PLAYSTATE_INVALID;
                } else if(obj_playstatus instanceof String) {
                    if(DBG) Log.d(TAG, "Play Status is String");
                    is_playstatus_string = true;
                } else
                    is_playstatus_string = false;
            } catch(Exception ee) {
                Log.e(TAG, "Exception while parsing playstate", ee);
                return PLAYSTATE_INVALID;
            }
            if(is_playstatus_string == true) {
                strPlayStatus = (String) obj_playstatus;
                if(DBG) Log.d(TAG, " *** state:"+strPlayStatus);
                if(strPlayStatus == null) {
                    Log.w(TAG, "playstate is null in checkPlayStateInIntent()");
                    return PLAYSTATE_INVALID;
                }
                if (strPlayStatus.equals("playing"))
                {
                    tmpPlayStatus = PLAYSTATE_PLAYING;
                }
                else if (strPlayStatus.equals("stopped"))
                {
                    tmpPlayStatus = PLAYSTATE_STOPPED;
                }
                else if (strPlayStatus.equals("paused"))
                {
                    long tmpPosition = updatePositionFromIntent(intent);
                    if(DBG) Log.d(TAG, "*** tmpPosition:"+tmpPosition);
                    if(tmpPosition == 0) {
                        tmpPlayStatus = PLAYSTATE_STOPPED;
                    }
                    else
                        tmpPlayStatus = PLAYSTATE_PAUSED;
                }
                else if (strPlayStatus.equals("forwarding"))
                {
                    tmpPlayStatus = PLAYSTATE_FWD_SEEK;
                }
                else if (strPlayStatus.equals("rewinding"))
                {
                    tmpPlayStatus = PLAYSTATE_REV_SEEK;
                }
                else
                {
                    Log.w(TAG, "play status not defined ??!!");
                }
            } else {
                if(obj_playstatus instanceof Boolean) {
                    tmp_bPlayStatus = ((Boolean)obj_playstatus).booleanValue();
                    if(DBG) Log.d(TAG, " @@@@ state:"+tmp_bPlayStatus);
                    long tmpPosition = updatePositionFromIntent(intent);
                    if(DBG) Log.d(TAG, "@@@@ tmpPosition:"+tmpPosition);
                    if (!tmp_bPlayStatus && (tmpPosition == 0)) {
                        tmpPlayStatus = PLAYSTATE_STOPPED;
                    } else if (!tmp_bPlayStatus) {
                        tmpPlayStatus = PLAYSTATE_PAUSED;
                    } else {
                        tmpPlayStatus = PLAYSTATE_PLAYING;
                    }
                } else if(obj_playstatus instanceof Integer) {
                    tmpPlayStatus = ((Integer)obj_playstatus).intValue();
                    tmpPlayStatus = convertToAvrcpPlayState(mPlayerIndex, tmpPlayStatus);
                } else {
                    Log.e(TAG, "PlayState present in unsupported datatype");
                    tmpPlayStatus = PLAYSTATE_INVALID;
                }
            }
        } else {
            Log.e(TAG, "No playstate TAG present in intent");
            return PLAYSTATE_INVALID;
        }
        return tmpPlayStatus;
    }

    public void updatePlayStateValue(Intent intent) {
        int tmpStatus = -1;
        if(DBG) Log.d(TAG, "updatePlayStateValue() : "+intent);

        tmpStatus = getPlayStateValue(intent);
        if(tmpStatus == PLAYSTATE_INVALID) {
            Log.i(TAG, "Failed to extract PLAY STATE from intent");
            return;
        }
        if(tmpStatus != mPlayStatus) {
            Log.d(TAG, "updatePlayStateValue() Play state changing from :"+mPlayStatus+" --> "+
                tmpStatus);
            mPlayStatus = tmpStatus;
            if(mPlayStatus == PLAYSTATE_STOPPED) {
                Log.d(TAG, "Resetting mPosition and mDuration to 0 for PLAYSTATE_STOPPED");
                mPosition = mDuration = 0;
            }
        }
    }

    long updateDurationFromIntent(Intent intent) {
        long tmpDuration;
        if(DBG) Log.d(TAG, "updateDurationFromIntent() : "+intent);
        if (intent.hasExtra(intentExtras.get("Duration"))) {
            Object tmp = (Object) intent.getExtra(intentExtras.get("Duration"), null);
            if(tmp == null) {
                if(DBG) Log.d(TAG, "updateDurationFromIntent() Duration not present in intent");
                return mDuration;
            }
            else if(tmp instanceof Long)
                tmpDuration = ((Long)tmp).longValue();
            else if(tmp instanceof Integer)
                tmpDuration = ((Integer)tmp).intValue();
            else if(tmp instanceof Short)
                tmpDuration = ((Short)tmp).shortValue();
            else
                tmpDuration = 0;

            if (tmpDuration < 0)
                tmpDuration = 0;
            return tmpDuration;
        }
        return mDuration;
    }

    long updatePositionFromIntent(Intent intent) {
        long tmpPosition;
        if(DBG) Log.d(TAG, "updatePositionFromIntent() : "+intent);
        if (intent.hasExtra(intentExtras.get("Position"))) {
            Object tmp = (Object) intent.getExtra(intentExtras.get("Position"), null);
            if(tmp == null) {
                if(DBG) Log.d(TAG, "updatePositionFromIntent() Position not present in intent");
                return mPosition;
            }
            else if(tmp instanceof Long)
                tmpPosition = ((Long)tmp).longValue();
            else if(tmp instanceof Integer)
                tmpPosition = ((Integer)tmp).intValue();
            else if(tmp instanceof Short)
                tmpPosition = ((Short)tmp).shortValue();
            else
                tmpPosition = 0;

            if (tmpPosition < 0)
                tmpPosition = 0;
            return tmpPosition;
        }
        return mPosition;
    }

    /* Update Current Track value from incoming intent */
    public void updateCurrentTrackFromIntent(Intent intent) {
        try {
            long trackNum = 0;

            Object track = (Object) intent.getExtra(intentExtras.get("TrackNum"), 0);
            if (track == null) {
                Log.e(TAG, "TrackNum returned null");
                trackNum = 0;
            }
            else if(track instanceof Long)
                trackNum = ((Long)track).longValue();
            else if(track instanceof Integer)
                trackNum = ((Integer)track).intValue();
            else if(track instanceof Short)
                trackNum = ((Short)track).shortValue();
            else
                trackNum = 0;

            if (trackNum < 0)
                trackNum = DEFAULT_TRACK_NUM;

            if (trackNum != mTrackNum)
                Log.d(TAG, "Current Track has changed");
            mTrackNum = trackNum;
        } catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in updateCurrentTrackFromIntent()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return;
        }
    }

    /* Update Total Track count (in current playing list) from incoming intent */
    public void updateTotalTracksFromIntent(Intent intent) {
        boolean rv = false;
        long tracks = 0;
        try {
            Object totalTracks = (Object) intent.getExtra(intentExtras.get("TotalTracks"), 0);
            if (totalTracks == null) {
                Log.e(TAG, "TotalTracks returned null");
                tracks = 0;
            }
            else if(totalTracks instanceof Long)
                tracks = ((Long)totalTracks).longValue();
            else if(totalTracks instanceof Integer)
                tracks = ((Integer)totalTracks).intValue();
            else if(totalTracks instanceof Short)
                tracks = ((Short)totalTracks).shortValue();
            else
                tracks = 0;

            if (tracks < 0)
                tracks = 0;

        if (tracks != mTotalTracks)
            Log.d(TAG, "Total Tracks count has changed");
        mTotalTracks = tracks;

        } catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in updateTotalTracksFromIntent()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return;
        }
    }

    /* Function to convert incoming play states to BluetoothAVRCP equivalent state.
    This conversion is currently needed only for Amazon media player. Extend this function
    to support players that need the play states to be converted. */
    int convertToAvrcpPlayState(int index, int playstate) {
        // Currently supporting only conversion for Amazon Music app
        if(index != IDX_AMAZON_MEDIA_PLAYER) {
            Log.d(TAG, "Unsupported operation for player index :"+index);
            return PLAYSTATE_STOPPED;
        }

        switch(playstate) {
            case 1:
                return PLAYSTATE_PAUSED;
            case 2:
                return PLAYSTATE_STOPPED;
            case 3:
                return PLAYSTATE_PLAYING;
            default:
                Log.w(TAG, "Unknown playstate :"+playstate);
                return PLAYSTATE_STOPPED;
        }
    }

    /* Function to check whether the intent contains play status value.*/
    public boolean checkPlayStateInIntent(Intent intent) {
        int tmpPlayStatus = -1;
        tmpPlayStatus = getPlayStateValue(intent);
        if(tmpPlayStatus == PLAYSTATE_INVALID) {
            Log.i(TAG, "Failed to extract PLAYSTATE from intent");
            return false;
        }
        if(tmpPlayStatus == mPlayStatus) {
            Log.e(TAG, "Playstate hasnt changed yet : "+mPlayStatus);
            return false;
        }
        Log.d(TAG, "Play State changed from "+mPlayStatus+" to "+tmpPlayStatus);
        return true;
    }

    public boolean updateMetaData(Intent metadataIntent) {
        boolean rv = false;
        Log.d(TAG, "updateMetaData");

        try {
            dumpExtras(metadataIntent);
            String title = null;

            /* The attributes are filled with music player's intent-specific details
             * in fillIntentExtras(). Need not modify it here.
             */
            title = metadataIntent.getStringExtra(intentExtras.get("Title"));

            if (title == null) {
                Log.w(TAG, "updateMetaData: title is null");
                return false;
            }
            if (!title.equals (mTitle))
                rv = true;
            mTitle = title;
            if (mTitle == null)
                mTitle = DEFAULT_METADATA_STRING;

            mArtistName = metadataIntent.getStringExtra(intentExtras.get("Artist"));

            if (mArtistName == null)
                mArtistName = DEFAULT_METADATA_STRING;

            mAlbumName = metadataIntent.getStringExtra(intentExtras.get("Album"));

            if (mAlbumName == null)
                mAlbumName = DEFAULT_METADATA_STRING;

            updateCurrentTrackFromIntent(metadataIntent);
            updateTotalTracksFromIntent(metadataIntent);

            mDuration = updateDurationFromIntent(metadataIntent);
            mPosition = updatePositionFromIntent(metadataIntent);

            Log.d(TAG, "updateMetaData: Title: "+mTitle+" Artist: "+
                mArtistName+" Album: "+mAlbumName+" TrackNum "+
                String.valueOf(mTrackNum)+" Duration: "+
                String.valueOf(mDuration)+" TotalTracks: "+
                String.valueOf(mTotalTracks));
        }
        catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in updateMetaData()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return false;
        }
        return rv;
    }

    public boolean updatePlayStatus(Intent playstatusIntent) {
        if(DBG) Log.d(TAG, "updatePlayStatus() : "+playstatusIntent);

        try {
            /* The attributes are filled with music player's
             * intent-specific details in fillIntentExtras().
             *  Need not modify it here.
             */
            dumpExtras(playstatusIntent);

            mDuration = updateDurationFromIntent(playstatusIntent);
            mPosition = updatePositionFromIntent(playstatusIntent);
            updatePlayStateValue(playstatusIntent);

            Log.d(TAG, "updatePlayStatus: PlayStatus: " +mPlayStatus + " Duration: " +
                String.valueOf(mDuration) + " Position: " +String.valueOf(mPosition));
        }
        catch (ClassCastException ex) {
            Log.e(TAG, "Expected parameter in different format");
        }
        catch (BadParcelableException e) {
            Log.e(TAG, "BadParcelableException caught in updatePlayStatus()", e);
            Log.e(TAG, "Player passed serializable object in intent extras. Hence the current"+
                " version of the player not supported");
            return false;
        }
        return true;
    }

    /* Update Repeat/Shuffle status */
    public boolean updateApplicationSettings(Intent applicationsettingsIntent) {
        if(DBG) Log.d(TAG, "updateApplicationSettings() : "+applicationsettingsIntent);

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
                if(DBG) Log.d(TAG, "updateApplicationSettings() shuffle:"+shuffle);
                if (shuffle == 2)
                    shuffle = SHUFFLE_ON;
                else if(shuffle == 1)
                    shuffle = SHUFFLE_GROUP;
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
                if(DBG) Log.d(TAG, "updateApplicationSettings() repeat:"+repeat);

                if (repeat == 1)
                    repeat = REPEAT_SINGLE;
                else if(repeat == 2)
                    repeat = REPEAT_ALL;
                else
                    repeat = REPEAT_OFF;
            }
            else
                repeat = mRepeat;

            if (repeat != mRepeat) {
                mRepeat = repeat;
                if ((repeat == REPEAT_SINGLE) &&(applicationsettingsIntent.getAction()
                    .equals(AVRCP_ACTION_PLAYSTATE_CHANGED[IDX_DOUBLETWIST_MEDIA_PLAYER])))
                    mShuffle = SHUFFLE_OFF;
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

}

