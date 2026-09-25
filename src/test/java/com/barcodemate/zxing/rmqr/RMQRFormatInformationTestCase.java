/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from zxing-cpp's test/unit/qrcode/QRFormatInformationTest.cpp (tag
 * v3.1.1, commit 287c85d): same test vectors, same expectations.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Decoding rMQR format information, including from damaged copies.
 *
 * <p>This is the part that has to work before anything else can: rMQR carries
 * its version number nowhere but here, so a wrong answer is not a degraded
 * read, it is reading the symbol as the wrong shape entirely.</p>
 */
public final class RMQRFormatInformationTestCase {

  private static final int MASKED = 0x20137;
  private static final int MASKED_SUB = 0x1F1FE;

  /** Clears the lowest {@code numBits} set bits, as upstream's helper does. */
  private static int unsetBits(int formatInfoBits, int numBits) {
    for (int i = 0; i < 18 && numBits > 0; i++) {
      if ((formatInfoBits & (1 << i)) != 0) {
        formatInfoBits ^= 1 << i;
        numBits--;
      }
    }
    return formatInfoBits;
  }

  @Test
  public void testDecode() {
    RMQRFormatInformation info = RMQRFormatInformation.decode(MASKED, MASKED_SUB);
    assertTrue(info.isValid());
    assertEquals(4, info.getDataMask());
    assertEquals(ErrorCorrectionLevel.H, info.getErrorCorrectionLevel());
    assertEquals(RMQRFormatInformation.MASK_FINDER, info.getMask());
  }

  @Test
  public void testDecodeWithBitDifference() {
    RMQRFormatInformation expected = RMQRFormatInformation.decode(MASKED, MASKED_SUB);
    assertEquals(ErrorCorrectionLevel.H, expected.getErrorCorrectionLevel());

    // Up to four wrong bits across the two copies still read correctly.
    for (int damaged = 1; damaged <= 4; damaged++) {
      RMQRFormatInformation actual = RMQRFormatInformation.decode(
          unsetBits(MASKED, damaged), unsetBits(MASKED_SUB, damaged));
      assertTrue("with " + damaged + " bits cleared", expected.sameAs(actual));
    }

    // Five is beyond what the code can correct, and it says so rather than
    // returning a confident wrong answer.
    RMQRFormatInformation broken = RMQRFormatInformation.decode(
        unsetBits(MASKED, 5), unsetBits(MASKED_SUB, 5));
    assertFalse(expected.sameAs(broken));
    assertFalse(broken.isValid());
  }

  @Test
  public void testDecodeWithMisread() {
    RMQRFormatInformation expected = RMQRFormatInformation.decode(MASKED, MASKED_SUB);

    // The two copies are damaged unequally; the better one should win, which
    // is the entire reason the format information is written twice.
    RMQRFormatInformation finderBetter = RMQRFormatInformation.decode(
        unsetBits(MASKED, 2), unsetBits(MASKED_SUB, 4));
    assertTrue(expected.sameAs(finderBetter));
    assertEquals(RMQRFormatInformation.MASK_FINDER, finderBetter.getMask());

    RMQRFormatInformation subBetter = RMQRFormatInformation.decode(
        unsetBits(MASKED, 5), unsetBits(MASKED_SUB, 4));
    assertTrue(expected.sameAs(subBetter));
    assertEquals(RMQRFormatInformation.MASK_SUBPATTERN, subBetter.getMask());
  }

  @Test
  public void testEveryVersionRoundTrips() {
    // Not an upstream test. Each of the 64 valid sequences must decode to the
    // version and error correction level it encodes, which is what makes the
    // table trustworthy rather than merely present.
    for (int version = 1; version <= 32; version++) {
      for (boolean high : new boolean[] {false, true}) {
        int data = (version - 1) | (high ? 1 << 5 : 0);
        int bits = RMQRFormatInformation.maskedPatternFor(data, false);
        RMQRFormatInformation info = RMQRFormatInformation.decode(bits, 0);
        assertTrue("v" + version, info.isValid());
        assertEquals("v" + version, version, info.getVersionNumber());
        assertEquals("v" + version, high ? ErrorCorrectionLevel.H : ErrorCorrectionLevel.M,
            info.getErrorCorrectionLevel());
        assertEquals("v" + version, 0, info.getHammingDistance());
      }
    }
  }

}
