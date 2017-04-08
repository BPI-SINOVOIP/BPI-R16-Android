/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  This program is the proprietary software of Broadcom Corporation and/or its
 *  licensors, and may only be used, duplicated, modified or distributed
 *  pursuant to the terms and conditions of a separate, written license
 *  agreement executed between you and Broadcom (an "Authorized License")
 *  Except as set forth in an Authorized License, Broadcom grants no license
 *  (express or implied), right to use, or waiver of any kind with respect to
 *  the Software, and Broadcom expressly reserves all rights in and to the
 *  Software and all intellectual property rights therein
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
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY
 *
 ************************************************************************************/

#ifndef ANDROID_INCLUDE_BT_FTS_H
#define ANDROID_INCLUDE_BT_FTS_H

#include <stdint.h>

__BEGIN_DECLS

#define FS_PERM_SIZE 3
#define FS_PATH_LEN  256
#define FS_FILE_LEN  294

/* FTS Operation status */
typedef enum {
    BTFTS_OPER_OK,
    BTFTS_OPER_FAIL
} btfts_operation_status_t;

/* FTS Operation events */
typedef enum {
    BTFTS_PUT_CMPL_EVT,
    BTFTS_GET_CMPL_EVT,
    BTFTS_DEL_CMPL_EVT,
    BTFTS_ENABLE_CMPL_EVT,
    BTFTS_DISABLE_CMPL_EVT
} btfts_event_t;

/* FTS connection states */
typedef enum
{
    BTFTS_CONN_STATE_CLOSE,
    BTFTS_CONN_STATE_OPEN
} btfts_connection_state_t;

typedef struct {
    uint32_t total_bytes; /* Total size of file */
    uint16_t bytes;       /* Number of bytes read/written since last event */
} btfts_progress_info_t;

typedef struct {
    uint8_t      *p_userid;
    uint8_t       userid_len;
    unsigned char userid_required; /* TRUE if user ID is
                                    required in response */
} btfts_auth_t;

typedef struct {
    uint8_t       oper;
    unsigned char access;
    uint32_t      size;    /* file size */
    bt_bdaddr_t   bd_addr; /* Address of device */
    bt_bdname_t   bd_name; /* Name of device, "" if unknown */
    char          file_name[FS_PATH_LEN+FS_FILE_LEN]; /* file name(fully qualified path)*/
    uint8_t       perms[FS_PERM_SIZE]; /* user/group/other permission */
} btfts_access_t;

typedef struct {
    char                    file_name[FS_PATH_LEN+FS_FILE_LEN]; /* file or folder name. */
    btfts_event_t            event;
    btfts_operation_status_t status;
} btfts_operation_cmpl_t;


/** Callback for FTS connection status.
 *  state will have one of the values from  btfts_connection_state_t
 */
typedef void (*btfts_connection_state_callback)( bt_bdaddr_t *bd_addr,
    btfts_connection_state_t state);

/** Callback for FTP request for Authentication key and realm
 */
typedef void (*btfts_auth_callback)( bt_bdaddr_t *bd_addr,
    btfts_auth_t param);

/** Callback for FTP request for access to put a file
 * param will contain details of the access request callback
 */
typedef void (*btfts_access_callback)( bt_bdaddr_t *bd_addr,
    btfts_access_t param);

/** Callback for FTP progress status.
 *  prog_info will contain details about FTP progress
 */
typedef void (*btfts_progress_callback)( bt_bdaddr_t *bd_addr,
    btfts_progress_info_t prog_info);

/** Callback for FTP operation complete
 *  state will have one of the values from  btfts_operation_cmpl_t
 */
typedef void (*btfts_operation_cmpl_callback)( bt_bdaddr_t *bd_addr,
    btfts_operation_cmpl_t param);


/** BT-FTS callback structure. */
typedef struct {
    /** set to sizeof(btftp_callbacks_t) */
    size_t      size;
    btfts_connection_state_callback conn_state_cb;
    btfts_auth_callback             auth_cb;
    btfts_access_callback           access_cb;
    btfts_progress_callback         progress_cb;
    btfts_operation_cmpl_callback   operation_cmpl_cb;
} btfts_callbacks_t;


/** Represents the standard BT-FTS interface. */
typedef struct {

    /** Sets to sizeof(btftp_interface_t)  */
    size_t          size;

    /**
     * Registers the BT-FTS callbacks
     */
    bt_status_t (*init)(btfts_callbacks_t* callbacks);

    /**
     * Sends a response to an access request event
     */
    bt_status_t (*access_rsp)(uint8_t oper_code, uint8_t access, char *file_name);

    /**
     * Sends a response to authentication request event.
     * An OBEX authentication challenge is sent to the connected
     * OBEX client.
     */
    bt_status_t (*auth_rsp)(char *user_name, char *password);

    /**
     * Disables the file transfer server and
     * Close the current connection
     */
    void (*cleanup)(void);

    /**
     * FTP don't working when SD card remove.
     * Disables the file transfer server and
     * Close the current connection without disble service
     */
    void (*closeftpserver)(void);
} btftp_interface_t ;
__END_DECLS

#endif /* ANDROID_INCLUDE_BT_FTS_H */

