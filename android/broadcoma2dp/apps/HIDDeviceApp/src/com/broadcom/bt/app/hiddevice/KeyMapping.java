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

import java.util.HashMap;

import android.util.Log;
import android.view.KeyEvent;

public class KeyMapping {

    public HashMap<Integer, Integer> keyMap = new HashMap<Integer, Integer>();
    // The modifier carries the last modifier key press
    static int modifier = 0;
    private static KeyMapping mInstance;

    public static KeyMapping getInstance()
    {
        if (mInstance==null)
            mInstance = new KeyMapping();
        return mInstance;
    }

    public byte[] createKeyDownReport (int modifier, int keyCode)
    {
        byte values[] = new byte[9];

        values[0]= (byte)0x01;  // report id
        values[1]= (byte)modifier;
        values[2]= (byte)0x00;
        values[3]= (byte)keyCode;
        values[4]= (byte)0x00;
        values[5]= (byte)0x00;
        values[6]= (byte)0x00;
        values[7]= (byte)0x00;
        values[8]= (byte)0x00;

        return values;
    }

    public byte[] createKeyUpReport ()
    {
        byte values[] = new byte[9];

        values[0]= (byte)0x01; // report id
        values[1]= (byte)0x00;
        values[2]= (byte)0x00;
        values[3]= (byte)0x00;
        values[4]= (byte)0x00;
        values[5]= (byte)0x00;
        values[6]= (byte)0x00;
        values[7]= (byte)0x00;
        values[8]= (byte)0x00;

        return values;
    }


    public byte[] createMouseReport (int btn, int delta_x, int delta_y, int delta_wheel)
    {


        byte values[] = new byte[8];

        values[0]= (byte)0x02;

        values[1]= (byte)delta_x; // report id

        values[2]= (byte) delta_y;

        values[4]= (byte) (byte) ((btn == HIDConstants.LEFT_CLICK ? 0x01 : 0x00)
                | (btn == HIDConstants.RIGHT_CLICK ? 0x02 : 0x00)
                | (btn == HIDConstants.MIDDLE_CLICK ? 0x04 : 0x00));

        values[3] = (byte)0x00;
        if (delta_wheel < 127 && delta_wheel > -128){
            Integer intg = delta_wheel ;
            values[3] = intg.byteValue();
        }

        values[5]= (byte)0x00;
        //if()
        values[6]= (byte)0x00;//(byte)0xff;
        //else

        values[7]= (byte)0x00;


        return values;
    }


    private KeyMapping() {

        int value= 10000;


        keyMap.put(KeyEvent.KEYCODE_0, 39); // 7
        keyMap.put(KeyEvent.KEYCODE_1, 30); // 8
        keyMap.put(KeyEvent.KEYCODE_2, 31); // 9
        keyMap.put(KeyEvent.KEYCODE_3, 32); // 10
        keyMap.put(KeyEvent.KEYCODE_4, 33); // 11
        keyMap.put(KeyEvent.KEYCODE_5, 34); // 12
        keyMap.put(KeyEvent.KEYCODE_6, 35); // 13
        keyMap.put(KeyEvent.KEYCODE_7, 36); // 14
        keyMap.put(KeyEvent.KEYCODE_8, 37); // 15
        keyMap.put(KeyEvent.KEYCODE_9, 38); // 16


        keyMap.put(KeyEvent.KEYCODE_STAR, 37); // 17
        keyMap.put(KeyEvent.KEYCODE_POUND, 32); // 18

        keyMap.put(KeyEvent.KEYCODE_DPAD_UP, 82); // 19, Up
        keyMap.put(KeyEvent.KEYCODE_DPAD_DOWN, 81); // 20, Down
        keyMap.put(KeyEvent.KEYCODE_DPAD_LEFT, 80); // 21, Left
        keyMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, 79); //22, Right

    /*
        keyMap.put(KeyEvent.KEYCODE_DPAD_CENTER, value); // 23, center
        keyMap.put(KeyEvent.KEYCODE_VOLUME_UP, value); // 24, vol up
        keyMap.put(KeyEvent.KEYCODE_VOLUME_DOWN, value); // 25, vol down
        keyMap.put(KeyEvent.KEYCODE_POWER, value); //26, power
        keyMap.put(KeyEvent.KEYCODE_CAMERA, value); // 27, camera
        keyMap.put(KeyEvent.KEYCODE_CLEAR, value); // 28, clear
    */

        keyMap.put(KeyEvent.KEYCODE_A, 4); // 29
        keyMap.put(KeyEvent.KEYCODE_B, 5); // 30
        keyMap.put(KeyEvent.KEYCODE_C, 6); // 31
        keyMap.put(KeyEvent.KEYCODE_D, 7); // 32
        keyMap.put(KeyEvent.KEYCODE_E, 8); // 33
        keyMap.put(KeyEvent.KEYCODE_F, 9); // 34
        keyMap.put(KeyEvent.KEYCODE_G, 10); // 35
        keyMap.put(KeyEvent.KEYCODE_H, 11); // 36
        keyMap.put(KeyEvent.KEYCODE_I, 12); // 37
        keyMap.put(KeyEvent.KEYCODE_J, 13); // 38
        keyMap.put(KeyEvent.KEYCODE_K, 14); // 39
        keyMap.put(KeyEvent.KEYCODE_L, 15); // 40
        keyMap.put(KeyEvent.KEYCODE_M, 16); // 41
        keyMap.put(KeyEvent.KEYCODE_N, 17); // 42
        keyMap.put(KeyEvent.KEYCODE_O, 18); // 43
        keyMap.put(KeyEvent.KEYCODE_P, 19); // 44
        keyMap.put(KeyEvent.KEYCODE_Q, 20); // 45
        keyMap.put(KeyEvent.KEYCODE_R, 21); // 46
        keyMap.put(KeyEvent.KEYCODE_S, 22); // 47
        keyMap.put(KeyEvent.KEYCODE_T, 23); // 48
        keyMap.put(KeyEvent.KEYCODE_U, 24); // 49
        keyMap.put(KeyEvent.KEYCODE_V, 25); // 50
        keyMap.put(KeyEvent.KEYCODE_W, 26); // 51
        keyMap.put(KeyEvent.KEYCODE_X, 27); // 52
        keyMap.put(KeyEvent.KEYCODE_Y, 28); // 53
        keyMap.put(KeyEvent.KEYCODE_Z, 29); // 54


        keyMap.put(KeyEvent.KEYCODE_COMMA, 54); // 55, ,
        keyMap.put(KeyEvent.KEYCODE_PERIOD, 55); // 56, .
        keyMap.put(KeyEvent.KEYCODE_ALT_LEFT, 226); // 57
        keyMap.put(KeyEvent.KEYCODE_ALT_RIGHT, 230); // 58
        keyMap.put(KeyEvent.KEYCODE_SHIFT_LEFT, 225); // 59
        keyMap.put(KeyEvent.KEYCODE_SHIFT_RIGHT, 229); // 60
        keyMap.put(KeyEvent.KEYCODE_TAB, 43); // 61


        keyMap.put(KeyEvent.KEYCODE_SPACE, 44); // 62, SpaceBar
        keyMap.put(KeyEvent.KEYCODE_SYM, value); // 63
        keyMap.put(KeyEvent.KEYCODE_ENTER, 40); // 66, Enter

        keyMap.put(KeyEvent.KEYCODE_DEL, 42); // 67, BackSpace
        keyMap.put(KeyEvent.KEYCODE_GRAVE, 53); // 68

        keyMap.put(KeyEvent.KEYCODE_MINUS, 45); // 69
        keyMap.put(KeyEvent.KEYCODE_PLUS, 46); // 69

        keyMap.put(KeyEvent.KEYCODE_EQUALS, 46); // 70
        keyMap.put(KeyEvent.KEYCODE_LEFT_BRACKET, 47); // 71
        keyMap.put(KeyEvent.KEYCODE_RIGHT_BRACKET, 48); // 72
        keyMap.put(KeyEvent.KEYCODE_BACKSLASH, 49); // 73

        keyMap.put(KeyEvent.KEYCODE_SEMICOLON, 51); // 74
        keyMap.put(KeyEvent.KEYCODE_APOSTROPHE, 52); // 75
        keyMap.put(KeyEvent.KEYCODE_SLASH, 56); // 76
        keyMap.put(KeyEvent.KEYCODE_AT, 31); // 77, @
        keyMap.put(KeyEvent.KEYCODE_NUM, 83); // 78


        // Key Mapping is required for these keys.

        keyMap.put(KeyEvent.KEYCODE_UNKNOWN, value); // 0
        keyMap.put(KeyEvent.KEYCODE_SOFT_LEFT, value); // 1
        keyMap.put(KeyEvent.KEYCODE_SOFT_RIGHT, value); // 2
        keyMap.put(KeyEvent.KEYCODE_HOME, value); // 3
        keyMap.put(KeyEvent.KEYCODE_BACK, value); // 4
        keyMap.put(KeyEvent.KEYCODE_CALL, value); // 5
        keyMap.put(KeyEvent.KEYCODE_ENDCALL, value); // 6


        keyMap.put(KeyEvent.KEYCODE_EXPLORER, value); // 64
        keyMap.put(KeyEvent.KEYCODE_ENVELOPE, value); // 65

        keyMap.put(KeyEvent.KEYCODE_MUTE, value); // 6
        keyMap.put(KeyEvent.KEYCODE_MENU, value); // 6
        keyMap.put(KeyEvent.KEYCODE_MUTE, value); // 6

    }

    public boolean isAndroidSpecificKey(int keyCode){

        switch(keyCode)
        {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MUTE:
                return true;
        }
        return false;
    }

    public boolean isHandleAndroidExceptionsReq(int keyCode){

        switch(keyCode)
        {
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_AT:
            case KeyEvent.KEYCODE_POUND:

                return true;
        }

        return false;
    }

}