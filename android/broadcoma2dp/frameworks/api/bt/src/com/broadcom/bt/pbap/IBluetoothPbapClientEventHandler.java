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
package com.broadcom.bt.pbap;

import android.bluetooth.BluetoothDevice;

/**
 * Event callback interface invoked by BluetoothPbapClient to notify application
 * completion of requests.
 *
 * @author fredc@broadcom
 *
 */
public interface IBluetoothPbapClientEventHandler {
    /**
     * Callback invoked when a PBAP Server connect completes.
     *
     * @param device The remote PBAP Server.
     * @param success If true, the {@link BluetoothPbapClient#connect} succeeded.
     */
    public void onConnected(BluetoothDevice device, boolean success);

    /**
     * Callback invoked when a PBAP Server disconnect completes.
     *
     * @param device The remote PBAP Server.
     * @param success If true, the {@link BluetoothPbapClient#disconnect} succeeded.
     */
    public void onDisconnected(BluetoothDevice device, boolean success);

    /**
     * Callback invoked when PBAP Server set path requst completes.
     *
     * @param device The remote PBAP Server.
     * @param success If true, the {@link BluetoothPbapClient#setPath} succeeded.
     * @param path If success, the current path of the PBAP Server.
     */
    public void onPathSet(BluetoothDevice device, boolean success, String path);

    /**
     * Callback invoked when PBAP Server pull phonebook request completes.
     *
     * @param device The remote PBAP Server.
     * @param path The path that the {@link BluetoothPbapClient#pullPhonebook} was requested on.
     * @param success If true, {@link BluetoothPbapClient#pullPhonebook} succeeded.
     * @param phonebookSize If the request maxListCount was set to 0, this field returns
     * the phonebookSize. Otherwise, -1 is returned.
     * @param newMissedCalls If the {@link BluetoothPbapClient#pullPhonebook} was done on the
     * missed call history, this field returns the number of missed calls that have not been
     * acknowledged by the user yet.
     * @param outputFilepath The destination file location where the vCard result is stored
     * as specified in the {@link BluetoothPbapClient#pullPhonebook} call.
     */
    public void onPullPhonebookCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath);

    /**
     * Callback invoked when PBAP Server pull vcard listing request completes.
     *
     * @param device The remote PBAP Server.
     * @param path The path that the {@link BluetoothPbapClient#pullVcardListing} was requested on.
     * @param success If true, the {@link BluetoothPbapClient#pullVcardListing} succeeded.
     * @param phonebookSize If the request maxListCount was set to 0, this field returns
     * the phonebookSize. Otherwise, -1 is returned.
     * @param newMissedCalls If the {@link BluetoothPbapClient#pullPhonebook} was done on
     * the missed call history, this field returns the number of missed calls that have not been
     * acknowledged by the user yet.
     * @param outputFilepath The destination file location where the vCard result is stored
     * as specified in the {@link BluetoothPbapClient#pullVcardListing} call.
     */
    public void onPullVcardListingCompleted(BluetoothDevice device, String path, boolean success,
            int phonebookSize, int newMissedCalls, String outputFilepath);

    /**
     * Callback invoked when PBAP Server pull vcard entry request completes.
     *
     * @param device The remote PBAP Server.
     * @param name The name  that the {@link BluetoothPbapClient#pullVcardListing} was requested on.
     * @param success If true, the {@link BluetoothPbapClient#pullVcardEntry} succeeded.
     * @param outputFilepath The destination file location where the vCard result is stored
     * as specified in the {@link BluetoothPbapClient#pullVcardEntry} call.
     */
    public void onPullVcardEntryEvent(BluetoothDevice device, String name, boolean success,
            String outputFilepath);

}
