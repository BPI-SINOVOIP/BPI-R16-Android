package com.aw.parrot;

import java.util.List;

import com.unisound.sim.cmd.base.CmdManager;
import com.unisound.sim.cmd.base.CmdType;
import com.unisound.sim.im.music.Music;
import com.unisound.sim.im.music.MusicData;
import com.unisound.sim.im.music.MusicPlayMode;

public class YZSMusicCtrl {
	private final static String TAG = "llh>>YZSMusicCtrl";
	public static void play(){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_PLAY);
	}
	
	public static void pause(){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_PAUSE);
	}
	
	public static void stop(){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_STOP);
	}
	
	public static void seek(int seekTime){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_SEEK_TO_TIME,seekTime);
	}
	
	public static void previous(){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_PRE);
	}
	
	public static void next(){
		CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_NEXT);
	}
	
	public static void playMusicList(List<Music> playList,int index,MusicPlayMode mode){
		MusicData musicdata = new MusicData();
		musicdata.setMode(mode);
		musicdata.setMusicList(playList);
		musicdata.setBeginIndex(index);
		if(mode == MusicPlayMode.PLAY_LIST){
			CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_PLAY_LIST, musicdata);
		}else{
			CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_SINGLE_LOOP_MODE,musicdata);
		}
		CmdManager.getInstacne().excuteCmd(CmdType.ENTER_WAKE_UP);
	}
}
