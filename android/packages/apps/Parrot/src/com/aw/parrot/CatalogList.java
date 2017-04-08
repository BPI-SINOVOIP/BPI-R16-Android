package com.aw.parrot;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;

import com.unisound.sim.im.music.Music;

public class CatalogList {
	private final String TAG = "llh>>>CatalogList";
	private ArrayList<Music> mStorageMusicList;
	private FileFilter mFileFilter;
	private MediaMetadataRetriever mMediaMetadataRetriever;
	public static final int AUDIO_TYPE = 1;
	
	private static final FileFilter mMusicFilter = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			// TODO Auto-generated method stub
			//keep all directions and needed files
			if(pathname.isDirectory())
			{
				return true;
			}
			String name = pathname.getAbsolutePath();
			String item_ext = null;
			
			try {
	    		item_ext = name.substring(name.lastIndexOf(".") + 1, name.length());
	    		
	    	} catch(IndexOutOfBoundsException e) {	
	    		item_ext = ""; 
	    	}
			if(isMusicFile(item_ext))
			{
				return true;
			}
			
			return false;
		}
	};
	
	public CatalogList(Context context){
		mStorageMusicList =  new ArrayList<Music>();
		mMediaMetadataRetriever = new MediaMetadataRetriever();
	}
	
	private static boolean isMusicFile(String ext)
	{
		if(ext.equalsIgnoreCase("mp3") ||
			ext.equalsIgnoreCase("ogg") ||
			ext.equalsIgnoreCase("wav") ||
			ext.equalsIgnoreCase("wma") ||
			ext.equalsIgnoreCase("m4a") ||
            ext.equalsIgnoreCase("ape") ||
            ext.equalsIgnoreCase("dts") ||
            ext.equalsIgnoreCase("flac") ||
            ext.equalsIgnoreCase("mp1") ||
            ext.equalsIgnoreCase("mp2") ||
            ext.equalsIgnoreCase("aac") ||
            ext.equalsIgnoreCase("midi") ||
            ext.equalsIgnoreCase("mid") ||
            ext.equalsIgnoreCase("mp5") ||
            ext.equalsIgnoreCase("mpga") ||
            ext.equalsIgnoreCase("mpa") ||
			ext.equalsIgnoreCase("m4p"))
		{
			return true;
		}
		return false;
	}
	
	private void attachPathToList(File file){
		File[] filelist = file.listFiles(mFileFilter);
		/*
		 * add by chenjd,chenjd@allwinnertech 2011-09-14
		 * other application:mediaScanner will save its thumbnail data here, so 
		 * I must not scan this area for 'picture'. 
		 */
		String pathToIgnored = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "DCIM" + "/" + ".thumbnails";
		int i = 0;
		if(filelist == null)
		{
			return;
		}
		for(i = 0;i < filelist.length;i ++)
		{
			if(filelist[i].isDirectory())
			{
				String mPath = filelist[i].getPath();
				//Log.d("CatalogList",mPath);
				if(!mPath.equalsIgnoreCase(pathToIgnored))
				{
					attachPathToList(filelist[i]);
				}
			}
			else
			{
				if(mStorageMusicList.size() >= 500)
				{
					return;
				}
				if (mStorageMusicList.contains(filelist[i].getAbsolutePath())) {
					continue;
				}
				String filePath = filelist[i].getAbsolutePath();
				mMediaMetadataRetriever.setDataSource(filePath);
				String musicAlbum = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
				String musicArtist = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
//				String musicTitle = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
				String musicName = filelist[i].getName();
				String musicTitle = musicName.split("\\.")[0];
				String musicDurationStr = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				Log.d(TAG, "absolute path = "+filePath+",album = "+musicAlbum+",artist = "+musicArtist+",title = "+musicTitle
						+",name = "+musicName+",duration = "+musicDurationStr);
				Music music = new Music();
				music.setUrl(filePath);
				music.setAlbum(musicAlbum);
				music.setArtist(musicArtist);
				music.setTitle(musicTitle);
				music.setName(musicName);
				if(musicDurationStr == null){
					Log.e(TAG, "the duration is err");
					music.setDuration(0);
				}else{
					music.setDuration(Long.parseLong(musicDurationStr));
				}
				music.setLocal(true);
				mStorageMusicList.add(music);
			}
		}
	}
	
	private void attachPathToList(String path){
		File file = new File(path);
		attachPathToList(file);
	}
	
	public void setFileType(int fileType){
		switch (fileType) {
		case AUDIO_TYPE:
			mFileFilter = mMusicFilter;
			break;
		default:
			break;
		}
	}
	
	public ArrayList<Music> getExtStorageMusicList(String storagePath){
		//maybe sort?
		mStorageMusicList.clear();
		attachPathToList(storagePath);
		return mStorageMusicList;
	}
}
