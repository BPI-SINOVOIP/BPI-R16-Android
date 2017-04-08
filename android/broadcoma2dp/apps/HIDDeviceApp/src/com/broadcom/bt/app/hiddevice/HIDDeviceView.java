
/* Copyright 2009-2013 Broadcom Corporation
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
package com.broadcom.bt.app.hiddevice;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class HIDDeviceView extends View implements View.OnClickListener, View.OnTouchListener {

    private static final String TAG = "HIDDeviceView";
    private static final boolean DEBUG = true;

    /* Registered touch screen event handler. */
    private IHIDViewTouchEventHandler mTouchEventhandler;

    private Context mContext;
    private View mView;

    private ImageView mLeftClickButton;
    private ImageView mRightClickButton;
    private ImageView mScrollButton;
    private ImageView mKeyBoardButton;
    private ImageView mVirtualUnplugButton;
    private LinearLayout mTouchPad;

    public HIDDeviceView(Context context) {
        super(context);
        mContext = context;
        mView.invalidate();
    }

    public View getView() {
        return mView;
    }

    public View getKeyBaordView() {
        return mKeyBoardButton;
    }

    /**
     * Main constructor. This function registers the supplied eventHandler
     * instance as the receive of all touch screen events.
     *
     * @param context
     *            the context to operate in.
     * @param eventHandler
     *            the event handler to receive touch screen events.
     */
    public HIDDeviceView(Context context,
            IHIDViewTouchEventHandler eventHandler, DisplayMetrics metrics) {
        super(context);

        if (DEBUG)
            Log.d(TAG, "HIDDeviceView(ctx, eventHandler)");

        mContext = context;
        mTouchEventhandler = eventHandler;

        int heightPixels = metrics.heightPixels;
        int widthPixels = metrics.widthPixels;
    }

    public void loadViewBasedOnResolution(int widthPixels, int heightPixels) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);

        Log.d(TAG,"Height : "+ heightPixels + " Width : " + widthPixels);

        if(heightPixels == 800 && widthPixels == 480)
            mView = inflater.inflate(R.layout.touch_pad_480x800, null);
        else if (heightPixels == 480 && widthPixels == 800)
            mView = inflater.inflate(R.layout.touch_pad_800x480, null);
        else if(heightPixels == 854 && widthPixels == 480)
            mView = inflater.inflate(R.layout.touch_pad_480x800, null);
        else if((heightPixels == 1200 && widthPixels == 720)  ||
            (heightPixels == 1184 && widthPixels == 720))
            mView = inflater.inflate(R.layout.touch_pad_xhdpi, null);
        else if((heightPixels == 720 && widthPixels == 1200)  ||
            (heightPixels == 720 && widthPixels == 1196))
            mView = inflater.inflate(R.layout.touch_pad_xhdpi_land, null);
        else if((heightPixels == 1776 && widthPixels == 1080)  ||
            (heightPixels == 1080 && widthPixels == 1794))
            mView = inflater.inflate(R.layout.touch_pad_xhdpi, null);
        else
            mView = inflater.inflate(R.layout.touch_pad_480x800, null);

        mLeftClickButton = (ImageView) mView.findViewById(R.id.left_click);
        mLeftClickButton.setId(HIDConstants.LEFT_CLICK);
        mLeftClickButton.setOnClickListener(this);

        mRightClickButton = (ImageView) mView.findViewById(R.id.right_click);
        mRightClickButton.setId(HIDConstants.RIGHT_CLICK);
        mRightClickButton.setOnClickListener(this);

        mScrollButton = (ImageView) mView.findViewById(R.id.scroll_pad);
//        mScrollButton.setId(HIDConstants.MIDDLE_CLICK);
//        mScrollButton.setOnClickListener(this);

        mScrollButton.setOnTouchListener(this);
        mScrollButton.requestFocus();

        mKeyBoardButton = (ImageView) mView.findViewById(R.id.keyboard_button);
        mKeyBoardButton.setId(HIDConstants.KEYBOARD_CLICK);
        mKeyBoardButton.setOnClickListener(this);

        mVirtualUnplugButton = (ImageView) mView.findViewById(R.id.unplug_button);
        mVirtualUnplugButton.setId(HIDConstants.VIRTUAL_UNPLUG_CLICK);
        mVirtualUnplugButton.setOnClickListener(this);

        mTouchPad = (LinearLayout) mView.findViewById(R.id.touch_pad);
        mTouchPad.setOnTouchListener(this);
    }

    public void onClick(View v) {
        if (DEBUG)
            Log.d(TAG, "onClick(): " + v.getId());
        int buttonId = v.getId();
        mTouchEventhandler.handleButtonEvent(buttonId, HIDConstants.BUTTON_EVENT_UP);

    }

    public boolean onTouch(View v, MotionEvent event) {
        if (R.id.scroll_pad == v.getId()){
            Log.d(TAG,"event X Y"+ event.getX()+ event.getY());
            return mTouchEventhandler.handleScrollTouchEvent(event);
        }
        else{
            return mTouchEventhandler.handleTouchEvent(event);
        }
    }

    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        //mTouchEventhandler.handleScrollEvent(l, t, oldl, oldt);
    }

}
