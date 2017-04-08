#include <CdxTsParser.h>
#include <CdxLog.h>

#define	SYNCWORDH			0xff
#define	SYNCWORDL			0xf0

static const int sampRateTab[12] =
{
    96000, 88200, 64000, 48000, 44100, 32000,
	24000, 22050, 16000, 12000, 11025,  8000
};


int GetAACDuration(const unsigned char *data, int datalen)
{
	int i                     = 0;
	int firstframe            = 1;
	unsigned int Duration     = 0; 	//ms
	unsigned int BitRate      = 0;
	unsigned int frameOn      = 0;
	unsigned char layer       = 0;
	unsigned char profile     = 0;
	unsigned char channelConfig = 0;
	unsigned int  sampRateIdx = 0;
	//unsigned int  SampleRate  = 0;
	int          frameLength = 0;

	unsigned char firlayer       = 0;
	unsigned char firprofile     = 0;
	unsigned char firchannelConfig = 0;
	unsigned int  firsampRateIdx = 0;
	unsigned int  firSampleRate  = 0;

	if(datalen < 4)
		return 0;

	for (i = 0; i < datalen - 5; i++)
	{
		if((data[i + 0] & SYNCWORDH) == SYNCWORDH && (data[i + 1] & SYNCWORDL) == SYNCWORDL)
		{
			if (firstframe)
			{
				firlayer       = (data[i + 1] >> 1) & 0x03;
				firprofile     = (data[i +2] >> 6) & 0x03;
				firsampRateIdx = (data[i+2] >> 2) & 0x0f;
				firSampleRate  = sampRateTab[firsampRateIdx];
				firchannelConfig = (((data[i+2] << 2) & 0x04) | ((data[i+3] >> 6) & 0x03));

				frameLength = ((int)(data[i+3] & 0x3) << 11) | ((int)(data[i+4] & 0xff) << 3) | ((data[i+5] >> 5) & 0x07);

				if (layer != 0  || sampRateIdx >= 12 || channelConfig >= 8 || frameLength > 2*1024*2)
				{
					continue;
				}

				firstframe  = 0;
				i += frameLength ;
				frameOn++;

			}

			if(i < datalen - 5)
			{
				layer       = (data[i+1] >> 1) & 0x03;
				profile     = (data[i+2] >> 6) & 0x03;
				sampRateIdx = (data[i+2] >> 2) & 0x0f;
				channelConfig = (((data[i+2] << 2) & 0x04) | ((data[i+3] >> 6) & 0x03));
				if ( layer != firlayer || profile != firprofile || sampRateIdx != firsampRateIdx || channelConfig != firchannelConfig)
					continue;

				frameLength = ((int)(data[i+3] & 0x3) << 11) | ((int)(data[i+4] & 0xff) << 3)|((data[i+5] >> 5) & 0x07);

				if (layer != 0  ||
					sampRateIdx >= 12 || channelConfig >= 8)
				{
					continue;
				}
				//if frameLength == 0, then i will decreased by 1 here,
				//and inceased by 1 in for statement, this is a dead loop.
				if(frameLength >= 1) {
					i += (frameLength - 1);
				}
				frameOn++;
			}
		}

	}

	if(frameOn > 0)
		BitRate = (int)((datalen*8*firSampleRate)/(frameOn*1024));

	if(BitRate > 0)
		Duration = datalen * 8000 / BitRate;


	return Duration;
}

#if PROBE_STREAM    

#define min(x, y)   ((x) <= (y) ? (x) : (y));

static unsigned char getbits(unsigned char* buffer, unsigned int from, unsigned char len)
{
    unsigned int n;
    unsigned char  m, u, l, y;

    n = from / 8;
    m = from % 8;
    u = 8 - m;
    l = (len > u ? len - u : 0);

    y = (buffer[n] << m);
    if(8 > len)
    	y  >>= (8-len);
    if(l)
    	y |= (buffer[n+1] >> (8-l));
    	
    return  y;
}

static __inline unsigned int getbits16(unsigned char* buffer, unsigned int from, unsigned char len)
{
    if(len > 8)
        return (getbits(buffer, from, len - 8) << 8) | getbits(buffer, from + len - 8, 8);
    else
        return getbits(buffer, from, len);
}

static unsigned int read_golomb(unsigned char* buffer, unsigned int* init)
{
    unsigned int x, v = 0, v2 = 0, m, len = 0, n = *init;

    while(getbits(buffer, n++, 1) == 0)
        len++;

    x = len + n;
    while(n < x)
    {
        m = min(x - n, 8);
        v |= getbits(buffer, n, m);
        n += m;
        if(x - n > 8)
            v <<= 8;
    }

    v2 = 1;
    for(n = 0; n < len; n++)
        v2 <<= 1;
    v2 = (v2 - 1) + v;

    //fprintf(stderr, "READ_GOLOMB(%u), V=2^%u + %u-1 = %u\n", *init, len, v, v2);
    *init = x;
    return v2;
}


static __inline int read_golomb_s(unsigned char* buffer, unsigned int* init)
{
    unsigned int v = read_golomb(buffer, init);
    return (v & 1) ? ((v + 1) >> 1) : -(v >> 1);
}

//* video probe functions
int prob_mpg(Stream *st)
{
    unsigned int code;
    unsigned int tmp;
    unsigned char* ptr;

    int frame_rate_table[16] = {0, 23976, 24000, 25000, 29970, 30000, 50000, 59940, 60000, 0, 0, 0, 0, 0, 0, 0};

    code = 0xffffffff;

    for(ptr = st->probeBuf; ptr <= st->probeBuf + st->probeDataSize - 6;)
    {
        code = code<<8 | *ptr++;
        if (code == 0x01b3) //* sequence header
        {
            tmp = ptr[0]<<24 | ptr[1]<<16 | ptr[2]<<8 | ptr[3];
            if(!st->metadata)
            {
                st->metadata = (VideoMetaData *)malloc(sizeof(VideoMetaData));
                memset(st->metadata, 0, sizeof(VideoMetaData));
            }
            else
            {
                CDX_LOGW("may be created yet.");
            }
            VideoMetaData *videoMetaData = (VideoMetaData *)st->metadata;
            videoMetaData->width = (tmp >> 20);
            videoMetaData->height = (tmp >> 8) & 0xfff;
            videoMetaData->frameRate = frame_rate_table[tmp & 0xf];
            videoMetaData->bitRate = (ptr[4]<<10 | ptr[5]<<2 | ptr[6]>>6) * 400;
            break;
        }
    }
    return 0;
}
int prob_mpg4(Stream *st)
{
    (void)st;
    return 0;
}
static int h264_parse_vui(VideoMetaData *videoMetaData, unsigned char* buf, unsigned int n)
{
    unsigned int overscan, vsp_color, chroma, timing, fixed_fps;
    unsigned int aspect_ratio_information, timeinc_unit, timeinc_resolution;
    unsigned int width, height;
    
    timeinc_unit = 0;
    timeinc_resolution = 0;

    if(getbits(buf, n++, 1))
    {
        aspect_ratio_information = getbits(buf, n, 8);
        n += 8;
        if(aspect_ratio_information == 255)
        {
            width = (getbits(buf, n, 8) << 8) | getbits(buf, n + 8, 8);
            n += 16;

            height = (getbits(buf, n, 8) << 8) | getbits(buf, n + 8, 8);
            n += 16;
        }
    }

    overscan = getbits(buf, n++, 1);
    if(overscan)
        n++;
        
    vsp_color=getbits(buf, n++, 1);
    if(vsp_color)
    {
        n += 4;
        if(getbits(buf, n++, 1))
            n += 24;
    }
    
    chroma=getbits(buf, n++, 1);
    if(chroma)
    {
        read_golomb(buf, &n);
        read_golomb(buf, &n);
    }
    
    timing=getbits(buf, n++, 1);
    if(timing)
    {
        timeinc_unit = (getbits(buf, n, 8) << 24) | (getbits(buf, n+8, 8) << 16) | (getbits(buf, n+16, 8) << 8) | getbits(buf, n+24, 8);
        n += 32;

        timeinc_resolution = (getbits(buf, n, 8) << 24) | (getbits(buf, n+8, 8) << 16) | (getbits(buf, n+16, 8) << 8) | getbits(buf, n+24, 8);
        n += 32;

        fixed_fps = getbits(buf, n, 1);

        if(timeinc_unit > 0 && timeinc_resolution > 0)
            videoMetaData->frameRate = timeinc_resolution * 1000 / timeinc_unit;
            
        if(fixed_fps)
            videoMetaData->frameRate /= 2;
    }
    
    return n;
}

static int h264_parse_sps(VideoMetaData *videoMetaData, unsigned char* buf, int len)
{
    unsigned int n = 0, v, i, k, mbh;
    int frame_mbs_only;

    (void)len;
    
    n = 24;
    read_golomb(buf, &n);
    if(buf[0] >= 100)
    {
        if(read_golomb(buf, &n) == 3)
            n++;
        read_golomb(buf, &n);
        read_golomb(buf, &n);
        n++;
        if(getbits(buf, n++, 1))
        {
            for(i = 0; i < 8; i++)
            {
                // scaling list is skipped for now
                if(getbits(buf, n++, 1))
                {
                    v = 8;
                    for(k = (i < 6 ? 16 : 64); k && v; k--)
                        v = (v + read_golomb_s(buf, &n)) & 255;
                }
            }
        }
    }
  
    read_golomb(buf, &n);
    v = read_golomb(buf, &n);
    if(v == 0)
        read_golomb(buf, &n);
    else if(v == 1)
    {
        getbits(buf, n++, 1);
        read_golomb(buf, &n);
        read_golomb(buf, &n);
        v = read_golomb(buf, &n);
        for(i = 0; i < v; i++)
            read_golomb(buf, &n);
    }
    
    read_golomb(buf, &n);
    getbits(buf, n++, 1);
    videoMetaData->width = 16 *(read_golomb(buf, &n)+1);
    mbh = read_golomb(buf, &n)+1;
    frame_mbs_only = getbits(buf, n++, 1);
    videoMetaData->height = 16 * (2 - frame_mbs_only) * mbh;
    if(!frame_mbs_only)
        getbits(buf, n++, 1);
    getbits(buf, n++, 1);
    if(getbits(buf, n++, 1))
    {
        read_golomb(buf, &n);
        read_golomb(buf, &n);
        read_golomb(buf, &n);
        read_golomb(buf, &n);
    }
    if(getbits(buf, n++, 1))
        n = h264_parse_vui(videoMetaData, buf, n);

    return n;
}

int prob_mvc(Stream *st)
{
    unsigned int       code;
    unsigned char*       ptr;
    code = 0xffffffff;

    for (ptr = st->probeBuf; ptr <= st->probeBuf + st->probeDataSize - 16;)
    {
        code = 0x1f &(*ptr++);
        if (code == 0xf) //* sequence header
        {
            if(!st->metadata)
            {
                st->metadata = (VideoMetaData *)malloc(sizeof(VideoMetaData));
                memset(st->metadata, 0, sizeof(VideoMetaData));
            }
            else
            {
                CDX_LOGW("may be created yet.");
            }
            VideoMetaData *videoMetaData = (VideoMetaData *)st->metadata;
            h264_parse_sps(videoMetaData, ptr, st->probeBuf + st->probeDataSize - ptr);
            break;
        }
    }
    return 0;
}

int prob_h264(Stream *st)
{
    unsigned int       code;
    unsigned char*       ptr;

    code = 0xffffffff;

    for (ptr = st->probeBuf; ptr <= st->probeBuf + st->probeDataSize - 16;)
    {
        code = code<<8 | *ptr++;
        if (code == 0x0167 || code == 0x0147 || code == 0x0127 || code == 0x0107) //* sequence header
        {
            if(!st->metadata)
            {
                st->metadata = (VideoMetaData *)malloc(sizeof(VideoMetaData));
                memset(st->metadata, 0, sizeof(VideoMetaData));
            }
            else
            {
                CDX_LOGW("may be created yet.");
            }
            VideoMetaData *videoMetaData = (VideoMetaData *)st->metadata;
            h264_parse_sps(videoMetaData, ptr, st->probeBuf + st->probeDataSize - ptr);
            break;
        }
    }
    return 0;
}

int vc1_decode_sequence_header(VideoMetaData *videoMetaData, unsigned char* buf, int len)
{
    int n, x;
    (void)len;
    
    n = 0;
    x = getbits(buf, n, 2);
    n += 2;
    if(x != 3) //not advanced profile
        return 0;

    getbits16(buf, n, 14);
    n += 14;
    videoMetaData->width = getbits16(buf, n, 12) * 2 + 2;
    n += 12;
    videoMetaData->height = getbits16(buf, n, 12) * 2 + 2;
    n += 12;
    getbits(buf, n, 6);
    n += 6;
    x = getbits(buf, n, 1);
    n += 1;
    if(x) //display info
    {
        getbits16(buf, n, 14);
        n += 14;
        getbits16(buf, n, 14);
        n += 14;
        if(getbits(buf, n++, 1)) //aspect ratio
        {
            x = getbits(buf, n, 4);
            n += 4;
            if(x == 15)
            {
                getbits16(buf, n, 16);
                n += 16;
            }
        }

        if(getbits(buf, n++, 1)) //framerates
        {
            int frexp=0, frnum=0, frden=0;

            if(getbits(buf, n++, 1))
            {
                frexp = getbits16(buf, n, 16);
                n += 16;
                videoMetaData->frameRate = (frexp+1)*1000 / 32.0;
            }
            else
            {
                unsigned int frates[] = {0, 24000, 25000, 30000, 50000, 60000, 48000, 72000, 0};
                unsigned int frdivs[] = {0, 1000, 1001, 0};

                frnum = getbits(buf, n, 8);
                n += 8;
                frden = getbits(buf, n, 4);
                n += 4;
                if((frden == 1 || frden == 2) && (frnum < 8))
                    videoMetaData->frameRate = frates[frnum]*1000 / frdivs[frden];
            }
        }
    }
    
    return 1;
}

int prob_vc1(Stream *st)
{
    unsigned int       code;
    unsigned char*       ptr;

    code = 0xffffffff;

    for (ptr = st->probeBuf; ptr <= st->probeBuf + st->probeDataSize - 16;)
    {
        code = code<<8 | *ptr++;
        if (code == 0x010f) //* sequence header
        {
            if(!st->metadata)
            {
                st->metadata = (VideoMetaData *)malloc(sizeof(VideoMetaData));
                memset(st->metadata, 0, sizeof(VideoMetaData));
            }
            else
            {
                CDX_LOGW("may be created yet.");
            }
            VideoMetaData *videoMetaData = (VideoMetaData *)st->metadata;
            vc1_decode_sequence_header(videoMetaData, ptr, st->probeBuf + st->probeDataSize - ptr);
            break;
        }
    }
    return 0;
}
static unsigned int h265_read_golomb(unsigned char* buffer, unsigned int* init)
{
    unsigned int x, v = 0, v2 = 0, m, len = 0, n = *init;

    while(getbits(buffer, n++, 1) == 0)
        len++;

    x = len + n;

    while(n < x)
    {
        m = min(x - n, 8); /* getbits() function, number of bits should not greater than 8 */
        v |= getbits(buffer, n, m);
        n += m;
        if(x - n > 8)
            v <<= 8;
        else if((x - n) > 0)
        	v <<= (x - n);
    }

    v2 = 1;
    for(n = 0; n < len; n++)
        v2 <<= 1;
    v2 = (v2 - 1) + v;

    *init = x;
    return v2;
}

static int h265_parse_sps_profile_tier_level(unsigned char* buf,
								unsigned int *len, unsigned int sps_max_sub_layers_minus1)
{
	unsigned int n = *len;
	unsigned char sub_layer_profile_present_flag[64] = { 0 };
	unsigned char sub_layer_level_present_flag[64] = { 0 };
	unsigned int i, j;

	n += 2; /* general_profile_space: u(2) */
	n += 1; /* general_tier_flag: u(1) */
	n += 5; /* general_profile_idc: u(5) */
	for(j = 0; j < 32; j++)
		n += 1; /* general_profile_compatibility_flag: u(1) */
	n += 1; /* general_progressive_source_flag: u(1) */
	n += 1; /* general_interlaced_source_flag: u(1) */
	n += 1; /* general_non_packed_constraint_flag: u(1) */
	n += 1; /* general_frame_only_constraint_flag: u(1) */
	n += 44; /* general_reserved_zero_44bits: u(44) */
	n += 8; /* general_level_idc: u(8) */

	for(i = 0; i < sps_max_sub_layers_minus1; i++)
	{
		sub_layer_profile_present_flag[i] = getbits(buf, n++, 1); /* u(1) */
		sub_layer_level_present_flag[i] = getbits(buf, n++, 1); /* u(1) */
	}
	if(sps_max_sub_layers_minus1 > 0)
		for(i = sps_max_sub_layers_minus1; i < 8; i++)
			n += 2; /* reserved_zero_2bits: u(2) */
	for(i = 0; i < sps_max_sub_layers_minus1; i++)
	{
		if(sub_layer_profile_present_flag[i])
		{
			n += 2; /* sub_layer_profile_space[i]: u(2) */
			n += 1; /* sub_layer_tier_flag[i]: u(1) */
			n += 5; /* sub_layer_profile_idc[i]: u(5) */
			for(j = 0; j < 32; j++)
				n += 1; /* sub_layer_profile_compatibility_flag[i][j]: u(1) */
			n += 1; /* sub_layer_progressive_source_flag[i]: u(1) */
			n += 1; /* sub_layer_interlaced_source_flag[i]: u(1) */
			n += 1; /* sub_layer_non_packed_constraint_flag[i]: u(1) */
			n += 1; /* sub_layer_frame_only_constraint_flag[i]: u(1) */
			n += 44; /* sub_layer_reserved_zero_44bits[i]: u(44) */
		}
		if(sub_layer_level_present_flag[i])
			n += 8; /* sub_layer_level_idc[i]: u(8)*/
	}

	(*len) = n;
	return 0;
}

static int h265_parse_sps(VideoMetaData *videoMetaData, unsigned char* buf, int len)
{
    unsigned int n = 0;
    unsigned int sps_max_sub_layers_minus1 = 0;
    unsigned int chroma_format_idc = 0;
    //unsigned int pic_width = 0;
    //unsigned int pic_height = 0;

    (void)len;
    
    n += 1;  /* forbidden_zero_bit: u(1)*/
    n += 6; /* nal_unit_type: u(6) */
    n += 6; /* nuh_layer_id: u(6) */
    n += 3; /* nuh_temporal_id_plus1: u(3) */

    n += 4;  /* sps_video_parameter_set_id: u(4) */
    sps_max_sub_layers_minus1 = getbits(buf, n, 3);
    n += 3;  /* sps_max_sub_layers_minus1:  u(3) */
    n += 1;  /* sps_temporal_id_nesting_flag: u(1) */
    h265_parse_sps_profile_tier_level(buf, &n, sps_max_sub_layers_minus1);
    h265_read_golomb(buf, &n); /* sps_seq_parameter_set_id: ue(v) */
    chroma_format_idc = h265_read_golomb(buf, &n); /* chroma_format_idc: ue(v) */
    if(chroma_format_idc == 3)
    	n += 1; /* separate_colour_plane_flag: u(1) */

    videoMetaData->width = h265_read_golomb(buf, &n); /* pic_width_in_luma_samples: ue(v) */
    videoMetaData->height = h265_read_golomb(buf, &n); /* pic_height_in_luma_samples: ue(v) */
    CDX_LOGD("zwh h265 parser prob pic width: %d, pic height: %d", videoMetaData->width, videoMetaData->height);
	return 0;
}
static int prob_h265_delete_emulation_code(cdx_uint8 *buf_out, cdx_uint8 *buf, cdx_int32 len)
{
	int i, size, skipped;
	cdx_int32 temp_value = -1;
	const cdx_uint32 mask = 0xFFFFFF;

	size = len;
	skipped = 0;
	for(i = 0; i < size; i++)
	{
		temp_value =(temp_value << 8)| buf[i];
		switch(temp_value & mask)
		{
		case 0x000003:
			skipped += 1;
			break;
		default:
			buf_out[i - skipped] = buf[i];
		}
	}

	return (size - skipped);
}

static int prob_h265(Stream *st)
{
    unsigned char *ptr, *ptr_nalu;
    unsigned char *nalu;
    int kk = 0, sps_nalu_len = 0;;
    int found = 0;

    for (ptr = st->probeBuf; ptr <= st->probeBuf + st->probeDataSize - 16;)
    {
    	if(ptr[0] == 0 && ptr[1] == 0 && ptr[2] == 1 && /*h265 nalu start code*/
    			ptr[3] == 0x42 && ptr[4] == 0x01 /*h265 sps nalu type*/)
    	{
    		ptr += 3;
    		ptr_nalu = ptr;
    		found = 1;
    		break; /* find sps, next we will get the size of sps_nalu by searching next start_code*/
    	}
    	++kk;
    	++ptr;
    }
    kk = 0;
    if(found == 1)
    {
    	for (; ptr <= st->probeBuf + st->probeDataSize - 16;)
    	{
    		if(ptr[0] == 0 && ptr[1] == 0 && ptr[2] == 1)
    		{
   				nalu = calloc(kk+16, 1);
    			sps_nalu_len = prob_h265_delete_emulation_code(nalu, ptr_nalu, kk);
                if(!st->metadata)
                {
                    st->metadata = (VideoMetaData *)malloc(sizeof(VideoMetaData));
                    memset(st->metadata, 0, sizeof(VideoMetaData));
                }
                else
                {
                    CDX_LOGW("may be created yet.");
                }
                VideoMetaData *videoMetaData = (VideoMetaData *)st->metadata;
                
    			h265_parse_sps(videoMetaData, nalu, sps_nalu_len);
    			free(nalu);
    			return 0;
    		}
        	++kk;
        	++ptr;
    	}
    }
	return 0;
}

int ProbeVideo(Stream *stream)
{
    if (stream->codec_id == VIDEO_CODEC_FORMAT_MPEG1 || stream->codec_id == VIDEO_CODEC_FORMAT_MPEG2)
    {
        return prob_mpg(stream);
    }
    else if (stream->codec_id == VIDEO_CODEC_FORMAT_MPEG4)
    {
        return prob_mpg4(stream);
    }
    else if (stream->codec_id == VIDEO_CODEC_FORMAT_H264)
    {
        return prob_h264(stream);
    }
    else if (stream->codec_id == VIDEO_CODEC_FORMAT_H265)
    {
        return prob_h265(stream);
    }
    else if (stream->codec_id == VIDEO_CODEC_FORMAT_WMV3)
    {
        return prob_vc1(stream);
    }
    else if (stream->codec_id == VIDEO_CODEC_FORMAT_AVS)
    {
    	return 0;
    }
    else
    {
        CDX_LOGE("should not be here.");
        return -1;
    }
}

int ProbeAudio(Stream *stream)
{
    (void)stream;
    return 0;
}

int ProbeStream(Stream *stream)
{
    if(stream->mMediaType == TYPE_VIDEO)
    {
        return ProbeVideo(stream);
    }
    else if(stream->mMediaType == TYPE_AUDIO)
    {
        return ProbeAudio(stream);
    }
    else
    {
        CDX_LOGE("should not be here.");
        return -1;
    }
}
#endif    

