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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

public class MapServerConnectionManager {
    private static final String TAG = MapClientServiceConfig.TAG_PREFIX
            + "MapServerConnectionManager";

    private static final int INVALID_SESSION_ID = -1;
    private static final int INVALID_INSTANCE_ID = -1;

    private static MapServerConnectionManager sInstance = null;
    private static boolean sIsInited = false;

    public static MapServerConnectionManager getInstance() {
        return sInstance;
    }

    static synchronized void init(Context ctx) {

        Log.d(TAG, "init(): application context = " + ctx.getApplicationContext());
        if (!sIsInited) {
            Log.d(TAG, "init(): done initializing");
            try {
                sInstance = new MapServerConnectionManager();
                sIsInited = true;
                Log.d(TAG, "init(): done initializing");
            } catch (Throwable t) {
                Log.e(TAG, "Error initializing datasources....", t);
            }
        } else {
            Log.d(TAG, "init(): already initialized");
        }
    }

    public static void cleanup() {
        if (sInstance != null) {
            sInstance.finish();
        }
    }

    private HashMap<String, MapServerConnection> mServerInstanceConnectionMap = new HashMap<String, MapServerConnection>();
    private HashMap<String, MapServerConnection> mSessionIdConnectionMap = new HashMap<String, MapServerConnection>();

    private HashMap<String, BluetoothDevice> mNotificationConnectionMap = new HashMap<String, BluetoothDevice>();

    void finish() {
        mServerInstanceConnectionMap.clear();
        mSessionIdConnectionMap.clear();
        mNotificationConnectionMap.clear();

    }

    private String getInstanceKey(BluetoothDevice server, int serverInstanceId) {
        if (server == null) {
            return null;
        }
        return server.getAddress() + "-" + serverInstanceId;
    }

    private String getSessionKey(int sessionId) {
        return String.valueOf(sessionId);
    }

    public synchronized void addOrUpdateConnection(MapServerConnection conn) {
        if (conn == null) {
            return;
        }
        String instanceKey = getInstanceKey(conn.mServer, conn.mServerInstanceId);
        MapServerConnection oldConn = mServerInstanceConnectionMap.get(instanceKey);
        if (oldConn != null) {
            Log.d(TAG, "addConnection(): updating existing connection: server " + oldConn.mServer
                    + ", serverInstanceId" + oldConn.mServerInstanceId);
            oldConn.mIsConnected = conn.mIsConnected;
            oldConn.mClientId = conn.mClientId;

            int oldSessionId = oldConn.mSessionId;
            if (oldSessionId != conn.mSessionId) {
                oldConn.mSessionId = conn.mSessionId;
                mSessionIdConnectionMap.remove(getSessionKey(oldSessionId));
                if (conn.isValidSession()) {
                    mSessionIdConnectionMap.put(getSessionKey(conn.mSessionId), oldConn);
                }
            }
        } else {
            Log.d(TAG, "addConnection(): adding new connection connection:server " + conn.mServer
                    + ", serverInstanceId" + conn.mServerInstanceId);
            mServerInstanceConnectionMap.put(instanceKey, conn);
            if (conn.isValidSession()) {
                mSessionIdConnectionMap.put(getSessionKey(conn.mSessionId), conn);
            }
        }
    }

    public synchronized void updateConnectionStateByInstanceId(BluetoothDevice server,
            int serverInstanceId, int sessionId, boolean isConnected) {
        // Update connection state by sessionId then send event
        MapServerConnection conn = getConnectionByInstanceId(server, serverInstanceId);
        if (conn == null) {
            Log.d(TAG, "updateConnectionStateByInstanceId(): connection not found: server="
                    + server + ",serverInstanceId=" + serverInstanceId);
            return;
        }

        conn.mIsConnected = isConnected;
        // If the sessionId is valid and different, update it.
        if (sessionId != INVALID_INSTANCE_ID && conn.mSessionId != sessionId) {
            int oldSessionId = conn.mSessionId;
            conn.mSessionId = sessionId;

            if (oldSessionId != INVALID_SESSION_ID) {
                mSessionIdConnectionMap.remove(getSessionKey(oldSessionId));
            }
            if (conn.isValidSession()) {
                mSessionIdConnectionMap.put(getSessionKey(conn.mSessionId), conn);
            }
        }

        //If disconnected, clean out folder path
        if (!isConnected) {
            conn.clearFolderPath();
        }
    }

    public synchronized void updateConnectionStateBySessionId(BluetoothDevice server,
            int sessionId, boolean isConnected) {
        if (server == null || sessionId <= INVALID_INSTANCE_ID) {
            Log.w(TAG, "updateConnectionStateBySessionId(): connection parameters invalid: server="
                    + (server == null ? "null" : server) + ", sessionId=" + sessionId
                    + ",isConnected=" + isConnected);
            return;
        }
        MapServerConnection conn = mSessionIdConnectionMap.get(getSessionKey(sessionId));
        if (conn == null) {
            Log.w(TAG, "updateConnectionStateBySessionId(): Connection not found for server="
                    + (server == null ? "null" : server) + ", sessionId=" + sessionId);
            return;
        }
        conn.mIsConnected = isConnected;

        //If disconnected, clean out folder path
        if (!isConnected) {
            conn.clearFolderPath();
        }
    }

    public synchronized MapServerConnection getConnectionByInstanceId(BluetoothDevice server,
            int serverInstanceId) {
        MapServerConnection conn = mServerInstanceConnectionMap.get(getInstanceKey(server,
                serverInstanceId));
        if (conn == null) {
            Log.w(TAG, "getConnectionByInstanceId(): connection not found for: server="
                    + (server == null ? "null" : server) + ", serverInstanceId=" + serverInstanceId);
        }
        return conn;
    }

    public synchronized MapServerConnection getConnectionBySessionId(int sessionId) {
        MapServerConnection conn = mSessionIdConnectionMap.get(getSessionKey(sessionId));
        if (conn == null) {
            Log.w(TAG, "getConnectionBySessionId(): connection not found for: sessionId="
                    + sessionId);
        }
        return conn;
    }

    public synchronized String getClientId(BluetoothDevice server, int serverInstanceId) {
        MapServerConnection conn = mServerInstanceConnectionMap.get(getInstanceKey(server,
                serverInstanceId));
        if (conn == null) {
            Log.w(TAG, "getClientId(): connection not found for: server="
                    + (server == null ? "null" : server) + ", serverInstanceId=" + serverInstanceId);
            return null;
        }
        return conn.mClientId;
    }

    public synchronized void removeConnection(BluetoothDevice server, int serverInstanceId) {
        if (server == null || serverInstanceId <= INVALID_INSTANCE_ID) {
            Log.w(TAG, "removeConnection(): connection parameters invalid: server="
                    + (server == null ? "null" : server) + ", serverInstanceId=" + serverInstanceId);
            return;
        }
        MapServerConnection conn = mServerInstanceConnectionMap.remove(getInstanceKey(server,
                serverInstanceId));
        if (conn != null) {
            mSessionIdConnectionMap.remove(getSessionKey(conn.mSessionId));
        }
    }

    public synchronized LinkedList<MapServerConnection> setConnectionsPendingCleanup(String clientId) {
        LinkedList<MapServerConnection> connlist = new LinkedList<MapServerConnection>();
        Iterator<MapServerConnection> i = mServerInstanceConnectionMap.values().iterator();
        while (i.hasNext()) {
            MapServerConnection conn = i.next();
            if (conn.hasClientId(clientId)) {
                conn.mCleanupPending = true;
                connlist.add(conn);
            }
        }
        return connlist;
    }

    public synchronized boolean removePendingRequest(String clientId, BluetoothDevice server,
            int serverInstanceId, String transactionId) {
        MapServerConnection conn = getConnectionByInstanceId(server, serverInstanceId);
        if (conn == null) {
            Log.w(TAG, "removePendingRequest(): connection parameters invalid: server="
                    + (server == null ? "null" : server) + ", serverInstanceId=" + serverInstanceId);
            return false;
        } else {
            return conn.removePendingRequest(transactionId);
        }
    }

    public synchronized boolean setNotificationConnected(String sessionId,
            BluetoothDevice removeDevice) {
        BluetoothDevice oldServer = mNotificationConnectionMap.put(sessionId, removeDevice);
        if (oldServer != null) {
            Log.w(TAG, "setNotificationServerConnected(): replaced existing connection....");
        }
        return true;
    }

    public synchronized boolean setNotificationDisconnected(String sessionId) {
        BluetoothDevice server = mNotificationConnectionMap.remove(sessionId);
        return server != null;
    }

    public synchronized BluetoothDevice getNotificationRemoteDevice(String sessionId) {
        return mNotificationConnectionMap.get(sessionId);
    }
}
