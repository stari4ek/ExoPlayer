package com.google.android.exoplayer2.demo;


import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.*;

import android.media.MediaDrm;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

/**
 * TVirl: add own functionality
 */
public class PlayerActivityExt extends PlayerActivity {

    @Override
    MediaSource createLeafMediaSource(Sample.UriSample parameters) {

        final int tsFlags = FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS;

        final String mime = Sample.inferAdaptiveStreamMimeType(parameters.uri, parameters.extension);
        if (MimeTypes.APPLICATION_M3U8.equals(mime)) {
            return new HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(
                    new DefaultHlsExtractorFactory(tsFlags, true)
                )
                .createMediaSource(parameters.uri);
        } else if (mime == null) {
            return new ProgressiveMediaSource.Factory(dataSourceFactory,
                new DefaultExtractorsFactory()
                    .setTsExtractorFlags(tsFlags))
                .createMediaSource(parameters.uri);
        } else {
            return super.createLeafMediaSource(parameters);
        }
    }
}
