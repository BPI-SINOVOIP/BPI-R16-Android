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

import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView.OnItemClickListener;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerEventHandler;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

/**
 * This Activity appears as a dialog. It tests the browse feature of remote device.
 */
public class BrowseActivity extends Activity {
    // Debugging
    private static final String TAG = "BrowseActivity";
    private static final boolean D = true;

    // Member fields
    BluetoothAvrcpController avrcp;
    BluetoothDevice target;
    Spinner spnPlayers;
    TextView txtPath;
    ListView lstFolder;
    ListView lstSearch;
    ListView lstNowPlaying;
    TextView txtSearch;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.d(TAG, "onCreate");
        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_browse);

        // Get connected device
        target = getIntent().getParcelableExtra(MediaPlayerActivity.EXTRA_DEVICE);
        if (target == null) Log.e(TAG, "onCreate()  Failed to get target device");

        // Load UI components
        loadUIComponents();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if(avrcp != null) avrcp.closeProxy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        BluetoothAvrcpController.getProxy(getApplicationContext(), listener);
    }

    @Override
    protected void onDestroy() {
        if(D) Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

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

            mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
        }
    };

    IBluetoothAvrcpControllerEventHandler eventHandler =
        new IBluetoothAvrcpControllerEventHandler() {

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
        }

        public void onChangePathRsp(BluetoothDevice target, byte direction, int numberOfItems,
                        int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "ChangePath response failed");
                return;
            }
            if(D) Log.d(TAG, "onChangePathRsp");
            Message m = mHandler.obtainMessage(PATH_CHANGED, (int)direction, numberOfItems, null);
            mHandler.sendMessage(m);
        }

        public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                        BluetoothAvrcpBrowseItem[] items, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "GetFolderItems response failed");
                return;
            }
            if(D) Log.d(TAG, "onGetFolderItemsRsp");

            switch (scope) {
            case BluetoothAvrcpController.SCOPE_MEDIA_PLAYER_LIST:
                mMediaPlayers = items;
                mHandler.sendEmptyMessage(GOT_MEDIA_PLAYER_LIST);
                break;
            case BluetoothAvrcpController.SCOPE_VIRTUAL_FILESYSTEM:
                mFolderItems = items;
                mHandler.sendEmptyMessage(GOT_FOLDER_ITEMS);
                break;
            case BluetoothAvrcpController.SCOPE_SEARCH:
                mSearchResult = items;
                mHandler.sendEmptyMessage(GOT_SEARCH_RESULT);
                break;
            case BluetoothAvrcpController.SCOPE_NOW_PLAYING:
                mNowPlayingItems = items;
                mHandler.sendEmptyMessage(GOT_NOW_PLAYING);
                break;
            }

        }

        public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                        String[] valueTexts, int status){
            if(D) Log.d(TAG, "onGetItemAttributesRsp");
        }

        public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "Search response failed");
                return;
            }
            if(D) Log.d(TAG, "onSearchRsp");
            Message m = mHandler.obtainMessage(GET_SEARCH_RESULT, numberOfItems, 0, null);
            mHandler.sendMessage(m);
        }

        public void onPlayItemRsp(BluetoothDevice target, int status){
            if(D) Log.d(TAG, "onPlayItemRsp");
        }

        public void onAddToNowPlayingRsp(BluetoothDevice target, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "AddToNowPlaying response failed");
                return;
            }
            if(D) Log.d(TAG, "onAddToNowPlayingRsp");
            mHandler.sendEmptyMessage(GET_NOW_PLAYING);
        }

        public void onSetAbsoluteVolumeRsp(BluetoothDevice target, byte volume, int status){
            if(D) Log.d(TAG, "onSetAbsoluteVolumeRsp");
        }

        public void onAddressedPlayerChanged(BluetoothDevice target, int playerId){
            if(D) Log.d(TAG, "onAddressedPlayerChanged");
        }

        public void onAvailablePlayersChanged(BluetoothDevice target){
            if(D) Log.d(TAG, "onAvailablePlayersChanged");
        }

        public void onNowPlayingContentChanged(BluetoothDevice target) {
            Log.d(TAG, "onNowPlayingContentChanged: Now Playing content changed");
            mHandler.sendEmptyMessage(GET_NOW_PLAYING);
        }

        public void onUIDsChanged(BluetoothDevice target){
            if(D) Log.d(TAG, "onUIDsChanged");
        }

        public void onVolumeChanged(BluetoothDevice target, byte volume){
            if(D) Log.d(TAG, "onVolumeChanged");
        }

    };

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage : " + msg);
            switch (msg.what) {
            case GET_MEDIA_PLAYER_LIST:
                if(D) Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                avrcp.getFolderItems(target, avrcp.SCOPE_MEDIA_PLAYER_LIST, 0, 99, null);
                break;
            case SET_BROWSED_PLAYER:
                if(D) Log.d(TAG, "handleMessage: SET_BROWSED_PLAYER");
                avrcp.setBrowsedPlayer(target, mBrowsedPlayer.mPlayerId);
                break;
            case GET_FOLDER_ITEMS:
                if(D) Log.d(TAG, "handleMessage: GET_FOLDER_ITEMS");
                avrcp.getFolderItems(target, avrcp.SCOPE_VIRTUAL_FILESYSTEM, 0, msg.arg1 - 1, null);
                break;
            case GET_NOW_PLAYING:
                if(D) Log.d(TAG, "handleMessage: GET_NOW_PLAYING");
                avrcp.getFolderItems(target, avrcp.SCOPE_NOW_PLAYING, 0, 99, null);
                break;
            case SET_FOLDER:
                if(D) Log.d(TAG, "handleMessage: SET_FOLDER");
                avrcp.changePath(target, avrcp.CHANGE_PATH_DIRECTION_DOWN,
                                 mSelectedFolderItem.mFolderUid);
                break;
            case SET_PARENT_FOLDER:
                if(D) Log.d(TAG, "handleMessage: SET_PARENT_FOLDER");
                avrcp.changePath(target, avrcp.CHANGE_PATH_DIRECTION_UP, (long)0);
                break;
            case PLAY_FOLDER_ITEM:
                if(D) Log.d(TAG, "handleMessage: PLAY_FOLDER_ITEM");
                playItem(avrcp.SCOPE_VIRTUAL_FILESYSTEM, mSelectedFolderItem);
                break;
            case ADD_FOLDER_ITEM_TO_NOW:
                if(D) Log.d(TAG, "handleMessage: ADD_FOLDER_ITEM_TO_NOW");
                addToNowPlaying(avrcp.SCOPE_VIRTUAL_FILESYSTEM, mSelectedFolderItem);
                break;
            case SEARCH:
                if(D) Log.d(TAG, "handleMessage: SEARCH");
                avrcp.search(target, txtSearch.getText().toString());
                break;
            case GET_SEARCH_RESULT:
                if(D) Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                avrcp.getFolderItems(target, avrcp.SCOPE_SEARCH, 0, msg.arg1 - 1, null);
                break;
            case PLAY_SEARCH_ITEM:
                if(D) Log.d(TAG, "handleMessage: PLAY_SEARCH_ITEM");
                playItem(avrcp.SCOPE_SEARCH, mSelectedSearchItem);
                break;
            case ADD_SEARCH_ITEM_TO_NOW:
                if(D) Log.d(TAG, "handleMessage: ADD_SEARCH_ITEM_TO_NOW");
                addToNowPlaying(avrcp.SCOPE_SEARCH, mSelectedSearchItem);
                break;
            case PLAY_NOW_ITEM:
                if(D) Log.d(TAG, "handleMessage: PLAY_NOW_ITEM");
                playItem(avrcp.SCOPE_NOW_PLAYING, mSelectedNowItem);
                break;

            case GOT_MEDIA_PLAYER_LIST:
                if(D) Log.d(TAG, "handleMessage: GOT_MEDIA_PLAYER_LIST");
                updateMediaPlayers();
                break;
            case BROWSED_PLAYER_SET: {
                if(D) Log.d(TAG, "handleMessage: BROWSED_PLAYER_SET");
                displayFolderPath();
                Message m = mHandler.obtainMessage(GET_FOLDER_ITEMS, msg.arg1, 0, null);
                mHandler.sendMessage(m);
                }
                break;
            case GOT_FOLDER_ITEMS:
                if(D) Log.d(TAG, "handleMessage: GOT_FOLDER_ITEMS");
                updateFolderItems();
                break;
            case PATH_CHANGED: {
                if(D) Log.d(TAG, "handleMessage: PATH_CHANGED");
                if ((byte)msg.arg1 == avrcp.CHANGE_PATH_DIRECTION_UP)
                    mFolderPath.remove(mFolderPath.size() - 1);
                else
                    mFolderPath.add(mSelectedFolderItem.mDisplayableName);
                displayFolderPath();
                Message m = mHandler.obtainMessage(GET_FOLDER_ITEMS, msg.arg2, 0, null);
                mHandler.sendMessage(m);
                }
                break;
            case GOT_SEARCH_RESULT:
                if(D) Log.d(TAG, "handleMessage: GOT_SEARCH_RESULT");
                updateSearchResult();
                break;
            case GOT_NOW_PLAYING:
                if(D) Log.d(TAG, "handleMessage: GOT_NOW_PLAYING");
                updateNowPlaying();
                break;
            default:
                Log.e(TAG, "Unhandled msg: " + msg.what);
            }
        }
    };

    private void loadUIComponents() {
        if(D) Log.d(TAG, "loadUIComponents");
        spnPlayers = (Spinner)findViewById(R.id.spn_players);
        spnPlayers.setOnItemSelectedListener(mPlayerSelectedListener);
        txtPath = (TextView)findViewById(R.id.txt_path);
        lstFolder = (ListView)findViewById(R.id.lst_folder);
        lstFolder.setOnItemClickListener(mFolderItemClickListener);
        txtSearch = (TextView)findViewById(R.id.edt_search);
        lstSearch = (ListView)findViewById(R.id.lst_search);
        lstSearch.setOnItemClickListener(mSearchItemClickListener);
        lstNowPlaying = (ListView)findViewById(R.id.lst_now);
        lstNowPlaying.setOnItemClickListener(mNowItemClickListener);
    }

    private OnItemSelectedListener mPlayerSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if(D) Log.d(TAG, "mPlayerSelectedListener");
            mBrowsedPlayer = mMediaPlayers[pos];
            if (mBrowsedPlayer.isBrowsingSupported()) {
                mHandler.sendEmptyMessage(SET_BROWSED_PLAYER);
                mHandler.sendEmptyMessageDelayed(GET_NOW_PLAYING, 500);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            if(D) Log.d(TAG, "mPlayerSelectedListener: onNothingSelected");
        }
    };

    private OnItemClickListener mFolderItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if(D) Log.d(TAG, "mFolderItemClickListener");
            mSelectedFolderItem = mFolderItems[pos];
            Log.d(TAG, "Folder item " + mSelectedFolderItem.mDisplayableName + " selected");
        }
    };

    private OnItemClickListener mSearchItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if(D) Log.d(TAG, "mSearchItemClickListener");
            mSelectedSearchItem = mSearchResult[pos];
            Log.d(TAG, "Search item " + mSelectedSearchItem.mDisplayableName + " selected");
        }
    };

    private OnItemClickListener mNowItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if(D) Log.d(TAG, "mNowItemClickListener");
            mSelectedNowItem = mNowPlayingItems[pos];
            Log.d(TAG, "Now Playing item " + mSelectedNowItem.mDisplayableName + " selected");
        }
    };

    private void updateMediaPlayers() {
        if(D) Log.d(TAG, "updateMediaPlayers");
        int size = mMediaPlayers.length;
        String array_spinner[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_spinner[i] = mMediaPlayers[i].mDisplayableName;
        }
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item,
                                                array_spinner);
        spnPlayers.setAdapter(adapter);
    }

    private void updateFolderItems() {
        if(D) Log.d(TAG, "updateFolderItems");
        int size = mFolderItems.length;
        String array_items[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_items[i] = mFolderItems[i].mDisplayableName;
        }
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_activated_1,
                                                array_items);
        lstFolder.setAdapter(adapter);

        // Select the first item by default
        mSelectedFolderItem = mFolderItems[0];
    }

    private void updateSearchResult() {
        if(D) Log.d(TAG, "updateSearchResult");
        int size = mSearchResult.length;
        String array_items[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_items[i] = mSearchResult[i].mDisplayableName;
        }
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_activated_1,
                                                array_items);
        lstSearch.setAdapter(adapter);

        // Select the first item by default
        mSelectedSearchItem = mSearchResult[0];
    }

    private void updateNowPlaying() {
        if(D) Log.d(TAG, "updateNowPlaying");
        try{
            int size = mNowPlayingItems.length;
            String array_items[] = new String[size];
            for (int i = 0; i < size; i++) {
                array_items[i] = mNowPlayingItems[i].mDisplayableName;
            }
            ArrayAdapter adapter = new ArrayAdapter(this,
                    android.R.layout.simple_list_item_activated_1, array_items);
            lstNowPlaying.setAdapter(adapter);

            // Select the first item by default
            mSelectedNowItem = mNowPlayingItems[0];
        } catch (NullPointerException e) {
            Log.e(TAG, "updateNowPlaying: NullPointerException");
        }
    }

    private void displayFolderPath() {
        if(D) Log.d(TAG, "displayFolderPath");
        String path = "";
        for (int i = 0; i < mFolderPath.size(); i++) {
            path += "/" + mFolderPath.get(i);
        }
        txtPath.setText(path);
    }

    private void playItem(byte scope, BluetoothAvrcpBrowseItem item) {
        if(D) Log.d(TAG, "playItem");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.playItem(target, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.playItem(target, scope, item.mElementUid);
    }

    private void addToNowPlaying(byte scope, BluetoothAvrcpBrowseItem item) {
        if(D) Log.d(TAG, "addToNowPlaying");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.addToNowPlaying(target, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.addToNowPlaying(target, scope, item.mElementUid);
    }

    public void onSetFolder(View v) {
        if(D) Log.d(TAG, "onSetFolder");
        mHandler.sendEmptyMessage(SET_FOLDER);
    }

    public void onParentFolder(View v) {
        if(D) Log.d(TAG, "onParentFolder");
        mHandler.sendEmptyMessage(SET_PARENT_FOLDER);
    }

    public void onPlayFolderItem(View v) {
        if(D) Log.d(TAG, "onPlayFolderItem");
        mHandler.sendEmptyMessage(PLAY_FOLDER_ITEM);
    }

    public void onAddFolderItemToNow(View v) {
        if(D) Log.d(TAG, "onAddFolderItemToNow");
        mHandler.sendEmptyMessage(ADD_FOLDER_ITEM_TO_NOW);
    }

    public void onSearch(View v) {
        if(D) Log.d(TAG, "onSearch");
        mHandler.sendEmptyMessage(SEARCH);
    }

    public void onPlaySearchItem(View v) {
        if(D) Log.d(TAG, "onPlaySearchItem");
        mHandler.sendEmptyMessage(PLAY_SEARCH_ITEM);
    }

    public void onAddSearchItemToNow(View v) {
        if(D) Log.d(TAG, "onAddSearchItemToNow");
        mHandler.sendEmptyMessage(ADD_SEARCH_ITEM_TO_NOW);
    }

    public void onPlayNowItem(View v) {
        if(D) Log.d(TAG, "onPlayNowItem");
        mHandler.sendEmptyMessage(PLAY_NOW_ITEM);
    }

}
