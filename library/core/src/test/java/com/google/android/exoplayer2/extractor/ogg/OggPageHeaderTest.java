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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.android.exoplayer2.testutil.TestUtil.getByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.testutil.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggPageHeader}. */
@RunWith(AndroidJUnit4.class)
public final class OggPageHeaderTest {

  @Test
  public void testPopulatePageHeader() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/page_header");

    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ true);
    OggPageHeader header = new OggPageHeader();
    populatePageHeader(input, header, /* quiet= */ false);

    assertThat(header.type).isEqualTo(0x01);
    assertThat(header.headerSize).isEqualTo(27 + 2);
    assertThat(header.bodySize).isEqualTo(4);
    assertThat(header.pageSegmentCount).isEqualTo(2);
    assertThat(header.granulePosition).isEqualTo(123456);
    assertThat(header.pageSequenceNumber).isEqualTo(4);
    assertThat(header.streamSerialNumber).isEqualTo(0x1000);
    assertThat(header.pageChecksum).isEqualTo(0x100000);
    assertThat(header.revision).isEqualTo(0);
  }

  @Test
  public void testPopulatePageHeaderQuietOnExceptionLessThan27Bytes() throws Exception {
    FakeExtractorInput input =
        createInput(TestUtil.createByteArray(2, 2), /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, /* quiet= */ true)).isFalse();
  }

  @Test
  public void testPopulatePageHeaderQuietOnExceptionNotOgg() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/page_header");
    // change from 'O' to 'o'
    data[0] = 'o';
    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, /* quiet= */ true)).isFalse();
  }

  @Test
  public void testPopulatePageHeaderQuiteOnExceptionWrongRevision() throws Exception {
    byte[] data = getByteArray(ApplicationProvider.getApplicationContext(), "ogg/page_header");
    // change revision from 0 to 1
    data[4] = 0x01;
    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, /* quiet= */ true)).isFalse();
  }

  private static boolean populatePageHeader(
      FakeExtractorInput input, OggPageHeader header, boolean quiet) throws Exception {
    while (true) {
      try {
        return header.populate(input, quiet);
      } catch (SimulatedIOException e) {
        // ignored
      }
    }
  }

  private static FakeExtractorInput createInput(byte[] data, boolean simulateUnknownLength) {
    return new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateIOErrors(true)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(true)
        .build();
  }
}

