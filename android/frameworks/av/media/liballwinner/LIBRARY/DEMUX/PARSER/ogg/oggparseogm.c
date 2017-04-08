//#define LOG_NDEBUG 0
#define LOG_TAG "ParseOgm"
#include "CdxOggParser.h"
#include <CdxLog.h>
#include <CdxMemory.h>

static AVCodecTag ff_codec_bmp_tags[] = {
    { CODEC_ID_H264,         MKTAG('H', '2', '6', '4') },
    { CODEC_ID_H264,         MKTAG('h', '2', '6', '4') },
    { CODEC_ID_H264,         MKTAG('X', '2', '6', '4') },
    { CODEC_ID_H264,         MKTAG('x', '2', '6', '4') },
    { CODEC_ID_H264,         MKTAG('a', 'v', 'c', '1') },
    { CODEC_ID_H264,         MKTAG('V', 'S', 'S', 'H') },
    { CODEC_ID_H263,         MKTAG('H', '2', '6', '3') },
    { CODEC_ID_H263,         MKTAG('X', '2', '6', '3') },
    { CODEC_ID_H263,         MKTAG('T', '2', '6', '3') },
    { CODEC_ID_H263,         MKTAG('L', '2', '6', '3') },
    { CODEC_ID_H263,         MKTAG('V', 'X', '1', 'K') },
    { CODEC_ID_H263,         MKTAG('Z', 'y', 'G', 'o') },
    { CODEC_ID_H263P,        MKTAG('H', '2', '6', '3') },
    { CODEC_ID_H263I,        MKTAG('I', '2', '6', '3') }, /* intel h263 */
    { CODEC_ID_H261,         MKTAG('H', '2', '6', '1') },
    { CODEC_ID_H263P,        MKTAG('U', '2', '6', '3') },
    { CODEC_ID_H263P,        MKTAG('v', 'i', 'v', '1') },
    { CODEC_ID_MPEG4,        MKTAG('F', 'M', 'P', '4') },
    { CODEC_ID_MPEG4,        MKTAG('D', 'I', 'V', 'X') },
    { CODEC_ID_DIVX5,        MKTAG('D', 'X', '5', '0') },
    { CODEC_ID_XVID,         MKTAG('X', 'V', 'I', 'D') },
    { CODEC_ID_MPEG4,        MKTAG('M', 'P', '4', 'S') },
    { CODEC_ID_MPEG4,        MKTAG('M', '4', 'S', '2') },
    { CODEC_ID_MPEG4,        MKTAG( 4 ,  0 ,  0 ,  0 ) }, /* some broken avi use this */
    { CODEC_ID_MPEG4,        MKTAG('D', 'I', 'V', '1') },
    { CODEC_ID_MPEG4,        MKTAG('B', 'L', 'Z', '0') },
    { CODEC_ID_MPEG4,        MKTAG('m', 'p', '4', 'v') },
    { CODEC_ID_MPEG4,        MKTAG('U', 'M', 'P', '4') },
    { CODEC_ID_MPEG4,        MKTAG('W', 'V', '1', 'F') },
    { CODEC_ID_MPEG4,        MKTAG('S', 'E', 'D', 'G') },
    { CODEC_ID_MPEG4,        MKTAG('R', 'M', 'P', '4') },
    { CODEC_ID_MPEG4,        MKTAG('3', 'I', 'V', '2') },
    { CODEC_ID_MPEG4,        MKTAG('F', 'F', 'D', 'S') },
    { CODEC_ID_MPEG4,        MKTAG('F', 'V', 'F', 'W') },
    { CODEC_ID_MPEG4,        MKTAG('D', 'C', 'O', 'D') },
    { CODEC_ID_MPEG4,        MKTAG('M', 'V', 'X', 'M') },
    { CODEC_ID_MPEG4,        MKTAG('P', 'M', '4', 'V') },
    { CODEC_ID_MPEG4,        MKTAG('S', 'M', 'P', '4') },
    { CODEC_ID_MPEG4,        MKTAG('D', 'X', 'G', 'M') },
    { CODEC_ID_MPEG4,        MKTAG('V', 'I', 'D', 'M') },
    { CODEC_ID_MPEG4,        MKTAG('M', '4', 'T', '3') },
    { CODEC_ID_MPEG4,        MKTAG('G', 'E', 'O', 'X') },
    { CODEC_ID_MPEG4,        MKTAG('H', 'D', 'X', '4') }, /* flipped video */
    { CODEC_ID_MPEG4,        MKTAG('D', 'M', 'K', '2') },
    { CODEC_ID_MPEG4,        MKTAG('D', 'I', 'G', 'I') },
    { CODEC_ID_MPEG4,        MKTAG('I', 'N', 'M', 'C') },
    { CODEC_ID_MPEG4,        MKTAG('E', 'P', 'H', 'V') }, /* Ephv MPEG-4 */
    { CODEC_ID_MPEG4,        MKTAG('E', 'M', '4', 'A') },
    { CODEC_ID_MPEG4,        MKTAG('M', '4', 'C', 'C') }, /* Divio MPEG-4 */
    { CODEC_ID_MPEG4,        MKTAG('S', 'N', '4', '0') },
    { CODEC_ID_MPEG4,        MKTAG('V', 'S', 'P', 'X') },
    { CODEC_ID_MPEG4,        MKTAG('U', 'L', 'D', 'X') },
    { CODEC_ID_MPEG4,        MKTAG('G', 'E', 'O', 'V') },
    { CODEC_ID_MPEG4,        MKTAG('S', 'I', 'P', 'P') }, /* Samsung SHR-6040 */
    { CODEC_ID_MSMPEG4V3,    MKTAG('D', 'I', 'V', '3') }, /* default signature when using MSMPEG4 */
    { CODEC_ID_MSMPEG4V3,    MKTAG('M', 'P', '4', '3') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('M', 'P', 'G', '3') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('D', 'I', 'V', '5') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('D', 'I', 'V', '6') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('D', 'I', 'V', '4') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('D', 'V', 'X', '3') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('A', 'P', '4', '1') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('C', 'O', 'L', '1') },
    { CODEC_ID_MSMPEG4V3,    MKTAG('C', 'O', 'L', '0') },
    { CODEC_ID_MSMPEG4V2,    MKTAG('M', 'P', '4', '2') },
    { CODEC_ID_MSMPEG4V2,    MKTAG('D', 'I', 'V', '2') },
    { CODEC_ID_MSMPEG4V1,    MKTAG('M', 'P', 'G', '4') },
    { CODEC_ID_MSMPEG4V1,    MKTAG('M', 'P', '4', '1') },
    { CODEC_ID_WMV1,         MKTAG('W', 'M', 'V', '1') },
    { CODEC_ID_WMV2,         MKTAG('W', 'M', 'V', '2') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 's', 'd') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 'h', 'd') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 'h', '1') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 's', 'l') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', '2', '5') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', '5', '0') },
    { CODEC_ID_DVVIDEO,      MKTAG('c', 'd', 'v', 'c') }, /* Canopus DV */
    { CODEC_ID_DVVIDEO,      MKTAG('C', 'D', 'V', 'H') }, /* Canopus DV */
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 'c', ' ') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 'c', 's') },
    { CODEC_ID_DVVIDEO,      MKTAG('d', 'v', 'h', '1') },
    { CODEC_ID_MPEG1VIDEO,   MKTAG('m', 'p', 'g', '1') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('m', 'p', 'g', '2') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('M', 'P', 'E', 'G') },
    { CODEC_ID_MPEG1VIDEO,   MKTAG('P', 'I', 'M', '1') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('P', 'I', 'M', '2') },
    { CODEC_ID_MPEG1VIDEO,   MKTAG('V', 'C', 'R', '2') },
    { CODEC_ID_MPEG1VIDEO,   MKTAG( 1 ,  0 ,  0 ,  16) },
    { CODEC_ID_MPEG2VIDEO,   MKTAG( 2 ,  0 ,  0 ,  16) },
    { CODEC_ID_MPEG4,        MKTAG( 4 ,  0 ,  0 ,  16) },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('D', 'V', 'R', ' ') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('M', 'M', 'E', 'S') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('L', 'M', 'P', '2') }, /* Lead MPEG2 in avi */
    { CODEC_ID_MPEG2VIDEO,   MKTAG('s', 'l', 'i', 'f') },
    { CODEC_ID_MPEG2VIDEO,   MKTAG('E', 'M', '2', 'V') },
    { CODEC_ID_MJPEG,        MKTAG('M', 'J', 'P', 'G') },
    { CODEC_ID_MJPEG,        MKTAG('L', 'J', 'P', 'G') },
    { CODEC_ID_MJPEG,        MKTAG('d', 'm', 'b', '1') },
    { CODEC_ID_MJPEG,        MKTAG('m', 'j', 'p', 'a') },
    { CODEC_ID_LJPEG,        MKTAG('L', 'J', 'P', 'G') },
    { CODEC_ID_MJPEG,        MKTAG('J', 'P', 'G', 'L') }, /* Pegasus lossless JPEG */
    { CODEC_ID_JPEGLS,       MKTAG('M', 'J', 'L', 'S') }, /* JPEG-LS custom FOURCC for avi - encoder */
    { CODEC_ID_MJPEG,        MKTAG('M', 'J', 'L', 'S') }, /* JPEG-LS custom FOURCC for avi - decoder */
    { CODEC_ID_MJPEG,        MKTAG('j', 'p', 'e', 'g') },
    { CODEC_ID_MJPEG,        MKTAG('I', 'J', 'P', 'G') },
    { CODEC_ID_MJPEG,        MKTAG('A', 'V', 'R', 'n') },
    { CODEC_ID_MJPEG,        MKTAG('A', 'C', 'D', 'V') },
    { CODEC_ID_MJPEG,        MKTAG('Q', 'I', 'V', 'G') },
    { CODEC_ID_MJPEG,        MKTAG('S', 'L', 'M', 'J') }, /* SL M-JPEG */
    { CODEC_ID_MJPEG,        MKTAG('C', 'J', 'P', 'G') }, /* Creative Webcam JPEG */
    { CODEC_ID_MJPEG,        MKTAG('I', 'J', 'L', 'V') }, /* Intel JPEG Library Video Codec */
    { CODEC_ID_MJPEG,        MKTAG('M', 'V', 'J', 'P') }, /* Midvid JPEG Video Codec */
    { CODEC_ID_MJPEG,        MKTAG('A', 'V', 'I', '1') },
    { CODEC_ID_MJPEG,        MKTAG('A', 'V', 'I', '2') },
    { CODEC_ID_MJPEG,        MKTAG('M', 'T', 'S', 'J') },
    { CODEC_ID_MJPEG,        MKTAG('Z', 'J', 'P', 'G') }, /* Paradigm Matrix M-JPEG Codec */
    { CODEC_ID_HUFFYUV,      MKTAG('H', 'F', 'Y', 'U') },
    { CODEC_ID_FFVHUFF,      MKTAG('F', 'F', 'V', 'H') },
    { CODEC_ID_CYUV,         MKTAG('C', 'Y', 'U', 'V') },
    { CODEC_ID_RAWVIDEO,     MKTAG( 0 ,  0 ,  0 ,  0 ) },
    { CODEC_ID_RAWVIDEO,     MKTAG( 3 ,  0 ,  0 ,  0 ) },
    { CODEC_ID_RAWVIDEO,     MKTAG('I', '4', '2', '0') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', 'U', 'Y', '2') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', '4', '2', '2') },
    { CODEC_ID_RAWVIDEO,     MKTAG('V', '4', '2', '2') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', 'U', 'N', 'V') },
    { CODEC_ID_RAWVIDEO,     MKTAG('U', 'Y', 'N', 'V') },
    { CODEC_ID_RAWVIDEO,     MKTAG('U', 'Y', 'N', 'Y') },
    { CODEC_ID_RAWVIDEO,     MKTAG('u', 'y', 'v', '1') },
    { CODEC_ID_RAWVIDEO,     MKTAG('2', 'V', 'u', '1') },
    { CODEC_ID_RAWVIDEO,     MKTAG('2', 'v', 'u', 'y') },
    { CODEC_ID_RAWVIDEO,     MKTAG('P', '4', '2', '2') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', 'V', '1', '2') },
    { CODEC_ID_RAWVIDEO,     MKTAG('U', 'Y', 'V', 'Y') },
    { CODEC_ID_RAWVIDEO,     MKTAG('V', 'Y', 'U', 'Y') },
    { CODEC_ID_RAWVIDEO,     MKTAG('I', 'Y', 'U', 'V') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', '8', '0', '0') },
    { CODEC_ID_RAWVIDEO,     MKTAG('H', 'D', 'Y', 'C') },
    { CODEC_ID_RAWVIDEO,     MKTAG('Y', 'V', 'U', '9') },
    { CODEC_ID_RAWVIDEO,     MKTAG('V', 'D', 'T', 'Z') }, /* SoftLab-NSK VideoTizer */
    //{ CODEC_ID_FRWU,         MKTAG('F', 'R', 'W', 'U') },
    //{ CODEC_ID_R210,         MKTAG('r', '2', '1', '0') },
    //{ CODEC_ID_V210,         MKTAG('v', '2', '1', '0') },
    { CODEC_ID_INDEO3,       MKTAG('I', 'V', '3', '1') },
    { CODEC_ID_INDEO3,       MKTAG('I', 'V', '3', '2') },
    { CODEC_ID_INDEO4,       MKTAG('I', 'V', '4', '1') },
    { CODEC_ID_INDEO5,       MKTAG('I', 'V', '5', '0') },
    { CODEC_ID_VP3,          MKTAG('V', 'P', '3', '1') },
    { CODEC_ID_VP3,          MKTAG('V', 'P', '3', '0') },
    { CODEC_ID_VP5,          MKTAG('V', 'P', '5', '0') },
    { CODEC_ID_VP6,          MKTAG('V', 'P', '6', '0') },
    { CODEC_ID_VP6,          MKTAG('V', 'P', '6', '1') },
    { CODEC_ID_VP6,          MKTAG('V', 'P', '6', '2') },
    { CODEC_ID_VP6F,         MKTAG('V', 'P', '6', 'F') },
    { CODEC_ID_VP6F,         MKTAG('F', 'L', 'V', '4') },
    { CODEC_ID_VP8,          MKTAG('V', 'P', '8', '0') },
    { CODEC_ID_ASV1,         MKTAG('A', 'S', 'V', '1') },
    { CODEC_ID_ASV2,         MKTAG('A', 'S', 'V', '2') },
    { CODEC_ID_VCR1,         MKTAG('V', 'C', 'R', '1') },
    { CODEC_ID_FFV1,         MKTAG('F', 'F', 'V', '1') },
    { CODEC_ID_XAN_WC4,      MKTAG('X', 'x', 'a', 'n') },
    { CODEC_ID_MIMIC,        MKTAG('L', 'M', '2', '0') },
    { CODEC_ID_MSRLE,        MKTAG('m', 'r', 'l', 'e') },
    { CODEC_ID_MSRLE,        MKTAG( 1 ,  0 ,  0 ,  0 ) },
    { CODEC_ID_MSRLE,        MKTAG( 2 ,  0 ,  0 ,  0 ) },
    { CODEC_ID_MSVIDEO1,     MKTAG('M', 'S', 'V', 'C') },
    { CODEC_ID_MSVIDEO1,     MKTAG('m', 's', 'v', 'c') },
    { CODEC_ID_MSVIDEO1,     MKTAG('C', 'R', 'A', 'M') },
    { CODEC_ID_MSVIDEO1,     MKTAG('c', 'r', 'a', 'm') },
    { CODEC_ID_MSVIDEO1,     MKTAG('W', 'H', 'A', 'M') },
    { CODEC_ID_MSVIDEO1,     MKTAG('w', 'h', 'a', 'm') },
    { CODEC_ID_CINEPAK,      MKTAG('c', 'v', 'i', 'd') },
    { CODEC_ID_TRUEMOTION1,  MKTAG('D', 'U', 'C', 'K') },
    { CODEC_ID_TRUEMOTION1,  MKTAG('P', 'V', 'E', 'Z') },
    { CODEC_ID_MSZH,         MKTAG('M', 'S', 'Z', 'H') },
    { CODEC_ID_ZLIB,         MKTAG('Z', 'L', 'I', 'B') },
    { CODEC_ID_SNOW,         MKTAG('S', 'N', 'O', 'W') },
    { CODEC_ID_4XM,          MKTAG('4', 'X', 'M', 'V') },
    { CODEC_ID_FLV1,         MKTAG('F', 'L', 'V', '1') },
    { CODEC_ID_FLASHSV,      MKTAG('F', 'S', 'V', '1') },
    { CODEC_ID_SVQ1,         MKTAG('s', 'v', 'q', '1') },
    { CODEC_ID_TSCC,         MKTAG('t', 's', 'c', 'c') },
    { CODEC_ID_ULTI,         MKTAG('U', 'L', 'T', 'I') },
    { CODEC_ID_VIXL,         MKTAG('V', 'I', 'X', 'L') },
    { CODEC_ID_QPEG,         MKTAG('Q', 'P', 'E', 'G') },
    { CODEC_ID_QPEG,         MKTAG('Q', '1', '.', '0') },
    { CODEC_ID_QPEG,         MKTAG('Q', '1', '.', '1') },
    { CODEC_ID_WMV3,         MKTAG('W', 'M', 'V', '3') },
    { CODEC_ID_VC1,          MKTAG('W', 'V', 'C', '1') },
    { CODEC_ID_VC1,          MKTAG('W', 'M', 'V', 'A') },
    { CODEC_ID_LOCO,         MKTAG('L', 'O', 'C', 'O') },
    { CODEC_ID_WNV1,         MKTAG('W', 'N', 'V', '1') },
    { CODEC_ID_AASC,         MKTAG('A', 'A', 'S', 'C') },
    { CODEC_ID_INDEO2,       MKTAG('R', 'T', '2', '1') },
    { CODEC_ID_FRAPS,        MKTAG('F', 'P', 'S', '1') },
    { CODEC_ID_THEORA,       MKTAG('t', 'h', 'e', 'o') },
    { CODEC_ID_TRUEMOTION2,  MKTAG('T', 'M', '2', '0') },
    { CODEC_ID_CSCD,         MKTAG('C', 'S', 'C', 'D') },
    { CODEC_ID_ZMBV,         MKTAG('Z', 'M', 'B', 'V') },
    { CODEC_ID_KMVC,         MKTAG('K', 'M', 'V', 'C') },
    { CODEC_ID_CAVS,         MKTAG('C', 'A', 'V', 'S') },
    { CODEC_ID_JPEG2000,     MKTAG('M', 'J', '2', 'C') },
    { CODEC_ID_VMNC,         MKTAG('V', 'M', 'n', 'c') },
    { CODEC_ID_TARGA,        MKTAG('t', 'g', 'a', ' ') },
    { CODEC_ID_PNG,          MKTAG('M', 'P', 'N', 'G') },
    { CODEC_ID_PNG,          MKTAG('P', 'N', 'G', '1') },
    { CODEC_ID_CLJR,         MKTAG('c', 'l', 'j', 'r') },
    { CODEC_ID_DIRAC,        MKTAG('d', 'r', 'a', 'c') },
    { CODEC_ID_RPZA,         MKTAG('a', 'z', 'p', 'r') },
    { CODEC_ID_RPZA,         MKTAG('R', 'P', 'Z', 'A') },
    { CODEC_ID_RPZA,         MKTAG('r', 'p', 'z', 'a') },
    { CODEC_ID_SP5X,         MKTAG('S', 'P', '5', '4') },
    //{ CODEC_ID_AURA,         MKTAG('A', 'U', 'R', 'A') },
    //{ CODEC_ID_AURA2,        MKTAG('A', 'U', 'R', '2') },
    //{ CODEC_ID_DPX,          MKTAG('d', 'p', 'x', ' ') },
    //{ CODEC_ID_KGV1,         MKTAG('K', 'G', 'V', '1') },
    { CODEC_ID_NONE,         0 }
};

int ff_vorbis_comment(CdxOggParser *ogg, AVMetadata **m, const cdx_uint8 *buf, int size);


static cdx_uint32 bytestream_get_buffer(const cdx_uint8 **b, cdx_uint8 *dst, cdx_uint32 size)
{
    memcpy(dst, *b, size);
    (*b) += size;
    return size;
}

static int
ogm_header(CdxOggParser *ogg, int idx)
{
    struct ogg_stream *os = ogg->streams + idx;
    AVStream *st = os->stream;
    const cdx_uint8 *p = os->buf + os->pstart;
    uint64_t time_unit;
    uint64_t spu;
    uint32_t size;

    if(!(*p & 1))
        return 0;
    if(*p == 1) {
        p++;

        if(*p == 'v'){
            int tag;
            st->codec->codec_type = AVMEDIA_TYPE_VIDEO;
            p += 8;
            tag = GetLE32Bits(p);//TODO
            st->codec->codec_id = ff_codec_get_id(ff_codec_bmp_tags, tag);
            st->codec->codec_tag = tag;
            CDX_LOGD("codec_tag = 0x%x, codec_id = %d", tag, st->codec->codec_id);
            ogg->hasVideo =1;
        } else if (*p == 't') {
            st->codec->codec_type = AVMEDIA_TYPE_SUBTITLE;
            st->codec->codec_id = CODEC_ID_TEXT;//TODO
            p += 12;
            ogg->hasSubTitle =1;
        } else {
            uint8_t acid[5];
            int cid;
            st->codec->codec_type = AVMEDIA_TYPE_AUDIO;
            p += 8;
            bytestream_get_buffer(&p, acid, 4);
            acid[4] = 0;
            cid = strtol((const char *)acid, NULL, 16);
            st->codec->codec_id = ff_codec_get_id(ff_codec_wav_tags, cid);
            // our parser completely breaks AAC in Ogg
            if (st->codec->codec_id != CODEC_ID_AAC)
                st->need_parsing = AVSTREAM_PARSE_FULL;
            
            ogg->hasAudio =1;
        }

        size        = GetLE32Bits(p);
        size        = FFMIN(size, os->psize);
        time_unit   = GetLE64Bits(p);
        spu         = GetLE64Bits(p);
        p += 4;    /* default_len */
        p += 8;    /* buffersize + bits_per_sample */

        if(st->codec->codec_type == AVMEDIA_TYPE_VIDEO){
            st->codec->width = GetLE32Bits(p);
            st->codec->height = GetLE32Bits(p);
            st->codec->time_base.den = spu * 10000000;
            st->codec->time_base.num = time_unit;
            st->codec->nFrameDuration = 
                st->codec->time_base.num * 1000000LL/ st->codec->time_base.den;
            st->time_base = st->codec->time_base;
        } else {
            st->codec->channels = GetLE16Bits(p);
            p += 2;                 /* block_align */
            st->codec->bit_rate = GetLE32Bits(p) * 8;
            st->codec->sample_rate = time_unit ? spu * 10000000 / time_unit : 0;
            st->time_base.num = 1;
            st->time_base.den = st->codec->sample_rate;
        }
    } else if (*p == 3) {
        if (os->psize > 8)
            ff_vorbis_comment(ogg, &st->metadata, p+7, os->psize-8);
    }

    return 1;
}


static int
ogm_packet(CdxOggParser *ogg, int idx)
{
    struct ogg_stream *os = ogg->streams + idx;
    uint8_t *p = os->buf + os->pstart;
    int lb;

    if(*p & 8)
        os->pflags |= KEY_FRAME;

    lb = ((*p & 2) << 1) | ((*p >> 6) & 3);
    os->pstart += lb + 1;
    os->psize -= lb + 1;

    while (lb--)
        os->pduration += p[lb+1] << (lb*8);

    return 0;
}

const struct ogg_codec ff_ogm_video_codec = {
    .magic = "\001video",
    .magicsize = 6,
    .header = ogm_header,
    .packet = ogm_packet,
    .granule_is_start = 1,
    .nb_header = 2,
};

const struct ogg_codec ff_ogm_audio_codec = {
    .magic = "\001audio",
    .magicsize = 6,
    .header = ogm_header,
    .packet = ogm_packet,
    .granule_is_start = 1,
    .nb_header = 2,
};

const struct ogg_codec ff_ogm_text_codec = {
    .magic = "\001text",
    .magicsize = 5,
    .header = ogm_header,
    .packet = ogm_packet,
    .granule_is_start = 1,
    .nb_header = 2,
};

