/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * The function pattern expectations are those of zxing-cpp's
 * test/unit/qrcode/QRVersionTest.cpp (tag v3.1.1, commit 287c85d), extracted
 * from that file rather than retyped.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.common.BitMatrix;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The 32 rMQR symbol formats and the function pattern each one lays down.
 *
 * <p>The function pattern is the mask of module positions that carry structure
 * rather than data, so getting it wrong does not produce a detection failure --
 * it produces a symbol that is read with the data bits offset, which decodes to
 * nothing or, worse, to something. Upstream's expectations are asserted here
 * verbatim.</p>
 */
public final class RMQRVersionTestCase {

  private static final String[] PATTERN_V1 = {
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "XXXXXXXXXXXX        XXX            XXXXXXXX",
      "XXXXXXXXXXXX        XXX            XXXXXXXX",
      "XXXXXXXXXXXX         X             XXXXXXXX",
      "XXXXXXXXXXX         XXX            XXXXXXXX",
      "XXXXXXXXXXX         XXX            XXXXXXXX",
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  };

  private static final String[] PATTERN_V6 = {
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "XXXXXXXXXXXX        XXX                  XX",
      "XXXXXXXXXXXX        XXX                   X",
      "XXXXXXXXXXXX         X             XXXXXX X",
      "XXXXXXXXXXX          X             XXXXXXXX",
      "XXXXXXXXXXX          X             XXXXXXXX",
      "XXXXXXXX            XXX            XXXXXXXX",
      "XXXXXXXX            XXX            XXXXXXXX",
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  };

  private static final String[] PATTERN_V11 = {
      "XXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "XXXXXXXXXXXX             XX",
      "XXXXXXXXXXXX              X",
      "XXXXXXXXXXXX              X",
      "XXXXXXXXXXX               X",
      "XXXXXXXXXXX        XXXXXX X",
      "XXXXXXXX           XXXXXXXX",
      "XXXXXXXX           XXXXXXXX",
      "X                  XXXXXXXX",
      "XX                 XXXXXXXX",
      "XXXXXXXXXXXXXXXXXXXXXXXXXXX",
  };

  private static final String[] PATTERN_V12 = {
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "XXXXXXXXXXXX        XXX                  XX",
      "XXXXXXXXXXXX        XXX                   X",
      "XXXXXXXXXXXX         X                    X",
      "XXXXXXXXXXX          X                    X",
      "XXXXXXXXXXX          X             XXXXXX X",
      "XXXXXXXX             X             XXXXXXXX",
      "XXXXXXXX             X             XXXXXXXX",
      "X                   XXX            XXXXXXXX",
      "XX                  XXX            XXXXXXXX",
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  };

  private static final String[] PATTERN_V13 = {
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "XXXXXXXXXXXX      XXX                 XXX                XX",
      "XXXXXXXXXXXX      XXX                 XXX                 X",
      "XXXXXXXXXXXX       X                   X                  X",
      "XXXXXXXXXXX        X                   X                  X",
      "XXXXXXXXXXX        X                   X           XXXXXX X",
      "XXXXXXXX           X                   X           XXXXXXXX",
      "XXXXXXXX           X                   X           XXXXXXXX",
      "X                 XXX                 XXX          XXXXXXXX",
      "XX                XXX                 XXX          XXXXXXXX",
      "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  };

  @Test
  public void testVersionForNumber() {
    assertNull("there is no version 0", RMQRVersion.forNumber(0));
    assertNull("there are only 32 versions", RMQRVersion.forNumber(33));

    for (int i = 1; i <= 32; i++) {
      RMQRVersion version = RMQRVersion.forNumber(i);
      assertNotNull("version " + i, version);
      assertEquals(i, version.getVersionNumber());
      // Upstream's invariant: only the 27 module wide symbols are narrow
      // enough to carry no alignment patterns at all.
      assertEquals("version " + i + " alignment patterns",
          version.getWidth() == 27, version.getAlignmentPatternCenters().length == 0);
    }
  }

  @Test
  public void testErrorCorrectionLevelsAgreeOnSymbolCapacity() {
    // M and H differ in how the codewords are split between data and error
    // correction, never in how many there are. A transcription slip in the
    // table would almost certainly break this.
    for (int i = 1; i <= 32; i++) {
      RMQRVersion version = RMQRVersion.forNumber(i);
      assertEquals("version " + i,
          version.getECBlocks(false).getTotalCodewords(),
          version.getECBlocks(true).getTotalCodewords());
      assertTrue("version " + i, version.getECBlocks(false).getTotalDataCodewords()
          > version.getECBlocks(true).getTotalDataCodewords());
    }
  }

  @Test
  public void testSizeLookup() {
    assertEquals(1, RMQRVersion.forSize(43, 7).getVersionNumber());
    assertEquals(32, RMQRVersion.forSize(139, 17).getVersionNumber());
    assertNull(RMQRVersion.forSize(21, 21));

    assertTrue(RMQRVersion.isValidSize(43, 7));
    assertTrue(RMQRVersion.isValidSize(27, 11));
    // Square is never rMQR, nor are even dimensions or unlisted combinations.
    assertTrue(!RMQRVersion.isValidSize(43, 43));
    assertTrue(!RMQRVersion.isValidSize(44, 7));
    assertTrue(!RMQRVersion.isValidSize(27, 7));
  }

  @Test
  public void testFunctionPatterns() {
    assertFunctionPattern(1, PATTERN_V1);
    assertFunctionPattern(6, PATTERN_V6);
    assertFunctionPattern(11, PATTERN_V11);
    assertFunctionPattern(12, PATTERN_V12);
    assertFunctionPattern(13, PATTERN_V13);
  }

  private static void assertFunctionPattern(int versionNumber, String[] expected) {
    RMQRVersion version = RMQRVersion.forNumber(versionNumber);
    BitMatrix pattern = version.buildFunctionPattern();

    assertEquals("height of " + version, expected.length, pattern.getHeight());
    assertEquals("width of " + version, expected[0].length(), pattern.getWidth());

    String[] actual = new String[pattern.getHeight()];
    for (int y = 0; y < pattern.getHeight(); y++) {
      StringBuilder row = new StringBuilder();
      for (int x = 0; x < pattern.getWidth(); x++) {
        row.append(pattern.get(x, y) ? 'X' : ' ');
      }
      actual[y] = row.toString();
    }
    assertArrayEquals(version.toString(), expected, actual);
  }
}
