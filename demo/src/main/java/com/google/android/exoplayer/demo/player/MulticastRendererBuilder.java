/*
 * Copyright (c) 2015. Michael Sotnikov
 */
package com.google.android.exoplayer.demo.player;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.ts.PtsTimestampAdjuster;
import com.google.android.exoplayer.extractor.ts.TsExtractor;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.UdpDataSource;
import com.google.android.exoplayer.util.Assertions;


public class MulticastRendererBuilder implements RendererBuilder {

  private static final String TAG = "UdpRendererBuilder";

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int BUFFER_SEGMENT_COUNT = 256;

  // TODO: take it to app's logic
  private static final int RETRY_ATTEMPTS = 2;
  private static final int READ_TIMEOUT_MS = 10 * 1000;

  private final Context context;
  private final Uri uri;

  public MulticastRendererBuilder(Context context, Uri uri) {
    this.context = context;
    this.uri = uri;
  }

  @Override
  public void buildRenderers(DemoPlayer player) {
    Uri adoptedUri = adoptUriForDataSource(uri);
    Log.d(TAG, "Constructing renderer with url: " + adoptedUri.toString());

    // TODO: do we need new allocator each time?
    Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

    // TODO: this bandwidth meter is useless cause we don't provide any listeners
    // Build the video and audio renderers.
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(player.getMainHandler(),
                                                                     null);
    DataSource dataSource = new UdpDataSource(bandwidthMeter,
                                              UdpDataSource.DEFAULT_MAX_PACKET_SIZE,
                                              READ_TIMEOUT_MS);
    TsExtractor tsExtractor = new TsExtractor(new PtsTimestampAdjuster(0), false);
    ExtractorSampleSource sampleSource = new ExtractorSampleSource(adoptedUri, dataSource, allocator,
        BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE, RETRY_ATTEMPTS, tsExtractor);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
        sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
        player.getMainHandler(), player, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
        MediaCodecSelector.DEFAULT, null, true, player.getMainHandler(), player,
        AudioCapabilities.getCapabilities(context), AudioManager.STREAM_MUSIC);
    TrackRenderer textRenderer = new TextTrackRenderer(sampleSource, player,
        player.getMainHandler().getLooper());

    // Invoke the callback.
    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_TEXT] = textRenderer;

    player.onRenderers(renderers, bandwidthMeter);
  }

  @Override
  public void cancel() {
    // Do nothing.
  }

  /**
   * UdpDataSource doesn't understand uri with scheme. adopt it
   */
  private static Uri adoptUriForDataSource(Uri uri) {
    // we expect uri for UDP. if it has scheme - drop it, cause UdpDataSource doesn't understand it
    String scheme = uri.getScheme();
    Assertions.checkArgument(TextUtils.isEmpty(scheme) || scheme.equals("udp"));
    if (!TextUtils.isEmpty(scheme)) {
      // TODO (astar): do it nicely
      // TODO (astar): "@" is part of auth. should we keep it?
      return Uri.parse(uri.toString().substring("udp://@".length()));
    }

    return uri;
  }
}
