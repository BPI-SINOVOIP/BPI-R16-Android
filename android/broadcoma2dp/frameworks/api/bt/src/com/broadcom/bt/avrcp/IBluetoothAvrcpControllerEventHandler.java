/******************************************************************************
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/


package com.broadcom.bt.avrcp;

import android.bluetooth.BluetoothDevice;
import java.util.List;

import com.broadcom.bt.avrcp.BluetoothAvrcpBrowseItem;


/** Interface for receiving AVRCP Controller callback events. */
public interface IBluetoothAvrcpControllerEventHandler {

    /**
     * Callback indicating an AVRCP connection state change
     *
     * @param target
     *            the Target device associated with the connection state change
     * @param newState
     *            Can be one of
     *              {@link BluetoothAvrcpController#STATE_DISCONNECTED}, {@link BluetoothAvrcpController#STATE_CONNECTING},
     *              {@link BluetoothAvrcpController#STATE_CONNECTED}, {@link BluetoothAvrcpController#STATE_DISCONNECTING}.
     *            This param is invalid if status is false
     * @param success
     *            true if it was a successful state change
     *            false if the previous connect/disconnect request failed
     */
    public void onConnectionStateChange(BluetoothDevice target,
                    int newState, boolean success);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#listPlayerApplicationSettingAttributes}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attributes
     *            list of attribute ID
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onListPlayerApplicationSettingAttributesRsp(BluetoothDevice target,
                    byte[] attributes, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#listPlayerApplicationSettingValues}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attribute
     *            the attribute that the values are returned for
     *            This param is invalid if status is false
     * @param values
     *            list of value ID
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onListPlayerApplicationSettingValuesRsp(BluetoothDevice target,
                    byte attribute, byte[] values, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getCurrentPlayerApplicationSettingValue}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attributes
     *            list of attribute ID
     *            This param is invalid if status is false
     * @param values
     *            list of current setting values
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetCurrentPlayerApplicationSettingValueRsp(BluetoothDevice target,
                    byte[] attributes, byte[] values, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getPlayerApplicationSettingAttributeText}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attributes
     *            list of attribute ID
     *            This param is invalid if status is false
     * @param attributeTexts
     *            list of attribute text
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetPlayerApplicationSettingAttributeTextRsp(BluetoothDevice target,
                    byte[] attributes, String[] attributeTexts, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getPlayerApplicationSettingValueText}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attribute
     *            the attribute that the values are returned for
     *            This param is invalid if status is false
     * @param values
     *            list of value ID
     *            This param is invalid if status is false
     * @param valueTexts
     *            list of value text
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetPlayerApplicationSettingValueTextRsp(BluetoothDevice target,
                    byte attribute, byte[] values, String[] valueTexts, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getElementAttributes}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attributes
     *            list of attribute ID
     *            This param is invalid if status is false
     * @param valueTexts
     *            list of value text
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetElementAttributesRsp(BluetoothDevice target,
                    int[] attributes, String[] valueTexts, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getPlayStatus}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param songLength
     *            leng of the current song
     *            This param is invalid if status is false
     * @param songPosition
     *            play position in the current song
     *            This param is invalid if status is false
     * @param playStatus
     *            play status
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetPlayStatusRsp(BluetoothDevice target, int songLength,
                    int songPosition, byte playStatus, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#setBrowsedPlayer}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param numberOfItems
     *            number of items in the current folder
     *            This param is invalid if status is false
     * @param folderPath
     *            path of the current folder
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onSetBrowsedPlayerRsp(BluetoothDevice target, int numberOfItems,
                    String[] folderPath, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#changePath}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param direction
     *            {@link #CHANGE_PATH_DIRECTION_UP} or {@link #CHANGE_PATH_DIRECTION_DOWN}
     * @param numberOfItems
     *            number of items in the folder which has been changed to
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onChangePathRsp(BluetoothDevice target, byte direction,
                    int numberOfItems, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getFolderItems}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param scope
     *            One of
     *            {@link #SCOPE_MEDIA_PLAYER_LIST}
     *            {@link #SCOPE_VIRTUAL_FILESYSTEM}
     *            {@link #SCOPE_SEARCH}
     *            {@link #SCOPE_NOW_PLAYING}
     * @param items
     *            list of items
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetFolderItemsRsp(BluetoothDevice target, byte scope,
                    BluetoothAvrcpBrowseItem[] items, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#getItemAttributes}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param attributes
     *            list of attribute ID
     *            This param is invalid if status is false
     * @param valueTexts
     *            list of value text
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onGetItemAttributesRsp(BluetoothDevice target, int[] attributes,
                    String[] valueTexts, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#search}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param numberOfItems
     *            number of items found in the search
     *            This param is invalid if status is false
     * @param status
     *            AVRCP response error/status code
     */
    public void onSearchRsp(BluetoothDevice target, int numberOfItems, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#playItem}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param status
     *            AVRCP response error/status code
     */
    public void onPlayItemRsp(BluetoothDevice target, int status);

    /**
     * Callback invoked in response to the command {@link BluetoothAvrcpController#addToNowPlaying}.
     *
     * @param target
     *            the Target device that sent back the response
     * @param status
     *            AVRCP response error/status code
     */
    public void onAddToNowPlayingRsp(BluetoothDevice target, int status);

    /**
     * Callback reporting a PLAYBACK STATUS CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     * @param playStatus
     *            Can be one of
     *            {@link BluetoothAvrcpController#PLAY_STATUS_STOPPED}, {@link BluetoothAvrcpController#PLAY_STATUS_PLAYING},
     *            {@link BluetoothAvrcpController#PLAY_STATUS_PAUSED}, {@link BluetoothAvrcpController#PLAY_STATUS_FWD_SEEK},
     *            {@link BluetoothAvrcpController#PLAY_STATUS_REV_SEEK}, {@link BluetoothAvrcpController#PLAY_STATUS_ERROR},
     */
    public void onPlaybackStatusChanged(BluetoothDevice target, byte playStatus);

    /**
     * Callback reporting a TRACK CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     * @param trackId
     *            index of the current track
     */
    public void onTrackChanged(BluetoothDevice target, long trackId);

    /**
     * Callback reporting a TRACK REACHED END event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     */
    public void onTrackReachedEnd(BluetoothDevice target);

    /**
     * Callback reporting a TRACK REACHED START event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     */
    public void onTrackReachedStart(BluetoothDevice target);

    /**
     * Callback reporting a PLAYBACK POSITION CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     * @param playbackPosition
     *            current playback position in millisecond
     */
    public void onPlaybackPositionChanged(BluetoothDevice target, int playbackPosition);

    /**
     * Callback reporting a PLAYER APPLICATION SETTING CHANGED event from target device.
     * Returns CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     * @param attributes
     *            list of attribute IDs. Can be one or more of
     *            {@link BluetoothAvrcpController#ATTRIBUTE_EQUALIZER}, {@link BluetoothAvrcpController#ATTRIBUTE_REPEAT},
     *            {@link BluetoothAvrcpController#ATTRIBUTE_SHUFFLE}, {@link BluetoothAvrcpController#ATTRIBUTE_SCAN},
     * @param values
     *            list of the respective new attribute values
     */
    public void onPlayerAppSettingChanged(BluetoothDevice target, byte[] attributes, byte[] values);

    /**
     * Callback reporting an ADDRESSED PLAYER CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     * @param playerId
     *            unique Media Player ID
     */
    public void onAddressedPlayerChanged(BluetoothDevice target, int playerId);

    /**
     * Callback reporting an AVAILABLE PLAYERS CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     */
    public void onAvailablePlayersChanged(BluetoothDevice target);

    /**
     * Callback reporting a NOW PLAYING CONTENT CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     */
    public void onNowPlayingContentChanged(BluetoothDevice target);

    /**
     * Callback reporting a UIDS CHANGED event from target device.
     *
     * @param target
     *            the Target device that sent back the notification event
     */
    public void onUIDsChanged(BluetoothDevice target);

}

