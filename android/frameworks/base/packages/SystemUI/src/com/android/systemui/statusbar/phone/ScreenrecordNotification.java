package com.android.systemui.statusbar.phone;

import com.android.systemui.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.Handler;
import android.content.Context;


public class ScreenrecordNotification{
		
		private Context mContext;
		private NotificationManager mNotificationManager;
		private Notification notification;
		private long recordDuration;
		
		
		Handler srHandler = new Handler();
		Runnable srRunnable = new Runnable(){
			@Override
			public void run(){
				notification.setLatestEventInfo(mContext, "Recording",toStringTime(),null);
				mNotificationManager.notify("screenrecord",10086,notification);
				srHandler.postDelayed(srRunnable, 1000);
			}
		};
		
		public ScreenrecordNotification(Context context){
			mContext = context;
			mNotificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
			notification = new Notification(R.drawable.ic_qs_screen_record, "Recording", System.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR; 
			
		}
		
		public void notifyNotificaiton(){
			recordDuration = System.currentTimeMillis();
			notification.setLatestEventInfo(mContext, "Recording","00:00:00",null);
			mNotificationManager.notify("screenrecord",10086,notification);
			srHandler.postDelayed(srRunnable, 1000);
		}
		
		public void cancelNotificaiton(){
			srHandler.removeCallbacks(srRunnable);
			mNotificationManager.cancel("screenrecord",10086);
		}
		
		private String toStringTime(){
			long l = (System.currentTimeMillis() - recordDuration) / 1000; 
			long h=l/3600;
			long m=l/60%60;
			long s=l%60;
			String fs = String.format("%02d:%02d:%02d",h,m,s);
			return fs;
		}
	}