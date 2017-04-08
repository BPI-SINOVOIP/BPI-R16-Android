/************************************************************************************
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
 ************************************************************************************/

#ifndef ANDROID_INCLUDE_BT_HFDEVICE_H
#define ANDROID_INCLUDE_BT_HFDEVICE_H

__BEGIN_DECLS


/* Bluetooth Hf device connection states */
typedef enum {
    BTHFDEVICE_CONNECTION_STATE_DISCONNECTED = 0,
    BTHFDEVICE_CONNECTION_STATE_CONNECTING,
    BTHFDEVICE_CONNECTION_STATE_CONNECTED,
    BTHFDEVICE_CONNECTION_STATE_SLC_CONNECTED,
    BTHFDEVICE_CONNECTION_STATE_DISCONNECTING
} bthfdevice_connection_state_t;

/* Bluetooth Hf device datapath states */
typedef enum {
    BTHFDEVICE_AUDIO_STATE_DISCONNECTED = 0,
    BTHFDEVICE_AUDIO_STATE_CONNECTING,
    BTHFDEVICE_AUDIO_STATE_CONNECTED,
} bthfdevice_audio_state_t;

/* Voice recognisation states.*/
typedef enum {
    BTHFDEVICE_VR_STATE_STOPPED = 0,
    BTHFDEVICE_VR_STATE_STARTED
} bthfdevice_vr_state_t;

/* Volume change types.*/
typedef enum {
    BTHFDEVICE_VOLUME_TYPE_SPK = 0,
    BTHFDEVICE_VOLUME_TYPE_MIC
} bthfdevice_volume_type_t;

/* WBS codec setting */
typedef enum
{
   BTHFDEVICE_WBS_NONE,
   BTHFDEVICE_WBS_YES
}bthfdevice_wbs_config_t;

/* In band ring tone settings states.*/
typedef enum {
    BTHFDEVICE_INBAND_RING_STATE_OFF = 0,
    BTHFDEVICE_INBAND_RING_STATE_ON
} bthfdevice_inband_ring_status_t;


/* CHLD - Call held handling */
typedef enum
{
    // Terminate all held or set UDUB("busy") to a waiting call
    BTHFDEVICE_CHLD_TYPE_RELEASEHELD,
    // Terminate all active calls and accepts a waiting/held call
    BTHFDEVICE_CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD,
    // Hold all active calls and accepts a waiting/held call
    BTHFDEVICE_CHLD_TYPE_HOLDACTIVE_ACCEPTHELD,
    // Add all held calls to a conference
    BTHFDEVICE_CHLD_TYPE_ADDHELDTOCONF,
} bthfdevice_chld_type_t;

typedef enum {
    BTHFDEVICE_CALL_STATE_ACTIVE = 0,
    BTHFDEVICE_CALL_STATE_HELD,
    BTHFDEVICE_CALL_STATE_DIALING,
    BTHFDEVICE_CALL_STATE_ALERTING,
    BTHFDEVICE_CALL_STATE_INCOMING,
    BTHFDEVICE_CALL_STATE_WAITING,
    BTHFDEVICE_CALL_STATE_IDLE
} bthfdevice_call_state_t;

/** Network Status */
typedef enum
{
    BTHFDEVICE_SERVICE_NOT_AVAILABLE = 0,
    BTHFDEVICE_SERVICE_AVAILABLE
} bthfdevice_service_t;

/** Service type */
typedef enum
{
    BTHFDEVICE_SERVICE_TYPE_HOME = 0,
    BTHFDEVICE_SERVICE_TYPE_ROAMING
} bthfdevice_service_type_t;

/* PB download mode configuration.*/
typedef enum {
    BTHFDEVICE_PB_DOWNLOAD_MODE_ENABLE = 0,
    BTHFDEVICE_PB_DOWNLOAD_MODE_DISABLE
} bthfdevice_pb_download_mode_t;

/** Mobile Equipment Error  CME ERROR */
/* These values match the spec provided CME Error Codes */
typedef enum
{
    BTHFDEVICE_ERROR_AG_FAILURE = 0,
    BTHFDEVICE_ERROR_NO_CONNECTION_TO_PHONE = 1,
    BTHFDEVICE_ERROR_OPERATION_NOT_ALLOWED = 3,
    BTHFDEVICE_ERROR_OPERATION_NOT_SUPPORTED = 4,
    BTHFDEVICE_ERROR_PIN_REQUIRED = 5,
    BTHFDEVICE_ERROR_SIM_MISSING = 10,
    BTHFDEVICE_ERROR_SIM_PIN_REQUIRED = 11,
    BTHFDEVICE_ERROR_SIM_PUK_REQUIRED = 12,
    BTHFDEVICE_ERROR_SIM_FAILURE = 13,
    BTHFDEVICE_ERROR_SIM_BUSY = 14,
    BTHFDEVICE_ERROR_WRONG_PASSWORD = 16,
    BTHFDEVICE_ERROR_SIM_PIN2_REQUIRED = 17,
    BTHFDEVICE_ERROR_SIM_PUK2_REQUIRED = 18,
    BTHFDEVICE_ERROR_MEMORY_FULL = 20,
    BTHFDEVICE_ERROR_INVALID_INDEX = 21,
    BTHFDEVICE_ERROR_MEMORY_FAILURE = 23,
    BTHFDEVICE_ERROR_TEXT_TOO_LONG = 24,
    BTHFDEVICE_ERROR_TEXT_HAS_INVALID_CHARS = 25,
    BTHFDEVICE_ERROR_DIAL_STRING_TOO_LONG = 26,
    BTHFDEVICE_ERROR_DIAL_STRING_HAS_INVALID_CHARS = 27,
    BTHFDEVICE_ERROR_NO_SERVICE = 30,
    BTHFDEVICE_ERROR_ONLY_911_ALLOWED = 32,

    /* This is not a spec defined code. Defined to return no_error to the callbacks */
    BTHFDEVICE_NO_ERROR = 255
}bthfdevice_error_code_t;

/** Callback for connection state change.
 *  state will have one of the values from bthf_connection_state_t
 */
typedef void (* bthfdevice_connection_state_callback)(bthfdevice_connection_state_t state,
                                        bt_bdaddr_t *bd_addr,
                                        int peer_features,int local_features);

/** Callback for audiopath state change.
 *  state will have one of the values from bthfdevice_audio_state_t
 */
typedef void (* bthfdevice_audio_state_callback)(bthfdevice_audio_state_t state,
                                       bt_bdaddr_t *bd_addr);

/** Voice recognition status in ag. */
typedef void (*bthfdevice_vr_cmd_callback)(int status, bthfdevice_vr_state_t vrstate);

/** Device status indication from ag. */
typedef void (*bthfdevice_device_status_ind_callback)(bthfdevice_service_t svc,
                                    bthfdevice_service_type_t svc_type, int signal, int batt_chg);

/** Call status indication from ag. */
typedef void (*bthfdevice_call_status_ind_callback)(int num_active,
                                    bthfdevice_call_state_t call_setup, int num_held);

/** Volume config for speaker/Mic. */
typedef void (*bthfdevice_volume_cmd_callback)(bthfdevice_volume_type_t type, int volume);

/** Ag sends the CLIP info in the for incomming call */
typedef void (*bthfdevice_clip_callback)(char* clip_str);

/** Call waiting notificaton from ag. */
typedef void (*bthfdevice_ccwa_callback)(char* ccwa_str);

/** Calling subsriber info event from ag.Can be called iteratively */
typedef void (*bthfdevice_cnum_callback)(bthfdevice_error_code_t code, char* cnum_str);

/** Operator info callback.Can be called iteratively */
typedef void (*bthfdevice_cops_callback)(bthfdevice_error_code_t code, char* cops_str);

/** Current active call list info event from ag.Can be called iteratively */
typedef void (*bthfdevice_clcc_callback)(bthfdevice_error_code_t code, char* clcc_str);

/** Non standard vendor AT command response */
typedef void (*bthfdevice_unknown_at_cmd_rsp_callback)(bthfdevice_error_code_t code,
                                                            char* unat_str);

/** Callback for wbs status when Ag supports wbs */
typedef void (* bthfdevice_wbs_callback)(bthfdevice_wbs_config_t wbs);

/** Callback for HSP RING event */
typedef void (* bthfdevice_ring_callback)();


/** Callback for IN-BAND ring tone status event */
typedef void (* bthfdevice_inband_ring_status_callback)(bthfdevice_inband_ring_status_t status);

/** Callback for reporting the Indicator Activation status*/
typedef void (* bthfdevice_bia_status_callback)(int status);

/** BT-HFDEVICE callback structure. */
typedef struct {
    /** set to sizeof(bthfdevice_callbacks_t) */
    size_t      size;
    bthfdevice_connection_state_callback  connection_state_cb;
    bthfdevice_audio_state_callback audio_state_cb;
    bthfdevice_vr_cmd_callback vr_cmd_cb;
    bthfdevice_volume_cmd_callback volume_cmd_cb;
    bthfdevice_device_status_ind_callback device_status_ind_cb;
    bthfdevice_call_status_ind_callback call_status_ind_cb;

    bthfdevice_clip_callback clip_cb;
    bthfdevice_ccwa_callback ccwa_cb;
    bthfdevice_cnum_callback cnum_cb;
    bthfdevice_cops_callback cops_cb;
    bthfdevice_clcc_callback clcc_cb;
    bthfdevice_unknown_at_cmd_rsp_callback unknown_at_cmd_rsp_cb;
    bthfdevice_wbs_callback wbs_cb;
    bthfdevice_ring_callback ring_cb;
    bthfdevice_inband_ring_status_callback inband_ring_status_cb;
    bthfdevice_bia_status_callback bia_status_cb;

} bthfdevice_callbacks_t;




/** Represents the standard BT-HFDEVICE interface. */
typedef struct {

    /** set to sizeof(bthfdevice_interface_t) */
    size_t          size;
    /**
     * Register the Bthfdevice callbacks
     */
    bt_status_t (*init)( bthfdevice_callbacks_t* callbacks );

    /** connect to HFP */
    bt_status_t (*connect)( bt_bdaddr_t *bd_addr );

    /** dis-connect from HFP */
    bt_status_t (*disconnect)( bt_bdaddr_t *bd_addr );

    /** connect to HFDEVICE audio */
    bt_status_t (*connect_audio)( bt_bdaddr_t *bd_addr );

    /** dis-connect from HFDEVICE audio */
    bt_status_t (*disconnect_audio)( bt_bdaddr_t *bd_addr );

    /** Sends AT command to start voice recognition */
    bt_status_t (*start_voice_recognition)();

    /** Sends AT command to stop voice recognition */
    bt_status_t (*stop_voice_recognition)();

    /** Sends AT command to control volume to ag
        value 0-15*/
    bt_status_t (*set_volume) (bthfdevice_volume_type_t type, int volume);

    /** Sends AT command to answer a call */
    bt_status_t (*answer)();


    /** Sends AT command to dial a call to 'num'.
            Redial when 'num' is NULL*/
    bt_status_t (*dial)(char* num);

    /** Sends AT command to Hold a call */
    bt_status_t (*hold)(bthfdevice_chld_type_t type);

    /** Sends AT command to Hangup/reject call */
    bt_status_t (*hangup)();

    /** Sends AT command with dtmf tone info to ag */
    bt_status_t (*send_dtmf_cmd)(char data);

    /** Sends AT command with keypress event to ag in HSP role */
    bt_status_t (*send_key_pressed_event_cmd)();

    /** Sends AT command to query subscriber info */
    bt_status_t (*send_cnum_cmd)();

    /** Sends AT command to query operator selection from ag */
    bt_status_t (*send_cops_cmd)();

    /** Sends AT command to get current call list info */
    bt_status_t (*send_clcc_cmd)();

    /** Send unknown vendor specific commands not is spec */
    bt_status_t (*send_unknown_at_cmd)(char* command);

    /** Enable/Disable phonebook download mode */
    bt_status_t (*config_pb_download_mode)(bthfdevice_pb_download_mode_t mode);

    /** Sends AT command for Indicator Activation. */
    bt_status_t  (*send_bia)( int enable_roam, int enable_service,
                                   int enable_signal, int enable_battery);

    /** Closes the interface. */
    void  (*cleanup)( void );

    // NREC and CMEE support will be available cofigurable in btif

    // TBD codec support APIs

} bthfdevice_interface_t;


__END_DECLS

#endif /* ANDROID_INCLUDE_BT_HFDEVICE_H */

