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

import android.os.Bundle;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView.OnItemClickListener;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerEventHandler;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

public class BrowseNewActivity extends Activity {

    // Debugging
    private static final String TAG = "BrowseNewActivity";
    private static final boolean D = true;

    // Member fields
    ActionBar bar;
    BluetoothAvrcpController avrcp;
    BluetoothDevice target;
    Spinner spnPlayers;
    TextView txtPath;
    ListView lstFolder;
    ImageButton btnSearch;
    EditText editSearch;
    TextView searchDesc;
    boolean searchEnabled = false;
    int selectedItem;
    String[] mMediaElements;
    int numberOfItemsToSend;
    int optionPickerId;

    BluetoothAvrcpBrowseItem[] mMediaPlayers;
    BluetoothAvrcpBrowseItem mBrowsedPlayer;
    BluetoothAvrcpBrowseItem[] mFolderItems;
    BluetoothAvrcpBrowseItem mSelectedFolderItem;
    BluetoothAvrcpBrowseItem[] mSearchResult;
    BluetoothAvrcpBrowseItem mSelectedSearchItem;
    BluetoothAvrcpBrowseItem[] mNowPlayingItems;
    BluetoothAvrcpBrowseItem mSelectedNowItem;

    private int mNumItems;
    private List<BluetoothAvrcpBrowseItem> mItems = new ArrayList<BluetoothAvrcpBrowseItem>(0);

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

    private static final int PLAY = 0;
    private static final int ADD_TO_NOW_PLAYING = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (D)
            Log.d(TAG, "onCreate");
        // Setup the window
        // requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_browse_new);
        // Show the Up button in the action bar.
        setupActionBar();

        // Get connected device
        target = getIntent().getParcelableExtra(MediaPlayerActivity.EXTRA_DEVICE);
        if (target == null)
            Log.e(TAG, "onCreate(): Failed to get target device");

        // Load UI components
        loadUIComponents();
    }

    /**
     * Set up the {@link android.app.ActionBar}.
     */
    private void setupActionBar() {
        bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.browse_new, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown.
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_search:
                if (D)
                    Log.d(TAG, "onOptionsItemSelected: action_search");
                onSearchRequest();
                return true;
            case R.id.action_browse:
                if (D)
                    Log.d(TAG, "onOptionsItemSelected: action_browse");
                onBrowseRequest();
                return true;
            default:
                if (D)
                    Log.d(TAG, "onOptionsItemSelected: default");
                // TODO: remove after testing
                displayMsg("case not handled");
                return false;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem menu_search = menu.findItem(R.id.action_search);
        MenuItem menu_browse = menu.findItem(R.id.action_browse);
        if (searchEnabled) {
            menu_search.setVisible(!searchEnabled);
            menu_browse.setVisible(searchEnabled);
        } else {
            menu_search.setVisible(!searchEnabled);
            menu_browse.setVisible(searchEnabled);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (avrcp != null)
            avrcp.closeProxy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        BluetoothAvrcpController.getProxy(getApplicationContext(), listener);
    }

    @Override
    protected void onDestroy() {
        if (D)
            Log.d(TAG, "onDestroy");
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
            avrcp = (BluetoothAvrcpController)proxy;
            avrcp.registerEventHandler(eventHandler);
            Log.d(TAG, "onServiceConnected() eventHandler:" + eventHandler);
            mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
        }
    };

    IBluetoothAvrcpControllerEventHandler eventHandler = new IBluetoothAvrcpControllerEventHandler() {

        // EXTRAS: just to implement interface methods ++
        @Override
        public void onConnectionStateChange(BluetoothDevice target, int newState, boolean success) {
        }

        @Override
        public void onListPlayerApplicationSettingAttributesRsp(BluetoothDevice target,
                byte[] attributeId, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "ListPlayerApplicationSettingAttributes response failed");
                return;
            }
        }

        @Override
        public void onListPlayerApplicationSettingValuesRsp(BluetoothDevice target, byte attrId,
                byte[] valueId, int status) {
        }

        @Override
        public void onGetCurrentPlayerApplicationSettingValueRsp(BluetoothDevice target,
                byte[] attributeId, byte[] valueId, int status) {
        }

        @Override
        public void onGetPlayerApplicationSettingAttributeTextRsp(BluetoothDevice target,
                byte[] attributeId, String[] attributeText, int status) {
        }

        @Override
        public void onGetPlayerApplicationSettingValueTextRsp(BluetoothDevice target,
                byte attributeId, byte[] valueId, String[] valueText, int status) {
        }

        @Override
        public void onGetElementAttributesRsp(BluetoothDevice target, int[] attributeId,
                String[] valueText, int status) {
        }

        @Override
        public void onGetPlayStatusRsp(BluetoothDevice target, int songLength, int songPosition,
                byte playStatus, int status) {
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
        public void onPlayerAppSettingChanged(BluetoothDevice target, byte[] attribute,
                byte[] value) {
        }

        // EXTRAS: just to implement interface methods --

        @Override
        public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                String[] folderPath, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "SetBrowsedPlayer response failed");
                return;
            }
            if (D)
                Log.d(TAG, "onSetBrowsedPlayerRsp");
            mFolderPath.clear();
            for (int i = 0; i < folderPath.length; i++)
                mFolderPath.add(folderPath[i]);
            Message m = mHandler.obtainMessage(BROWSED_PLAYER_SET, numberOfItems, 0, null);
            mHandler.sendMessage(m);
        }

        @Override
        public void onChangePathRsp(BluetoothDevice target, byte direction, int numberOfItems,
                int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "ChangePath response failed");
                return;
            }
            if (D)
                Log.d(TAG, "onChangePathRsp");
            Message m = mHandler.obtainMessage(PATH_CHANGED, direction, numberOfItems, null);
            mHandler.sendMessage(m);
        }

        @Override
        public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                BluetoothAvrcpBrowseItem[] items, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "GetFolderItems response failed");
                return;
            }
            if (D)
                Log.d(TAG, "onGetFolderItemsRsp");

            switch (scope) {
                case BluetoothAvrcpController.SCOPE_MEDIA_PLAYER_LIST:
                    mMediaPlayers = items;
                    mHandler.sendEmptyMessage(GOT_MEDIA_PLAYER_LIST);
                    break;
                case BluetoothAvrcpController.SCOPE_VIRTUAL_FILESYSTEM:
                    for (int i = 0; i < items.length; i++)
                        mItems.add(items[i]);
                    if (mItems.size() < mNumItems) {
                        Message m = mHandler.obtainMessage(GET_FOLDER_ITEMS, mItems.size(),
                                                           mNumItems - 1, null);
                        mHandler.sendMessage(m);
                    } else {
                        BluetoothAvrcpBrowseItem[] bi = new BluetoothAvrcpBrowseItem[mItems.size()];
                        mItems.toArray(bi);
                        int itemType = checkItemType(bi);
                        processItemType(itemType, bi);
                    }
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

        @Override
        public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                String[] valueTexts, int status) {
            if (D)
                Log.d(TAG, "onGetItemAttributesRsp");
        }

        @Override
        public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "Search response failed");
                return;
            }
            if (D)
                Log.d(TAG, "onSearchRsp");
            Message m = mHandler.obtainMessage(GET_SEARCH_RESULT, numberOfItems, 0, null);
            mHandler.sendMessage(m);
        }

        @Override
        public void onPlayItemRsp(BluetoothDevice target, int status) {
            if (D)
                Log.d(TAG, "onPlayItemRsp");
        }

        @Override
        public void onAddToNowPlayingRsp(BluetoothDevice target, int status) {
            if (!BluetoothAvrcpController.isSuccess(status)) {
                Log.d(TAG, "AddToNowPlaying response failed");
                return;
            }
            if (D)
                Log.d(TAG, "onAddToNowPlayingRsp");
            mHandler.sendEmptyMessage(GET_NOW_PLAYING);
        }

        @Override
        public void onAddressedPlayerChanged(BluetoothDevice target, int playerId) {
            if (D)
                Log.d(TAG, "onAddressedPlayerChanged");
            mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
        }

        @Override
        public void onAvailablePlayersChanged(BluetoothDevice target) {
            if (D)
                Log.d(TAG, "onAvailablePlayersChanged");
        }

        @Override
        public void onNowPlayingContentChanged(BluetoothDevice target) {
            Log.d(TAG, "onNowPlayingContentChanged: Now Playing content changed");
            mHandler.sendEmptyMessage(GET_NOW_PLAYING);
        }

        @Override
        public void onUIDsChanged(BluetoothDevice target) {
            if (D)
                Log.d(TAG, "onUIDsChanged");
        }

    };

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage : " + msg);
            switch (msg.what) {
                case GET_MEDIA_PLAYER_LIST:
                    if (D)
                        Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                    avrcp.getFolderItems(target, avrcp.SCOPE_MEDIA_PLAYER_LIST, 0, 99, null);
                    break;
                case SET_BROWSED_PLAYER:
                    if (D)
                        Log.d(TAG, "handleMessage: SET_BROWSED_PLAYER");
                    avrcp.setBrowsedPlayer(target, mBrowsedPlayer.mPlayerId);
                    break;
                case GET_FOLDER_ITEMS:
                    if (D)
                        Log.d(TAG, "handleMessage: GET_FOLDER_ITEMS");
                    avrcp.getFolderItems(target, avrcp.SCOPE_VIRTUAL_FILESYSTEM, msg.arg1,
                            msg.arg2, null);
                    break;
                case GET_NOW_PLAYING:
                    if (D)
                        Log.d(TAG, "handleMessage: GET_NOW_PLAYING");
                    avrcp.getFolderItems(target, avrcp.SCOPE_NOW_PLAYING, 0, 99, null);
                    break;
                case SET_FOLDER:
                    if (D)
                        Log.d(TAG, "handleMessage: SET_FOLDER");
                    avrcp.changePath(target, avrcp.CHANGE_PATH_DIRECTION_DOWN,
                            mSelectedFolderItem.mFolderUid);
                    break;
                case SET_PARENT_FOLDER:
                    if (D)
                        Log.d(TAG, "handleMessage: SET_PARENT_FOLDER");
                    avrcp.changePath(target, avrcp.CHANGE_PATH_DIRECTION_UP, 0);
                    break;
                case PLAY_FOLDER_ITEM:
                    if (D)
                        Log.d(TAG, "handleMessage: PLAY_FOLDER_ITEM");
                    playItem(avrcp.SCOPE_VIRTUAL_FILESYSTEM, mSelectedFolderItem);
                    break;
                case ADD_FOLDER_ITEM_TO_NOW:
                    if (D)
                        Log.d(TAG, "handleMessage: ADD_FOLDER_ITEM_TO_NOW");
                    addToNowPlaying(avrcp.SCOPE_VIRTUAL_FILESYSTEM, mSelectedFolderItem);
                    break;
                case SEARCH:
                    if (D)
                        Log.d(TAG, "handleMessage: SEARCH");
                    displayMsg("Searching For: " + editSearch.getText().toString());
                    avrcp.search(target, editSearch.getText().toString());
                    editSearch.setText("");
                    break;
                case GET_SEARCH_RESULT:
                    if (D)
                        Log.d(TAG, "handleMessage:GET_MEDIA_PLAYER_LIST");
                    avrcp.getFolderItems(target, avrcp.SCOPE_SEARCH, 0, msg.arg1 - 1, null);
                    break;
                case PLAY_SEARCH_ITEM:
                    if (D)
                        Log.d(TAG, "handleMessage: PLAY_SEARCH_ITEM");
                    playItem(avrcp.SCOPE_SEARCH, mSelectedSearchItem);
                    break;
                case ADD_SEARCH_ITEM_TO_NOW:
                    if (D)
                        Log.d(TAG, "handleMessage: ADD_SEARCH_ITEM_TO_NOW");
                    addToNowPlaying(avrcp.SCOPE_SEARCH, mSelectedSearchItem);
                    break;
                case PLAY_NOW_ITEM:
                    if (D)
                        Log.d(TAG, "handleMessage: PLAY_NOW_ITEM");
                    // playItem(avrcp.SCOPE_NOW_PLAYING, mSelectedNowItem);
                    break;
                case GOT_MEDIA_PLAYER_LIST:
                    // this.removeMessages(UI_TIMEOUT);
                    if (D)
                        Log.d(TAG, "handleMessage: GOT_MEDIA_PLAYER_LIST");
                    updateMediaPlayers();
                    break;
                case BROWSED_PLAYER_SET: {
                    if (D)
                        Log.d(TAG, "handleMessage: BROWSED_PLAYER_SET");
                    displayFolderPath();
                    getFolderContent(msg.arg1);
                }
                    break;
                case GOT_FOLDER_ITEMS:
                    if (D)
                        Log.d(TAG, "handleMessage: GOT_FOLDER_ITEMS");
                    lstFolder.setOnItemClickListener(mFolderItemClickListener);
                    lstFolder.setOnItemLongClickListener(mFolderLongClickListener);
                    updateFolderItems();
                    break;
                case GOT_MEDIA_ELEMENTS:
                    if (D)
                        Log.d(TAG, "handleMessage: GOT_MEDIA_ELEMENTS");
                    updateMediaItems();
                    displayMediaItems();
                    break;
                case PATH_CHANGED: {
                    if (D)
                        Log.d(TAG, "handleMessage: PATH_CHANGED");
                    try {
                        if ((byte)msg.arg1 == avrcp.CHANGE_PATH_DIRECTION_UP)
                            mFolderPath.remove(mFolderPath.size() - 1);
                        else
                            mFolderPath.add(mSelectedFolderItem.mDisplayableName);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        if (D)
                            Log.e(TAG, "FolderPath: ArrayIndexOutOfBoundsException");
                    }
                    displayFolderPath();
                    getFolderContent(msg.arg2);
                }
                    break;
                case GOT_SEARCH_RESULT:
                    if (D)
                        Log.d(TAG, "handleMessage: GOT_SEARCH_RESULT");
                    lstFolder.setOnItemClickListener(mSearchItemClickListener);
                    lstFolder.setOnItemLongClickListener(mSearchItemLongClickListener);
                    updateSearchResult();
                    break;
                case GOT_NOW_PLAYING:
                    if (D)
                        Log.d(TAG, "handleMessage: GOT_NOW_PLAYING");
                    break;
                default:
                    Log.e(TAG, "Unhandled msg: " + msg.what);
            }
        }
    };

    private void loadUIComponents() {
        if (D)
            Log.d(TAG, "loadUIComponents");
        spnPlayers = (Spinner)findViewById(R.id.spn_players);
        spnPlayers.setOnItemSelectedListener(mPlayerSelectedListener);
        txtPath = (TextView)findViewById(R.id.txt_path);
        lstFolder = (ListView)findViewById(R.id.lst_folder);
        btnSearch = (ImageButton)findViewById(R.id.btn_search);
        editSearch = (EditText)findViewById(R.id.edt_search);
        searchDesc = (TextView)findViewById(R.id.lbl_search);
        // mProgressDialog = new ProgressDialog(this);
    }

    private void getFolderContent(int numItems) {
        mItems.clear();
        mNumItems = numItems;
        Message m = mHandler.obtainMessage(GET_FOLDER_ITEMS, 0, numItems - 1, null);
        mHandler.sendMessage(m);
    }

    protected int checkItemType(BluetoothAvrcpBrowseItem[] items) {
        int media_element_count = 0;
        int folder_count = 0;
        int itemType = 0;
        int size = items.length;
        if (size == 0)
            return itemType;
        for (int i = 0; i < size; i++) {
            itemType = (int)(items[i].mItemType);
            if (itemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT) {
                media_element_count++;
            } else if (itemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER) {
                folder_count++;
            } else {
                Log.e(TAG, "NOT SUPPORTED VIRTUAL FILESYSTEM TYPE");
                itemType = -1;
                return itemType;
            }

        }
        if (media_element_count != size && folder_count != size) {
            // TODO: Handle the case if folderList returns the items where,
            // list contains Media Elements as well as Folder items
            Log.d(TAG, "FolderList contains Media Elements as well as Folder items");
            itemType = 2;
        }
        numberOfItemsToSend = media_element_count;
        return itemType;
    }

    protected void processItemType(int itemType, BluetoothAvrcpBrowseItem[] items) {
        switch (itemType) {
            case -1:
                Log.d(TAG, "error in chekItemType");
                break;
            case 0:
                if (D)
                    Log.d(TAG, "getFolderList Empty");
                Toast.makeText(getApplicationContext(), "Empty List received", Toast.LENGTH_LONG)
                        .show();
                break;
            case 2:
                if (D)
                    Log.d(TAG, "ITEM_TYPE_FOLDER");
                mFolderItems = items;
                mHandler.sendEmptyMessage(GOT_FOLDER_ITEMS);
                break;
            case 3:
                if (D)
                    Log.d(TAG, "ITEM_TYPE_MEDIA_ELEMENT");
                // TODO: Display media_element_list
                mFolderItems = items;
                mHandler.sendEmptyMessage(GOT_MEDIA_ELEMENTS);
                break;
            default:
                Log.e(TAG, "ItemType not handled");
        }
    }

    private OnItemSelectedListener mPlayerSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (D)
                Log.d(TAG, "mPlayerSelectedListener");
            mBrowsedPlayer = mMediaPlayers[pos];
            if (mBrowsedPlayer.isBrowsingSupported()) {
                mHandler.sendEmptyMessage(SET_BROWSED_PLAYER);
                mHandler.sendEmptyMessageDelayed(GET_NOW_PLAYING, 500);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            if (D)
                Log.d(TAG, "mPlayerSelectedListener: onNothingSelected");
        }
    };

    private OnItemClickListener mFolderItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if (D)
                Log.d(TAG, "mFolderItemClickListener");
            mSelectedFolderItem = mFolderItems[pos];
            Log.d(TAG, "Folder item " + mSelectedFolderItem.mDisplayableName + " selected");
            mHandler.sendEmptyMessage(SET_FOLDER);
        }
    };

    private OnItemLongClickListener mFolderLongClickListener = new OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            optionPicker(1);
            return false;
        }
    };

    private OnItemClickListener mSearchItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if (D)
                Log.d(TAG, "mSearchItemClickListener");
            mSelectedSearchItem = mSearchResult[pos];
            Log.d(TAG, "Search item " + mSelectedSearchItem.mDisplayableName + " selected");
        }
    };

    private OnItemLongClickListener mSearchItemLongClickListener = new OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
            //mSelectedSearchItem = mSearchResult[pos];
            optionPicker(2);
            return false;
        }
    };

    private void updateMediaPlayers() {
        if (D)
            Log.d(TAG, "updateMediaPlayers");
        int size = mMediaPlayers.length;
        String array_spinner[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_spinner[i] = mMediaPlayers[i].mDisplayableName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, array_spinner);
        spnPlayers.setAdapter(adapter);
    }

    private void updateFolderItems() {
        if (D)
            Log.d(TAG, "updateFolderItems");
        int size = mFolderItems.length;
        String array_items[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_items[i] = mFolderItems[i].mDisplayableName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, array_items);
        lstFolder.setAdapter(adapter);

        // Select the first item by default
        mSelectedFolderItem = mFolderItems[0];
    }

    private void updateMediaItems() {
        if (D)
            Log.d(TAG, "updateFolderItems");
        int size = mFolderItems.length;
        mMediaElements = new String[size];
        for (int i = 0; i < size; i++) {
            mMediaElements[i] = mFolderItems[i].mDisplayableName;
        }
        mSelectedFolderItem = mFolderItems[0];
    }

    private void updateSearchResult() {
        if (D)
            Log.d(TAG, "updateSearchResult");
        int size = mSearchResult.length;
        String array_items[] = new String[size];
        for (int i = 0; i < size; i++) {
            array_items[i] = mSearchResult[i].mDisplayableName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_activated_1, array_items);
        lstFolder.setAdapter(adapter);

        // Select the first item by default
        mSelectedSearchItem = mSearchResult[0];
    }

    private void displayFolderPath() {
        if (D)
            Log.d(TAG, "displayFolderPath");
        String path = "";
        for (int i = 0; i < mFolderPath.size(); i++) {
            path += "/" + mFolderPath.get(i);
        }
        txtPath.setText(path);
    }

    private void displayMediaItems() {
        if (mMediaElements.length != 0) {
            displayMsg("Opening Media Element List");
            try {
                Intent openMediaElementlistIntent = new Intent(this,
                        MediaElementListActivity.class);
                openMediaElementlistIntent.putExtra(MediaPlayerActivity.EXTRA_DEVICE, target);
                openMediaElementlistIntent.putExtra("media_items", mFolderItems);
                // here in putextra we'll send now_playing_list_items
                openMediaElementlistIntent.putExtra(NowPlayingActivity.CURRENT_SONG,
                        mSelectedFolderItem.mDisplayableName);
                // TODO: Define constant in place of hard coded 8888
                startActivityForResult(openMediaElementlistIntent, 8888);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            displayMsg("Folder Empty");
            Log.d(TAG, "Media Element List Empty");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 8888:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "RESULT_OK");
                } else {
                    // It is okay if user only wants to see Media Element List
                    // and press back
                    Log.i(TAG, "!RESULT_OK = FAILED(" + resultCode + ")");
                }
                break;
            default:
                // TODO: remove after testing
                displayMsg("Activity Result not handled");
        }
    }

    private void playItem(byte scope, BluetoothAvrcpBrowseItem item) {
        if (D)
            Log.d(TAG, "playItem");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.playItem(target, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.playItem(target, scope, item.mElementUid);
    }

    private void addToNowPlaying(byte scope, BluetoothAvrcpBrowseItem item) {
        if (D)
            Log.d(TAG, "addToNowPlaying");
        if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_FOLDER)
            avrcp.addToNowPlaying(target, scope, item.mFolderUid);
        else if (item.mItemType == BluetoothAvrcpBrowseItem.ITEM_TYPE_MEDIA_ELEMENT)
            avrcp.addToNowPlaying(target, scope, item.mElementUid);
    }

    public void onParentFolder(View v) {
        if (D)
            Log.d(TAG, "onParentFolder");
        mHandler.sendEmptyMessage(SET_PARENT_FOLDER);
    }

    public void onSearch(View v) {
        if (D)
            Log.d(TAG, "onSearch");
        InputMethodManager imm = (InputMethodManager)getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        mHandler.sendEmptyMessage(SEARCH);
    }

    // local methods
    private void onSearchRequest() {
        searchEnabled = true;
        bar.setTitle("    Click here for Browse >");
        btnSearch.setVisibility(View.VISIBLE);
        searchDesc.setVisibility(View.VISIBLE);
        editSearch.setVisibility(View.VISIBLE);
        editSearch.setBackgroundResource(android.R.drawable.editbox_background);
        editSearch.post(new Runnable() {
            @Override
            public void run() {
                editSearch.setFocusableInTouchMode(true);
                editSearch.requestFocus();
                final InputMethodManager imm = (InputMethodManager)getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT);
            }

        });
        lstFolder.setOnItemClickListener(null);
        lstFolder.setOnItemLongClickListener(null);
        invalidateOptionsMenu();
    }

    private void onBrowseRequest() {
        InputMethodManager imm = (InputMethodManager)getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        searchEnabled = false;
        editSearch.setText("");
        bar.setTitle("Browse");
        searchDesc.setVisibility(View.GONE);
        btnSearch.setVisibility(View.GONE);
        editSearch.setVisibility(View.GONE);
        lstFolder.setOnItemClickListener(null);
        lstFolder.setOnItemLongClickListener(null);
        invalidateOptionsMenu();
        mHandler.sendEmptyMessage(GET_MEDIA_PLAYER_LIST);
    }

    private void optionPicker(int id) {
        optionPickerId = id;
        final CharSequence[] optionsList = {
                "Play", "Add To Now Playing List"
        };
        AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
        alt_bld.setIcon(R.drawable.bluetooth);
        alt_bld.setTitle("Select Option:");
        selectedItem = -1;
        alt_bld.setSingleChoiceItems(optionsList, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                selectedItem = item;
                if(D) Log.d(TAG, "in optionPicker: > selected Item: " + selectedItem);
                switch (item) {
                    case PLAY:
                        if(D) Log.d(TAG, "in optionPicker: > PLAY> optionPickerId: " + optionPickerId);
                        switch (optionPickerId) {
                            case 1:
                                if (D)
                                    Log.d(TAG, "optionPicker: PLAY_FOLDER_ITEM");
                                mHandler.sendEmptyMessage(PLAY_FOLDER_ITEM);
                                break;
                            case 2:
                                if (D)
                                    Log.d(TAG, "optionPicker: PLAY_SEARCH_ITEM");
                                mHandler.sendEmptyMessage(PLAY_SEARCH_ITEM);
                                break;
                            default:
                                if (D)
                                    Log.e(TAG, "optionPicker: case not handled");
                        }
                        break;
                    case ADD_TO_NOW_PLAYING:
                        switch (optionPickerId) {
                            case 1:
                                if (D)
                                    Log.d(TAG, "optionPicker: ADD_FOLDER_ITEM_TO_NOW");
                                mHandler.sendEmptyMessage(ADD_FOLDER_ITEM_TO_NOW);
                                break;
                            case 2:
                                if (D)
                                    Log.d(TAG, "optionPicker: ADD_SEARCH_ITEM_TO_NOW");
                                mHandler.sendEmptyMessage(ADD_SEARCH_ITEM_TO_NOW);
                                break;
                            default:
                                if (D)
                                    Log.e(TAG, "optionPicker: case not handled");
                        }
                        break;
                }
                displayMsg("Selected Action: " + optionsList[selectedItem]);
                dialog.dismiss();
            }
        });

        alt_bld.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog alert = alt_bld.create();
        alert.show();
    }

    private void displayMsg(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        TextView v = (TextView)toast.getView().findViewById(android.R.id.message);
        if (v != null)
            v.setGravity(Gravity.CENTER);
        toast.show();
    }

    @Override
    public void onBackPressed() {
        if (D)
            Log.d(TAG, "onBackPressed");
        if(txtPath.getText() != "") mHandler.sendEmptyMessage(SET_PARENT_FOLDER);
        else super.onBackPressed();
    }

}
