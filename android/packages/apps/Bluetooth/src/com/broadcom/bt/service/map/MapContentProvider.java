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
import java.io.FileNotFoundException;
import java.net.URLDecoder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class MapContentProvider extends ContentProvider{
    private static final String TAG = MapClientServiceConfig.TAG_PREFIX+"MapContentProvider";

    public static final String URI_PREFIX = "content://com.broadcom.bt.map/";
    public static final Uri CONTENT_URL=Uri.parse(URI_PREFIX);
    public static File DEFAULT_TMP_DIR;

    private static boolean isInited;
    static synchronized void init(File defaultTmpDir) {
        if (isInited) {
            return;
        }
        DEFAULT_TMP_DIR= defaultTmpDir;
        isInited=true;
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = null;
        try {
            file = new File(DEFAULT_TMP_DIR,URLDecoder.decode(uri.getPath(),"UTF-8"));
        } catch (Throwable t) {
            Log.d(TAG,"Error opening internal file for MapContentProvider",t);
            return null;
        }
        Log.d(TAG, "Opening file...." + file.getAbsolutePath());
        int parcelMode=0;
        if (mode !=null && mode.length()>0) {
            if (mode.indexOf('r') >=0) {
                Log.d(TAG, "Adding read flag....");
                parcelMode |= ParcelFileDescriptor.MODE_READ_ONLY;
            }
            if (mode.indexOf('c') >=0) {
                Log.d(TAG, "Adding create flag....");
                parcelMode |= ParcelFileDescriptor.MODE_CREATE;
            }
            if (mode.indexOf('w') >=0) {
                Log.d(TAG, "Adding write flag....");
                parcelMode |=ParcelFileDescriptor.MODE_WRITE_ONLY;
            }
            if (mode.indexOf('t') >=0) {
                Log.d(TAG, "Adding truncate flag....");
                parcelMode |= ParcelFileDescriptor.MODE_TRUNCATE;
            }
            /*//Never allow world readable
            if (mode.indexOf('W') >=0) {
                Log.d(TAG, "Adding world write flag....");
                parcelMode |= ParcelFileDescriptor.MODE_WORLD_WRITEABLE;
            }
            if (mode.indexOf('R') >=0) {
                parcelMode |= ParcelFileDescriptor.MODE_WORLD_READABLE;
            }
            */
        }
        if (parcelMode ==0) {
            parcelMode = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        ParcelFileDescriptor parcel = ParcelFileDescriptor.open(file, parcelMode);
        return parcel;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String s, String[] as) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentvalues) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public int update(Uri uri, ContentValues contentvalues, String s, String[] as) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }
}
