/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2012 Broadcom Corporation
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

package com.android.bluetooth.btservice;

/*
 * @hide
 */

final public class AbstractionLayer {
    // Do not modify without upating the HAL files.

    // TODO: Some of the constants are repeated from BluetoothAdapter.java.
    // Get rid of them and maintain just one.
    static final int BT_STATE_OFF = 0x00;
    static final int BT_STATE_ON = 0x01;
    static final int BT_RADIO_OFF = 0x02;
    static final int BT_RADIO_ON = 0x03;


    static final int BT_SCAN_MODE_NONE = 0x00;
    static final int BT_SCAN_MODE_CONNECTABLE = 0x01;
    static final int BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE = 0x02;

    static final int BT_PROPERTY_BDNAME = 0x01;
    static final int BT_PROPERTY_BDADDR = 0x02;
    static final int BT_PROPERTY_UUIDS = 0x03;
    static final int BT_PROPERTY_CLASS_OF_DEVICE = 0x04;
    static final int BT_PROPERTY_TYPE_OF_DEVICE = 0x05;
    static final int BT_PROPERTY_SERVICE_RECORD = 0x06;
    static final int BT_PROPERTY_ADAPTER_SCAN_MODE = 0x07;
    static final int BT_PROPERTY_ADAPTER_BONDED_DEVICES = 0x08;
    static final int BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT = 0x09;

    static final int BT_PROPERTY_REMOTE_FRIENDLY_NAME = 0x0A;
    static final int BT_PROPERTY_REMOTE_RSSI = 0x0B;

    static final int BT_DEVICE_TYPE_BREDR = 0x01;
    static final int BT_DEVICE_TYPE_BLE = 0x02;
    static final int BT_DEVICE_TYPE_DUAL = 0x03;

    static final int BT_BOND_STATE_NONE = 0x00;
    static final int BT_BOND_STATE_BONDED = 0x01;

    static final int BT_SSP_VARIANT_PASSKEY_CONFIRMATION = 0x00;
    static final int BT_SSP_VARIANT_PASSKEY_ENTRY = 0x01;
    static final int BT_SSP_VARIANT_CONSENT = 0x02;
    static final int BT_SSP_VARIANT_PASSKEY_NOTIFICATION = 0x03;

    static final int BT_DISCOVERY_STOPPED = 0x00;
    static final int BT_DISCOVERY_STARTED = 0x01;

    static final int BT_ACL_STATE_CONNECTED = 0x00;
    static final int BT_ACL_STATE_DISCONNECTED = 0x01;

    static final int BT_UUID_SIZE = 16; // bytes

    public static final int BT_STATUS_SUCCESS = 0;
    public static final int BT_STATUS_FAIL = 1;
    public static final int BT_STATUS_NOT_READY = 2;
    public static final int BT_STATUS_NOMEM = 3;
    public static final int BT_STATUS_BUSY = 4;
    public static final int BT_STATUS_DONE = 5;
    public static final int BT_STATUS_UNSUPPORTED = 6;
    public static final int BT_STATUS_PARM_INVALID = 7;
    public static final int BT_STATUS_UNHANDLED = 8;
    public static final int BT_STATUS_AUTH_FAILURE = 9;
    public static final int BT_STATUS_RMT_DEV_DOWN = 10;
    public static final int BT_STATUS_AUTH_REJECTED =11;
    public static final int BT_STATUS_AUTH_TIMEOUT = 12;

    //service Ids used by HAL/JNI  layer for notifying Service authorization request
    public static final int BT_RES_SERVICE_ID = 0;       // Reserved
    public static final int BT_SPP_SERVICE_ID = 1;       // Serial port profile
    public static final int BT_DUN_SERVICE_ID = 2;       //Dial-up networking profile
    public static final int BT_A2DP_SRC_SERVICE_ID = 3;  // A2DP Source profile.
    public static final int BT_LAP_SERVICE_ID = 4;       // LAN access profile.
    public static final int BT_HSP_SERVICE_ID = 5;       // Headset profile.
    public static final int BT_HFP_SERVICE_ID = 6;       // Hands-free profile.
    public static final int BT_OPP_SERVICE_ID = 7;       // Object push
    public static final int BT_FTP_SERVICE_ID = 8;       // File transfer
    public static final int BT_CTP_SERVICE_ID = 9;       // Cordless Terminal
    public static final int BT_ICP_SERVICE_ID = 10;      // Intercom Terminal
    public static final int BT_SYNC_SERVICE_ID = 11;     // Synchronization
    public static final int BT_BPP_SERVICE_ID = 12;      // Basic printing profile
    public static final int BT_BIP_SERVICE_ID = 13;      // Basic Imaging profile
    public static final int BT_PANU_SERVICE_ID = 14;     // PAN User
    public static final int BT_NAP_SERVICE_ID = 15;      // PAN Network access point
    public static final int BT_GN_SERVICE_ID = 16;       // PAN Group Ad-hoc networks
    public static final int BT_SAP_SERVICE_ID = 17;      // SIM Access profile
    public static final int BT_A2DP_SERVICE_ID = 18;     // A2DP Sink
    public static final int BT_AVRCP_SERVICE_ID = 19;    // A/V remote control
    public static final int BT_HID_SERVICE_ID = 20;      // HID
    public static final int BT_VDP_SERVICE_ID = 21;      // Video distribution
    public static final int BT_PBAP_SERVICE_ID = 22;     // PhoneBook Access Server
    public static final int BT_HSP_HS_SERVICE_ID = 23;   // HFP HS role
    public static final int BT_HFP_HS_SERVICE_ID = 24;   // HSP HS role
    public static final int BT_MAP_SERVICE_ID = 25;      // Message Access Profile
    public static final int BT_MN_SERVICE_ID = 26;       // Message Notification Service
    public static final int BT_HDP_SERVICE_ID = 27;      // Health Device Profile
    public static final int BT_PCE_SERVICE_ID = 28;      // PhoneBook Access Client

}
