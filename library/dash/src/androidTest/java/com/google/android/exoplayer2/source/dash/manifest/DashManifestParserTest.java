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
package com.google.android.exoplayer2.source.dash.manifest;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link DashManifestParser}.
 */
public class DashManifestParserTest extends InstrumentationTestCase {

  private static final String SAMPLE_MPD_1 = "sample_mpd_1";
  private static final String SAMPLE_MPD_2_UNKNOWN_MIME_TYPE = "sample_mpd_2_unknown_mime_type";
  private static final String SAMPLE_MPD_3_SEGMENT_TEMPLATE = "sample_mpd_3_segment_template";
  private static final String SAMPLE_MPD_4_EVENT_STREAM = "sample_mpd_4_event_stream";

  /**
   * Simple test to ensure the sample manifests parse without any exceptions being thrown.
   */
  public void testParseMediaPresentationDescription() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_1));
    parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_2_UNKNOWN_MIME_TYPE));
  }

  public void testParseMediaPresentationDescriptionWithSegmentTemplate() throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd = parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_3_SEGMENT_TEMPLATE));

    assertThat(mpd.getPeriodCount()).isEqualTo(1);

    Period period = mpd.getPeriod(0);
    assertThat(period).isNotNull();
    assertThat(period.adaptationSets).hasSize(2);

    for (AdaptationSet adaptationSet : period.adaptationSets) {
      assertThat(adaptationSet).isNotNull();
      for (Representation representation : adaptationSet.representations) {
        if (representation instanceof Representation.MultiSegmentRepresentation) {
          Representation.MultiSegmentRepresentation multiSegmentRepresentation =
              (Representation.MultiSegmentRepresentation) representation;
          int firstSegmentIndex = multiSegmentRepresentation.getFirstSegmentNum();
          RangedUri uri = multiSegmentRepresentation.getSegmentUrl(firstSegmentIndex);
          assertThat(
                  uri.resolveUriString(representation.baseUrl)
                      .contains("redirector.googlevideo.com"))
              .isTrue();
        }
      }
    }
  }

  public void testParseMediaPresentationDescriptionCanParseEventStream()
      throws IOException {
    DashManifestParser parser = new DashManifestParser();
    DashManifest mpd = parser.parse(Uri.parse("https://example.com/test.mpd"),
        TestUtil.getInputStream(getInstrumentation(), SAMPLE_MPD_4_EVENT_STREAM));

    Period period = mpd.getPeriod(0);
    assertThat(period.eventStreams).hasSize(3);

    // assert text-only event stream
    EventStream eventStream1 = period.eventStreams.get(0);
    assertThat(eventStream1.events.length).isEqualTo(1);
    EventMessage expectedEvent1 = new EventMessage("urn:uuid:XYZY", "call", 10000, 0,
        "+ 1 800 10101010".getBytes(), 0);
    assertThat(eventStream1.events[0]).isEqualTo(expectedEvent1);

    // assert CData-structured event stream
    EventStream eventStream2 = period.eventStreams.get(1);
    assertThat(eventStream2.events.length).isEqualTo(1);
    assertThat(eventStream2.events[0])
        .isEqualTo(
            new EventMessage(
                "urn:dvb:iptv:cpm:2014",
                "",
                1500000,
                1,
                ("<![CDATA[<BroadcastEvent>\n"
                        + "      <Program crid=\"crid://broadcaster.example.com/ABCDEF\"/>\n"
                        + "      <InstanceDescription>\n"
                        + "      <Title xml:lang=\"en\">The title</Title>\n"
                        + "      <Synopsis xml:lang=\"en\" length=\"medium\">"
                        + "The description</Synopsis>\n"
                        + "      <ParentalGuidance>\n"
                        + "      <mpeg7:ParentalRating href=\"urn:dvb:iptv:rating:2014:15\"/>\n"
                        + "      <mpeg7:Region>GB</mpeg7:Region>\n"
                        + "      </ParentalGuidance>\n"
                        + "      </InstanceDescription>\n"
                        + "      </BroadcastEvent>]]>")
                    .getBytes(),
                300000000));

    // assert xml-structured event stream
    EventStream eventStream3 = period.eventStreams.get(2);
    assertThat(eventStream3.events.length).isEqualTo(1);
    assertThat(eventStream3.events[0])
        .isEqualTo(
            new EventMessage(
                "urn:scte:scte35:2014:xml+bin",
                "",
                1000000,
                2,
                ("<scte35:Signal>\n"
                        + "         <scte35:Binary>\n"
                        + "         /DAIAAAAAAAAAAAQAAZ/I0VniQAQAgBDVUVJQAAAAH+cAAAAAA==\n"
                        + "         </scte35:Binary>\n"
                        + "       </scte35:Signal>")
                    .getBytes(),
                1000000000));
  }

  public void testParseCea608AccessibilityChannel() {
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC1=eng")))
        .isEqualTo(1);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC2=eng")))
        .isEqualTo(2);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC3=eng")))
        .isEqualTo(3);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC4=eng")))
        .isEqualTo(4);

    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors(null)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC0=eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("CC5=eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea608AccessibilityChannel(
                buildCea608AccessibilityDescriptors("Wrong format")))
        .isEqualTo(Format.NO_VALUE);
  }

  public void testParseCea708AccessibilityChannel() {
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("1=lang:eng")))
        .isEqualTo(1);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("2=lang:eng")))
        .isEqualTo(2);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("3=lang:eng")))
        .isEqualTo(3);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("62=lang:eng")))
        .isEqualTo(62);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("63=lang:eng")))
        .isEqualTo(63);

    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors(null)))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("0=lang:eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("64=lang:eng")))
        .isEqualTo(Format.NO_VALUE);
    assertThat(
            DashManifestParser.parseCea708AccessibilityChannel(
                buildCea708AccessibilityDescriptors("Wrong format")))
        .isEqualTo(Format.NO_VALUE);
  }

  private static List<Descriptor> buildCea608AccessibilityDescriptors(String value) {
    return Collections.singletonList(new Descriptor("urn:scte:dash:cc:cea-608:2015", value, null));
  }

  private static List<Descriptor> buildCea708AccessibilityDescriptors(String value) {
    return Collections.singletonList(new Descriptor("urn:scte:dash:cc:cea-708:2015", value, null));
  }

}
