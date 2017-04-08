/* Copyright 2009-2012 Broadcom Corporation
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
package com.broadcom.bt.service.ftp;

/**
 * Specifies the configuration options for the FTP Server.
 *
 * @hide
 */
class FTPServiceConfig {
    /**
     * Verbose level debugging flag for FTP
     */
    static final boolean V = true;

    /**
     * Debug level debugging flag for FTP
     */
    static final boolean D = true;

    /**
     * If true, events are returned back to client applications as Broadcast
     */
    static final boolean USE_BROADCAST_INTENTS = true;

    /**
     * If true, events are returned back to client applications as Broadcasts
     * using the BTL-2.0 events. This option should not be used for new releases
     * that do not need to be migrated from BTL-A 2.0
     */
    static final boolean USE_LEGACY_BROADCAST_INTENTS = false;

    /**
     * Use remote callbacks to send events from BTL-A to application.
     * {@link #USE_BROADCAST_INTENTS}must be set to false for this setting to be
     * enabled
     */
    static final boolean USE_CALLBACKS = false;


    /**
     * If true, an access request event will always be broadcasted to solicit
     * for access request.
     *
     * If false, an access request event will only be broadcasted IF the system
     * property "service.brcm.bt.secure_mode" is "true". If the system property
     * is not set or not set to "true", the FTP Event Loop will automatically
     * respond to the BTL-A stack to allow the access
     */
    static final boolean FORCE_ACCESS_REQUEST = false;

    /**
     * If true, FTP puts/deletes invoke the media scanner to refresh the media
     * cache
     */
    static final boolean USE_MEDIA_SCANNER = true;



    /**
     * Specify the action commands to support
     * NOTE: Setting file permissions not supported
     * if the FTP root directory is /mnt/sdcard/ or /sdcard
     *
     */
    static final byte[] SUPPORTED_ACTION_COMMANDS= {
    //BluetoothFTP.FTPS_OPER_COPY
    //,BluetoothFTP.FTPS_OPER_MOVE
    //,BluetoothFTP.FTPS_OPER_SET_PERM
    };

}
