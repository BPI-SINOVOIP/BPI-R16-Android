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

package com.broadcom.bt.map;

import android.util.Log;

import com.broadcom.bt.map.BluetoothMessageInfo;
import com.broadcom.bt.util.bmsg.BMessage;
import com.broadcom.bt.util.bmsg.BMessageBody;
import com.broadcom.bt.util.bmsg.BMessageBodyContent;
import com.broadcom.bt.util.bmsg.BMessageConstants;
import com.broadcom.bt.util.bmsg.BMessageEnvelope;
import com.broadcom.bt.util.bmsg.BMessageVCard;
import com.broadcom.bt.util.bmsg.BMessageVCardProperty;

/**
 * @hide
 */
public class BMessageUtil {
    private static final String TAG = "BtMap.BMessageUtil";

    public static void setBMessageType(BMessage bMsg, BluetoothMessageInfo mInfo) {
        if (mInfo != null) {
            if ((mInfo.mMsgType & BluetoothMessageInfo.MSG_TYPE_EMAIL) == BluetoothMessageInfo.MSG_TYPE_EMAIL) {
                bMsg.setMessageType(BMessageConstants.BTA_MA_MSG_TYPE_EMAIL);
            } else if ((mInfo.mMsgType & BluetoothMessageInfo.MSG_TYPE_SMS_CDMA) == BluetoothMessageInfo.MSG_TYPE_SMS_CDMA) {
                bMsg.setMessageType(BMessageConstants.BTA_MA_MSG_TYPE_SMS_CDMA);
            } else if ((mInfo.mMsgType & BluetoothMessageInfo.MSG_TYPE_SMS_GSM) == BluetoothMessageInfo.MSG_TYPE_SMS_GSM) {
                bMsg.setMessageType(BMessageConstants.BTA_MA_MSG_TYPE_SMS_GSM);
            } else if ((mInfo.mMsgType & BluetoothMessageInfo.MSG_TYPE_MMS) == BluetoothMessageInfo.MSG_TYPE_MMS) {
                bMsg.setMessageType(BMessageConstants.BTA_MA_MSG_TYPE_MMS);
            } else {
                Log.e(TAG, "Unable to set message type");
            }
        }
    }

    /**
     * Set a BMessage Header Info from the BluetoothMessageInfo object
     *
     * @param bMsg
     * @param vCardVersionId
     * @param folderPath
     * @param mInfo
     */
    public static void setBMessageHeaderInfo(BMessage bMsg, byte vCardVersionId, String folderPath,
            BluetoothMessageInfo mInfo) {
        if (bMsg == null
                || !(vCardVersionId == BMessageConstants.BTA_MA_VCARD_VERSION_21 || vCardVersionId == BMessageConstants.BTA_MA_VCARD_VERSION_30)) {
            Log.w(TAG, "Unable to set BMessage Header Info");
            return;
        }
        // Set bMessage header info
        // bMsg.setVersion("1.0");
        bMsg.setReadStatus(mInfo.mIsRead);
        bMsg.setFolder(folderPath);
        setBMessageType(bMsg, mInfo);

        // Set originator info
        BMessageVCard bOriginator = bMsg.addOriginator();

        // There is only one originator

        BluetoothPersonInfo originator = mInfo.mSender;
        String originatorAddress = mInfo.mSenderAddress;
        bOriginator.setVersion(vCardVersionId);
        if (originator == null) {
            bOriginator.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_N, "", null);
        } else {
            bOriginator.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_N,
                    originator.toVcardField_N(), null);
            bOriginator.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_FN,
                    originator.toVcardField_FN(), null);
        }
        bOriginator.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_TEL, originatorAddress, null);

        // Set envelope info
        BMessageEnvelope bEnv = bMsg.addEnvelope();

        // Set recipient(s) info
        int count = mInfo.mRecipientAddress == null ? 0 : mInfo.mRecipientAddress.size();
        int infoSize = mInfo.mRecipient == null ? 0 : mInfo.mRecipient.size();
        for (int i = 0; i < count; i++) {
            String addr = mInfo.mRecipientAddress.get(i);
            BluetoothPersonInfo pInfo = null;
            if (i < infoSize) {
                pInfo = mInfo.mRecipient.get(i);
            }
            BMessageVCard bRecipient = bEnv.addRecipient();
            bRecipient.setVersion(vCardVersionId);
            if (pInfo == null) {
                bRecipient.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_N, "", null);
            } else {
                bRecipient.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_N,
                        pInfo.toVcardField_N(), null);
                bRecipient.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_FN,
                        pInfo.toVcardField_FN(), null);
            }
            bRecipient.addProperty(BMessageConstants.BTA_MA_VCARD_PROP_TEL, addr, null);
        }
    }

    /**
     * @hide
     */
    public static BMessage toBMessage(BluetoothMessageInfo mInfo, String virtualPath, byte bCharset,
            String content) {
        BMessage bMsg = null;
        try {
            bMsg = new BMessage();
            BMessageUtil.setBMessageHeaderInfo(bMsg, BMessageConstants.BTA_MA_VCARD_VERSION_21,
                    virtualPath, mInfo);

            // Set message body
            BMessageEnvelope bEnv = bMsg.getEnvelope();
            BMessageBody bBody = bEnv.addBody();
            // default is utf8

            bBody.setCharSet(BMessageConstants.BTA_MA_CHARSET_UTF_8);

            // Per spec for SMS-CDMA or SMS-GSM
            //
            // Message encoding rule for SMS Text: (1) UTF-8 charset (2)
            // encoding property NOT set (3) Exactly 1 body content
            //
            // bBody.setCharSet(BMessageConstants.BTA_MA_CHARSET_UTF_8);
            BMessageBodyContent bContent = bBody.addContent();
            //
            // if native charset is requested, convert the content to
            // native message
            //
            if (bCharset == BMessageConstants.BTA_MA_CHARSET_NATIVE
                    && mInfo.mMsgType == BluetoothMessageInfo.MSG_TYPE_SMS_GSM) {
                Log.d(TAG, "Native charset requested");
                String encodedContent = bMsg.encodeSMSDeliverPDU(content,
                        mInfo.mRecipientAddress == null || mInfo.mRecipientAddress.size() < 1 ? ""
                                : mInfo.mRecipientAddress.get(0), mInfo.mSenderAddress,
                        mInfo.mDateTime);
                if (null == encodedContent) {
                    Log.d(TAG, "Native charset requested but encoding failed");
                } else {
                    Log.d(TAG, "Native charset requested - encoding succeeded - " + encodedContent);
                    content = encodedContent;
                    bBody.setCharSet(bCharset);
                    bBody.setEncoding(BMessageConstants.BTA_MA_BMSG_ENC_G7BIT);
                }
            }
            // bBody.setLanguage(lang);
            // bBody.setPartId(partId);

            bContent.addMessageContent(content);
        } catch (Throwable t) {
            Log.e(TAG, "Error creating BMessage", t);
            if (bMsg != null) {
                bMsg.finish();
                bMsg = null;
            }
        }
        return bMsg;
    }

    /**
     * Get recipient's contact property (TEL, email, etc). The recipient may be
     * in a nested envelope, so we search starting from the parent envelope, and
     * search inward into each child envelope if the property was not found in
     * the current envelope
     *
     * @param bEnv
     *            the parent BMessage Envelope to start searching from
     * @param propId
     *            , the identifier for the property to return. For example,
     *            BMessageConstants.BTA_MA_VCARD_PROP_TEL
     *            ,BMessageConstants.BTA_MA_VCARD_PROP_FN,etc
     * @return the property value if found, or null if not.
     */
    public static BMessageVCardProperty findRecipientProperty(BMessageEnvelope bEnv, byte propId) {
        int nestCount = 1;
        while (bEnv != null) {
            Log.d(TAG, "Finding recipient in envelope level #" + nestCount);
            BMessageVCard bRecipient = bEnv.getRecipient();
            if (bRecipient != null) {
                BMessageVCardProperty bProp = bRecipient.getProperty(propId);
                if (bProp != null) {
                    Log.d(TAG, "findRecipientProperty(): Found property!");
                    return bProp;
                }
            }
            bEnv = bEnv.getChildEnvelope();
            nestCount++;
        }
        return null;
    }

    /**
     * Get message body property. The message body may be in a nested envelope,
     * so we search starting from the parent envelope, and search inward into
     * each child envelope if the property was not found in the current envelope
     *
     * @param bEnv
     *            the parent BMessage Envelope to start searching from
     * @return the message body if found, or null if not.
     */
    public static BMessageBody findMessageBody(BMessageEnvelope bEnv) {
        int nestCount = 1;
        while (bEnv != null) {
            Log.d(TAG, "Finding message body in envelope level #" + nestCount);
            BMessageBody bBody = bEnv.getBody();
            if (bBody != null) {
                Log.d(TAG, "findMessageBody(): Found body!");
                return bBody;
            }
            bEnv = bEnv.getChildEnvelope();
            nestCount++;
        }
        return null;
    }
}
