package com.google.android.exoplayer2.demo;


import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.util.Util;

/**
 * TVirl: add own functionality
 */
public class PlayerActivityExt extends PlayerActivity {

    @Override
    MediaSource createLeafMediaSource(
        Uri uri, String extension, DrmSessionManager<ExoMediaCrypto> drmSessionManager) {

        @C.ContentType int type = Util.inferContentType(uri, extension);
        switch (type) {
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory)
                    .setDrmSessionManager(drmSessionManager)
                    .setExtractorFactory(
                        new DefaultHlsExtractorFactory(
                            FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS,
                            true
                        )
                    )
                    .createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(dataSourceFactory,
                    new DefaultExtractorsFactory()
                        .setTsExtractorFlags(FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS))
                    .setDrmSessionManager(drmSessionManager)
                    .createMediaSource(uri);
            case C.TYPE_DASH:
            case C.TYPE_SS:
            default:
                return super.createLeafMediaSource(uri, extension, drmSessionManager);
        }
    }
}
