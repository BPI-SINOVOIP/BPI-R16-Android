package com.aw.parrot;

public interface IMusicCtrl {
	public void play();
	public void pause();
	public void stop();
	public void prePlay();
	public void nextPlay();
	public void seek(int seekTime);
	public void fastForward();
	public void fastBackward();
	public void maxVolume();
	public void minVolume();
	public void increaseVolume();
	public void decreaseVolume();
	public void singleLoopMode(boolean flag);
	public void listLoopMode(boolean flag);
	public void randomLoopMode(boolean flag);
}
