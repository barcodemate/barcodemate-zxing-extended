/*
 * Copyright 2023 gitlost
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRBitMatrixParser.cpp
 * (tag v3.1.1, commit 287c85d), the rMQR parts.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitMatrix;

/**
 * Reads an rMQR symbol's format information and codewords out of a sampled
 * module matrix.
 *
 * <p>The codewords are not stored in reading order. They run up and down
 * through the symbol in vertical pairs of columns, right to left, skipping
 * every module the function pattern occupies. rMQR differs from QR here in two
 * ways: the rightmost column is an alignment column rather than data, so the
 * walk starts one column in, and the function pattern is far more intricate
 * because the timing patterns run along all four edges.</p>
 */
public final class RMQRBitMatrixParser {

  private RMQRBitMatrixParser() {
  }

  /**
   * Reads both copies of the format information: one beside the finder
   * pattern, one beside the finder subpattern in the opposite corner.
   */
  public static RMQRFormatInformation readFormatInformation(BitMatrix matrix) {
    int bits1 = 0;
    for (int y = 3; y >= 1; y--) {
      bits1 = appendBit(bits1, matrix.get(11, y));
    }
    for (int x = 10; x >= 8; x--) {
      for (int y = 5; y >= 1; y--) {
        bits1 = appendBit(bits1, matrix.get(x, y));
      }
    }

    int width = matrix.getWidth();
    int height = matrix.getHeight();
    int bits2 = 0;
    for (int x = 3; x <= 5; x++) {
      bits2 = appendBit(bits2, matrix.get(width - x, height - 6));
    }
    for (int x = 6; x <= 8; x++) {
      for (int y = 2; y <= 6; y++) {
        bits2 = appendBit(bits2, matrix.get(width - x, height - y));
      }
    }

    return RMQRFormatInformation.decode(bits1, bits2);
  }

  /**
   * Reads the data and error correction codewords, unmasking as it goes.
   *
   * @throws FormatException if the symbol does not yield the expected number
   *         of codewords, which means the matrix is not the shape claimed
   */
  public static byte[] readCodewords(BitMatrix matrix, RMQRVersion version,
                                     RMQRFormatInformation formatInfo) throws FormatException {
    BitMatrix functionPattern = version.buildFunctionPattern();

    byte[] result = new byte[version.getTotalCodewords()];
    int resultOffset = 0;
    int currentByte = 0;
    int bitsRead = 0;
    boolean readingUp = true;
    int width = matrix.getWidth();
    int height = matrix.getHeight();

    // Column pairs, right to left. The first column is skipped because the
    // right edge carries an alignment pattern, not data.
    for (int x = width - 2; x > 0; x -= 2) {
      for (int row = 0; row < height; row++) {
        int y = readingUp ? height - 1 - row : row;
        for (int col = 0; col < 2; col++) {
          int xx = x - col;
          if (functionPattern.get(xx, y)) {
            continue;
          }
          boolean bit = isMasked(xx, y) != matrix.get(xx, y);
          currentByte = appendBit(currentByte, bit);
          if (++bitsRead % 8 == 0) {
            if (resultOffset == result.length) {
              throw FormatException.getFormatInstance();
            }
            result[resultOffset++] = (byte) currentByte;
            currentByte = 0;
          }
        }
      }
      readingUp = !readingUp;
    }

    if (resultOffset != result.length) {
      throw FormatException.getFormatInstance();
    }
    return result;
  }

  /**
   * rMQR defines exactly one data mask, the pattern QR calls 100. There is no
   * mask selection to decode, and no reason to reach for the eight-way enum.
   */
  private static boolean isMasked(int x, int y) {
    return (((y / 2) + (x / 3)) & 1) == 0;
  }

  private static int appendBit(int value, boolean bit) {
    return (value << 1) | (bit ? 1 : 0);
  }
}
