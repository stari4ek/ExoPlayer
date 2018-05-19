package com.google.android.exoplayer2.demo;


import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;

import java.util.List;

import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

/**
 * TVirl: add own functionality
 */
public class PlayerActivityExt extends PlayerActivity {


    @Override
    MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {

        int type = Util
            .inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                                  : uri.getLastPathSegment());
        if (type == C.TYPE_OTHER) {
            return new ExtractorMediaSource
                .Factory(mediaDataSourceFactory)
                .setExtractorsFactory(
                    new ExtractorsFactory() {

                        @Override
                        public Extractor[] createExtractors() {
                            Extractor[] defaultExts = new DefaultExtractorsFactory().createExtractors();
                            for (int i = 0; i < defaultExts.length; ++i) {
                                // replace TS extractor
                                if (defaultExts[i] instanceof TsExtractor) {
                                    defaultExts[i] = new TsExtractor(
                                        TsExtractor.MODE_SINGLE_PMT,
                                        new TimestampAdjuster(0),
                                        new DefaultTsPayloadReaderFactory(
                                            FLAG_ALLOW_NON_IDR_KEYFRAMES|FLAG_DETECT_ACCESS_UNITS
                                        )
                                    );
                                }
                            }

                            return defaultExts;
                        }
                    }
                )
                .createMediaSource(uri);
        }

        return super.buildMediaSource(uri, overrideExtension);
    }
}
