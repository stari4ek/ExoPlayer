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
package com.google.android.exoplayer.extractor.wav;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.IOException;

/** {@link Extractor} to extract samples from a WAV byte stream. */
public final class WavExtractor implements Extractor, SeekMap {

  /** Arbitrary maximum input size of 32KB, which is ~170ms of 16-bit stereo PCM audio at 48KHz. */
  private static final int MAX_INPUT_SIZE = 32 * 1024;

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;
  private WavHeader wavHeader;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return WavHeaderReader.peek(input) != null;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0);
    wavHeader = null;
    output.endTracks();
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {

    if (wavHeader == null) {
      wavHeader = WavHeaderReader.peek(input);
      if (wavHeader == null) {
        // Someone tried to read a non-WAV or unsupported WAV without sniffing first.
        throw new ParserException("Error initializing WavHeader. Did you sniff first?");
      }
    }

    // If we haven't read in the data start and size, read and store them.
    if (!wavHeader.hasDataBounds()) {
      WavHeaderReader.skipToData(input, wavHeader);

      trackOutput.format(
          MediaFormat.createAudioFormat(
              null,
              MimeTypes.AUDIO_RAW,
              wavHeader.getBitrate(),
              MAX_INPUT_SIZE,
              wavHeader.getDurationUs(),
              wavHeader.getNumChannels(),
              wavHeader.getSampleRateHz(),
              null,
              null));
      extractorOutput.seekMap(this);
    }

    long inputPosition = input.getPosition();

    int bytesRead = trackOutput.sampleData(input, MAX_INPUT_SIZE, true);
    if (bytesRead == RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    trackOutput.sampleMetadata(
        wavHeader.getTimeUs(inputPosition), C.SAMPLE_FLAG_SYNC, bytesRead, 0, null);

    return RESULT_CONTINUE;
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getPosition(long timeUs) {
    return wavHeader.getPosition(timeUs);
  }
}
