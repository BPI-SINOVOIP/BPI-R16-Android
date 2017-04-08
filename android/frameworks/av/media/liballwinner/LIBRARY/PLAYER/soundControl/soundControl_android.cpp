
#include <utils/Errors.h>

#include "soundControl.h"
#include "log.h"
#include <media/AudioTrack.h>
#include <pthread.h>
#ifdef CONFIG_ENABLE_DIRECT_OUT
    #include <media/IAudioFlinger.h>
#endif
typedef struct SoundCtrlContext
{
    MediaPlayerBase::AudioSink* pAudioSink;

    sp<AudioTrack>              pAudioTrack;//* new an audio track if pAudioSink not set.
    unsigned int                nSampleRate;
    unsigned int                nChannelNum;
    
    int64_t                     nDataSizePlayed;
    int64_t                     nFramePosOffset;
    unsigned int                nFrameSize;
    unsigned int                nLastFramePos;
#ifdef CONFIG_ENABLE_DIRECT_OUT  
	RawCallback                 RawplayCallback;
	void*						hdeccomp;
	int                         raw_flag;
#endif
    enum EPLAYERSTATUS          eStatus;
    pthread_mutex_t             mutex;
	float						volume;
    
}SoundCtrlContext;

static int SoundDeviceStop_l(SoundCtrlContext* sc);
#ifdef CONFIG_ENABLE_DIRECT_OUT
SoundCtrl* SoundDeviceInit(void* pAudioSink,void* hdeccomp,RawCallback callback)
#else
SoundCtrl* SoundDeviceInit(void* pAudioSink)
#endif
{
    SoundCtrlContext* s;
    
    logd("<SoundCtl>: init");
    s = (SoundCtrlContext*)malloc(sizeof(SoundCtrlContext));
    if(s == NULL)
    {
        loge("malloc memory fail.");
        return NULL;
    }
    memset(s, 0, sizeof(SoundCtrlContext));
    s->volume = -1.0;
    s->pAudioSink = (MediaPlayerBase::AudioSink*)pAudioSink;
#ifdef CONFIG_ENABLE_DIRECT_OUT
	s->hdeccomp = hdeccomp;
	s->RawplayCallback = callback;
#endif
    s->eStatus    = PLAYER_STATUS_STOPPED;
    pthread_mutex_init(&s->mutex, NULL);
    
    return (SoundCtrl*)s;
}


void SoundDeviceRelease(SoundCtrl* s)
{
    SoundCtrlContext* sc;
    
    logd("<SoundCtl>: release");
    sc = (SoundCtrlContext*)s;
    
    pthread_mutex_lock(&sc->mutex);
    if(sc->eStatus != PLAYER_STATUS_STOPPED)
        SoundDeviceStop_l(sc);
    pthread_mutex_unlock(&sc->mutex);
    
    pthread_mutex_destroy(&sc->mutex);
    
    free(sc);
    return;
}


void SoundDeviceSetFormat(SoundCtrl* s, unsigned int nSampleRate, unsigned int nChannelNum)
{
    SoundCtrlContext* sc;
    
    sc = (SoundCtrlContext*)s;
    
    pthread_mutex_lock(&sc->mutex);
    
    if(sc->eStatus != PLAYER_STATUS_STOPPED)
    {
        loge("Sound device not int stop status, can not set audio params.");
        abort();
    }
    
    sc->nSampleRate = nSampleRate;
    sc->nChannelNum = nChannelNum;
    
    pthread_mutex_unlock(&sc->mutex);
    
    return;
}

#ifdef CONFIG_ENABLE_DIRECT_OUT
int SoundDeviceStart(SoundCtrl* s,int raw_flag,int samplebit)
#else
int SoundDeviceStart(SoundCtrl* s)
#endif
{
    SoundCtrlContext* sc;
    status_t          err;
    unsigned int      nFramePos;
	int 			  param_occupy[3]={1,0,0};
	int 			  param_release[3]={0,0,0};
	int 			  param_pause[3]={-1,0,0};
	
    audio_format_t    aformat = AUDIO_FORMAT_PCM_16_BIT;
	audio_output_flags_t  flags = AUDIO_OUTPUT_FLAG_NONE;
	
    sc = (SoundCtrlContext*)s;
#ifdef CONFIG_ENABLE_DIRECT_OUT
	sc->raw_flag = raw_flag;
#endif   
    pthread_mutex_lock(&sc->mutex);
    
    if(sc->eStatus == PLAYER_STATUS_STARTED)
    {
        logw("Sound device already started.");
        pthread_mutex_unlock(&sc->mutex);
        return -1;
    }
    
    if(sc->eStatus == PLAYER_STATUS_STOPPED)
    {
#ifdef CONFIG_ENABLE_DIRECT_OUT
		if(raw_flag)// || sc->nSampleRate == 96000 || sc->nSampleRate == 192000)
        {
            loge("direct out put !!!!!!   sc->nSampleRate:%d,raw_flag:%d,samplebit:%d",sc->nSampleRate,raw_flag,samplebit);
            flags = AUDIO_OUTPUT_FLAG_DIRECT;
            aformat = AUDIO_FORMAT_PCM_DIR_16_BIT;
			switch(raw_flag)
			{
				case 2:
					aformat = AUDIO_FORMAT_RAW_AC3;
					break;
				case 7:
					aformat = AUDIO_FORMAT_RAW_DTS;
					break;
			    default:
					break;
			}
			switch(samplebit)
			{
				case 24:
					aformat = AUDIO_FORMAT_PCM_24BIT ;
					param_occupy[0] = 24;
					break;
				case 32:
					aformat = AUDIO_FORMAT_PCM_24BIT;
					param_occupy[0] = 32;
					break;
				default:
					break;
			}
			 loge("direct out put !!!!!!  aformat:%d sc->nSampleRate:%d,raw_flag:%d,samplebit:%d ,flags:%d",aformat,sc->nSampleRate,raw_flag,samplebit,flags);
	        //sc->RawplayCallback(sc->hdeccomp,(void*)param_occupy);
		}
        else
        {
            //sc->RawplayCallback(sc->hdeccomp,(void*)param_pause);
        }
#endif
        if(sc->pAudioSink != NULL)
        {
            loge("sc->nSampleRate:%d,flags:%d",sc->nSampleRate,flags);
            err = sc->pAudioSink->open(sc->nSampleRate,
                                       sc->nChannelNum,
                                       CHANNEL_MASK_USE_CHANNEL_ORDER,
                                       aformat,
                                       DEFAULT_AUDIOSINK_BUFFERCOUNT,
                                       NULL,    //* no callback mode.
                                       NULL,
                                       flags);
            loge("======================================err:%d\n", err);
            if(err != OK)
            {
                pthread_mutex_unlock(&sc->mutex);
                return -1;
            }

			unsigned int tmp = 0;
			sc->pAudioSink->getPosition(&tmp);
			if(tmp != 0)
			{
				sc->pAudioSink->pause();
				sc->pAudioSink->flush();
			}

            
            sc->nFrameSize = sc->pAudioSink->frameSize();
        }
        else
        {
            sc->pAudioTrack = new AudioTrack();
         
            if(sc->pAudioTrack == NULL)
            {
                loge("create audio track fail.");
                pthread_mutex_unlock(&sc->mutex);
                return -1;
            }
            
            sc->pAudioTrack->set(AUDIO_STREAM_DEFAULT, 
                                 sc->nSampleRate, 
                                 aformat,
                                 (sc->nChannelNum == 2) ? AUDIO_CHANNEL_OUT_STEREO : AUDIO_CHANNEL_OUT_MONO,0,flags);
            
            if(sc->pAudioTrack->initCheck() != OK)
            {
                loge("audio track initCheck() return fail.");
                sc->pAudioTrack.clear();
                sc->pAudioTrack = NULL;
                pthread_mutex_unlock(&sc->mutex);
                return -1;
            }
            
            sc->nFrameSize = sc->pAudioTrack->frameSize();
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2) //* for compile		
			if(sc->volume != -1.0)
			{
				sc->pAudioTrack->setVolume(sc->volume);
			}
#endif
        }
        
	    sc->nDataSizePlayed = 0;
        sc->nFramePosOffset = 0;
        sc->nLastFramePos   = 0;
    }
    loge("chengkan : ----- cedarx start");
    if(sc->pAudioSink != NULL)
        sc->pAudioSink->start();
    else
        sc->pAudioTrack->start();
    
    sc->eStatus = PLAYER_STATUS_STARTED;
    pthread_mutex_unlock(&sc->mutex);
                
    return 0;
}


int SoundDeviceStop(SoundCtrl* s)
{
    int               ret;
    SoundCtrlContext* sc;
    
    logd("<SoundCtl>: stop");
    sc = (SoundCtrlContext*)s;
    
    pthread_mutex_lock(&sc->mutex);
    ret = SoundDeviceStop_l(sc);
    pthread_mutex_unlock(&sc->mutex);
    
    return ret;
}


static int SoundDeviceStop_l(SoundCtrlContext* sc)
{
    int 			  param_occupy[3]={1,0,0};
	int 			  param_release[3]={0,0,0};
	int 			  param_pause[3]={-1,0,0};

    if(sc->eStatus == PLAYER_STATUS_STOPPED)
    {
        logw("Sound device already stopped.");
        return 0;
    }
    
    if(sc->pAudioSink != NULL)
    {
        sc->pAudioSink->pause();
        sc->pAudioSink->flush();
        sc->pAudioSink->stop();
        sc->pAudioSink->close();
    }
    else
    {
        if (sc->pAudioTrack.get() != NULL)
        {
            sc->pAudioTrack->pause();
            sc->pAudioTrack->flush();
            sc->pAudioTrack->stop();
            sc->pAudioTrack.clear();
            sc->pAudioTrack = NULL;
        }
    }
    //sc->RawplayCallback(sc->hdeccomp,(void*)param_release);
    sc->nDataSizePlayed = 0;
    sc->nFramePosOffset = 0;
    sc->nLastFramePos   = 0;
    sc->nFrameSize      = 0;
    sc->eStatus         = PLAYER_STATUS_STOPPED;
    return 0;
}


int SoundDevicePause(SoundCtrl* s)
{
    SoundCtrlContext* sc;
    int 			  param_occupy[3]={1,0,0};
	int 			  param_release[3]={0,0,0};
	int 			  param_pause[3]={-1,0,0};
    
    sc = (SoundCtrlContext*)s;
    logd("<SoundCtl>: pause");
    pthread_mutex_lock(&sc->mutex);
    
    if(sc->eStatus != PLAYER_STATUS_STARTED)
    {
        logw("Invalid pause operation, sound device not in start status.");
        pthread_mutex_unlock(&sc->mutex);
        return -1;
    }
#ifdef CONFIG_ENABLE_DIRECT_OUT  
	if(sc->raw_flag)
	{
		SoundDeviceStop_l(sc);
	}
	else
#endif
    {
	    if(sc->pAudioSink != NULL)
	        sc->pAudioSink->pause();
	    else
	        sc->pAudioTrack->pause();
	    
	    sc->eStatus = PLAYER_STATUS_PAUSED;
    }
    pthread_mutex_unlock(&sc->mutex);
    return 0;
}


int SoundDeviceWrite(SoundCtrl* s, void* pData, int nDataSize)
{
    int               nWritten;
    SoundCtrlContext* sc;
    
    sc = (SoundCtrlContext*)s;
//    loge("SoundDeviceWrite,l:%d,nDataSize:%d", __LINE__, nDataSize);
    pthread_mutex_lock(&sc->mutex);
    
    if(sc->eStatus == PLAYER_STATUS_STOPPED || sc->eStatus == PLAYER_STATUS_PAUSED)
    {
        pthread_mutex_unlock(&sc->mutex);
        return 0;
    }
    if(sc->pAudioSink != NULL)
        nWritten = sc->pAudioSink->write(pData, nDataSize); 
    else
        nWritten = sc->pAudioTrack->write(pData, nDataSize);
    
    if(nWritten < 0)
        nWritten = 0;
    else
        sc->nDataSizePlayed += nWritten;
    
    pthread_mutex_unlock(&sc->mutex);
    
    return nWritten;
}


//* called at player seek operation.
int SoundDeviceReset(SoundCtrl* s)
{
    logd("<SoundCtl>: reset");
    return SoundDeviceStop(s);
}


int SoundDeviceGetCachedTime(SoundCtrl* s)
{
	unsigned int      nFramePos;
	int64_t           nCachedFrames;
	int64_t           nCachedTimeUs;
    SoundCtrlContext* sc;
    
    sc = (SoundCtrlContext*)s;
    
    pthread_mutex_lock(&sc->mutex);
    
    if(sc->eStatus == PLAYER_STATUS_STOPPED)
    {
        pthread_mutex_unlock(&sc->mutex);
        return 0;
    }
	
	if(sc->pAudioSink != NULL)
	    sc->pAudioSink->getPosition(&nFramePos);
	else
	    sc->pAudioTrack->getPosition(&nFramePos);
	
	if(sc->nFrameSize == 0)
	{
	    loge("nFrameSize == 0.");
	    abort();
	}
	
    if(nFramePos < sc->nLastFramePos)
        sc->nFramePosOffset += 0x100000000;
	nCachedFrames = sc->nDataSizePlayed/sc->nFrameSize - nFramePos - sc->nFramePosOffset;
	nCachedTimeUs = nCachedFrames*1000000/sc->nSampleRate;
#if 0    
	loge("nDataSizePlayed = %lld, nFrameSize = %d, nFramePos = %u, nLastFramePos = %u, nFramePosOffset = %lld",
	    sc->nDataSizePlayed, sc->nFrameSize, nFramePos, sc->nLastFramePos, sc->nFramePosOffset);
    
	loge("nCachedFrames = %lld, nCachedTimeUs = %lld, nSampleRate = %d",
	    nCachedFrames, nCachedTimeUs, sc->nSampleRate);
#endif
    sc->nLastFramePos = nFramePos;
    pthread_mutex_unlock(&sc->mutex);
	    
    return (int)nCachedTimeUs;
}
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2) //* for compile
int SoundDeviceSetVolume(SoundCtrl* s, float volume)
{
	
	logd("SoundDeviceSetVolume, volume=%f", volume);
    SoundCtrlContext* sc = (SoundCtrlContext*)s;
	if(volume == -1.0)
	{
		logw("volume == -1.0");
		return 0;
	}
    pthread_mutex_lock(&sc->mutex);
	sc->volume = volume;
	if(sc->pAudioTrack == NULL)
	{
		logw("sc->pAudioTrack == NULL");
		pthread_mutex_unlock(&sc->mutex);
		return -1;
	}
	int ret = (int)sc->pAudioTrack->setVolume(sc->volume);
    pthread_mutex_unlock(&sc->mutex);
	return ret;
}
int SoundDeviceGetVolume(SoundCtrl* s, float *volume)
{
    SoundCtrlContext* sc = (SoundCtrlContext*)s;
	*volume = sc->volume;
	/*
    pthread_mutex_lock(&sc->mutex);
	if(sc->pAudioTrack == NULL)
	{
		logw("sc->pAudioTrack == NULL");
		pthread_mutex_unlock(&sc->mutex);
		return -1;
	}
	int ret = (int)sc->pAudioTrack->getVolume(0, volume);
    pthread_mutex_unlock(&sc->mutex);
    */
	return 0;
}
#else
int SoundDeviceSetVolume(SoundCtrl* s, float volume)
{
    return 0;
}
int SoundDeviceGetVolume(SoundCtrl* s, float *volume)
{
    return 0;
}
#endif
