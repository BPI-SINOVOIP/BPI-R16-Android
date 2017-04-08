/*******************************************************************************
 *
 *  Copyright (C) 2012-2013 Broadcom Corporation
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
package com.broadcom.bt.map;

import com.broadcom.bt.util.bmsg.BMessage;

import android.bluetooth.BluetoothDevice;
import android.net.Uri;

/**
 * Event callback interface invoked by MAP client to notify application.
 *
 * <p/>
 * Application must implement this interface and register an instance of this
 * interface with the
 * {@link BluetoothMapClient.#registerEventHandler(IBluetoothMapClientEventHandler)}
 * method.
 */
public interface IBluetoothMapClientEventHandler {
    /**
     * Callback invoked when MAP Notification Server (MNS) has started.
     * <p>
     * Response to {@link BluetoothMapClient#startNotificationServer(String)} request.
     *
     * @param success
     *            If true, {@link BluetoothMapClient#startNotificationServer(String)}
     *            request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onNotificationServerStarted(boolean success);

    /**
     * Callback invoked when MAP Notification Server (MNS) has stopped.
     * <p>
     * Response to {@link BluetoothMapClient#stopNotificationServer()} request.
     *
     * @param success
     *            If true, {@link BluetoothMapClient#stopNotificationServer()}
     *             request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onNotificationServerStopped(boolean success);

    /**
     * Callback in response to connection initiation to MAP server from MAP client.
     * <p>
     * Response to {@link BluetoothMapClient#connect(int)} request.
     *
     * @param server
     *            remote MAP server to which connection was initiated.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#connect(int)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onServerConnected(BluetoothDevice server, int serverInstanceId, boolean success);

    /**
     * Callback invoked when MAP client has disconnected from MAP Server.
     * <p>
     * Response to {@link BluetoothMapClient#disconnect(int)} request.
     *
     * @param server
     *            remote MAP server to which connection was initiated.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#disconnect(int)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onServerDisconnected(BluetoothDevice server, int serverInstanceId, boolean success);

    /**
     * Callback notifying the status of {@link BluetoothMapClient#setFolderPath(int serverInstanceId, String folderPath)}
     * call.
     * <p>
     * Response to {@link BluetoothMapClient#setFolderPath(int, String)} request.
     *
     * @param server
     *            remote MAP server to which connection was initiated.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#setFolderPath(int, String)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param currentFolderPath
     *            the current folder path of the specified MAP server instance on MAP server.
     */
    void onFolderPathSet(BluetoothDevice server, int serverInstanceId, boolean success,
            String currentFolderPath);

    /**
     * Callback invoked when MAP client receives Folder list information as Array of BluetoothFolderInfo objects
     * from MAP Server. The BluetoothFolderInfo object holds information like name, timestamp and size of the
     * folder.
     * <p>
     * Response to {@link BluetoothMapClient#getFolderListObject(int serverInstanceId, int maxLength, int offset)}
     * request.
     *
     * @param server
     *            remote MAP server for which folder path was set.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true,
     *            {@link BluetoothMapClient#getFolderListObject(int serverInstanceId, int maxLength, int offset)}
     *            request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param listLength
     *            the number of folder entries, if the initial request specified
     *            a maxListCount of 0. Otherwise, -1 is returned.
     * @param info
     *            if maxListCount specified in the request was not 0, contains
     *            the folder entries for the request. Otherwise, this field is a
     *            zero length list.
     */
    void onFolderListResult(BluetoothDevice server, int serverInstanceId, boolean success,
            int listLength, BluetoothFolderInfo[] info);

    /**
     * Callback invoked when MAP client receives Folder list  information as XML file.
     * <p>
     * Response to {@link BluetoothMapClient#getFolderList(int serverInstanceId, int maxLength, int offset)}
     * request.
     *
     * @param server
     *            remote MAP server from which folder list has been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#getFolderList(int serverInstanceId, int maxLength, int offset)}
     *            request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param listLength
     *            the number of folder entries, if the initial request specified
     *            a maxListCount of 0. Otherwise, -1 is returned.
     * @param contentUri
     *            if maxListCount specified in the request was not 0, contains
     *            the URI to the content containing the folder listing XML file.
     */
    void onFolderListResult(BluetoothDevice server, int serverInstanceId, boolean success,
            int listLength, Uri contentUri);


    /**
     * Callback invoked when MAP client receives MAP message list as Array of BluetoothMessageInfo objects
     * from MAP Server. The BluetoothMessageInfo object holds information like name, subject, address of the
     * sender and recipient of the message.
     * <p>
     * Response to {@link BluetoothMapClient#getMessageListObject(int serverInstanceId, int maxLength,
     *            int offset, BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter)}
     *            request.
     *
     * @param server
     *            remote MAP server from which MAP message list has been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#getMessageListObject(int serverInstanceId, int maxLength,
     *            int offset, BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter)}
     *            request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param listLength
     *            the number of message entries, if the initial request
     *            specified a maxLength of 0. Otherwise, -1 is returned if request is for all available messages.
     * @param hasNewMessages
     *            true if the initial request specified a maxListCount of 0 AND
     *            there is at least one new message.
     * @param info
     *            Message list object which contains messageinfo list in descending
     *            order of datetime with newest message listing first.
     *            if maxListCount specified in the request was not 0, contains
     *            the message info entries for the request. Otherwise, this
     *            field is a zero length list.
     */
    void onMessageListResult(BluetoothDevice server, int serverInstanceId, boolean success,
            int listLength, boolean hasNewMessages, BluetoothMessageInfo[] info);

    /**
     * Callback invoked when MAP client receives MAP message list as XML file from MAP Server.
     * <p>
     * Response to {@link BluetoothMapClient#getMessageList(int serverInstanceId, int maxLength, int offset,
     *            BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter)} request.
     *
     * @param server
     *            remote MAP server from which MAP message list has been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#getMessageList(int serverInstanceId, int maxLength, int offset,
     *            BluetoothMessageListFilter listFilter, BluetoothMessageParameterFilter paramFilter)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param listLength
     *            the number of message entries, if the initial request
     *            specified a maxLength of 0. Otherwise, -1 is returned if request is for all available messages.
     * @param hasNewMessages
     *            true if the initial request specified a maxListCount of 0 AND
     *            there is at least one new message.
     * @param contentUri
     *            if maxListCount specified in the request was not 0, contains
     *            the URI to the content containing the message listing XML file.
     */
    void onMessageListResult(BluetoothDevice server, int serverInstanceId, boolean success,
            int listLength, boolean hasNewMessages, Uri contentUri);

    /**
     * Callback invoked after MAP client requests available instances on MAP server.
     * Example: MAP server instances like SMS, MMS, Email.
     * <p>
     * Response to a {@link BluetoothMapClient#getMseInstances()} request.
     *
     * @param server
     *            remote MAP server from which instances have been received.
     * @param mseInfo
     *            zero or more objects describing each MAP server instance.
     */
    void onGetMseInstancesResult(BluetoothDevice server, BluetoothMseInfo[] mseInfo);

    /**
     * Callback invoked when MAP client receives MAP message as XML from MAP server.
     * <p>
     * Response to {@link BluetoothMapClient#getMessage(int serverInstanceId, String messageHandle, byte charset,
     *            boolean includeAttachments)} request.
     *
     * @param server
     *            remote MAP server from which MAP messages have been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param messageHandle
     *            unique idenitifer for the MAP message.
     * @param success
     *            If true, {@link BluetoothMapClient#getMessage(int serverInstanceId, String messageHandle, byte charset,
                  boolean includeAttachments)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param contentUri
     *            URI to the content containing the BMessage.
     */
    void onGetMessageResult(BluetoothDevice server, int serverInstanceId, String messageHandle,
            boolean success, Uri contentUri);

    /**
     * Callback invoked when MAP client receives MAP message as BMessage object from MAP server.
     * <p>
     * Response to {@link BluetoothMapClient#getMessageAsObject(int serverInstanceId, String messageHandle,
     *            byte charset, boolean includeAttachments)} request.
     *
     * @param server
     *            remote MAP server from which MAP messages have been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param messageHandle
     *            unique idenitifer assigned by MAP server to identity individual messages.
     *            The handle shall be 64 bit unsigned integer value.
     *            Each messageHandle is valid only for duration of MAP session.
     * @param success
     *            If true, {@link BluetoothMapClient#getMessageAsObject(int serverInstanceId, String messageHandle,
     *            byte charset, boolean includeAttachments)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param bMessage
     *            BMessage object.
     */
    void onGetMessageResult(BluetoothDevice server, int serverInstanceId, String messageHandle,
            boolean success, BMessage bMessage);

    /**
     * Callback invoked when MAP Server completes retrieving new messages from the network.
     * <p>
     * Response to {@link BluetoothMapClient#updateInbox(int serverInstanceId)} request.
     *
     * @param server
     *            remote MAP server from which new messages have been received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#updateInbox(int serverInstanceId)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onUpdateInboxResult(BluetoothDevice server, int serverInstanceId, boolean success);

    /**
     * Callback invoked when MAP client message notification settings for Notification server
     * have changed.
     * <p>
     * Response for {@link BluetoothMapClient.#registerForNotification(int serverInstanceId)} or
     *  {@link BluetoothMapClient.#unregisterForNotification(int serverInstanceId)} request.
     *
     * @param server
     *            remote MAP server on which notification settings have changed.
     * @param instanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param isRegistration
     *            true, if request is {@link BluetoothMapClient.#registerForNotification(int serverInstanceId)}.
     *            false if request is {@link BluetoothMapClient.#unregisterForNotification(int serverInstanceId)}.
     * @param success
     *            If true, {@link BluetoothMapClient.#registerForNotification(int serverInstanceId)} or
     *  {@link BluetoothMapClient.#unregisterForNotification(int serverInstanceId)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onNotificationRegistrationStateChange(BluetoothDevice server, int instanceId,
            boolean isRegistration, boolean success);

    /**
     * Callback invoked when MAP message status is updated on MAP server.
     * <p>
     * Response for {@link BluetoothMapClient#setMessageDeletedStatus(int serverInstanceId,
     *  String messageHandle, boolean setDeleted)} or
     *  {@link BluetoothMapClient.#setMessageReadStatus(int serverInstanceId,
     *  String messageHandle, boolean setRead)} request.
     *
     * @param server
     *            remote MAP server on which MAP message status was updated.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param statusType
     *            Indicates the message status. 1 for read status, 2 for deleted status of MAP message.
     * @param messageHandle
     *            unique indentifier for the message.
     * @param success
     *            If true {@link BluetoothMapClient#setMessageDeletedStatus(int serverInstanceId,
     *  String messageHandle, boolean setDeleted)} or
     *  {@link BluetoothMapClient.#setMessageReadStatus(int serverInstanceId,
     *  String messageHandle, boolean setRead)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     */
    void onMessageStatusUpdated(BluetoothDevice server, int serverInstanceId, int statusType,
            String messageHandle, boolean success);

    /**
     * Callback invoked when MAP message has been pushed to MAP Server from the MAP client.
     * <p>
     * Response for {@link BluetoothMapClient#pushMessage(int serverInstanceId,
     * BMessage bMessage, byte charset, boolean isRetry, boolean autoSend)} request.
     *
     * @param server
     *            remote MAP server to which MAP message was pushed from MAP client.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param success
     *            If true, {@link BluetoothMapClient#pushMessage(int serverInstanceId,
     * BMessage bMessage, byte charset, boolean isRetry, boolean autoSend)} request succeeded.
     *            If false, request failed due to local error or response from MAP server.
     * @param messageHandle
     *            unique identifier for the newly pushed message on MAP server.
     */
    void onPushMessageResult(BluetoothDevice server, int serverInstanceId, boolean success,
            String messageHandle);

    /**
     * Callback invoked when there is a notification from MAP server to MAP client.
     * <p>
     * The MAP server notifies the MAP client for any change in message-listing of any
     * folder on MAP server. Example: Arrival of new message, User deletes a message.
     *
     * @param server
     *            remote MAP server from which notification was received.
     * @param serverInstanceId
     *            unique identifier for the MAP server instance to which connection was initiated.
     * @param contentUri
     *            URI to the content (XML file) containing the MAP notification event report.
     */
    void onNotification(BluetoothDevice server, int serverInstanceId, Uri contentUri);
}
