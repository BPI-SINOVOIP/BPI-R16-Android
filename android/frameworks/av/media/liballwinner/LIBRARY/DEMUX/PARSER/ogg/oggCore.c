//#define LOG_NDEBUG 0
#define LOG_TAG "OggParseAuxiliary"
#include "CdxOggParser.h"
#include <CdxLog.h>

void av_freep(void *arg)
{
    void **ptr= (void**)arg;
    free(*ptr);
    *ptr = NULL;
}

void *av_mallocz(cdx_uint32 size)
{
    void *ptr = malloc(size);
    if (ptr)
        memset(ptr, 0, size);
    return ptr;
}
unsigned int avpriv_toupper4(unsigned int x)
{
    return toupper(x & 0xFF) +
          (toupper((x >>  8) & 0xFF) << 8)  +
          (toupper((x >> 16) & 0xFF) << 16) +
          (toupper((x >> 24) & 0xFF) << 24);
}

enum CodecID ff_codec_get_id(const AVCodecTag *tags, unsigned int tag)
{
    int i;
    for(i=0; tags[i].id != CODEC_ID_NONE;i++) {
        if(tag == tags[i].tag)
            return tags[i].id;
    }
    for(i=0; tags[i].id != CODEC_ID_NONE; i++) {
        if (avpriv_toupper4(tag) == avpriv_toupper4(tags[i].tag))
            return tags[i].id;
    }
    return CODEC_ID_NONE;
}
