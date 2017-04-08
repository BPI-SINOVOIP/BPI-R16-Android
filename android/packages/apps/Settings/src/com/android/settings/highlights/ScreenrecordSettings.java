/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.highlights;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.List;

public class ScreenrecordSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "ScreenrecordSettings";

    private static final String KEY_VIDEO_CATEGORY = "video_category";
    private static final String KEY_VIDEO_SIZE = "video_size";
    private static final String KEY_VIDEO_TIME_LIMIT = "video_time_limit";
    private static final String KEY_AUDIO_SOURCE = "audio_source";

    private ListPreference mVideoSizePreference;
    private ListPreference mVideoTimeLimitPreference;
    private ListPreference mAudioSourcePreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.screenrecord_settings);

        // video size
        mVideoSizePreference = (ListPreference) findPreference(KEY_VIDEO_SIZE);
        String videoSize = Settings.System.getString(getContentResolver(), Settings.System.SCREENRECORD_VIDEO_SIZE);
        mVideoSizePreference.setValue(videoSize);
        mVideoSizePreference.setOnPreferenceChangeListener(this);
        updateListPreferenceDescription(mVideoSizePreference, videoSize);
        // video time limit
        mVideoTimeLimitPreference = (ListPreference) findPreference(KEY_VIDEO_TIME_LIMIT);
        String timeLimit = Settings.System.getString(getContentResolver(), Settings.System.SCREENRECORD_VIDEO_TIME_LIMIT);
        mVideoTimeLimitPreference.setValue(timeLimit);
        mVideoTimeLimitPreference.setOnPreferenceChangeListener(this);
        updateListPreferenceDescription(mVideoTimeLimitPreference, timeLimit);
        // audio source
        mAudioSourcePreference = (ListPreference) findPreference(KEY_AUDIO_SOURCE);
        String audioSource = Settings.System.getString(getContentResolver(), Settings.System.SCREENRECORD_AUDIO_SOURCE);
        mAudioSourcePreference.setValue(audioSource);
        mAudioSourcePreference.setOnPreferenceChangeListener(this);
        updateListPreferenceDescription(mAudioSourcePreference, audioSource);
        // video category
        removePreference(KEY_VIDEO_CATEGORY);
    }

    private void updateListPreferenceDescription(ListPreference preference, String currentValue) {
        String summary;
        if (currentValue == null) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i].toString().equals(currentValue)) {
                        best = i;
                    }
                }
                summary = entries[best].toString();
            }
        }
        preference.setSummary(summary);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_VIDEO_SIZE.equals(key)) {
			String value = String.valueOf(objValue);
            Settings.System.putString(getContentResolver(), Settings.System.SCREENRECORD_VIDEO_SIZE, value);
            updateListPreferenceDescription(mVideoSizePreference, value);
        } else if (KEY_VIDEO_TIME_LIMIT.equals(key)) {
			String value = String.valueOf(objValue);
            Settings.System.putString(getContentResolver(), Settings.System.SCREENRECORD_VIDEO_TIME_LIMIT, value);
            updateListPreferenceDescription(mVideoTimeLimitPreference, value);
        } else if (KEY_AUDIO_SOURCE.equals(key)) {
			String value = String.valueOf(objValue);
            Settings.System.putString(getContentResolver(), Settings.System.SCREENRECORD_AUDIO_SOURCE, value);
            updateListPreferenceDescription(mAudioSourcePreference, value);
		}
        return true;
    }
}
