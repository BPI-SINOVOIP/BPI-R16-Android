/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) 2009-2013 Broadcom Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.broadcom.bt.service.opp;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * This class stores information about a single sending file It will only be
 * used for outbound share.
 */
public class BluetoothOppSendFileInfo {
    private static final String TAG = "BluetoothOppSendFileInfo";

    private static final boolean D = Constants.DEBUG;

    private static final boolean V = Constants.VERBOSE;

    /** Reusable SendFileInfo for error status. */
    static final BluetoothOppSendFileInfo SEND_FILE_INFO_ERROR = new BluetoothOppSendFileInfo(
            null, null, 0, BluetoothShare.STATUS_FILE_ERROR );

    /** readable media file name */
    public final String mFileName;

    /** media file input stream */
    //public final FileInputStream mInputStream;

    /** vCard string data */
    public final String mData;

    public final int mStatus;

    public final String mMimetype;

    public final long mLength;

    private AssetFileDescriptor assetFd;

    /** for media file */
    public BluetoothOppSendFileInfo(String fileName, String type, long length,
        int status ) {
        mFileName = fileName;
        mMimetype = type;
        mLength = length;
        //mInputStream = inputStream;
        mStatus = status;
        mData = null;
    }


    public void setAssetFileDescriptor(AssetFileDescriptor Fd){
        assetFd = Fd;
    }

    public AssetFileDescriptor getAssetFileDescriptor(){
        return assetFd;
    }

    public static BluetoothOppSendFileInfo generateFileInfo(Context context, Uri uri,
            String type) {
        Log.e(TAG,"generateFileInfo");
        ContentResolver contentResolver = context.getContentResolver();
        String scheme = uri.getScheme();
        String fileName = null;
        String contentType;
        long length = 0;
        AssetFileDescriptor fd  = null;
        // Support all Uri with "content" scheme
        // This will allow more 3rd party applications to share files via
        // bluetooth
        if ("content".equals(scheme)) {
            Log.e(TAG, "Scheme is of type : content");
            contentType = contentResolver.getType(uri);
            Cursor metadataCursor;
            try {
                metadataCursor = contentResolver.query(uri, new String[] {
                        OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
                }, null, null, null);
            } catch (SQLiteException e) {
                // some content providers don't support the DISPLAY_NAME or SIZE columns
                metadataCursor = null;
            }
            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToFirst()) {
                        fileName = metadataCursor.getString(0);
                        length = metadataCursor.getInt(1);
                        if (D) Log.d(TAG, "fileName = " + fileName + " length = " + length);
                    }
                } finally {
                    metadataCursor.close();
                }
            }
            if (fileName == null) {
                // use last segment of URI if DISPLAY_NAME query fails
                fileName = uri.getLastPathSegment();
            }
        } else if ("file".equals(scheme)) {
            Log.e(TAG, "Scheme is of type : file");
            fileName = uri.getLastPathSegment();
            contentType = type;
            File f = new File(uri.getPath());
            length = f.length();
        } else {
            // currently don't accept other scheme
            Log.e(TAG, "Error : wont accept schemes other than file and content");
            return SEND_FILE_INFO_ERROR;
        }
        /*logic to generate  Asset file descriptor and length*/
        if (scheme.equals("content")) {
            try {
                    // We've found that content providers don't always have the
                    // right size in _OpenableColumns.SIZE
                    // As a second source of getting the correct file length,
                    // get a file descriptor and get the stat length
                    //AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(uri, "r");
                    Log.e(TAG, "@Call to openAssetFileDescriptor");
                    fd = contentResolver.openAssetFileDescriptor(uri, "r");
                    long statLength = fd.getLength();
                    if (length != statLength && statLength > 0) {
                        Log.e(TAG, "Content provider length is wrong (" + Long.toString(length) +
                                "), using stat length (" + Long.toString(statLength) + ")");
                        length = statLength;
                    }
                    //close the fd
                    try{
                           fd.close();
                    }catch(IOException e){
                    }
                } catch (FileNotFoundException e) {
                            // Ignore
                }
         }


        return new BluetoothOppSendFileInfo(fileName, contentType, length, 0);
    }
}
