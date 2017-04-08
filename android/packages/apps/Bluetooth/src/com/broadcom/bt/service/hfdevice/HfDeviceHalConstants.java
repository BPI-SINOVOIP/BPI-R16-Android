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

/*
 * @hide
 */

final public class HfDeviceHalConstants {
    // Do not modify without upating the HAL bt_hfdevice.h files.

    // match up with bthfdevice_connection_state_t enum of bt_hfdevice.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_SLC_CONNECTED = 3;
    final static int CONNECTION_STATE_DISCONNECTING = 4;

    // match up with bthfdevice_audio_state_t enum of bt_hfdevice.h
    final static int AUDIO_STATE_DISCONNECTED = 0;
    final static int AUDIO_STATE_CONNECTING = 1;
    final static int AUDIO_STATE_CONNECTED = 2;
    final static int AUDIO_STATE_DISCONNECTING = 3;

    // match up with bthfdevice_vr_state_t enum of bt_hfdevice.h
    final static int VR_STATE_STOPPED = 0;
    final static int VR_STATE_STARTED = 1;

    // match up with bthfdevice_volume_type_t enum of bt_hfdevice.h
    final static int VOLUME_TYPE_SPK = 0;
    final static int VOLUME_TYPE_MIC = 1;

    // match up with bthfdevice_network_state_t enum of bt_hfdevice.h
    final static int NETWORK_STATE_NOT_AVAILABLE = 0;
    final static int NETWORK_STATE_AVAILABLE = 1;

    // match up with bthfdevice_service_type_t enum of bt_hfdevice.h
    final static int SERVICE_TYPE_HOME = 0;
    final static int SERVICE_TYPE_ROAMING = 1;

    // match up with bthfdevice_at_response_t of bt_hfdevice.h
    final static int AT_RESPONSE_ERROR = 0;
    final static int AT_RESPONSE_OK = 1;

    final static int PB_DOWNLOAD_MODE_ENABLE = 0;
    final static int PB_DOWNLOAD_MODE_DISABLE = 1;

    // match up with bthfdevice_call_state_t of bt_hfdevice.h
    final static int CALL_STATE_ACTIVE = 0;
    final static int CALL_STATE_HELD = 1;
    final static int CALL_STATE_DIALING = 2;
    final static int CALL_STATE_ALERTING = 3;
    final static int CALL_STATE_INCOMING = 4;
    final static int CALL_STATE_WAITING = 5;
    final static int CALL_STATE_IDLE = 6;

    final static int INBAND= 0x0008;/*Bit field to acces In-band ring tone feature */


}
