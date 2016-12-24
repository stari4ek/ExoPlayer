package com.google.android.exoplayer2.demo;


import android.net.Uri;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.TimestampAdjuster;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Util;

import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

/**
 * TVirl: add own functionality
 */
public class PlayerActivityExt extends PlayerActivity {


    MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = Util
            .inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                                  : uri.getLastPathSegment());
        if (type == C.TYPE_OTHER) {
            return new ExtractorMediaSource(
                uri,
                mediaDataSourceFactory,
                new ExtractorsFactory() {

                    @Override
                    public Extractor[] createExtractors() {
                        Extractor[] defaultExts = new DefaultExtractorsFactory().createExtractors();
                        for (int i = 0; i < defaultExts.length; ++i) {
                            // replace TS extractor
                            if (defaultExts[i] instanceof TsExtractor) {
                                defaultExts[i] = new TsExtractor(
                                    new TimestampAdjuster(0),
                                    new DefaultTsPayloadReaderFactory(
                                        FLAG_ALLOW_NON_IDR_KEYFRAMES|FLAG_DETECT_ACCESS_UNITS
                                    ),
                                    false
                                );
                            }
                        }

                        return defaultExts;
                    }
                },
                mainHandler, eventLogger);
        }

        return super.buildMediaSource(uri, overrideExtension);
    }
}
