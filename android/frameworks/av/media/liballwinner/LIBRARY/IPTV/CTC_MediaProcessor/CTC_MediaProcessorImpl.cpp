//#define LOG_NDEBUG 0
#define LOG_TAG "CTC_MediaProcessorImpl"

#include <utils/Log.h>
//#include <utils/Vector.h>
//#include <unistd.h>
//#include <stdlib.h>
//#include <sys/types.h>
//#include <sys/stat.h>
//#include <binder/Parcel.h>
//#include <cutils/properties.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/ISurfaceComposer.h>
#include <ui/DisplayInfo.h>
#include <hardware/hwcomposer.h>

#include <ui/Rect.h>
#include <ui/GraphicBufferMapper.h>
#include "CTC_MediaProcessorImpl.h"
#include "player.h"
#include <CdxLog.h>
using namespace android;
#define SAVE_FILE    (0)

#if SAVE_FILE
    FILE    *file = NULL;
	FILE	*file1 = NULL;
	FILE	*file2 = NULL;

#endif


//* config
static const unsigned int CTC_MAX_VIDEO_PACKET_SIZE  = 256 * 1024;
static const unsigned int CTC_MAX_AUDIO_PACKET_SIZE  = 8 * 1024;
static const unsigned int CTC_MAX_TSDATA_PACKET_SIZE = 256 * 1024;
static const unsigned int CTC_ES_VIDEO_BUFFER_SIZE	 = 10 * 1024;
static const unsigned int CTC_ES_AUDIO_BUFFER_SIZE	 = 1 * 1024;


static int gVolume = 50;//0-100

static int SendThreeBlackFrameToGpu(ANativeWindow* pNativeWindow)
{
    logd("SendThreeBlackFrameToGpu()");
    
    ANativeWindowBuffer* pWindowBuf;
    void*                pDataBuf;
    int                  i;
    int                  err;

    //* it just work on A80-box and H8         
    for(i = 0;i < 1;i++)
    {
        err = pNativeWindow->dequeueBuffer_DEPRECATED(pNativeWindow, &pWindowBuf);
        if(err != 0)
        {
            logw("dequeue buffer fail, return value from dequeueBuffer_DEPRECATED() method is %d.", err);
            return -1;
        }
        pNativeWindow->lockBuffer_DEPRECATED(pNativeWindow, pWindowBuf);

        //* lock the data buffer.
        {
            GraphicBufferMapper& mapper = GraphicBufferMapper::get();
            Rect bounds(pWindowBuf->stride, pWindowBuf->height);
            mapper.lock(pWindowBuf->handle, GRALLOC_USAGE_SW_WRITE_OFTEN, bounds, &pDataBuf);
        }

        memset((char*)pDataBuf,0x10,(pWindowBuf->height * pWindowBuf->stride));
        memset((char*)pDataBuf + pWindowBuf->height * pWindowBuf->stride,0x80,(pWindowBuf->height * pWindowBuf->stride)/2);
        
        int nBufAddr[7] = {0};
        //nBufAddr[6] = HAL_PIXEL_FORMAT_AW_NV12;
#if (CONFIG_CHIP != OPTION_CHIP_1651)
        nBufAddr[6] = HAL_PIXEL_FORMAT_AW_FORCE_GPU;
#endif
        pNativeWindow->perform(pNativeWindow, NATIVE_WINDOW_SET_VIDEO_BUFFERS_INFO,	nBufAddr[0], nBufAddr[1],
        nBufAddr[2], nBufAddr[3], nBufAddr[4], nBufAddr[5], nBufAddr[6]);
        
        //* unlock the buffer.
        {
            GraphicBufferMapper& mapper = GraphicBufferMapper::get();
            mapper.unlock(pWindowBuf->handle);
        }
        
        pNativeWindow->queueBuffer_DEPRECATED(pNativeWindow, pWindowBuf);
    }
    return 0;
}

int GetMediaProcessorVersion()
{
    logd("GetMediaProcessorVersion");
    return 2;
}

CTC_MediaProcessor* GetMediaProcessor()
{
    logd("GetMediaProcessor");
    return new CTC_MediaProcessorImpl;
}


static int CTCPlayerCallback(void* pUserData, int eMessageId, void* param)
{
    CTC_MediaProcessorImpl* c;
    logd("CTCPlayerCallback");
    c = (CTC_MediaProcessorImpl*)pUserData;
    return c->ProcessCallback(eMessageId, param);
}

CTC_MediaProcessorImpl::CTC_MediaProcessorImpl()
	: m_callBackFunc(NULL),
	  m_eventHandler(NULL),
	  //mSurface(NULL),
	  mNativeWindow(NULL),
	  mPlayer(NULL),
	  mIsInited(false),
      mIsEos(false),
      mHoldLastPicture(false),
      mAudioTrackCount(0),
      mCurrentAudioTrack(0),
      mLeftBytes(0),
      mIsQuitedRequest(false),
      mIsSetSurfaceTexture(false),
      mVideoX(0),
      mVideoY(0),
      mVideoWidth(0),
      mVideoHeight(0),
      mIsSetVideoPosition(false),
      mSeekRequest(false),
      mSeekReply(0)
{
    logd("media processor construct!");
    
    mVideoStreamBufSize = CTC_MAX_VIDEO_PACKET_SIZE;
    mVideoStreamBuf = malloc(mVideoStreamBufSize);
    if(mVideoStreamBuf == NULL)
        mVideoStreamBufSize = 0;
    
    mAudioStreamBufSize = CTC_MAX_AUDIO_PACKET_SIZE;
    mAudioStreamBuf = malloc(mVideoStreamBufSize);
    if(mAudioStreamBuf == NULL)
        mAudioStreamBufSize = 0;
    
    mTsStreamBufSize = CTC_MAX_TSDATA_PACKET_SIZE;
    mTsStreamBuf = malloc(mVideoStreamBufSize);
    if(mTsStreamBuf == NULL)
        mTsStreamBufSize = 0;
    
    mPlayer = PlayerCreate();
    if(mPlayer != NULL)
        PlayerSetCallback(mPlayer, CTCPlayerCallback, (void*)this);
    
    mDemux = ts_demux_open();
    
    memset(&mVideoPara, 0, sizeof(mVideoPara));
    memset(&mAudioPara, 0, sizeof(mAudioPara));

    mCache = StreamCacheCreate();
    pthread_cond_init(&mCond, NULL);
    pthread_mutex_init(&mSeekMutex, NULL);
    
  	if(pthread_create(&mWriteDataThreadId, NULL, WriteDataThread, this))
	{
		loge("create thread failed!\n");
	}
#if SAVE_FILE
		file = fopen("/data/camera/save.ts", "wb+");
		if (!file)
		{
			CDX_LOGE("open file failure errno(%d)", errno);
		}

		
		file1 = fopen("/data/camera/video.dat", "wb+");
		if (!file1)
		{
			CDX_LOGE("open file failure errno(%d)", errno);
		}
		
		file2 = fopen("/data/camera/audio.dat", "wb+");
		if (!file2)
		{
			CDX_LOGE("open file failure errno(%d)", errno);
		}

#endif

}

CTC_MediaProcessorImpl::~CTC_MediaProcessorImpl()
{
    logd("media processor destroy!");
	mIsQuitedRequest = true;
	pthread_join(mWriteDataThreadId, NULL);

    if(mVideoStreamBuf != NULL)
    {
        free(mVideoStreamBuf);
        mVideoStreamBuf = NULL;
    }
    if(mAudioStreamBuf != NULL)
    {
        free(mAudioStreamBuf);
        mAudioStreamBuf = NULL;
    }
    if(mTsStreamBuf != NULL)
    {
        free(mTsStreamBuf);
        mTsStreamBuf = NULL;
    }
    if(mDemux != NULL)
    {
        ts_demux_close(mDemux);
        mDemux = NULL;
    }
    if(mPlayer != NULL)
    {
        PlayerDestroy(mPlayer);
        mPlayer = NULL;
    }
	/*
    if (mSurface != NULL)
    {
        mSurface.clear();
        mSurface = NULL;
    }*/
    if (mNativeWindow != NULL)
    {
        mNativeWindow.clear();
        mNativeWindow = NULL;
    }

    if(mCache)
    {
    	StreamCacheDestroy(mCache);
    }
    
    pthread_cond_destroy(&mCond);
    pthread_mutex_destroy(&mSeekMutex);
    
#if SAVE_FILE
	if (file)
	{
		fclose(file);
		file = NULL;
	}
	
	if (file1)
	{
		fclose(file1);
		file1 = NULL;
	}

	if (file2)
	{
		fclose(file2);
		file2 = NULL;
	}
#endif
}

extern "C" int RequestVideoBufferCallback(void* param, void* cookie)
{
    md_buf_t*               mdBuf;
    CTC_MediaProcessorImpl* self;
    
    mdBuf = (md_buf_t*)param;
    self  = (CTC_MediaProcessorImpl*)cookie;
    return self->RequestBuffer(&mdBuf->buf, &mdBuf->bufSize, (int)MEDIA_TYPE_VIDEO);
}

extern "C" int UpdateVideoDataCallback(void* param, void* cookie)
{
    int                     bIsFirst;
    int                     bIsLast;
    int64_t                 nPts;
    md_data_info_t*         mdDataInfo;
    CTC_MediaProcessorImpl* self;
    
    mdDataInfo = (md_data_info_t*)param;
    self  = (CTC_MediaProcessorImpl*)cookie;
    
    bIsFirst = (mdDataInfo->ctrlBits & FIRST_PART_BIT) ? 1 : 0;
    bIsLast  = (mdDataInfo->ctrlBits & LAST_PART_BIT) ? 1 : 0;
    nPts     = (mdDataInfo->ctrlBits & PTS_VALID_BIT) ? mdDataInfo->pts : -1;
    return self->UpdateData(nPts, mdDataInfo->dataLen, bIsFirst, bIsLast, (int)MEDIA_TYPE_VIDEO);
}

extern "C" int RequestAudioBufferCallback(void* param, void* cookie)
{
    md_buf_t*               mdBuf;
    CTC_MediaProcessorImpl* self;
    
    mdBuf = (md_buf_t*)param;
    self  = (CTC_MediaProcessorImpl*)cookie;
    return self->RequestBuffer(&mdBuf->buf, &mdBuf->bufSize, (int)MEDIA_TYPE_AUDIO);
}

extern "C" int UpdateAudioDataCallback(void* param, void* cookie)
{
    int                     bIsFirst;
    int                     bIsLast;
    int64_t                 nPts;
    md_data_info_t*         mdDataInfo;
    CTC_MediaProcessorImpl* self;
    
    mdDataInfo = (md_data_info_t*)param;
    self 	   = (CTC_MediaProcessorImpl*)cookie;
    bIsFirst   = (mdDataInfo->ctrlBits & FIRST_PART_BIT) ? 1 : 0;
    bIsLast    = (mdDataInfo->ctrlBits & LAST_PART_BIT) ? 1 : 0;
    nPts       = (mdDataInfo->ctrlBits & PTS_VALID_BIT) ? mdDataInfo->pts : -1;
    return self->UpdateData(nPts, mdDataInfo->dataLen, bIsFirst, bIsLast, (int)MEDIA_TYPE_AUDIO);
}

static enum EVIDEOCODECFORMAT videoCodecConvert(vformat_t codec)
{
    logd("videoCodecConvert");
    enum EVIDEOCODECFORMAT ret = VIDEO_CODEC_FORMAT_UNKNOWN;
    switch(codec)
    {
        case VFORMAT_MPEG12:
            ret = VIDEO_CODEC_FORMAT_MPEG2;
            break;
        case VFORMAT_MPEG4:
        	logd(" mpeg4 , we should goto mpeg4Normal decoder");
            ret = VIDEO_CODEC_FORMAT_DIVX4;  // we should goto mpeg4Normal decoder
            break;
        case VFORMAT_H264:
            ret = VIDEO_CODEC_FORMAT_H264;
            break;
        case VFORMAT_MJPEG:
            ret = VIDEO_CODEC_FORMAT_MJPEG;
            break;
        case VFORMAT_REAL:
            ret = VIDEO_CODEC_FORMAT_RX;
            break;
        case VFORMAT_VC1:
            ret = VIDEO_CODEC_FORMAT_WMV3;
            break;
        case VFORMAT_H265:
            ret = VIDEO_CODEC_FORMAT_H265;
            break;
        default:
            loge("unsupported video codec format(%d)!", codec);
            break;
    }
    
    return ret;
}

static enum EAUDIOCODECFORMAT audioCodecConvert(aformat_t codec)
{
    logd("audioCodecConvert codec %d", codec);
    enum EAUDIOCODECFORMAT ret = AUDIO_CODEC_FORMAT_UNKNOWN;    
    switch(codec)
    {
        case AFORMAT_MPEG:
            ret = AUDIO_CODEC_FORMAT_MP2;
            break;
        case AFORMAT_PCM_U8:
        case AFORMAT_ADPCM:
        case AFORMAT_PCM_S16BE:
        case AFORMAT_ALAW:
        case AFORMAT_MULAW:
        case AFORMAT_PCM_S16LE:
        case AFORMAT_PCM_BLURAY:
            ret = AUDIO_CODEC_FORMAT_PCM;
            break;
        case AFORMAT_RAAC:
        case AFORMAT_AAC:
            ret = AUDIO_CODEC_FORMAT_MPEG_AAC_LC;
            break;
        //case AFORMAT_DDPlus:
        case AFORMAT_AC3:
            ret = AUDIO_CODEC_FORMAT_AC3;
            break;
        case AFORMAT_DTS:
            ret = AUDIO_CODEC_FORMAT_DTS;
            break;
        case AFORMAT_FLAC:
            ret = AUDIO_CODEC_FORMAT_FLAC;
            break;
        case AFORMAT_COOK:
            ret = AUDIO_CODEC_FORMAT_COOK;
            break;
        case AFORMAT_AMR:
            ret = AUDIO_CODEC_FORMAT_AMR;
            break;
        case AFORMAT_WMA:
        case AFORMAT_WMAPRO:
            ret = AUDIO_CODEC_FORMAT_WMA_STANDARD;
            break;
        case AFORMAT_VORBIS:
            ret = AUDIO_CODEC_FORMAT_OGG;
            break;
        default:
            loge("unsupported video codec format!");
            break;
    }
    return ret;
}

int CTC_MediaProcessorImpl::ProcessCallback(int eMessageId, void* param)
{
    logd("ProcessCallback");
    switch(eMessageId)
    {
        case PLAYER_NOTIFY_EOS:
        	logd("Player Notify EOS");
            mIsEos = true;
            break;
        case PLAYER_NOTIFY_FIRST_PICTURE:
            if(m_callBackFunc != NULL)
                m_callBackFunc(IPTV_PLAYER_EVT_FIRST_PTS, m_eventHandler, 0, 0);
            break;

        case PLAYER_NOTIFY_VIDEO_UNSUPPORTED:
        	loge("do not support this video format");
        	if(m_callBackFunc != NULL)
                m_callBackFunc(IPTV_PLAYER_EVT_ABEND, m_eventHandler, 0, 0);
        	break;
        default:
            break;
    }
    
    return 0;
}

int CTC_MediaProcessorImpl::QueryBuffer(Player* pl, int nRequireSize,  int MediaType)
{
    int             ret = 0;
    void*           pBuf0 = NULL;
    int             nBufSize0 = 0;
    void*           pBuf1 = NULL;
    int             nBufSize1 = 0;
    int             nStreamIndex = 0;
	enum EMEDIATYPE eMediaType;

	eMediaType = (enum EMEDIATYPE)MediaType;
    ret = PlayerRequestStreamBuffer(mPlayer, nRequireSize, &pBuf0, &nBufSize0, &pBuf1, &nBufSize1, eMediaType, nStreamIndex);
    if(nRequireSize > nBufSize0 + nBufSize1)
    {
    	logd("not enough stream buffer, nBufSize0 %d, nBufSize1 %d", nBufSize0, nBufSize1);
        ret = -1;
    }
	return ret;
}

int CTC_MediaProcessorImpl::RequestBuffer(unsigned char** ppBuf, unsigned int* pSize, int MediaType)
{
    int             ret = 0;
    int             nRequireSize = 0;
    void*           pBuf0 = NULL;
    int             nBufSize0 = 0;
    void*           pBuf1 = NULL;
    int             nBufSize1 = 0;
    enum EMEDIATYPE eMediaType;
    int             nStreamIndex = 0;

	eMediaType = (enum EMEDIATYPE) MediaType;
	nRequireSize = (eMediaType == MEDIA_TYPE_VIDEO) ? CTC_ES_VIDEO_BUFFER_SIZE : CTC_ES_AUDIO_BUFFER_SIZE;
	
    while(1)
    {
        ret = PlayerRequestStreamBuffer(mPlayer, nRequireSize, &pBuf0, &nBufSize0, &pBuf1, &nBufSize1, eMediaType, nStreamIndex);
	    if(ret == 0)
	    {
	   	 	if (eMediaType == MEDIA_TYPE_VIDEO)
	        	mVideoBufForTsDemux = (char*)pBuf0;
			else
				mAudioBufForTsDemux = (char*)pBuf0;
	        *ppBuf              	= (unsigned char*)pBuf0;
	        *pSize              	= nBufSize0;
	        break;
	    }
	    else
	    {
	        logd(" waiting for video stream buffer, nBufSize0 %d, nBufSize1 %d", nBufSize0, nBufSize1);
            if (mIsQuitedRequest)
            	return 0;

	        usleep(10*1000);
	    }
    }
    	
	return 0;
}

int CTC_MediaProcessorImpl::UpdateData(int64_t pts, int nDataSize, int bIsFirst, int bIsLast, int MediaType)
{
	MediaStreamDataInfo dataInfo;
	memset(&dataInfo, 0, sizeof(MediaStreamDataInfo));
    enum EMEDIATYPE     eMediaType; 
    int                 nStreamIndex = 0;
    
    eMediaType			  = (enum EMEDIATYPE)MediaType;
	dataInfo.pData 		  = (eMediaType == MEDIA_TYPE_VIDEO)? (char*)mVideoBufForTsDemux : (char*)mAudioBufForTsDemux;
	dataInfo.nLength      = nDataSize;
    dataInfo.nPts         = pts;  //* input pts is in 90KHz.
    dataInfo.bIsFirstPart = bIsFirst;
    dataInfo.bIsLastPart  = bIsLast;
//	logd("eMediaType(%d), dataInfo.nLength(%d), dataInfo.nPts(%lld), dataInfo.bIsFirstPart(%d), dataInfo.bIsLastPart(%d),dataInfo.pData(%p)", 
//		eMediaType, dataInfo.nLength, dataInfo.nPts, dataInfo.bIsFirstPart, dataInfo.bIsLastPart, dataInfo.pData);

	
#if SAVE_FILE
	FILE *tmp = NULL;
	if(eMediaType == MEDIA_TYPE_VIDEO)
	{
		tmp = file1;
	}
	else 
	{
		tmp = file2;
	}
	if (tmp)
	{
		fwrite(dataInfo.pData, 1, dataInfo.nLength, tmp);
		sync();
	}
	else
	{
		CDX_LOGW("save file = NULL");
	}
#endif
    return PlayerSubmitStreamData(mPlayer, &dataInfo, eMediaType, nStreamIndex);
}

int CTC_MediaProcessorImpl::OpenTsDemux(void *mDemux, int nPid, int MediaType, int Format)
{
	int ret = 0;
	demux_filter_param_t filterParam;
	enum EMEDIATYPE	eMediaType; 
	vformat_t vFormat;
	
	eMediaType = (enum EMEDIATYPE)MediaType;
	vFormat    = (vformat_t)Format;
	
	if (mDemux != NULL)
	{
		if (eMediaType == MEDIA_TYPE_VIDEO) 
		{
			filterParam.request_buffer_cb = RequestVideoBufferCallback;
		    filterParam.update_data_cb    = UpdateVideoDataCallback;
		    if(vFormat == VFORMAT_H264)
		    	filterParam.codec_type		  =  DMX_CODEC_H264;
		    else if(vFormat == VFORMAT_H265)
		    	filterParam.codec_type		  =  DMX_CODEC_H265;
		    else
		    	filterParam.codec_type		  =  DMX_CODEC_UNKOWN;
		}
		else if (eMediaType == MEDIA_TYPE_AUDIO)
		{
			filterParam.request_buffer_cb = RequestAudioBufferCallback;
			filterParam.update_data_cb	  = UpdateAudioDataCallback;
		}
		else
			ret = -1;	
		
		filterParam.cookie = (void*)this;
		ret = ts_demux_open_filter(mDemux, nPid, &filterParam);
	}else
	{
		ALOGW("OpenTsDemux Failed");
		ret = -1;
	}	
	
	return ret;
}

int CTC_MediaProcessorImpl::CloseTsDemux(void *mDemux, int nPid)
{
	int ret = 0;
	if (mDemux != NULL)
	{
		ret = ts_demux_close_filter(mDemux, nPid);
	}else
	{
		ALOGW("CloseTsDemux Failed");
		ret = -1;
	}
	return ret;	
}

int CTC_MediaProcessorImpl::SetAudioInfo(PAUDIO_PARA_T pAudioPara)
{	
	logd("SetAudioStreamInfo");
    int	ret = 0;
	AudioStreamInfo streamInfo;

	//* set audio stream info.
    memset(&streamInfo, 0, sizeof(streamInfo));
    streamInfo.eCodecFormat          = (enum EAUDIOCODECFORMAT)audioCodecConvert(pAudioPara->aFmt);
    streamInfo.nChannelNum           = pAudioPara->nChannels;
    streamInfo.nSampleRate           = pAudioPara->nSampleRate;
#if !NewInterface
    streamInfo.nBitsPerSample        = pAudioPara->bit_per_sample;
    streamInfo.nBlockAlign           = pAudioPara->block_align;
#endif	
    streamInfo.nCodecSpecificDataLen = pAudioPara->nExtraSize;
    streamInfo.pCodecSpecificData    = (char*)pAudioPara->pExtraData;  //* player will copy the data to its internal buffer.
    streamInfo.nFlags 				 = (streamInfo.eCodecFormat == AUDIO_CODEC_FORMAT_MPEG_AAC_LC) ? 1 : 0;

	logd("******* audio info *******************");
	logd("audio streamInfo.eCodecFormat(%d)", streamInfo.eCodecFormat);
	logd("streamInfo.nChannelNum(%d)",        streamInfo.nChannelNum);
	logd("streamInfo.nSampleRate(%d)",        streamInfo.nSampleRate);
	logd("streamInfo.nBitsPerSample(%d)",     streamInfo.nBitsPerSample);
	logd("streamInfo.nCodecSpecificDataLen(%d)", streamInfo.nCodecSpecificDataLen);
	logd("************* end *******************");
    ret = PlayerSetAudioStreamInfo(mPlayer, &streamInfo, 1, 0);
	
	return ret;
}

int CTC_MediaProcessorImpl::SetVideoInfo(PVIDEO_PARA_T pVideoPara)
{
	logd("SetVideoStreamInfo");
    int	ret = 0;
	VideoStreamInfo streamInfo;

	//* set video stream info.
	memset(&streamInfo,0,sizeof(streamInfo));
	streamInfo.eCodecFormat = videoCodecConvert(pVideoPara->vFmt);
	streamInfo.nWidth		= pVideoPara->nVideoWidth;
	streamInfo.nHeight		= pVideoPara->nVideoHeight;
	streamInfo.nFrameRate	= pVideoPara->nFrameRate;

	memcpy(&mVideoPara, pVideoPara, sizeof(VIDEO_PARA_T));

	ret = PlayerSetVideoStreamInfo(mPlayer, &streamInfo);
	//if(ret != 0)
	{
		//loge("InitVideo err");
		logd("vFmt:%d",pVideoPara->vFmt);
		logd("nVideoWidth:%d",pVideoPara->nVideoWidth);
		logd("nVideoHeight:%d",pVideoPara->nVideoHeight);
		logd("nFrameRate:%d",pVideoPara->nFrameRate);
	}

	return ret;
}

void CTC_MediaProcessorImpl::InitVideo(PVIDEO_PARA_T pVideoPara)
{
    logd("InitVideo, pid: %d", pVideoPara->pid);
    int ret = 0;

	Mutex::Autolock _l(mLock);

	//* ES pid=0xffff, do not open ts demux
	if (pVideoPara->pid != 0xffff)
		ret = OpenTsDemux(mDemux, pVideoPara->pid, (int)MEDIA_TYPE_VIDEO, (int)pVideoPara->vFmt);
	else
		logd("ES Video Stream");

	if (pVideoPara != NULL)
		ret = SetVideoInfo(pVideoPara);
	else
		ALOGW("InitVideo Failed");
	mVideoPids = pVideoPara->pid;
	
	ret = PlayerSetHoldLastPicture(mPlayer, 1);
	return;
}

void CTC_MediaProcessorImpl::InitAudio(PAUDIO_PARA_T pAudioPara)
{
	logd("InitAudio, pid: %d", pAudioPara->pid);
    int	ret = 0;

	Mutex::Autolock _l(mLock);

	//* ES pid=0xffff, do not open ts demux
	if (pAudioPara->pid != 0xffff)
		ret = OpenTsDemux(mDemux, pAudioPara->pid, (int)MEDIA_TYPE_AUDIO, (int)0);
	else
		logd("ES Video Stream");

    mAudioTrackCount               = 1;
    mCurrentAudioTrack             = 0;
    mAudioPids[mCurrentAudioTrack] = pAudioPara->pid;         

	if (pAudioPara != NULL)
		ret = SetAudioInfo(pAudioPara);
	else
		ALOGW("InitAudio Failed");

    return;
}	


bool CTC_MediaProcessorImpl::StartPlay()
{
    int ret;
	logd("StartPlay");

    mIsEos = false;
    mIsQuitedRequest = false;

	Mutex::Autolock _l(mLock);

	if (!mIsSetSurfaceTexture)
	{
		if(mPlayer != NULL && mNativeWindow != NULL)
		{
			PlayerSetWindow(mPlayer, mNativeWindow.get());
			mIsSetSurfaceTexture = true;
		}
	}


	
	ret = PlayerStart(mPlayer);  
    if(ret == 0)
        return true;
    else
        return false;
}

bool CTC_MediaProcessorImpl::Pause()
{
    int ret;
    
	logd("Pause");
	
	//mIsQuitedRequest = true;
	Mutex::Autolock _l(mLock);

	ret = PlayerPause(mPlayer);
    if(ret == 0)
        return true;
    else
        return false;
}


bool CTC_MediaProcessorImpl::Resume()
{
    int ret;
	logd("Resume");

	mIsQuitedRequest = false;
	Mutex::Autolock _l(mLock);

	ret = PlayerStart(mPlayer);
    if(ret == 0)
        return true;
    else
        return false;
}

bool CTC_MediaProcessorImpl::Fast()
{
    int ret;
	logd("Fast");

	pthread_mutex_lock(&mSeekMutex);
	mSeekRequest = true;
	while(!mSeekReply)
	{
		pthread_cond_wait(&mCond, &mSeekMutex);
	}
	pthread_mutex_unlock(&mSeekMutex);
	
	Mutex::Autolock _l(mLock);

	if (mPlayer)
		ret = PlayerReset(mPlayer);

	mLeftBytes = 0; //* discard kept ts data.  
	
	if(mCache)
		StreamCacheFlushAll(mCache);

	mSeekRequest = false;

	ret = PlayerFast(mPlayer);//只解关键帧
	mLeftBytes = 0; //* discard kept ts data.   
    if(ret == 0)
        return true;
    else
        return false;
}

bool CTC_MediaProcessorImpl::StopFast()
{
    int ret;
	logd("Stop Fast");
	Mutex::Autolock _l(mLock);

	ret = PlayerStopFast(mPlayer);
	mLeftBytes = 0; //* discard kept ts data.
    if(ret == 0)
        return true;
    else
        return false;
}

bool CTC_MediaProcessorImpl::Stop()
{
    int ret;
	logd("Stop");
	pthread_mutex_lock(&mSeekMutex);
	mSeekRequest = true;
	while(!mSeekReply)
	{
		pthread_cond_wait(&mCond, &mSeekMutex);
	}
	pthread_mutex_unlock(&mSeekMutex);

 	Mutex::Autolock _l(mLock);   
	
	ret = PlayerStop(mPlayer);
	
	ret = CloseTsDemux(mDemux, mAudioPids[mCurrentAudioTrack]);
	ret = CloseTsDemux(mDemux, mVideoPids);
	mVideoBufForTsDemux = NULL;
	mAudioBufForTsDemux = NULL;
	logd("Stop end");
	mLeftBytes = 0;
	
	if(mCache)
		StreamCacheFlushAll(mCache);
	mSeekRequest = false;
    if(ret == 0)
        return true;
    else
        return false;
}

bool CTC_MediaProcessorImpl::Seek()
{
    int ret;
	logd("Seek");	
	
	pthread_mutex_lock(&mSeekMutex);
	mSeekRequest = true;
	while(!mSeekReply)
	{
		pthread_cond_wait(&mCond, &mSeekMutex);
	}
	pthread_mutex_unlock(&mSeekMutex);

	Mutex::Autolock _l(mLock);

	if (mPlayer)
		ret = PlayerReset(mPlayer);
	else
		ret = -1;

	mLeftBytes = 0; //* discard kept ts data.  
	
	if(mCache)
		StreamCacheFlushAll(mCache);

	mSeekRequest = false;
		
    if(ret == 0)
        return true;
    else
        return false;
}


static int PlayerBufferOverflow(Player* p)
{
    int bVideoOverflow;
    int bAudioOverflow;
    
    int     nPictureNum;
    int     nFrameDuration;
    int     nPcmDataSize;
    int     nSampleRate;
    int     nChannelCount;
    int     nBitsPerSample;
    int     nStreamDataSize;
    int     nBitrate;
    int     nStreamBufferSize;
    int64_t nVideoCacheTime;
    int64_t nAudioCacheTime;
    
    bVideoOverflow = 1;
    bAudioOverflow = 1;
    
    if(PlayerHasVideo(p))
    {
        nPictureNum       = PlayerGetValidPictureNum(p);
        nFrameDuration    = PlayerGetVideoFrameDuration(p);
        nStreamDataSize   = PlayerGetVideoStreamDataSize(p);
        nBitrate          = PlayerGetVideoBitrate(p);
        nStreamBufferSize = PlayerGetVideoStreamBufferSize(p);
        
        nVideoCacheTime = nPictureNum*nFrameDuration;
        
        if(nBitrate > 0)
            nVideoCacheTime += ((int64_t)nStreamDataSize)*8*1000*1000/nBitrate;
        
        if(nVideoCacheTime <= 4000000 || nStreamDataSize<nStreamBufferSize/2)   //* cache more than 2 seconds of data.
            bVideoOverflow = 0;
        
        logi("picNum = %d, frameDuration = %d, dataSize = %d, bitrate = %d, bVideoOverflow = %d",
            nPictureNum, nFrameDuration, nStreamDataSize, nBitrate, bVideoOverflow);
    }
    
    if(PlayerHasAudio(p))
    {
        nPcmDataSize    = PlayerGetAudioPcmDataSize(p);
        nStreamDataSize = PlayerGetAudioStreamDataSize(p);
        nBitrate        = PlayerGetAudioBitrate(p);
        PlayerGetAudioParam(p, &nSampleRate, &nChannelCount, &nBitsPerSample);
        
        nAudioCacheTime = 0;
        
        if(nSampleRate != 0 && nChannelCount != 0 && nBitsPerSample != 0)
        {
            nAudioCacheTime += ((int64_t)nPcmDataSize)*8*1000*1000/(nSampleRate*nChannelCount*nBitsPerSample);
        }
        
        if(nBitrate > 0)
            nAudioCacheTime += ((int64_t)nStreamDataSize)*8*1000*1000/nBitrate;
        
        if(nAudioCacheTime <= 2000000)   //* cache more than 2 seconds of data.
            bAudioOverflow = 0;
        
        logi("nPcmDataSize = %d, nStreamDataSize = %d, nBitrate = %d, nAudioCacheTime = %lld, bAudioOverflow = %d",
            nPcmDataSize, nStreamDataSize, nBitrate, nAudioCacheTime, bAudioOverflow);
    }
    
    return bVideoOverflow && bAudioOverflow;
}

void* CTC_MediaProcessorImpl::WriteDataThread(void* pArg)
{
	unsigned char* pBuffer;
	unsigned int   nSize;
	CacheNode*          node;
	CTC_MediaProcessorImpl *impl = (CTC_MediaProcessorImpl *)pArg;//dynamic_cast<CTC_MediaProcessorImpl*>(pArg);
	while(1)
	{
		if (impl->mIsQuitedRequest)
            	return NULL;

		if(impl->mSeekRequest)
		{
			pthread_mutex_lock(&impl->mSeekMutex);
			impl->mSeekReply = 1;
			pthread_cond_signal(&impl->mCond);
			pthread_mutex_unlock(&impl->mSeekMutex);

			usleep(10*1000); // wait playerReset and FlushCacheStream
			continue;
		}
		impl->mSeekReply = 0;
		
//        logd("StreamCacheGetSize(impl->mCache): %d", StreamCacheGetSize(impl->mCache));
		if(PlayerBufferOverflow(impl->mPlayer) || StreamCacheGetSize(impl->mCache) <= 0)
		{
			usleep(10*1000);
			continue;
		}
		
		//********************************
        //* 1. get one frame from cache.
        //********************************
        node = StreamCacheNextFrame(impl->mCache);
	    if(node == NULL)
	    {
	        loge("Cache not underflow but cannot get stream frame, shouldn't run here.");
	        abort();
	    }

		pBuffer   = node->pData;
		nSize	  = node->nLength;

#if 1		    
		if(impl->mLeftBytes > 0)
		{
	        if(impl->mTsStreamBufSize < (nSize+impl->mLeftBytes))
	        {
	            unsigned char* newBuf;
	            unsigned int   newBufSize;
	            newBufSize = nSize+impl->mLeftBytes;
	            if(newBufSize < CTC_MAX_TSDATA_PACKET_SIZE)
	                newBufSize = CTC_MAX_TSDATA_PACKET_SIZE;
				
	            
	            newBuf = (unsigned char*)malloc(newBufSize);
	            if(newBuf == NULL)
	            {
	            	logd("malloc fail");
					return NULL;
	            }
	            
	            memcpy(newBuf, impl->mTsStreamBuf, impl->mLeftBytes);
	            
	            free(impl->mTsStreamBuf);
	            impl->mTsStreamBuf = newBuf;
	            impl->mTsStreamBufSize = newBufSize;
	        }
			memcpy((unsigned char*)impl->mTsStreamBuf + impl->mLeftBytes, pBuffer, nSize);
			pBuffer = (unsigned char*)impl->mTsStreamBuf;
			nSize += impl->mLeftBytes;
		}

		impl->mLeftBytes = ts_demux_handle_packets(impl->mDemux, (unsigned char*)pBuffer, nSize);
		if(impl->mLeftBytes < 0)
		{
			loge("mLeftBytes(%d)", impl->mLeftBytes);
			impl->mLeftBytes = 0;
			return NULL;
		}
		else if(impl->mLeftBytes > 0)
		{
	    	logw("mLeftBytes(%d)", impl->mLeftBytes);
	        unsigned char* ptr = (unsigned char*)pBuffer + nSize -impl-> mLeftBytes;
	        memcpy(impl->mTsStreamBuf, ptr, impl->mLeftBytes);
		}

#else
		impl->mLeftBytes = ts_demux_handle_packets(impl->mDemux, (unsigned char*)pBuffer, nSize);
		if(impl->mLeftBytes < 0)
		{
			loge("mLeftBytes(%d)", impl->mLeftBytes);
			impl->mLeftBytes = 0;
			return NULL;
		}
		else if (impl->mLeftBytes > 0)
		{
	    	logw("mLeftBytes(%d)", impl->mLeftBytes);
		}
#endif

		StreamCacheFlushOneFrame(impl->mCache);
	}

	return NULL;
}


#if NewInterface
#define HandleLeftInLocal (0)
int CTC_MediaProcessorImpl::WriteData(unsigned char* pBufferIn, unsigned int nSizeIn)
{
	//logd("WriteData, pBuffer(%p), nSize(%d)", pBufferIn, nSizeIn);
	SetVideoWindow(mVideoX, mVideoY, mVideoWidth, mVideoHeight);
	Mutex::Autolock _l(mLock);
	unsigned int size1 = nSizeIn;

	if(StreamCacheOverflow(mCache))
	{
		logd("StreamCacheOverflow");
		return -1;
	}

	//*  add this stream to cache
    CacheNode  node;
    node.pData = (unsigned char*)malloc(nSizeIn);
    memcpy(node.pData, pBufferIn, nSizeIn);
    node.nLength = nSizeIn;
	node.pNext   = NULL;
	StreamCacheAddOneFrame(mCache, &node);

#if SAVE_FILE
	if(file)
		fwrite(pBufferIn, 1, nSizeIn, file);
#endif

	return size1;
}
#else 
int CTC_MediaProcessorImpl::GetWriteBuffer(IPTV_PLAYER_STREAMTYPE_e type, unsigned char** pBuffer, unsigned int *nSize)
{
    enum EMEDIATYPE     mediaType;
    void*               pBuf0 = NULL;
    int                 nBufSize0 = 0;
    void*               pBuf1 = NULL;
    int                 nBufSize1 = 0;
    int                 nStreamIndex = 0;
    int					ret = 0;

    unsigned int nRequireSize;
    unsigned int nQueryRequireSize;
    
    nQueryRequireSize = nRequireSize = *nSize;
	Mutex::Autolock _l(mLock);

    if(type == IPTV_PLAYER_STREAMTYPE_TS)
    {
    	mStreamType = IPTV_PLAYER_STREAMTYPE_TS;
		
        if(mTsStreamBufSize < (nRequireSize+mLeftBytes))
        {
            unsigned char* newBuf;
            unsigned int   newBufSize;
            newBufSize = nRequireSize+mLeftBytes;
            if(newBufSize < CTC_MAX_TSDATA_PACKET_SIZE)
                newBufSize = CTC_MAX_TSDATA_PACKET_SIZE;
            
            newBuf = (unsigned char*)malloc(newBufSize);
            if(newBuf == NULL)
            {
                *pBuffer = NULL;
                *nSize   = 0;
                ret = -1;
            	goto GETBUFFERQUIT;
            }
            
            if(mLeftBytes > 0)
                memcpy(newBuf, mTsStreamBuf, mLeftBytes);
            
            free(mTsStreamBuf);
            mTsStreamBuf = newBuf;
            mTsStreamBufSize = newBufSize;
        }
        
		/*
         * nQueryRequireSize in simple caculation
        */
		if (mPlayer != NULL)
		{
            if (*nSize > 4)
			    nQueryRequireSize = *nSize >> 1;
			ret = QueryBuffer(mPlayer, nQueryRequireSize, (int)MEDIA_TYPE_VIDEO);
			if (ret != 0)
			{
				*pBuffer = NULL;
				*nSize = 0;
				ret = -1;
				goto GETBUFFERQUIT;
			}

            if (*nSize > 4)
			    nQueryRequireSize = *nSize >> 2;
			ret = QueryBuffer(mPlayer, nQueryRequireSize, (int)MEDIA_TYPE_AUDIO);
			if (ret != 0)
			{
				*pBuffer = NULL;
				*nSize = 0;
				ret = -1;
				goto GETBUFFERQUIT;
			}
		}

        *pBuffer = (unsigned char*)mTsStreamBuf + mLeftBytes;
        return 0;
    }
    else if(type == IPTV_PLAYER_STREAMTYPE_VIDEO)
    {
    	mStreamType = IPTV_PLAYER_STREAMTYPE_VIDEO;
        if(mVideoStreamBufSize < nRequireSize)
        {
            mVideoStreamBufSize = nRequireSize;
            if(mVideoStreamBufSize < CTC_MAX_VIDEO_PACKET_SIZE)
                mVideoStreamBufSize = CTC_MAX_VIDEO_PACKET_SIZE;

			unsigned char* p;
            p = realloc(mVideoStreamBuf, mVideoStreamBufSize);
            if(p == NULL)
            {
                *pBuffer = NULL;
                *nSize   = 0;
                mVideoStreamBufSize = 0;
                if(mVideoStreamBuf)
                {
	                free(mVideoStreamBuf);
	                mVideoStreamBuf = NULL;
                }
                ret = -1;
            	goto GETBUFFERQUIT;
            }

            mVideoStreamBuf = p;            
        }

        if (mPlayer != NULL)
        {
            ret = QueryBuffer(mPlayer, *nSize, (int)MEDIA_TYPE_VIDEO);
    		if (ret != 0)
    		{
                *pBuffer = NULL;
                mVideoStreamBufSize = 0;
                ret = -1;
            	goto GETBUFFERQUIT;
    		}
        }

        *pBuffer = (unsigned char*)mVideoStreamBuf;
        ret = 0;
    }
    else
    {
		mStreamType = IPTV_PLAYER_STREAMTYPE_AUDIO;
        if(mAudioStreamBufSize < nRequireSize)
        {
            mAudioStreamBufSize = nRequireSize;
            if(mAudioStreamBufSize < CTC_MAX_AUDIO_PACKET_SIZE)
                mAudioStreamBufSize = CTC_MAX_AUDIO_PACKET_SIZE;

			unsigned char* p;
            p = realloc(mAudioStreamBuf, mAudioStreamBufSize);
            if(p == NULL)
            {
                *pBuffer = NULL;
                *nSize   = 0;
                mAudioStreamBufSize = 0;
                if(mAudioStreamBuf)
                {
	                free(mAudioStreamBuf);
	                mAudioStreamBuf = NULL;
                }
                ret = -1;
            	goto GETBUFFERQUIT;
            }

            mAudioStreamBuf = p;
        }

        if (mPlayer != NULL)
        {
            ret = QueryBuffer(mPlayer, *nSize, (int)MEDIA_TYPE_AUDIO);
    		if (ret != 0)
    		{
                *pBuffer = NULL;
                mAudioStreamBufSize = 0;
                ret = -1;
            	goto GETBUFFERQUIT;
    		}
        }

        *pBuffer = (unsigned char*)mAudioStreamBuf;
        ret = 0;
    }

GETBUFFERQUIT:
	return ret;
}

int CTC_MediaProcessorImpl::WriteData(IPTV_PLAYER_STREAMTYPE_e type, unsigned char* pBuffer, unsigned int nSize, uint64_t timestamp)
{
	MediaStreamDataInfo dataInfo;
    enum EMEDIATYPE     mediaType;
    void*               pBuf0;
    int                 nBufSize0;
    void*               pBuf1;
    int                 nBufSize1;
    int                 nStreamIndex;
	int					ret;
	
	Mutex::Autolock _l(mLock);

    if(type == IPTV_PLAYER_STREAMTYPE_TS)
    {
        if(pBuffer != mTsStreamBuf) //* some data left at last time and we've kept it.
            nSize += mLeftBytes;

        mLeftBytes = ts_demux_handle_packets(mDemux, (unsigned char*)mTsStreamBuf, nSize);
        if(mLeftBytes > 0)
        {
        	logd("mLeftBytes(%d)", mLeftBytes);
            unsigned char* ptr = (unsigned char*)mTsStreamBuf + nSize - mLeftBytes;
            memcpy(mTsStreamBuf, ptr, mLeftBytes);
        }
        else if(mLeftBytes < 0)
        {
            //* something error, return fail.
            mLeftBytes = 0;
            return -1;
        }
        return 0;
    }
    else if(type == IPTV_PLAYER_STREAMTYPE_VIDEO)
    {
        nStreamIndex = 0;
        mediaType = MEDIA_TYPE_VIDEO;
    }
    else if(type == IPTV_PLAYER_STREAMTYPE_AUDIO)
    {
        nStreamIndex = mCurrentAudioTrack;
        mediaType = MEDIA_TYPE_AUDIO;
    }
    else
    {
        loge("not support stream type!%d",type);
        return -1;
    }

    // VideoStreamDataInfo dataInfo;
    memset(&dataInfo, 0, sizeof(dataInfo));

    while(1)
    {
        ret = PlayerRequestStreamBuffer(mPlayer, nSize, &pBuf0, &nBufSize0, &pBuf1, &nBufSize1, mediaType, nStreamIndex);
        if((unsigned int)(nBufSize0 + nBufSize1) < nSize)
        {
            logd("waiting for stream buffer.");
            if (mIsQuitedRequest)
            	return 0;

            usleep(10 * 1000);
            continue;
        }

        if((unsigned int)nBufSize0 >= nSize)
            memcpy(pBuf0, pBuffer, nSize);
        else
        {
            memcpy(pBuf0, pBuffer, nBufSize0);
            memcpy(pBuf1, pBuffer+nBufSize0, nSize-nBufSize0);
        }

		//* When ES pts not valid, it would set it to 0xffffffff
		if (timestamp != 0xffffffff)
			dataInfo.nPts = timestamp * 1000 / 90; //* input pts is in 90KHz.
		else
			dataInfo.nPts = -1LL;

        dataInfo.pData        =(char*)pBuf0;
        dataInfo.nLength      = nSize;
		dataInfo.bIsFirstPart = 1;
        dataInfo.bIsLastPart  = 1;		

        ret = PlayerSubmitStreamData(mPlayer, &dataInfo, mediaType, nStreamIndex);
        break;
    }

    return 0;
}
#endif 

#if NewInterface
void CTC_MediaProcessorImpl::SwitchAudioTrack(int pid)
{
    logd("SwitchAudioTrack");
	int ret = 0;
	Mutex::Autolock _l(mLock);
	if (mPlayer != NULL)
	{
		if(mAudioPids[mCurrentAudioTrack] == pid)
		{
			logd("mAudioPids[mCurrentAudioTrack] == pid");
			return;
		}
		logd("SwitchAudio, pid:(%d)->(%d)", mAudioPids[mCurrentAudioTrack], pid);
#if SwitchAudioSeamless
		PlayerStopAudio(mPlayer);
#endif
		mIsQuitedRequest  = true;
		ret = CloseTsDemux(mDemux, mAudioPids[mCurrentAudioTrack]);
		mIsQuitedRequest  = false;
		mAudioPids[mCurrentAudioTrack] = pid;
		ret = OpenTsDemux(mDemux, pid, (int)MEDIA_TYPE_AUDIO, (int)0);
		
#if SwitchAudioSeamless
		PlayerStartAudio(mPlayer);
#endif
	}
	else
	{
		ALOGW("mPlayer Not Initialized");
	}
	return ;
}
#else
void CTC_MediaProcessorImpl::SwitchAudioTrack(int pid, PAUDIO_PARA_T pAudioPara)
{
    logd("SwitchAudioTrack");
	int ret = 0;
	Mutex::Autolock _l(mLock);

	if (mPlayer != NULL)
	{
	    if (mStreamType == IPTV_PLAYER_STREAMTYPE_TS) //* TS Stream
	    {
	    	mIsQuitedRequest  = true;
			ret = CloseTsDemux(mDemux, mAudioPids[mCurrentAudioTrack]);
			mIsQuitedRequest  = false;
			mAudioPids[mCurrentAudioTrack] = pAudioPara->pid = pid;
		    ret = OpenTsDemux(mDemux, pAudioPara->pid, (int)MEDIA_TYPE_AUDIO, (int)0);
	    }else
	    {
			//* for temp
			ret = PlayerPause(mPlayer);
			if (ret != 0)
				goto EXIT;
			SetAudioInfo(pAudioPara);
			ret = PlayerStart(mPlayer);	 
			if (ret != 0)
				goto EXIT;

	    }
	}else
	{
		ALOGW("mPlayer Not Initialized");
	}
EXIT:	
	return ;
}
#endif

void CTC_MediaProcessorImpl::SwitchSubtitle(int pid)
{
    logd("SwitchSubtitle");
	return ;
}



#if !NewInterface

bool CTC_MediaProcessorImpl::GetIsEos()
{
    logd("GetIsEos");
	Mutex::Autolock _l(mLock);
	//TO DO
    return mIsEos;

	//  return PlayerGetEos(mPlayer);
}
int CTC_MediaProcessorImpl::GetBufferStatus(long *totalsize, long *datasize)
{
	Mutex::Autolock _l(mLock);
	//TO DO
//    *totalsize = PlayerGetVideoTotalBufferSize(mPlayer) + PlayerGetAudioTotalBufferSize(mPlayer);
//    *datasize  = PlayerGetVideoStreamDataSize(mPlayer) + PlayerGetAudioStreamDataSize(mPlayer);
    //logd("GetBufferStatus, total = %d, data = %d", (int)*totalsize, (int)*datasize);
    return 0;
}

void CTC_MediaProcessorImpl::SetStopMode(bool bHoldLastPic)
{
	Mutex::Autolock _l(mLock);

    logd("SetStopMode, bHoldLastPic=%d", bHoldLastPic);
    mHoldLastPicture = bHoldLastPic;
    PlayerSetHoldLastPicture(mPlayer, mHoldLastPicture);
    return;
}
#endif

int	CTC_MediaProcessorImpl::GetPlayMode()
{
    logd("GetPlayMode");
#if NewInterface
	return 1;
#else
    enum EPLAYERSTATUS status = PlayerGetStatus(mPlayer);
    IPTV_PLAYER_STATE_e ret = IPTV_PLAYER_STATE_OTHER;
	
	Mutex::Autolock _l(mLock);
	
    switch(status)
    {
        case PLAYER_STATUS_STOPPED:
            ret = IPTV_PLAYER_STATE_STOP;
            break;
        case PLAYER_STATUS_STARTED:
            ret = IPTV_PLAYER_STATE_PLAY;
            break;
        case PLAYER_STATUS_PAUSED:
            ret = IPTV_PLAYER_STATE_PAUSE;
            break;
        default:
            loge("unkonw player status!");
            break;
    }
    return ret;
#endif
}

long CTC_MediaProcessorImpl::GetCurrentPlayTime()
{
    int64_t nCurTime;
	Mutex::Autolock _l(mLock);
	
    nCurTime = PlayerGetPosition(mPlayer)/1000;       //* current presentation time stamp.
    logd("nCurTime(%lld)", nCurTime);
    return (long)nCurTime;
}

void CTC_MediaProcessorImpl::GetVideoPixels(int& width, int& height)
{
	Mutex::Autolock _l(mLock);
    sp<IBinder> display(SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain));
    DisplayInfo info;
    SurfaceComposerClient::getDisplayInfo(display, &info);
    width = info.w;
    height = info.h;
    logd("GetVideoPixels, width = %d, height = %d", width, height);
    return ;
}

int CTC_MediaProcessorImpl::SetVideoWindow(int x,int y,int width,int height)
{
	Mutex::Autolock _l(mLock);
	int retPosition = 0, retSize = 0;
	bool modify = true;
	if ((mVideoX == x) && (mVideoY == y) 
	    && (mVideoWidth == width) && (mVideoHeight == height))
	{
	    modify = false;
	}
	else
	{
	    modify = true;

        mVideoX = x;
        mVideoY = y;
        mVideoWidth = width;
        mVideoHeight = height;

	    mIsSetVideoPosition = false; // position or size modify;
	}

    if ((!modify) && mIsSetVideoPosition)
    {
//        logw("already set: '%d, %d, %d, %d', do nothing", x, y, width, height);
//        return 0;
    }
	
//	logd("SetVideoWindow, x(%d),y(%d),width(%d), height(%d)", x,y,width, height);
//	logd("mNativeWindow '%p'", mNativeWindow.get());

	
	if(mNativeWindow != NULL)
	{
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2) //* for compile
		retPosition = mNativeWindow->perform(mNativeWindow.get(), 
		                                NATIVE_WINDOW_SETPARAMETER, 
		                                DISPLAY_CMD_SETVIDEOPOSITION, x, y);
		retSize = mNativeWindow->perform(mNativeWindow.get(), 
		                                NATIVE_WINDOW_SETPARAMETER, 
		                                DISPLAY_CMD_SETVIDEOSIZE, width, height);
        if ((retPosition == 0) && (retSize == 0))
        {
            logi("set video position success '%d, %d, %d, %d'", x, y, width, height);
            mIsSetVideoPosition = true;
        }
#endif		
	}
    return 0;
}
#if NewInterface
bool CTC_MediaProcessorImpl::SetRatio(int nRatio)
{
    logd("SetRadioMode");
    Mutex::Autolock _l(mLock);
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2)
    mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SETPARAMETER, DISPLAY_CMD_SETSCREENRADIO, nRatio);
#endif
    return 0;
}
#else
void CTC_MediaProcessorImpl::SetContentMode(IPTV_PLAYER_CONTENTMODE_e contentMode)
{
    logd("SetContentMode");
    enum EDISPLAYRATIO eDisplayRatio;
	
    Mutex::Autolock _l(mLock);
	
    switch(contentMode)
    {
        case IPTV_PLAYER_CONTENTMODE_LETTERBOX:
            eDisplayRatio = DISPLAY_RATIO_LETTERBOX;
            break;
        default:
            eDisplayRatio = DISPLAY_RATIO_FULL_SCREEN;
            break;
    }
    
    PlayerSetDisplayRatio(mPlayer, eDisplayRatio);
}
#endif

int CTC_MediaProcessorImpl::VideoShow()
{
    logd("VideoShow");
	Mutex::Autolock _l(mLock);
	if(mNativeWindow != NULL)
	{
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2)	
		mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SETPARAMETER, DISPLAY_CMD_SETVIDEOENABLE, VIDEO_ENABLE);
#endif
	}
	else
	{
		loge("mNativeWindow == NULL");
	}
    return 0;
}

int CTC_MediaProcessorImpl::VideoHide()
{
    logd("VideoHide");
	Mutex::Autolock _l(mLock);
	if(mNativeWindow != NULL)
	{
#if (CONFIG_OS_VERSION != OPTION_OS_VERSION_ANDROID_4_2)
    	mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SETPARAMETER, DISPLAY_CMD_SETVIDEOENABLE, VIDEO_DISABLE);
#endif
		int ret = SendThreeBlackFrameToGpu(mNativeWindow.get());
		logd("SendThreeBlackFrameToGpu,ret = %d", ret);
	}
	else
	{
		loge("mNativeWindow == NULL");
	}
    return 0;
}

int CTC_MediaProcessorImpl::GetAudioBalance()
{
    logd("GetAudioBalance");
	Mutex::Autolock _l(mLock);
	
	return PlayerGetAudioBalance(mPlayer);
}

bool CTC_MediaProcessorImpl::SetAudioBalance(int nAudioBalance)
{
    logd("SetAudioBalance");
	Mutex::Autolock _l(mLock);
	
    if(PlayerSetAudioBalance(mPlayer, nAudioBalance) == 0)
        return true;
    else
        return false;
}

void CTC_MediaProcessorImpl::SetSurface(Surface* pSurface)
{
    logd("SetSurface");
	Mutex::Autolock _l(mLock);
	
    if (pSurface == NULL)
    	return;
    	
    if (mNativeWindow != NULL)
    {
        mNativeWindow = NULL;
    }
	mNativeWindow = pSurface;
	
	native_window_set_buffers_geometry(
			mNativeWindow.get(),
			0,
			0,
			HAL_PIXEL_FORMAT_YCrCb_420_SP);
	
    //mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SETPARAMETER, DISPLAY_CMD_SETVIDEOENABLE, VIDEO_ENABLE);

    if (mPlayer != NULL)
    {
    	PlayerSetWindow(mPlayer, mNativeWindow.get());
    	mIsSetSurfaceTexture = true;
    }
    else
    {
    	mIsSetSurfaceTexture = false;
    }
    return;
}

void CTC_MediaProcessorImpl::playerback_register_evt_cb(IPTV_PLAYER_EVT_CB pfunc, void *handler)
{
	logd("playback_register_evt_cb");
	m_callBackFunc = pfunc;
	m_eventHandler = handler;
	return;
}
void CTC_MediaProcessorImpl::SetProperty(int nType, int nSub, int nValue)
{
	return;
}
void CTC_MediaProcessorImpl::leaveChannel()
{
	return;
}
int  CTC_MediaProcessorImpl::GetAbendNum()
{
	return 0;
}
bool CTC_MediaProcessorImpl::IsSoftFit()
{
	return false;
}
void CTC_MediaProcessorImpl::SetEPGSize(int w, int h)
{
	return;
}
bool CTC_MediaProcessorImpl::SetVolume(int volume)
{
	logd("SetVolume, volume=%d", volume);
	gVolume = volume;
	float vol = ((float)volume)/100.0;
	int ret = PlayerSetVolume(mPlayer, vol);
	logd("PlayerSetVolume, ret = %d", ret);
	return ret == 0? true : false;
}

int CTC_MediaProcessorImpl::GetVolume()
{
	logd("GetVolume");
	float vol;
	int ret = PlayerGetVolume(mPlayer, &vol);
	logd("PlayerGetVolume, ret = %d, vol = %f", ret, vol);
	if(ret != 0)
	{
		loge("PlayerGetVolume fail");
		return gVolume;
	}
	return (int)(vol*100);
}
