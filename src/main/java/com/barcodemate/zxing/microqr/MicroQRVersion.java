/*
 * Copyright 2016 Nu-book Inc.
 * Copyright 2016 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRVersion.cpp (tag
 * v3.1.1, commit 287c85d), the Micro QR parts. Tables are ISO/IEC 18004:2006
 * 6.5.1 Table 9.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * One of the four Micro QR symbol versions, M1 to M4.
 *
 * <p>Micro QR trades away almost everything QR has spare. There is one finder
 * pattern instead of three, no alignment patterns, timing along only two edges,
 * and the symbol runs from 11x11 to 17x17 modules. M1 goes furthest: it holds
 * five codewords in total, carries numeric data only, has no mode indicator at
 * all, and offers a single error correction level.</p>
 */
public final class MicroQRVersion {

  /** Error correction blocks for one level. */
  public static final class ECBlocks {
    private final int ecCodewordsPerBlock;
    private final int numBlocks;
    private final int dataCodewords;

    ECBlocks(int ecCodewordsPerBlock, int numBlocks, int dataCodewords) {
      this.ecCodewordsPerBlock = ecCodewordsPerBlock;
      this.numBlocks = numBlocks;
      this.dataCodewords = dataCodewords;
    }

    public int getECCodewordsPerBlock() {
      return ecCodewordsPerBlock;
    }

    public int getNumBlocks() {
      return numBlocks;
    }

    public int getTotalDataCodewords() {
      return numBlocks * dataCodewords;
    }

    public int getTotalCodewords() {
      return numBlocks * (dataCodewords + ecCodewordsPerBlock);
    }
  }

  private final int versionNumber;
  private final ECBlocks[] ecBlocks;

  private MicroQRVersion(int versionNumber, ECBlocks... ecBlocks) {
    this.versionNumber = versionNumber;
    this.ecBlocks = ecBlocks;
  }

  // Indexed by ErrorCorrectionLevel ordinal: L, M, Q. M1 offers only L; M2 and
  // M3 add M; M4 adds Q. No Micro QR version offers H.
  private static final MicroQRVersion[] VERSIONS = {
      new MicroQRVersion(1, new ECBlocks(2, 1, 3)),
      new MicroQRVersion(2, new ECBlocks(5, 1, 5), new ECBlocks(6, 1, 4)),
      new MicroQRVersion(3, new ECBlocks(6, 1, 11), new ECBlocks(8, 1, 9)),
      new MicroQRVersion(4, new ECBlocks(8, 1, 16), new ECBlocks(10, 1, 14), new ECBlocks(14, 1, 10)),
  };

  public int getVersionNumber() {
    return versionNumber;
  }

  /** M1 is 11 modules square, each version two more than the last. */
  public int getDimension() {
    return 9 + 2 * versionNumber;
  }

  /** @return null if this version does not offer that level */
  public ECBlocks getECBlocks(ErrorCorrectionLevel level) {
    int index = level.ordinal();
    return index < ecBlocks.length ? ecBlocks[index] : null;
  }

  public int getTotalCodewords() {
    return ecBlocks[0].getTotalCodewords();
  }

  public static MicroQRVersion forNumber(int number) {
    return number < 1 || number > VERSIONS.length ? null : VERSIONS[number - 1];
  }

  public static MicroQRVersion forDimension(int dimension) {
    if (dimension < 11 || dimension > 17 || (dimension & 1) == 0) {
      return null;
    }
    return forNumber((dimension - 9) / 2);
  }

  public static boolean isValidDimension(int dimension) {
    return forDimension(dimension) != null;
  }

  /**
   * The mask of module positions carrying structure rather than data: the
   * single finder pattern with its separator and format area, and the timing
   * patterns along the top and left edges.
   */
  public BitMatrix buildFunctionPattern() {
    int dimension = getDimension();
    BitMatrix pattern = new BitMatrix(dimension, dimension);
    pattern.setRegion(0, 0, 9, 9);
    pattern.setRegion(9, 0, dimension - 9, 1);
    pattern.setRegion(0, 9, 1, dimension - 9);
    return pattern;
  }

  @Override
  public String toString() {
    return "M" + versionNumber;
  }
}
