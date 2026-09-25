/*
 * Copyright 2023 gitlost
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRCodecMode.cpp (tag
 * v3.1.1, commit 287c85d), the rMQR parts. The character count table is
 * ISO/IEC 23941:2022 7.4.1, Table 3.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.FormatException;

/**
 * The data encoding modes an rMQR symbol may switch between, and how wide the
 * character count that follows each one is.
 *
 * <p>rMQR uses a 3 bit mode indicator, where QR uses 4 and Micro QR uses 1 to
 * 3. The count that follows is narrower still, and depends on the version:
 * there is no point spending 8 bits saying how many characters follow when the
 * symbol cannot hold more than 30. That is why the width is a table of 32
 * entries per mode rather than the three size bands QR gets away with.</p>
 */
public enum RMQRMode {

  TERMINATOR(0),
  NUMERIC(1),
  ALPHANUMERIC(2),
  BYTE(3),
  KANJI(4),
  FNC1_FIRST_POSITION(5),
  FNC1_SECOND_POSITION(6),
  ECI(7);

  private static final RMQRMode[] FOR_BITS = values();

  /** Character count indicator widths per version, ISO/IEC 23941 Table 3. */
  private static final int[] NUMERIC_COUNT_BITS =
      {4, 5, 6, 7, 7, 5, 6, 7, 7, 8, 4, 6, 7, 7, 8, 8, 5, 6, 7, 7, 8, 8, 7, 7, 8, 8, 9, 7, 8, 8, 8, 9};
  private static final int[] ALPHANUMERIC_COUNT_BITS =
      {3, 5, 5, 6, 6, 5, 5, 6, 6, 7, 4, 5, 6, 6, 7, 7, 5, 6, 6, 7, 7, 8, 6, 7, 7, 7, 8, 6, 7, 7, 8, 8};
  private static final int[] BYTE_COUNT_BITS =
      {3, 4, 5, 5, 6, 4, 5, 5, 6, 6, 3, 5, 5, 6, 6, 7, 4, 5, 6, 6, 7, 7, 6, 6, 7, 7, 7, 6, 6, 7, 7, 8};
  private static final int[] KANJI_COUNT_BITS =
      {2, 3, 4, 5, 5, 3, 4, 5, 5, 6, 2, 4, 5, 5, 6, 6, 3, 5, 5, 6, 6, 7, 5, 5, 6, 6, 7, 5, 6, 6, 6, 7};

  private final int bits;

  RMQRMode(int bits) {
    this.bits = bits;
  }

  public int getBits() {
    return bits;
  }

  /** @throws FormatException if the three bits are not a defined mode */
  public static RMQRMode forBits(int bits) throws FormatException {
    if (bits < 0 || bits >= FOR_BITS.length) {
      throw FormatException.getFormatInstance();
    }
    return FOR_BITS[bits];
  }

  /**
   * How many bits the character count after this mode indicator occupies, in
   * the given version. Zero for modes that are not followed by a count.
   */
  public int getCharacterCountBits(RMQRVersion version) {
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
