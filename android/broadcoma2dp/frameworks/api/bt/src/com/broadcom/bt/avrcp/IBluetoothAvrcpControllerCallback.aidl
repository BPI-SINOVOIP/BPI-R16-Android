/*******************************************************************************
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
 *******************************************************************************/
package com.broadcom.bt.avrcp;

import android.bluetooth.BluetoothDevice;

import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;

/**
 * Callback definitions for interacting with AVRCP CT service
 * @hide
 */
interface IBluetoothAvrcpControllerCallback {
    void onConnectionStateChange(in BluetoothDevice target,
             in int newState, in boolean success);
    void onListPlayerApplicationSettingAttributesRsp(in BluetoothDevice target,
             in byte[] attributes, in int status);
    void onListPlayerApplicationSettingValuesRsp(in BluetoothDevice target,
             in byte attribute, in byte[] values, in int status);
    void onGetCurrentPlayerApplicationSettingValueRsp(in BluetoothDevice target,
             in byte[] attributes, in byte[] values, in int status);
    void onGetPlayerApplicationSettingAttributeTextRsp(in BluetoothDevice target,
             in byte[] attributes, in String[] attributeTexts, in int status);
    void onGetPlayerApplicationSettingValueTextRsp(in BluetoothDevice target,
             in byte attribute, in byte[] values, in String[] valueTexts, in int status);
    void onGetElementAttributesRsp(in BluetoothDevice target,
             in int[] attributes, in String[] valueTexts, in int status);
    void onGetPlayStatusRsp(in BluetoothDevice target, in int songLength,
             in int songPosition, in byte playStatus, in int status);
    void onSetBrowsedPlayerRsp(in BluetoothDevice target, in int numberOfItems,
             in String[] folderPath, in int status);
    void onChangePathRsp(in BluetoothDevice target, in byte direction, in int numberOfItems,
             in int status);
    void onGetFolderItemsRsp(in BluetoothDevice target, in byte scope,
             in BluetoothAvrcpBrowseItem[] items, in int status);
    void onGetItemAttributesRsp(in BluetoothDevice target, in int[] attributes,
             in String[] valueTexts, in int status);
    void onSearchRsp(in BluetoothDevice target, in int numberOfItems, in int status);
    void onPlayItemRsp(in BluetoothDevice target, in int status);
    void onAddToNowPlayingRsp(in BluetoothDevice target, in int status);
    void onPlaybackStatusChanged(in BluetoothDevice target, in byte playStatus);
    void onTrackChanged(in BluetoothDevice target, in long trackId);
    void onTrackReachedEnd(in BluetoothDevice target);
    void onTrackReachedStart(in BluetoothDevice target);
    void onPlaybackPositionChanged(in BluetoothDevice target, in int playbackPosition);
    void onPlayerAppSettingChanged(in BluetoothDevice target,
             in byte[] attributes, in byte[] values);
    void onAddressedPlayerChanged(in BluetoothDevice target, in int playerId);
    void onAvailablePlayersChanged(in BluetoothDevice target);
    void onNowPlayingContentChanged(in BluetoothDevice target);
    void onUIDsChanged(in BluetoothDevice target);
}
