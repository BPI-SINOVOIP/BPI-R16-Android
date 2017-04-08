/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ScreenRecord"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <binder/IPCThreadState.h>
#include <utils/Errors.h>
#include <utils/Thread.h>
#include <utils/Timers.h>

#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/ISurfaceComposer.h>
#include <ui/DisplayInfo.h>
#include <media/openmax/OMX_IVCommon.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaMuxer.h>
#include <media/ICrypto.h>
#include <media/mediarecorder.h>
#include <media/stagefright/AudioSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/Thread.h>
#include <pthread.h>

#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <signal.h>
#include <getopt.h>
#include <sys/wait.h>

using namespace android;

static const uint32_t kMinBitRate = 100000;         // 0.1Mbps
static const uint32_t kMaxBitRate = 100 * 1000000;  // 100Mbps
static const uint32_t kMaxTimeLimitSec = 86400;     // 24 hours
static const uint32_t kFallbackWidth = 1280;        // 720p
static const uint32_t kFallbackHeight = 720;

// Command-line parameters.
static bool gVerbose = false;               // chatty on stdout
static bool gRotate = false;                // rotate 90 degrees
static bool gSizeSpecified = false;         // was size explicitly requested?
static uint32_t gVideoWidth = 0;            // default width+height
static uint32_t gVideoHeight = 0;
static uint32_t gBitRate = 4000000;         // 4Mbps
static uint32_t gTimeLimitSec = kMaxTimeLimitSec;

// Set by signal handler to stop recording.
static bool gStopRequested;

// Previous signal handler state, restored after first hit.
static struct sigaction gOrigSigactionINT;
static struct sigaction gOrigSigactionHUP;

static pthread_mutex_t mMutex = PTHREAD_MUTEX_INITIALIZER;
static bool gAudioEnable = false;
static audio_source_t gAudioSourceType;
static int gEncoderFlag;

#define SAVE_AUDIO_PCM   0
#if SAVE_AUDIO_PCM
const char*  fpStreamPath = "/data/camera/data.pcm";
static FILE* fpStream = NULL;
#endif

/*
 * Catch keyboard interrupt signals.  On receipt, the "stop requested"
 * flag is raised, and the original handler is restored (so that, if
 * we get stuck finishing, a second Ctrl-C will kill the process).
 */
static void signalCatcher(int signum)
{
    gStopRequested = true;
    switch (signum) {
    case SIGINT:
    case SIGHUP:
        sigaction(SIGINT, &gOrigSigactionINT, NULL);
        sigaction(SIGHUP, &gOrigSigactionHUP, NULL);
        break;
    default:
        abort();
        break;
    }
}

/*
 * Configures signal handlers.  The previous handlers are saved.
 *
 * If the command is run from an interactive adb shell, we get SIGINT
 * when Ctrl-C is hit.  If we're run from the host, the local adb process
 * gets the signal, and we get a SIGHUP when the terminal disconnects.
 */
static status_t configureSignals()
{
    struct sigaction act;
    memset(&act, 0, sizeof(act));
    act.sa_handler = signalCatcher;
    if (sigaction(SIGINT, &act, &gOrigSigactionINT) != 0) {
        status_t err = -errno;
        fprintf(stderr, "Unable to configure SIGINT handler: %s\n",
                strerror(errno));
        return err;
    }
    if (sigaction(SIGHUP, &act, &gOrigSigactionHUP) != 0) {
        status_t err = -errno;
        fprintf(stderr, "Unable to configure SIGHUP handler: %s\n",
                strerror(errno));
        return err;
    }
    return NO_ERROR;
}

/*
 * Returns "true" if the device is rotated 90 degrees.
 */
static bool isDeviceRotated(int orientation) {
    return orientation != DISPLAY_ORIENTATION_0 &&
            orientation != DISPLAY_ORIENTATION_180;
}

/*
 * Configures and starts the MediaCodec encoder.  Obtains an input surface
 * from the codec.
 */
static status_t prepareEncoder(float displayFps, sp<MediaCodec>* pCodec,
        sp<IGraphicBufferProducer>* pBufferProducer) {
    status_t err;

    if (gVerbose) {
        printf("Configuring recorder for %dx%d video at %.2fMbps\n",
                gVideoWidth, gVideoHeight, gBitRate / 1000000.0);
    }

    sp<AMessage> format = new AMessage;
    format->setInt32("width", gVideoWidth);
    format->setInt32("height", gVideoHeight);
    format->setString("mime", "video/avc");
    format->setInt32("color-format", OMX_COLOR_FormatAndroidOpaque);
    format->setInt32("bitrate", gBitRate);
    format->setFloat("frame-rate", displayFps);
    format->setInt32("i-frame-interval", 10);

    sp<ALooper> looper = new ALooper;
    looper->setName("screenrecord_looper");
    looper->start();
    ALOGV("Creating codec");
    sp<MediaCodec> codec = MediaCodec::CreateByType(looper, "video/avc", true);
    if (codec == NULL) {
        fprintf(stderr, "ERROR: unable to create video/avc codec instance\n");
        return UNKNOWN_ERROR;
    }
    err = codec->configure(format, NULL, NULL,
            MediaCodec::CONFIGURE_FLAG_ENCODE);
    if (err != NO_ERROR) {
        codec->release();
        codec.clear();

        fprintf(stderr, "ERROR: unable to configure codec (err=%d)\n", err);
        return err;
    }

    ALOGV("Creating buffer producer");
    sp<IGraphicBufferProducer> bufferProducer;
    err = codec->createInputSurface(&bufferProducer);
    if (err != NO_ERROR) {
        codec->release();
        codec.clear();

        fprintf(stderr,
            "ERROR: unable to create encoder input surface (err=%d)\n", err);
        return err;
    }

    ALOGV("Starting codec");
    err = codec->start();
    if (err != NO_ERROR) {
        codec->release();
        codec.clear();

        fprintf(stderr, "ERROR: unable to start codec (err=%d)\n", err);
        return err;
    }

    ALOGV("Codec prepared");
    *pCodec = codec;
    *pBufferProducer = bufferProducer;
    return 0;
}

/*
 * Configures the virtual display.  When this completes, virtual display
 * frames will start being sent to the encoder's surface.
 */
static status_t prepareVirtualDisplay(const DisplayInfo& mainDpyInfo,
        const sp<IGraphicBufferProducer>& bufferProducer,
        sp<IBinder>* pDisplayHandle) {
    status_t err;

    // Set the region of the layer stack we're interested in, which in our
    // case is "all of it".  If the app is rotated (so that the width of the
    // app is based on the height of the display), reverse width/height.
    bool deviceRotated = isDeviceRotated(mainDpyInfo.orientation);
    uint32_t sourceWidth, sourceHeight;
    if (!deviceRotated) {
        sourceWidth = mainDpyInfo.w;
        sourceHeight = mainDpyInfo.h;
    } else {
        ALOGV("using rotated width/height");
        sourceHeight = mainDpyInfo.w;
        sourceWidth = mainDpyInfo.h;
    }
    Rect layerStackRect(sourceWidth, sourceHeight);

    // We need to preserve the aspect ratio of the display.
    float displayAspect = (float) sourceHeight / (float) sourceWidth;


    // Set the way we map the output onto the display surface (which will
    // be e.g. 1280x720 for a 720p video).  The rect is interpreted
    // post-rotation, so if the display is rotated 90 degrees we need to
    // "pre-rotate" it by flipping width/height, so that the orientation
    // adjustment changes it back.
    //
    // We might want to encode a portrait display as landscape to use more
    // of the screen real estate.  (If players respect a 90-degree rotation
    // hint, we can essentially get a 720x1280 video instead of 1280x720.)
    // In that case, we swap the configured video width/height and then
    // supply a rotation value to the display projection.
    uint32_t videoWidth, videoHeight;
    uint32_t outWidth, outHeight;
    if (!gRotate) {
        videoWidth = gVideoWidth;
        videoHeight = gVideoHeight;
    } else {
        videoWidth = gVideoHeight;
        videoHeight = gVideoWidth;
    }
    if (videoHeight > (uint32_t)(videoWidth * displayAspect)) {
        // limited by narrow width; reduce height
        outWidth = videoWidth;
        outHeight = (uint32_t)(videoWidth * displayAspect);
    } else {
        // limited by short height; restrict width
        outHeight = videoHeight;
        outWidth = (uint32_t)(videoHeight / displayAspect);
    }
    uint32_t offX, offY;
    offX = (videoWidth - outWidth) / 2;
    offY = (videoHeight - outHeight) / 2;
    Rect displayRect(offX, offY, offX + outWidth, offY + outHeight);

    if (gVerbose) {
        if (gRotate) {
            printf("Rotated content area is %ux%u at offset x=%d y=%d\n",
                    outHeight, outWidth, offY, offX);
        } else {
            printf("Content area is %ux%u at offset x=%d y=%d\n",
                    outWidth, outHeight, offX, offY);
        }
    }


    sp<IBinder> dpy = SurfaceComposerClient::createDisplay(
            String8("ScreenRecorder"), false /* secure */);

    SurfaceComposerClient::openGlobalTransaction();
    SurfaceComposerClient::setDisplaySurface(dpy, bufferProducer);
    SurfaceComposerClient::setDisplayProjection(dpy,
            gRotate ? DISPLAY_ORIENTATION_90 : DISPLAY_ORIENTATION_0,
            layerStackRect, displayRect);
    SurfaceComposerClient::setDisplayLayerStack(dpy, 0);    // default stack
    SurfaceComposerClient::closeGlobalTransaction();

    *pDisplayHandle = dpy;

    return NO_ERROR;
}

/*
 * Configures and starts the MediaCodec encoder.  Obtains an input surface
 * from the codec.
 */
static status_t prepareAudioEncoder(sp<MediaCodec>* pCodec,
        sp<AudioSource>* pSource) {
    status_t err;

    int32_t bitrate = 128000;

    sp<AudioSource> audioSource;
    if (gAudioSourceType == AUDIO_SOURCE_REMOTE_SUBMIX) { // remote
        audioSource = new AudioSource(
                AUDIO_SOURCE_REMOTE_SUBMIX, /* mAudioSource */
                48000 /* sampleRate */,
                2 /* channelCount */);
    } else if (gAudioSourceType == AUDIO_SOURCE_MIC) { // mic
        audioSource = new AudioSource(
                AUDIO_SOURCE_MIC,/* mAudioSource */
                44100 /* sampleRate */,
                1 /* channelCount */);
    } else {
        audioSource = new AudioSource(
                AUDIO_SOURCE_REMOTE_SUBMIX, /* mAudioSource */
                48000 /* sampleRate */,
                2 /* channelCount */);
    }

    if ((err = audioSource->initCheck()) != NO_ERROR) {
        return err;
    }

    sp<AMessage> format = new AMessage;

    err = convertMetaDataToMessage(audioSource->getFormat(), &format);
    CHECK_EQ(err, (status_t)OK);

    format->setString("mime", MEDIA_MIMETYPE_AUDIO_AAC); // audio/mp4a-latm
    format->setInt32("bitrate", bitrate);

    sp<ALooper> looper = new ALooper;
    looper->setName("screenrecord_audio_looper");
    looper->start(
                false /* runOnCallingThread */,
                false /* canCallJava */,
                PRIORITY_AUDIO);

    ALOGV("Creating audio codec");
    sp<MediaCodec> codec = MediaCodec::CreateByType(looper, MEDIA_MIMETYPE_AUDIO_AAC, true);
    if (codec == NULL) {
        fprintf(stderr, "ERROR: unable to create audio/mp4a-latm codec instance\n");
        return UNKNOWN_ERROR;
    }

    err = codec->configure(format, NULL, NULL,
            MediaCodec::CONFIGURE_FLAG_ENCODE);
    if (err != NO_ERROR) {
        codec->release();
        codec.clear();

        fprintf(stderr, "ERROR: unable to configure audio codec (err=%d)\n", err);
        return err;
    }

    ALOGV("Starting audio codec");
    err = codec->start();
    if (err != NO_ERROR) {
        codec->release();
        codec.clear();

        fprintf(stderr, "ERROR: unable to start audio codec (err=%d)\n", err);
        return err;
    }

    ALOGV("Audio Codec prepared");
    *pCodec = codec;
    *pSource = audioSource;

    return NO_ERROR;
}

static status_t prepareAudioBuffers(const sp<MediaCodec>& encoder, const sp<AudioSource>& audioSource) {
    status_t err;
    MediaBuffer *mbuf;
    Vector<sp<ABuffer> > buffers;
    List<size_t> gAudioAvailInputIndices;
    List<sp<ABuffer> > gAudioInputBufferQueue;

    err = encoder->getInputBuffers(&buffers);
    if (err != NO_ERROR) {
        fprintf(stderr, "Unable to get input audio buffers (err=%d)\n", err);
        return err;
    }

    gAudioInputBufferQueue.push_back(NULL);
    gAudioAvailInputIndices.push_back(0);

    // This atrocity causes AudioSource to deliver absolute
    // systemTime() based timestamps (off by 1 us).
    sp<MetaData> params = new MetaData;
    params->setInt64(kKeyTime, 1ll);

#if SAVE_AUDIO_PCM

    fpStream = fopen(fpStreamPath,"wb");
    if (fpStream < 0) {
        ALOGD("fopen fpStreamPath failed");
    }

#endif

    err = audioSource->start(params.get());

    if (err != NO_ERROR) {
        fprintf(stderr, "source failed to start (err=%d)\n", err);
        return err;
    }

    for(;;) {
        err = audioSource->read(&mbuf);

        if (err != NO_ERROR) {
            ALOGE("error %d reading stream.", err);
        } else {
            int64_t timeUs;
            CHECK(mbuf->meta_data()->findInt64(kKeyTime, &timeUs));

            sp<ABuffer> accessUnit = new ABuffer(mbuf->range_length());

            memcpy(accessUnit->data(),
                    (const uint8_t *)mbuf->data() + mbuf->range_offset(),
                    mbuf->range_length());

            accessUnit->meta()->setInt64("timeUs", timeUs);

            gAudioInputBufferQueue.push_back(accessUnit);

            #if SAVE_AUDIO_PCM

                if (mbuf->range_length() > 0) {
                    fwrite(((char *)mbuf->data() + mbuf->range_offset()), 1, mbuf->range_length(), fpStream);
                }

            #endif

            mbuf->release();
            mbuf = NULL;
        }

        for (;;) {
            size_t bufferIndex;
            err = encoder->dequeueInputBuffer(&bufferIndex);

            if (err != OK) {
                break;
            }

            gAudioAvailInputIndices.push_back(bufferIndex);
        }

        int64_t timeUs = 0ll;
        uint32_t flags = 0;
        while (!gAudioInputBufferQueue.empty()
                && !gAudioAvailInputIndices.empty()) {

            sp<ABuffer> buffer = *gAudioInputBufferQueue.begin();
            gAudioInputBufferQueue.erase(gAudioInputBufferQueue.begin());

            size_t bufferIndex = *gAudioAvailInputIndices.begin();
            gAudioAvailInputIndices.erase(gAudioAvailInputIndices.begin());

            timeUs = 0ll;
            flags = 0;

            if (buffer != NULL) {

                CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

                memcpy(buffers.itemAt(bufferIndex)->data(),
                       buffer->data(),
                       buffer->size());
            } else {
                ALOGD("feedAudioEncoderInputBuffers: buffer is NULL");
                //flags = MediaCodec::BUFFER_FLAG_EOS;
            }

            err = encoder->queueInputBuffer(
                    bufferIndex, 0, (buffer == NULL) ? 0 : buffer->size(),
                    timeUs, flags);

            if (err != OK) {
                ALOGD("feedAudioEncoderInputBuffers err %d", err);
            }
        }
    }
}

class AudioInputThread : public Thread {
    sp<MediaCodec> mEncoder;
    sp<AudioSource> mSource;
public:
    AudioInputThread(sp<MediaCodec>& encoder, sp<AudioSource>& source)
        : mEncoder(encoder),
          mSource(source) {
    }

    bool threadLoop() {
        status_t err = prepareAudioBuffers(mEncoder, mSource);
        if (err != NO_ERROR) {
            mEncoder->release();
            mEncoder.clear();

            return err;
        }
        return false;
    }
};

/*
 * Runs the MediaCodec encoder, sending the output to the MediaMuxer.  The
 * input frames are coming from the virtual display as fast as SurfaceFlinger
 * wants to send them.
 *
 * The muxer must *not* have been started before calling.
 */
static status_t runEncoder(const sp<MediaCodec>& encoder,
        const sp<MediaMuxer>& muxer) {
    static int kTimeout = 250000;   // be responsive on signal
    status_t err;
    ssize_t trackIdx = -1;
    uint32_t debugNumFrames = 0;
    int64_t startWhenNsec = systemTime(CLOCK_MONOTONIC);
    int64_t endWhenNsec = startWhenNsec + seconds_to_nanoseconds(gTimeLimitSec);

    Vector<sp<ABuffer> > buffers;
    err = encoder->getOutputBuffers(&buffers);
    if (err != NO_ERROR) {
        fprintf(stderr, "Unable to get output buffers (err=%d)\n", err);
        return err;
    }

    // This is set by the signal handler.
    gStopRequested = false;

    // Run until we're signaled.
    while (!gStopRequested) {
        size_t bufIndex, offset, size;
        int64_t ptsUsec;
        uint32_t flags;

        if (systemTime(CLOCK_MONOTONIC) > endWhenNsec) {
            if (gVerbose) {
                printf("Time limit reached\n");
            }
            break;
        }

        ALOGV("Calling dequeueOutputBuffer");
        err = encoder->dequeueOutputBuffer(&bufIndex, &offset, &size, &ptsUsec,
                &flags, kTimeout);
        ALOGV("dequeueOutputBuffer returned %d", err);
        switch (err) {
        case NO_ERROR:
            // got a buffer
            if ((flags & MediaCodec::BUFFER_FLAG_CODECCONFIG) != 0) {
                // ignore this -- we passed the CSD into MediaMuxer when
                // we got the format change notification
                ALOGV("Got codec config buffer (%u bytes); ignoring", size);
                size = 0;
            }
            if (size != 0) {
                ALOGV("Got data in buffer %d, size=%d, pts=%lld",
                        bufIndex, size, ptsUsec);
                CHECK(trackIdx != -1);

                // If the virtual display isn't providing us with timestamps,
                // use the current time.
                if (ptsUsec == 0) {
                    ptsUsec = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;
                }

                // The MediaMuxer docs are unclear, but it appears that we
                // need to pass either the full set of BufferInfo flags, or
                // (flags & BUFFER_FLAG_SYNCFRAME).
                if (gAudioEnable) {
                pthread_mutex_lock (&mMutex);
                if (gEncoderFlag == 2)
                    err = muxer->writeSampleData(buffers[bufIndex], trackIdx,
                            ptsUsec, flags);
                pthread_mutex_unlock (&mMutex);
                } else {
                err = muxer->writeSampleData(buffers[bufIndex], trackIdx,
                        ptsUsec, flags);
                }
                if (err != NO_ERROR) {
                    fprintf(stderr, "Failed writing data to muxer (err=%d)\n",
                            err);
                    return err;
                }
                debugNumFrames++;
            }
            err = encoder->releaseOutputBuffer(bufIndex);
            if (err != NO_ERROR) {
                fprintf(stderr, "Unable to release output buffer (err=%d)\n",
                        err);
                return err;
            }
            if ((flags & MediaCodec::BUFFER_FLAG_EOS) != 0) {
                // Not expecting EOS from SurfaceFlinger.  Go with it.
                ALOGD("Received end-of-stream");
                gStopRequested = false;
            }
            break;
        case -EAGAIN:                       // INFO_TRY_AGAIN_LATER
            ALOGV("Got -EAGAIN, looping");
            break;
        case INFO_FORMAT_CHANGED:           // INFO_OUTPUT_FORMAT_CHANGED
            {
                // format includes CSD, which we must provide to muxer
                ALOGV("Encoder format changed");
                sp<AMessage> newFormat;
                encoder->getOutputFormat(&newFormat);
                trackIdx = muxer->addTrack(newFormat);
                ALOGV("Starting muxer");
                if (gAudioEnable) {
                gEncoderFlag++;
                if (gEncoderFlag < 2)
                    break;
                }
                err = muxer->start();
                if (err != NO_ERROR) {
                    fprintf(stderr, "Unable to start muxer (err=%d)\n", err);
                    return err;
                }
            }
            break;
        case INFO_OUTPUT_BUFFERS_CHANGED:   // INFO_OUTPUT_BUFFERS_CHANGED
            // not expected for an encoder; handle it anyway
            ALOGV("Encoder buffers changed");
            err = encoder->getOutputBuffers(&buffers);
            if (err != NO_ERROR) {
                fprintf(stderr,
                        "Unable to get new output buffers (err=%d)\n", err);
                return err;
            }
            break;
        case INVALID_OPERATION:
            fprintf(stderr, "Request for encoder buffer failed\n");
            return err;
        default:
            fprintf(stderr,
                    "Got weird result %d from dequeueOutputBuffer\n", err);
            return err;
        }
    }

    ALOGV("Encoder stopping (req=%d)", gStopRequested);
    if (gVerbose) {
        printf("Encoder stopping; recorded %u frames in %lld seconds\n",
                debugNumFrames,
                nanoseconds_to_seconds(systemTime(CLOCK_MONOTONIC) - startWhenNsec));
    }
    return NO_ERROR;
}

class MuxerThread : public Thread {
    sp<MediaCodec> mEncoder;
    sp<MediaMuxer> mMuxer;
public:
    MuxerThread(sp<MediaCodec>& encoder, sp<MediaMuxer>& muxer)
        : mEncoder(encoder),
          mMuxer(muxer) {
    }

    virtual ~MuxerThread() {
    }

    bool threadLoop() {
        status_t err = runEncoder(mEncoder, mMuxer);
        if (err != NO_ERROR) {
            mEncoder->release();
            mEncoder.clear();

            return err;
        }
        return false;
    }
};

/*
 * Main "do work" method.
 *
 * Configures codec, muxer, and virtual display, then starts moving bits
 * around.
 */
static status_t recordScreen(const char* fileName) {
    status_t err;

    // Configure signal handler.
    err = configureSignals();
    if (err != NO_ERROR) return err;

    // Start Binder thread pool.  MediaCodec needs to be able to receive
    // messages from mediaserver.
    sp<ProcessState> self = ProcessState::self();
    self->startThreadPool();

    // Get main display parameters.
    sp<IBinder> mainDpy = SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain);
    DisplayInfo mainDpyInfo;
    err = SurfaceComposerClient::getDisplayInfo(mainDpy, &mainDpyInfo);
    if (err != NO_ERROR) {
        fprintf(stderr, "ERROR: unable to get display characteristics\n");
        return err;
    }
    if (gVerbose) {
        printf("Main display is %dx%d @%.2ffps (orientation=%u)\n",
                mainDpyInfo.w, mainDpyInfo.h, mainDpyInfo.fps,
                mainDpyInfo.orientation);
    }

    bool rotated = isDeviceRotated(mainDpyInfo.orientation);
    if (gVideoWidth == 0) {
        gVideoWidth = rotated ? mainDpyInfo.h : mainDpyInfo.w;
    }
    if (gVideoHeight == 0) {
        gVideoHeight = rotated ? mainDpyInfo.w : mainDpyInfo.h;
    }

    // Configure and start the encoder.
    sp<MediaCodec> encoder;
    sp<IGraphicBufferProducer> bufferProducer;
    err = prepareEncoder(mainDpyInfo.fps, &encoder, &bufferProducer);

    if (err != NO_ERROR && !gSizeSpecified) {
        // fallback is defined for landscape; swap if we're in portrait
        bool needSwap = gVideoWidth < gVideoHeight;
        uint32_t newWidth = needSwap ? kFallbackHeight : kFallbackWidth;
        uint32_t newHeight = needSwap ? kFallbackWidth : kFallbackHeight;
        if (gVideoWidth != newWidth && gVideoHeight != newHeight) {
            ALOGV("Retrying with 720p");
            fprintf(stderr, "WARNING: failed at %dx%d, retrying at %dx%d\n",
                    gVideoWidth, gVideoHeight, newWidth, newHeight);
            gVideoWidth = newWidth;
            gVideoHeight = newHeight;
            err = prepareEncoder(mainDpyInfo.fps, &encoder, &bufferProducer);
        }
    }
    if (err != NO_ERROR) {
        return err;
    }

    // Configure virtual display.
    sp<IBinder> dpy;
    err = prepareVirtualDisplay(mainDpyInfo, bufferProducer, &dpy);
    if (err != NO_ERROR) {
        encoder->release();
        encoder.clear();

        return err;
    }

    sp<MediaCodec> audioEncoder;
    sp<AudioSource> audioSource;
    sp<AudioInputThread> audioInputThread;
    if (gAudioEnable) {
        // Configure and start the audio encoder.
        err = prepareAudioEncoder(&audioEncoder, &audioSource);

        if (err != NO_ERROR) {
            audioEncoder->release();
            audioEncoder.clear();

            return err;
        }

        audioInputThread = new AudioInputThread(audioEncoder, audioSource);
        audioInputThread->run();
    }

    // Configure, but do not start, muxer.
    sp<MediaMuxer> muxer = new MediaMuxer(fileName,
            MediaMuxer::OUTPUT_FORMAT_MPEG_4);
    if (gRotate) {
        muxer->setOrientationHint(90);
    }

    sp<MuxerThread> videoThread;
    sp<MuxerThread> audioThread;
    if (gAudioEnable) {
        gEncoderFlag = 0;
        videoThread = new MuxerThread(encoder, muxer);
        videoThread->run();
        audioThread = new MuxerThread(audioEncoder, muxer);
        audioThread->run();
        while(videoThread->isRunning() || audioThread->isRunning()) {
            sleep(0.1);
        }
    } else {
    // Main encoder loop.
    err = runEncoder(encoder, muxer);
    if (err != NO_ERROR) {
        encoder->release();
        encoder.clear();

        return err;
    }
    }

    if (gVerbose) {
        printf("Stopping encoder and muxer\n");
    }

    // Shut everything down, starting with the producer side.
    bufferProducer = NULL;
    SurfaceComposerClient::destroyDisplay(dpy);

    if (gAudioEnable) {
        audioInputThread->requestExit();
        videoThread->requestExit();
        audioThread->requestExit();
    }
    encoder->stop();
    if (gAudioEnable) {
        audioEncoder->stop();
        audioSource->stop();
    }
    muxer->stop();
    encoder->release();
    if (gAudioEnable) {
        audioEncoder->release();
    }

    return 0;
}

/*
 * Sends a broadcast to the media scanner to tell it about the new video.
 *
 * This is optional, but nice to have.
 */
static status_t notifyMediaScanner(const char* fileName) {
    pid_t pid = fork();
    if (pid < 0) {
        int err = errno;
        ALOGW("fork() failed: %s", strerror(err));
        return -err;
    } else if (pid > 0) {
        // parent; wait for the child, mostly to make the verbose-mode output
        // look right, but also to check for and log failures
        int status;
        pid_t actualPid = TEMP_FAILURE_RETRY(waitpid(pid, &status, 0));
        if (actualPid != pid) {
            ALOGW("waitpid() returned %d (errno=%d)", actualPid, errno);
        } else if (status != 0) {
            ALOGW("'am broadcast' exited with status=%d", status);
        } else {
            ALOGV("'am broadcast' exited successfully");
        }
    } else {
        const char* kCommand = "/system/bin/am";

        // child; we're single-threaded, so okay to alloc
        String8 fileUrl("file://");
        fileUrl.append(fileName);
        const char* const argv[] = {
                kCommand,
                "broadcast",
                "-a",
                "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                "-d",
                fileUrl.string(),
                NULL
        };
        if (gVerbose) {
            printf("Executing:");
            for (int i = 0; argv[i] != NULL; i++) {
                printf(" %s", argv[i]);
            }
            putchar('\n');
        } else {
            // non-verbose, suppress 'am' output
            ALOGV("closing stdout/stderr in child");
            int fd = open("/dev/null", O_WRONLY);
            if (fd >= 0) {
                dup2(fd, STDOUT_FILENO);
                dup2(fd, STDERR_FILENO);
                close(fd);
            }
        }
        execv(kCommand, const_cast<char* const*>(argv));
        ALOGE("execv(%s) failed: %s\n", kCommand, strerror(errno));
        exit(1);
    }
    return NO_ERROR;
}

/*
 * Parses a string of the form "1280x720".
 *
 * Returns true on success.
 */
static bool parseWidthHeight(const char* widthHeight, uint32_t* pWidth,
        uint32_t* pHeight) {
    long width, height;
    char* end;

    // Must specify base 10, or "0x0" gets parsed differently.
    width = strtol(widthHeight, &end, 10);
    if (end == widthHeight || *end != 'x' || *(end+1) == '\0') {
        // invalid chars in width, or missing 'x', or missing height
        return false;
    }
    height = strtol(end + 1, &end, 10);
    if (*end != '\0') {
        // invalid chars in height
        return false;
    }

    *pWidth = width;
    *pHeight = height;
    return true;
}

/*
 * Dumps usage on stderr.
 */
static void usage() {
    fprintf(stderr,
        "Usage: screenrecord [options] <filename>\n"
        "\n"
        "Records the device's display to a .mp4 file.\n"
        "\n"
        "Options:\n"
        "--size WIDTHxHEIGHT\n"
        "    Set the video size, e.g. \"1280x720\".  Default is the device's main\n"
        "    display resolution (if supported), 1280x720 if not.  For best results,\n"
        "    use a size supported by the AVC encoder.\n"
        "--bit-rate RATE\n"
        "    Set the video bit rate, in megabits per second.  Default %dMbps.\n"
        "--time-limit TIME\n"
        "    Set the maximum recording time, in seconds.  Default / maximum is %d.\n"
        "--rotate\n"
        "    Rotate the output 90 degrees.\n"
        "--audio\n"
        "    Set the audio source(MIC:1). Default no audio.\n"
        "--verbose\n"
        "    Display interesting information on stdout.\n"
        "--help\n"
        "    Show this message.\n"
        "\n"
        "Recording continues until Ctrl-C is hit or the time limit is reached.\n"
        "\n",
        gBitRate / 1000000, gTimeLimitSec
        );
}

/*
 * Parses args and kicks things off.
 */
int main(int argc, char* const argv[]) {
    static const struct option longOptions[] = {
        { "help",       no_argument,        NULL, 'h' },
        { "verbose",    no_argument,        NULL, 'v' },
        { "size",       required_argument,  NULL, 's' },
        { "bit-rate",   required_argument,  NULL, 'b' },
        { "time-limit", required_argument,  NULL, 't' },
        { "rotate",     no_argument,        NULL, 'r' },
        { "audio",      required_argument,  NULL, 'a' },
        { NULL,         0,                  NULL, 0 }
    };

    while (true) {
        int optionIndex = 0;
        int ic = getopt_long(argc, argv, "", longOptions, &optionIndex);
        if (ic == -1) {
            break;
        }

        switch (ic) {
        case 'h':
            usage();
            return 0;
        case 'v':
            gVerbose = true;
            break;
        case 's':
            if (!parseWidthHeight(optarg, &gVideoWidth, &gVideoHeight)) {
                fprintf(stderr, "Invalid size '%s', must be width x height\n",
                        optarg);
                return 2;
            }
            if (gVideoWidth == 0 || gVideoHeight == 0) {
                fprintf(stderr,
                    "Invalid size %ux%u, width and height may not be zero\n",
                    gVideoWidth, gVideoHeight);
                return 2;
            }
            gSizeSpecified = true;
            break;
        case 'b':
            gBitRate = atoi(optarg);
            if (gBitRate < kMinBitRate || gBitRate > kMaxBitRate) {
                fprintf(stderr,
                        "Bit rate %dbps outside acceptable range [%d,%d]\n",
                        gBitRate, kMinBitRate, kMaxBitRate);
                return 2;
            }
            break;
        case 't':
            gTimeLimitSec = atoi(optarg);
            if (gTimeLimitSec == 0) {
                fprintf(stderr,
                        "Time limit %ds outside acceptable range [1,%d]\n",
                        gTimeLimitSec, kMaxTimeLimitSec);
                return 2;
            }
            break;
        case 'r':
            gRotate = true;
            break;
        case 'a':
            gAudioEnable = true;
            gAudioSourceType = (audio_source_t) atoi(optarg);
            break;
        default:
            if (ic != '?') {
                fprintf(stderr, "getopt_long returned unexpected value 0x%x\n", ic);
            }
            return 2;
        }
    }

    if (optind != argc - 1) {
        fprintf(stderr, "Must specify output file (see --help).\n");
        return 2;
    }

    // MediaMuxer tries to create the file in the constructor, but we don't
    // learn about the failure until muxer.start(), which returns a generic
    // error code without logging anything.  We attempt to create the file
    // now for better diagnostics.
    const char* fileName = argv[optind];
    int fd = open(fileName, O_CREAT | O_RDWR, 0644);
    if (fd < 0) {
        fprintf(stderr, "Unable to open '%s': %s\n", fileName, strerror(errno));
        return 1;
    }
    close(fd);

    status_t err = recordScreen(fileName);
    if (err == NO_ERROR) {
        // Try to notify the media scanner.  Not fatal if this fails.
        notifyMediaScanner(fileName);
    }
    ALOGD(err == NO_ERROR ? "success" : "failed");
    return (int) err;
}
