#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <CdxUrl.h>
#include <ctype.h>
#include <CdxLog.h>

#ifdef SIZE_MAX
#undef SIZE_MAX
#endif
#define SIZE_MAX ((size_t)-1)
void CdxUrlPrintf(CdxUrlT* url)
{
    CDX_LOGD("**********print the url container.");
    CDX_LOGD("**********ur->url=(%s)", url->url);
    CDX_LOGD("**********ur->protocol=%s", url->protocol);
    CDX_LOGD("**********ur->hostname=%s", url->hostname);
    CDX_LOGD("**********ur->file=%s", url->file);
    CDX_LOGD("**********ur->prot=%u", url->port);
    CDX_LOGD("**********ur->username=%s", url->username);
    CDX_LOGD("**********ur->password=%s", url->password);
}
CdxUrlT* CdxUrlNew(char* url)
{
    int pos1 = 0;
    int pos2 = 0;
    int v6addr = 0;
    int len = 0;
    int len2 = 0;
    char *ptr1=NULL;
    char *ptr2=NULL;
    char *ptr3=NULL;
    char *ptr4=NULL;
    int jumpSize = 3;
    CdxUrlT* curUrl = NULL;
    char *escfilename = NULL;
    
    if(url == NULL)
    {
        CDX_LOGE("url null");
        return NULL;
    }
    if(strlen(url) >(SIZE_MAX/3 - 1))
    {
        CDX_LOGE("the length of the url is too longer.");
        goto err_out;
    }
    escfilename = malloc(strlen(url)*3+1);
    if(escfilename == NULL)
    {
        CDX_LOGE("malloc memory for escfilename failed.");
        goto err_out;
    }

    // Create the URL container
    curUrl = malloc(sizeof(CdxUrlT));
    if(curUrl == NULL)
    {
        CDX_LOGE("malloc memory for curUrl failed.");
        goto err_out;
    }

    //Initialisation of the URL container members
    memset(curUrl, 0, sizeof(CdxUrlT));
    CdxUrlEscapeString(escfilename,url);
    // Copy the url in the URL container
    curUrl->url = strdup(escfilename);
    if(curUrl->url == NULL)
    {   
        CDX_LOGE("curUrl->url is NULL.");
        goto err_out;
    }
    
    // extract the protocol
    ptr1 = strstr(escfilename, "://");
    
    if(ptr1==NULL)
    {
        // Check for a special case: "sip:" (without "//"):
        if(strstr(escfilename, "sip:") == escfilename)
        {
            ptr1 = (char *)&url[3]; // points to ':'
            jumpSize = 1;
        }
        else
        {
            CDX_LOGE("the url (%s) is not a URL.", escfilename);
            goto err_out;
        }
    }

    pos1 = ptr1-escfilename;
    curUrl->protocol = malloc(pos1+1);
    if(curUrl->protocol == NULL)
    {
        CDX_LOGE("curUrl->protocol is NULL.");
        goto err_out;
    }

    strncpy(curUrl->protocol, escfilename, pos1);
    curUrl->protocol[pos1] = '\0';
    
    // jump the "://"
    ptr1 += jumpSize;
    pos1 += jumpSize;

    //check if a username:password is given
    ptr2 = strstr(ptr1, "@");
    ptr3 = strstr(ptr1, "/");
    if(ptr3!=NULL && ptr3<ptr2)
    {
        // it isn't really a username but rather a part of the path
        ptr2 = NULL;
    }

    if(ptr2 != NULL)
    {
        // We got something, at least a username...
        len = ptr2-ptr1;
        curUrl->username = malloc(len+1);
        if(curUrl->username == NULL )
        {
            CDX_LOGE("curUrl->username  is faile.");
            goto err_out;
        }
        strncpy(curUrl->username, ptr1, len);
        curUrl->username[len] = '\0';

        ptr3 = strstr(ptr1, ":");
        if(ptr3!=NULL && ptr3<ptr2)
        {
            // We also have a password
            len2 = ptr2-ptr3-1;
            curUrl->username[ptr3-ptr1]='\0';
            curUrl->password = malloc(len2+1);
            if(curUrl->password == NULL)
            {
                CDX_LOGE("curUrl->password is failed.");
                goto err_out;
            }
            strncpy(curUrl->password, ptr3+1, len2);
            curUrl->password[len2]='\0';
        }
        ptr1 = ptr2+1;
        pos1 = ptr1-escfilename;
    }

    // before looking for a port number check if we have an IPv6 type numeric address
    // in IPv6 URL the numeric address should be inside square braces.
    ptr2 = strstr(ptr1, "[");
    ptr3 = strstr(ptr1, "]");
    ptr4 = strstr(ptr1, "/");
    if(ptr2!=NULL && ptr3!=NULL && ptr2 < ptr3 && (!ptr4 || ptr4 > ptr3))
    {
        // we have an IPv6 numeric address
        ptr1++;
        pos1++;
        ptr2 = ptr3;
        v6addr = 1;
    }
    else
    {
        ptr2 = ptr1;
    }

    // look if the port is given
    ptr2 = strstr(ptr2, ":");
    // If the : is after the first / it isn't the port
    ptr3 = strstr(ptr1, "/");
    if(ptr3 && ptr3 - ptr2 < 0)
    {
        ptr2 = NULL;
    }
    if(ptr2==NULL)
    {
        // No port is given, Look if a path is given
        if(ptr3==NULL)
        {
            // No path/filename, So we have an URL like http://www.hostname.com
            pos2 = strlen(escfilename);
        }
        else
        {
            // We have an URL like http://www.hostname.com/file.txt
            pos2 = ptr3-escfilename;
        }
    }
    else
    {
        // We have an URL beginning like http://www.hostname.com:1212, Get the port number
        curUrl->port = atoi(ptr2+1);
        pos2 = ptr2-escfilename;
    }

    if(v6addr)
    {
        pos2--; 
    }
        
    // copy the hostname in the URL container
    curUrl->hostname = malloc(pos2-pos1+1);
    if(curUrl->hostname==NULL)
    {
        CDX_LOGE("curUrl->hostname is NULL.");
        goto err_out;
    }

    strncpy(curUrl->hostname, ptr1, pos2-pos1);
    curUrl->hostname[pos2-pos1] = '\0';

    // Look if a path is given
    ptr2 = strstr(ptr1, "/");
    
    if(ptr2 != NULL)
    {
        //A path/filename is given, check if it's not a trailing '/'
        if(strlen(ptr2) > 1)
        {
            // copy the path/filename in the URL container
            curUrl->file = strdup(ptr2);
            if(curUrl->file==NULL)
            {
                CDX_LOGE("curURL is NULL.");
                goto err_out;
            }
        }
    }

    // Check if a filename was given or set, else set it with '/'
    if(curUrl->file==NULL)
    {
        curUrl->file = malloc(2);
        if(curUrl->file==NULL)
        {
            CDX_LOGE("curURL file is NULL.");
            goto err_out;
        }
        strcpy(curUrl->file, "/");
    }
    free(escfilename);
    escfilename = NULL;
    return curUrl;

err_out:
    if(escfilename)
    {
        free(escfilename);
        escfilename = NULL;
    }
    if(curUrl)
    {
        CdxUrlFree(curUrl);
    }
    return NULL;
}
//*******************************************************************************************//
/* Replace specific characters in the URL string by an escape sequence */
/* works like strcpy(), but without return argument */
//*******************************************************************************************//
void CdxUrlEscapeString(char *outbuf, const char *inbuf) 
{   
    unsigned char c;
    int i = 0;
    int j = 0;
    int len = 0;
    char *tmp = NULL;
    char *in = NULL;
    char *unesc = NULL;
    
    len = strlen(inbuf);
    
    // Look if we have an ip6 address, if so skip it there is no need to escape anything in there.
    tmp = strstr(inbuf,"://[");
    if(tmp) 
       {
            tmp = strchr(tmp+4,']');
            if(tmp && (tmp[1] == '/' || tmp[1] == ':' || tmp[1] == '\0')) 
            {
                i = tmp+1-inbuf;
                strncpy(outbuf,inbuf,i);
                outbuf += i;
                tmp = NULL;
            }
        }

    tmp = NULL;
    while(i < len) 
    {
        // look for the next char that must be kept
        for(j=i;j<len;j++)
        {
            c = inbuf[j];
            if(c=='-' || c=='_' || c=='.' || c=='!' || c=='~' ||    /* mark characters */
               c=='*' || c=='\'' || c=='(' || c==')' ||              /* do not touch escape character */
               c==';' || c=='/' || c=='?' || c==':' || c=='@' ||     /* reserved characters */
               c=='&' || c=='=' || c=='+' || c=='$' || c==',' ||     /* see RFC 2396 */
               c=='[' || c==']')

                break;
        }
        // we are on a reserved char, write it out
        if(j == i) 
        {
            *outbuf++ = c;
            i++;
            continue;
        }
        // we found one, take that part of the string
        if(j < len) 
        {
            if(!tmp)
            {
                tmp = malloc(len+1);
            }
            strncpy(tmp,inbuf+i,j-i);
            tmp[j-i] = '\0';
            in = tmp;
        } 
        else // take the rest of the string
        {   
            in = (char*)inbuf+i;
        }

        if(!unesc)
        {
            unesc = malloc(len+1);
        }
        // unescape first to avoid escaping escape
        CdxUrlUnescapeString(unesc,in);
        // then escape, including mark and other reserved char that can come from escape sequences
        CdxUrlEscapeStringPart(outbuf,unesc);
        outbuf += strlen(outbuf);
        i += strlen(in);
    }
    *outbuf = '\0';
    
    if(tmp) 
    {
        free(tmp);
        tmp = NULL;
    }
    if(unesc) 
    {
        free(unesc);
        unesc = NULL;
    }
}

//*******************************************************************************************//
/* Replace escape sequences in an URL (or a part of an URL) */
/* works like strcpy(), but without return argument */
//*******************************************************************************************//

void CdxUrlUnescapeString(char *outbuf, const char *inbuf)
{   
    int i = 0;
    int len = 0;
    unsigned char c,c1,c2;
    
    len = strlen(inbuf);
    for (i=0;i<len;i++)
    {
        c = inbuf[i];
        if (c == '%' && i<len-2)     //must have 2 more chars
        { 
            c1 = toupper(inbuf[i+1]); // we need uppercase characters
            c2 = toupper(inbuf[i+2]);
            //if (((c1>='0' && c1<='9') || (c1>='A' && c1<='F')) &&
            if ((c1>='0' && c1<='7') &&
                ((c2>='0' && c2<='9') || (c2>='A' && c2<='F'))) 
            {
                if (c1>='0' && c1<='9') 
                {
                    c1-='0';
                }
                else 
                {
                    c1-='A'-10;
                }
                if(c2>='0' && c2<='9')
                {
                    c2-='0';
                }
                else 
                {
                    c2-='A'-10;
                }
                c = (c1<<4) + c2;
                i= i+2;               //only skip next 2 chars if valid esc
            }
        }
        *outbuf++ = c;
    }
    *outbuf++='\0'; //add nullterm to string
}

//*******************************************************************************************//
//*******************************************************************************************//

void CdxUrlEscapeStringPart(char *outbuf, const char *inbuf)

{   
    int i = 0;
    int len = 0;
    unsigned char c,c1,c2;

    len=strlen(inbuf);

    for(i=0;i<len;i++) 
    {
        c = inbuf[i];
        if ((c=='%') && i<len-2 ) 
        { 
            //need 2 more characters
             c1 = toupper(inbuf[i+1]); 
             c2 = toupper(inbuf[i+2]); // need uppercase chars
        } 
        else 
        {
            c1 = 129; 
            c2 = 129; //not escape chars
        }
         
        //if((c >= 'A' && c <= 'Z') ||(c >= 'a' && c <= 'z') ||(c >= '0' && c <= '9') ||(c >= 0x7f)) 
        if((c >= 'A' && c <= 'Z') ||(c >= 'a' && c <= 'z') ||(c >= '0' && c <= '9'))
        {
            *outbuf++ = c;
        } 
        else if(c=='%' && ((c1 >= '0' && c1 <= '9') || (c1 >= 'A' && c1 <= 'F')) &&
               ((c2 >= '0' && c2 <= '9') || (c2 >= 'A' && c2 <= 'F'))) 
        {
            // check if part of an escape sequence already
            *outbuf++=c;                   
           // dont escape again error as this should not happen against RFC 2396 to escape a string twice
        } 
        else 
        {
            /* all others will be escaped */
            c1 = ((c & 0xf0) >> 4);
            c2 = (c & 0x0f);
            if(c1 < 10) 
            {
                c1 += '0';
            }
            else 
            {
                c1 += 'A'-10;
            }
            if(c2 < 10) 
            {
                c2 += '0';
            }
            else
            {
                c2+='A'-10;
            }
            *outbuf++ = '%';
            *outbuf++ = c1;
            *outbuf++ = c2;
        }
    }
    *outbuf++ = '\0';
}
void CdxUrlFree(CdxUrlT* curUrl)
{   
    if(curUrl == NULL)
    {
        return;
    }
    if(curUrl->url)
    {
        free(curUrl->url);
    }
    if(curUrl->protocol)
    {
        free(curUrl->protocol);
    }
    if(curUrl->hostname)
    {
        free(curUrl->hostname);
    }
    if(curUrl->file)
    {
        free(curUrl->file);
    }
    if(curUrl->username)
    {
        free(curUrl->username);
    }
    if(curUrl->password)
    {
        free(curUrl->password);
    }
    if(curUrl->noauth_url)
    {
        free(curUrl->noauth_url);
    }
    free(curUrl);
}
CdxUrlT* CdxCheck4Proxies(CdxUrlT *url) 
{   
    int len = 0;
    CdxUrlT *urlOut = NULL;
    char *proxy = NULL;
    char *newUrl = NULL;
    CdxUrlT *tmpUrl = NULL;
    CdxUrlT *proxyUrl = NULL;
    
    if(url == NULL) 
    {
        return NULL;
    }
    urlOut = CdxUrlNew(url->url);
    if(!strcasecmp(url->protocol, "http_proxy")) 
    {
        CDX_LOGI("Using HTTP proxy: http://%s:%d\n", url->hostname, url->port);
        return urlOut;
    }
    // Check if the http_proxy environment variable is set.
    if(!strcasecmp(url->protocol, "http"))
    {
        proxy = getenv("http_proxy");
        if(proxy != NULL) 
        {
            // We got a proxy, build the URL to use it
            proxyUrl = CdxUrlNew(proxy);
            if(proxyUrl == NULL) 
            {   
                CDX_LOGI("proxy_url is NULL.");
                return urlOut;
            }

#ifdef HAVE_AF_INET6
            if(network_ipv4_only_proxy && (gethostbyname(url->hostname)==NULL))
            {
                CdxUrlFree(proxyUrl);
                return urlOut;
            }
#endif
            len = strlen(proxyUrl->hostname) + strlen(url->url) + 20;    // 20 = http_proxy:// + port
            newUrl = malloc(len+1);
            if (newUrl == NULL) 
            {
                CdxUrlFree(proxyUrl);
                return urlOut;
            }
            sprintf(newUrl, "http_proxy://%s:%d/%s", proxyUrl->hostname, proxyUrl->port, url->url);
            tmpUrl = CdxUrlNew(newUrl);
            if(tmpUrl == NULL) 
            {
                free(newUrl);
                newUrl = NULL;
                CdxUrlFree(proxyUrl);
                return urlOut;
            }
            CdxUrlFree(urlOut);
            urlOut = tmpUrl;
            free(newUrl);
            newUrl = NULL;
            CdxUrlFree(proxyUrl);
        }
    }
    return urlOut;
}
#if 0
char *aw_mp_asprintf(char *fmt, ...)
{
    char *p = NULL;
    va_list va;
    int len;
    
    va_start(va, fmt);
    len = vsnprintf(NULL, 0, fmt, va);
    va_end(va);
    if(len < 0)
    {
        goto end;
    }
    p = malloc(len + 1);
    if(!p)
    {
        goto end;
    }
    va_start(va, fmt);
    len = vsnprintf(p, len + 1, fmt, va);
    va_end(va);
    if(len < 0)
    {
        free(p);
        p = NULL;
    }
end:
    return p;
}

char *CdxGetNoauthUrl(CdxUrlT *url)
{
    if(url->port)
    {
        return aw_mp_asprintf("%s://%s:%d%s",
                            url->protocol, url->hostname, url->port, url->file);
    }
    else
    {
        return aw_mp_asprintf("%s://%s%s",
                            url->protocol, url->hostname, url->file);
    }
}
#endif
CdxUrlT *CdxUrlRedirect(CdxUrlT **url, char *redir) 
{
    CdxUrlT *u = *url;
    CdxUrlT *res;
    
    if(!strchr(redir, '/') || *redir == '/') 
    {
        char *tmp;
        char *newurl = malloc(strlen(u->url) + strlen(redir) + 1);
        strcpy(newurl, u->url);
        if (*redir == '/') 
        {
            redir++;
            tmp = strstr(newurl, "://");
            if(tmp) 
            {
                tmp = strchr(tmp + 3, '/');
            }
        } 
        else
        {
            tmp = strrchr(newurl, '/');
        }
        
        if(tmp) 
        {
            tmp[1] = 0;
        }
        strcat(newurl, redir);
        res = CdxUrlNew(newurl);
        free(newurl);
   }
   else
   {
        res = CdxUrlNew(redir);
   }
   CdxUrlFree(u);
   *url = res;
   return res;
}

