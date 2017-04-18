#include <linux/module.h>
#include <linux/vermagic.h>
#include <linux/compiler.h>

MODULE_INFO(vermagic, VERMAGIC_STRING);

struct module __this_module
__attribute__((section(".gnu.linkonce.this_module"))) = {
 .name = KBUILD_MODNAME,
 .init = init_module,
#ifdef CONFIG_MODULE_UNLOAD
 .exit = cleanup_module,
#endif
 .arch = MODULE_ARCH_INIT,
};

MODULE_INFO(intree, "Y");

static const struct modversion_info ____versions[]
__used
__attribute__((section("__versions"))) = {
	{ 0xe2630d0, "module_layout" },
	{ 0xa6c8cd46, "input_free_int" },
	{ 0x12da5bb2, "__kmalloc" },
	{ 0xf9a482f9, "msleep" },
	{ 0xfbc74f64, "__copy_from_user" },
	{ 0x528c709d, "simple_read_from_buffer" },
	{ 0x15692c87, "param_ops_int" },
	{ 0x2e5810c6, "__aeabi_unwind_cpp_pr1" },
	{ 0x97255bdf, "strlen" },
	{ 0x7ee40a71, "dev_set_drvdata" },
	{ 0x43a53735, "__alloc_workqueue_key" },
	{ 0xe90a3c53, "i2c_del_driver" },
	{ 0x3b05df25, "malloc_sizes" },
	{ 0x76789af4, "hrtimer_cancel" },
	{ 0x240bcc02, "i2c_transfer" },
	{ 0xa778cffc, "remove_proc_entry" },
	{ 0x33543801, "queue_work" },
	{ 0x432fd7f6, "__gpio_set_value" },
	{ 0x9e370dae, "filp_close" },
	{ 0xb1ad28e0, "__gnu_mcount_nc" },
	{ 0x63588dd8, "input_free_platform_resource" },
	{ 0x91715312, "sprintf" },
	{ 0x64cb8f0, "sysfs_remove_group" },
	{ 0xe2d5255a, "strcmp" },
	{ 0x1888d6b3, "input_set_abs_params" },
	{ 0x89d145ed, "pin_config_get" },
	{ 0xb299985, "input_event" },
	{ 0xfa2a45e, "__memzero" },
	{ 0xb5eeb329, "register_early_suspend" },
	{ 0x5f754e5a, "memset" },
	{ 0x74c97f9c, "_raw_spin_unlock_irqrestore" },
	{ 0x27e1a049, "printk" },
	{ 0x20c55ae0, "sscanf" },
	{ 0x89e34b4, "sysfs_create_group" },
	{ 0x71c90087, "memcmp" },
	{ 0xa8f59416, "gpio_direction_output" },
	{ 0xd34bb007, "input_set_int_enable" },
	{ 0x84b183ae, "strncmp" },
	{ 0x73e20c1c, "strlcpy" },
	{ 0x7283e7cc, "input_set_capability" },
	{ 0x8c03d20c, "destroy_workqueue" },
	{ 0x673d42f5, "input_fetch_sysconfig_para" },
	{ 0xd648b85b, "i2c_register_driver" },
	{ 0x5546f22b, "input_request_int" },
	{ 0x3d45355f, "input_init_platform_resource" },
	{ 0xbc601ad6, "script_get_item" },
	{ 0x28422807, "input_register_device" },
	{ 0x780f2390, "hrtimer_start" },
	{ 0x996bdb64, "_kstrtoul" },
	{ 0x3d22b4f3, "kmem_cache_alloc_trace" },
	{ 0xbd7083bc, "_raw_spin_lock_irqsave" },
	{ 0x7bed2aa3, "input_set_power_enable" },
	{ 0x81f91103, "proc_create_data" },
	{ 0x1b68aba6, "pin_config_set" },
	{ 0x37a0cba, "kfree" },
	{ 0x9d669763, "memcpy" },
	{ 0x4d06ea9c, "input_unregister_device" },
	{ 0xb190a2b0, "hrtimer_init" },
	{ 0xb227ae83, "unregister_early_suspend" },
	{ 0x16113b7c, "dev_get_drvdata" },
	{ 0xe914e41e, "strcpy" },
	{ 0xfc595949, "filp_open" },
	{ 0xefaa8757, "input_allocate_device" },
};

static const char __module_depends[]
__used
__attribute__((section(".modinfo"))) =
"depends=";


MODULE_INFO(srcversion, "376A3E8DAF2C0201A94E53B");
