/**
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) 2009-2012, Broadcom Corporation
 *
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
package com.broadcom.bt.pbap;

import javax.obex.ApplicationParameter;

import android.util.Log;

/**
 * Helper class to hold an OBEX Application Parameter Value
 *
 * Note: this class was copied from PBAP Server
 *
 * @author fredc@broadcom.com
 *
 * @hide
 *
 */
public class AppParamValue {
    private static final String TAG = "AppParamValue";
    private static final boolean DBG = true;

    public boolean mNeedPhonebookSize;

    public byte[] mFilter;

    public int mMaxListCount;

    public int mListStartOffset;

    public int mPhonebookSize;

    public int mMissedCalls;

    public String mSearchValue;

    // Indicate which vCard parameter the search operation shall be carried
    // out on. Can be "Name | Number | Sound", default value is "Name".
    public String mSearchAttr;

    // Indicate which sorting order shall be used for the
    // <x-bt/vcard-listing> listing object.
    // Can be "Alphabetical | Indexed | Phonetical", default value is
    // "Indexed".
    public String mOrder;

    public int mNeedTag;

    public boolean mIsVcard21;

    public AppParamValue() {
        mMaxListCount = 0xFFFF;
        mListStartOffset = 0;
        mSearchValue = "";
        mSearchAttr = "";
        mOrder = "";
        mNeedTag = 0x00;
        mIsVcard21 = true;
        mPhonebookSize = -1;
    }

    public void dump() {
        Log.i(TAG, "maxListCount=" + mMaxListCount + " listStartOffset=" + mListStartOffset
                + " searchValue=" + mSearchValue + " searchAttr=" + mSearchAttr + " needTag="
                + mNeedTag + " vcard21=" + mIsVcard21 + " order=" + mOrder + " phonebookSize="
                + mPhonebookSize + " missedCalls=" + mMissedCalls);
        try {
            StringBuilder filterVal = new StringBuilder();
            filterVal.append("filter=");
            if (mFilter == null || mFilter.length == 0) {
                filterVal.append("(not set)");
            } else {
                filterVal.append("0x");
                for (int i = 0; i < mFilter.length; i++) {
                    filterVal.append(String.format("%02x", mFilter[i]));
                }
            }
            Log.i(TAG, filterVal.toString());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** To parse obex application parameter */
    public final boolean parse(final byte[] appParam) {
        int i = 0;
        int lowValue = 0;
        int highValue = 0;
        boolean parseOk = true;
        while (i < appParam.length) {
            switch (appParam[i]) {
            case ApplicationParameter.TRIPLET_TAGID.FILTER_TAGID:
                Log.i(TAG, "Processing filter....");
                i += 2; // length and tag field in triplet
                // Broadcom enhancement: parse for filter
                if (mFilter == null) {
                    mFilter = new byte[ApplicationParameter.TRIPLET_LENGTH.FILTER_LENGTH];
                }
                System.arraycopy(appParam, i, mFilter, 0,
                        ApplicationParameter.TRIPLET_LENGTH.FILTER_LENGTH);
                i += ApplicationParameter.TRIPLET_LENGTH.FILTER_LENGTH;

                break;
            case ApplicationParameter.TRIPLET_TAGID.ORDER_TAGID:
                i += 2; // length and tag field in triplet
                mOrder = Byte.toString(appParam[i]);
                i += ApplicationParameter.TRIPLET_LENGTH.ORDER_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.SEARCH_VALUE_TAGID:
                i += 1; // length field in triplet
                // length of search value is variable
                int length = appParam[i];
                if (length == 0) {
                    parseOk = false;
                    break;
                }
                if (appParam[i + length] == 0x0) {
                    mSearchValue = new String(appParam, i + 1, length - 1);
                } else {
                    mSearchValue = new String(appParam, i + 1, length);
                }
                i += length;
                i += 1;
                break;
            case ApplicationParameter.TRIPLET_TAGID.SEARCH_ATTRIBUTE_TAGID:
                i += 2;
                mSearchAttr = Byte.toString(appParam[i]);
                i += ApplicationParameter.TRIPLET_LENGTH.SEARCH_ATTRIBUTE_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.MAXLISTCOUNT_TAGID:
                i += 2;
                if (appParam[i] == 0 && appParam[i + 1] == 0) {
                    mNeedPhonebookSize = true;
                } else {
                    highValue = appParam[i] & 0xff;
                    lowValue = appParam[i + 1] & 0xff;
                    mMaxListCount = highValue * 256 + lowValue;
                }
                i += ApplicationParameter.TRIPLET_LENGTH.MAXLISTCOUNT_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.LISTSTARTOFFSET_TAGID:
                i += 2;
                highValue = appParam[i] & 0xff;
                lowValue = appParam[i + 1] & 0xff;
                mListStartOffset = highValue * 256 + lowValue;
                i += ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.FORMAT_TAGID:
                i += 2;// length field in triplet
                if (appParam[i] != 0) {
                    mIsVcard21 = false;
                }
                i += ApplicationParameter.TRIPLET_LENGTH.FORMAT_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.PHONEBOOKSIZE_TAGID:
                i += 2;
                highValue = appParam[i] & 0xff;
                lowValue = appParam[i + 1] & 0xff;
                mPhonebookSize = highValue * 256 + lowValue;
                i += ApplicationParameter.TRIPLET_LENGTH.PHONEBOOKSIZE_LENGTH;
                break;
            case ApplicationParameter.TRIPLET_TAGID.NEWMISSEDCALLS_TAGID:

                i += 2;
                mMissedCalls = appParam[i];
                Log.d(TAG, "Parsing for new missed calls: " + mMissedCalls);
                i += ApplicationParameter.TRIPLET_LENGTH.NEWMISSEDCALLS_LENGTH;
                break;
            default:
                parseOk = false;
                Log.e(TAG, "Parse Application Parameter error");
                break;
            }
        }
        if (DBG)
            dump();

        return parseOk;
    }

}
