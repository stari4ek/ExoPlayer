package com.google.android.exoplayer2.demo;


import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * TVirl: add own functionality
 */
public class PlayerActivityExt extends PlayerActivity {

    @Override
    MediaSource createLeafMediaSource(MediaItem mediaItem) {

        final int tsFlags = FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS;

        Assertions.checkNotNull(mediaItem.playbackProperties);
        if (MimeTypes.APPLICATION_M3U8.equals(mediaItem.playbackProperties.mimeType)) {
            return new HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(
                    new DefaultHlsExtractorFactory(tsFlags, true)
                )
                .createMediaSource(mediaItem);
        } else if (mediaItem.playbackProperties.mimeType == null) {
            return new ProgressiveMediaSource.Factory(dataSourceFactory,
                new DefaultExtractorsFactory()
                    .setTsExtractorFlags(tsFlags))
                .createMediaSource(mediaItem);
        } else {
            return super.createLeafMediaSource(mediaItem);
        }
    }
}
