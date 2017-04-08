//#define CONFIG_LOG_LEVEL 4
#include <CdxLog.h>
#include <CdxBuffer.h>
#include <CdxList.h>

#include <CdxStream.h>
#include <AwRtpStream.h>

#include <stdlib.h>

//#include <CdxQueue.h>
//#include <CdxSeqBuffer.h>
//#include <CdxRtspUtil.h>
#include <CdxBitReader.h>
#include <CdxStrUtil.h>
#include <CdxBinary.h>
//#include <CdxSessionDescription.h>

struct RtpMpeg4ItfImplS
{
    RtpStreamItfT base;
    AwPoolT *pool;

    CdxListT bufList;
    cdx_bool mIsGeneric;
//    cdx_uint32 rate;
    cdx_char *mParams;

    cdx_uint32 mSizeLength;
    cdx_uint32 mIndexLength;
    cdx_uint32 mIndexDeltaLength;
    cdx_uint32 mCTSDeltaLength;
    cdx_uint32 mDTSDeltaLength;
    cdx_bool mRandomAccessIndication;
    cdx_uint32 mStreamStateIndication;
    cdx_uint32 mAuxiliaryDataSizeLength;
    cdx_bool mHasAUHeader;

    cdx_int32 mChannelConfig;
    cdx_uint32 mSampleRateIndex;

};

struct Mpeg4AUHeaderS
{
    cdx_uint32 mSize;
    cdx_uint32 mSerial;
    CdxListNodeT node;
};

void Mpeg4FilterReset(struct RtpMpeg4ItfImplS *impl)
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

    return ;
}


static int __Mpeg4ItfFilter(RtpStreamItfT *itf, CdxBufferT *in, CdxListT **pOut)
{
    struct RtpMpeg4ItfImplS *impl;
    cdx_err ret;
    cdx_uint32 rtpTime;
//    int bufSize = CdxBufferGetSize(in);
    int bufOffset = CdxBufferGetData(in) - CdxBufferGetBase(in);
    
    struct RtpStreamHdrS hdr = {0, 0, 0, 0};
    
    impl = CdxContainerOf(itf, struct RtpMpeg4ItfImplS, base);

    Mpeg4FilterReset(impl);
    ret = CdxBufferFindInt32(in, "rtp-time", (int *)&rtpTime);
    
    hdr.rtpTime = rtpTime;
    hdr.type = CDX_MEDIA_AUDIO;
    
    if (!impl->mIsGeneric)
    {
        hdr.length = CdxBufferGetSize(in);
        CdxBufferInsert(in, (cdx_uint8 *)&hdr, sizeof(hdr));
        BufferListAdd(&impl->bufList, in, impl->pool);
        *pOut = &impl->bufList;
        return 0;
    }
    else
    {
        cdx_uint8 *data;
        cdx_uint32 size;
        cdx_uint32 AU_headers_length;
        CdxBitReaderT *bits;
        cdx_uint32 numBitsLeft;
        cdx_uint32 AU_serial = 0;
        CdxListT headerList;
        cdx_uint32 offset;
        CdxListInit(&headerList);
        
        data = CdxBufferGetData(in);
        size = CdxBufferGetSize(in);
        CDX_CHECK(size > 2U);
        AU_headers_length = GetBE16(data);  // in bits
        CDX_CHECK(size >= 2 + (AU_headers_length + 7) / 8);

        bits = CdxBitReaderCreate(data + 2, size - 2);
        numBitsLeft = AU_headers_length;

        for (;;)
        {
            cdx_uint32 AU_size;
            cdx_uint32 n;
            struct Mpeg4AUHeaderS *header;
            cdx_uint32 AU_index;
            
            if (numBitsLeft < impl->mSizeLength)
            {
                break;
            }

            AU_size = CdxBitReaderGetBits(bits, impl->mSizeLength);
            numBitsLeft -= impl->mSizeLength;

            n = CdxListEmpty(&headerList) ? impl->mIndexLength : impl->mIndexDeltaLength;
            if (numBitsLeft < n)
            {
                break;
            }

            AU_index = CdxBitReaderGetBits(bits, n);
            numBitsLeft -= n;

            if (CdxListEmpty(&headerList))
            {
                AU_serial = AU_index;
            }
            else
            {
                AU_serial += 1 + AU_index;
            }

            if (impl->mCTSDeltaLength > 0)
            {
                if (numBitsLeft < 1)
                {
                    break;
                }
                --numBitsLeft;
                if (CdxBitReaderGetBits(bits, 1))
                {
                    if (numBitsLeft < impl->mCTSDeltaLength)
                    {
                        break;
                    }
                    CdxBitReaderSkipBits(bits, impl->mCTSDeltaLength);
                    numBitsLeft -= impl->mCTSDeltaLength;
                }
            }

            if (impl->mDTSDeltaLength > 0)
            {
                if (numBitsLeft < 1)
                {
                    break;
                }
                --numBitsLeft;
                if (CdxBitReaderGetBits(bits, 1))
                {
                    if (numBitsLeft < impl->mDTSDeltaLength)
                    {
                        break;
                    }
                    CdxBitReaderSkipBits(bits, impl->mDTSDeltaLength);
                    numBitsLeft -= impl->mDTSDeltaLength;
                }
            }

            if (impl->mRandomAccessIndication)
            {
                if (numBitsLeft < 1)
                {
                    break;
                }
                CdxBitReaderSkipBits(bits, 1);
                --numBitsLeft;
            }

            if (impl->mStreamStateIndication > 0)
            {
                if (numBitsLeft < impl->mStreamStateIndication)
                {
                    break;
                }
                CdxBitReaderSkipBits(bits, impl->mStreamStateIndication);
            }

            header = Palloc(impl->pool, sizeof(*header));
            CDX_FORCE_CHECK(header);
            header->mSize = AU_size;
            header->mSerial = AU_serial;
            CdxListAddTail(&header->node, &headerList); 
        }
        CdxBitReaderDestroy(bits);

        offset = 2 + (AU_headers_length + 7) / 8;

        if (impl->mAuxiliaryDataSizeLength > 0)
        {
            CdxBitReaderT *bits1;
            cdx_uint32 auxSize;
            bits1 = CdxBitReaderCreate(data + offset, size - offset);

            auxSize = CdxBitReaderGetBits(bits1, impl->mAuxiliaryDataSizeLength);

            offset += (impl->mAuxiliaryDataSizeLength + auxSize + 7) / 8;
            CdxBitReaderDestroy(bits1);
        }

        struct Mpeg4AUHeaderS *posHeader, *tmpHeader;
        CdxListForEachEntrySafe(posHeader, tmpHeader, &headerList, node)
        {
            hdr.length += posHeader->mSize;
            
            CdxListDel(&posHeader->node);
            Pfree(impl->pool, posHeader);
        }

        CdxBufferSetRange(in, bufOffset + offset, hdr.length);
        CdxBufferInsert(in, (cdx_uint8 *)&hdr, sizeof(hdr));

        BufferListAdd(&impl->bufList, in, impl->pool);
        *pOut = &impl->bufList;
    }
    
    return 0;
}

static void __Mpeg4ItfDestroy(RtpStreamItfT *itf)
{
    struct RtpMpeg4ItfImplS *impl;
    
    impl = CdxContainerOf(itf, struct RtpMpeg4ItfImplS, base);

    Mpeg4FilterReset(impl);
    Pfree(impl->pool, impl->mParams);
    Pfree(impl->pool, impl);
    
    return ;
}

struct RtpStreamItfOpsS mpeg4ItfOps =
{
    .filter = __Mpeg4ItfFilter,
    .destroy = __Mpeg4ItfDestroy,
};

cdx_void RtpMapParser(char *rtpmap, cdx_int32 *timescale, cdx_int32 *numChannels)
{
    const char *slash1 = strchr(rtpmap, '/');
    const char *s;
    cdx_char *end;
    cdx_long x;
    
    CDX_FORCE_CHECK(slash1 != NULL);

    s = slash1 + 1;

    x = strtoul(s, &end, 10);
    CDX_FORCE_CHECK(end > s);
    CDX_FORCE_CHECK(*end == '\0' || *end == '/');

    *timescale = x;
    *numChannels = 1;

    if (*end == '/') 
    {
        s = end + 1;
        x = strtoul(s, &end, 10);
        CDX_FORCE_CHECK(end > s);
        CDX_FORCE_CHECK(*end == '\0');

        *numChannels = x;
    }
    return ;
}

static cdx_err GetSampleRateIndex(cdx_int32 sampleRate, cdx_uint32 *tableIndex)
{
    const cdx_int32 kSampleRateTable[] =
    {
        96000, 88200, 64000, 48000, 44100, 32000,
        24000, 22050, 16000, 12000, 11025, 8000
    };
    cdx_uint32 index = 0;
    const cdx_uint32 kNumSampleRates = sizeof(kSampleRateTable) / sizeof(kSampleRateTable[0]);

    *tableIndex = 0;
    for (index = 0; index < kNumSampleRates; ++index)
    {
        if (sampleRate == kSampleRateTable[index])
        {
            *tableIndex = index;
            return CDX_SUCCESS;
        }
    }

    return CDX_FAILURE;
}

RtpStreamItfT *RtpMpeg4ItfCreate(AwPoolT *pool, cdx_char *desc, cdx_char *params)
{
    cdx_err ret;
    struct RtpMpeg4ItfImplS *impl;
    impl = Palloc(pool, sizeof(*impl));
    CDX_FORCE_CHECK(impl);

    impl->pool = pool;
    
    impl->mIsGeneric = CDX_FALSE;
    impl->mParams = Pstrdup(impl->pool, params);
    impl->mSizeLength = 0;
    impl->mIndexLength = 0;
    impl->mIndexDeltaLength = 0;
    impl->mCTSDeltaLength = 0;
    impl->mDTSDeltaLength = 0;
    impl->mRandomAccessIndication = CDX_FALSE;
    impl->mStreamStateIndication = 0;
    impl->mAuxiliaryDataSizeLength = 0;
    impl->mHasAUHeader = CDX_FALSE;
    impl->mChannelConfig = 0;
    impl->mSampleRateIndex = 0;

    CdxListInit(&impl->bufList);
    impl->mIsGeneric = !strncasecmp(desc, "mpeg4-generic/", 14);

    if (impl->mIsGeneric)
    {
        cdx_char *mode, *value;
        cdx_int32 sampleRate, numChannels;
        mode = CdxStrAttributeOfKey(impl->pool, params, "mode", ';');
        CDX_CHECK(mode);
        Pfree(impl->pool, mode);
        
        value = CdxStrAttributeOfKey(impl->pool, params, "sizeLength", ';');
        if (value)
        {
            impl->mSizeLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mSizeLength = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "indexLength", ';');
        if (value)
        {
            impl->mIndexLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mIndexLength = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "indexDeltaLength", ';');
        if (value)
        {
            impl->mIndexDeltaLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mIndexDeltaLength = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "CTSDeltaLength", ';');
        if (value)
        {
            impl->mCTSDeltaLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mCTSDeltaLength = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "DTSDeltaLength", ';');
        if (value)
        {
            impl->mDTSDeltaLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mDTSDeltaLength = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "randomAccessIndication", ';');
        if (value)
        {
            impl->mRandomAccessIndication = !!atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mRandomAccessIndication = CDX_FALSE;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "streamStateIndication", ';');
        if (value)
        {
            impl->mStreamStateIndication = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mStreamStateIndication = 0;
        }

        value = CdxStrAttributeOfKey(impl->pool, params, "auxiliaryDataSizeLength", ';');
        if (value)
        {
            impl->mAuxiliaryDataSizeLength = atoi(value);
            Pfree(impl->pool, value);
        }
        else
        {
            impl->mAuxiliaryDataSizeLength = 0;
        }

        CDX_LOGI("mpeg4-generic: %u,%u,%u,%u,%u,%d,%u,%u", 
                impl->mSizeLength,
                impl->mIndexLength,
                impl->mIndexDeltaLength,
                impl->mCTSDeltaLength,
                impl->mDTSDeltaLength,
                impl->mRandomAccessIndication,
                impl->mStreamStateIndication,
                impl->mAuxiliaryDataSizeLength);

        impl->mHasAUHeader =
                            impl->mSizeLength > 0
                            || impl->mIndexLength > 0
                            || impl->mIndexDeltaLength > 0
                            || impl->mCTSDeltaLength > 0
                            || impl->mDTSDeltaLength > 0
                            || impl->mRandomAccessIndication
                            || impl->mStreamStateIndication > 0;

        RtpMapParser(desc, &sampleRate, &numChannels);

//        impl->mChannelConfig = numChannels;
//        impl->rate = sampleRate;
        ret = GetSampleRateIndex(sampleRate, &impl->mSampleRateIndex);
        CDX_CHECK(ret == CDX_SUCCESS);
    }
    
    impl->base.ops = &mpeg4ItfOps;

    return &impl->base;
}

