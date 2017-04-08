/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
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

#ifndef ANDROID_INCLUDE_BT_MSE_H
#define ANDROID_INCLUDE_BT_MSE_H

__BEGIN_DECLS

/** If set, allows the MSE state information in stack to be dumped via API call */
#define ENABLE_DEBUG_DUMPSTATE

/** Maximum length of a file or directory path*/
#define MAX_PATH_LENGTH 248

/** Maximum length of a name identifier */
#define MAX_NAME_LENGTH 100

/** Maximum length of a date/time string */
#define MAX_DATETIME_LENGTH 20

/** Maximum length of a string field in the message metadata structure*/
#define MAX_MSG_INFO_FIELD_LENGTH 256

/* MAP MSE profile service states */
typedef enum
{
    BTMSE_DISABLED = 0,
    BTMSE_ENABLED = 1,
} btmse_profile_state_t;

/* MAP MSE profile service states */
typedef enum
{
    BTMSE_STOPPED= 0,
    BTMSE_STARTED = 1,
} btmse_mse_state_t;

/* MAP MSE connection state to a MAP client (MCE) */
typedef enum
{
    BTMSE_DISCONNECTED = 0,
    BTMSE_CONNECTED = 1,
} btmse_connection_state_t;

/* MAP MSE permission */
typedef enum
{
    BTMSE_ALLOWED = 0,
    BTMSE_DENIED = 1,
} btmse_permission_t;

/**
 * MAP message type (email,sms, mms)
 */
typedef enum {
    BTMSE_MSG_TYPE_UNKNOWN  = 0 ,
    BTMSE_MSG_TYPE_EMAIL    = (1<<0),
    BTMSE_MSG_TYPE_SMS_GSM  = (1<<1),
    BTMSE_MSG_TYPE_SMS_CDMA = (1<<2),
    BTMSE_MSG_TYPE_MMS      = (1<<3)
} btmse_msg_type_t;

/** BMessage charset */
typedef enum {
    BTMSE_CHARSET_NATIVE,
    BTMSE_CHARSET_UTF8
} btmse_charset_t;

/**
 * MAP BMessage status indicators
 */
typedef enum {
    BTMSE_MSG_STATUS_READ,
    BTMSE_MSG_STATUS_DELETE,
} btmse_msg_status_t;

/** MAP BMessage transfer status */
typedef enum {
  BTMSE_MSG_XFR_NOT_STARTED,
  BTMSE_MSG_XFR_IN_PROGRESS,
  BTMSE_MSG_XFR_COMPLETED,
} btmse_transfer_status_t;

/** Status of receiving a BMessage (complete ,fractioned)*/
typedef enum {
  BTMSE_MSG_RECEIVE_STATUS_COMPLETE,
  BTMSE_MSG_RECEIVE_STATUS_FRACTIONED,
  BTMSE_MSG_RECEIVE_STATUS_NOTIFICATION
} btmse_msg_receive_status_t;

/** MAP MSE operation status */
typedef enum {
  BTMSE_OP_STATUS_OK,
  BTMSE_OP_STATUS_FAIL,
  BTMSE_OP_STATUS_ABORT,
} btmse_op_status_t;

/** MAP I/O operation status */
typedef enum {
  BTMSE_IO_STATUS_OK,
  BTMSE_IO_STATUS_FAIL,
  BTMSE_IO_STATUS_ABORT,
  BTMSE_IO_STATUS_NO_RESOURCE,
  BTMSE_IO_STATUS_EACCESS,
  BTMSE_IO_STATUS_ENOTEMPTY,
  BTMSE_IO_STATUS_EOF,
  BTMSE_IO_STATUS_EODIR ,
  BTMSE_IO_STATUS_ENOSPACE,
} btmse_io_status_t;

/** MAP MSE operations that can be performed */
typedef enum {
    BTMSE_OP_UNKNOWN,
    BTMSE_OP_SETPATH,
    BTMSE_OP_GET_FOLDER_LIST,
    BTMSE_OP_GET_MSG_LIST,
    BTMSE_OP_GET_MSG,
    BTMSE_OP_SET_MSG_STATUS,
    BTMSE_OP_SET_DEL_MSG,
    BTMSE_OP_PUSH_MSG,
    BTMSE_OP_NOTIF_REG,
    BTMSE_OP_UPDATE_INBOX,
    BTMSE_OP_NOTIF_DEREG, //Added for notification deregistration
} btmse_operation_t;


/** MAP MSE notification events */
typedef enum
{
    BTMSE_NOTIF_NEW_MSG = 0,
    BTMSE_NOTIF_DELIVERY_SUCCESS,
    BTMSE_NOTIF_SENDING_SUCCESS,
    BTMSE_NOTIF_DELIVERY_FAILURE,
    BTMSE_NOTIF_SENDING_FAILURE,
    BTMSE_NOTIF_MEMORY_FULL,
    BTMSE_NOTIF_MEMORY_AVAILABLE,
    BTMSE_NOTIF_MESSAGE_DELETED,
    BTMSE_NOTIF_MESSAGE_SHIFT,
    BTMSE_NOTIF_MAX
} btmse_notification_t;

/** File/directory path*/
typedef struct {
    uint8_t path[MAX_PATH_LENGTH];
} __attribute__((packed))btmse_path_t;

/** MSE name*/
typedef struct {
    uint8_t name[MAX_NAME_LENGTH];
} __attribute__((packed))btmse_name_t;

/* Date/Time field formated as  YYMMDDTHHMMSSZ */
typedef struct {
    uint8_t datetime[MAX_DATETIME_LENGTH];
} __attribute__((packed))btmse_datetime_t;


/** Flags to indicate which part(s) of BMessage to return */
#define BTMSE_MSG_MASK_SUBJECT               (1<<0)
#define BTMSE_MSG_MASK_DATETIME              (1<<1)
#define BTMSE_MSG_MASK_SENDER_NAME           (1<<2)
#define BTMSE_MSG_MASK_SENDER_ADDRESSING     (1<<3)
#define BTMSE_MSG_MASK_RECIPIENT_NAME        (1<<4)
#define BTMSE_MSG_MASK_RECIPIENT_ADDRESSING  (1<<5)
#define BTMSE_MSG_MASK_TYPE                  (1<<6)
#define BTMSE_MSG_MASK_SIZE                  (1<<7)
#define BTMSE_MSG_MASK_RECEPTION_STATUS      (1<<8)
#define BTMSE_MSG_MASK_TEXT                  (1<<9)
#define BTMSE_MSG_MASK_ATTACHMENT_SIZE       (1<<10)
#define BTMSE_MSG_MASK_PRIORITY              (1<<11)
#define BTMSE_MSG_MASK_READ                  (1<<12)
#define BTMSE_MSG_MASK_SENT                  (1<<13)
#define BTMSE_MSG_MASK_PROTECTED             (1<<14)
#define BTMSE_MSG_MASK_REPLYTO_ADDRESSING    (1<<15)

/** Structure specifying filter criteria for a message listing query */
typedef struct {
    uint16_t     max_list_cnt;
    uint16_t     list_start_offset;
    uint8_t     subject_length;
    uint32_t     message_mask;
    btmse_datetime_t period_begin;
    btmse_datetime_t period_end;
    uint8_t     read_status; //1 indicates true, false otherwise
    char recipient[MAX_MSG_INFO_FIELD_LENGTH+1];
    char originator[MAX_MSG_INFO_FIELD_LENGTH+1];
    uint8_t     is_priority; //1 indicates true, false otherwise
} btmse_msg_filter_t;

/** Metadata about a message */
typedef struct {
    uint32_t message_info_mask; //Indicates the fields that are set for the message info
    btmse_msg_type_t  message_type;
    uint32_t  message_size;
    uint32_t  attachment_size;
    uint8_t has_text;  //1 = true, false otherwise
    uint8_t is_high_priority; //1 = true, false otherwise
    uint8_t is_read; //1 = true, false otherwise
    uint8_t is_sent; //1 = true, false otherwise
    uint8_t is_protected; //1 = true, false otherwise
    btmse_msg_receive_status_t reception_status;
    char subject[MAX_MSG_INFO_FIELD_LENGTH+1];
    char sender_name[MAX_MSG_INFO_FIELD_LENGTH+1];
    char sender_address[MAX_MSG_INFO_FIELD_LENGTH+1];
    char recipient_name[MAX_MSG_INFO_FIELD_LENGTH+1];
    char recipient_address[MAX_MSG_INFO_FIELD_LENGTH+1];
    char replyto_address[ MAX_MSG_INFO_FIELD_LENGTH+1];
    btmse_datetime_t date_time;
} btmse_msg_info_t;



/** BT MSE instance id */
typedef uint8_t  btmse_instance_id_t;

/** BT MSE session id */
typedef uint16_t btmse_session_id_t;

/** BT MSE event id */
typedef uint16_t btmse_event_id_t;

/** BT MSE message id */
typedef uint8_t  btmse_msg_handle_t[8];

/** Sends an MSE profile service state change event  (enabled or disabled)
 *
 *  Parameters:
 *     profile_state: state of the MAP MSE profile service (enabled or disabled)
 */
typedef void (*btmse_profile_state_callback)(btmse_profile_state_t profile_state);


/** Sends an MSE instance state change event  (enabled or disabled)
 *
 *  Parameters:
 *     instance_id: unique identifier for the MSE instance
 *     mse_state: state of the MSE instance
 */
typedef void (*btmse_mse_state_callback)(btmse_instance_id_t instance_id,btmse_profile_state_t mse_state);


/**
 *  Sends an MCE-MSE connection state change event
 *
 *  Parameters:
 *    instance_id: unique identifier to the MSE instance
 *    session_id:  unique identifier for the MCE-MSE session
 *    bd_name: name of the MAP client
 *    bd_addr: address of the MAP client
 *    connection_state: MSE connection state to the MAP client (connected or disconnected)
 *    status: indicates if the connect/disconnect succeeded or failed. if the request failed, indicates
 *            the failure reason.
 */
typedef void (*btmse_connection_state_callback)( btmse_instance_id_t instance_id,
                                                 btmse_session_id_t session_id,
                                                 bt_bdname_t* bd_name,
                                                 bt_bdaddr_t* bd_addr,
                                                 btmse_connection_state_t connection_state,
                                                 btmse_io_status_t status);

/**
 *  Sends an access request event
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    bd_name: name of the MAP client
 *    bd_addr: address of the MAP client
 *    operation: the operation access/permission is being requested for
 *    message_handle: the unique identifier for the message the operation will be performed on
 *    is_action_set : if set to 1, indicates that the operation action is being requested.
 *                      Otherwise, the inverse action is requested.
 */
typedef void (*btmse_access_callback)( btmse_session_id_t session_id,
                                       bt_bdname_t* bd_name,
                                       bt_bdaddr_t* bd_addr,
                                       btmse_operation_t operation,
                                       btmse_path_t* path,
                                       btmse_msg_handle_t message_handle,
                                       uint8_t is_action_set);


/**
 *  Sends an event to report to the registration status of an MCE with the MCE for
 *  MAP notification events
 *
 *  Parameters:
 *    instance_id: unique identifier to the MSE instance
 *    session_id:  unique identifier for the MCE-MSE session
 *    bd_addr: address of the MAP client
 *    status: Indicates if the register or unregister completed successfully
 *    is_registered: if set to 1, indicates the MCE is registered for MAP notification events.
 *                   Otherwise, the MCE is not registered for MAP notification events.
 *
 *    API NOTES:
 *    IF MCE successfully registered,         status=BTMSE_OP_STATUS_OK, is_registered=1
 *    IF MCE did not register,                status=BTMSE_OP_STATUS_FAIL, is_registered=0
 *    IF MCE successfully unregistered,       status=BTMSE_OP_STATUS_OK, is_registered=0
 *    IF MCE did not unregister successfully, status=BTMSE_OP_STATUS_FAIL, is_registered=1
 *
 */
typedef void (*btmse_registration_changed_callback)( btmse_instance_id_t instance_id,
                                                      btmse_session_id_t session_id,
                                                      bt_bdaddr_t* bd_addr,
                                                      uint8_t is_registered);

/**
 *  Sends an event to report to the transfer status of a push BMessage request
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    transfer_status: indicates the transfer state (not started, in progress, completed)
 *    status: If completed, indicates if the transfer completed successfully
 *    total_transfered: total bytes transfered
 *
 */
typedef void (*btmse_push_message_transfer_callback)( btmse_session_id_t session_id,
                                                      btmse_transfer_status_t transfer_status,
                                                      btmse_op_status_t status,
                                                      uint32_t total_transferred);

/**
 *  Sends an event to report to the transfer status of a get BMessage request
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    transfer_status: indicates the transfer state (not started, in progress, completed)
 *    status: If completed, indicates if the transfer completed successfully
 *    total_transfered: total bytes transfered
 */
typedef void (*btmse_get_message_transfer_callback)( btmse_session_id_t session_id,
                                                     btmse_transfer_status_t transfer_status,
                                                     btmse_op_status_t status,
                                                     uint32_t total_transferred);


/**
 *  Sends an event to indicate if a notification was successfully sent to the MCE
 *
 *  Parameters:
 *    instance_id: unique identifier for the MSE instance
 *    bd_addr: address of the MAP client
 *    status: Indicates if the notification was successfully sent
 */
typedef void (*btmse_send_notification_callback) ( btmse_instance_id_t instance_id,
                                                   bt_bdaddr_t* bd_addr,
                                                   btmse_op_status_t status);


/**
 *  Update an MSE inbox
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 */
typedef void (*btmse_update_inbox_callback)(btmse_session_id_t session_id);


/**
 *  Sends a request to the MSE data provider to change to the specified folder_path
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    folder_path: virtual folder path to set on the MSE.
 *                 The folder path can be an absolute path or a relative path
 */
typedef void (*btmse_set_folder_callback)(btmse_session_id_t session_id, btmse_path_t* folder_path);

/**
 *  Sends an request to the MSE data provider to get a folder entry
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    event_id:    internal unique identifier that must be returned to the stack
 *                 unique identifier for the MCE-MSE session
 *    folder_path: virtual path to the MSE folder.
 *                 The folder path can be an absolute path or a relative path.
 *    is_first_entry: if set to 1, indicates the entry requested is the first entry in the folder
 *    entry_id: the unique identifier for the folder entry
 */
typedef void (*btmse_get_folder_entry_callback)( btmse_session_id_t session_id,
                                                 btmse_event_id_t event_id,
                                                 btmse_path_t* folder_path,
                                                 uint8_t is_first_entry,  uint32_t entry_id);

/**
 *  Sends an request to the MSE data provider to get information about a list of messages in an MSE folder
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    event_id:    internal unique identifier that must be returned to the stack
 *                 unique identifier for the MCE-MSE session
 *    folder_path: virtual path to the MSE folder.
 *                 The folder path can be an absolute path or a relative path.
 *    filter:      filter criteria for the messages to be return
 *    parameter_mask: specifies the message fields to return in the message listing object
 */
typedef void (*btmse_get_message_list_info_callback)( btmse_session_id_t session_id,
                                                      btmse_event_id_t event_id,
                                                      btmse_path_t* folder_path,
                                                      btmse_msg_filter_t* filter,
                                                      uint32_t parameter_mask);

/**
 *  Sends an request to the MSE data provider to get a message entry
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    event_id:    internal unique identifier that must be returned to the stack
 *                 unique identifier for the MCE-MSE session
 *    folder_path: virtual path to the MSE folder.
 *                 The folder path can be an absolute path or a relative path.
 *    is_first_entry: if set to 1, indicates the entry requested is the first entry in the folder
 *    entry_id: the unique identifier for the mesage entry
 *    filter:      criteria to for the message entries to return
 *    parameter_mask: specifies the message fields to return in the message listing object
 */
typedef void (*btmse_get_message_entry_callback)( btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_path_t* folder_path,
                                                  uint8_t is_first_entry,
                                                  uint32_t entry_id,
                                                  btmse_msg_filter_t* filter,
                                                  uint32_t parameter_mask);

/**
 *  Sends an request to the MSE data provider to get a message entry
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    event_id:    internal unique identifier that must be returned to the stack
 *                 unique identifier for the MCE-MSE session
 *    message_handle:  the unique identifier for the message
 *    include_attachments:  if 1, returns attachments associated to the message
 *    charset: the character set to use in the returned message
 */
typedef void (*btmse_get_message_callback)( btmse_session_id_t session_id,
                                            btmse_event_id_t event_id,
                                            btmse_msg_handle_t message_handle,
                                            uint8_t include_attachments,
                                            btmse_charset_t charset);

/**
 *  Sends a request to the MSE data provider to set a message's status
 *
 *  Parameters:
 *    session_id:  unique identifier for the MCE-MSE session
 *    event_id:    internal unique identifier that must be returned to the stack
 *                 unique identifier for the MCE-MSE session
 *    message_handle:  the unique identifier for the message
 *    message_status_type:  the type of message status to set
 *    is_status_set: if 1, the status is set. Otherwise, the status is not set.
 */
typedef void (*btmse_set_message_status_callback)( btmse_session_id_t session_id,
                                                   btmse_event_id_t event_id,
                                                   btmse_msg_handle_t message_handle,
                                                   btmse_msg_status_t message_status_type,
                                                   uint8_t is_status_set);


/**
 *  Sends an event indicating a push message request to the MSE data provider
 *
 *  Parameters:
 *    session_id:   unique identifier for the MCE-MSE session
 *    event_id:     internal unique identifier that must be returned to the stack
 *                  unique identifier for the MCE-MSE session
 *    message_handle:   the unique identifier for the message
 *    folder_path:  virtual path to the MSE folder.
 *                  The folder path can be an absolute path or a relative path.
 *    save_in_sent: If 1, and the push it to the outbox, the message will be moved
 *                  to the sent box after sucessfully sent to recipient.
 *    retry:        If true, then the push will automatically be retried if it previously failed
 *    charset:      the character set to use in the returned message
 */
typedef void (*btmse_push_message_callback)( btmse_session_id_t session_id,
                                             btmse_event_id_t event_id,
                                             btmse_path_t* message_file_path,
                                             char* folder_path,
                                             uint8_t save_in_sent,
                                             uint8_t retry,
                                             btmse_charset_t charset);


/** BT-MSE callback structure. */
typedef struct {
    /** set to sizeof(BtMseCallbacks) */
    size_t                                size;
    //MSE events
    btmse_profile_state_callback          profile_state_cb;
    btmse_mse_state_callback              mse_state_cb;
    btmse_connection_state_callback       connection_state_cb;
    btmse_access_callback                 access_cb;
    btmse_send_notification_callback      send_notification_cb;
    btmse_push_message_transfer_callback  push_message_transfer_cb;
    btmse_get_message_transfer_callback   get_message_transfer_cb;
    btmse_registration_changed_callback   registration_changed_cb;

    //MSE callout requests
    btmse_update_inbox_callback           update_inbox_cb;
    btmse_set_folder_callback             set_folder_cb;
    btmse_get_folder_entry_callback       get_folder_entry_cb;
    btmse_get_message_list_info_callback  get_message_list_info_cb;
    btmse_get_message_entry_callback      get_message_entry_cb;
    btmse_get_message_callback            get_message_cb;
    btmse_set_message_status_callback     set_message_status_cb;
    btmse_push_message_callback           push_message_cb;

} btmse_callbacks_t;


/** Represents the standard BT-MSE interface. */
typedef struct {

    /** set to sizeof(BtMseInterface) */
    size_t          size;

    /**
     * Register the BtMse callbacks
     */
    bt_status_t (*init)( btmse_callbacks_t* callbacks );

    /**
     * Cleanup MSE
     */
    bt_status_t (*cleanup)( void );

    bt_status_t (*init_tmp_dir) (btmse_path_t* path, uint8_t key_tmp_files);

    bt_status_t (*start_mse)(uint8_t mse_msg_type, btmse_instance_id_t instance_id, btmse_name_t* mse_name, btmse_path_t* mse_root_path) ;

    bt_status_t (*stop_mse)(btmse_instance_id_t instance_id) ;

    /**
     *  Disconnects a MCE from the specified MSE.
     *
     *  Parameters:
     *    instance_id: unique identifier for the MSE instance
     *    bd_addr: address of the MAP client
     *
     */
    bt_status_t (*disconnect)(btmse_instance_id_t instance_id, bt_bdaddr_t *bd_addr);

    /**
     *  Return a message deleted response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    status:     If deleted successfully, status=BTMSE_IO_STATUS_OK.
     */
    bt_status_t (*set_message_deleted_response)(  btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_io_status_t status);

    /**
     *  Return a push message response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    message_handle: unique identifier for the message
     *    status:     If message pushed successfully, status=BTMSE_IO_STATUS_OK.
     */
    bt_status_t (*push_message_response)       (  btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_msg_handle_t message_handle,
                                                  btmse_io_status_t status);


    /**
     *  Return a get folder entry response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    status:     indicates if the folder entry retrieval succeeded
     *    next_folder_entry: unique identifier for the next folder entry after this one.
     *    folder_name: the name of the folder
     *    creation_datetime: the date/time the folder was created
     *    is_read_only: if 1, indicates the folder is read only
     */
    bt_status_t (*get_folder_entry_response)   (  btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_io_status_t status,
                                                  uint32_t next_folder_entry_id,
                                                  uint32_t file_size,
                                                  btmse_path_t* folder_name,
                                                  btmse_datetime_t* creation_datetime,
                                                  uint8_t is_read_only);

    /**
     *  Return a get message list response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    status: indicates if the get message list request succeeded or failed
     *    list_size:  number of messages in the list
     *    creation_datetime: date/time message was created
     *    has_new_messages: if 1, indicates there are new messages in the list.
     */
    bt_status_t (*get_message_list_response)      ( btmse_session_id_t session_id,
                                                    btmse_event_id_t event_id,
                                                    uint16_t list_size,
                                                    btmse_datetime_t* creation_datetime,
                                                    uint8_t has_new_messages
                                                    );

    /**
     *  Return a get message entry response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    status: indicates if the get message entry request succeeded or failed
     *    next_message_entry_id: unique identifier for the next message entry after this one.
     *    message_handle:  unique identifier for the message
     *    message_info: metadata describing the message
     *
     */
    bt_status_t (*get_message_entry_response)   ( btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_io_status_t status,
                                                  uint32_t next_message_entry_id,
                                                  btmse_msg_handle_t message_handle,
                                                  btmse_msg_info_t* message_info);

    /**
     *  Return a get message response to the MCE
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    event_id:   internal unique identifier passed from the stack in the corresponding event callback API
     *    status: indicates if the get message request succeeded or failed
     *    message_file_path: the absolute file path containing the BMessage content
     *
     */
    bt_status_t (*get_message_response)         ( btmse_session_id_t session_id,
                                                  btmse_event_id_t event_id,
                                                  btmse_io_status_t status,
                                                  btmse_path_t* message_file_path);
    /**
     *  Return the response to an access request
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    notification_type: type of notification to send
     *    message_type: type of MAP message (sms,mms,email)
     *    message_handle: unique identifier for the message
     *    folder: this field is only set of notification type is BTMSE_NOTIF_MESSAGE_SHIFT.
     *            Indicates the folder the message has moved to
     *    old_folder: this field is only set of notification type is BTMSE_NOTIF_MESSAGE_SHIFT.
     *            Indicates the folder the message has moved from
     */
    bt_status_t (*access_response)         (  btmse_session_id_t session_id,
                                              btmse_operation_t,
                                              btmse_path_t* path,
                                              btmse_permission_t permission);

    /**
     *  Send notification to all MCEs
     *
     *  Parameters:
     *    session_id: unique identifier for the MCE-MSE session
     *    notification_type: type of notification to send
     *    message_type: type of MAP message (sms,mms,email)
     *    message_handle: unique identifier for the message
     *    folder: this field is only set of notification type is BTMSE_NOTIF_MESSAGE_SHIFT.
     *            Indicates the folder the message has moved to
     *    old_folder: this field is only set of notification type is BTMSE_NOTIF_MESSAGE_SHIFT.
     *            Indicates the folder the message has moved from
     */
    bt_status_t (*send_notification)(  btmse_instance_id_t instance_id,
                                          btmse_notification_t notification_type,
                                          btmse_msg_type_t message_type,
                                          btmse_msg_handle_t message_handle,
                                          btmse_path_t* folder, btmse_path_t* old_folder);

#ifdef ENABLE_DEBUG_DUMPSTATE
    /**
     * Debugging function to dump the current state of the MAP service
     */
    bt_status_t (*debug_dump_state)();
#endif
} btmse_interface_t;

__END_DECLS



#endif /* ANDROID_INCLUDE_BT_MSE_H */
