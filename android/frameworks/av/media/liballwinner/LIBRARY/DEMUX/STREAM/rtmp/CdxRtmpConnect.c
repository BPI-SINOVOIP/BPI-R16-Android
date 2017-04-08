//* open the macro definition of LOG_NDEBUG if you want the printed message of this module.
//#define LOG_NDEBUG 0
#define LOG_TAG "CdxRtmpConnect.c:"       //* prefix of the printed messages.                    //* include for printing message.
#include "CdxRtmpStream.h"
#include "CdxRtmpBytes.h"
#include <errno.h>
#include <unistd.h>
#include <ctype.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/time.h>
#include <netinet/tcp.h>


/* most of the time closing a socket is just closing an fd */

static int WriteN(aw_rtmp_t *r, char *buffer, int n);
void aw_rtmp_close(aw_rtmp_t *r);
char *aw_amf_prop_encode(aw_amfobject_property_t  *prop, char *pBuffer, char *pBufEnd);
int aw_amf_prop_decode(aw_rtmp_t*r, aw_amfobject_property_t *prop, char *pBuffer, int nSize,int bDecodeName);
int aw_amf3_decode(aw_rtmp_t*r, aw_amf_object_t *obj, char *pBuffer, int nSize, int bAMFData);
int aw_amf_decode(aw_rtmp_t*r,aw_amf_object_t *obj, const char *pBuffer, int nSize, int bDecodeName);
void aw_amf_prop_dump(aw_amfobject_property_t *prop);
void aw_amf_prop_reset(aw_amfobject_property_t *prop);

extern void aw_amf_add_prop(aw_amf_object_t *obj, aw_amfobject_property_t *prop);

//***************************************************************************************//
//***************************************************************************************//
#define TRUE 1
#define FALSE 0
#define AVC(str)	{str,sizeof(str)-1}

void aw_set_rtmp_parameter(aw_rtmp_t* r)
{   
    
    av_rtmp_param_t* p = NULL;
    char *startPtr = NULL;
    int len = 0;
    
    p = &(r->rtmpParam);
    startPtr = p->pBuffer;
   
    len = sizeof("app");
    p->av_app.av_val = startPtr;
    p->av_app.av_len = len-1;
    strncpy(p->av_app.av_val, "app", len);
    startPtr += len;

 
    len = sizeof("connect");
    p->av_connect.av_val = startPtr;
    p->av_connect.av_len = len-1;
    strncpy(p->av_connect.av_val, "connect", len);
    startPtr += len;
    

    len = sizeof("flashVer");
    p->av_flashVer.av_val = startPtr;
    p->av_flashVer.av_len = len-1;
    strncpy(p->av_flashVer.av_val, "flashVer", len);
    startPtr += len;

    len = sizeof("swfUrl");
    p->av_swfUrl.av_val = startPtr;
    p->av_swfUrl.av_len = len-1;
    strncpy(p->av_swfUrl.av_val, "swfUrl", len);
    startPtr += len;

    len = sizeof("pageUrl");
    p->av_pageUrl.av_val = startPtr;
    p->av_pageUrl.av_len = len-1;
    strncpy(p->av_pageUrl.av_val, "pageUrl", len);
    startPtr += len;
    
    len = sizeof("tcUrl");
    p->av_tcUrl.av_val = startPtr;
    p->av_tcUrl.av_len = len-1;
    strncpy(p->av_tcUrl.av_val, "tcUrl", len);
    startPtr += len;
    

    len = sizeof("fpad");
    p->av_fpad.av_val = startPtr;
    p->av_fpad.av_len = len-1;
    strncpy(p->av_fpad.av_val, "fpad", len);
    startPtr += len;

    len = sizeof("capabilities");
    p->av_capabilities.av_val = startPtr;
    p->av_capabilities.av_len = len-1;
    strncpy(p->av_capabilities.av_val, "capabilities", len);
    startPtr += len;

    len = sizeof("audioCodecs");
    p->av_audioCodecs.av_val = startPtr;
    p->av_audioCodecs.av_len = len-1;
    strncpy(p->av_audioCodecs.av_val, "audioCodecs", len);
    startPtr += len;
    
    len = sizeof("videoCodecs");
    p->av_videoCodecs.av_val = startPtr;
    p->av_videoCodecs.av_len = len-1;
    strncpy(p->av_videoCodecs.av_val, "videoCodecs", len);
    startPtr += len;
    

    len = sizeof("videoFunction");
    p->av_videoFunction.av_val = startPtr;
    p->av_videoFunction.av_len = len-1;
    strncpy(p->av_videoFunction.av_val, "videoFunction", len);
    startPtr += len;
    
   
    len = sizeof("objectEncoding");
    p->av_objectEncoding.av_val = startPtr;
    p->av_objectEncoding.av_len = len-1;
    strncpy(p->av_objectEncoding.av_val, "objectEncoding", len);
    startPtr += len;
    
    len = sizeof("secureToken");
    p->av_secureToken.av_val = startPtr;
    p->av_secureToken.av_len = len-1;
    strncpy(p->av_secureToken.av_val, "secureToken", len);
    startPtr += len;

    len = sizeof("secureTokenResponse");
    p->av_secureTokenResponse.av_val = startPtr;
    p->av_secureTokenResponse.av_len = len-1;
    strncpy(p->av_secureTokenResponse.av_val, "secureTokenResponse", len);
    startPtr += len;

    len = sizeof("type");
    p->av_type.av_val = startPtr;
    p->av_type.av_len = len-1;
    strncpy(p->av_type.av_val, "type", len);
    startPtr += len;
    

    len = sizeof("nonprivate");
    p->av_nonprivate.av_val = startPtr;
    p->av_nonprivate.av_len = len-1;
    strncpy(p->av_nonprivate.av_val, "nonprivate", len);
    startPtr += len;
    
  
    len = sizeof("pause");
    p->av_pause.av_val = startPtr;
    p->av_pause.av_len = len-1;
    strncpy(p->av_pause.av_val, "pause", len);
    startPtr += len;


    len = sizeof("_checkbw");
    p->av__checkbw.av_val = startPtr;
    p->av__checkbw.av_len = len-1;
    strncpy(p->av__checkbw.av_val, "_checkbw", len);
    startPtr += len;
    

    len = sizeof("_result");
    p->av__result.av_val = startPtr;
    p->av__result.av_len = len-1;
    strncpy(p->av__result.av_val, "_result", len);
    startPtr += len;
    

    len = sizeof("ping");
    p->av_ping.av_val = startPtr;
    p->av_ping.av_len = len-1;
    strncpy(p->av_ping.av_val, "ping", len);
    startPtr += len;

    len = sizeof("pong");
    p->av_pong.av_val = startPtr;
    p->av_pong.av_len = len-1;
    strncpy(p->av_pong.av_val, "pong", len);
    startPtr += len;

    len = sizeof("play");
    p->av_play.av_val = startPtr;
    p->av_play.av_len = len-1;
    strncpy(p->av_play.av_val, "play", len);
    startPtr += len;
    

    len = sizeof("set_playlist");
    p->av_set_playlist.av_val = startPtr;
    p->av_set_playlist.av_len = len-1;
    strncpy(p->av_set_playlist.av_val, "set_playlist", len);
    startPtr += len;

    len = sizeof("0");
    p->av_0.av_val = startPtr;
    p->av_0.av_len = len-1;
    strncpy(p->av_0.av_val, "0", len);
    startPtr += len;

    len = sizeof("onBWDone");
    p->av_onBWDone.av_val = startPtr;
    p->av_onBWDone.av_len = len-1;
    strncpy(p->av_onBWDone.av_val, "onBWDone", len);
    startPtr += len;

    len = sizeof("onFCSubscribe");
    p->av_onFCSubscribe.av_val = startPtr;
    p->av_onFCSubscribe.av_len = len-1;
    strncpy(p->av_onFCSubscribe.av_val, "onFCSubscribe", len);
    startPtr += len;
    
    len = sizeof("onFCUnsubscribe");
    p->av_onFCUnsubscribe.av_val = startPtr;
    p->av_onFCUnsubscribe.av_len = len-1;
    strncpy(p->av_onFCUnsubscribe.av_val, "onFCUnsubscribe", len);
    startPtr += len;

    len = sizeof("onFCUnsubscribe");
    p->av_onFCUnsubscribe.av_val = startPtr;
    p->av_onFCUnsubscribe.av_len = len-1;
    strncpy(p->av_onFCUnsubscribe.av_val, "onFCUnsubscribe", len);
    startPtr += len;

    len = sizeof("_onbwcheck");
    p->av__onbwcheck.av_val = startPtr;
    p->av__onbwcheck.av_len = len-1;
    strncpy(p->av__onbwcheck.av_val, "_onbwcheck", len);
    startPtr += len;

    len = sizeof("_onbwdone");
    p->av__onbwdone.av_val = startPtr;
    p->av__onbwdone.av_len = len-1;
    strncpy(p->av__onbwdone.av_val, "_onbwdone", len);
    startPtr += len;
   
    len = sizeof("_error");
    p->av__error.av_val = startPtr;
    p->av__error.av_len = len-1;
    strncpy(p->av__error.av_val, "_error", len);
    startPtr += len;

    len = sizeof("close");
    p->av_close.av_val = startPtr;
    p->av_close.av_len = len-1;
    strncpy(p->av_close.av_val, "close", len);
    startPtr += len;

    len = sizeof("code");
    p->av_code.av_val = startPtr;
    p->av_code.av_len = len-1;
    strncpy(p->av_code.av_val, "code", len);
    startPtr += len;

    len = sizeof("level");
    p->av_level.av_val = startPtr;
    p->av_level.av_len = len-1;
    strncpy(p->av_level.av_val, "level", len);
    startPtr += len;

    len = sizeof("onStatus");
    p->av_onStatus.av_val = startPtr;
    p->av_onStatus.av_len = len-1;
    strncpy(p->av_onStatus.av_val, "onStatus", len);
    startPtr += len;
    

    len = sizeof("playlist_ready");
    p->av_playlist_ready.av_val = startPtr;
    p->av_playlist_ready.av_len = len-1;
    strncpy(p->av_playlist_ready.av_val, "playlist_ready", len);
    startPtr += len;

    len = sizeof("onMetaData");
    p->av_onMetaData.av_val = startPtr;
    p->av_onMetaData.av_len = len-1;
    strncpy(p->av_onMetaData.av_val, "onMetaData", len);
    startPtr += len;
 
    len = sizeof("duration");
    p->av_duration.av_val = startPtr;
    p->av_duration.av_len = len-1;
    strncpy(p->av_duration.av_val, "duration", len);
    startPtr += len;

    len = sizeof("video");
    p->av_video.av_val = startPtr;
    p->av_video.av_len = len-1;
    strncpy(p->av_video.av_val, "video", len);
    startPtr += len;

    len = sizeof("audio");
    p->av_audio.av_val = startPtr;
    p->av_audio.av_len = len-1;
    strncpy(p->av_audio.av_val, "audio", len);
    startPtr += len;

    len = sizeof("audio");
    p->av_audio.av_val = startPtr;
    p->av_audio.av_len = len-1;
    strncpy(p->av_audio.av_val, "audio", len);
    startPtr += len;

    len = sizeof("FCPublish");
    p->av_FCPublish.av_val = startPtr;
    p->av_FCPublish.av_len = len-1;
    strncpy(p->av_FCPublish.av_val, "FCPublish", len);
    startPtr += len;
    

    len = sizeof("FCUnpublish");
    p->av_FCUnpublish.av_val = startPtr;
    p->av_FCUnpublish.av_len = len-1;
    strncpy(p->av_FCUnpublish.av_val, "FCUnpublish", len);
    startPtr += len;

    len = sizeof("releaseStream");
    p->av_releaseStream.av_val = startPtr;
    p->av_releaseStream.av_len = len-1;
    strncpy(p->av_releaseStream.av_val, "releaseStream", len);
    startPtr += len;

    len = sizeof("publish");
    p->av_publish.av_val = startPtr;
    p->av_publish.av_len = len-1;
    strncpy(p->av_publish.av_val, "publish", len);
    startPtr += len;

    len = sizeof("live");
    p->av_live.av_val = startPtr;
    p->av_live.av_len = len-1;
    strncpy(p->av_live.av_val, "live", len);
    startPtr += len;

    len = sizeof("record");
    p->av_record.av_val = startPtr;
    p->av_record.av_len = len-1;
    strncpy(p->av_record.av_val, "record", len);
    startPtr += len;

    len = sizeof("seek");
    p->av_seek.av_val = startPtr;
    p->av_seek.av_len = len-1;
    strncpy(p->av_seek.av_val, "seek", len);
    startPtr += len;

    len = sizeof("createStream");
    p->av_createStream.av_val = startPtr;
    p->av_createStream.av_len = len-1;
    strncpy(p->av_createStream.av_val, "createStream", len);
    startPtr += len;

    len = sizeof("FCSubscribe");
    p->av_FCSubscribe.av_val = startPtr;
    p->av_FCSubscribe.av_len = len-1;
    strncpy(p->av_FCSubscribe.av_val, "FCSubscribe", len);
    startPtr += len;

    len = sizeof("deleteStream");
    p->av_deleteStream.av_val = startPtr;
    p->av_deleteStream.av_len = len-1;
    strncpy(p->av_deleteStream.av_val, "deleteStream", len);
    startPtr += len;
    
    len = sizeof("NetStream.Authenticate.NetStream.Failed");
    p->av_NetStream_Authenticate_UsherToken.av_val = startPtr;
    p->av_NetStream_Authenticate_UsherToken.av_len = len-1;
    strncpy(p->av_NetStream_Authenticate_UsherToken.av_val, "NetStream.Authenticate.UsherToken", len);
    startPtr += len;

    len = sizeof("NetStream.Failed");
    p->av_NetStream_Failed.av_val = startPtr;
    p->av_NetStream_Failed.av_len = len-1;
    strncpy(p->av_NetStream_Failed.av_val, "NetStream.Failed", len);
    startPtr += len;

    len = sizeof("NetStream.Play.Failed");
    p->av_NetStream_Play_Failed.av_val = startPtr;
    p->av_NetStream_Play_Failed.av_len = len-1;
    strncpy(p->av_NetStream_Play_Failed.av_val, "NetStream.Play.Failed", len);
    startPtr += len;
    
    len = sizeof("NetStream.Play.StreamNotFound");
    p->av_NetStream_Play_StreamNotFound.av_val = startPtr;
    p->av_NetStream_Play_StreamNotFound.av_len = len-1;
    strncpy(p->av_NetStream_Play_StreamNotFound.av_val, "NetStream.Play.StreamNotFound", len);
    startPtr += len;

    len = sizeof("NetConnection.Connect.InvalidApp");
    p->av_NetConnection_Connect_InvalidApp.av_val = startPtr;
    p->av_NetConnection_Connect_InvalidApp.av_len = len-1;
    strncpy(p->av_NetConnection_Connect_InvalidApp.av_val, "NetConnection.Connect.InvalidApp", len);
    startPtr += len;

    len = sizeof("NetStream.Play.Start");
    p->av_NetStream_Play_Start.av_val = startPtr;
    p->av_NetStream_Play_Start.av_len = len-1;
    strncpy(p->av_NetStream_Play_Start.av_val, "NetStream.Play.Start", len);
    startPtr += len;
    
    len = sizeof("NetStream.Play.Complete");
    p->av_NetStream_Play_Complete.av_val = startPtr;
    p->av_NetStream_Play_Complete.av_len = len-1;
    strncpy(p->av_NetStream_Play_Complete.av_val, "NetStream.Play.Complete", len);
    startPtr += len;
        
    len = sizeof("NetStream.Play.Stop");
    p->av_NetStream_Play_Stop.av_val = startPtr;
    p->av_NetStream_Play_Stop.av_len = len-1;
    strncpy(p->av_NetStream_Play_Stop.av_val, "NetStream.Play.Stop", len);
    startPtr += len;

    len = sizeof("NetStream.Seek.Notify");
    p->av_NetStream_Seek_Notify.av_val = startPtr;
    p->av_NetStream_Seek_Notify.av_len = len-1;
    strncpy(p->av_NetStream_Seek_Notify.av_val, "NetStream.Seek.Notify", len);
    startPtr += len;

    len = sizeof("NetStream.Pause.Notify");
    p->av_NetStream_Pause_Notify.av_val = startPtr;
    p->av_NetStream_Pause_Notify.av_len = len-1;
    strncpy(p->av_NetStream_Pause_Notify.av_val, "NetStream.Pause.Notify", len);
    startPtr += len;

    len = sizeof("NetStream.Play.PublishNotify");
    p->av_NetStream_Play_PublishNotify.av_val = startPtr;
    p->av_NetStream_Play_PublishNotify.av_len = len-1;
    strncpy(p->av_NetStream_Play_PublishNotify.av_val, "NetStream.Play.PublishNotify", len);
    startPtr += len;

    len = sizeof("NetStream.Play.UnpublishNotify");
    p->av_NetStream_Play_UnpublishNotify.av_val = startPtr;
    p->av_NetStream_Play_UnpublishNotify.av_len = len-1;
    strncpy(p->av_NetStream_Play_UnpublishNotify.av_val, "NetStream.Play.UnpublishNotify", len);
    startPtr += len;

    len = sizeof("NetStream.Publish.Start");
    p->av_NetStream_Publish_Start.av_val = startPtr;
    p->av_NetStream_Publish_Start.av_len = len-1;
    strncpy(p->av_NetStream_Publish_Start.av_val, "NetStream.Publish.Start", len);
    startPtr += len;
}

static int aw_add_addr_info(struct sockaddr_in *service, aw_rtmp_aval_t *host, int port)
{
    char *hostname;
    int ret = 1;
    
    /* if host is not end with '\0', add it */
    if(host->av_val[host->av_len])
    {
        hostname = malloc(host->av_len+1);
        memcpy(hostname, host->av_val, host->av_len);
        hostname[host->av_len] = '\0';
    }
    else
    {
        hostname = host->av_val;
    }

    service->sin_addr.s_addr = inet_addr(hostname);  /*将点分十进制字字符串转换为32位二进制网络字节序的IPV4地址*/
    if(service->sin_addr.s_addr == INADDR_NONE)
    {
        struct hostent *host = gethostbyname(hostname); /*返回对应于给定主机名的包含主机名字和地址信息的hostent结构指针*/
        if(host == NULL || host->h_addr == NULL)
	    {
	        ret = 0;
	        goto finish;
	    }
        service->sin_addr = *(struct in_addr *)host->h_addr;
    }
    service->sin_port = htons(port);  /*主机字节顺序转化为网络字节顺序*/
    
finish:
    if(hostname != host->av_val)
    {
        free(hostname);
        hostname = NULL;
    }
    return ret;
}

//******************************************************************************************//
//******************************************************************************************//
typedef enum 
{
    RTMPT_OPEN=0, RTMPT_SEND, RTMPT_IDLE, RTMPT_CLOSE
}aw_rtmp_cmd;

static const char *RTMPT_cmds[] = {
  "open",
  "send",
  "idle",
  "close"
};

int aw_rtmp_sockBuf_send(aw_rtmp_socket_buf_t*sb, char *buf, int len, int exitflag)
{

    int rc;
    fd_set fdset;
    int sendSize = 0;
    struct timeval tv;
    int ret;

    while(1)
    {
    	if(exitflag == 1)
    		return -1;

    	FD_ZERO(&fdset);
    	FD_SET(sb->sb_socket, &fdset);

    	tv.tv_sec = 0;
    	tv.tv_usec = 100000;
    	ret = select(sb->sb_socket+1, NULL, &fdset, NULL, &tv);
    	if(ret <= 0)
    	{
    		continue;
    	}

    	while(1)
    	{
    		if(exitflag == 1)
    			return -1;

    		rc = send(sb->sb_socket, ((char*)buf)+sendSize, len-sendSize, 0);
    		if(rc < 0)
    		{
    			if(EAGAIN == errno)
    			{
    				//buffer not ready
    				break;
    			}
    			else
    			{
    				return -1;
    			}
    		}
    		else if (rc ==0)
    		{
    			break;
    		}
    		else
    		{
    			sendSize += rc;
    			if(sendSize == len)
    			{
    				return sendSize;
    			}
    		}
    	}
    }
    return sendSize;

}

static int aw_rtmp_http_post(aw_rtmp_t *r, aw_rtmp_cmd cmd, char *buf, int len)
{
    char hbuf[512];
    /*http post*/
    int hlen = snprintf(hbuf, sizeof(hbuf), "POST /%s%s/%d HTTP/1.1\r\n"
                "Host: %.*s:%d\r\n"
                "Accept: */*\r\n"
                "User-Agent: Shockwave Flash\n"
                "Connection: Keep-Alive\n"
                "Cache-Control: no-cache\r\n"
                "Content-type: application/x-fcs\r\n"
                "Content-length: %d\r\n\r\n", RTMPT_cmds[cmd],
                r->m_clientID.av_val ? r->m_clientID.av_val : "",
                r->m_msgCounter, r->Link.hostname.av_len, r->Link.hostname.av_val,
                r->Link.port, len);
    aw_rtmp_sockBuf_send(&r->m_sb, hbuf, hlen, r->exitFlag);
    hlen = aw_rtmp_sockBuf_send(&r->m_sb, buf, len, r->exitFlag);
    r->m_msgCounter++;
    r->m_unackd++;
    return hlen;
}

//*******************************************************************************************//
//*******************************************************************************************/
ssize_t CdxRecv(int sockfd, void *buf, size_t len,
                        long timeoutUs/* microseconds */, int *pForceStop)
{
    fd_set rs;
    struct timeval tv;
    ssize_t ret = 0, recvSize = 0;
    long loopTimes = 0, i = 0;
    int ioErr;

    if (timeoutUs == 0)
    {
        loopTimes = ((unsigned long)(-1L))>> 1;
    }
    else
    {
        loopTimes = timeoutUs/100000L;
    }

    for (i = 0; i < loopTimes; i++)
    {
        if (pForceStop && *pForceStop)
        {
            return -1;
        }

        FD_ZERO(&rs);
        FD_SET(sockfd, &rs);
        tv.tv_sec = 0;
        tv.tv_usec = 100000L;
        ret = select(sockfd + 1, &rs, NULL, NULL, &tv);
        if (ret < 0)
        {
            ioErr = errno;
            if (EINTR == ioErr)
            {
                continue;
            }
            return -1;
        }
        else if (ret == 0)
        {
            //printf("select = 0; timeout\n");
            continue;
        }

        while (1)
        {
            if (pForceStop && *pForceStop)
            {
                return -1;
            }

            ret = recv(sockfd, ((char *)buf) + recvSize, len - recvSize, 0);
            if (ret < 0)
            {
                ioErr = errno;
                if (EAGAIN == ioErr /*|| EINTR == ioErr*/)
                {
                    //buffer not ready
                    if (recvSize > 0)
                    {
                        return recvSize;
                    }
                    break;
                }
                else
                {
                    return -1;
                }
            }
            else if (ret == 0)
            {
                //buffer not ready
                if (recvSize > 0)
                {
                    return recvSize;
                }
                break;
            }
            else // ret > 0
            {
                recvSize += ret;
                if ((size_t)recvSize == len)
                {
                    return recvSize;
                }
            }
        }

    }
    return recvSize;
}

//把socket buffer填满
int aw_rtmp_sockbuf_fill(aw_rtmp_socket_buf_t *sb, int *exitflag)
{
    int nBytes;
    int readLen = 0;
    
    if(!sb->sb_size)
    {
        sb->sb_start = sb->sb_buf;
    }

#if 1
    while (1)
    {
    	nBytes = sizeof(sb->sb_buf) - sb->sb_size - (sb->sb_start - sb->sb_buf);
    	readLen = CdxRecv(sb->sb_socket, sb->sb_start+sb->sb_size, nBytes, 0, exitflag);

    	if(readLen != -1)
    	{
    		sb->sb_size += readLen;
    	}
    	else
    	{
    		sb->sb_timedout = 1;
    		readLen = 0;
    	}
    	break;
    }

    return readLen;

#else
   	while (1)
    	{
   			if(exitflag == 1)
   				return -1;
			nBytes = sizeof(sb->sb_buf) - sb->sb_size
					- (sb->sb_start - sb->sb_buf);
			readLen = recv(sb->sb_socket, sb->sb_start + sb->sb_size, nBytes, 0); //* 接受数据存入socket缓存
			if (readLen != -1)
			{
				sb->sb_size += readLen;
			}
			else
			{
				sb->sb_timedout = 1;
				readLen = 0;
			}
			break;
		}
    return readLen;
#endif
}

//***************************************************************************************//
//***************************************************************************************//
static int aw_rtmp_http_read(aw_rtmp_t *r, int fill)
{
    char *ptr;
    int hlen;
    
    if(fill)
    {
        if(aw_rtmp_sockbuf_fill(&r->m_sb, &r->exitFlag)<0)
        	return -1;
    }
    
    if(r->m_sb.sb_size < 144)
    {
        return -2;
    }
    if(strncmp(r->m_sb.sb_start, "HTTP/1.1 200 ", 13))
    {
        return -1;
    }
    ptr = r->m_sb.sb_start + sizeof("HTTP/1.1 200");
    while((ptr = strstr(ptr, "Content-"))) 
    {
        if(!strncasecmp(ptr+8, "length:", 7))
        {
            break;
        }
        ptr += 8;
    }
    if(!ptr)
    {
        return -1;
    }

    hlen = atoi(ptr+16);
    ptr = strstr(ptr+16, "\r\n\r\n");
    if(!ptr)
    {
        return -1;
    }
    ptr += 4;
    r->m_sb.sb_size -= ptr - r->m_sb.sb_start;
    r->m_sb.sb_start = ptr;
    r->m_unackd--;

    if(!r->m_clientID.av_val)
    {
        r->m_clientID.av_len = hlen;
        r->m_clientID.av_val = malloc(hlen+1);
        if(!r->m_clientID.av_val)
        {
            return -1;
        }
        r->m_clientID.av_val[0] = '/';
        memcpy(r->m_clientID.av_val+1, ptr, hlen-1);
        r->m_clientID.av_val[hlen] = 0;
        r->m_sb.sb_size = 0;
    }
    else
    {
//        r->m_polling = *ptr++;
        r->m_resplen = hlen - 1;
        r->m_sb.sb_start++;
        r->m_sb.sb_size--;
    }
    return 0;
}

//*******************************************************************************//
//******************************************************************************//
char *aw_amf_encode_int16(char *output, char *outend, short nVal)
{
    if(output+2 > outend)
    {
        return NULL;
    }
    output[1] = nVal & 0xff;
    output[0] = nVal >> 8;
    return output+2;
}

char *aw_amf_encode_int24(char *output, char *outend, int nVal)
{
    if(output+3 > outend)
    {
        return NULL;
    }

    output[2] = nVal & 0xff;
    output[1] = nVal >> 8;
    output[0] = nVal >> 16;
    return output+3;
}

char *aw_amf_encode_int32(char *output, char *outend, int nVal)
{
    if(output+4 > outend)
    {
        return NULL;
    }
    output[3] = nVal & 0xff;
    output[2] = nVal >> 8;
    output[1] = nVal >> 16;
    output[0] = nVal >> 24;
    return output+4;
}

int aw_encode_int32Le(char *output, int nVal)
{
    output[0] = nVal;
    nVal >>= 8;
    output[1] = nVal;
    nVal >>= 8;
    output[2] = nVal;
    nVal >>= 8;
    output[3] = nVal;
    return 4;
}

char * aw_amf_encode_string(char *output, char *outend, const aw_rtmp_aval_t *bv)
{
    if((bv->av_len < 65536 && output + 1 + 2 + bv->av_len > outend) ||
	    output + 1 + 4 + bv->av_len > outend)
    {
        return NULL;
    }

    if(bv->av_len < 65536)
    {
        *output++ = AMF_STRING;
        output = aw_amf_encode_int16(output, outend, bv->av_len);
    }
    else
    {
        *output++ = AMF_LONG_STRING;
        output = aw_amf_encode_int32(output, outend, bv->av_len);
    }
    memcpy(output, bv->av_val, bv->av_len);
    output += bv->av_len;
    return output;
}

char *aw_amf_encode_number(char *output, char *outend, double dVal)
{
    if(output+1+8 > outend)
    {
        return NULL;
    }
    *output++ = AMF_NUMBER;	/* type: Number */

    #if __FLOAT_WORD_ORDER == __BYTE_ORDER
        #if __BYTE_ORDER == __BIG_ENDIAN
            memcpy(output, &dVal, 8);
        #elif __BYTE_ORDER == __LITTLE_ENDIAN
        {
            unsigned char *ci, *co;
            ci = (unsigned char *)&dVal;
            co = (unsigned char *)output;
            co[0] = ci[7];
            co[1] = ci[6];
            co[2] = ci[5];
            co[3] = ci[4];
            co[4] = ci[3];
            co[5] = ci[2];
            co[6] = ci[1];
            co[7] = ci[0];
        }
        #endif
    #else
        #if __BYTE_ORDER == __LITTLE_ENDIAN	/* __FLOAT_WORD_ORER == __BIG_ENDIAN */
        {
            unsigned char *ci, *co;
            ci = (unsigned char *)&dVal;
            co = (unsigned char *)output;
            co[0] = ci[3];
            co[1] = ci[2];
            co[2] = ci[1];
            co[3] = ci[0];
            co[4] = ci[7];
            co[5] = ci[6];
            co[6] = ci[5];
            co[7] = ci[4];
        }
        #else /* __BYTE_ORDER == __BIG_ENDIAN && __FLOAT_WORD_ORER == __LITTLE_ENDIAN */
        {
            unsigned char *ci, *co;
            ci = (unsigned char *)&dVal;
            co = (unsigned char *)output;
            co[0] = ci[4];
            co[1] = ci[5];
            co[2] = ci[6];
            co[3] = ci[7];
            co[4] = ci[0];
            co[5] = ci[1];
            co[6] = ci[2];
            co[7] = ci[3];
        }
        #endif
    #endif
    return output+8;
}

char *aw_amf_encode_boolean(char *output, char *outend, int bVal)
{
    if(output+2 > outend)
    {
        return NULL;
    }
    *output++ = AMF_BOOLEAN;
    *output++ = bVal ? 0x01 : 0x00;
    return output;
}

/*  add string(0x00) and the string value to output
*/
char *aw_amf_encode_named_string(char *output, char *outend, aw_rtmp_aval_t *strName, aw_rtmp_aval_t *strValue)
{
    if((output+2+strName->av_len) > outend)
    {
        return NULL;
    }
    output = aw_amf_encode_int16(output, outend, strName->av_len);
    memcpy(output, strName->av_val, strName->av_len);
    output += strName->av_len;
    return aw_amf_encode_string(output, outend, strValue);
}

char *aw_amf_encode_named_number(char *output, char *outend, aw_rtmp_aval_t *strName, double dVal)
{
    if((output+2+strName->av_len) > outend)
    {
        return NULL;
    }
    output = aw_amf_encode_int16(output, outend, strName->av_len);
    memcpy(output, strName->av_val, strName->av_len);
    output += strName->av_len;
    return aw_amf_encode_number(output, outend, dVal);
}

char *aw_amf_encode_named_boolean(char *output, char *outend,aw_rtmp_aval_t *strName, int bVal)
{
    if(output+2+strName->av_len > outend)
    {
        return NULL;
    }
    output = aw_amf_encode_int16(output, outend, strName->av_len);
    memcpy(output, strName->av_val, strName->av_len);
    output += strName->av_len;
    return aw_amf_encode_boolean(output, outend, bVal);
}

void aw_amf_prop_get_name(aw_amfobject_property_t *prop, aw_rtmp_aval_t *name)
{
    *name = prop->p_name;
}

int aw_amf_decode_boolean(char *data)
{
    return *data != 0;
}


/* Data is Big-Endian */
unsigned short aw_amf_decode_int16(const char *data)
{
    unsigned char *c = NULL;
    unsigned short val;
    
    c = (unsigned char *) data;
    val = (c[0] << 8) | c[1];
    return val;
}

unsigned int aw_amf_decode_int24(const char *data)
{
    unsigned char *c = NULL;
    unsigned int val;
    
    c = (unsigned char *) data;
    val = (c[0] << 16) | (c[1] << 8) | c[2];
    return val;
}

unsigned int aw_amf_decode_int32(const char *data)
{
    unsigned char *c = NULL;
    unsigned int val;
    
    c = (unsigned char *)data;
    val = (c[0] << 24) | (c[1] << 16) | (c[2] << 8) | c[3];
    return val;
}

void aw_amf_decode_string(char *data, aw_rtmp_aval_t *bv)
{
    bv->av_len = aw_amf_decode_int16(data);
    bv->av_val = (bv->av_len > 0) ? (char *)data + 2 : NULL;
}

double aw_amf_decode_number(const char *data)
{
    double dVal;
    #if __FLOAT_WORD_ORDER == __BYTE_ORDER
        #if __BYTE_ORDER == __BIG_ENDIAN
            memcpy(&dVal, data, 8);
        #elif __BYTE_ORDER == __LITTLE_ENDIAN
            unsigned char *ci, *co;
            ci = (unsigned char *)data;
            co = (unsigned char *)&dVal;
            co[0] = ci[7];
            co[1] = ci[6];
            co[2] = ci[5];
            co[3] = ci[4];
            co[4] = ci[3];
            co[5] = ci[2];
            co[6] = ci[1];
            co[7] = ci[0];
        #endif
    #else
        #if __BYTE_ORDER == __LITTLE_ENDIAN	/* __FLOAT_WORD_ORER == __BIG_ENDIAN */
            unsigned char *ci, *co;
            ci = (unsigned char *)data;
            co = (unsigned char *)&dVal;
            co[0] = ci[3];
            co[1] = ci[2];
            co[2] = ci[1];
            co[3] = ci[0];
            co[4] = ci[7];
            co[5] = ci[6];
            co[6] = ci[5];
            co[7] = ci[4];
        #else /* __BYTE_ORDER == __BIG_ENDIAN && __FLOAT_WORD_ORER == __LITTLE_ENDIAN */
            unsigned char *ci, *co;
            ci = (unsigned char *)data;
            co = (unsigned char *)&dVal;
            co[0] = ci[4];
            co[1] = ci[5];
            co[2] = ci[6];
            co[3] = ci[7];
            co[4] = ci[0];
            co[5] = ci[1];
            co[6] = ci[2];
            co[7] = ci[3];
        #endif
    #endif
    return dVal;
}

//*******************************************************************************//
//*******************************************************************************//
static void aw_av_queue(aw_rtmp_method_t**vals, int *num, aw_rtmp_aval_t *av, int txn)
{
    char *tmp;
    if(!(*num & 0x0f))
    {
        *vals = realloc(*vals, (*num + 16) * sizeof(aw_rtmp_method_t));
    }
    tmp = malloc(av->av_len + 1);
    memcpy(tmp, av->av_val, av->av_len);
     tmp[av->av_len] = '\0';
    (*vals)[*num].num = txn;
    (*vals)[*num].name.av_len = av->av_len;
    (*vals)[(*num)++].name.av_val = tmp;
}


//*******************************************************************************//
//*******************************************************************************//

#define RTMP_PACKET_SIZE_LARGE    0
#define RTMP_PACKET_SIZE_MEDIUM   1
#define RTMP_PACKET_SIZE_SMALL    2
#define RTMP_PACKET_SIZE_MINIMUM  3

/*message type id*/
#define RTMP_PACKET_TYPE_CHUNK_SIZE         0x01      /* set chunk size*/
#define RTMP_PACKET_TYPE_BYTES_READ_REPORT  0x03      /* Acknowledge message*/
#define RTMP_PACKET_TYPE_CONTROL            0x04      /* user control message */
#define RTMP_PACKET_TYPE_SERVER_BW          0x05      /* the spec present is Windows Acknowledge message(?)  */
#define RTMP_PACKET_TYPE_CLIENT_BW          0x06      /* set peer bandwidth */
#define RTMP_PACKET_TYPE_AUDIO              0x08      /*   audio   */
#define RTMP_PACKET_TYPE_VIDEO              0x09      /*    video  */
#define RTMP_PACKET_TYPE_FLEX_STREAM_SEND   0x0F      /*   (AMF3)data message   */
#define RTMP_PACKET_TYPE_FLEX_SHARED_OBJECT 0x10      /*   (AMF3)share object message  */
#define RTMP_PACKET_TYPE_FLEX_MESSAGE       0x11      /*   (AMF3)command message  */
#define RTMP_PACKET_TYPE_INFO               0x12      /*   (AMF0)data message(metadata)   */
#define RTMP_PACKET_TYPE_SHARED_OBJECT      0x13      /*   (AMF0)share object message   */
#define RTMP_PACKET_TYPE_INVOKE             0x14      /*   (AMF0)command message  */
#define RTMP_PACKET_TYPE_FLASH_VIDEO        0x16      /*    Aggregate message */

#define RTMP_MAX_HEADER_SIZE 18  /*chunk basic header(max bytes:3)+ message header(11)+ extend time stamp*/

int aw_rtmp_send_packet(aw_rtmp_t *r, aw_rtmp_packet_t *packet, int queue)
{
    aw_rtmp_packet_t *prevPacket;
    unsigned int last = 0;
    unsigned int t = 0;
    int nSize = 0;
    int hSize = 0;
    int cSize = 0;
    int nChunkSize = 0;
    int tlen = 0;
    char *header = NULL;
    char *hptr = NULL;
    char *hend = NULL;
    char *buffer = NULL;
    char *tbuf = NULL;
    char *toff = NULL;
    char c = 0;
    char hbuf[RTMP_MAX_HEADER_SIZE];
    int packetSize[] = { 12, 8, 4, 1 };
    
    prevPacket = r->m_vecChannelsOut[packet->m_nChannel];   // prev packet
    if(prevPacket && packet->m_headerType != RTMP_PACKET_SIZE_LARGE)
    {
        /* compress a bit by using the prev packet's attributes */
        if(prevPacket->m_nBodySize == packet->m_nBodySize
	      && prevPacket->m_packetType == packet->m_packetType
	      && packet->m_headerType == RTMP_PACKET_SIZE_MEDIUM)
	    {
            packet->m_headerType = RTMP_PACKET_SIZE_SMALL;
        }

        if(prevPacket->m_nTimeStamp == packet->m_nTimeStamp
	    && packet->m_headerType == RTMP_PACKET_SIZE_SMALL)
	    {
            packet->m_headerType = RTMP_PACKET_SIZE_MINIMUM;
        }
        last = prevPacket->m_nTimeStamp;
   }

    if(packet->m_headerType > 3)	/* sanity */
    {
        return 0;
    }

    nSize = packetSize[packet->m_headerType];
    hSize = nSize; cSize = 0;
    t = packet->m_nTimeStamp - last;

    if(packet->m_body)
    {
        header = packet->m_body - nSize;
        hend = packet->m_body;
    }
    else
    {
        header = hbuf + 6;
        hend = hbuf + sizeof(hbuf);
    }

    if(packet->m_nChannel > 319)
    {
        cSize = 2;
    }
    else if(packet->m_nChannel > 63)
    {
        cSize = 1;
    }

    if(cSize)
    {
        header -= cSize;
        hSize += cSize;
    }
    
    if(nSize > 1 && t >= 0xffffff)
    {
        header -= 4;
        hSize += 4;
    }

    hptr = header;
    c = packet->m_headerType << 6;

    switch(cSize)
    {
        case 0:
            c |= packet->m_nChannel;
            break;
        case 1:
            break;
        case 2:
            c |= 1;
            break;
    }

    *hptr++ = c;
    if(cSize)
    {
        int tmp = packet->m_nChannel - 64;
        *hptr++ = tmp & 0xff;
        if(cSize == 2)
	    {
            *hptr++ = tmp >> 8;
        }
    }

    if(nSize > 1)
    {
        hptr = aw_amf_encode_int24(hptr, hend, t > 0xffffff ? 0xffffff : t);
    }

    if(nSize > 4)
    {
        hptr = aw_amf_encode_int24(hptr, hend, packet->m_nBodySize);
        *hptr++ = packet->m_packetType;
    }

    if(nSize > 8)
    {
        hptr += aw_encode_int32Le(hptr, packet->m_nInfoField2);
    }

    if(nSize > 1 && t >= 0xffffff)
    {
        hptr = aw_amf_encode_int32(hptr, hend, t);
    }

    nSize = packet->m_nBodySize;
    buffer = packet->m_body;
    nChunkSize = r->m_outChunkSize;
    
    /* send all chunks in one HTTP request */
    if(r->Link.protocol & RTMP_FEATURE_HTTP)
    {
        int chunks = (nSize+nChunkSize-1) / nChunkSize;
        if(chunks > 1)
        {
	        tlen = chunks * (cSize + 1) + nSize + hSize;
	        tbuf = malloc(tlen);
	        if(!tbuf)
	        {
                return 0;
	        }
	        toff = tbuf;
	    }
    }

    while(nSize + hSize)
    {
        int wrote;
        if(nSize < nChunkSize)
	    {
            nChunkSize = nSize;
        }
        //RTMP_LogHexString(RTMP_LOGDEBUG2, (uint8_t *)header, hSize);
        //RTMP_LogHexString(RTMP_LOGDEBUG2, (uint8_t *)buffer, nChunkSize);
        if(tbuf)
        {
	        memcpy(toff, header, nChunkSize + hSize);
	        toff += nChunkSize + hSize;
	    }
        else
        {
	        wrote = WriteN(r, header, nChunkSize + hSize);
	        if(!wrote)
	        {
                return 0;
	        }
	    }

        nSize -= nChunkSize;
        buffer += nChunkSize;
        hSize = 0;

        if(nSize > 0)
	    {
	        header = buffer - 1;
	        hSize = 1;
	        if(cSize)
	        {
	            header -= cSize;
	            hSize += cSize;
	        }
	        *header = (0xc0 | c);
	        if(cSize)
	        {
	            int tmp = packet->m_nChannel - 64;
	            header[1] = tmp & 0xff;
	            if(cSize == 2)
		        {
                    header[2] = tmp >> 8;
	            }
	        }
	    }
    }
    
    if(tbuf)
    {
        int wrote = WriteN(r, tbuf, toff-tbuf);
        free(tbuf);
        tbuf = NULL;
        if (!wrote)
        {
            return 0;
        }
    }

    /* we invoked a remote method */
    if(packet->m_packetType == RTMP_PACKET_TYPE_INVOKE)
    {
        aw_rtmp_aval_t method;
        char *ptr;
        
        ptr = packet->m_body + 1;
        aw_amf_decode_string(ptr, &method);
        /* keep it in call queue till result arrives */
        if(queue) 
        {
            int txn;
            ptr += 3 + method.av_len;
            txn = (int)aw_amf_decode_number(ptr);
	        aw_av_queue(&r->m_methodCalls, &r->m_numCalls, &method, txn);
        }
    }

    if(!r->m_vecChannelsOut[packet->m_nChannel])
    {
        r->m_vecChannelsOut[packet->m_nChannel] = malloc(sizeof(aw_rtmp_packet_t));
    }
    memcpy(r->m_vecChannelsOut[packet->m_nChannel], packet, sizeof(aw_rtmp_packet_t));
    return 1;
}

//*******************************************************************************//
//*******************************************************************************//
static int aw_send_bytes_received(aw_rtmp_t *r)
{
  aw_rtmp_packet_t packet;
  char pbuf[256];
  char *pend = NULL;
  
  pend = pbuf + sizeof(pbuf);
  packet.m_nChannel = 0x02;	/* control channel (invoke) */
  packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
  packet.m_packetType = RTMP_PACKET_TYPE_BYTES_READ_REPORT;
  packet.m_nTimeStamp = 0;
  packet.m_nInfoField2 = 0;
  packet.m_hasAbsTimestamp = 0;
  packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

  packet.m_nBodySize = 4;

  aw_amf_encode_int32(packet.m_body, pend, r->m_nBytesIn);	/* hard coded for now */
  r->m_nBytesInSent = r->m_nBytesIn;
  return aw_rtmp_send_packet(r, &packet, 0);
}
//*******************************************************************************//
//*******************************************************************************//
/*从socket buffer读n个字节到client buffer*/
static int ReadN(aw_rtmp_t *r, char *buffer, int n)
{
    int nOriginalSize = n;
    int avail;
    char *ptr;

    r->m_sb.sb_timedout = 0;
    ptr = buffer;

    while(n > 0)
    {
        int nBytes = 0, nRead;
        if(r->Link.protocol & RTMP_FEATURE_HTTP)
        {
	        while(!r->m_resplen)
	        {
	            if(r->m_sb.sb_size < 144)
	            {
		            if(!r->m_unackd)
		            {
                        aw_rtmp_http_post(r, RTMPT_IDLE, "", 1);
		            }
		            if(aw_rtmp_sockbuf_fill(&r->m_sb, &r->exitFlag) < 1)
		            {
		                if(!r->m_sb.sb_timedout)
		                {
                            aw_rtmp_close(r);
		                }
		                return 0;
		            }
		        }
	            if(aw_rtmp_http_read(r, 0) == -1)
		        {
		            aw_rtmp_close(r);
		            return 0;
		        }
	        }
	        if(r->m_resplen && !r->m_sb.sb_size)
	        {
                aw_rtmp_sockbuf_fill(&r->m_sb, &r->exitFlag);
	        }
            avail = r->m_sb.sb_size;
	        if(avail > r->m_resplen)
	        {
                avail = r->m_resplen;
	        }
	    }
        else
        {
            avail = r->m_sb.sb_size;
	        if(avail == 0)
	        {
	            if(aw_rtmp_sockbuf_fill(&r->m_sb, &r->exitFlag) < 1)
	            {
	                if(!r->m_sb.sb_timedout)
	                {
                        aw_rtmp_close(r);
	                }
	                return 0;
		        }
	            avail = r->m_sb.sb_size;
	        }
	    }

        nRead = ((n < avail) ? n : avail);
        if(nRead > 0)
	    {
	        memcpy(ptr, r->m_sb.sb_start, nRead);  /*copy data from socket buffer to client buffer*/
	        r->m_sb.sb_start += nRead;
	        r->m_sb.sb_size -= nRead;
	        nBytes = nRead;
	        r->m_nBytesIn += nRead;
	        if(r->m_bSendCounter && r->m_nBytesIn > ( r->m_nBytesInSent + r->m_nClientBW / 10)) //if
	        {
                if(!aw_send_bytes_received(r))
	            {
                    return 0;
                }
	        }
	    }
      
        if(nBytes == 0)
	    {
	        /*goto again; */
	        aw_rtmp_close(r);
	        break;
	    }

        if(r->Link.protocol & RTMP_FEATURE_HTTP)
	    {
            r->m_resplen -= nBytes;
        }
        n -= nBytes;
        ptr += nBytes;
    }
    return nOriginalSize - n;
}


static int WriteN(aw_rtmp_t *r, char *buffer, int n)
{
    char *ptr = buffer;

    while (n > 0)
    {
        int nBytes;
        /*如果是http形式，则需要在传输之前添加一些请求信息*/
        if(r->Link.protocol & RTMP_FEATURE_HTTP)
        {
            nBytes = aw_rtmp_http_post(r, RTMPT_SEND, ptr, n);
        }
        else
        {
            nBytes = aw_rtmp_sockBuf_send(&r->m_sb, ptr, n, r->exitFlag);
        }
        
        if(nBytes < 0)
	    {   
	        //aw_rtmp_close(r);
	        n = 1;
	        break;
	    }

        if(nBytes == 0)
	    {
            break;
        }
        n -= nBytes;
        ptr += nBytes;
    }
    return n == 0;
}

//**********************************************************************//
//**********************************************************************//
#define SET_RCVTIMEO(tv,s)	tv = s*1000
#define GetSockError()	WSAGetLastError()

static int SocksNegotiate(aw_rtmp_t *r)
{
    unsigned long addr;
    struct sockaddr_in service;

    memset(&service, 0, sizeof(struct sockaddr_in));

    aw_add_addr_info(&service, &r->Link.hostname, r->Link.port);
    addr = htonl(service.sin_addr.s_addr);      /*将一个32位数从主机字节顺序转换成网络字节顺序。*/

    {
        char packet[] = {4, 1,           /* SOCKS 4, connect */
                        (r->Link.port >> 8) & 0xFF,
                        (r->Link.port) & 0xFF,
                        (char)(addr >> 24) & 0xFF, (char)(addr >> 16) & 0xFF,
                        (char)(addr >> 8) & 0xFF, (char)addr & 0xFF,
                         0};				/* NULL terminate */

        WriteN(r, packet, sizeof packet);

        if(ReadN(r, packet, 8) != 8)
        {
            return 0;
        }

        if(packet[0] == 0 && packet[1] == 90)
        {
            return 1;
        }
        else
        {
            return 0;
        }
     }
}

int aw_rtmp_connect3(aw_rtmp_t *r, struct sockaddr * service)
{
    int on = 1;
    int tv =  0;

    r->m_sb.sb_timedout = 0;
    r->m_pausing = 0;
    r->m_fDuration = 0.0;

    r->m_sb.sb_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if(r->m_sb.sb_socket != -1)
    {
        if(connect(r->m_sb.sb_socket, service, sizeof(struct sockaddr)) < 0)
	    {
	        //int err = GetSockError();
	        aw_rtmp_close(r);
	        return 0;
	    }

        if(r->Link.socksport)
	    {
	        if(!SocksNegotiate(r))
	        {
	            aw_rtmp_close(r);
	            return 0;
	        }
	    }
    }
    else
    {
        return 0;
    }

    /* set timeout */
    SET_RCVTIMEO(tv, r->Link.timeout);
    setsockopt(r->m_sb.sb_socket, SOL_SOCKET, SO_RCVTIMEO, (char *)&tv, sizeof(tv));
    setsockopt(r->m_sb.sb_socket, IPPROTO_TCP, TCP_NODELAY, (char *) &on, sizeof(on));
    return 1;
}

int aw_rtmp_connect0(aw_rtmp_t *r, struct sockaddr * service)
{
    int on = 1;
    int tv =  0;
    fd_set fdset;


    r->m_sb.sb_timedout = 0;
    r->m_pausing = 0;
    r->m_fDuration = 0.0;

    r->m_sb.sb_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);  //create socket

    // Turn the socket as non blocking so we can timeout on the connection
    int ret = fcntl( r->m_sb.sb_socket, F_SETFL, fcntl(r->m_sb.sb_socket, F_GETFL, 0) | O_NONBLOCK );
    if(ret < 0)
    {
    	return 0;
    }

    if(r->m_sb.sb_socket != -1) // SOCKET_ERROR : -1
    {
        if(connect(r->m_sb.sb_socket, service, sizeof(struct sockaddr)) != 0) //*connect将socket连接到服务端
	    {

			if (errno != EINPROGRESS)  //*阻塞
			{
				r->iostate = CDX_IO_STATE_ERROR;
				aw_rtmp_close(r);
				return 0;
			}

			while (1)
			{
				if(r->exitFlag == 1)
					return -1;

				struct timeval timeout;
				timeout.tv_sec = 0;
				timeout.tv_usec = 50000;

				FD_ZERO(&fdset);
				FD_SET(0, &fdset);
				FD_SET(r->m_sb.sb_socket, &fdset);
				ret = select(r->m_sb.sb_socket + 1, NULL, &fdset, NULL, &timeout);
				if (ret > 0)
				{
					if (r->Link.socksport)
					{
						if (!SocksNegotiate(r))  //*判断是否连接成功？
						{
							aw_rtmp_close(r);
							return 0;
						}
					}
					break;
				}
				else
				{
					r->iostate = CDX_IO_STATE_ERROR;
				}
			}
//        	fcntl( r->m_sb.sb_socket, F_SETFL, fcntl(r->m_sb.sb_socket, F_GETFL, 0) & (~O_NONBLOCK) );
	    }
    }
    else
    {
        return 0;
    }
        //* set timeout
    SET_RCVTIMEO(tv, r->Link.timeout);
    setsockopt(r->m_sb.sb_socket, SOL_SOCKET, SO_RCVTIMEO, (char *)&tv, sizeof(tv));  //*设置套接字属性
    setsockopt(r->m_sb.sb_socket, IPPROTO_TCP, TCP_NODELAY, (char *) &on, sizeof(on));

    return 1;
}

//******************************************************************************************//
//******************************************************************************************//
#define RTMP_SIG_SIZE 1536
unsigned int  aw_rtmp_get_time()
{   
    #if 0
    struct tms t;
    if(!clk_tck)
    {
        clk_tck = sysconf(_SC_CLK_TCK);
    }
    return times(&t) * 1000 / clk_tck;
    #endif
    return 0;
}


static int aw_rtmp_hand_shake(aw_rtmp_t *r, int FP9HandShake)
{
	CDX_UNUSE(FP9HandShake);
    int i = 0;

    char type = 0;
    unsigned int uptime = 0;
    unsigned int suptime = 0;
    char *clientsig = NULL;
    char clientbuf[RTMP_SIG_SIZE + 1];
    char serversig[RTMP_SIG_SIZE];

    clientsig = clientbuf + 1;
    clientbuf[0] = 0x03;		/* not encrypted */
    uptime = htonl(aw_rtmp_get_time());
    memcpy(clientsig, &uptime, 4);    /*time stamp*/
    memset(&clientsig[4], 0, 4);     /* zero */
    /*client随机生成1528个字节的数据*/
    for(i = 8; i < RTMP_SIG_SIZE; i++)
    {
        clientsig[i] = (char)(rand() % 256);
    }

    if(!WriteN(r, clientbuf, RTMP_SIG_SIZE + 1))  /*C0+C1*/
    {
        CDX_LOGW("write C0,C1 error");
        return 0;
    }

    if(ReadN(r, &type, 1) != 1)	/* 0x03 or 0x06  (S0)*/
    {
        return 0;
    }

    if(ReadN(r, serversig, RTMP_SIG_SIZE) != RTMP_SIG_SIZE)   /*S1*/
    {
        CDX_LOGW(" the size of server send S1 is 1536");
        return 0;
    }

    /* decode server response */

    memcpy(&suptime, serversig, 4);
    suptime = ntohl(suptime);
    
    /* 2nd part of handshake */
    if(!WriteN(r, serversig, RTMP_SIG_SIZE))   /*C2*/
    {
        return 0;
    }

    if(ReadN(r, serversig, RTMP_SIG_SIZE) != RTMP_SIG_SIZE)  /*S2*/
    {
        return 0;
    }

    //if(memcmp(serversig, clientsig, RTMP_SIG_SIZE) != 0)
    //{
    //}
    return 1;
}

//**************************************************************************************//
//**************************************************************************************//
char *aw_amf_encode(aw_amf_object_t *obj, char *pBuffer, char *pBufEnd)
{
    int i;

    if(pBuffer+4 >= pBufEnd)
    {
        return NULL;
    }

    *pBuffer++ = AMF_OBJECT;
    for(i = 0; i < obj->o_num; i++)
    {
        char *res = aw_amf_prop_encode(&obj->o_props[i], pBuffer, pBufEnd);
        if(res == NULL)
	    {
	        break;
	    }
        else
	    {
	        pBuffer = res;
	    }
    }

    if(pBuffer + 3 >= pBufEnd)
    {
        return NULL;            /* no room for the end marker */
    }
    pBuffer = aw_amf_encode_int24(pBuffer, pBufEnd, AMF_OBJECT_END);
    return pBuffer;
}

//****************************************************************************************//
//****************************************************************************************//
char *aw_amf_prop_encode(aw_amfobject_property_t  *prop, char *pBuffer, char *pBufEnd)
{
    if(prop->p_type == AMF_INVALID)
    {
        return NULL;
    }

    if(prop->p_type != AMF_NULL && pBuffer + prop->p_name.av_len + 2 + 1 >= pBufEnd)
    {
        return NULL;
    }

    if(prop->p_type != AMF_NULL && prop->p_name.av_len)
    {
        *pBuffer++ = prop->p_name.av_len >> 8;
        *pBuffer++ = prop->p_name.av_len & 0xff;
        memcpy(pBuffer, prop->p_name.av_val, prop->p_name.av_len);
        pBuffer += prop->p_name.av_len;
    }

    switch(prop->p_type)
    {
        case AMF_NUMBER:
            pBuffer = aw_amf_encode_number(pBuffer, pBufEnd, prop->p_vu.p_number);
            break;
        case AMF_BOOLEAN:
            pBuffer = aw_amf_encode_boolean(pBuffer, pBufEnd, prop->p_vu.p_number != 0);
            break;
        case AMF_STRING:
            pBuffer = aw_amf_encode_string(pBuffer, pBufEnd, &prop->p_vu.p_aval);
            break;
        case AMF_NULL:
            if(pBuffer+1 >= pBufEnd)
            {
                return NULL;
            }
            *pBuffer++ = AMF_NULL;
            break;
        case AMF_OBJECT:
            pBuffer = aw_amf_encode(&prop->p_vu.p_object, pBuffer, pBufEnd);
            break;
        default:
            pBuffer = NULL;
    }
    return pBuffer;
}

//**************************************************************************************//
//**************************************************************************************//
#define RTMP_LF_AUTH	0x0001	/* using auth param */
#define RTMP_LF_LIVE	0x0002	/* stream is live */
#define RTMP_LF_SWFV	0x0004	/* do SWF verification */
#define RTMP_LF_PLST	0x0008	/* send playlist before play */
#define RTMP_LF_BUFX	0x0010	/* toggle stream on BufferEmpty msg */
#define RTMP_LF_FTCU	0x0020	/* free tcUrl on close */


/*
*	send connect command
*/
static int aw_send_connect_packet(aw_rtmp_t *r, aw_rtmp_packet_t *cp)
{
    aw_rtmp_packet_t packet;
    char pbuf[4096];
    char *pend = NULL;
    char *enc;

    pend = pbuf + sizeof(pbuf);

    if(cp)
    {
        return aw_rtmp_send_packet(r, cp, 1);
    }

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;    /*header format*/
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;   /*message type id*/
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_connect); /*connect command: 0x02 00 07 "connect" */
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);        /* connect transcation id (always set to 1)*/
    *enc++ = AMF_OBJECT;  /* command object(AMF0 type is 0x03) */

    enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_app), &r->Link.app);
    if (!enc)
    {
        return 0;
    }
    if(r->Link.protocol & RTMP_FEATURE_WRITE)
    {
        enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_type), (aw_rtmp_aval_t*)(&r->rtmpParam.av_nonprivate));
        if(!enc)
	    {
            return 0;
        }
    }

    if(r->Link.flashVer.av_len)
    {
        enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_flashVer), &r->Link.flashVer);
        if (!enc)
	    {
            return 0;
        }
    }
    if(r->Link.swfUrl.av_len)
    {
        enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_swfUrl), &r->Link.swfUrl);
        if(!enc)
	    {
            return 0;
        }
    }
    if(r->Link.tcUrl.av_len)
    {
        enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_tcUrl), &r->Link.tcUrl);
        if (!enc)
	    {
            return 0;
        }
    }
    
    if(!(r->Link.protocol & RTMP_FEATURE_WRITE))
    {
        enc = aw_amf_encode_named_boolean(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_fpad), 0);
        if(!enc)
	    {
            return 0;
        }
        enc = aw_amf_encode_named_number(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_capabilities), 15.0);
        if(!enc)
	    {
            return 0;
        }
        enc = aw_amf_encode_named_number(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_audioCodecs), r->m_fAudioCodecs);
        if(!enc)
	    {
            return 0;
        }
        enc = aw_amf_encode_named_number(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_videoCodecs), r->m_fVideoCodecs);
        if(!enc)
	    {
            return 0;
        }
        enc = aw_amf_encode_named_number(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_videoFunction), 1.0);
        if(!enc)
	    {
            return 0;
        }
        if(r->Link.pageUrl.av_len)
	    {
	        enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_pageUrl), &r->Link.pageUrl);
	        if (!enc)
	        {
                return 0;
	        }
	    }
    }
    
    if(  0.001<= r->m_fEncoding || r->m_fEncoding <= -0.001 || r->m_bSendEncoding)
    {	
        /* AMF0, AMF3 not fully supported yet */
        enc = aw_amf_encode_named_number(enc, pend, (aw_rtmp_aval_t*)(&r->rtmpParam.av_objectEncoding), r->m_fEncoding);
        if(!enc)
	    {
            return 0;
        }
    }

    if(enc + 3 >= pend)
    {
        return 0;
    }
    *enc++ = 0;
    *enc++ = 0;			/* end of object - 0x00 0x00 0x09 */
    *enc++ = AMF_OBJECT_END;

    /* add auth string */
    if(r->Link.auth.av_len)
    {
        enc = aw_amf_encode_boolean(enc, pend, r->Link.lFlags & RTMP_LF_AUTH);
        if(!enc)
	    {
            return 0;
        }
        enc = aw_amf_encode_string(enc, pend, &r->Link.auth);
        if (!enc)
	    {
            return 0;
        }
    }
    if(r->Link.extras.o_num)
    {
        int i;
        for(i = 0; i < r->Link.extras.o_num; i++)
	    {
	        enc = aw_amf_prop_encode(&r->Link.extras.o_props[i], enc, pend);
	        if(!enc)
	        {
                return 0;
	        }
	    }
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//**************************************************************************************//
//**************************************************************************************//
int aw_rtmp_connect1(aw_rtmp_t *r, aw_rtmp_packet_t *cp)
{
    if(r->Link.protocol & RTMP_FEATURE_SSL)
    {
        CDX_LOGE("it is SSL rtmp. not support");
        //aw_rtmp_close(r);
        return 0;
    }
    
    if(r->Link.protocol & RTMP_FEATURE_HTTP)
    {
        r->m_msgCounter = 1;
        r->m_clientID.av_val = NULL;
        r->m_clientID.av_len = 0;
        aw_rtmp_http_post(r, RTMPT_OPEN, "", 1);
        if(aw_rtmp_http_read(r, 1) != 0)
	    {
	        r->m_msgCounter = 0;
	        //aw_rtmp_close(r);
	        return 0;
	    }
        r->m_msgCounter = 0;
    }

    if(!aw_rtmp_hand_shake(r, 1))
    { 
        CDX_LOGW("hand shake error, rtmp close");
        //aw_rtmp_close(r);
        return 0;
    }
    if(!aw_send_connect_packet(r, cp))
    {
        CDX_LOGW("connect packet error, rtmp close");
        //aw_rtmp_close(r);
        return 0;
    }
    return 1;
}

//******************************************************************************************//
//******************************************************************************************//
int aw_rtmp_connect(aw_rtmp_t *r, aw_rtmp_packet_t *cp)
{   
    struct sockaddr_in service;  /* server地址*/
    if(!r->Link.hostname.av_len)
    {
        return 0;
    }
    memset(&service, 0, sizeof(struct sockaddr_in));
    service.sin_family = AF_INET;
    
    if(r->Link.socksport)
    {
        /* Connect via SOCKS */
        if(!aw_add_addr_info(&service, &r->Link.sockshost, (int)r->Link.socksport))
	    {
	        CDX_LOGW("Connect via SOCKS  error");
            return 0;
        }
    }
    else
    {
        /* Connect directly */
        if(!aw_add_addr_info(&service, &r->Link.hostname, (int)r->Link.port))
	    {
	        CDX_LOGW("-- Connect directly error, hostname = %s", r->Link.hostname.av_val);
            return 0;
        }
    }

    if(!aw_rtmp_connect0(r, (struct sockaddr *)(&service)))  /* tcp传输层连接 */
    {
        CDX_LOGD("aw_rtmp_connect0  error");
        return 0;
    }
    r->m_bSendCounter = 1;

    return aw_rtmp_connect1(r, cp);  /* rtmp应用层三次握手 C0+C1, S0+S1+S2, C2 */
}
//************************************************************************************//
//************************************************************************************//

int aw_rtmp_is_connected(aw_rtmp_t *r)
{
    return r->m_sb.sb_socket != -1;
}

void aw_rtmp_packet_free(aw_rtmp_packet_t *p)
{
    if(p->m_body)
    {
        free(p->m_body - RTMP_MAX_HEADER_SIZE);
        p->m_body = NULL;
    }
}

static int aw_decode_int32le(char *data)
{
    unsigned char *c = (unsigned char *)data;
    unsigned int val;
    
    val = (c[3] << 24) | (c[2] << 16) | (c[1] << 8) | c[0];
    return val;
}

int aw_rtmp_packet_alloc(aw_rtmp_packet_t *p, int nSize)
{
    char *ptr = calloc(1, nSize + RTMP_MAX_HEADER_SIZE);
    if(!ptr)
    {
        return 0;
    }
    p->m_body = ptr + RTMP_MAX_HEADER_SIZE;
    p->m_nBytesRead = 0;
    return 1;
}

#define RTMPPacket_IsReady(a)	((a)->m_nBytesRead == (a)->m_nBodySize)

#define RTMP_LARGE_HEADER_SIZE 12

/*parse a chunk, (chunk basic header & chunk message header)*/
int aw_rtmp_read_packet(aw_rtmp_t*r, aw_rtmp_packet_t *packet)
{
    int nSize = 0;
    int hSize = 0;
    int nToRead = 0;
    int nChunk = 0;
//    int didAlloc = 0;
    unsigned char hbuf[RTMP_MAX_HEADER_SIZE]= {0};
    int packetSize[4] = { 12, 8, 4, 1 };
    char *header = (char *)hbuf;
    
    if(ReadN(r, (char *)hbuf, 1) == 0)  /* chunk basic header */
    {
        return 0;
    }

    packet->m_headerType = (hbuf[0] & 0xc0) >> 6;
    packet->m_nChannel = (hbuf[0] & 0x3f);
    header++;
    /**if chunk stream id is 0, the chunk stream id is represented by 2 bytes (the range of stream id is 64-319)*/
    if(packet->m_nChannel == 0)
    {
        if(ReadN(r, (char *)&hbuf[1], 1) != 1)
	    {
	        return 0;
	    }
        packet->m_nChannel = hbuf[1]; /*chunk stream id = the second byte + 64*/
        packet->m_nChannel += 64;
        header++;
    }
    /**if chunk stream id is 1, the chunk stream id is represented by 3 bytes (the range of stream id is 64-65599)*/
    else if (packet->m_nChannel == 1)
    {
        int tmp;
        if(ReadN(r, (char *)&hbuf[1], 2) != 2)
	    {
	        return 0;
	    }
        tmp = (hbuf[2] << 8) + hbuf[1]; /* chunk stream id = the third byte*255 + the second byte+64 */
        packet->m_nChannel = tmp + 64;
        header += 2;
    }

    nSize = packetSize[packet->m_headerType];

    /* Chunk basic header format 0 */
    if(nSize == RTMP_LARGE_HEADER_SIZE)	/* if we get a full header the timestamp is absolute */
    {
        packet->m_hasAbsTimestamp = 1;
    }
    else if(nSize < RTMP_LARGE_HEADER_SIZE)
    {				/* using values from the last message of this channel */
        if(r->m_vecChannelsIn[packet->m_nChannel])
	    {
            memcpy(packet, r->m_vecChannelsIn[packet->m_nChannel],sizeof(aw_rtmp_packet_t));
        }
    }
    
    nSize--;
    if(nSize > 0 && ReadN(r, header, nSize) != nSize)
    {
        return 0;
    }

    hSize = nSize + (header - (char *)hbuf);
    if(nSize >= 3)
    {
        packet->m_nTimeStamp = aw_amf_decode_int24(header);  /*big endium*/
        if(nSize >= 6)
	    {
	        packet->m_nBodySize = aw_amf_decode_int24(header + 3);  /*message length*/
	        packet->m_nBytesRead = 0;
	        aw_rtmp_packet_free(packet);
	        if(nSize > 6)
	        {
	            packet->m_packetType = header[6];  /*message type id*/
	            if(nSize == 11)
		        {
                    packet->m_nInfoField2 = aw_decode_int32le(header + 7);  /* message stream id */
	            }
	        }
	    }
        
        if(packet->m_nTimeStamp == 0xffffff)  /*if the time stamp is the max number, need the extend time stamp*/
	    {
            if(ReadN(r, header + nSize, 4) != 4) /*Extend Time stamp*/
	        {
	            return 0;
	        }
	        packet->m_nTimeStamp = aw_amf_decode_int32(header + nSize);
	        hSize += 4;
	    }
    }

    //RTMP_LogHexString(RTMP_LOGDEBUG2, (uint8_t *)hbuf, hSize);

    if(packet->m_nBodySize > 0 && packet->m_body == NULL)
    {
        if(!aw_rtmp_packet_alloc(packet, packet->m_nBodySize))
	    {
	        return 0;
	    }
//        didAlloc = 1;
        packet->m_headerType = (hbuf[0] & 0xc0) >> 6;
    }

    nToRead = packet->m_nBodySize - packet->m_nBytesRead; /*the data size of same message in different chunk */
    nChunk = r->m_inChunkSize;
    if(nToRead < nChunk)
    {
        nChunk = nToRead;
    }

    /* Does the caller want the raw chunk? */
    if(packet->m_chunk)
    {
        packet->m_chunk->c_headerSize = hSize;
        memcpy(packet->m_chunk->c_header, hbuf, hSize);
        packet->m_chunk->c_chunk = packet->m_body + packet->m_nBytesRead;
        packet->m_chunk->c_chunkSize = nChunk;
    }

    if(ReadN(r, packet->m_body + packet->m_nBytesRead, nChunk) != nChunk)  /*read chunk data */
    {
        return 0;
    }

    packet->m_nBytesRead += nChunk;

    /* keep the packet as ref for other packets on this channel */
    if(!r->m_vecChannelsIn[packet->m_nChannel])
    {
        r->m_vecChannelsIn[packet->m_nChannel] = malloc(sizeof(aw_rtmp_packet_t));
    }
    memcpy(r->m_vecChannelsIn[packet->m_nChannel], packet, sizeof(aw_rtmp_packet_t));
    if(RTMPPacket_IsReady(packet))
    {
        /* make packet's timestamp absolute */
        if(!packet->m_hasAbsTimestamp)
	    {
            packet->m_nTimeStamp += r->m_channelTimestamp[packet->m_nChannel];  /* timestamps seem to be always relative!! */
        }

        r->m_channelTimestamp[packet->m_nChannel] = packet->m_nTimeStamp;

        /* reset the data from the stored packet. we keep the header since we may use it later if a new packet for this channel */
        /* arrives and requests to re-use some info (small packet header) */
        r->m_vecChannelsIn[packet->m_nChannel]->m_body = NULL;
        r->m_vecChannelsIn[packet->m_nChannel]->m_nBytesRead = 0;
        r->m_vecChannelsIn[packet->m_nChannel]->m_hasAbsTimestamp = 0;	/* can only be false if we reuse header */
    }
    else
    {
        packet->m_body = NULL;	/* so it won't be erased on free */
    }
    return 1;
}

//**************************************************************************************************************************//
//***************************************************************************************************************************//
static void aw_handle_change_chunkSize(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
    if(packet->m_nBodySize >= 4)
    {
        r->m_inChunkSize = aw_amf_decode_int32(packet->m_body);
    }
}

/*
from http://jira.red5.org/confluence/display/docs/Ping:

Ping is the most mysterious message in RTMP and till now we haven't fully interpreted it yet.
In summary, Ping message is used as a special command that are exchanged between client and server.
This page aims to document all known Ping messages. Expect the list to grow.

The type of Ping packet is 0x4 and contains two mandatory parameters and two optional parameters.
The first parameter is the type of Ping and in short integer.
The second parameter is the target of the ping.
As Ping is always sent in Channel 2 (control channel)
and the target object in RTMP header is always 0 which means the Connection object,
it's necessary to put an extra parameter to indicate the exact target object the Ping is sent to.
The second parameter takes this responsibility.
The value has the same meaning as the target object field in RTMP header.
(The second value could also be used as other purposes, like RTT Ping/Pong. It is used as the timestamp.)
The third and fourth parameters are optional and could be looked upon as the parameter of the Ping packet.
Below is an unexhausted list of Ping messages.


    * this function handle the user message Event
    * type 0(stream begin): Clear the stream. No third and fourth parameters. The second parameter could be 0.
    *         After the connection is established, a Ping 0,0 will be sent from server to client.
    *         The message will also be sent to client on the start of Play and in response of a Seek or Pause/Resume request.
    *         This Ping tells client to re-calibrate the clock with the timestamp of the next packet server sends.
    * type 1(stream EOF): Tell the stream to clear the playing buffer.
    * type 3(set buffer length): Buffer time of the client. The third parameter is the buffer time in millisecond.
    * type 4(stream id recorded): Reset a stream. Used together with type 0 in the case of VOD. Often sent before type 0.
    * type 6(ping request): Ping the client from server. The second parameter is the current time.
    * type 7(ping response): Pong reply from client. The second parameter is the time the server sent with his ping request.
    * type 26: SWFVerification request
    * type 27: SWFVerification response
*/
int aw_rtmp_send_ctrl(aw_rtmp_t *r, short nType, unsigned int nObject, unsigned int nTime)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = NULL;
    char *buf = NULL;
    int nSize = 0;

    pend = pbuf + sizeof(pbuf);
    packet.m_nChannel = 0x02;	/* control channel (ping) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_CONTROL;
    packet.m_nTimeStamp = 0;	/* RTMP_GetTime(); */
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    switch(nType)
    {
        case 0x03: 
            nSize = 10; 
            break;  /* buffer time */
        case 0x1A: 
            nSize = 3; 
            break;   /* SWF verify request */
        case 0x1B: 
            nSize = 44; 
            break;  /* SWF verify response */
        default: 
            nSize = 6;
            break;
    }
    packet.m_nBodySize = nSize;
    buf = packet.m_body;
    buf = aw_amf_encode_int16(buf, pend, nType);

    if(nType == 0x1B)
    {
        
    }
    else if (nType == 0x1A)
    {
	    *buf = nObject & 0xff;
	}
    else
    {
        if(nSize > 2)
	    {
            buf = aw_amf_encode_int32(buf, pend, nObject);
        }
        if(nSize > 6)
	    {
            buf = aw_amf_encode_int32(buf, pend, nTime);
        }
    }
    return aw_rtmp_send_packet(r, &packet, 0);
}

//********************************************************************//
//********************************************************************//
int aw_rtmp_send_pause(aw_rtmp_t *r, int DoPause, int iTime)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = NULL;
    char *enc = NULL;
    
    pend = pbuf + sizeof(pbuf);
    packet.m_nChannel = 0x08;	/* video channel */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_pause);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_boolean(enc, pend, DoPause);
    enc = aw_amf_encode_number(enc, pend, (double)iTime);
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//********************************************************************//
//********************************************************************//

static void aw_handle_ctrl(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
    short nType = -1;
    unsigned int tmp;

    if(packet->m_body && packet->m_nBodySize >= 2)
    {
        nType = aw_amf_decode_int16(packet->m_body);/* Event Type*/
    }
    
    if(packet->m_nBodySize >= 6)
    {
        switch (nType)   /*event Type*/
	    {
	        case 0:   /*stream begin*/
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            break;
	        case 1:   /*stream FOF*/
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            if(r->m_pausing == 1)
	            {
                    r->m_pausing = 2;
	            }
	            break;
	        case 2:   /*stream Dry(notify the client that there is no more data on the stream)*/
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            break;
	        case 4:  /*Stream is recorded*/
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            break;
	        case 6:		/* server ping. reply with pong. */
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            aw_rtmp_send_ctrl(r, 0x07, tmp, 0);
	            break;
	        case 31:
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            if(!(r->Link.lFlags & RTMP_LF_BUFX))
	            {
                    break;
	            }
	            if(!r->m_pausing)
	            {
	                r->m_pauseStamp = r->m_channelTimestamp[r->m_mediaChannel];
	                aw_rtmp_send_pause(r, 1, r->m_pauseStamp);
	                r->m_pausing = 1;
	            }
	            else if (r->m_pausing == 2)
	            {
	                aw_rtmp_send_pause(r, 0, r->m_pauseStamp);
	                r->m_pausing = 3;
	            }
	            break;
	        case 32:
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            break;

	        default:
	            tmp = aw_amf_decode_int32(packet->m_body + 2);
	            break;
	    }
    }

    if(nType == 0x1A)
    {   
        #if 0
        if(r->Link.SWFSize)
	    {
	        aw_rtmp_send_ctrl(r, 0x1B, 0, 0);
        }
        #endif
    }
}

//***********************************************************************//
//***********************************************************************//
static void aw_handle_server_bW(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
    r->m_nServerBW = aw_amf_decode_int32(packet->m_body);
}

//***********************************************************************//
//***********************************************************************//
static void aw_handle_client_bW(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
    r->m_nClientBW = aw_amf_decode_int32(packet->m_body);
    if(packet->m_nBodySize > 4)
    {
        r->m_nClientBW2 = packet->m_body[4];
    }
    else
    {
        r->m_nClientBW2 = -1;
    }
}

static void aw_handle_audio(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
	CDX_UNUSE(r);
	CDX_UNUSE(packet);
    
}
static void aw_handle_video(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
	CDX_UNUSE(r);
	CDX_UNUSE(packet);
    
}

int aw_amf_decode_array(aw_rtmp_t*r, aw_amf_object_t*obj, const char *pBuffer, int nSize,int nArrayLen, int bDecodeName)
{
    int nOriginalSize = nSize;
    int bError = 0;

    obj->o_num = 0;
    obj->o_props = NULL;
    
    while(nArrayLen > 0)
    {   
        aw_amfobject_property_t prop;
        int nRes;
        nArrayLen--;
        nRes = aw_amf_prop_decode(r, &prop, (char*)pBuffer, nSize, bDecodeName);
        if(nRes == -1)
	    {
            bError = 1;
        }
        else
	    {
	        nSize -= nRes;
	        pBuffer += nRes;
	        aw_amf_add_prop(obj, &prop);
	    }
    }
    if(bError)
    {
        return -1;
    }
    return nOriginalSize - nSize;
}


void aw_amf_decode_long_string(char *data, aw_rtmp_aval_t *bv)
{
    bv->av_len = aw_amf_decode_int32(data);
    bv->av_val = (bv->av_len > 0) ? (char *)data + 4 : NULL;
}

#define AMF3_INTEGER_MAX	268435455
#define AMF3_INTEGER_MIN	-268435456

int aw_amf3_read_integer(char *data, int *valp)
{
    int i = 0;
    int val = 0;

    while(i <= 2)
    {				/* handle first 3 bytes */
        if(data[i] & 0x80)
	    {			/* byte used */
	        val <<= 7;		/* shift up */
	        val |= (data[i] & 0x7f);	/* add bits */
	        i++;
	    }
        else
	    {
	        break;
	    }
    }

    if(i > 2)
    {				/* use 4th byte, all 8bits */
        val <<= 8;
        val |= data[3];

        /* range check */
        if(val > AMF3_INTEGER_MAX)
	    {
            val -= (1 << 29);
        }
    }
    else
    {				/* use 7bits of last unparsed byte (0xxxxxxx) */
        val <<= 7;
        val |= data[i];
    }
    *valp = val;
    return i > 2 ? 4 : i + 1;
}

typedef struct AMF3_CLASS_DEF
{   
    char cd_externalizable;
    char cd_dynamic;
    int cd_num;
    aw_rtmp_aval_t cd_name;
    aw_rtmp_aval_t *cd_props;
}aw_amf3_class_def_t;

int aw_amf3_read_string(char *data, aw_rtmp_aval_t *str)
{
    int ref = 0;
    int len;
   // assert(str != 0);

    len = aw_amf3_read_integer(data, &ref);
    data += len;

    if((ref & 0x1) == 0)
    {				/* reference: 0xxx */
 //       unsigned int refIndex = (ref >> 1);
        return len;
    }
    else
    {
        unsigned int nSize = (ref >> 1);
        str->av_val = (char *)data;
        str->av_len = nSize;
        return len + nSize;
    }
    return len;
}


/* AMF3ClassDefinition */

void aw_amf3_class_definition_add_prop(aw_amf3_class_def_t *cd, aw_rtmp_aval_t *prop)
{
    if(!(cd->cd_num & 0x0f))
    {
        cd->cd_props = realloc(cd->cd_props, (cd->cd_num + 16) * sizeof(aw_rtmp_aval_t));
    }
    cd->cd_props[cd->cd_num++] = *prop;
}

aw_rtmp_aval_t * aw_amf3_class_definition_get_prop(aw_rtmp_t*r, aw_amf3_class_def_t *cd, int nIndex)
{   
    r->rtmpParam.AV_empty.av_len = 0;
    r->rtmpParam.AV_empty.av_val = 0;
    
    if(nIndex >= cd->cd_num)
    {
        return &r->rtmpParam.AV_empty;
    }
    return &cd->cd_props[nIndex];
}

typedef enum
{ 
    AMF3_UNDEFINED = 0, AMF3_NULL, AMF3_FALSE, AMF3_TRUE,
    AMF3_INTEGER, AMF3_DOUBLE, AMF3_STRING, AMF3_XML_DOC, AMF3_DATE,
    AMF3_ARRAY, AMF3_OBJECT, AMF3_XML, AMF3_BYTE_ARRAY
}aw_amf3_data_type;

int aw_amf3_prop_decode(aw_rtmp_t*r, aw_amfobject_property_t *prop, const char *pBuffer, int nSize,int bDecodeName)
{

    int nOriginalSize = nSize;
    aw_amf3_data_type type;

    prop->p_name.av_len = 0;
    prop->p_name.av_val = NULL;

    if(nSize == 0 || !pBuffer)
    {
        return -1;
    }

    /* decode name */
    if(bDecodeName)
    {
        aw_rtmp_aval_t name;
        int nRes = aw_amf3_read_string((char*)pBuffer, &name);
        if(name.av_len <= 0)
	    {
            return nRes;
        }
        prop->p_name = name;
        pBuffer += nRes;
        nSize -= nRes;
    }

    /* decode */
    type = *pBuffer++;
    nSize--;

    switch (type)
    {
        case AMF3_UNDEFINED:
        case AMF3_NULL:
            prop->p_type = AMF_NULL;
            break;
        case AMF3_FALSE:
            prop->p_type = AMF_BOOLEAN;
            prop->p_vu.p_number = 0.0;
            break;
        case AMF3_TRUE:
            prop->p_type = AMF_BOOLEAN;
            prop->p_vu.p_number = 1.0;
            break;
        case AMF3_INTEGER:
        {
	        int res = 0;
	        int len = aw_amf3_read_integer((char*)pBuffer, &res);
	        prop->p_vu.p_number = (double)res;
	        prop->p_type = AMF_NUMBER;
	        nSize -= len;
	        break;
        }
        case AMF3_DOUBLE:
            if(nSize < 8)
	        {
                return -1;
            }
            prop->p_vu.p_number = aw_amf_decode_number(pBuffer);
            prop->p_type = AMF_NUMBER;
            nSize -= 8;
            break;
        case AMF3_STRING:
        case AMF3_XML_DOC:
        case AMF3_XML:
        {
	        int len = aw_amf3_read_string((char*)pBuffer, &prop->p_vu.p_aval);
	        prop->p_type = AMF_STRING;
	        nSize -= len;
	        break;
        }
        case AMF3_DATE:
        {
	        int res = 0;
	        int len = aw_amf3_read_integer((char*)pBuffer, &res);
            nSize -= len;
	        pBuffer += len;

	        if((res & 0x1) == 0)
	        {			
                /* reference */
//	            unsigned int nIndex = (res >> 1);
	        }
	        else
	        {
	            if(nSize < 8)
	            {
                    return -1;
	            }
	            prop->p_vu.p_number = aw_amf_decode_number(pBuffer);
	            nSize -= 8;
	            prop->p_type = AMF_NUMBER;
	        }
	        break;
        }
        case AMF3_OBJECT:
        {
	        int nRes = aw_amf3_decode(r, &prop->p_vu.p_object, (char*)pBuffer, nSize, 1);
	        if(nRes == -1)
	        {
                return -1;
	        }
	        nSize -= nRes;
	        prop->p_type = AMF_OBJECT;
	        break;
        }
        case AMF3_ARRAY:
        case AMF3_BYTE_ARRAY:
        default:
            return -1;
    }
    return nOriginalSize - nSize;
}

void aw_amf_prop_set_name(aw_amfobject_property_t *prop, aw_rtmp_aval_t *name)
{
    prop->p_name = *name;
}


int aw_amf3_decode(aw_rtmp_t*r, aw_amf_object_t *obj, char *pBuffer, int nSize, int bAMFData)
{
    int ref;
    int len;
    int nOriginalSize;

    nOriginalSize = nSize;
    obj->o_num = 0;
    obj->o_props = NULL;
    
    if(bAMFData)
    {
        pBuffer++;
        nSize--;
    }

    ref = 0;
    len = aw_amf3_read_integer(pBuffer, &ref);
    pBuffer += len;
    nSize -= len;

    if((ref & 1) == 0)
    {				/* object reference, 0xxx */
//        unsigned int  objectIndex = (ref >> 1);
    }
    else				/* object instance */
    {
        int classRef = (ref >> 1);
        aw_amf3_class_def_t cd;
        aw_amfobject_property_t prop;

        memset(&cd, 0, sizeof(aw_amf3_class_def_t));
        
        if((classRef & 0x1) == 0)
	    {	
            /* class reference */
//            unsigned int classIndex = (classRef >> 1);
	    }
        else
	    {
            int classExtRef = (classRef >> 1);
	        int i;

	        cd.cd_externalizable = (classExtRef & 0x1) == 1;
	        cd.cd_dynamic = ((classExtRef >> 1) & 0x1) == 1;
	        cd.cd_num = classExtRef >> 2;

	        /* class name */

	        len = aw_amf3_read_string(pBuffer, &cd.cd_name);
	        nSize -= len;
	        pBuffer += len;

	        /*std::string str = className; */

	        for(i = 0; i < cd.cd_num; i++)
	        {
                aw_rtmp_aval_t  memberName;
	            len = aw_amf3_read_string(pBuffer, &memberName);
	            aw_amf3_class_definition_add_prop(&cd, &memberName);
	            nSize -= len;
	            pBuffer += len;
	        }
	    }
        /* add as referencable object */

        if(cd.cd_externalizable)
	    {
            int nRes;
	        aw_rtmp_aval_t name = AVC("DEFAULT_ATTRIBUTE");

	        nRes = aw_amf3_prop_decode(r, &prop, pBuffer, nSize, 0);
	        if(nRes == -1)
	        {
            
            }
	        else
	        {
                nSize -= nRes;
	            pBuffer += nRes;
	        }
            aw_amf_prop_set_name(&prop, &name);
	        aw_amf_add_prop(obj, &prop);
        }
        else
	    {
            int nRes, i;
	        for(i = 0; i < cd.cd_num; i++)	/* non-dynamic */
	        {
	            nRes = aw_amf3_prop_decode(r,&prop, pBuffer, nSize, FALSE);
	            aw_amf_prop_set_name(&prop, aw_amf3_class_definition_get_prop(r, &cd, i));
	            aw_amf_add_prop(obj, &prop);
	            pBuffer += nRes;
	            nSize -= nRes;
	        }
	        if(cd.cd_dynamic)
	        {
                int len = 0;
                do
		        {
		            nRes = aw_amf3_prop_decode(r,&prop, pBuffer, nSize, TRUE);
		            aw_amf_add_prop(obj, &prop);
		            pBuffer += nRes;
		            nSize -= nRes;
		            len = prop.p_name.av_len;
		        }while (len > 0);
	        }
        }
    }
    return nOriginalSize - nSize;
}


int aw_amf_prop_decode(aw_rtmp_t*r, aw_amfobject_property_t *prop, char *pBuffer, int nSize,int bDecodeName)
{
    int nOriginalSize = nSize;
    int nRes;

    prop->p_name.av_len = 0;
    prop->p_name.av_val = NULL;

    if(nSize == 0 || !pBuffer)
    {
        return -1;
    }

    if(bDecodeName && nSize < 4)
    {				/* at least name (length + at least 1 byte) and 1 byte of data */
        return -1;
    }

    if(bDecodeName)
    {
        unsigned short nNameSize = aw_amf_decode_int16(pBuffer);
        if(nNameSize > nSize - 2)
	    {
	        return -1;
	    }
        aw_amf_decode_string(pBuffer, &prop->p_name);
        nSize -= 2 + nNameSize;
        pBuffer += 2 + nNameSize;
    }

    if(nSize == 0)
    {
        return -1;
    }
    nSize--;
    prop->p_type = *pBuffer++;
    
    switch(prop->p_type)
    {
        case AMF_NUMBER:
            if(nSize < 8)
	        {
                return -1;
            }
            prop->p_vu.p_number = aw_amf_decode_number(pBuffer);
            nSize -= 8;
            break;
        case AMF_BOOLEAN:
            if(nSize < 1)
	        {
                return -1;
            }
            prop->p_vu.p_number = (double)aw_amf_decode_boolean(pBuffer);
            nSize--;
            break;
        case AMF_STRING:
        {
	        unsigned short nStringSize = aw_amf_decode_int16(pBuffer);
	        if(nSize < (long)nStringSize + 2)
	        {
                return -1;
	        }
	        aw_amf_decode_string(pBuffer, &prop->p_vu.p_aval);
	        nSize -= (2 + nStringSize);
	        break;
        }
        case AMF_OBJECT:
        {
	        int nRes = aw_amf_decode(r,&prop->p_vu.p_object, pBuffer, nSize, 1);
	        if(nRes == -1)
	        {
                return -1;
	        }
	        nSize -= nRes;
	        break;
        }
        case AMF_MOVIECLIP:
        {
	        return -1;
        }
        case AMF_NULL:
        case AMF_UNDEFINED:
        case AMF_UNSUPPORTED:
            prop->p_type = AMF_NULL;
            break;
        case AMF_REFERENCE:
        {
            return -1;
        }
        case AMF_ECMA_ARRAY:
        {
	        nSize -= 4;
	        /* next comes the rest, mixed array has a final 0x000009 mark and names, so its an object */
	        nRes = aw_amf_decode(r,&prop->p_vu.p_object, pBuffer + 4, nSize, 1);
	        if(nRes == -1)
	        {
                return -1;
	        }
	        nSize -= nRes;
	        prop->p_type = AMF_OBJECT;
	        break;
        }
        case AMF_OBJECT_END:
        {
	        return -1;
        }
        case AMF_STRICT_ARRAY:
        {
	        unsigned int nArrayLen = aw_amf_decode_int32(pBuffer);
	        nSize -= 4;
	        nRes = aw_amf_decode_array(r, &prop->p_vu.p_object, pBuffer + 4, nSize,nArrayLen, 0);
	        if(nRes == -1)
	        {
                return -1;
	        }
	        nSize -= nRes;
	        prop->p_type = AMF_OBJECT;
	        break;
        }
        case AMF_DATE:
        {
	        if(nSize < 10)
	        {
                return -1;
	        }
	        prop->p_vu.p_number = aw_amf_decode_number(pBuffer);
	        prop->p_UTCoffset = aw_amf_decode_int16(pBuffer + 8);
	        nSize -= 10;
	        break;
        }
        case AMF_LONG_STRING:
        case AMF_XML_DOC:
        {
	        unsigned int nStringSize = aw_amf_decode_int32(pBuffer);
	        if(nSize < (long)nStringSize + 4)
	        {
                return -1;
	        }
	        aw_amf_decode_long_string(pBuffer, &prop->p_vu.p_aval);
	        nSize -= (4 + nStringSize);
	        if(prop->p_type == AMF_LONG_STRING)
	        {
                prop->p_type = AMF_STRING;
	        }
	        break;
        }
        case AMF_RECORDSET:
        {
	        return -1;
        }
        case AMF_TYPED_OBJECT:
        {
            return -1;
        }
        case AMF_AVMPLUS:
        {
	        int nRes = aw_amf3_decode(r, &prop->p_vu.p_object, pBuffer, nSize, TRUE);
	        if(nRes == -1)
	        {
                return -1;
	        }
	        nSize -= nRes;
	        prop->p_type = AMF_OBJECT;
	        break;
        }
        default:
        {
            return -1;
        }
    }
    return nOriginalSize - nSize;
}

int aw_amf_decode(aw_rtmp_t *r, aw_amf_object_t *obj, const char *pBuffer, int nSize, int bDecodeName)
{
    int nOriginalSize = nSize;
    int bError = 0;		/* if there is an error while decoding - try to at least find the end mark AMF_OBJECT_END */

    obj->o_num = 0;
    obj->o_props = NULL;
    
    while(nSize > 0)
    {
        aw_amfobject_property_t prop;
        int nRes;
        
        if(nSize >=3 && aw_amf_decode_int24(pBuffer) == AMF_OBJECT_END)
	    {
	        nSize -= 3;
	        bError = 0;
	        break;
	    }

        if(bError)
        {
	        nSize--;
	        pBuffer++;
	        continue;
	    }

        nRes = aw_amf_prop_decode(r, &prop, (char*)pBuffer, nSize, bDecodeName);
        if(nRes == -1)
	    {
            bError = 1;
        }
        else
	    {
	        nSize -= nRes;
	        pBuffer += nRes;
	        aw_amf_add_prop(obj, &prop);
	    }
    }

    if(bError)
    {
        return -1;
    }
    return nOriginalSize - nSize;
}

//******************************************************************************//
//******************************************************************************//
void aw_amf_dump(aw_amf_object_t *obj)
{
    int n;
    for(n = 0; n < obj->o_num; n++)
    {
        aw_amf_prop_dump(&obj->o_props[n]);
    }
}

void aw_amf_prop_dump(aw_amfobject_property_t *prop)
{
    char strRes[256];
    char str[256];
    aw_rtmp_aval_t name;

    if(prop->p_type == AMF_INVALID)
    {
        return;
    }
    if(prop->p_type == AMF_NULL)
    {
        return;
    }
    
    if(prop->p_name.av_len)
    {
        name = prop->p_name;
    }
    else
    {
        name.av_val = "no-name.";
        name.av_len = sizeof("no-name.") - 1;
    }
    
    if(name.av_len > 18)
    {
        name.av_len = 18;
    }
    snprintf(strRes, 255, "Name: %18.*s, ", name.av_len, name.av_val);

    if(prop->p_type == AMF_OBJECT)
    {
        aw_amf_dump(&prop->p_vu.p_object);
        return;
    }

    switch (prop->p_type)
    {
        case AMF_NUMBER:
            snprintf(str, 255, "NUMBER:\t%.2f", (double)prop->p_vu.p_number);
            break;
        case AMF_BOOLEAN:
            snprintf(str, 255, "BOOLEAN:\t%s",
	        prop->p_vu.p_number != 0.0 ? "TRUE" : "FALSE");
            break;
        case AMF_STRING:
            snprintf(str, 255, "STRING:\t%.*s", prop->p_vu.p_aval.av_len,
	        prop->p_vu.p_aval.av_val);
            break;
        case AMF_DATE:
            snprintf(str, 255, "DATE:\ttimestamp: %.2f, UTC offset: %d",
	        (double)prop->p_vu.p_number, prop->p_UTCoffset);
            break;
        default:
            snprintf(str, 255, "INVALID TYPE 0x%02x", (unsigned char)prop->p_type);
     }
}

//******************************************************************************//
//******************************************************************************//
void aw_amf_prop_get_string(aw_amfobject_property_t *prop, aw_rtmp_aval_t *str)
{
    *str = prop->p_vu.p_aval;
}
#define AVMATCH(a1,a2)	((a1)->av_len == (a2)->av_len && !memcmp((a1)->av_val,(a2)->av_val,(a1)->av_len))
//static const aw_amfobject_property_t AMFProp_Invalid = { {0, 0}, AMF_INVALID };

aw_amfobject_property_t *aw_amf_get_prop(aw_rtmp_t*r, aw_amf_object_t *obj, aw_rtmp_aval_t*name, int nIndex)
{   
    r->rtmpParam.AMFProp_Invalid.p_name.av_val = 0;
    r->rtmpParam.AMFProp_Invalid.p_name.av_len = 0;
    r->rtmpParam.AMFProp_Invalid.p_type = AMF_INVALID;
        
    if(nIndex >= 0)
    {
        if(nIndex < obj->o_num)
	    {
            return &obj->o_props[nIndex];
        }
    }
    else
    {
        int n;
        for (n = 0; n < obj->o_num; n++)
	    {
	        if(AVMATCH(&obj->o_props[n].p_name, name))
	        {
                return &obj->o_props[n];
	        }
	    }
    }
    return (aw_amfobject_property_t *)&r->rtmpParam.AMFProp_Invalid;
}

int aw_amf_prop_get_number(aw_amfobject_property_t *prop)
{
    return prop->p_vu.p_number;
}

//*****************************************************************************//
//****************************************************************************//
static void aw_av_erase(aw_rtmp_method_t *vals, int *num, int i, int freeit)
{
    if(freeit)
    {
        free(vals[i].name.av_val);
    }
    (*num)--;
    for(; i < *num; i++)
    {
        vals[i] = vals[i + 1];
    }
    vals[i].name.av_val = NULL;
    vals[i].name.av_len = 0;
    vals[i].num = 0;
}

//******************************************************************************//
//******************************************************************************//
int aw_rtmp_find_first_matching_property(aw_rtmp_t*r, aw_amf_object_t *obj, aw_rtmp_aval_t *name,
			       aw_amfobject_property_t* p)
{
    int n;
    /* this is a small object search to locate the "duration" property */
    for(n = 0; n < obj->o_num; n++)
    {
        aw_amfobject_property_t *prop = aw_amf_get_prop(r,obj, NULL, n);

        if(AVMATCH(&prop->p_name, name))
	    {
	        memcpy(p, prop, sizeof(*prop));
	        return 1;
	    }
        if(prop->p_type == AMF_OBJECT)
	    {
	        if(aw_rtmp_find_first_matching_property(r, &prop->p_vu.p_object, name, p))
	        {
                return 1;
	        }
	    }
    }
    return 0;
}

//******************************************************************************//
//******************************************************************************//
#define HEX2BIN(a)	(((a)&0x40)?((a)&0xf)+9:((a)&0xf))

static void aw_decode_tea(aw_rtmp_aval_t *key, aw_rtmp_aval_t *text)
{   
    int p = 0;
    int q = 0;
    int i = 0;
    int n = 0;
    unsigned char *ptr;
    unsigned char *out;
    unsigned int *v;
    unsigned int k[4] = {0};
    unsigned int u = 0;
    unsigned int z = 0;
    unsigned int y = 0;
    unsigned int sum = 0;
    unsigned int e = 0;
    unsigned int DELTA = 0x9e3779b9;

    /* prep key: pack 1st 16 chars into 4 LittleEndian ints */
    ptr = (unsigned char *)key->av_val;
    u = 0;
     n = 0;
    v = k;
    p = key->av_len > 16 ? 16 : key->av_len;
    
    for(i = 0; i < p; i++)
    {
        u |= ptr[i] << (n * 8);
        if(n == 3)
	    {
	        *v++ = u;
	        u = 0;
	        n = 0;
	    }
        else
	    {
	        n++;
	    }
    }
    /* any trailing chars */
    if(u)
    {
        *v = u;
    }

    /* prep text: hex2bin, multiples of 4 */
    n = (text->av_len + 7) / 8;
    out = malloc(n * 8);
    ptr = (unsigned char *)text->av_val;
      
    v = (unsigned int *) out;
    for(i = 0; i < n; i++)
    {
        u = (HEX2BIN(ptr[0]) << 4) + HEX2BIN(ptr[1]);
        u |= ((HEX2BIN(ptr[2]) << 4) + HEX2BIN(ptr[3])) << 8;
        u |= ((HEX2BIN(ptr[4]) << 4) + HEX2BIN(ptr[5])) << 16;
        u |= ((HEX2BIN(ptr[6]) << 4) + HEX2BIN(ptr[7])) << 24;
        *v++ = u;
        ptr += 8;
    }
    v = (unsigned int *) out;

    /* http://www.movable-type.co.uk/scripts/tea-block.html */
    #define MX (((z>>5)^(y<<2)) + ((y>>3)^(z<<4))) ^ ((sum^y) + (k[(p&3)^e]^z));
    z = v[n - 1];
    y = v[0];
    q = 6 + 52 / n;
    sum = q * DELTA;
    while(sum != 0)
    {
        e = sum >> 2 & 3;
        for(p=n-1; p > 0; p--)
	    {
            z = v[p - 1], y = v[p] -= MX;
        }
        z = v[n - 1];
        y = v[0] -= MX;
        sum -= DELTA;
    }
    text->av_len /= 2;
    memcpy(text->av_val, out, text->av_len);
    free(out);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_secure_token_response(aw_rtmp_t *r, aw_rtmp_aval_t *resp)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = NULL;
    char *enc;
    
    pend = pbuf + 1024;
    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_secureTokenResponse);
    enc = aw_amf_encode_number(enc, pend, 0.0);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, resp);
    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_release_stream(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = NULL;
    char *enc;
    
    pend = pbuf + sizeof(pbuf);
    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_releaseStream);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, &r->Link.playpath);
    if (!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//******************************************************************************//
//*******************************************************************************//
static int aw_send_fc_publish(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = NULL;
    char *enc;
    
    pend = pbuf + sizeof(pbuf);
    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_FCPublish);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, &r->Link.playpath);
    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//*****************************************************************************//
//*****************************************************************************//
int aw_rtmp_send_serverBw(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);

    packet.m_nChannel = 0x02;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_SERVER_BW;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    packet.m_nBodySize = 4;

    aw_amf_encode_int32(packet.m_body, pend, r->m_nServerBW);
    return aw_rtmp_send_packet(r, &packet, 0);
}

//******************************************************************************//
//******************************************************************************//

int aw_rtmp_send_create_stream(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_createStream);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;		/* NULL */
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_usher_token(aw_rtmp_t *r, aw_rtmp_aval_t *usherToken)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;
    
    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;
    
    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_NetStream_Authenticate_UsherToken);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, usherToken);

    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_fc_subscribe(aw_rtmp_t *r, aw_rtmp_aval_t *subscribepath)
{
    aw_rtmp_packet_t packet;
    char pbuf[512];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;
    
    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_FCSubscribe);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, subscribepath);

    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_publish(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x04;	/* source channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = r->m_stream_id;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_publish);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, &r->Link.playpath);
    if(!enc)
    {
        return 0;
    }

    /* FIXME: should we choose live based on Link.lFlags & RTMP_LF_LIVE? */
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_live);
    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_playlist(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x08;	/* we make 8 our stream channel */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = r->m_stream_id;	/*0x01000000; */
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_set_playlist);
    enc = aw_amf_encode_number(enc, pend, 0);
    *enc++ = AMF_NULL;
    *enc++ = AMF_ECMA_ARRAY;
    *enc++ = 0;
    *enc++ = 0;
    *enc++ = 0;
    *enc++ = AMF_OBJECT;
    enc = aw_amf_encode_named_string(enc, pend, (aw_rtmp_aval_t*)&r->rtmpParam.av_0, &r->Link.playpath);
    if(!enc)
    {
        return 0;
    }
    if(enc + 3 >= pend)
    {
        return 0;
    }
    *enc++ = 0;
    *enc++ = 0;
    *enc++ = AMF_OBJECT_END;
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_play(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x08;	/* we make 8 our stream channel */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = r->m_stream_id;	/*0x01000000; */
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_play);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, &r->Link.playpath);
    if(!enc)
    {
        return FALSE;
    }

  /* Optional parameters start and len.
   *
   * start: -2, -1, 0, positive number
   *  -2: looks for a live stream, then a recorded stream,
   *      if not found any open a live stream
   *  -1: plays a live stream
   * >=0: plays a recorded streams from 'start' milliseconds
   */
    if(r->Link.lFlags & RTMP_LF_LIVE)
    {
        enc = aw_amf_encode_number(enc, pend, -1000.0);
    }
    else
    {
        if(r->Link.seekTime > 0.0)
	    {
            enc = aw_amf_encode_number(enc, pend, r->Link.seekTime);    /* resume from here */
        }
        else
	    {
            enc = aw_amf_encode_number(enc, pend, -2000.0); /*-2000.0);*/ /* recorded as default, -2000.0 is not reliable since that freezes the player if the stream is not found */
        }
    }
    if(!enc)
    {
        return 0;
    }

  /* len: -1, 0, positive number
   *  -1: plays live or recorded stream to the end (default)
   *   0: plays a frame 'start' ms away from the beginning
   *  >0: plays a live or recoded stream for 'len' milliseconds
   */
  /*enc += EncodeNumber(enc, -1.0); */ /* len */
    if(r->Link.stopTime)
    {
        enc = aw_amf_encode_number(enc, pend, r->Link.stopTime - r->Link.seekTime);
        if(!enc)
	    {
            return 0;
        }
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 1);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_checkBw(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;	/* RTMP_GetTime(); */
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av__checkbw);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    packet.m_nBodySize = enc - packet.m_body;
    /* triggers _onbwcheck and eventually results in _onbwdone */
    return aw_rtmp_send_packet(r, &packet, 0);
}

//*******************************************************************************//
//*******************************************************************************//
static int aw_send_pong(aw_rtmp_t *r, double txn)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0x16 * r->m_nBWCheckCounter;	/* temp inc value. till we figure it out. */
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_pong);
    enc = aw_amf_encode_number(enc, pend, txn);
    *enc++ = AMF_NULL;
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//******************************************************************************//
//******************************************************************************//
static int aw_send_checkBw_result(aw_rtmp_t *r, double txn)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0x16 * r->m_nBWCheckCounter;	/* temp inc value. till we figure it out. */
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av__result);
    enc = aw_amf_encode_number(enc, pend, txn);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_number(enc, pend, (double)r->m_nBWCheckCounter++);
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

//*****************************************************************************//
//*****************************************************************************//
void aw_amf_prop_get_object(aw_amfobject_property_t *prop, aw_amf_object_t *obj)
{
    *obj = prop->p_vu.p_object;
}

//******************************************************************************//
//******************************************************************************//
void aw_amf_reset(aw_amf_object_t *obj)
{
    int n;
    for(n = 0; n < obj->o_num; n++)
    {
        aw_amf_prop_reset(&obj->o_props[n]);
    }
    free(obj->o_props);
    obj->o_props = NULL;
    obj->o_num = 0;
}

void aw_amf_prop_reset(aw_amfobject_property_t *prop)
{
    if(prop->p_type == AMF_OBJECT)
    {
        aw_amf_reset(&prop->p_vu.p_object);
    }
    else
    {
        prop->p_vu.p_aval.av_len = 0;
        prop->p_vu.p_aval.av_val = NULL;
    }
    prop->p_type = AMF_INVALID;
}

//******************************************************************************//
//******************************************************************************//
/* Returns 0 for OK/Failed/error, 1 for 'Stop or Complete' */
static int aw_handle_invoke(aw_rtmp_t *r, char *body, unsigned int nBodySize)
{
    aw_amf_object_t obj;
    aw_rtmp_aval_t method;
    int txn;
    int ret = 0;
    int nRes = 0;
    
    if(body[0] != 0x02)		/* make sure it is a string method name we start with */
    {
        return 0;
    }

    nRes = aw_amf_decode(r, &obj, body, nBodySize, 0);
    if(nRes < 0)
    {
        return 0;
    }
    aw_amf_dump(&obj);
    aw_amf_prop_get_string(aw_amf_get_prop(r,&obj, NULL, 0), &method);
    txn = aw_amf_prop_get_number(aw_amf_get_prop(r,&obj, NULL, 1));

    if(AVMATCH(&method, &r->rtmpParam.av__result)) /* "_result" */
    {
        aw_rtmp_aval_t methodInvoked;
        int i;
        memset(&methodInvoked, 0, sizeof(aw_rtmp_aval_t));
        
        for(i=0; i<r->m_numCalls; i++) 
        {
	        if(r->m_methodCalls[i].num == (int)txn) 
            {
	            methodInvoked = r->m_methodCalls[i].name;
	            aw_av_erase(r->m_methodCalls, &r->m_numCalls, i, 0);
	            break;
	        }
        }

        if(!methodInvoked.av_val) 
        {
	        goto leave;
        }
        if(AVMATCH(&methodInvoked, &r->rtmpParam.av_connect))    /* Command message(connect)*/
	    {
	        if(r->Link.token.av_len)
	        {
	            aw_amfobject_property_t p;
	            if(aw_rtmp_find_first_matching_property(r,&obj, (aw_rtmp_aval_t*)(&r->rtmpParam.av_secureToken), &p))
		        {
		            aw_decode_tea(&r->Link.token, &p.p_vu.p_aval);
		            aw_send_secure_token_response(r, &p.p_vu.p_aval);
		        }
	        }
	        if(r->Link.protocol & RTMP_FEATURE_WRITE)
	        {
	            aw_send_release_stream(r);
	            aw_send_fc_publish(r);
	        }
	        else
	        {
	            aw_rtmp_send_serverBw(r);
	            aw_rtmp_send_ctrl(r, 3, 0, 300);
	        }
	        aw_rtmp_send_create_stream(r);
	        if(!(r->Link.protocol & RTMP_FEATURE_WRITE))
	        {
	            /* Authenticate on Justin.tv legacy servers before sending FCSubscribe */
	            if(r->Link.usherToken.av_len)
	            {
                    aw_send_usher_token(r, &r->Link.usherToken);
	            }
	            /* Send the FCSubscribe if live stream or if subscribepath is set */
	            if(r->Link.subscribepath.av_len)
	            {
                    aw_send_fc_subscribe(r, &r->Link.subscribepath);
	            }
	            else if (r->Link.lFlags & RTMP_LF_LIVE)
	            {
                    aw_send_fc_subscribe(r, &r->Link.playpath);
	            }
	        }
	    }
        else if (AVMATCH(&methodInvoked, &r->rtmpParam.av_createStream))       /* command message (createStream)*/
	    {
	        r->m_stream_id = (int)aw_amf_prop_get_number(aw_amf_get_prop(r,&obj, NULL, 3));
	        if(r->Link.protocol & RTMP_FEATURE_WRITE)
	        {
	            aw_send_publish(r);
	        }
	        else
	        {
	            if(r->Link.lFlags & RTMP_LF_PLST)
	            {
                    aw_send_playlist(r);
	            }
	            aw_send_play(r);
	            aw_rtmp_send_ctrl(r, 3, r->m_stream_id, r->m_nBufferMS);
	        }
	    }
        else if(AVMATCH(&methodInvoked, &r->rtmpParam.av_play) ||AVMATCH(&methodInvoked, &r->rtmpParam.av_publish))
	    {
	        r->m_bPlaying = TRUE;
	    }
        free(methodInvoked.av_val);
    }
    else if(AVMATCH(&method, &r->rtmpParam.av_onBWDone))  /* onBWDone */
    {
	    if (!r->m_nBWCheckCounter)
        {
            aw_send_checkBw(r);
	    }
    }
    else if(AVMATCH(&method, &r->rtmpParam.av_onFCSubscribe))
    {
      /* SendOnFCSubscribe(); */
    }
    else if(AVMATCH(&method, &r->rtmpParam.av_onFCUnsubscribe))
    {
        aw_rtmp_close(r);
        ret = 1;
    }
    else if (AVMATCH(&method, &r->rtmpParam.av_ping))
    {
        aw_send_pong(r, txn);
    }
    else if(AVMATCH(&method, &r->rtmpParam.av__onbwcheck))
    {
        aw_send_checkBw_result(r, txn);
    }
    else if(AVMATCH(&method, &r->rtmpParam.av__onbwdone))
    {
        int i;
        for (i = 0; i < r->m_numCalls; i++)
	    if(AVMATCH(&r->m_methodCalls[i].name, &r->rtmpParam.av__checkbw))
	    {
	        aw_av_erase(r->m_methodCalls, &r->m_numCalls, i, TRUE);
	        break;
	    }
    }
    else if(AVMATCH(&method, &r->rtmpParam.av__error))
    {
        
    }
    else if(AVMATCH(&method, &r->rtmpParam.av_close))
    {
        aw_rtmp_close(r);
    }
    else if(AVMATCH(&method, &r->rtmpParam.av_onStatus))  /* "onStatus" */
    {
        aw_amf_object_t obj2;
        aw_rtmp_aval_t code, level;
        aw_amf_prop_get_object(aw_amf_get_prop(r,&obj, NULL, 3), &obj2);
        aw_amf_prop_get_string(aw_amf_get_prop(r,&obj2, (aw_rtmp_aval_t*)&r->rtmpParam.av_code, -1), &code);
        aw_amf_prop_get_string(aw_amf_get_prop(r,&obj2, (aw_rtmp_aval_t*)&r->rtmpParam.av_level, -1), &level);
        if(AVMATCH(&code, &r->rtmpParam.av_NetStream_Failed)
	        || AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_Failed)
	        || AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_StreamNotFound)
	        || AVMATCH(&code, &r->rtmpParam.av_NetConnection_Connect_InvalidApp))
	    {
	        r->m_stream_id = -1;
	        aw_rtmp_close(r);
	    }
        else if(AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_Start)
           || AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_PublishNotify))
	    {
	        int i;
	        r->m_bPlaying = TRUE;
	        for(i = 0; i < r->m_numCalls; i++)
	        {
	            if(AVMATCH(&r->m_methodCalls[i].name, &r->rtmpParam.av_play))
		        {
		            aw_av_erase(r->m_methodCalls, &r->m_numCalls, i, TRUE);
		            break;
		        }
	        }
	    }
        else if(AVMATCH(&code, &r->rtmpParam.av_NetStream_Publish_Start))
	    {
	        int i;
	        r->m_bPlaying = TRUE;
	        for(i = 0; i < r->m_numCalls; i++)
	        {
	            if(AVMATCH(&r->m_methodCalls[i].name, &r->rtmpParam.av_publish))
		        {
		            aw_av_erase(r->m_methodCalls, &r->m_numCalls, i, TRUE);
		            break;
		        }
	        }
	    }
        /* Return 1 if this is a Play.Complete or Play.Stop */
        else if (AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_Complete)
	        || AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_Stop)
	        || AVMATCH(&code, &r->rtmpParam.av_NetStream_Play_UnpublishNotify))
	    {
	        aw_rtmp_close(r);
	        ret = 1;
        }
        else if (AVMATCH(&code, &r->rtmpParam.av_NetStream_Seek_Notify))
        {
	        //r->m_read.flags &= ~RTMP_READ_SEEKING;
	    }
        else if(AVMATCH(&code, &r->rtmpParam.av_NetStream_Pause_Notify))
        {
	        if(r->m_pausing == 1 || r->m_pausing == 2)
	        {
	            aw_rtmp_send_pause(r, FALSE, r->m_pauseStamp);
	            r->m_pausing = 3;
	        }
	    }
    }
    else if (AVMATCH(&method, &r->rtmpParam.av_playlist_ready))
    {
        int i;
        for(i = 0; i < r->m_numCalls; i++)
        {
            if(AVMATCH(&r->m_methodCalls[i].name, &r->rtmpParam.av_set_playlist))
	        {
	            aw_av_erase(r->m_methodCalls, &r->m_numCalls, i, TRUE);
	            break;
	        }
        }
    }
    else
    {

    }
leave:
    aw_amf_reset(&obj);
    return ret;
}

//***********************************************************************//
//***********************************************************************//
static int aw_dump_meta_data(aw_rtmp_t*r, aw_amf_object_t *obj)
{
    aw_amfobject_property_t *prop;
    int n;

    for(n = 0; n < obj->o_num; n++)
    {
        prop = aw_amf_get_prop(r,obj, NULL, n);
        if(prop->p_type != AMF_OBJECT)
	    {
	        char str[256] = "";
	        switch (prop->p_type)
	        {
	            case AMF_NUMBER:
	                snprintf(str, 255, "%.2f", (double)prop->p_vu.p_number);
	                break;
	            case AMF_BOOLEAN:
	                snprintf(str, 255, "%s",
		            prop->p_vu.p_number != 0. ? "TRUE" : "FALSE");
	                break;
	            case AMF_STRING:
	                snprintf(str, 255, "%.*s", prop->p_vu.p_aval.av_len,
		            prop->p_vu.p_aval.av_val);
	                break;
	            case AMF_DATE:
	                snprintf(str, 255, "timestamp:%.2f", (double)prop->p_vu.p_number);
	                break;
	            default:
	                snprintf(str, 255, "INVALID TYPE 0x%02x",(unsigned char)prop->p_type);
	        }
            
	        if(prop->p_name.av_len)
	        {
                /* chomp */
	            if(strlen(str) >= 1 && str[strlen(str) - 1] == '\n')
		        {
                    str[strlen(str) - 1] = '\0';
	            }
	        }
	    }
        else
	    {
	        aw_dump_meta_data(r, &prop->p_vu.p_object);
	    }
    }
    return 0;
}

//********************************************************************************************//
//*******************************************************************************************//
/* Like above, but only check if name is a prefix of property */
int aw_rtmp_find_prefix_property(aw_rtmp_t*r, aw_amf_object_t *obj, aw_rtmp_aval_t *name,
			       aw_amfobject_property_t* p)
{
    int n;
    for(n = 0; n < obj->o_num; n++)
    {
        aw_amfobject_property_t *prop = aw_amf_get_prop(r, obj, NULL, n);
        if(prop->p_name.av_len > name->av_len &&
      	  !memcmp(prop->p_name.av_val, name->av_val, name->av_len))
	    {
	        memcpy(p, prop, sizeof(aw_amfobject_property_t));
	        return 1;
	    }
        if(prop->p_type == AMF_OBJECT)
	    {
	        if(aw_rtmp_find_prefix_property(r, &prop->p_vu.p_object, name, p))
	        {
                return 1;
	        }
	    }
    }
    return 0;
}

//***********************************************************************//
//***********************************************************************//
static int aw_handle_metadata(aw_rtmp_t *r, char *body, unsigned int len)
{
    /* allright we get some info here, so parse it and print it */
     /* also keep duration or filesize to make a nice progress bar */

    aw_amf_object_t obj;
    aw_rtmp_aval_t  metastring;
    int ret = 0;

    int nRes = aw_amf_decode(r,&obj, body, len, 0);
    if(nRes < 0)
    {
        return FALSE;
    }

    aw_amf_dump(&obj);
    aw_amf_prop_get_string(aw_amf_get_prop(r,&obj, NULL, 0), &metastring);

    if(AVMATCH(&metastring, &r->rtmpParam.av_onMetaData))
    {
        aw_amfobject_property_t prop;
        /* Show metadata */
        aw_dump_meta_data(r,&obj);
        if(aw_rtmp_find_first_matching_property(r,&obj, (aw_rtmp_aval_t*)&r->rtmpParam.av_duration, &prop))
	    {
	        r->m_fDuration = prop.p_vu.p_number;
	    }
        /* Search for audio or video tags */
        if(aw_rtmp_find_prefix_property(r, &obj, (aw_rtmp_aval_t*)&r->rtmpParam.av_video, &prop))
        {
            r->m_read.dataType |= 1;
        }
        if(aw_rtmp_find_prefix_property(r, &obj, (aw_rtmp_aval_t*)&r->rtmpParam.av_audio, &prop))
        {
            r->m_read.dataType |= 4;
        }
        ret = TRUE;
    }
    aw_amf_reset(&obj);
    return ret;
}

//***********************************************************************//
//***********************************************************************//
int aw_rtmp_client_packet(aw_rtmp_t*r, aw_rtmp_packet_t *packet)
{
    int bHasMediaPacket = 0;

    switch(packet->m_packetType)  /*message(AMF) type id*/
    {
        case RTMP_PACKET_TYPE_CHUNK_SIZE:       /* set chunk size */
            /* chunk size */
            aw_handle_change_chunkSize(r, packet);
            break;
        case RTMP_PACKET_TYPE_BYTES_READ_REPORT:   /*acknowledge message*/
             /* bytes read report */
            break;
        case RTMP_PACKET_TYPE_CONTROL:     /*use control message*/
            /* ctrl */
            aw_handle_ctrl(r, packet);
            break;
        case RTMP_PACKET_TYPE_SERVER_BW:     /* spec descrip it window acknownledge size, but code is not*/
            /* server bw */
            aw_handle_server_bW(r, packet);
            break;
        case RTMP_PACKET_TYPE_CLIENT_BW:     /* "set peer banwith" */
            /* client bw */
            aw_handle_client_bW(r, packet);
            break;
        case RTMP_PACKET_TYPE_AUDIO:
            /* audio data */
            aw_handle_audio(r, packet);
            bHasMediaPacket = 1;
            if(!r->m_mediaChannel)
	        {
                r->m_mediaChannel = packet->m_nChannel;
            }
            if(!r->m_pausing)
	        {
                r->m_mediaStamp = packet->m_nTimeStamp;
            }
            break;
        case RTMP_PACKET_TYPE_VIDEO:
            /* video data */
            aw_handle_video(r, packet);
            bHasMediaPacket = 1;
            if(!r->m_mediaChannel)
	        {
                r->m_mediaChannel = packet->m_nChannel;
            }
            if(!r->m_pausing)
	        {
                r->m_mediaStamp = packet->m_nTimeStamp;
            }
            break;
        case RTMP_PACKET_TYPE_FLEX_STREAM_SEND:
            /* flex stream send */
            break;

        case RTMP_PACKET_TYPE_FLEX_SHARED_OBJECT:
            /* flex shared object */
            break;
        case RTMP_PACKET_TYPE_FLEX_MESSAGE:
            /* flex message */

	        if(aw_handle_invoke(r, packet->m_body + 1, packet->m_nBodySize - 1) == 1)
	        {
                bHasMediaPacket = 2;
	        }
	        break;
        case RTMP_PACKET_TYPE_INFO:
            /* metadata (notify) */
            if(aw_handle_metadata(r, packet->m_body, packet->m_nBodySize))
	        {
                bHasMediaPacket = 1;
            }
            break;
        case RTMP_PACKET_TYPE_SHARED_OBJECT:
            break;
        case RTMP_PACKET_TYPE_INVOKE:              /* user control message */
 
            /* invoke */
            if(aw_handle_invoke(r, packet->m_body, packet->m_nBodySize) == 1)
	        {
                bHasMediaPacket = 2;
            }
            break;
        case RTMP_PACKET_TYPE_FLASH_VIDEO:  /* Aggregate Message */
        {
	        /* go through FLV packets and handle metadata packets */
	        unsigned int pos = 0;
	        unsigned int nTimeStamp;
            
            nTimeStamp = packet->m_nTimeStamp;
	        while (pos + 11 < packet->m_nBodySize)
	        {
	            unsigned int dataSize = aw_amf_decode_int24(packet->m_body + pos + 1);	/* size without header (11) and prevTagSize (4) */

	            if(pos + 11 + dataSize + 4 > packet->m_nBodySize)
	            {
		            break;
	            }
	            if(packet->m_body[pos] == 0x12)
	            {
		            aw_handle_metadata(r, packet->m_body + pos + 11, dataSize);
	            }
	            else if (packet->m_body[pos] == 8 || packet->m_body[pos] == 9)
	            {
		            nTimeStamp = aw_amf_decode_int24(packet->m_body + pos + 4);
		            nTimeStamp |= (packet->m_body[pos + 7] << 24);
	            }
	            pos += (11 + dataSize + 4);
	        }
	        if(!r->m_pausing)
	        {
                r->m_mediaStamp = nTimeStamp;
	        }
	        bHasMediaPacket = 1;
	        break;
        }
        default:
        {
            break;
        }
    }
    return bHasMediaPacket;
}

//**************************************************************************************************************************//
//**************************************************************************************************************************//
int aw_rtmp_connect_stream(aw_rtmp_t *r, int seekTime)
{
    aw_rtmp_packet_t packet;

    /* seekTime was already set by SetupStream / SetupURL.
     * This is only needed by ReconnectStream.
    */
    memset(&packet, 0, sizeof(aw_rtmp_packet_t));
    if(seekTime > 0)
    {
        r->Link.seekTime = seekTime;
    }
    r->m_mediaChannel = 0;
    int ret;

	// parse the packet header and chunk msg header, then deal with the packet according to the packet type
    while(!r->m_bPlaying && aw_rtmp_is_connected(r) && (ret = aw_rtmp_read_packet(r, &packet)))
    {
        if(RTMPPacket_IsReady(&packet))
	    {
	        if(!packet.m_nBodySize)
	        {
                continue;
	        }
	        if ((packet.m_packetType == RTMP_PACKET_TYPE_AUDIO) ||
	            (packet.m_packetType == RTMP_PACKET_TYPE_VIDEO) ||
	            (packet.m_packetType == RTMP_PACKET_TYPE_INFO))
	        {
	            aw_rtmp_packet_free(&packet);
	            continue;
	        }
	        aw_rtmp_client_packet(r, &packet);
	        aw_rtmp_packet_free(&packet);
	    }
    }
//    printf("aw_rtmp_connect_stream    end\n");
    return r->m_bPlaying;
}

//*****************************************************8**************************************//
//*******************************************************************************************//
static int aw_send_fc_unpublish(aw_rtmp_t *r)
{
    aw_rtmp_packet_t packet;
    char pbuf[1024];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_FCUnpublish);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_string(enc, pend, &r->Link.playpath);
    if(!enc)
    {
        return 0;
    }
    packet.m_nBodySize = enc - packet.m_body;
    return aw_rtmp_send_packet(r, &packet, 0);
}

static int aw_send_delete_stream(aw_rtmp_t *r, double dStreamId)
{
    aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x03;	/* control channel (invoke) */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_deleteStream);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_number(enc, pend, dStreamId);
    packet.m_nBodySize = enc - packet.m_body;
    /* no response expected */
    return aw_rtmp_send_packet(r, &packet, 0);
}


int aw_send_seek(aw_rtmp_t *r, int64_t iTime)
{
	aw_rtmp_packet_t packet;
    char pbuf[256];
    char *pend = pbuf + sizeof(pbuf);
    char *enc;

    packet.m_nChannel = 0x08;	/* video channel */
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_INVOKE;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_body = pbuf + RTMP_MAX_HEADER_SIZE;

    enc = packet.m_body;
    enc = aw_amf_encode_string(enc, pend, &r->rtmpParam.av_seek);
    enc = aw_amf_encode_number(enc, pend, ++r->m_numInvokes);
    *enc++ = AMF_NULL;
    enc = aw_amf_encode_number(enc, pend, (double)iTime);

    packet.m_nBodySize = enc - packet.m_body;

    r->m_read.flags |= RTMP_READ_SEEKING;
    r->m_read.nResumeTS = 0;

    return aw_rtmp_send_packet(r, &packet, TRUE);
}

int aw_rtmp_sock_buf_close(aw_rtmp_socket_buf_t* sb)
{
    if(sb->sb_socket != -1)
    {
        return closesocket(sb->sb_socket);
    }
    return 0;
}

static void aw_av_clear(aw_rtmp_method_t *vals, int num)
{
    int i;
    for(i = 0; i < num; i++)
    {
        free(vals[i].name.av_val);
    }
    free(vals);
}

void aw_rtmp_close(aw_rtmp_t *r)
{
    int i;
    #define RTMP_READ_HEADER	0x01

    if(!r)
    {
        return;
    }

    if(aw_rtmp_is_connected(r))
    {
        if(r->m_stream_id > 0)
        {
	        i = r->m_stream_id;
	        r->m_stream_id = 0;
            if((r->Link.protocol & RTMP_FEATURE_WRITE))
	        {
                aw_send_fc_unpublish(r);
            }
	        aw_send_delete_stream(r, i);
	    }
        
        if(r->m_clientID.av_val)
        {
	        aw_rtmp_http_post(r, RTMPT_CLOSE, "", 1);
	        free(r->m_clientID.av_val);
	        r->m_clientID.av_val = NULL;
	        r->m_clientID.av_len = 0;
	    }
        aw_rtmp_sock_buf_close(&r->m_sb);
    }

    r->m_stream_id = -1;
    r->m_sb.sb_socket = -1;
    r->m_nBWCheckCounter = 0;
    r->m_nBytesIn = 0;
    r->m_nBytesInSent = 0;
    
    r->m_read.dataType = 0;
    r->m_read.flags = 0;
    r->m_read.nResumeTS = 0;
    r->m_write.m_nBytesRead = 0;
    aw_rtmp_packet_free(&r->m_write);

    for(i = 0; i < RTMP_CHANNELS; i++)
    {
        if(r->m_vecChannelsIn[i])
	    {
	        aw_rtmp_packet_free(r->m_vecChannelsIn[i]);
	        free(r->m_vecChannelsIn[i]);
	        r->m_vecChannelsIn[i] = NULL;
	    }
        if(r->m_vecChannelsOut[i])
	    {
	        free(r->m_vecChannelsOut[i]);
	        r->m_vecChannelsOut[i] = NULL;
	    }
    }
    aw_av_clear(r->m_methodCalls, r->m_numCalls);
    r->m_methodCalls = NULL;
    r->m_numCalls = 0;
    r->m_numInvokes = 0;

    r->m_bPlaying = 0;
    r->m_sb.sb_size = 0;

    r->m_msgCounter = 0;
    r->m_resplen = 0;
    r->m_unackd = 0;

    free(r->Link.playpath0.av_val);
    r->Link.playpath0.av_val = NULL;

    if(r->Link.lFlags & RTMP_LF_FTCU)
    {
        free(r->Link.tcUrl.av_val);
        r->Link.tcUrl.av_val = NULL;
        r->Link.lFlags ^= RTMP_LF_FTCU;
    }
}


