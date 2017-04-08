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
package com.broadcom.bt.pbap;

import android.util.Log;

/**
 * Helper class for an PBAP Attribute Mask used for PullPhonebook and
 * PullVCardEntry.
 *
 * @author fredc@broadcom.com
 *
 */
public class BluetoothAttributeMask {
    private static final String TAG = "BluetoothAttributeMask";
    private static final boolean DBG = true;

    /** 64 bit mask to represent all vCard attributes*/
    public static BluetoothAttributeMask EMPTY_MASK = new BluetoothAttributeMask();

    /** vCard Version*/
    public static final int VERSION = 0;
    /** Formatted Name*/
    public static final int FN = 1;
    /** Structured Presentation of Name*/
    public static final int N = 2;
    /** Associated Image or Photo*/
    public static final int PHOTO = 3;
    /** Birthday*/
    public static final int BDAY = 4;
    /** Delivery Address*/
    public static final int ADR = 5;
    /** Delivery*/
    public static final int LABEL = 6;
    /** Telephone Number*/
    public static final int TEL = 7;
    /** Electronic Mail Address*/
    public static final int EMAIL = 8;
    /** Electronic Mail*/
    public static final int MAILER = 9;
    /** Timezone*/
    public static final int TZ = 10;
    /** Geographic Position*/
    public static final int GEO = 11;
    /** Job*/
    public static final int TITLE = 12;
    /** Role within the Organization*/
    public static final int ROLE = 13;
    /** Organization Logo*/
    public static final int LOGO = 14;
    /** vCard of Person Representing*/
    public static final int AGENT = 15;
    /** Name of Organization*/
    public static final int ORG = 16;
    /** Comments*/
    public static final int NOTE = 17;
    /** Revision*/
    public static final int REV = 18;
    /** Pronounciation of Name*/
    public static final int SOUND = 19;
    /** Uniform Resource Locator*/
    public static final int URL = 20;
    /** Unique ID*/
    public static final int UID = 21;
    /** Public Encryption Key*/
    public static final int KEY = 22;
    /** Nickname*/
    public static final int NICKNAME = 23;
    /** Categories*/
    public static final int CATEGORIES = 24;
    /** Product ID*/
    public static final int PROID = 25;
    /** Class Information*/
    public static final int CLASS = 26;
    /** String used for sort operations*/
    public static final int SORT_STRING = 27;
    /** Timestamp*/
    public static final int X_IRMC_CALL_DATETIME = 28;

    private byte[] mBluetoothAttributeMask = { 0, 0, 0, 0, 0, 0, 0, 0 };

    /**
     * parse attribute specified
     *
     * @param mask
     * @return
     */
    public void parse(long mask) {
        for (int i = 0; i < mBluetoothAttributeMask.length; i++) {
            mBluetoothAttributeMask[mBluetoothAttributeMask.length - 1 - i] = (byte) (mask & 0xFF);
            mask = mask >> 8;
        }
    }
    
    /**
     * Add specified vCard attribute in the bit mask
     *
     * @param attr vCard attribute to add
     * @return
     */
    public void addAttribute(int attr) {
        int byteIndex = 7 - attr / 8;
        int bitIndex = attr % 8;
        if (byteIndex < mBluetoothAttributeMask.length) {
            mBluetoothAttributeMask[byteIndex] |= (1 << bitIndex);
        }
    }


    /**
     * Check if an attribute is set in the bit mask
     *
     * @param attr vCard attribute to add
     * @return
     */
    public boolean isAttributeSet(int attr) {
        int byteIndex = 7 - attr / 8;
        int bitIndex = attr % 8;
        if (byteIndex < mBluetoothAttributeMask.length) {
            return (mBluetoothAttributeMask[byteIndex] & (1 << bitIndex)) > 0;
        }
        return false;
    }

    /**
     * Clear an attribute from the set the bit mask
     *
     * @param attr vCard attribute to add
     * @return
     */
    public void removeAttribute(int attr) {
        int byteIndex = 7 - attr / 8;
        int bitIndex = attr % 8;
        if (byteIndex < mBluetoothAttributeMask.length) {
            mBluetoothAttributeMask[byteIndex] &= (1 << bitIndex);
        }
    }

    /**
     * Get the byte array holding entire bit mask
     *
     * @hide
     */
    public byte[] getBytes() {
        if (DBG) {
            Log.d(TAG, toDebugString());
        }
        return mBluetoothAttributeMask;
    }

    /**
     * Get the entire bit mask in hex string format for debugging
     *
     * @hide
     */
    public String toDebugString() {
        StringBuilder filterVal = new StringBuilder();
        if (mBluetoothAttributeMask == null || mBluetoothAttributeMask.length == 0) {
            filterVal.append("(not set)");
        } else {
            filterVal.append("0x");
            for (int i = 0; i < mBluetoothAttributeMask.length; i++) {
                filterVal.append(String.format("%02x", mBluetoothAttributeMask[i]));
            }
        }
        return filterVal.toString();
    }
}
