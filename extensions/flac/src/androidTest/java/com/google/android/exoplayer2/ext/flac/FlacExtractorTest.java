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
package com.google.android.exoplayer2.ext.flac;

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;

/**
 * Unit test for {@link FlacExtractor}.
 */
public class FlacExtractorTest extends InstrumentationTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (!FlacLibrary.isAvailable()) {
      fail("Flac library not available.");
    }
  }

  public void testExtractFlacSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        new ExtractorFactory() {
          @Override
          public Extractor create() {
            return new FlacExtractor();
          }
        },
        "bear.flac",
        getInstrumentation().getContext());
  }

  public void testExtractFlacSampleWithId3Header() throws Exception {
    ExtractorAsserts.assertBehavior(
        new ExtractorFactory() {
          @Override
          public Extractor create() {
            return new FlacExtractor();
          }
        },
        "bear_with_id3.flac",
        getInstrumentation().getContext());
  }
}
