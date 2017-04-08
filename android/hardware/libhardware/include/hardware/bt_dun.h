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
#ifndef ANDROID_INCLUDE_BT_DUN_H
#define ANDROID_INCLUDE_BT_DUN_H

__BEGIN_DECLS
/* RS-232 Signal Mask */
#define RFC_DTRDSR           0x01        /* DTR/DSR signal. */
#define RFC_RTSCTS           0x02        /* RTS/CTS signal. */
#define RFC_RI               0x04        /* Ring indicator signal. */
#define RFC_CD               0x08        /* Carrier detect signal. */

/* RS-232 Signal Values */
#define RFC_DTRDSR_ON        0x01        /* DTR/DSR signal on. */
#define RFC_DTRDSR_OFF       0x00        /* DTR/DSR signal off. */
#define RFC_RTSCTS_ON        0x02        /* RTS/CTS signal on. */
#define RFC_RTSCTS_OFF       0x00        /* RTS/CTS signal off. */
#define RFC_RI_ON            0x04        /* Ring indicator signal on. */
#define RFC_RI_OFF           0x00        /* Ring indicator signal off. */
#define RFC_CD_ON            0x08        /* Carrier detect signal on. */
#define RFC_CD_OFF           0x00        /* Carrier detect signal off. */


typedef enum {
    BTDUN_STATE_CONNECTED        = 0,
    BTDUN_STATE_CONNECTING       = 1,
    BTDUN_STATE_DISCONNECTING    = 2,
    BTDUN_STATE_DISCONNECTED     = 3,
    BTDUN_STATE_MODEM_CHANGED   = 4,
} btdun_connection_state_t;

typedef enum {
    BTDUN_STATE_ENABLED = 0,
    BTDUN_STATE_DISABLED = 1
} btdun_control_state_t;

/**
* Callback for pan connection state
*/
typedef void (*btdun_connection_state_callback)(btdun_connection_state_t state, bt_status_t error,
                                                const bt_bdaddr_t *bd_addr, int param1, int param2);
typedef void (*btdun_control_state_callback)(btdun_control_state_t state, bt_status_t error);

typedef struct {
    size_t size;
    btdun_control_state_callback control_state_cb;
    btdun_connection_state_callback connection_state_cb;
} btdun_callbacks_t;
typedef struct {
    /** set to size of this struct*/
    size_t          size;
    /**
     * Initialize the dun interface and register the btdun callbacks
     */
    bt_status_t (*init)(const btdun_callbacks_t* callbacks);
    /*
     * enable/disable the dun service by specified the type of gateway(server) or data terminal(client).
     * The result state will be returned by btdun_control_state_callback.
     */
    bt_status_t (*enable)(int enable, int server);
    /**
     * start bluetooth dun client connection. The result state will be
     * returned by btdun_connection_state_callback
     */
    bt_status_t (*connect)(const bt_bdaddr_t *bd_addr);
    /**
     * stop bluetooth dun connection. The result state will be returned by btdun_connection_state_callback
     */
    bt_status_t (*disconnect)(const bt_bdaddr_t *bd_addr);
    /**
     * get modem state of the dun connection
     */
    bt_status_t (*get_modem_state)(int dun_id);
    /**
     * set modem state of the dun connection
     */
    bt_status_t (*set_modem_state)(int dun_id, int signals, int values);
    /**
     * Cleanup the dun interface
     */
    void (*cleanup)(void);

} btdun_interface_t;

__END_DECLS

#endif /* ANDROID_INCLUDE_BT_DUN_H */
