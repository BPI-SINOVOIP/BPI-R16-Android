/*
 * cx2081x.c  --  cx2081x ALSA Soc Audio driver
 *
 * Copyright(c) 2015-2018 Allwinnertech Co., Ltd.
 *      http://www.allwinnertech.com
 *
 * Author: liu shaohua <liushaohua@allwinnertech.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 */

#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/firmware.h>
#include <linux/delay.h>
#include <linux/pm.h>
#include <linux/i2c.h>
#include <linux/regmap.h>
#include <linux/debugfs.h>
#include <linux/slab.h>
#include <sound/core.h>
#include <sound/pcm.h>
#include <sound/pcm_params.h>
#include <sound/soc.h>
#include <sound/initval.h>
#include <sound/tlv.h>
#include <linux/regulator/consumer.h>
//#include "cx2081x.h"


#define I2C_CHANNEL_NUM 1


struct cx2081x_priv {
	struct i2c_client *i2c;
	struct snd_soc_codec *codec;
};

static const struct snd_soc_dapm_widget cx2081x_dapm_widgets[] = {

};

/* Target, Path, Source */
static const struct snd_soc_dapm_route cx2081x_audio_map[] = {
};

static const struct snd_kcontrol_new cx2081x_controls[] = {
};
#ifdef CONFIG_PM
static int cx2081x_suspend(struct snd_soc_codec *codec)
{
//	struct cx2081x_priv *cx2081x = dev_get_drvdata(codec->dev);
	return 0;
}

static int cx2081x_resume(struct snd_soc_codec *codec)
{
//	struct cx2081x_priv *cx2081x = dev_get_drvdata(codec->dev);
	return 0;
}
#else
#define cx2081x_suspend NULL
#define cx2081x_resume NULL
#endif

static int cx2081x_probe(struct snd_soc_codec *codec)
{
	struct cx2081x_priv *cx2081x = dev_get_drvdata(codec->dev);
	int ret = 0;

	ret = snd_soc_codec_set_cache_io(codec, 8, 8, CONFIG_REGMAP_I2C);
	if (ret < 0) {
		dev_err(codec->dev, "Failed to set cache I/O: %d\n", ret);
		return ret;
	}
	cx2081x->codec = codec;
#if 0
{
	snd_soc_write(codec, 0x10,0x00);
	/*0x11=0x00 ; Disable ADC for Setup*/
	snd_soc_write(codec, 0x11,0x00);
	/*0x16=0x00 ; Use DC Filters for ADCs*/
	snd_soc_write(codec, 0x16,0x00);
	/*0x80=0x03 ; MCLK is an input*/
	snd_soc_write(codec, 0x80,0x03);
	/*0x08=0x20 ; MCLK !gated*/
	snd_soc_write(codec, 0x08,0x20);
	/*0x09=0x03 ; Use MLCK directly*/
	snd_soc_write(codec, 0x09,0x03);
	/*0x0B=0x00 ;*/
	snd_soc_write(codec, 0x0B,0x00);
	/*0x0C=0x0A ; Slave Mode, Clocks !gated*/
	snd_soc_write(codec, 0x0C,0x0A);
	/*0x0E=0x00 ; Gate DAC/PWM clocks*/
	snd_soc_write(codec, 0x0E,0x00);

	/*
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	*/
	snd_soc_write(codec, 0x78,0x2d);
	/*
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	*/
	snd_soc_write(codec, 0x78,0x6d);
	/*
	0x7A=0x01 ; Enable VREFP
	*/
	snd_soc_write(codec, 0x7A,0x01);

	/*0x30=0x0B ; 7-Wire Mode, 16-bit, DSP/TDM Mode*/
	snd_soc_write(codec, 0x30,0x0B);
	/*0x31=0x1F ;*/ /*256*/
	snd_soc_write(codec, 0x31,0x1F);
	/*0x32=0x07 ;*/
	snd_soc_write(codec, 0x32,0x07);
	/*0x39=0x0A ; DSP_DSTART_DLY, MSB First, DSP_TX_OUT_LINE_SEL (all data on TX_DATA_1)*/
	snd_soc_write(codec, 0x39,0x0A);
	/*0x3A=0x00 ; ADC1 starts at 0*/
	snd_soc_write(codec, 0x3A,0x00);
	/*0x3B=0x02 ; ADC2 starts at 2*/
	snd_soc_write(codec, 0x3B,0x02);
	/*0x3C=0x04 ; ADC3 starts at 4*/
	snd_soc_write(codec, 0x3C,0x04);
	/*0x3D=0x06 ; ADC4 starts at 6*/
	snd_soc_write(codec, 0x3D,0x06);
	/*0x3E=0x6F ; Enable TX1-4 and Ch4 is Last of Frame*/
	snd_soc_write(codec, 0x3E,0x6F);

	/*0x3F=0x00 ;*/
	snd_soc_write(codec, 0x3F,0x00);
	/*0x83=0x0F ; TX_CLK, TX_LRCK are Inputs; TX_DATA_1 is Output*/
	snd_soc_write(codec, 0x83,0x0F);
	/*0xBC=0x0C ; ADC1 6dB Gain*/
	snd_soc_write(codec, 0xBC,0x0C);
	/*0xBD=0x0C ; ADC2 6dB Gain*/
	snd_soc_write(codec, 0xBD,0x0C);
	/*0xBE=0x0C ; ADC3 6dB Gain*/
	snd_soc_write(codec, 0xBE,0x0C);
	/*0xBF=0x0C ; ADC4 6dB Gain*/
	snd_soc_write(codec, 0xBF,0x0C);
	/*0xA2=0x18 ; Invert Ch1 DSM Clock, Output on Rising Edge*/
	snd_soc_write(codec, 0xA2,0x18);
	/*0xA9=0x18 ; Ch2*/
	snd_soc_write(codec, 0xA9,0x18);
	/*0xB0=0x18 ; Ch3*/
	snd_soc_write(codec, 0xB0,0x18);

	/*0xB7=0x18 ; Ch4*/
	snd_soc_write(codec, 0xB7,0x18);
	/*0x10=0x00 ; Disable all ADC clocks*/
	snd_soc_write(codec, 0x10,0x00);
	/*0x11=0x00 ; Disable all ADC clocks and Mixer*/
	snd_soc_write(codec, 0x11,0x00);
	/*0x10=0x1F ; Enable all ADC clocks and ADC digital*/
	snd_soc_write(codec, 0x10,0x1F);
	/*0x11=0x1F ; Enable all ADCs and set 48kHz sample rate*/
	snd_soc_write(codec, 0x11,0x1F);
	/*0x10=0x5F ; Enable all ADC clocks, ADC digital and ADC Mic Clock Gate*/
	snd_soc_write(codec, 0x10,0x5F);
	/*0xA0=0x0E ; ADC1, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xA0,0x0E);
	/*0xA7=0x0E ; ADC2, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xA7,0x0E);
	/*0xAE=0x0E ; ADC3, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xAE,0x0E);
	/*0xB5=0x0E ; ADC4, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xB5,0x0E);
	/*0xA0=0x06 ; ADC1 !Mute*/
	snd_soc_write(codec, 0xA0,0x07);
	/*0xA7=0x06 ; ADC2 !Mute*/
	snd_soc_write(codec, 0xA7,0x07);
	/*0xAE=0x06 ; ADC3 !Mute*/
	snd_soc_write(codec, 0xAE,0x07);
	/*0xB5=0x06 ; ADC4 !Mute*/
	snd_soc_write(codec, 0xB5,0x07);
}
	#endif
	//reg_val = snd_soc_read(codec, AIF1_MXR_SRC);
	//reg_val |= (0x1<<AIF1_AD0L_ADCL_MXR);


	return 0;
}

static int cx2081x_remove(struct snd_soc_codec *codec)
{
//	struct cx2081x_priv *cx2081x = dev_get_drvdata(codec->dev);

	return 0;
}

static int cx2081x_hw_params(struct snd_pcm_substream *substream,
			    struct snd_pcm_hw_params *params,
			    struct snd_soc_dai *dai)
{
	struct snd_soc_codec *codec;
	u16 blen;
	codec = dai->codec;
	/*0x10=0x00 ; Disable ADC for Setup*/
	snd_soc_write(codec, 0x10,0x00);
	/*0x11=0x00 ; Disable ADC for Setup*/
	snd_soc_write(codec, 0x11,0x00);
	/*0x16=0x00 ; Use DC Filters for ADCs*/
	snd_soc_write(codec, 0x16,0x00);
	/*0x80=0x03 ; MCLK is an input*/
	snd_soc_write(codec, 0x80,0x03);
	/*0x08=0x20 ; MCLK !gated*/
	snd_soc_write(codec, 0x08,0x20);
	/*0x09=0x03 ; Use MLCK directly*/
	snd_soc_write(codec, 0x09,0x03);
	/*0x0B=0x00 ;*/
	snd_soc_write(codec, 0x0B,0x00);
	/*0x0C=0x0A ; Slave Mode, Clocks !gated*/
	snd_soc_write(codec, 0x0C,0x0A);
	/*0x0E=0x00 ; Gate DAC/PWM clocks*/
	snd_soc_write(codec, 0x0E,0x00);

	/*
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	0x78=0x2D ; Enable VREF @ 2.8V (5V) or 2.6V (3.3V)
	*/
	snd_soc_write(codec, 0x78,0x2d);
	/*
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	0x78=0x6D ; Enable Analog LDO
	*/
	snd_soc_write(codec, 0x78,0x6d);
	/*
	0x7A=0x01 ; Enable VREFP
	*/
	snd_soc_write(codec, 0x7A,0x01);

	/*0x30=0x0B ; 7-Wire Mode, 16-bit, DSP/TDM Mode*/
	snd_soc_write(codec, 0x30,0x0B);
	/*0x31=0x1F ;*/ /*256*/
	snd_soc_write(codec, 0x31,0x1F);
	/*0x32=0x07 ;*/
	snd_soc_write(codec, 0x32,0x07);
	/*0x39=0x0A ; DSP_DSTART_DLY, MSB First, DSP_TX_OUT_LINE_SEL (all data on TX_DATA_1)*/
	snd_soc_write(codec, 0x39,0x0A);
	/*0x3A=0x00 ; ADC1 starts at 0*/
	snd_soc_write(codec, 0x3A,0x00);
	/*0x3B=0x02 ; ADC2 starts at 2*/
	snd_soc_write(codec, 0x3B,0x02);
	/*0x3C=0x04 ; ADC3 starts at 4*/
	snd_soc_write(codec, 0x3C,0x04);
	/*0x3D=0x06 ; ADC4 starts at 6*/
	snd_soc_write(codec, 0x3D,0x06);
	/*0x3E=0x6F ; Enable TX1-4 and Ch4 is Last of Frame*/
	snd_soc_write(codec, 0x3E,0x6F);

	/*0x3F=0x00 ;*/
	snd_soc_write(codec, 0x3F,0x00);
	/*0x83=0x0F ; TX_CLK, TX_LRCK are Inputs; TX_DATA_1 is Output*/
	snd_soc_write(codec, 0x83,0x0F);
	/*0xBC=0x0C ; ADC1 6dB Gain*/
	snd_soc_write(codec, 0xBC,0x3f);//snd_soc_write(codec, 0xBC,0x0C);
	/*0xBD=0x0C ; ADC2 6dB Gain*/
	snd_soc_write(codec, 0xBD,0x3f);
	/*0xBE=0x0C ; ADC3 6dB Gain*/
	snd_soc_write(codec, 0xBE,0x3f);
	/*0xBF=0x0C ; ADC4 6dB Gain*/
	snd_soc_write(codec, 0xBF,0x3f);   
	/*0xA2=0x18 ; Invert Ch1 DSM Clock, Output on Rising Edge*/
	snd_soc_write(codec, 0xA2,0x18);
	/*0xA9=0x18 ; Ch2*/
	snd_soc_write(codec, 0xA9,0x18);
	/*0xB0=0x18 ; Ch3*/
	snd_soc_write(codec, 0xB0,0x18);

	/*0xB7=0x18 ; Ch4*/
	snd_soc_write(codec, 0xB7,0x18);
	/*0x10=0x00 ; Disable all ADC clocks*/
	snd_soc_write(codec, 0x10,0x00);
	/*0x11=0x00 ; Disable all ADC clocks and Mixer*/
	snd_soc_write(codec, 0x11,0x00);
	/*0x10=0x1F ; Enable all ADC clocks and ADC digital*/
	snd_soc_write(codec, 0x10,0x1F);
	/*0x11=0x1F ; Enable all ADCs and set 48kHz sample rate*/
	snd_soc_write(codec, 0x11,0x1F);
	/*0x10=0x5F ; Enable all ADC clocks, ADC digital and ADC Mic Clock Gate*/
	snd_soc_write(codec, 0x10,0x5F);
	/*0xA0=0x0E ; ADC1, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xA0,0x0E);
	/*0xA7=0x0E ; ADC2, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xA7,0x0E);
	/*0xAE=0x0E ; ADC3, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xAE,0x0E);
	/*0xB5=0x0E ; ADC4, Mute PGA, enable AAF/ADC/PGA*/
	snd_soc_write(codec, 0xB5,0x0E);
	/*0xA0=0x06 ; ADC1 !Mute*/
	snd_soc_write(codec, 0xA0,0x07);
	/*0xA7=0x06 ; ADC2 !Mute*/
	snd_soc_write(codec, 0xA7,0x07);
	/*0xAE=0x06 ; ADC3 !Mute*/
	snd_soc_write(codec, 0xAE,0x07);
	/*0xB5=0x06 ; ADC4 !Mute*/
	snd_soc_write(codec, 0xB5,0x07);
	switch (params_format(params)) {
	case SNDRV_PCM_FORMAT_S16_LE:
		blen = 0x0;
		break;
	case SNDRV_PCM_FORMAT_S20_3LE:
		blen = 0x1;
		break;
	case SNDRV_PCM_FORMAT_S24_LE:
		blen = 0x2;
		break;
	default:
		dev_err(dai->dev, "Unsupported word length: %u\n",
			params_format(params));
		return -EINVAL;
	}
  msleep(400);
	return 0;
}

static int cx2081x_set_fmt(struct snd_soc_dai *dai, unsigned int fmt)
{
	return 0;
}
static int cx2081x_set_sysclk(struct snd_soc_dai *dai,
			     int clk_id, unsigned int freq, int dir)
{
	return 0;
}

static int cx2081x_set_clkdiv(struct snd_soc_dai *dai,
			     int div_id, int div)
{
	return 0;
}

static int cx2081x_set_pll(struct snd_soc_dai *dai, int pll_id,
			  int source, unsigned int freq_in,
			  unsigned int freq_out)
{
	return 0;
}


static int cx2081x_hw_free(struct snd_pcm_substream *substream,struct snd_soc_dai *dai)
{
	struct snd_soc_codec *codec;
	codec = dai->codec;
	//snd_soc_write(codec, 0x78,0);
	//snd_soc_write(codec, 0x7A,0);
	return 0;
}

static const struct snd_soc_dai_ops cx2081x_dai_ops = {
	.hw_params = cx2081x_hw_params,
	.set_fmt = cx2081x_set_fmt,
	.set_sysclk = cx2081x_set_sysclk,
	.set_clkdiv = cx2081x_set_clkdiv,
	.set_pll = cx2081x_set_pll,
	.hw_free = cx2081x_hw_free,
};

#define CX2081X_FORMATS (SNDRV_PCM_FMTBIT_S16_LE | SNDRV_PCM_FMTBIT_S20_3LE | \
			SNDRV_PCM_FMTBIT_S24_LE)

#define CX2081X_RATES SNDRV_PCM_RATE_8000_96000
static struct snd_soc_dai_driver cx2081x_dai0 = {
		.name = "cx2081x-pcm0",
		.capture = {
			.stream_name = "Capture",
			.channels_min = 1,
			.channels_max = 8,
			.rates = CX2081X_RATES,
			.formats = CX2081X_FORMATS,
		},
		.ops = &cx2081x_dai_ops,
};

static struct snd_soc_dai_driver cx2081x_dai1 = {
		.name = "cx2081x-pcm1",
		.capture = {
			.stream_name = "Capture",
			.channels_min = 1,
			.channels_max = 8,
			.rates = CX2081X_RATES,
			.formats = CX2081X_FORMATS,
		},
		.ops = &cx2081x_dai_ops,
};

static struct snd_soc_codec_driver soc_codec_dev_cx2081x = {
	.probe = cx2081x_probe,
	.remove = cx2081x_remove,
	.suspend = cx2081x_suspend,
	.resume = cx2081x_resume,

	.dapm_widgets = cx2081x_dapm_widgets,
	.num_dapm_widgets = ARRAY_SIZE(cx2081x_dapm_widgets),
	.dapm_routes = cx2081x_audio_map,
	.num_dapm_routes = ARRAY_SIZE(cx2081x_audio_map),
	.controls = cx2081x_controls,
	.num_controls = ARRAY_SIZE(cx2081x_controls),
};

/***************************************************************************/
static ssize_t cx2081x_store(struct device *dev,
	struct device_attribute *attr, const char *buf, size_t count)
{
	static int val = 0, flag = 0;
	u8 reg,num,i=0;
	u8 value_w,value_r[128];
	struct cx2081x_priv *cx2081x = dev_get_drvdata(dev);
	val = simple_strtol(buf, NULL, 16);
	flag = (val >> 16) & 0xF;
	if(flag) {//write
		reg = (val >> 8) & 0xFF;
		value_w =  val & 0xFF;
		snd_soc_write(cx2081x->codec, reg, value_w);
		printk("write 0x%x to reg:0x%x\n",value_w,reg);
	} else {
		reg =(val>>8)& 0xFF;
		num=val&0xff;
		printk("\n");
		printk("read:start add:0x%x,count:0x%x\n",reg,num);
		do{
			value_r[i] = snd_soc_read(cx2081x->codec, reg);
			printk("0x%x: 0x%x ",reg,value_r[i]);
			reg+=1;
			i++;
			if(i == num)
				printk("\n");
			if(i%4==0)
				printk("\n");
		}while(i<num);
	}
	return count;
}
static ssize_t cx2081x_show(struct device *dev,
	struct device_attribute *attr, char *buf)
{
	printk("echo flag|reg|val > cx2081x\n");
	printk("eg read star addres=0x06,count 0x10:echo 0610 >cx2081x\n");
	printk("eg write value:0xfe to address:0x06 :echo 106fe > cx2081x\n");
    return 0;
}
static DEVICE_ATTR(cx2081x, 0644, cx2081x_show, cx2081x_store);

static struct attribute *audio_debug_attrs[] = {
	&dev_attr_cx2081x.attr,
	NULL,
};

static struct attribute_group audio_debug_attr_group = {
	.name   = "cx2081x_debug",
	.attrs  = audio_debug_attrs,
};
/************************************************************/
static int __devinit cx2081x_i2c_probe(struct i2c_client *i2c,
				      const struct i2c_device_id *i2c_id)
{
	struct cx2081x_priv *cx2081x;
	int ret = 0;
	struct regulator *cx20810_regulator = NULL;
	cx2081x = devm_kzalloc(&i2c->dev, sizeof(struct cx2081x_priv),
			      GFP_KERNEL);
	if (cx2081x == NULL) {
		dev_err(&i2c->dev, "Unable to allocate private data\n");
		return -ENOMEM;
	} else
		dev_set_drvdata(&i2c->dev, cx2081x);

{
	cx20810_regulator = regulator_get(NULL, "axp22_dldo1");
	if (!cx20810_regulator) {
		pr_err("get audio pa_sw0_vol failed\n");
		return -EFAULT;
	}
	regulator_set_voltage(cx20810_regulator, 3300000, 3300000);
	regulator_enable(cx20810_regulator);
}

	//sprintf(cx2081x_dai.name, "cx2081x-pcm%d", i2c_id->driver_data);
	if (i2c_id->driver_data == 0)
		ret = snd_soc_register_codec(&i2c->dev, &soc_codec_dev_cx2081x, &cx2081x_dai0, 1);
	else if (i2c_id->driver_data == 1)
		ret = snd_soc_register_codec(&i2c->dev, &soc_codec_dev_cx2081x, &cx2081x_dai1, 1);
	else
		pr_err("The wrong i2c_id number :%d\n",i2c_id->driver_data);
	if (ret < 0) {
		dev_err(&i2c->dev, "Failed to register cx2081x: %d\n", ret);
	}
	ret = sysfs_create_group(&i2c->dev.kobj, &audio_debug_attr_group);
	if (ret) {
		pr_err("failed to create attr group\n");
	}
	return ret;
}

static __devexit int cx2081x_i2c_remove(struct i2c_client *i2c)
{
//	struct cx2081x_priv *cx2081x = dev_get_drvdata(&i2c->dev);
	snd_soc_unregister_codec(&i2c->dev);
	return 0;
}

static struct i2c_board_info cx2081x_i2c_board_info[] = {
	{I2C_BOARD_INFO("cx2081x_0", 0x35),	},
	{I2C_BOARD_INFO("cx2081x_1", 0x3b),	},
};

static const struct i2c_device_id cx2081x_i2c_id[] = {
	{ "cx2081x_0", 0 },
	{ "cx2081x_1", 1 },
	{ }
};
MODULE_DEVICE_TABLE(i2c, cx2081x_i2c_id);

static struct i2c_driver cx2081x_i2c_driver = {
	.driver = {
		.name = "cx2081x",
		.owner = THIS_MODULE,
	},
	.probe = cx2081x_i2c_probe,
	.remove = __devexit_p(cx2081x_i2c_remove),
	.id_table = cx2081x_i2c_id,
};

static int __init cx2081x_init(void)
{
	struct i2c_adapter *adapter;
	struct i2c_client *client;
	int i = 0;

	adapter = i2c_get_adapter(I2C_CHANNEL_NUM);
	if (!adapter)
		return -ENODEV;
	for(i = 0; i < sizeof(cx2081x_i2c_board_info)/sizeof(cx2081x_i2c_board_info[0]);i++) {
		client = NULL;
		client = i2c_new_device(adapter, &cx2081x_i2c_board_info[i]);
		if (!client)
			return -ENODEV;
	}
	i2c_put_adapter(adapter);

	return i2c_add_driver(&cx2081x_i2c_driver);

}
module_init(cx2081x_init);

static void __exit cx2081x_exit(void)
{
	i2c_del_driver(&cx2081x_i2c_driver);
}
module_exit(cx2081x_exit);

MODULE_DESCRIPTION("ASoC cx2081x driver");
MODULE_AUTHOR("liu shaohua <liushaohua@allwinnertech.com>");
MODULE_LICENSE("GPL");

