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

package com.broadcom.bt.service.map;

import android.bluetooth.BluetoothDevice;
import android.os.RemoteException;
import android.util.Log;

import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.broadcom.bt.map.IBluetoothMapClient;
import com.broadcom.bt.map.IBluetoothMapClientCallback;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMessageParameterFilter;

public class MapClientServiceBinder extends IBluetoothMapClient.Stub implements
        IProfileServiceBinder {
    private static final String TAG = MapClientServiceConfig.TAG_PREFIX + "MapClientServiceBinder";

    public static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    public static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private MapClientService mService;

    MapClientServiceBinder(MapClientService svc) {
        mService = svc;
    }

    public boolean cleanup() {
        mService = null;
        return true;
    }

    private MapClientService getService() {
        if (mService != null && mService.isAvailable()) {
            return mService;
        } else {
            Log.w(TAG, "getService(): service unavailable");
        }
        return null;
    }

    @Override
    public boolean registerCallback(String clientId, IBluetoothMapClientCallback cb)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.registerCallback(clientId, cb);
    }

    @Override
    public boolean unregisterCallback(String clientId, IBluetoothMapClientCallback cb)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.unregisterCallback(clientId, cb);
    }

    @Override
    public boolean startNotificationServer(String name) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.startNotificationServer(name);
    }

    @Override
    public boolean stopNotificationServer() throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.stopNotificationServer();
    }

    @Override
    public boolean connect(String clientId, BluetoothDevice device, int mseInstanceId)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.connect(clientId, device, mseInstanceId);
    }

    @Override
    public boolean disconnect(String clientId, BluetoothDevice device, int mseInstanceId)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.disconnect(clientId, device, mseInstanceId);
    }

    @Override
    public void disconnectAll() throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return;
        }
        svc.mHandler.disconnectAll();
    }

    @Override
    public boolean setFolderPath(String clientId, BluetoothDevice server, int serverInstanceId,
            String folderPath) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.setFolderPath(clientId, server, serverInstanceId, folderPath);
    }

    @Override
    public String getFolderPath(String clientId, BluetoothDevice server, int serverInstanceId)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return null;
        }
        return svc.getFolderPath(clientId, server, serverInstanceId);
    }

    @Override
    public boolean getFolderList(String clientId, BluetoothDevice server, int serverInstanceId,
            int maxLength, int offset, boolean returnAsObject) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.getFolderList(clientId, server, serverInstanceId, maxLength, offset,
                returnAsObject);
    }

    @Override
    public boolean searchMseInstances(String clientId, BluetoothDevice server)
            throws RemoteException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getMessageList(String clientId, BluetoothDevice server, int serverInstanceId,
            int maxLength, int offset, BluetoothMessageListFilter listFilter,
            BluetoothMessageParameterFilter paramFilter, boolean returnAsObject) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.getMessageList(clientId, server, serverInstanceId, maxLength, offset,
                listFilter, paramFilter, returnAsObject);
    }

    @Override
    public boolean getMessage(String clientId, BluetoothDevice server, int serverInstanceId,
            String messageHandle, byte charset, boolean includeAttachments, boolean returnAsObject)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.getMessage(clientId, server, serverInstanceId, messageHandle, charset,
                includeAttachments, returnAsObject);
    }

    @Override
    public boolean updateInbox(String clientId, BluetoothDevice server, int serverInstanceId)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.updateInbox(clientId, server, serverInstanceId);
    }

    @Override
    public boolean setMessageStatus(String clientId, BluetoothDevice server, int serverInstanceId,
            String messageHandle, byte statusType, boolean isSet) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.setMessageStatus(clientId, server, serverInstanceId, messageHandle,
                statusType, isSet);
    }

    @Override
    public boolean pushMessage(String clientId, BluetoothDevice server, int serverInstanceId,
            String contentUri, byte charset, boolean isRetry, boolean autoSend)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.pushMessage(clientId, server, serverInstanceId, contentUri, charset,
                isRetry, autoSend);
    }

    @Override
    public boolean registerForNotification(String clientId, BluetoothDevice server,
            int serverInstanceId) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.registerForNotification(clientId, server, serverInstanceId);
    }

    @Override
    public boolean unregisterForNotification(String clientId, BluetoothDevice server,
            int serverInstanceId) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.unregisterForNotification(clientId, server, serverInstanceId);
    }

    @Override
    public boolean abortOperation(String clientId, BluetoothDevice server, int serverInstanceId)
            throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.abortOperation(clientId, server, serverInstanceId);
    }

    @Override
    public boolean getMseInstances(String clientId, BluetoothDevice server) throws RemoteException {
        MapClientService svc = getService();
        if (svc == null) {
            return false;
        }
        return svc.mHandler.getMseInstances(clientId, server);
    }
}
