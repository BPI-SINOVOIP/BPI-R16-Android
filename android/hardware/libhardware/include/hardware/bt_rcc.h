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

#ifndef ANDROID_INCLUDE_BT_RCC_H
#define ANDROID_INCLUDE_BT_RCC_H

__BEGIN_DECLS

/* Macros */
#define BTRCC_MAX_SETTING_STR_LEN       255
#define BTRCC_MAX_APP_SETTINGS          8
#define BTRCC_MAX_ABS_VOLUME            0x7F

#define BTRCC_UID_SIZE                  8
typedef uint8_t btrcc_uid_t[BTRCC_UID_SIZE];

typedef uint8_t btrcc_label_t;

typedef uint16_t btrcc_player_id_t;


typedef enum {
    SCOPE_MEDIA_PLAYER_LIST = 0x00,
    SCOPE_MEDIA_PLAYER_FILESYSTEM = 0x01,
    SCOPE_SEARCH = 0x02,
    SCOPE_NOW_PLAYING = 0x03
} btrcc_scope;

typedef uint8_t btrcc_scope_t;

/* RCC operation status */
typedef enum {
    BTRCC_STATUS_INVALID_CMD = 0x00,
    BTRCC_STATUS_INVALID_PARAM = 0x01,
    BTRCC_STATUS_PARAM_ERR = 0x02,
    BTRCC_STATUS_INTERNAL_ERR = 0x03,
    BTRCC_STATUS_NO_ERROR = 0x04,
} btrcc_status;

typedef uint32_t btrcc_status_t;


#define BTRCC_SUCCEEDED(s)    (s == BTRCC_STATUS_NO_ERROR)
#define BTRCC_FAILED(s)       (s != BTRCC_STATUS_NO_ERROR)

/* RCC connection state */
typedef enum
{
    BTRCC_DISCONNECTED = 0,
    BTRCC_CONNECTED = 1,
} btrcc_connection_state_t;

/* Player application setting attribute ID */
typedef enum {
    PLAYER_ATTR_EQUALIZER = 0x01,
    PLAYER_ATTR_REPEAT = 0x02,
    PLAYER_ATTR_SHUFFLE = 0x03,
    PLAYER_ATTR_SCAN = 0x04,
} btrcc_player_attr;

typedef uint8_t btrcc_player_attr_t;

/* Player application setting value ID */
typedef enum {
    PLAYER_VALUE_EQUALIZER_OFF = 0x01,
    PLAYER_VALUE_EQUALIZER_ON = 0x02,
} btrcc_player_value_equalizer_value_ids;

typedef enum {
    PLAYER_VALUE_REPEAT_OFF = 0x01,
    PLAYER_VALUE_REPEAT_SINGLE_TRACK = 0x02,
    PLAYER_VALUE_REPEAT_ALL_TRACK = 0x03,
    PLAYER_VALUE_REPEAT_GROUP = 0x04,
} btrcc_player_value_repeat_value_ids;

typedef enum {
    PLAYER_VALUE_SHUFFLE_OFF = 0x01,
    PLAYER_VALUE_SHUFFLE_ALL_TRACKS = 0x02,
    PLAYER_VALUE_SHUFFLE_GROUP = 0x03,
} btrcc_player_value_shuffle_value_ids;

typedef enum {
    PLAYER_VALUE_SCAN_OFF = 0x01,
    PLAYER_VALUE_SCAN_ALL_TRACKS = 0x02,
    PLAYER_VALUE_SCAN_GROUP = 0x03,
} btrcc_player_value_scan_value_ids;

typedef uint8_t btrcc_player_value_t;

/* Play status */
typedef enum {
    PLAY_STATUS_STOPPED = 0x00,
    PLAY_STATUS_PLAYING = 0x01,
    PLAY_STATUS_PAUSED = 0x02,
    PLAY_STATUS_FWD_SEEK = 0x03,
    PLAY_STATUS_REV_SEEK = 0x04,
} btrcc_play_status;

typedef uint8_t btrcc_play_status_t;

/* Notification event ID */
typedef enum {
    NOTIF_EVT_PLAY_STATUS_CHANGED = 0x01,
    NOTIF_EVT_TRACK_CHANGE = 0x02,
    NOTIF_EVT_TRACK_REACHED_END = 0x03,
    NOTIF_EVT_TRACK_REACHED_START = 0x04,
    NOTIF_EVT_PLAY_POS_CHANGED = 0x05,
    NOTIF_EVT_APP_SETTINGS_CHANGED = 0x08,
    NOTIF_EVT_NOW_PLAYING_CHANGED = 0x09,
    NOTIF_EVT_AVAIL_PLAYERS_CHANGED = 0x0a,
    NOTIF_EVT_ADDRESSED_PLAYER_CHANGED = 0x0b,
    NOTIF_EVT_UIDS_CHANGED = 0x0c,
} btrcc_notif_evt_id;

typedef uint8_t btrcc_notif_evt_id_t;

/* Pass Through commands */
typedef enum {
    PASS_CMD_PLAY = 0x44,
    PASS_CMD_STOP = 0x45,
    PASS_CMD_PAUSE = 0x46,
    PASS_CMD_RECORD = 0x47,
    PASS_CMD_REWIND = 0x48,
    PASS_CMD_FFWD = 0x49,
    PASS_CMD_EJECT = 0x4A,
    PASS_CMD_FORWARD = 0x4B,
    PASS_CMD_BACKWARD = 0x4C,
} btrcc_pass_cmd;

typedef uint8_t btrcc_pass_cmd_t;

/* Pass Through state */
typedef enum {
    PASS_STATE_PRESS = 0x00,
    PASS_STATE_RELEASE = 0x01,
} btrcc_pass_state;

typedef uint8_t btrcc_pass_state_t;

/* Media attribute IDs */
typedef enum {
    MEDIA_ATTR_TITLE = 0x01,
    MEDIA_ATTR_ARTIST = 0x02,
    MEDIA_ATTR_ALBUM = 0x03,
    MEDIA_ATTR_TRACK_NUM = 0x04,
    MEDIA_ATTR_NUM_TRACKS = 0x05,
    MEDIA_ATTR_GENRE = 0x06,
    MEDIA_ATTR_PLAYING_TIME = 0x07,
} btrcc_media_attr;

typedef uint32_t btrcc_media_attr_t;

/* Folder directions */
typedef enum {
    FOLDER_DIRECTION_UP   = 0x00,
    FOLDER_DIRECTION_DOWN = 0x01
} btrcc_folder_dir;

/* Player settings parameters */
typedef struct {
    uint8_t num_attr;
    btrcc_player_attr_t attr[BTRCC_MAX_APP_SETTINGS];
    btrcc_player_value_t value[BTRCC_MAX_APP_SETTINGS];
} btrcc_player_settings_t;

/* Player settings attribute/value text parameters */
typedef struct {
    uint8_t id; /* can be attr_id or value_id */
    uint16_t character_set;
    uint8_t string_len;
    uint8_t *text;
} btrcc_player_setting_text_t;

/* String types */
typedef struct {
    uint16_t length;
    uint8_t *string;
} btrcc_string_t;

typedef struct {
    uint16_t character_set;
    uint16_t length;
    uint8_t *string;
} btrcc_full_string_t;

/* Element attribute parameters */
typedef struct {
    uint32_t id; /* attribute id */
    btrcc_full_string_t attr_str;
} btrcc_element_attr_t;

#define BTRCC_FEATURE_MASK_SIZE 16

/* Media Player Browseable Item */
typedef struct {
    uint16_t player_id;
    uint8_t  player_type;
    uint32_t player_subtype;
    uint8_t  play_status;
    uint8_t  feature_mask[BTRCC_FEATURE_MASK_SIZE];
    btrcc_full_string_t name;
} btrcc_media_player_browse_item_t;

/* Folder Browseable Item */
typedef struct {
    btrcc_uid_t uid;
    uint8_t     type;
    uint8_t     is_playable;
   btrcc_full_string_t name;
} btrcc_folder_browse_item_t;

/* Media Element Browseable Item */
typedef struct {
    btrcc_uid_t uid;
    uint8_t     element_type;
    btrcc_full_string_t name;
    uint8_t     attr_count;
    btrcc_element_attr_t *attr_list;
} btrcc_media_element_browse_item_t;

/* Browseable item types */
typedef enum {
    ITEM_TYPE_MEDIA_PLAYER = 0x01,
    ITEM_TYPE_FOLDER = 0x02,
    ITEM_TYPE_MEDIA_ELEMENT = 0x03,
} btrcc_browse_item_type;

/* Browseable items */
typedef struct {
    uint8_t  item_type;
    union
    {
        btrcc_media_player_browse_item_t player;
        btrcc_folder_browse_item_t folder;
        btrcc_media_element_browse_item_t element;
    } item;
} btrcc_browse_item_t;

/* Notification parameters */
typedef union
{
    btrcc_play_status_t play_status;
    btrcc_uid_t track_id; /* queue position in NowPlaying */
    uint32_t song_pos;
    btrcc_player_settings_t player_setting;
    btrcc_player_id_t player_id;
    uint8_t volume;
} btrcc_notification_t;

/**
 * RCC connection state callback
 */
typedef void (*btrcc_connection_state_callback)(bt_bdaddr_t* bd_addr,
                                                bt_status_t status,
                                                btrcc_connection_state_t connection_state);

/**
 * Callback for ListPlayerApplicationSettingAttributes command response
 */
typedef void (*btrcc_list_player_attrs_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                     btrcc_label_t label,
                                                     btrcc_status_t status,
                                                     uint8_t num_attr,
                                                     btrcc_player_attr_t *p_attrs);

/**
 * Callback for ListPlayerApplicationSettingValues command response
 */
typedef void (*btrcc_list_player_values_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                      btrcc_label_t label,
                                                      btrcc_status_t status,
                                                      uint8_t num_val,
                                                      btrcc_player_value_t *p_vals);

/**
 * Callback for GetCurrentPlayerApplicationSettingValue command response
 */
typedef void (*btrcc_get_player_value_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                    btrcc_label_t label,
                                                    btrcc_status_t status,
                                                    btrcc_player_settings_t *p_attrs);

/**
 * Callback for GetPlayerApplicationSettingAttributeText command response
 */
typedef void (*btrcc_get_player_attrs_text_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                         btrcc_label_t label,
                                                         btrcc_status_t status,
                                                         uint8_t num_attr,
                                                         btrcc_player_setting_text_t *p_attrs);

/**
 * Callback for GetPlayerApplicationSettingValueText command response
 */
typedef void (*btrcc_get_player_values_text_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                          btrcc_label_t label,
                                                          btrcc_status_t status,
                                                          uint8_t num_val,
                                                          btrcc_player_setting_text_t *p_values);

/**
 * Callback for GetElementAttributes command response
 */
typedef void (*btrcc_get_element_attr_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                    btrcc_label_t label,
                                                    btrcc_status_t status,
                                                    uint8_t num_attr,
                                                    btrcc_element_attr_t *p_attrs);

/**
 * Callback for GetPlayStatus command response
 */
typedef void (*btrcc_get_play_status_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                   btrcc_label_t label,
                                                   btrcc_status_t status,
                                                   uint32_t song_len,
                                                   uint32_t song_pos,
                                                   btrcc_play_status_t play_status);

/**
 * Callback for SetBrowsedPlayer command response
 */
typedef void (*btrcc_set_browsed_player_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                    btrcc_label_t label,
                                                    btrcc_status_t status,
                                                    uint32_t num_item,
                                                    uint16_t character_set,
                                                    uint8_t folder_depth,
                                                    btrcc_string_t* p_folder_name);

/**
 * Callback for ChangePath command response
 */
typedef void (*btrcc_change_path_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                btrcc_label_t label,
                                                btrcc_status_t status,
                                                uint32_t num_item);

/**
 * Callback for GetFolderItems command response
 */
typedef void (*btrcc_get_folder_items_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                    btrcc_label_t label,
                                                    btrcc_status_t status,
                                                    uint16_t num_item,
                                                    btrcc_browse_item_t* p_items);

/**
 * Callback for GetItemAttributes command response
 */
typedef void (*btrcc_get_item_attributes_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                        btrcc_label_t label,
                                                        btrcc_status_t status,
                                                        uint8_t num_attr,
                                                        btrcc_element_attr_t *p_attrs);

/**
 * Callback for Search command response
 */
typedef void (*btrcc_search_rsp_callback)(bt_bdaddr_t* bd_addr,
                                        btrcc_label_t label,
                                        btrcc_status_t status,
                                        uint32_t num_item);

/**
 * Callback for PlayItem command response
 */
typedef void (*btrcc_play_item_rsp_callback)(bt_bdaddr_t* bd_addr,
                                            btrcc_label_t label,
                                            btrcc_status_t status);

/**
 * Callback for AddToNowPlaying command response
 */
typedef void (*btrcc_add_to_now_playing_rsp_callback)(bt_bdaddr_t* bd_addr,
                                                        btrcc_label_t label,
                                                        btrcc_status_t status);

/**
 * Callback to pass up a SetAbsoluteVolume command from the remote
 */
typedef void (*btrcc_set_absolute_volume_cmd_callback)(bt_bdaddr_t* bd_addr,
                                                     btrcc_label_t label,
                                                     uint8_t volume);
/**
 * Callback for Notification
 */
typedef void (*btrcc_notification_callback)(bt_bdaddr_t* bd_addr,
                                            btrcc_status_t status,
                                            btrcc_notif_evt_id_t event_id,
                                            btrcc_notification_t *p_notif);

/**
 * Callback for command response timeout
 */
typedef void (*btrcc_command_timeout_callback)(bt_bdaddr_t* bd_addr,
                                                btrcc_label_t label);



/** BT-RC callback structure. */
typedef struct {
    /** set to sizeof(BtRcCallbacks) */
    size_t      size;
    btrcc_connection_state_callback             connection_state_cb;
    btrcc_list_player_attrs_rsp_callback        list_player_attrs_rsp_cb;
    btrcc_list_player_values_rsp_callback       list_player_values_rsp_cb;
    btrcc_get_player_value_rsp_callback         get_player_value_rsp_cb;
    btrcc_get_player_attrs_text_rsp_callback    get_player_attrs_text_rsp_cb;
    btrcc_get_player_values_text_rsp_callback   get_player_values_text_rsp_cb;
    btrcc_get_element_attr_rsp_callback         get_element_attr_rsp_cb;
    btrcc_get_play_status_rsp_callback          get_play_status_rsp_cb;
    btrcc_set_browsed_player_rsp_callback       set_browsed_player_rsp_cb;
    btrcc_change_path_rsp_callback              change_path_rsp_cb;
    btrcc_get_folder_items_rsp_callback         get_folder_items_rsp_cb;
    btrcc_get_item_attributes_rsp_callback      get_item_attributes_rsp_cb;
    btrcc_search_rsp_callback                   search_rsp_cb;
    btrcc_play_item_rsp_callback                play_item_rsp_cb;
    btrcc_add_to_now_playing_rsp_callback       add_to_now_playing_rsp_cb;
    btrcc_set_absolute_volume_cmd_callback      set_absolute_volume_cmd_cb;
    btrcc_notification_callback                 notification_cb;
    btrcc_command_timeout_callback              command_timeout_cb;
} btrcc_callbacks_t;

/** Represents the standard BT-RCC interface. */
typedef struct {

    /** set to sizeof(BtRccInterface) */
    size_t          size;

    /**
     * Register the BtRcc callbacks
     */
    bt_status_t (*init)(btrcc_callbacks_t* callbacks);

    /**
     * Cleanup RCC
     */
    bt_status_t (*cleanup)(void);

    /**
     * Connect to remote TG
     */
    bt_status_t (*connect)(bt_bdaddr_t *bd_addr);

    /**
     * Disconnect CT from remote TG
     */
    bt_status_t (*disconnect)(bt_bdaddr_t* bd_addr);

    /**
     * Disconnect CT from remote TG
     */
    int (*get_target_features)(bt_bdaddr_t* bd_addr);

    /**
     * Send PASS THROUGH command
     */
    bt_status_t (*pass_through_cmd)(bt_bdaddr_t* bd_addr,
                                    btrcc_pass_cmd_t cmd,
                                    btrcc_pass_state_t state,
                                    uint8_t data_field_len,
                                    uint8_t *data_field);

    /**
     * Request the target device to provide target supported player application setting attributes
     */
    bt_status_t (*list_player_attrs)(bt_bdaddr_t* bd_addr, btrcc_label_t* p_label);

    /**
     * Requests the target device to list the set of possible values for the requested player
     * application setting attribute
     */
    bt_status_t (*list_player_values)(bt_bdaddr_t* bd_addr,
                                      btrcc_player_attr_t attr,
                                      btrcc_label_t* p_label);

    /**
     * Requests the target device to provide the current set values on the target for the provided
     * player application setting attributes list
     */
    bt_status_t (*get_player_value)(bt_bdaddr_t* bd_addr,
                                    uint8_t num_attr,
                                    btrcc_player_attr_t *p_attrs,
                                    btrcc_label_t* p_label);

    /**
     * Requests to set the player application setting list of player application setting values on
     * the target device
     */
    bt_status_t (*set_player_value)(bt_bdaddr_t* bd_addr,
                                    btrcc_player_settings_t *p_vals);

    /**
     * Requests the target device to provide the current set values on the target for the provided
     * player application setting attributes list
     */
    bt_status_t (*get_player_attrs_text)(bt_bdaddr_t* bd_addr,
                                         uint8_t num_attr,
                                         btrcc_player_attr_t *p_attrs,
                                         btrcc_label_t* p_label);

    /**
     * Request the target device to provide target supported player application setting value
     * displayable text
     */
    bt_status_t (*get_player_values_text)(bt_bdaddr_t* bd_addr,
                                          btrcc_player_attr_t attr,
                                          uint8_t num_val,
                                          btrcc_player_value_t *p_values,
                                          btrcc_label_t* p_label);

    /**
     * Requests the TG to provide the attributes of the element specified in the parameter
     */
    bt_status_t (*get_element_attr)(bt_bdaddr_t* bd_addr,
                                    btrcc_uid_t element_id,
                                    uint8_t num_attr,
                                    btrcc_media_attr_t *p_attrs,
                                    btrcc_label_t* p_label);

    /**
     * Get the status of the currently playing media at the TG
     */
    bt_status_t (*get_play_status)(bt_bdaddr_t* bd_addr, btrcc_label_t* p_label);

    /**
     * Set the player id to the player to be addressed on the TG
     */
    bt_status_t (*set_addressed_player)(bt_bdaddr_t* bd_addr,
                                        btrcc_player_id_t player_id);

    /**
     * Set the player id to the browsed player to be addressed on the TG
     */
    bt_status_t (*set_browsed_player)(bt_bdaddr_t* bd_addr,
                                      btrcc_player_id_t player_id,
                                      btrcc_label_t* p_label);

    /**
     * Change the path in the Virtual file system being browsed
     */
    bt_status_t (*change_path)(bt_bdaddr_t* bd_addr,
                               uint8_t direction,
                               btrcc_uid_t path_uid,
                               btrcc_label_t* p_label);

    /**
     * Retrieves a listing of the contents of a folder
     */
    bt_status_t (*get_folder_items)(bt_bdaddr_t* bd_addr,
                                    btrcc_scope_t scope,
                                    uint32_t start_item,
                                    uint32_t end_item,
                                    uint8_t num_attr,
                                    btrcc_media_attr_t *p_attrs,
                                    btrcc_label_t* p_label);

    /**
     * Retrieves the metadata attributes for a particular media element item or folder item
     */
    bt_status_t (*get_item_attributes)(bt_bdaddr_t* bd_addr,
                                       btrcc_scope_t scope,
                                       btrcc_uid_t path_uid,
                                       uint8_t num_attr,
                                       btrcc_media_attr_t *p_attrs,
                                       btrcc_label_t* p_label);

    /**
     * Performs search from the current folder in the Browsed Player's virtual file system
     */
    bt_status_t (*search)(bt_bdaddr_t* bd_addr,
                          btrcc_full_string_t search_string,
                          btrcc_label_t* p_label);

    /**
     * Starts playing an item indicated by the UID
     */
     bt_status_t (*play_item)(bt_bdaddr_t* bd_addr,
                              btrcc_scope_t scope,
                              btrcc_uid_t item_uid,
                              btrcc_label_t* p_label);

    /**
     * Adds an item indicated by the UID to the Now Playing queue
     */
     bt_status_t (*add_to_now_playing)(bt_bdaddr_t* bd_addr,
                                       btrcc_scope_t scope,
                                       btrcc_uid_t item_uid,
                                       btrcc_label_t* p_label);

    /**
     * Send an absolute volume response to the remote
     */
    void (*send_absolute_volume_rsp)(bt_bdaddr_t* bd_addr,
                                     uint8_t volume,
                                     btrcc_label_t p_label,
                                     btrcc_status_t status);

    /**
     * Cache an updated absolute volume setting from the service and
     * respond to any registered notification requests.
     */
    bt_status_t (*absolute_volume_update)(uint8_t volume);


} btrcc_interface_t;

__END_DECLS

#endif /* ANDROID_INCLUDE_BT_RCC_H */
