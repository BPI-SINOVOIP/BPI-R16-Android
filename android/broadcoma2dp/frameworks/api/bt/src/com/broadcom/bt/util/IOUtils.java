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
package com.broadcom.bt.util;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

/**
 * Helper class for IO tasks
 *
 * @author fredc@broadcom.com
 *
 */
public class IOUtils {
    public static final String TAG = "IOUtils";

    public static boolean isValidParentPath(File f) {
        if (f != null) {
            File parent = f.getParentFile();
            return parent != null && parent.exists();
        }
        return false;
    }

    /**
     * Copy the bytes from InputStream to the target OutputStream using the
     * specified buffer size.
     *
     * @param is
     * @param os
     * @param bufferSize
     * @param closeInput
     * @param closeOutput
     * @return
     */
    public static int copy(InputStream is, OutputStream os, int bufferSize, boolean closeInput,
            boolean closeOutput) {
        int totalBytes = 0;
        try {
            int bytesRead = 0;
            byte[] buf = new byte[bufferSize];
            while ((bytesRead = is.read(buf)) >= 0) {
                os.write(buf, 0, bytesRead);
                totalBytes += bytesRead;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error copying data ", t);
        }
        if (closeInput) {
            safeClose(is);
        }
        if (closeOutput) {
            safeClose(os);
        }
        return totalBytes;
    }

    /**
     * Safely closes an input stream, ignoring all exceptions
     *
     * @param is
     */
    public static void safeClose(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (Throwable t) {
            Log.d(TAG, "Error closing InputStream", t);

        }
    }

    /**
     * Safely closes an output stream, ignoring all exceptions
     *
     * @param os
     */
    public static void safeClose(OutputStream os) {
        if (os == null) {
            return;
        }
        try {
            os.close();
        } catch (Throwable t) {
            Log.d(TAG, "Error closing InputStream", t);

        }
    }

}
