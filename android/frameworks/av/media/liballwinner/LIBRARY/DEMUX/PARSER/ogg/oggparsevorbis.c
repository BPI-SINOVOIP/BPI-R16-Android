//#define LOG_NDEBUG 0
#define LOG_TAG "ParseVorbis"
#include "CdxOggParser.h"
#include <CdxLog.h>
#include <CdxMemory.h>


static AVMetadataTag *
av_metadata_get(AVMetadata *m, const char *key, const AVMetadataTag *prev, int flags)
{
    cdx_int32 i, j;

    if(!m)
        return NULL;

    if(prev) i= prev - m->elems + 1;
    else     i= 0;

    for(; i<m->count; i++){
        const char *s= m->elems[i].key;
        if(flags & AV_METADATA_MATCH_CASE) for(j=0;         s[j]  ==         key[j]  && key[j]; j++);
        else                               for(j=0; toupper(s[j]) == toupper(key[j]) && key[j]; j++);
        if(key[j])
            continue;
        if(s[j] && !(flags & AV_METADATA_IGNORE_SUFFIX))
            continue;
        return &m->elems[i];
    }
    return NULL;
}

static int av_metadata_set2(AVMetadata **pm, const char *key, const char *value, int flags)
{
    AVMetadata *m= *pm;
    AVMetadataTag *tag= av_metadata_get(m, key, NULL, AV_METADATA_MATCH_CASE);

    if(!m)
        m=*pm= av_mallocz(sizeof(*m));

    if(tag){
        if (flags & AV_METADATA_DONT_OVERWRITE)
            return 0;
        free(tag->value);
        free(tag->key);
        *tag= m->elems[--m->count];
    }else{
        AVMetadataTag *tmp= realloc(m->elems, (m->count+1) * sizeof(*m->elems));
        if(tmp){
            m->elems= tmp;
        }else
            return AVERROR_ENOMEM;
    }
    if(value){
        if(flags & AV_METADATA_DONT_STRDUP_KEY){
            m->elems[m->count].key  = (char *)key;
        }else
        m->elems[m->count].key  = strdup(key  );
        if(flags & AV_METADATA_DONT_STRDUP_VAL){
            m->elems[m->count].value= (char *)value;
        }else
        m->elems[m->count].value= strdup(value);
        m->count++;
    }
    if(!m->count) {
        free(m->elems);
        av_freep(pm);
    }

    return 0;
}

static void ff_dynarray_add(intptr_t **tab_ptr, intptr_t *nb_ptr, intptr_t elem)//intptr_t  int
{
	intptr_t nb, nb_alloc;
    intptr_t *tab;

    nb = *nb_ptr;
    tab = *tab_ptr;
    if ((nb & (nb - 1)) == 0) {
        if (nb == 0)
            nb_alloc = 1;
        else
            nb_alloc = nb * 2;
        tab = realloc(tab, nb_alloc * sizeof(int));
        if(tab == NULL)
        {
            CDX_CHECK(tab);
        }
        *tab_ptr = tab;
    }
    tab[nb++] = elem;
    *nb_ptr = nb;
}

#define dynarray_add(tab, nb_ptr, elem)\
	do {\
    ff_dynarray_add((intptr_t **)(tab), nb_ptr, (intptr_t)(elem));\
} while(0)

static AVChapter *ff_new_chapter(CdxOggParser *ogg, int id, AVRational time_base, long long start, long long end, const char *title)
{
    AVChapter *chapter = NULL;
    int i;

    for(i=0; i<ogg->nb_chapters; i++)
        if(ogg->chapters[i]->id == id)
            chapter = ogg->chapters[i];

    if(!chapter){
        chapter= av_mallocz(sizeof(AVChapter));
        if(!chapter)
            return NULL;
        dynarray_add(&ogg->chapters, &ogg->nb_chapters, chapter);//TODO
    }
#if LIBAVFORMAT_VERSION_INT < (53<<16)
    free(chapter->title);
#endif
    av_metadata_set2(&chapter->metadata, "title", title, 0);
    chapter->id    = id;
    chapter->time_base= time_base;
    chapter->start = start;
    chapter->end   = end;

    return chapter;
}

static int ogm_chapter(CdxOggParser *ogg, cdx_char *key, cdx_char *val)
{
    int i, cnum, h, m, s, ms;
    unsigned int keylen = strlen(key);
    AVChapter *chapter = NULL;

    if (keylen < 9 || sscanf(key, "CHAPTER%02d", &cnum) != 1)
        return 0;

    if (keylen == 9) {
        if (sscanf(val, "%02d:%02d:%02d.%03d", &h, &m, &s, &ms) < 4)
            return 0;

        ff_new_chapter(ogg, cnum, (AVRational){1,1000},
                       ms + 1000*(s + 60*(m + 60*h)),
                       AV_NOPTS_VALUE, NULL);
        free(val);
    } else if (!strcmp(key+9, "NAME")) {
        for(i = 0; i < ogg->nb_chapters; i++)
            if (ogg->chapters[i]->id == cnum) {
                chapter = ogg->chapters[i];
                break;
            }
        if (!chapter)
            return 0;

        av_metadata_set2(&chapter->metadata, "title", val,
                         AV_METADATA_DONT_STRDUP_VAL);
    } else
        return 0;

    free(key);
    return 1;
}

int ff_vorbis_comment(CdxOggParser *ogg, AVMetadata **m, const cdx_uint8 *buf, int size)
{
    const cdx_uint8 *p = buf;
    const cdx_uint8 *end = buf + size;
    unsigned n;
    int j, s;

    if (size < 8) /* must have vendor_length and user_comment_list_length */
        return -1;

    s = GetLE32Bits(p);

    if (end - p - 4 < s || s < 0)
        return -1;

    p += s;

    n = GetLE32Bits(p);

    while (end - p >= 4 && n > 0) {
        const char *t, *v;
        int tl, vl;

        s = GetLE32Bits(p);

        if (end - p < s || s < 0)
            break;

        t = (const char *)p;
        p += s;
        n--;

        v = memchr(t, '=', s);
        if (!v)
            continue;

        tl = v - t;
        vl = s - tl - 1;
        v++;

        if (tl && vl) {
            char *tt, *ct;

            tt = malloc(tl + 1);
            ct = malloc(vl + 1);
            if (!tt || !ct) {
                av_freep(&tt);
                av_freep(&ct);
                continue;
            }

            for (j = 0; j < tl; j++)
                tt[j] = toupper(t[j]);
            tt[tl] = 0;

            memcpy(ct, v, vl);
            ct[vl] = 0;

            if (!ogm_chapter(ogg, tt, ct))
                av_metadata_set2(m, tt, ct,
                                   AV_METADATA_DONT_STRDUP_KEY |
                                   AV_METADATA_DONT_STRDUP_VAL);
        }
    }

    return 0;
}

/*
 * Parse the vorbis header
 *
 * Vorbis Identification header from Vorbis_I_spec.html#vorbis-spec-codec
 * [vorbis_version] = read 32 bits as unsigned integer | Not used
 * [audio_channels] = read 8 bit integer as unsigned | Used
 * [audio_sample_rate] = read 32 bits as unsigned integer | Used
 * [bitrate_maximum] = read 32 bits as signed integer | Not used yet
 * [bitrate_nominal] = read 32 bits as signed integer | Not used yet
 * [bitrate_minimum] = read 32 bits as signed integer | Used as bitrate
 * [blocksize_0] = read 4 bits as unsigned integer | Not Used
 * [blocksize_1] = read 4 bits as unsigned integer | Not Used
 * [framing_flag] = read one bit | Not Used
 */


cdx_uint32 av_xiphlacing(unsigned char *s, cdx_uint32 v)
{
    cdx_uint32 n = 0;

    while(v >= 0xff) {
        *s++ = 0xff;
        v -= 0xff;
        n++;
    }
    *s = v;
    n++;
    return n;
}
#if 0
static cdx_uint32
fixup_vorbis_headers(struct oggvorbis_private *priv, cdx_uint8 **buf)
{
    int i,offset, len;
    unsigned char *ptr;

    len = priv->len[0] + priv->len[1] + priv->len[2];
    ptr = *buf = av_mallocz(len + len/255 + 64);

    ptr[0] = 2;
    offset = 1;
    offset += av_xiphlacing(&ptr[offset], priv->len[0]);
    offset += av_xiphlacing(&ptr[offset], priv->len[1]);
    for (i = 0; i < 3; i++) {
        memcpy(&ptr[offset], priv->packet[i], priv->len[i]);
        offset += priv->len[i];
        av_freep(&priv->packet[i]);
    }
    *buf = realloc(*buf, offset);
    return offset;
}
#endif

static int vorbis_header(CdxOggParser *ogg, int idx)
{
    struct ogg_stream *os = ogg->streams + idx;
    AVStream *st    = os->stream;
    struct oggvorbis_private *priv;
    int pkt_type = os->buf[os->pstart];

    if (!(pkt_type & 1))
        return 0;

    if (!os->private) {
        os->private = av_mallocz(sizeof(struct oggvorbis_private));
        if (!os->private)
            return 0;
    }

    if (os->psize < 1 || pkt_type > 5)
        return -1;

    priv = os->private;
    priv->len[pkt_type >> 1] = os->psize;
    priv->packet[pkt_type >> 1] = av_mallocz(os->psize);
    memcpy(priv->packet[pkt_type >> 1], os->buf + os->pstart, os->psize);
    if (os->buf[os->pstart] == 1) {
        const cdx_uint8 *p = os->buf + os->pstart + 7; /* skip "\001vorbis" tag */
        unsigned blocksize, bs0, bs1;

        if (os->psize != 30)
            return -1;

        if (GetLE32Bits(p) != 0) /* vorbis_version */
            return -1;

        st->codec->channels = Get8Bits(p);
        st->codec->sample_rate = GetLE32Bits(p);
        p += 4; // skip maximum bitrate
        st->codec->bit_rate = GetLE32Bits(p); // nominal bitrate
        p += 4; // skip minimum bitrate

        blocksize = Get8Bits(p);
        bs0 = blocksize & 15;
        bs1 = blocksize >> 4;

        if (bs0 > bs1)
            return -1;
        if (bs0 < 6 || bs1 > 13)
            return -1;

        if (Get8Bits(p) != 1) /* framing_flag */
            return -1;

        st->codec->codec_type = AVMEDIA_TYPE_AUDIO;
        st->codec->codec_id = CODEC_ID_VORBIS;
        ogg->hasAudio =1;

        st->time_base.num = 1;
        st->time_base.den = st->codec->sample_rate;
    } else if (os->buf[os->pstart] == 3) {
        if (os->psize > 8)
            ff_vorbis_comment (ogg, &st->metadata, os->buf + os->pstart + 7, os->psize - 8);
    } else {
        //st->codec->extradata_size =
            //fixup_vorbis_headers(priv, &st->codec->extradata);
    }

    return 1;
}

const struct ogg_codec ff_vorbis_codec = {
    .magic     = "\001vorbis",
    .magicsize = 7,
    .header    = vorbis_header,
    .nb_header = 3,
};
