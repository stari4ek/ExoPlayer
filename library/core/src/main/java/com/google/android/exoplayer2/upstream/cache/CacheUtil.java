/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream.cache;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caching related utility methods.
 */
public final class CacheUtil {

  /** Receives progress updates during cache operations. */
  public interface ProgressListener {

    /**
     * Called when progress is made during a cache operation.
     *
     * @param requestLength The length of the content being cached in bytes, or {@link
     *     C#LENGTH_UNSET} if unknown.
     * @param bytesCached The number of bytes that are cached.
     * @param newBytesCached The number of bytes that have been newly cached since the last progress
     *     update.
     */
    void onProgress(long requestLength, long bytesCached, long newBytesCached);
  }

  /** Default buffer size to be used while caching. */
  public static final int DEFAULT_BUFFER_SIZE_BYTES = 128 * 1024;

  /** @deprecated Use {@link CacheKeyFactory#DEFAULT}. */
  @Deprecated
  public static final CacheKeyFactory DEFAULT_CACHE_KEY_FACTORY = CacheKeyFactory.DEFAULT;

  /**
   * Caches the data defined by {@code dataSpec}, skipping already cached data. Caching stops early
   * if the end of the input is reached.
   *
   * <p>To cancel the operation, the caller should both set {@code isCanceled} to true and interrupt
   * the calling thread.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param cache A {@link Cache} to store the data.
   * @param dataSpec Defines the data to be cached.
   * @param upstreamDataSource A {@link DataSource} for reading data not in the cache.
   * @param progressListener A listener to receive progress updates, or {@code null}.
   * @param isCanceled An optional flag that will cancel the operation if set to true.
   * @throws IOException If an error occurs caching the data, or if the operation was canceled.
   */
  @WorkerThread
  public static void cache(
      Cache cache,
      DataSpec dataSpec,
      DataSource upstreamDataSource,
      @Nullable ProgressListener progressListener,
      @Nullable AtomicBoolean isCanceled)
      throws IOException {
    cache(
        new CacheDataSource(cache, upstreamDataSource),
        dataSpec,
        progressListener,
        isCanceled,
        /* enableEOFException= */ false,
        new byte[DEFAULT_BUFFER_SIZE_BYTES]);
  }

  /**
   * Caches the data defined by {@code dataSpec}, skipping already cached data. Caching stops early
   * if end of input is reached and {@code enableEOFException} is false.
   *
   * <p>If {@code dataSource} has a {@link PriorityTaskManager}, then it's the responsibility of the
   * calling code to call {@link PriorityTaskManager#add} to register with the manager before
   * calling this method, and to call {@link PriorityTaskManager#remove} afterwards to unregister.
   *
   * <p>To cancel the operation, the caller should both set {@code isCanceled} to true and interrupt
   * the calling thread.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param dataSource A {@link CacheDataSource} to be used for caching the data.
   * @param dataSpec Defines the data to be cached.
   * @param progressListener A listener to receive progress updates, or {@code null}.
   * @param isCanceled An optional flag that will cancel the operation if set to true.
   * @param enableEOFException Whether to throw an {@link EOFException} if end of input has been
   *     reached unexpectedly.
   * @param temporaryBuffer A temporary buffer to be used during caching.
   * @throws IOException If an error occurs caching the data, or if the operation was canceled.
   */
  @WorkerThread
  public static void cache(
      CacheDataSource dataSource,
      DataSpec dataSpec,
      @Nullable ProgressListener progressListener,
      @Nullable AtomicBoolean isCanceled,
      boolean enableEOFException,
      byte[] temporaryBuffer)
      throws IOException {
    Assertions.checkNotNull(dataSource);
    Assertions.checkNotNull(temporaryBuffer);

    Cache cache = dataSource.getCache();
    String cacheKey = dataSource.getCacheKeyFactory().buildCacheKey(dataSpec);
    long requestLength = dataSpec.length;
    if (requestLength == C.LENGTH_UNSET) {
      long resourceLength = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey));
      if (resourceLength != C.LENGTH_UNSET) {
        requestLength = resourceLength - dataSpec.position;
      }
    }
    long bytesCached = cache.getCachedBytes(cacheKey, dataSpec.position, requestLength);
    @Nullable ProgressNotifier progressNotifier = null;
    if (progressListener != null) {
      progressNotifier = new ProgressNotifier(progressListener);
      progressNotifier.init(requestLength, bytesCached);
    }

    long position = dataSpec.position;
    long bytesLeft = requestLength;
    while (bytesLeft != 0) {
      throwExceptionIfCanceled(isCanceled);
      long blockLength = cache.getCachedLength(cacheKey, position, bytesLeft);
      if (blockLength > 0) {
        // Skip already cached data.
      } else {
        // There is a hole in the cache which is at least "-blockLength" long.
        blockLength = -blockLength;
        long length = blockLength == Long.MAX_VALUE ? C.LENGTH_UNSET : blockLength;
        boolean isLastBlock = length == bytesLeft;
        long read =
            readAndDiscard(
                dataSpec,
                position,
                length,
                dataSource,
                isCanceled,
                progressNotifier,
                isLastBlock,
                temporaryBuffer);
        if (read < blockLength) {
          // Reached to the end of the data.
          if (enableEOFException && bytesLeft != C.LENGTH_UNSET) {
            throw new EOFException();
          }
          break;
        }
      }
      position += blockLength;
      if (bytesLeft != C.LENGTH_UNSET) {
        bytesLeft -= blockLength;
      }
    }
  }

  /**
   * Reads and discards all data specified by the {@code dataSpec}.
   *
   * @param dataSpec Defines the data to be read. The {@code position} and {@code length} fields are
   *     overwritten by the following parameters.
   * @param position The position of the data to be read.
   * @param length Length of the data to be read, or {@link C#LENGTH_UNSET} if it is unknown.
   * @param dataSource The {@link CacheDataSource} to read the data from.
   * @param isCanceled An optional flag that will cancel the operation if set to true.
   * @param progressNotifier A notifier through which to report progress updates, or {@code null}.
   * @param isLastBlock Whether this read block is the last block of the content.
   * @param temporaryBuffer A temporary buffer to be used during caching.
   * @return Number of read bytes, or 0 if no data is available because the end of the opened range
   *     has been reached.
   * @param isCanceled An optional flag that will cancel the operation if set to true.
   */
  private static long readAndDiscard(
      DataSpec dataSpec,
      long position,
      long length,
      CacheDataSource dataSource,
      @Nullable AtomicBoolean isCanceled,
      @Nullable ProgressNotifier progressNotifier,
      boolean isLastBlock,
      byte[] temporaryBuffer)
      throws IOException {
    long positionOffset = position - dataSpec.position;
    long initialPositionOffset = positionOffset;
    long endOffset = length != C.LENGTH_UNSET ? positionOffset + length : C.POSITION_UNSET;
    @Nullable PriorityTaskManager priorityTaskManager = dataSource.getUpstreamPriorityTaskManager();
    while (true) {
      if (priorityTaskManager != null) {
        // Wait for any other thread with higher priority to finish its job.
        try {
          priorityTaskManager.proceed(dataSource.getUpstreamPriority());
        } catch (InterruptedException e) {
          throw new InterruptedIOException();
        }
      }
      throwExceptionIfCanceled(isCanceled);
      try {
        long resolvedLength = C.LENGTH_UNSET;
        boolean isDataSourceOpen = false;
        if (endOffset != C.POSITION_UNSET) {
          // If a specific length is given, first try to open the data source for that length to
          // avoid more data then required to be requested. If the given length exceeds the end of
          // input we will get a "position out of range" error. In that case try to open the source
          // again with unset length.
          try {
            resolvedLength =
                dataSource.open(dataSpec.subrange(positionOffset, endOffset - positionOffset));
            isDataSourceOpen = true;
          } catch (IOException exception) {
            if (!isLastBlock || !DataSourceException.isCausedByPositionOutOfRange(exception)) {
              throw exception;
            }
            Util.closeQuietly(dataSource);
          }
        }
        if (!isDataSourceOpen) {
          resolvedLength = dataSource.open(dataSpec.subrange(positionOffset, C.LENGTH_UNSET));
        }
        if (isLastBlock && progressNotifier != null && resolvedLength != C.LENGTH_UNSET) {
          progressNotifier.onRequestLengthResolved(positionOffset + resolvedLength);
        }
        while (positionOffset != endOffset) {
          throwExceptionIfCanceled(isCanceled);
          int bytesRead =
              dataSource.read(
                  temporaryBuffer,
                  0,
                  endOffset != C.POSITION_UNSET
                      ? (int) Math.min(temporaryBuffer.length, endOffset - positionOffset)
                      : temporaryBuffer.length);
          if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (progressNotifier != null) {
              progressNotifier.onRequestLengthResolved(positionOffset);
            }
            break;
          }
          positionOffset += bytesRead;
          if (progressNotifier != null) {
            progressNotifier.onBytesCached(bytesRead);
          }
        }
        return positionOffset - initialPositionOffset;
      } catch (PriorityTaskManager.PriorityTooLowException exception) {
        // catch and try again
      } finally {
        Util.closeQuietly(dataSource);
      }
    }
  }

  private static void throwExceptionIfCanceled(@Nullable AtomicBoolean isCanceled)
      throws InterruptedIOException {
    if (isCanceled != null && isCanceled.get()) {
      throw new InterruptedIOException();
    }
  }

  private CacheUtil() {}

  private static final class ProgressNotifier {
    /** The listener to notify when progress is made. */
    private final ProgressListener listener;
    /** The length of the content being cached in bytes, or {@link C#LENGTH_UNSET} if unknown. */
    private long requestLength;
    /** The number of bytes that are cached. */
    private long bytesCached;

    public ProgressNotifier(ProgressListener listener) {
      this.listener = listener;
    }

    public void init(long requestLength, long bytesCached) {
      this.requestLength = requestLength;
      this.bytesCached = bytesCached;
      listener.onProgress(requestLength, bytesCached, /* newBytesCached= */ 0);
    }

    public void onRequestLengthResolved(long requestLength) {
      if (this.requestLength == C.LENGTH_UNSET && requestLength != C.LENGTH_UNSET) {
        this.requestLength = requestLength;
        listener.onProgress(requestLength, bytesCached, /* newBytesCached= */ 0);
      }
    }

    public void onBytesCached(long newBytesCached) {
      bytesCached += newBytesCached;
      listener.onProgress(requestLength, bytesCached, newBytesCached);
    }
  }
}
