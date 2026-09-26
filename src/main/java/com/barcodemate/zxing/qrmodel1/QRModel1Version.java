/*
 * Copyright 2016 ZXing authors
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRVersion.cpp (tag
 * v3.1.1, commit 287c85d), the Model 1 parts. Tables are ISO/IEC 18004:2000
 * Annex M, tables M.2 and M.4.
 */

package com.barcodemate.zxing.qrmodel1;

/**
 * One of the 14 QR Code Model 1 versions.
 *
 * <p>Model 1 is the original QR Code, from 1994, superseded by Model 2 in 2000.
 * The two share their dimensions -- 17 + 4n modules square -- so a symbol's
 * size says nothing about which it is, and they share the finder patterns, so
 * a detector finds both the same way. What differs is everything after that:
 * Model 1 has no alignment patterns, extends its symbols with extra blocks
 * rather than adding alignment, and lays its codewords out in a completely
 * different order.</p>
 *
 * <p>Model 1 symbols are rare. The format is supported here because ZXing-C++
 * supports it and this project set out to close that gap, not because anyone
 * is likely to encounter one.</p>
 */
public final class QRModel1Version {

  /** Error correction blocks for one level. */
  public static final class ECBlocks {
    final int ecCodewordsPerBlock;
    final int count1;
    final int dataCodewords1;
    final int count2;
    final int dataCodewords2;

    ECBlocks(int ecCodewordsPerBlock, int count1, int dataCodewords1, int count2, int dataCodewords2) {
      this.ecCodewordsPerBlock = ecCodewordsPerBlock;
      this.count1 = count1;
      this.dataCodewords1 = dataCodewords1;
      this.count2 = count2;
      this.dataCodewords2 = dataCodewords2;
    }

    public int getECCodewordsPerBlock() {
      return ecCodewordsPerBlock;
    }

    public int getNumBlocks() {
      return count1 + count2;
    }

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
  private final ECBlocks[] ecBlocks;

  private QRModel1Version(int versionNumber, ECBlocks... ecBlocks) {
    this.versionNumber = versionNumber;
    this.ecBlocks = ecBlocks;
  }

  private static final QRModel1Version[] VERSIONS = {
      // Version 1, 21x21 modules
      new QRModel1Version(1, new ECBlocks(7, 1, 19, 0, 0), new ECBlocks(10, 1, 16, 0, 0), new ECBlocks(13, 1, 13, 0, 0), new ECBlocks(17, 1, 9, 0, 0)),
      // Version 2, 25x25 modules
      new QRModel1Version(2, new ECBlocks(10, 1, 36, 0, 0), new ECBlocks(16, 1, 30, 0, 0), new ECBlocks(22, 1, 24, 0, 0), new ECBlocks(30, 1, 16, 0, 0)),
      // Version 3, 29x29 modules
      new QRModel1Version(3, new ECBlocks(15, 1, 57, 0, 0), new ECBlocks(28, 1, 44, 0, 0), new ECBlocks(36, 1, 36, 0, 0), new ECBlocks(48, 1, 24, 0, 0)),
      // Version 4, 33x33 modules
      new QRModel1Version(4, new ECBlocks(20, 1, 80, 0, 0), new ECBlocks(40, 1, 60, 0, 0), new ECBlocks(50, 1, 50, 0, 0), new ECBlocks(66, 1, 34, 0, 0)),
      // Version 5, 37x37 modules
      new QRModel1Version(5, new ECBlocks(26, 1, 108, 0, 0), new ECBlocks(52, 1, 82, 0, 0), new ECBlocks(66, 1, 68, 0, 0), new ECBlocks(44, 2, 23, 0, 0)),
      // Version 6, 41x41 modules
      new QRModel1Version(6, new ECBlocks(34, 1, 136, 0, 0), new ECBlocks(32, 2, 53, 0, 0), new ECBlocks(42, 2, 43, 0, 0), new ECBlocks(56, 2, 29, 0, 0)),
      // Version 7, 45x45 modules
      new QRModel1Version(7, new ECBlocks(42, 1, 170, 0, 0), new ECBlocks(40, 2, 66, 0, 0), new ECBlocks(52, 2, 54, 0, 0), new ECBlocks(46, 3, 24, 0, 0)),
      // Version 8, 49x49 modules
      new QRModel1Version(8, new ECBlocks(24, 2, 104, 0, 0), new ECBlocks(48, 2, 80, 0, 0), new ECBlocks(64, 2, 64, 0, 0), new ECBlocks(56, 3, 29, 0, 0)),
      // Version 9, 53x53 modules
      new QRModel1Version(9, new ECBlocks(30, 2, 123, 0, 0), new ECBlocks(60, 2, 93, 0, 0), new ECBlocks(50, 3, 52, 0, 0), new ECBlocks(68, 3, 34, 0, 0)),
      // Version 10, 57x57 modules
      new QRModel1Version(10, new ECBlocks(34, 2, 145, 0, 0), new ECBlocks(68, 2, 111, 0, 0), new ECBlocks(58, 3, 61, 0, 0), new ECBlocks(58, 4, 31, 0, 0)),
      // Version 11, 61x61 modules
      new QRModel1Version(11, new ECBlocks(40, 2, 168, 0, 0), new ECBlocks(40, 4, 64, 0, 0), new ECBlocks(52, 4, 52, 0, 0), new ECBlocks(54, 5, 29, 0, 0)),
      // Version 12, 65x65 modules
      new QRModel1Version(12, new ECBlocks(46, 2, 192, 0, 0), new ECBlocks(46, 4, 73, 0, 0), new ECBlocks(58, 4, 61, 0, 0), new ECBlocks(62, 5, 33, 0, 0)),
      // Version 13, 69x69 modules
      new QRModel1Version(13, new ECBlocks(36, 3, 144, 0, 0), new ECBlocks(52, 4, 83, 0, 0), new ECBlocks(66, 4, 69, 0, 0), new ECBlocks(58, 6, 32, 0, 0)),
      // Version 14, 73x73 modules
      new QRModel1Version(14, new ECBlocks(40, 3, 163, 0, 0), new ECBlocks(60, 4, 92, 0, 0), new ECBlocks(60, 5, 62, 0, 0), new ECBlocks(66, 6, 35, 0, 0)),
  };

  public int getVersionNumber() {
    return versionNumber;
  }

  public int getDimension() {
    return 17 + 4 * versionNumber;
  }

  /** @param levelIndex L, M, Q, H as 0 to 3 */
  public ECBlocks getECBlocks(int levelIndex) {
    return ecBlocks[levelIndex];
  }

  public int getTotalCodewords() {
    return ecBlocks[0].getTotalCodewords();
  }

  public static QRModel1Version forNumber(int number) {
    return number < 1 || number > VERSIONS.length ? null : VERSIONS[number - 1];
  }

  public static QRModel1Version forDimension(int dimension) {
    if (dimension < 21 || dimension > 73 || (dimension % 4) != 1) {
      return null;
    }
    return forNumber((dimension - 17) / 4);
  }

  @Override
  public String toString() {
    return "Model 1 v" + versionNumber;
  }
}
