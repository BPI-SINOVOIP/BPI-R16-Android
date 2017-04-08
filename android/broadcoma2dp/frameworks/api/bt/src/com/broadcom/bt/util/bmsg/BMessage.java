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

import java.io.File;
import java.io.IOException;

import android.util.Log;

/***
 * The BMessage class encapsulates a MAP message retrieved from MAP server.  It contains
 * message body, sender and recipient.
 *
 */
public class BMessage extends BMessageBase {
    private static final String TAG = "BMessage";
    private static final boolean ERR_CHECK = BMessageManager.ERR_CHECK;

    /**
     * Parse BMessage file to create BMessage object
     *
     * @throws IOException
     *             if memory cannot be allocated
     *
     *@param f File object for BMessage file
     *@return BMessage object
     */
    public static BMessage parse(File f) {
        if (!f.exists() || !f.isFile()) {
            Log.e(TAG, "Unable to parse " + f.getAbsolutePath() + ". Invalid file.");
            return null;
        }

        int nativeObj = BMessageManager.parseBMsgFile(f.getAbsolutePath());
        if (nativeObj > 0) {
            return new BMessage(nativeObj);
        }
        return null;
    }

    /**
     * Parse BMessage file to create BMessage object
     *
     * @throws IOException
     *             if memory cannot be allocated
     *
     *@param fd file descriptor for BMessage file
     *@return BMessage object
     */
    public static BMessage parse(int fd) {
        int nativeObj = BMessageManager.parseBMsgFileFD(fd);
        if (nativeObj > 0) {
            return new BMessage(nativeObj);
        }
        return null;
    }

    /**
     * Write BMessage information to file
     *
     *@param fd file descriptor for BMessage file
     *@return true if successful to write to BMessage file, false otherwise
     */
    public boolean write(int fd) {
        if (fd>0) {
            return BMessageManager.writeBMsgFileFD(mNativeObjectRef, fd);
        }
        Log.e(TAG,"Unable to write bmessage to file descriptor " + fd);
        return false;
    }

    /**
     * Write BMessage information to bmsg file
     *
     *@param f file object for BMessage file
     *@return true if successful to write to BMessage file, false otherwise
     */
    public boolean write(File f) {
        if (f.exists()) {

            Log.e(TAG, "Unable to write to " + f.getAbsolutePath() + ". File already exists.");
            return false;
        }
        return BMessageManager.writeBMsgFile(mNativeObjectRef, f.getAbsolutePath());
    }

    /**
     * Creates a BMessage object. If the object cannot allocate memory, throws
     * an IOException
     *
     * @throws IOException
     *             if memory cannot be allocated
     */
    public BMessage() throws IOException {
        if (!setNativeRef(BMessageManager.createBMsg())) {
            throw new IOException("Unable to create BMesage object");
        }
    }

    /**
     * @hide
     * @param nativeRef
     */
    BMessage(int nativeRef) {
        setNativeRef(nativeRef);
    }

    /**
     * Free resources of the BMessage
     */
    public void finish() {
        if (isNativeCreated()) {
            BMessageManager.deleteBMsg(mNativeObjectRef);
            clearNativeRef();
        }
    }

    /**
     * @hide
     */
    protected void finalize() {
        finish();
    }

    /**
     * Set the read status for BMessage
     *
     * @param isRead
     *            if true, message is read.
     */
    public void setReadStatus(boolean isRead) {
        BMessageManager.setBMsgRd(mNativeObjectRef, isRead);
    }

    /**
     * Returns the read/unread status of BMessage
     *
     * @return true, if message is read.
     */
    public boolean isRead() {
        return BMessageManager.isBMsgRd(mNativeObjectRef);
    }

    /**
     * Set the message type of this BMessage
     *
     * @param msgType
     *            The message type: Possible values are
     *            {@link BMessageConstants#BTA_MA_MSG_TYPE_EMAIL},
     *            {@link BMessageConstants#BTA_MA_MSG_TYPE_MMS},
     *            {@link BMessageConstants#BTA_MA_MSG_TYPE_SMS_GSM},
     *            {@link BMessageConstants#BTA_MA_MSG_TYPE_SMS_CDMA}
     */
    public void setMessageType(byte msgType) {
        if (ERR_CHECK && BMessageManager.hasBitError(msgType, 4)) {
            Log.e(TAG, "Invalid message type: " + msgType);
            return;
        }
        BMessageManager.setBMsgMType(mNativeObjectRef, msgType);
    }

    /**
     * Get the message type of the BMessage
     *
     * @return {@link BMessageConstants#INVALID_VALUE} if message type is not
     *         set. Otherwise returns a valid message type defined in
     *         {@link BMessageConstants}
     */
    public byte getMessageType() {
        return BMessageManager.getBMsgMType(mNativeObjectRef);
    }

    /**
     * Set the folder path of BMessage.
     * Example: telecom/msg/inbox
     *
     * @param folder
     *          path of BMessage
     */
    public void setFolder(String folder) {
        BMessageManager.setBMsgFldr(mNativeObjectRef, folder);
    }

    /**
     * Get the folder path of BMessage
     *
     * @return String Folder path of BMessage
     */
    public String getFolder() {
        return BMessageManager.getBMsgFldr(mNativeObjectRef);

    }

    /**
     * Add original sender to the MAP message
     *
     * @return original sender as vcard.
     */
    public BMessageVCard addOriginator() {
        int nativeObject = BMessageManager.addBMsgOrig(mNativeObjectRef);
        if (nativeObject <= 0) {
            Log.e(TAG, "Unable to create native VCard for BMessage originator object");
            return null;
        }
        BMessageVCard vCard = new BMessageVCard(this, nativeObject);
        return vCard;
    }

    /**
     * Get original sender of the MAP message
     *
     * @return original sender as BMessageVCard object
     */
    public BMessageVCard getOriginator() {
        int nativeMessageVCard = BMessageManager.getBMsgOrig(mNativeObjectRef);
        if (nativeMessageVCard <= 0) {
            return null;
        }
        BMessageVCard vCard = new BMessageVCard(this, nativeMessageVCard);
        return vCard;

    }

    /**
     * Add BMessageEnvelope to add more recipients.
     * The maximum level of encapsulations shall be 3.
     *
     * @return BMessageEnvelope which encapsulates BMessage
     */
    public BMessageEnvelope addEnvelope() {
        int nativeObject = BMessageManager.addBMsgEnv(mNativeObjectRef);
        if (nativeObject <= 0) {
            Log.e(TAG, "Unable to create native Envelope object for BMessage");
            return null;
        }
        BMessageEnvelope obj = new BMessageEnvelope(this, nativeObject);
        return obj;
    }

    /**
     * Get BMessageEnvelope which encapsulates BMessage or another BMessageEnvelope.
     * If Encapsulation levels exceeds 3, MSE will deliver only the upper 3 levels.
     *
     * @return BMessageEnvelope which encapsulates BMessage or another BMessageEnvelope
     */
    public BMessageEnvelope getEnvelope() {
        int nativeObject = BMessageManager.getBMsgEnv(mNativeObjectRef);
        if (nativeObject <= 0) {
            return null;
        }
        BMessageEnvelope vCard = new BMessageEnvelope(this, nativeObject);
        return vCard;
    }

    /**
     * @hide
     */
    public String decodeSMSSubmitPDU(String submitPDU) {
        Log.d(TAG, "decodeSMSSubmitPDU");
        return BMessageManager.decodeSMSSubmitPDU(submitPDU);
    }

    /**
     * @hide
     */
    public String encodeSMSDeliverPDU(String content, String recipient, String sender, String dateTime) {
        Log.d(TAG, "encodeSMSDeliverPDU");
        return BMessageManager.encodeSMSDeliverPDU(content, recipient, sender, dateTime);
    }
    // ----------Native helper methods

}
