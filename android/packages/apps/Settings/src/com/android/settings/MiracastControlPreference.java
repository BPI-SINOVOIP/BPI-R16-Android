
package com.android.settings;

import java.io.IOException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.SeekBarDialogPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.provider.SyncStateContract.Constants;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.InvalidDisplayException;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.os.SystemProperties;
import android.hardware.display.IDisplayManager;

public class MiracastControlPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener{

	private int miracast_value;
	private SeekBar mSeekBar;
	private Context mContext;
	private IDisplayManager mIDisplayManager;
	private static final int  SEEK_BAR_RANGE = 200;
	private int miracast ;
	private int oldmiracast;
	 public MiracastControlPreference(Context context, AttributeSet attrs) {
	        super(context, attrs);
	        	mContext = context;
	        }

	 @Override
	    protected void onBindDialogView(View view) {
	        super.onBindDialogView(view);

	        mSeekBar = getSeekBar(view);
	        mSeekBar.setMax(SEEK_BAR_RANGE);
	        try {
				miracast = Settings.System.getInt(mContext.getContentResolver(), Settings.System.MIRACAST_CONTROL);
			} catch (SettingNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        mSeekBar.setProgress(miracast);
	        oldmiracast = miracast;
	        mSeekBar.setEnabled(true);
	        mSeekBar.setOnSeekBarChangeListener(this);
	    }
	 @Override
	    protected void onDialogClosed(boolean positiveResult) {
	        super.onDialogClosed(positiveResult);
	        if(positiveResult){
	        	Settings.System.putInt(mContext.getContentResolver(), Settings.System.MIRACAST_CONTROL, miracast);
	        }else {
	        	Settings.System.putInt(mContext.getContentResolver(), Settings.System.MIRACAST_CONTROL, oldmiracast);
			}
	 }
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}
    @Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		setMiracast(progress);
	}

    private void setMiracast(int progress){
    	miracast = progress;
    	Settings.System.putInt(mContext.getContentResolver(), Settings.System.MIRACAST_CONTROL, miracast);
    }
}

