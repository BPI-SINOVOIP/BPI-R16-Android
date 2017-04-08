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

import android.bluetooth.BluetoothDevice;
import java.util.List;


/** Interface for receiving HFDevice callback events. */
public interface IBluetoothHfDeviceEventHandler {

    /**
     * Callback indicating an HFDevice Profile connection state change.
     *
     * @param errCode
     *           Success is indicated by {@link BluetoothHfDevice#NO_ERROR}.
     *           Failure is indicated by one of the ERROR_ values
     * @param remoteDevice
     *           Remote AG device associated with the connection state change
     * @param newState
     *            Can be one of
     *              {@link BluetoothHfDevice#STATE_DISCONNECTED}, {@link BluetoothHfDevice#STATE_CONNECTING},
     *              {@link BluetoothHfDevice#STATE_CONNECTED}, {@link BluetoothHfDevice#STATE_DISCONNECTING}.
     * @param prevState
     *            Can be one of
     *              {@link BluetoothHfDevice#STATE_DISCONNECTED}, {@link BluetoothHfDevice#STATE_CONNECTING},
     *              {@link BluetoothHfDevice#STATE_CONNECTED}, {@link BluetoothHfDevice#STATE_DISCONNECTING}.
     */
    public void onConnectionStateChange(int errCode, BluetoothDevice remoteDevice,
                                                int newState, int prevState);

    /**
     * Callback indicating audio state change.
     *
     * @param newState
     *            Can be one of {@link BluetoothHfDevice#STATE_AUDIO_DISCONNECTED}, {@link BluetoothHfDevice#STATE_AUDIO_CONNECTING},
     *            {@link BluetoothHfDevice#STATE_AUDIO_CONNECTED}, {@link BluetoothHfDevice#STATE_AUDIO_DISCONNECTING}
     * @param prevState
     *            Can be one of {@link BluetoothHfDevice#STATE_AUDIO_DISCONNECTED}, {@link BluetoothHfDevice#STATE_AUDIO_CONNECTING},
     *            {@link BluetoothHfDevice#STATE_AUDIO_CONNECTED}, {@link BluetoothHfDevice#STATE_AUDIO_DISCONNECTING}
     */
    public void onAudioStateChange( int newState, int prevState);

    /**
     * Callback for device indicators update (service, roam, signal, battery).
     *
     * @param indValue Array containing individual indicators. Use
     *                 {@link BluetoothHfDevice#INDICATOR_TYPE_SERVICE}, {@link BluetoothHfDevice#INDICATOR_TYPE_ROAM},
     *                 {@link BluetoothHfDevice#INDICATOR_TYPE_SIGNAL} or {@link BluetoothHfDevice#INDICATOR_TYPE_BATTERY}
     *                 to access the individual indicator values
     */
    public void onIndicatorsUpdate(int[] indValue);

    /**
     * Callback for call state change.
     *
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call related command ({@link BluetoothHfDevice#dial}, {@link BluetoothHfDevice#answer}, ...)
     * @param callSetupState
     *            One of the values from CALL_STATE_
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param numActive
     *            Has active calls.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param numHeld
     *            Has held calls.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param number
     *            Contains caller number information.
     *            Is valid only if callSetupState is {@link BluetoothHfDevice#CALL_SETUP_STATE_INCOMING} or {@link BluetoothHfDevice#CALL_SETUP_STATE_WAITING}.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param addrType
     *            Contains caller number type information.
     *            Is valid only if callSetupState is {@link BluetoothHfDevice#CALL_SETUP_STATE_INCOMING} or {@link BluetoothHfDevice#CALL_SETUP_STATE_WAITING}.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onCallStateChange(int status, int callSetupState, int numActive, int numHeld,
                                String number ,int addrType);

    /**
     * Callback indicating Voice Recognition state change.
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier VR related command ({@link BluetoothHfDevice#startVoiceRecognition} or {@link BluetoothHfDevice#stopVoiceRecognition})
     * @param vrState
     *            One of the values from VR_STATE_
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onVRStateChange(int status, int vrState);

    /**
     * Callback indicating volume gain change.
     *
     * @param volType
     *            Type of volume change {@link BluetoothHfDevice#VOLUME_TYPE_MIC}/{@link BluetoothHfDevice#VOLUME_TYPE_SPK}
     * @param volume
     *            Current volume level (0-15)
     */
    public void onVolumeChange(int volType, int volume);

    /**
     * Callback for readPhoneBookList() response.
     *
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call to {@link BluetoothHfDevice#readPhoneBookList}
     * @param phoneNum
     *         List of phone book entries  with index, number , addrtype,name.
     *         getXXXX() can be used to get info from the class {@link BluetoothPhoneBookInfo}
     */
    public void onPhoneBookReadRsp(int status, List<BluetoothPhoneBookInfo> phoneNum);

    /**
     * Callback for querySubscriberInfo() response
     *
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call to {@link BluetoothHfDevice#querySubscriberInfo}
     * @param number
     *            Contains caller number information.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param addrType
     *            Contains caller number type information.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onSubscriberInfoRsp(int status, String number ,int addrType);

    /**
     * Callback for queryOperatorSelectionInfo response.
     *
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call to {@link BluetoothHfDevice#queryOperatorSelectionInfo}
     * @param mode
     *            Indicating mode of operatior selection.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     * @param operatorName
     *            The operator name.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onOperatorSelectionRsp(int status, int mode, String operatorName);

    /**
     * Callback for Extended error result code.
     *
     * @param errorResultCode
     *            Containing the extended result code
     * @hide
     */
    public void onExtendedErrorResult(int errorResultCode);


    /**
     * Callback indicating response for {@link BluetoothHfDevice#getCLCC} command.
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call to {@link BluetoothHfDevice#getCLCC}
     * @param clcc List of {@link BluetoothClccInfo}
     *         List of current call info with index, call direction , call state,
     *         call mode_, multiparty, number, call addrtype.
     *         Individual get methods can be used to get info from the class {@link BluetoothClccInfo}.
     *         This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onCLCCRsp(int status, List<BluetoothClccInfo> clcc);

    /**
     * Callback for Vendor/app pre-formatted AT strings.
     * Note that if app sends a pre-formatted AT command for which a
     * callback is already defined above, then the response will be sent in the
     * pre-defined callback.
     * @param status
     *            Value other than {@link BluetoothHfDevice#NO_ERROR} indicates failure
     *            of an earlier call to {@link BluetoothHfDevice#sendVendorCmd}
     * @param atRsp
     *            String containing the received AT response.
     *            This parameter is invalid if status is not {@link BluetoothHfDevice#NO_ERROR}
     */
    public void onVendorAtRsp(int status, String atRsp);

    /**
     * Callback for RING event(send by AG) when a HSP connection exist.
     * This will usually happen for incoming call in AG. Continous RING event
     * may be sent from AG side till the call is answered.In such case
     *.the app should take care such that it ignores the
     * the subsequent RING event if it is not necessary.
     */
    public void onRingEvent();

    /**
     * Callback to report the ON/OFF status for the IN-Band Ring tone feature.
     * @param status
     *            With either one of the values {@link BluetoothHfDevice#INBAND_STATE_OFF}
     *              or {@link BluetoothHfDevice#INBAND_STATE_ON}
     */
    public void onInBandRingStatusEvent(int inBandRingStatus);

    /**
    * Callback to report the status for the API {@link BluetoothHfDevice#sendBIA}.
    * @param status
    *            With either one of the values {@link BluetoothHfDevice#ERROR_AG_FAILURE}
    *              or {@link BluetoothHfDevice#NO_ERROR}
    */
    public void onBIAStatus(int status);

}

