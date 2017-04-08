package com.android.gallery3d.gif;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.gif.GifData;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.FileNotFoundException;

public class GifRequest{
    private static final String TAG = "GifRequest";

    private GifRequest() {}

    public static GifData request(JobContext jc, String filePath) {
        Log.d(TAG, "request(jc,filePath="+filePath+")");
        if (null == filePath) return null;

        GifData gifData = new GifData();
        gifData.gifDecoder =
                GifDecoderWrapper.createGifDecoderWrapper(filePath);

        return gifData;
    }

    public static GifData request(JobContext jc, FileDescriptor fd) {
        Log.d(TAG, "request(jc,fd="+fd+")");
        if (null == fd) return null;

        GifData gifData = new GifData();
        gifData.gifDecoder =
                GifDecoderWrapper.createGifDecoderWrapper(fd);

        return gifData;
    }
    
    public static GifData request(JobContext jc, byte[] data, int offset,int length) {
        Log.d(TAG, "request(jc, data, ...)");
        if (null == data || length <= 0) return null;

        GifData gifData = new GifData();
        gifData.gifDecoder =
                GifDecoderWrapper.createGifDecoderWrapper(
                                      data, offset, length);

        return gifData;
    }

    public static GifData request(JobContext jc, ContentResolver cr, Uri uri) {
        Log.d(TAG, "request(jc, cr, uri="+uri+")");
        if (null == cr || null == uri) return null;

        GifData gifData = new GifData();
        gifData.gifDecoder =
                GifDecoderWrapper.createGifDecoderWrapper(openUriInputStream(cr, uri));

        return gifData;
    }

    private static InputStream openUriInputStream(ContentResolver cr, Uri uri) {
        if (null == cr || null == uri) return null;
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme) || 
            ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme) || 
            ContentResolver.SCHEME_FILE.equals(scheme)) {
            try {
                return cr.openInputStream(uri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "openUriInputStream:fail to open: " + uri, e);
                return null;
            }
        }
        Log.w(TAG,"openUriInputStream:encountered unknow scheme!");
        return null;
    }
}
