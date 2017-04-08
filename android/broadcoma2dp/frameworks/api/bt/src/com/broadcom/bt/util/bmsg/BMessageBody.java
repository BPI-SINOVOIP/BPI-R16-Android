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

package com.broadcom.bt.util.bmsg;

import android.util.Log;

/***
 * The BMessageBody class holds information about the body of a MAP message.
 * It is used by {@link BMessageEnvelope} class.
 *
 */
public class BMessageBody extends BMessageBase {
    private static final String TAG = "BMessageBody";
    private static final boolean ERR_CHECK = BMessageManager.ERR_CHECK;

    private BMessageEnvelope mParent;

    /**
     * @hide
     */
    BMessageBody(BMessageEnvelope parent, int nativeRef) {
        mParent = parent;
        setNativeRef(nativeRef);
    }

    /**
     * Set the encoding of the message body.
     *
     * @param encoding
     *            The message body encoding: Possible values are
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_8BIT},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_G7BIT},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_G7BITEXT},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_GUCS2},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_G8BIT},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_C8BIT},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CEPM},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_C7ASCII},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CIA5},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CUNICODE},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CSJIS},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CKOREAN},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CLATINHEB},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_CLATIN},
     *            {@link BMessageConstants#BTA_MA_BMSG_ENC_UNKNOWN}.
     */
    public void setEncoding(byte encoding) {
        if (ERR_CHECK && encoding < BMessageConstants.BTA_MA_BMSG_ENC_8BIT
                || encoding >= BMessageConstants.BTA_MA_BMSG_ENC_UNKNOWN) {
            Log.e(TAG, "Invalid encoding: " + encoding);
            return;
        }
        BMessageManager.setBBodyEnc(mNativeObjectRef, encoding);
    }

    /**
     * Get the encoding of the message body.
     *
     * @return {@link BMessageConstants#BTA_MA_BMSG_ENC_8BIT} if body is not
     *         set. Otherwise returns a encoding value defined in
     *         {@link BMessageConstants}.
     */
    public byte getEncoding() {
        return isNativeCreated() ? BMessageManager.getBBodyEnc(mNativeObjectRef)
                : BMessageConstants.BTA_MA_BMSG_ENC_UNKNOWN;
    }

    /**
     * Set part Id for Multipart message.
     * This is used if and only if the content of the related message can't be
     * delivered completely within one BMessageBodyContent object, i.e in case of a fragmented
     * email.
     */
    public void setPartId(int partId) {
        if (isNativeCreated()) {
            BMessageManager.setBBodyPartId(mNativeObjectRef, partId);
        }
    }

    /**
     * Get part Id for Multipart message.
     * This is used if and only if the content of the related message can't be
     * delivered completely within one BMessageBodyContent object, i.e in case of a fragmented
     * email.
     *
     * @return int part Id for Content of the message.
     */
    public int getPartId() {
        return (isNativeCreated() ? BMessageManager.getBBodyPartId(mNativeObjectRef) : Integer.MIN_VALUE);
    }

    /*
     * public void setMultipart(boolean isMutipart) { if (isNativeCreated()) {
     * BMessageManager.setBBodyMultiP(mNativeObjectRef, true); } }
     */

    /**
     * Check if message has multiple parts.
     *
     * @return boolean true, if message has multiple parts.
     */
    public boolean isMultipart() {
        return isNativeCreated() ? false : BMessageManager.isBBodyMultiP(mNativeObjectRef);

    }

    /**
     * Set the character set of the message body.
     *
     * @param charset
     *            The message body encoding: Possible values are
     *            {@link BMessageConstants#BTA_MA_CHARSET_NATIVE},
     *            {@link BMessageConstants#BTA_MA_CHARSET_UTF_8}.
     */
    public void setCharSet(byte charset) {
        if (ERR_CHECK && charset < BMessageConstants.BTA_MA_CHARSET_NATIVE
                || charset > BMessageConstants.BTA_MA_CHARSET_UTF_8) {
            Log.e(TAG, "Invalid charset: " + charset);
            return;
        }
        BMessageManager.setBBodyCharset(mNativeObjectRef, charset);
    }

    /**
     * Get the character set of the message body.
     *
     * @return {@link BMessageConstants#BTA_MA_CHARSET_UNKNOWN} if body is not
     *         set. Otherwise returns a charset id value defined in
     *         {@link BMessageConstants}.
     */
    public byte getCharSet() {
        return isNativeCreated() ? BMessageManager.getBBodyCharset(mNativeObjectRef)
                : BMessageConstants.BTA_MA_CHARSET_UNKNOWN;
    }

    /**
     * Set the language of the message body.
     *
     * @param lang
     *            The message body language: Possible values are
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_UNSPECIFIED},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_UNKNOWN},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_SPANISH},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_TURKISH},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_PORTUGUESE},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_ENGLISH},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_FRENCH},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_JAPANESE},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_KOREAN},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_CHINESE},
     *            {@link BMessageConstants#BTA_MA_BMSG_LANG_HEBREW}.
     */
    public void setLanguage(byte lang) {
        if (ERR_CHECK
                && !(lang == BMessageConstants.BTA_MA_BMSG_LANG_UNSPECIFIED || (lang >= BMessageConstants.BTA_MA_BMSG_LANG_SPANISH && lang <= BMessageConstants.BTA_MA_BMSG_LANG_HEBREW))) {
            Log.e(TAG, "Invalid language: " + lang);
            return;
        }
        BMessageManager.setBBodyLang(mNativeObjectRef, lang);
    }

    /**
     * Get the language of the message body.
     *
     * @return {@link BMessageConstants#BTA_MA_BMSG_LANG_UNKNOWN} if body is not
     *         set. Otherwise returns a charset id value defined in
     *         {@link BMessageConstants}.
     */
    public byte getLanguage() {
        return isNativeCreated() ? BMessageManager.getBBodyLang(mNativeObjectRef)
                : BMessageConstants.BTA_MA_BMSG_LANG_UNKNOWN;
    }

    /**
     * Add Content to message body.
     *
     * @return returns content as {@link BMessageBodyContent}.
     */
    public BMessageBodyContent addContent() {
        if (isNativeCreated()) {
            int childNativeObject = BMessageManager.addBBodyCont(mNativeObjectRef);
            if (childNativeObject > 0) {
                return new BMessageBodyContent(this, childNativeObject);
            }
        }
        return null;
    }

    /**
     * Get Content from message body.
     *
     * @return returns content as {@link BMessageBodyContent},
     *         null if no content is present.
     */
    public BMessageBodyContent getContent() {
        if (isNativeCreated()) {
            int childNativeObject = BMessageManager.getBBodyCont(mNativeObjectRef);
            if (childNativeObject > 0) {
                return new BMessageBodyContent(this, childNativeObject);
            }
        }
        return null;
    }

}
