/* Copyright 2013 Broadcom Corporation
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

package com.broadcom.bt.app.avrcp;

import java.nio.ByteBuffer;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothUuid;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.widget.Toast;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerEventHandler;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

public class MediaPlayerActivity extends Activity implements OnClickListener,
        OnTouchListener, OnCancelListener, OnDismissListener,
        AvrcpCommandCallback {

    ImageButton btnPlayPause, btnPause, btnStop, btnRew, btnFF, btnNext, btnPrev;
    ImageButton btnRepeat, btnShuffle, btnRefresh;

    TextView lblPlayerName, txtMediaTitle, txtMediaAlbum, txtMediaArtist, txtMediaGenre,
            txtDeviceName, txtMediaTrackInfo;
    TextView txtCurrMediaTime, txtMediaTotalDuration;
    SeekBar mSeekBar;

    BluetoothAvrcpController avrcp;
    BluetoothDevice connectedDevice;
    long avrcpState = BluetoothAvrcpController.PLAY_STATUS_STOPPED;
    private ProgressDialog mWaitingProgressDialog;
    private AlertDialog mErrorDialog;

    int mTgFeatures;
    ImageButton btnBrowse;
    ImageButton btnNowPlaying;
    Spinner spnPlayers;
    String array_mediaPlayers[];
    String array_nowPlayingItems[] = new String[0];
    static boolean activityStarted = true;

    private static final int LOAD_CONN_DEV_REQUEST = 10;
    private static final int START_AVRC_REQUEST = 100;
    private static final int STOP_AVRC_REQUEST = 101;
    private static final int LIST_APP_ATTR_REQUEST = 102;
    private static final int LIST_APP_ATTR_VALS_REQUEST = 103;
    private static final int GET_CURR_ATTR_VAL_REQUEST = 104;
    private static final int SET_CURR_ATTR_VAL_REQUEST = 105;
    private static final int GET_APP_ATTR_TEXT_REQUEST = 106;
    private static final int GET_APP_ATTR_VAL_TEXT_REQUEST = 107;
    private static final int GET_ELEMENT_ATTR_REQUEST = 108;
    private static final int GET_PLAYER_STATUS_REQUEST = 109;
    private static final int MEDIA_SEEK_REQUEST         = 110;
    /* Command Events for updating the UI based on the callbacks received for the above request
    events.*/
    private static final int CMD_ON_LOAD_APP_ATTR_FAIL  = 200;
    private static final int CMD_RELOAD_UI              = 201;
    private static final int CMD_RELOAD_METADATA        = 202;
    private static final int CMD_RELOAD_PROGRESS        = 203;
    private static final int CMD_UPDATE_PROGRESS        = 204;
    private static final int CMD_UPDATE_UI_FOR_SEEK     = 205;
    private static final int CMD_UPDATE_UI_APP_SETTINGS = 206;
    private static final int CMD_RESET_METADATA         = 207;
    private static final int CMD_ALTERNATE_UPDATE_TIMER = 208;

    //For adding menu Items dynamically
    //private static final int switch_player = 500;
    private static final int browse_button = 501;
    private static final int browse_button_old = 502;
    private static final int browse_button_dep = 503;


    private AvrcpCommandManager mCmdController;
    public static final String EXTRA_DEVICE = "connected_device";
    protected static final String TAG = "AVRCP-Player";
    private static final boolean D = true;
    private static final int GUI_UPDATE_MSG_WAITING_FOR_INIT_COMPLETE = 1000;
    private static final int GUI_UPDATE_MSG_NO_CONN_DEVICE_ERROR = 1001;

    private static final int GET_MEDIA_PLAYER_LIST = 5001;
//    private static final int SET_BROWSED_PLAYER = 5002;
//    private static final int GET_FOLDER_ITEMS = 5003;
    private static final int GET_NOW_PLAYING = 5004;

    private static final int GOT_MEDIA_PLAYER_LIST = 5101;
//    private static final int BROWSED_PLAYER_SET = 5102;
//    private static final int GOT_FOLDER_ITEMS = 5103;
    private static final int GOT_NOW_PLAYING = 5106;
    private static final int PLAY_NOW_ITEM = 5107;
    private long mDuration, mPosition;

    private int[] supportElementAttr = {
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_TITLE,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_ARTIST,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_ALBUM,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_TRACK_NUM,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_NUM_TRACKS,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_GENRE,
            (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_PLAYING_TIME };

    BluetoothAvrcpBrowseItem[] mMediaPlayers;
    BluetoothAvrcpBrowseItem mBrowsedPlayer;
    BluetoothAvrcpBrowseItem[] mFolderItems;
    BluetoothAvrcpBrowseItem mSelectedFolderItem;
    BluetoothAvrcpBrowseItem[] mNowPlayingItems;
    BluetoothAvrcpBrowseItem mSelectedNowItem;
    private List<String> mFolderPath = new ArrayList<String>(0);

    ServiceListener listener = new ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            // TODO Auto-generated method stub
            Log.d(TAG, "onServiceDisconnected()");
            displayMsg("Service Disconnected");
            finish();
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            // TODO Auto-generated method stub
            avrcp = (BluetoothAvrcpController) proxy;
            avrcp.registerEventHandler(eventHandler);
            Log.d(TAG, "onServiceConnected() eventHandler:" + eventHandler);
            if(connectedDevice == null)
                mHandler.sendEmptyMessage(LOAD_CONN_DEV_REQUEST);
            else{
                mHandler.sendEmptyMessage(START_AVRC_REQUEST);
                if((avrcp.getTargetFeatures(connectedDevice) & avrcp.FEATURE_BROWSE) > 0){
                    mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
                }
            }
        }
    };

    IBluetoothAvrcpControllerEventHandler eventHandler =
        new IBluetoothAvrcpControllerEventHandler() {

        @Override
        public void onConnectionStateChange(BluetoothDevice target,
                int newState, boolean success) {
            if (mCmdController != null)
                mCmdController.onConnectionStateChange(target, newState,
                        success);
        }

        @Override
        public void onListPlayerApplicationSettingAttributesRsp(
                BluetoothDevice target, byte[] attributeId, int status) {
            if (mCmdController != null)
                mCmdController.onListPlayerApplicationSettingAttributesRsp(
                        target, attributeId, status);

        }

        @Override
        public void onListPlayerApplicationSettingValuesRsp(
                BluetoothDevice target, byte attrId, byte[] valueId,
                int status) {
            Log.d(TAG, "onListPlayerApplicationSettingValuesRsp() " + attrId);
            if (mCmdController != null)
                mCmdController.onListPlayerApplicationSettingValuesRsp(target,
                        attrId, valueId, status);

        }

        @Override
        public void onGetCurrentPlayerApplicationSettingValueRsp(
                BluetoothDevice target, byte[] attributeId, byte[] valueId,
                int status) {
            Log.d(TAG, "onGetCurrentPlayerApplicationSettingValueRsp() ");
            if (mCmdController != null)
                mCmdController.onGetCurrentPlayerApplicationSettingValueRsp(
                        target, attributeId, valueId, status);

        }

        @Override
        public void onGetPlayerApplicationSettingAttributeTextRsp(
                BluetoothDevice target, byte[] attributeId,
                String[] attributeText, int status) {
            Log.d(TAG, "onGetPlayerApplicationSettingAttributeTextRsp() ");
            if (mCmdController != null)
                mCmdController.onGetPlayerApplicationSettingAttributeTextRsp(
                        target, attributeId, attributeText,
                        status);

        }

        @Override
        public void onGetPlayerApplicationSettingValueTextRsp(
                BluetoothDevice target, byte attributeId, byte[] valueId,
                String[] valueText, int status) {
            Log.d(TAG, "onGetPlayerApplicationSettingValueTextRsp() "+ attributeId);
            if (mCmdController != null)
                mCmdController.onGetPlayerApplicationSettingValueTextRsp(
                        target, attributeId, valueId, valueText,
                        status);

        }

        @Override
        public void onGetElementAttributesRsp(BluetoothDevice target,
                int[] attributeId, String[] valueText,
                int status) {
            if (mCmdController != null)
                mCmdController.onGetElementAttributesRsp(target, attributeId,
                        valueText, status);
        }

        @Override
        public void onGetPlayStatusRsp(BluetoothDevice target, int songLength,
                int songPosition, byte playStatus, int status) {
            Log.d(TAG, "onGetPlayStatusRsp() songLength:"+songLength+
                ", songPosition:"+songPosition+", status:"+status);
            if(!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "Failed to get Play status. Do not update UI");
                return;
            }
            if (mCmdController != null)
                mCmdController.onGetPlayStatusRsp(target, songLength,
                        songPosition, playStatus, status);
            Log.d(TAG, "onGetPlayStatusRsp() : "+avrcpState+" >>> "+ (long) playStatus);
            mPosition = songPosition;
            Message msg = mHandler.obtainMessage(CMD_UPDATE_PROGRESS);
            msg.arg1 = songPosition;
            msg.arg2 = songLength;
            mHandler.sendMessage(msg);
            if(avrcpState != (long) playStatus) {
                avrcpState = (long) playStatus;
                mHandler.sendEmptyMessage(CMD_RELOAD_UI);
                // If play state is playing, get the play state value.
                mHandler.removeMessages(GET_ELEMENT_ATTR_REQUEST);
                mHandler.sendEmptyMessage(GET_ELEMENT_ATTR_REQUEST);
                return;
            }
            mHandler.sendEmptyMessage(CMD_ALTERNATE_UPDATE_TIMER);
            mHandler.sendEmptyMessage(CMD_RELOAD_METADATA);
            mHandler.sendEmptyMessage(CMD_RELOAD_UI);

        }

        @Override
        public void onPlaybackStatusChanged(BluetoothDevice target,
                byte playStatus) {
            Log.d(TAG, "onPlaybackStatusChanged() "+avrcpState+" >>> "+ (long) playStatus);
            boolean hasPlayStatusChanged = (avrcpState == (long) playStatus)?true:false;
            boolean hasPlayStatusRestarted =
                (avrcpState == BluetoothAvrcpController.PLAY_STATUS_STOPPED &&
                playStatus == BluetoothAvrcpController.PLAY_STATUS_PLAYING)? true:false;
            boolean hasPlayStatusContinued =
                (avrcpState == BluetoothAvrcpController.PLAY_STATUS_PAUSED &&
                playStatus == BluetoothAvrcpController.PLAY_STATUS_PLAYING)? true:false;
            avrcpState = (long) playStatus;
            mHandler.sendEmptyMessage(CMD_RELOAD_UI);
            // If play state is playing, get the play state value.
            if(avrcpState == BluetoothAvrcpController.PLAY_STATUS_PLAYING) {
                mHandler.sendEmptyMessage(GET_PLAYER_STATUS_REQUEST);
                mHandler.sendEmptyMessageDelayed(GET_ELEMENT_ATTR_REQUEST, 100);
                if(hasPlayStatusRestarted && !hasPlayStatusContinued)
                    mSeekBar.setProgress(0);
                mHandler.sendEmptyMessageDelayed(CMD_RELOAD_PROGRESS, 1000);
                mHandler.sendEmptyMessageDelayed(CMD_ALTERNATE_UPDATE_TIMER, 1000);
            } else {
                if(avrcpState == BluetoothAvrcpController.PLAY_STATUS_STOPPED)
                    mSeekBar.setProgress(0);
                mHandler.removeMessages(CMD_RELOAD_PROGRESS);
                mHandler.removeMessages(CMD_UPDATE_PROGRESS);
                mHandler.removeMessages(CMD_ALTERNATE_UPDATE_TIMER);
            }
        }

        @Override
        public void onTrackChanged(BluetoothDevice target, long trackId) {
            // TODO Auto-generated method stub
            mHandler.sendEmptyMessage(GET_ELEMENT_ATTR_REQUEST);
            mSeekBar.setProgress(0);
            mHandler.sendEmptyMessageDelayed(CMD_RELOAD_PROGRESS, 1000);
        }

        @Override
        public void onTrackReachedEnd(BluetoothDevice target) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onTrackReachedStart(BluetoothDevice target) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onPlaybackPositionChanged(BluetoothDevice target,
                int playbackPosition) {
            // TODO Auto-generated method stub
            // Since this callback is successful, remove alternate timer for
            // getPlayStatus call
            mHandler.removeMessages(CMD_ALTERNATE_UPDATE_TIMER);
            Log.d(TAG, "onPlaybackPositionChanged()  playbackPosition: " + playbackPosition);
            mPosition = playbackPosition;
            Message msg = mHandler.obtainMessage(CMD_UPDATE_PROGRESS);
            msg.arg1 = playbackPosition;
            msg.arg2 = -1;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onPlayerAppSettingChanged(BluetoothDevice target,
                byte[] attribute, byte[] value) {
            if (mCmdController != null)
                mCmdController.onPlayerAppSettingChanged(target, attribute,
                        value);
            mHandler.sendEmptyMessage(CMD_RELOAD_UI);
        }

/*        @Override
        public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                String[] folderPath, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "SetBrowsedPlayer response failed");
                return;
            }
            if(D) Log.d(TAG, "onSetBrowsedPlayerRsp");
            mFolderPath.clear();
            for (int i = 0; i < folderPath.length; i++)
                mFolderPath.add(folderPath[i]);
            Message m = mHandler.obtainMessage(BROWSED_PLAYER_SET, numberOfItems, 0, null);
            mHandler.sendMessage(m);
        }*/

        @Override
        public void onChangePathRsp(BluetoothDevice target, byte direction, int numberOfItems,
                        int status){}

        @Override
        public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                        BluetoothAvrcpBrowseItem[] items, int status){
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "GetFolderItems response failed");
                return;
            }

            if(D) Log.d(TAG, "GetFolderItems response succeed");

        switch (scope) {
            case BluetoothAvrcpController.SCOPE_MEDIA_PLAYER_LIST:
                Log.d(TAG, "case:setting Media player list");
                mMediaPlayers = items;
                mHandler.sendEmptyMessage(GOT_MEDIA_PLAYER_LIST);
                break;
            case BluetoothAvrcpController.SCOPE_NOW_PLAYING:
                mNowPlayingItems = items;
                mHandler.sendEmptyMessage(GOT_NOW_PLAYING);
                break;
            }
        }

        @Override
        public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                        String[] valueTexts, int status){}

        @Override
        public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status){}

        @Override
        public void onPlayItemRsp(BluetoothDevice target, int status){
            if(D) Log.d(TAG, "onPlayItemRsp");
        }

        @Override
        public void onAddToNowPlayingRsp(BluetoothDevice target, int status){}

        @Override
        public void onAddressedPlayerChanged(BluetoothDevice target, int playerId){}

        @Override
        public void onAvailablePlayersChanged(BluetoothDevice target){}

        @Override
        public void onNowPlayingContentChanged(BluetoothDevice target){}

        @Override
        public void onUIDsChanged(BluetoothDevice target){}

        @Override
        public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                String[] folderPath, int status) {}

    };

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage : " + msg);
            switch (msg.what) {
            case LOAD_CONN_DEV_REQUEST:
                loadConnectedDevice();
                break;
            case START_AVRC_REQUEST:
                mTgFeatures = avrcp.getTargetFeatures(connectedDevice);
                Log.d(TAG, "Target features: " + Integer.toBinaryString(mTgFeatures));

                mCmdController = new AvrcpCommandManager(avrcp, connectedDevice);
                mCmdController
                        .processCmds(AvrcpCommandManager.CMD_LOAD_APP_SETTING_ATTR);
                waitForAvrcpInit();
                break;
            case LIST_APP_ATTR_REQUEST: {
                Bundle data = msg.getData();
                avrcp.listPlayerApplicationSettingAttributes(connectedDevice);
            }
            case LIST_APP_ATTR_VALS_REQUEST: {
                Bundle data = msg.getData();
                byte attributeId = data.getByte("attrID");
                avrcp.listPlayerApplicationSettingValues(connectedDevice,
                        attributeId);
            }
                break;
            case GET_CURR_ATTR_VAL_REQUEST: {
                Bundle data = msg.getData();
                byte[] attributeId = data.getByteArray("attrs");
                avrcp.getCurrentPlayerApplicationSettingValue(connectedDevice,
                        attributeId);
            }
                break;
            case SET_CURR_ATTR_VAL_REQUEST: {
                Bundle data = msg.getData();
                byte[] attrID = data.getByteArray("attrs");
                byte[] valueID = data.getByteArray("vals");
                avrcp.setPlayerApplicationSettingValue(connectedDevice, attrID,
                        valueID);
            }
                break;
            case GET_APP_ATTR_TEXT_REQUEST: {
                Bundle data = msg.getData();
                byte[] attrID = data.getByteArray("attrID");
                avrcp.getPlayerApplicationSettingAttributeText(connectedDevice,
                        attrID);
            }
                break;
            case GET_APP_ATTR_VAL_TEXT_REQUEST: {
                Bundle data = msg.getData();
                byte attrID = data.getByte("attrID");
                byte[] valueID = data.getByteArray("valueID");
                avrcp.getPlayerApplicationSettingValueText(connectedDevice,
                        attrID, valueID);
            }
                break;
            case GET_ELEMENT_ATTR_REQUEST:
                avrcp.getElementAttributes(connectedDevice, supportElementAttr);
                break;
            case GET_PLAYER_STATUS_REQUEST:
                btnPlayPause.setEnabled(true);
                btnPause.setEnabled(true);
                btnStop.setEnabled(true);
                btnRew.setEnabled(true);
                btnFF.setEnabled(true);
                btnNext.setEnabled(true);
                btnPrev.setEnabled(true);
                avrcp.getPlayStatus(connectedDevice);
                break;
            case MEDIA_SEEK_REQUEST:
                // TODO : Add new API for seek operation
                break;
            case CMD_ON_LOAD_APP_ATTR_FAIL:
                btnRepeat.setEnabled(false);
                btnShuffle.setEnabled(false);
                dismissDialog(GUI_UPDATE_MSG_WAITING_FOR_INIT_COMPLETE);
                break;
            case CMD_RELOAD_UI:
                updateUI();
                break;
            case CMD_RELOAD_METADATA:
            {
                String txt;
                //TODO: Need to update txtPlayername here.
                //txtPlayer.setText("Current Player Name");
                txtMediaTitle.setText(mCmdController.getMediaTitle());
                txtMediaArtist.setText(mCmdController.getMediaArtist());
                txtMediaAlbum.setText(mCmdController.getMediaAlbum());
                txtMediaGenre.setText(mCmdController.getMediaGenre());
                if(mCmdController.getMediaTrackNumber() != -1)
                    txt = mCmdController.getMediaTrackNumber()+" / ";
                else
                    txt = "-- / ";
                if (mCmdController.getMediaTotalTracks() != -1)
                    txt += mCmdController.getMediaTotalTracks();
                else
                    txt += "--";
                txtMediaTrackInfo.setText(txt);
                mDuration = mCmdController.getMediaDuration();
                if (mDuration > 0) {
                    txt = formatPlayTime(mDuration/1000);
                    txtMediaTotalDuration.setText(txt);
                    mSeekBar.setMax(1000);
                    if(mPosition > 0) {
                        txt = formatPlayTime((int)mPosition/1000);
                        txtCurrMediaTime.setText(txt);
                        float tmpVal = ((float)(mPosition * 1000.00)/mDuration);
                        int currPos =Math.round(tmpVal);
                        Log.d(TAG, "CMD_RELOAD_METADATA  currPos: " + currPos+
                            " mPosition:"+msg.arg1+", duration:"+mDuration);
                        mSeekBar.setProgress(currPos);
                    }
                }
                // Enable Seekbar modification only after supporting seek functionality
                //mSeekBar.setOnSeekBarChangeListener(mSeekListener);
            }
            break;
            case CMD_RESET_METADATA:
            {
                btnRepeat.setImageResource(R.drawable.media_repeat_off);
                btnShuffle.setImageResource(R.drawable.media_shuffle_off);
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                btnPlayPause.setEnabled(true);
                btnPause.setImageResource(android.R.drawable.ic_media_pause);
                btnPause.setEnabled(true);
                btnStop.setEnabled(true);
                btnRew.setEnabled(true);
                btnFF.setEnabled(true);
                btnNext.setEnabled(true);
                btnPrev.setEnabled(true);
                //TODO: Need to update txtPlayername here.
                //txtPlayer.setText("");
                txtMediaTitle.setText("");
                txtMediaArtist.setText("");
                txtMediaAlbum.setText("");
                txtMediaGenre.setText("");
                txtMediaTrackInfo.setText(getString(R.string.def_track));
                txtCurrMediaTime.setText(getString(R.string.def_time));
                txtMediaTotalDuration.setText(getString(R.string.def_time));
                mSeekBar.setProgress(0);
            }
            break;
            case CMD_RELOAD_PROGRESS:
            {
                // This event takes care of updating the current time and progress seekbar
                // every 1 second.
                if(avrcpState != BluetoothAvrcpController.PLAY_STATUS_PLAYING
                    || mCmdController == null)
                    return;
                float tmpVal = (float)mSeekBar.getProgress() +
                    (float)(mCmdController.getMediaDuration()/1000.00/1000.00);
                int currPos = Math.round(tmpVal);
                Log.d(TAG, "CMD_RELOAD_PROGRESS  currPos: " + currPos);
                mSeekBar.setProgress(currPos);
                sendEmptyMessageDelayed(CMD_RELOAD_PROGRESS, 1000);
            }
            break;
            case CMD_UPDATE_PROGRESS:
            {
                float duration;
                // This event will update the progress seekbar with modified play position from
                // onPlaybackPositionChanged() callback. This event suppliments CMD_RELOAD_PROGRESS
                // event as it gives accurate playtime of the remote device.
                if(mCmdController == null) {
                    Log.w(TAG, "mCmdController - Not yet initialized.");
                    return;
                }
                removeMessages(CMD_RELOAD_PROGRESS);
                removeMessages(CMD_ALTERNATE_UPDATE_TIMER);
                if(msg.arg2 != -1) {
                    duration = (float)msg.arg2;
                } else {
                    // When msg.arg2 is -1, the song duration is obtained from mCmdController
                    duration = (float)mCmdController.getMediaDuration();
                }
                float tmpVal = ((float)(msg.arg1 * 1000.00)/duration);
                int currPos =Math.round(tmpVal);
                Log.d(TAG, "CMD_UPDATE_PROGRESS  currPos: " + currPos+" playpos:"+msg.arg1+
                    ", duration:"+duration);
                mSeekBar.setProgress(currPos);
                sendEmptyMessageDelayed(CMD_RELOAD_PROGRESS, 1000);
                sendEmptyMessageDelayed(CMD_ALTERNATE_UPDATE_TIMER, 1000);
                String txt = formatPlayTime((int)msg.arg1/1000);
                txtCurrMediaTime.setText(txt);
            }
            break;
            case CMD_ALTERNATE_UPDATE_TIMER:
            {
                if(avrcpState == BluetoothAvrcpController.PLAY_STATUS_PLAYING) {
                    avrcp.getPlayStatus(connectedDevice);
                    sendEmptyMessageDelayed(CMD_ALTERNATE_UPDATE_TIMER, 5000);
                }
                else {
                    removeMessages(CMD_ALTERNATE_UPDATE_TIMER);
                }
            }
            break;
            case CMD_UPDATE_UI_FOR_SEEK:
            {
                // This event disable/enable the UI controls when seek operation is in progress.
                if(msg.arg1 == 0) {
                    btnPlayPause.setEnabled(false);
                    btnStop.setEnabled(true);
                    btnRew.setEnabled(false);
                    btnFF.setEnabled(false);
                    btnNext.setEnabled(false);
                    btnPrev.setEnabled(false);
                } else {
                    updateUI();
                }
            }
            break;
            case CMD_UPDATE_UI_APP_SETTINGS:
            {
                if (mCmdController != null) {
                    switch (mCmdController.getRepeatState()) {
                    case BluetoothAvrcpController.ATTR_VALUE_REPEAT_OFF:
                        btnRepeat.setImageResource(R.drawable.media_repeat_off);
                        break;

                    case BluetoothAvrcpController.ATTR_VALUE_REPEAT_SINGLE_TRACK:
                        btnRepeat.setImageResource(R.drawable.media_repeat_once);
                        break;

                    case BluetoothAvrcpController.ATTR_VALUE_REPEAT_GROUP:
                    case BluetoothAvrcpController.ATTR_VALUE_REPEAT_ALL_TRACK:
                        btnRepeat.setImageResource(R.drawable.media_repeat_all);
                        break;

                    default:
                        btnRepeat.setImageResource(R.drawable.media_repeat_off);
                    }

                    switch (mCmdController.getShuffleState()) {
                    case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_OFF:
                        btnShuffle.setImageResource(R.drawable.media_shuffle_off);
                        break;

                    case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_GROUP:
                        btnShuffle.setImageResource(R.drawable.media_partyshuffle_on);
                        break;

                    case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_ALL_TRACK:
                        btnShuffle.setImageResource(R.drawable.media_shuffle_on);
                        break;

                    default:
                        btnShuffle.setImageResource(R.drawable.media_shuffle_off);
                    }
                }
            }
            break;
            case GET_MEDIA_PLAYER_LIST:
                if(D) Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                avrcp.getFolderItems(connectedDevice, avrcp.SCOPE_MEDIA_PLAYER_LIST, 0, 99, null);
                break;
            case GOT_MEDIA_PLAYER_LIST:
                if(D) Log.d(TAG, "handleMessage: GOT_MEDIA_PLAYER_LIST");
                updateMediaPlayers();
                break;
            case GET_NOW_PLAYING:
                if(D) Log.d(TAG, "handleMessage: GET_NOW_PLAYING");
                avrcp.getFolderItems(connectedDevice, avrcp.SCOPE_NOW_PLAYING, 0, 99, null);
                break;
            case GOT_NOW_PLAYING:
                if(D) Log.d(TAG, "handleMessage: GOT_NOW_PLAYING");
                updateNowPlaying();
                break;
/*            case SET_BROWSED_PLAYER:
                if(D) Log.d(TAG, "handleMessage: SET_BROWSED_PLAYER");
                avrcp.setBrowsedPlayer(connectedDevice, mBrowsedPlayer.mPlayerId);
                break;*/
            case PLAY_NOW_ITEM:
                if(D) Log.d(TAG, "handleMessage: PLAY_NOW_ITEM");
                playItem(avrcp.SCOPE_NOW_PLAYING, mSelectedNowItem);
                break;
            default:
                Log.e(TAG, "Unhandled msg: " + msg.what);
            }
        }
    };

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            Log.d(TAG, "onStartTrackingTouch()");
            Message msg = mHandler.obtainMessage(CMD_UPDATE_UI_FOR_SEEK);
            mHandler.removeMessages(CMD_RELOAD_PROGRESS);
            msg.arg1 = 0;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            Log.d(TAG, "onProgressChanged() progress:"+progress+", fromuser:"+fromuser);
            if (!fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }
            Message msg = mHandler.obtainMessage(MEDIA_SEEK_REQUEST);
            msg.arg1 = progress;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            Message msg = mHandler.obtainMessage(CMD_UPDATE_UI_FOR_SEEK);
            msg.arg1 = 1;
            mHandler.sendMessage(msg);
            mHandler.sendEmptyMessage(CMD_RELOAD_PROGRESS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_media_player);

        connectedDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);

        if(connectedDevice == null) {
            Log.d(TAG, "connectedDevice is null. Query for connected device "
                +"once BluetoothAvrcpController is up");
        }
    }

//AVRCP_1.5 IMPLEMENTATION ++
    /** Called when the user clicks the NowPlaying button */
    public void openNowPlayingList(View view) {
        // Do something in response to NowPlayingList button
//        Toast.makeText(getApplicationContext(), "Opens now Playing List", Toast.LENGTH_SHORT)
//                .show();
        /*mHandler.sendEmptyMessage(GET_NOW_PLAYING);*/
        if (array_nowPlayingItems.length != 0){
            displayMsg("Opening Now Playing List");
            try {
                Intent openNowPlayingListIntent = new Intent(this,
                        NowPlayingActivity.class);
                openNowPlayingListIntent.putExtra(
                        MediaPlayerActivity.EXTRA_DEVICE, connectedDevice);
                openNowPlayingListIntent.putExtra("array", array_nowPlayingItems);
                //here in putextra we'll send now_playing_list_items
                openNowPlayingListIntent.putExtra(NowPlayingActivity.CURRENT_SONG,
                        mSelectedNowItem.mDisplayableName);
                startActivityForResult(openNowPlayingListIntent, 2222);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            displayMsg("Now Playing List Empty");
            Log.d(TAG, "Now Playing List Empty");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 2222:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "RESULT_OK");

                    /*// Retrieve the Info
                    Bundle extras = data.getExtras();

                    if (extras != null) {
                        Log.i(TAG, "Bundle ok");
                        String playSong = extras
                                .getString(NowPlayingListActivity.EXTRA_USER_SELECTION);
                        int which = extras
                                .getInt(NowPlayingListActivity.EXTRA_SELECTION_POSITION);
                        displayMsg(which + ") Selected song to play:\n" + playSong);
                        mSelectedNowItem = mNowPlayingItems[which];
                        if(D) Log.d(TAG, "onPlayNowItem");
                        mHandler.sendEmptyMessage(PLAY_NOW_ITEM);
                    }*/
                } else {
                    //It is okay if user only wants to see now playing list and come back
                    Log.i(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
//                    Toast.makeText(this, "Failed(" + resultCode + ")", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                // TODO: remove after testing
                displayMsg("Activity Result not handled");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //Adding Menu Items dynamically if features supported
          if(avrcp != null) {
              if(D) Log.d(TAG,"features: "+avrcp.getTargetFeatures(connectedDevice) +
                        " & with Browse:" + avrcp.FEATURE_BROWSE);
          }
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if(avrcp != null){
            if((avrcp.getTargetFeatures(connectedDevice) & avrcp.FEATURE_BROWSE) > 0){
                menu.clear();
                //adding MenuItem Browse
                MenuItem browse = menu.add(Menu.NONE, browse_button , 200, "Browse");
                browse.setIcon(R.drawable.ic_menu_browse);
                browse.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                        MenuItem.SHOW_AS_ACTION_WITH_TEXT);

              //adding MenuItem Browse
                MenuItem browse2 = menu.add(Menu.NONE, browse_button_old , 300, "Browse_old");
                browse.setIcon(R.drawable.ic_menu_browse);
                browse.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                        MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }
        } else {
            if(D) Log.d(TAG, "avrcp null, invalidate once received avrcp");
        }
         return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            switch (item.getItemId()) {
                /*case switch_player:
                     mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
                     break;*/
                case browse_button:
                    try{
                        Intent browseMusicIntent = new Intent(this, BrowseNewActivity.class);
                        browseMusicIntent.putExtra(EXTRA_DEVICE, connectedDevice);
                        startActivity(browseMusicIntent);
                    }
                    catch(ActivityNotFoundException e){
                        e.printStackTrace();
                    }
                    break;
                case browse_button_old:
                    try{
                        Intent browseIntent = new Intent(this, BrowseActivity.class);
                        browseIntent.putExtra(EXTRA_DEVICE, connectedDevice);
                        startActivity(browseIntent);
                    }
                    catch(ActivityNotFoundException e){
                        e.printStackTrace();
                    }
                    break;
/*                case browse_button_dep:
                    try{
                        Intent browseIntent = new Intent(this, BrowseMusicActivity.class);
                        browseIntent.putExtra(EXTRA_DEVICE, connectedDevice);
                        startActivity(browseIntent);
                    }
                    catch(ActivityNotFoundException e){
                        e.printStackTrace();
                    }
                    break;*/
/*                case R.id.refresh_button:
                    //TODO: remove after testing
                    displayMsg("refresh");
                     break;*/
                case R.id.action_settings:
                    //TODO: remove after testing
                    displayMsg("action settings");
                     break;
                    default:
                        //TODO: remove after testing
                        displayMsg("case not handled");
                        return false;


            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return super.onOptionsItemSelected(item);
    }

     @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

             //Check which feature of Avrcp supports switch player and if it's available
             //adding menuItem SwitchPlayer
/*             MenuItem SwitchPlayer = menu.add(Menu.NONE, switch_player , 100, "SwitchPlayer");
             SwitchPlayer.setIcon(R.drawable.switch_user_button);
             SwitchPlayer.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                     MenuItem.SHOW_AS_ACTION_WITH_TEXT);*/
            return true;
        }

/*    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case PLAYER_PICKER_REQ_CODE:
                if(resultCode == RESULT_OK) {
                    Log.i(TAG, "RESULT_OK");

                    // Retrieve the Info
                    Bundle extras = data.getExtras();

                    if(extras != null) {
                        Log.i(TAG, "Bundle ok");
                        int playerSelected = extras
                                .getInt(AvailablePlayersPickerActivity.PLAYER_SELECTED);
                        String currentPlayerName = extras
                                .getString(AvailablePlayersPickerActivity.PLAYER_NAME);
                        displayMsg("Selected player: " + playerSelected +
                                "\nplayer Name: " + currentPlayerName);
                        //txtPlayer.setText(currentPlayerName);
                    }
                }
                else {
                    Log.e(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
                    Toast.makeText(this, "Failed(" + resultCode +")", Toast.LENGTH_SHORT).show();
                }
                break;
            case NOW_PLAYING_LIST_REQ_CODE:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "RESULT_OK");

                    // Retrieve the Info
                    Bundle extras = data.getExtras();

                    if (extras != null) {
                        Log.i(TAG, "Bundle ok");
                        String playSong = extras
                                .getString(NowPlayingListActivity.EXTRA_USER_SELECTION);
                        Toast.makeText(this, "Selected song to play:\n" + playSong,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    //It is okay if user only wants to see now playing list and come back
                    Log.i(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
//                    Toast.makeText(this, "Failed(" + resultCode + ")", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                //TODO: remove after testing
                displayMsg("Activity Result not handled");
        }
    }*/
//AVRCP_1.5 IMPLEMENTATION --

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if(avrcp != null) avrcp.closeProxy();
        avrcpState = BluetoothAvrcpController.PLAY_STATUS_STOPPED;
        connectedDevice = null;
        if(mCmdController != null) {
            mCmdController.unregisterCallback();
            mCmdController.clear();
            mCmdController = null;
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        avrcpState = BluetoothAvrcpController.PLAY_STATUS_STOPPED;
        loadUIComponents();
        BluetoothAvrcpController.getProxy(getApplicationContext(), listener);
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mCmdController = null;
        avrcp = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "OnConfigurationChanged: Orientation:" + newConfig.orientation);
        setContentView(R.layout.activity_media_player);
        loadUIComponents();
        if(avrcp != null && mCmdController != null) {
            mHandler.sendEmptyMessage(GET_PLAYER_STATUS_REQUEST);
        }
        super.onConfigurationChanged(newConfig);
    }

    // Based on Play state, invoking this method updates the UI buttons.
    protected void updateUI() {
        // TODO Auto-generated method stub
        Log.d(TAG, "updateUI() :" + avrcpState);
        if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_PLAYING) {
            btnPlayPause.setEnabled(true);
            btnPause.setEnabled(true);
            btnStop.setEnabled(true);
            btnRew.setEnabled(true);
            btnFF.setEnabled(true);
            btnNext.setEnabled(true);
            btnPrev.setEnabled(true);
        } else if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_PAUSED) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            btnPlayPause.setEnabled(true);
            btnStop.setEnabled(true);
            btnRew.setEnabled(true);
            btnFF.setEnabled(true);
            btnNext.setEnabled(true);
            btnPrev.setEnabled(true);
        }/* else if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_FWD_SEEK) {
            btnPlayPause.setEnabled(false);
            btnStop.setEnabled(true);
            btnRew.setEnabled(false);
            btnFF.setEnabled(true);
            btnNext.setEnabled(false);
            btnPrev.setEnabled(false);
        } else if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_REV_SEEK) {
            btnPlayPause.setEnabled(false);
            btnStop.setEnabled(true);
            btnRew.setEnabled(true);
            btnFF.setEnabled(false);
            btnNext.setEnabled(false);
            btnPrev.setEnabled(false);
        } */ else if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_STOPPED) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            btnPlayPause.setEnabled(true);
            btnStop.setEnabled(true);
            btnRew.setEnabled(true);
            btnFF.setEnabled(true);
            btnNext.setEnabled(true);
            btnPrev.setEnabled(true);
            //TODO: txtPlayer.setText("");
            txtMediaTitle.setText("");
            txtMediaArtist.setText("");
            txtMediaAlbum.setText("");
            txtMediaGenre.setText("");
            txtMediaTrackInfo.setText(getString(R.string.def_track));
            txtCurrMediaTime.setText(getString(R.string.def_time));
            txtMediaTotalDuration.setText(getString(R.string.def_time));
        }

        // Update Shuffle/Repeat Status
        if (mCmdController != null) {
            switch (mCmdController.getRepeatState()) {
            case BluetoothAvrcpController.ATTR_VALUE_REPEAT_OFF:
                btnRepeat.setImageResource(R.drawable.media_repeat_off);
                break;

            case BluetoothAvrcpController.ATTR_VALUE_REPEAT_SINGLE_TRACK:
                btnRepeat.setImageResource(R.drawable.media_repeat_once);
                break;

            case BluetoothAvrcpController.ATTR_VALUE_REPEAT_GROUP:
            case BluetoothAvrcpController.ATTR_VALUE_REPEAT_ALL_TRACK:
                btnRepeat.setImageResource(R.drawable.media_repeat_all);
                break;

            default:
                btnRepeat.setImageResource(R.drawable.media_repeat_off);
            }

            switch (mCmdController.getShuffleState()) {
            case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_OFF:
                btnShuffle.setImageResource(R.drawable.media_shuffle_off);
                break;

            case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_GROUP:
                btnShuffle.setImageResource(R.drawable.media_partyshuffle_on);
                break;

            case BluetoothAvrcpController.ATTR_VALUE_SHUFFLE_ALL_TRACK:
                btnShuffle.setImageResource(R.drawable.media_shuffle_on);
                break;

            default:
                btnShuffle.setImageResource(R.drawable.media_shuffle_off);
            }
        }

    //btnBrowse.setEnabled((mTgFeatures & avrcp.FEATURE_BROWSE) > 0 ? true : false);

    }

    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs = new Object[5];

    public String formatPlayTime(long secs) {
        String durationformat = getString(secs < 3600 ? R.string.durationformatshort :
            R.string.durationformatlong);
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }

    private void loadUIComponents() {
        btnPlayPause = (ImageButton) findViewById(R.id.btn_play_pause);
        btnPlayPause.setOnClickListener(this);
        btnPause = (ImageButton) findViewById(R.id.btn_pause);
        btnPause.setOnClickListener(this);
        btnStop = (ImageButton) findViewById(R.id.btn_stop);
        btnStop.setEnabled(false);
        btnStop.setOnClickListener(this);
        btnRew = (ImageButton) findViewById(R.id.btn_rew);
        btnRew.setEnabled(false);
        btnRew.setOnTouchListener(this);
        btnFF = (ImageButton) findViewById(R.id.btn_ff);
        btnFF.setEnabled(false);
        btnFF.setOnTouchListener(this);
        btnNext = (ImageButton) findViewById(R.id.btn_next);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(this);
        btnPrev = (ImageButton) findViewById(R.id.btn_prev);
        btnPrev.setEnabled(false);
        btnPrev.setOnClickListener(this);
        btnRepeat = (ImageButton) findViewById(R.id.btn_repeat);
        btnRepeat.setOnClickListener(this);
        btnShuffle = (ImageButton) findViewById(R.id.btn_shuffle);
        btnShuffle.setOnClickListener(this);
        btnRefresh = (ImageButton) findViewById(R.id.btn_refresh);
        btnRefresh.setOnClickListener(this);
        btnBrowse = (ImageButton) findViewById(R.id.btn_browse);
        btnBrowse.setOnClickListener(this);

        txtDeviceName = (TextView) findViewById(R.id.txt_deviceName);
        txtMediaTitle = (TextView) findViewById(R.id.txt_mediaTitle);
        txtMediaArtist = (TextView) findViewById(R.id.txt_mediaArtist);
        txtMediaAlbum = (TextView) findViewById(R.id.txt_mediaAlbum);
        txtMediaGenre = (TextView) findViewById(R.id.txt_mediaGenre);
        txtMediaTrackInfo = (TextView) findViewById(R.id.txt_mediaTrack);
        txtMediaTotalDuration = (TextView) findViewById(R.id.totalTime);
        txtCurrMediaTime = (TextView) findViewById(R.id.currTime);

        mSeekBar = (SeekBar) findViewById(R.id.seekBar1);
        mSeekBar.setVisibility(View.VISIBLE);
        mSeekBar.setEnabled(false);
        lblPlayerName = (TextView) findViewById(R.id.lbl_playerName);
        spnPlayers = (Spinner)findViewById(R.id.main_spn_players);
        spnPlayers.setOnItemSelectedListener(mPlayerSelectedListener);
        btnNowPlaying = (ImageButton)findViewById(R.id.btn_now_playing);
        /* Reset all data here */
        mHandler.sendEmptyMessage(CMD_RESET_METADATA);
    }
    private OnItemSelectedListener mPlayerSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if(D) Log.d(TAG, "mPlayerSelectedListener:selected:"+pos);
            if(D) Log.d(TAG, "mPlayerSelectedListener");
            mBrowsedPlayer = mMediaPlayers[pos];
            if (mBrowsedPlayer.isBrowsingSupported()) {
                /*mHandler.sendEmptyMessage(SET_BROWSED_PLAYER);*/
                mHandler.sendEmptyMessage(GET_NOW_PLAYING);
            }
         }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            if(D) Log.d(TAG, "mPlayerSelectedListener: onNothingSelected");
        }
    };


    private void loadConnectedDevice() {
        List<BluetoothDevice> connDevices = avrcp.getConnectedDevices();
        if(connDevices == null || connDevices.size() == 0) {
            Log.d(TAG, "No connected device available.");
            showDialog(GUI_UPDATE_MSG_NO_CONN_DEVICE_ERROR);
            return;
        }
        // Retrieve the last connected (and valid) device
        connectedDevice = connDevices.get(0);
        Log.d(TAG, "Connected device : "+connectedDevice);
        txtDeviceName.setText(connectedDevice.getName());
        if((avrcp.getTargetFeatures(connectedDevice) & avrcp.FEATURE_BROWSE) > 0){
                spnPlayers.setVisibility(View.VISIBLE);
                lblPlayerName.setVisibility(View.VISIBLE);
                btnNowPlaying.setVisibility(View.VISIBLE);
                displayMsg("Avrcp 1.4");
            } else {
                spnPlayers.setVisibility(View.GONE);
                lblPlayerName.setVisibility(View.GONE);
                btnNowPlaying.setVisibility(View.GONE);
                //displayMsg("Not supporting latest Version Avrcp 1.4");
            }
        invalidateOptionsMenu();
        mHandler.sendEmptyMessage(START_AVRC_REQUEST);
        if((avrcp.getTargetFeatures(connectedDevice) & avrcp.FEATURE_BROWSE) > 0){
                mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
        }
    }

    @Override
    public void onClick(View v) {
        // When any passThrough command is invoked, disable the corresponding
        // button and enable the same based on
        // IBluetoothAvrcpControllerEventHandler.onPlaybackStatusChanged()
        // callback.
        if (v == btnPlayPause) {
            if (avrcpState == BluetoothAvrcpController.PLAY_STATUS_STOPPED
                    || avrcpState == BluetoothAvrcpController.PLAY_STATUS_PAUSED)
                avrcp.play(connectedDevice);

            else
                avrcp.pause(connectedDevice);
        } else if (v == btnPause) {
                avrcp.pause(connectedDevice);
        } else if (v == btnStop) {
            avrcp.stop(connectedDevice);
        } else if (v == btnNext) {
            avrcp.forward(connectedDevice);
        } else if (v == btnPrev) {
            avrcp.backward(connectedDevice);
        } else if (v == btnRepeat) {
            byte val = selectAttributeValue(BluetoothAvrcpController.ATTRIBUTE_REPEAT);
            Log.d(TAG, "Setting Repeat val ->" + val);
            avrcp.setPlayerApplicationSettingValue(connectedDevice,
                    new byte[] { BluetoothAvrcpController.ATTRIBUTE_REPEAT },
                    new byte[] { val });
        } else if (v == btnShuffle) {
            byte val = selectAttributeValue(BluetoothAvrcpController.ATTRIBUTE_SHUFFLE);
            Log.d(TAG, "Setting Shuffle val ->" + val);
            avrcp.setPlayerApplicationSettingValue(connectedDevice,
                    new byte[] { BluetoothAvrcpController.ATTRIBUTE_SHUFFLE },
                    new byte[] { val });
        } else if (v == btnRefresh) {
            Log.d(TAG, "Refreshing UI..");
            mHandler.sendEmptyMessage(GET_PLAYER_STATUS_REQUEST);
            mHandler.sendEmptyMessageDelayed(GET_ELEMENT_ATTR_REQUEST, 500);
        } else if (v == btnBrowse) {
            Intent intent = new Intent(this, BrowseActivity.class);
            intent.putExtra(EXTRA_DEVICE, connectedDevice);
            startActivity(intent);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        // TODO Auto-generated method stub
        if (v == btnRew) {
            if (action == MotionEvent.ACTION_DOWN)
                avrcp.rewind(connectedDevice, true);
            else if (action == MotionEvent.ACTION_UP)
                avrcp.rewind(connectedDevice, false);
            return true;
        } else if (v == btnFF) {
            if (action == MotionEvent.ACTION_DOWN)
                avrcp.fastforward(connectedDevice, true);
            else if (action == MotionEvent.ACTION_UP)
                avrcp.fastforward(connectedDevice, false);
            return true;
        }
        return false;
    }

    // Display dialog waiting for remote connection
    private void waitForAvrcpInit() {
        mCmdController.registerCallback(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showDialog();
            }
        });
    }

    private void playItem(byte scope, BluetoothAvrcpBrowseItem item) {
        if(D) Log.d(TAG, "playItem");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.playItem(connectedDevice, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.playItem(connectedDevice, scope, item.mElementUid);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        // TODO Auto-generated method stub
        if (id == GUI_UPDATE_MSG_WAITING_FOR_INIT_COMPLETE) {
            mWaitingProgressDialog = new ProgressDialog(this);
            mWaitingProgressDialog
                    .setMessage("AVRCP Controller App is waiting for the Init completion");
            mWaitingProgressDialog.setIndeterminate(true);
            mWaitingProgressDialog.setCancelable(true);
            mWaitingProgressDialog.setOnCancelListener(this);
            mWaitingProgressDialog.setOnDismissListener(this);
            mWaitingProgressDialog.setCanceledOnTouchOutside(false);
        }
        else if(id == GUI_UPDATE_MSG_NO_CONN_DEVICE_ERROR) {
            mErrorDialog= createErrorDialog("AVRCP Controller App",
                "No connected AVRCP Target device found currently.");
            mErrorDialog.setButton("Exit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    finish();
                }
            });
            return mErrorDialog;
        }
        return mWaitingProgressDialog;
    }

    @SuppressWarnings("deprecation")
    protected void showDialog() {
        // TODO Auto-generated method stub
        Log.d(TAG, "showDialog()");
        if (mWaitingProgressDialog == null
                || !mWaitingProgressDialog.isShowing()) {
            // Display dialog waiting for remote connection
            showDialog(GUI_UPDATE_MSG_WAITING_FOR_INIT_COMPLETE);
        }
    }

    private AlertDialog createErrorDialog(String title, String message)
    {
        AlertDialog errorDialog = new AlertDialog.Builder(this).create();
        errorDialog.setTitle(title);
        errorDialog.setMessage(message);
        errorDialog.setCancelable(true);
        errorDialog.setOnCancelListener(this);
        errorDialog.setOnDismissListener(this);
        return errorDialog;
    }


    @Override
    public void onCancel(DialogInterface arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onCommandCompleted(int status) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onCommandFailure(int errStatus) {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("deprecation")
    @Override
    public void onInitCompleted() {
        // TODO Auto-generated method stub
        dismissDialog(GUI_UPDATE_MSG_WAITING_FOR_INIT_COMPLETE);
        mHandler.sendEmptyMessage(GET_PLAYER_STATUS_REQUEST);
    }

    @Override
    public void onLoadAttributesFailure() {
        mHandler.sendEmptyMessage(CMD_ON_LOAD_APP_ATTR_FAIL);
        mHandler.sendEmptyMessage(GET_PLAYER_STATUS_REQUEST);
    }

    @Override
    public void onLoadMetataCompleted() {
        //mPosition = 0;
        mHandler.sendEmptyMessage(CMD_RELOAD_METADATA);
    }

    private byte selectAttributeValue(byte attrId) {
        ByteBuffer supportedAttrVals = mCmdController
                .getSupportedAttrValues(attrId);
        Log.w(TAG, "selectAttributeValue() attrId:"+attrId);
        if(supportedAttrVals == null) {
            Log.w(TAG, "No supported values set. Set default");
            return 0x01;
        }
        byte currVal = mCmdController.getCurrentAttrVal(attrId);
        int size = supportedAttrVals.array().length;
        if (size == 0) {
            Log.w(TAG, "No supported values available. Set default");
            return 0x01;
        }

        byte startVal = supportedAttrVals.get(0);
        byte endVal = supportedAttrVals.get(size - 1);
        Log.d(TAG, "start: " + startVal + ", end:" + endVal + ", len:" + size
                + " *** pos:" + supportedAttrVals.position());

        supportedAttrVals.position(0);
        for (int i = 0; i < size; i++) {
            byte attr = supportedAttrVals.get();
            Log.d(TAG, "currVal : " + currVal + " >>> " + attr);
            if (attr == currVal) {
                if (attr == endVal) {
                    Log.d(TAG, " >< ATTR VAL :: " + startVal);
                    supportedAttrVals.position(0);
                    return startVal;
                }
                attr = supportedAttrVals.get();
                Log.d(TAG, "ATTR VAL :: " + attr);
                return attr;
            } else
                continue;
        }

        Log.w(TAG, "Returning default XXX_OFF");
        return 0x01;
    }

    @Override
    public void onLoadAppAttrsLoaded() {
        Log.d(TAG, "onLoadAppAttrsLoaded() repeat:"+mCmdController.getRepeatState()+
                ", shuffle:"+mCmdController.getShuffleState());
        mHandler.sendEmptyMessage(CMD_UPDATE_UI_APP_SETTINGS);
    }

    private void updateMediaPlayers() {
        if(D) Log.d(TAG, "updateMediaPlayers");
        int size = mMediaPlayers.length;
        String array_spinner[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_spinner[i] = mMediaPlayers[i].mDisplayableName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, array_spinner);
        spnPlayers.setAdapter(adapter);
    }

/*    private void updateNowPlaying(int size) {
        array_nowPlayingItems = new String[size];
        for (int i = 0; i < size; i++) {
            array_nowPlayingItems[i] = mNowPlayingItems[i].mDisplayableName;
        }

        // Select the first item by default
        mSelectedNowItem = mNowPlayingItems[0];
    }*/

    private void updateNowPlaying() {
        if(D) Log.d(TAG, "updateNowPlaying");
        int size = mNowPlayingItems.length;
        array_nowPlayingItems = new String[size];
        for (int i = 0; i < size; i++) {
            array_nowPlayingItems[i] = mNowPlayingItems[i].mDisplayableName;
        }
        // Select the first item by default
        mSelectedNowItem = mNowPlayingItems[0];
    }

/*    private void callDisplayNowPlaying(){
        try{
            Intent btNowPLayingListIntent = new Intent(this, NowPlayingListActivity.class);
            Bundle b=new Bundle();
            b.putStringArray(NowPlayingListActivity.EXTRA_NOW_PLAYING_LIST, array_mediaPlayers);
            btNowPLayingListIntent.putExtras(b);
            startActivityForResult(btNowPLayingListIntent, NOW_PLAYING_LIST_REQ_CODE);
        }
        catch(ActivityNotFoundException e){
            e.printStackTrace();
        }
   }
*/
    private void displayMsg(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        TextView v = (TextView)toast.getView().findViewById(android.R.id.message);
        if(v != null) v.setGravity(Gravity.CENTER);
        toast.show();
    }
}
