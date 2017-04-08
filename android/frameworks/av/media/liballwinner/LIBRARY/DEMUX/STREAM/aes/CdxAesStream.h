#ifndef AES_STREAM_H
#define AES_STREAM_H
#include <pthread.h>
#include <CdxTypes.h>
#include <CdxStream.h>
#include <CdxAtomic.h>
#include <openssl/aes.h>

enum CdxStreamStatus
{
    STREAM_IDLE,
	STREAM_CONNECTING,
    STREAM_SEEKING,
    STREAM_READING,
};

typedef struct
{
    CdxStreamT base;
	enum CdxStreamStatus status;
    cdx_int32 ioState;
    pthread_mutex_t lock;
    pthread_cond_t cond;
    
    int forceStop;
    CdxStreamProbeDataT probeData;
    pthread_t threadId;/*UdpDownloadThread*/
  
	cdx_uint8 *bigBuf;
	cdx_uint32 bufSize;

    CdxDataSourceT dataSource;
    CdxStreamT *mediaFile;
    cdx_int64 mediaFileSize;
    cdx_char *mediaUri;
    cdx_uint8 iv[16];
    cdx_uint8 originalIv[16];
    cdx_uint8 key[16];
    AES_KEY aesKey;
    enum PaddingType paddingType;

    int downloadComplete;
    cdx_uint32 remainingSize;
    cdx_uint32 remainingOffset;
    cdx_uint8 remaining[16];

}CdxAesStream;

#endif
