/*
 * Copyright 2016 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.FormatException;

/**
 * The four data encoding modes a Micro QR symbol may use.
 *
 * <p>The mode indicator narrows with the symbol: M1 has none at all and is
 * always numeric, M2 uses one bit, M3 two and M4 three. The character count
 * that follows shrinks the same way. In a symbol holding five codewords there
 * is nothing to spend on saying what kind of data follows.</p>
 */
public enum MicroQRMode {

  NUMERIC(0),
  ALPHANUMERIC(1),
  BYTE(2),
  KANJI(3);

  private static final MicroQRMode[] FOR_BITS = values();

  /** Character count widths per version, ISO/IEC 18004 Table 3. */
  private static final int[] NUMERIC_COUNT_BITS = {3, 4, 5, 6};
  private static final int[] ALPHANUMERIC_COUNT_BITS = {0, 3, 4, 5};
  private static final int[] BYTE_COUNT_BITS = {0, 0, 4, 5};
  private static final int[] KANJI_COUNT_BITS = {0, 0, 3, 4};

  private final int bits;

  MicroQRMode(int bits) {
    this.bits = bits;
  }

  public int getBits() {
    return bits;
  }

  public static MicroQRMode forBits(int bits) throws FormatException {
    if (bits < 0 || bits >= FOR_BITS.length) {
      throw FormatException.getFormatInstance();
    }
    return FOR_BITS[bits];
  }

  /** M1 has no mode indicator; each later version adds one bit. */
  public static int getModeBitsLength(MicroQRVersion version) {
    return version.getVersionNumber() - 1;
  }

  /** The terminator widens with the version: 3, 5, 7, 9 bits. */
  public static int getTerminatorBitsLength(MicroQRVersion version) {
    return version.getVersionNumber() * 2 + 1;
  }

  /**
   * @return the character count width for this mode in this version, or 0 if
   *         the version cannot carry this mode at all
   */
  public int getCharacterCountBits(MicroQRVersion version) {
    int index = version.getVersionNumber() - 1;
    switch (this) {
      case NUMERIC:
        return NUMERIC_COUNT_BITS[index];
      case ALPHANUMERIC:
        return ALPHANUMERIC_COUNT_BITS[index];
      case BYTE:
        return BYTE_COUNT_BITS[index];
      case KANJI:
        return KANJI_COUNT_BITS[index];
      default:
        return 0;
    }
  }
}
