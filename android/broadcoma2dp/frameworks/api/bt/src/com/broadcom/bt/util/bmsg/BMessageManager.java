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


/**
 * Helper class to interact with native layer
 *
 * @author fredc
 * @hide
 */
final class BMessageManager {
    static final boolean ERR_CHECK = true;

    static {
    	System.loadLibrary("bt-client-api");
    }
    /**
     * Helper function to check if a byte value has exactly 1 bit set
     *
     * @param val
     * @param numOfBits
     * @return
     */
    static boolean hasBitError(int val, int numOfBits) {
        int bitsSet = 0;
        for (int i = 0; i <= numOfBits - 1; i++) {
            if ((val & (1 << i)) > 0) {
                if (bitsSet == 1) {
                    return true;
                }
                bitsSet++;
            }
        }
        return bitsSet != 1;
    }

    // --------------BMessage Native Functions ------------------------------
    static native int parseBMsgFile(String filePath);

    static native int parseBMsgFileFD(int fd);

    static native boolean writeBMsgFile(int bMsgObjRef, String filePath);

    static native boolean writeBMsgFileFD(int bMsgObjRef, int fd);

    static native int createBMsg();

    static native void deleteBMsg(int bMsgObjRef);

    static native void setBMsgMType(int bMsgObjRef, byte msgType);

    static native byte getBMsgMType(int bMsgObjRef);

    static native int addBMsgOrig(int bMsgObjRef);

    static native int getBMsgOrig(int bMsgObjRef);

    static native int addBMsgEnv(int bMsgObjRef);

    static native int getBMsgEnv(int bMsgObjRef);

    static native void setBMsgRd(int bMsgObjRef, boolean isRead);

    static native boolean isBMsgRd(int bMsgObjRef);

    static native void setBMsgFldr(int bMsgObjRef, String folder);

    static native String getBMsgFldr(int bMsgObjRef);

    static native int addBEnvChld(int bEnvObjRef);

    static native int getBEnvChld(int bEnvObjRef);

    static native int addBEnvRecip(int bEnvObjRef);

    static native int getBEnvRecip(int bEnvObjRef);

    static native int addBEnvBody(int bEnvObjRef);

    static native int getBEnvBody(int bEnvObjRef);

    static native void setBBodyEnc(int bBodyObjRef, byte encoding);

    static native byte getBBodyEnc(int bBodyObjRef);

    static native void setBBodyPartId(int bBodyObjRef, int partId);

    static native int getBBodyPartId(int bBodyObjRef);

    //static native void setBBodyMultiP(int bBodyObjRef, boolean isMultipart);

    static native boolean isBBodyMultiP(int bBodyObjRef);

    static native void setBBodyCharset(int bBodyObjRef, byte charSetId);

    static native byte getBBodyCharset(int bBodyObjRef);

    static native void setBBodyLang(int bBodyObjRef, byte lang);

    static native byte getBBodyLang(int bBodyObjRef);

    static native int addBBodyCont(int bBodyObjRef);

    static native int getBBodyCont(int bBodyObjRef);

    //static native int addBContNext(int bBContObjRef);

    static native int getBContNext(int bBContObjRef);

    static native void addBContMsg(int bBContObjRef, String content);

    static native String getBCont1stMsg(int bBContObjRef);

    static native String getBContNextMsg(int bBContObjRef);

    //static native int addBvCardNext(int bvCardObjRef);

    static native int getBvCardNext(int bvCardObjRef);

    static native void setBvCardVer(int bvCardObjRef, byte version);

    static native byte getBvCardVer(int bvCardObjRef);

    static native int addBvCardProp(int bvCardObjRef, byte propId, String value, String param);

    static native int getBvCardProp(int bvCardObjRef, byte propId);

    static native int getBvCardPropNext(int bvCardPObjRef);

    static native String getBvCardPropVal(int bvCardPObjRef);

    static native String getBvCardPropParam(int bvCardPObjRef);

    static native String decodeSMSSubmitPDU( String pdu);

    static native String encodeSMSDeliverPDU(String content, String recipient, String sender, String dateTime);

    // --------------BMessage Native Functions ------------------------------

}
