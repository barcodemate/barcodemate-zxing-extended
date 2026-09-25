/*
 * Copyright 2023 gitlost
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRFormatInformation.cpp
 * (tag v3.1.1, commit 287c85d), the rMQR parts. The tables are ISO/IEC
 * 23941:2022 Annex C, Table C.1.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * The format information of an rMQR symbol: which of the 32 versions it is and
 * whether it carries M or H error correction.
 *
 * <p>Unlike QR, rMQR does not encode a version anywhere else. A QR symbol's
 * dimension gives away its version, and versions above 6 repeat it in a
 * dedicated field. An rMQR symbol's dimension does not: R7x43 and R9x43 differ
 * by two rows, which a detector working from one finder pattern cannot assume
 * it measured correctly. So the format information is how the version is
 * established, and it is written twice -- beside the finder pattern and beside
 * the finder subpattern in the opposite corner -- with 12 BCH check bits over 6
 * data bits each time.</p>
 *
 * <p>Decoding is therefore a search rather than a calculation: both copies are
 * compared against all 64 valid sequences, and the closest match across the two
 * wins. That recovers the version even when one copy is damaged, and it is why
 * a symbol whose format area is partly destroyed still reads.</p>
 */
public final class RMQRFormatInformation {

  /** Mask applied to the copy beside the finder pattern. */
  public static final int MASK_FINDER = 0x1FAB2;
  /** Mask applied to the copy beside the finder subpattern. */
  public static final int MASK_SUBPATTERN = 0x20A7B;

  /** rMQR always uses this data mask: ((y / 2) + (x / 3)) % 2 == 0. */
  public static final int DATA_MASK = 4;

  /** Valid sequences beside the finder pattern, ISO/IEC 23941 Table C.1. */
  private static final int[] MASKED_PATTERNS = {
      0x1FAB2, 0x1E597, 0x1DBDD, 0x1C4F8, 0x1B86C, 0x1A749, 0x19903, 0x18626,
      0x17F0E, 0x1602B, 0x15E61, 0x14144, 0x13DD0, 0x122F5, 0x11CBF, 0x1039A,
      0x0F1CA, 0x0EEEF, 0x0D0A5, 0x0CF80, 0x0B314, 0x0AC31, 0x0927B, 0x08D5E,
      0x07476, 0x06B53, 0x05519, 0x04A3C, 0x036A8, 0x0298D, 0x017C7, 0x008E2,
      0x3F367, 0x3EC42, 0x3D208, 0x3CD2D, 0x3B1B9, 0x3AE9C, 0x390D6, 0x38FF3,
      0x376DB, 0x369FE, 0x357B4, 0x34891, 0x33405, 0x32B20, 0x3156A, 0x30A4F,
      0x2F81F, 0x2E73A, 0x2D970, 0x2C655, 0x2BAC1, 0x2A5E4, 0x29BAE, 0x2848B,
      0x27DA3, 0x26286, 0x25CCC, 0x243E9, 0x23F7D, 0x22058, 0x21E12, 0x20137
  };

  /** The same, beside the finder subpattern. */
  private static final int[] MASKED_PATTERNS_SUB = {
      0x20A7B, 0x2155E, 0x22B14, 0x23431, 0x248A5, 0x25780, 0x269CA, 0x276EF,
      0x28FC7, 0x290E2, 0x2AEA8, 0x2B18D, 0x2CD19, 0x2D23C, 0x2EC76, 0x2F353,
      0x30103, 0x31E26, 0x3206C, 0x33F49, 0x343DD, 0x35CF8, 0x362B2, 0x37D97,
      0x384BF, 0x39B9A, 0x3A5D0, 0x3BAF5, 0x3C661, 0x3D944, 0x3E70E, 0x3F82B,
      0x003AE, 0x01C8B, 0x022C1, 0x03DE4, 0x04170, 0x05E55, 0x0601F, 0x07F3A,
      0x08612, 0x09937, 0x0A77D, 0x0B858, 0x0C4CC, 0x0DBE9, 0x0E5A3, 0x0FA86,
      0x108D6, 0x117F3, 0x129B9, 0x1369C, 0x14A08, 0x1552D, 0x16B67, 0x17442,
      0x18D6A, 0x1924F, 0x1AC05, 0x1B320, 0x1CFB4, 0x1D091, 0x1EEDB, 0x1F1FE
  };

  private final int data;
  private final int hammingDistance;
  private final int mask;

  private RMQRFormatInformation(int data, int hammingDistance, int mask) {
    this.data = data;
    this.hammingDistance = hammingDistance;
    this.mask = mask;
  }

  /**
   * @param bits the 18 bits read beside the finder pattern
   * @param subBits the 18 bits read beside the finder subpattern, or 0 if only
   *        one copy could be read
   */
  public static RMQRFormatInformation decode(int bits, int subBits) {
    Best best = new Best();
    best.consider(bits, MASKED_PATTERNS, MASK_FINDER);
    if (subBits != 0) {
      best.consider(subBits, MASKED_PATTERNS_SUB, MASK_SUBPATTERN);
    }
    return new RMQRFormatInformation(best.data, best.hammingDistance, best.mask);
  }

  /** At most 3 wrong bits; beyond that the BCH code cannot be trusted. */
  public boolean isValid() {
    return hammingDistance <= 3;
  }

  /** Version 1 to 32, from the low 5 data bits. */
  public int getVersionNumber() {
    return (data & 0x1F) + 1;
  }

  public RMQRVersion getVersion() {
    return RMQRVersion.forNumber(getVersionNumber());
  }

  /** rMQR offers only M and H. */
  public ErrorCorrectionLevel getErrorCorrectionLevel() {
    // The single error correction bit is shifted to where QR's M and H sit,
    // so the shared enum can be reused.
    return ErrorCorrectionLevel.forBits(((data >> 5) & 1) << 1);
  }

  public int getDataMask() {
    return DATA_MASK;
  }

  /** Which of the two copies the winning match came from. */
  public int getMask() {
    return mask;
  }

  public int getHammingDistance() {
    return hammingDistance;
  }

  /**
   * Whether two readings describe the same symbol. Follows upstream in
   * comparing what the format information means -- data mask and error
   * correction level -- rather than the raw bits.
   */
  public boolean sameAs(RMQRFormatInformation other) {
    return other != null && getDataMask() == other.getDataMask()
        && getErrorCorrectionLevel() == other.getErrorCorrectionLevel();
  }

  @Override
  public String toString() {
    return "rMQR v" + getVersionNumber() + " " + getErrorCorrectionLevel()
        + " (hamming " + hammingDistance + ")";
  }

  /**
   * The valid sequence encoding {@code data}, for tests. The table is indexed
   * by the data bits themselves, which the entries satisfy by construction.
   */
  static int maskedPatternFor(int data, boolean subPattern) {
    return (subPattern ? MASKED_PATTERNS_SUB : MASKED_PATTERNS)[data];
  }

  /** Tracks the closest match seen across both copies. */
  private static final class Best {
    int data = 255;
    int hammingDistance = 255;
    int mask;

    void consider(int bits, int[] patterns, int patternMask) {
      for (int masked : patterns) {
        int pattern = masked ^ patternMask;
        // Both sides are unmasked before comparison, so the mask cancels and
        // the distance is simply between the bits read and the valid sequence.
        int distance = Integer.bitCount(bits ^ masked);
        if (distance < hammingDistance) {
          this.mask = patternMask;
          this.data = pattern >>> 12; // drop the 12 BCH check bits
          this.hammingDistance = distance;
        }
      }
    }
  }
}
