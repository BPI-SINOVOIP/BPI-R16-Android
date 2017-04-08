
#include <arpa/inet.h>

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <netdb.h>

#include <CdxLog.h>
#include <CdxMemory.h>
#include <CdxStream.h>
#include <CdxMessage.h>
#include <CdxLock.h>
#include <CdxQueue.h>
#include <CdxList.h>
#include <CdxAtomic.h>
#include <CdxBuffer.h>
#include <CdxSocketUtil.h>
#include <CdxSeqBuffer.h>
#include <CdxTime.h>
#include <AwPool.h>
#include <CdxBinary.h>

#include <AwRtpStream.h>
//#define RTP_DEBUG
//#define RTP_RESEQ

#ifdef RTP_DEBUG
#include <stdio.h>

static FILE *rtpfp = NULL;
void RtpDebugStorePkt(void *buf, int len)
{
    if (!rtpfp)
    {
        rtpfp = fopen("/sdcard/rtp.ts", "wb");
    }
    fwrite(buf, 1, len, rtpfp);
    return;
}

void RtpDebugSync(void)
{
    if (rtpfp)
    {
        fclose(rtpfp);
        rtpfp = NULL;
        sync();
    }
}

#endif


#define ABS_DIFF(seq1, seq2) \
    (seq1 > seq2 ? seq1 - seq2 : seq2 - seq1)

#define RTP_RECEIVE_BUF_SIZE 2048
#define RTP_PROBE_DATA_SIZE 2048

struct AwRtpStreamImplS
{
    CdxStreamT base;

    CdxDeliverT *msgDeliver;
    CdxHandlerItfT msgHdrItf;
    CdxHandlerT *msgHdr;
    
    RtpStreamItfT *itf;
    
    char *url;
    struct in_addr multiaddr;   /*ÍøÂçÐò */
    cdx_uint16 port;            /*ÍøÂçÐò */
    cdx_int32 skfd; 

    CdxListT pktList;
    cdx_int32 pktNum;
    cdx_uint32 highestSeqNumber;

    cdx_uint64 readPos;
    CdxBufferT *curBuf;

    CdxCondT queueCond;
    CdxMutexT queueMutex;
    
    CdxQueueT *cacheQueue;
    cdx_atomic_t cacheSize;
    cdx_int64 lastEnqueueTime; /*us*/
    cdx_uint32 nextEnqueueSeq;
    
    enum CdxIOStateE status;

    CdxStreamProbeDataT probeData;
    
    cdx_int32 exitFlag;
	cdx_int32 forceStop;
    AwPoolT *pool;
    cdx_bool multicast;
    cdx_uint32 basePts;
    cdx_uint32 timeScale;
};

enum RtpMessageWhatE
{
    RTP_MSG_READ,
};

/*time in ms*/
static cdx_uint32 TimestampToPts(cdx_uint32 rate, cdx_uint32 timestamp)
{
    cdx_uint64 ptsUs;
    ptsUs = (((cdx_uint64)timestamp) * 1000000LL) / (cdx_uint64)rate;
    return (cdx_uint32)(ptsUs/1000);
}

static cdx_int32 RtpParserUrl(char *url, struct in_addr *addr, cdx_uint16 *port)
{
    cdx_char strIp[32] = {0};
    cdx_uint16 portHost;
    cdx_int32 ret;
        
    CDX_CHECK(strncasecmp("rtp://", url, 6) == 0);
    
    ret = sscanf(url, "rtp://%15[^:]:%hu", strIp, &portHost);
    CDX_LOG_CHECK(ret == 2, "url (%s) ret (%s:%hu)", url, strIp, portHost);
    
    if (strIp[0] == '@')
    {
        if (strcmp(strIp, "@") == 0) /* ip str only a "@" ---> INADDR_ANY */
        {
            addr->s_addr = htonl(INADDR_ANY);
        }
        else
        {
            inet_aton(&strIp[1], addr);
        }
    }
    else
    {
        inet_aton(strIp, addr);
    }
    CDX_LOGD("get %s:%hu", strIp, portHost);

    *port = htons(portHost);
    
    return CDX_SUCCESS;
};

cdx_int32 RtpSocketCreate(struct in_addr addr, cdx_uint16 port)
{
    cdx_int32 skfd;
    cdx_int32 ret;
    struct sockaddr_in skaddr;
    const cdx_int32 recvBufSize = 1024 * 1024; /*1MByte*/
    
    skfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    CDX_FORCE_CHECK(skfd);
    
    memset(skaddr.sin_zero, 0, sizeof(skaddr.sin_zero));
    skaddr.sin_family = AF_INET;
    skaddr.sin_addr.s_addr = addr.s_addr;
    skaddr.sin_port = port;

    ret = bind(skfd, (const struct sockaddr *)&skaddr, sizeof(skaddr));
	if (ret != 0)
	{
		loge("%x:%hu, %d", addr.s_addr, port, errno);
		goto failure;
	}

    ret = setsockopt(skfd, SOL_SOCKET, SO_RCVBUF, &recvBufSize, sizeof(int));
    CDX_FORCE_CHECK(ret == 0);

    ret = CdxSockSetBlocking(skfd, 0);
    CDX_FORCE_CHECK(ret == 0);

	return skfd;
	
failure:
	if (skfd)
	{
		close(skfd);
	}
    return -1;
}

cdx_int32 MulticastJoinGroup(struct AwRtpStreamImplS *impl, struct in_addr multiaddr)
{	
    struct ip_mreq mreq;
	const unsigned char ttl=200;
    cdx_int32 ret;

    if (!impl->multicast)
    {
        CDX_LOGD("not multicast");
        return 0;
    }
    
	ret = setsockopt(impl->skfd, IPPROTO_IP, IP_MULTICAST_TTL, &ttl, sizeof(ttl)); 		  
    CDX_LOG_CHECK(ret == 0, "errno (%d)", errno);
    
    mreq.imr_multiaddr.s_addr = multiaddr.s_addr;

    mreq.imr_interface.s_addr = htonl(INADDR_ANY);
//	inet_aton("111.9.44.81", &mreq.imr_interface);// TODO: need local ip??
	
	ret = setsockopt(impl->skfd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq));		   
    CDX_LOG_CHECK(ret == 0, "errno (%d)", errno);

    return 0;
}  

cdx_int32 MulticastExitGroup(struct AwRtpStreamImplS *impl, struct in_addr multiaddr, cdx_uint16 port)
{
    struct ip_mreq mreq;
	//const unsigned char ttl=200;
    cdx_int32 ret;

	CDX_UNUSE(port);

    if (!impl->multicast)
    {
        CDX_LOGD("not multicast");
        return 0;
    }
    mreq.imr_multiaddr.s_addr = multiaddr.s_addr;

    mreq.imr_interface.s_addr = htonl(INADDR_ANY);
//	inet_aton("111.9.44.81", &mreq.imr_interface);// TODO: need local ip

    ret = setsockopt(impl->skfd, IPPROTO_IP, IP_DROP_MEMBERSHIP, &mreq, sizeof(mreq));
    CDX_LOG_CHECK(ret == 0, "errno (%d)", errno);

    return 0;
}

cdx_void RtpStreamDestroy(struct AwRtpStreamImplS *impl)
{
    CdxMutexDestroy(&impl->queueMutex);
    CdxCondDestroy(&impl->queueCond);

    if (impl->skfd > 0)
    {
        close(impl->skfd);
        impl->skfd = 0;
    }
    if (impl->msgDeliver)
    {
        CdxDeliverDestroy(impl->msgDeliver);
        impl->msgDeliver = NULL;
    }
    if (impl->msgHdr)
    {
        CdxHandlerDestroy(impl->msgHdr);
        impl->msgHdr = NULL;
    }
    if (impl->url)
    {
        CdxFree(impl->url);
        impl->url = NULL;
    }

    if (impl->cacheQueue)
    {
        CdxBufferT *buf = NULL;
        while (!CdxQueueEmpty(impl->cacheQueue))
        {
            buf = CdxQueuePop(impl->cacheQueue);
            CdxBufferDestroy(buf);
        }
        CdxQueueDestroy(impl->cacheQueue);
        impl->cacheQueue = NULL;
    }

    if (impl->curBuf)
    {
        CdxBufferDestroy(impl->curBuf);
        impl->curBuf = NULL;
    }

    if (impl->probeData.buf)
    {
        Pfree(impl->pool, impl->probeData.buf);
        impl->probeData.buf = NULL;
    }

    if (impl->itf)
    {
        impl->itf->ops->destroy(impl->itf);
    }

    AwPoolDestroy(impl->pool);
    CdxFree(impl);
    return ;
}

static cdx_void RtpStreamPrepareProbeData(struct AwRtpStreamImplS *impl, CdxBufferT *buf)
{
    unsigned int validSize = 0;
    if (impl->probeData.len >= RTP_PROBE_DATA_SIZE)
    {
        return ;
    }

    validSize = RTP_PROBE_DATA_SIZE - impl->probeData.len;
    validSize = validSize < CdxBufferGetSize(buf) ? validSize : CdxBufferGetSize(buf);
    memcpy(impl->probeData.buf + impl->probeData.len, CdxBufferGetData(buf), validSize);
    impl->probeData.len += validSize;
    if (impl->probeData.len == RTP_PROBE_DATA_SIZE)
    {   
        CDX_LOGD("rtp stream ready...");
        impl->status = CDX_IO_STATE_OK;
    }
    else
    {
        CDX_LOGD("(%p)cur probe data size (%d)", impl, impl->probeData.len);
    }
    return ;
}

/* return value: queue buf num */
static cdx_int32 RtpStreamCachePkt(struct AwRtpStreamImplS *impl, CdxBufferT *buf)
{
    int ret = 0;
    if (impl->itf)
    {
        CdxListT *bufList = NULL;
        struct RtpBufListNodeS *bufNode = NULL;
        if (impl->itf->ops->filter(impl->itf, buf, &bufList) == 0)
        {
            CDX_CHECK(bufList);
            CdxListForEachEntry(bufNode, bufList, node)
            {
                if (!bufNode->buf)
                {
                    CDX_LOGE("uri(%s)", impl->url);
                }
                RtpStreamPrepareProbeData(impl, bufNode->buf);
                CdxAtomicAdd(&impl->cacheSize, CdxBufferGetSize(bufNode->buf));
                CdxQueuePush(impl->cacheQueue, bufNode->buf);
                ret++;
                bufNode->buf = NULL;
            }
        }
    }
    else
    {
        RtpStreamPrepareProbeData(impl, buf);
        CdxAtomicAdd(&impl->cacheSize, CdxBufferGetSize(buf));
        CdxQueuePush(impl->cacheQueue, buf);
        ret++;
    }
    return ret;
}

#ifdef RTP_RESEQ
static cdx_int32 RtpStreamQueuePkt(struct AwRtpStreamImplS *impl)
{
    struct SeqBufferS *seqBuf, *next;
    cdx_int32 enqueueNum = 0;
    
    if (CdxGetNowUs() - impl->lastEnqueueTime > 200000) /*over 0.2s*/
    {
        CDX_LOGW("--------RTP Drop packet seq.(%u - %u)--------", impl->nextEnqueueSeq,
                (CdxListEntry(impl->pktList.head, struct SeqBufferS, node))->seqNum - 1);
    
        int index = 0;
        CDX_LOGD("------------------show pktlist ------------------------");
        CdxListForEachEntry(seqBuf, &impl->pktList, node)
        {
            CDX_LOGD("[%d]:%u", index++, seqBuf->seqNum);
        }
        CDX_LOGD("--------------------show end---------------------------");
        
        impl->nextEnqueueSeq = ((cdx_uint32)-1);
    }
    
    CdxListForEachEntrySafe(seqBuf, next, &impl->pktList, node)
    {
        if (impl->nextEnqueueSeq == seqBuf->seqNum 
            || impl->nextEnqueueSeq == (cdx_uint32)-1)
        {            
            enqueueNum += RtpStreamCachePkt(impl, seqBuf->val);
            impl->nextEnqueueSeq = seqBuf->seqNum + 1;
            CdxListDel(&seqBuf->node);
            CdxFree(seqBuf);
        }
    }

    if (enqueueNum)
    {
        impl->lastEnqueueTime = CdxGetNowUs();
        CdxMutexLock(&impl->queueMutex);
        CdxCondSignal(&impl->queueCond);
        CdxMutexUnlock(&impl->queueMutex);
        return 0;
    }
    return -1;
}
#endif

static cdx_int32 RtpStreamInsertPkt(struct AwRtpStreamImplS *impl, CdxBufferT *buffer)
{
    cdx_uint32 seqNum = 0;
    cdx_int32 ret;
    cdx_uint32 seq1, seq2, seq3, diff1, diff2, diff3;
    struct SeqBufferS *seqBuf;

    seqBuf = CdxMalloc(sizeof(*seqBuf));
    CDX_FORCE_CHECK(seqBuf);
    seqBuf->val = buffer;
    
    ret = CdxBufferFindInt32(seqBuf->val, "seqNumber", (cdx_int32 *)&seqNum);
    CDX_CHECK(ret == CDX_SUCCESS);
#ifdef RTP_RESEQ
    if (impl->pktNum++ == 0)
    {
        CDX_LOGD("recv first pkt seq (%u)", seqNum);
        impl->highestSeqNumber = seqNum;
        seqBuf->seqNum = seqNum;
        CdxListAddTail(&seqBuf->node, &impl->pktList);
        return CDX_SUCCESS;
    }    
#endif
    // Only the lower 16-bit of the sequence numbers are transmitted,
    // derive the high-order bits by choosing the candidate closest
    // to the highest sequence number (extended to 32 bits) received so far.

    seq1 = seqNum | (impl->highestSeqNumber & 0xffff0000);
    seq2 = seqNum | ((impl->highestSeqNumber & 0xffff0000) + 0x10000);
    seq3 = seqNum | ((impl->highestSeqNumber & 0xffff0000) - 0x10000);
    diff1 = ABS_DIFF(seq1, impl->highestSeqNumber);
    diff2 = ABS_DIFF(seq2, impl->highestSeqNumber);
    diff3 = ABS_DIFF(seq3, impl->highestSeqNumber);

    if (diff1 < diff2)
    {
        if (diff1 < diff3)
        {
            // diff1 < diff2 ^ diff1 < diff3
            seqNum = seq1;
        }
        else
        {
            // diff3 <= diff1 < diff2
            seqNum = seq3;
        }
    }
    else if (diff2 < diff3)
    {
        // diff2 <= diff1 ^ diff2 < diff3
        seqNum = seq2;
    }
    else
    {
        // diff3 <= diff2 <= diff1
        seqNum = seq3;
    }
    CdxBufferRemoveInt32(seqBuf->val, "seqNumber");
    CdxBufferSetInt32(seqBuf->val, "seqNumber", seqNum);
    seqBuf->seqNum = seqNum;
//    CDX_LOGD("add buf, seq (%u)", seqNum);

//#define PKT_LIST_HEAD_SEQNUM()
    /* nextEnqueueSeq <= pktList head node num <= higest seq Num */
#ifdef RTP_RESEQ
    if (seqNum > impl->highestSeqNumber)
    {
        impl->highestSeqNumber = seqNum;
        
        CdxListAddTail(&seqBuf->node, &impl->pktList);
        return CDX_SUCCESS;
    }
    else if (seqNum < impl->nextEnqueueSeq)
    {
        CDX_LOGW("-----------Coming the timeout packet seq.(%u)-----------", seqNum);
        CdxBufferDestroy(seqBuf->val); 
        CdxFree(seqBuf);
        return CDX_SUCCESS;
    }
    else if (((!CdxListEmpty(&impl->pktList)) 
                && seqNum < (CdxListEntry(impl->pktList.head, struct SeqBufferS, node))->seqNum))
    {
        CDX_LOGD("----Coming the little seq pkt (%u)-----", seqNum);
        CdxListAdd(&seqBuf->node, &impl->pktList);
        return CDX_SUCCESS;
    }
    else if (CdxListEmpty(&impl->pktList))
    {
        CdxListAdd(&seqBuf->node, &impl->pktList);
        return CDX_SUCCESS;
    }
    else /*pktlist not empty, seqnum middle...*/
    {
        ret = SeqBufferInsert(&impl->pktList, seqBuf);
        if (ret != CDX_SUCCESS)
        {
            CdxBufferDestroy(seqBuf->val);
            CdxFree(seqBuf);
            CDX_LOGE("insert failure...");
            return CDX_FAILURE;
        }
        return CDX_SUCCESS;
    }
#else /* not define RTP_RESEQ */
    {
        if (seqNum > impl->highestSeqNumber)
        {
            impl->highestSeqNumber = seqNum;
            if (RtpStreamCachePkt(impl, buffer) > 0)
            {
                //impl->lastEnqueueTime = CdxGetNowUs();
				CdxMutexLock(&impl->queueMutex);
				CdxCondSignal(&impl->queueCond);
				CdxMutexUnlock(&impl->queueMutex);
            }
        }
        else
        {
            CDX_LOGW("Coming the timeout packet seq.(%u, %u)", seqNum, impl->highestSeqNumber);
            CdxBufferDestroy(buffer); 
        }
        CdxFree(seqBuf);
        return CDX_SUCCESS;
    }
#endif
}

static cdx_int32 RtpStreamParsePkt(struct AwRtpStreamImplS *impl, CdxBufferT *buffer)
{
    cdx_uint32 bufSize = CdxBufferGetSize(buffer);
    const cdx_uint8 *data = CdxBufferGetData(buffer);
    cdx_int32 numCSRCs;
    cdx_size payloadOffset;
//    cdx_uint32 srcId;
//    CdxRtpSourceT *source;
    cdx_uint32 rtpTime;

//    impl->pktNum++;

    CDX_LOG_CHECK(bufSize >= 12, "bufSize(%u)", bufSize);

    if ((data[0] >> 6) != 2) /*version 2, RFC 1889*/
    {
        CDX_LOGE(" Unsupported version");
        return CDX_FAILURE;
    }

    if (data[0] & 0x20)
    {
        // Padding present.
        cdx_size paddingLength = data[bufSize - 1];

        if (paddingLength + 12 > bufSize)
        {
            // If we removed this much padding we'd end up with something
            CDX_LOGE("that's too short to be a valid RTP header.");
            return CDX_FAILURE;
        }
        bufSize -= paddingLength;
    }

    numCSRCs = data[0] & 0x0f;
    payloadOffset = 12 + 4 * numCSRCs;

    if (bufSize < payloadOffset)
    {
        CDX_LOGE("Not enough data to fit the basic header and all "
                "the CSRC entries.");
        return CDX_FAILURE;
    }

    if (data[0] & 0x10)
    {
        const cdx_uint8 *extensionData;
        cdx_size extensionLength;
        // Header eXtension present.
        if (bufSize < payloadOffset + 4)
        {
            CDX_LOGE("Not enough data to fit the basic header, all CSRC entries"
                     "and the first 4 bytes of the extension header.");
            return CDX_FAILURE;
        }

        extensionData = &data[payloadOffset];
        extensionLength = 4 * (extensionData[2] << 8 | extensionData[3]);

        if (bufSize < payloadOffset + 4 + extensionLength)
        {
            CDX_LOGE("format error");
            return CDX_FAILURE;
        }

        payloadOffset += 4 + extensionLength;
    }

    CdxBufferSetInt32(buffer, "marker", data[1] >> 7);
    CdxBufferSetInt32(buffer, "payloadType", data[1] & 0x7f); /*payload type*/
    CdxBufferSetInt32(buffer, "seqNumber", GetBE16(&data[2]));

    CdxBufferSetInt32(buffer, "ssrc", GetBE32(&data[8]));

    rtpTime = GetBE32(&data[4]);
    if (impl->basePts == (cdx_uint32)-1)
    {
        impl->basePts = rtpTime;
    }
    rtpTime -= impl->basePts;

    if (impl->timeScale)
    {
        rtpTime = TimestampToPts(impl->timeScale, rtpTime);
    }
    CdxBufferSetInt32(buffer, "rtp-time", rtpTime);
    
    CdxBufferSeekRange(buffer, payloadOffset, 0);

    return CDX_SUCCESS;
}

static CdxBufferT *RtpStreamReceivePkt(struct AwRtpStreamImplS *impl)
{
#define RECV_BUF_RESERVE_LEN 64
    struct timeval tv;
    fd_set rs;
    cdx_int32 ret;
    CdxBufferT *buffer = NULL;
    cdx_uint8 *data;
    cdx_ssize nbytes;
    int i = 0;

    FD_ZERO(&rs);
    FD_SET(impl->skfd, &rs);
            
    tv.tv_sec = 0;
    tv.tv_usec = CDX_SELECT_TIMEOUT;
    ret = select(impl->skfd + 1, &rs, NULL, NULL, &tv);

    if (ret <= 0)
    {
        CDX_LOGD("fd (%d) no read event... ret (%d)", impl->skfd, ret);
        return NULL;
    }
    
    if (!(FD_ISSET(impl->skfd, &rs)))
    {   
        CDX_LOGD("fd (%d) not in set", impl->skfd);
        return NULL;
    }
    
    buffer = CdxBufferCreate(impl->pool, 2048, NULL, 0);
    
    CdxBufferSetRange(buffer, RECV_BUF_RESERVE_LEN, 0);

    data = CdxBufferGetData(buffer);
    
    for (i = 0; i < 20; i++) /*try 20 times*/
    {
        nbytes = recvfrom(impl->skfd, data, 2048 - RECV_BUF_RESERVE_LEN, 0, NULL, NULL);
        if (nbytes > 0)
        {
            break;
        }
    } 

    if (nbytes <= 0)
    {
        CDX_LOGD("zero recv");
        CdxBufferDestroy(buffer);
        return NULL;
    }
    
    CdxBufferSeekRange(buffer, 0, nbytes);
    
    return buffer;
}

void RtpStreamReadInternal(struct AwRtpStreamImplS *impl)
{
    cdx_int32 ret;
    CdxBufferT *buffer;        
    
    buffer = RtpStreamReceivePkt(impl);
    if (!buffer)
    {
//        CDX_LOGD("no read event");
        return;
    }

    ret = RtpStreamParsePkt(impl, buffer);
    if (ret != 0)
    {
        CDX_LOGE("invilad packet...");
        return;
    }

    ret = RtpStreamInsertPkt(impl ,buffer);
    if (ret != 0)
    {
        CDX_LOGE("insert packet failure...");
        return;
    }

#ifdef RTP_RESEQ
    ret = RtpStreamQueuePkt(impl);
    if (ret != 0)
    {
        cdx_uint32 seq;
        CdxBufferFindInt32(buffer, "seqNumber", (cdx_int32 *)&seq);
        CDX_LOGW("not expect packet seq(%u)", seq);
    }
#endif
    
    return ;
}

static cdx_void __RtpMessageRecv(CdxHandlerItfT *itf, CdxMessageT *msg)
{
    struct AwRtpStreamImplS *impl = NULL;
    impl = CdxContainerOf(itf, struct AwRtpStreamImplS, msgHdrItf);
    
    switch (CdxMessageWhat(msg))
    {
    case RTP_MSG_READ:
    {
        RtpStreamReadInternal(impl);
   		
        if (impl->exitFlag)
        {
        	CDX_LOGW("RTP exist...");
        }
        else
        {
            CdxMessageT *readerMsg;
            readerMsg = CdxMessageCreate(impl->pool, RTP_MSG_READ, impl->msgHdr);
            CdxMessagePost(readerMsg);
        }
    }
    break;
    default:
        CDX_LOG_CHECK(0, "not implement...");
        break;
    }
}

static struct CdxHandlerItfOpsS rtpMsgHdrItfOps =
{
    .msgRecv = __RtpMessageRecv
};

static CdxStreamProbeDataT *__RtpStreamGetProbeData(CdxStreamT *stream)
{
    
    struct AwRtpStreamImplS *impl = NULL;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
    
    return &impl->probeData;
};

cdx_int32 RtpStreamReadCurBuf(struct AwRtpStreamImplS *impl, void *buf, cdx_uint32 len)
{
    cdx_uint8 *data;
    cdx_uint32 dataLen;
    cdx_int32 ret;

    if (impl->exitFlag)
    {
        CDX_LOGW("invalid handler..");
        return -1;
    }

    if (!impl->curBuf)
    {
        while (CdxQueueEmpty(impl->cacheQueue))
        {
            CdxMutexLock(&impl->queueMutex); /*lock*/
            CdxCondWait(&impl->queueCond, &impl->queueMutex);
            CdxMutexUnlock(&impl->queueMutex); /*unlock*/
            if (impl->exitFlag)
            {
                CDX_LOGD("should be exit now...");
                return -1;
            }
        }
        impl->curBuf = CdxQueuePop(impl->cacheQueue);
        CDX_CHECK(impl->curBuf);
    }
    
    data = CdxBufferGetData(impl->curBuf);
    dataLen = CdxBufferGetSize(impl->curBuf);
    if (dataLen <= len)
    {
        memcpy(buf, data, dataLen);
        CdxBufferDestroy(impl->curBuf);
        impl->curBuf = NULL;
        ret = dataLen;
    }
    else /*current data enought*/
    {
        memcpy(buf, data, len);
        CdxBufferSeekRange(impl->curBuf, len, 0);
        ret = len;
    }
    
    CdxAtomicSub(&impl->cacheSize, ret);
    impl->readPos += ret;
    return ret;
}

static cdx_int32 __RtpStreamRead(CdxStreamT *stream, void *buf, cdx_uint32 len)
{
    struct AwRtpStreamImplS *impl = NULL;
    cdx_int32 ret;
    cdx_uint32 readSize = 0;
   // cdx_uint8 *data;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
    for (;;)
    {
        ret = RtpStreamReadCurBuf(impl, (cdx_char *)buf + readSize, len - readSize);
        if (ret < 0)
        {
            CDX_LOGW("interrupt exit...");
            return ret;
        }

        readSize += ret;
        if (readSize == len)
        {
            break;
        }
    }

#ifdef RTP_DEBUG
    RtpDebugStorePkt(buf, len);
#endif
//    CDX_LOGD("get buf (%u)", readSize);
    return readSize;
}

static cdx_int32 __RtpStreamClose(CdxStreamT *stream)
{
    struct AwRtpStreamImplS *impl = NULL;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
    CDX_LOGD("close rtp stream...");
    
    if (!impl->exitFlag)
    {
        MulticastExitGroup(impl, impl->multiaddr, impl->port);
        impl->exitFlag = 1;
        CdxMutexLock(&impl->queueMutex);
        CdxCondSignal(&impl->queueCond);
        CdxMutexUnlock(&impl->queueMutex);
    }
    usleep(1000);
    RtpStreamDestroy(impl);
#ifdef RTP_DEBUG
    RtpDebugSync();
#endif
    return 0;
}
    
static cdx_int32 __RtpStreamGetIOState(CdxStreamT *stream)
{
    struct AwRtpStreamImplS *impl = NULL;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
    return impl->status;
}  

static cdx_uint32 __RtpStreamAttribute(CdxStreamT *stream)
{
	CDX_UNUSE(stream);
    return CDX_STREAM_FLAG_NET;
}

static cdx_int32 __RtpStreamControl(CdxStreamT *stream, cdx_int32 cmd, void *param)
{
    struct AwRtpStreamImplS *impl = NULL;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
	switch(cmd)
	{
	case STREAM_CMD_GET_CACHESTATE:
	{
#define ONE_MB (0x1 << 20)
        struct StreamCacheStateS *cacheState = param;
        int cacheSize = (int)CdxAtomicRead(&impl->cacheSize);

/* not printf here...
        if (cacheSize > (ONE_MB * 10) || cacheSize < ONE_MB/10)
        {
            CDX_LOGD("@%p, cache size(%d)", stream, cacheSize);
        }
*/
        cacheSize = (cacheSize > ONE_MB) ? ONE_MB : cacheSize;
        cacheState->nCacheCapacity = (0x1 << 20);
        cacheState->nCacheSize = cacheSize;
        cacheState->nBandwidthKbps = 1000;
        cacheState->nPercentage = 0; /*online*/
        return 0;
	}
	case STREAM_CMD_SET_FORCESTOP:
	{
		impl->forceStop = 1;
		
		if (!impl->exitFlag)
		{
			MulticastExitGroup(impl, impl->multiaddr, impl->port);
			impl->exitFlag = 1;
			CdxMutexLock(&impl->queueMutex);
			CdxCondSignal(&impl->queueCond);
			CdxMutexUnlock(&impl->queueMutex);
		}
	    return 0;
	}
	default:
	    CDX_LOGW("not implement cmd(%d)", cmd);
		break;
	}

    return -1;
}

static cdx_int32 __RtpStreamSeek(CdxStreamT *stream, cdx_int64 offset, cdx_int32 whence)
{
	CDX_UNUSE(stream);
	CDX_UNUSE(offset);
	CDX_UNUSE(whence);

    CDX_LOGD("not implement now...");    
    return -1;
}

#if 0 // not use now
static cdx_bool __RtpStreamEos(CdxStreamT *stream)
{
	CDX_UNUSE(stream);

    return CDX_FALSE;
}
#endif

static cdx_int64 __RtpStreamTell(CdxStreamT *stream)
{
    struct AwRtpStreamImplS *impl = NULL;
    
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);
    return impl->readPos;
}

cdx_int32 __RtpStreamConnect(CdxStreamT * stream)
{
    int ret = 0;
    CdxMessageT *readerMsg;
    struct AwRtpStreamImplS *impl = NULL;
    impl = CdxContainerOf(stream, struct AwRtpStreamImplS, base);

    ret = MulticastJoinGroup(impl, impl->multiaddr);
    if (ret != 0)
    {
        CDX_LOGE("join muticast group failure. errno (%d)", errno);
        return -1;
    }

    readerMsg = CdxMessageCreate(impl->pool, RTP_MSG_READ, impl->msgHdr);
    CdxMessagePost(readerMsg);

	while (1)
	{
		if (impl->status == CDX_IO_STATE_OK)
		{
			CDX_LOGD("RTP connected");
			break;
		}
		if (impl->forceStop)
		{
			CDX_LOGW("RTP force stop");	
			break;
		}
		usleep(1000);
	}
    return 0;
}

static struct CdxStreamOpsS rtpStreamOps =
{
    .connect = __RtpStreamConnect,
    .getProbeData = __RtpStreamGetProbeData,
    .read = __RtpStreamRead,
    .write = NULL,
    .close = __RtpStreamClose,
    .getIOState = __RtpStreamGetIOState,
    .attribute = __RtpStreamAttribute,
    .control = __RtpStreamControl,
    .getMetaData = NULL,
    .seek = __RtpStreamSeek,
    .seekToTime = NULL,
    .eos = NULL,
    .tell = __RtpStreamTell,
    .size = NULL,
};

/*  rtp://ip:port  */
CdxStreamT *__RtpStreamCreate(CdxDataSourceT *dataSource)
{
    struct AwRtpStreamImplS *impl;
    cdx_int32 ret;
    
    CDX_LOGD("open rtsp(%s)", dataSource->uri);
    impl = CdxMalloc(sizeof(*impl));
    
    CDX_LOG_CHECK(impl, "errno (%d)", errno);
    memset(impl, 0x00, sizeof(*impl));

    impl->base.ops = &rtpStreamOps;
    impl->msgHdrItf.ops = &rtpMsgHdrItfOps;
    impl->pool = AwPoolCreate(NULL);
    impl->multicast = CDX_TRUE;
    impl->timeScale = 0;
    
    if (dataSource->extraDataType == EXTRA_DATA_RTP)
    {
        struct RtpStreamExtraDataS *data = dataSource->extraData;
        impl->itf = data->itf;
        impl->multicast = data->multicast;
        impl->timeScale = data->timeScale;
    }
    
    CdxMutexInit(&impl->queueMutex);
    CdxCondInit(&impl->queueCond);

    impl->curBuf = NULL;
    impl->msgDeliver = CdxDeliverCreate(impl->pool);
    CDX_CHECK(impl->msgDeliver);
    impl->msgHdr = CdxHandlerCreate(impl->pool, &impl->msgHdrItf, impl->msgDeliver);

    impl->url = CdxStrdup(dataSource->uri);

    CDX_CHECK(strncmp(impl->url, "rtp://", 6) == 0);
    ret = RtpParserUrl(impl->url, &impl->multiaddr, &impl->port);
    CDX_CHECK(ret == 0);
    
    impl->skfd = RtpSocketCreate(impl->multiaddr, impl->port);
    if (impl->skfd <= 0)
    {
        CDX_LOGE("rtp socket create failure. errno (%d)", errno);
        goto failure;
    }

    CdxListInit(&impl->pktList);
    
    impl->cacheQueue = CdxQueueCreate(impl->pool);
    impl->nextEnqueueSeq = (cdx_uint32)-1;
    
    impl->exitFlag = 0;
	impl->forceStop = 0;
    impl->probeData.buf = Palloc(impl->pool, RTP_PROBE_DATA_SIZE);
    impl->probeData.len = 0;
    impl->status = CDX_IO_STATE_INVALID;
    impl->basePts = (cdx_uint32)-1;
    
    CDX_LOGD("rtp stream open success...");
    return &impl->base;
    
failure:
    RtpStreamDestroy(impl);
    return NULL;
    
}

CdxStreamCreatorT rtpStreamCreator =
{
    .create = __RtpStreamCreate
};

