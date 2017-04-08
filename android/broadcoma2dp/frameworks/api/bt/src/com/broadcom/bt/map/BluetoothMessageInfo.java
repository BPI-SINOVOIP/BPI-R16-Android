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

import java.util.ArrayList;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

/**
 * This class is used to create parcelable objects
 * to store MAP message information. This class is used
 * in the callback {@link
 * IBluetoothMapClientEventHandler#onMessageListResult(BluetoothDevice server,
 *  int serverInstanceId, boolean success, int listLength, boolean hasNewMessages,
 *   BluetoothMessageInfo[] info)}.
 *
 */
public class BluetoothMessageInfo implements Parcelable {
    private static final String TAG = "BtMap.BluetoothMessageInfo";

    // Message types
    /**
     * Message Type for Email
     */
    public static final byte MSG_TYPE_EMAIL = 1;
    /**
     * Message Type for SMS GSM
     */
    public static final byte MSG_TYPE_SMS_GSM = 2;
    /**
     * Message Type for SMS CDMA
     */
    public static final byte MSG_TYPE_SMS_CDMA = 4;
    /**
     * Message Type for SMS MMS
     */
    public static final byte MSG_TYPE_MMS = 8;

    // MAP reception types
    /**
     * MAP reception type for completion state
     */
    public static final byte MSG_RECEPTION_STATUS_COMPLETE = 0;
    /**
     * MAP reception type for fractional completion state
     */
    public static final byte MSG_RECEPTION_STATUS_FRACTION = 1;
    /**
     * MAP reception type for notification
     */
    public static final byte MSG_RECEPTION_STATUS_NOTIFICATION = 2;

    // For IOP with carkits with low resolution screens. Limit subject to 20
    // characters,
    // or if over 20 characters 16 characters + " ..."
    private static final int IOP_MAX_SUBJECT_LENGTH = 20;
    private static final String IOP_SUBJECT_TRAILER = " ...";
    private static final int IOP_SUBJECT_TRAILER_LENGTH = IOP_SUBJECT_TRAILER.length();

    //public long mParameterMask = 0;
    /**
     * MAP message size in bytes
     */
    public int mMsgSize = 0;
    /**
     * MAP message attachement size in bytes
     */
    public int mAttachmentSize = 0;
    /**
     * Check if MAP message is a text message
     */
    public boolean mIsText = false;
    /**
     * Check if MAP message is a high priority message
     */
    public boolean mIsHighPriority = false;
    /**
     * Check if MAP message is read
     */
    public boolean mIsRead = false;
    /**
     * Check if MAP message is sent
     */
    public boolean mIsSent = false;
    /**
     * Check if MAP message is protected
     */
    public boolean mIsProtected = false;
    /**
     * Unique String identifying a MAP message
     */
    public String mMsgHandle = "";
    /**
     * MAP message type.
     * It can have the values {@link #MSG_TYPE_EMAIL},
     * {@link #MSG_TYPE_SMS_GSM}, {@link #MSG_TYPE_SMS_CDMA}, {@link #MSG_TYPE_MMS}
     */
    public byte mMsgType;
    /**
     * MAP message reception state.
     * It can have the values {@link #MSG_RECEPTION_STATUS_COMPLETE},
     *  {@link #MSG_RECEPTION_STATUS_FRACTION}, {@link #MSG_RECEPTION_STATUS_NOTIFICATION}
     */
    public byte mReceptionStatus;
    String mSubject = "";
    /**
     * MAP message timestamp with format "yyyymmddTHHMMSS", or "" if none
     */
    public String mDateTime = "";
    String mReplyToAddress = "";
    /**
     * @hide
     */
    public String mSenderAddress;
    /**
     * @hide
     */
    public BluetoothPersonInfo mSender;
    /**
     * @hide
     */
    public ArrayList<BluetoothPersonInfo> mRecipient = new ArrayList<BluetoothPersonInfo>();
    /**
     * @hide
     */
    public ArrayList<String> mRecipientAddress = new ArrayList<String>();


    /**
     * @hide
     */
    public BluetoothMessageInfo(Parcel source) {
        //mParameterMask = source.readLong();
        mMsgSize =  source.readInt();
        mAttachmentSize =  source.readInt();
        if (source.readByte() != 0) {
            mIsText = true;
        } else {
            mIsText = false;
        }
        if (source.readByte() != 0) {
            mIsHighPriority = true;
        } else {
            mIsHighPriority = false;
        }
        if (source.readByte() != 0) {
            mIsRead = true;
        } else {
            mIsRead = false;
        }
        if (source.readByte() != 0) {
            mIsSent = true;
        } else {
            mIsSent = false;
        }
        if (source.readByte() != 0) {
            mIsProtected = true;
        } else {
            mIsProtected = false;
        }

        mMsgHandle = source.readString();
        mMsgType = source.readByte();
        mReceptionStatus = source.readByte();
        mSubject = source.readString();
        mDateTime = source.readString();

        int count = source.readInt();
        if (count > 0) {
            mSender = new BluetoothPersonInfo(source);
        }
        count = source.readInt();
        if (count > 0) {
            mSenderAddress = source.readString();
        }

        count = source.readInt();
        for (int i = 0; i < count; i++) {
            mRecipient.add(new BluetoothPersonInfo(source));
        }
        count = source.readInt();
        for (int i = 0; i < count; i++) {
            mRecipientAddress.add(source.readString());
        }

        count = source.readInt();
        if (count > 0) {
            mReplyToAddress = source.readString();
        }
    }

    /**
     * @hide
     */
    public BluetoothMessageInfo() {
    }

    /**
     * Set the sender's addressing information.
     *
     * @param address Address of the sender.
     *                In case of SMS, it is sender's phone number.
     *                In case of Email, it is sender's email address.
     *                In case of MMS, it is sender's email address or phone number.
     * @param pInfo Personal information of the sender.
     *
     * @hide
     */
    public void setSender(String address, final BluetoothPersonInfo pInfo) {
        if (address == null) {
            return;
        }
        mSenderAddress = address;
        mSender = pInfo;
        Log.d(TAG, "setSenderNameInfo: address="
                + address
                + (pInfo == null ? "null" : pInfo.mFamilyName + " " + pInfo.mGivenName + " "
                        + pInfo.getDisplayName()));
    }

    /**
     * Get sender's display name in plain text or HTML-encoded text
     *
     * @param htmlEncode
     *            true if display name should be returned in HTML-encoded string
     */
    public String getSenderDisplayName(boolean htmlEncode) {
        if (mSender == null) {
            return "";
        }
        String name = mSender.getDisplayName();
        if (name == null) {
            return "";
        }
        return htmlEncode ? TextUtils.htmlEncode(name) : name;
    }

    /**
     * Get recipient's display name in plain text or HTML-encoded text
     *
     * @param htmlEncode
     *            true if display name should be returned in HTML-encoded string
     */
    public String getRecipientDisplayName(boolean htmlEncode) {
        if (mRecipient == null || mRecipient.size() == 0) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < mRecipient.size(); i++) {
            BluetoothPersonInfo p = mRecipient.get(i);
            if (p != null) {
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(p.getDisplayName());
            }
        }
        return htmlEncode ? TextUtils.htmlEncode(b.toString()) : b.toString();
    }

    /**
     * Set "To" address and personal information
     *
     * @param address
     *            address of the recipient
     * @param pInfo personal information of recipient
     *
     * @hide
     */
    public void setReplyTo(String address, final BluetoothPersonInfo pInfo) {
        if (address == null) {
            return;
        }
        mReplyToAddress = address;
        Log.d(TAG, "setReplyToNameInfo:address= "
                + address
                + ", pInfo=" +(pInfo == null ? "null" : pInfo.mFamilyName + ", " + pInfo.mGivenName + ", "
                        + pInfo.getDisplayName()));
    }

    /**
     * Add recipient for the MAP message.
     *
     * @param address
     *            address of the recipient
     * @param pInfo personal information of recipient
     *
     * @hide
     */
    public void addRecipient(String address, final BluetoothPersonInfo pInfo) {
        if (address == null) {
            return;
        }
        mRecipientAddress.add(address);
        mRecipient.add(pInfo);
        Log.d(TAG,
                "addRecipient: address=" + address
                        + ", pInfo=" +(pInfo == null ? "null" : pInfo.getDisplayName()));
    }

    /**
     * Set the subject of the MAP message.
     *
     * @param subject
     *            subject to the message
     * @param maxSubjectLength personal information of recipient
     *
     * @hide
     */
    public void setSubject(String subject, int maxSubjectLength) {
        if (subject == null) {
            mSubject = null;
            return;
        }

        // FOR IOP, we limit the subject length to IOP_MAX_SUBJECT_LENGTH
        if (maxSubjectLength <= 0 || maxSubjectLength > IOP_MAX_SUBJECT_LENGTH) {
            maxSubjectLength = IOP_MAX_SUBJECT_LENGTH;
        }

        int subjectLength = subject.length();
        if (subjectLength <= maxSubjectLength) {
            mSubject = subject;
        } else {
            if (maxSubjectLength > IOP_SUBJECT_TRAILER_LENGTH) {
                mSubject = subject.substring(0, maxSubjectLength - IOP_SUBJECT_TRAILER_LENGTH)
                        + IOP_SUBJECT_TRAILER;
            } else {
                mSubject = subject.substring(0, maxSubjectLength);
            }
        }
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
        //dest.writeLong(mParameterMask);
        dest.writeInt(mMsgSize);
        dest.writeInt(mAttachmentSize);
        if (mIsText == true) {
            dest.writeByte((byte) 1);
        } else {
            dest.writeByte((byte) 0);
        }
        if (mIsHighPriority == true) {
            dest.writeByte((byte) 1);
        } else {
            dest.writeByte((byte) 0);
        }
        if (mIsRead == true) {
            dest.writeByte((byte) 1);
        } else {
            dest.writeByte((byte) 0);
        }
        if (mIsSent == true) {
            dest.writeByte((byte) 1);
        } else {
            dest.writeByte((byte) 0);
        }
        if (mIsProtected == true) {
            dest.writeByte((byte) 1);
        } else {
            dest.writeByte((byte) 0);
        }

        dest.writeString(mMsgHandle);
        dest.writeByte(mMsgType);
        dest.writeByte(mReceptionStatus);
        dest.writeString(mSubject);
        dest.writeString(mDateTime);
        int count = (mSender == null ? 0 : 1);
        dest.writeInt(count);
        if (count == 1) {
            mSender.writeToParcel(dest, flags);
        }

        count = (mSenderAddress == null ? 0 : 1);
        dest.writeInt(count);
        if (count == 1) {
            dest.writeString(mSenderAddress);
        }

        count = (mRecipient == null ? 0 : mRecipient.size());
        dest.writeInt(count);
        for (int i = 0; i < count; i++) {
            BluetoothPersonInfo p = mRecipient.get(i);
            if (p != null) {
                p.writeToParcel(dest, flags);
            }
        }

        count = (mRecipientAddress == null ? 0 : mRecipientAddress.size());
        dest.writeInt(count);
        for (int i = 0; i < count; i++) {
            String addr = mRecipientAddress.get(i);
            dest.writeString(addr);
        }

        count = (mReplyToAddress == null ? 0 : 1);
        dest.writeInt(count);
        if (count >0) {
            dest.writeString(mReplyToAddress);
        }
    }

    /**
     * Get the subject (summary) of the message.
     * This will contain the first few words of the message.
     *
     * @param htmlEncode
     *            true if subject has to be returned as HTML encoded text.
     *
     * @return subject of the message.
     */
    public String getSubject(boolean htmlEncode) {
        if (mSubject == null) {
            return "";
        }
        return htmlEncode ? TextUtils.htmlEncode(mSubject) : mSubject;
    }

    /**
     * Get the sender's address of the MAP message as String.
     *
     * @param htmlEncode
     *            true if sender's address has to be returned in as HTML.
     *
     * @return sender's address for the MAP message.
     *         In case of SMS, it is sender's phone number.
     *         In case of Email, it is sender's email address.
     *         In case of MMS, it is sender's email address or phone number.
     *         If the sender is not known, this can be empty string.
     */
    public String getSenderAddress(boolean htmlEncode) {
        if (mSenderAddress == null) {
            return "";
        }
        return htmlEncode ? TextUtils.htmlEncode(mSenderAddress) : mSenderAddress;
    }

    /**
     * Get the addressing information of the recipient.
     *
     * @param htmlEncode
     *            true if recipient's address has to be returned in as HTML.
     *
     * @return recipient's address for the MAP message.
     *         In case of SMS, this is recipient's phone number.
     *         In case of MMS, this is recipient's phone number of email address.
     *         In case of Email, this is recipients's email addresses delimited by ";".
     *         If recipient is not known, this field can be empty string.
     */
    public String getRecipientAddress(boolean htmlEncode) {
        if (mRecipientAddress == null || mRecipientAddress.size() == 0) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < mRecipientAddress.size(); i++) {
            if (b.length() > 0) {
                b.append(",");
            }
            b.append(mRecipientAddress.get(i));
        }
        return htmlEncode ? TextUtils.htmlEncode(b.toString()) : b.toString();
    }

    /**
     * Get the addressing information for replies to sender.
     * This shall be used only for emails to deliver the sender's reply-to email address.
     *
     * @param htmlEncode
     *            true if reply to address has to be returned as HTML.
     *
     * @return sender's reply-to email address ("Reply-To:" field).
     *         Empty string if not present.
     */
    public String getReplyToAddress(boolean htmlEncode) {
        if (mReplyToAddress == null || mReplyToAddress.length() == 0) {
            return "";
        }
        return htmlEncode ? TextUtils.htmlEncode(mReplyToAddress) : mReplyToAddress;

    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothMessageInfo> CREATOR = new Parcelable.Creator<BluetoothMessageInfo>() {

        public BluetoothMessageInfo createFromParcel(Parcel source) {

            return new BluetoothMessageInfo(source);
        }

        public BluetoothMessageInfo[] newArray(int size) {

            return new BluetoothMessageInfo[size];
        }
    };

    /**
     * @hide
     */
    public String toString() {
        StringBuilder s = new StringBuilder(200);
        toString(s);
        return s.toString();
    }

    /**
     * @hide
     */
    public void toString(StringBuilder s) {
        dumpState(s, "");
    }

    /**
     * @hide
     */
    public void dumpState(StringBuilder s, String prefix) {
        s.append(prefix);
        s.append("messageHandle  = ").append(mMsgHandle);

        s.append("\n");
        s.append(prefix);
        s.append("messageType = ").append(mMsgType);

        s.append("\n");
        s.append(prefix);
        s.append("messageSize = ").append(mMsgSize);

        s.append("\n");
        s.append(prefix);
        s.append("attachmentSize = ").append(mAttachmentSize);

        //s.append("\n");
        //s.append(prefix);
        //s.append("parameterMask = ").append(mParameterMask);

        s.append("\n");
        s.append(prefix);
        s.append("isText = ").append(mIsText);

        s.append("\n");
        s.append(prefix);
        s.append("isHighPriority = ").append(mIsHighPriority);

        s.append("\n");
        s.append(prefix);
        s.append("isRead = ").append(mIsRead);

        s.append("\n");
        s.append(prefix);
        s.append("isSent = ").append(mIsSent);

        s.append("\n");
        s.append(prefix);
        s.append("isProtected = ").append(mIsProtected);

        s.append("\n");
        s.append(prefix);
        s.append("receptionStatus = ").append(mReceptionStatus);

        s.append("\n");
        s.append(prefix);
        s.append("subject = ").append(mSubject == null ? "" : mSubject);

        s.append("\n");
        s.append(prefix);
        s.append("date_time = ").append(mDateTime);

        s.append("\n");
        s.append(prefix);
        s.append("sender=");
        if (mSender != null) {
            mSender.dumpState(s, prefix);
        }

        s.append("\n");
        s.append(prefix);
        s.append("senderAddress = ").append(getSenderAddress(false));

        s.append("\n");
        s.append(prefix);
        int recipientCount = mRecipient == null ? 0 : mRecipient.size();
        s.append("recipient mRecipient =").append(recipientCount);
        for (int i = 0; i < recipientCount; i++) {
            BluetoothPersonInfo p = mRecipient.get(i);
            p.dumpState(s, prefix);
        }

        s.append("\n");
        s.append(prefix);
        s.append("recipientAddress = ").append(getRecipientAddress(false));

        s.append("\n");
        s.append(prefix);
        s.append("replyToAddress = ").append(getReplyToAddress(false));

        s.append("\n");
    }

}
