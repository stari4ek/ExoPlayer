package com.google.android.exoplayer2.demo;

import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;

public class CustomMediaSourceFactory implements MediaSourceFactory {

  private final DataSource.Factory dataSourceFactory;
  private final DefaultMediaSourceFactory fallback;

  CustomMediaSourceFactory(
      Context context,
      DataSource.Factory dataSourceFactory,
      DefaultMediaSourceFactory.AdSupportProvider adSupportProvider) {

    this.dataSourceFactory = dataSourceFactory;
    fallback = DefaultMediaSourceFactory.newInstance(context, dataSourceFactory, adSupportProvider);
  }

  @NonNull
  @Override
  public MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    fallback.setDrmSessionManager(drmSessionManager);
    return this;
  }

  @NonNull
  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(@Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    fallback.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
    return this;
  }

  @NonNull
  @Override
  public int[] getSupportedTypes() {
    return fallback.getSupportedTypes();
  }

  @NonNull
  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
//    TsExtractor.HLS_MULTIPLE_TRACKS_ALLOWED = true;
//    HlsSampleStreamWrapper.enableMapping(false);

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
      return fallback.createMediaSource(mediaItem);
    }
  }
}
