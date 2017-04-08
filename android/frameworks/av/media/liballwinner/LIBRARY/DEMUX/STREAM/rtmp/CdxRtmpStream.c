#include <CdxLog.h>
#include "CdxRtmpStream.h"

#define URL_RDONLY 0
#define URL_WRONLY 1
#define URL_RDWR   2


enum RtmpStreamStateE
{
    RTMP_STREAM_IDLE          = 0x00L,
    RTMP_STREAM_CONNECTING    = 0x01L,
    RTMP_STREAM_READING       = 0x02L,
    RTMP_STREAM_SEEKING       = 0x03L,
    RTMP_STREAM_FORCESTOPPED  = 0x04L,
};


enum {OPT_STR=0, OPT_INT, OPT_BOOL, OPT_CONN};

#if 0 //not use
static const char *optinfo[]= 
{
	"string", "integer", "boolean", "AMF"
};
#endif

#define offsetof_x(T, F) ((size_t)((char *)&((T *)0)->F))
#define OFF(x)	offsetof_x(aw_rtmp_t,x)
#define AVC(str)	{str,sizeof(str)-1}

#undef OSS
#ifdef _WIN32
#define OSS	"WIN"
#elif defined(__sun__)
#define OSS	"SOL"
#elif defined(__APPLE__)
#define OSS	"MAC"
#elif defined(__linux__)
#define OSS	"LNX"
#else
#define OSS	"GNU"
#endif

#define DEF_VERSTR	OSS " 10,0,32,18"
#define RTMP_LF_AUTH	0x0001	/* using auth param */
#define RTMP_LF_LIVE	0x0002	/* stream is live */
#define RTMP_LF_SWFV	0x0004	/* do SWF verification */
#define RTMP_LF_PLST	0x0008	/* send playlist before play */
#define RTMP_LF_BUFX	0x0010	/* toggle stream on BufferEmpty msg */
#define RTMP_LF_FTCU	0x0020	/* free tcUrl on close */

static struct aw_rtmp_urlopt 
{
    aw_rtmp_aval_t name;
    unsigned int off;
    int otype;
    int omisc;
    char *use;
} options[19] = 
{
  { AVC("socks"),     OFF(Link.sockshost),     OPT_STR,  0,"Use the specified SOCKS proxy" },
  { AVC("app"),       OFF(Link.app),           OPT_STR,  0,"Name of target app on server" },
  { AVC("tcUrl"),     OFF(Link.tcUrl),         OPT_STR,  0,"URL to played stream" },
  { AVC("pageUrl"),   OFF(Link.pageUrl),       OPT_STR,  0,"URL of played media's web page" },
  { AVC("swfUrl"),    OFF(Link.swfUrl),        OPT_STR,  0,"URL to player SWF file" },
  { AVC("flashver"),  OFF(Link.flashVer),      OPT_STR,  0,"Flash version string (default " DEF_VERSTR ")" },
  { AVC("conn"),      OFF(Link.extras),        OPT_CONN, 0,"Append arbitrary AMF data to Connect message" },
  { AVC("playpath"),  OFF(Link.playpath),      OPT_STR,  0,"Path to target media on server" },
  { AVC("playlist"),  OFF(Link.lFlags),        OPT_BOOL, RTMP_LF_PLST,"Set playlist before play command" },
  { AVC("live"),      OFF(Link.lFlags),        OPT_BOOL, RTMP_LF_LIVE,"Stream is live, no seeking possible" },
  { AVC("subscribe"), OFF(Link.subscribepath), OPT_STR,  0,"Stream to subscribe to" },
  { AVC("jtv"), OFF(Link.usherToken),          OPT_STR,  0,"Justin.tv authentication token" },
  { AVC("token"),     OFF(Link.token),	       OPT_STR,  0,"Key for SecureToken response" },
  { AVC("swfVfy"),    OFF(Link.lFlags),        OPT_BOOL, RTMP_LF_SWFV,"Perform SWF Verification" },
  { AVC("swfAge"),    OFF(Link.swfAge),        OPT_INT,  0,"Number of days to use cached SWF hash" },
  { AVC("start"),     OFF(Link.seekTime),      OPT_INT,  0,"Stream start position in milliseconds" },
  { AVC("stop"),      OFF(Link.stopTime),      OPT_INT,  0,"Stream stop position in milliseconds" },
  { AVC("buffer"),    OFF(m_nBufferMS),        OPT_INT,  0,"Buffer time in milliseconds" },
  { AVC("timeout"),   OFF(Link.timeout),       OPT_INT,  0,"Session timeout in seconds" }
};

static const aw_rtmp_aval_t truth[] = {
	AVC("1"),
	AVC("on"),
	AVC("yes"),
	AVC("true"),
	{0,0}
};


const char RTMPProtocolStringsLower[][7] = {
  "rtmp",
  "rtmpt",
  "rtmpe",
  "rtmpte",
  "rtmps",
  "rtmpts",
  "",
  "",
  "rtmfp"
};


#define RTMP_DEFAULT_CHUNKSIZE	128
#define FALSE	0
#define TRUE	1
#define RTMP_PROBE_DATA_LEN 1024 


extern void aw_rtmp_close(aw_rtmp_t *r);
extern int aw_send_seek(aw_rtmp_t *r, int64_t iTime);
extern  void aw_set_rtmp_parameter(aw_rtmp_t* r);
extern int aw_rtmp_is_connected(aw_rtmp_t *r);
extern int aw_rtmp_read_packet(aw_rtmp_t*r, aw_rtmp_packet_t *packet);
extern int aw_rtmp_client_packet(aw_rtmp_t*r, aw_rtmp_packet_t *packet);
extern void aw_rtmp_packet_free(aw_rtmp_packet_t *p);
extern char *aw_amf_encode_int24(char *output, char *outend, int nVal);
extern unsigned int aw_amf_decode_int24(const char *data);
extern unsigned int aw_amf_decode_int32(const char *data);
extern char *aw_amf_encode_int32(char *output, char *outend, int nVal);
extern int aw_rtmp_connect(aw_rtmp_t *r, aw_rtmp_packet_t *cp);
extern int aw_rtmp_connect_stream(aw_rtmp_t *r, int seekTime);


//***************************************************************************************//
//***************************************************************************************//
/*
 * Extracts playpath from RTMP URL. playpath is the file part of the
 * URL, i.e. the part that comes after rtmp://host:port/app/
 *
 * Returns the stream name in a format understood by FMS. The name is
 * the playpath part of the URL with formatting depending on the stream
 * type:
 *
 * mp4 streams: prepend "mp4:", remove extension
 * mp3 streams: prepend "mp3:", remove extension
 * flv streams: remove extension
 */
void aw_rtmp_parse_playpath(aw_rtmp_aval_t *in, aw_rtmp_aval_t *out)
{
	int addMP4 = 0;
	int addMP3 = 0;
	int subExt = 0;
	char *playpath = NULL;
	char *temp = NULL;
    char *q = NULL;
    char *ext = NULL;  /*后缀名*/
	char *ppstart = NULL;
	char *streamname = NULL;
    char *destptr = NULL;
    char *p = NULL;
	int pplen  = 0;
    unsigned int c;
    
    out->av_val = NULL;
	out->av_len = 0;
    pplen = in->av_len;
	playpath = in->av_val;
    ppstart = playpath;
    
    /*   ?XXX=XXX&XXX=XXX    */
	if((*ppstart == '?') &&(temp=strstr(ppstart, "slist=")) != 0) 
    {
		ppstart = temp+6;
		pplen = strlen(ppstart);
		temp = strchr(ppstart, '&');
		if(temp)
        {
			pplen = temp-ppstart;
		}
	}

	q = strchr(ppstart, '?');
	if(pplen >= 4) 
    {
		if(q)
		{
            ext = q-4;
		}
		else
		{
            ext = &ppstart[pplen-4];
		}
		if((strncmp(ext, ".f4v", 4) == 0) ||(strncmp(ext, ".mp4", 4) == 0)) 
        {
			addMP4 = 1;
			subExt = 1;
		    /* Only remove .flv from rtmp URL, not slist params */
		}
        else if ((ppstart == playpath) &&(strncmp(ext, ".flv", 4) == 0)) 
        {
			subExt = 1;
		} 
        else if(strncmp(ext, ".mp3", 4) == 0) 
        {
			addMP3 = 1;
			subExt = 1;
		}
	}

	streamname = (char *)malloc((pplen+4+1)*sizeof(char));
	if (!streamname)
	{
        return;
	}

	destptr = streamname;
	if(addMP4)
    {
		if(strncmp(ppstart, "mp4:", 4)) 
        {
			strcpy(destptr, "mp4:");
			destptr += 4;
		} 
        else 
        {
			subExt = 0;
		}
	} 
    else if(addMP3)
    {
		if(strncmp(ppstart, "mp3:", 4))
        {
			strcpy(destptr, "mp3:");
			destptr += 4;
		} 
        else 
        {
			subExt = 0;
		}
	}

 	for(p=(char*)ppstart; pplen >0;) 
    {
		/* skip extension */
		if(subExt && p == ext) 
        {
			p += 4;
			pplen -= 4;
			continue;
		}
        
		if(*p == '%') 
        {
			sscanf(p+1, "%02x", &c);
			*destptr++ = c;
			pplen -= 3;
			p += 3;
		} 
        else 
        {
			*destptr++ = *p++;
			pplen--;
		}
	}
    
	*destptr = '\0';
	out->av_val = streamname;
	out->av_len = destptr - streamname;
}

//***************************************************************************************//
//***************************************************************************************//
int aw_rtmp_parseUrl(char *url, int *protocol, aw_rtmp_aval_t *host, unsigned int *port,
	                    aw_rtmp_aval_t *playpath, aw_rtmp_aval_t *app)
{   
	char *p = NULL;
    char *end = NULL;
    char *col = NULL;
    char *ques = NULL;
    char *slash = NULL;
	char *slash2 = NULL;
    char *slash3 = NULL;
    int len = 0;
    int hostlen = 0;
	int applen = 0;
    int appnamelen = 0;
    unsigned int p2 = 0;;
    aw_rtmp_aval_t av;
    
	*protocol = RTMP_PROTOCOL_RTMP;
	*port = 0;
	 playpath->av_len = 0;
	 playpath->av_val = NULL;
	 app->av_len = 0;
	 app->av_val = NULL;

	/* Old School Parsing */

	/* look for usual :// pattern */
	p = strstr(url, "://");
	if(!p)
    {
		return FALSE;
	}
	len = (int)(p-url);

	if(len == 4 && strncasecmp(url, "rtmp", 4)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMP;
	}
	else if(len == 5 && strncasecmp(url, "rtmpt", 5)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMPT;
	}
	else if(len == 5 && strncasecmp(url, "rtmps", 5)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMPS;
	}
	else if(len == 5 && strncasecmp(url, "rtmpe", 5)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMPE;
	}
	else if(len == 5 && strncasecmp(url, "rtmfp", 5)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMFP;
	}
	else if(len == 6 && strncasecmp(url, "rtmpte", 6)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMPTE;
	}
	else if(len == 6 && strncasecmp(url, "rtmpts", 6)==0)
	{
        *protocol = RTMP_PROTOCOL_RTMPTS;
	}
    
	/* let's get the hostname */
	p += 3;

	/* check for sudden death */
	if(*p == 0) 
    {
		return FALSE;
	}

	end   = p + strlen(p);
	col   = strchr(p, ':');
	ques  = strchr(p, '?');
	slash = strchr(p, '/');

	if(slash)
	{
        hostlen = slash - p;
	}
	else
	{
        hostlen = end - p;
	}
	if(col && col-p < hostlen)
	{
        hostlen = col - p;
	}

	if(hostlen < 256)
    {
		host->av_val = p;
		host->av_len = hostlen;
	} 
	p += hostlen;

	/* get the port number if available */
	if(*p == ':')
    {
		p++;
		p2 = atoi(p);
		if(p2 > 65535)
        {
			//LOGV("Invalid port number!");
		} 
        else
        {
			*port = p2;
		}
	}

	if(!slash) 
    {
		return FALSE;
	}
	p = slash+1;
	/* parse application
	 *
	 * rtmp://host[:port]/app[/appinstance][/...]
	 * application = app[/appinstance]
	 */
	 
	slash2 = strchr(p, '/');
	if(slash2)
	{
        slash3 = strchr(slash2+1, '/');
	}

	applen = end-p; /* ondemand, pass all parameters as app */
	appnamelen = applen; /* ondemand length */

	if(ques && strstr(p, "slist="))
    { 
        /* whatever it is, the '?' and slist= means we need to use everything as app and parse plapath from slist= */
		appnamelen = ques-p;
	}
	else if(strncmp(p, "ondemand/", 9)==0) 
    {
        /* app = ondemand/foobar, only pass app=ondemand */
        applen = 8;
        appnamelen = 8;
    }
	else
    {
        /* app!=ondemand, so app is app[/appinstance] */
		if(slash3)
		{
            appnamelen = slash3-p;
		}
		else if(slash2)
		{
            appnamelen = slash2-p;
		}
		applen = appnamelen;
	}

	app->av_val = p;
	app->av_len = applen;
	p += appnamelen;
    
	if(*p == '/')
	{
        p++;
	}

	if(end-p) 
	{
		//av = {p, end-p};
		av.av_val = p;
        av.av_len = end-p;
		aw_rtmp_parse_playpath(&av, playpath);
	}
	return TRUE;
}

//***************************************************************************************//
//***************************************************************************************//


void aw_amf_add_prop(aw_amf_object_t *obj, aw_amfobject_property_t *prop)
{
    if(!(obj->o_num & 0x0f))
    {
        obj->o_props = realloc(obj->o_props, (obj->o_num + 16) * sizeof(aw_amfobject_property_t));
    }
    memcpy(&obj->o_props[obj->o_num++], prop, sizeof(aw_amfobject_property_t));
}

static int aw_parse_amf(aw_amf_object_t *obj, aw_rtmp_aval_t *av, int *depth)
{   
    int i;
    char *p = NULL;
    char *arg = NULL;
    aw_amfobject_property_t prop;//{{0,0}};

    memset(&prop, 0, sizeof(aw_amfobject_property_t));
    
    arg = av->av_val;

    if(arg[1] == ':')
    {
        p = (char *)arg+2;
        switch(arg[0])
	    {
	        case 'B':
	            prop.p_type = AMF_BOOLEAN;
	            prop.p_vu.p_number = atoi(p);
	            break;
	        case 'S':
	            prop.p_type = AMF_STRING;
	            prop.p_vu.p_aval.av_val = p;
	            prop.p_vu.p_aval.av_len = av->av_len - (p-arg);
	            break;
	        case 'N':
	            prop.p_type = AMF_NUMBER;
	            prop.p_vu.p_number = strtod(p, NULL);
	            break;
	        case 'Z':
	            prop.p_type = AMF_NULL;
	            break;
	        case 'O':
	            i = atoi(p);
	            if(i)
	            {
	                prop.p_type = AMF_OBJECT;
	            }
	            else
	            {
	                (*depth)--;
	                return 0;
	            }
	            break;
	        default:
	        {
                return -1;
	        }
	    }
    }
    else if (arg[2] == ':' && arg[0] == 'N')
    {
        p = strchr(arg+3, ':');
        if(!p || !*depth)
	    {
            return -1;
        }
        prop.p_name.av_val = (char *)arg+3;
        prop.p_name.av_len = p - (arg+3);
        p++;
        switch(arg[1])
	    {
	        case 'B':
	            prop.p_type = AMF_BOOLEAN;
	            prop.p_vu.p_number = atoi(p);
	            break;
	        case 'S':
	            prop.p_type = AMF_STRING;
	            prop.p_vu.p_aval.av_val = p;
	            prop.p_vu.p_aval.av_len = av->av_len - (p-arg);
	            break;
	        case 'N':
	            prop.p_type = AMF_NUMBER;
	            prop.p_vu.p_number = strtod(p, NULL);
	            break;
	        case 'O':
	            prop.p_type = AMF_OBJECT;
	            break;
	        default:
	            return -1;
	    }
    }
    else
    {
        return -1;
    }

    if(*depth)
    {
        aw_amf_object_t *o2;
        for (i=0; i<*depth; i++)
	    {
	        o2 = &obj->o_props[obj->o_num-1].p_vu.p_object;
	        obj = o2;
	    }
    }
    aw_amf_add_prop(obj, &prop);
    if(prop.p_type == AMF_OBJECT)
    {
        (*depth)++;
    }
    return 0;
}

int aw_rtmp_setOpt(aw_rtmp_t *r, aw_rtmp_aval_t *opt, aw_rtmp_aval_t *arg)
{
    int i;
    void *v;
    
    for(i=0; options[i].name.av_len; i++) 
    {
       if(opt->av_len != options[i].name.av_len) 
       {
            continue;
       }
       if(strcasecmp(opt->av_val, options[i].name.av_val)) 
       {
            continue;
       }

       v = (char *)r + options[i].off;
       switch(options[i].otype) 
       {
            case OPT_STR: 
            {
                aw_rtmp_aval_t *aptr = v;
                *aptr = *arg; 
            }
                break;
            case OPT_INT: 
            {
                long l = strtol(arg->av_val, NULL, 0);
                *(int *)v = l; 
            }
                break;
            case OPT_BOOL: 
            {
                int j, fl;
                fl = *(int *)v;
                for(j=0; truth[j].av_len; j++) 
                {
                    if(arg->av_len != truth[j].av_len) 
                    {
                        continue;
                    }
                    if(strcasecmp(arg->av_val, truth[j].av_val))
                    {
                        continue;
                    }
                    fl |= options[i].omisc;
                    break;
                }
                *(int *)v = fl;
          }
                break;
          case OPT_CONN:
                if(aw_parse_amf(&r->Link.extras, arg, &r->Link.edepth))
                {
                    return 0;
                }
                break;
       }
       break;
  }

    if(!options[i].name.av_len) 
    {
        return 0;
    }
    return 1;
}

//***************************************************************************************//
//***************************************************************************************//





int aw_rtmp_setupUrl2(CdxDataSourceT *datasource, aw_rtmp_t *r)
{
    aw_rtmp_aval_t opt;
    aw_rtmp_aval_t arg;
    char *p1 = NULL;
    char *p2 = NULL;
    char *ptr = NULL;
    int   ret = 0;
    int   len = 0;
    unsigned int port = 0;
    unsigned int c;

    if(strstr(datasource->uri, "live"))
    {
        r->isLiveStream = 1;
        r->Link.lFlags |= RTMP_LF_LIVE;
    }

    ptr = strchr(datasource->uri, ' ');
    if(ptr != NULL)
    {
        *ptr = '\0';
    }

    len = strlen(datasource->uri);
    ret = aw_rtmp_parseUrl(datasource->uri, &r->Link.protocol, &r->Link.hostname,
  	                       &port, &r->Link.playpath0, &r->Link.app);
    if(!ret)
    {
        return ret;
    }

    r->Link.port = port;
    r->Link.playpath = r->Link.playpath0;

    while(ptr)
    {
        *ptr++ = '\0';
        p1 = ptr;
        p2 = strchr(p1, '=');
        if(!p2)
        {
            break;
        }
        opt.av_val = p1;
        opt.av_len = p2 - p1;
        *p2++ = '\0';
        arg.av_val = p2;
        ptr = strchr(p2, ' ');
        if(ptr)
        {
            *ptr = '\0';
            arg.av_len = ptr - p2;
            /* skip repeated spaces */
            while(ptr[1] == ' ')
      	    {
                *ptr++ = '\0';
            }
        }
        else
        {
            arg.av_len = strlen(p2);
        }

         /* unescape */
        port = arg.av_len;
        for (p1=p2; port >0;)
        {
            if(*p1 == '\\')
            {
	            if(port < 3)
	            {
                    return -1;
	            }
	            sscanf(p1+1, "%02x", &c);
	            *p2++ = c;
	            port -= 3;
	            p1 += 3;
            }
            else
            {
	            *p2++ = *p1++;
	            port--;
            }
        }
        arg.av_len = p2 - arg.av_val;
        ret = aw_rtmp_setOpt(r, &opt, &arg);
        if (!ret)
        {
            return ret;
        }
    }

    if(!r->Link.tcUrl.av_len)
    {
        r->Link.tcUrl.av_val = datasource->uri;
        if(r->Link.app.av_len)
        {
            if(r->Link.app.av_val < datasource->uri + len)
    	    {
    	        /* if app is part of original url, just use it */
                r->Link.tcUrl.av_len = r->Link.app.av_len + (r->Link.app.av_val - datasource->uri);
    	    }
    	    else
    	    {
    	        len = r->Link.hostname.av_len + r->Link.app.av_len +
    		          sizeof("rtmpte://:65535/");
	            r->Link.tcUrl.av_val = malloc(len);
	            r->Link.tcUrl.av_len = snprintf(r->Link.tcUrl.av_val, len,
		                               "%s://%.*s:%d/%.*s",
		                                RTMPProtocolStringsLower[r->Link.protocol],
		                                r->Link.hostname.av_len, r->Link.hostname.av_val,
		                                r->Link.port,
		                                r->Link.app.av_len, r->Link.app.av_val);
	                                    r->Link.lFlags |= RTMP_LF_FTCU;
	        }
        }
        else
        {
	        r->Link.tcUrl.av_len = strlen(datasource->uri);
	    }
    }

    if(r->Link.port == 0)
    {
         if(r->Link.protocol & RTMP_FEATURE_SSL)
	     {
            r->Link.port = 443;
         }
         else if(r->Link.protocol & RTMP_FEATURE_HTTP)
	     {
            r->Link.port = 80;
         }
         else
	     {
            r->Link.port = 1935;
         }
    }
    return 1;
}

//***************************************************************************//
//***************************************************************************//
int aw_rtmp_get_next_media_packet(aw_rtmp_t *r, aw_rtmp_packet_t *packet)
{
    int bHasMediaPacket = 0;
    #define RTMPPacket_IsReady(a)	((a)->m_nBytesRead == (a)->m_nBodySize)
    while(!bHasMediaPacket && aw_rtmp_is_connected(r)
	    && aw_rtmp_read_packet(r, packet))
    {
        if(!RTMPPacket_IsReady(packet))
	    {
	        continue;
	    }
        bHasMediaPacket = aw_rtmp_client_packet(r, packet);

        if(!bHasMediaPacket)
	    {
	        aw_rtmp_packet_free(packet);
	    }
        else if(r->m_pausing == 3)
	    {
	        if(packet->m_nTimeStamp <= r->m_mediaStamp)
	        {
	            bHasMediaPacket = 0;
	            continue;
	        }
	        r->m_pausing = 0;
	    }
    }

    if(bHasMediaPacket)
    {
        r->m_bPlaying = TRUE;
    }
    else if(r->m_sb.sb_timedout && !r->m_pausing)
    {
        r->m_pauseStamp = r->m_channelTimestamp[r->m_mediaChannel];
    }
    return bHasMediaPacket;
}


//**********************************************************************************************//
//**********************************************************************************************//
int aw_rtmp_read_one_packet(aw_rtmp_t *r, char *buf, unsigned int buflen)
{   
    unsigned int size = 0;
    int len = 0;
    int useAnciBufFlag = 0;
    int rtnGetNextMediaPacket = 0;
    char *ptr = NULL;
    char* pend = NULL;
    unsigned int prevTagSize = 0;
    unsigned int nTimeStamp = 0;
    aw_rtmp_packet_t packet;
    char *packetBody = NULL;
    unsigned int nPacketLen = 0;
 
    #define RTMP_PACKET_TYPE_AUDIO              0x08
    #define RTMP_PACKET_TYPE_VIDEO              0x09
    #define RTMP_PACKET_TYPE_INFO               0x12
    #define RTMP_PACKET_TYPE_FLASH_VIDEO        0x16

new_read_packet:
    memset(&packet, 0, sizeof(aw_rtmp_packet_t));
    rtnGetNextMediaPacket = aw_rtmp_get_next_media_packet(r, &packet);

    while(rtnGetNextMediaPacket > 0)
    {   
        if(r->exitFlag == 1)
        {
            return -1;
        }
        packetBody = packet.m_body;
        nPacketLen = packet.m_nBodySize;  //size of message length
        /****Return RTMP_READ_COMPLETE if this was completed nicely with invoke message Play.Stop or Play.Complete*/
        if(rtnGetNextMediaPacket == 2)
        {
            return -1;
        }
        r->m_read.dataType |= (((packet.m_packetType == RTMP_PACKET_TYPE_AUDIO) << 2) |
			     (packet.m_packetType == RTMP_PACKET_TYPE_VIDEO));
        
        if(packet.m_packetType == RTMP_PACKET_TYPE_VIDEO && nPacketLen <= 5)   /*video chunk do not have data*/
        {   

            goto new_read_packet;
        }
        if(packet.m_packetType == RTMP_PACKET_TYPE_AUDIO && nPacketLen <= 1)  /*audio chunk do not have data*/
        {   

            goto new_read_packet;
        }
        /* calculate packet size and allocate slop buffer if necessary */    
        size = nPacketLen +
	        ((packet.m_packetType == RTMP_PACKET_TYPE_AUDIO
            || packet.m_packetType == RTMP_PACKET_TYPE_VIDEO
	        || packet.m_packetType == RTMP_PACKET_TYPE_INFO) ? 11 : 0) +   /*tag header*/
	        (packet.m_packetType != RTMP_PACKET_TYPE_FLASH_VIDEO ? 4 : 0); /* privious tag size */
        
        if((size+4) <= buflen)
        {
            ptr = buf;
        }
        else
        {
            if(nPacketLen >= 128*1024)
            {
            }
            ptr = r->flvDataBuffer;
            useAnciBufFlag = 1;
        }
        pend = ptr + size + 4;

        if(packet.m_packetType == RTMP_PACKET_TYPE_AUDIO
            || packet.m_packetType == RTMP_PACKET_TYPE_VIDEO
	        || packet.m_packetType == RTMP_PACKET_TYPE_INFO)
	    {
            nTimeStamp = r->m_read.nResumeTS + packet.m_nTimeStamp;
            r->timeStamp = nTimeStamp;
	        prevTagSize = 11 + nPacketLen; /*rtmp stream do not contain flv tag header */
	        *ptr = packet.m_packetType;  /* 0x08 or 0x09 or 0x12 */
	        ptr++;
	        ptr = aw_amf_encode_int24(ptr, pend, nPacketLen); /* tag size (3 bytes)*/

	        ptr = aw_amf_encode_int24(ptr, pend, nTimeStamp); /* timestamp(3 bytes) */
	        *ptr = (char)((nTimeStamp & 0xFF000000) >> 24);   /*ex time stamp*/
	        ptr++;
	        /* stream id */
	        ptr = aw_amf_encode_int24(ptr, pend, 0);
	    }
        memcpy(ptr, packetBody, nPacketLen); /* read tag data form packetBody */
        len = nPacketLen;

		/* find the keyframes, if  metadata has "keyframes", the stream is seekable */

/*		
		if(packet.m_packetType == RTMP_PACKET_TYPE_INFO)
		{		
			char* a = (char*)malloc(len+1);
			memcpy(a, ptr, len);
			a[len] = '\0';
			//printf("*****************\n%c\n******************\n",a);
			char* key = strstr(a, "p");
			if(key)
			{
				r->isLiveStream = 1;
				printf("The stream has keyframes.\n");
			}
			else
			{			
				printf("connot seek.\n");
			}

			char* datasize = strstr(a, "width");
			if(datasize)printf("******width******\n");
		}
*/
        /* correct tagSize and obtain timestamp if we have an FLV stream */
        if(packet.m_packetType == RTMP_PACKET_TYPE_FLASH_VIDEO)
	    {
            unsigned int pos = 0;
	        int delta;

	        /* grab first timestamp and see if it needs fixing */
	        nTimeStamp = aw_amf_decode_int24(packetBody + 4);
	        nTimeStamp |= (packetBody[7] << 24);
	        delta = packet.m_nTimeStamp - nTimeStamp + r->m_read.nResumeTS;

	        while(pos + 11 < nPacketLen)
	        {   
                if(r->exitFlag==1)
                {
                    return -1;
                }
                /* size without header (11) and without prevTagSize (4) */
	            unsigned int dataSize = aw_amf_decode_int24(packetBody + pos + 1);
	            nTimeStamp = aw_amf_decode_int24(packetBody + pos + 4);
	            nTimeStamp |= (packetBody[pos + 7] << 24);

	            if(delta)
		        {
		            nTimeStamp += delta;
		            aw_amf_encode_int24(ptr+pos+4, pend, nTimeStamp);
                    ptr[pos+7] = nTimeStamp>>24;
		        }

	            /* set data type */
	            r->m_read.dataType |= (((*(packetBody + pos) == 0x08) << 2) |
				     (*(packetBody + pos) == 0x09));

	            if(pos + 11 + dataSize + 4 > nPacketLen)
		        {
		            if(pos + 11 + dataSize > nPacketLen)
		            {   

		                return -1;
		            }
		            /* we have to append a last tagSize! */
		            prevTagSize = dataSize + 11;
		            aw_amf_encode_int32(ptr + pos + 11 + dataSize, pend,prevTagSize);
		            size += 4;
		            len += 4;
		        }
	            else
		        {
		            prevTagSize = aw_amf_decode_int32(packetBody + pos + 11 + dataSize);
		            if(prevTagSize != (dataSize + 11))
		            {
                        prevTagSize = dataSize + 11;
		                aw_amf_encode_int32(ptr + pos + 11 + dataSize, pend,prevTagSize);
		            }
		        }
	            pos += prevTagSize + 4;	/*(11+dataSize+4); */
	        }
	    }
        ptr += len;
    
        if(packet.m_packetType != RTMP_PACKET_TYPE_FLASH_VIDEO)
	    {
            /* FLV tag packets contain their own prevTagSize */
	        aw_amf_encode_int32(ptr, pend, prevTagSize);
	    }

        /* In non-live this nTimeStamp can contain an absolute TS.
        * Update ext timestamp with this absolute offset in non-live mode
        * otherwise report the relative one
        */

        r->m_read.timestamp = (r->Link.lFlags & RTMP_LF_LIVE) ? packet.m_nTimeStamp : nTimeStamp;
        break;
    }

    if(rtnGetNextMediaPacket)
    {
        aw_rtmp_packet_free(&packet);
    }
    if(useAnciBufFlag == 1)
    {
        #if 0
        memcpy(buf, r->flvDataBuffer, buflen);
        if(buf+buflen > r->bufEndPtr)
        {
            memcpy(r->buffer, r->flvDataBuffer+buflen, size-buflen);
        }
        #else
        if(size > buflen)
        {
            memcpy(buf, r->flvDataBuffer, buflen);
            memcpy(r->buffer, r->flvDataBuffer+buflen, size-buflen);
        }
        else
        {
            memcpy(buf, r->flvDataBuffer, size);
        }
        #endif
        useAnciBufFlag = 0;
    }
    return size;
}

//*********************************************************************************************//
//*********************************************************************************************//
int aw_rtmp_stream_read(aw_rtmp_t* r, char* buf, int size)
{
    int readLen = 0;
    int nRead = 0;
    char *orgBuf = NULL;
    


    char flvHeader[] = { 'F', 'L', 'V', 0x01,
                          0x00,             /* 0x04 == audio, 0x01 == video */
                          0x00, 0x00, 0x00, 0x09,
                          0x00, 0x00, 0x00, 0x00
                        };
    
    if((r->m_read.flags & RTMP_READ_HEADER) == 0)
    {   
        orgBuf = buf;
        memcpy(buf, flvHeader, sizeof(flvHeader));
        buf += sizeof(flvHeader);
        readLen += sizeof(flvHeader);
        size -= sizeof(flvHeader);

        while(r->m_read.timestamp == 0)
        {
            nRead = aw_rtmp_read_one_packet(r, buf, size);  /*读取网络流数据到rtmp客户端buffer*/
            if(nRead <= 0)
            {   

                return -1;
            }
            buf += nRead;
            size -= nRead;
            readLen += nRead;
            if(r->m_read.dataType == 5)
	        {
                break;
	        }
            if(r->exitFlag == 1)
            {
                return -1;
            }
        }
        orgBuf[4] = r->m_read.dataType;   //change the fifth byte of  flv header 
        r->m_read.flags |= RTMP_READ_HEADER;
    }
    else
    {
        nRead = aw_rtmp_read_one_packet(r, buf, size);
        if(nRead <= 0)
        {
            return -1;
        }
        readLen += nRead;
    }
    return readLen;
}



void* aw_read_rtmp_stream2(void* p_arg)
{
    aw_rtmp_t*   rtmp = NULL;
    int remainSize = 0;
    int redDataLen = 0;
    rtmp = (aw_rtmp_t*)p_arg;
    int probeFlag = 1;

    while(1)
    {
        if(rtmp->exitFlag == 1)  /* player exit */
        {
            break;
        }
        else if((rtmp->stream_buf_size-rtmp->validDataSize)<= 3*1024*1024) // protect buffer size 3M
        {
            usleep(4*1000);
            continue;
        }
        if(rtmp->bufWritePtr < rtmp->bufReleasePtr)
        {
            remainSize = rtmp->bufReleasePtr - rtmp->bufWritePtr;
        }
        else
        {
            remainSize = rtmp->bufEndPtr - rtmp->bufWritePtr+1;
        }

        redDataLen = aw_rtmp_stream_read(rtmp, rtmp->bufWritePtr, remainSize);
        if(redDataLen <= 0)
        {
            break;
        }

        rtmp->bufWritePtr += redDataLen;
        if(rtmp->bufWritePtr > rtmp->bufEndPtr)
        {
            rtmp->bufWritePtr -= rtmp->stream_buf_size;
        } 

        pthread_mutex_lock(&rtmp->bufferMutex);
        rtmp->validDataSize += redDataLen;  /**/
        rtmp->buf_pos += redDataLen;
        pthread_mutex_unlock(&rtmp->bufferMutex);

		// for probe
        if(probeFlag && (rtmp->validDataSize >= RTMP_PROBE_DATA_LEN))
		{			 
			 probeFlag = 0;
			 memcpy(rtmp->probeData.buf, rtmp->buffer, RTMP_PROBE_DATA_LEN);
			 pthread_mutex_lock(&rtmp->lock);
    		 rtmp->iostate = CDX_IO_STATE_OK;
    		 pthread_cond_signal(&rtmp->cond);
			 pthread_mutex_unlock(&rtmp->lock);			 
		}
    }

	pthread_mutex_lock(&rtmp->lock);
	rtmp->iostate = CDX_IO_STATE_ERROR;
	pthread_mutex_unlock(&rtmp->lock);
    rtmp->eof = 1;
    return NULL;
}

static int RtmpGetCacheState(struct StreamCacheStateS *cacheState, CdxStreamT *stream)//0424
{
    aw_rtmp_t* rtmp = NULL;
    if(!stream)
    {
        return -1;
    }
    
    rtmp = (aw_rtmp_t*)stream;

    cdx_int64 totSize = rtmp->fileSize;
    cdx_int64 bufPos = rtmp->buf_pos;
    //cdx_int64 readPos = rtmp->dmx_read_pos;
    double percentage = 0;
	cdx_int32 kbps = 0;//4000;//512KB/s
	
    memset(cacheState, 0, sizeof(struct StreamCacheStateS));

	if(totSize > 0) 
	{
		percentage = 100.0 * (double)(bufPos)/totSize;
	} 
	else
	{
		percentage = 0.0;
	}

	if (percentage > 100) 
	{
		percentage = 100;
	}

    cacheState->nBandwidthKbps = kbps;
    cacheState->nCacheCapacity = rtmp->stream_buf_size;
    cacheState->nCacheSize = rtmp->validDataSize;

	return 0;
}

void RtmpClose(aw_rtmp_t* rtmp)
{
    if(rtmp != NULL)
    {
        rtmp->exitFlag = 1;
        while(rtmp->eof == 0)
        {
            usleep(1000);
        }
        aw_rtmp_close(rtmp);

        if(rtmp->buffer != NULL)
        {
            free(rtmp->buffer);
            rtmp->buffer = NULL;
        }
        if(rtmp->probeData.buf != NULL)
        {
        	free(rtmp->probeData.buf);
        	rtmp->probeData.buf = NULL;
        }
        if(rtmp->flvDataBuffer != NULL)
        {
            free(rtmp->flvDataBuffer);
            rtmp->flvDataBuffer  = NULL;
        }
        pthread_mutex_destroy(&rtmp->bufferMutex);
        pthread_mutex_destroy(&rtmp->lock);
    	pthread_cond_destroy(&rtmp->cond);
        free(rtmp);
    }
}

//*********************************************************************************************//
//*********************************************************************************************//
CdxStreamProbeDataT* __RtmpGetProbeData(CdxStreamT * stream)
{
	aw_rtmp_t* rtmp = NULL;

    rtmp = (aw_rtmp_t*)stream; 
        
	return &(rtmp->probeData);
}


int __RtmpRead(CdxStreamT* stream, void* buffer, unsigned int size)
{
	int sendSize = 0;
    int remainSize = 0;

    aw_rtmp_t* rtmp = NULL;

    rtmp = (aw_rtmp_t*)stream;

    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_READING);
    sendSize = size;
    while(rtmp->validDataSize < (int)size)
    {
        if(rtmp->exitFlag == 1)
        {
            CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
            return -1;
        }
        if(rtmp->eof == 1)
        {
            sendSize = (sendSize>=rtmp->validDataSize)? rtmp->validDataSize: sendSize;
            break;
        }
        if(rtmp->forceStop)
        {
            CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
            return -1;
        }
        usleep(4*1000);
    }

    remainSize = rtmp->bufEndPtr-rtmp->bufReadPtr+1;
    if(remainSize >= sendSize)
    {
        memcpy((char*)buffer, rtmp->bufReadPtr, sendSize);
    }
    else
    {
        memcpy((char*)buffer, rtmp->bufReadPtr, remainSize);
        memcpy((char*)buffer+remainSize, rtmp->buffer, sendSize-remainSize);
    }
    rtmp->bufReadPtr += sendSize;
    if(rtmp->bufReadPtr > rtmp->bufEndPtr)
    {
        rtmp->bufReadPtr -= rtmp->stream_buf_size;
    }

	rtmp->bufReleasePtr = rtmp->bufReadPtr;
    pthread_mutex_lock(&rtmp->bufferMutex);
    rtmp->dmx_read_pos += sendSize;
    rtmp->validDataSize -= sendSize;

    pthread_mutex_unlock(&rtmp->bufferMutex);
    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
    return sendSize;
}

int __RtmpClose(CdxStreamT* stream)
{
    aw_rtmp_t* rtmp = NULL;

    rtmp = (aw_rtmp_t*)stream;

    rtmp->exitFlag = 1;
    RtmpClose(rtmp);
    rtmp = NULL;
    return 0;
}

cdx_uint32 __RtmpAttribute(CdxStreamT * stream)
{
	CDX_UNUSE(stream);
	return CDX_STREAM_FLAG_NET;// | CDX_STREAM_FLAG_SEEK;
}

int __RtmpGetIoState(CdxStreamT * stream)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	return rtmp->iostate;
}


cdx_int32 __RtmpForceStop(CdxStreamT *stream)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	rtmp->exitFlag = 1;
    int ref;

    if(CdxAtomicRead(&rtmp->mState) == RTMP_STREAM_FORCESTOPPED)
    {
        return 0;
    }
    while((ref = CdxAtomicRead(&rtmp->mState)) != RTMP_STREAM_IDLE)
    {
        usleep(10000);
    }
    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_FORCESTOPPED);

	return 0;
}

cdx_int32 __RtmpClrForceStop(CdxStreamT *stream)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;

	rtmp->exitFlag = 0;
    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
	return 0;
}


int __RtmpControl(CdxStreamT* stream, int cmd, void* param)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*) stream;
    switch(cmd)
    {
        case STREAM_CMD_GET_CACHESTATE:
    		return RtmpGetCacheState((struct StreamCacheStateS *)param, stream);

        case STREAM_CMD_SET_FORCESTOP:
            return __RtmpForceStop(stream);

        case STREAM_CMD_CLR_FORCESTOP:
            return __RtmpClrForceStop(stream);
        default:
            break;
    }

    return CDX_SUCCESS;
} 


int __RtmpSeek(CdxStreamT* stream, cdx_int64 offset, int whence)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*) stream;

	int64_t seekPos = 0;
	CdxAtomicSet(&rtmp->mState, RTMP_STREAM_SEEKING);
//CDX_LOGD("rtmp seek (offset = %lld, whence = %d)", offset, whence);
//CDX_LOGD("demux read pos = %lld", rtmp->dmx_read_pos);

	switch(whence)
	{
    	case SEEK_SET:
    	{ 	    
    	    seekPos = offset;
    	    break;
        }
    	case SEEK_CUR:
    	    seekPos = rtmp->dmx_read_pos + offset;
    	    break;

    	case SEEK_END:
    	    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
    	    return -1;
	}

    if(seekPos < 0)
    {
        CDX_LOGW("we can not seek to this position");
        CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
        return -1;
    }

    // do not cache so much data, so wait
    while(seekPos > rtmp->buf_pos)
    {
        if(rtmp->exitFlag)
        {
            CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
            return -1;
        }
        usleep(1000);
    }

    if(seekPos > (rtmp->dmx_read_pos-1024*1024))
    {
        pthread_mutex_lock(&rtmp->bufferMutex);
        rtmp->bufReadPtr += (seekPos - rtmp->dmx_read_pos);
        rtmp->validDataSize -= (seekPos - rtmp->dmx_read_pos);
        if (rtmp->bufReadPtr > rtmp->bufEndPtr)
		{
			rtmp->bufReadPtr -= rtmp->stream_buf_size;
		}
		else if(rtmp->bufReadPtr < rtmp->buffer)
		{
		    rtmp->bufReadPtr += rtmp->stream_buf_size;
		}
        rtmp->dmx_read_pos = seekPos;
        pthread_mutex_unlock(&rtmp->bufferMutex);
    }
    else
    {
        CDX_LOGW("maybe the buffer is override by the new data, so we can not support to seek to this position");
        pthread_mutex_lock(&rtmp->bufferMutex);
        rtmp->bufReadPtr += (seekPos - rtmp->dmx_read_pos);
        rtmp->validDataSize -= (seekPos - rtmp->dmx_read_pos);
        if (rtmp->bufReadPtr > rtmp->bufEndPtr)
		{
			rtmp->bufReadPtr -= rtmp->stream_buf_size;
		}
		else if(rtmp->bufReadPtr < rtmp->buffer)
		{
		    rtmp->bufReadPtr += rtmp->stream_buf_size;
		}
        rtmp->dmx_read_pos = seekPos;
        pthread_mutex_unlock(&rtmp->bufferMutex);
    }

    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
	return 0;
}

int __RtmpSeekToTime(CdxStreamT* stream, cdx_int64 timeUs)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	int64_t nTimeStamp = rtmp->timeStamp;
	int64_t offset;
	if(rtmp->isLiveStream)
	{
		return -1;
	}
	else
	{
		offset = timeUs - nTimeStamp;
		if(!aw_send_seek(rtmp, offset))
		{
			CDX_LOGE("seek error.\n");
			return -1;
		}
		else
		{
			return timeUs;
		}
	}
	return -1;
}

cdx_int64 __RtmpTell(CdxStreamT* stream)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	pthread_mutex_lock(&rtmp->bufferMutex);
	int64_t ret = rtmp->dmx_read_pos;
	pthread_mutex_unlock(&rtmp->bufferMutex);
	return ret;
}

int __RtmpEos(CdxStreamT* stream)
{
	aw_rtmp_t* rtmp = (aw_rtmp_t*)stream;
	return rtmp->eof;
}

cdx_int64 __RtmpSize(CdxStreamT* stream)
{
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	if(rtmp->isLiveStream)
	    return -1;
	else    
	    return rtmp->fileSize;
}

static int __RtmpConect(CdxStreamT* stream)
{
	pthread_t	downloadTid;
	aw_rtmp_t* rtmp = NULL;
	rtmp = (aw_rtmp_t*)stream;
	int ret;

    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_CONNECTING);

	if(!aw_rtmp_connect(rtmp, NULL) )
    {
        CDX_LOGW("rtmp connect error!");
        rtmp->eof = 1;
        CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);

		
        pthread_mutex_lock(&rtmp->lock);
		rtmp->iostate = CDX_IO_STATE_ERROR;
		pthread_mutex_unlock(&rtmp->lock);
    	return -1;
    }

    if(!aw_rtmp_connect_stream(rtmp, 0))
    {
        CDX_LOGW("rtmp connect stream error!");
        rtmp->eof = 1;
        CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
        
        pthread_mutex_lock(&rtmp->lock);
		rtmp->iostate = CDX_IO_STATE_ERROR;
		pthread_mutex_unlock(&rtmp->lock);
        return -1;
    }
    
	ret = pthread_create(&downloadTid, NULL, aw_read_rtmp_stream2, (void*)rtmp); /*create thread*/	
	if(ret != 0)
	{
		downloadTid = (pthread_t)0;
	}

	pthread_mutex_lock(&rtmp->lock);
	while(rtmp->iostate != CDX_IO_STATE_OK 
		&& rtmp->iostate != CDX_IO_STATE_EOS 
		&& rtmp->iostate != CDX_IO_STATE_ERROR)
	{
		pthread_cond_wait(&rtmp->cond, &rtmp->lock);		
	}
	pthread_mutex_unlock(&rtmp->lock);

	return 0;
}


static struct CdxStreamOpsS RtmpStreamOps =
{
	.connect        = __RtmpConect, 
    .getProbeData   = __RtmpGetProbeData, 
    .read           = __RtmpRead,
    .close          = __RtmpClose,
    .getIOState     = __RtmpGetIoState,
    .attribute      = __RtmpAttribute,
    .control        = __RtmpControl,
    .seek           = __RtmpSeek,
    .seekToTime     = __RtmpSeekToTime,
    .eos            = __RtmpEos,
    .tell           = __RtmpTell,
    .size           = __RtmpSize,
};



//***************************************************************************************//
//***************************************************************************************//



void aw_rtmp_init(aw_rtmp_t *rtmp)
{
	CDX_CHECK(rtmp);
    memset(rtmp, 0, sizeof(aw_rtmp_t)); 
    
    rtmp->probeData.buf = malloc (RTMP_PROBE_DATA_LEN);
	rtmp->probeData.len = RTMP_PROBE_DATA_LEN;
	memset(rtmp->probeData.buf, 0, RTMP_PROBE_DATA_LEN);

	rtmp->streaminfo.ops  = &RtmpStreamOps;
	 
	rtmp->eof = 0;
	rtmp->isLiveStream = 1;
	rtmp->iostate = CDX_IO_STATE_INVALID;
    rtmp->m_sb.sb_socket = -1;
    rtmp->m_inChunkSize = RTMP_DEFAULT_CHUNKSIZE;
    rtmp->m_outChunkSize = RTMP_DEFAULT_CHUNKSIZE;
    rtmp->m_nBufferMS = 30000;
    rtmp->m_nClientBW = 2500000;
    rtmp->m_nClientBW2 = 2;
    rtmp->m_nServerBW = 2500000;
    rtmp->m_fAudioCodecs = 3191.0;
    rtmp->m_fVideoCodecs = 252.0;
    rtmp->Link.timeout = 30;
    rtmp->Link.swfAge = 30;

    aw_set_rtmp_parameter(rtmp);
}

CdxStreamT* aw_rtmp_streaming_start2(CdxDataSourceT* datasource, int flags)
{
    int ret = 0;

    #define URL_RDONLY 0
    #define URL_WRONLY 1
    #define URL_RDWR   2

    aw_rtmp_t *rtmp = (aw_rtmp_t*)malloc(sizeof(aw_rtmp_t));
    if(!rtmp)
    {
    	return NULL;
    }
    aw_rtmp_init(rtmp);
    if(!aw_rtmp_setupUrl2(datasource, rtmp))
    {
        goto fail;
    }
    if(flags & URL_WRONLY)
    {
        rtmp->Link.protocol |= RTMP_FEATURE_WRITE;
    }    

    rtmp->stream_buf_size = MAX_STREAM_BUF_SIZE;
    CdxAtomicSet(&rtmp->mState, RTMP_STREAM_IDLE);
    rtmp->buffer = (char*)malloc(sizeof(char)*rtmp->stream_buf_size);  //*  SetBufferSize
    if(rtmp->buffer == NULL)
    {
        goto fail;
    }

    rtmp->flvDataBuffer = (char*)malloc(sizeof(char)*128*1024);
	if(rtmp->flvDataBuffer  == NULL)
	{
	    goto fail;
	}

	rtmp->bufEndPtr      = rtmp->buffer + rtmp->stream_buf_size - 1;
	rtmp->bufWritePtr    = rtmp->buffer;
	rtmp->bufReadPtr     = rtmp->buffer;
	rtmp->bufReleasePtr  = rtmp->buffer;
	rtmp->validDataSize  = 0;
	pthread_mutex_init(&rtmp->bufferMutex, NULL);
	rtmp->dmx_read_pos = 0;

	ret = pthread_mutex_init(&rtmp->lock, NULL);
	CDX_CHECK(!ret);
	ret = pthread_cond_init(&rtmp->cond, NULL);

    return &(rtmp->streaminfo);
    
fail:
	if(rtmp)
	{
	    CDX_LOGW("failed in rtmp stream start");	
		rtmp->eof = 1;
        rtmp->iostate = CDX_IO_STATE_ERROR;
        RtmpClose(rtmp);
   	 	free(rtmp);
    	rtmp = NULL;
    }
    return NULL;
}

static CdxStreamT* __RtmpStreamOpen(CdxDataSourceT* dataSource)
{
	CDX_CHECK(dataSource);
	if(dataSource->uri == NULL)
	{
		return NULL;
	}

	CdxStreamT* streaminfo = aw_rtmp_streaming_start2(dataSource, URL_RDONLY);

	return streaminfo;
}

CdxStreamCreatorT rtmpStreamCtor =
{
    .create = __RtmpStreamOpen
};


