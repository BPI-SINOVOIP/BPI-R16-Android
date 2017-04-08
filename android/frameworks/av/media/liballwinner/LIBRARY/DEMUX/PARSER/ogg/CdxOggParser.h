#ifndef OGG_PARSER_H
#define OGG_PARSER_H
#include <stdint.h>
#include <pthread.h>
#include <CdxTypes.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxAtomic.h>
#include <ctype.h>

#define OGG_SAVE_VIDEO_STREAM    (0)
#define OGG_SAVE_AUDIO_STREAM    (0)

#define MKTAG(a,b,c,d) ((a) | ((b) << 8) | ((c) << 16) | ((unsigned)(d) << 24))
#define Get8Bits(p)   (*p++)
#define GetLE16Bits(p)  ({\
    cdx_uint32 myTmp = Get8Bits(p);\
    myTmp | Get8Bits(p) << 8;})
#define GetLE32Bits(p)  ({\
    cdx_uint32 myTmp = GetLE16Bits(p);\
    myTmp | GetLE16Bits(p) << 16;})
#define GetLE64Bits(p) ({\
        cdx_uint64 myTmp = GetLE32Bits(p);\
        myTmp | (cdx_uint64)GetLE32Bits(p) << 32;})

#define FFMIN(a,b) ((a) > (b) ? (b) : (a))
#define FFMAX(a,b) ((a) > (b) ? (a) : (b))
#define FFABS(a) ((a) >= 0 ? (a) : (-(a)))

#define MAX_PAGE_SIZE 65307
#define DECODER_BUFFER_SIZE MAX_PAGE_SIZE
#define AV_NOPTS_VALUE          ((cdx_int64)-1)
#define OGG_NOGRANULE_VALUE (-1ull)
#define INT_MAX      0x7fffffff

#define AVERROR_EOF (-1)
#define AVERROR_INVALIDDATA (-2)
#define AVERROR_ENOMEM (-3)

#define MaxProbePacketNum (1024)
#define SIZE_OF_VIDEO_PROVB_DATA (2*1024*1024)

//#define AV_PKT_FLAG_KEY     0x0001 ///< The packet contains a keyframe
//#define AV_PKT_FLAG_CORRUPT 0x0002 ///< The packet content is corrupted

#define AV_METADATA_MATCH_CASE      1
#define AV_METADATA_IGNORE_SUFFIX   2
#define AV_METADATA_DONT_STRDUP_KEY 4
#define AV_METADATA_DONT_STRDUP_VAL 8
#define AV_METADATA_DONT_OVERWRITE 16



#define OGG_FLAG_CONT 1
#define OGG_FLAG_BOS  2
#define OGG_FLAG_EOS  4

enum AVMediaType {
    AVMEDIA_TYPE_UNKNOWN = -1,
    AVMEDIA_TYPE_VIDEO,
    AVMEDIA_TYPE_AUDIO,
    AVMEDIA_TYPE_DATA,
    AVMEDIA_TYPE_SUBTITLE,
    AVMEDIA_TYPE_ATTACHMENT,
    AVMEDIA_TYPE_NB
};

enum CodecID {
    CODEC_ID_NONE,

    // video codecs
    CODEC_ID_MPEG1VIDEO,
    CODEC_ID_MPEG2VIDEO, ///< preferred ID for MPEG-1/2 video decoding
    CODEC_ID_MPEG2VIDEO_XVMC,
    CODEC_ID_H261,
    CODEC_ID_H263,
    CODEC_ID_RV10,
    CODEC_ID_RV20,
    CODEC_ID_MJPEG,
    CODEC_ID_MJPEGB,
    CODEC_ID_LJPEG,
    CODEC_ID_SP5X,
    CODEC_ID_JPEGLS,
    CODEC_ID_MPEG4,
    CODEC_ID_RAWVIDEO,
    CODEC_ID_MSMPEG4V1,
    CODEC_ID_MSMPEG4V2,
    CODEC_ID_MSMPEG4V3,
    CODEC_ID_WMV1,
    CODEC_ID_WMV2,
    CODEC_ID_H263P,
    CODEC_ID_H263I,
    CODEC_ID_FLV1,
    CODEC_ID_SVQ1,
    CODEC_ID_SVQ3,
    CODEC_ID_DVVIDEO,
    CODEC_ID_HUFFYUV,
    CODEC_ID_CYUV,
    CODEC_ID_H264,
    CODEC_ID_INDEO3,
    CODEC_ID_VP3,
    CODEC_ID_THEORA,
    CODEC_ID_ASV1,
    CODEC_ID_ASV2,
    CODEC_ID_FFV1,
    CODEC_ID_4XM,
    CODEC_ID_VCR1,
    CODEC_ID_CLJR,
    CODEC_ID_MDEC,
    CODEC_ID_ROQ,
    CODEC_ID_INTERPLAY_VIDEO,
    CODEC_ID_XAN_WC3,
    CODEC_ID_XAN_WC4,
    CODEC_ID_RPZA,
    CODEC_ID_CINEPAK,
    CODEC_ID_WS_VQA,
    CODEC_ID_MSRLE,
    CODEC_ID_MSVIDEO1,
    CODEC_ID_IDCIN,
    CODEC_ID_8BPS,
    CODEC_ID_SMC,
    CODEC_ID_FLIC,
    CODEC_ID_TRUEMOTION1,
    CODEC_ID_VMDVIDEO,
    CODEC_ID_MSZH,
    CODEC_ID_ZLIB,
    CODEC_ID_QTRLE,
    CODEC_ID_SNOW,
    CODEC_ID_TSCC,
    CODEC_ID_ULTI,
    CODEC_ID_QDRAW,
    CODEC_ID_VIXL,
    CODEC_ID_QPEG,
    CODEC_ID_XVID,
    CODEC_ID_PNG,
    CODEC_ID_PPM,
    CODEC_ID_PBM,
    CODEC_ID_PGM,
    CODEC_ID_PGMYUV,
    CODEC_ID_PAM,
    CODEC_ID_FFVHUFF,
    CODEC_ID_RV30,
    CODEC_ID_RV40,
    CODEC_ID_VC1,
    CODEC_ID_WMV3,
    CODEC_ID_LOCO,
    CODEC_ID_WNV1,
    CODEC_ID_AASC,
    CODEC_ID_INDEO2,
    CODEC_ID_FRAPS,
    CODEC_ID_TRUEMOTION2,
    CODEC_ID_BMP,
    CODEC_ID_CSCD,
    CODEC_ID_MMVIDEO,
    CODEC_ID_ZMBV,
    CODEC_ID_AVS,
    CODEC_ID_SMACKVIDEO,
    CODEC_ID_NUV,
    CODEC_ID_KMVC,
    CODEC_ID_FLASHSV,
    CODEC_ID_CAVS,
    CODEC_ID_JPEG2000,
    CODEC_ID_VMNC,
    CODEC_ID_VP5,
    CODEC_ID_VP6,
    CODEC_ID_VP8,
    CODEC_ID_VP6F,
    CODEC_ID_TARGA,
    CODEC_ID_DSICINVIDEO,
    CODEC_ID_TIERTEXSEQVIDEO,
    CODEC_ID_TIFF,
    CODEC_ID_GIF,
    CODEC_ID_FFH264,
    CODEC_ID_DXA,
    CODEC_ID_DNXHD,
    CODEC_ID_THP,
    CODEC_ID_SGI,
    CODEC_ID_C93,
    CODEC_ID_BETHSOFTVID,
    CODEC_ID_PTX,
    CODEC_ID_TXD,
    CODEC_ID_VP6A,
    CODEC_ID_AMV,
    CODEC_ID_VB,
    CODEC_ID_PCX,
    CODEC_ID_SUNRAST,
    CODEC_ID_INDEO4,
    CODEC_ID_INDEO5,
    CODEC_ID_MIMIC,
    CODEC_ID_RL2,
    CODEC_ID_8SVX_EXP,
    CODEC_ID_8SVX_FIB,
    CODEC_ID_ESCAPE124,
    CODEC_ID_DIRAC,
    CODEC_ID_BFI,
	CODEC_ID_DIVX3,
	CODEC_ID_DIVX4,
	CODEC_ID_DIVX5,
	CODEC_ID_V_UNKNOWN,

    // various PCM "codecs"
    CODEC_ID_PCM_S16LE= 0x10000,
    CODEC_ID_PCM_S16BE,
    CODEC_ID_PCM_U16LE,
    CODEC_ID_PCM_U16BE,
    CODEC_ID_PCM_S8,
    CODEC_ID_PCM_U8,
    CODEC_ID_PCM_MULAW,
    CODEC_ID_PCM_ALAW,
    CODEC_ID_PCM_S32LE,
    CODEC_ID_PCM_S32BE,
    CODEC_ID_PCM_U32LE,
    CODEC_ID_PCM_U32BE,
    CODEC_ID_PCM_S24LE,
    CODEC_ID_PCM_S24BE,
    CODEC_ID_PCM_U24LE,
    CODEC_ID_PCM_U24BE,
    CODEC_ID_PCM_S24DAUD,
    CODEC_ID_PCM_ZORK,
    CODEC_ID_PCM_S16LE_PLANAR,
    CODEC_ID_PCM_DVD,

    // various ADPCM codecs
    CODEC_ID_ADPCM_IMA_QT= 0x11000,
    CODEC_ID_ADPCM_IMA_WAV,
    CODEC_ID_ADPCM_IMA_DK3,
    CODEC_ID_ADPCM_IMA_DK4,
    CODEC_ID_ADPCM_IMA_WS,
    CODEC_ID_ADPCM_IMA_SMJPEG,
    CODEC_ID_ADPCM_MS,
    CODEC_ID_ADPCM_4XM,
    CODEC_ID_ADPCM_XA,
    CODEC_ID_ADPCM_ADX,
    CODEC_ID_ADPCM_EA,
    CODEC_ID_ADPCM_G726,
    CODEC_ID_ADPCM_CT,
    CODEC_ID_ADPCM_SWF,
    CODEC_ID_ADPCM_YAMAHA,
    CODEC_ID_ADPCM_SBPRO_4,
    CODEC_ID_ADPCM_SBPRO_3,
    CODEC_ID_ADPCM_SBPRO_2,
    CODEC_ID_ADPCM_THP,
    CODEC_ID_ADPCM_IMA_AMV,
    CODEC_ID_ADPCM_EA_R1,
    CODEC_ID_ADPCM_EA_R3,
    CODEC_ID_ADPCM_EA_R2,
    CODEC_ID_ADPCM_IMA_EA_SEAD,
    CODEC_ID_ADPCM_IMA_EA_EACS,
    CODEC_ID_ADPCM_EA_XAS,
    CODEC_ID_ADPCM_EA_MAXIS_XA,

    // AMR
    CODEC_ID_AMR_NB= 0x12000,
    CODEC_ID_AMR_WB,

    // RealAudio codecs
    CODEC_ID_RA_144= 0x13000,
    CODEC_ID_RA_288,

    // various DPCM codecs
    CODEC_ID_ROQ_DPCM= 0x14000,
    CODEC_ID_INTERPLAY_DPCM,
    CODEC_ID_XAN_DPCM,
    CODEC_ID_SOL_DPCM,

    // audio codecs
    CODEC_ID_MP2= 0x15000,
    CODEC_ID_MP3, ///< preferred ID for decoding MPEG audio layer 1, 2 or 3
    CODEC_ID_AAC,
#if LIBAVCODEC_VERSION_INT < ((52<<16)+(0<<8)+0)
    CODEC_ID_MPEG4AAC,
#endif
    CODEC_ID_AC3,
    CODEC_ID_DTS,
    CODEC_ID_VORBIS,
    CODEC_ID_DVAUDIO,
    CODEC_ID_WMAV1,
    CODEC_ID_WMAV2,
    CODEC_ID_MACE3,
    CODEC_ID_MACE6,
    CODEC_ID_VMDAUDIO,
    CODEC_ID_SONIC,
    CODEC_ID_SONIC_LS,
    CODEC_ID_FLAC,
    CODEC_ID_MP3ADU,
    CODEC_ID_MP3ON4,
    CODEC_ID_SHORTEN,
    CODEC_ID_ALAC,
    CODEC_ID_WESTWOOD_SND1,
    CODEC_ID_GSM, ///< as in Berlin toast format
    CODEC_ID_QDM2,
    CODEC_ID_COOK,
    CODEC_ID_SIPRO,
    CODEC_ID_TRUESPEECH,
    CODEC_ID_TTA,
    CODEC_ID_SMACKAUDIO,
    CODEC_ID_QCELP,
    CODEC_ID_WAVPACK,
    CODEC_ID_DSICINAUDIO,
    CODEC_ID_IMC,
    CODEC_ID_MUSEPACK7,
    CODEC_ID_MLP,
    CODEC_ID_GSM_MS, // as found in WAV
    CODEC_ID_ATRAC3,
    CODEC_ID_VOXWARE,
    CODEC_ID_APE,
    CODEC_ID_NELLYMOSER,
    CODEC_ID_MUSEPACK8,
    CODEC_ID_SPEEX,
    CODEC_ID_WMAVOICE,
    CODEC_ID_WMAPRO,
    CODEC_ID_WMALOSSLESS,
    CODEC_ID_ATRAC3P,
	CODEC_ID_A_UNKNOWN,

    // subtitle codecs
    CODEC_ID_DVD_SUBTITLE= 0x17000,
    CODEC_ID_DVB_SUBTITLE,
    CODEC_ID_TEXT,  /// raw UTF-8 text
    CODEC_ID_XSUB,
    CODEC_ID_SSA,
    CODEC_ID_MOV_TEXT,

    // other specific kind of codecs (generally used for attachments)
    CODEC_ID_TTF= 0x18000,

    CODEC_ID_MPEG2TS= 0x20000, //_FAKE_ codec to indicate a raw MPEG-2 TS stream (only used by libavformat)
};

typedef struct AVCodecTag {
    int id;
    unsigned int tag;
} AVCodecTag;

static const AVCodecTag ff_codec_wav_tags[] = {
    { CODEC_ID_PCM_S16LE,       0x0001 },
    { CODEC_ID_PCM_U8,          0x0001 }, /* must come after s16le in this list */
    { CODEC_ID_PCM_S24LE,       0x0001 },
    { CODEC_ID_PCM_S32LE,       0x0001 },
    { CODEC_ID_ADPCM_MS,        0x0002 },
    //{ CODEC_ID_PCM_F32LE,       0x0003 },
    //{ CODEC_ID_PCM_F64LE,       0x0003 }, /* must come after f32le in this list */
    { CODEC_ID_PCM_ALAW,        0x0006 },
    { CODEC_ID_PCM_MULAW,       0x0007 },
    { CODEC_ID_WMAVOICE,        0x000A },
    { CODEC_ID_ADPCM_IMA_WAV,   0x0011 },
    { CODEC_ID_PCM_ZORK,        0x0011 }, /* must come after adpcm_ima_wav in this list */
    { CODEC_ID_ADPCM_YAMAHA,    0x0020 },
    { CODEC_ID_TRUESPEECH,      0x0022 },
    { CODEC_ID_GSM_MS,          0x0031 },
    { CODEC_ID_ADPCM_G726,      0x0045 },
    { CODEC_ID_MP2,             0x0050 },
    { CODEC_ID_MP3,             0x0055 },
    { CODEC_ID_AMR_NB,          0x0057 },
    { CODEC_ID_AMR_WB,          0x0058 },
    { CODEC_ID_ADPCM_IMA_DK4,   0x0061 },  /* rogue format number */
    { CODEC_ID_ADPCM_IMA_DK3,   0x0062 },  /* rogue format number */
    { CODEC_ID_ADPCM_IMA_WAV,   0x0069 },
    { CODEC_ID_VOXWARE,         0x0075 },
    { CODEC_ID_AAC,             0x00ff },
    //{ CODEC_ID_SIPR,            0x0130 },
    { CODEC_ID_WMAV1,           0x0160 },
    { CODEC_ID_WMAV2,           0x0161 },
    { CODEC_ID_WMAPRO,          0x0162 },
    { CODEC_ID_WMALOSSLESS,     0x0163 },
    { CODEC_ID_ADPCM_CT,        0x0200 },
    { CODEC_ID_ATRAC3,          0x0270 },
    { CODEC_ID_IMC,             0x0401 },
    { CODEC_ID_GSM_MS,          0x1500 },
    { CODEC_ID_TRUESPEECH,      0x1501 },
    { CODEC_ID_AC3,             0x2000 },
    { CODEC_ID_DTS,             0x2001 },
    { CODEC_ID_SONIC,           0x2048 },
    { CODEC_ID_SONIC_LS,        0x2048 },
    { CODEC_ID_PCM_MULAW,       0x6c75 },
    { CODEC_ID_AAC,             0x706d },
    { CODEC_ID_AAC,             0x4143 },
    { CODEC_ID_FLAC,            0xF1AC },
    { CODEC_ID_ADPCM_SWF,       ('S'<<8)+'F' },
    { CODEC_ID_VORBIS,          ('V'<<8)+'o' }, //HACK/FIXME, does vorbis in WAV/AVI have an (in)official id?

    /* FIXME: All of the IDs below are not 16 bit and thus illegal. */
    // for NuppelVideo (nuv.c)
    { CODEC_ID_PCM_S16LE, MKTAG('R', 'A', 'W', 'A') },
    { CODEC_ID_MP3,       MKTAG('L', 'A', 'M', 'E') },
    { CODEC_ID_MP3,       MKTAG('M', 'P', '3', ' ') },
    { CODEC_ID_NONE,      0 },
};

enum AVStreamParseType {
    AVSTREAM_PARSE_NONE,
    AVSTREAM_PARSE_FULL,       /**< full parsing and repack */
    AVSTREAM_PARSE_HEADERS,    /**< Only parse headers, do not repack. */
    AVSTREAM_PARSE_TIMESTAMPS, /**< full parsing and interpolation of timestamps for frames not starting on a packet boundary */
};

struct oggvorbis_private {
    cdx_uint32 len[3];
    unsigned char *packet[3];
};

typedef struct {
    char *key;
    char *value;
}AVMetadataTag;

typedef struct{
    int count;
    AVMetadataTag *elems;
}AVMetadata;

typedef struct AVRational{
    int num; ///< numerator
    int den; ///< denominator
} AVRational;

typedef struct AVChapter {
    int id;                 ///< unique ID to identify the chapter
    AVRational time_base;   ///< time base in which the start/end timestamps are specified
    long long  start, end;     ///< chapter start/end time in time_base units
#if LIBAVFORMAT_VERSION_INT < (53<<16)
    char *title;            ///< chapter title
#endif
    AVMetadata *metadata;
} AVChapter;

typedef struct AVCodecContext {
	enum AVMediaType codec_type; /* see AVMEDIA_TYPE_xxx */
    enum CodecID codec_id; /* see CODEC_ID_xxx */

	unsigned char *extradata;
    int extradata_size;

	int bit_rate;
	unsigned int codec_tag;

	int width, height;
	AVRational time_base;
    cdx_int64 nFrameDuration;
	int sample_rate;
	int channels;

	AVRational sample_aspect_ratio;

	long long reordered_opaque;

}AVCodecContext;


typedef struct AVFrac {
    long long val, num, den;
} AVFrac;


typedef struct AVStream {
    int index;    /**< stream index in AVFormatContext */
    int id;       /**< format-specific stream ID */
    AVCodecContext *codec; /**< codec context */

    /* internal data used in av_find_stream_info() */
    long long first_dts;
    /** encoding: pts generation when outputting stream */
    struct AVFrac pts;

    /**
     * This is the fundamental unit of time (in seconds) in terms
     * of which frame timestamps are represented. For fixed-fps content,
     * time base should be 1/framerate and timestamp increments should be 1.
     */
    AVRational time_base;
    int pts_wrap_bits; /**< number of bits in pts (used for wrapping control) */
    /**
     * Decoding: pts of the first frame of the stream, in stream time base.
     * Only set this if you are absolutely 100% sure that the value you set
     * it to really is the pts of the first frame.
     * This may be undefined (AV_NOPTS_VALUE).
     * @note The ASF header does NOT contain a correct start_time the ASF
     * demuxer must NOT set this.
     */
    long long start_time;
    /**
     * Decoding: duration of the stream, in stream time base.
     * If a source file does not specify a duration, but does specify
     * a bitrate, this value will be estimated from bitrate and file size.
     */
    long long duration;

#if LIBAVFORMAT_VERSION_INT < (53<<16)
    char language[4]; /** ISO 639-2/B 3-letter language code (empty string if undefined) */
#endif

    /* av_read_frame() support */
    enum AVStreamParseType need_parsing;
    long long cur_dts;
    int last_IP_duration;
    long long last_IP_pts;

/*
#define MAX_REORDER_DELAY 16
    long long pts_buffer[MAX_REORDER_DELAY+1];*/

    /**
     * sample aspect ratio (0 if unknown)
     * - encoding: Set by user.
     * - decoding: Set by libavformat.
     */
    AVRational sample_aspect_ratio;

    AVMetadata *metadata;

    // Timestamp generation support:
    /**
     * Timestamp corresponding to the last dts sync point.
     *
     * Initialized when AVCodecParserContext.dts_sync_point >= 0 and
     * a DTS is received from the underlying container. Otherwise set to
     * AV_NOPTS_VALUE by default.
     */
    long long reference_dts;

} AVStream;


typedef struct CdxOggParserS CdxOggParser;

struct ogg_codec {
    const char *magic;
    uint8_t magicsize;
    const char *name;
    /**
     * Attempt to process a packet as a header
     * @return 1 if the packet was a valid header,
     *         0 if the packet was not a header (was a data packet)
     *         -1 if an error occurred or for unsupported stream
     */
    int (*header)(CdxOggParser *, int);
    int (*packet)(CdxOggParser *, int);
    /**
     * Translate a granule into a timestamp.
     * Will set dts if non-null and known.
     * @return pts
     */
    uint64_t (*gptopts)(CdxOggParser *, int, uint64_t, int64_t *dts);
    /**
     * 1 if granule is the start time of the associated packet.
     * 0 if granule is the end time of the associated packet.
     */
    int granule_is_start;
    /**
     * Number of expected headers
     */
    int nb_header;
    void (*cleanup)(CdxOggParser *s, int idx);

};

struct ogg_stream {
    uint8_t *buf;
    unsigned int bufsize;
    unsigned int bufpos;
    unsigned int pstart;
    unsigned int psize;
    unsigned int pflags;
    unsigned int pduration;
    uint32_t serial;
    int64_t granule;
    uint64_t start_granule;
    int64_t lastpts;
    int64_t lastdts;
    int64_t sync_pos;   ///< file offset of the first page needed to reconstruct the current packet
    int64_t page_pos;   ///< file offset of the current page
    int flags;
    const struct ogg_codec *codec;
    int header;
    int nsegs, segp;
    uint8_t segments[255];
    int incomplete; ///< whether we're expecting a continuation in the next page
    int page_end;   ///< current packet is the last one completed in the page
    int keyframe_seek;
    int got_start;
    int got_data;   ///< 1 if the stream got some data (non-initial packets), 0 otherwise
    int nb_header; ///< set to the number of parsed headers
    //int end_trimming; ///< set the number of packets to drop from the end
    void *private;

    int firstPacket;
    
    char *extradata;
    int extradata_size;

    AVStream *stream;
    int invalid;
    int streamIndex;
};


enum CdxParserStatus
{
    CDX_PSR_INITIALIZED,
    CDX_PSR_IDLE,
    CDX_PSR_PREFETCHING,
    CDX_PSR_PREFETCHED,
    CDX_PSR_SEEKING,
};
typedef struct
{
    cdx_uint8 *probeBuf;
    unsigned probeBufSize;
    unsigned probeDataSize;
}ProbeSpecificDataBuf;

struct CdxOggParserS
{
	CdxParserT base;
	cdx_uint32 flags;/*使能标志*/
    
	enum CdxParserStatus status;
    pthread_mutex_t statusLock;
    pthread_cond_t cond;
    int mErrno;
	int forceStop;
	
	CdxStreamT *file;
    cdx_int64 fileSize;
    int seekable;
    cdx_uint8 *buf;
    int bufSize;
    int seekStreamIndex;
    CdxMediaInfoT mediaInfo;
    CdxPacketT cdxPkt;

    char     hasVideo;               //video flag, =0:no video bitstream; >0:video bitstream count
    char     hasAudio;               //audio flag, =0:no audio bitstream; >0:audio bitstream count
    char     hasSubTitle;            //lyric flag, =0:no lyric bitstream; >0:lyric bitstream count
    long long 	  pos;//

    intptr_t nb_chapters;
    AVChapter **chapters;
    
    int nstreams;
    struct ogg_stream *streams;
    
    int headers;
    int curidx;//
    cdx_uint64 total_frame;
    int64_t data_offset; /**< offset of the first packet */
    
    int curStream;
    int pstart;

    int page_pos;

    int io_repositioned;

    cdx_uint64 durationUs;
    //int firstPacket;
    cdx_int64 seekTimeUs;

    
#if OGG_SAVE_VIDEO_STREAM
    FILE* fpVideoStream[2];
#endif
#if OGG_SAVE_AUDIO_STREAM 
    FILE* fpAudioStream[AUDIO_STREAM_LIMIT];
    char url[256];
#endif

    CdxPacketT *packets[MaxProbePacketNum];
    cdx_uint32 packetNum;
    cdx_uint32 packetPos;
    ProbeSpecificDataBuf vProbeBuf;
    VideoStreamInfo tempVideoInfo;
};

void av_freep(void *arg);
void *av_mallocz(cdx_uint32 size);
enum CodecID ff_codec_get_id(const AVCodecTag *tags, unsigned int tag);

#endif
