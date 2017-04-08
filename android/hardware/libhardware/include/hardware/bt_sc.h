/************************************************************************************
 *
 *  Copyright (C) 2009-2011 Broadcom Corporation
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

#ifndef ANDROID_INCLUDE_BT_SC_H
#define ANDROID_INCLUDE_BT_SC_H

#include <stdint.h>
#include <hardware/bluetooth.h>

__BEGIN_DECLS

/*****************************************************************************
**  Constants and data types
*****************************************************************************/

/* Results codes for use with Sim card driver  responses*/
#define BTSC_RESULT_OK                    0              /* Request processed correctly. */
#define BTSC_RESULT_ERROR                 1              /* Error - no reason specified. */
#define BTSC_RESULT_CARD_NOT_ACCESSIBLE   2              /* Error - card inserted but not accessible. */
#define BTSC_RESULT_CARD_POWERED_OFF      3              /* Error - card is powered off. */
#define BTSC_RESULT_CARD_REMOVED          4              /* Error - card is not inserted. */
#define BTSC_RESULT_CARD_ALREADY_ON       5              /* Error - card already turned on. */
#define BTSC_RESULT_DATA_NOT_AVAILABLE    6              /* Error - data not available. */
#define BTSC_RESULT_NO_SUPPORT            7              /* Error - Not supported. */

/* Request codes for use with call-in functions */
#define BTSC_REQUEST_APDU                 (BTSC_RESULT_OK +10)

/* SC unsolicited  card status */
#define BTSC_CARD_STATUS_UNKNOWN         0x00
#define BTSC_CARD_STATUS_RESET           0x01
#define BTSC_CARD_STATUS_NOT_ACCESSIBLE  0x02
#define BTSC_CARD_STATUS_REMOVED         0x03
#define BTSC_CARD_STATUS_INSERTED        0x04
#define BTSC_CARD_STATUS_RECOVERED       0x05

/* SC Card Reader status */
#define BTSC_CARD_READER_STATUS_ATTACHED  0x00
#define BTSC_CARD_READER_STATUS_REMOVED   0x01


/* SAP connection states */
typedef enum
{
    BTSC_CONN_STATE_CONNECTED              = 0,
    BTSC_CONN_STATE_DISCONNECTED,
    BTSC_CONN_STATE_UNKNOWN
} btsc_connection_state_t;

typedef enum
{
    BTSC_DISCONNECT_TYPE_GRACEFUL             = 0,
    BTSC_DISCONNECT_TYPE_IMMEDIATE
}btsc_disconnection_type_t;

typedef enum
{
    BTSC_OK                = 0,
    BTSC_ERR_DB_FULL,             /* device database full error, used  */
    BTSC_ERR_NO_RES,              /* out of system resources */
    BTSC_ERR_AUTH_FAILED,         /* authentication fail */
    BTSC_ERR_UNKNOWN
}btsc_status_t;

typedef enum
{
    BTSC_DISCONNECT_REQ,
    BTSC_SIM_ON_RESP,
    BTSC_SIM_OFF_RESP,
    BTSC_SIM_RESET_RESP,
    BTSC_SIM_ATR_RESP,
    BTSC_SIM_APDU_RESP,
    BTSC_SIM_CARD_STATUS,
    BTSC_SIM_CARD_READER_STATUS,
}btsc_sim_resp_type_t;

typedef struct
{
    uint16_t    result;
    uint16_t    resp_len;
    uint8_t   is_apdu_7816;
    uint8_t     resp_data[4];
}btsc_sim_resp_t;

typedef enum
{
    BTSC_SIM_OPEN_EVT,
    BTSC_SIM_CLOSE_EVT,
    BTSC_SIM_RESET_EVT,
    BTSC_SIM_ON_EVT,
    BTSC_SIM_OFF_EVT,
    BTSC_SIM_ATR_EVT,
    BTSC_SIM_APDU_EVT,
    BTSC_SIM_CR_STATUS_EVT

}btsc_client_req_type_t;

typedef struct
{
     uint16_t     req_len;
     uint16_t     rsp_maxlen;
     uint8_t      is_apdu_7816;
     uint8_t      req_data[4];
}btsc_client_req_t;



/** Callback for connection state change.
 *  state will have one of the values from btsc_connection_state_t
 */
typedef void (* btsc_connection_state_callback)(const bt_bdaddr_t *bd_addr,btsc_connection_state_t state);

typedef void (* btsc_client_request_callback ) (btsc_client_req_type_t req_type,
                                                btsc_client_req_t *req_param);

/** BT-SAP callback structure. */
typedef struct {
    /** set to sizeof(BtSapCallbacks) */
    size_t      size;
    btsc_connection_state_callback  connection_state_cb;
    btsc_client_request_callback    client_req_cb;
} btsc_callbacks_t;



/** Represents the standard BT-SAP interface. */
typedef struct {

    /** set to sizeof(btsc Interface) */
    size_t          size;

    /**
     * Register the BtSap callbacks
     */
    bt_status_t (*init)( btsc_callbacks_t* callbacks );

    /** dis-connect from SAP client */
    bt_status_t (*disconnect)(btsc_disconnection_type_t disc_type);

    /**  Response from SIM  card module to SAP Client */
    void (*sim_card_response) ( btsc_sim_resp_type_t resp_type,btsc_sim_resp_t *resp_param);

   /** Closes the interface. */
    void  (*cleanup)( void );

} btsc_interface_t;

__END_DECLS

#endif /* ANDROID_INCLUDE_BT_SC_H */


