/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.pdf417;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Compact PDF417 symbols, read by the inherited PDF417 reader.
 *
 * <p>This project's format comparison originally listed Compact PDF417 as a
 * gap, on the strength of ZXing-C++ having a {@code CompactPDF417} constant
 * that ZXing for Java does not. That was wrong, and this test is what
 * establishes it.</p>
 *
 * <p>ZXing-C++'s PDF417 reader never returns that constant either: it reports
 * {@code PDF417} for both, and {@code CompactPDF417} exists only to request
 * the reader and to name a generation target. Meanwhile the inherited Java
 * reader decodes compact symbols perfectly well, as the symbols below show --
 * they were produced by bwip-js as {@code pdf417compact}.</p>
 *
 * <p>So there is no reading gap here, and nothing to port. Writing this test
 * was cheaper than implementing a format that turned out already to work, and
 * it stops the question being reopened.</p>
 */
public final class CompactPDF417TestCase {

  private static final String[] CPDF_HELLO = {
      "XXXXXXXX X X X   XXXX X X XXXX    XXXX X X  XXXX   X XX XXX  XX     X",
      "XXXXXXXX X X X   XXXX X X    X    X XX  X XXXXXX   XXXX XX     XX X X",
      "XXXXXXXX X X X   XXX X X XXXXXX   X XX XX  XXXX    X XXX     X   XX X",
      "XXXXXXXX X X X   X X XXXX  XXXX   XXX X   X  XXX   X  XXXX XXXXX XX X",
      "XXXXXXXX X X X   XX X XXX     X   X  X XXXXXX  XXX X XX XXXXX X     X",
      "XXXXXXXX X X X   XXXX X XXXX X    X   XXXX XX XX   XXXX X   XXXX X  X",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
  };

  private static final String[] CPDF_DIGITS = {
      "XXXXXXXX X X X   XXXXX X X XXXXX  XX X X   XX      X  XXXX XXXX X   X",
      "XXXXXXXX X X X   XXXXX X X   XX   XXXXXX X XX XXXX XXX  X   XXX XX  X",
      "XXXXXXXX X X X   XXX X X XXXXXX   X   XXX     X XX X XX XX   XXXX   X",
      "XXXXXXXX X X X   XX X XXXX  XXXXX XX  XX XX   XX   XX   XX X   X    X",
      "XXXXXXXX X X X   XX X XXX    X    XX XX XXXX   X   XXXX X  X   X    X",
      "XXXXXXXX X X X   XXXX X XXXX X    XX   XX   X XXXX X X X   XXXX     X",
      "XXXXXXXX X X X   XXX X  XXX XXXXX X X    XX   XX   XXXX  XX X  XXX  X",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
  };

  private static final String[] CPDF_COLS2 = {
      "XXXXXXXX X X X   XXXXX X X XXXXX  XX X X    XX     XX X XXXX XXXXX  X",
      "XXXXXXXX X X X   XXXXXX X X   XXX XXXX   X  X    X X XX   XXXXX  X  X",
      "XXXXXXXX X X X   XXX X X XXXXXX   X  X X  XXXX     XX X XX   XXXXXX X",
      "XXXXXXXX X X X   XX X XXXX  XXXXX XX  X X   XX     X    XX   XX  X  X",
      "XXXXXXXX X X X   XXX X XXX    XX  XXXX  X X     X  X  X XXXX  X     X",
      "XXXXXXXX X X X   XXXX X XXXX X    X  XXX    X XX   X   XXX  X    XX X",
      "XXXXXXXX X X X   XXX X  XXX XXXXX XX  XX    X X    XX XX  X      X  X",
      "XXXXXXXX X X X   XXXXX X  X XX    X XX XXXXX X     XXXXX    XX  X X X",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
      "                                                                     ",
  };

  @Test
  public void testCompactSymbolsAreRead() throws Exception {
    assertReads(CPDF_HELLO, "HELLO");
    assertReads(CPDF_DIGITS, "1234567890");
    assertReads(CPDF_COLS2, "BARCODEMATE");
  }

  private static void assertReads(String[] rows, String expected) throws Exception {
    for (int moduleSize : new int[] {3, 5}) {
      BitMatrix image = render(rows, moduleSize, 6 * moduleSize);
      int width = image.getWidth();
      int height = image.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          pixels[y * width + x] = image.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        }
      }
      BinaryBitmap bitmap = new BinaryBitmap(
          new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
      Result result = new PDF417Reader().decode(bitmap);
      assertEquals("module size " + moduleSize, expected, result.getText());
      // Reported as PDF417, which is what ZXing-C++ reports for these too.
      assertEquals(BarcodeFormat.PDF_417, result.getBarcodeFormat());
    }
  }

  private static BitMatrix render(String[] rows, int moduleSize, int quietZone) {
    int width = rows[0].length() * moduleSize + 2 * quietZone;
    int height = rows.length * moduleSize + 2 * quietZone;
    BitMatrix image = new BitMatrix(width, height);
    for (int y = 0; y < rows.length; y++) {
      for (int x = 0; x < rows[y].length(); x++) {
        if (rows[y].charAt(x) != 'X') {
          continue;
        }
        for (int dy = 0; dy < moduleSize; dy++) {
          for (int dx = 0; dx < moduleSize; dx++) {
            image.set(quietZone + x * moduleSize + dx, quietZone + y * moduleSize + dy);
          }
        }
      }
    }
    return image;
  }
}
