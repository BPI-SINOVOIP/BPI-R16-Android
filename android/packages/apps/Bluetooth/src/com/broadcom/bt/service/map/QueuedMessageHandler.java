/*******************************************************************************
 *
 *  Copyright (C) 2013 Broadcom Corporation
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
package com.broadcom.bt.service.map;

import java.util.LinkedList;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class QueuedMessageHandler extends Handler {
    private static final boolean DBG = true;
    private static final String TAG = "QueuedMessageHandler";

    protected static final int MSG_PASSTHRU_OFFSET = 200000;
    protected static final int MSG_URGENT_OFFSET = 100000;
    protected static final int MSG_TIMEOUT_OFFSET = 10000;
    protected final Message NO_PENDING_MESSAGE = null;
    protected Message mPendingMessage;
    protected LinkedList<Message> mCommandQueue = new LinkedList<Message>();

    protected String getMessageName(int messageType) {
        return "UNKNOWN_MSG(" + messageType + ")";
    }

    protected void addToQueueTop(Message m) {
        mCommandQueue.addFirst(m);
    }

    protected void addToQueueBottom(Message m) {
        mCommandQueue.addLast(m);
    }

    protected Message removeFromQueue() {
        return mCommandQueue.removeFirst();
    }

    protected Message peekFirstQueued() {
        return mCommandQueue.peekFirst();
    }

    public void processNextQueuedMessage() {
        synchronized (mCommandQueue) {
            Message m = peekFirstQueued();
            if (m != null) {
                removeFromQueue();
                sendMessage(m);
            }
            mPendingMessage = NO_PENDING_MESSAGE;
        }
    }
    public Message getPendingMessage() {
        return mPendingMessage;
    }

    protected void setPassThruMessage(Message m) {
        m.what += MSG_PASSTHRU_OFFSET;
    }
    protected void unsetPassThruMessage(Message m) {
        m.what -= MSG_PASSTHRU_OFFSET;
    }

    protected void setUrgentMessage(Message m) {
        m.what += MSG_URGENT_OFFSET;
    }
    protected void unsetUrgentMessage(Message m) {
        m.what -= MSG_URGENT_OFFSET;
    }

    protected boolean isPassThruMessage(Message m) {
        return m.what > MSG_PASSTHRU_OFFSET;
    }
    protected boolean isUrgentMessage(Message m) {
        return m.what > MSG_URGENT_OFFSET && m.what < MSG_PASSTHRU_OFFSET;
    }
    protected boolean isTimeoutMessage(Message m) {
        return m.what > MSG_TIMEOUT_OFFSET && m.what < MSG_URGENT_OFFSET;
    }

    protected void startTimeoutTimer(int timeoutMs) {
        Message timeoutMessage = null;
        synchronized (mCommandQueue) {
            if (mPendingMessage != NO_PENDING_MESSAGE) {
                timeoutMessage = Message.obtain(mPendingMessage);
                timeoutMessage.what = mPendingMessage.what + MSG_TIMEOUT_OFFSET;
                Log.d(TAG, "Adding timeout message for " + getMessageName(mPendingMessage.what));

            }
        }
        if (timeoutMessage != null) {
            sendMessageDelayed(timeoutMessage, timeoutMs);
        }
    }

    protected void stopTimeoutTimer(int messageType) {
        int pendingMessageType = -1;
        synchronized (mCommandQueue) {
            if (mPendingMessage != NO_PENDING_MESSAGE) {
                pendingMessageType = mPendingMessage.what;
            }
        }
        if (pendingMessageType != messageType) {
            Log.w(TAG, "stopTimeoutTimer():  pending message type "
                    + getMessageName(pendingMessageType) + " != " + getMessageName(messageType)
                    + ". Skipping...");
        } else {
            if (DBG) {
                Log.d(TAG, "Removing timeout message for " + getMessageName(messageType));
            }
            removeMessages(messageType + MSG_TIMEOUT_OFFSET);
        }
    }

    private void handleTimeoutMessage(Message m, int timeoutType) {
        Log.w(TAG, "TIMEOUT OCCURRED: while handling " + getMessageName(timeoutType));
        synchronized (mCommandQueue) {
            if (mPendingMessage != NO_PENDING_MESSAGE) {
                int messageType = mPendingMessage.what;
                if (timeoutType == messageType) {
                    Log.d(TAG, "TIMEOUT: removing pending message");
                    mPendingMessage = NO_PENDING_MESSAGE;
                } else {
                    Log.w(TAG,
                            "TIMEOUT: pending message not same type as timeout. Timeout request= "
                                    + getMessageName(timeoutType) + ", pending request="
                                    + getMessageName(messageType));
                }
            }
        }
        processTimeoutMessage(m, timeoutType);
        processNextQueuedMessage();
    }

    protected boolean processTimeoutMessage(Message m, int timeoutType) {
        return true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void handleMessage(Message m) {
        if (isTimeoutMessage(m)) {
            // Handle timeout message
            handleTimeoutMessage(m, m.what - MSG_TIMEOUT_OFFSET);
            return;
        }

        synchronized (mCommandQueue) {
            // If we have a pending request, and the current message is not
            // a
            // timeout...
            // Queue the request
            boolean isPassThru =false;
            boolean isUrgent =false;
            if (isPassThruMessage(m)){
                isPassThru=true;
                unsetPassThruMessage(m);
            }
            if (isUrgentMessage(m)){
                isUrgent=true;
                unsetUrgentMessage(m);
            }
            if (mPendingMessage != NO_PENDING_MESSAGE && !isPassThru) {
                if (DBG) {
                    Log.d(TAG, "handleMessage(): pending commands exist:"
                            + getMessageName(mPendingMessage.what) + ". Queueing request: "
                            + getMessageName(m.what));
                }
                if (isUrgent) {
                    addToQueueTop(Message.obtain(m));
                } else {
                    addToQueueBottom(Message.obtain(m));
                }
                return;
            }
            if (DBG) {
                Log.d(TAG, "handleMessage(): processing request: " + getMessageName(m.what));
            }
            if (!isPassThru)
                mPendingMessage = Message.obtain(m);
        }
        processMessage(m);
    }

    protected boolean processMessage(Message m) {
        return true;
    }
}
