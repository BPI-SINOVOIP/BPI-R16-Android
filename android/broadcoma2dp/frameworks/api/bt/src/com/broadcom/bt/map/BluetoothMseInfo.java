/*******************************************************************************
 *
 *  Copyright (C) 2012 Broadcom Corporation
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
 * to store MSE instance information. This is used by the callback
 * {@link IBluetoothMapClientEventHandler#onGetMseInstancesResult(BluetoothDevice server,
 *  BluetoothMseInfo[] mseInfo)}.
 */
public class BluetoothMseInfo implements Parcelable {

    /**
     * MSE Instance name
     */
    public String mName = "";
    /**
     * MSE Instance ID
     */
    public int mServerInstanceId = -1;
    /**
     * MAP message type
     */
    public int mMessageTypes = -1;

    /**
     * @hide
     */
    public BluetoothMseInfo() {
    }

    /**
     * @hide
     */
    public BluetoothMseInfo(Parcel source) {
        mName = source.readString();
        mServerInstanceId = source.readInt();
        mMessageTypes = source.readInt();
    }

    /**
     * @hide
     */
    public BluetoothMseInfo(String name, int serverInstanceId, int messageTypes) {
        mName = name;
        mServerInstanceId = serverInstanceId;
        mMessageTypes = messageTypes;
    }

    /**
     * Check if selected MSE instance supports SMS_GSM.
     *
     * @return true if selected MSE instance supports SMS_GSM,
     *         false otherwise.
     */
    public boolean supportsSmsGsmMessages() {
        return (BluetoothMessageInfo.MSG_TYPE_SMS_GSM & mMessageTypes) > 0;
    }

    /**
     * Check if selected MSE instance supports SMS_CDMA.
     *
     * @return true if selected MSE instance supports SMS_CDMA,
     *         false otherwise.
     */
    public boolean supportsSmsCdmaMessages() {
        return (BluetoothMessageInfo.MSG_TYPE_SMS_CDMA & mMessageTypes) > 0;
    }

    /**
     * Check if selected MSE instance supports SMS_CDMA or SMS_GSM.
     *
     * @return true if selected MSE instance supports SMS_CDMA or SMS_GSM,
     *         false otherwise.
     */
    public boolean supportsSmsMessages() {
        return supportsSmsGsmMessages() || supportsSmsCdmaMessages();
    }

    /**
     * Check if selected MSE instance supports MMS.
     *
     * @return true if selected MSE instance supports MMS,
     *         false otherwise.
     */
    public boolean supportsMmsMessages() {
        return (BluetoothMessageInfo.MSG_TYPE_MMS & mMessageTypes) > 0;
    }

    /**
     * Check if selected MSE instance supports Email.
     *
     * @return true if selected MSE instance supports Email,
     *         false otherwise.
     */
    public boolean supportsEmailMessages() {
        return (BluetoothMessageInfo.MSG_TYPE_EMAIL & mMessageTypes) > 0;
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
        dest.writeString(mName);
        dest.writeInt(mServerInstanceId);
        dest.writeInt(mMessageTypes);
    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothMseInfo> CREATOR = new Parcelable.Creator<BluetoothMseInfo>() {

        public BluetoothMseInfo createFromParcel(Parcel source) {
            return new BluetoothMseInfo(source);
        }

        public BluetoothMseInfo[] newArray(int size) {
            return new BluetoothMseInfo[size];
        }
    };

    /**
     * @hide
     */
    public void dumpState(StringBuilder builder, String prefix) {
        builder.append(prefix);
        builder.append("Name    :");
        builder.append(mName == null ? "" : mName);

        builder.append("\n");
        builder.append(prefix);
        builder.append("InstanceId    : ");
        builder.append(mServerInstanceId);

        builder.append("\n");
        builder.append(prefix);
        builder.append("Message Types : ");
        builder.append(mMessageTypes);

        builder.append("\n");
    }
}
