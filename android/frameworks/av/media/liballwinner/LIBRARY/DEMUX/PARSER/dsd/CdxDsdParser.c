#include <CdxTypes.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxMemory.h>
#include <CdxDsdParser.h>
#define DSD_MATCH(x,y) (memcmp((x),(y),sizeof(x))==0)
#define GUINT16_FROM_LE(x)                  (cdx_uint16) (x)
#define GUINT32_FROM_LE(x)                  (cdx_uint32) (x)
#define GUINT64_FROM_LE(x)                  (cdx_uint64) (x)

#define GUINT16_FROM_BE(x)            \
	    ((((x) & 0xff00) >> 8) | \
	     (((x) & 0x00ff) << 8))
#define GUINT32_FROM_BE(x)                 \
	    ((((x) & 0xff000000) >> 24) | \
	     (((x) & 0x00ff0000) >> 8) |  \
	     (((x) & 0x0000ff00) << 8) |  \
	     (((x) & 0x000000ff) << 24))
#define GUINT64_FROM_BE(x)                            \
	    ((((x) & 0xff00000000000000) >> 56) | \
	     (((x) & 0x00ff000000000000) >> 40) | \
	     (((x) & 0x0000ff0000000000) >> 24) | \
	     (((x) & 0x000000ff00000000) >> 8) |  \
	     (((x) & 0x00000000ff000000) << 8) |  \
	     (((x) & 0x0000000000ff0000) << 24) | \
	     (((x) & 0x000000000000ff00) << 40) | \
	     (((x) & 0x00000000000000ff) << 56))

static cdx_int32 dsd_read_raw(void *buffer, cdx_int32 bytes, dsdfile *file) {
  size_t bytes_read;
  bytes_read = CdxStreamRead(file->stream,buffer, bytes);
  file->offset += (cdx_int64)bytes_read;
  
  return bytes_read;
}

static cdx_bool dsd_seek(dsdfile *file, cdx_int64 offset, int whence) {
  
	cdx_int8 buffer[8192];
	cdx_size read_bytes;
	cdx_int32 tmp;


	if (file->canseek)
	{
		if (CdxStreamSeek(file->stream, offset, whence) == 0)
		{
			file->offset += offset;
			return CDX_TRUE;
		} 
		else
		{
			return CDX_FALSE;
		}
	}


	if (whence == SEEK_CUR)
	read_bytes = offset;
	else if (whence == SEEK_SET)
	{
		if (offset < 0 || (cdx_uint64)offset < file->offset)
			return CDX_FALSE;
		read_bytes = offset - file->offset;
	} 
	else
		return CDX_FALSE;

	while (read_bytes > 0) 
	{
		tmp = (read_bytes > sizeof(buffer) ? sizeof(buffer) : read_bytes);
		if (dsd_read_raw(buffer, tmp, file) == tmp)
			read_bytes -= tmp;
		else
			return CDX_FALSE;
	}

	return CDX_TRUE;
}

cdx_bool read_header(dsdiff_header *head, dsdfile *file) {
  cdx_uint8 buffer[12];
  cdx_uint8 *ptr=buffer;
  if ((size_t)dsd_read_raw(buffer, sizeof(buffer), file) < sizeof(buffer)) return CDX_FALSE;
  head->size = GUINT64_FROM_BE(*((cdx_uint64*)ptr)); ptr+=8;
  memcpy(head->prop, ptr, 4);
  if (!DSD_MATCH(head->prop, "DSD ")) return CDX_FALSE;
  return CDX_TRUE;
}

cdx_bool read_chunk_header(dsdiff_chunk_header *head, dsdfile *file) {
  cdx_uint8 buffer[12];
  cdx_uint8 *ptr=buffer;
  if ((size_t)dsd_read_raw(buffer, sizeof(buffer), file) < sizeof(buffer)) return CDX_FALSE;
  memcpy(head->id, ptr, 4); ptr+=4;
  head->size = GUINT64_FROM_BE(*((cdx_uint64*)ptr));
  return CDX_TRUE;
}

cdx_bool parse_prop_chunk(dsdfile *file, dsdiff_chunk_header *head) {
  cdx_uint64 stop = file->offset + head->size;
  dsdiff_chunk_header prop_head;
  char propType[4];

  if ((size_t)dsd_read_raw(propType, sizeof(propType), file) < sizeof(propType)) return CDX_FALSE;

  if (!DSD_MATCH(propType, "SND ")) {
    if (dsd_seek(file, head->size - sizeof(propType), SEEK_CUR))
      return CDX_TRUE;
    else
      return CDX_FALSE;
  }
  
  while (file->offset < stop) {
    if (!read_chunk_header(&prop_head, file)) return CDX_FALSE;
    if (DSD_MATCH(prop_head.id, "FS  ")) {
      cdx_uint32 sample_frequency;
      if ((size_t)dsd_read_raw(&sample_frequency, sizeof(sample_frequency), file)< sizeof(sample_frequency)) return CDX_FALSE;
      file->sampling_frequency = GUINT32_FROM_BE(sample_frequency);
    } else if (DSD_MATCH(prop_head.id, "CHNL")) {
      cdx_uint16 num_channels;
      if ((size_t)dsd_read_raw(&num_channels, sizeof(num_channels), file) < sizeof(num_channels) ||
	  !dsd_seek(file, prop_head.size - sizeof(num_channels), SEEK_CUR))return CDX_FALSE;
      file->channel_num = (cdx_uint32)GUINT16_FROM_BE(num_channels);
	} else{
	  dsd_seek(file, prop_head.size, SEEK_CUR);
	}
  }
  
  return CDX_TRUE;
}

cdx_bool read_dsd_chunk(dsd_chunk *dsf_head, dsdfile *file) {
  cdx_uint8 buffer[24];
  cdx_uint8 *ptr=buffer;
  if ((size_t)dsd_read_raw(buffer, sizeof(buffer), file) < sizeof(buffer)) return CDX_FALSE;
  dsf_head->size_of_chunk = GUINT64_FROM_LE(*((cdx_uint64*)ptr)); ptr+=8;
  if (dsf_head->size_of_chunk != 28) return CDX_FALSE;
  dsf_head->total_size = GUINT64_FROM_LE(*((cdx_uint64*)ptr)); ptr+=8;
  dsf_head->ptr_to_metadata = GUINT64_FROM_LE(*((cdx_uint64*)ptr));
  return CDX_TRUE;
}

cdx_bool read_fmt_chunk(fmt_chunk *dsf_fmt, dsdfile *file) {
  cdx_uint8 buffer[52];
  cdx_uint8 *ptr=buffer;
  if ((size_t)dsd_read_raw(buffer, sizeof(buffer), file) < sizeof(buffer)) return CDX_FALSE;
  memcpy(dsf_fmt->header, ptr, 4); ptr+=4;
  if (!DSD_MATCH(dsf_fmt->header, "fmt ")) return CDX_FALSE;
  dsf_fmt->size_of_chunk = GUINT64_FROM_LE(*((cdx_uint64*)ptr)); ptr+=8;
  if (dsf_fmt->size_of_chunk != 52) return CDX_FALSE;
  dsf_fmt->format_version = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->format_id = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->channel_type = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->channel_num = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->sampling_frequency = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->bits_per_sample = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  dsf_fmt->sample_count = GUINT64_FROM_LE(*((cdx_uint64*)ptr)); ptr+=8;
  dsf_fmt->block_size_per_channel = GUINT32_FROM_LE(*((cdx_uint32*)ptr)); ptr+=4;
  //      !DSD_MATCH(dsf_fmt.reserved,"\0\0\0\0")) {
  return CDX_TRUE;
}

cdx_bool read_data_header(data_header *dsf_datahead, dsdfile *file) {
  cdx_uint8 buffer[12];
  cdx_uint8 *ptr=buffer;
  
  if ((size_t)dsd_read_raw(buffer, sizeof(buffer), file) < sizeof(buffer)) return CDX_FALSE;
  memcpy(dsf_datahead->header, ptr, 4); ptr+=4;
  if (!DSD_MATCH(dsf_datahead->header, "data")) return CDX_FALSE;
  dsf_datahead->size_of_chunk = GUINT64_FROM_LE(*((cdx_uint64*)ptr));
  return CDX_TRUE;
}

cdx_int32 aw_dsdiff_set_pos(dsdfile *file, cdx_uint32 mseconds, cdx_uint64 *offset) {
  cdx_uint64 skip_bytes = 0;
  cdx_int32  ret = 0;
  if (!file) return -1;
  //if(file->eof) return -1;

  file->sample_offset = (cdx_uint64)file->sampling_frequency * mseconds / 8000;
  skip_bytes = file->sample_offset * file->channel_num;

  *offset = skip_bytes;
  return 0;
}


cdx_int32 aw_dsf_set_pos(dsdfile *file, cdx_uint32 mseconds, cdx_uint64 *offset) {
  cdx_uint64 skip_blocks = 0;
  cdx_uint64 skip_bytes = 0;
  cdx_int32  ret = 0;
  if(!file) return -1;
  //if(file->eof) return -1;

  skip_blocks = (cdx_uint64) file->sampling_frequency * mseconds / (8000 * file->dsf.block_size_per_channel);
  skip_bytes = skip_blocks * file->dsf.block_size_per_channel * file->channel_num;

  file->sample_offset = skip_blocks * file->dsf.block_size_per_channel;

  *offset = skip_bytes;
  return 0;
}

static cdx_bool aw_dsf_read(dsdfile *file,cdx_int32 *len) {
  //if (file->eof) return CDX_FALSE;
  *len = file->channel_num * file->dsf.block_size_per_channel;


  if (file->dsf.block_size_per_channel >= (file->sample_stop - file->sample_offset)) {
    file->buffer.bytes_per_channel = (file->sample_stop - file->sample_offset);
    //file->eof = CDX_TRUE;
  } else {
    file->sample_offset += file->dsf.block_size_per_channel;
    file->buffer.bytes_per_channel = file->dsf.block_size_per_channel;
  }

  return CDX_TRUE;
}

static cdx_bool aw_dsdiff_read(dsdfile *file,cdx_int32 *len) {
  cdx_uint32 num_samples;
  //if (file->eof) return CDX_FALSE;
  if ((file->sample_stop - file->sample_offset) < file->buffer.max_bytes_per_ch)
    num_samples = file->sample_stop - file->sample_offset;
  else
    num_samples = file->buffer.max_bytes_per_ch;

  *len = file->channel_num * num_samples;

  file->buffer.bytes_per_channel = num_samples;
  file->sample_offset += num_samples;
  //if (file->sample_offset >= file->sample_stop) file->eof = CDX_TRUE;

  return CDX_TRUE;
}

static cdx_bool aw_dsdiff_init(dsdfile *file) {
  dsdiff_header head;
  dsdiff_chunk_header chunk_head;

  if (!read_header(&head, file)) return CDX_FALSE;
  file->file_size = head.size + 12;

  while (read_chunk_header(&chunk_head, file)) {
    if (DSD_MATCH(chunk_head.id, "FVER")) {
      char version[4];
      if (chunk_head.size != 4 ||
	  (size_t)dsd_read_raw(&version, sizeof(version), file)<sizeof(version) ||
	  version[0] > 1) { // Major version 1 supported
	return CDX_FALSE;
      }
    } else if (DSD_MATCH(chunk_head.id, "PROP")) {
      if (!parse_prop_chunk(file, &chunk_head)) return CDX_FALSE;
    } else if (DSD_MATCH(chunk_head.id, "DSD ")) {
      if (file->channel_num == 0) return CDX_FALSE;
      
      file->sample_offset = 0;
      file->sample_count = 8 * chunk_head.size / file->channel_num;
      file->sample_stop = file->sample_count / 8;

      file->dataoffset = file->offset;
      file->datasize = chunk_head.size;

      file->buffer.max_bytes_per_ch = 4096;
      file->buffer.lsb_first = CDX_FALSE;
      file->buffer.sample_step = file->channel_num;
      file->buffer.ch_step = 1;
      
      return CDX_TRUE;
    } else
      dsd_seek(file, chunk_head.size, SEEK_CUR);
  }

  return CDX_FALSE;
}

static cdx_bool aw_dsf_init(dsdfile *file) {  
  dsd_chunk dsf_head;
  fmt_chunk dsf_fmt;
  data_header dsf_datahead;
  cdx_int64 padtest;

  if (!read_dsd_chunk(&dsf_head, file)) return CDX_FALSE;
  
  if (!read_fmt_chunk(&dsf_fmt, file)) return CDX_FALSE;

  file->file_size = dsf_head.total_size;

  file->channel_num = dsf_fmt.channel_num;
  file->sampling_frequency = dsf_fmt.sampling_frequency;

  file->sample_offset = 0;
  file->sample_count = dsf_fmt.sample_count;
  file->sample_stop = file->sample_count / 8;

  file->dsf.format_version = dsf_fmt.format_version;
  file->dsf.format_id = dsf_fmt.format_id;
  file->dsf.channel_type = dsf_fmt.channel_type;
  file->dsf.bits_per_sample = dsf_fmt.bits_per_sample;
  file->dsf.block_size_per_channel = dsf_fmt.block_size_per_channel;

  if ((file->dsf.format_version != 1) ||
      (file->dsf.format_id != 0) ||
      (file->dsf.block_size_per_channel != 4096))
    return CDX_FALSE;

  if (!read_data_header(&dsf_datahead, file))
    return CDX_FALSE;

  padtest = (cdx_int64)(dsf_datahead.size_of_chunk - 12) / file->channel_num
    - file->sample_count / 8;
  if ((padtest < 0) || (padtest > file->dsf.block_size_per_channel)) {
    return CDX_FALSE;
  }

  file->dataoffset = file->offset;
  file->datasize = (file->sample_count / 8 * file->channel_num);
  
  file->buffer.max_bytes_per_ch = file->dsf.block_size_per_channel;
  file->buffer.lsb_first = (file->dsf.bits_per_sample == 1);
  file->buffer.sample_step = 1;
  file->buffer.ch_step = file->dsf.block_size_per_channel;

  return CDX_TRUE;
}

static cdx_bool aw_dsd_open(dsdfile *file) {
  cdx_uint8 header_id[4];
  file->canseek = CDX_TRUE;
  file->eof = CDX_FALSE;
  file->offset = 0;
  file->sample_offset = 0;
  file->channel_num = 0;
  file->sampling_frequency = 0;

  if((size_t)dsd_read_raw(header_id,sizeof(header_id),file)<sizeof(header_id)) return CDX_FALSE;

  if (DSD_MATCH(header_id, "DSD ")) {
    file->type = DSF;
    if (!aw_dsf_init(file)) {
      free(file);
      return CDX_FALSE;
    }
  } else if (DSD_MATCH(header_id, "FRM8")) {
    file->type = DSDIFF;
    if (!aw_dsdiff_init(file)) {
      free(file);
      return CDX_FALSE;
    }
  } else {
    free(file);
    return CDX_FALSE;
  }
  
  // Finalize buffer
  file->buffer.num_channels = file->channel_num;
  file->buffer.bytes_per_channel = 0;
  file->buffer.data = (cdx_uint8 *)malloc(sizeof(cdx_uint8) * file->buffer.max_bytes_per_ch * file->channel_num);
  return CDX_TRUE;
}




static int DsdInit(CdxParserT *dsd_impl)
{
    cdx_int32 ret = 0;
    cdx_int64 offset=0;
    cdx_int32 tmpFd = 0;
	cdx_int8 *buffer = NULL;
    unsigned char headbuf[10] = {0};    
    struct DsdParserImplS *impl          = NULL;
    impl = (DsdParserImplS*)dsd_impl;
	impl->fileSize = CdxStreamSize(impl->stream);
	
    impl->dsd = (dsdfile *)malloc(sizeof(dsdfile));
	memset(impl->dsd,0x00,sizeof(dsdfile));
	impl->dsd->stream = impl->stream;
	if(!aw_dsd_open(impl->dsd))
		goto OPENFAILURE;
	impl->file_offset = CdxStreamTell(impl->stream);
	buffer = malloc(impl->file_offset+INPUT_BUFFER_GUARD_SIZE);
	memset(buffer,0,impl->file_offset+INPUT_BUFFER_GUARD_SIZE);			CdxStreamSeek(impl->stream, 0,SEEK_SET);
	CdxStreamRead(impl->stream, buffer, impl->file_offset);
	impl->extradata = buffer;	impl->extradata_size = impl->file_offset;
	buffer = NULL;
    impl->mChannels   = impl->dsd->channel_num;
	impl->mSampleRate = 44100;
	impl->mOriSampleRate = impl->dsd->sampling_frequency;
	impl->mTotalSamps = impl->dsd->sample_stop;
	impl->mDuration   = impl->mTotalSamps * 8000/impl->mOriSampleRate;
	impl->mBitsSample = 16;
	CDX_LOGE("CK-----  impl->mChannels:%d,impl->mSampleRate:%d,impl->mOriSampleRate:%d,impl->mTotalSamps:%lld,impl->mDuration:%lld",impl->mChannels,impl->mSampleRate,impl->mOriSampleRate,impl->mTotalSamps,impl->mDuration);
	impl->mErrno = PSR_OK;
    return 0;
OPENFAILURE:
    CDX_LOGE("DsdOpenThread fail!!!");
    impl->mErrno = PSR_OPEN_FAIL;
    return -1;
}

static cdx_int32 __DsdParserControl(CdxParserT *parser, cdx_int32 cmd, void *param)
{
    struct DsdParserImplS *impl = NULL; 
    impl = (DsdParserImplS*)parser;
	(void)param;
 
     switch (cmd)
    {	    case CDX_PSR_CMD_DISABLE_AUDIO:	    case CDX_PSR_CMD_DISABLE_VIDEO:	    case CDX_PSR_CMD_SWITCH_AUDIO:    		break;	    case CDX_PSR_CMD_SET_FORCESTOP:            CdxStreamForceStop(impl->stream);	        break;	    case CDX_PSR_CMD_CLR_FORCESTOP:	    	CdxStreamClrForceStop(impl->stream);	    	break;	    default :	        CDX_LOGW("not implement...(%d)", cmd);	        break;    }    impl->flags = cmd;    return CDX_SUCCESS;
}

static cdx_int32 __DsdParserPrefetch(CdxParserT *parser, CdxPacketT *pkt)
{
    cdx_int32 ret = CDX_SUCCESS;
    struct DsdParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct DsdParserImplS, base);
    dsdfile * file = impl->dsd;
	if (file->type == DSF)
		if(!aw_dsf_read(file,&pkt->length))
			goto FAIL;
	if (file->type == DSDIFF) 
		if(!aw_dsdiff_read(file,&pkt->length))
			goto FAIL;

	pkt->type = CDX_MEDIA_AUDIO;
	pkt->pts = impl->mCurSamps*8000000ll/(impl->mOriSampleRate);//one frame pts;
	CDX_LOGV("pkt->pts:%lld",pkt->pts);
    pkt->flags |= (FIRST_PART|LAST_PART);
    return ret;
FAIL:
	return ret;

}

static cdx_int32 __DsdParserRead(CdxParserT *parser, CdxPacketT *pkt)
{
	cdx_int32 ret = CDX_FAILURE;
	cdx_int32 read_length= 0;
	CdxStreamT *cdxStream = NULL;
	dsdfile *   file = NULL;
	struct DsdParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct DsdParserImplS, base);
    cdxStream = impl->stream;
	file = impl->dsd;
	//READ ACTION	
	if(pkt->length <= pkt->buflen) 	
	{	    
		read_length = CdxStreamRead(cdxStream, pkt->buf, pkt->length);	
	}	
	else	
	{	    
		read_length = CdxStreamRead(cdxStream, pkt->buf, pkt->buflen);	   
		read_length += CdxStreamRead(cdxStream, pkt->ringBuf,	pkt->length - pkt->buflen);	
	}		
	if(read_length < 0)	
	{	    
		CDX_LOGE("CdxStreamRead fail");
		impl->mErrno = PSR_IO_ERR;	    
		return CDX_FAILURE;	
	}	
	else if(read_length == 0)
	{	   
       CDX_LOGE("CdxStream EOS");
	   impl->mErrno = PSR_EOS;
	   return CDX_FAILURE;
	}	
	pkt->length = read_length;	
	impl->file_offset += read_length;
	impl->mCurSamps = file->sample_offset;
	//TODO: fill pkt	
	return CDX_SUCCESS;
}

static cdx_int32 __DsdParserGetMediaInfo(CdxParserT *parser, CdxMediaInfoT *mediaInfo)
{
	struct CdxProgramS *cdxProgram = NULL;
    struct DsdParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct DsdParserImplS, base);
	memset(mediaInfo, 0x00, sizeof(*mediaInfo));
    if(impl->mErrno != PSR_OK)    {        CDX_LOGE("audio parse status no PSR_OK");        return CDX_FAILURE;    }	    mediaInfo->programNum = 1;    mediaInfo->programIndex = 0;    cdxProgram = &mediaInfo->program[0];    memset(cdxProgram, 0, sizeof(struct CdxProgramS));    cdxProgram->id = 0;    cdxProgram->audioNum = 1;    cdxProgram->videoNum = 0;//    cdxProgram->subtitleNum = 0;    cdxProgram->audioIndex = 0;    cdxProgram->videoIndex = 0;    cdxProgram->subtitleIndex = 0;          mediaInfo->bSeekable = CdxStreamSeekAble(impl->stream);    mediaInfo->fileSize = impl->fileSize;    	cdxProgram->duration = impl->mDuration;
	cdxProgram->audio[0].eCodecFormat    = AUDIO_CODEC_FORMAT_DSD;
	cdxProgram->audio[0].eSubCodecFormat = 0;	cdxProgram->audio[0].nChannelNum     = impl->mChannels;
	cdxProgram->audio[0].nBitsPerSample  = impl->mBitsSample;
	cdxProgram->audio[0].nSampleRate     = impl->mSampleRate;   
	cdxProgram->audio[0].nCodecSpecificDataLen = impl->extradata_size;	cdxProgram->audio[0].pCodecSpecificData = impl->extradata;	return CDX_SUCCESS;
	/*for the request from ericwang, for */
	mediaInfo->programNum = 1;
	mediaInfo->programIndex = 0;
	/**/
}

static cdx_int32 __DsdParserSeekTo(CdxParserT *parser, cdx_int64 timeUs)
{ 
	dsdfile *file = NULL;
	cdx_uint64 off = 0;
    struct DsdParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct DsdParserImplS, base); 
	cdx_uint32 mseconds = (cdx_uint32)(timeUs/1000);
	CDX_LOGE("SEEK TO timeUs:%lld",timeUs);
    file = impl->dsd;
	if (file->type == DSF)
		if(aw_dsf_set_pos(file,mseconds,&off))
			goto FAIL;
	if (file->type == DSDIFF) 
		if(aw_dsdiff_set_pos(file,mseconds,&off))
			goto FAIL;
	
	impl->mCurSamps = file->sample_offset;
    impl->file_offset = impl->extradata_size + off;
	CdxStreamSeek(impl->stream,impl->file_offset,SEEK_SET);
	return CDX_SUCCESS;
FAIL:
	return CDX_FAILURE;
}

static cdx_uint32 __DsdParserAttribute(CdxParserT *parser)
{
    struct DsdParserImplS *impl = NULL;
    impl = CdxContainerOf(parser, struct DsdParserImplS, base);
    return 0;
}
#if 0
static cdx_int32 __Id3ParserForceStop(CdxParserT *parser)
{
    struct Id3ParserImplS *impl = NULL;
	impl = CdxContainerOf(parser, struct Id3ParserImplS, base);
    return CdxParserForceStop(impl->child);
}
#endif
static cdx_int32 __DsdParserGetStatus(CdxParserT *parser)
{
    struct DsdParserImplS *impl = NULL;
	impl = CdxContainerOf(parser, struct DsdParserImplS, base);
    if (CdxStreamEos(impl->stream))    {        return PSR_EOS;    }    return impl->mErrno;
}

static cdx_int32 __DsdParserClose(CdxParserT *parser)
{
	struct DsdParserImplS *impl = NULL;
	impl = CdxContainerOf(parser, struct DsdParserImplS, base);
	
	CdxStreamClose(impl->stream);
	if(impl->extradata)
	{
		free(impl->extradata);
        impl->extradata = NULL;
	}

	CdxFree(impl);
	return CDX_SUCCESS;
}

static struct CdxParserOpsS dsdParserOps =
{
    .control = __DsdParserControl,
    .prefetch = __DsdParserPrefetch,
    .read = __DsdParserRead,
    .getMediaInfo = __DsdParserGetMediaInfo,
    .seekTo = __DsdParserSeekTo,
    .attribute = __DsdParserAttribute,
    .getStatus = __DsdParserGetStatus,
    .close = __DsdParserClose,
    .init = DsdInit
};

static cdx_uint32 __DsdParserProbe(CdxStreamProbeDataT *probeData)
{
	int ret = 0;
    CDX_CHECK(probeData);
    if(probeData->len < 10)
    {
        CDX_LOGE("Probe data is not enough.");
        return ret;
    }
    if (DSD_MATCH(probeData->buf, "DSD ")) 
	{
	    CDX_LOGE("YES,IT'S IS DSD");
		ret = 100;
	} 
	else if (DSD_MATCH(probeData->buf, "FRM8")) 
	{
		CDX_LOGE("YES,IT'S IS DSDIFF");
		ret = 100;
	}
    return ret;
}

static CdxParserT *__DsdParserOpen(CdxStreamT *stream, cdx_uint32 flags)
{
    cdx_int32 ret = 0;
    struct DsdParserImplS *impl;
    impl = CdxMalloc(sizeof(*impl));

    memset(impl, 0x00, sizeof(*impl));
 
    impl->stream = stream;
    impl->base.ops = &dsdParserOps;
	(void)flags;
    //ret = pthread_create(&impl->openTid, NULL, DsdOpenThread, (void*)impl);
    //CDX_FORCE_CHECK(!ret);
    impl->mErrno = PSR_INVALID;
    
    return &impl->base;
}

struct CdxParserCreatorS dsdParserCtor =
{
    .probe = __DsdParserProbe,
    .create = __DsdParserOpen
};
