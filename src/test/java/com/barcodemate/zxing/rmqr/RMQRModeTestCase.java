/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from zxing-cpp's test/unit/qrcode/QRModeTest.cpp (tag v3.1.1, commit
 * 287c85d): same vectors, same expectations.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.FormatException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RMQRModeTestCase {

  @Test
  public void testForBits() throws Exception {
    assertEquals(RMQRMode.TERMINATOR, RMQRMode.forBits(0x00));
    assertEquals(RMQRMode.NUMERIC, RMQRMode.forBits(0x01));
    assertEquals(RMQRMode.ALPHANUMERIC, RMQRMode.forBits(0x02));
    assertEquals(RMQRMode.BYTE, RMQRMode.forBits(0x03));
    assertEquals(RMQRMode.KANJI, RMQRMode.forBits(0x04));
    assertEquals(RMQRMode.FNC1_FIRST_POSITION, RMQRMode.forBits(0x05));
    assertEquals(RMQRMode.FNC1_SECOND_POSITION, RMQRMode.forBits(0x06));
    assertEquals(RMQRMode.ECI, RMQRMode.forBits(0x07));
  }

  @Test
  public void testUndefinedModeIsRejected() {
    try {
      RMQRMode.forBits(0x08);
      fail("0x08 is not a defined rMQR mode");
    } catch (FormatException expected) {
      // The mode indicator is three bits, so 8 cannot occur in a valid symbol;
      // reaching here means the bit stream is corrupt and saying so beats
      // guessing.
    }
  }

  @Test
  public void testCharacterCountBits() {
    assertEquals(7, RMQRMode.NUMERIC.getCharacterCountBits(RMQRVersion.forNumber(5)));
    assertEquals(8, RMQRMode.NUMERIC.getCharacterCountBits(RMQRVersion.forNumber(26)));
    assertEquals(9, RMQRMode.NUMERIC.getCharacterCountBits(RMQRVersion.forNumber(32)));
    assertEquals(5, RMQRMode.ALPHANUMERIC.getCharacterCountBits(RMQRVersion.forNumber(6)));
    assertEquals(5, RMQRMode.BYTE.getCharacterCountBits(RMQRVersion.forNumber(7)));
    assertEquals(5, RMQRMode.KANJI.getCharacterCountBits(RMQRVersion.forNumber(8)));
  }

  @Test
  public void testCountWidthsAreOrderedByCapacity() {
    // Not an upstream test. A wider mode indicator cannot need fewer bits than
    // a narrower one for the same version: numeric packs the most characters
    // per codeword and so needs the widest count, kanji the fewest and the
    // narrowest. A table entry typed out of place would break this.
    for (int v = 1; v <= 32; v++) {
      RMQRVersion version = RMQRVersion.forNumber(v);
      int numeric = RMQRMode.NUMERIC.getCharacterCountBits(version);
      int alphanumeric = RMQRMode.ALPHANUMERIC.getCharacterCountBits(version);
      int byteMode = RMQRMode.BYTE.getCharacterCountBits(version);
      int kanji = RMQRMode.KANJI.getCharacterCountBits(version);
      assertTrue("v" + v, numeric >= alphanumeric);
      assertTrue("v" + v, alphanumeric >= byteMode);
      assertTrue("v" + v, byteMode >= kanji);
    }
  }
}
