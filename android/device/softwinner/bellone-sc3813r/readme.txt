说明：
.git		: git仓库保存相关；
bluetooth	: 蓝牙相关
configs		: camera、gsensor等相关配置
hawkview	: ov5647相关
media		: 开机画面，动画，音乐；充电照片（其加载与否 看 fiber-xxx.mk中配置）
	initlogo.rle 为开机第二张

modules		: 内核加载模块（extract-bsp 加载过来）
kernel		: 内核（extract-bsp 加载过来）

overlay		: 需求修改
	frameworks（或packages） 对应 Android下frameworks （或packages）；在编译时复写到Android相应下面

prebuild	: 预装apk
recovery	: 复位重置相关

Sysconfig或packconfigs	; 对应 lichee下各项目的 sysconfig配置；修改配置时以lichee下为准，详情参考 packages.sh

tools		: 相应工具

其他，看其内容...


【注意：】
1、下载完后请将下载的device名astar-sc3403q中添加新分支重命名为astar-sc3403q（DEVICE 名），并切换替换掉源分支master。
2、补丁包在patch/或patch.rar里，解压合并补丁，需要编译uboot，即lichee/brandy目录。