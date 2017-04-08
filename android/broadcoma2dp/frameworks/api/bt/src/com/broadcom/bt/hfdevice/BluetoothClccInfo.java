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

package com.broadcom.bt.hfdevice;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeMap;
import android.util.Log;


/**
 * Parcelable objects that stores a single parsed call from a CLCC response.
 * {@link IBluetoothHfDeviceEventHandler#onCLCCRsp} returns a list of this object in response to a call
 * to {@link BluetoothHfDevice#getCLCC}.
 */
public final class BluetoothClccInfo implements Parcelable {
    int mIndex;
    int mDirection;
    int mState;
    int mMode;
    int mMpty;
    String mNumber;
    int mType;

    /** @hide */
    public static final String TAG = "BluetoothClccInfo";

    /** @hide */
    public BluetoothClccInfo(String clccString)
    {
        parseClcc(clccString);
    }

// Copy constructor
    /** @hide */
    public BluetoothClccInfo(BluetoothClccInfo clcc)
    {
        this.mIndex = clcc.mIndex;
        this.mDirection = clcc.mDirection;
        this.mState = clcc.mState;
        this.mMode = clcc.mMode;
        this.mMpty = clcc.mMpty;
        this.mNumber = clcc.mNumber;
        this.mType = clcc.mType;
    }

    private void parseClcc(String input)
    {

        StringTokenizer stringTokenizer = new StringTokenizer(input,",");
        int i = 0;
        String tokenString;

        Log.d(TAG, input);

        while(stringTokenizer.hasMoreTokens()) {

            tokenString = stringTokenizer.nextToken();
            if (null != tokenString)
            {
                tokenString = tokenString.trim();
                if (i == 0) {
                    mIndex = Integer.parseInt(tokenString);
                } else if ( i == 1) {
                    mDirection = Integer.parseInt(tokenString);
                } else if (i == 2) {
                    mState = Integer.parseInt(tokenString);
                } else if (i == 3) {
                    mMode = Integer.parseInt(tokenString);
                } else if (i == 4) {
                    mMpty = Integer.parseInt(tokenString);
                } else if (i == 5) {
                    mNumber = tokenString.substring(1 ,tokenString.length()-1);
                } else if (i == 6) {
                    mType = Integer.parseInt(tokenString);
                }
            }
            i = i + 1;
        }

        Log.d("BluetoothClccInfo",getSummary());

    }

    /** @hide */
    public String getSummary() {
        return "Idx="+mIndex+"direction="+mDirection+"state="+mState+"number="+mNumber;
    }

    /** Retrieves the current call index.
     * @return A non-zero integer indicating the call index 
     */
    public int getCallIndex()
    {
        return mIndex;
    }

    /** Retrieves the current call direction.
     * @return Direction of the call. Can be one of {@link BluetoothHfDevice#CALL_DIRECTION_OUTGOING} or
     *         {@link BluetoothHfDevice#CALL_DIRECTION_INCOMING}.
     */
    public int getCallDirection()
    {
        return mDirection;
    }

    /** Retrieves the current call state.
     * @return Call state. Can be one of {@link BluetoothHfDevice#CALL_STATE_ACTIVE},
     *         {@link BluetoothHfDevice#CALL_STATE_HELD}, {@link BluetoothHfDevice#CALL_SETUP_STATE_DIALING},
     *         {@link BluetoothHfDevice#CALL_SETUP_STATE_ALERTING}, {@link BluetoothHfDevice#CALL_SETUP_STATE_INCOMING} or
     *         {@link BluetoothHfDevice#CALL_SETUP_STATE_WAITING}
     */
    public int getCallState()
    {
        return mState;
    }

    /** Retrieves the current call mode.
     * @return Call mode. Can be one of {@link BluetoothHfDevice#CALL_TYPE_VOICE},
     *         {@link BluetoothHfDevice#CALL_TYPE_DATA}, {@link BluetoothHfDevice#CALL_TYPE_FAX}
     */
    public int getCallMode()
    {
        return mMode;
    }

    /** Retrieves the current call's multiparty type.
     * @return Call multi-party type. Can be one of {@link BluetoothHfDevice#CALL_MPTY_TYPE_SINGLE},
     *         {@link BluetoothHfDevice#CALL_MPTY_TYPE_MULTI}
     */
    public int getCallMultiPartyType()
    {
        return mMpty;
    }

    /** Retrieves the current call number.
     * @return String indicating the call number
     */
    public String getCallNumber()
    {
        return mNumber;
    }


    /** Retrieves the current call number type.
     * @return String indicating the call number type. Can be one of {@link BluetoothHfDevice#CALL_ADDRTYPE_UNKNOWN},
     *         {@link BluetoothHfDevice#CALL_ADDRTYPE_INTERNATIONAL}
     */
    public int getCallNumType()
    {
        return mType;
    }

    /** @hide */
    public int describeContents()
    {
        return 0;
    }

    /** @hide */
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeInt(mIndex);
        dest.writeInt(mDirection);
        dest.writeInt(mState);
        dest.writeInt(mMode);
        dest.writeInt(mMpty);
        dest.writeString(mNumber);
        dest.writeInt(mType);
    }

    /** @hide */
    public static final Creator<BluetoothClccInfo> CREATOR = new Creator<BluetoothClccInfo>() {
            public BluetoothClccInfo createFromParcel(Parcel source) {
                return new BluetoothClccInfo(source);
            }
            public BluetoothClccInfo[] newArray(int size) {
                return new BluetoothClccInfo[size];
            }
    };

    private BluetoothClccInfo(Parcel source)
    {
        mIndex = source.readInt();
        mDirection = source.readInt();
        mState = source.readInt();
        mMode = source.readInt();
        mMpty = source.readInt();
        mNumber = source.readString();
        mType = source.readInt();
    }
}

