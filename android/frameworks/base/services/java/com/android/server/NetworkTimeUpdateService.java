/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.NtpTrustedTime;
import android.util.TrustedTime;
import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.location.Criteria;
import java.util.TimeZone;

import com.android.internal.telephony.TelephonyIntents;

/**
 * Monitors the network time and updates the system time if it is out of sync
 * and there hasn't been any NITZ update from the carrier recently.
 * If looking up the network time fails for some reason, it tries a few times with a short
 * interval and then resets to checking on longer intervals.
 * <p>
 * If the user enables AUTO_TIME, it will check immediately for the network time, if NITZ wasn't
 * available.
 * </p>
 */
public class NetworkTimeUpdateService {

    private static final String TAG = "NetworkTimeUpdateService";
    private static final boolean DBG = false;

    private static final int EVENT_AUTO_TIME_CHANGED = 1;
    private static final int EVENT_POLL_NETWORK_TIME = 2;
    private static final int EVENT_NETWORK_CONNECTED = 3;
    private static final int EVENT_AUTO_TIME_ZONE_CHANGED = 4;

    private static final String ACTION_POLL =
            "com.android.server.NetworkTimeUpdateService.action.POLL";
    private static int POLL_REQUEST = 0;

    private static final long NOT_SET = -1;
    private long mNitzTimeSetTime = NOT_SET;
    // TODO: Have a way to look up the timezone we are in
    private long mNitzZoneSetTime = NOT_SET;

    private Context mContext;
    private TrustedTime mTime;

    // NTP lookup is done on this thread and handler
    private Handler mHandler;
    private AlarmManager mAlarmManager;
    private PendingIntent mPendingPollIntent;
    private SettingsObserver mSettingsObserver;
    private SettingsObserverForTimeZone mSettingsObserverForTimeZone;
    // The last time that we successfully fetched the NTP time.
    private long mLastNtpFetchTime = NOT_SET;

    // Normal polling frequency
    private final long mPollingIntervalMs;
    // Try-again polling interval, in case the network request failed
    private final long mPollingIntervalShorterMs;
    // Number of times to try again
    private final int mTryAgainTimesMax;
    // If the time difference is greater than this threshold, then update the time.
    private final int mTimeErrorThresholdMs;
    // Keeps track of how many quick attempts were made to fetch NTP time.
    // During bootup, the network may not have been up yet, or it's taking time for the
    // connection to happen.
    private int mTryAgainCounter;

    public NetworkTimeUpdateService(Context context) {
        mContext = context;
        mTime = NtpTrustedTime.getInstance(context);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent pollIntent = new Intent(ACTION_POLL, null);
        mPendingPollIntent = PendingIntent.getBroadcast(mContext, POLL_REQUEST, pollIntent, 0);

        mPollingIntervalMs = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpPollingInterval);
        mPollingIntervalShorterMs = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpPollingIntervalShorter);
        mTryAgainTimesMax = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpRetry);
        mTimeErrorThresholdMs = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpThreshold);
    }

    /** Initialize the receivers and initiate the first NTP request */
    public void systemRunning() {
        registerForTelephonyIntents();
        registerForAlarms();
        registerForConnectivityIntents();

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new MyHandler(thread.getLooper());
        // Check the network time on the new thread
        mHandler.obtainMessage(EVENT_POLL_NETWORK_TIME).sendToTarget();

        mSettingsObserver = new SettingsObserver(mHandler, EVENT_AUTO_TIME_CHANGED);
        mSettingsObserver.observe(mContext);

	 mSettingsObserverForTimeZone = new SettingsObserverForTimeZone(mHandler, EVENT_AUTO_TIME_ZONE_CHANGED);
	 mSettingsObserverForTimeZone.observe(mContext);
    }

    private void registerForTelephonyIntents() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intentFilter.addAction(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        mContext.registerReceiver(mNitzReceiver, intentFilter);
    }

    private void registerForAlarms() {
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mHandler.obtainMessage(EVENT_POLL_NETWORK_TIME).sendToTarget();
                }
            }, new IntentFilter(ACTION_POLL));
    }

    private void registerForConnectivityIntents() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mConnectivityReceiver, intentFilter);
    }

    private void onPollNetworkTime(int event) {
        // If Automatic time is not set, don't bother.
        if (!isAutomaticTimeRequested()) return;

        final long refTime = SystemClock.elapsedRealtime();
        // If NITZ time was received less than mPollingIntervalMs time ago,
        // no need to sync to NTP.
        if (mNitzTimeSetTime != NOT_SET && refTime - mNitzTimeSetTime < mPollingIntervalMs) {
            resetAlarm(mPollingIntervalMs);
            return;
        }
        final long currentTime = System.currentTimeMillis();
        if (DBG) Log.d(TAG, "System time = " + currentTime);
        // Get the NTP time
        if (mLastNtpFetchTime == NOT_SET || refTime >= mLastNtpFetchTime + mPollingIntervalMs
                || event == EVENT_AUTO_TIME_CHANGED) {
            if (DBG) Log.d(TAG, "Before Ntp fetch");

            // force refresh NTP cache when outdated
            if (mTime.getCacheAge() >= mPollingIntervalMs) {
                mTime.forceRefresh();
            }

            // only update when NTP time is fresh
            if (mTime.getCacheAge() < mPollingIntervalMs) {
                final long ntp = mTime.currentTimeMillis();
                mTryAgainCounter = 0;
                // If the clock is more than N seconds off or this is the first time it's been
                // fetched since boot, set the current time.
                if (Math.abs(ntp - currentTime) > mTimeErrorThresholdMs
                        || mLastNtpFetchTime == NOT_SET) {
                    // Set the system time
                    if (DBG && mLastNtpFetchTime == NOT_SET
                            && Math.abs(ntp - currentTime) <= mTimeErrorThresholdMs) {
                        Log.d(TAG, "For initial setup, rtc = " + currentTime);
                    }
                    if (DBG) Log.d(TAG, "Ntp time to be set = " + ntp);
                    // Make sure we don't overflow, since it's going to be converted to an int
                    if (ntp / 1000 < Integer.MAX_VALUE) {
                        SystemClock.setCurrentTimeMillis(ntp);
                    }
                } else {
                    if (DBG) Log.d(TAG, "Ntp time is close enough = " + ntp);
                }
                mLastNtpFetchTime = SystemClock.elapsedRealtime();
            } else {
                // Try again shortly
                mTryAgainCounter++;
                if (mTryAgainTimesMax < 0 || mTryAgainCounter <= mTryAgainTimesMax) {
                    resetAlarm(mPollingIntervalShorterMs);
                } else {
                    // Try much later
                    mTryAgainCounter = 0;
                    resetAlarm(mPollingIntervalMs);
                }
                return;
            }
        }
        resetAlarm(mPollingIntervalMs);
    }
    
    private void updateTimeZone(double latitude,double longitude){
    	TimeZone mTimeZone = null;
    	if( longitude >= -180 && longitude < -165){
			mTimeZone = TimeZone.getTimeZone("Pacific/Tongatapu");

			}else if( longitude >= -165 && longitude < -150){
				mTimeZone = TimeZone.getTimeZone("Pacific/Midway");

			}else if( longitude >= -150 && longitude < -135){
				mTimeZone = TimeZone.getTimeZone("Pacific/Honolulu");

			}else if( longitude >= -135 && longitude < -120){
				mTimeZone = TimeZone.getTimeZone("America/Anchorage");

			}else if( longitude >= -120 && longitude < -105){
				mTimeZone = TimeZone.getTimeZone("America/Los_Angeles");

			}else if( longitude >= -105 && longitude < -90){
				mTimeZone = TimeZone.getTimeZone("America/Phoenix");

			}else if( longitude >= -90 && longitude < -75){
				mTimeZone = TimeZone.getTimeZone("America/Chicago");

			}else if( longitude >= -75 && longitude < -60){
				mTimeZone = TimeZone.getTimeZone("America/New_York");

			}else if( longitude >= -60 && longitude < -45){
				mTimeZone = TimeZone.getTimeZone("America/Barbados");

			}else if( longitude >= -45 && longitude < -30){
				mTimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires");

			}else if( longitude >= -30 && longitude < -15){
				mTimeZone = TimeZone.getTimeZone("Atlantic/South_Georgia");
                
    		}else if( longitude == -1){
    			mTimeZone = TimeZone.getTimeZone("Asia/Shanghai");

    		}else if( longitude >= -15 && longitude < 0){
    			mTimeZone = TimeZone.getTimeZone("Atlantic/Azores");

    		}else if( longitude == 0){
    			mTimeZone = TimeZone.getTimeZone("Europe/London");

    		}else if( longitude > 0 && longitude < 15){
    			mTimeZone = TimeZone.getTimeZone("Europe/Amsterdam");

    		}else if( longitude >= 15 && longitude < 30){
    			mTimeZone = TimeZone.getTimeZone("Europe/Athens");

    		}else if( longitude >= 30 && longitude < 45){
    			mTimeZone = TimeZone.getTimeZone("Africa/Nairobi");

			}else if( longitude >= 45 && longitude < 60){
				mTimeZone = TimeZone.getTimeZone("Europe/Moscow");

			}else if( longitude >= 60 && longitude < 75){
				mTimeZone = TimeZone.getTimeZone("Asia/Karachi");

			}else if( longitude >= 75 && longitude < 90){
				mTimeZone = TimeZone.getTimeZone("Asia/Almaty");

			}else if( longitude >= 90 && longitude < 105){
				mTimeZone = TimeZone.getTimeZone("Asia/Bangkok");

			}else if( longitude >= 105 && longitude < 120){
				mTimeZone = TimeZone.getTimeZone("Asia/Shanghai");

			}else if( longitude >= 120 && longitude < 135){
				mTimeZone = TimeZone.getTimeZone("Asia/Tokyo");

			}else if( longitude >= 135 && longitude < 150){
				mTimeZone = TimeZone.getTimeZone("Asia/Yakutsk");

			}else if( longitude >= 150 && longitude < 165){
				mTimeZone = TimeZone.getTimeZone("Asia/Vladivostok");

			}else if( longitude >= 165 && longitude < 180){
				mTimeZone = TimeZone.getTimeZone("Asia/Magadan");

			}

		Log.d(TAG, "latitude=" + latitude);
		Log.d(TAG, "longitude=" + longitude);
		Log.d(TAG, "mTimeZone=" + mTimeZone);

		if(mTimeZone != null){
			mAlarmManager.setTimeZone(mTimeZone.getID());
		}
    }
    
	private void onPollNetworkTimeZone(int event) {
		// If Automatic timezone is not set, don't bother.
		if (!isAutomaticTimeZoneRequested())
			return;
		LocationManager mLocationManager = (LocationManager) mContext
				.getSystemService(Context.LOCATION_SERVICE);
		AlarmManager mAlarmManager = (AlarmManager) mContext
				.getSystemService(Context.ALARM_SERVICE);
		TimeZone mTimeZone = null;
		double latitude = 0.0;
		double longitude = -181.0; // don't set timezone if can't get location.
		Log.i(TAG,"GPS_PROVIDER="+ mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
		Log.i(TAG,"NETWORK_PROVIDER="+ mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
		Log.i(TAG,"PASSIVE_PROVIDER="+ mLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER));

		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				latitude = location.getLatitude();
				longitude = location.getLongitude();
			}
		}
		LocationListener locationListener = new LocationListener() {

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			@Override
			public void onProviderEnabled(String provider) {
			}

			@Override
			public void onProviderDisabled(String provider) {
			}

			@Override
			public void onLocationChanged(Location location) {
				if (location != null) {
					double latitude = location.getLatitude();
					double longitude = location.getLongitude();
					Log.d(TAG, "Location changed : Lat: " + latitude + " Lng: "
							+ longitude);
					updateTimeZone(latitude, longitude);
				}
			}
		};
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
		Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (location != null) {
			latitude = location.getLatitude();
			longitude = location.getLongitude();
		}

		updateTimeZone(latitude, longitude);
	}

    /**
     * Cancel old alarm and starts a new one for the specified interval.
     *
     * @param interval when to trigger the alarm, starting from now.
     */
    private void resetAlarm(long interval) {
        mAlarmManager.cancel(mPendingPollIntent);
        long now = SystemClock.elapsedRealtime();
        long next = now + interval;
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, next, mPendingPollIntent);
    }

    /**
     * Checks if the user prefers to automatically set the time.
     */
    private boolean isAutomaticTimeRequested() {
        return Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0) != 0;
    }

    private boolean isAutomaticTimeZoneRequested() {
	 return Settings.Global.getInt(
		  mContext.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0) != 0;
		 }

    /** Receiver for Nitz time events */
    private BroadcastReceiver mNitzReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_NETWORK_SET_TIME.equals(action)) {
                mNitzTimeSetTime = SystemClock.elapsedRealtime();
            } else if (TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE.equals(action)) {
                mNitzZoneSetTime = SystemClock.elapsedRealtime();
            }
        }
    };

    /** Receiver for ConnectivityManager events */
    private BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                // There is connectivity
                final ConnectivityManager connManager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                final NetworkInfo netInfo = connManager.getActiveNetworkInfo();
                if (netInfo != null) {
                    // Verify that it's a WIFI connection
                    if (netInfo.getState() == NetworkInfo.State.CONNECTED &&
                            (netInfo.getType() == ConnectivityManager.TYPE_WIFI ||
                                netInfo.getType() == ConnectivityManager.TYPE_ETHERNET) ) {
                        mHandler.obtainMessage(EVENT_NETWORK_CONNECTED).sendToTarget();
                    }
                }
            }
        }
    };

    /** Handler to do the network accesses on */
    private class MyHandler extends Handler {

        public MyHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_AUTO_TIME_CHANGED:
                case EVENT_POLL_NETWORK_TIME:
                case EVENT_NETWORK_CONNECTED:
                    onPollNetworkTime(msg.what);
			 break;
		   case EVENT_AUTO_TIME_ZONE_CHANGED:
		   	onPollNetworkTimeZone(msg.what);
                    break;
            }
        }
    }

    /** Observer to watch for changes to the AUTO_TIME setting */
    private static class SettingsObserver extends ContentObserver {

        private int mMsg;
        private Handler mHandler;

        SettingsObserver(Handler handler, int msg) {
            super(handler);
            mHandler = handler;
            mMsg = msg;
        }

        void observe(Context context) {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.obtainMessage(mMsg).sendToTarget();
        }
    }
	private static class SettingsObserverForTimeZone extends ContentObserver {

				private int mMsg;
				private Handler mHandler;

				SettingsObserverForTimeZone(Handler handler, int msg) {
					super(handler);
					mHandler = handler;
					mMsg = msg;
				}

				void observe(Context context) {
					ContentResolver resolver = context.getContentResolver();
					resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE),
							false, this);
				}

				@Override
				public void onChange(boolean selfChange) {
					mHandler.obtainMessage(mMsg).sendToTarget();
				}
			}

}
