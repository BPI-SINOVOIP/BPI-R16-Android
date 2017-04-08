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

package com.broadcom.bt.app.avrcp;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerEventHandler;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

public class NowPlayingActivity extends Activity {

    // Debugging
    private static final String TAG = "NowPlayingActivity";
    private static final boolean D = true;

    BluetoothAvrcpController avrcp;
    BluetoothDevice target;

    private SongsTabAdapter mSongsTabAdapter;
    private String mUserSeleection;
    private int selectedItem, selecetdPosition;
    String[] array;
    int numberOfItems = 0;
    boolean pathChanged = false;

    // sending Intent extra
    public static final String CURRENT_SONG = "currentPlayingSong";
    // Return Intent extra
    public static String EXTRA_USER_SELECTION = "user_selection";
    public static String EXTRA_SELECTION_POSITION = "element_position";
    public static String EXTRA_OPTION = "element_option";

    private static final int PLAY = 0;
    private static final int ADD_TO_NOW_PLAYING = 1;

    String[] mMediaElements;

    BluetoothAvrcpBrowseItem[] mMediaPlayers;
    BluetoothAvrcpBrowseItem mBrowsedPlayer;
    BluetoothAvrcpBrowseItem[] mFolderItems;
    BluetoothAvrcpBrowseItem mSelectedFolderItem;
    BluetoothAvrcpBrowseItem[] mSearchResult;
    BluetoothAvrcpBrowseItem mSelectedSearchItem;
    BluetoothAvrcpBrowseItem[] mNowPlayingItems;
    BluetoothAvrcpBrowseItem mSelectedNowItem;
    private List<String> mFolderPath = new ArrayList<String>(0);

    private static final int GET_MEDIA_PLAYER_LIST = 101;
    private static final int SET_BROWSED_PLAYER = 102;
    private static final int GET_FOLDER_ITEMS = 103;
    private static final int GET_NOW_PLAYING = 104;
    private static final int SET_FOLDER = 105;
    private static final int SET_PARENT_FOLDER = 106;
    private static final int PLAY_FOLDER_ITEM = 107;
    private static final int ADD_FOLDER_ITEM_TO_NOW = 108;
    private static final int SEARCH = 109;
    private static final int GET_SEARCH_RESULT = 110;
    private static final int PLAY_SEARCH_ITEM = 111;
    private static final int ADD_SEARCH_ITEM_TO_NOW = 112;
    private static final int PLAY_NOW_ITEM = 113;

    private static final int GOT_MEDIA_PLAYER_LIST = 201;
    private static final int BROWSED_PLAYER_SET = 202;
    private static final int GOT_FOLDER_ITEMS = 203;
    private static final int PATH_CHANGED = 204;
    private static final int GOT_SEARCH_RESULT = 205;
    private static final int GOT_NOW_PLAYING = 206;
    private static final int GOT_MEDIA_ELEMENTS = 207;


    ServiceListener listener = new ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "onServiceDisconnected()");
            avrcp = null;
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            avrcp = (BluetoothAvrcpController) proxy;
            avrcp.registerEventHandler(eventHandler);
            Log.d(TAG, "onServiceConnected() eventHandler:" + eventHandler);
            if(numberOfItems != 0){
                mHandler.sendEmptyMessage(GET_NOW_PLAYING);
            } else {
                if(D) Log.e(TAG, "received list empty");
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Init
        init();
    }

    private void init() {
        if(D) Log.d(TAG, "init");
        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_media_element_list);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            target = getIntent().getParcelableExtra(MediaPlayerActivity.EXTRA_DEVICE);
            if (target == null)
                Log.e(TAG, "onCreate(): Failed to get target device");
            array = extras.getStringArray("array");
            numberOfItems = array.length;

            // Getting Current song on player
            mUserSeleection = getIntent().getStringExtra(CURRENT_SONG);
        } else {
            if (D)
                Log.e(TAG, "Bundle null");
            finish();
        }
        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
        ActionBar bar = getActionBar();
    }


    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        BluetoothAvrcpController.getProxy(getApplicationContext(), listener);
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if(avrcp != null) {
            avrcp.closeProxy();
        }
    }

    private OnItemClickListener mElementClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> av, View v, int position, long id) {
            // TODO Auto-generated method stub
            mUserSeleection = ((TextView)v.findViewById(R.id.Track_line1)).getText().toString();
            selecetdPosition = position;
            try {
                mSelectedNowItem = mNowPlayingItems[position];
            } catch (ArrayIndexOutOfBoundsException e){
                if(D) Log.e(TAG, "mFolderLongClickListener: ArrayIndexOutOfBoundsException");
            }
            mHandler.sendEmptyMessage(PLAY_NOW_ITEM);
        }
    };

    private void playItem(byte scope, BluetoothAvrcpBrowseItem item) {
        if (D)
            Log.d(TAG, "playItem");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.playItem(target, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.playItem(target, scope, item.mElementUid);
    }

    // Clearing all data as these activity is about to finish.
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    IBluetoothAvrcpControllerEventHandler eventHandler =
            new IBluetoothAvrcpControllerEventHandler() {

            //EXTRAS: just to implement interface methods ++
            @Override
            public void onConnectionStateChange(BluetoothDevice target,
                    int newState, boolean success) {
            }

            @Override
            public void onListPlayerApplicationSettingAttributesRsp(
                    BluetoothDevice target, byte[] attributeId, int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "ListPlayerApplicationSettingAttributes response failed");
                    return;
                }
            }

            @Override
            public void onListPlayerApplicationSettingValuesRsp(
                    BluetoothDevice target, byte attrId, byte[] valueId, int status) {
            }

            @Override
            public void onGetCurrentPlayerApplicationSettingValueRsp(
                    BluetoothDevice target, byte[] attributeId, byte[] valueId, int status) {
            }

            @Override
            public void onGetPlayerApplicationSettingAttributeTextRsp(
                    BluetoothDevice target, byte[] attributeId,
                    String[] attributeText, int status) {
            }

            @Override
            public void onGetPlayerApplicationSettingValueTextRsp(
                    BluetoothDevice target, byte attributeId, byte[] valueId,
                    String[] valueText, int status) {
            }

            @Override
            public void onGetElementAttributesRsp(BluetoothDevice target,
                    int[] attributeId, String[] valueText, int status) {
            }

            @Override
            public void onGetPlayStatusRsp(BluetoothDevice target, int songLength,
                    int songPosition, byte playStatus, int status) {
            }

            @Override
            public void onPlaybackStatusChanged(BluetoothDevice target, byte playStatus) {
            }

            @Override
            public void onTrackChanged(BluetoothDevice target, long trackId) {
            }

            @Override
            public void onTrackReachedEnd(BluetoothDevice target) {
            }

            @Override
            public void onTrackReachedStart(BluetoothDevice target) {
            }

            @Override
            public void onPlaybackPositionChanged(BluetoothDevice target, int playbackPosition) {
            }

            @Override
            public void onPlayerAppSettingChanged(BluetoothDevice target,
                    byte[] attribute, byte[] value) {
            }
            //EXTRAS: just to implement interface methods --

            @Override
            public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                            String[] folderPath, int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "SetBrowsedPlayer response failed");
                    return;
                }
                if(D) Log.d(TAG, "onSetBrowsedPlayerRsp");
            }

            @Override
            public void onChangePathRsp(BluetoothDevice target, byte direction, int numberOfItems,
                            int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "ChangePath response failed");
                    return;
                }
                if(D) Log.d(TAG, "onChangePathRsp");
            }

            @Override
            public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                            BluetoothAvrcpBrowseItem[] items, int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "GetFolderItems response failed");
                    return;
                }
                if(D) Log.d(TAG, "onGetFolderItemsRsp");

                switch (scope) {
                    case BluetoothAvrcpController.SCOPE_NOW_PLAYING:
                        mNowPlayingItems = items;
                        mHandler.sendEmptyMessage(GOT_NOW_PLAYING);
                        break;
                    default:
                        if(D) displayMsg("Unhandled Folderitem");
                        break;
                }

            }

            @Override
            public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                            String[] valueTexts, int status){
                if(D) Log.d(TAG, "onGetItemAttributesRsp");
            }

            @Override
            public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "Search response failed");
                    return;
                }
                if(D) Log.d(TAG, "onSearchRsp");
            }

            @Override
            public void onPlayItemRsp(BluetoothDevice target, int status){
                if(D) Log.d(TAG, "onPlayItemRsp");
                Intent resultIntent = new Intent();
                // Set result as positive and finish this Activity
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onAddToNowPlayingRsp(BluetoothDevice target, int status) {
                if (!BluetoothAvrcpController.isSuccess(status)) {
                    Log.d(TAG, "AddToNowPlaying response failed");
                    return;
                }
                if(D) Log.d(TAG, "onAddToNowPlayingRsp");
            }

            @Override
            public void onAddressedPlayerChanged(BluetoothDevice target, int playerId){
                if(D) Log.d(TAG, "onAddressedPlayerChanged");
            }

            @Override
            public void onAvailablePlayersChanged(BluetoothDevice target){
                if(D) Log.d(TAG, "onAvailablePlayersChanged");
            }

            @Override
            public void onNowPlayingContentChanged(BluetoothDevice target) {
                Log.d(TAG, "onNowPlayingContentChanged: Now Playing content changed");
                mHandler.sendEmptyMessage(GET_NOW_PLAYING);
            }

            @Override
            public void onUIDsChanged(BluetoothDevice target){
                if(D) Log.d(TAG, "onUIDsChanged");
            }

        };

        protected Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "handleMessage : " + msg);
                switch (msg.what) {
                case GET_MEDIA_PLAYER_LIST:
                    if(D) Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                    break;
                case SET_BROWSED_PLAYER:
                    if(D) Log.d(TAG, "handleMessage: SET_BROWSED_PLAYER");
                    break;
                case GET_FOLDER_ITEMS:
                    if(D) Log.d(TAG, "handleMessage: GET_FOLDER_ITEMS");
                    if(avrcp!= null){
                        avrcp.getFolderItems(target, avrcp.SCOPE_VIRTUAL_FILESYSTEM, 0, msg.arg1 - 1, null);
                    } else {
                        if(D)
                            Log.e(TAG, "Avrcp proxy null, cannot process GET_FOLDER_ITEMS");
                    }
                    break;
                case GET_NOW_PLAYING:
                    if(D) Log.d(TAG, "handleMessage: GET_NOW_PLAYING");
                    avrcp.getFolderItems(target, avrcp.SCOPE_NOW_PLAYING, 0, 99, null);
                    break;
                case SET_FOLDER:
                    if(D) Log.d(TAG, "handleMessage: SET_FOLDER");
                    break;
                case SET_PARENT_FOLDER:
                    if(D) Log.d(TAG, "handleMessage: SET_PARENT_FOLDER");
                    break;
                case PLAY_FOLDER_ITEM:
                    if(D) Log.d(TAG, "handleMessage: PLAY_FOLDER_ITEM");
                    break;
                case ADD_FOLDER_ITEM_TO_NOW:
                    if(D) Log.d(TAG, "handleMessage: ADD_FOLDER_ITEM_TO_NOW");
                    break;
                case SEARCH:
                    if(D) Log.d(TAG, "handleMessage: SEARCH");
                    break;
                case GET_SEARCH_RESULT:
                    if(D) Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                    break;
                case PLAY_SEARCH_ITEM:
                    if(D) Log.d(TAG, "handleMessage: PLAY_SEARCH_ITEM");
                    break;
                case ADD_SEARCH_ITEM_TO_NOW:
                    if(D) Log.d(TAG, "handleMessage: ADD_SEARCH_ITEM_TO_NOW");
                    break;
                case PLAY_NOW_ITEM:
                    if(D) Log.d(TAG, "handleMessage: PLAY_NOW_ITEM");
                    playItem(avrcp.SCOPE_NOW_PLAYING, mSelectedNowItem);
                    break;
                case GOT_MEDIA_PLAYER_LIST:
                    if(D) Log.d(TAG, "handleMessage: GOT_MEDIA_PLAYER_LIST");
                    break;
                case BROWSED_PLAYER_SET: {
                    if(D) Log.d(TAG, "handleMessage: BROWSED_PLAYER_SET");
                    }
                    break;
                case GOT_FOLDER_ITEMS:
                    if(D) Log.d(TAG, "handleMessage: GOT_FOLDER_ITEMS");
                    break;
                case GOT_MEDIA_ELEMENTS:
                    if(D) Log.d(TAG, "handleMessage: GOT_MEDIA_ELEMENTS");
                    break;
                case PATH_CHANGED:
                    if(D) Log.d(TAG, "handleMessage: PATH_CHANGED");
                    break;
                case GOT_SEARCH_RESULT:
                    break;
                case GOT_NOW_PLAYING:
                    if(D) Log.d(TAG, "handleMessage: GOT_NOW_PLAYING");
                    updateNowPlaying();
                    displayNowPlaying();
                    break;
                default:
                    Log.e(TAG, "Unhandled msg: " + msg.what);
                }
            }
        };

        private void updateNowPlaying() {
            if(D) Log.d(TAG, "updateNowPlaying");
            int size = mNowPlayingItems.length;
            String array_items[] = new String[size];
            for (int i = 0; i < size; i++) {
                array_items[i] = mNowPlayingItems[i].mDisplayableName;
            }
            // Select the first item by default
            mSelectedNowItem = mNowPlayingItems[0];
        }

        private void displayNowPlaying(){
            try{
                if (mNowPlayingItems.length != 0){
                    mSongsTabAdapter = new SongsTabAdapter(this,
                            android.R.layout.simple_list_item_1, array, null, null);
                    // Initialize array adapter for list of media Elements
                    /*
                     * ArrayAdapter<CharSequence> mPlayerAdapter = new
                     * ArrayAdapter<CharSequence> (this,
                     * android.R.layout.simple_list_item_1, array);
                     */

                    // Find and set up the ListView for players list
                    ListView playersListView = (ListView)findViewById(R.id.npl_players_list);
                    playersListView.setAdapter(mSongsTabAdapter);
                    //playersListView.setOnItemLongClickListener(mFolderLongClickListener);
                    playersListView.setOnItemClickListener(mElementClickListener);
                } else {
                    Log.d(TAG, "Media Element List Empty");
                }
            } catch (NullPointerException e) {
                if(D) Log.d(TAG, "mNowPlayingItems Null");
            }
        }
    private void displayMsg(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
        TextView v = (TextView)toast.getView().findViewById(android.R.id.message);
        if (v != null)
            v.setGravity(Gravity.CENTER);
        toast.show();
    }

}
