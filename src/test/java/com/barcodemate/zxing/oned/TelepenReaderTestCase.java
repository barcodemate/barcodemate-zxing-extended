/*
 * Copyright 2026 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from zxing-cpp's test/unit/oned/ODTelepenReaderTest.cpp (tag v3.1.1,
 * commit 287c85d). The module-width vectors and expected results are taken
 * from there unchanged.
 *
 * Upstream calls the reader's decodePattern() with a run-length row directly.
 * These tests instead render the same run lengths into a BitArray and go
 * through decodeRow(), so the port is exercised end to end, including the
 * start-pattern search and the quiet-zone checks that upstream's harness
 * bypasses.
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.common.BitArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class TelepenReaderTestCase {

  private static final int[][] STARTS = {
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3},
      {1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 3},
      {1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 1, 3},
  };
  private static final int[][] STOPS = {
      {3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1},
      {3, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1},
      {3, 1, 1, 1, 1, 3, 1, 1, 1, 1, 1},
  };

  /** Pixels per module. Any value works; a few keeps the variance checks honest. */
  private static final int SCALE = 4;

  @Test
  public void testAlpha() throws Exception {
    int[] data = {
        1, 1, 3, 1, 3, 1, 3, 3,             // A
        1, 3, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, // checksum
    };
    Result result = decode(1, data, new int[] {0});
    assertEquals("A", result.getText());
    assertEquals(BarcodeFormat.TELEPEN_ALPHA, result.getBarcodeFormat());
    assertEquals("]B0", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));

    // The same symbol followed by various amounts of trailing quiet zone.
    assertEquals("A", decode(1, data, new int[] {1}).getText());
    assertEquals("A", decode(1, data, new int[] {100}).getText());
    assertEquals("A", decode(1, data, new int[] {100, 5}).getText());
  }

  @Test
  public void testNumeric() throws Exception {
    int[] data = {
        1, 1, 3, 1, 1, 1, 3, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 3, 3, 3, 1,
        3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 1,
        3, 3, 3, 1, 1, 1, 1, 1, 1, 1,
    };
    Result result = decode(1, data, new int[] {0});
    assertEquals("466X33", result.getText());
    assertEquals(BarcodeFormat.TELEPEN_NUMERIC, result.getBarcodeFormat());
    assertEquals("]B1", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  @Test
  public void testNumericThenAlpha() throws Exception {
    int[] data = {
        1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, // 12
        3, 1, 3, 1, 1, 1, 3, 1, 1, 1,       // DLE
        1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 3, 1, // 3
        1, 1, 3, 3, 1, 1, 3, 1, 1, 1,       // checksum 21
    };
    Result result = decode(1, data, new int[] {0});
    assertEquals("123", result.getText());
    assertEquals("]B2", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  @Test
  public void testNumericWithControlCharacter() throws Exception {
    int[] data = {
        1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, // 12
        1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 3, 1, // SI (ASCII 15)
        1, 1, 1, 3, 1, 1, 1, 1, 1, 3, 1, 1, // 34
        3, 1, 1, 1, 1, 1, 3, 1, 3, 1,       // checksum 12
    };
    // Requesting numeric only: a control character below 16 would otherwise
    // make the full-ASCII guess win.
    Result result = decode(new TelepenReader(false, true), 1, data, new int[] {0});
    assertEquals("12\u000f34", result.getText());
    assertEquals(BarcodeFormat.TELEPEN_NUMERIC, result.getBarcodeFormat());
    assertEquals("]B1", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  @Test
  public void testAimStartNumeric() throws Exception {
    int[] data = {
        1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, // 12
        3, 1, 1, 3, 1, 3, 1, 1, 1, 1,       // checksum 88
    };
    Result result = decode(2, data, new int[] {0});
    assertEquals("12", result.getText());
    assertEquals("]B1", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  @Test
  public void testAimStartAlphaThenNumeric() throws Exception {
    int[] data = {
        1, 1, 3, 1, 3, 1, 3, 3,             // A
        3, 1, 3, 1, 1, 1, 3, 1, 1, 1,       // DLE
        1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, // 12
        1, 1, 1, 1, 1, 1, 3, 1, 3, 1, 1, 1, // checksum 7
    };
    Result result = decode(3, data, new int[] {0});
    assertEquals("A12", result.getText());
    assertEquals("]B4", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  @Test
  public void testWrongChecksum() throws Exception {
    int[] data = {
        1, 1, 3, 1, 3, 1, 3, 3, // A
        1, 1, 3, 1, 3, 1, 3, 3, // not the checksum for "A"
    };
    try {
      decode(1, data, new int[] {0});
      fail("expected ChecksumException");
    } catch (ChecksumException expected) {
      // A symbol that scans cleanly but fails its check digit must be
      // reported as such, not silently dropped as "not found".
    }
  }

  private static Result decode(int startVariant, int[] data, int[] tail)
      throws NotFoundException, ChecksumException, com.barcodemate.zxing.FormatException {
    return decode(new TelepenReader(), startVariant, data, tail);
  }

  private static Result decode(TelepenReader reader, int startVariant, int[] data, int[] tail)
      throws NotFoundException, ChecksumException, com.barcodemate.zxing.FormatException {
    return reader.decodeRow(0, toRow(buildPattern(startVariant, data, tail)), null);
  }

  private static int[] buildPattern(int startVariant, int[] data, int[] tail) {
    int[] start = STARTS[startVariant - 1];
    int[] stop = STOPS[startVariant - 1];
    int[] pattern = new int[1 + start.length + data.length + stop.length + tail.length];
    int at = 1; // pattern[0] is the leading white run, zero wide here
    System.arraycopy(start, 0, pattern, at, start.length);
    at += start.length;
    System.arraycopy(data, 0, pattern, at, data.length);
    at += data.length;
    System.arraycopy(stop, 0, pattern, at, stop.length);
    at += stop.length;
    System.arraycopy(tail, 0, pattern, at, tail.length);
    return pattern;
  }

  /** Renders run lengths into a row. Index 0 is white, then alternating. */
  private static BitArray toRow(int[] pattern) {
    int total = 0;
    for (int run : pattern) {
      total += run * SCALE;
    }
    BitArray row = new BitArray(Math.max(total, 1));
    int index = 0;
    boolean black = false;
    for (int run : pattern) {
      int width = run * SCALE;
      if (black && width > 0) {
        row.setRange(index, index + width);
      }
      index += width;
      black = !black;
    }
    return row;
  }
}
