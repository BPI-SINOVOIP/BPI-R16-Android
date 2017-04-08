/* Copyright 2013 Broadcom Corporation
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

package com.broadcom.bt.service.hidd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;

/**
 * Represents a SDPRecord for the HID device
 *
 * Currently, the SDPRecord is created by specifying an XML file in the format
 * of SDPTOOLS
 */
public class SDPRecord {
    protected File mSDPFile;
    protected int mResourceId;
    protected Context mContext;

    /**
     * Create an SDP record
     */
    public SDPRecord() {
    }


    /**
     * Sets the SDP record configuration file the specified SDPTOOL file
     *
     * @param f
     *            file representing the SDP configuration.
     */
    public void setFile(File f) throws IllegalArgumentException,IOException {
        mSDPFile = f;
        init();
    }

    public void setResource(Context ctx, int id) throws IllegalArgumentException, IOException {
        mContext = ctx;
        mResourceId =id;
        init();
    }

    public void init() throws IllegalArgumentException, IOException{
        String xmlSdpRecord = toXMLString();
        //Parse for report type
    }

    //FIXME: deduce from Attribute 0x206
    private boolean mHasReportIdsInDescriptors=false;
    public void setHasReportIdsInDescriptors(boolean hasIds) {
        mHasReportIdsInDescriptors=hasIds;
    }
    public boolean hasReportIdsInDescriptors() {
        return mHasReportIdsInDescriptors;
    }
    //End FIXME:
    /**
     * Returns a byte array representing the SDP record in the XML format
     *
     * @throws IOException
     *             if the SDP record contents cannot be retrieved
     *
     * @throws IllegalArgumentException
     *             if the SDP record contents is not valid
     * @return byte array representing the SDP record
     */
    public byte[] toXMLBytes() throws IllegalArgumentException, IOException {
        if (mSDPFile != null && mSDPFile.isFile()) {
            return toXMLBytes_FromFile();
        } else if (mResourceId >0) {
            return toXMLBytes_FromResourceId();
        } else {
            throw new IllegalArgumentException("Invalid SDP file or resource id " +
                (mSDPFile == null ? "null" : mSDPFile.getAbsolutePath()));
        }
    }

    public byte[] toXMLBytes_FromFile() throws IllegalArgumentException, IOException {
        IOException ioe = null;
        ;
        byte[] buf = null;
        int bytes = (int) mSDPFile.length();
        FileInputStream ios = null;
        try {
            ios = new FileInputStream(mSDPFile);
            buf = new byte[bytes];
            ios.read(buf);
        } catch (Throwable t) {
            ioe = new IOException("Unable to read SDPRecord from file "+mSDPFile.getAbsolutePath()
                + ". ");
        }

        if (ios != null) {
            try {
                ios.close();
            } catch (Throwable t) {
            }
            ios = null;
        }

        if (ioe != null) {
            throw ioe;
        }
        return buf;
    }

    public byte[] toXMLBytes_FromResourceId() throws IllegalArgumentException, IOException {

        IOException ioe = null;
        byte[] buf = null;
        InputStream ios = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        int bytesRead = 0;
        try {
            ios = mContext.getResources().openRawResource(mResourceId);
            buf = new byte[1024];
            while ((bytesRead = ios.read(buf)) >= 0) {
                bos.write(buf, 0, bytesRead);
            }
        } catch (Throwable t) {
            ioe = new IOException("Unable to read SDPRecord from file "+mSDPFile.getAbsolutePath()
                + ". ");
        }

        if (ios != null) {
            try {
                ios.close();
            } catch (Throwable t) {
            }
            ios = null;
        }
        bos.close();
        if (ioe != null) {
            bos = null;
            throw ioe;
        }
        return bos.toByteArray();
    }

    /**
     * Returns a string representing the SDP record in the XML format
     *
     * @throws IOException
     *             if the SDP record contents cannot be retrieved
     *
     * @throws IllegalArgumentException
     *             if the SDP record contents is not valid
     *
     * @return string representing the SDP reocrd
     */
    public String toXMLString() throws IllegalArgumentException, IOException {
        byte[] buf = toXMLBytes();
        return buf == null ? null : new String(buf);
    }

}
