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
import android.text.TextUtils;

/**
 * @hide
 */
public class BluetoothPersonInfo implements Parcelable {
    public String mDisplayName;
    public String mGivenName;
    public String mMiddleName;
    public String mFamilyName;

    public static String getDisplayName(String displayName, String givenName, String middleName,
            String familyName) {
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        } else {
            StringBuilder b = new StringBuilder();
            if (!TextUtils.isEmpty(givenName)) {
                b.append(givenName);
            }
            if (!TextUtils.isEmpty(middleName)) {
                if (b.length() > 0) {
                    b.append(" ");
                }
                b.append(middleName);
            }
            if (!TextUtils.isEmpty(familyName)) {
                if (b.length() > 0) {
                    b.append(" ");
                }
                b.append(familyName);
            }
            return b.length() > 0 ? b.toString() : null;
        }
    }

    public BluetoothPersonInfo(Parcel source) {
        byte isSet = source.readByte();
        if (isSet ==1) {
            mDisplayName=source.readString();
        }

        isSet = source.readByte();
        if (isSet==1) {
            mGivenName= source.readString();
        }

        isSet = source.readByte();
        if (isSet==1) {
            mMiddleName= source.readString();
        }

        isSet = source.readByte();
        if (isSet==1) {
            mFamilyName= source.readString();
        }
    }

    public BluetoothPersonInfo() {
    }

    public String getDisplayName() {
        return getDisplayName(mDisplayName, mGivenName, mMiddleName, mFamilyName);

    }
    public String toVcardField_N() {
        return toVcardField_N(mDisplayName,mGivenName,mMiddleName,mFamilyName);
    }
    public String toVcardField_FN() {
        return toVcardField_FN(mDisplayName,mGivenName,mMiddleName,mFamilyName);
    }

    public static String toVcardField_N(String displayName, String givenName, String middleName,
            String familyName) {
        StringBuilder b = new StringBuilder();
        if (!TextUtils.isEmpty(familyName)) {
            b.append(familyName);
        }
        if (!TextUtils.isEmpty(givenName)) {
            if (b.length() > 0) {
                b.append(";");
            }
            b.append(givenName);
        }
        if (!TextUtils.isEmpty(middleName)) {
            if (b.length() > 0) {
                b.append(";");
            }
            b.append(middleName);
        }
        return b.length() > 0 ? b.toString() : "";
    }

    public static String toVcardField_FN(String displayName, String givenName,String middleName,
            String familyName) {
        String dName = getDisplayName(displayName, givenName, middleName,familyName);
        return dName == null ? "" : dName;
    }

    public void dumpState(StringBuilder s, String prefix) {
        s.append(prefix);
        s.append("displayName  = ").append(mDisplayName==null?"":mDisplayName);

        s.append("\n");
        s.append(prefix);
        s.append("givenName = ").append(mGivenName==null?"":mGivenName);

        s.append("\n");
        s.append(prefix);
        s.append("middleName = ").append(mMiddleName==null?"":mMiddleName);

        s.append("\n");
        s.append(prefix);
        s.append("familyName = ").append(mFamilyName==null?"":mFamilyName);
    }

    public final static Parcelable.Creator<BluetoothPersonInfo> CREATOR = new Parcelable.Creator<BluetoothPersonInfo>() {

        public BluetoothPersonInfo createFromParcel(Parcel source) {

            return new BluetoothPersonInfo(source);
        }

        public BluetoothPersonInfo[] newArray(int size) {

            return new BluetoothPersonInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(mDisplayName==null?(byte)0:(byte)1);
        if (mDisplayName != null) {
            dest.writeString(mDisplayName);
        }
        dest.writeByte(mGivenName==null?(byte)0:(byte)1);
        if (mGivenName != null) {
            dest.writeString(mGivenName);
        }
        dest.writeByte(mMiddleName==null?(byte)0:(byte)1);
        if (mMiddleName != null) {
            dest.writeString(mMiddleName);
        }
        dest.writeByte(mFamilyName==null?(byte)0:(byte)1);
        if (mFamilyName != null) {
            dest.writeString(mFamilyName);
        }

    }
}
