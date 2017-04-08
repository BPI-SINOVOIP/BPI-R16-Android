#ifndef CDX_RTMP_STREAM_H
#define CDX_RTMP_STREAM_H
#include<pthread.h>
#include<sys/types.h>
#include<unistd.h>
#include<stdio.h>
#include<string.h>
#include <stdlib.h>
#include <CdxStream.h>
#include <CdxAtomic.h>
//#include "stream.h"


#ifdef __cplusplus
extern "C" {
#endif


#if !HAVE_CLOSESOCKET
    #define closesocket close
#endif

    
//***************************************************************************************//
   
#define RTMP_CHANNELS 65600
#define RTMP_MAX_HEADER_SIZE 18
#define RTMP_BUFFER_CACHE_SIZE (16*1024)
#define RTMP_SWF_HASHLEN 32

#define RTMP_FEATURE_HTTP	0x01
#define RTMP_FEATURE_ENC	0x02
#define RTMP_FEATURE_SSL	0x04
#define RTMP_FEATURE_MFP	0x08	/* not yet supported */
#define RTMP_FEATURE_WRITE	0x10	/* publish, not play */
#define RTMP_FEATURE_HTTP2	0x20	/* server-side rtmpt */

#define RTMP_PROTOCOL_UNDEFINED	-1
#define RTMP_PROTOCOL_RTMP      0
#define RTMP_PROTOCOL_RTMPE     RTMP_FEATURE_ENC
#define RTMP_PROTOCOL_RTMPT     RTMP_FEATURE_HTTP
#define RTMP_PROTOCOL_RTMPS     RTMP_FEATURE_SSL
#define RTMP_PROTOCOL_RTMPTE    (RTMP_FEATURE_HTTP|RTMP_FEATURE_ENC)
#define RTMP_PROTOCOL_RTMPTS    (RTMP_FEATURE_HTTP|RTMP_FEATURE_SSL)
#define RTMP_PROTOCOL_RTMFP     RTMP_FEATURE_MFP

 typedef struct RTMP_CHUNK
 {
    int      c_headerSize;         /* chunk header size(basic header, message header, extend time stamp)*/
    int      c_chunkSize;		   /* chunk entry size */
    char*    c_chunk;			   /* chunk entry data */
    char     c_header[RTMP_MAX_HEADER_SIZE];
 }aw_rtmp_chunk_t;
      
 typedef struct RTMP_PACKET
 {    
    int                   m_nChannel;                     /*chunk stream id*/
    int                   m_nInfoField2;                  /* message stream id ,last 4 bytes in a long header */
    unsigned char 		  m_headerType;         		  /*basic header format,第一个字节的高两位*/
    unsigned char 		  m_packetType;         		  /*message(AMF) type id*/
    unsigned char 		  m_hasAbsTimestamp;   			  /* timestamp absolute  */
            
    unsigned int 		  m_nTimeStamp;          		  /* timestamp */
    unsigned int 		  m_nBodySize;                    /*message length*/
    unsigned int 		  m_nBytesRead;
    aw_rtmp_chunk_t*      m_chunk;
    char*                 m_body;                         /* pointer to chunk body*/
}aw_rtmp_packet_t;
  
 typedef struct AVal
 {
    char *av_val;
    int av_len;
 }aw_rtmp_aval_t;  
 
 typedef struct RTMP_METHOD
 {
    aw_rtmp_aval_t name;
    int num;
 }aw_rtmp_method_t;
 
 /* state for read() wrapper */
 typedef struct RTMP_READ
 {    
   // char status;
    unsigned int 		timestamp;
    unsigned char 		dataType;     /* 0x04: audio, 0x01: vedio */
    unsigned char 		flags;        /* the flag to add "FLV" header tag(9bytes) to client data buffer */
    unsigned int 		nResumeTS;
 }aw_rtmp_read_t;
 
       
 typedef struct RTMP_SOCKET_BUF
 {
    int 		sb_socket;
    int 		sb_size;        /* number of unprocessed bytes in buffer */
    char*		sb_start;     /* pointer into sb_pBuffer of next byte to process */
    char 		sb_buf[RTMP_BUFFER_CACHE_SIZE];    /* data read from socket */
    int 		sb_timedout;
    void*		sb_ssl;
}aw_rtmp_socket_buf_t;

 struct AMFObjectProperty;

typedef struct AMFObject
{
    int o_num;
    struct AMFObjectProperty *o_props;
} aw_amf_object_t;

typedef enum
{
    AMF_NUMBER = 0,
    AMF_BOOLEAN,
    AMF_STRING,
    AMF_OBJECT,
    AMF_MOVIECLIP,      /* reserved, not used */
    AMF_NULL,
    AMF_UNDEFINED,
    AMF_REFERENCE,
    AMF_ECMA_ARRAY,
    AMF_OBJECT_END,
    AMF_STRICT_ARRAY,
    AMF_DATE,
    AMF_LONG_STRING,
    AMF_UNSUPPORTED,
    AMF_RECORDSET,      /* reserved, not used */
    AMF_XML_DOC,
    AMF_TYPED_OBJECT,
    AMF_AVMPLUS,        /* switch to AMF3 */
    AMF_INVALID = 0xff
} aw_amf_type_e;


typedef struct AMFObjectProperty
{
    aw_rtmp_aval_t p_name;
    aw_amf_type_e p_type;
    union
    {
        int p_number;
        aw_rtmp_aval_t p_aval;
        aw_amf_object_t p_object;
    } p_vu;
    short p_UTCoffset;
} aw_amfobject_property_t;

typedef struct RTMP_LNK
{
    aw_rtmp_aval_t 		hostname;
    aw_rtmp_aval_t 		sockshost;
    
    aw_rtmp_aval_t 		playpath0; /* parsed from URL */
    aw_rtmp_aval_t 		playpath;  /* passed in explicitly */
    aw_rtmp_aval_t 		tcUrl;
    aw_rtmp_aval_t 		swfUrl;
    aw_rtmp_aval_t 		pageUrl;
    aw_rtmp_aval_t 		app;
    aw_rtmp_aval_t 		auth;
    aw_rtmp_aval_t 		flashVer;
    aw_rtmp_aval_t 		subscribepath;
    aw_rtmp_aval_t 		usherToken;
    aw_rtmp_aval_t 		token;
    aw_amf_object_t 	extras;
    int 				edepth;
    
    int 				seekTime;      /* the seek time with "play" command , play resume from here */
    int 				stopTime;
    
    int 				lFlags;
    
    int 				swfAge;
    
    int 				protocol;
    int 				timeout;        /* connection timeout in seconds */
    
    unsigned short 		socksport;
    unsigned short 		port;
} aw_rtmp_link_t; 
        

#define RTMP_READ_HEADER	0x01
#define RTMP_READ_RESUME	0x02
#define RTMP_READ_NO_IGNORE	0x04
#define RTMP_READ_GOTKF		0x08
#define RTMP_READ_GOTFLVK	0x10
#define RTMP_READ_SEEKING	0x20
#define RTMP_READ_COMPLETE	-3
#define RTMP_READ_ERROR	-2
#define RTMP_READ_EOF	-1
#define RTMP_READ_IGNORE	0

typedef struct RTMP_PARAM
{   
    char pBuffer[1024];
    aw_rtmp_aval_t AV_empty;
    aw_amfobject_property_t AMFProp_Invalid;
    aw_rtmp_aval_t av_app;
    aw_rtmp_aval_t av_connect;
    aw_rtmp_aval_t av_flashVer;
    aw_rtmp_aval_t av_swfUrl;
    aw_rtmp_aval_t av_pageUrl;
    aw_rtmp_aval_t av_tcUrl;
    aw_rtmp_aval_t av_fpad;
    aw_rtmp_aval_t av_capabilities;
    aw_rtmp_aval_t av_audioCodecs;
    aw_rtmp_aval_t av_videoCodecs;
    aw_rtmp_aval_t av_videoFunction;
    aw_rtmp_aval_t av_objectEncoding;
    aw_rtmp_aval_t av_secureToken;
    aw_rtmp_aval_t av_secureTokenResponse;
    aw_rtmp_aval_t av_type;
    aw_rtmp_aval_t av_nonprivate;
    aw_rtmp_aval_t av_pause;
    aw_rtmp_aval_t av__checkbw;
    aw_rtmp_aval_t av__result;
    aw_rtmp_aval_t av_ping;
    aw_rtmp_aval_t av_pong;
    aw_rtmp_aval_t av_play;
    aw_rtmp_aval_t av_set_playlist;
    aw_rtmp_aval_t av_0;
    aw_rtmp_aval_t av_onBWDone;
    aw_rtmp_aval_t av_onFCSubscribe;
    aw_rtmp_aval_t av_onFCUnsubscribe;
    aw_rtmp_aval_t av__onbwcheck;
    aw_rtmp_aval_t av__onbwdone;
    aw_rtmp_aval_t av__error;
    aw_rtmp_aval_t av_close;
    aw_rtmp_aval_t av_code;
    aw_rtmp_aval_t av_level;
    aw_rtmp_aval_t av_onStatus;
    aw_rtmp_aval_t av_playlist_ready;
    aw_rtmp_aval_t av_onMetaData;
    aw_rtmp_aval_t av_duration;
    aw_rtmp_aval_t av_video;
    aw_rtmp_aval_t av_audio;
    aw_rtmp_aval_t av_FCPublish;
    aw_rtmp_aval_t av_FCUnpublish;
    aw_rtmp_aval_t av_releaseStream;
    aw_rtmp_aval_t av_publish;
    aw_rtmp_aval_t av_live;
    aw_rtmp_aval_t av_record;
    aw_rtmp_aval_t av_seek;
    aw_rtmp_aval_t av_createStream;
    aw_rtmp_aval_t av_FCSubscribe;
    aw_rtmp_aval_t av_deleteStream;
    aw_rtmp_aval_t av_NetStream_Authenticate_UsherToken;
    aw_rtmp_aval_t av_NetStream_Failed;
    aw_rtmp_aval_t av_NetStream_Play_Failed;
    aw_rtmp_aval_t av_NetStream_Play_StreamNotFound;
    aw_rtmp_aval_t av_NetConnection_Connect_InvalidApp;
    aw_rtmp_aval_t av_NetStream_Play_Start;
    aw_rtmp_aval_t av_NetStream_Play_Complete;
    aw_rtmp_aval_t av_NetStream_Play_Stop;
    aw_rtmp_aval_t av_NetStream_Seek_Notify;
    aw_rtmp_aval_t av_NetStream_Pause_Notify;
    aw_rtmp_aval_t av_NetStream_Play_PublishNotify;
    aw_rtmp_aval_t av_NetStream_Play_UnpublishNotify;
    aw_rtmp_aval_t av_NetStream_Publish_Start;
}av_rtmp_param_t;   /* RTMP Command message */


#define MAX_STREAM_BUF_SIZE 12*1024*1024    /* client buffer size, set in RTMPStreamOpen*/

typedef struct RTMP
{
	CdxStreamT           streaminfo;
	CdxStreamProbeDataT  probeData;
	CdxDataSourceT*      dataSource;

	cdx_atomic_t         mState;
	pthread_mutex_t      lock;
    pthread_cond_t       cond;

	int64_t              fileSize;           //* return value of Size()
	int64_t              buf_pos;            //* stream data is buffered to this file pos.
    int                  eof;                 /* all stream data is read to buffer.*/
    int 				 iostate;            /*IOState*/
	int                  isLiveStream;         /* 1: live stream; */
    char*                bufReadPtr;         // the read pointer of the buffer
    char*                bufWritePtr;        // the write pointer of the buffer
    char*                bufEndPtr;          /* pointer to the end of client buffer */
    char*   			     bufReleasePtr;      /* pointer to the last read postion */
    char*   			     buffer;             /* rtmp client buffer */
    char*    		     flvDataBuffer;      /* (128kb defalt)recv data from network and add flv header , then store in this buffer */
    int                  exitFlag;
    int                  forceStop;          /* forcestop read before seek  */
    int                  validDataSize;      /* size can be red data in rtmp client buffer*/
    pthread_mutex_t      bufferMutex;
    int64_t              timeStamp;           /* curret play timestamp ,used for seek,because the timestamp of aw_send_seek is the offset time */

    int64_t              dmx_read_pos;        /* (seek)demuxer read the stream to this file pos.*/

    int                  stream_buf_size;     /* cache size, set in RtmpStreamOPen(), default value is MAX_STREAM_BUF_SIZE */

    av_rtmp_param_t      rtmpParam;
    int                  m_inChunkSize;       /* read chunk size (128 bytes default) (server to client)*/
    int                  m_outChunkSize;      /* send chunk size (128 bytes default) (client send command to server)*/

    int                  m_nBWCheckCounter;   /* used for calculate time stamp of send message to server */
    int                  m_nBytesIn;          /* recieve data size from server,used for send Acknownledge size command to server */
    int                  m_nBytesInSent;      /* record last time m_nBytesIn */
    int                  m_nBufferMS;         /* Buffer time of the client. in millisecond */
    int                  m_stream_id;         /* returned in _result from createStream */
    int                  m_mediaChannel;      /* media stream id  */
    unsigned int         m_mediaStamp;        /* media time stamp, read from packet time stamp   */
    unsigned int         m_pauseStamp;		  /* the time stamp of pause */
    int                  m_pausing;		      /* 1: pause,  2: stream eof, 3: unkown(User control Message Event=31, unkown) */
    int                  m_nServerBW;         /* server bandwith */
    int                  m_nClientBW;
    unsigned char        m_nClientBW2;
    unsigned char        m_bPlaying;           /* server send "play" */

    unsigned char        m_bSendCounter;      /**/

    int                  m_numInvokes;         /* command transcation id ( 一般为0)*/
    int                  m_numCalls;
    aw_rtmp_method_t*    m_methodCalls;        /* remote method calls queue */

    aw_rtmp_packet_t*    m_vecChannelsIn[RTMP_CHANNELS];
    aw_rtmp_packet_t*    m_vecChannelsOut[RTMP_CHANNELS];
    int                  m_channelTimestamp[RTMP_CHANNELS];  /* absolute timestamp of every packet with different stream id */

    double               m_fAudioCodecs;     /* audioCodecs for the connect packet */
    double               m_fVideoCodecs;     /* videoCodecs for the connect packet */
    double               m_fEncoding;        /* AMF0 or AMF3 */
    unsigned char        m_bSendEncoding;

    double               m_fDuration;        /* the "duration" in metadata ,duration of stream in seconds */

    int                  m_msgCounter;       /*(not used) http post header, RTMPT stuff */

    int                  m_resplen;
    int                  m_unackd;
    aw_rtmp_aval_t       m_clientID;         /* (is not used) needed by http post header*/

    aw_rtmp_read_t       m_read;
    aw_rtmp_packet_t     m_write;            /* chuank data */
    aw_rtmp_socket_buf_t m_sb;               /* socket */
    aw_rtmp_link_t       Link;               /* parser url */
} aw_rtmp_t;


#ifndef SIZE_MAX
#define SIZE_MAX ((size_t)-1)
#endif
   
#ifdef __cplusplus
}
#endif

#endif

