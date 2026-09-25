/*
 * Copyright 2023 Axel Waggershauser
 * Copyright 2023 gitlost
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRVersion.cpp (tag
 * v3.1.1, commit 287c85d), the rMQR parts. The tables are those of
 * ISO/IEC 23941:2022: Annex D Table D.1 for the alignment pattern columns and
 * 7.5.1 Table 8 for the error correction characteristics.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.common.BitMatrix;

/**
 * One of the 32 rMQR symbol formats.
 *
 * <p>rMQR is rectangular, which is the whole point of it: ISO/IEC 23941 defines
 * 32 shapes from R7x43 to R17x139, so a symbol can be fitted to a narrow label
 * rather than forcing the label to accommodate a square. That also means a
 * version number here does not determine a dimension the way it does for QR --
 * width and height vary independently -- so the size table is explicit.</p>
 *
 * <p>Only the M and H error correction levels exist for rMQR.</p>
 */
public final class RMQRVersion {

  /** Error correction blocks for one level. */
  public static final class ECBlocks {
    private final int ecCodewordsPerBlock;
    private final int count1;
    private final int dataCodewords1;
    private final int count2;
    private final int dataCodewords2;

    ECBlocks(int[] spec) {
      this.ecCodewordsPerBlock = spec[0];
      this.count1 = spec[1];
      this.dataCodewords1 = spec[2];
      this.count2 = spec[3];
      this.dataCodewords2 = spec[4];
    }

    public int getECCodewordsPerBlock() {
      return ecCodewordsPerBlock;
    }

    public int getNumBlocks() {
      return count1 + count2;
    }

    /** Data codewords in block {@code index}, counting the larger blocks last. */
    public int getDataCodewords(int index) {
      return index < count1 ? dataCodewords1 : dataCodewords2;
    }

    public int getTotalDataCodewords() {
      return count1 * dataCodewords1 + count2 * dataCodewords2;
    }

    public int getTotalCodewords() {
      return getTotalDataCodewords() + ecCodewordsPerBlock * getNumBlocks();
    }
  }

  private final int versionNumber;
  private final int width;
  private final int height;
  private final int[] alignmentPatternCenters;
  private final ECBlocks ecBlocksM;
  private final ECBlocks ecBlocksH;

  private RMQRVersion(int versionNumber, int width, int height, int[] alignmentPatternCenters,
                      int[] ecM, int[] ecH) {
    this.versionNumber = versionNumber;
    this.width = width;
    this.height = height;
    this.alignmentPatternCenters = alignmentPatternCenters;
    this.ecBlocksM = new ECBlocks(ecM);
    this.ecBlocksH = new ECBlocks(ecH);
  }

  private static final RMQRVersion[] VERSIONS = {
      // R7x43, 13 codewords
      new RMQRVersion(1, 43, 7, new int[] {21}, new int[] {7, 1, 6, 0, 0}, new int[] {10, 1, 3, 0, 0}),
      // R7x59, 21 codewords
      new RMQRVersion(2, 59, 7, new int[] {19, 39}, new int[] {9, 1, 12, 0, 0}, new int[] {14, 1, 7, 0, 0}),
      // R7x77, 32 codewords
      new RMQRVersion(3, 77, 7, new int[] {25, 51}, new int[] {12, 1, 20, 0, 0}, new int[] {22, 1, 10, 0, 0}),
      // R7x99, 44 codewords
      new RMQRVersion(4, 99, 7, new int[] {23, 49, 75}, new int[] {16, 1, 28, 0, 0}, new int[] {30, 1, 14, 0, 0}),
      // R7x139, 68 codewords
      new RMQRVersion(5, 139, 7, new int[] {27, 55, 83, 111}, new int[] {24, 1, 44, 0, 0}, new int[] {22, 2, 12, 0, 0}),
      // R9x43, 21 codewords
      new RMQRVersion(6, 43, 9, new int[] {21}, new int[] {9, 1, 12, 0, 0}, new int[] {14, 1, 7, 0, 0}),
      // R9x59, 33 codewords
      new RMQRVersion(7, 59, 9, new int[] {19, 39}, new int[] {12, 1, 21, 0, 0}, new int[] {22, 1, 11, 0, 0}),
      // R9x77, 49 codewords
      new RMQRVersion(8, 77, 9, new int[] {25, 51}, new int[] {18, 1, 31, 0, 0}, new int[] {16, 1, 8, 1, 9}),
      // R9x99, 66 codewords
      new RMQRVersion(9, 99, 9, new int[] {23, 49, 75}, new int[] {24, 1, 42, 0, 0}, new int[] {22, 2, 11, 0, 0}),
      // R9x139, 99 codewords
      new RMQRVersion(10, 139, 9, new int[] {27, 55, 83, 111}, new int[] {18, 1, 31, 1, 32}, new int[] {22, 3, 11, 0, 0}),
      // R11x27, 15 codewords
      new RMQRVersion(11, 27, 11, new int[] {}, new int[] {8, 1, 7, 0, 0}, new int[] {10, 1, 5, 0, 0}),
      // R11x43, 31 codewords
      new RMQRVersion(12, 43, 11, new int[] {21}, new int[] {12, 1, 19, 0, 0}, new int[] {20, 1, 11, 0, 0}),
      // R11x59, 47 codewords
      new RMQRVersion(13, 59, 11, new int[] {19, 39}, new int[] {16, 1, 31, 0, 0}, new int[] {16, 1, 7, 1, 8}),
      // R11x77, 67 codewords
      new RMQRVersion(14, 77, 11, new int[] {25, 51}, new int[] {24, 1, 43, 0, 0}, new int[] {22, 1, 11, 1, 12}),
      // R11x99, 89 codewords
      new RMQRVersion(15, 99, 11, new int[] {23, 49, 75}, new int[] {16, 1, 28, 1, 29}, new int[] {30, 1, 14, 1, 15}),
      // R11x139, 132 codewords
      new RMQRVersion(16, 139, 11, new int[] {27, 55, 83, 111}, new int[] {24, 2, 42, 0, 0}, new int[] {30, 3, 14, 0, 0}),
      // R13x27, 21 codewords
      new RMQRVersion(17, 27, 13, new int[] {}, new int[] {9, 1, 12, 0, 0}, new int[] {14, 1, 7, 0, 0}),
      // R13x43, 41 codewords
      new RMQRVersion(18, 43, 13, new int[] {21}, new int[] {14, 1, 27, 0, 0}, new int[] {28, 1, 13, 0, 0}),
      // R13x59, 60 codewords
      new RMQRVersion(19, 59, 13, new int[] {19, 39}, new int[] {22, 1, 38, 0, 0}, new int[] {20, 2, 10, 0, 0}),
      // R13x77, 85 codewords
      new RMQRVersion(20, 77, 13, new int[] {25, 51}, new int[] {16, 1, 26, 1, 27}, new int[] {28, 1, 14, 1, 15}),
      // R13x99, 113 codewords
      new RMQRVersion(21, 99, 13, new int[] {23, 49, 75}, new int[] {20, 1, 36, 1, 37}, new int[] {26, 1, 11, 2, 12}),
      // R13x139, 166 codewords
      new RMQRVersion(22, 139, 13, new int[] {27, 55, 83, 111}, new int[] {20, 2, 35, 1, 36}, new int[] {28, 2, 13, 2, 14}),
      // R15x43, 51 codewords
      new RMQRVersion(23, 43, 15, new int[] {21}, new int[] {18, 1, 33, 0, 0}, new int[] {18, 1, 7, 1, 8}),
      // R15x59, 74 codewords
      new RMQRVersion(24, 59, 15, new int[] {19, 39}, new int[] {26, 1, 48, 0, 0}, new int[] {24, 2, 13, 0, 0}),
      // R15x77, 103 codewords
      new RMQRVersion(25, 77, 15, new int[] {25, 51}, new int[] {18, 1, 33, 1, 34}, new int[] {24, 2, 10, 1, 11}),
      // R15x99, 136 codewords
      new RMQRVersion(26, 99, 15, new int[] {23, 49, 75}, new int[] {24, 2, 44, 0, 0}, new int[] {22, 4, 12, 0, 0}),
      // R15x139, 199 codewords
      new RMQRVersion(27, 139, 15, new int[] {27, 55, 83, 111}, new int[] {24, 2, 42, 1, 43}, new int[] {26, 1, 13, 4, 14}),
      // R17x43, 61 codewords
      new RMQRVersion(28, 43, 17, new int[] {21}, new int[] {22, 1, 39, 0, 0}, new int[] {20, 1, 10, 1, 11}),
      // R17x59, 88 codewords
      new RMQRVersion(29, 59, 17, new int[] {19, 39}, new int[] {16, 2, 28, 0, 0}, new int[] {30, 2, 14, 0, 0}),
      // R17x77, 122 codewords
      new RMQRVersion(30, 77, 17, new int[] {25, 51}, new int[] {22, 2, 39, 0, 0}, new int[] {28, 1, 12, 2, 13}),
      // R17x99, 160 codewords
      new RMQRVersion(31, 99, 17, new int[] {23, 49, 75}, new int[] {20, 2, 33, 1, 34}, new int[] {26, 4, 14, 0, 0}),
      // R17x139, 232 codewords
      new RMQRVersion(32, 139, 17, new int[] {27, 55, 83, 111}, new int[] {20, 4, 38, 0, 0}, new int[] {26, 2, 12, 4, 13}),
  };

  public int getVersionNumber() {
    return versionNumber;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int[] getAlignmentPatternCenters() {
    return alignmentPatternCenters;
  }

  /** @param high true for error correction level H, false for M */
  public ECBlocks getECBlocks(boolean high) {
    return high ? ecBlocksH : ecBlocksM;
  }

  public int getTotalCodewords() {
    return ecBlocksM.getTotalCodewords();
  }

  /** @return the version numbered {@code number} from 1 to 32, or null */
  public static RMQRVersion forNumber(int number) {
    return number < 1 || number > VERSIONS.length ? null : VERSIONS[number - 1];
  }

  /** @return the version with exactly this symbol size, or null */
  public static RMQRVersion forSize(int width, int height) {
    for (RMQRVersion version : VERSIONS) {
      if (version.width == width && version.height == height) {
        return version;
      }
    }
    return null;
  }

  public static boolean isValidSize(int width, int height) {
    // Cheap rejections first: rMQR is never square, both dimensions are odd,
    // and they are bounded. Only then is the table consulted.
    return width != height && (width & 1) == 1 && (height & 1) == 1
        && width >= 27 && width <= 139 && height >= 7 && height <= 17
        && forSize(width, height) != null;
  }

  /**
   * The mask of module positions that carry function patterns rather than data:
   * the timing patterns around the edge, the alignment patterns and vertical
   * timing columns, the finder pattern and its format area, the bottom right
   * finder subpattern with its own format area, and the corner finders.
   */
  public BitMatrix buildFunctionPattern() {
    BitMatrix pattern = new BitMatrix(width, height);

    // Timing patterns along all four edges.
    pattern.setRegion(0, 0, width, 1);
    pattern.setRegion(0, height - 1, width, 1);
    pattern.setRegion(0, 1, 1, height - 2);
    pattern.setRegion(width - 1, 1, 1, height - 2);

    // Alignment patterns, each with a vertical timing column through it.
    for (int center : alignmentPatternCenters) {
      pattern.setRegion(center - 1, 1, 3, 2);
      pattern.setRegion(center - 1, height - 3, 3, 2);
      pattern.setRegion(center, 3, 1, height - 6);
    }

    // Top left finder pattern and separator. On the 7 module high symbols the
    // finder runs flush with the bottom edge, so it is one row shorter.
    pattern.setRegion(1, 1, 7, height == 7 ? 6 : 7);
    // Top left format information.
    pattern.setRegion(8, 1, 3, 5);
    pattern.setRegion(11, 1, 1, 3);

    // Bottom right finder subpattern and its format information.
    pattern.setRegion(width - 5, height - 5, 4, 4);
    pattern.setRegion(width - 8, height - 6, 3, 5);
    pattern.setRegion(width - 5, height - 6, 3, 1);

    // Corner finders.
    pattern.set(width - 2, 1);
    if (height > 9) {
      pattern.set(1, height - 2);
    }
    return pattern;
  }

  @Override
  public String toString() {
    return "R" + height + "x" + width;
  }
}
