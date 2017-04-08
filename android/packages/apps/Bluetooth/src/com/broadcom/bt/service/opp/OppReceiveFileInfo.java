/* Copyright 2009-2013 Broadcom Corporation
 **
 ** This program is the proprietary software of Broadcom Corporation and/or its
 ** licensors, and may only be used, duplicated, modified or distributed
 ** pursuant to the terms and conditions of a separate, written license
 ** agreement executed between you and Broadcom (an "Authorized License").
 ** Except as set forth in an Authorized License, Broadcom grants no license
 ** (express or implied), right to use, or waiver of any kind with respect to
 ** the Software, and Broadcom expressly reserves all rights in and to the
 ** Software and all intellectual property rights therein.
 ** IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 ** SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 ** ALL USE OF THE SOFTWARE.
 **
 ** Except as expressly set forth in the Authorized License,
 **
 ** 1.     This program, including its structure, sequence and organization,
 **        constitutes the valuable trade secrets of Broadcom, and you shall
 **        use all reasonable efforts to protect the confidentiality thereof,
 **        and to use this information only in connection with your use of
 **        Broadcom integrated circuit products.
 **
 ** 2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 **        "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 **        REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 **        OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 **        DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 **        NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 **        ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 **        CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 **        OF USE OR PERFORMANCE OF THE SOFTWARE.
 **
 ** 3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 **        ITS LICENSORS BE LIABLE FOR
 **        (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 **              DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 **              YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 **              HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 **        (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 **              SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 **              LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 **              ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 */


package com.broadcom.bt.service.opp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.MimeTypeMap;

/**
 * This class stores information about a single receiving file. It will only be
 * used for inbounds share, e.g. receive a file to determine a correct save file
 * name
 */
public class OppReceiveFileInfo{

    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;
    private static String sDesiredStoragePath = null;

    /** absolute store file name */
    public String mAbsFileName;

    /** mime type*/
    public String mMimeType;

    public int mStatus;


    public OppReceiveFileInfo(String filename, int status, String mimeType) {
        mAbsFileName = filename;
        mStatus = status;
        mMimeType = mimeType;
    }

    public OppReceiveFileInfo(int status) {
        this(null, status , null);
    }

    // public static final int BATCH_STATUS_CANCELED = 4;
    public static OppReceiveFileInfo genAbsPathAndMime(String fileName , int fileLength)
    {

        //ContentResolver contentResolver = context.getContentResolver();
        //Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
        String filename = null;
        String hint= fileName;
        long length = fileLength;

        File base = null;
        StatFs stat = null;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String root = Environment.getExternalStorageDirectory().getPath();
            base = new File(root + Constants.DEFAULT_STORE_SUBDIR);
            if (!base.isDirectory() && !base.mkdir()) {
                if (D) Log.d(Constants.TAG, "Receive File aborted - can't create base directory "
                            + base.getPath());
                return new OppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);

            }
            stat = new StatFs(base.getPath());
        } else {
            if (D) Log.d(Constants.TAG, "Receive File aborted - no external storage");
            return new OppReceiveFileInfo(BluetoothShare.STATUS_ERROR_NO_SDCARD);
        }

        /*
         * Check whether there's enough space on the target filesystem to save
         * the file. Put a bit of margin (in case creating the file grows the
         * system by a few blocks).
         */
        if (stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4) < length) {
            if (D) Log.d(Constants.TAG, "Receive File aborted - not enough free space");
            return new OppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
        }

        filename = choosefilename(hint);
        if (filename == null) {
            // should not happen. It must be pre-rejected
            return new OppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }
        String extension = null;
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0) {
             extension = "";

        } else {
            extension = filename.substring(dotIndex);
            filename = filename.substring(0, dotIndex);
        }
        filename = base.getPath() + File.separator + filename;
        // Generate a unique filename, create the file, return it.
        String fullfilename = chooseUniquefilename(filename, extension);

        if (!safeCanonicalPath(fullfilename)) {
            // If this second check fails, then we better reject the transfer
            return new OppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }
        if (V) Log.v(Constants.TAG, "Generated received filename " + fullfilename);

        if (fullfilename != null) {

           try{
                   new FileOutputStream(fullfilename).close();
                   String type = generateMimeType(fileName);
                   return new OppReceiveFileInfo( fullfilename, 0 , type );
            }catch(IOException e){

                   if (D) Log.e(Constants.TAG, "Error when creating file " + fullfilename);
                  return new OppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
            }
        } else {
           return new OppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }

    }

    private static boolean safeCanonicalPath(String uniqueFileName) {
        try {
            File receiveFile = new File(uniqueFileName);
            if (sDesiredStoragePath == null) {
                sDesiredStoragePath = Environment.getExternalStorageDirectory().getPath() +
                    Constants.DEFAULT_STORE_SUBDIR;
            }
            String canonicalPath = receiveFile.getCanonicalPath();

            // Check if canonical path is complete - case sensitive-wise
            if (!canonicalPath.startsWith(sDesiredStoragePath)) {
                return false;
            }

            return true;
        } catch (IOException ioe) {
            // If an exception is thrown, there might be something wrong with the file.
            return false;
        }
    }

    private static String chooseUniquefilename(String filename, String extension) {
        String fullfilename = filename + extension;
        if (!new File(fullfilename).exists()) {
            return fullfilename;
        }
        filename = filename + Constants.filename_SEQUENCE_SEPARATOR;
        /*
         * This number is used to generate partially randomized filenames to
         * avoid collisions. It starts at 1. The next 9 iterations increment it
         * by 1 at a time (up to 10). The next 9 iterations increment it by 1 to
         * 10 (random) at a time. The next 9 iterations increment it by 1 to 100
         * (random) at a time. ... Up to the point where it increases by
         * 100000000 at a time. (the maximum value that can be reached is
         * 1000000000) As soon as a number is reached that generates a filename
         * that doesn't exist, that filename is used. If the filename coming in
         * is [base].[ext], the generated filenames are [base]-[sequence].[ext].
         */
        Random rnd = new Random(SystemClock.uptimeMillis());
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; ++iteration) {
                fullfilename = filename + sequence + extension;
                if (!new File(fullfilename).exists()) {
                    return fullfilename;
                }
                if (V) Log.v(Constants.TAG, "file with sequence number " + sequence + " exists");
                sequence += rnd.nextInt(magnitude) + 1;
            }
        }
        return null;
    }

    private static String choosefilename(String hint) {
        String filename = null;

        // First, try to use the hint from the application, if there's one
        if (filename == null && !(hint == null) && !hint.endsWith("/") && !hint.endsWith("\\")) {
            // Prevent abuse of path backslashes by converting all backlashes '\\' chars
            // to UNIX-style forward-slashes '/'
            hint = hint.replace('\\', '/');
            // Convert all whitespace characters to spaces.
            hint = hint.replaceAll("\\s", " ");
            // Replace illegal fat filesystem characters from the
            // filename hint i.e. :"<>*?| with something safe.
            hint = hint.replaceAll("[:\"<>*?|]", "_");
            if (V) Log.v(Constants.TAG, "getting filename from hint");
            int index = hint.lastIndexOf('/') + 1;
            if (index > 0) {
                filename = hint.substring(index);
            } else {
                filename = hint;
            }
        }
        return filename;
    }

    private static String generateMimeType(String fileName){

        String extension, type;
        int dotIndex = fileName.lastIndexOf(".");
        extension = fileName.substring(dotIndex + 1).toLowerCase();
        MimeTypeMap map = MimeTypeMap.getSingleton();
        type = map.getMimeTypeFromExtension(extension);
        Log.d("OppReciveFileInfo" , fileName);

        return type;

    }
}
