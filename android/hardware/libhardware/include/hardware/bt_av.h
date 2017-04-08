/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_INCLUDE_BT_AV_H
#define ANDROID_INCLUDE_BT_AV_H

__BEGIN_DECLS

/* Bluetooth AV connection states */
typedef enum {
    BTAV_CONNECTION_STATE_DISCONNECTED = 0,
    BTAV_CONNECTION_STATE_CONNECTING,
    BTAV_CONNECTION_STATE_CONNECTED,
    BTAV_CONNECTION_STATE_DISCONNECTING
} btav_connection_state_t;

/* Bluetooth AV datapath states */
typedef enum {
    BTAV_AUDIO_STATE_REMOTE_SUSPEND = 0,
    BTAV_AUDIO_STATE_STOPPED,
    BTAV_AUDIO_STATE_STARTED,
} btav_audio_state_t;

typedef enum {
    BTAV_CP_TYPE_COPY_NEVER = 0x0,
    BTAV_CP_TYPE_COPY_ONCE,
    BTAV_CP_TYPE_COPY_FREE,
} btav_cp_type_t;

/** Callback for connection state change.
 *  state will have one of the values from btav_connection_state_t
 */
typedef void (* btav_connection_state_callback)(btav_connection_state_t state, 
                                                    bt_bdaddr_t *bd_addr);

/** Callback for audiopath state change.
 *  state will have one of the values from btav_audio_state_t
 */
typedef void (* btav_audio_state_callback)(btav_audio_state_t state, 
                                               bt_bdaddr_t *bd_addr);

typedef void (*btav_cp_type_callback)(uint8_t cp_enabled, btav_cp_type_t cp_type);
/** BT-AV callback structure. */
typedef struct {
    /** set to sizeof(btav_callbacks_t) */
    size_t      size;
    btav_connection_state_callback  connection_state_cb;
    btav_audio_state_callback audio_state_cb;
    btav_cp_type_callback cp_type_cb;
} btav_callbacks_t;

/** 
 * NOTE:
 *
 * 1. AVRCP 1.0 shall be supported initially. AVRCP passthrough commands
 *    shall be handled internally via uinput 
 *
 * 2. A2DP data path shall be handled via a socket pipe between the AudioFlinger
 *    android_audio_hw library and the Bluetooth stack.
 * 
 */
/** Represents the standard BT-AV interface. */
typedef struct {

    /** set to sizeof(btav_interface_t) */
    size_t          size;
    /**
     * Register the BtAv callbacks
     */
    bt_status_t (*init)( btav_callbacks_t* callbacks );

    /** connect to headset */
    bt_status_t (*connect)( bt_bdaddr_t *bd_addr );

    /** dis-connect from headset */
    bt_status_t (*disconnect)( bt_bdaddr_t *bd_addr );

    bt_status_t (*configure_cp)( btav_cp_type_t type);
    /** Closes the interface. */
    void  (*cleanup)( void );
} btav_interface_t;

__END_DECLS

#endif /* ANDROID_INCLUDE_BT_AV_H */
