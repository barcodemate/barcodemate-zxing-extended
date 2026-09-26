/*
 * Copyright 2016 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRFormatInformation.cpp
 * (tag v3.1.1, commit 287c85d), the Micro QR parts. Table is ISO/IEC
 * 18004:2015 Annex C, Table C.1.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * The format information of a Micro QR symbol: its version, error correction
 * level and data mask, packed into five data bits with ten BCH check bits.
 *
 * <p>Micro QR shares QR's valid sequences and differs only in the mask applied
 * to them, so the same 32 entry table serves both. Version and error correction
 * level are not separate fields: three bits select one of eight combinations,
 * because most versions do not offer every level.</p>
 *
 * <p>Both the sequence and its mirror image are tried, which is how a symbol
 * photographed through glass or from the back of a transparency still reads.</p>
 */
public final class MicroQRFormatInformation {

  private static final int MASK_MODEL2 = 0x5412;
  private static final int MASK_MICRO = 0x4445;

  /** The 32 valid sequences, ISO/IEC 18004 Annex C Table C.1. */
  private static final int[] MASKED_PATTERNS = {
      0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0,
      0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976,
      0x1689, 0x13BE, 0x1CE7, 0x19D0, 0x0762, 0x0255, 0x0D0C, 0x083B,
      0x355F, 0x3068, 0x3F31, 0x3A06, 0x24B4, 0x2183, 0x2EDA, 0x2BED,
  };

  /** Three bits choose both version and level, since not every pair exists. */
  private static final int[] BITS_TO_VERSION = {1, 2, 2, 3, 3, 4, 4, 4};
  private static final ErrorCorrectionLevel[] BITS_TO_LEVEL = {
      ErrorCorrectionLevel.L, ErrorCorrectionLevel.L, ErrorCorrectionLevel.M,
      ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L,
      ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q,
  };

  /** Micro QR's four masks, as indices into QR's eight. */
  private static final int[] MICRO_TO_QR_MASK = {1, 4, 6, 7};

  private final int data;
  private final int hammingDistance;
  private final boolean mirrored;

  private MicroQRFormatInformation(int data, int hammingDistance, boolean mirrored) {
    this.data = data;
    this.hammingDistance = hammingDistance;
    this.mirrored = mirrored;
  }

  public static MicroQRFormatInformation decode(int formatInfoBits) {
    int[] candidates = {formatInfoBits, mirrorBits(formatInfoBits)};

    int bestData = 255;
    int bestDistance = 255;
    int bestIndex = 0;
    for (int index = 0; index < candidates.length; index++) {
      for (int masked : MASKED_PATTERNS) {
        int pattern = masked ^ MASK_MODEL2;
        int distance = Integer.bitCount((candidates[index] ^ MASK_MICRO) ^ pattern);
        if (distance < bestDistance) {
          bestData = pattern >>> 10; // drop the 10 BCH check bits
          bestDistance = distance;
          bestIndex = index;
        }
      }
    }
    return new MicroQRFormatInformation(bestData, bestDistance, bestIndex == 1);
  }

  /** The 15 format bits, read back to front. */
  private static int mirrorBits(int bits) {
    return Integer.reverse(bits) >>> 17;
  }

  public boolean isValid() {
    return hammingDistance <= 3;
  }

  public int getVersionNumber() {
    return BITS_TO_VERSION[(data >> 2) & 0x07];
  }

  public MicroQRVersion getVersion() {
    return MicroQRVersion.forNumber(getVersionNumber());
  }

  public ErrorCorrectionLevel getErrorCorrectionLevel() {
    return BITS_TO_LEVEL[(data >> 2) & 0x07];
  }

  /** The data mask, expressed as the equivalent QR mask index. */
  public int getDataMask() {
    return MICRO_TO_QR_MASK[data & 0x03];
  }

  public boolean isMirrored() {
    return mirrored;
  }

  public int getHammingDistance() {
    return hammingDistance;
  }

  @Override
  public String toString() {
    return "M" + getVersionNumber() + " " + getErrorCorrectionLevel()
        + " mask " + (data & 0x03) + (mirrored ? " mirrored" : "");
  }
}
