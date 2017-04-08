#ifndef _AW_SSTR_UTILS_H_
#define _AW_SSTR_UTILS_H_

#include <stdlib.h>
#include <AwSstrParser.h>

#define TAB_INSERT_CAST( cast, count, tab, p, index ) do { \
    if( (count) > 0 )                           \
        (tab) = cast realloc( tab, sizeof( void ** ) * ( (count) + 1 ) ); \
    else                                        \
        (tab) = cast malloc( sizeof( void ** ) );       \
    if( !(tab) ) abort();                       \
    if( (count) - (index) > 0 )                 \
        memmove( (void**)(tab) + (index) + 1,   \
                 (void**)(tab) + (index),       \
                 ((count) - (index)) * sizeof(*(tab)) );\
    (tab)[(index)] = (p);                       \
    (count)++;                                  \
} while(0)



//* Arrays
static inline void SmsArrayInit(SmsArrayT * p_array)
{
    memset( p_array, 0, sizeof(SmsArrayT));
}

static inline void SmsArrayClear(SmsArrayT * p_array)
{
    free(p_array->ppElems);
    memset(p_array, 0, sizeof(SmsArrayT));
}

static inline SmsArrayT *SmsArrayNew(void)
{
    SmsArrayT *ret = (SmsArrayT *)malloc(sizeof(SmsArrayT));
    if(ret) SmsArrayInit(ret);
    return ret;
}

static inline void SmsArrayDestroy(SmsArrayT * p_array)
{
    if(!p_array)
        return;
    SmsArrayClear(p_array);
    free(p_array);
}

//* Read
static inline int SmsArrayCount(SmsArrayT *p_array )
{
    return p_array->iCount;
}

static inline void *SmsArrayItemAtIndex(SmsArrayT *p_array, int i_index )
{
    return p_array->ppElems[i_index];
}

static inline int SmsArrayIndexOfItem(SmsArrayT *p_array, void *item )
{
    int i;
    for( i = 0; i < p_array->iCount; i++)
    {
        if( p_array->ppElems[i] == item )
            return i;
    }
    return -1;
}

/* Write */
static inline void SmsArrayInsert(SmsArrayT *p_array, void *p_elem, int i_index)
{
    TAB_INSERT_CAST((void **), p_array->iCount, p_array->ppElems, p_elem, i_index);
}

static inline void SmsArrayAppend(SmsArrayT *p_array, void *p_elem)
{
    SmsArrayInsert(p_array, p_elem, p_array->iCount);
}

static inline void SmsArrayRemove(SmsArrayT *p_array, int i_index)
{
    if( i_index >= 0 )
    {
        if( p_array->iCount > 1 )
        {
            memmove( p_array->ppElems + i_index,
                     p_array->ppElems + i_index+1,
                     ( p_array->iCount - i_index - 1 ) * sizeof( void* ) );
        }
        p_array->iCount--;
        if( p_array->iCount == 0 )
        {
            free( p_array->ppElems );
            p_array->ppElems = NULL;
        }
    }
}

char *AwFromCharset(const char *charset, const void *data, size_t data_size);
SmsStreamT *SmsNew(void);
void SmsFree(SmsStreamT *sms);
cdx_uint8 *DecodeStringHexToBinary(const char *psz_src);
QualityLevelT *QlNew(void);
void QlFree(QualityLevelT *qlevel);
ChunkT *ChunkNew(SmsStreamT* sms, cdx_int64 duration, cdx_int64 start_time );
ChunkT *ChunkGet(SmsStreamT *sms, cdx_int64 start_time);
void ChunkFree(ChunkT *chunk);
QualityLevelT *GetQlevel(SmsStreamT *sms, const unsigned qid);
SmsQueueT *SmsQueueInit(const int length);
void SmsQueueFree(SmsQueueT* queue);
int SmsQueuePut(SmsQueueT *queue, const cdx_uint64 value);
cdx_uint64 SmsQueueAvg(SmsQueueT *queue);
SmsStreamT *SmsGetStreamByCat(SmsArrayT *streams, int i_cat);
int EsCatToIndex(int i_cat);
int IndexToEsCat(int index);
char *aw_strtok_r(char *s, const char *delim, char **save_ptr);
AwIsmcT *ParseIsmc(char *buffer, int size, AwIsmcT *ismc, const char *encoding, int options);


#endif
