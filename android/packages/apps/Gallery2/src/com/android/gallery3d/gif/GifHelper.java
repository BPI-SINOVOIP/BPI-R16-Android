package com.android.gallery3d.gif;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.util.Log;
import android.gif.GifDecoder;
import com.android.gallery3d.util.ThreadPool.JobContext;

public class GifHelper {
	
    private static final String TAG = "GifHelper";

    public static final String FILE_EXTENSION = "gif";

    public static final String MIME_TYPE = "image/gif";


    public static GifDecoder createGifDecoder(JobContext jc, String filePath) {
        try {
            InputStream is = new FileInputStream(filePath);
            GifDecoder gifDecoder = createGifDecoderInner(is);
            is.close();
            return gifDecoder;
        } catch (Throwable t) {
            Log.w(TAG, t);
            return null;
        }
    }

    public static GifDecoder createGifDecoder(JobContext jc, byte[] data, 
                              int offset,int length) {
        if (null == data) {
            Log.e(TAG,"createGifDecoder:find null buffer!");
            return null;
        }
        GifDecoder gifDecoder = new GifDecoder(data, offset, length);
        if (gifDecoder.getTotalFrameCount() == GifDecoder.INVALID_VALUE) {
            Log.e(TAG,"createGifDecoder:got invalid GifDecoder");
            gifDecoder = null;
        }
        return gifDecoder;
    }

    public static GifDecoder createGifDecoder(JobContext jc, InputStream is) {
        try {
            return createGifDecoderInner(is);
        } catch (Throwable t)  {
            Log.w(TAG, t);
            return null;
        }
    }

    public static GifDecoder createGifDecoder(JobContext jc, FileDescriptor fd) {
        try {
            InputStream is = new FileInputStream(fd);
            GifDecoder gifDecoder = createGifDecoderInner(is);
            is.close();
            return gifDecoder;
        } catch (Throwable t)  {
            Log.w(TAG, t);
            return null;
        }
    }
    
    private static GifDecoder createGifDecoderInner(InputStream is) {
        if (null == is) {
            Log.e(TAG,"createGifDecoder:find null InputStream!");
            return null;
        }
        GifDecoder gifDecoder = new GifDecoder(is);
        if (gifDecoder.getTotalFrameCount() == GifDecoder.INVALID_VALUE) {
            Log.e(TAG,"createGifDecoder:got invalid GifDecoder");
            gifDecoder = null;
        }
        return gifDecoder;
    }
}
