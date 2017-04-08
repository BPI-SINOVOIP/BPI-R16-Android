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

#ifndef ANDROID_INCLUDE_BT_MCE_H
#define ANDROID_INCLUDE_BT_MCE_H

__BEGIN_DECLS

/** If set, allows the MCE state information in stack to be dumped via API call */
//#define MCE_ENABLE_DEBUG_DUMPSTATE

/** Maximum length of a file or directory path*/
#define MAX_PATH_LENGTH 248

/** Maximum length of a name identifier */
#define MAX_NAME_LENGTH 21

/** Maximum length of a date/time string */
#define MAX_DATETIME_LENGTH 15

/** Maximum length of a string field in the message metadata structure*/
#define MAX_MSG_INFO_FIELD_LENGTH 256

/* MAP MCE profile service states */
typedef enum
{
    BTMCE_DISABLED = 0,
    BTMCE_ENABLED = 1,
} btmce_profile_state_t;

/* MAP MCE notification service states */
typedef enum
{
    BTMCE_STOPPED= 0,
    BTMCE_STARTED = 1,
} btmce_ns_state_t;

/* MAP MSE connection state to a MAP server (MSE) */
typedef enum
{
    BTMCE_DISCONNECTED = 0,
    BTMCE_CONNECTED = 1,
} btmce_connection_state_t;


/**
 * MAP message type (email,sms, mms)
 */
typedef enum {
    BTMCE_MSG_TYPE_UNKNOWN  = 0 ,
    BTMCE_MSG_TYPE_EMAIL    = (1<<0),
    BTMCE_MSG_TYPE_SMS_GSM  = (1<<1),
    BTMCE_MSG_TYPE_SMS_CDMA = (1<<2),
    BTMCE_MSG_TYPE_MMS      = (1<<3)
} btmce_msg_type_t;

/** BMessage charset */
typedef enum {
    BTMCE_CHARSET_NATIVE,
    BTMCE_CHARSET_UTF8
} btmce_charset_t;

/**
 * MAP BMessage status indicators
 */
typedef enum {
    BTMCE_MSG_STATUS_READ,
    BTMCE_MSG_STATUS_DELETE,
} btmce_msg_status_t;

/** MAP BMessage transfer status */
typedef enum {
  BTMCE_MSG_XFR_NOT_STARTED,
  BTMCE_MSG_XFR_IN_PROGRESS,
  BTMCE_MSG_XFR_COMPLETED,
} btmce_transfer_status_t;

/** Status of receiving a BMessage (complete ,fractioned)*/
typedef enum {
  BTMCE_MSG_RECEIVE_STATUS_COMPLETE,
  BTMCE_MSG_RECEIVE_STATUS_FRACTIONED,
  BTMCE_MSG_RECEIVE_STATUS_NOTIFICATION
} btmce_msg_receive_status_t;

/** MAP MSE operation status */
typedef enum {
  BTMCE_OP_STATUS_OK,
  BTMCE_OP_STATUS_FAIL,
  BTMCE_OP_STATUS_ABORT,
} btmce_op_status_t;

/** MAP I/O operation status */
typedef enum {
  BTMCE_IO_STATUS_OK,
  BTMCE_IO_STATUS_FAIL,
  BTMCE_IO_STATUS_ABORT,
  BTMCE_IO_STATUS_NO_RESOURCE,
  BTMCE_IO_STATUS_EACCESS,
  BTMCE_IO_STATUS_ENOTEMPTY,
  BTMCE_IO_STATUS_EOF,
  BTMCE_IO_STATUS_EODIR ,
  BTMCE_IO_STATUS_ENOSPACE,
} btmce_io_status_t;

/** MAP MSE operations that can be performed */
typedef enum {
    BTMCE_OP_UNKNOWN,
    BTMCE_OP_SETPATH,
    BTMCE_OP_GET_FOLDER_LIST,
    BTMCE_OP_GET_MSG_LIST,
    BTMCE_OP_GET_MSG,
    BTMCE_OP_SET_MSG_STATUS,
    BTMCE_OP_SET_DEL_MSG,
    BTMCE_OP_PUSH_MSG,
    BTMCE_OP_NOTIF_REG,
    BTMCE_OP_UPDATE_INBOX,
    BTMCE_OP_NOTIF_DEREG, //Added for notification deregistration
} btmce_operation_t;


/** MAP MSE notification events */
typedef enum
{
    BTMCE_NOTIF_NEW_MSG = 0,
    BTMCE_NOTIF_DELIVERY_SUCCESS,
    BTMCE_NOTIF_SENDING_SUCCESS,
    BTMCE_NOTIF_DELIVERY_FAILURE,
    BTMCE_NOTIF_SENDING_FAILURE,
    BTMCE_NOTIF_MEMORY_FULL,
    BTMCE_NOTIF_MEMORY_AVAILABLE,
    BTMCE_NOTIF_MESSAGE_DELETED,
    BTMCE_NOTIF_MESSAGE_SHIFT,
    BTMCE_NOTIF_MAX
} btmce_notification_t;

/** File/directory path*/
typedef struct {
    uint8_t path[MAX_PATH_LENGTH];
} __attribute__((packed))btmce_path_t;

/** MSE name*/
typedef struct {
    uint8_t name[MAX_NAME_LENGTH + 1];
} __attribute__((packed))btmce_name_t;

/* Date/Time field formated as  YYMMDDTHHMMSSZ */
typedef struct {
    uint8_t datetime[MAX_DATETIME_LENGTH + 1];
} __attribute__((packed))btmce_datetime_t;


/** Structure specifying filter criteria for a message listing query */
typedef struct {
    uint8_t    subject_length;
    uint8_t    message_mask;
    btmce_datetime_t period_begin;
    btmce_datetime_t period_end;
    uint8_t     read_status; //1= filter read, 2=filter unread, 0=no filter
    char recipient[MAX_MSG_INFO_FIELD_LENGTH+1];
    char originator[MAX_MSG_INFO_FIELD_LENGTH+1];
    uint8_t     is_priority; //1=filter priority, 2=filter non-priority, 0=no filter
} btmce_msg_list_filter_t;

/** Flags to indicate which part(s) of BMessage to return */
#define BTMCE_MSG_MASK_SUBJECT               (1<<0)
#define BTMCE_MSG_MASK_DATETIME              (1<<1)
#define BTMCE_MSG_MASK_SENDER_NAME           (1<<2)
#define BTMCE_MSG_MASK_SENDER_ADDRESSING     (1<<3)
#define BTMCE_MSG_MASK_RECIPIENT_NAME        (1<<4)
#define BTMCE_MSG_MASK_RECIPIENT_ADDRESSING  (1<<5)
#define BTMCE_MSG_MASK_TYPE                  (1<<6)
#define BTMCE_MSG_MASK_SIZE                  (1<<7)
#define BTMCE_MSG_MASK_RECEPTION_STATUS      (1<<8)
#define BTMCE_MSG_MASK_TEXT                  (1<<9)
#define BTMCE_MSG_MASK_ATTACHMENT_SIZE       (1<<10)
#define BTMCE_MSG_MASK_PRIORITY              (1<<11)
#define BTMCE_MSG_MASK_READ                  (1<<12)
#define BTMCE_MSG_MASK_SENT                  (1<<13)
#define BTMCE_MSG_MASK_PROTECTED             (1<<14)
#define BTMCE_MSG_MASK_REPLYTO_ADDRESSING    (1<<15)


/** BT MSE instance id */
typedef uint8_t  btmce_instance_id_t;

/** BT MSE session id */
typedef uint16_t btmce_session_id_t;

/** BT MSE event id */
typedef uint16_t btmce_event_id_t;

/** BT MSE message id */
typedef uint8_t  btmce_msg_handle_t[8];

/** Structure specifying MSE instance info returned from an SDP inquiry*/
typedef struct {
    btmce_instance_id_t  instance_id;
    uint8_t              message_types;
    btmce_name_t         display_name;
} btmce_mse_instance_info_t;

/** Sends an MCE profile service state change event  (enabled or disabled)
 *
 *  Parameters:
 *     state: state of the MAP MCE profile service (enabled or disabled)
 */
typedef void (*btmce_profile_state_callback)(btmce_profile_state_t state);

/** Sends an MCE notification server state change event  (enabled or disabled)
 *
 *  Parameters:
 *     state: state of the MAP MCE notification server (started or stopped)
 */
typedef void (*btmce_notification_server_state_callback)(btmce_ns_state_t state,
        btmce_session_id_t session_id);


/**
 *  Sends an MCE-MSE connection state change event
 *
 *  Parameters:
 *    instance_id: unique identifier to the MSE instance
 *    session_id:  unique identifier for the MCE-MSE session
 *    bd_name: name of the MAP client
 *    bd_addr: address of the MAP client
 *    connection_state: MSE connection state to the MAP client (connected or disconnected)
 *    status: indicates if the connect/disconnect succeeded or failed. if the request failed,
 *            indicates the failure reason.
 */
typedef void (*btmce_connection_state_callback)( btmce_instance_id_t instance_id,
                                                 btmce_session_id_t session_id,
                                                 bt_bdname_t* bd_name,
                                                 bt_bdaddr_t* bd_addr,
                                                 btmce_connection_state_t connection_state,
                                                 btmce_io_status_t status);

typedef void (*btmce_notification_connection_state_callback)( btmce_session_id_t session_id,
                                                 bt_bdname_t* bd_name,
                                                 bt_bdaddr_t* bd_addr,
                                                 btmce_connection_state_t connection_state,
                                                 btmce_io_status_t status);

/**
 *  Sends an folder path result event
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    status: indicates if the connect/disconnect succeeded or failed. if the request failed,
 *            indicates the failure reason.
 */
typedef void (*btmce_set_folder_path_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status);



/**
 *  Sends an get folder list result event
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    status: indicates if the connect/disconnect succeeded or failed. if the request failed,
 *            indicates the failure reason.
 *    list_size: The folder info list size
 *    file_path: The file path to where the folder info list is located.
 */
typedef void (*btmce_get_folder_list_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status,
                                                int list_size,
                                                btmce_path_t* file_path);


/**
 *  Sends an get message list result event
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    status: indicates if the connect/disconnect succeeded or failed. if the request failed,
 *            indicates the failure reason.
 *    list_size: The message info list size
 *    file_path: The file path to where the message info list is located.
 */
typedef void (*btmce_get_message_list_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status,
                                                int list_size,
                                                int has_new_msg,
                                                btmce_path_t* file_path);

typedef void (*btmce_get_message_callback)( btmce_session_id_t session_id,
                                            btmce_op_status_t status,
                                            btmce_msg_handle_t message_handle,
                                            btmce_path_t* file_path);

typedef void (*btmce_push_message_callback)( btmce_session_id_t session_id,
                                             btmce_op_status_t status,
                                             btmce_msg_handle_t message_handle);


typedef void (*btmce_update_inbox_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status);

typedef void (*btmce_message_status_updated_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status);

typedef void (*btmce_notification_registration_updated_callback)( btmce_session_id_t session_id,
                                                btmce_op_status_t status);

typedef void (*btmce_notification_callback)( btmce_instance_id_t instance_id,
                                             btmce_session_id_t session_id,
                                             btmce_path_t* file_path);

typedef void (*btmce_get_mse_instances_callback)(uint8_t size,
                                                 btmce_mse_instance_info_t* mse_instances);

typedef void (*btmce_get_mas_ins_info_callback)(btmce_session_id_t session_id,
                                                btmce_instance_id_t instance_id,
                                                btmce_op_status_t status,
                                                char* mas_ins_info);

/** BT-MSE callback structure. */
typedef struct {
    /** set to sizeof(BtMseCallbacks) */
    size_t                                size;

    //MSE events
    btmce_profile_state_callback             profile_state_cb;
    btmce_connection_state_callback          connection_state_cb;
    btmce_notification_server_state_callback     notification_server_state_cb;
    btmce_notification_connection_state_callback notification_connection_state_cb;
    btmce_set_folder_path_callback           set_folder_path_cb;
    btmce_get_folder_list_callback           get_folder_list_cb;
    btmce_get_message_list_callback          get_message_list_cb;
    btmce_get_message_callback               get_message_cb;
    btmce_push_message_callback              push_message_cb;
    btmce_update_inbox_callback              update_inbox_cb;
    btmce_message_status_updated_callback    message_status_updated_cb;
    btmce_notification_registration_updated_callback  notification_registration_updated_cb;
    btmce_notification_callback              notification_cb;
    btmce_get_mse_instances_callback         get_mse_instances_cb;
/*    btmce_get_mas_ins_info_callback          get_mas_ins_info_cb; */

} btmce_callbacks_t;


/** Represents the standard BT-MCE interface. */
typedef struct {

    /** set to sizeof(BtMceInterface) */
    size_t          size;

    /**
     * Register the BtMse callbacks
     */
    bt_status_t (*init)( btmce_callbacks_t* callbacks );

    /**
     * Cleanup MCE
     */
    bt_status_t (*cleanup)( void );

    /**
     * Starts the notification server
     *  Parameters:
     *    name: the name of the notification server advertised in SDP
     */
    bt_status_t (*start_notification_server)(btmce_name_t* name);

    /**
     * Stop  the notification server
     */
    bt_status_t (*stop_notification_server)(void);

    /**
     *  Get the MSE instances information from the remote device
     *
     *  Parameters:
     *    instance_id: unique identifier for the MSE instance
     *    bd_addr: address of the MAP client
     *
     */
    bt_status_t (*get_mse_instances)(bt_bdaddr_t *bd_addr);

    /**
     *  Connect to the specified MSE instance.
     *
     *  Parameters:
     *    instance_id: unique identifier for the MSE instance
     *    bd_addr: address of the MAP client
     *
     */
    bt_status_t (*connect)(bt_bdaddr_t *bd_addr,btmce_instance_id_t instance_id);


    /**
     *  Disconnects a MCE from the specified MSE.
     *
     *  Parameters:
     *    instance_id: unique identifier for the MSE instance
     *    bd_addr: address of the MAP client
     *
     */
    bt_status_t (*disconnect)(btmce_session_id_t session_id);

    /**
     *  Abort the current Obex operation, if running.
     *
     *  Parameters:
     *    instance_id: unique identifier for the MSE instance
     *    bd_addr: address of the MAP client
     */
    bt_status_t (*abort_request) (bt_bdaddr_t *bd_addr,btmce_instance_id_t instance_id);

    bt_status_t (*set_folder_path)(btmce_session_id_t session_id, int set_to_root,
            btmce_path_t folder_path);

    bt_status_t (*get_folder_list)(btmce_session_id_t session_id, int max_list_count,
            int start_offset);

    bt_status_t (*get_message_list)(btmce_session_id_t session_id, int max_list_count,
            int start_offset, btmce_msg_list_filter_t* list_filter, long param_filter);

    bt_status_t (*get_message)(btmce_session_id_t session_id, btmce_msg_handle_t message_handle,
            btmce_charset_t charset, int include_attachments);

    bt_status_t (*push_message)(btmce_session_id_t session_id, btmce_path_t* filepath,
            btmce_charset_t charset, int is_retry, int auto_send);

    bt_status_t (*update_inbox)(btmce_session_id_t session_id);
    bt_status_t (*register_for_notification)(btmce_session_id_t session_id);
    bt_status_t (*unregister_for_notification)(btmce_session_id_t session_id);
    bt_status_t (*set_message_status)(btmce_session_id_t session_id, btmce_msg_status_t status_type,
            btmce_msg_handle_t message_handle, int is_set);

    /**
     * MAP 1.2
     */
/*    bt_status_t (*get_mas_instance_info)(btmce_session_id_t session_id,
            btmce_instance_id_t instance_id);
*/
} btmce_interface_t;

__END_DECLS



#endif /* ANDROID_INCLUDE_BT_MCE_H */
