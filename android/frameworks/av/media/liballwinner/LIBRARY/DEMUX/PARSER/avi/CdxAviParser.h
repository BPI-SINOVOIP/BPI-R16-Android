#ifndef _CDX_AVI_PARSER_H_
#define _CDX_AVI_PARSER_H_

#define AVI_TRUE    (1)
#define AVI_FALSE   (0)

#define MAX_STREAM (15)
#define MAX_AUDIO_STREAM    (8)//FILE_MAX_AUDIO_STREAM_NUM //MAX_AUDIO_STREAM_NUM        //(4)
#define MAX_SUBTITLE_STREAM (10)

#define STRH_DWSCALE_THRESH 100 
#define MIN_BYTERATE    1024    //min byte rate of audio stream

#define ABS_EDIAN_FLAG_LITTLE       ((cdx_uint32)(0<<16))


#ifndef mmioFOURCC
#define mmioFOURCC( ch0, ch1, ch2, ch3 )    \
            ( (cdx_uint32)(cdx_uint8)(ch0) | ( (cdx_uint32)(cdx_uint8)(ch1) << 8 ) |    \
            ( (cdx_uint32)(cdx_uint8)(ch2) << 16 ) | ( (cdx_uint32)(cdx_uint8)(ch3) << 24 ) )
#endif
/* Macro to make a TWOCC out of two characters */
#ifndef aviTWOCC
#define aviTWOCC(ch0, ch1) ((cdx_uint16)(cdx_uint8)(ch0) | ((cdx_uint16)(cdx_uint8)(ch1) << 8))
#endif

#define U32_INVALID_VALUE       0xffffffff
#define AVI_INVALID_PTS         0xffffffff

#define AVIF_HASINDEX           0x00000010        // Index at end of file?
#define AVIF_MUSTUSEINDEX       0x00000020
#define AVIF_ISINTERLEAVED      0x00000100
#define AVIF_TRUSTCKTYPE        0x00000800        // Use CKType to find key frames?
#define AVIF_WASCAPTUREFILE     0x00010000
#define AVIF_COPYRIGHTED        0x00020000

/* form types, list types, and chunk types */
#define formheaderRIFF          mmioFOURCC('R', 'I', 'F', 'F')
#define formtypeAVI             mmioFOURCC('A', 'V', 'I', ' ')
#define formtypeAVIX            mmioFOURCC('A', 'V', 'I', 'X')
#define listheaderLIST          mmioFOURCC('L', 'I', 'S', 'T')
#define listtypeAVIHEADER       mmioFOURCC('h', 'd', 'r', 'l')
#define ckidAVIMAINHDR          mmioFOURCC('a', 'v', 'i', 'h')
#define listtypeSTREAMHEADER    mmioFOURCC('s', 't', 'r', 'l')
#define ckidSTREAMHEADER        mmioFOURCC('s', 't', 'r', 'h')
#define ckidSTREAMFORMAT        mmioFOURCC('s', 't', 'r', 'f')
#define ckidSTREAMHANDLERDATA   mmioFOURCC('s', 't', 'r', 'd')
#define ckidSTREAMNAME          mmioFOURCC('s', 't', 'r', 'n')
#define listtypeAVIMOVIE        mmioFOURCC('m', 'o', 'v', 'i')
#define listtypeAVIRECORD       mmioFOURCC('r', 'e', 'c', ' ')
#define ckidAVINEWINDEX         mmioFOURCC('i', 'd', 'x', '1')
#define ckidODMLINDX            mmioFOURCC('i', 'n', 'd', 'x')
#define ckidIX00                mmioFOURCC('i', 'x', '0', '0')
#define ckidIX01                mmioFOURCC('i', 'x', '0', '1')
#define ckidIX02                mmioFOURCC('i', 'x', '0', '2')
#define ckidIX03                mmioFOURCC('i', 'x', '0', '3')
#define ckidIX04                mmioFOURCC('i', 'x', '0', '4')
#define ckidIX05                mmioFOURCC('i', 'x', '0', '5')
#define ckidIX06                mmioFOURCC('i', 'x', '0', '6')
#define ckidIX07                mmioFOURCC('i', 'x', '0', '7')
#define ckidIX08                mmioFOURCC('i', 'x', '0', '8')
#define ckidIX09                mmioFOURCC('i', 'x', '0', '9')
#define ckidODMLHEADER          mmioFOURCC('d', 'm', 'l', 'h')

/*
** Stream types for the <fccType> field of the stream header.
*/
#define streamtypeVIDEO         mmioFOURCC('v', 'i', 'd', 's')
#define streamtypeAUDIO         mmioFOURCC('a', 'u', 'd', 's')
#define streamtypeMIDI          mmioFOURCC('m', 'i', 'd', 's')
#define streamtypeTEXT          mmioFOURCC('t', 'x', 't', 's')

/* Basic chunk types */
#define cktypeDIBbits           aviTWOCC('d', 'b')
#define cktypeDIBcompressed     aviTWOCC('d', 'c')
#define cktypeDIBdrm            aviTWOCC('d', 'd')
#define cktypePALchange         aviTWOCC('p', 'c')
#define cktypeWAVEbytes         aviTWOCC('w', 'b')
#define cktypeSUBtext           aviTWOCC('s', 't')
#define cktypeSUBbmp            aviTWOCC('s', 'b')
#define cktypeCHAP              aviTWOCC('c', 'h')

/* Chunk id to use for extra chunks for padding. */
#define ckidAVIPADDING          mmioFOURCC('J', 'U', 'N', 'K')

/*
** Useful macros
*/

/* Macro to get stream number out of a FOURCC ckid, only for chunk id "01wb"ï¿½ï¿½ */
#define FromHex(n)              (((n) >= 'A') ? ((n) + 10 - 'A') : ((n) - '0'))
#define StreamFromFOURCC(fcc)   ((FromHex((fcc) & 0xff)) << 4) + (FromHex((fcc >> 8) & 0xff))

/* Macro to get TWOCC chunk type out of a FOURCC ckid */
#define TWOCCFromFOURCC(fcc)    (fcc >> 16)

/* Macro to make a ckid for a chunk out of a TWOCC and a stream number
** from 0-255., tcc = "bd", stream = 0x01, --> "bd10", ï¿½ï¿½01db 
*/
#define ToHex(n)                    ((cdx_uint8) (((n) > 9) ? ((n) - 10 + 'A') : ((n) + '0')))
#define MAKEAVICKID(tcc, stream)    ((cdx_uint32) ((ToHex(((stream) & 0xf0)>>4))  \
                                           | (ToHex((stream) & 0x0f) << 8) | (tcc << 16)))

/*
** Main AVI File Header
*/

/* flags for use in <dwFlags> in AVIFileHdr */
#define AVIF_HASINDEX           0x00000010  // Index at end of file
#define AVIF_MUSTUSEINDEX       0x00000020
#define AVIF_ISINTERLEAVED      0x00000100
#define AVIF_TRUSTCKTYPE        0x00000800  // Use CKType to find key frames
#define AVIF_WASCAPTUREFILE     0x00010000
#define AVIF_COPYRIGHTED        0x00020000

//#define PCM_TAG                 0x0001
//#define ADPCM_TAG               0x0002
//#define MP3_TAG1                0x0055
//#define MP3_TAG2                0x0050
//#define AAC_TAG                 0x00ff
//#define WMA1_TAG                0x0160
//#define WMA2_TAG                0x0161
//#define WMAPRO_TAG              0x0162
//#define AC3_TAG                 0x2000
//#define DTS_TAG                 0x2001      //Support DTS audio--Michael 2004/06/29

enum AVI_AUDIO_TAG
{    
    PCM_TAG     = 0x0001,
    ADPCM_TAG   = 0x0002,
    ALAW_TAG    = 0x0006,
    MULAW_TAG   = 0x0007,
    ADPCM11_TAG = 0X0011,
    MP3_TAG1    = 0x0055,
    MP3_TAG2    = 0x0050,
    AAC_TAG     = 0x00ff,
    WMA1_TAG    = 0x0160,
    WMA2_TAG    = 0x0161,
    WMAPRO_TAG  = 0x0162,
    AC3_TAG     = 0x2000,
    DTS_TAG     = 0x2001,      //Support DTS audio--Michael 2004/06/29
    //VORBIS_TAG  = 0x6771,
};

/* The AVI File Header LIST chunk should be padded to this size */
#define AVI_HEADERSIZE          2048        // size of AVI header list

typedef struct
{
    cdx_uint32        dwMicroSecPerFrame;        // frame display rate (or 0L)
    cdx_uint32        dwMaxBytesPerSec;          // max. transfer rate
    cdx_uint32        dwPaddingGranularity;      // pad to multiples of this size; normally 2K.
    cdx_uint32        dwFlags;                   // the ever-present flags
    cdx_uint32        dwTotalFrames;             // total frames in file   LIST odml->dmlh->dwTotalFramesÎª×¼
    cdx_uint32        dwInitialFrames;           // 
    cdx_uint32        dwStreams;
    cdx_uint32        dwSuggestedBufferSize;
    cdx_uint32        dwWidth;
    cdx_uint32        dwHeight;
    cdx_uint32        dwReserved[4];
} MainAVIHeaderT;

/*
** Stream header
*/

#define AVISF_DISABLED              0x00000001
#define AVISF_VIDEO_PALCHANGES      0x00010000

typedef cdx_uint32 FOURCC;

typedef struct _video_rect_
{
    cdx_uint16        left;
    cdx_uint16        top;
    cdx_uint16        right;
    cdx_uint16        bottom;
} VideoRectT;

typedef struct _AVISTREAMHEADER_
{
    FOURCC              fccType;    //±íÊ¾Êý¾ÝÁ÷µÄÖÖÀà,vids±íÊ¾ÊÓÆµÊý¾ÝÁ÷£¬audsÒôÆµÊý¾ÝÁ÷
    FOURCC              fccHandler;
    cdx_uint32          dwFlags;    // Contains AVITF_* flags 
    cdx_uint16          wPriority;
    cdx_uint16          wLanguage;
    cdx_uint32          dwInitialFrames;
    cdx_uint32          dwScale;    //Êý¾ÝÁ¿£¬ÊÓÆµÃ¿Ö¡µÄ´óÐ¡»òÕßÒôÆµµÄ²ÉÑù´óÐ¡
    cdx_uint32          dwRate;     // dwRate / dwScale == samples/second, for video:frame/second, for audio:byte/second
    cdx_uint32          dwStart;        
    cdx_uint32          dwLength;   //In units above..., for vidoe:frame count;for audio: byte count 
    cdx_uint32          dwSuggestedBufferSize;
    cdx_uint32          dwQuality;
    cdx_uint32          dwSampleSize;
    VideoRectT          rcFrame;
} AVIStreamHeaderT;

typedef struct tagWAVEFORMATEX
{
    cdx_uint16       wFormatTag;     //MP3_TAG1,...
    cdx_uint16       nChannels;
    cdx_uint32       nSamplesPerSec;
    cdx_uint32       nAvgBytesPerSec;
    cdx_uint16       nBlockAlign;
    cdx_uint16       wBitsPerSample;
    cdx_uint16       cbSize;
} WAVEFORMATEX;

/* Flags for index */
#define AVIIF_LIST          0x00000001L // chunk is a 'LIST'
#define AVIIF_KEYFRAME      0x00000010L // this frame is a key frame.
#define AVIIF_NOTIME        0x00000100L // this frame doesn't take any time
#define AVIIF_COMPUSE       0x0FFF0000L // these bits are for compressor use

typedef struct
{
    cdx_uint8        bFirstEntry;    /* first entry to change */
    cdx_uint8        bNumEntries;    /* # entries to change (0 if 256) */
    cdx_uint16       wFlags;         /* Mostly to preserve alignment... */
//    PALETTEENTRY    peNew[];    /* New color specifications */
} AVIPALCHANGE;

typedef struct tagBITMAPINFOHEADER
{                               // bmih
    cdx_uint32       biSize;
    cdx_uint32       biWidth;
    cdx_uint32       biHeight;
    cdx_uint16       biPlanes;
    cdx_uint16       biBitCount;
    cdx_uint32       biCompression;
    cdx_uint32       biSizeImage;
    cdx_uint32       biXPelsPerMeter;
    cdx_uint32       biYPelsPerMeter;
    cdx_uint32       biClrUsed;
    cdx_uint32       biClrImportant;
} BITMAPINFOHEADER;

typedef struct tagTEXTINFO
{
    cdx_uint16       wCodePage;
    cdx_uint16       wCountryCode;
    cdx_uint16       wLanguageCode;
    cdx_uint16       wDialect;
} TEXTINFO;

//#define MAX_STREAM 8
#define INDEX_INC 1000

typedef struct _AVI_CHUNK_
{
    FOURCC      fcc;
    cdx_uint32  length;
    cdx_uint32  flag;
    cdx_char    *buffer;
}AviChunkT;

#define AVI_INDEX_OF_INDEXES        0x00    //when each entry is aIndex array points to an index chunk
#define AVI_INDEX_OF_CHUNKS         0x01    //when each entry is aIndex array points to a chunk in the file
#define AVI_INDEX_IS_DATA           0x80    //when each entry is aIndex is really the data bIndexSubType codes for INDEX_OF_CHUNKS
#define AVI_INDEX_2FIELD            0x01    //when fields within frames are also indexed.
#define AVI_INDEX_SUBTYPE_1FRMAE    0x00    //when frame is not indexed to two fileds.

typedef struct _avisuperindex_entry
{
    cdx_uint32       offsetLow;    //ï¿½Ä¼ï¿½ï¿½Ð£ï¿½ï¿½ï¿½Î»ï¿½ï¿½Ó¦ï¿½ï¿½ï¿½Ö½Ú£ï¿½ï¿½ï¿½ï¿½ï¿½Ö±ï¿½ï¿½ï¿½ï¿½
    cdx_uint32       offsetHigh;   //point to indx chunk head.
    cdx_uint32       size;          //size of index chunk, two case:
                                    //(1)not include chunk head, (2)include chunk head.
    cdx_uint32       duration;      //chunk_entry total count in this index_chunk.
}AviSuperIndexEntryT;

typedef struct _avistdindex_enty
{
    cdx_uint32       offset; //offset is set to the chunk body, not chunk head!
    cdx_uint32       size;   //bit31 is set if this is NOT a keyframe!(ref to OpenDML AVI File Format)
}AviStdIndexEntyT;

typedef struct _avifiledindex_enty
{
    cdx_uint32       offset; //offset is set to the chunk body, not chunk head!
    cdx_uint32       size;   //bit31 is set if this is NOT a keyframe!
                        //and size is the chunk body size.Not include chunk head.
    cdx_uint32       offsetField2;//offset to second field in this chunk.
}AviFieldIndexEntyT;


typedef struct _ODMLExtendedAVIHeader
{
    cdx_uint32       dwTotalFrames;
} ODMLExtendedAVIHeaderT;

typedef struct _avistdindex_chunk
{
    cdx_uint16       wLongsperEntry;
    cdx_uint8        bIndexSubType;  //AVI_INDEX_2FILED,etc
    cdx_uint8        bIndexType;
    cdx_uint32       nEntriesInUse;
    cdx_uint32       dwChunkId;
    cdx_uint32       baseOffsetLow;
    cdx_uint32       baseOffsetHigh;
    cdx_uint32       dwReserved;
    AviStdIndexEntyT    *aIndex;
}AviStdIndexChunkT;

#define MAX_IX_ENTRY_NUM    128 //ï¿½ï¿½ï¿½ï¿½ï¿½ï¿½odml aviï¿½ï¿½video indx chunkï¿½ï¿½98ï¿½ï¿½ï¿½ï¿½
typedef struct _avisuperindex_chunk
{
    cdx_uint16       wLongsPerEntry; //ï¿½ï¿½DWORD(4ï¿½Ö½ï¿½)Îªï¿½ï¿½Î»ï¿½ï¿½Ò»ï¿½ï¿½Îª4ï¿½ï¿½ï¿½ï¿½16ï¿½ï¿½ï¿½Ö½Ú¡ï¿½sizeof entry in this table
    cdx_uint8        bIndexSubType;  //Ò»ï¿½ï¿½Îª0ï¿½ï¿½Ò²ï¿½ï¿½ï¿½ï¿½ï¿½ï¿½AVI_INDEX_2FILED,etc
    cdx_uint8        bIndexType;     //AVI_INDEX_OF_INDEXES / AVI_INDEX_OF_CHUNKS
    cdx_uint32       nEntriesInUse;  //aIndexï¿½ï¿½ï¿½Ë¶ï¿½ï¿½ï¿½ï¿½ï¿½
    cdx_uint32       dwChunkId;
    cdx_uint32       baseOffsetLow;
    cdx_uint32       baseOffsetHigh;
    cdx_uint32       dwReserved;
    AviSuperIndexEntryT *aIndex[MAX_IX_ENTRY_NUM];  //ï¿½ï¿½ï¿½ï¿½Ê±ï¿½ï¿½ï¿½ï¿½ï¿½Ú´ï¿½
}AviSuperIndexChunkT;

typedef struct
{
    cdx_uint32       position;
    cdx_uint32       length;
}AVIIXENTRY;

typedef struct
{
    cdx_uint32       ix_tag;
    cdx_uint32       size;
    cdx_uint16       wLongsPerEntry;
    cdx_uint8        bIndexSubType;      //(0 == frame index)
    cdx_uint8        bIndexType;         //(1 == AVI_INDEX_OF_CHUNKS)
    cdx_uint32       nEntriesInUse;
    cdx_uint32       dwChunkId;
    cdx_uint32       qwBaseOffset_low;
    cdx_uint32       qwBaseOffset_high;
    cdx_uint32       reserved;
}AVIIXCHUNK;    //odmlï¿½ï¿½ï¿½Ä¼ï¿½ï¿½ï¿½Ê½


typedef struct _AVI_STREAM_INFO_
{
    cdx_int8        streamType;// strn: "Video/Audio/Subtitle/Chapter [- Description]"
    /**********************
    type of the stream
    a - audio
    v - video
    s - subtitle
    c - chapter
    u - unknown
    **********************/

    cdx_int8        mediaType; //AVIStreamHeader->fccType.
    /**********************
    type of the media
    a - audio
    v - video
    t - text
    u - unknown
    **********************/

    cdx_int8        isODML;
    cdx_int64       indxTblOffset; // LIST strl -> indx
    AviSuperIndexChunkT *indx;     // 

    AviChunkT       *strh;  //strh,length, AVIStreamHeader,  malloc
    AviChunkT       *strf;  //strf ,length, ï¿½ï¿½Ý½á¹¹ï¿½ï¿½video:BITMAPINFOHEADER,audio:WAVEFORMATEX,ï¿½ï¿½Òªmalloc
    AviChunkT       *strd;  //ï¿½ï¿½Òªmalloc
    AviChunkT       *strn;  //strn, length, "string" ï¿½ï¿½Òªmalloc

}AviStreamInfoT;
typedef struct _AUDIO_STREAM_INFO_
{
    cdx_int32   streamIdx;     //
    cdx_int32   aviAudioTag;   //PCM_TAG 
    cdx_int32   cbrFlag;       //1:cbr, 0:vbr
    cdx_int32   sampleRate;
    cdx_int32   sampleNumPerFrame; 
    cdx_int32   avgBytesPerSec; //unit: byte/s 
    cdx_int32   nBlockAlign;    //if VBR, indicate an audioframe's max bytes

    cdx_int32   dataEncodeType;    //enum __CEDARLIB_SUBTITLE_ENCODE_TYPE, CDX_SUBTITLE_ENCODE_UTF8,  <==>__cedar_subtitle_encode_t,  CDX_SUBTITLE_ENCODE_UTF8,  
    cdx_uint8   sStreamName[AVI_STREAM_NAME_SIZE];   
}AudioStreamInfoT;

typedef struct _SUB_STREAM_INFO_ //for subtitle
{
    cdx_int32   streamIdx;     
    cdx_int32   aviSubTag;     
    cdx_uint8   sStreamName[AVI_STREAM_NAME_SIZE];   
}SubStreamInfoT;

//struct avi_chunk_reader{
//    struct cdx_stream_info    *fp;
//    cdx_uint32   cur_pos;        //the offset in file start.
//    cdx_uint32   chunk_length;   //not include 8-byte header.
//
//    AVI_CHUNK   *pavichunk; //used to read a chunk. But its space is malloc outside.
//};
enum READ_CHUNK_MODE{
    READ_CHUNK_SEQUENCE = 0,    //sequence read avi chunk
    READ_CHUNK_BY_INDEX = 1,    //read avi chunk base index table's indication.
};
enum USE_INDEX_STYLE{
    NOT_USE_INDEX   = 0,    //in common, when index table not exist, we set this value.
    USE_IDX1        = 1,
    USE_INDX        = 2,    //use Open-DML index table.
};
enum AVI_CHUNK_TYPE{
    CHUNK_TYPE_VIDEO = 0,
    CHUNK_TYPE_AUDIO = 1,
};

typedef struct AviVideoStreamInfo
{
    VideoStreamInfo vFormat;
    cdx_uint32      nMicSecPerFrame;
    
}AviVideoStreamInfo;

#endif /* _CDX_AVI_PARSER_H_ */

