#include <CdxStream.h>
#include <CdxDebug.h>
#include <stdint.h>
#if 1
#include <CdxAtomic.h>
#include <CdxMemory.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#endif

#define BDMV_STREAM_SCHEME "bdmv://"

static char *bdmvProbeData = "aw.bdmv.";

/*fmt: "bdmv://xxx" */
struct BdmvStreamImplS
{
    CdxStreamT base;
    CdxStreamProbeDataT probeData;
    
    struct IoOperationS *ioOps;
    void *ioOpsHdr;
    
    cdx_char *root;
    int state;
};

// TODO: implement sys io ops 
static struct IoOperationS sysIoOps =
{
    NULL, NULL, NULL, NULL, NULL
/*
    .openDir = opendir,
    .readDir = readdir,
    .closeDir = closedir,

    .openFile = open,

    .accessFile = access
*/
};

int onExtIoOperation(struct BdmvStreamImplS *impl, struct ExtIoctlParamS *param)
{
    int ret = 0;
    switch (param->cmd)
    {
    case EXT_IO_OPERATION_OPENDIR:
    {
        void *dirId = NULL;
        char fullPath[4096] = {0};
        snprintf(fullPath, 4096, "%s/BDMV/%s", impl->root + 7, param->inPath);
        ret = impl->ioOps->openDir(impl->ioOpsHdr, fullPath, &dirId);

        param->outRet = ret;
        param->outHdr = dirId;
        
        break;
    }
    case EXT_IO_OPERATION_READDIR:
    {
        ret = impl->ioOps->readDir(impl->ioOpsHdr, param->inHdr, param->inBuf, param->inBufLen);
        
        param->outRet = ret;
        break;
    }
    case EXT_IO_OPERATION_CLOSEDIR:
    {
        ret = impl->ioOps->closeDir(impl->ioOpsHdr, param->inHdr);

        param->outRet = ret;
        break;
    }    
    case EXT_IO_OPERATION_ACCESS:
    {
        char fullPath[4096] = {0};
        snprintf(fullPath, 4096, "%s/BDMV/%s", impl->root + 7, param->inPath);
        ret = impl->ioOps->accessFile(impl->ioOpsHdr, fullPath, R_OK);

        param->outRet = ret;
        break;
    }
    case EXT_IO_OPERATION_OPENFILE:
    {
        int fd = -1;
        char fullPath[4096] = {0};
        snprintf(fullPath, 4096, "%s/BDMV/%s", impl->root + 7, param->inPath);

        fd = impl->ioOps->openFile(impl->ioOpsHdr, fullPath, 0);
        
        ret = (fd == -1) ? -1 : 0;
        
        param->outRet = ret;
        param->outHdr = (void *)(uintptr_t)fd;
        if (ret != 0)
        {
            CDX_LOGE("open file failure, '%s'", fullPath);
        }
        break;
    }
    case EXT_IO_OPERATION_READFILE:
    {
        int fd = (int)((uintptr_t)param->inHdr);
        ret = read(fd, param->inBuf, param->inBufLen);

        param->outRet = ret;

        ret = (ret == -1) ? -1 : 0;
        break;
    }
    case EXT_IO_OPERATION_CLOSEFILE:
    {
        int fd = (int)((uintptr_t)param->inHdr);
        
        ret = close(fd);

        param->outRet = ret;
        
        break;
    }
    default:
        break;
    }
    
    return ret;
}

static CdxStreamProbeDataT *__BdmvStreamGetProbeData(CdxStreamT *stream)
{
    struct BdmvStreamImplS *impl;

    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);
    return &impl->probeData;
}

static cdx_int32 __BdmvStreamRead(CdxStreamT *stream, cdx_void *buf, cdx_uint32 len)
{
	CDX_UNUSE(stream);
	CDX_UNUSE(buf);
	CDX_UNUSE(len);

    return -1;
}

static cdx_int32 __BdmvStreamClose(CdxStreamT *stream)
{
    struct BdmvStreamImplS *impl;

    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);

    free(impl->root);
    free(impl);
    return 0;
}

static cdx_int32 __BdmvStreamGetIoState(CdxStreamT *stream)
{
    struct BdmvStreamImplS *impl;

    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);
    
    return impl->state;
}

static cdx_uint32 __BdmvStreamAttribute(CdxStreamT *stream)
{
	CDX_UNUSE(stream);
    return 0;
}

static cdx_int32 __BdmvStreamControl(CdxStreamT *stream, cdx_int32 cmd, cdx_void *param)
{
    struct BdmvStreamImplS *impl;
    int ret = 0;
    
    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);
    switch (cmd)
    {
    case STREAM_CMD_EXT_IO_OPERATION:
        ret = onExtIoOperation(impl, param);
        break;
    default:
        CDX_LOGW("not support cmd(%d), who call me:", cmd);
//        CdxDumpThreadStack(gettid());
        ret = 0;
        break;
    }
    
    return ret;
}

static cdx_int64 __BdmvStreamTell(CdxStreamT *stream)
{
	CDX_UNUSE(stream);
    return 8LL;
}

static cdx_bool __BdmvStreamEos(CdxStreamT *stream)
{
	CDX_UNUSE(stream);
    return CDX_TRUE;
}

static cdx_int64 __BdmvStreamSize(CdxStreamT *stream)
{
	CDX_UNUSE(stream);
    return 8;
}

static cdx_int32 __BdmvStreamGetMetaData(CdxStreamT *stream, const cdx_char *key, cdx_void **pVal)
{
    struct BdmvStreamImplS *impl;
    
    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);
    if(strcmp(key, "uri") == 0)
    {
        *pVal = impl->root;
        return 0;
    }
    *pVal = NULL;
    return -1;
}

cdx_int32 __BdmvStreamConnect(CdxStreamT *stream)
{
    struct BdmvStreamImplS *impl;
    
    impl = CdxContainerOf(stream, struct BdmvStreamImplS, base);
    impl->state = CDX_IO_STATE_OK; /* just set state OK, enought */
    
	return 0;
}

static struct CdxStreamOpsS bdmvStreamOps =
{
	.connect = __BdmvStreamConnect,
    .getProbeData = __BdmvStreamGetProbeData,
    .read = __BdmvStreamRead,
    .write = NULL,
    .close = __BdmvStreamClose,
    .getIOState = __BdmvStreamGetIoState,
    .attribute = __BdmvStreamAttribute,
    .control = __BdmvStreamControl,
    .getMetaData = __BdmvStreamGetMetaData,
    .seek = NULL,
    .seekToTime = NULL,
    .eos = __BdmvStreamEos,
    .tell = __BdmvStreamTell,
    .size = __BdmvStreamSize,
};

static CdxStreamT *__BdmvStreamCreate(CdxDataSourceT *source)
{
    struct BdmvStreamImplS *impl;
    //cdx_int32 ret;

    CDX_LOGI("bdmv stream...");    
    impl = malloc(sizeof(*impl));
    CDX_FORCE_CHECK(impl);
    memset(impl, 0x00, sizeof(*impl));
    
    impl->base.ops = &bdmvStreamOps;
    impl->root = strdup(source->uri);

    CDX_CHECK(strncmp(impl->root, BDMV_STREAM_SCHEME, 7) == 0);

    if (source->extraData)
    {
        struct BdmvExtraDataS *bdmvED = source->extraData;
        impl->ioOps = bdmvED->ioCb;
        impl->ioOpsHdr = bdmvED->cbHdr;
    }

    if (!impl->ioOps)
    {
        impl->ioOps = &sysIoOps;
        impl->ioOpsHdr = NULL;
    }
        
    impl->state = CDX_IO_STATE_INVALID;
    impl->probeData.buf = bdmvProbeData;
    impl->probeData.len = 8;
    
    return &impl->base;
}

CdxStreamCreatorT bdmvStreamCtor =
{
    .create = __BdmvStreamCreate
};

