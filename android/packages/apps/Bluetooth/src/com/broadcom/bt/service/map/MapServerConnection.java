/*******************************************************************************
 *
 *  Copyright (C) 2012 Broadcom Corporation
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

class MapServerConnection {
    private static final String TAG = MapClientServiceConfig.TAG_PREFIX + "MapServerConnection";
    public static final int INVALID_SESSION_ID = -1;
    public static final int INVALID_INSTANCE_ID = -1;

    public static final String ROOT_PATH = "/telecom/msg";

    public HashSet<String> mPendingRequests = new HashSet<String>();

    static boolean isValidSession(int sessionId) {
        return sessionId >= INVALID_SESSION_ID;
    }

    static class FolderManager {
        private LinkedList<String> mCurrentPath;
        private LinkedList<String> mPendingPath;

        public void clear() {
            mCurrentPath=null;
            mPendingPath = null;
        }
        public synchronized String getCurrentPath() {
            StringBuilder b = new StringBuilder();
            if (mCurrentPath != null) {
                Iterator<String> i = mCurrentPath.iterator();
                while (i.hasNext()) {
                    b.append("/");
                    b.append(i.next());
                }
            }
            return b.toString();
        }

        public synchronized boolean setPendingPath(LinkedList<String> path) {
            mPendingPath = path;
            return true;
        }

        public synchronized String peekPendingFolderName() {
            return (mPendingPath != null && mPendingPath.size() > 0 ? mPendingPath.get(0) : null);
        }

        public synchronized boolean hasPendingFolderName() {
            return (mPendingPath != null && mPendingPath.size() > 0);
        }

        private synchronized String popPendingFolderName() {
            return (mPendingPath != null && mPendingPath.size() > 0 ? mPendingPath.remove(0) : null);
        }

        public synchronized boolean addPendingFolderNameToCurrentPath() {
            String folderName = popPendingFolderName();
            Log.d(TAG, "addPendingFolderNameToCurrentPath(): " + folderName);
            if (folderName == null || folderName.length() == 0) {
                return false;
            } else {
                if (mCurrentPath == null) {
                    mCurrentPath = new LinkedList<String>();
                }
                if (folderName.equals("/")) {
                    mCurrentPath.clear();
                } else {
                    mCurrentPath.add(folderName);
                }
            }
            return true;
        }
    }

    String mClientId;
    BluetoothDevice mServer;
    int mServerInstanceId;
    int mSessionId;
    boolean mIsConnected;
    boolean mCleanupPending;
    FolderManager mFolderManager;

    public MapServerConnection(String clientId, BluetoothDevice server, int serverInstanceId,
            boolean isConnected) {
        mServer = server;
        mServerInstanceId = serverInstanceId;
        mClientId = clientId;
        mIsConnected = isConnected;
        mSessionId = INVALID_SESSION_ID;
        mFolderManager = new FolderManager();
    }

    public void setClientId(String clientId) {
        mClientId = clientId;
    }

    public void removeClientId(String clientId) {
        mClientId = clientId;
    }

    public boolean isClientIdSet() {
        return mClientId != null;
    }

    public boolean hasClientId(String clientId) {
        return mClientId == clientId || mClientId.equals(clientId);
    }

    public boolean isValidSession() {
        return isValidSession(mSessionId);
    }

    public void clearFolderPath() {
        mFolderManager.clear();
    }
    public String getFolderPath() {
        return mFolderManager.getCurrentPath();
    }

    public boolean setPendingFolderPath(LinkedList<String> path) {
        return mFolderManager.setPendingPath(path);
    }

    public boolean addPendingRequest(String transactionId) {
        return mPendingRequests.add(transactionId);
    }

    public boolean removePendingRequest(String transactionId) {
        return mPendingRequests.remove(transactionId);
    }

    public boolean hasPendingRequest(String transactionId) {
        return mPendingRequests.contains(transactionId);
    }
}