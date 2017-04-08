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

#ifndef ANDROID_INCLUDE_BT_HD_H
#define ANDROID_INCLUDE_BT_HD_H

#include <stdint.h>

__BEGIN_DECLS

/* HD connection states */
typedef enum
{
    BTHD_CONN_STATE_CONNECTED              = 0,
    BTHD_CONN_STATE_CONNECTING,
    BTHD_CONN_STATE_DISCONNECTED,
    BTHD_CONN_STATE_DISCONNECTING,
    BTHD_CONN_STATE_CONNECTABLE,
    BTHD_CONN_STATE_CONNECT_FAILED,
    BTHD_CONN_STATE_DISCONNECT_FAILED,
    BTHD_CONN_STATE_FAILED_GENERIC,
    BTHD_CONN_STATE_UNKNOWN
} bthd_connection_state_t;

/* HD states */
typedef enum
{
    BTHD_DEV_STATUS_DISABLED              = 0,
    BTHD_DEV_STATUS_ENABLED,
    BTHD_DEV_STATUS_ENABLE_FAILED,
    BTHD_DEV_STATUS_DISABLE_FAILED,
    BTHD_DEV_STATUS_UNKNOWN
} bthd_device_status_t;

typedef enum
{
    BTHD_OK         = 0,
    BTHD_ERR,           /* general BTA HD error */
    BTHD_ERR_SDP        /* SDP error */
}bthd_status_t;

/* HID Device types */
typedef enum {
    BTHD_DEVICE_ID_SPEC     =0,
    BTHD_DEVICE_ID_KEYBOARD,
    BTHD_DEVICE_ID_POINTING,
    BTHD_DEVICE_ID_INVALID
}bthd_device_type_t;

/** Callback for connection state change.
 *
 *  Parameters:
 *  bd_addr : BD Address of Peer device
 *  state : will have one of the values from bthd_connection_state_t
 */
typedef void (* bthd_connection_state_callback)(bt_bdaddr_t *bd_addr,
                bthd_connection_state_t state);

/** Callback for device status change.
 *
 *  Parameters:
 *  status : Status will have one of the values from bthd_device_status_t
 */
typedef void (* bthd_device_status_callback)(bthd_device_status_t status);

/** Incoming Virtul unplug request from HID Host side.
 *
 *  Parameters:
 *  bd_addr : BD Address of Peer device
 *  hd_status : the status of the vitual unplug
 */
typedef void (* bthd_virtual_unplug_event)(bt_bdaddr_t *bd_addr, bthd_status_t hd_status);

/** Callback for received Output report
 *
 *  Parameters:
 *  bd_addr : BD Address of Peer device
 *  data : Output Report data
 */
typedef void (* bthd_output_report_callback)(bt_bdaddr_t *bd_addr, uint8_t* data);

/** BT-HD callback structure. */
typedef struct {
    /** set to sizeof(BtHdInterface) */
    size_t      size;
    bthd_connection_state_callback  connection_state_cb;
    bthd_device_status_callback     device_status_cb;
    bthd_virtual_unplug_event       virtual_unplug_event;
    bthd_output_report_callback     output_report_cb;
} bthd_callbacks_t;

/** Represents the standard BT-HD interface. */
typedef struct {

    /** set to sizeof(BtHdInterface) */
    size_t          size;

    /**
     * Register the BT-HD callbacks
     */
    bt_status_t (*init)( bthd_callbacks_t* callbacks );

    /**
     * Enable HID Device
     */
    bt_status_t (*enable)(void);

    /**
     * Disable HID Device
     */
    bt_status_t (*disable)(void);

    /** connect to hid host */
    bt_status_t (*connect)( bt_bdaddr_t *bd_addr);

    /** dis-connect from hid host */
    bt_status_t (*disconnect)( bt_bdaddr_t *bd_addr );

    /** Virtual UnPlug (VUP) from HID host device. */
    bt_status_t (*virtual_unplug)(bt_bdaddr_t *bd_addr);

    /** Send data to HID Host device. */
    bt_status_t (*send_data)(bt_bdaddr_t *bd_addr, bthd_device_type_t dev_type,
        uint8_t* p_rep_data, uint16_t rep_len);

    /** Set the SDP record for the HID Device. */
    bt_status_t (*set_sdp_record)(uint8_t *sdp_data, uint16_t data_len);

    /** Closes the interface. */
    void  (*cleanup)( void );

} bthd_interface_t;
__END_DECLS

#endif /* ANDROID_INCLUDE_BT_HD_H */


