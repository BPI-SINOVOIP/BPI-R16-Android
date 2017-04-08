/*******************************************************************************
--                                                                            --
--                    CedarX Multimedia Framework                             --
--                                                                            --
--          the Multimedia Framework for Linux/Android System                 --
--                                                                            --
--       This software is confidential and proprietary and may be used        --
--        only as expressly authorized by a licensing agreement from          --
--                         Softwinner Products.                               --
--                                                                            --
--                   (C) COPYRIGHT 2011 SOFTWINNER PRODUCTS                   --
--                            ALL RIGHTS RESERVED                             --
--                                                                            --
--                 The entire notice above must be reproduced                 --
--                  on all copies and should not be removed.                  --
--                                                                            --
*******************************************************************************/
#define LOG_TAG "CdxApeParser"
#include <CdxTypes.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxBinary.h>
#include "CdxApeParser.h"

#include <limits.h>
#include <fcntl.h> 
//#define BYTEALIGN
#define SENDTWOFRAME
static void ApeDumpInfo(APEParserImpl *ape_ctx)
{
    CDX_LOGV("blocksperframe       = %d\n", ape_ctx->blocksperframe);
    CDX_LOGV("duration             = %lld\n", ape_ctx->duration);
#if ENABLE_INFO_DEBUG
    cdx_uint32 i;

    CDX_LOGV("magic                = \"%c%c%c%c\"\n", ape_ctx->magic[0], ape_ctx->magic[1], ape_ctx->magic[2], ape_ctx->magic[3]);
    CDX_LOGV("fileversion          = %d\n", ape_ctx->fileversion);
    CDX_LOGV("descriptorlength     = %d\n", ape_ctx->descriptorlength);
    CDX_LOGV("headerlength         = %d\n", ape_ctx->headerlength);
    CDX_LOGV("seektablelength      = %d\n", ape_ctx->seektablelength);
    CDX_LOGV("wavheaderlength      = %d\n", ape_ctx->wavheaderlength);
    CDX_LOGV("audiodatalength      = %d\n", ape_ctx->audiodatalength);
    CDX_LOGV("audiodatalength_high = %d\n", ape_ctx->audiodatalength_high);
    CDX_LOGV("wavtaillength        = %d\n", ape_ctx->wavtaillength);
    CDX_LOGV("md5                  = ");
    for (i = 0; i < 16; i++)
         CDX_LOGV("%02x", ape_ctx->md5[i]);

    CDX_LOGV("\nHeader Block:\n\n");

    CDX_LOGV("compressiontype      = %d\n", ape_ctx->compressiontype);
    CDX_LOGV("formatflags          = %d\n", ape_ctx->formatflags);
    CDX_LOGV("finalframeblocks     = %d\n", ape_ctx->finalframeblocks);
    CDX_LOGV("totalframes          = %d\n", ape_ctx->totalframes);
    CDX_LOGV("bps                  = %d\n", ape_ctx->bps);
    CDX_LOGV("channels             = %d\n", ape_ctx->channels);
    CDX_LOGV("samplerate           = %d\n", ape_ctx->samplerate);

    CDX_LOGV("\nSeektable\n");
    if ((ape_ctx->seektablelength / sizeof(uint32_t)) != ape_ctx->totalframes) {
        CDX_LOGV("No seektable\n");
    } else {
        for (i = 0; i < ape_ctx->seektablelength / sizeof(uint32_t); i++) {
            if (i < ape_ctx->totalframes - 1) {
                CDX_LOGV("%8d   %d (%d bytes)\n", i, ape_ctx->seektable[i], ape_ctx->seektable[i + 1] - ape_ctx->seektable[i]);
            } else {
                CDX_LOGV("%8d   %d\n", i, ape_ctx->seektable[i]);
            }
        }
    }

    CDX_LOGV("\nFrames\n");
    for (i = 0; i < ape_ctx->totalframes; i++) {
        CDX_LOGV("%8d, pos = %8lld, size = %8d, samples = %d, skips %d\n", i, ape_ctx->frames[i].pos, ape_ctx->frames[i].size, ape_ctx->frames[i].nblocks, 
            ape_ctx->frames[i].skip);
        CDX_LOGV("%d, perframespts      = %8lld \n", i, ape_ctx->frames[i].pts);
    }
    CDX_LOGV("\nCalculated information:\n\n");
    CDX_LOGV("junklength           = %d\n", ape_ctx->junklength);
    CDX_LOGV("firstframe           = %d\n", ape_ctx->firstframe);
    CDX_LOGV("totalsamples         = %d\n", ape_ctx->totalsamples);
#endif
}

static int ApeIndexSearch(CdxParserT *parser, cdx_int64 timeUs)
{
    APEParserImpl *ape;
    int ret = 0;
    cdx_int32 frameindex;
    cdx_int32 samplesindex;
    cdx_int32 timeindex;
    cdx_int32 posdiff1;
    cdx_int32 posdiff2;
    cdx_int64 nseekpos = 0;
    
    ape = (APEParserImpl *)parser;
    if(!ape) {
        CDX_LOGE("ape file parser lib has not been initiated!");
        ret = -1;
        goto Exit;
    }
    timeindex = timeUs / 1E6;
    CDX_LOGV("timeindex %d", timeindex); 
    if (timeindex >= 0) {
        frameindex = (cdx_int32)timeindex * ape->samplerate / ape->blocksperframe;
        CDX_LOGV("frameindex %d", frameindex);
        if (frameindex > (cdx_int32)ape->totalframes - 1) {
            frameindex = ape->totalframes - 1;
        }
        else {
            samplesindex = (cdx_int32)timeindex * ape->samplerate * ape->nBlockAlign;
            posdiff1 = samplesindex - ape->frames[frameindex].pos;
            posdiff2 = ape->frames[frameindex + 1].pos - samplesindex;         
            if (posdiff1 >= 0 && posdiff2 >= 0) {
                #if 0
                if (posdiff1 < posdiff2) 
                    frameindex++;
                #endif
            }
        }
    } else {
        CDX_LOGW("ape file seekUs negative");
        frameindex = 0;
    }
    ape->currentframe = frameindex;    
    nseekpos = ape->frames[ape->currentframe].pos - (ape->frames[ape->currentframe].pos - ape->frames[0].pos) % 4;

    ret = CdxStreamSeek(ape->stream, nseekpos, SEEK_SET);
    if (ret < 0) {
        CDX_LOGW("CdxApeParserRead Failed to seek");
        ret = -1;
        goto Exit;
    }
    ape->nseeksession = 1;
    CDX_LOGV("ape file seekUs currentframe %d", ape->currentframe);
Exit:
    return ret;
   
}

static int CdxApeInit(CdxParserT* Parameter)
{
    APEParserImpl *ape;
    cdx_uint32 tag;
    cdx_uint32 i;  
    cdx_int64 pts = 0;
    
    ape = (APEParserImpl *)Parameter;

    ape->junklength = 0;
    tag = CdxStreamGetLE32(ape->stream);
    if (tag != MKTAG('M', 'A', 'C', ' ')) {
        ape->mErrno = PSR_OPEN_FAIL;
        goto Exit;
    }

    ape->fileversion = CdxStreamGetLE16(ape->stream);
    if (ape->fileversion >= 3980) {
        ape->padding1             = CdxStreamGetLE16(ape->stream);
        ape->descriptorlength     = CdxStreamGetLE32(ape->stream);
        ape->headerlength         = CdxStreamGetLE32(ape->stream);
        ape->seektablelength      = CdxStreamGetLE32(ape->stream);
        ape->wavheaderlength      = CdxStreamGetLE32(ape->stream);
        ape->audiodatalength      = CdxStreamGetLE32(ape->stream);
        ape->audiodatalength_high = CdxStreamGetLE32(ape->stream);
        ape->wavtaillength        = CdxStreamGetLE32(ape->stream);
        CdxStreamRead(ape->stream, ape->md5, 16);

        /* Skip any unknown bytes at the end of the descriptor.
           This is for future compatibility */
        if (ape->descriptorlength > 52)
            CdxStreamSeek(ape->stream, ape->descriptorlength - 52, SEEK_CUR);

        /* Read header data */
        ape->compressiontype      = CdxStreamGetLE16(ape->stream);
        ape->formatflags          = CdxStreamGetLE16(ape->stream);
        ape->blocksperframe       = CdxStreamGetLE32(ape->stream);
        ape->finalframeblocks     = CdxStreamGetLE32(ape->stream);
        ape->totalframes          = CdxStreamGetLE32(ape->stream);
        ape->bps                  = CdxStreamGetLE16(ape->stream);
        ape->channels             = CdxStreamGetLE16(ape->stream);
        ape->samplerate           = CdxStreamGetLE32(ape->stream);
    }else {
        ape->descriptorlength = 0;
        ape->headerlength = 32;

        ape->compressiontype      = CdxStreamGetLE16(ape->stream);
        ape->formatflags          = CdxStreamGetLE16(ape->stream);
        ape->channels             = CdxStreamGetLE16(ape->stream);
        ape->samplerate           = CdxStreamGetLE32(ape->stream);
        ape->wavheaderlength      = CdxStreamGetLE32(ape->stream);
        ape->wavtaillength        = CdxStreamGetLE32(ape->stream);
        ape->totalframes          = CdxStreamGetLE32(ape->stream);
        ape->finalframeblocks     = CdxStreamGetLE32(ape->stream);

        if (ape->formatflags & MAC_FORMAT_FLAG_HAS_PEAK_LEVEL) {
            CdxStreamSeek(ape->stream, 4, SEEK_CUR); /* Skip the peak level */
            ape->headerlength += 4;
        }

        if (ape->formatflags & MAC_FORMAT_FLAG_HAS_SEEK_ELEMENTS) {
            ape->seektablelength = CdxStreamGetLE32(ape->stream);
            ape->headerlength += 4;
            ape->seektablelength *= sizeof(int32_t);
        } else
            ape->seektablelength = ape->totalframes * sizeof(int32_t);

        if (ape->formatflags & MAC_FORMAT_FLAG_8_BIT)
            ape->bps = 8;
        else if (ape->formatflags & MAC_FORMAT_FLAG_24_BIT)
            ape->bps = 24;
        else
            ape->bps = 16;

        if (ape->fileversion >= 3950)
            ape->blocksperframe = 73728 * 4;
        else if (ape->fileversion >= 3900 || (ape->fileversion >= 3800  && ape->compressiontype >= 4000))
            ape->blocksperframe = 73728;
        else
            ape->blocksperframe = 9216;

        /* Skip any stored wav header */
        if (!(ape->formatflags & MAC_FORMAT_FLAG_CREATE_WAV_HEADER))
            CdxStreamSkip(ape->stream, ape->wavheaderlength);
    }

    ape->nBlockAlign = ape->bps / 8 * ape->channels;

    if (ape->totalframes > UINT_MAX / sizeof(APEFrame)) {
        CDX_LOGE("Too many frames %d\n", ape->totalframes);
        ape->mErrno = PSR_OPEN_FAIL;
        goto Exit;
    }
    ape->frames = malloc(ape->totalframes * sizeof(APEFrame));
    if (!ape->frames) {
        ape->mErrno = PSR_OPEN_FAIL;
        goto Exit;
    }

    ape->firstframe   = ape->junklength + ape->descriptorlength + ape->headerlength + ape->seektablelength + ape->wavheaderlength;
    ape->currentframe = 0;
    ape->nheadframe   = 1;

    ape->totalsamples = ape->finalframeblocks;
    if (ape->totalframes > 1)
        ape->totalsamples += ape->blocksperframe * (ape->totalframes - 1);

    if (ape->seektablelength > 0) {
        ape->seektable = malloc(ape->seektablelength);
        for (i = 0; i < ape->seektablelength / sizeof(cdx_uint32); i++)
            ape->seektable[i] = CdxStreamGetLE32(ape->stream);
    }

    ape->frames[0].pos     = ape->firstframe;
    ape->frames[0].nblocks = ape->blocksperframe;
    ape->frames[0].skip    = 0;
    for (i = 1; i < ape->totalframes; i++) {
        ape->frames[i].pos      = ape->seektable[i];
        ape->frames[i].nblocks  = ape->blocksperframe;
        ape->frames[i - 1].size = ape->frames[i].pos - ape->frames[i - 1].pos;
        ape->frames[i].skip     = (ape->frames[i].pos - ape->frames[0].pos) & 3;
    }

    ape->frames[ape->totalframes - 1].size    = ape->finalframeblocks * 4;
    ape->frames[ape->totalframes - 1].nblocks = ape->finalframeblocks;
#ifdef  BYTEALIGN
    for (i = 0; i < ape->totalframes; i++) {
        if(ape->frames[i].skip){
            ape->frames[i].pos  -= ape->frames[i].skip;
            ape->frames[i].size += ape->frames[i].skip;
        }
        ape->frames[i].size = (ape->frames[i].size + 3) & ~3;
    }
#endif
    ape->totalblock = (ape->totalframes == 0) ? 0 : (ape->totalframes - 1) * ape->blocksperframe + ape->finalframeblocks;
    ape->duration = (cdx_int64) ape->totalblock * AV_TIME_BASE / 1000 / ape->samplerate;
    for (i = 0; i < ape->totalframes; i++) {
        ape->frames[i].pts = pts;
        pts += (cdx_int64)ape->blocksperframe * AV_TIME_BASE  / ape->samplerate;
    }
    
    ape->mErrno = PSR_OK;
	pthread_cond_signal(&ape->cond);
	return 0;
Exit:
	pthread_cond_signal(&ape->cond);
    return -1;
}

static int CdxApeParserGetMediaInfo(CdxParserT *parser, CdxMediaInfoT *mediaInfo)
{
    APEParserImpl *ape;
    int ret = 0;    
    AudioStreamInfo *audio = NULL;
    
    ape = (APEParserImpl *)parser;
    if(!ape) {
        CDX_LOGE("ape file parser lib has not been initiated!");
        ret = -1;
        goto Exit;
    }

    mediaInfo->fileSize = CdxStreamSize(ape->stream);
    if (ape->seektablelength > 0 && CdxStreamSeekAble(ape->stream)) 
        mediaInfo->bSeekable = CDX_TRUE;
    else
        CDX_LOGW("Ape file Unable To Seek");

    ApeDumpInfo(ape);

    audio                   = &mediaInfo->program[0].audio[mediaInfo->program[0].audioNum];
    audio->eCodecFormat     = AUDIO_CODEC_FORMAT_APE;
    audio->nChannelNum      = ape->channels;	    
    audio->nSampleRate      = ape->samplerate;
    audio->nAvgBitrate      = ape->bitrate;
    audio->nMaxBitRate      = ape->bitrate;
    
    mediaInfo->program[0].audioNum++;
    mediaInfo->program[0].duration  = ape->duration;
    mediaInfo->bSeekable            = 1;
	/*for the request from ericwang, for */
	mediaInfo->programNum = 1;
	mediaInfo->programIndex = 0;
	/**/

Exit:    
    return ret;
}

static int CdxApeParserControl(CdxParserT *parser, cdx_int32 cmd, void *param)
{
    APEParserImpl *ape;

    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser prefetch failed!");
        return -1;
    }
    
    switch (cmd) {
        case CDX_PSR_CMD_DISABLE_AUDIO:
        case CDX_PSR_CMD_DISABLE_VIDEO:
        case CDX_PSR_CMD_SWITCH_AUDIO:
        	break;
        case CDX_PSR_CMD_SET_FORCESTOP:
        	CdxStreamForceStop(ape->stream);
          break;
        case CDX_PSR_CMD_CLR_FORCESTOP:
        	CdxStreamClrForceStop(ape->stream);
        	break;
        default:
            CDX_LOGW("not implement...(%d)", cmd);
            break;
    }
    
   return 0; 
}

static int CdxApeParserPrefetch(CdxParserT *parser, CdxPacketT *pkt)
{
    APEParserImpl *ape;

    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser prefetch failed!");
        return -1;
    }

    if (ape->currentframe >= ape->totalframes) {
        CDX_LOGD("Ape file is eos");
        return -1;
    }    

    pkt->type = CDX_MEDIA_AUDIO;
#ifdef SENDTWOFRAME
    if(ape->currentframe == 0 || ape->seek_flag)
    {
        if(ape->currentframe < ape->totalframes - 1)
			pkt->length = ape->frames[ape->currentframe].size + ape->frames[ape->currentframe+1].size;
		else
			pkt->length = ape->frames[ape->currentframe].size;
	}
	else
#endif
		pkt->length = ape->frames[ape->currentframe].size;

		
   	pkt->pts = ape->frames[ape->currentframe].pts;
    pkt->flags |= (FIRST_PART|LAST_PART);

    // First Frame
    if (ape->currentframe == 0 && !ape->nseeksession) {
        pkt->length += ape->firstframe;
    }    

    CDX_LOGV("pkt length %d, pkt pts %lld", pkt->length, pkt->pts);
    return 0;
}

static int CdxApeParserRead(CdxParserT *parser, CdxPacketT *pkt)
{
    APEParserImpl *ape;
    int ret = 0;
    int nblocks = 0;
    cdx_int64 nreadpos = 0;
    int nreadsize = 0;
    int nretsize  = 0;

    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser prefetch failed!");
        ret = -1;
        goto Exit;
    }

    if (ape->currentframe >= ape->totalframes) {
        CDX_LOGW("Ape file is eos");
    }    

    //nreadsize = ape->frames[ape->currentframe].size;
#ifdef SENDTWOFRAME   
    if(ape->currentframe == 0 || ape->seek_flag)
		nreadsize = ape->frames[ape->currentframe].size + ape->frames[ape->currentframe+1].size;
	else
#endif
		nreadsize = ape->frames[ape->currentframe].size;

		
    // using for headframe
    if (ape->nheadframe == 1) {
        nreadpos = 0;
        nreadsize += ape->firstframe;
        ret = CdxStreamSeek(ape->stream, nreadpos, SEEK_SET);
        if (ret < 0) {
            CDX_LOGW("CdxApeParserRead Failed to seek");
            ret = -1;
            goto Exit;
        }
        ape->nheadframe = 0;        
    } else {
        nreadpos = ape->frames[ape->currentframe].pos;
    }
    
    nretsize = CdxStreamRead(ape->stream, pkt->buf, nreadsize);
    if (nretsize <= 0) {
        CDX_LOGW("CdxApeParserRead Overflow");
        ret = -1;
        goto Exit;
    }
    
#if ENABLE_FILE_DEBUG
    CDX_LOGV("nreadpos %lld, nreadsize %d", nreadpos, nretsize);
    if (ape->teeFd >= 0) {
        write(ape->teeFd, pkt->buf, nretsize);
    }
#endif
   
    if (ape->currentframe == (ape->totalframes - 1))
        nblocks = ape->finalframeblocks;
    else
		nblocks = ape->blocksperframe;

    pkt->pts = ape->frames[ape->currentframe].pts;
    //ape->currentframe++;
#ifdef SENDTWOFRAME
	if(ape->currentframe == 0|| ape->seek_flag)
		ape->currentframe+=2;
	else
#endif
		ape->currentframe++;
    ape->seek_flag = 0;
Exit:
    return ret;
}

static int CdxApeParserSeekTo(CdxParserT *parser, cdx_int64 timeUs)
{
    APEParserImpl *ape;
    int ret = 0;

    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser seekto failed!");
        ret = -1;
        goto Exit;
    }

    // Clear
    ape->seek_flag = 1;
    ret = ApeIndexSearch(parser, timeUs);
    
Exit:    
    return ret;
}

static cdx_uint32 CdxApeParserAttribute(CdxParserT *parser)
{
    APEParserImpl *ape;
    int ret = 0;

    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser Attribute failed!");
        ret = -1;
        goto Exit;
    }
    
Exit:    
    return ret;
}

static int CdxApeParserGetStatus(CdxParserT *parser)
{
    APEParserImpl *ape;

    ape = (APEParserImpl *)parser;

    if (CdxStreamEos(ape->stream)) {
        CDX_LOGE("File EOS! ");
        return ape->mErrno = PSR_EOS;
    }
    return ape->mErrno;
}

static int CdxApeParserClose(CdxParserT *parser)
{
    APEParserImpl *ape;
    int ret = 0;
    ape = (APEParserImpl *)parser;
    if (!ape) {
        CDX_LOGE("Ape file parser prefetch failed!");
        ret = -1;
        goto Exit;
    }
    ape->exitFlag = 1;
    //pthread_join(ape->openTid, NULL);
#if ENABLE_FILE_DEBUG
    if (ape->teeFd) {
        close(ape->teeFd);
    }
#endif
    if (ape->frames) {
        free(ape->frames);
        ape->frames = NULL;
    }
    if (ape->seektable) {
        free(ape->seektable);
        ape->seektable = NULL;
    }
	if (ape->stream) {
		CdxStreamClose(ape->stream);
	}
	pthread_cond_destroy(&ape->cond);
    if (ape != NULL) {
        free(ape);
        ape = NULL;
    }

Exit:
    return ret;
}

static struct CdxParserOpsS ApeParserImpl = 
{
    .control      = CdxApeParserControl,
    .prefetch     = CdxApeParserPrefetch,
    .read         = CdxApeParserRead,
    .getMediaInfo = CdxApeParserGetMediaInfo,
    .seekTo       = CdxApeParserSeekTo,
    .attribute    = CdxApeParserAttribute,
    .getStatus    = CdxApeParserGetStatus,
    .close        = CdxApeParserClose,
    .init         = CdxApeInit
};

CdxParserT *CdxApeParserOpen(CdxStreamT *stream, cdx_uint32 flags)
{
    APEParserImpl *ApeParserImple;
    //int ret = 0;
    if(flags > 0) {
        CDX_LOGI("Flag Not Zero");
    }
    ApeParserImple = (APEParserImpl *)malloc(sizeof(APEParserImpl));
    if (ApeParserImple == NULL) {
        CDX_LOGE("ApeParserOpen Failed");
		CdxStreamClose(stream);
        return NULL;
    }
    memset(ApeParserImple, 0, sizeof(APEParserImpl));
    
    ApeParserImple->stream = stream;
    ApeParserImple->base.ops = &ApeParserImpl;
    ApeParserImple->mErrno = PSR_INVALID;
	pthread_cond_init(&ApeParserImple->cond, NULL);
    //ret = pthread_create(&ApeParserImple->openTid, NULL, ApeOpenThread, (void*)ApeParserImple);
   // CDX_FORCE_CHECK(!ret);
    
#if ENABLE_FILE_DEBUG
    char teePath[64];
    strcpy(teePath, "/data/camera/ape.dat");
    ApeParserImple->teeFd = open(teePath, O_WRONLY | O_CREAT | O_EXCL, 0775);   
#endif

    return &ApeParserImple->base;
}

static int ApeProbe(CdxStreamProbeDataT *p) 
{
    if (p->buf[0] == 'M' && p->buf[1] == 'A' && p->buf[2] == 'C' && p->buf[3] == ' ')
        return CDX_TRUE; 
	
	return CDX_FALSE;
}

static cdx_uint32 CdxApeParserProbe(CdxStreamProbeDataT *probeData)
{
    if (probeData->len < 4 || !ApeProbe(probeData)) {
        CDX_LOGE("Ape Probe Failed");
        return 0;
    }

    return 100;
}

CdxParserCreatorT apeParserCtor = 
{
    .create = CdxApeParserOpen,
    .probe = CdxApeParserProbe
};
