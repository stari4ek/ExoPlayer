/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Decodes and renders audio using a {@link Decoder}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VOLUME} to set the volume. The message payload should be
 *       a {@link Float} with 0 being silence and 1 being unity gain.
 *   <li>Message with type {@link #MSG_SET_AUDIO_ATTRIBUTES} to set the audio attributes. The
 *       message payload should be an {@link com.google.android.exoplayer2.audio.AudioAttributes}
 *       instance that will configure the underlying audio track.
 *   <li>Message with type {@link #MSG_SET_AUX_EFFECT_INFO} to set the auxiliary effect. The message
 *       payload should be an {@link AuxEffectInfo} instance that will configure the underlying
 *       audio track.
 *   <li>Message with type {@link #MSG_SET_SKIP_SILENCE_ENABLED} to enable or disable skipping
 *       silences. The message payload should be a {@link Boolean}.
 *   <li>Message with type {@link #MSG_SET_AUDIO_SESSION_ID} to set the audio session ID. The
 *       message payload should be a session ID {@link Integer} that will be attached to the
 *       underlying audio track.
 * </ul>
 */
public abstract class DecoderAudioRenderer extends BaseRenderer implements MediaClock {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}
  /**
   * The decoder does not need to be re-initialized.
   */
  private static final int REINITIALIZATION_STATE_NONE = 0;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;

  private final EventDispatcher eventDispatcher;
  private final AudioSink audioSink;
  private final DecoderInputBuffer flagsOnlyBuffer;

  private DecoderCounters decoderCounters;
  private Format inputFormat;
  private int encoderDelay;
  private int encoderPadding;

  @Nullable
  private Decoder<DecoderInputBuffer, ? extends SimpleOutputBuffer, ? extends DecoderException>
      decoder;

  @Nullable private DecoderInputBuffer inputBuffer;
  @Nullable private SimpleOutputBuffer outputBuffer;
  @Nullable private DrmSession decoderDrmSession;
  @Nullable private DrmSession sourceDrmSession;

  @ReinitializationState private int decoderReinitializationState;
  private boolean decoderReceivedBuffers;
  private boolean audioTrackNeedsConfigure;

  private long currentPositionUs;
  private boolean allowFirstBufferPositionDiscontinuity;
  private boolean allowPositionDiscontinuity;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeys;

  public DecoderAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    this(
        eventHandler,
        eventListener,
        /* audioCapabilities= */ null,
        audioProcessors);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(eventHandler, eventListener, new DefaultAudioSink(audioCapabilities, audioProcessors));
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public DecoderAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(C.TRACK_TYPE_AUDIO);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    this.audioSink = audioSink;
    audioSink.setListener(new AudioSinkListener());
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    audioTrackNeedsConfigure = true;
  }

  @Override
  @Nullable
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  @Capabilities
  public final int supportsFormat(Format format) {
    if (!MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_TYPE);
    }
    @FormatSupport int formatSupport = supportsFormatInternal(format);
    if (formatSupport <= FORMAT_UNSUPPORTED_DRM) {
      return RendererCapabilities.create(formatSupport);
    }
    @TunnelingSupport
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    return RendererCapabilities.create(formatSupport, ADAPTIVE_NOT_SEAMLESS, tunnelingSupport);
  }

  /**
   * Returns the {@link FormatSupport} for the given {@link Format}.
   *
   * @param format The format, which has an audio {@link Format#sampleMimeType}.
   * @return The {@link FormatSupport} for this {@link Format}.
   */
  @FormatSupport
  protected abstract int supportsFormatInternal(Format format);

  /**
   * Returns whether the sink supports the audio format.
   *
   * @see AudioSink#supportsOutput(int, int, int)
   */
  protected final boolean supportsOutput(
      int channelCount, int sampleRateHz, @C.Encoding int encoding) {
    return audioSink.supportsOutput(channelCount, sampleRateHz, encoding);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      try {
        audioSink.playToEndOfStream();
      } catch (AudioSink.WriteException e) {
        throw createRendererException(e, inputFormat);
      }
      return;
    }

    // Try and read a format if we don't have one already.
    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @SampleStream.ReadDataResult int result = readSource(formatHolder, flagsOnlyBuffer, true);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder);
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        processEndOfStream();
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder();

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer()) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } catch (DecoderException
          | AudioSink.ConfigurationException
          | AudioSink.InitializationException
          | AudioSink.WriteException e) {
        throw createRendererException(e, inputFormat);
      }
      decoderCounters.ensureUpdated();
    }
  }

  /**
   * Called when the audio session id becomes known. The default implementation is a no-op. One
   * reason for overriding this method would be to instantiate and enable a {@link Virtualizer} in
   * order to spatialize the audio channels. For this use case, any {@link Virtualizer} instances
   * should be released in {@link #onDisabled()} (if not before).
   *
   * <p>See {@link AudioSink.Listener#onAudioSessionId(int)}.
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  /** See {@link AudioSink.Listener#onPositionDiscontinuity()}. */
  protected void onAudioTrackPositionDiscontinuity() {
    // Do nothing.
  }

  /** See {@link AudioSink.Listener#onUnderrun(int, long, long)}. */
  protected void onAudioTrackUnderrun(
      int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    // Do nothing.
  }

  /** See {@link AudioSink.Listener#onSkipSilenceEnabledChanged(boolean)}. */
  protected void onAudioTrackSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
    // Do nothing.
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param mediaCrypto The {@link ExoMediaCrypto} object required for decoding encrypted content.
   *     Maybe null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws DecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract Decoder<
          DecoderInputBuffer, ? extends SimpleOutputBuffer, ? extends DecoderException>
      createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto) throws DecoderException;

  /**
   * Returns the format of audio buffers output by the decoder. Will not be called until the first
   * output buffer has been dequeued, so the decoder may use input data to determine the format.
   */
  protected abstract Format getOutputFormat();

  /**
   * Returns whether the existing decoder can be kept for a new format.
   *
   * @param oldFormat The previous format.
   * @param newFormat The new format.
   * @return Whether the existing decoder can be kept.
   */
  protected boolean canKeepCodec(Format oldFormat, Format newFormat) {
    return false;
  }

  private boolean drainOutputBuffer()
      throws ExoPlaybackException, DecoderException, AudioSink.ConfigurationException,
          AudioSink.InitializationException, AudioSink.WriteException {
    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      if (outputBuffer.skippedOutputBufferCount > 0) {
        decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
        audioSink.handleDiscontinuity();
      }
    }

    if (outputBuffer.isEndOfStream()) {
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoder();
        maybeInitDecoder();
        // The audio track may need to be recreated once the new output format is known.
        audioTrackNeedsConfigure = true;
      } else {
        outputBuffer.release();
        outputBuffer = null;
        processEndOfStream();
      }
      return false;
    }

    if (audioTrackNeedsConfigure) {
      Format outputFormat = getOutputFormat();
      audioSink.configure(outputFormat.pcmEncoding, outputFormat.channelCount,
          outputFormat.sampleRate, 0, null, encoderDelay, encoderPadding);
      audioTrackNeedsConfigure = false;
    }

    if (audioSink.handleBuffer(
        outputBuffer.data, outputBuffer.timeUs, /* encodedAccessUnitCount= */ 1)) {
      decoderCounters.renderedOutputBufferCount++;
      outputBuffer.release();
      outputBuffer = null;
      return true;
    }

    return false;
  }

  private boolean feedInputBuffer() throws DecoderException, ExoPlaybackException {
    if (decoder == null || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    @SampleStream.ReadDataResult int result;
    FormatHolder formatHolder = getFormatHolder();
    if (waitingForKeys) {
      // We've already read an encrypted sample into buffer, and are waiting for keys.
      result = C.RESULT_BUFFER_READ;
    } else {
      result = readSource(formatHolder, inputBuffer, false);
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder);
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      return false;
    }
    boolean bufferEncrypted = inputBuffer.isEncrypted();
    waitingForKeys = shouldWaitForKeys(bufferEncrypted);
    if (waitingForKeys) {
      return false;
    }
    inputBuffer.flip();
    onQueueInputBuffer(inputBuffer);
    decoder.queueInputBuffer(inputBuffer);
    decoderReceivedBuffers = true;
    decoderCounters.inputBufferCount++;
    inputBuffer = null;
    return true;
  }

  private boolean shouldWaitForKeys(boolean bufferEncrypted) throws ExoPlaybackException {
    if (decoderDrmSession == null
        || (!bufferEncrypted && decoderDrmSession.playClearSamplesWithoutKeys())) {
      return false;
    }
    @DrmSession.State int drmSessionState = decoderDrmSession.getState();
    if (drmSessionState == DrmSession.STATE_ERROR) {
      throw createRendererException(decoderDrmSession.getError(), inputFormat);
    }
    return drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS;
  }

  private void processEndOfStream() throws ExoPlaybackException {
    outputStreamEnded = true;
    try {
      audioSink.playToEndOfStream();
    } catch (AudioSink.WriteException e) {
      // TODO(internal: b/145658993) Use outputFormat for the call from drainOutputBuffer.
      throw createRendererException(e, inputFormat);
    }
  }

  private void flushDecoder() throws ExoPlaybackException {
    waitingForKeys = false;
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder();
      maybeInitDecoder();
    } else {
      inputBuffer = null;
      if (outputBuffer != null) {
        outputBuffer.release();
        outputBuffer = null;
      }
      decoder.flush();
      decoderReceivedBuffers = false;
    }
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded && audioSink.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioSink.hasPendingData()
        || (inputFormat != null && !waitingForKeys && (isSourceReady() || outputBuffer != null));
  }

  @Override
  public long getPositionUs() {
    if (getState() == STATE_STARTED) {
      updateCurrentPosition();
    }
    return currentPositionUs;
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    audioSink.setPlaybackSpeed(playbackSpeed);
  }

  @Override
  public float getPlaybackSpeed() {
    return audioSink.getPlaybackSpeed();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    int tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      audioSink.enableTunnelingV21(tunnelingAudioSessionId);
    } else {
      audioSink.disableTunneling();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    audioSink.flush();
    currentPositionUs = positionUs;
    allowFirstBufferPositionDiscontinuity = true;
    allowPositionDiscontinuity = true;
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onStarted() {
    audioSink.play();
  }

  @Override
  protected void onStopped() {
    updateCurrentPosition();
    audioSink.pause();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    audioTrackNeedsConfigure = true;
    waitingForKeys = false;
    try {
      setSourceDrmSession(null);
      releaseDecoder();
      audioSink.reset();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VOLUME:
        audioSink.setVolume((Float) message);
        break;
      case MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(audioAttributes);
        break;
      case MSG_SET_AUX_EFFECT_INFO:
        AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
        audioSink.setAuxEffectInfo(auxEffectInfo);
        break;
      case MSG_SET_SKIP_SILENCE_ENABLED:
        audioSink.setSkipSilenceEnabled((Boolean) message);
        break;
      case MSG_SET_AUDIO_SESSION_ID:
        audioSink.setAudioSessionId((Integer) message);
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    setDecoderDrmSession(sourceDrmSession);

    ExoMediaCrypto mediaCrypto = null;
    if (decoderDrmSession != null) {
      mediaCrypto = decoderDrmSession.getMediaCrypto();
      if (mediaCrypto == null) {
        DrmSessionException drmError = decoderDrmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if the session recovers, or if a new
          // input format causes the session to be replaced before it's used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      }
    }

    try {
      long codecInitializingTimestamp = SystemClock.elapsedRealtime();
      TraceUtil.beginSection("createAudioDecoder");
      decoder = createDecoder(inputFormat, mediaCrypto);
      TraceUtil.endSection();
      long codecInitializedTimestamp = SystemClock.elapsedRealtime();
      eventDispatcher.decoderInitialized(decoder.getName(), codecInitializedTimestamp,
          codecInitializedTimestamp - codecInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (DecoderException e) {
      throw createRendererException(e, inputFormat);
    }
  }

  private void releaseDecoder() {
    inputBuffer = null;
    outputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
    if (decoder != null) {
      decoder.release();
      decoder = null;
      decoderCounters.decoderReleaseCount++;
    }
    setDecoderDrmSession(null);
  }

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setDecoderDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(decoderDrmSession, session);
    decoderDrmSession = session;
  }

  private void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    setSourceDrmSession(formatHolder.drmSession);
    Format oldFormat = inputFormat;
    inputFormat = newFormat;

    if (decoder == null) {
      maybeInitDecoder();
    } else if (sourceDrmSession != decoderDrmSession || !canKeepCodec(oldFormat, inputFormat)) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder();
        maybeInitDecoder();
        audioTrackNeedsConfigure = true;
      }
    }

    encoderDelay = inputFormat.encoderDelay;
    encoderPadding = inputFormat.encoderPadding;
    eventDispatcher.inputFormatChanged(inputFormat);
  }

  private void onQueueInputBuffer(DecoderInputBuffer buffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
      // TODO: Remove this hack once we have a proper fix for [Internal: b/71876314].
      // Allow the position to jump if the first presentable input buffer has a timestamp that
      // differs significantly from what was expected.
      if (Math.abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs;
      }
      allowFirstBufferPositionDiscontinuity = false;
    }
  }

  private void updateCurrentPosition() {
    long newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
          allowPositionDiscontinuity
              ? newCurrentPositionUs
              : Math.max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
  }

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onAudioSessionId(int audioSessionId) {
      eventDispatcher.audioSessionId(audioSessionId);
      DecoderAudioRenderer.this.onAudioSessionId(audioSessionId);
    }

    @Override
    public void onPositionDiscontinuity() {
      onAudioTrackPositionDiscontinuity();
      // We are out of sync so allow currentPositionUs to jump backwards.
      DecoderAudioRenderer.this.allowPositionDiscontinuity = true;
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      eventDispatcher.skipSilenceEnabledChanged(skipSilenceEnabled);
      onAudioTrackSkipSilenceEnabledChanged(skipSilenceEnabled);
    }
  }
}
