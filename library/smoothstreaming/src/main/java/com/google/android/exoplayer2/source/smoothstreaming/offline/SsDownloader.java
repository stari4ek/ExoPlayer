/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsUtil;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A downloader for SmoothStreaming streams.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * CacheDataSource.Factory cacheDataSourceFactory =
 *     new CacheDataSource.Factory()
 *         .setCache(cache)
 *         .setUpstreamDataSourceFactory(new DefaultHttpDataSourceFactory(userAgent));
 * // Create a downloader for the first track of the first stream element.
 * SsDownloader ssDownloader =
 *     new SsDownloader(
 *         manifestUrl,
 *         Collections.singletonList(new StreamKey(0, 0)),
 *         cacheDataSourceFactory);
 * // Perform the download.
 * ssDownloader.download(progressListener);
 * // Use the downloaded data for playback.
 * SsMediaSource mediaSource =
 *     new SsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem);
 * }</pre>
 */
public final class SsDownloader extends SegmentDownloader<SsManifest> {

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which streams in the manifest should be selected for download.
   *     If empty, all streams are downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public SsDownloader(
      Uri manifestUri, List<StreamKey> streamKeys, CacheDataSource.Factory cacheDataSourceFactory) {
    this(manifestUri, streamKeys, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which streams in the manifest should be selected for download.
   *     If empty, all streams are downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public SsDownloader(
      Uri manifestUri,
      List<StreamKey> streamKeys,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    super(
        SsUtil.fixManifestUri(manifestUri),
        new SsManifestParser(),
        streamKeys,
        cacheDataSourceFactory,
        executor);
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, SsManifest manifest, boolean allowIncompleteList) {
    ArrayList<Segment> segments = new ArrayList<>();
    for (StreamElement streamElement : manifest.streamElements) {
      for (int i = 0; i < streamElement.formats.length; i++) {
        for (int j = 0; j < streamElement.chunkCount; j++) {
          segments.add(
              new Segment(
                  streamElement.getStartTimeUs(j),
                  new DataSpec(streamElement.buildRequestUri(i, j))));
        }
      }
    }
    return segments;
  }

}
