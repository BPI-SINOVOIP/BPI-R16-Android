/* //device/content/providers/media/src/com/android/providers/media/MediaScannerReceiver.java
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package com.android.providers.media;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import android.os.Handler;
import android.os.Message;
import java.util.ArrayList;

import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;

import android.os.Environment;
import android.os.storage.StorageManager;


import java.io.File;
import java.io.IOException;

import java.lang.ref.WeakReference;

public class MediaScannerReceiver extends BroadcastReceiver {
    private final static String TAG = "MediaScannerReceiver";

	private static final int EVENT_ACTION_BOOT_COMPLETED             = 100;
    private static final int EVENT_ACTION_MEDIA_MOUNTED              = 200;
	private static final int EVENT_ACTION_MEDIA_UNMOUNTED            = 300;
	private static final int EVENT_ACTION_MEDIA_SCANNER_SCAN_FILE    = 400;

	private Handler mHandler = new MyHandler(this);
	
	private Context mContext;
	private String  mPath;

	private StorageManager mStorageManager;
	private ArrayList<String> mExtsdList;
	private ArrayList<String> mUsbList;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final Uri uri = intent.getData();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Scan both internal and external storage
            scan(context, MediaProvider.INTERNAL_VOLUME);
            scan(context, MediaProvider.EXTERNAL_VOLUME);

			Message msg = new Message();
			msg.what = EVENT_ACTION_BOOT_COMPLETED;
			mHandler.sendMessage(msg);

        } else {
            if (uri.getScheme().equals("file")) {
                // handle intents related to external storage
                String path = uri.getPath();
                String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
                String legacyPath = Environment.getLegacyExternalStorageDirectory().getPath();

				Log.d(TAG, "path " + path);
				Log.d(TAG, "externalStoragePath " + externalStoragePath);
				Log.d(TAG, "legacyPath " + legacyPath);

                try {
                    path = new File(path).getCanonicalPath();
                } catch (IOException e) {
                    Log.e(TAG, "couldn't canonicalize " + path);
                    return;
                }
                if (path.startsWith(legacyPath)) {
                    path = externalStoragePath + path.substring(legacyPath.length());
                }

				/*Begin (Modified by Michael. 2014.06.11)*/	
				/* removed residual images*/
                if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action))
                {
					mContext = context;
					mStorageManager = (StorageManager) mContext.getSystemService(mContext.STORAGE_SERVICE);
					String[] list = mStorageManager.getVolumePaths();

					mExtsdList = new ArrayList<String>();
			        mUsbList = new ArrayList<String>();
					
					for(int i = 0; i < list.length; i++)
					{
					    Log.d(TAG, "i " + i + "list[i] "+ list[i]);
						if(list[i].contains("extsd")){
							//Log.d(TAG, "extsd i " + i + "list[i] "+ list[i]);
							mExtsdList.add(list[i]);
						}else if(list[i].contains("usb")){
						    //Log.d(TAG, "usb i " + i + "list[i] "+ list[i]);
							mUsbList.add(list[i]);
						}
					}
					mPath = path;
                }
				/*End (Modified by Michael. 2014.06.11)*/	
				
                Log.d(TAG, "action: " + action + " path: " + path);
                if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                    // scan whenever any volume is mounted
                    scan(context, MediaProvider.EXTERNAL_VOLUME);
					
					Message msg = new Message();
					msg.what = EVENT_ACTION_MEDIA_MOUNTED;
					mHandler.sendMessage(msg);
                }else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
					
					Message msg = new Message();
					msg.what = EVENT_ACTION_MEDIA_UNMOUNTED;
					mHandler.sendMessage(msg);
                            
                }else if (Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action) &&
                        path != null && (path.startsWith(externalStoragePath + "/") || path.startsWith("/mnt/extsd") || path.startsWith("/mnt/usbhost")
                        || path.startsWith("/storage/extsd") || path.startsWith("/storage/usbhost"))) {
                    scanFile(context, path);

					Message msg = new Message();
					msg.what = EVENT_ACTION_MEDIA_SCANNER_SCAN_FILE;
					mHandler.sendMessage(msg);
                }
            }
        }
		
    }

	
	/*Begin (Modified by Michael. 2014.06.11)*/ 
	/* removed residual images*/
	private void deleteMediaFile(Context context, String file) {
		final int ID_AUDIO_COLUMN_INDEX = 0;
        final int PATH_AUDIO_COLUMN_INDEX = 1;
		String[] PROJECTION = new String[] {
								Audio.Media._ID,
								Audio.Media.DATA,
								};
		Uri[] mediatypes = new Uri[] {
								Audio.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME),
								Video.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME),
								Images.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME),
								};
		ContentResolver cr = context.getContentResolver();
		Cursor c = null;
		
		for( int i = 0; i < mediatypes.length; i++) 
		{
			c = cr.query(mediatypes[i], PROJECTION, null, null, null);
			if(c != null) 
			{
				try
				{
					while(c.moveToNext()) 
					{
						long rowId = c.getLong(ID_AUDIO_COLUMN_INDEX);
						String path = c.getString(PATH_AUDIO_COLUMN_INDEX);

						if(path != null && path.startsWith(file)) 
						{
							Log.d(TAG, "delete row " + rowId + "in table " + mediatypes[i]);
							cr.delete(ContentUris.withAppendedId(mediatypes[i], rowId), null, null);
						}
					}
				}
				finally 
				{
					c.close();
					c = null;
				}
			}
		}
	} 

	private final class MyHandler extends Handler {
        private WeakReference<MediaScannerReceiver> AWMediaReceiver;
		
        public MyHandler(MediaScannerReceiver activity) {
            AWMediaReceiver = new WeakReference<MediaScannerReceiver>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaScannerReceiver status = AWMediaReceiver.get();
            if (status == null) {
                return;
            }
            switch (msg.what) {
                case EVENT_ACTION_BOOT_COMPLETED:
					Log.d(TAG, "EVENT_ACTION_BOOT_COMPLETED");
                    break;

                case EVENT_ACTION_MEDIA_MOUNTED:
                    Log.d(TAG, "EVENT_ACTION_MEDIA_MOUNTED");
                    break;
					
                case EVENT_ACTION_MEDIA_UNMOUNTED:
                    Log.d(TAG, "EVENT_ACTION_MEDIA_UNMOUNTED");
					
					if (mPath.contains("extsd")){
						for(String extsd:mExtsdList)
						{
							if(Environment.MEDIA_MOUNTED.equals(mStorageManager.getVolumeState(extsd))){
								Log.d(TAG, "mount /mnt/extsd");
					        }else {
								Log.d(TAG, "unmount /mnt/extsd");
								deleteMediaFile(mContext, mPath);
								// notify on media Uris as well as the files Uri
				                mContext.getContentResolver().notifyChange(
				                        Audio.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Images.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Video.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Files.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
					        }
						}
					}else if (mPath.contains("usb")){
						for(String usb:mUsbList)
						{
							if(Environment.MEDIA_MOUNTED.equals(mStorageManager.getVolumeState(usb))){
								Log.d(TAG, "mount /mnt/usb");
					        }else {
								Log.d(TAG, "unmount /mnt/usb");
								deleteMediaFile(mContext, mPath);
								// notify on media Uris as well as the files Uri
				                mContext.getContentResolver().notifyChange(
				                        Audio.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Images.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Video.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
				                mContext.getContentResolver().notifyChange(
				                        Files.getContentUri(MediaProvider.EXTERNAL_VOLUME), null);
					        }
						}
					}
					
                    break;
				case EVENT_ACTION_MEDIA_SCANNER_SCAN_FILE:
                    Log.d(TAG, "EVENT_ACTION_MEDIA_SCANNER_SCAN_FILE");
                    break;
            }
        }
    }
	/*End (Modified by Michael. 2014.06.11)*/	

    private void scan(Context context, String volume) {
        Bundle args = new Bundle();
        args.putString("volume", volume);
        context.startService(
                new Intent(context, MediaScannerService.class).putExtras(args));
    }    

    private void scanFile(Context context, String path) {
        Bundle args = new Bundle();
        args.putString("filepath", path);
        context.startService(
                new Intent(context, MediaScannerService.class).putExtras(args));
    }    
}
