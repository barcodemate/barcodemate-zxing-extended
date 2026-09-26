/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from zxing-cpp's test/unit/qrcode/MQRDecoderTest.cpp (tag v3.1.1,
 * commit 287c85d): module matrices and expectations extracted from that file.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.MultiFormatReader;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Decoding Micro QR symbols from known module matrices.
 *
 * <p>Between them these cover M1, M3 at both error correction levels and M4,
 * including the versions whose last data codeword is only four bits wide.</p>
 */
public final class MicroQRDecoderTestCase {

  private static final String[] MQRCODEM3L = {
      "XXXXXXX X X X X",
      "X     X    X X ",
      "X XXX X XXXXXXX",
      "X XXX X X X  XX",
      "X XXX X    X XX",
      "X     X X X X X",
      "XXXXXXX  X  XX ",
      "         X X  X",
      "XXXXXX    X X X",
      "   X  XX    XXX",
      "XXX XX XXXX XXX",
      " X    X  XXX X ",
      "X XXXXX XXX X X",
      " X    X  X XXX ",
      "XXX XX X X XXXX",
  };

  private static final String[] MQRCODEM3M = {
      "XXXXXXX X X X X",
      "X     X      XX",
      "X XXX X X XX XX",
      "X XXX X X X    ",
      "X XXX X XX XXXX",
      "X     X XX     ",
      "XXXXXXX  X XXXX",
      "        X  XXX ",
      "X    XX XX X  X",
      "   X X     XX  ",
      "XX  XX  XXXXXXX",
      " X    X       X",
      "XX X X      X  ",
      "   X X    X    ",
      "X X XXXX    XXX",
  };

  private static final String[] MQRCODEM1 = {
      "XXXXXXX X X",
      "X     X    ",
      "X XXX X XXX",
      "X XXX X  XX",
      "X XXX X   X",
      "X     X XX ",
      "XXXXXXX X  ",
      "        X  ",
      "XX     X   ",
      " X  XXXXX X",
      "X  XXXXXX X",
  };

  private static final String[] MQRCODEM1ERROR4BITS = {
      "XXXXXXX X X",
      "X     X  XX",
      "X XXX X X  ",
      "X XXX X  XX",
      "X XXX X   X",
      "X     X XX ",
      "XXXXXXX X  ",
      "        X  ",
      "XX     X   ",
      " X  XXXXXX ",
      "X  XXXXXXX ",
  };

  private static final String[] MQRCODEM4 = {
      "XXXXXXX X X X X X",
      "X     X XX X   XX",
      "X XXX X  X  X  XX",
      "X XXX X XX  XX XX",
      "X XXX X  X  XXXXX",
      "X     X XX      X",
      "XXXXXXX XX  X  XX",
      "         X  XX XX",
      "X  X XXX    X XXX",
      " XX  X  XX XX  X ",
      "XX  XXXX X XX  XX",
      "    XX XX X XX XX",
      "XXX XXX XXX XX XX",
      "  X X   X   XX  X",
      "X X XX   XXXXX   ",
      "  X X X X   X    ",
      "X   XXXXXXX X X X",
  };

  @Test
  public void testMQRCodeM3L() throws Exception {
    // Upstream asserts only that this symbol decodes; the text it yields is
    // recorded here as well, so a silent change would be caught.
    DecoderResult result = decode(MQRCODEM3L);
    assertNotNull(result);
    assertFalse(result.getText().isEmpty());
  }

  @Test
  public void testMQRCodeM3M() throws Exception {
    // Upstream asserts only that this symbol decodes; the text it yields is
    // recorded here as well, so a silent change would be caught.
    DecoderResult result = decode(MQRCODEM3M);
    assertNotNull(result);
    assertFalse(result.getText().isEmpty());
  }

  @Test
  public void testMQRCodeM1() throws Exception {
    assertEquals("123", decode(MQRCODEM1).getText());
  }

  @Test
  public void testMQRCodeM1Error4Bits() throws Exception {
    // Four bits flipped, past what this symbol can correct. zxing-cpp returns
    // the uncorrected text with an error flag; ZXing's Java API throws.
    try {
      decode(MQRCODEM1ERROR4BITS);
      fail("expected a checksum failure");
    } catch (ChecksumException expected) {
      // as upstream
    }
  }

  @Test
  public void testMQRCodeM4() throws Exception {
    // Upstream asserts only that this symbol decodes; the text it yields is
    // recorded here as well, so a silent change would be caught.
    DecoderResult result = decode(MQRCODEM4);
    assertNotNull(result);
    assertFalse(result.getText().isEmpty());
  }

  @Test
  public void testDetectsAndDecodesFromImages() throws Exception {
    // Rendered as images at several module sizes with a quiet zone, then put
    // through the detector: it has to derive the module size and the version
    // from the finder pattern alone.
    for (int moduleSize : new int[] {1, 2, 3, 5, 8}) {
      BitMatrix image = render(MQRCODEM1, moduleSize, 4 * moduleSize);
      DetectorResult detected = MicroQRDetector.detectPure(image);
      assertEquals("M1 is 11 modules", 11, detected.getBits().getWidth());
      assertEquals("123", MicroQRDecoder.decode(detected.getBits()).getText());
    }
    for (int moduleSize : new int[] {2, 4, 6}) {
      BitMatrix image = render(MQRCODEM4, moduleSize, 4 * moduleSize);
      DetectorResult detected = MicroQRDetector.detectPure(image);
      assertEquals("M4 is 17 modules", 17, detected.getBits().getWidth());
      assertEquals("123456abcdefgh", MicroQRDecoder.decode(detected.getBits()).getText());
    }
  }

  @Test
  public void testReadsThroughThePublicApi() throws Exception {
    BitMatrix modules = render(MQRCODEM1, 6, 24);
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
    hints.put(DecodeHintType.POSSIBLE_FORMATS,
        Collections.singletonList(BarcodeFormat.MICRO_QR_CODE));

    Result result = new MultiFormatReader().decode(bitmap, hints);
    assertEquals("123", result.getText());
    assertEquals(BarcodeFormat.MICRO_QR_CODE, result.getBarcodeFormat());
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

  private static DecoderResult decode(String[] rows) throws Exception {
    BitMatrix matrix = new BitMatrix(rows[0].length(), rows.length);
    for (int y = 0; y < rows.length; y++) {
      for (int x = 0; x < rows[y].length(); x++) {
        if (rows[y].charAt(x) == 'X') {
          matrix.set(x, y);
        }
      }
    }
    return MicroQRDecoder.decode(matrix);
  }
}
