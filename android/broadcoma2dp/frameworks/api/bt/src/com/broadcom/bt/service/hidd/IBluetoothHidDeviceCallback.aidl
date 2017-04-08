/* Copyright 2013 Broadcom Corporation
**
** This program is the proprietary software of Broadcom Corporation and/or its
** licensors, and may only be used, duplicated, modified or distributed
** pursuant to the terms and conditions of a separate, written license
** agreement executed between you and Broadcom (an "Authorized License").
** Except as set forth in an Authorized License, Broadcom grants no license
** (express or implied), right to use, or waiver of any kind with respect to
** the Software, and Broadcom expressly reserves all rights in and to the
** Software and all intellectual property rights therein.
** IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
** SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
** ALL USE OF THE SOFTWARE.
**
** Except as expressly set forth in the Authorized License,
**
** 1.     This program, including its structure, sequence and organization,
**        constitutes the valuable trade secrets of Broadcom, and you shall
**        use all reasonable efforts to protect the confidentiality thereof,
**        and to use this information only in connection with your use of
**        Broadcom integrated circuit products.
**
** 2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
**        "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
**        REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
**        OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
**        DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
**        NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
**        ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
**        CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
**        OF USE OR PERFORMANCE OF THE SOFTWARE.
**
** 3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
**        ITS LICENSORS BE LIABLE FOR
**        (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
**              DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
**              YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
**              HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
**        (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
**              SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
**              LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
**              ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
*/

package com.broadcom.bt.service.hidd;

import android.bluetooth.BluetoothDevice;

/**
 * Callback interface for receiving events from the FTP profile service.
 *
 */
/**
 * Interface for receiving HID device events.
 *
 * Applications provide an implementation of this interface to receive HID
 * events
 *
 * {@link HidDevice} will invoke these callback methods on a distinct thread
 * when events occur in the HID device.
 *
 *
 */
interface IBluetoothHidDeviceCallback {
    void onEnable();
    void onEnableError(int errCode);
    void onDisable();
    void onDisableError(int errCode);

    /**
     * Event indicating a connection to the HID host was established
     *
     * @param hidHost
     *            the BluetoothDevice representing the HID host
     * @param isDeviceInitiated
     *            if true, the connection was initiated by the HID device via
     *            the connect() API. If false, the connection was initiated by
     *            the HID host and established after the HID device accepted the
     *            connection
     */
    void onConnected(in BluetoothDevice hidHost, boolean isDeviceInitiated);

    /**
     * Event indicating an error connecting to the HID host
     *
     * @param hidHost
     *            the hidHost that the connection was attempted. If the
     *            HIDDevice was listening for an incoming connection, this
     *            parameter is null
     * @param isDeviceInitiated
     *            if true, the connection was initiated by the HID device via
     *            the connect() API. If false, the connection was initiated by
     *            the HID host and established after the HID device accepted the
     *            connection
     * @param errorCode
     *            Identifier for the error that occured. Possible values are TBD
     */
    void onConnectError(in BluetoothDevice hidHost, boolean isDeviceInitiated,
        int errorCode);

    /**
     * Event indicating a disconnect from the HID host occured
     *
     * @param hidHost
     *            the hidHost that the device disconnected from.
     * @param isDeviceInitiated
     *            if true, the disconnect was initiated by the HID device via
     *            the disconnect() API. If false, the connection was
     *            disconnected by the HID host
     */
    void onDisconnected(in BluetoothDevice hidHost, boolean isDeviceInitiated);

    /**
     * Event indicating an error disconnecting from the HID host
     *
     * @param hidHost
     *            the hidHost that that the device was connected to.
     *
     * @param isDeviceInitiated
     *            if true, the disconnect was initiated by the HID device via
     *            the connect() API. If false, the disconnect was initiated by
     *            the HID host
     * @param errorCode
     *            Identifier for the error that occurred. Possible values are
     *            TBD
     */
    void onDisconnectError(in BluetoothDevice hidHost, boolean isDeviceInitiated,
        int errorCode);


    /**
     * Event indicating a virtual unplug from the HID host occured.
     * Currently not supported yet as Stack does not send Virtual Unplug event.
     *
     * @param hidHost
     *            the hidHost that the device disconnected from.
     * @param isDeviceInitiated
     *            if true, the Unplug was initiated by the HID device via
     *            the disconnect() API. If false, the Unplug was
     *            sent by the HID host
     */
    void onVirtualUnplug(in BluetoothDevice hidHost, boolean isDeviceInitiated);


    /**
     * Event indicating that the HID host sent a report
     *
     * @param reportData
     *            the contents of the report
     */
    void onReceiveReport(in byte[] reportData);

}
