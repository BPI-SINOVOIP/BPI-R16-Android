#ifndef _CDX_TS_PARSER_H_
#define _CDX_TS_PARSER_H_

#include <CdxTypes.h>
#include <CdxList.h>
#include <CdxBuffer.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxAtomic.h>

#define NB_PID_MAX 8192
#define MAX_SECTION_SIZE 4096
//* codec id

#define CODEC_ID_DVB_SUBTITLE       18
#define CODEC_ID_BLUERAY_SUBTITLE   19

//* packet size of different format
#define TS_FEC_PACKET_SIZE          204
#define TS_DVHS_PACKET_SIZE         192
#define TS_PACKET_SIZE              188

/* pids */
#define PAT_PID                     0x0000
#define SDT_PID                     0x0011

/* table ids */
#define PAT_TID                     0x00
#define PMT_TID                     0x02
#define SDT_TID                     0x42
#define M4OD_TID                    0x05

/* descriptor ids */
#define DVB_SUBT_DESCID             0x59

#define STREAM_TYPE_VIDEO_MPEG1     0x01
#define STREAM_TYPE_VIDEO_MPEG2     0x02
#define STREAM_TYPE_AUDIO_MPEG1     0x03
#define STREAM_TYPE_AUDIO_MPEG2     0x04
#define STREAM_TYPE_PRIVATE_SECTION 0x05
#define STREAM_TYPE_PRIVATE_DATA    0x06
#define STREAM_TYPE_AUDIO_AAC       0x0f
#define STREAM_TYPE_VIDEO_MPEG4     0x10
#define STREAM_TYPE_AUDIO_MPEG4     0x11
#define STREAM_TYPE_OMX_VIDEO_CodingAVC      0x1b
#define STREAM_TYPE_VIDEO_H265      0x24
#define STREAM_TYPE_VIDEO_VC1       0xea

#define STREAM_TYPE_PCM_BLURAY      0x80    //* add for blue ray
#define STREAM_TYPE_CDX_AUDIO_AC3       0x81
#define STREAM_TYPE_AUDIO_HDMV_DTS  0x82
#define STREAM_TYPE_CDX_AUDIO_AC3_TRUEHD    0x83
#define STREAM_TYPE_AUDIO_EAC3      0x84
#define STREAM_TYPE_CDX_AUDIO_DTS_HRA   0x85    //* add for blue ray
#define STREAM_TYPE_CDX_AUDIO_DTS_MA    0x86    //* add for blue ray
#define STREAM_TYPE_CDX_AUDIO_DTS       0x8a
#define STREAM_TYPE_CDX_AUDIO_AC3_      0xa1
#define STREAM_TYPE_CDX_AUDIO_DTS_      0xa2

#define STREAM_TYPE_CDX_VIDEO_MVC		0x20

#define STREAM_TYPE_HDMV_PGS_SUBTITLE 0x90
#define STREAM_TYPE_SUBTITLE_DVB    0x100

#define STREAM_TYPE_VIDEO_AVS		0x42

static const int AC3_Channels[]=
{2, 1, 2, 3, 3, 4, 4, 5};
static const int AC3_SamplingRate[]=
{ 48000,  44100,  32000,	  0,};
static const int AC3_BitRate[]=
{
     32,
     40,
     48,
     56,
     64,
     80,
     96,
    112,
    128,
    160,
    192,
    224,
    256,
    320,
    384,
    448,
    512,
    576,
    640,
};

enum CdxParserStatus
{
    CDX_PSR_INITIALIZED,
    CDX_PSR_IDLE,
    CDX_PSR_PREFETCHING,
    CDX_PSR_PREFETCHED,
    CDX_PSR_SEEKING,
};

typedef enum {
    /* Do not change these values (starting with HDCP_STREAM_TYPE_),
     * keep them in sync with header file "DX_Hdcp_Types.h" from Discretix.
     */
	HDCP_STREAM_TYPE_UNKNOWN,
	HDCP_STREAM_TYPE_VIDEO,
	HDCP_STREAM_TYPE_AUDIO,
	HDCP_STREAM_TYPE_INVALUD = 0x7FFFFFFF
}HdcpStreamType;

#define CTS (0)
#define ProbeDataLen (2*1024)
#define HandlePtsInLocal 0
#define ParseAdaptationField 0
#define PES_PRIVATE_DATA_SIZE (16)
		
#define SizeToReadEverytimeInLocal (500*1024)
#define SizeToReadEverytimeInNet (4*1024)
#define SizeToReadEverytimeInMiracast (188*7)
#define VideoStreamBufferSize (1024*1024)
#define OPEN_CHECK (0)
#define Debug (0)
#define PtsDebug (0)
#define PROBE_STREAM (1)
#define ProbeSpecificData (1)
#define SIZE_OF_VIDEO_PROVB_DATA (2*1024*1024)
#define SIZE_OF_AUDIO_PROVB_DATA (150*1024)

typedef enum
{
	OK = 0,
	ERROR_MALFORMED = -1
}
status_t;


typedef enum
{
	TYPE_UNKNOWN	= -1,
    TYPE_VIDEO      = 0,
    TYPE_AUDIO		= 1,
    TYPE_SUBS		= 2,
}MediaType;

typedef struct
{
    struct CdxListNodeS node;
	unsigned PID;
	cdx_bool mPayloadStarted;
	CdxBufferT *mBuffer;
}PSISection;
/*
struct ESNode
{
	cdx_int64 mTimestampUs;
	const cdx_uint8 *data;
	size_t size;
	cdx_int64 pts;
	cdx_int64 durationUs;
};
*/
typedef struct
{
	cdx_uint8 *bigBuf;
	cdx_uint32 bufSize;
	cdx_uint32 validDataSize;
	cdx_uint32 writePos;//标记要写入的位置
	cdx_uint32 readPos;//标记要读取的位置
	cdx_int32 endPos;//标记要读取数据的截止位置
}CacheBuffer;


typedef struct ProgramS Program;
typedef struct StreamS Stream;
typedef struct TSParserS TSParser;
typedef struct PacketS Packet;



struct TSParserS
{
	CdxParserT base;
	
	CdxStreamT *cdxStream;
	cdx_int64 fileSize;
	cdx_int64 fileValidSize;
	int isNetStream;
	cdx_uint64 durationMs;
	cdx_uint64 byteRate;
	int seekMethod;
	cdx_uint64 preOutputTimeUs;
	//cdx_uint32 pidForProbePTS;
	//cdx_int64 preSeekPos;
	//cdx_int64 preSeekPTS;
	int hasAudioSync;
	int hasVideoSync;
	int syncOn;
	cdx_int64 videoPtsBaseUs;
	cdx_int64 audioPtsBaseUs;
	cdx_int64 commonPtsBaseUs;
	cdx_int64 vd_pts;
	cdx_int64 ad_pts;
	cdx_uint32 unsyncVideoFrame;
	cdx_uint32 unsyncAudioFrame;

    struct CdxListS mPSISections;
    struct CdxListS mPrograms;
	cdx_uint8 mProgramCount;
    unsigned pat_version_number;

	cdx_uint32 mRawPacketSize;
	cdx_int32 autoGuess;/*-1,选择节目结束，0依靠pat,pmt,1依靠猜测的pmt,2没有pat,pmt,构成杂散节目*/
	cdx_uint32 mStreamCount;
		
	cdx_uint64 mPCR[2];
	size_t mPCRBytes[2];
	cdx_int64 mSystemTimeUs[2];//目前没用
	size_t mNumPCRs;
	size_t mNumTSPacketsParsed;	
	cdx_uint64 dynamicRate;//bytes/sec
	cdx_uint64 overallRate;//bytes/sec
	cdx_uint64 accumulatedDeltaPCR;
    //size_t pesCount;
	
	Stream *currentES;

	CacheBuffer mCacheBuffer;
	cdx_uint8 *tmpBuf;

	int hasVideo;
	Stream *curVideo;

	int hasMinorVideo;
	Stream *curMinorVideo;
	
	int hasAudio;
	//Stream *curAudio;
	
	int hasSubtitle;
	//Stream *curSubtitlePid;
	
    int bdFlag;
	Program *curProgram;
	unsigned char enablePid[NB_PID_MAX];/*0被丢弃，1被解析*/
	
    int cdxStreamEos;
	enum CdxParserStatus status;
    pthread_mutex_t statusLock;
    pthread_cond_t cond;
	cdx_uint32 attribute;
	cdx_uint32 forceStop;
    int mErrno;
	CdxMediaInfoT mediaInfo;
    CdxPacketT pkt;
    int sizeToReadEverytime;

    int needSelectProgram;
    int needUpdateProgram;

    int miracast;
    struct HdcpOpsS *hdcpOps;
    void *hdcpHandle;

    ParserCallback callback;
    void *pUserData;
    int endPosFlag;

    VideoStreamInfo tempVideoInfo;
	cdx_int64 firstPts;
	int mIslastSegment;
};

struct ProgramS
{
    struct CdxListNodeS node;
    int mProgramNumber;
    int mProgramMapPID;
    unsigned version_number;
	
    struct CdxListS mStreams;
    unsigned mStreamCount;

	unsigned audioCount;
	unsigned videoCount;//目前从流也算入Count
	unsigned subsCount;
	
	TSParser *mTSParser;

	cdx_int64 mFirstPTS;
	//cdx_bool mFirstPTSValid;//
	int format;/*0普通ts，1HDMV*/
	int existInNewPat;
	
};
typedef struct PesS PES;
typedef struct AccessUnitS AccessUnit;
struct PesS
{
    CdxBufferT *mBuffer;
	cdx_int64 pts;
	//cdx_int64 durationUs;
	
	const cdx_uint8 *ESdata;
	size_t size;

	const cdx_uint8 *AUdata;
};
struct AccessUnitS
{
    //PES *head;
    //PES *tail;
	const cdx_uint8 *data;
	size_t size;
	cdx_int64 pts;
	cdx_int64 mTimestampUs;
	cdx_int64 durationUs;
};
typedef struct AudioMetaDataS AudioMetaData;
struct AudioMetaDataS
{
    int channelNum;
    int samplingFrequency;
    int bitPerSample;
	int bitRate;
	int maxBitRate;
};
typedef struct VideoMetaDataS VideoMetaData;
struct VideoMetaDataS
{
    int videoFormat;
    int frameRate;
    int aspectRatio;
    int ccFlag;

    unsigned width;
    unsigned height;
    unsigned bitRate;
};
struct StreamS
{
    struct CdxListNodeS node;
	Program *mProgram;
	unsigned mElementaryPID;
	int mStreamType;
    int streamIndex;
    
	//unsigned mPCR_PID;
	int mExpectedContinuityCounter;

	//CdxBufferT *mBuffer[2];//PES
	PES pes[2];
	int pesIndex;//标记当前要parse的pes,解完后反转
    cdx_uint8 *tmpBuf;
    unsigned tmpDataSize;
    AccessUnit accessUnit;
    cdx_bool accessUnitStarted;
    
	cdx_bool mPayloadStarted;
	char lang[4];
	MediaType mMediaType;
	unsigned codec_id;
	unsigned codec_sub_id;
    void *metadata;
	cdx_uint64 preAudioFrameDuration;

	int hasFirstPTS;
	cdx_uint64 firstPTS;
	
	int counter;
	//cdx_uint64 mPrevPTS;

    cdx_uint8 privateData[PES_PRIVATE_DATA_SIZE];
    int hdcpEncrypted;

    int existInNewPmt;

#if PROBE_STREAM    
    cdx_uint8 *probeBuf;
    unsigned probeBufSize;
    unsigned probeDataSize;
#endif
	
};

#endif

