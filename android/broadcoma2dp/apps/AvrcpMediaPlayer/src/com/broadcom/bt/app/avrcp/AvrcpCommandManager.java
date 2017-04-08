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
import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.broadcom.bt.avrcp.IBluetoothAvrcpControllerEventHandler;
import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

/**
 * Helper class to execute {@link BluetoothAvrcpController} API calls and update
 * the UI Activity class with the callbacks using {@link AvrcpCommandCallback}
 *
 * @author syedibra
 *
 */
public class AvrcpCommandManager implements
        IBluetoothAvrcpControllerEventHandler {

    public final static int CMD_LOAD_APP_SETTING_ATTR = 100;
    public final static int CMD_LOAD_METADATA = 101;
    public final static int CMD_LOAD_MISC = 102;
    public final static int CMD_RESET_APP_SETTING_ATTR = 103;
    private final static int INIT_TIMEOUT_EVT = 1000;
    protected static final String TAG = "AvrcpCommandManager";
    private final static boolean DBG = false;

    private static int mCmdCount = 0;
    private static Object LOCK = new Object();

    /* Stores all info for supported App Settings Attributes*/
    // 0 = Equalizer attrs
    // 1 = Repeat attrs
    // 2 = Shuffle attrs
    // 3 = Scan attrs
    List<AvrcAppAttributeWrapper> mAttrsData = new ArrayList<AvrcAppAttributeWrapper>(5);
    /* Stores all info for media metadata */
    AvrcMetadatawrapper mMetadata;
    long mCurrPlayState = BluetoothAvrcpController.PLAY_STATUS_STOPPED;

    BluetoothAvrcpController mService;
    BluetoothDevice device;
    AvrcpCommandCallback callback;

    private boolean mListAppSettingAttrs = false, mListAppSettingVals = false,
            mGetAppSettingAttrsText = false, mGetAppSettingValsText = false,
            mGetCurrentAppAttrVals = false, mIsInitCompleted = false;

    // Private Wrapper class to store supported and current values of all
    // attributes
    class AvrcAppAttributeWrapper {
        Byte    attributeID;
        String  attributeText;
        int    attributeCharset;
        ByteBuffer supportedVals;
        String[] supportedValsText;
        int[] supportedValsCharset;
        Byte currentVal;
        boolean isAttrPresent;
    };

    class AvrcMetadatawrapper {
        String mediaTitle;
        String mediaAlbum;
        String mediaArtist;
        String mediaGenre;
        long trackDuration;
        int trackId;
        int totalTrack;
    }

    public AvrcpCommandManager(BluetoothAvrcpController controller,
            BluetoothDevice device) {
        this.mService = controller;
        this.device = device;
        initAppSettingAttrs();
        initMetadataAttrs();
    }

    public void registerCallback(AvrcpCommandCallback callback) {
        this.callback = callback;
    }

    public void unregisterCallback() {
        this.callback = null;
    }

    public void clear() {
        this.mService = null;
        mAttrsData.clear();
        this.device = null;
        resetMetadata();
    }

    // Method to initialize all AppSettingAttribute variables in AvrcpCommandManager
    public void initAppSettingAttrs() {

        mListAppSettingAttrs = false;
        mListAppSettingVals = false;
        mGetAppSettingAttrsText = false;
        mGetAppSettingValsText = false;
        mGetCurrentAppAttrVals = false;
        mIsInitCompleted = false;
        Log.d(TAG, "initAppSettingAttrs() size: " + mAttrsData.size());
        for (byte i = 0; i < 4; i++) {
            Log.d(TAG, "initAppSettingAttrs() >>> " + i);
            AvrcAppAttributeWrapper data = new AvrcAppAttributeWrapper();
            data.attributeID = (byte) (i + 1);
            data.isAttrPresent = false;
            mAttrsData.add(i, data);
            Log.d(TAG, "initAppSettingAttrs() size: " + mAttrsData.size());
        }
    }

    // Method to initialize all metadata variables in AvrcpCommandManager
    public void initMetadataAttrs() {
        mMetadata = new AvrcMetadatawrapper();
        mMetadata.mediaTitle = "";
        mMetadata.mediaAlbum = "";
        mMetadata.mediaArtist = "";
        mMetadata.mediaGenre = "";
        mMetadata.totalTrack = -1;
        mMetadata.trackId = -1;
        mMetadata.trackDuration = -1;

    }

    protected Handler mInitTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage : " + msg);
            switch (msg.what) {
                case INIT_TIMEOUT_EVT:
                    Log.d(TAG, "Failed to complete init");
                    processCmds(CMD_RESET_APP_SETTING_ATTR);
            }
        }
    };

    // Method to invoke calling into BluetoothAvrcpController API class
    public void processCmds(int cmd) {
        switch (cmd) {
        case CMD_LOAD_APP_SETTING_ATTR:
            if (mIsInitCompleted) {
                Log.d(TAG, "Init already completed. Hence return");
                return;
            }
            Log.d(TAG, "CMD_LOAD_APP_SETTING_ATTR ::: mListAppSettingAttrs:"
                    + mListAppSettingAttrs + ", mListAppSettingVals:"
                    + mListAppSettingVals + ", mGetAppSettingAttrsText:"
                    + mGetAppSettingAttrsText + ", mGetAppSettingValsText:"
                    + mGetAppSettingValsText + ", mGetCurrentAppAttrVals:"
                    + mGetCurrentAppAttrVals + ", mIsInitCompleted :"
                    + mIsInitCompleted);
            if (!mListAppSettingAttrs) {
                synchronized(LOCK) {
                    // Reset Cmd Count to 0 to recover from previous errors
                    mCmdCount = 0;
                }
                if (mService != null)
                    mService.listPlayerApplicationSettingAttributes(device);
                mInitTimeoutHandler.sendEmptyMessageDelayed(INIT_TIMEOUT_EVT, 10000); // 10 sec
            } else if (!mListAppSettingVals) {
                listApplSettingValsForAllAttrs();
            } else if (!mGetAppSettingAttrsText) {
                getAlltAttrVals(false);
            } else if (!mGetAppSettingValsText) {
                loadAllAvrcpAttrValsText();
            } else if (!mGetCurrentAppAttrVals) {
                getAlltAttrVals(true);
            } else {
                mIsInitCompleted = true;
                Log.d(TAG, "Completed all cmds for init. Send callback now..");
                mInitTimeoutHandler.removeMessages(INIT_TIMEOUT_EVT);
                callback.onInitCompleted();
            }
            break;

        case CMD_LOAD_METADATA:
            if (mService != null)
                mService.getElementAttributes(device, new int[] {
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_TITLE,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_ARTIST,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_ALBUM,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_TRACK_NUM,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_NUM_TRACKS,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_GENRE,
                        (int) BluetoothAvrcpController.MEDIA_ATTRIBUTE_PLAYING_TIME });
            break;
        case CMD_RESET_APP_SETTING_ATTR:
            Log.d(TAG, "Stop loading of App attributes.");
            mListAppSettingAttrs = mListAppSettingVals = mGetAppSettingAttrsText = true;
            mGetAppSettingValsText = mGetCurrentAppAttrVals = mIsInitCompleted = true;
            if(callback != null) callback.onLoadAttributesFailure();
            mIsInitCompleted = true;
            synchronized(LOCK) {
                mCmdCount = 0;
            }
            mInitTimeoutHandler.removeMessages(INIT_TIMEOUT_EVT);
            break;
        }

    }

    public byte getRepeatState() {
        return getCurrentAttrVal(BluetoothAvrcpController.ATTRIBUTE_REPEAT);
    }

    public byte getShuffleState() {
        return getCurrentAttrVal(BluetoothAvrcpController.ATTRIBUTE_SHUFFLE);
    }

    public long getPlayState() {
        return mCurrPlayState;
    }

    public byte getCurrentAttrVal(byte attr) {
        if(mAttrsData == null || mAttrsData.size() == 0)
            return 0;
        if(mAttrsData.get((int) (attr - 1)) == null ||
            !mAttrsData.get((int) (attr - 1)).isAttrPresent)
            return 0;
        Byte data = mAttrsData.get((int) (attr - 1)).currentVal;
        return (data == null)?0:data.byteValue();
    }

    public String getMediaTitle() {
        return mMetadata.mediaTitle;
    }

    public String getMediaAlbum() {
        return mMetadata.mediaAlbum;
    }

    public String getMediaArtist() {
        return mMetadata.mediaArtist;
    }

    public String getMediaGenre() {
        return (mMetadata.mediaGenre==null)?"-":mMetadata.mediaGenre;
    }

    public int getMediaTrackNumber() {
        return mMetadata.trackId;
    }

    public int getMediaTotalTracks() {
        return mMetadata.totalTrack;
    }

    public long getMediaDuration() {
        return mMetadata.trackDuration;
    }

    // Method to retrieve all supported Attributes
    public byte[] getSupportedAttrs() {
        ByteBuffer attrs = ByteBuffer.allocate(4);
        int count=0;
        for (AvrcAppAttributeWrapper data : mAttrsData) {
            if (data.isAttrPresent && data.supportedVals != null) {
                attrs.put(data.attributeID.byteValue());
                count++;
            }
        }
        if (!attrs.hasArray()) {
            Log.e(TAG, "No supported Attrs found!");
            return new byte[0];
        }
        byte[] b = new byte[count];
        attrs.get(b, 0, count);
        Log.d(TAG, "Found array :" + b.length);
        return b;
    }

    // Method to retrieve all supported Attribute values
    public ByteBuffer getSupportedAttrValues(byte attributeId) {
        if ((attributeId - 1) > mAttrsData.size())
            return null;
        AvrcAppAttributeWrapper data = mAttrsData.get(attributeId - 1);
        if (!data.isAttrPresent) {
            Log.d(TAG, "Attribute " + attributeId
                    + " is not supported by remote device");
            return null;
        }
        if (data.supportedVals == null) {
            Log.d(TAG, "Attribute " + attributeId
                    + " has no supported values by remote device");
            return null;
        }
        if(DBG) {
            int size = data.supportedVals.array().length;
            ByteBuffer tmpBuff = data.supportedVals;
            tmpBuff.position(0);
            Log.d(TAG, "getSupportedAttrValues() Orig size :" + size);
            for (int i = 0; i < size; i++) {
                Log.d(TAG, i+" :::: " + tmpBuff.get());
            }
        }
        return data.supportedVals;
    }

    private void loadAllAvrcpAttrValsText() {
        for (AvrcAppAttributeWrapper data : mAttrsData) {
            if (data.isAttrPresent && data.supportedVals != null) {
                if (mService != null)
                    mService.getPlayerApplicationSettingValueText(device,
                            data.attributeID, data.supportedVals.array());
            }
        }

    }

    private void getAlltAttrVals(boolean getCurrentVals) {
        ByteBuffer attrs = ByteBuffer.allocate(4);
        int count=0;
        Log.d(TAG, "getAlltAttrVals() getCurrentVals?" + getCurrentVals);
        for (AvrcAppAttributeWrapper data : mAttrsData) {
            if (data.isAttrPresent) {
                attrs.put(count, data.attributeID.byteValue());
                count++;
            }
        }
        byte[] b = new byte[count];
        attrs.get(b, 0, count);
        Log.d(TAG, "Array formed :" + b.length);
        if (mService != null && attrs != null) {
            if (getCurrentVals)
                mService.getCurrentPlayerApplicationSettingValue(device, b);
            else
                mService.getPlayerApplicationSettingAttributeText(device, b);
        }
    }

    private void listApplSettingValsForAllAttrs() {
        // TODO Auto-generated method stub
        for (AvrcAppAttributeWrapper data : mAttrsData) {
            if (data.isAttrPresent) {
                if (mService != null)
                    mService.listPlayerApplicationSettingValues(device,
                            data.attributeID);
                synchronized(LOCK) {
                    mCmdCount++;
                }
            }
        }
    }

    private void resetMetadata() {
        mMetadata.mediaAlbum = mMetadata.mediaArtist = mMetadata.mediaTitle = null;
        mMetadata.mediaGenre = null;
        mMetadata.totalTrack = mMetadata.trackId = -1;
        mMetadata.trackDuration = -1;
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice target, int newState,
            boolean success) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onListPlayerApplicationSettingAttributesRsp(
            BluetoothDevice target, byte[] attributeId, int status) {

        boolean isAllAttributesInvalid = true;
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load ListPlayerApplicationSettingAttributes");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        for (int i = 0; i < attributeId.length; i++) {
            Log.d(TAG, "onListPlayerApplicationSettingAttributesRsp() >> "
                    + attributeId[i]);
            if(!isValidAttribute(attributeId[i])) {
                Log.d(TAG, "onListPlayerApplicationSettingAttributesRsp() Invalid Attribute:"+
                    attributeId[i]+". Skipping..");
                continue;
            }
            // Update mAttrsData
            AvrcAppAttributeWrapper data = mAttrsData
                    .get((int) attributeId[i] - 1);
            data.isAttrPresent = true;
            mAttrsData.set((int) attributeId[i] - 1, data);
            isAllAttributesInvalid = false;
        }
        if(isAllAttributesInvalid) {
            Log.d(TAG, "All attributes are invalid. Skip loading of App Attributes now.");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        if(!mIsInitCompleted) {
            mListAppSettingAttrs = true;
            processCmds(CMD_LOAD_APP_SETTING_ATTR);
        }
    }

    @Override
    public void onListPlayerApplicationSettingValuesRsp(BluetoothDevice target,
            byte attribute, byte[] valueId, int status) {
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load ListPlayerApplicationSettingValues");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(valueId);
        for (int i = 0; i < valueId.length; i++)
            Log.d(TAG, "listAppSettingVals attr:" + attribute + " --> "
                    + valueId[i]);
        if(!isValidAttribute(attribute)) {
            Log.d(TAG, "onListPlayerApplicationSettingValuesRsp() Invalid Attribute:"+
                attribute+". Skipping..");
        } else {
            AvrcAppAttributeWrapper data = mAttrsData.get((int) attribute - 1);
            data.supportedVals = buffer;
            mAttrsData.set((int) attribute - 1, data);
        }
        synchronized(LOCK) {
            mCmdCount--;
            if(!mIsInitCompleted && mCmdCount ==0) {
                mListAppSettingVals = true;
                processCmds(CMD_LOAD_APP_SETTING_ATTR);
            }
            else
                Log.d(TAG, "onListPlayerApplicationSettingValuesRsp() mCmdCount:"+mCmdCount);
        }
    }

    @Override
    public void onGetCurrentPlayerApplicationSettingValueRsp(
            BluetoothDevice target, byte[] attributeId, byte[] valueId,
            int status) {
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load GetCurrentPlayerApplicationSettingValue");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        dumpAttrList();
        for (int i = 0; i < valueId.length; i++) {
            Log.d(TAG, "onGetCurrentPlayerApplicationSettingValueRsp() i: " + i
                    + ", attr : " + attributeId[i] + ", val >>> " + valueId[i]);
            AvrcAppAttributeWrapper data = mAttrsData.get(attributeId[i] - 1);
            if (!data.isAttrPresent)
                continue;
            data.currentVal = valueId[i];
            mAttrsData.set(attributeId[i] - 1, data);
        }
        if (mIsInitCompleted)
            callback.onLoadAppAttrsLoaded();
        else {
            mGetCurrentAppAttrVals = true;
            processCmds(CMD_LOAD_APP_SETTING_ATTR);
        }
    }

    @Override
    public void onGetPlayerApplicationSettingAttributeTextRsp(
            BluetoothDevice target, byte[] attributeId,
            String[] attributeText, int status) {
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load GetPlayerApplicationSettingAttributeText");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        Log.d(TAG, "onGetPlayerApplicationSettingAttributeTextRsp() attr size:"
                + attributeId.length + ", size:" + mAttrsData.size());
        try {
            for (int i = 0; i < attributeId.length; i++) {
                Log.d(TAG, "getAppAttrText i:"+i+", attr:"+attributeId[i]+" --> "+attributeText[i]);
                if (mAttrsData.isEmpty() || mAttrsData.size() < (int) attributeId[i])
                    return;
                AvrcAppAttributeWrapper data = mAttrsData.get(attributeId[i] - 1);
                data.attributeText= attributeText[i];
                mAttrsData.set((int) (attributeId[i] - 1), data);
            }
        } catch(Exception ee) {
            Log.e(TAG, "Error in onGetPlayerApplicationSettingAttributeTextRsp()", ee);
        }
        if(!mIsInitCompleted) {
            mGetAppSettingAttrsText = true;
            processCmds(CMD_LOAD_APP_SETTING_ATTR);
        }
        Log.d(TAG, "onGetPlayerApplicationSettingAttributeText() exit");
    }

    @Override
    public void onGetPlayerApplicationSettingValueTextRsp(
            BluetoothDevice target, byte attributeId, byte[] valueId,
            String[] valueText, int status) {
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load GetPlayerApplicationSettingValueText");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }
        Log.d(TAG, "onGetPlayerApplicationSettingValueTextRsp() attr:"
                + attributeId + ", size:" + mAttrsData.size());
        for (int i = 0; i < valueId.length; i++)
            Log.d(TAG, "getAppAttrValText attr:" + attributeId + " --> "
                    + valueId[i] + " = " + valueText[i]);
        if (mAttrsData.isEmpty() || mAttrsData.size() < (int) attributeId)
            return;
        if(!isValidAttribute(attributeId)) {
            Log.d(TAG, "onGetPlayerApplicationSettingValueTextRsp() Invalid Attribute:"+
                attributeId+". Skipping..");
        } else {
            AvrcAppAttributeWrapper data = mAttrsData.get((int)(attributeId - 1));
            data.supportedValsText = valueText;
            mAttrsData.set((int)(attributeId - 1), data);
        }
        if(!mIsInitCompleted) {
            mGetAppSettingValsText = true;
            processCmds(CMD_LOAD_APP_SETTING_ATTR);
        }

    }

    @Override
    public void onGetElementAttributesRsp(BluetoothDevice target,
            int[] attributeId, String[] valueText,
            int status) {
        Log.d(TAG, "onGetElementAttributesRsp() status: " + status);
        if(!BluetoothAvrcpController.isSuccess(status)) {
            Log.d(TAG, "Fail to load GetElementAttributes");
            if(!mIsInitCompleted) processCmds(CMD_RESET_APP_SETTING_ATTR);
            return;
        }

        for (int i = 0; i < attributeId.length; i++) {
            Log.d(TAG, "Attr: " + attributeId[i] + ", value:" + valueText[i]);
            if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_TITLE)
                mMetadata.mediaTitle = valueText[i];
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_ARTIST)
                mMetadata.mediaArtist = valueText[i];
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_ALBUM)
                mMetadata.mediaAlbum = valueText[i];
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_GENRE)
                mMetadata.mediaGenre = valueText[i];
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_TRACK_NUM) {
                if (valueText[i] != null && valueText[i].length() > 0)
                    mMetadata.trackId = Integer.parseInt(valueText[i]);
                else
                    mMetadata.trackId = 0;
            }
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_NUM_TRACKS) {
                if (valueText[i] != null && valueText[i].length() > 0)
                    mMetadata.totalTrack = Integer.parseInt(valueText[i]);
                else
                    mMetadata.totalTrack = 0;
            }
            else if (attributeId[i] == BluetoothAvrcpController.MEDIA_ATTRIBUTE_PLAYING_TIME) {
                if (valueText[i] != null && valueText[i].length() > 0)
                    mMetadata.trackDuration = Long.parseLong(valueText[i]);
                else
                    mMetadata.trackDuration = 0;
            }
            else
                Log.e(TAG, "Unhandled Attribute ID : " + attributeId[i]);
        }
        callback.onLoadMetataCompleted();
    }

    @Override
    public void onGetPlayStatusRsp(BluetoothDevice target, int songLength,
            int songPosition, byte playStatus, int status) {
        Log.d(TAG, "onGetPlayStatusRsp()");
        mMetadata.trackDuration = (long)songLength;

    }

    @Override
    public void onPlaybackStatusChanged(BluetoothDevice target, byte playStatus) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTrackChanged(BluetoothDevice target, long trackId) {
        // TODO Auto-generated method stub

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
    }

    @Override
    public void onPlayerAppSettingChanged(BluetoothDevice target,
            byte[] attribute, byte[] value) {
        if(DBG)
            Log.d(TAG, "onPlayerAppSettingChanged() attr.len : " + attribute.length +
                " , value.len:"+value.length);
        for (int i = 0; i < attribute.length; i++) {
            if(DBG) Log.d(TAG, "onPlayerAppSettingChanged() -> ["+i+"] attr : " +
                attribute[i] +" , value :"+value[i]);
            for (AvrcAppAttributeWrapper data : mAttrsData) {
                if (data.attributeID.byteValue() == attribute[i] &&
                    isValidAttribute(attribute[i])) {
                    data.currentVal = value[i];
                    mAttrsData.set((int) attribute[i] - 1, data);
                    break;
                }
            }
        }

    }

    public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                    String[] folderPath, int status){}

    public void onChangePathRsp(BluetoothDevice target, byte direction, int numberOfItems,
                    int status){}

    public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                    BluetoothAvrcpBrowseItem[] items, int status){}

    public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                    String[] valueTexts, int status){}

    public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status){}

    public void onPlayItemRsp(BluetoothDevice target, int status){}

    public void onAddToNowPlayingRsp(BluetoothDevice target, int status){}

    public void onSetAbsoluteVolumeRsp(BluetoothDevice target, byte volume, int status){}

    public void onAddressedPlayerChanged(BluetoothDevice target, int playerId){}

    public void onAvailablePlayersChanged(BluetoothDevice target){}

    public void onNowPlayingContentChanged(BluetoothDevice target){}

    public void onUIDsChanged(BluetoothDevice target){}

    public void onVolumeChanged(BluetoothDevice target, byte volume){}

    private boolean isValidAttribute(byte attribute) {
        if(attribute == BluetoothAvrcpController.ATTRIBUTE_REPEAT ||
            attribute == BluetoothAvrcpController.ATTRIBUTE_SHUFFLE ||
            attribute == BluetoothAvrcpController.ATTRIBUTE_EQUALIZER ||
            attribute == BluetoothAvrcpController.ATTRIBUTE_SCAN)
            return true;
        return false;
    }

    private void dumpAttrList() {
        for (AvrcAppAttributeWrapper data : mAttrsData) {
            Log.d(TAG, "@@@ attr : " + data.attributeID + " , isPresent:"
                    + data.isAttrPresent);
            if (data.isAttrPresent) {
                Log.d(TAG, "@@@ value:" + data.currentVal);
            }
        }
    }
}
