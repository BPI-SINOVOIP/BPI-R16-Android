
#ifndef LAYER_CONTROL
#define LAYER_CONTROL

#include "player_i.h"
#include "videoDecComponent.h"
#include "vdecoder.h"

typedef void* LayerCtrl;

const int MESSAGE_ID_LAYER_RETURN_BUFFER = 0x31;

const int LAYER_RESULT_USE_OUTSIDE_BUFFER = 0x2;

LayerCtrl* LayerInit(void* pNativeWindow, int bProtectedFlag = 0);

void LayerRelease(LayerCtrl* l, int bKeepPictureOnScreen);

int LayerSetExpectPixelFormat(LayerCtrl* l, enum EPIXELFORMAT ePixelFormat);

int LayerSetPictureSize(LayerCtrl* l, int nWidth, int nHeight);

int LayerSetDisplayRegion(LayerCtrl* l, int nLeftOff, int nTopOff, int nDisplayWidth, int nDisplayHeight);

int LayerSetBufferCount(LayerCtrl* l, int nBufferCount);

int LayerSetVideoWithTwoStreamFlag(LayerCtrl* l, int bVideoWithTwoStreamFlag);

int LayerSetIsSoftDecoderFlag(LayerCtrl* l, int bIsSoftDecoderFlag);

int LayerDequeueBuffer(LayerCtrl* l,VideoPicture** ppPicture, int bInitFlag);

int LayerQueueBuffer(LayerCtrl* l,VideoPicture* ppPicture, int bValid = 1);

int LayerCtrlHideVideo(LayerCtrl* l);

int LayerCtrlShowVideo(LayerCtrl* l);

int LayerCtrlIsVideoShow(LayerCtrl* l);

int LayerCtrlHoldLastPicture(LayerCtrl* l, int bHold);

void LayerResetNativeWindow(LayerCtrl* l,void* pNativeWindow);

int LayerReleaseBuffer(LayerCtrl* l,VideoPicture* pPicture);

VideoPicture* LayerGetPicNode(LayerCtrl* l);

#endif

