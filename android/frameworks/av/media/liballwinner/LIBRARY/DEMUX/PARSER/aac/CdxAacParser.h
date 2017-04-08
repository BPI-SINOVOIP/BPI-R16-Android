#ifndef CDX_AAC_PARSER_H
#define CDX_AAC_PARSER_H

/* AAC file format */
enum {
	AAC_FF_Unknown = 0,		/* should be 0 on init */

	AAC_FF_ADTS = 1,
	AAC_FF_ADIF = 2,
	AAC_FF_RAW  = 3,
	AAC_FF_LATM = 4

};

#define SUCCESS 1
#define ERROR   0
#define ERRORFAMENUM 10
#define READLEN 4096 
#define	AuInfTime  60
#define AAC_MAX_NCHANS 6
#define AAC_MAINBUF_SIZE	(768 * AAC_MAX_NCHANS * 2)

typedef struct AacParserImplS
{
    //audio common
    CdxParserT  base;
    CdxStreamT  *stream;

	pthread_cond_t cond;
    cdx_int64   ulDuration;//ms    
    cdx_int64   dFileSize;//total file length
    cdx_int64   dFileOffset; //now read location 
    cdx_int32   mErrno; //Parser Status
    cdx_uint32  nFlags; //cmd
    cdx_int32   bSeekable;
    //aac base
    cdx_int32   nFrames;//samples
    
    cdx_int32   ulChannels;
    cdx_int32   ulSampleRate;
    cdx_int32   ulBitRate;
    cdx_int32   ulformat;
    cdx_int32   uSyncLen;
    cdx_int32   bytesLeft; 
    cdx_uint8   readBuf[2*1024*6*6];
    cdx_uint8   *readPtr;
    cdx_int32   eofReached;
    
}AacParserImplS;



static const int sampRateTab[12] = {
    96000, 88200, 64000, 48000, 44100, 32000, 
    24000, 22050, 16000, 12000, 11025,  8000
};
  
/* channel mapping (table 1.6.3.4) (-1 = unknown, so need to determine mapping based on rules in 8.5.1) */
static const int channelMapTab[8] = {
    -1, 1, 2, 3, 4, 5, 6, 8
};
//static unsigned char readBuf[2*768*2*2];

#define SYNCWORDH     0xff
#define SYNCWORDL     0xf0

#define SYNCWORDL_LATM    0xE0
#define SYNCWORDL_H     0x56

#define ADIF_COPYID_SIZE    10
#define CHAN_ELEM_IS_CPE(x)   (((x) & 0x10) >> 4)  /* bit 4 = SCE/CPE flag */
typedef struct _ADTSHeader {
    /* fixed */
    unsigned char id;                             /* MPEG bit - should be 1 */
    unsigned char layer;                          /* MPEG layer - should be 0 */
    unsigned char protectBit;                     /* 0 = CRC word follows, 1 = no CRC word */
    unsigned char profile;                        /* 0 = main, 1 = LC, 2 = SSR, 3 = reserved */
    unsigned char sampRateIdx;                    /* sample rate index range = [0, 11] */
    unsigned char privateBit;                     /* ignore */
    unsigned char channelConfig;                  /* 0 = implicit, >0 = use default table */
    unsigned char origCopy;                       /* 0 = copy, 1 = original */
    unsigned char home;                           /* ignore */
    
    /* variable */
    unsigned char copyBit;                        /* 1 bit of the 72-bit copyright ID (transmitted as 1 bit per frame) */
    unsigned char copyStart;                      /* 1 = this bit starts the 72-bit ID, 0 = it does not */
    int           frameLength;                    /* length of frame */
    int           bufferFull;                     /* number of 32-bit words left in enc buffer, 0x7FF = VBR */
    unsigned char numRawDataBlocks;               /* number of raw data blocks in frame */
    
    /* CRC */
    int           crcCheckWord;                   /* 16-bit CRC check word (present if protectBit == 0) */
} ADTSHeader;

typedef struct _ADIFHeader {
    unsigned char copyBit;                        /* 0 = no copyright ID, 1 = 72-bit copyright ID follows immediately */
    unsigned char origCopy;                       /* 0 = copy, 1 = original */
    unsigned char home;                           /* ignore */
    unsigned char bsType;                         /* bitstream type: 0 = CBR, 1 = VBR */
    int           bitRate;                        /* bitRate: CBR = bits/sec, VBR = peak bits/frame, 0 = unknown */
    unsigned char numPCE;                         /* number of program config elements (max = 16) */
    int           bufferFull;                     /* bits left in bit reservoir */
    unsigned char copyID[ADIF_COPYID_SIZE];       /* optional 72-bit copyright ID */
} ADIFHeader;
#define IS_ADIF(p)    ((p)[0] == 'A' && (p)[1] == 'D' && (p)[2] == 'I' && (p)[3] == 'F')
//int AC3_FRAME(__audio_file_info_t *AIF,int filelen);

typedef struct _BitStreamInfo {
    unsigned char *bytePtr;
    unsigned int iCache;
    int cachedBits;
    int nBytes;
} BitStreamInfo;

#define AAC_NUM_PROFILES  3
#define AAC_PROFILE_MP    0
#define AAC_PROFILE_LC    1
#define AAC_PROFILE_SSR   2
#define ADTS_HEADER_BYTES 7
#define NUM_SAMPLE_RATES  12
#define NUM_DEF_CHAN_MAPS 8
#define NUM_ELEMENTS    8
#define MAX_NUM_PCE_ADIF  16

#define MAX_NUM_FCE     15
#define MAX_NUM_SCE     15
#define MAX_NUM_BCE     15
#define MAX_NUM_LCE      3
#define MAX_NUM_ADE      7
#define MAX_NUM_CCE     15
/* sizeof(ProgConfigElement) = 82 bytes (if KEEP_PCE_COMMENTS not defined) */
typedef struct _ProgConfigElement {
    unsigned char elemInstTag;                    /* element instance tag */
    unsigned char profile;                        /* 0 = main, 1 = LC, 2 = SSR, 3 = reserved */
    unsigned char sampRateIdx;                    /* sample rate index range = [0, 11] */
    unsigned char numFCE;                         /* number of front channel elements (max = 15) */
    unsigned char numSCE;                         /* number of side channel elements (max = 15) */
    unsigned char numBCE;                         /* number of back channel elements (max = 15) */
    unsigned char numLCE;                         /* number of LFE channel elements (max = 3) */
    unsigned char numADE;                         /* number of associated data elements (max = 7) */
    unsigned char numCCE;                         /* number of valid channel coupling elements (max = 15) */
    unsigned char monoMixdown;                    /* mono mixdown: bit 4 = present flag, bits 3-0 = element number */
    unsigned char stereoMixdown;                  /* stereo mixdown: bit 4 = present flag, bits 3-0 = element number */
    unsigned char matrixMixdown;                  /* matrix mixdown: bit 4 = present flag, bit 3 = unused, 
                                                                     bits 2-1 = index, bit 0 = pseudo-surround enable */
    unsigned char fce[MAX_NUM_FCE];               /* front element channel pair: bit 4 = SCE/CPE flag, bits 3-0 = inst tag */
    unsigned char sce[MAX_NUM_SCE];               /* side element channel pair: bit 4 = SCE/CPE flag, bits 3-0 = inst tag */
    unsigned char bce[MAX_NUM_BCE];               /* back element channel pair: bit 4 = SCE/CPE flag, bits 3-0 = inst tag */
    unsigned char lce[MAX_NUM_LCE];               /* instance tag for LFE elements */
    unsigned char ade[MAX_NUM_ADE];               /* instance tag for ADE elements */
    unsigned char cce[MAX_NUM_BCE];               /* channel coupling elements: bit 4 = switching flag, bits 3-0 = inst tag */

#ifdef KEEP_PCE_COMMENTS
    /* make this optional - if not enabled, decoder will just skip comments */
    unsigned char commentBytes;
    unsigned char commentField[MAX_COMMENT_BYTES];
#endif

} ProgConfigElement;

	

#endif
