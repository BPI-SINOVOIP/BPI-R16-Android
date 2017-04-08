/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 Broadcom Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#ifndef ANDROID_INCLUDE_BT_OP_H
#define ANDROID_INCLUDE_BT_OP_H

__BEGIN_DECLS


/**OPP operation type (push, pull, exchange) */
typedef enum {
  BTOP_OPERATION_PUSH,
  BTOP_OPERATION_PULL,
  BTOP_OPERATION_EXCHANGE
} btop_operation_t;

/** OPP Object types **/
typedef enum {
  BTOP_OBJECT_VCARD_21,
  BTOP_OBJECT_VCARD_30,
  BTOP_OBJECT_VCAL_10,
  BTOP_OBJECT_ICAL_20,
  BTOP_OBJECT_VNOTE,
  BTOP_OBJECT_VMSG,
  BTOP_OBJECT_OTHER
} btop_object_t;

/* Max file paths must be consistent with stack configuration (currently char[513], including \0 */
#define OPP_MAX_PATH_LEN 512
#define OPP_FILE_LEN  294

typedef char bt_op_filepath_t[OPP_MAX_PATH_LEN+1];
typedef uint32_t bt_op_filesize_t;


/* OPC and OPS connection states , STATE DISABLE is valid only for OPS*/
typedef enum
{
    BTOP_STATE_ENABLE,
    BTOP_STATE_OPEN,
    BTOP_STATE_CLOSE,
    BTOP_STATE_DISABLE
} btop_state_t;


/*OPC status*/
typedef enum
{
    BTOPC_OPER_OK,
    BTOPC_OPER_FAIL,
    BTOPC_OBJ_NOT_FOUND,
    BTOPC_NO_PERMISSION,
    BTOPC_SRV_UNAVAIL,
    BTOPC_RSP_FORBIDDEN,
    BTOPC_RSP_NOT_ACCEPTABLE
} btopc_status_t;



typedef struct {
    char          file_name[OPP_MAX_PATH_LEN]; /* file name(fully qualified path)*/
    char          ptype[OPP_FILE_LEN];
    uint32_t      size;    /* file size */
    bt_bdname_t   bd_name; /* Name of device, "" if unknown */
    uint8_t       oper;
    uint8_t       fmt;
    bt_bdaddr_t   bd_addr; /* Address of device */
} btops_access_t;




/** Callback for opp client connection state change.
 *  this will map to opc open and close events
 *  State will have one of the values from btopc_status_t
 */
typedef void (* btopc_state_change_callback)( bt_bdaddr_t *bd_addr,
                                                  btop_state_t state,
                                                  btopc_status_t status);


/**  Callback for opp client transfer state change.
 */
typedef void (* btopc_transfer_state_callback)( bt_bdaddr_t *bd_addr,
                                                    bt_op_filepath_t filepath,
                                                    btopc_status_t transfer_state,
                                                    btop_operation_t operation);

/**  Callback for opp client transfer progress.
 */
typedef void (* btopc_transfer_progress_callback)( bt_bdaddr_t *bd_addr,
                                                        bt_op_filesize_t total,
                                                        bt_op_filesize_t current,
                                                        btop_operation_t operation);

/**call back for ops connection state*/
typedef void(* btops_state_change_callback)(bt_bdaddr_t *bd_addr ,
                                                btop_state_t state);


/**  Callback for ops access requesting users permission. */
typedef void (* btops_access_callback)(bt_bdaddr_t *bd_addr, btops_access_t param);


/** Callback for Object recieved */
typedef void(* btops_obj_callback)(bt_bdaddr_t *bd_addr,
                                      btop_object_t filetype, bt_op_filepath_t filepath);

/**  Callback for opp server transfer progress.      */
typedef void (* btops_transfer_progress_callback)(bt_bdaddr_t *bd_addr,
                                                       bt_op_filesize_t total,
                                                       bt_op_filesize_t current,
                                                       btop_operation_t operation);



/** BT-OPP callback structure. */
typedef struct {
    /** set to sizeof(btopc_callbacks_t) */
    size_t      size;
    /** OPC callbacks **/
    btopc_state_change_callback         opc_state_cb;
    btopc_transfer_state_callback       opc_transfer_state_cb;
    btopc_transfer_progress_callback    opc_transfer_progress_cb;
    /*OPS callbacks*/
    btops_state_change_callback         ops_state_cb;
    btops_access_callback               ops_access_cb;
    btops_obj_callback                  ops_obj_cb;
    btops_transfer_progress_callback    ops_transfer_progress_cb;
} btop_callbacks_t;

/** Represents the standard BT-OPC interface. */
typedef struct {

    /** set to sizeof(btop_interface_t) */
    size_t          size;

    /**  Register the BtOp callbacks and enable OPC and OPS */
    bt_status_t (*init)( btop_callbacks_t* callbacks );

    /** Push a file to remote device. */
    bt_status_t (*push)( bt_bdaddr_t *bd_addr, bt_op_filepath_t src_filepath, uint32_t fd);

    /** Pull a vcard file from remote device., */
    bt_status_t (*pull)( bt_bdaddr_t *bd_addr, bt_op_filepath_t dest_filepath);

    /** Exchange vcard files with remote device.    */
    bt_status_t (*exchange)( bt_bdaddr_t *bd_addr,
                             bt_op_filepath_t src_filepath, bt_op_filepath_t dest_filepath);

    /** Allow or deny OPP server request.     */
    bt_status_t (*ops_access_resp)(uint8_t operation, uint8_t permission,
                                  bt_op_filepath_t dest_filepath);

    /** Close current OPP server      */
    bt_status_t (*closeOps)( void );

    /** Close current OPP client .    */
    bt_status_t (*closeOpc)( void );

    /** Closes the interface. */
    void  (*cleanup)( void );
} btop_interface_t;




__END_DECLS

#endif /* ANDROID_INCLUDE_BT_OP_H */
