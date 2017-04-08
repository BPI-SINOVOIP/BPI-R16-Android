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

package com.broadcom.bt.map;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMessageParameterFilter;
import com.broadcom.bt.map.IBluetoothMapClientCallback;
import android.bluetooth.BluetoothDevice;

/**
 * API for Bluetooth Map Client service
 *
 * {@hide}
 */
interface IBluetoothMapClient {
    boolean registerCallback(String clientId, IBluetoothMapClientCallback callback);
    boolean unregisterCallback(String clientId, IBluetoothMapClientCallback callback);

    /**
     * Search for MAP Server Instances
     */
    boolean searchMseInstances(String clientId, in BluetoothDevice server);
    
    /**
     * Start MCE notification server
     */
    boolean startNotificationServer(String name);
    
    /**
     * Stop MCE notification server
     */
    boolean stopNotificationServer();

    /**
     * Connect to an MSE instance
     */
    boolean connect(String clientId, in BluetoothDevice server, int serverInstanceId);
    
    /**
     * Disconnect from an MSE instance
     */
    boolean disconnect(String clientId, in BluetoothDevice server, int serverInstanceId);

    /**
     * Abort Obex operation on an MSE instance
     */
    boolean abortOperation(String clientId, in BluetoothDevice server, int serverInstanceId);


    /**
     * Disconnect all MCE instances
     */
    void disconnectAll();
    
    /**
     * Set Folder Path for a MSE instance
     */
    boolean setFolderPath(String clientId, in BluetoothDevice server, int serverInstanceId, String folderPath);

    /**
     * Get Folder Path for a MSE instance
     */
    String getFolderPath(String clientId, in BluetoothDevice server, int serverInstanceId);

    /**
     * Get Folder List for a MSE instance in an output file or as an object
     */
    boolean getFolderList(String clientId, in BluetoothDevice server, int serverInstanceId, 
                          int maxLength, int offset, boolean returnAsObject);


    /**
     * Get Message List for a MSE instance in an output file or as an object
     */
    boolean getMessageList(String clientId, in BluetoothDevice server, int serverInstanceId,
                           int maxLength, int offset, in BluetoothMessageListFilter listFilter,
                           in BluetoothMessageParameterFilter paramFilter, boolean returnAsObject);
                           

    /**
     * Get Message from an MSE instance in an output file or as an object
     */
    boolean getMessage(String clientId, in BluetoothDevice server,
                   int serverInstanceId, String messageHandle, byte charset,
                   boolean includeAttachments, boolean returnAsObject);
                   

    /**
     * Update an MSE instance inbox
     */
    boolean updateInbox(String clientId, in BluetoothDevice server,int serverInstanceId);
    
    /**
     * Set an MSE message's delete or read status
     */
    boolean setMessageStatus(String clientId, in BluetoothDevice server,
            int serverInstanceId, String messageHandle,
            byte statusType,  boolean isSet);


    /**
     * Push a BMessage to the current MSE instance's folder
     */
    boolean pushMessage(String clientId, in BluetoothDevice server,
        int serverInstanceId, String contentUri,byte charset, boolean isRetry, boolean autoSend); 

    /**
     * Register for MSE notifications
     */
    boolean registerForNotification(String clientId, in BluetoothDevice server,
        int serverInstanceId);
    /**
     * Register for MSE notifications
     */
    boolean unregisterForNotification(String clientId, in BluetoothDevice server,
        int serverInstanceId);

    /**
     * Get MSE instances information from MSE
     */
     boolean getMseInstances(String clientId, in BluetoothDevice server);
}
