/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.MultiFormatReader;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Detection followed by decoding, from an image rather than a module matrix.
 *
 * <p>The symbols are upstream's own decoder vectors, rendered as images at
 * several module sizes with a quiet zone around them. Recovering the original
 * text means the detector measured the module size, worked out which of the 32
 * shapes it was looking at, and sampled every module at the right place --
 * none of which it is told.</p>
 */
public final class RMQRDetectorTestCase {

  private static final String[] R7X43M = {
      "XXXXXXX X X X X X X XXX X X X X X X X X XXX",
      "X     X  X XXX  XXXXX XXX      X X XX   X X",
      "X XXX X X XXX X X X XXXX XXXX X  X XXXXXXXX",
      "X XXX X  XX    XXXXX   XXXXXX   X X   X   X",
      "X XXX X   XX  XXX   XXXXXXX  X X  XX  X X X",
      "X     X XXXXX XXX XXX XXXXX    XXXXXX X   X",
      "XXXXXXX X X X X X X XXX X X X X X X X XXXXX",
  };

  private static final String[] R9X59H = {
      "XXXXXXX X X X X X XXX X X X X X X X X XXX X X X X X X X XXX",
      "X     X    X  XXXXX XXX X  X XXXXXXXX X X  X    X XXXX  X X",
      "X XXX X XX XXX  X XXX XXXX  X         XXXXXXX  X XXXXX X  X",
      "X XXX X XXXX X XX X   XX   XXXX XX  XX   X  X  X XXX     X ",
      "X XXX X    X    X XX XXXXXX X X XX   X XX   X X XXXX  XXXXX",
      "X     X X  X  X  X  XXX X X   X   XX  X XXXX XX  X X  X   X",
      "XXXXXXX  XXXXX  XXXXXX X XX XXX X    XXXX  X    X  X XX X X",
      "          XXX  XXXX XX XXX    X XXXXXXX X XX XXX  XX XX   X",
      "XXX X X X X X X X XXX X X X X X X X X XXX X X X X X X XXXXX",
  };

  private static final String[] R17X99H = {
      "XXXXXXX X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X XXX",
      "X     X   X XXXXX XXX X X  X XX X X  XX  XXXXX  X XX   X XX XXX X X XX X  X XXX     X X XX   X  X X",
      "X XXX X X X   XXX     XXX X XXX XXX     X  X XX XXXX X  X X  X      XXX   XXXXX X    X XX X XX  X X",
      "X XXX X   XX X  XX X    X   XX   X  XXXX X  XXXXX  X  X    XX X XXX XX X X       X  X   XXXXXX X   ",
      "X XXX X    X XX  X X X X X  X   X X  XXX    XX XXXXXX X    X   XXX  X XXXXXX X   X X X X X X  XX  X",
      "X     X XX  X   X XXXXX  XX   X XXX  X XX   X X    XXX X  XXX  XXX X  XXXX  XX     X X X XX   XXXX ",
      "XXXXXXX X XX X      XX X X  XXX XX  X XXXX    X  X  XXX X X XX X XXXX  XX  X   X        X XX X XXXX",
      "        XX XX XX XX  X  XX  X    X  X XXX XX    X     X  XXX     XXXX  XX X X  X      X XX XX  XXX ",
      "XX       X XXX  X   X XXXX XXX XXXXX  XXX  XXX   X X X  X   X  XXX X  XX  XX X   X X  X  XX  X  XXX",
      " X   XXXXX X  X   XXXXX X  XX       X XX XXXX   X     X XXXXX X XX X  XX  X XX   X XX           XX ",
      "X XX XX   X  XX   XXX  XX XXXXXX X  XXXXX  XX    XXXX  X X X   X XXXX  XX  X   X  XXXXXX    XX  X X",
      " XXX XX  XXX  XX  XX X   X X XX  X X X X XX   XXX XXXX      X XX  XXX X X X XXXX    XXXXX  X XXX   ",
      "X  X  XX    X      XX XX  XX X X XX  X    X X XX XXXXXXXX X XX XX  X   X   X X X XX X X XXXXXXXXXXX",
      "    X X    X XX    X X   X XX XXXX    X XXX  X XX X X X   X X  XXX XXXXX    XX X X  X XXXXX X X   X",
      "XXXX XX XX   X  XXXX XXXX  X XX    X  XX  XX XX XXXX XXX X      X XX XX X XXXX   X XXX  XX X XX X X",
      "X XXX XX  XXX X X X XXX X  XXX   X XXXX  XX     X X  XXXXX X XX X  X X X  X X X X XXXX     XXXX   X",
      "XXX X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X XXXXX",
  };

  @Test
  public void testDetectsAndDecodesUpstreamSymbols() throws Exception {
    assertRoundTrip(R7X43M, "ABCDEFG", 43, 7);
    assertRoundTrip(R9X59H, "ABCDEFGHIJKLMN", 59, 9);
    assertRoundTrip(R17X99H, "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890________________________", 99, 17);
  }

  @Test
  public void testRejectsAnImageThatIsNotASymbol() {
    BitMatrix noise = new BitMatrix(60, 20);
    for (int y = 0; y < 20; y++) {
      for (int x = 0; x < 60; x++) {
        if (((x * 7 + y * 13) % 5) == 0) {
          noise.set(x, y);
        }
      }
    }
    try {
      RMQRDetector.detectPure(noise);
      fail("noise is not an rMQR symbol");
    } catch (NotFoundException expected) {
      // Measuring the module size from the finder, the subpattern and all
      // four timing patterns is what makes this cheap to refuse.
    }
  }

  @Test
  public void testRejectsASquareSymbol() {
    // A QR-shaped image must not be mistaken for rMQR, which is never square.
    BitMatrix square = new BitMatrix(21, 21);
    for (int i = 0; i < 21; i++) {
      square.set(i, 0);
      square.set(0, i);
    }
    try {
      RMQRDetector.detectPure(square);
      fail("rMQR is never square");
    } catch (NotFoundException expected) {
      // as designed
    }
  }

  @Test
  public void testReadsThroughThePublicApi() throws Exception {
    // The whole path a caller actually uses: a luminance source, a binarizer,
    // MultiFormatReader, and rMQR requested by name.
    BitMatrix modules = render(R7X43M, 6, 24);
    int width = modules.getWidth();
    int height = modules.getHeight();
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixels[y * width + x] = modules.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
      }
    }

    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.RMQR_CODE));

    Result result = new MultiFormatReader().decode(bitmap, hints);
    assertEquals("ABCDEFG", result.getText());
    assertEquals(BarcodeFormat.RMQR_CODE, result.getBarcodeFormat());
    assertEquals("]Q1", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  private static void assertRoundTrip(String[] rows, String expectedText, int width, int height)
      throws Exception {
    // Several module sizes, because the detector derives it rather than
    // being told, and a quiet zone, because a real image has one.
    for (int moduleSize : new int[] {1, 2, 3, 5, 8}) {
      BitMatrix image = render(rows, moduleSize, 4 * moduleSize);
      DetectorResult detected = RMQRDetector.detectPure(image);

      assertEquals("width at module size " + moduleSize, width, detected.getBits().getWidth());
      assertEquals("height at module size " + moduleSize, height, detected.getBits().getHeight());
      assertEquals("text at module size " + moduleSize,
          expectedText, RMQRDecoder.decode(detected.getBits()).getText());
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
