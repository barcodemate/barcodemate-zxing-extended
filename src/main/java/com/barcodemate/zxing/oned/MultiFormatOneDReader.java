/*
 * Copyright 2008 ZXing authors
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

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Reader;
import com.barcodemate.zxing.ReaderException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.BitArray;
import com.barcodemate.zxing.oned.rss.DataBarLimitedReader;
import com.barcodemate.zxing.oned.rss.RSS14Reader;
import com.barcodemate.zxing.oned.rss.expanded.RSSExpandedReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class MultiFormatOneDReader extends OneDReader {

  private static final OneDReader[] EMPTY_ONED_ARRAY = new OneDReader[0];

  private final OneDReader[] readers;

  public MultiFormatOneDReader(Map<DecodeHintType,?> hints) {
    @SuppressWarnings("unchecked")
    Collection<BarcodeFormat> possibleFormats = hints == null ? null :
        (Collection<BarcodeFormat>) hints.get(DecodeHintType.POSSIBLE_FORMATS);
    boolean useCode39CheckDigit = hints != null &&
        hints.get(DecodeHintType.ASSUME_CODE_39_CHECK_DIGIT) != null;
    Collection<OneDReader> readers = new ArrayList<>();
    if (possibleFormats != null) {
      if (possibleFormats.contains(BarcodeFormat.EAN_13) ||
          possibleFormats.contains(BarcodeFormat.UPC_A) ||
          possibleFormats.contains(BarcodeFormat.EAN_8) ||
          possibleFormats.contains(BarcodeFormat.UPC_E)) {
        readers.add(new MultiFormatUPCEANReader(hints));
      }
      if (possibleFormats.contains(BarcodeFormat.CODE_39)) {
        readers.add(new Code39Reader(useCode39CheckDigit));
      }
      // Code 32, PZN and Code 39 Extended are interpretations of a Code 39
      // symbol, not symbologies. This reader reports them; it decodes nothing
      // the inherited Code 39 reader could not already read.
      boolean pzn = possibleFormats.contains(BarcodeFormat.PZN);
      boolean code32 = possibleFormats.contains(BarcodeFormat.CODE_32);
      boolean code39Extended = possibleFormats.contains(BarcodeFormat.CODE_39_EXTENDED);
      if (pzn || code32 || code39Extended) {
        readers.add(new Code39VariantReader(pzn, code32, code39Extended));
      }
      if (possibleFormats.contains(BarcodeFormat.CODE_93)) {
        readers.add(new Code93Reader());
      }
      if (possibleFormats.contains(BarcodeFormat.CODE_128)) {
        readers.add(new Code128Reader());
      }
      if (possibleFormats.contains(BarcodeFormat.ITF)) {
        readers.add(new ITFReader());
      }
      if (possibleFormats.contains(BarcodeFormat.CODABAR)) {
        readers.add(new CodaBarReader());
      }
      if (possibleFormats.contains(BarcodeFormat.RSS_14)) {
        readers.add(new RSS14Reader());
      }
      if (possibleFormats.contains(BarcodeFormat.RSS_EXPANDED)) {
        readers.add(new RSSExpandedReader());
      }
      if (possibleFormats.contains(BarcodeFormat.DATA_BAR_LIMITED)) {
        readers.add(new DataBarLimitedReader());
      }
      // DX Film Edge is opt-in like Telepen: it is a niche symbology, and
      // unlike every other reader here it carries state between rows, so
      // scanning for it unconditionally would cost every caller something.
      if (possibleFormats.contains(BarcodeFormat.DX_FILM_EDGE)) {
        readers.add(new DXFilmEdgeReader());
      }
      // Telepen is deliberately absent from the default set below. Its start
      // pattern is ten narrow elements, which is weak enough that scanning for
      // it unconditionally would raise the misread rate of every other format.
      // Ask for it explicitly until that has been measured against the
      // falsepositives suites.
      boolean telepenAlpha = possibleFormats.contains(BarcodeFormat.TELEPEN)
          || possibleFormats.contains(BarcodeFormat.TELEPEN_ALPHA);
      boolean telepenNumeric = possibleFormats.contains(BarcodeFormat.TELEPEN)
          || possibleFormats.contains(BarcodeFormat.TELEPEN_NUMERIC);
      if (telepenAlpha || telepenNumeric) {
        readers.add(new TelepenReader(telepenAlpha, telepenNumeric));
      }
    }
    if (readers.isEmpty()) {
      readers.add(new MultiFormatUPCEANReader(hints));
      readers.add(new Code39Reader());
      readers.add(new CodaBarReader());
      readers.add(new Code93Reader());
      readers.add(new Code128Reader());
      readers.add(new ITFReader());
      readers.add(new RSS14Reader());
      readers.add(new RSSExpandedReader());
      readers.add(new DataBarLimitedReader());
    }
    this.readers = readers.toArray(EMPTY_ONED_ARRAY);
  }

  @Override
  public Result decodeRow(int rowNumber,
                          BitArray row,
                          Map<DecodeHintType,?> hints) throws NotFoundException {
    for (OneDReader reader : readers) {
      try {
        return reader.decodeRow(rowNumber, row, hints);
      } catch (ReaderException re) {
        // continue
      }
    }

    throw NotFoundException.getNotFoundInstance();
  }

  @Override
  public void reset() {
    for (Reader reader : readers) {
      reader.reset();
    }
  }

}
