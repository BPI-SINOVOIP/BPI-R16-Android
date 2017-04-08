/*
**********************************************************************************************************************
*
*						           the Embedded Secure Bootloader System
*
*
*						       Copyright(C), 2006-2014, Allwinnertech Co., Ltd.
*                                           All Rights Reserved
*
* File    :
*
* By      :
*
* Version : V2.00
*
* Date	  :
*
* Descript:
**********************************************************************************************************************
*/
#include <stdarg.h>
#include <common.h>
#include <asm/arch/uart.h>
#include <private_boot0.h>

#define  MASK_LOW4      0xf
#define  NEGATIVE       1
#define  POSITIVE       0
#define  HEX_x          'x'
#define  HEX_X          'X'

int debug_mode = 0x17;
/*
******************************************************************************************************************
*
*Function Name : int_to_string_dec
*
*Description : This function is to convert an 'int' data 'input' to a string in decimalism, and the string
*              converted is in 'str'.
*
*Input : int input : 'int' data to be converted.
*        char * str : the start address of the string storing the converted result.
*
*Output : void
*
*call for :
*
*Others : None at present.
*
*******************************************************************************************************************
*/
void int_to_string_dec( int input , char * str)
{
	char stack[12];
	char sign_flag = POSITIVE ;      // 'sign_flag indicates wheater 'input' is positive or negative, default
	int i ;                           // value is 'POSITIVE'.
	int j ;

	if( input == 0 )
	{
		str[0] = '0';
		str[1] = '\0';                   // 'str' must end with '\0'
		return ;
	}

	if( input < 0 )                      // If 'input' is negative, 'input' is assigned to its absolute value.
	{
		sign_flag = NEGATIVE ;
		input = -input ;
	}

	for( i = 0; input > 0; ++i )
	{
		stack[i] = input%10 + '0';      // characters in reverse order are put in 'stack' .
		input /= 10;
	}                                   // at the end of 'for' loop, 'i' is the number of characters.


    j = 0;
	if( sign_flag == NEGATIVE )
		str[j++] = '-';		            // If 'input' is negative, minus sign '-' is placed in the head.
	for( --i  ; i >= 0; --i, ++j )
		str[j] = stack[i];
	str[j] = '\0';				        // 'str' must end with '\0'

	return;
}


void int_to_string_hex( int input, char * str )
{
	int i;
	static char base[] = "0123456789abcdef";

	for( i = 7; i >= 0; --i )
	{
		str[i] = base[input&MASK_LOW4];
		input >>= 4;
	}

	str[8] = '\0';                        // 'str' must end with '\0'

	return;
}

/*
************************************************************************************************************
*
*                                             function
*
*    函数名称：
*
*    参数列表：
*
*    返回值  ：
*
*    说明    ：
*
*
************************************************************************************************************
*/
static __u32 mem_puts(const char *str, char *p )
{
    __u32 len = 0;

	while( *str != '\0' )
	{
		if( *str == '\n' )                      // if current character is '\n', insert and output '\r'
		{
		    *p++ = '\r';
		    len ++;
        }
        *p++ = *str++;
        len ++;
	}

	return len;
}

static size_t log_prefix(const char *p, unsigned int *level)
{
	unsigned int lev = 0;
	size_t len;

	if (p[0] == '<' && p[2] == '>') {
		/* usual single digit level number or special char */
		switch (p[1]) {
		case '0' ... '7':
			lev = p[1] - '0';
			break;
		default:
			return 0;
		}
		len = 3;
	} else {
        lev = 6;
		len = 0;
	}

	if (level)
		*level = lev;
	return len;
}

/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int vsprintf(char *buf, const char *fmt, va_list args)
{
	char string[16];
	char *p, *q = buf;

	while( *fmt )
	{
		if( *fmt == '%' )
		{
			++fmt;
			p = string;
			switch( *fmt )
			{
				case 'd': int_to_string_dec( va_arg( args, int), string );
                          q += mem_puts( p, q );
						  ++fmt;
						  break;
				case 'x':
				case 'X': int_to_string_hex( va_arg( args,  int ), string );
						  q += mem_puts( p, q );
                          ++fmt;
						  break;
				case 'c': *q++ = va_arg( args,  __s32 );
						  ++fmt;
						  break;
				case 's': q += mem_puts( va_arg( args, char * ), q );
						  ++fmt;
						  break;
				default : *q++ = '%';                                    // if current character is not Conversion Specifiers 'dxpXucs',
						  *q++ = *fmt++;                                 // output directly '%' and current character, and then
						                                                 // let 'fmt' point to next character.
			}
		}
		else
		{
			if( *fmt == '\n' )                      // if current character is '\n', insert and output '\r'
				*q++ = '\r';

            *q++ = *fmt++;
		}
	}

    *q = 0;

	return q-buf;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
void puts(const char *s)
{
	char *src = (char *)s;
	while (*src != '\0')
		sunxi_serial_putc (*src++);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int sprintf(char * buf, const char *fmt, ...)
{
	va_list args;
	int i;

	va_start(args, fmt);
	i=vsprintf(buf,fmt,args);
	va_end(args);
	return i;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int printf(const char *fmt, ...)
{
    char printbuffer[256];
	va_list args;

    char *p;
    size_t print_len = 0;
    size_t plen;
    unsigned int current_log_level = 0;

    plen = log_prefix(fmt, &current_log_level);

    if (current_log_level > (debug_mode & 0x0000000F))
        return 0;
	va_start(args, fmt);

	/* For this to work, printbuffer must be larger than
	 * anything we ever want to print.
	 */
	print_len = vsprintf(printbuffer, fmt, args);
	va_end(args);

    p = printbuffer;
    /* Print the string */
    if (debug_mode & 0x000000F0) {
        unsigned tlen;
        char tbuf[12], *tp;
        int msecond = get_timer_masked();
        tlen = sprintf(tbuf, "[%d.%d] ", msecond/1000,msecond%1000);
        for (tp = tbuf; tp < tbuf + tlen; tp++)
            sunxi_serial_putc(*tp);
        print_len += tlen;
    }
    puts(p + plen);
    return print_len;
}
