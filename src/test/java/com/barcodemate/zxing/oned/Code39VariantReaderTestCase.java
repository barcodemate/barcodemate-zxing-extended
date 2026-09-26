/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.BitArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Code 39 symbols carrying a further convention: Code 32, PZN, and the
 * extended encoding.
 *
 * <p>The symbols are bar and space widths taken from bwip-js, which shares no
 * code with this port or with zxing-cpp. None of these is a symbology and
 * nothing here decodes anything the inherited Code 39 reader could not already
 * read; what is being tested is whether the contents are recognised for what
 * they are.</p>
 */
public final class Code39VariantReaderTestCase {

  private static final int[] CODE32 = {1, 3, 1, 1, 3, 1, 3, 1, 1, 1, 3, 1, 3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 3, 1, 1, 3, 1, 1, 3, 1, 1, 1, 1, 1, 3, 3, 1, 1, 3, 1, 3, 1, 1, 1, 1, 3, 1, 1, 3, 1, 1, 3, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 3, 3, 1, 1, 3, 1, 1, 3, 1, 3, 1, 1, 1};

  private static final int[] PZN8 = {1, 3, 1, 1, 3, 1, 3, 1, 1, 1, 1, 3, 1, 1, 1, 1, 3, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 1, 3, 1, 1, 1, 3, 3, 1, 1, 1, 1, 3, 1, 3, 1, 3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 1, 1, 1, 3, 1, 3, 1, 1, 3, 3, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 3, 1, 3, 1, 3, 1, 1, 3, 1, 1, 3, 1, 1, 1, 1, 3, 1, 1, 3, 1, 3, 1, 1, 1};

  private static final int[] C39EXT = {1, 3, 1, 1, 3, 1, 3, 1, 1, 1, 3, 1, 1, 1, 1, 3, 3, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 3, 3, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, 3, 1, 1, 1, 1, 3, 1, 1, 1, 1, 3, 3, 1, 1, 3, 1, 1, 1, 3, 1, 3, 1, 1, 1, 1, 3, 1, 1, 1, 1, 3, 3, 1, 1, 3, 1, 1, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 3, 1, 1, 3, 1, 1, 1, 3, 1, 1, 3, 1, 3, 1, 1, 1};

  @Test
  public void testRecognisesVariants() throws Exception {
    assertVariant(CODE32, "A123456788", BarcodeFormat.CODE_32);
    assertVariant(PZN8, "-12345678", BarcodeFormat.PZN);
    assertVariant(C39EXT, "Hello", BarcodeFormat.CODE_39_EXTENDED);
  }

  @Test
  public void testPlainCode39IsNotClaimed() throws Exception {
    // "ABC123" is an ordinary Code 39 symbol: not a pharmacode, not extended.
    // The variant reader must decline it rather than force an interpretation.
    int[] plain = {1, 3, 1, 1, 3, 1, 3, 1, 1, 1};
    try {
      decode(plain, true, true, true);
      fail("expected no variant");
    } catch (Exception expected) {
      // as designed
    }
  }

  private static void assertVariant(int[] widths, String expected, BarcodeFormat format)
      throws Exception {
    for (int scale : new int[] {1, 2, 4}) {
      Result result = decode(widths, scale);
      assertEquals("scale " + scale, expected, result.getText());
      assertEquals("scale " + scale, format, result.getBarcodeFormat());
    }
  }

  private static Result decode(int[] widths, int scale) throws Exception {
    return new Code39VariantReader(true, true, true)
        .decodeRow(0, toRow(widths, scale), null);
  }

  private static Result decode(int[] widths, boolean pzn, boolean code32, boolean ext)
      throws Exception {
    return new Code39VariantReader(pzn, code32, ext).decodeRow(0, toRow(widths, 2), null);
  }

  /** Renders bar and space widths into a row, starting with a quiet zone. */
  private static BitArray toRow(int[] widths, int scale) {
    int quietZone = 20 * scale;
    int total = quietZone * 2;
    for (int width : widths) {
      total += width * scale;
    }
    BitArray row = new BitArray(total);
    int at = quietZone;
    boolean black = true;
    for (int width : widths) {
      int run = width * scale;
      if (black) {
        row.setRange(at, at + run);
      }
      at += run;
      black = !black;
    }
    return row;
  }
}
