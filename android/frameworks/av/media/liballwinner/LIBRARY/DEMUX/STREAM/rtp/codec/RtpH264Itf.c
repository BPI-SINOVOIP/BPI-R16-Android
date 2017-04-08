#include <CdxLog.h>
#include <CdxBuffer.h>
#include <CdxList.h>

#include <CdxStream.h>
#include <AwRtpStream.h>

#include <stdlib.h>

struct RtpH264ItfImplS
{
    RtpStreamItfT base;
    AwPoolT *pool;
    
    struct RtpStreamHdrS fragmentBufHdr;
    CdxListT bufList;
    int lastNalType; /*0:null, single nal; 24: STAP-A, 28:FU-A*/
//    cdx_uint32 rate;
};

static void H264FilterReset(struct RtpH264ItfImplS *impl)
{
    struct RtpBufListNodeS *bufNode, *nBufNode;
    CdxListForEachEntrySafe(bufNode, nBufNode, (&impl->bufList), node)
    {
        if (bufNode->buf)
        {
            int seqNumber = 0;
            CdxBufferFindInt32(bufNode->buf, "seqNumber", &seqNumber);
            CDX_LOGW("drop buf seq no.%d", seqNumber);
            
            CdxBufferDestroy(bufNode->buf);
            bufNode->buf = NULL;
        }
        CdxListDel(&bufNode->node);
        Pfree(impl->pool, bufNode);
    }
    impl->fragmentBufHdr.length = 0;
    impl->fragmentBufHdr.rtpTime = 0;
    impl->lastNalType = 0;
    return ;
}

static int __H264ItfFilter(RtpStreamItfT *itf, CdxBufferT *in, CdxListT **pOut)
{
    struct RtpH264ItfImplS *impl;
    struct RtpStreamHdrS hdr;
    cdx_uint8 *data = CdxBufferGetData(in);
    cdx_uint32 size = CdxBufferGetSize(in);
    int nalType;
    int seqNumber;
    cdx_uint32 rtpTime;
    
    impl = CdxContainerOf(itf, struct RtpH264ItfImplS, base);
    *pOut = NULL;
    nalType = data[0] & 0x1f;

    if (size < 1 || (data[0] & 0x80))
    {
        CDX_LOGW("Ignoring corrupt buffer.");
        CdxBufferDestroy(in);
        return -1;
    }

    if (impl->lastNalType != 28)
    {
        H264FilterReset(impl);
    }

    if (nalType >= 1 && nalType <= 23)
    {
        hdr.length = size + 4;
        hdr.type = CDX_MEDIA_VIDEO;
        CdxBufferFindInt32(in, "rtp-time", (int *)&rtpTime);
        hdr.rtpTime = rtpTime;
        CdxBufferInsert(in, (cdx_uint8 *)"\x00\x00\x00\x01", 4);
        CdxBufferInsert(in, (cdx_uint8 *)&hdr, sizeof(hdr));
        BufferListAdd(&impl->bufList, in, impl->pool);

        *pOut = &impl->bufList;
        impl->lastNalType = 0;
        return 0;
    } 
    else if (nalType == 28) /*FU-A*/
    {
        if (impl->lastNalType != 28)
        {
            if (data[1] & 0x80)
            {
//                impl->fragmentNri = (data[0] >> 5) & 0x03;
                CdxBufferFindInt32(in, "rtp-time", (int *)&rtpTime);
                impl->fragmentBufHdr.rtpTime = rtpTime;
//                CdxBufferFindInt32(in, "seqNumber", (cdx_int32 *)&impl->fragmentBufHdr.rtpTime);
                data[1] = (data[0] & 0xc0) | (data[1] & 0x1f);
                CdxBufferSeekRange(in, 1, 0);
                impl->fragmentBufHdr.length += CdxBufferGetSize(in);
                BufferListAdd(&impl->bufList, in, impl->pool);
                impl->lastNalType = 28;
                return -1; /*not complement...*/
            }
            else
            {
                CdxBufferFindInt32(in, "seqNumber", &seqNumber);
                CDX_LOGW("not start fragment, drop this no.%d", seqNumber);
                CdxBufferDestroy(in);
                impl->lastNalType = 0;
                return -1;
            }
        }
        else  /* impl->lastNalType == 28 */
        {
            cdx_uint32 fragmentNalType = data[1];
            CdxBufferSeekRange(in, 2, 0);
            impl->fragmentBufHdr.length += CdxBufferGetSize(in);
            BufferListAdd(&impl->bufList, in, impl->pool);

            if (fragmentNalType & 0x40)
            {
                CdxBufferT *hdrBuf;
                struct RtpBufListNodeS *bufNode;
                
                impl->fragmentBufHdr.type = CDX_MEDIA_VIDEO;
                impl->fragmentBufHdr.length += 4; /*00 00 00 01*/
                hdrBuf = CdxBufferCreate(impl->pool, 128, NULL, 0);
                CdxBufferAppend(hdrBuf, (cdx_uint8 *)&impl->fragmentBufHdr, sizeof(impl->fragmentBufHdr));
                CdxBufferAppend(hdrBuf, (cdx_uint8 *)"\x00\x00\x00\x01", 4);

                bufNode = Palloc(impl->pool, sizeof(*bufNode));
                bufNode->buf = hdrBuf;
                
                CdxListAdd(&bufNode->node, &impl->bufList);

                *pOut = &impl->bufList;
                impl->lastNalType = 0;
                return 0;
            }
            return -1;
        }
        
        CDX_LOG_CHECK(0, "don't come here...");
        return -1;
    } 
    else if (nalType == 24)
    {
        // STAP-A
        // TODO: implement this...
        CDX_LOG_CHECK(0, "not implement not"); 
        return 0;
    } 
    else 
    {
        CDX_LOGW("Ignoring unsupported buffer (nalType=%d)", nalType);

        CdxBufferDestroy(in);
 //       impl->lastNalType = 0;
        return -1;
    }

    CDX_LOG_CHECK(0, "don't come here...");    
    return 0;
}

void __H264ItfFilterDestroy(RtpStreamItfT *itf)
{
    struct RtpH264ItfImplS *impl;
    
    impl = CdxContainerOf(itf, struct RtpH264ItfImplS, base);

    H264FilterReset(impl);

    Pfree(impl->pool, impl);
    return ;
}

struct RtpStreamItfOpsS h264ItfOps =
{
    .filter = __H264ItfFilter,
    .destroy = __H264ItfFilterDestroy,
};

RtpStreamItfT *RtpH264ItfCreate(AwPoolT *pool, cdx_char *desc, cdx_char *params)
{
    struct RtpH264ItfImplS *impl = NULL;
    //char *rateStr = NULL;
	CDX_UNUSE(desc);
	CDX_UNUSE(params);
    
    impl = Palloc(pool, sizeof(*impl));
    
    impl->pool = pool;
    impl->base.ops = &h264ItfOps;
    CdxListInit(&impl->bufList);
    
    H264FilterReset(impl);

    return &impl->base;
}

