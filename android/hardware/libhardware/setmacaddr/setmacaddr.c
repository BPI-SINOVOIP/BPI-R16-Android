#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <fcntl.h>
#include <utils/Log.h>

static void write_randmac_file(char *macaddr,char filepath[],int randvar1,int randvar2)
{
	int fd;
	char buf[80];
//	sprintf(macaddr, "00:e0:4c:%02x:%02x:%02x",
//changed by tingle for 6 bytes random
//Byte1,bit0,bit1 can't be 1.
	sprintf(macaddr, "%02x:%02x:%02x:%02x:%02x:%02x", \
	        (unsigned char)((randvar2)&0xFC), \
	        (unsigned char)((randvar2>>8)&0xFF), \
	        (unsigned char)((randvar2>>16)&0xFF), \
		 (unsigned char)((randvar1)&0xFF), \
	        (unsigned char)((randvar1>>8)&0xFF), \
	        (unsigned char)((randvar1>>16)&0xFF));	

	fd = open(filepath, O_CREAT|O_TRUNC|O_RDWR, 0644);
	if( fd >= 0)
	{
		memset(buf, 0, sizeof(buf));
		sprintf(buf, "%s",macaddr);
		write(fd, buf, sizeof(buf));
		close(fd);
	}
	
	if (chmod(filepath, 0644) < 0) {
	    ALOGE("Error changing permissions of %s to 0660: %s",
	         filepath, strerror(errno));
	    unlink(filepath);
	
	}


	ALOGD("%s: %s fd=%d, data=%s",__FUNCTION__, filepath, fd,buf);
}

unsigned int gen_randseed()
{
	int fd;
	int rc;
	unsigned int randseed;
	size_t len;
struct timeval tval;

	len =  sizeof(randseed);
	fd = open("/dev/urandom", O_RDONLY);
	if (fd < 0)
	{
		ALOGD("%s: Open /dev/urandom fail\n", __FUNCTION__);
		return -1;
	}
	rc = read(fd, &randseed, len);
	close(fd);
	if(rc <0)
	{
		if (gettimeofday(&tval, (struct timezone *)0) > 0)
			randseed = (unsigned int) tval.tv_usec;
		else
			randseed = (unsigned int) time(NULL);

		ALOGD("open /dev/urandom fail, using system time for randseed\n");
	}
	return randseed;
}


static void generate_mac(char *macaddr,char filepath[])
{
	unsigned int randseed;
	int rand_var1,rand_var2;
	ALOGD("Enter %s %s\n",__FUNCTION__,filepath);

	/*Check MAC_ADDR_FILE exist */

	if(access(filepath, F_OK) == 0)
	{
		ALOGD("%s: %s exists",__FUNCTION__, filepath);
		return;
	}
	randseed = gen_randseed();
	if(randseed == -1)
		return ;
	srand(randseed);

	rand_var1 = rand();
	rand_var2 = rand();
	ALOGD("%s:  rand_var1 =0x%x, rand_var2=0x%x",__FUNCTION__, rand_var1,rand_var2);
	write_randmac_file(macaddr,filepath,rand_var1,rand_var2);

}

int main(int argc, char ** argv)
{
	char macaddr[32],filepath[80];
	memset(macaddr, 0, sizeof(macaddr));
	memset(filepath, 0, sizeof(filepath));
	if(argc != 2)
	{
			ALOGD("Usage:setmacaddr <mac_addr_path>\n");
			return 0;
	}
	strncpy(filepath, argv[1], strlen(argv[1]));
	generate_mac(macaddr,filepath);

	return 0;
}
