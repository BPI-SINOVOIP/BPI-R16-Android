#include <CdxTypes.h>
#include <CdxParser.h>
#include <CdxStream.h>
#include <CdxMemory.h>
#include <CdxAacParser.h>

/**************************************************************************************
 * Function:    SetBitstreamPointer
 *
 * Description: initialize bitstream reader
 *
 * Inputs:      pointer to BitStreamInfo struct
 *              number of bytes in bitstream
 *              pointer to byte-aligned buffer of data to read from
 *
 * Outputs:     initialized bitstream info struct
 *
 * Return:      none
 **************************************************************************************/
static void SetBitstreamPointer(BitStreamInfo *bsi, int nBytes, unsigned char *buf)
{
   /* init bitstream */
   bsi->bytePtr = buf;
   bsi->iCache = 0;   /* 4-byte unsigned int */
   bsi->cachedBits = 0; /* i.e. zero bits in cache */
   bsi->nBytes = nBytes;
}

/**************************************************************************************
 * Function:    RefillBitstreamCache
 *
 * Description: read new data from bitstream buffer into 32-bit cache
 *
 * Inputs:      pointer to initialized BitStreamInfo struct
 *
 * Outputs:     updated bitstream info struct
 *
 * Return:      none
 *
 * Notes:       only call when iCache is completely drained (resets bitOffset to 0)
 *              always loads 4 new bytes except when bsi->nBytes < 4 (end of buffer)
 *              stores data as big-endian in cache, regardless of machine endian-ness
 **************************************************************************************/
static __inline void RefillBitstreamCache(BitStreamInfo *bsi)
{
   int nBytes = bsi->nBytes;
   
   /* optimize for common case, independent of machine endian-ness */
   if (nBytes >= 4) 
   {
        bsi->iCache  = (*bsi->bytePtr++) << 24;
        bsi->iCache |= (*bsi->bytePtr++) << 16;
        bsi->iCache |= (*bsi->bytePtr++) <<  8;
        bsi->iCache |= (*bsi->bytePtr++);
        bsi->cachedBits = 32;
        bsi->nBytes -= 4;
    } 
    else 
    {
        bsi->iCache = 0;
        while (nBytes--) 
        {
            bsi->iCache |= (*bsi->bytePtr++);
            bsi->iCache <<= 8;
        }
        bsi->iCache <<= ((3 - bsi->nBytes)*8);
        bsi->cachedBits = 8*bsi->nBytes;
        bsi->nBytes = 0;
   }
}
/**************************************************************************************
 * Function:    GetBits
 *
 * Description: get bits from bitstream, advance bitstream pointer
 *
 * Inputs:      pointer to initialized BitStreamInfo struct
 *              number of bits to get from bitstream
 *
 * Outputs:     updated bitstream info struct
 *
 * Return:      the next nBits bits of data from bitstream buffer
 *
 * Notes:       nBits must be in range [0, 31], nBits outside this range masked by 0x1f
 *              for speed, does not indicate error if you overrun bit buffer 
 *              if nBits == 0, returns 0
 **************************************************************************************/
static unsigned int GetBits(BitStreamInfo *bsi, int nBits)
{
    unsigned int data, lowBits;
    
    nBits &= 0x1f;              /* nBits mod 32 to avoid unpredictable results like >> by negative amount */
    data = bsi->iCache >> (31 - nBits);   /* unsigned >> so zero-extend */
    data >>= 1;               /* do as >> 31, >> 1 so that nBits = 0 works okay (returns 0) */
    bsi->iCache <<= nBits;          /* left-justify cache */
    bsi->cachedBits -= nBits;       /* how many bits have we drawn from the cache so far */
    
    /* if we cross an int boundary, refill the cache */
    if (bsi->cachedBits < 0) {
        lowBits = -bsi->cachedBits;
        RefillBitstreamCache(bsi);
        data |= bsi->iCache >> (32 - lowBits);    /* get the low-order bits */
        
        bsi->cachedBits -= lowBits;     /* how many bits have we drawn from the cache so far */
        bsi->iCache <<= lowBits;      /* left-justify cache */
    }
    
    return data;
}
/**************************************************************************************
 * Function:    AACFindSyncWord
 *
 * Description: locate the next byte-alinged sync word in the raw AAC stream
 *
 * Inputs:      buffer to search for sync word
 *              max number of bytes to search in buffer
 *
 * Outputs:     none
 *
 * Return:      impl->uSyncLen to first sync word (bytes from start of buf)
 *              -1 if sync not found after searching nBytes
 **************************************************************************************/

int AACFindSyncWord(unsigned char *buf, int nBytes)
{
    int i;
    
    /* find byte-aligned syncword (12 bits = 0xFFF) */
    for (i = 0; i < nBytes - 1; i++) 
    {
        if ( (buf[i+0] & SYNCWORDH) == SYNCWORDH && (buf[i+1] & SYNCWORDL) == SYNCWORDL )
            return i;
    }
    
    return -1;
}
int AACFindSyncWord_LATM(unsigned char *buf, int nBytes)
{
    int i;
    
    /* find byte-aligned syncword (12 bits = 0xFFF) */
    for (i = 0; i < nBytes - 1; i++) {
        if ( (buf[i+0] & SYNCWORDH) == SYNCWORDL_H && (buf[i+1] & SYNCWORDL_LATM) == SYNCWORDL_LATM )
            return i;
    }
    
    return -1;
}
static int AACFindSyncWord_before(unsigned char *buf, int nBytes)
{
    int i;
    
    /* find byte-aligned syncword (12 bits = 0xFFF) */
    for (i = nBytes-2; i > 0; i--) {
        if ( (buf[i+0] & SYNCWORDH) == SYNCWORDH && (buf[i+1] & SYNCWORDL) == SYNCWORDL )
            return i;
    }
    
    return -1;
}
static int AACFindSyncWord_before_LATM(unsigned char *buf, int nBytes)
{
    int i;
    
    /* find byte-aligned syncword (12 bits = 0xFFF) */
    for (i = nBytes-2; i > 0; i--) {
        if ( (buf[i+0] & SYNCWORDH) == SYNCWORDL_H && (buf[i+1] & SYNCWORDL_LATM) == SYNCWORDL_LATM )
            return i;
    }
    
    return -1;
}
static int FillReadBuffer(AacParserImplS *impl, unsigned char *readBuf, unsigned char *readPtr, int bufSize, int bytesLeft)
{
    int nRead;
    
    /* move last, small chunk from end of buffer to start, then fill with new data */
    memmove(readBuf, readPtr, bytesLeft);
    impl->dFileOffset += readPtr - readBuf;
    //nRead = fread(readBuf + bytesLeft, 1, bufSize - bytesLeft, infile);
    nRead = CdxStreamRead(impl->stream, (void *)(readBuf + bytesLeft),bufSize - bytesLeft);
    /* zero-pad to avoid finding false sync word after last frame (from old data in readBuf) */
    if (nRead < bufSize - bytesLeft)
        memset(readBuf + bytesLeft + nRead, 0, bufSize - bytesLeft - nRead);	
    
    return nRead;
}

static int GetBeforeFrame(AacParserImplS *impl )
{
    int retVal = -1;
    ADTSHeader            fhADTS;
    if((impl->readPtr-impl->readBuf)>8)
    {
        impl->readPtr -=7;
        impl->bytesLeft +=7;	
    }
    else
    {
        int ret = impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024;
        if(ret>impl->dFileOffset-impl->uSyncLen)
        {
            ret = impl->dFileOffset - impl->uSyncLen - (impl->readPtr-impl->readBuf+impl->bytesLeft);
            if(ret<=0)
                return 0;
            if(CdxStreamSeek(impl->stream,impl->uSyncLen, SEEK_SET))
            {
                return -1;
            }
            retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), ret);
            if(retVal != ret)
            {
                return -1;
            }
            impl->bytesLeft = 0;
            impl->readPtr = impl->readBuf + ret;
            impl->dFileOffset = impl->uSyncLen;
        }
        else
        {
            if(CdxStreamSeek(impl->stream,-(int)(impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024), SEEK_CUR))
            {
                return -1;
            }
            
            retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), 1024 * 2);
            if(retVal != 1024*2)
            {
                return -1;
            }
            impl->bytesLeft = 0;
            impl->readPtr = impl->readBuf + 2*1024;
            impl->dFileOffset -= 2*1024;
        }
    }
    while(retVal==-1)
    {
        if(impl->ulformat == AAC_FF_ADTS)
        {
            retVal = AACFindSyncWord_before(impl->readBuf,(int)(impl->readPtr-impl->readBuf));
            if(retVal==-1)
            {
                int ret = impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024;
                if(ret>impl->dFileOffset-impl->uSyncLen)
                {	
                    ret = impl->dFileOffset-impl->uSyncLen - (impl->readPtr-impl->readBuf+impl->bytesLeft);
                    if(ret<=0)
                        return 0;
                    if(CdxStreamSeek(impl->stream,impl->uSyncLen, SEEK_SET))
                    {
                        return -1;
                    }
                    retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), ret);
                    if(retVal != ret)
                    {
                        return -1;
                    }
                    impl->bytesLeft = 0;
                    impl->readPtr = impl->readBuf + ret;
                    impl->dFileOffset = impl->uSyncLen;
                }
                else
                {
                    if(CdxStreamSeek(impl->stream,-(int)(impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024), SEEK_CUR))
                    {
                        return -1;
                    }
                    
                    retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), 1024 * 2);
                    if(retVal != 1024*2)
                    {
                        return -1;
                    }
                    impl->bytesLeft = 0;
                    impl->readPtr = impl->readBuf + 2*1024;
                    impl->dFileOffset -= 2*1024;
                }
            }
            else
            {
                impl->bytesLeft += impl->readPtr - impl->readBuf - retVal; 
                impl->readPtr = impl->readBuf + retVal;
                
                fhADTS.layer =            (impl->readPtr[1]>>1)&0x03;//GetBits(&bsi, 2);
                //fhADTS.protectBit =       GetBits(&bsi, 1);
                fhADTS.profile =          (impl->readPtr[2]>>6)&0x03;//GetBits(&bsi, 2);
                fhADTS.sampRateIdx =      (impl->readPtr[2]>>2)&0x0f;//GetBits(&bsi, 4);
                //fhADTS.privateBit =       GetBits(&bsi, 1);
                fhADTS.channelConfig =    ((impl->readPtr[2]<<2)&0x04)|((impl->readPtr[3]>>6)&0x03);//GetBits(&bsi, 3);
				fhADTS.frameLength = ((int)(impl->readPtr[3]&0x3)<<11)|((int)(impl->readPtr[4]&0xff)<<3)|((impl->readPtr[5]>>5)&0x07);
				/* check validity of header */
                if(fhADTS.layer != 0 || /*fhADTS.profile != 1 ||*/
                    fhADTS.sampRateIdx >= 12 || fhADTS.channelConfig >= 8||(fhADTS.frameLength>2*1024*2)||(fhADTS.frameLength>impl->bytesLeft))
                    retVal = -1;
            }
        }
        else//for latm
        {
            retVal = AACFindSyncWord_before_LATM(impl->readBuf,(int)(impl->readPtr-impl->readBuf));
            if(retVal==-1)
            {
                
                int ret = impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024;
                if(ret>impl->dFileOffset-impl->uSyncLen)
                {	
                    ret = impl->dFileOffset-impl->uSyncLen - (impl->readPtr-impl->readBuf+impl->bytesLeft);
                    if(ret<=0)
                        return -2;
                    if(CdxStreamSeek(impl->stream,impl->uSyncLen, SEEK_SET))
                    {
                        return -1;
                    }
                    retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), ret);
                    if(retVal != ret)
                    {
                        return -1;
                    }
                    impl->bytesLeft = 0;
                    impl->readPtr = impl->readBuf + ret;
                    impl->dFileOffset = impl->uSyncLen;
                }
                else
                {
                    if(CdxStreamSeek(impl->stream,-(int)(impl->readPtr-impl->readBuf+impl->bytesLeft+2* 1024), SEEK_CUR))
                    {
                        return -1;
                    }
                    
                    retVal = CdxStreamRead(impl->stream, (void *)(impl->readBuf), 1024 * 2);
                    if(retVal != 1024*2)
                    {
                        return -1;
                    }
                    impl->bytesLeft = 0;
                    impl->readPtr = impl->readBuf + 2*1024;
                    impl->dFileOffset -= 2*1024;
                }
            }
            else
            {
                impl->bytesLeft += impl->readPtr - impl->readBuf - retVal; 
                impl->readPtr = impl->readBuf + retVal;
            }
        }	
    }
    return 0;
        
}
int GetNextFrame(AacParserImplS *impl )
{
    int nBytes;
    int retVal=-1;
    int nRead = 0;
    ADTSHeader            fhADTS;
    //if (bytesLeft < ulchannels * 1024 * ulchannels +3 /*&& !eofReached*/) {
    if (impl->bytesLeft < 2 * 1024 * 2 && !impl->eofReached) {
        nRead = FillReadBuffer(impl,impl->readBuf, impl->readPtr, impl->bytesLeft + 1024 * 2 * 2, impl->bytesLeft);
        impl->bytesLeft += nRead;
        impl->readPtr = impl->readBuf;
        if(nRead == 0)
        {
            return 0;
           
        }
        else if (nRead != 1024*2*2)
        {
            impl->eofReached = 1;
            //return -1;
        }
        
    }
    if(impl->ulformat == AAC_FF_ADTS)
    {
        nBytes = AACFindSyncWord(impl->readPtr,impl->bytesLeft);
        if(nBytes<0)
        {
            return -1;
        }
        
        impl->readPtr +=nBytes;
        impl->bytesLeft -=nBytes;
        fhADTS.frameLength = ((int)(impl->readPtr[3]&0x3)<<11)|((int)(impl->readPtr[4]&0xff)<<3)|((impl->readPtr[5]>>5)&0x07);
        if((fhADTS.frameLength>2*1024*2)||(fhADTS.frameLength>impl->bytesLeft))//frameLength error
        {
            fhADTS.frameLength = 1;	
        }
        impl->readPtr +=fhADTS.frameLength;
        impl->bytesLeft -=fhADTS.frameLength;
        while(retVal<0)
        {
            retVal = 0;
            //	if (bytesLeft < ulchannels * 1024 * ulchannels/*&& !eofReached*/) {
            if (impl->bytesLeft < 2 * 1024 * 2&& !impl->eofReached) {
                nRead = FillReadBuffer(impl,impl->readBuf, impl->readPtr, impl->bytesLeft + 1024* 2 * 2, impl->bytesLeft);
                impl->bytesLeft += nRead;
                impl->readPtr = impl->readBuf;
                /*if(bytesLeft==0)
                {
                return -1;
                }*/
                //if (nRead == 0)
                if(nRead == 0)
                {
                    return 0;
                }
                else if (nRead != 1024*2*2)
                {
                    impl->eofReached = 1;
                    //return -1;
                }
            }
            nBytes = AACFindSyncWord(impl->readPtr,impl->bytesLeft);
            if(nBytes<0)
            {
                return -1;
            }
            impl->readPtr +=nBytes;
            impl->bytesLeft -=nBytes;
          
            fhADTS.layer =            (impl->readPtr[1]>>1)&0x03;//GetBits(&bsi, 2);
            //fhADTS.protectBit =       GetBits(&bsi, 1);
            fhADTS.profile =          (impl->readPtr[2]>>6)&0x03;//GetBits(&bsi, 2);
            fhADTS.sampRateIdx =      (impl->readPtr[2]>>2)&0x0f;//GetBits(&bsi, 4);
            //fhADTS.privateBit =       GetBits(&bsi, 1);
            fhADTS.channelConfig =    ((impl->readPtr[2]<<2)&0x04)|((impl->readPtr[3]>>6)&0x03);//GetBits(&bsi, 3);
            // check validity of header 
            if (fhADTS.layer != 0 ||
            fhADTS.sampRateIdx >= 12 || fhADTS.channelConfig >= 8)
            {
                impl->bytesLeft -=1;
                impl->readPtr +=1;
                retVal = -1;
            }
        }
    }
    else//latm
    {
        nBytes = AACFindSyncWord_LATM(impl->readPtr,impl->bytesLeft);
        if(nBytes<0)
        {
            return -1;
        }
        impl->readPtr +=nBytes;
        impl->bytesLeft -=nBytes;
        fhADTS.frameLength = ((int)(impl->readPtr[1]&0x1F)<<8)|((int)(impl->readPtr[2]&0xff));
        if((fhADTS.frameLength>2*1024*2)||(fhADTS.frameLength>impl->bytesLeft))//frameLength error
        {
            fhADTS.frameLength = 0;	
        }
        fhADTS.frameLength += 3; 
        impl->readPtr +=fhADTS.frameLength;
        impl->bytesLeft -=fhADTS.frameLength;
        while(retVal<0)
        {
            retVal = 0;
            //	if (bytesLeft < ulchannels * 1024 * ulchannels/*&& !eofReached*/) {
            if (impl->bytesLeft < 2 * 1024 * 2&& !impl->eofReached) {
                nRead = FillReadBuffer(impl,impl->readBuf, impl->readPtr, impl->bytesLeft + 1024* 2 * 2, impl->bytesLeft);
                impl->bytesLeft += nRead;
                impl->readPtr = impl->readBuf;
                /*if(bytesLeft==0)
                {
                return -1;
                }*/
                //if (nRead == 0)
                if(nRead == 0)
                {
                    return 0;
                }
                else if (nRead != 1024*2*2)
                {
                    impl->eofReached = 1;
                    //return -1;
                }
            }
            nBytes = AACFindSyncWord_LATM(impl->readPtr,impl->bytesLeft);
            if(nBytes<0)
            {
                return -1;
            }
            impl->readPtr +=nBytes;
            impl->bytesLeft -=nBytes;
        }
    }
    return 0;
}
static int GetFrame(AacParserImplS *impl)
{
    cdx_int32 nBytes;
    cdx_int32 retVal    = -1;
    cdx_int32 nRead     = 0;
    cdx_int32 nSyncLen  = 0;    
    cdx_int32 bytesLeft = impl->bytesLeft;
    cdx_uint8 *readPtr  = impl->readPtr;
    ADTSHeader            fhADTS;
    
    if(impl->ulformat == AAC_FF_ADTS)
    {
        while(retVal<0)
        {
            retVal = 0;
            //	if (bytesLeft < ulchannels * 1024 * ulchannels/*&& !eofReached*/) {
            if (bytesLeft < 2 * 1024 * 2&& !impl->eofReached) {
                nRead = FillReadBuffer(impl,impl->readBuf, impl->readPtr, impl->bytesLeft + 1024* 2 * 2, impl->bytesLeft);
                impl->bytesLeft += nRead;
                impl->readPtr = impl->readBuf;
                bytesLeft += nRead;
                readPtr = impl->readPtr + nSyncLen;
//                if(nRead == 0)
//                {
//                    return -1;
//                }
                if (nRead != 1024*2*2)
                {
                    impl->eofReached = 1;
                    //return -1;
                }
            }
            nBytes = AACFindSyncWord(readPtr,bytesLeft);
            if(nBytes<0)
            {
                CDX_LOGE("AACFindSyncWord error");
                return -1;
            }
            nSyncLen  += nBytes; 
            readPtr   += nBytes;
            bytesLeft -= nBytes;
            
          
            fhADTS.layer =            (readPtr[1]>>1)&0x03;//GetBits(&bsi, 2);
            //fhADTS.protectBit =       GetBits(&bsi, 1);
            fhADTS.profile =          (readPtr[2]>>6)&0x03;//GetBits(&bsi, 2);
            fhADTS.sampRateIdx =      (readPtr[2]>>2)&0x0f;//GetBits(&bsi, 4);
            //fhADTS.privateBit =       GetBits(&bsi, 1);
            fhADTS.channelConfig =    ((readPtr[2]<<2)&0x04)|((readPtr[3]>>6)&0x03);//GetBits(&bsi, 3);            
            fhADTS.frameLength = ((int)(readPtr[3]&0x3)<<11)|((int)(readPtr[4]&0xff)<<3)|((readPtr[5]>>5)&0x07);
            // check validity of header 
            if ((fhADTS.layer != 0 || fhADTS.sampRateIdx >= 12 || fhADTS.channelConfig >= 8)||
            	  ((fhADTS.frameLength>2*1024*2)||(fhADTS.frameLength>bytesLeft)))//frameLength error  
            {
                bytesLeft -=1;
                readPtr +=1;
                retVal = -1;
                nSyncLen +=1;
                if(nSyncLen > READLEN)//maybe sync too long
                {
                    CDX_LOGW("SyncWord :%d",nBytes);
                    impl->readPtr += nSyncLen;
                    impl->bytesLeft -= nSyncLen;
                    nSyncLen = 0;
                }
            }
            else
            {
                break;
            }
        }
    }
    else//latm
    {
        while(retVal<0)
        {
            retVal = 0;
            //	if (bytesLeft < ulchannels * 1024 * ulchannels/*&& !eofReached*/) {
            if (bytesLeft < 2 * 1024 * 2&& !impl->eofReached) {
                nRead = FillReadBuffer(impl,impl->readBuf, impl->readPtr, impl->bytesLeft + 1024* 2 * 2, impl->bytesLeft);
                impl->bytesLeft += nRead;
                impl->readPtr = impl->readBuf;
                bytesLeft += nRead;
                readPtr = impl->readPtr + nSyncLen;
                if (nRead != 1024*2*2)
                {
                    impl->eofReached = 1;
                    //return -1;
                }
            }
            nBytes = AACFindSyncWord_LATM(readPtr,bytesLeft);
            if(nBytes<0)
            {
                CDX_LOGE("LATMFindSyncWord error");
                return -1;
            }
            readPtr +=nBytes;
            bytesLeft -=nBytes;
            nSyncLen  += nBytes;
            fhADTS.frameLength = ((int)(readPtr[1]&0x1F)<<8)|((int)(readPtr[2]&0xff));
            if((fhADTS.frameLength>2*1024*2)||(fhADTS.frameLength>bytesLeft))//frameLength error
            {
                fhADTS.frameLength = 0;
                readPtr += 2;
                bytesLeft -= 2;
                nSyncLen +=2;
                retVal = -1;
                if(nSyncLen > READLEN)//maybe sync too long
                {
                    CDX_LOGW("SyncWord :%d",nBytes);
                    impl->readPtr += nSyncLen;
                    impl->bytesLeft -= nSyncLen;
                    nSyncLen = 0;
                }	
            }
            else
            {
            	 break;
            }
        }
    }
    return  nSyncLen + fhADTS.frameLength;
}

static int GetInfo_GETID3_Len(unsigned char *ptr,int len)
{
    int Id3v2len = 0;
    if(len<10)
    {
        return 0;
    }
    if((ptr[0]==0x49)&&(ptr[1]==0x44)&&(ptr[2]==0x33))
    {
        Id3v2len = ptr[6]<<7 | ptr[7];
        Id3v2len = Id3v2len<<7 | ptr[8];
        Id3v2len = Id3v2len<<7 | ptr[9];
        Id3v2len += 10;
        if (ptr[5] & 0x10)
               Id3v2len += 10;
    }
    return Id3v2len;
      
}
/**************************************************************************************
 * Function:    DecodeProgramConfigElement
 *
 * Description: decode one PCE
 *
 * Inputs:      BitStreamInfo struct pointing to start of PCE (14496-3, table 4.4.2) 
 *
 * Outputs:     filled-in ProgConfigElement struct
 *              updated BitStreamInfo struct
 *
 * Return:      0 if successful, error code (< 0) if error
 *
 * Notes:       #define KEEP_PCE_COMMENTS to save the comment field of the PCE
 *                (otherwise we just skip it in the bitstream, to save memory)
 **************************************************************************************/
static int DecodeProgramConfigElement(ProgConfigElement *pce, BitStreamInfo *bsi)
{
  int i;

  pce->elemInstTag =   GetBits(bsi, 4);
  pce->profile =       GetBits(bsi, 2);
  pce->sampRateIdx =   GetBits(bsi, 4);
  pce->numFCE =        GetBits(bsi, 4);
  pce->numSCE =        GetBits(bsi, 4);
  pce->numBCE =        GetBits(bsi, 4);
  pce->numLCE =        GetBits(bsi, 2);
  pce->numADE =        GetBits(bsi, 3);
  pce->numCCE =        GetBits(bsi, 4);

  pce->monoMixdown = GetBits(bsi, 1) << 4;  /* present flag */
  if (pce->monoMixdown)
    pce->monoMixdown |= GetBits(bsi, 4);  /* element number */

  pce->stereoMixdown = GetBits(bsi, 1) << 4;  /* present flag */
  if (pce->stereoMixdown)
    pce->stereoMixdown  |= GetBits(bsi, 4); /* element number */

  pce->matrixMixdown = GetBits(bsi, 1) << 4;  /* present flag */
  if (pce->matrixMixdown) {
    pce->matrixMixdown  |= GetBits(bsi, 2) << 1;  /* index */
    pce->matrixMixdown  |= GetBits(bsi, 1);     /* pseudo-surround enable */
  }

  for (i = 0; i < pce->numFCE; i++) {
    pce->fce[i]  = GetBits(bsi, 1) << 4;  /* is_cpe flag */
    pce->fce[i] |= GetBits(bsi, 4);     /* tag select */
  }

  for (i = 0; i < pce->numSCE; i++) {
    pce->sce[i]  = GetBits(bsi, 1) << 4;  /* is_cpe flag */
    pce->sce[i] |= GetBits(bsi, 4);     /* tag select */
  }

  for (i = 0; i < pce->numBCE; i++) {
    pce->bce[i]  = GetBits(bsi, 1) << 4;  /* is_cpe flag */
    pce->bce[i] |= GetBits(bsi, 4);     /* tag select */
  }

  for (i = 0; i < pce->numLCE; i++)
    pce->lce[i] = GetBits(bsi, 4);      /* tag select */

  for (i = 0; i < pce->numADE; i++)
    pce->ade[i] = GetBits(bsi, 4);      /* tag select */

  for (i = 0; i < pce->numCCE; i++) {
    pce->cce[i]  = GetBits(bsi, 1) << 4;  /* independent/dependent flag */
    pce->cce[i] |= GetBits(bsi, 4);     /* tag select */
  }

    i = bsi->cachedBits&0x07;
  if(i!=0)GetBits(bsi, i);
  //ByteAlignBitstream(bsi);

#ifdef KEEP_PCE_COMMENTS
  pce->commentBytes = GetBits(bsi, 8);
  for (i = 0; i < pce->commentBytes; i++)
    pce->commentField[i] = GetBits(bsi, 8);
#else
  /* eat comment bytes and throw away */
  i = GetBits(bsi, 8);
  while (i--)
    GetBits(bsi, 8);
#endif

  return 0;
}
/**************************************************************************************
 * Function:    GetNumChannelsADIF
 *
 * Description: get number of channels from program config elements in an ADIF file
 *
 * Inputs:      array of filled-in program config element structures
 *              number of PCE's
 *
 * Outputs:     none
 *
 * Return:      total number of channels in file
 *              -1 if error (invalid number of PCE's or unsupported mode)
 **************************************************************************************/
static int GetNumChannelsADIF(ProgConfigElement *fhPCE, int nPCE)
{
  int i, j, nChans;

  if (nPCE < 1 || nPCE > 16)
    return -1;

  nChans = 0;
  for (i = 0; i < nPCE; i++) {
    /* for now: only support LC, no channel coupling */
    if (/*fhPCE[i].profile != AAC_PROFILE_LC ||*/ fhPCE[i].numCCE > 0)
      return -1;

    /* add up number of channels in all channel elements (assume all single-channel) */
        nChans += fhPCE[i].numFCE;
        nChans += fhPCE[i].numSCE;
        nChans += fhPCE[i].numBCE;
        nChans += fhPCE[i].numLCE;

    /* add one more for every element which is a channel pair */
        for (j = 0; j < fhPCE[i].numFCE; j++) {
            if (CHAN_ELEM_IS_CPE(fhPCE[i].fce[j]))
                nChans++;
        }
        for (j = 0; j < fhPCE[i].numSCE; j++) {
            if (CHAN_ELEM_IS_CPE(fhPCE[i].sce[j]))
                nChans++;
        }
        for (j = 0; j < fhPCE[i].numBCE; j++) {
            if (CHAN_ELEM_IS_CPE(fhPCE[i].bce[j]))
                nChans++;
        }

  }

  return nChans;
}

/**************************************************************************************
 * Function:    GetSampleRateIdxADIF
 *
 * Description: get sampling rate index from program config elements in an ADIF file
 *
 * Inputs:      array of filled-in program config element structures
 *              number of PCE's
 *
 * Outputs:     none
 *
 * Return:      sample rate of file
 *              -1 if error (invalid number of PCE's or sample rate mismatch)
 **************************************************************************************/
static int GetSampleRateIdxADIF(ProgConfigElement *fhPCE, int nPCE)
{
    int i, idx;
    
    if (nPCE < 1 || nPCE > MAX_NUM_PCE_ADIF)
        return -1;
    
    /* make sure all PCE's have the same sample rate */
    idx = fhPCE[0].sampRateIdx;
    for (i = 1; i < nPCE; i++) {
        if (fhPCE[i].sampRateIdx != idx)
            return -1;
    }
    
    return idx;
}
static int UnpackADIFHeader(AacParserImplS *impl,unsigned char *ptr,int len)
{
    int ret;
    int i;
    int ulChannels,ulSampleRate;
    ADIFHeader fhADIF;
    BitStreamInfo bsi;
    ProgConfigElement pce[16];
    SetBitstreamPointer(&bsi, len-4, (unsigned char*)(ptr+4));
    if(GetBits(&bsi, 1))
    {
        for (i = 0; i < 9; i++)
            fhADIF.copyID[i] = GetBits(&bsi, 8);
    }
    fhADIF.origCopy = GetBits(&bsi, 1);
    fhADIF.home =     GetBits(&bsi, 1);
    fhADIF.bsType =   GetBits(&bsi, 1);
    fhADIF.bitRate =  GetBits(&bsi, 23);
    fhADIF.numPCE =   GetBits(&bsi, 4) + 1; /* add 1 (so range = [1, 16]) */
    if (fhADIF.bsType == 0)
    fhADIF.bufferFull = GetBits(&bsi, 20);
    /* parse all program config elements */
    for (i = 0; i < fhADIF.numPCE; i++)
        DecodeProgramConfigElement(pce + i, &bsi);
    
    /* byte align */
    //ByteAlignBitstream(&bsi);
    ret = bsi.cachedBits&0x07;
    if(ret!=0)GetBits(&bsi, ret);
    
    /* update codec info */
    //  AIF->ulChannels = GetNumChannelsADIF(pce, fhADIF.numPCE);
    //  AIF->ulSampleRate = GetSampleRateIdxADIF(pce, fhADIF.numPCE);
    ulChannels = GetNumChannelsADIF(pce, fhADIF.numPCE);
    ulSampleRate = GetSampleRateIdxADIF(pce, fhADIF.numPCE);
    
    /* check validity of header */
    //if (AIF->ulChannels < 0 || AIF->ulSampleRate < 0 || AIF->ulSampleRate >= 12)
    if (ulChannels < 0 || ulSampleRate < 0 || ulSampleRate >= 12)
    {
        CDX_LOGE("ERROR: ulChannels:%d,ulSampleRate[0-11]:%d ",ulChannels,ulSampleRate);
        return ERROR;
    }
    impl->ulChannels  = ulChannels;
    impl->ulSampleRate = sampRateTab[ulSampleRate];
    if(fhADIF.bitRate!=0)
    {
        impl->ulBitRate =fhADIF.bitRate;
        impl->ulDuration = (int)(((double)(impl->dFileSize-impl->uSyncLen) * (double)(8))*1000 /((double)impl->ulBitRate) ); 
    }
    return SUCCESS;
}

static int AacInit(CdxParserT* parameter)
{
    cdx_int32 ret = 0;
    int ID3Len = 0;
    int readlength = 0;
    int readlength_copy = 0;
    int i = 0;
    int nBytes = 0;
    int retVal = -1;
    int frameOn = 0;
    int frameOn_copy = 0;
    ADTSHeader            fhADTS;
    struct AacParserImplS *impl          = NULL;
    
    impl = (AacParserImplS *)parameter;
    impl->dFileSize = CdxStreamSize(impl->stream);
    if(CdxStreamSeek(impl->stream,0, SEEK_SET))
    {
        CDX_LOGE("CdxStreamSeek error");
        goto AAC_error;
    }
    
    ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, 10);
    if(ret != 10)
    {
        CDX_LOGE("CdxStreamRead error");
        goto AAC_error;
    }
    //maybe id3 
    ID3Len = GetInfo_GETID3_Len(impl->readBuf,10);
    readlength = ID3Len;
    
    if(CdxStreamSeek(impl->stream,ID3Len,SEEK_SET))
    {
        CDX_LOGE("CdxStreamSeek error");
        goto AAC_error;
    }
    ret = CdxStreamRead(impl->stream, (void *)(impl->readBuf), READLEN);
    if(ret != READLEN)
    {
        CDX_LOGE("CdxStreamRead error");
        goto AAC_error;
    } 
    if (IS_ADIF(impl->readBuf)) 
    {
        /* unpack ADIF header */
        
        impl->ulformat = AAC_FF_ADIF;
        ret = UnpackADIFHeader(impl,impl->readBuf,READLEN);
        if(ret==ERROR)
        {
            CDX_LOGE("UnpackADIFHeader error");
            goto AAC_error;
        }
    }
    else
    {
        {
            //BsInfo      *BSINFO = (BsInfo *)AIF->ulBSINFO;
            unsigned char *buf = impl->readBuf;
            for (i = 0; i < READLEN-1; i++) 
            {
                if ( (buf[i+0] & SYNCWORDH) == SYNCWORDH && (buf[i+1] & SYNCWORDL) == SYNCWORDL )
                {
                    impl->ulformat = AAC_FF_ADTS;
                    break;
                }
                if ( (buf[i+0] & SYNCWORDH) == SYNCWORDL_H && (buf[i+1] & SYNCWORDL_LATM) == SYNCWORDL_LATM )
                {
                    impl->ulformat = AAC_FF_LATM;
                    break;
                }
            
            }
        }
        
        nBytes = 0;
        if(impl->ulformat == AAC_FF_ADTS)
        {
            unsigned char *buf = impl->readBuf;
            
            for(i=0;(i<ERRORFAMENUM )&&(retVal<0);i++)
            {
                retVal = 0;
                if(2*nBytes>READLEN)
                {
                    if(CdxStreamSeek(impl->stream,readlength,SEEK_SET))
                    {
                        CDX_LOGE("CdxStreamSeek error");
                        goto AAC_error;
                    }
                    ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, READLEN);
                    if(ret != READLEN)
                    {
                        CDX_LOGE("CdxStreamRead error");
                        goto AAC_error;
                    }
                    nBytes = 0; 
                }
                nBytes = AACFindSyncWord(impl->readBuf,READLEN - nBytes);//maybe 4*1024;
                if(nBytes<0)
                {
                    CDX_LOGE("AACFindSyncWord error");
                    goto AAC_error;
                }
                readlength += nBytes;
                if(readlength>=impl->dFileSize)
                {
                    readlength =impl->dFileSize;
                    CDX_LOGE("dFileSize error");
                    goto AAC_error;
                }
                if(nBytes+7>READLEN)
                {
                    if(CdxStreamSeek(impl->stream,readlength,SEEK_SET))
                    {
                        CDX_LOGE("CdxStreamSeek error");
                        goto AAC_error;
                    }
                    ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, READLEN);
                    if(ret != READLEN)
                    {
                        CDX_LOGE("CdxStreamRead error");
                        goto AAC_error;
                    }
                    nBytes = 0; 
                
                }
                fhADTS.layer =            (impl->readBuf[nBytes + 1]>>1)&0x03;//GetBits(&bsi, 2);
                //fhADTS.protectBit =       GetBits(&bsi, 1);
                fhADTS.profile =          (impl->readBuf[nBytes + 2]>>6)&0x03;//GetBits(&bsi, 2);
                fhADTS.sampRateIdx =      (impl->readBuf[nBytes + 2]>>2)&0x0f;//GetBits(&bsi, 4);
                //fhADTS.privateBit =       GetBits(&bsi, 1);
                fhADTS.channelConfig =    ((impl->readBuf[nBytes + 2]<<2)&0x04)|((impl->readBuf[nBytes + 3]>>6)&0x03);//GetBits(&bsi, 3);
                /* check validity of header */
                fhADTS.frameLength = ((int)(impl->readBuf[nBytes + 3]&0x3)<<11)|((int)(impl->readBuf[nBytes + 4]&0xff)<<3)|((impl->readBuf[nBytes + 5]>>5)&0x07);
                if (((fhADTS.layer != 0 )&&(fhADTS.layer != 3 ))/*|| fhADTS.profile != 1*/ ||
                fhADTS.sampRateIdx >= 12 || fhADTS.channelConfig >= 8 ||fhADTS.frameLength>2*1024*2)
                {
                    nBytes +=1 ;
                    readlength +=1;
                    retVal = -1;
                }
                else
                {
                    unsigned char cBuf[2];
                    if(fhADTS.frameLength+2>READLEN-nBytes)
                    {
                        if(CdxStreamSeek(impl->stream,readlength + fhADTS.frameLength,SEEK_SET))
                        {
                            CDX_LOGE("CdxStreamSeek error");
                            goto AAC_error;
                        }
                        ret = CdxStreamRead(impl->stream, (void *)cBuf, 2);
                        if(ret != READLEN)
                        {
                            CDX_LOGE("CdxStreamRead error");
                            goto AAC_error;
                        }
                        buf =  cBuf;
                    }
                    else
                    {
                    	buf = impl->readBuf + nBytes + fhADTS.frameLength;
                    }
                    if ( (buf[0] & SYNCWORDH) == SYNCWORDH && (buf[1] & SYNCWORDL) == SYNCWORDL )
                    {
                        retVal = 0;
                        break;
                    }
                    else
                    {
                        nBytes +=1 ;
                        readlength +=1;
                        retVal = -1;
                    
                    }
                
                }
                if(readlength-ID3Len>2*1024)
                {
                    CDX_LOGE("readlength error");
                    goto AAC_error;
                }                   
            }
            if(i==ERRORFAMENUM)
            {
                CDX_LOGE("ERRORFAMENUM error");
                goto AAC_error;
            }
            impl->ulSampleRate = sampRateTab[fhADTS.sampRateIdx];
        }
        ////////////////////////////////////////////////////////////////
        else//AAC_format = AAC_FF_LATM
        {
            unsigned char *buf = impl->readBuf;
            int nLength = 0;
            for(i=0;(i<ERRORFAMENUM )&&(retVal<0);i++)
            //while(retVal<0)
            {
                retVal = 0;
                if(2*nBytes>READLEN)
                {
                    if(CdxStreamSeek(impl->stream,readlength,SEEK_SET))
                    {
                        goto AAC_error;
                    }
                    ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, READLEN);
                    if(ret != READLEN)
                    {
                        goto AAC_error;
                    }
                    nBytes = 0; 
                }
                nBytes = AACFindSyncWord_LATM(impl->readBuf,READLEN - nBytes);
                if(nBytes<0)goto AAC_error;
                readlength += nBytes;
                if(readlength>=impl->dFileSize)
                {
                    readlength =impl->dFileSize;
                    goto AAC_error;
                }
                if(nBytes+7>READLEN)
                {
                    if(CdxStreamSeek(impl->stream,readlength,SEEK_SET))
                    {
                        goto AAC_error;
                    }
                    ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, READLEN);
                    if(ret != READLEN)
                    {
                        goto AAC_error;
                    }
                    nBytes = 0;
                }
                /****************************************************************/
                nLength = (((int)impl->readBuf[nBytes + 1]&0x1f)<<8)|((int)impl->readBuf[nBytes + 2]&0xff);
                fhADTS.frameLength = nLength + 3;
                if((impl->readBuf[nBytes]&0xC0)||(fhADTS.frameLength>4096))
                {
                		nBytes +=1 ;
                    readlength +=1;
                    retVal = -1;
                
                }
                else
                {
                    unsigned char cBuf[2];
                    if(fhADTS.frameLength+2>READLEN-nBytes)
                    {
                        if(CdxStreamSeek(impl->stream,readlength + fhADTS.frameLength,SEEK_SET))
                        {
                            goto AAC_error;
                        }
                        ret = CdxStreamRead(impl->stream, (void *)cBuf, 2);
                        if(ret != READLEN)
                        {
                            goto AAC_error;
                        }
                        buf =  cBuf;
                    }
                    else
                    {
                    	buf = impl->readBuf + nBytes + fhADTS.frameLength;
                    }
                    if ( (buf[0] & SYNCWORDH) == SYNCWORDL_H && (buf[1] & SYNCWORDL_LATM) == SYNCWORDL_LATM )
                    {
                        retVal = 0;
                        //useSameStreamMux == 0 && audioMuxVersion==0
                        fhADTS.layer =  impl->readBuf[nBytes + 1 + 3]&0x07;//GetBits(&bsi,3); //3 uimsbf
                        //audioObjectType = (GetInfo_ShowBs(2,1,AIF)>>3)&0x1F;//GetBits(&bsi,5); //5 bslbf
                        fhADTS.sampRateIdx = 2*((impl->readBuf[nBytes + 2 + 3])&0x07)+ ((impl->readBuf[nBytes + 3+3]>>7)&0x01);//GetBits(&bsi,4); //4 bslbf
                        if ( fhADTS.sampRateIdx==0xf )
                        {
                            //GetBits(&bsi,24); //24 uimsbf
                            impl->ulSampleRate = ((impl->readBuf[nBytes +3+3]&0x07F)<<17)
                                                           | (impl->readBuf[nBytes +4+3] << 9)
                                                           | (impl->readBuf[nBytes +5+3] << 1)
                                                           | ((impl->readBuf[nBytes +6+3] >> 7)&0x01);
                            fhADTS.channelConfig = ((impl->readBuf[nBytes +6+3] >> 3)&0x0F);//GetBits(&bsi,4);
                        }
                        else
                        {
                            impl->ulSampleRate = sampRateTab[fhADTS.sampRateIdx];
                            fhADTS.channelConfig = ((impl->readBuf[nBytes +3+3] >> 3)&0x0F);
                        }
                        break;
                    }
                    else
                    {
                        nBytes +=1 ;
                        readlength +=1;
                        retVal = -1;
                    }    
                }
                
                /*******************************************************************/
            			    			
            }
            if(i==ERRORFAMENUM)
            {
                goto AAC_error;
            }
        
        }
        ////////////////////////////////////////////////////////////////
        impl->ulChannels		= channelMapTab[fhADTS.channelConfig];
    
        impl->uSyncLen = readlength;
        impl->dFileOffset = readlength;        
        impl->readPtr = impl->readBuf;
        impl->bytesLeft = 0;
        ret =0;
        if(CdxStreamSeek(impl->stream,impl->uSyncLen,SEEK_SET))
        {
        	CDX_LOGE("CdxStreamSeek error");
        	goto AAC_error;
        }
        #ifndef TRYALL
            
        ret = 10*impl->ulSampleRate /1024;
        for(i=0;i<ret;i++)
        {
            retVal = GetNextFrame(impl);
            frameOn++;
            if(retVal==-1)break;
        }
        readlength =impl->dFileOffset + (cdx_int32)((uintptr_t)impl->readPtr - (uintptr_t)impl->readBuf);
        if(i==ret)
        {
            readlength_copy =readlength;
            frameOn_copy=frameOn;
            ret = AuInfTime*impl->ulSampleRate /1024;
            for(i=0;i<ret;i++)
            {
                retVal = GetNextFrame(impl);
                frameOn++;
                if(retVal==-1)break;
            }
        }
        readlength =impl->dFileOffset + (cdx_int32)((uintptr_t)impl->readPtr - (uintptr_t)impl->readBuf);
        if(i==ret)
        {
            readlength =readlength - readlength_copy + impl->uSyncLen ;
            frameOn -= frameOn_copy;
        }
        #else
        
        while(ret>=0)
        {
            ret = GetNextFrame(impl);
            frameOn++;
        }
        readlength = impl->dFileSize;
        #endif
   
        impl->ulBitRate			= (int)((double)((double)(readlength-impl->uSyncLen)*(double)8*(double)impl->ulSampleRate)/(double)((double)frameOn*1024));
        impl->ulDuration    = (int)((double)((double)(impl->dFileSize-impl->uSyncLen)*8000) /(double)impl->ulBitRate);//	
        
        //AAC_format = AAC_FF_ADTS;
    }
    if(impl->ulBitRate>impl->ulSampleRate*impl->ulChannels*16)
    {
        CDX_LOGE("aac ulBitRate error.rate:%d,fs:%d,ch:%d",impl->ulBitRate,impl->ulSampleRate,impl->ulChannels);
        goto AAC_error;//for aac not bitrate lag
    }
////////////////////////////////////////////////////////////////
    if(CdxStreamSeek(impl->stream,ID3Len,SEEK_SET))
    {
        CDX_LOGE("CdxStreamSeek error");
        goto AAC_error;
    }
    impl->uSyncLen = ID3Len;
    impl->dFileOffset = ID3Len;
    impl->readPtr = impl->readBuf;
    impl->eofReached = 0;
    impl->bytesLeft = 0;
    impl->mErrno = PSR_OK;
	pthread_cond_signal(&impl->cond);
    CDX_LOGW("AAC ulDuration:%lld",impl->ulDuration);
    return 0;
AAC_error:
    CDX_LOGE("AacOpenThread fail!!!");
    impl->mErrno = PSR_OPEN_FAIL;
	pthread_cond_signal(&impl->cond);
    return -1;
}

static cdx_int32 __AacParserControl(CdxParserT *parser, cdx_int32 cmd, void *param)
{
    (void)param;
	cdx_int64 streamSeekPos = 0, timeUs = 0;
	cdx_int32 ret = 0,nFrames = 0;
    struct AacParserImplS *impl = NULL;    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    switch (cmd)
    {
    case CDX_PSR_CMD_DISABLE_AUDIO:
    case CDX_PSR_CMD_DISABLE_VIDEO:
    case CDX_PSR_CMD_SWITCH_AUDIO:
        break;
    case CDX_PSR_CMD_SET_FORCESTOP:
        CdxStreamForceStop(impl->stream);
        break;
    case CDX_PSR_CMD_CLR_FORCESTOP:
        CdxStreamClrForceStop(impl->stream);
        break;
	case CDX_PSR_CMD_REPLACE_STREAM:
        CDX_LOGD("replace stream!!!");
        if(impl->stream)
        {
            CdxStreamClose(impl->stream);
        }
        impl->stream = (CdxStreamT*)param;
        impl->eofReached = 0;
        impl->mErrno = PSR_OK;
        break;
    case CDX_PSR_CMD_STREAM_SEEK:
        if(!impl->stream)
        {
            CDX_LOGE("mAACParser->cdxStream == NULL, can not stream control");
            return -1;
        }
        if(!CdxStreamSeekAble(impl->stream))
        {
            CDX_LOGV("CdxStreamSeekAble == 0");
            return 0;
        }
        streamSeekPos = *(cdx_int64 *)param;
        ret = CdxStreamSeek(impl->stream, streamSeekPos, SEEK_SET);
        if(ret < 0)
        {
            CDX_LOGE("CdxStreamSeek fail");
        }
		memset(impl->readBuf,0x00,2*1024*6*6);
		impl->readPtr = impl->readBuf;
        impl->bytesLeft = 0;
		ret = GetNextFrame(impl);
		if(ret<0)
		{
		    CDX_LOGE("Controll GetNextFrame error");
		    return CDX_FAILURE;	
		}
        impl->eofReached = 0;
		nFrames = (timeUs/1000000) * impl->ulSampleRate /1024;
        impl->nFrames = nFrames;
		
        break;
    default :
        CDX_LOGW("not implement...(%d)", cmd);
        break;
    }
    impl->nFlags = cmd;
  
    return CDX_SUCCESS;
}

static cdx_int32 __AacParserPrefetch(CdxParserT *parser, CdxPacketT *pkt)
{
    struct AacParserImplS *impl = NULL;
    int ret = 0;
    int nReadLen = 0;
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    pkt->type = CDX_MEDIA_AUDIO;
    if(impl->ulformat == AAC_FF_ADIF)
    {
        nReadLen = AAC_MAINBUF_SIZE;
        pkt->pts = -1;
        ret = CdxStreamRead(impl->stream, (void *)impl->readBuf, AAC_MAINBUF_SIZE);
        if(ret < 0)
        {
            CDX_LOGE("CdxStreamRead fail");
            impl->mErrno = PSR_IO_ERR;
            return CDX_FAILURE;
        }
        else if(ret == 0)
        {
           CDX_LOGE("CdxStream EOS");
           impl->mErrno = PSR_EOS;
           return CDX_FAILURE;
        }
        if(ret != AAC_MAINBUF_SIZE)
        {
           CDX_LOGE("CdxStream EOS");
           impl->mErrno = PSR_EOS;
        }
        
        impl->readPtr = impl->readBuf;
        impl->bytesLeft = ret;
        pkt->length = ret;
    }
    else if(impl->ulformat == AAC_FF_ADTS)
    {
        ret = GetFrame(impl);
        if(ret<0)
        {
            if(impl->eofReached)
            {
                CDX_LOGE("CdxStream EOS");
                impl->mErrno = PSR_EOS;            	
            }
            pkt->length = impl->bytesLeft;
            pkt->pts = -1;
            CDX_LOGE("maybe sync err");            
        } 
        else
        {
            pkt->length =  ret;
            pkt->pts = (cdx_int64)impl->nFrames* 1024 *1000000/impl->ulSampleRate;
        }   
    	
    }
    
    CDX_LOGV("****len:%d,",pkt->length);
    if(pkt->length == 0)
	{
	   CDX_LOGE("CdxStream EOS");
	   impl->mErrno = PSR_EOS;
	   return CDX_FAILURE;
	}
    
    pkt->flags |= (FIRST_PART|LAST_PART);
    //pkt->pts = (cdx_int64)impl->frames*impl->frame_samples *1000000/impl->aac_format.nSamplesPerSec;//-1;
    
    return CDX_SUCCESS;
}

static cdx_int32 __AacParserRead(CdxParserT *parser, CdxPacketT *pkt)
{
    struct AacParserImplS *impl = NULL;
    CdxStreamT *cdxStream = NULL;
    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    cdxStream = impl->stream;
    
    if(pkt->length <= pkt->buflen) 
    {
        memcpy(pkt->buf, impl->readPtr, pkt->length);
    }
    else
    {
        memcpy(pkt->buf, impl->readPtr, pkt->buflen);
        memcpy(pkt->ringBuf, impl->readPtr + pkt->buflen, pkt->length - pkt->buflen);
    }
    
    impl->readPtr   += pkt->length;
    impl->bytesLeft -= pkt->length;
    CDX_LOGV("****len:%d,",pkt->length);
    if(pkt->pts != -1)
    {
        impl->nFrames++;
    }
    if(pkt->length == 0)
    {
       CDX_LOGE("CdxStream EOS");
       impl->mErrno = PSR_EOS;
       return CDX_FAILURE;
    }
    
    // TODO: fill pkt
    return CDX_SUCCESS;
}

static cdx_int32 __AacParserGetMediaInfo(CdxParserT *parser, CdxMediaInfoT *mediaInfo)
{
    struct AacParserImplS *impl;
    struct CdxProgramS *cdxProgram = NULL;
    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    memset(mediaInfo, 0x00, sizeof(*mediaInfo));

    if(impl->mErrno != PSR_OK)
    {
        CDX_LOGE("audio parse status no PSR_OK");
        return CDX_FAILURE;
    }
  
    mediaInfo->programNum = 1;
    mediaInfo->programIndex = 0;
    cdxProgram = &mediaInfo->program[0];
    memset(cdxProgram, 0, sizeof(struct CdxProgramS));
    cdxProgram->id = 0;
    cdxProgram->audioNum = 1;
    cdxProgram->videoNum = 0;//
    cdxProgram->subtitleNum = 0;
    cdxProgram->audioIndex = 0;
    cdxProgram->videoIndex = 0;
    cdxProgram->subtitleIndex = 0;
            
    impl->bSeekable = CdxStreamSeekAble(impl->stream);
    if(impl->bSeekable)
    {
        if((impl->ulformat != AAC_FF_ADTS)&&(impl->ulformat != AAC_FF_LATM))
        {
            impl->bSeekable = 0;
        }
    }
    mediaInfo->bSeekable = impl->bSeekable;
    mediaInfo->fileSize = impl->dFileSize;
    
    cdxProgram->duration = impl->ulDuration;
    cdxProgram->audio[0].eCodecFormat    = AUDIO_CODEC_FORMAT_RAAC;
    cdxProgram->audio[0].eSubCodecFormat = 0;//impl->WavFormat.wFormatag | impl->nBigEndian;
    cdxProgram->audio[0].nChannelNum     = impl->ulChannels;
    cdxProgram->audio[0].nBitsPerSample  = 16;
    cdxProgram->audio[0].nSampleRate     = impl->ulSampleRate;
    cdxProgram->audio[0].nAvgBitrate     = impl->ulBitRate;
    //cdxProgram->audio[0].nMaxBitRate;
    //cdxProgram->audio[0].nFlags
    cdxProgram->audio[0].nBlockAlign     = 0;//impl->WavFormat.nBlockAlign;
    //CDX_LOGD("eSubCodecFormat:0x%04x,ch:%d,fs:%d",cdxProgram->audio[0].eSubCodecFormat,cdxProgram->audio[0].nChannelNum,cdxProgram->audio[0].nSampleRate);
    //CDX_LOGD("AAC ulDuration:%d",cdxProgram->duration);
         
    return CDX_SUCCESS;
}

static cdx_int32 __AacParserSeekTo(CdxParserT *parser, cdx_int64 timeUs)
{ 
    struct AacParserImplS *impl = NULL;
    cdx_int32 nFrames = 0;
    cdx_int32 ret = 0;
    int i = 0;
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    
    if(!impl->bSeekable)
    {
        CDX_LOGE("bSeekable = 0");
        return CDX_FAILURE;
    }
    nFrames = (timeUs/1000000) * impl->ulSampleRate /1024;
    if(nFrames > impl->nFrames)//ff
    {
        int skipframeN = nFrames - impl->nFrames;
        for(i=0;i<skipframeN;i++)
        {
            ret = GetNextFrame(impl);
			if(impl->eofReached == 1)
			{
            	skipframeN = i;
				break;
			}
            if(ret<0)
            {
                CDX_LOGE("GetNextFrame error");
                return CDX_FAILURE;	
            }
        }
        impl->nFrames +=skipframeN;
        
    }
    else if(nFrames < impl->nFrames)
    {
        int skipframeN = impl->nFrames - nFrames;
        for(i=0;i<skipframeN;i++)
        {
            ret = GetBeforeFrame(impl);
            if(ret<0)
            {
                CDX_LOGE("GetBeforeFrame error");
                return CDX_FAILURE;	
            }
		 }
		impl->nFrames -=skipframeN;
        impl->eofReached = 0;
    
    }
    // TODO: not implement now...
    pthread_cond_signal(&impl->cond);
    CDX_LOGI("TODO, seek to now...");
    return CDX_SUCCESS;
}

static cdx_uint32 __AacParserAttribute(CdxParserT *parser)
{
    struct AacParserImplS *impl = NULL;
    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    return 0;
}


static cdx_int32 __AacParserGetStatus(CdxParserT *parser)
{
    struct AacParserImplS *impl = NULL;
    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
#if 0
    if (CdxStreamEos(impl->stream))
    {
        CDX_LOGE("file PSR_EOS! ");
        return PSR_EOS;
    }
#endif	
    return impl->mErrno;
}

static cdx_int32 __AacParserClose(CdxParserT *parser)
{
    struct AacParserImplS *impl = NULL;
    
    impl = CdxContainerOf(parser, struct AacParserImplS, base);
    CdxStreamClose(impl->stream);
    pthread_cond_destroy(&impl->cond);
    CdxFree(impl);
    return CDX_SUCCESS;
}

static struct CdxParserOpsS aacParserOps =
{
    .control      = __AacParserControl,
    .prefetch     = __AacParserPrefetch,
    .read         = __AacParserRead,
    .getMediaInfo = __AacParserGetMediaInfo,
    .seekTo       = __AacParserSeekTo,
    .attribute    = __AacParserAttribute,
    .getStatus    = __AacParserGetStatus,
    .close        = __AacParserClose,
    .init         = AacInit
};

static cdx_int32 AacProbe(CdxStreamProbeDataT *p)
{
    cdx_char *ptr;
    cdx_int32 offset = 0;
    ptr = p->buf;
    /*We give up the judgement of id3 tag, instead of moving it to id3 parser*/
#if 0
	if((p->buf[0]==0x49)&&(p->buf[1]==0x44)&&(p->buf[2]==0x33))//for id3
	{
        CDX_LOGE("AAC ID3 SKIPING!");
		cdx_int32 Id3v2len = 0;
		Id3v2len = ((cdx_int32)(p->buf[6]&0x7f))<<7 | ((cdx_int32)(p->buf[7]&0x7f));
		Id3v2len = Id3v2len<<7 | ((cdx_int32)(p->buf[8]&0x7f));
		Id3v2len = Id3v2len<<7 | ((cdx_int32)(p->buf[9]&0x7f));
		Id3v2len +=10;
		offset = Id3v2len;
	}
	
	if(offset > (int)p->len)
	{
        CDX_LOGE("Warning! offset > p->len   offset:%d,len:%d",offset,p->len);
	}
#endif	
    if((((ptr[offset]&0xff)==0xff)&&((ptr[offset + 1]&0xf0)==0xf0)&&(((ptr[offset + 1] & (0x3<<1)) == 0x0<<1)||((ptr[offset + 1] & (0x3<<1)) == 0x3<<1))&&((ptr[offset + 2] & (0xf<<2)) < (12<<2))&&(((ptr[offset + 2]&0x01)|(ptr[offset + 3]&(0x03<<6)))!=0))
    ||(((ptr[offset]&0xff)==0x56)&&((ptr[offset + 1]&0xe0)==0xe0))
    ||(((ptr[offset]&0xff)=='A')&&((ptr[offset + 1]&0xff)=='D')&&((ptr[offset + 2]&0xff)=='I')&&((ptr[offset + 3]&0xff)=='F')))
    {
        return CDX_TRUE;
    }
    
    CDX_LOGE("audio probe fail!!!");
    return CDX_FALSE;
}

static cdx_uint32 __AacParserProbe(CdxStreamProbeDataT *probeData)
{
    CDX_CHECK(probeData);
    if(probeData->len < 4)
    {
        CDX_LOGE("Probe data is not enough.");
        return 0;
    }
    
    if(!AacProbe(probeData))
    {
        CDX_LOGE("aac probe failed.");
        return 0;
    }
    CDX_LOGW("aac probe ok.");
    return 100;    
}

static CdxParserT *__AacParserOpen(CdxStreamT *stream, cdx_uint32 flags)
{
    (void)flags;
    //cdx_int32 ret = 0;
    struct AacParserImplS *impl;
    impl = CdxMalloc(sizeof(*impl));

    memset(impl, 0x00, sizeof(*impl));
    impl->stream = stream;
    impl->base.ops = &aacParserOps;
	pthread_cond_init(&impl->cond, NULL);
    //ret = pthread_create(&impl->openTid, NULL, AacOpenThread, (void*)impl);
    //CDX_FORCE_CHECK(!ret);
    impl->mErrno = PSR_INVALID;
    return &impl->base;
}

struct CdxParserCreatorS aacParserCtor =
{
    .probe = __AacParserProbe,
    .create  = __AacParserOpen
};
