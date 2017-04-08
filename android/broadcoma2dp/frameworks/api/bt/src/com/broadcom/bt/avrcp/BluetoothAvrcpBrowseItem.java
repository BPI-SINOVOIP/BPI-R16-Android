/******************************************************************************
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/


package com.broadcom.bt.avrcp;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class is used to create parcelable objects to store AVRCP Browsable item information.
 * This is used by the callback
 * {@link IBluetoothAvrcpControllerEventHandler#onGetFolderItemsRsp}
 */
public class BluetoothAvrcpBrowseItem implements Parcelable {
    /** Browsable item types */
    public static final byte ITEM_TYPE_MEDIA_PLAYER = 1;
    public static final byte ITEM_TYPE_FOLDER = 2;
    public static final byte ITEM_TYPE_MEDIA_ELEMENT = 3;

    /** Media player types */
    public static final byte PLAYER_MAJOR_TYPE_AUDIO = 1;
    public static final byte PLAYER_MAJOR_TYPE_VIDEO = 2;
    public static final byte PLAYER_MAJOR_TYPE_BROADCAST_AUDIO = 4;
    public static final byte PLAYER_MAJOR_TYPE_BROADCAST_VIDEO = 8;

    public static final int PLAYER_SUB_TYPE_AUDIO_BOOK = 1;
    public static final int PLAYER_SUB_TYPE_PODCAST = 2;

    /** Player feature masks */
    /* Pass through commands support */
    public static final int  FEATURE_PLAY_INDEX = 5;
    public static final byte FEATURE_PLAY_MASK = 0x01;
    public static final int  FEATURE_STOP_INDEX = 5;
    public static final byte FEATURE_STOP_MASK = 0x02;
    public static final int  FEATURE_PAUSE_INDEX = 5;
    public static final byte FEATURE_PAUSE_MASK = 0x04;
    public static final int  FEATURE_RECORD_INDEX = 5;
    public static final byte FEATURE_RECORD_MASK = 0x08;
    public static final int  FEATURE_REWIND_INDEX = 5;
    public static final byte FEATURE_REWIND_MASK = 0x10;
    public static final int  FEATURE_FASTFORWARD_INDEX = 5;
    public static final byte FEATURE_FASTFORWARD_MASK = 0x20;
    public static final int  FEATURE_FORWARD_INDEX = 5;
    public static final byte FEATURE_FORWARD_MASK = (byte)(0x80 & 0xFF);
    public static final int  FEATURE_BACKWARD_INDEX = 6;
    public static final byte FEATURE_BACKWARD_MASK = 0x01;

    /* AVRCP 1.4 features */
    public static final int  FEATURE_ADVANCED_CONTROL_INDEX = 7;
    public static final byte FEATURE_ADVANCED_CONTROL_MASK = 0x04;
    public static final int  FEATURE_BROWSING_INDEX = 7;
    public static final byte FEATURE_BROWSING_MASK = 0x08;
    public static final int  FEATURE_SEARCHING_INDEX = 7;
    public static final byte FEATURE_SEARCHING_MASK = 0x10;
    public static final int  FEATURE_ADDTONOWPLAYING_INDEX = 7;
    public static final byte FEATURE_ADDTONOWPLAYING_MASK = 0x20;
    public static final int  FEATURE_UNIQUE_UIDS_INDEX = 7;
    public static final byte FEATURE_UNIQUE_UIDS_MASK = 0x40;
    public static final int  FEATURE_ONLYBROWSABLEWHENADDRESSED_INDEX = 7;
    public static final byte FEATURE_ONLYBROWSABLEWHENADDRESSED_MASK = (byte)(0x80 & 0xFF);
    public static final int  FEATURE_ONLYSEARCHABLEWHENADDRESSED_INDEX = 8;
    public static final byte FEATURE_ONLYSEARCHABLEWHENADDRESSED_MASK = 0x01;
    public static final int  FEATURE_NOWPLAYING_INDEX = 8;
    public static final byte FEATURE_NOWPLAYING_MASK = 0x02;
    public static final int  FEATURE_UIDPERSISTENCY_INDEX = 8;
    public static final byte FEATURE_UIDPERSISTENCY_MASK = 0x04;

    /** Folder types */
    public static final byte FOLDER_TYPE_MIXED = 0;
    public static final byte FOLDER_TYPE_TITLES = 1;
    public static final byte FOLDER_TYPE_ALBUMS = 2;
    public static final byte FOLDER_TYPE_ARTISTS = 3;
    public static final byte FOLDER_TYPE_GENRES = 4;
    public static final byte FOLDER_TYPE_PLAYLISTS = 5;
    public static final byte FOLDER_TYPE_YEARS = 6;

    /** Media types */
    public static final byte MEDIA_TYPE_AUDIO = 0;
    public static final byte MEDIA_TYPE_VIDEO = 1;

    /**
    * Members
    */

    /** Item type */
    public byte mItemType;

    /** Media player item specific members */
    public int mPlayerId;
    public byte mPlayerMajorType;
    public int mPlayerSubType;
    public byte mPlayStatus;
    public byte[] mPlayerFeatures = new byte[16];

    /** Folder item specific members */
    public long mFolderUid;
    public byte mFolderType;
    public boolean mIsPlayable;

    /** Media element item specific members */
    public long mElementUid;
    public byte mMediaType;
    public int[] mAttributes;
    public String[] mAttributeValues;

    /** Common members */
    public String mDisplayableName;

    /**
     * @hide
     */
    public BluetoothAvrcpBrowseItem() {
    }

    /**
     * @hide
     */
    public BluetoothAvrcpBrowseItem(Parcel source) {
        mItemType = source.readByte();
        if (mItemType == ITEM_TYPE_MEDIA_PLAYER) {
            mPlayerId = source.readInt();
            mPlayerMajorType = source.readByte();
            mPlayerSubType = source.readInt();
            mPlayStatus = source.readByte();
            source.readByteArray(mPlayerFeatures);
        } else if (mItemType == ITEM_TYPE_FOLDER) {
            mFolderUid = source.readLong();
            mFolderType = source.readByte();
            mIsPlayable = (source.readByte() == 1) ? true : false;
        } else if (mItemType == ITEM_TYPE_MEDIA_ELEMENT) {
            mElementUid = source.readLong();
            mMediaType = source.readByte();
            mAttributes = source.createIntArray();
            mAttributeValues = source.createStringArray();
        }
        mDisplayableName = source.readString();
    }

    /**
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(mItemType);
        if (mItemType == ITEM_TYPE_MEDIA_PLAYER) {
            dest.writeInt(mPlayerId);
            dest.writeByte(mPlayerMajorType);
            dest.writeInt(mPlayerSubType);
            dest.writeByte(mPlayStatus);
            dest.writeByteArray(mPlayerFeatures);
        } else if (mItemType == ITEM_TYPE_FOLDER) {
            dest.writeLong(mFolderUid);
            dest.writeByte(mFolderType);
            dest.writeByte(mIsPlayable ? (byte)1 : (byte)0);
        } else if (mItemType == ITEM_TYPE_MEDIA_ELEMENT) {
            dest.writeLong(mElementUid);
            dest.writeByte(mMediaType);
            dest.writeIntArray(mAttributes);
            dest.writeStringArray(mAttributeValues);
        }
        dest.writeString(mDisplayableName);
    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothAvrcpBrowseItem> CREATOR =
        new Parcelable.Creator<BluetoothAvrcpBrowseItem>() {

        public BluetoothAvrcpBrowseItem createFromParcel(Parcel source) {

            return new BluetoothAvrcpBrowseItem(source);
        }

        public BluetoothAvrcpBrowseItem[] newArray(int size) {

            return new BluetoothAvrcpBrowseItem[size];
        }
    };

    /**
    * Public methods
    */
    public boolean isPlaySupported() {
        return (mPlayerFeatures[FEATURE_PLAY_INDEX] & FEATURE_PLAY_MASK) != 0 ? true : false;
    }

    public boolean isStopSupported() {
        return (mPlayerFeatures[FEATURE_STOP_INDEX] & FEATURE_STOP_MASK) != 0 ? true : false;
    }

    public boolean isPauseSupported() {
        return (mPlayerFeatures[FEATURE_PAUSE_INDEX] & FEATURE_PAUSE_MASK) != 0 ? true : false;
    }

    public boolean isRecordSupported() {
        return (mPlayerFeatures[FEATURE_RECORD_INDEX] & FEATURE_RECORD_MASK) != 0 ? true : false;
    }

    public boolean isRewindSupported() {
        return (mPlayerFeatures[FEATURE_REWIND_INDEX] & FEATURE_REWIND_MASK) != 0 ? true : false;
    }

    public boolean isFastForwardSupported() {
        return (mPlayerFeatures[FEATURE_FASTFORWARD_INDEX] & FEATURE_FASTFORWARD_MASK) != 0 ?
                true : false;
    }

    public boolean isForwardSupported() {
        return (mPlayerFeatures[FEATURE_FORWARD_INDEX] & FEATURE_FORWARD_MASK) != 0 ? true : false;
    }

    public boolean isBackwardSupported() {
        return (mPlayerFeatures[FEATURE_BACKWARD_INDEX] & FEATURE_BACKWARD_MASK) != 0 ?
                true : false;
    }

    public boolean isAdvancedControlSupported() {
        return (mPlayerFeatures[FEATURE_ADVANCED_CONTROL_INDEX] &
                FEATURE_ADVANCED_CONTROL_MASK) != 0 ? true : false;
    }

    public boolean isBrowsingSupported() {
        return (mPlayerFeatures[FEATURE_BROWSING_INDEX] & FEATURE_BROWSING_MASK) != 0 ?
                true : false;
    }

    public boolean isSearchingSupported() {
        return (mPlayerFeatures[FEATURE_SEARCHING_INDEX] & FEATURE_SEARCHING_MASK) != 0 ?
                true : false;
    }

    public boolean isAddToNowPlayingSupported() {
        return (mPlayerFeatures[FEATURE_ADDTONOWPLAYING_INDEX] &
                FEATURE_ADDTONOWPLAYING_MASK) != 0 ? true : false;
    }

    public boolean isUniqueUIDsSupported() {
        return (mPlayerFeatures[FEATURE_UNIQUE_UIDS_INDEX] & FEATURE_UNIQUE_UIDS_MASK) != 0 ?
                true : false;
    }

    public boolean isOnlyBrowsableWhenAddressed() {
        return (mPlayerFeatures[FEATURE_ONLYBROWSABLEWHENADDRESSED_INDEX] &
                FEATURE_ONLYBROWSABLEWHENADDRESSED_MASK) != 0 ? true : false;
    }

    public boolean isOnlySearchableWhenAddressed() {
        return (mPlayerFeatures[FEATURE_ONLYSEARCHABLEWHENADDRESSED_INDEX] &
                FEATURE_ONLYSEARCHABLEWHENADDRESSED_MASK) != 0 ? true : false;
    }

    public boolean isNowPlayingSupported() {
        return (mPlayerFeatures[FEATURE_NOWPLAYING_INDEX] & FEATURE_NOWPLAYING_MASK) != 0 ?
                true : false;
    }

    public boolean isUIDPersistencySupported() {
        return (mPlayerFeatures[FEATURE_UIDPERSISTENCY_INDEX] & FEATURE_UIDPERSISTENCY_MASK) != 0 ?
                true : false;
    }

}
