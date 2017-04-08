package com.aw.parrot;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

@SuppressLint("SimpleDateFormat")
public class Utils {
	private final static String TAG = "llh>>Utils";
	
	public static String getPlayTime(long time) {
//        Log.d(TAG, "time == " + time);
        int minute = (int) (time / 1000 / 60);
        int second = (int) (time / 1000 - minute * 60);
        String format_minute = minute < 10 ? "0" + minute : minute + "";
        String format_second = second < 10 ? "0" + second : second + "";
        return format_minute + ":" + format_second;
    }

    public static long stringToDate(String strTime) {
    	Log.d(TAG, "str time = "+strTime);
        ParsePosition position = new ParsePosition(0);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                "yyyy.MM.dd HH:mm:ss");
        Date dateValue = simpleDateFormat.parse(strTime, position);
        if (dateValue != null) {
            return dateValue.getTime();
        } else {
            return 0;
        }
    }

    public static String dateToString(long time) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                "yyyy.MM.dd HH:mm:ss");
        return simpleDateFormat.format(time);
    }

    public static String getDeviceId(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx
                .getSystemService(Context.TELEPHONY_SERVICE);
        String mImei = tm.getDeviceId();
        return mImei;
    }
}
