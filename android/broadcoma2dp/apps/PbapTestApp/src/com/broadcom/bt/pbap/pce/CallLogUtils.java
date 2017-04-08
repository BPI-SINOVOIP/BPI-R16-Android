/******************************************************************************
 *
 *  Copyright (C) 2009-2013 Broadcom Corporation
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/

package com.broadcom.bt.pbap.pce;

import com.android.vcard.VCardConfig;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCommitter;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardProperty;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardNotSupportedException;
import com.android.vcard.exception.VCardVersionException;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class CallLogUtils extends VCardEntryConstructor {

    private static String TAG = "CallLogUtils";

    /* package */final static int VCARD_VERSION_V21 = 1;

    /* package */final static int VCARD_VERSION_V30 = 2;

    private VCardParser mVCardParser;

    private ContentResolver mResolver;

    private int mVcfCallType = 0;

    private void addNumToCallLog(ContentResolver resolver, int type, String strName, String strNum,
            int isNew, long timeInMiliSecond) {

        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, strNum);
        values.put(CallLog.Calls.DATE, timeInMiliSecond);
        values.put(CallLog.Calls.DURATION, Math.random() * 100);
        values.put(CallLog.Calls.TYPE, type);
        values.put(CallLog.Calls.NEW, isNew);
        values.put(CallLog.Calls.CACHED_NAME, strName);
        values.put(CallLog.Calls.CACHED_NUMBER_TYPE, 0);
        values.put(CallLog.Calls.CACHED_NUMBER_LABEL, strName);

        try {
            if (null != resolver) {
                resolver.insert(CallLog.Calls.CONTENT_URI, values);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteNumFromCallLog(ContentResolver resolver, String strNum) {
        try {
            String strUriCalls = "content://call_log/calls";
            Uri UriCalls = Uri.parse(strUriCalls);
            if (null != resolver) {
                resolver.delete(UriCalls, CallLog.Calls.NUMBER + "=?", new String[] {
                        strNum
                });
            }
        } catch (Exception e) {
            e.getMessage();
        }
    }

    public int clearAllCallLogs(ContentResolver resolver) {
        int rowsDeleted = 0;
        try {
            if (null != resolver)
                rowsDeleted = resolver.delete(CallLog.Calls.CONTENT_URI, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "clearAllCallLogs deleted " + rowsDeleted + " entries");
        return rowsDeleted;
    }

    public int clearCallLogs(ContentResolver resolver, int type) {
        int rowsDeleted = 0;
        Uri uriCalls = Uri.parse("content://call_log/calls");
        String where = CallLog.Calls.TYPE + "=" + type;
        try {
            if (null != resolver)
                rowsDeleted = resolver.delete(uriCalls, where, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "clearCallLogs deleted " + rowsDeleted + " entries");
        return rowsDeleted;
    }

    // For testing only
    public void getCallLogsFromSIM(ContentResolver resolver) {
        try {
            String simPhoneId = null;
            String simPhoneNum = null;
            String simPhoneName = null;

            Uri simUri = Uri.parse("content://icc/adn");
            Cursor simCursor = resolver.query(simUri, null, null, null, null);

            while (simCursor.moveToNext()) {
                simPhoneId = simCursor.getString(simCursor.getColumnIndex("_id"));
                simPhoneNum = simCursor.getString(simCursor.getColumnIndex("name"));
                simPhoneName = simCursor.getString(simCursor.getColumnIndex("number"));
                Log.v("CallLogUtil", " id = " + simPhoneId + " - name = " + simPhoneName
                        + " - number = " + simPhoneNum);
            }
            simCursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class vCardOnePerson {
        public String name;
        public String phone;
        public String email;
        public long time;
        public long duration;
        public int type;
    }

    public boolean addVCFtoCallLog(ContentResolver resolver, String pathToVcf, int vcfCallType)
            throws IOException, VCardException {

        VCardEntryCounter counter = null;
        VCardSourceDetector detector = null;

        if (resolver == null) {
            Log.e(TAG, "invalid resolver provided");
            return false;
        }

        mResolver = resolver;
        mVcfCallType = vcfCallType;
        int vcardVersion = VCARD_VERSION_V21;
        try {
            boolean shouldUseV30 = false;
            InputStream is;
            try {
                is = new FileInputStream(pathToVcf);
            } catch (FileNotFoundException fnf) {
                fnf.printStackTrace();
                return false;
            }

            mVCardParser = new VCardParser_V21();

            try {
                counter = new VCardEntryCounter();
                detector = new VCardSourceDetector();
                mVCardParser.addInterpreter(counter);
                mVCardParser.addInterpreter(detector);
                mVCardParser.parse(is);
            } catch (VCardVersionException e1) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                is = new FileInputStream(pathToVcf);

                shouldUseV30 = true;
                mVCardParser = new VCardParser_V30();
                try {
                    counter = new VCardEntryCounter();
                    detector = new VCardSourceDetector();
                    mVCardParser.addInterpreter(counter);
                    mVCardParser.addInterpreter(detector);
                    mVCardParser.parse(is);
                } catch (VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }

            vcardVersion = shouldUseV30 ? VCARD_VERSION_V30 : VCARD_VERSION_V21;
        } catch (VCardNestedException e) {
            Log.w(TAG, "Nested Exception is found (it may be false-positive).");
            // Go through without throwing the Exception, as we may be able to
            // detect the version before it
        }

        // Now, we can actually parse
        Log.d(TAG, "VCardEntryCounter count:" + counter.getCount());

        InputStream ins = new FileInputStream(pathToVcf);

        return readOneVCard(ins, detector.getEstimatedType(), detector.getEstimatedCharset(),
                vcardVersion, this);
    }

    private vCardOnePerson person;

    @Override
    public void onVCardStarted() {
        // Log.d(TAG, "onVCardStarted");
    }

    @Override
    public void onVCardEnded() {
        // Log.d(TAG, "onVCardEnded");
    }

    @Override
    public void onEntryStarted() {
        // Log.d(TAG, "onEntryStarted");
        person = new vCardOnePerson();
        person.type = 0;
    }

    @Override
    public void onEntryEnded() {
        Log.d(TAG, "onEntryEnded");
        // call type could not be determined by parsing .vcf
        if (person.type == 0) {
            Log.w(TAG, "Call Type not found in VCF for phone number:" + person.phone);
            if ((mVcfCallType == CallLog.Calls.OUTGOING_TYPE)
                    || (mVcfCallType == CallLog.Calls.INCOMING_TYPE)
                    || (mVcfCallType == CallLog.Calls.MISSED_TYPE)) {
                person.type = mVcfCallType;
            } else {
                // In case of cch path, we have to default to some valid value
                // else it corrupts calllogs database
                Log.e(TAG, "defaulting call log type to incoming type");
                person.type = CallLog.Calls.INCOMING_TYPE;
            }
        }
        addNumToCallLog(mResolver, person.type, person.name, person.phone, 1, person.time);
    }

    @Override
    public void onPropertyCreated(VCardProperty property) {

        String prop = property.getName();
        Log.d(TAG, "onPropertyCreated prop:" + prop + " value:" + property.getRawValue());
        if (prop.equalsIgnoreCase(VCardConstants.PROPERTY_FN)) {
            person.name = property.getRawValue();
        } else if (prop.equalsIgnoreCase(VCardConstants.PROPERTY_TEL)) {
            person.phone = property.getRawValue();
        } else if (prop.equalsIgnoreCase(VCardConstants.PROPERTY_EMAIL)) {
            person.email = property.getRawValue();
        } else if (prop.equalsIgnoreCase("X-IRMC-CALL-DATETIME")) {
            try {
                String dateTimeVal = property.getRawValue();
                String dateTime = dateTimeVal.replaceAll("T", "");
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                Date date = formatter.parse(dateTime);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                person.time = cal.getTimeInMillis();

                // Map the type in vcf to calllog type
                Map<String, Collection<String>> propertyParameterMap = property.getParameterMap();
                for (String paramType : propertyParameterMap.keySet()) {
                    Collection<String> paramValueList = propertyParameterMap.get(paramType);
                    for (String paramValue : paramValueList) {
                        Log.d(TAG, "paramValue: " + paramValue);
                        if (paramValue.equalsIgnoreCase("DIALED"))
                            person.type = CallLog.Calls.OUTGOING_TYPE;
                        else if (paramValue.equalsIgnoreCase("RECEIVED"))
                            person.type = CallLog.Calls.INCOMING_TYPE;
                        else if (paramValue.equalsIgnoreCase("MISSED"))
                            person.type = CallLog.Calls.MISSED_TYPE;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean readOneVCard(InputStream is, int vcardType, String charset, int vcardVersion,
            final VCardInterpreter interpreter) {
        boolean successful = false;
        try {
            if (interpreter instanceof VCardEntryConstructor) {
                // Let the object clean up internal temporary objects,
                ((VCardEntryConstructor)interpreter).clear();
            }

            synchronized (this) {
                mVCardParser = (vcardVersion == VCARD_VERSION_V30 ? new VCardParser_V30(vcardType)
                : new VCardParser_V21(vcardType));
            }
            mVCardParser.parse(is, interpreter);
            successful = true;
        } catch (IOException e) {
            Log.e(TAG, "IOException was emitted: " + e.getMessage());
        } catch (VCardNestedException e) {
            Log.e(TAG, "Nested Exception is found.");
        } catch (VCardNotSupportedException e) {
            Log.e(TAG, e.toString());
        } catch (VCardVersionException e) {
            Log.e(TAG, "Appropriate version for this vCard is not found.");
        } catch (VCardException e) {
            Log.e(TAG, e.toString());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        return successful;
    }
}
