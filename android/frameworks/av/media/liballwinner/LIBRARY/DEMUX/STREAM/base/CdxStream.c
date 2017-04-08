#include <CdxTypes.h>
#include <CdxLog.h>
#include <CdxMemory.h>

#include <CdxStream.h>
#include "config.h"
/*
#define STREAM_DECLARE(scheme) \
    extern CdxStreamCreatorT cdx_##scheme##_stream_ctor

#define STREAM_REGISTER(scheme) \
    {#scheme, &cdx_##scheme##_stream_ctor}
*/

extern CdxStreamCreatorT fileStreamCtor;
#if (CONFIG_OS != OPTION_OS_LINUX)
extern CdxStreamCreatorT rtspStreamCtor;
#endif
extern CdxStreamCreatorT httpStreamCtor;
extern CdxStreamCreatorT tcpStreamCtor;
extern CdxStreamCreatorT rtmpStreamCtor;
extern CdxStreamCreatorT mmsStreamCtor;
extern CdxStreamCreatorT udpStreamCtor;
extern CdxStreamCreatorT rtpStreamCreator;
extern CdxStreamCreatorT customerStreamCtor;
extern CdxStreamCreatorT sslStreamCtor;
extern CdxStreamCreatorT aesStreamCtor;
extern CdxStreamCreatorT bdmvStreamCtor;
extern CdxStreamCreatorT widevineStreamCtor;
extern CdxStreamCreatorT videoResizeStreamCtor;

struct CdxStreamNodeS
{
    const cdx_char *scheme;
    CdxStreamCreatorT *creator;
};

static struct CdxStreamNodeS gStreamList[] =
{
    {"fd", &fileStreamCtor},
    {"file", &fileStreamCtor},
#if (CONFIG_OS!=OPTION_OS_LINUX)
    {"rtsp", &rtspStreamCtor},
#endif
    {"http", &httpStreamCtor},
    {"https", &httpStreamCtor},
    {"tcp", &tcpStreamCtor},
    {"rtmp", &rtmpStreamCtor},
    {"mms", &mmsStreamCtor},
    {"mmsh", &mmsStreamCtor},
    {"mmst", &mmsStreamCtor},
    {"mmshttp", &mmsStreamCtor},
    {"udp", &udpStreamCtor},
    {"rtp", &rtpStreamCreator},
	{"customer", &customerStreamCtor},
    {"ssl", &sslStreamCtor},
    {"aes", &aesStreamCtor},
    {"bdmv", &bdmvStreamCtor},
#if CONFIG_OS == OPTION_OS_ANDROID
    {"widevine", &widevineStreamCtor},
#endif
    {"videoResize",&videoResizeStreamCtor},

};

CdxStreamT *CdxStreamCreate(CdxDataSourceT *source)
{
    cdx_int32 i = 0;
    cdx_char scheme[16] = {0};
    cdx_char *colon;
    CdxStreamCreatorT *ctor = NULL;
    static const cdx_int32 streamListSize = sizeof(gStreamList)/sizeof(struct CdxStreamNodeS);

    colon = strchr(source->uri, ':');
    CDX_CHECK(colon && (colon - source->uri < 16));
    memcpy(scheme, source->uri, colon - source->uri);
    
    
    for (i = 0; i < streamListSize; i++)
    {
        if (strcasecmp(gStreamList[i].scheme, scheme) == 0)
        {
            ctor = gStreamList[i].creator;
            break;
        }
    }
    if (NULL == ctor)
    {
        CDX_LOGE("unsupport stream. scheme(%s)", scheme);
        return NULL;
    }

    CDX_CHECK(ctor->create);
    CdxStreamT *stream = ctor->create(source);
    if (!stream)
    {
        CDX_LOGE("open stream fail, uri(%s)", source->uri);
        return NULL;
    }
    
    return stream;
}

int CdxStreamOpen(CdxDataSourceT *source, pthread_mutex_t *mutex, cdx_bool *exit, CdxStreamT **stream, ContorlTask *streamTasks)
{
	pthread_mutex_lock(mutex);
    if(exit && *exit)
    {
    	CDX_LOGW("open stream user cancel.");
        pthread_mutex_unlock(mutex);
    	return -1;
    }
    *stream = CdxStreamCreate(source);
	pthread_mutex_unlock(mutex);
    if (!*stream)
    {
    	CDX_LOGW("open stream failure.");
    	return -1;
    }
	int ret;
	while(streamTasks)
	{
		ret = CdxStreamControl(*stream, streamTasks->cmd, streamTasks->param);
		if(ret < 0)
		{
			CDX_LOGE("CdxStreamControl fail, cmd=%d", streamTasks->cmd);
			return ret;
		}
		streamTasks = streamTasks->next;
	}
    return CdxStreamConnect(*stream);
}

