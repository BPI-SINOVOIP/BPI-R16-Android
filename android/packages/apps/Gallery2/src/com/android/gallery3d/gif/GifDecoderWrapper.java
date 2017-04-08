package com.android.gallery3d.gif;

import java.io.FileDescriptor;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.gif.IGifDecoder;

public class GifDecoderWrapper {
	private static final String TAG = "GifDecoderWrapper";
    public static final int INVALID_VALUE = 
                                IGifDecoder.INVALID_VALUE;

    private IGifDecoder mGifDecoder;

    private GifDecoderWrapper(IGifDecoder gifDecoder) {
        mGifDecoder = gifDecoder;
    }

    public static GifDecoderWrapper 
        createGifDecoderWrapper(String filePath) {
        IGifDecoder gifDecoder = GifHelper.createGifDecoder(null, filePath);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper 
        createGifDecoderWrapper(byte[] data, int offset,int length) {
        IGifDecoder gifDecoder = 
            GifHelper.createGifDecoder(null, data, offset, length);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper 
        createGifDecoderWrapper(InputStream is) {
        IGifDecoder gifDecoder = GifHelper.createGifDecoder(null, is);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper 
        createGifDecoderWrapper(FileDescriptor fd) {
        IGifDecoder gifDecoder = GifHelper.createGifDecoder(null, fd);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public void close() {
        if (null == mGifDecoder) return;
        mGifDecoder.close();
    }

    public int getWidth() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getWidth();
    }

    public int getHeight() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getHeight();
    }

    public int getTotalDuration() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getTotalDuration();
    }

    public int getTotalFrameCount() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getTotalFrameCount();
    }

    public int getFrameDuration(int frameIndex) {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getFrameDuration(frameIndex);
    }

    public Bitmap getFrameBitmap(int frameIndex) {
        if (null == mGifDecoder) return null;
        return mGifDecoder.getFrameBitmap(frameIndex);
    }
}

