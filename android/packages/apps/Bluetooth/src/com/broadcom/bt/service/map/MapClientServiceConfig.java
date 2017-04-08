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

import java.io.File;

import com.broadcom.bt.map.BluetoothMapClient;
import com.broadcom.bt.map.BluetoothMessageListFilter;
import com.broadcom.bt.map.BluetoothMessageParameterFilter;

import android.util.Log;

public class MapClientServiceConfig {
    public static final String TAG_PREFIX = "BtMap.";
    public static final boolean DBG = true;
    public static final boolean DEBUG_ADMIN = true;
    private static final String TAG = TAG_PREFIX + "MapClientServiceConfig";

    public static final byte DEFAULT_CHARSET = BluetoothMapClient.CHARSET_UTF8;

    public static final int MAX_FOLDER_LIST_SIZE = 1024;
    public static final int MAX_MESSAGE_LIST_SIZE = 1024;

    public static final int TIMEOUT_START_STOP_MS = 1000;
    public static final int TIMEOUT_SET_FOLDER_MS = 3000;
    public static final int TIMEOUT_GET_FOLDER_LIST_MS = 3000;
    public static final int TIMEOUT_GET_MESSAGE_LIST_MS = 60000;
    public static final int TIMEOUT_CONNECT_DISCONNECT_MS = 10000;
    public static final int TIMEOUT_GET_MESSAGE_MS = 15000;
    public static final int TIMEOUT_UPDATE_INBOX_MS = 3000;
    public static final int TIMEOUT_SET_MESSAGE_STATUS_MS = 3000;
    public static final int TIMEOUT_PUSH_MESSAGE_MS = 15000;
    public static final int TIMEOUT_REGISTER_NOTIFICATION_MS = 3000;
    public static final int TIMEOUT_GET_MSE_INSTANCES_MS = 15000;

    public static final String DEFAULT_TMP_SUBDIR = "map";

    private static File mDefaultTmpDir;
    private static String mDefaultTmpPath;

    static void initTmpDirectories(File pkgDir) {
        // Initialize temporary directory/path
        mDefaultTmpDir = new File(pkgDir, DEFAULT_TMP_SUBDIR);
        if (!mDefaultTmpDir.exists()) {
            mDefaultTmpDir.mkdirs();
        }
        mDefaultTmpPath = mDefaultTmpDir.getAbsolutePath();
        Log.d(TAG, "Tmp directory is " + mDefaultTmpPath);
    }

    public static File getDefaultTmpDir() {
        return mDefaultTmpDir;
    }

    static final BluetoothMessageListFilter sDefaultMessageListFilter;
    static final BluetoothMessageParameterFilter sDefaultMessageParameterFilter;

    static {
        sDefaultMessageListFilter = new BluetoothMessageListFilter();
        sDefaultMessageParameterFilter = new BluetoothMessageParameterFilter(-1);
    }

    public static BluetoothMessageListFilter getDefaultMessageListFilter() {
        return sDefaultMessageListFilter;
    }

    public static long getDefaultMessageParameterFilter() {
        return sDefaultMessageParameterFilter.mParameterMask;
    }
}
