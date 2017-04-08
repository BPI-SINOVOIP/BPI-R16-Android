
include ../config.mk

################################################################################
## config tool chains
################################################################################
ifeq ($(CONFIG_CC),$(OPTION_CC_GNUEABIHF))

# set compile tool chains to be arm-linux-gnueabihf- set.
CC    = arm-linux-gnueabihf-gcc
CPP   = arm-linux-gnueabihf-g++
STRIP = arm-linux-gnueabihf-strip
AR    = arm-linux-gnueabihf-ar
endif

ifeq ($(CONFIG_CC),$(OPTION_CC_GNUEABI))

# set compile tool chains to be arm-none-linux-gnueabi- set.
CC    = arm-none-linux-gnueabi-gcc
CPP   = arm-none-linux-gnueabi-g++
STRIP = arm-none-linux-gnueabi-strip
AR    = arm-none-linux-gnueabi-ar

endif


################################################################################
## set flags for golobal compile and link setting.
################################################################################

CONFIG_FOR_COMPILE = 
CONFIG_FOR_LINK = 


################################################################################

BuildPath      = ./build
ObjCommonPath     = $(BuildPath)/obj
OutputPath     = ./
DepCommonPath = $(BuildPath)/dep
Target         = $(OutputPath)/libplayer.so   ## output target.

################################################################################
## set the source files, object files and dependency files
################################################################################
## set the source path to VPATH.
SourcePath = $(shell find ./ -type d)
SvnPath = $(shell find ./ -type d | grep ".svn")
SourcePath := $(filter-out $(SvnPath) $(BuildPath) $(ObjectPath) $(DependFilePath), $(SourcePath))
VPATH := $(SourcePath)

## set the source files.
SourceFiles  = \
    audioDecComponent.cpp                 \
    audioRenderComponent.cpp              \
    videoDecComponent.cpp                 \
    videoRenderComponent.cpp              \
    subtitleDecComponent.cpp              \
    subtitleRenderComponent.cpp           \
    layerControl/layerControl_linux.cpp   \
    soundControl/soundControl_linux.cpp   \
    avtimer.cpp                           \
    messageQueue.cpp                      \
    bitrateEstimater.cpp                  \
    framerateEstimater.cpp                \
    streamManager.cpp                     \
    player.cpp

## set the object files.
ObjectFiles = $(patsubst %.cpp, $(ObjCommonPath)/%.o ,$(SourceFiles))
ObjectPath := $(dir $(ObjectFiles))

## set the dependency files.
DependFiles = $(patsubst %.cpp, $(DepCommonPath)/%.d ,$(SourceFiles))
DependFilePath := $(dir $(DependFiles))

ifneq ($(BuildPath),wildcard($(BuildPath)))
a := $(shell mkdir -p $(BuildPath))
endif
ifneq ($(ObjectPath),wildcard($(ObjectPath)))
a := $(shell mkdir -p $(ObjectPath))
endif
ifneq ($(DependFilePath),wildcard($(DependFilePath)))
a := $(shell mkdir -p $(DependFilePath))
endif
################################################################################
## set flags for compile and link
################################################################################

## set the include path for compile flags.
SourceIncludePath = $(foreach dir,$(SourcePath),-I$(dir)) \
					-I./	\
                    -I../          \
                    -I../MEMORY/include/    \
                    -I../VE/include/        \
                    -I../CODEC/VIDEO/DECODER/include/ \
                    -I../CODEC/AUDIO/DECODER/include/ \
                    -I../CODEC/SUBTITLE/DECODER/include/ 

## set compile flags
CompileFlags = $(CONFIG_FOR_COMPILE) $(SourceIncludePath) -mfpu=neon -O2 -fPIC -ldl -march=armv7-a

## set link flags
LoadFlags = $(CONFIG_FOR_LINK) -ldl -shared


################################################################################
## make commands, all/clean/cleanall
################################################################################

## define commands for make, sush as all, clean
.PHONY: all clean cleantarget cleanall
all:$(Target)

clean:
	-rm -rf $(ObjCommonPath)/*
	-rm -f $(Target)

cleanall: clean
	-rm -rf $(DepCommonPath)/*
	-rm -rf $(BuildPath)



################################################################################
## define target dependencies.
################################################################################
## compile source files to object files.
$(ObjectFiles):$(ObjCommonPath)/%.o:%.cpp
	@$(CPP) $(CompileFlags) -o $@ -c $<
	@echo "CC    $<"

## link object files to the target share library.
$(Target):$(ObjectFiles)
	@$(CPP) -o $@ $^ $(LoadFlags)
	@echo "LD    $@"

## set rules to generate .d files.
$(DepCommonPath)/%.d:%.cpp
	@set -e; rm -f $@; \
	$(CPP) -MM $(CompileFlags) $< > $@.$$$$; \
	sed 's,.*\.o[:]*,$(ObjCommonPath)\/$*\.o $@: ,g' < $@.$$$$ > $@; \
	rm -f $@.$$$$
	@echo "updating    $@"

## include the .d files to set dependency rules.
ifneq ($(MAKECMDGOALS),clean)
ifneq ($(MAKECMDGOALS),cleantarget)
ifneq ($(MAKECMDGOALS),cleanall)
-include $(DependFiles)
endif
endif
endif


