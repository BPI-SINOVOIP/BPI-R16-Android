
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
package com.broadcom.bt.hfdevice;

import java.util.ArrayList;
import java.util.List;

import com.broadcom.bt.hfdevice.IBluetoothHfDevice;
import com.broadcom.bt.hfdevice.IBluetoothHfDeviceCallback;
import com.broadcom.bt.hfdevice.IBluetoothHfDevice.Stub;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;

import android.os.ServiceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/** Helper class for retrieving cached call state information */
public class BluetoothCallStateInfo {
    private int mNumActive;
    private int mCallSetupState;
    private int mNumHeld;
    private String mPhoneNumber= null;
    /** Retrieves current call setup state.
     * @return Cached call setup state.
     * Can be one of: {@link BluetoothHfDevice#CALL_SETUP_STATE_DIALING},
     *                {@link BluetoothHfDevice#CALL_SETUP_STATE_ALERTING},
     *                {@link BluetoothHfDevice#CALL_SETUP_STATE_INCOMING},
     *                {@link BluetoothHfDevice#CALL_SETUP_STATE_WAITING} or
     *                {@link BluetoothHfDevice#CALL_SETUP_STATE_IDLE}.
     */
    public int getCallSetupState(){
        return mCallSetupState;
    }

    /** Get current active calls.
     * @return Cached active calls. non-zero value indicates a presence
     **        of an active call. 0 implies there are no active call.
     */
    public int getNumActiveCall(){
        return mNumActive;
    }

    /** Get current held calls.
     * @return Cached held calls. non-zero value indicates a presence
     **        of an held call. 0 implies there are no held call.
     */
    public int getNumHeldCall(){
        return mNumHeld;
    }

    /** Retrieves the cached phone number of an incoming/waiting call.
     * @return Cached phone number retrieved through CLIP/CCWA of an
     *          incoming or waiting call
     */
    public String getPhoneNumber(){
        return mPhoneNumber;
    }

    /** Call state information.
     * @hide
     */
    public BluetoothCallStateInfo(int numActive, int callSetupState, int numHeld){
        mNumActive = numActive;
        mCallSetupState = callSetupState;
        mNumHeld = numHeld;
    }

    /** Call state information in the presence of incoming call.
     * @hide
     */
    public BluetoothCallStateInfo(int numActive, int callSetupState, int numHeld,
        String phoneNumber){
        mNumActive = numActive;
        mCallSetupState = callSetupState;
        mNumHeld = numHeld;
        mPhoneNumber = phoneNumber;
    }

    /** Set call setup state
     *  @hide
     */
    public void setCallSetupState(int callSetupState){
        mCallSetupState = callSetupState;
    }
    /** Set active call state.
     *  @hide
     */
    public void setNumActiveCall(int numActive){
        mNumActive = numActive;
    }
    /** Set held call state.
     *  @hide
     */
    public void setNumHeldCall(int numHeld){
        mNumHeld = numHeld;
    }

    /** Check presence of an active call.
     * @return true if there is an active call. false otherwise
     */
    public boolean isInCall() {
        return (mNumActive >= 1);
    }

    /** Check presence of an held call.
     * @return true if there is an held call. false otherwise
     */
    public boolean hasHeldCall() {
        return (mNumHeld >= 1);
    }

    /** Check presence of an active/held call.
     * @return non-zero value if there is an active/held call. 0 otherwise
     */
    public int getNumOfCalls() {
        return mNumHeld+mNumActive;
    }
}

