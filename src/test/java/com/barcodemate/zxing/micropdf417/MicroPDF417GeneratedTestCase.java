/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.micropdf417;

import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * MicroPDF417 symbols generated independently of this decoder.
 *
 * <p>Upstream ships no unit tests for this symbology and only three of its ten
 * sample images are upright, so the external evidence was thin. These symbols
 * were produced by bwip-js, an encoder with no shared code or lineage with
 * either this port or zxing-cpp, which makes them genuine third-party
 * verification rather than a second opinion from the same source.</p>
 */
public final class MicroPDF417GeneratedTestCase {

  private static final String[] MPDF_ABC = {
      "XXX XXX X X XXXXXX X XX    XXX XXX X X",
      "XXX  XX X XXX X X XXXXXX   XXX  XX X X",
      "XXXX XX X X X  XXX  XXX    XXXX XX X X",
      "XXXX  X X XXXXX X   X  XX  XXXX  X X X",
      "XXX   X X XXXXX  X  XXX  X XXX   X X X",
      "XX    X X X    XX   XX  X  XX    X X X",
      "XX   XX X X XXXXXX X XX    XX   XX X X",
      "XX   X  X X   XX X   XXX   XX   X  X X",
      "XXX  X  X X   X    X    X  XXX  X  X X",
      "XXXX X  X XX  X    XXXX XX XXXX X  X X",
      "XXXX X XX XXXXXX  X XX   X XXXX X XX X",
      "XXXX X X  X X   XX XX      XXXX X X  X",
      "XXX  X X  XXX      X  XX X XXX  X X  X",
      "XXX XX X  XX  XXXX X    XX XXX XX X  X",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
  };

  private static final String[] MPDF_NUM = {
      "XXX XXX X X XXXXXX X  XX   XXX XXX X X",
      "XXX  XX X XXX X X XXXXXX   XXX  XX X X",
      "XXXX XX X X XXX XXXX XX    XXXX XX X X",
      "XXXX  X X XXX      X  XX X XXXX  X X X",
      "XXX   X X X  X      X XXXX XXX   X X X",
      "XX    X X X    XX   XX  X  XX    X X X",
      "XX   XX X X XXXXXX X XX    XX   XX X X",
      "XX   X  X XX X  XX  XXXXXX XX   X  X X",
      "XXX  X  X XXX XX  X XX     XXX  X  X X",
      "XXXX X  X X   X XXXXX XX   XXXX X  X X",
      "XXXX X XX XX XXXXX X X     XXXX X XX X",
      "XXXX X X  XXXX X    X XXXX XXXX X X  X",
      "XXX  X X  X XX  XXXXX   X  XXX  X X  X",
      "XXX XX X  X XX   X     XXX XXX XX X  X",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
      "                                      ",
  };

  @Test
  public void testDecodesGeneratedSymbols() throws Exception {
    assertDecodes(MPDF_ABC, "ABCDEFG");
    assertDecodes(MPDF_NUM, "123456789");
  }

  private static void assertDecodes(String[] rows, String expected) throws Exception {
    // Several module sizes, since the detector derives it rather than being told.
    for (int moduleSize : new int[] {3, 4, 6}) {
      BitMatrix image = render(rows, moduleSize, 4 * moduleSize);
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
      Result result = new MicroPDF417Reader().decode(bitmap);
      assertEquals("module size " + moduleSize, expected, result.getText());
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
