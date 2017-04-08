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
 * to store MAP message list parameterized filter information. This class is used
 * to create filter object in the method
 * {@link BluetoothMapClient#getMessageListObject(int, int, int,
            BluetoothMessageListFilter, BluetoothMessageParameterFilter)}
 *
 */
public class BluetoothMessageParameterFilter implements Parcelable {
    // Message parameter masks
    /**
     * Message parameter mask constant for masking subject
     */
    public static final int MAP_MSG_MASK_SUBJECT = (1 << 0);
    /**
     * Message parameter mask constant for masking date time
     */
    public static final int MAP_MSG_MASK_DATETIME = (1 << 1);
    /**
     * Message parameter mask constant for masking sender name
     */
    public static final int MAP_MSG_MASK_SENDER_NAME = (1 << 2);
    /**
     * Message parameter mask constant for masking sender address
     */
    public static final int MAP_MSG_MASK_SENDER_ADDRESSING = (1 << 3);
    /**
     * Message parameter mask constant for masking recipient name
     */
    public static final int MAP_MSG_MASK_RECIPIENT_NAME = (1 << 4);
    /**
     * Message parameter mask constant for masking recipient address
     */
    public static final int MAP_MSG_MASK_RECIPIENT_ADDRESSING = (1 << 5);
    /**
     * Message parameter mask constant for masking message type
     */
    public static final int MAP_MSG_MASK_TYPE = (1 << 6);
    /**
     * Message parameter mask constant for masking message size
     */
    public static final int MAP_MSG_MASK_SIZE = (1 << 7);
    /**
     * Message parameter mask constant for masking reception status
     */
    public static final int MAP_MSG_MASK_RECEPTION_STATUS = (1 << 8);
    /**
     * Message parameter mask constant for masking message text
     */
    public static final int MAP_MSG_MASK_TEXT = (1 << 9);
    /**
     * Message parameter mask constant for masking attachement size
     */
    public static final int MAP_MSG_MASK_ATTACHMENT_SIZE = (1 << 10);
    /**
     * Message parameter mask constant for masking message priority
     */
    public static final int MAP_MSG_MASK_PRIORITY = (1 << 11);
    /**
     * Message parameter mask constant for masking read status
     */
    public static final int MAP_MSG_MASK_READ = (1 << 12);
    /**
     * Message parameter mask constant for masking sent status
     */
    public static final int MAP_MSG_MASK_SENT = (1 << 13);
    /**
     * Message parameter mask constant for masking projected status
     */
    public static final int MAP_MSG_MASK_PROTECTED = (1 << 14);
    /**
     * Message parameter mask constant for masking reply to address
     */
    public static final int MAP_MSG_REPLYTO_ADDRESSING = (1 << 15);

    /**
     * Paramaters contained in the returned list.  If this is not set, all parameters will
     * be returned.
     */
    public long mParameterMask;

    /**
     * @hide
     */
    private BluetoothMessageParameterFilter(Parcel source) {
        mParameterMask = source.readLong();
    }

    public BluetoothMessageParameterFilter(long paramMask) {
        mParameterMask = paramMask;
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
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mParameterMask);
    }

    /**
     * @hide
     */
    public final static Parcelable.Creator<BluetoothMessageParameterFilter> CREATOR = new Parcelable.Creator<BluetoothMessageParameterFilter>() {

        public BluetoothMessageParameterFilter createFromParcel(Parcel source) {
            return new BluetoothMessageParameterFilter(source);
        }

        public BluetoothMessageParameterFilter[] newArray(int size) {
            return new BluetoothMessageParameterFilter[size];
        }
    };
}
