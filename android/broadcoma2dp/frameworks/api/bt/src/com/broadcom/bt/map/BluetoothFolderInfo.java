/*******************************************************************************
 *
 *  Copyright (C) 2012-2013 Broadcom Corporation
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

package com.broadcom.bt.map;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class is used to create parcelable objects
 * that hold MAP message folder information.
 */
public class BluetoothFolderInfo implements Parcelable {
    // Constants for folder types
    /**
     * Virtual folder name for INBOX folder
     */
    public static final String VIRTUAL_FOLDER_INBOX="inbox";
    /**
     * Virtual folder name for OUTBOX folder
     */
    public static final String VIRTUAL_FOLDER_OUTBOX="outbox";
    /**
     * Virtual folder name for SENT folder
     */
    public static final String VIRTUAL_FOLDER_SENT="sent";
    /**
     * Virtual folder name for DELETED folder
     */
    public static final String VIRTUAL_FOLDER_DELETED="deleted";
    /**
     * Virtual folder name for DRAFT folder
     */
    public static final String VIRTUAL_FOLDER_DRAFT="draft";

    /**
     * Constant for Read only mode for MAP folders
     */
    public static final long MODE_READ_ONLY = 1;

    /**
     * FolderId for MAP folder
     */
    private String mFolderId;
    /**
     * Virtual name for MAP folder
     */
    public String mVirtualName = "";
    /**
     * Folder path for MAP folder
     */
    public String mFolderName = "";
    /**
     * DateTime of folder creation
     */
    public String mCreatedDateTimeMS = "";
    /**
     * DateTime of last folder modification
     */
    public String mModifiedDateTimeMS = "";
    /**
     * DateTime of last folder access
     */
    public String mAccessedDateTimeMS = "";

    /**
     * Size of MAP folder in bytes
     */
    public long mFolderSizeBytes = 0;

    /**
     * Mode of MAP folder - 0 for READ and 1 for WRITE
     */
    public long mMode = 0;

    /**
     * @hide
     */
    public boolean isReadOnly() {
        return false;
    }

    //
    // Define all final static types here
    //
    /**
     * @hide
     */
    public BluetoothFolderInfo(Parcel source) {
        mFolderName = source.readString();
        mFolderSizeBytes = source.readLong();
        mCreatedDateTimeMS = source.readString();
        mMode = source.readLong();
    }

    /**
     * Create BluetoothFolderInfo object
     *
     * @param folderId Unique ID for each folder
     * @param virtualName Virtual name of the folder
     * @param folderName Name of the folder
     * @param folderSizeBytes Size of folder in bytes
     * @param createdDateTimeMS DateTime when folder was created
     * @param mode Mode of folder.
     *        0 Read only
     *        1 Write.
     */
    public BluetoothFolderInfo(String folderId, String virtualName, String folderName, long folderSizeBytes,
            String createdDateTimeMS, long mode) {
        mFolderId = folderId;
        mVirtualName = virtualName;
        mFolderName = folderName;
        mFolderSizeBytes = folderSizeBytes;
        mCreatedDateTimeMS = createdDateTimeMS;
        mMode = mode;

    }

    /**
     * @hide
     */
    public BluetoothFolderInfo() {
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
        dest.writeString(mFolderName);
        dest.writeLong(mFolderSizeBytes);
        dest.writeString(mCreatedDateTimeMS);
        dest.writeLong(mMode);
    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothFolderInfo> CREATOR = new Parcelable.Creator<BluetoothFolderInfo>() {

        public BluetoothFolderInfo createFromParcel(Parcel source) {
            return new BluetoothFolderInfo(source);
        }

        public BluetoothFolderInfo[] newArray(int size) {
            return new BluetoothFolderInfo[size];
        }
    };

    /**
     * @hide
     */
    public void dumpState(StringBuilder builder, String prefix) {
        builder.append(prefix);
        builder.append("Name    :");
        builder.append (mFolderName==null?"":mFolderName);

        builder.append("\n");
        builder.append(prefix);
        builder.append("Size    : ");
        builder.append(mFolderSizeBytes);

        builder.append("\n");
        builder.append(prefix);
        builder.append("Created : ");
        builder.append(mCreatedDateTimeMS);

        builder.append("\n");
        builder.append(prefix);
        builder.append("Mode : ");
        builder.append(mMode);
        builder.append("\n");
    }
}
