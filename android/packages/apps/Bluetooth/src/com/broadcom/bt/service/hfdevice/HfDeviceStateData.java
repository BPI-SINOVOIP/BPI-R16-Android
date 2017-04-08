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

package com.broadcom.bt.service.hfdevice;

import android.content.Context;
import android.util.Log;

import com.broadcom.bt.hfdevice.BluetoothHfDevice;
import com.broadcom.bt.hfdevice.BluetoothClccInfo;
import java.util.List;
import java.util.ArrayList;


// Note:
// All methods in this class are not thread safe, donot call them from
// multiple threads. Call them from the HfDeviceStateMachine message
// handler only.
class HfDeviceStateData {
    private static final String TAG = "HfDeviceStateData";

    // HFP 1.6 CIND service
    private int mService = HfDeviceHalConstants.NETWORK_STATE_NOT_AVAILABLE;

    // Number of active (foreground) calls
    private int mNumActive = 0;

    // Current Call Setup State
    private int mCallSetupState = HfDeviceHalConstants.CALL_STATE_IDLE;

    // Number of held (background) calls
    private int mNumHeld = 0;

    // HFP 1.6 CIND signal
    private int mSignal = 0;

    // HFP 1.6 CIND roam
    private int mRoam = HfDeviceHalConstants.SERVICE_TYPE_HOME;

    // HFP 1.6 CIND battchg
    private int mBatteryCharge = 0;

    private int mSpeakerVolume = 0;

    private int mMicVolume = 0;


    HfDeviceStateData() {
    }


    void setService(int service) {
        mService = service;
    }

    int getService() {
        return mService;
    }

    int getNumActiveCall() {
        return mNumActive;
    }

    void setNumActiveCall(int numActive) {
        mNumActive = numActive;
    }

    int getCallSetupState() {
        return mCallSetupState;
    }

    void setCallSetupState(int callSetupState) {
        mCallSetupState = callSetupState;
    }

    int getNumHeldCall() {
        return mNumHeld;
    }

    void setNumHeldCall(int numHeldCall) {
        mNumHeld = numHeldCall;
    }

    int getSignal() {
        return mSignal;
    }

    void setSignal(int signal) {
        mSignal = signal;
    }

    int getRoam() {
        return mRoam;
    }

    void setRoam(int roam) {
        mRoam = roam;
    }

    void setBatteryCharge(int batteryLevel) {
        if (mBatteryCharge != batteryLevel) {
            mBatteryCharge = batteryLevel;
        }
    }

    int getBatteryCharge() {
        return mBatteryCharge;
    }

    void setSpeakerVolume(int volume) {
        mSpeakerVolume = volume;
    }

    int getSpeakerVolume() {
        return mSpeakerVolume;
    }

    void setMicVolume(int volume) {
        mMicVolume = volume;
    }

    int getMicVolume() {
        return mMicVolume;
    }

    boolean isInCall() {
        return (mNumActive >= 1);
    }



}
