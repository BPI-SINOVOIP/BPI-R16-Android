#include "CdxMmsStream.h"
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <errno.h>
#include <ctype.h>

#include <fcntl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>


#define USER_EXIT         -4
#define TCP_ERROR_TIMEOUT -3     /* connection timeout */
#define TCP_ERROR_FATAL   -2     /* unable to resolve name */
#define TCP_ERROR_PORT    -1     /* unable to connect to a particular port */


static int (*aw_stream_check_interrupt_cb)(int time) = NULL;

void aw_stream_set_interrupt_callback(int (*cb)(int))
{
    aw_stream_check_interrupt_cb = cb;
}

int aw_stream_check_interrupt(int time)
{
    if(!aw_stream_check_interrupt_cb)
    {
        usleep(time * 1000);
        return 0;
    }

    return aw_stream_check_interrupt_cb(time);
}


//***************************************************************************************************************//
//***************************************************************************************************************//
// Connect to a server using a TCP connection, with specified address family
// return -2 for fatal error, like unable to resolve name, connection timeout...
// return -1 is unable to connect to a particular port
// verb present whether the host need gethostbyname
static int connect2Server_with_af(aw_mms_inf_t* mmsStreamInf, char *host, int port, int af,int verb)
{
        int             socket_server_fd;
        int             ret;
        int             err;
        int             sockRecvLen;
        socklen_t       err_len;
        int             count = 0;
        fd_set          set;
        struct timeval  tv;
        union
        {
            struct sockaddr_in four;
        }server_address;
        
        size_t          server_address_size;
        void*           our_s_addr; //     Pointer to sin_addr or sin6_addr
        struct hostent* hp = NULL;
    
        struct timeval to;

        //socket_server_fd = socket(af, SOCK_STREAM, 0);   //create socket
        
        socket_server_fd = CdxAsynSocket(0/*SOCKRECVBUF_LEN*/, &sockRecvLen);
        if(socket_server_fd == 1)
        {
        	CDX_LOGW("+++++++ fd(%d), maybe error", socket_server_fd);
        }
        mmsStreamInf->sockFd = socket_server_fd;
        if(socket_server_fd==-1)
        {
            CDX_LOGE("io err errno(%d)", errno);
            return TCP_ERROR_FATAL;
        }

#if defined(SO_RCVTIMEO) && defined(SO_SNDTIMEO)

        to.tv_sec = 10;
        to.tv_usec = 0;

        setsockopt(socket_server_fd, SOL_SOCKET, SO_RCVTIMEO, &to, sizeof(to));
        setsockopt(socket_server_fd, SOL_SOCKET, SO_SNDTIMEO, &to, sizeof(to));
#endif
	    switch (af)
	    {
		    case AF_INET:
			    our_s_addr = (void *) &server_address.four.sin_addr;
			    break;

		    default:
			    CDX_LOGE("unexpect af...");
			    return TCP_ERROR_FATAL;
	    }
	    memset(&server_address, 0, sizeof(server_address));

	    if (inet_aton(host, our_s_addr)!=1)
	    {

		    hp=(struct hostent*)gethostbyname( host );

		    if( hp==NULL )
		    {
			    if(verb)
			    {
                    CDX_LOGE("io err errno(%d)", errno);			        
			    	return TCP_ERROR_FATAL;
			    }
		    }
		    memcpy( our_s_addr, (void*)hp->h_addr_list[0], hp->h_length );
	    }

        switch (af)
	    {
		    case AF_INET:
			    server_address.four.sin_family=af;
			    server_address.four.sin_port=htons(port);
			    server_address_size = sizeof(server_address.four);
			    break;

		    default:
                CDX_LOGE("unexpect af...");
		    	return TCP_ERROR_FATAL;
	    }
#if HAVE_INET_PTON
	inet_ntop(af, our_s_addr, buf, 255);    //*整数转化为点分十进制数
#elif HAVE_INET_ATON || defined(HAVE_WINSOCK2_H)
	av_strlcpy(buf, inet_ntoa( *((struct in_addr*)our_s_addr)), 255);
#endif

    // Turn the socket as non blocking so we can timeout on the connection
	fcntl( socket_server_fd, F_SETFL, fcntl(socket_server_fd, F_GETFL) | O_NONBLOCK);

	if(connect(socket_server_fd, (struct sockaddr*)&server_address, server_address_size)==-1 )
	{
	
        CDX_LOGV("connect, errno(%d)", errno);
		if(errno != EINPROGRESS )
		{
			closesocket(socket_server_fd);
            CDX_LOGE("io err errno(%d)", errno);
			return TCP_ERROR_PORT;
		}
	}
    tv.tv_sec = 0;
	tv.tv_usec = 10000;
	FD_ZERO(&set);
	FD_SET(socket_server_fd, &set);

    // When the connection will be made, we will have a writeable fd
	while((ret = select(socket_server_fd+1, NULL, &set, NULL, &tv)) == 0)
	{
	    if(mmsStreamInf->exitFlag)
	    {
	        return USER_EXIT;
	    }
		CDX_LOGV(" select 0");
		if(count > 30 || aw_stream_check_interrupt(100))
		{
			if(count > 30)
            {
                CDX_LOGE("*****************ConnTimeout");
            }
            else
            {
                CDX_LOGE("***************Connection interrupted by user");
            }
			return TCP_ERROR_TIMEOUT;
		}

		count++;
	    FD_ZERO( &set );
	    FD_SET( socket_server_fd, &set );
	    tv.tv_sec = 0;
	    tv.tv_usec = 100000;
	}

	if(ret < 0)
		CDX_LOGE(" select error\n");

	// Check if there were any errors
	err_len = sizeof(int);
	ret = getsockopt(socket_server_fd,SOL_SOCKET,SO_ERROR,&err,&err_len);
	if(ret < 0)
	{
        CDX_LOGE("io err errno(%d)", errno);
		return TCP_ERROR_FATAL;
	}

	if(err > 0)
	{
        CDX_LOGE("io err errno(%d)", errno);
		return TCP_ERROR_PORT;
	}

	return socket_server_fd;
}
    

// Connect to a server using a TCP connection
// return -2 for fatal error, like unable to resolve name, connection timeout...
// return -1 is unable to connect to a particular port
// if success , return socket_server_fd
int Connect2Server(aw_mms_inf_t* mmsStreamInf,char *host, int  port, int verb)
{
    #define network_prefer_ipv4 0

	return connect2Server_with_af(mmsStreamInf,host, port, AF_INET,verb);
}
