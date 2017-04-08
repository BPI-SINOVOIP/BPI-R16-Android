/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.BitmapScreenNail;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;

import com.android.gallery3d.gif.GifDecoderWrapper;
import com.android.gallery3d.gif.GifData;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.SystemClock;

public class SinglePhotoDataAdapter extends TileImageViewAdapter
        implements PhotoPage.Model {

    private static final String TAG = "SinglePhotoDataAdapter";
    private static final int SIZE_BACKUP = 1024;
    private static final int MSG_UPDATE_IMAGE = 1;
    private static final int MSG_RUN_OBJECT = 2;
    
    private static final boolean mIsGifAnimationSupported = true;

    private MediaItem mItem;
    private boolean mHasFullImage;
    private Future<?> mTask;
    private Handler mHandler;

    private PhotoView mPhotoView;
    private ThreadPool mThreadPool;
    private int mLoadingState = LOADING_INIT;
    private BitmapScreenNail mBitmapScreenNail;
	private boolean mIsActive = false;

    public SinglePhotoDataAdapter(
            AbstractGalleryActivity activity, PhotoView view, MediaItem item) {
        mItem = Utils.checkNotNull(item);
        mHasFullImage = (item.getSupportedOperations() &
                MediaItem.SUPPORT_FULL_IMAGE) != 0;

        if (mIsGifAnimationSupported && 
            (item.getSupportedOperations() & 
             MediaItem.SUPPORT_GIF_ANIMATION) != 0) {
            mAnimateGif = true;
        } else {
            mAnimateGif = false;
        }
        Log.i(TAG, "chen mAnimateGif=" + mAnimateGif);

        mPhotoView = Utils.checkNotNull(view);
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            @SuppressWarnings("unchecked")
            public void handleMessage(Message message) {
                //Utils.assertTrue(message.what == MSG_UPDATE_IMAGE);
                Utils.assertTrue(
                        message.what == MSG_RUN_OBJECT ||
                        message.what == MSG_UPDATE_IMAGE);
                //if (mHasFullImage) {
                //    onDecodeLargeComplete((ImageBundle) message.obj);
                //} else {
                //    onDecodeThumbComplete((Future<Bitmap>) message.obj);
                //}
                switch (message.what) {
                    case MSG_UPDATE_IMAGE:
                        if (mHasFullImage) {
                            onDecodeLargeComplete((ImageBundle) message.obj);
                        } else {
                            onDecodeThumbComplete((Future<Bitmap>) message.obj);
                        }
                        break;
                    case MSG_RUN_OBJECT: {
                        ((Runnable) message.obj).run();
                        break;
                    }
                 }
            }
        };
        mThreadPool = activity.getThreadPool();
    }

    private static class ImageBundle {
        public final BitmapRegionDecoder decoder;
        public final Bitmap backupImage;

        public ImageBundle(BitmapRegionDecoder decoder, Bitmap backupImage) {
            this.decoder = decoder;
            this.backupImage = backupImage;
        }
    }

    private FutureListener<BitmapRegionDecoder> mLargeListener =
            new FutureListener<BitmapRegionDecoder>() {
        @Override
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            BitmapRegionDecoder decoder = future.get();
            if (decoder == null) return;
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.computeSampleSize(
                    (float) SIZE_BACKUP / Math.max(width, height));
            Bitmap bitmap = decoder.decodeRegion(new Rect(0, 0, width, height), options);
            mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_UPDATE_IMAGE, new ImageBundle(decoder, bitmap)));
        }
    };

    private FutureListener<Bitmap> mThumbListener =
            new FutureListener<Bitmap>() {
        @Override
        public void onFutureDone(Future<Bitmap> future) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_UPDATE_IMAGE, future));
        }
    };

    @Override
    public boolean isEmpty() {
        return false;
    }

    private void setScreenNail(Bitmap bitmap, int width, int height) {
        mBitmapScreenNail = new BitmapScreenNail(bitmap);
        setScreenNail(mBitmapScreenNail, width, height);
    }

    private void onDecodeLargeComplete(ImageBundle bundle) {
        try {
            setScreenNail(bundle.backupImage,
                    bundle.decoder.getWidth(), bundle.decoder.getHeight());
            setRegionDecoder(bundle.decoder);
            mPhotoView.notifyImageChange(0);
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode large", t);
        }
    }

    private void onDecodeThumbComplete(Future<Bitmap> future) {
        try {
            Bitmap backup = future.get();
            if (backup == null) {
                mLoadingState = LOADING_FAIL;
                return;
            } else {
                mLoadingState = LOADING_COMPLETE;
            }
            setScreenNail(backup, backup.getWidth(), backup.getHeight());
            mPhotoView.notifyImageChange(0);
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode thumb", t);
        }
    }

    @Override
    public void resume() {
		mIsActive = true;
        if (mTask == null) {
            if (mHasFullImage) {
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            } else {
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
            }
        }
        if (mIsGifAnimationSupported && null == mGifTask) {
            if (mAnimateGif) {
                mGifTask = mThreadPool.submit(
                        mItem.requestGifImage(),
                        new GifDecoderListener());
            }
        }
    }

    @Override
    public void pause() {
		mIsActive = false;
        Future<?> task = mTask;
        task.cancel();
        task.waitDone();
        if (task.get() == null) {
            mTask = null;
        }
        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
    }

    @Override
    public void moveTo(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getImageSize(int offset, PhotoView.Size size) {
        if (offset == 0) {
            size.width = mItem.getWidth();
            size.height = mItem.getHeight();
        } else {
            size.width = 0;
            size.height = 0;
        }
    }

    @Override
    public int getImageRotation(int offset) {
        return (offset == 0) ? mItem.getFullImageRotation() : 0;
    }

    @Override
    public ScreenNail getScreenNail(int offset) {
        return (offset == 0) ? getScreenNail() : null;
    }

    @Override
    public void setNeedFullImage(boolean enabled) {
        // currently not necessary.
    }

    @Override
    public boolean isCamera(int offset) {
        return false;
    }

    @Override
    public boolean isPanorama(int offset) {
        return false;
    }

    @Override
    public boolean isStaticCamera(int offset) {
        return false;
    }

    @Override
    public boolean isVideo(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO;
    }

    @Override
    public boolean isDeletable(int offset) {
        return (mItem.getSupportedOperations() & MediaItem.SUPPORT_DELETE) != 0;
    }

    @Override
    public MediaItem getMediaItem(int offset) {
        return offset == 0 ? mItem : null;
    }

    @Override
    public int getCurrentIndex() {
        return 0;
    }

    @Override
    public void setCurrentPhoto(Path path, int indexHint) {
        // ignore
    }

    @Override
    public void setFocusHintDirection(int direction) {
        // ignore
    }

    @Override
    public void setFocusHintPath(Path path) {
        // ignore
    }

    @Override
    public int getLoadingState(int offset) {
        return mLoadingState;
    }

    private boolean mAnimateGif;
    private Future<?> mGifTask;
    private GifDecoderWrapper mGifDecoder;
    private Bitmap mCurrentGifFrame;
    private int mCurrentFrameNum;
    private int mTotalFrameCount;
    private static final int gifBackGroundColor = 0xFFFFFFFF;

    private class GifDecoderListener
            implements Runnable, FutureListener<GifData> {
        private Future<GifData> mFuture;

        public GifDecoderListener() {}

        @Override
        public void onFutureDone(Future<GifData> future) {
            mFuture = future;
            if (mIsGifAnimationSupported && null != mFuture.get()) {
                mHandler.sendMessage(
                        mHandler.obtainMessage(MSG_RUN_OBJECT, this));
            }
        }

        @Override
        public void run() {
            startGifAnimation(mFuture);
        }
    }

    private void startGifAnimation(Future<GifData> future) {
        mGifTask = null;
        mGifDecoder = future.get().gifDecoder;
        if (mGifDecoder != null) {

            mCurrentFrameNum = 0;
            mTotalFrameCount = mGifDecoder.getTotalFrameCount();
            if (mTotalFrameCount <= 1) {
                Log.w(TAG, "invalid frame count, NO animation!");
                return;
            }

            mCurrentGifFrame = Bitmap.createBitmap(mGifDecoder.getWidth(), 
                                                   mGifDecoder.getHeight(),
                                                   Bitmap.Config.ARGB_8888);
            Utils.assertTrue(null != mCurrentGifFrame);

            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_RUN_OBJECT, 
                                     new GifAnimationRunnable()));
        }
    }

    private class GifAnimationRunnable implements Runnable {
        public GifAnimationRunnable() {
        }

        @Override
        public void run() {
            if (!mIsActive) {
                Log.i(TAG, "GifAnimationRunnable: run: already paused");
                releaseGifResource();
                return;
            }

            if (null == mGifDecoder) {
                Log.e(TAG, "GifAnimationRunnable: run: invalid GifDecoder");
                releaseGifResource();
                return;
            }

            long preTime = SystemClock.uptimeMillis();

            Bitmap curBitmap = mGifDecoder.getFrameBitmap(mCurrentFrameNum);
            if (null == curBitmap) {
                Log.e(TAG, "GifAnimationRunnable: got null frame!");
                releaseGifResource();
                return;
            }

            long curDuration = mGifDecoder.getFrameDuration(mCurrentFrameNum);
            mCurrentFrameNum = (mCurrentFrameNum + 1) % mTotalFrameCount;

            Canvas canvas = new Canvas(mCurrentGifFrame);
            canvas.drawColor(gifBackGroundColor);
            Matrix m = new Matrix();
            canvas.drawBitmap(curBitmap, m, null);
            
            curBitmap.recycle();

            updateGifFrame(mCurrentGifFrame);

            mHandler.sendMessageAtTime(
                    mHandler.obtainMessage(MSG_RUN_OBJECT, this), (curDuration+preTime));
        }
    }

    private void updateGifFrame(Bitmap gifFrame) {
        if (gifFrame == null) return;
        
        setScreenNail(gifFrame, gifFrame.getWidth(), gifFrame.getHeight());
        mPhotoView.notifyImageChange(0);
    }

    private void releaseGifResource() {
        mGifDecoder = null;
        if (null != mCurrentGifFrame && !mCurrentGifFrame.isRecycled()) {
            mCurrentGifFrame.recycle();
            mCurrentGifFrame = null;
        }
    }
}
