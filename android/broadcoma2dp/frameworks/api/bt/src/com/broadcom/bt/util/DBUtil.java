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

import android.database.Cursor;
import android.util.Log;

public class DBUtil {
    public static final String TAG = "DBUtil";

    public static boolean safeMoveToFirst(Cursor c) {
        return c != null && c.moveToFirst();
    }
    public static Cursor getNonEmptyCursorOrClose(Cursor c) {
        if (c!=null && !c.moveToFirst()) {
            safeClose(c);
            c=null;
        }
        return c;
    }
    public static void safeClose(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                Log.d(TAG, "Error closing cursor", e);
            }
        }
    }

    public static int getInt(Cursor c, int colId, int defaultValue) {
        try {
            return c.getInt(colId);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get int value from col " + colId);
            return defaultValue;
        }
    }

    public static long getLong(Cursor c, int colId, long defaultValue) {
        try {
            return c.getLong(colId);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get long value from col " + colId);
            return defaultValue;
        }
    }

    public static boolean getBooleanFromInt(Cursor c, int colId,
            boolean defaultValue) {
        try {
            return 1 == c.getInt(colId);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get boolean value from col " + colId);
            return defaultValue;
        }
    }
    public static StringBuilder appendSelection(StringBuilder selection, String key, String operand, String value) {
        if (selection != null) {
            if (selection.length()>0) {
                selection.append(" AND ");
            }
            selection.append("(");
            selection.append(key);
            selection.append(" ");
            selection.append(operand);
            selection.append(" ");
            selection.append(value);
            selection.append(" ");
            selection.append(")");
        }
        return selection;
    }

    public static StringBuilder appendSelection(StringBuilder selection, String newCriteria) {
        if (selection != null) {
            if (selection.length()>0) {
                selection.append(" AND ");
            }
            selection.append(newCriteria);
        }
        return selection;
    }
}
