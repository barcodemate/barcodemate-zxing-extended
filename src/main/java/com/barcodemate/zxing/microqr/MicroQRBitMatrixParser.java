/*
 * Copyright 2016 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRBitMatrixParser.cpp
 * (tag v3.1.1, commit 287c85d), the Micro QR parts.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Reads a Micro QR symbol's format information and codewords. */
public final class MicroQRBitMatrixParser {

  private MicroQRBitMatrixParser() {
  }

  /** The 15 format bits run along the row and column just inside the finder. */
  public static MicroQRFormatInformation readFormatInformation(BitMatrix matrix) {
    int bits = 0;
    for (int x = 1; x < 9; x++) {
      bits = (bits << 1) | (matrix.get(x, 8) ? 1 : 0);
    }
    for (int y = 7; y >= 1; y--) {
      bits = (bits << 1) | (matrix.get(8, y) ? 1 : 0);
    }
    return MicroQRFormatInformation.decode(bits);
  }

  /**
   * Reads the codewords, unmasking as it goes.
   *
   * <p>One wrinkle of Micro QR shows up here. In M1, and in M3, the last data
   * codeword is only four bits wide -- the symbol's capacity does not divide
   * into whole bytes -- so that codeword is closed early. Which one it is
   * depends on the error correction level, since that changes how many data
   * codewords precede it. Missing this shifts every remaining bit and the
   * symbol decodes to nothing.</p>
   */
  public static byte[] readCodewords(BitMatrix matrix, MicroQRVersion version,
                                     MicroQRFormatInformation formatInfo) throws FormatException {
    BitMatrix functionPattern = version.buildFunctionPattern();
    int maskIndex = formatInfo.getDataMask();
    boolean mirrored = formatInfo.isMirrored();

    boolean hasShortBlock = version.getVersionNumber() % 2 == 1; // M1 and M3
    int shortBlockIndex = version.getVersionNumber() == 1 ? 3
        : (formatInfo.getErrorCorrectionLevel() == ErrorCorrectionLevel.L ? 11 : 9);

    byte[] result = new byte[version.getTotalCodewords()];
    int resultOffset = 0;
    int currentByte = 0;
    int bitsRead = 0;
    boolean readingUp = true;
    int dimension = matrix.getHeight();

    for (int x = dimension - 1; x > 0; x -= 2) {
      for (int row = 0; row < dimension; row++) {
        int y = readingUp ? dimension - 1 - row : row;
        for (int col = 0; col < 2; col++) {
          int xx = x - col;
          if (functionPattern.get(xx, y)) {
            continue;
          }
          boolean bit = isMasked(maskIndex, xx, y) != getBit(matrix, xx, y, mirrored);
          currentByte = (currentByte << 1) | (bit ? 1 : 0);
          bitsRead++;
          boolean closeShort = bitsRead == 4 && hasShortBlock && resultOffset == shortBlockIndex - 1;
          if (bitsRead == 8 || closeShort) {
            if (resultOffset == result.length) {
              throw FormatException.getFormatInstance();
            }
            result[resultOffset++] = (byte) currentByte;
            currentByte = 0;
            bitsRead = 0;
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

  private static boolean getBit(BitMatrix matrix, int x, int y, boolean mirrored) {
    return mirrored ? matrix.get(y, x) : matrix.get(x, y);
  }

  /** QR's data masks. Micro QR uses only 1, 4, 6 and 7 of them. */
  private static boolean isMasked(int maskIndex, int x, int y) {
    switch (maskIndex) {
      case 0: return (y + x) % 2 == 0;
      case 1: return y % 2 == 0;
      case 2: return x % 3 == 0;
      case 3: return (y + x) % 3 == 0;
      case 4: return ((y / 2) + (x / 3)) % 2 == 0;
      case 5: return (y * x) % 6 == 0;
      case 6: return ((y * x) % 6) < 3;
      case 7: return (y + x + ((y * x) % 3)) % 2 == 0;
      default: throw new IllegalArgumentException("mask index out of range: " + maskIndex);
    }
  }
}
