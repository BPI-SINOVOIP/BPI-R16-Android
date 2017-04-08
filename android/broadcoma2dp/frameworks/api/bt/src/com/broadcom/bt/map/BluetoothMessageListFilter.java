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
import android.util.Log;

/**
 * This class is used to create parcelable objects
 * to store MAP message list filter information. This class is used
 * to create filter object in the method
 * {@link BluetoothMapClient#getMessageListObject(int, int, int,
            BluetoothMessageListFilter, BluetoothMessageParameterFilter)}
 *
 */
public class BluetoothMessageListFilter implements Parcelable {
    private static final String TAG = "BluetoothMessageListFilter";
    private static final int NOT_SET = -1;

    /**
     * MAP Message filter constant for SMS GSM messages
     */
    public static final byte MAP_FILTER_MSGTYPE_SMS_GSM = 1;
    /**
     * MAP Message filter constant for SMS CDMA messages
     */
    public static final byte MAP_FILTER_MSGTYPE_SMS_CDMA = 2;
    /**
     * MAP Message filter constant for Email messages
     */
    public static final byte MAP_FILTER_MSGTYPE_EMAIL = 4;
    /**
     * MAP Message filter constant for MMS messages
     */
    public static final byte MAP_FILTER_MSGTYPE_MMS = 8;

    /**
     * Maximum number of messages in the returned list.
     * Default value is 1024 if this is not specified.
     */
    public int mMaxListCount = NOT_SET;
    /**
     * Offset of the first entry returned.  If it is set to n, then the first n messages are
     * not included.  Default value is 0 if this is not specified.
     */
    public int mListStartOffset = NOT_SET;

    /**
     * Message type filter.  This is a bit mask of one or more of the following values:
     *            {@link BluetoothMessageListFilter#MAP_FILTER_MSGTYPE_SMS_GSM},
     *            {@link BluetoothMessageListFilter#MAP_FILTER_MSGTYPE_SMS_CDMA},
     *            {@link BluetoothMessageListFilter#MAP_FILTER_MSGTYPE_EMAIL},
     *            {@link BluetoothMessageListFilter#MAP_FILTER_MSGTYPE_MMS},
     */
    public byte mMsgMask;
    /**
     * Maximum string length of "subject" parameter in the returned list.
     * Range from 1 to 255.
     */
    public byte mSubjectLength;
    /**
     * Begin time of time period filter.  Format "YYYYMMDDTHHMMSS".
     */
    public String mPeriodBegin;
    /**
     * End time of time period filter.  Format "YYYYMMDDTHHMMSS".
     */
    public String mPeriodEnd;
    /**
     * @hide
     */
    public byte mReadStatus = 0; //unset is 0
    /**
     * Message recipient filter.  Matching sub-string of name, telephone, or email.
     */
    public String mRecipient;
    /**
     * Message originator filter.  Matching sub-string of name, telephone, or email.
     */
    public String mOriginator;
    /**
     * @hide
     */
    public byte mPriorityStatus = 0; //unset is 0

    /**
     * @hide
     */
    public BluetoothMessageListFilter() {

    }

    /**
     * @hide
     */
    private BluetoothMessageListFilter(Parcel source) {
        mMsgMask = source.readByte();
        mMaxListCount = source.readInt();
        mListStartOffset = source.readInt();
        mSubjectLength = source.readByte();
        mPeriodBegin = source.readString();
        mPeriodEnd = source.readString();
        mReadStatus = source.readByte();
        mRecipient = source.readString();
        mOriginator = source.readString();
        mPriorityStatus = source.readByte();
    }

    /**
     * @hide
     */
    public boolean listStartOffsetSet() {
        return mListStartOffset > NOT_SET;
    }

    /**
     * @hide
     */
    public boolean maxListCountSet() {
        return mMaxListCount > NOT_SET;
    }

    /**
     * @hide
     */
    public boolean originatorFilterSet() {
        return mOriginator != null && mOriginator.length()>0 && !"*".equals(mOriginator);
    }

    /**
     * @hide
     */
    public boolean recipientFilterSet() {
        return mRecipient != null && mRecipient.length()>0 && !"*".equals(mRecipient);
    }

    /**
     * @hide
     */
    public boolean periodBeginFilterSet() {
        return (mPeriodBegin != null) && (mPeriodBegin.length() >= 8);
    }

    /**
     * @hide
     */
    public boolean periodEndFilterSet() {
        return (mPeriodEnd != null) && (mPeriodEnd.length() >= 8);
    }

    /**
     * @hide
     */
    public boolean readStatusFilterSet() {
        return (mReadStatus ==1 || mReadStatus ==2);
    }

    /**
     * @hide
     */
    public boolean priorityStatusFilterSet() {
        return (mPriorityStatus ==1 || mPriorityStatus ==2);
    }

    /**
     * @hide
     */
    public boolean filterIsPriority() {
        return mPriorityStatus == 1;
    }

    /**
     * Set priority filter.
     */
    public void setFilterPriority(boolean isPriority) {
        mPriorityStatus = (isPriority? (byte)1:(byte)2);
    }

    /**
     * @hide
     */
    public boolean filterRead() {
        return mReadStatus == 2;
    }

    /**
     * Set read status filter.
     */
    public void setFilterRead(boolean isRead) {
        mReadStatus = (isRead? (byte)2:(byte)1);
    }

    /**
     * @hide
     */
    public void debugDump() {
        Log.d(TAG, "mMsgMask:" + mMsgMask);
        Log.d(TAG, "mMaxListCount:" + mMaxListCount);
        Log.d(TAG, "mListStartOffset:" + mListStartOffset);
        Log.d(TAG, "mSubjectLength:" + mSubjectLength);
        Log.d(TAG, "mPeriodBegin:" + mPeriodBegin);
        Log.d(TAG, "mPeriodEnd:" + mPeriodEnd);
        Log.d(TAG, "mReadStatus:" + mReadStatus);
        Log.d(TAG, "mRecipient:" + mRecipient);
        Log.d(TAG, "mOriginator:" + mOriginator);
        Log.d(TAG, "mPriorityStatus:" + mPriorityStatus);
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(mMsgMask);
        dest.writeInt(mMaxListCount);
        dest.writeInt(mListStartOffset);
        dest.writeByte(mSubjectLength);
        dest.writeString(mPeriodBegin);
        dest.writeString(mPeriodEnd);
        dest.writeByte(mReadStatus);
        dest.writeString(mRecipient);
        dest.writeString(mOriginator);
        dest.writeByte(mPriorityStatus);
    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothMessageListFilter> CREATOR = new Parcelable.Creator<BluetoothMessageListFilter>() {

        public BluetoothMessageListFilter createFromParcel(Parcel source) {
            return new BluetoothMessageListFilter(source);
        }

        public BluetoothMessageListFilter[] newArray(int size) {
            return new BluetoothMessageListFilter[size];
        }
    };
}
