
#ifndef SOUND_CONTROL_H
#define SOUND_CONTROL_H

#include "player_i.h"

typedef void* SoundCtrl;

typedef void (*RawCallback)(void *self, void *param);
#ifdef CONFIG_ENABLE_DIRECT_OUT
SoundCtrl* SoundDeviceInit(void* pAudioSink,void* hdeccomp,RawCallback callback);
#else
SoundCtrl* SoundDeviceInit(void* pAudioSink);
#endif
void SoundDeviceRelease(SoundCtrl* s);

void SoundDeviceSetFormat(SoundCtrl* s, unsigned int nSampleRate, unsigned int nChannelNum);
#ifdef CONFIG_ENABLE_DIRECT_OUT
int SoundDeviceStart(SoundCtrl* s,int raw_flag,int samplebit);
#else
int SoundDeviceStart(SoundCtrl* s);
#endif

int SoundDeviceStop(SoundCtrl* s);

int SoundDevicePause(SoundCtrl* s);

int SoundDeviceWrite(SoundCtrl* s, void* pData, int nDataSize);

int SoundDeviceReset(SoundCtrl* s);

int SoundDeviceGetCachedTime(SoundCtrl* s);

int SoundDeviceSetVolume(SoundCtrl* s, float volume);

int SoundDeviceGetVolume(SoundCtrl* s, float *volume);
#endif

