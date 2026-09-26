/*
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRBitMatrixParser.cpp
 * (tag v3.1.1, commit 287c85d), ReadQRCodewordsModel1.
 */

package com.barcodemate.zxing.qrmodel1;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Reader;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.reedsolomon.GenericGF;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonException;
import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/**
 * Reads QR Code Model 1 symbols, ISO/IEC 18004:2000 Annex M.
 *
 * <p>Model 1 is the original QR Code, superseded by Model 2 in 2000 and rarely
 * seen since. It shares Model 2's dimensions and finder patterns, so nothing
 * about a symbol's outline says which it is; what differs is the codeword
 * layout, which is not the boustrophedon column walk of Model 2 but three
 * distinct regions read in their own orders, and the absence of alignment
 * patterns.</p>
 *
 * <p>Because the two are indistinguishable until the codewords are read, this
 * reader is only useful as a fallback after the ordinary QR reader has
 * declined, and it is opt-in for that reason.</p>
 */
public final class QRModel1Reader implements Reader {

  private static final ReedSolomonDecoder RS_DECODER =
      new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);

  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    BitMatrix matrix = image.getBlackMatrix();
    int[] box = matrix.getEnclosingRectangle();
    if (box == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    int left = box[0];
    int top = box[1];
    int width = box[2];
    int height = box[3];
    if (Math.abs(width - height) > Math.max(2, width / 21)) {
      throw NotFoundException.getNotFoundInstance();
    }

    // Model 1 shares Model 2's dimensions, so the version follows from the
    // module count once the module size is known.
    BitMatrix modules = null;
    QRModel1Version version = null;
    for (int candidate = 1; candidate <= 14; candidate++) {
      QRModel1Version v = QRModel1Version.forNumber(candidate);
      int dimension = v.getDimension();
      double moduleWidth = width / (double) dimension;
      double moduleHeight = height / (double) dimension;
      if (moduleWidth < 1 || moduleHeight < 1) {
        continue;
      }
      BitMatrix sampled = sample(matrix, left, top, dimension, moduleWidth, moduleHeight);
      if (sampled != null && hasFinderPatterns(sampled)) {
        modules = sampled;
        version = v;
        break;
      }
    }
    if (modules == null) {
      throw NotFoundException.getNotFoundInstance();
    }

    // The format information sits where Model 2 keeps it.
    int[] format = decodeFormatInformation(readFormatInformation(modules));
    if (format == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    int dataMask = format[0];
    int levelIndex = format[1];

    byte[] codewords = readCodewords(modules, version, dataMask);
    QRModel1Version.ECBlocks ecBlocks = version.getECBlocks(levelIndex);
    byte[] dataBytes = correctAndAssemble(codewords, ecBlocks);

    DecoderResult decoded = QRModel1BitStreamParser.decode(dataBytes, version);

    int right = left + width - 1;
    int bottom = top + height - 1;
    Result result = new Result(decoded.getText(), decoded.getRawBytes(),
        new ResultPoint[] {
            new ResultPoint(left, top),
            new ResultPoint(right, top),
            new ResultPoint(right, bottom),
            new ResultPoint(left, bottom),
        },
        BarcodeFormat.QR_CODE_MODEL_1);
    // ISO/IEC 15424 gives Model 1 its own modifier, which is how a result can
    // say which of the two models it came from.
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]Q0");
    result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL,
        ErrorCorrectionLevel.values()[levelIndex].toString());
    return result;
  }

  private static BitMatrix sample(BitMatrix image, int left, int top, int dimension,
                                  double moduleWidth, double moduleHeight) {
    BitMatrix modules = new BitMatrix(dimension, dimension);
    for (int y = 0; y < dimension; y++) {
      int py = (int) (top + (y + 0.5) * moduleHeight);
      for (int x = 0; x < dimension; x++) {
        int px = (int) (left + (x + 0.5) * moduleWidth);
        if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
          return null;
        }
        if (image.get(px, py)) {
          modules.set(x, y);
        }
      }
    }
    return modules;
  }

  /** The three 7x7 finder patterns, which Model 1 shares with Model 2. */
  private static boolean hasFinderPatterns(BitMatrix modules) {
    int dimension = modules.getWidth();
    return isFinder(modules, 0, 0)
        && isFinder(modules, dimension - 7, 0)
        && isFinder(modules, 0, dimension - 7);
  }

  private static boolean isFinder(BitMatrix modules, int x0, int y0) {
    for (int y = 0; y < 7; y++) {
      for (int x = 0; x < 7; x++) {
        int ring = Math.max(Math.abs(x - 3), Math.abs(y - 3));
        boolean expected = ring != 2; // black, white ring, black core
        if (modules.get(x0 + x, y0 + y) != expected) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Reads both copies of the format information, the second with its dark
   * module removed.
   */
  private static int[] readFormatInformation(BitMatrix modules) {
    int dimension = modules.getWidth();

    int first = 0;
    for (int x = 0; x < 6; x++) {
      first = (first << 1) | (modules.get(x, 8) ? 1 : 0);
    }
    first = (first << 1) | (modules.get(7, 8) ? 1 : 0);
    first = (first << 1) | (modules.get(8, 8) ? 1 : 0);
    first = (first << 1) | (modules.get(8, 7) ? 1 : 0);
    for (int y = 5; y >= 0; y--) {
      first = (first << 1) | (modules.get(8, y) ? 1 : 0);
    }

    int second = 0;
    for (int y = dimension - 1; y >= dimension - 8; y--) {
      second = (second << 1) | (modules.get(8, y) ? 1 : 0);
    }
    for (int x = dimension - 8; x < dimension; x++) {
      second = (second << 1) | (modules.get(x, 8) ? 1 : 0);
    }
    // The dark module always sits in that run and carries no information.
    second = ((second >> 1) & 0b111111100000000) | (second & 0b11111111);

    return new int[] {first, second};
  }

  /** The 32 valid sequences, shared with Model 2. */
  private static final int[] FORMAT_INFO_PATTERNS = {
      0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0,
      0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976,
      0x1689, 0x13BE, 0x1CE7, 0x19D0, 0x0762, 0x0255, 0x0D0C, 0x083B,
      0x355F, 0x3068, 0x3F31, 0x3A06, 0x24B4, 0x2183, 0x2EDA, 0x2BED,
  };

  /**
   * Model 2 masks its format information with 0x5412. Model 1 uses a
   * different mask, and some symbols in the wild apply no mask at all, so all
   * three are tried and the closest match across both copies wins. This is
   * also what tells the two models apart when their dimensions cannot.
   */
  private static final int[] FORMAT_INFO_MASKS = {0x2825, 0x5412, 0};

  /** @return {@code {dataMask, errorCorrectionLevelIndex}} or null */
  private static int[] decodeFormatInformation(int[] copies) {
    int bestData = -1;
    int bestDistance = 33;
    for (int mask : FORMAT_INFO_MASKS) {
      for (int bits : copies) {
        for (int masked : FORMAT_INFO_PATTERNS) {
          int pattern = masked ^ 0x5412;
          int distance = Integer.bitCount((bits ^ mask) ^ pattern);
          if (distance < bestDistance) {
            bestDistance = distance;
            bestData = pattern >>> 10;
          }
        }
      }
    }
    if (bestDistance > 3) {
      return null;
    }
    // Bits 3 and 4 are the error correction level, bits 0 to 2 the mask.
    int levelIndex = ErrorCorrectionLevel.forBits((bestData >> 3) & 0x03).ordinal();
    return new int[] {bestData & 0x07, levelIndex};
  }

  /**
   * Reads Model 1's codewords.
   *
   * <p>Model 2 reads its codewords in a single serpentine walk up and down
   * pairs of columns. Model 1 does nothing of the kind: it has two regions of
   * vertical codewords down the right, more down the left, and a large region
   * of horizontal ones in between, each with its own traversal and its own
   * exceptions for the timing patterns and the extension blocks. There is no
   * way to express it except as the walk itself.</p>
   */
  private static byte[] readCodewords(BitMatrix modules, QRModel1Version version, int dataMask) {
    int dimension = modules.getWidth();
    int total = version.getTotalCodewords();
    byte[] result = new byte[total];
    int at = 0;
    int columns = dimension / 4 + 1 + 2;

    for (int j = 0; j < columns && at < total; j++) {
      if (j <= 1) { // vertical codewords down the right edge
        int rows = (dimension - 8) / 4;
        for (int i = 0; i < rows && at < total; i++) {
          if (j == 0 && i % 2 == 0 && i > 0 && i < rows - 1) {
            continue; // extension block
          }
          int x = (dimension - 1) - (j * 2);
          int y = (dimension - 1) - (i * 4);
          result[at++] = (byte) readByte(modules, dataMask, x, y, 2);
        }
      } else if (columns - j <= 4) { // vertical codewords down the left edge
        int rows = (dimension - 16) / 4;
        for (int i = 0; i < rows && at < total; i++) {
          int x = (columns - j - 1) * 2 + 1 + (columns - j == 4 ? 1 : 0); // timing
          int y = (dimension - 1) - 8 - (i * 4);
          result[at++] = (byte) readByte(modules, dataMask, x, y, 2);
        }
      } else { // horizontal codewords in between
        int rows = dimension / 2;
        for (int i = 0; i < rows && at < total; i++) {
          if (j == 2 && i >= rows - 4) {
            continue; // alignment and finder
          }
          if (i == 0 && j % 2 == 1 && j + 1 != columns - 4) {
            continue; // extension block
          }
          int x = (dimension - 1) - (2 * 2) - (j - 2) * 4;
          int y = (dimension - 1) - (i * 2) - (i >= rows - 3 ? 1 : 0); // timing
          result[at++] = (byte) readByte(modules, dataMask, x, y, 4);
        }
      }
    }

    if (at != total) {
      return null;
    }
    result[0] &= 0xf; // the corner module is not data
    return result;
  }

  /**
   * One codeword: eight bits laid out {@code width} to a row, read right to
   * left and bottom to top from the given corner.
   */
  private static int readByte(BitMatrix modules, int dataMask, int x, int y, int width) {
    int value = 0;
    for (int b = 0; b < 8; b++) {
      int mx = x - b % width;
      int my = y - (b / width);
      boolean bit = isMasked(dataMask, mx, my) != modules.get(mx, my);
      value = (value << 1) | (bit ? 1 : 0);
    }
    return value;
  }

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
      default: return false;
    }
  }

  private static byte[] correctAndAssemble(byte[] codewords, QRModel1Version.ECBlocks ecBlocks)
      throws ChecksumException, FormatException {
    if (codewords == null) {
      throw FormatException.getFormatInstance();
    }
    // Model 1 uses one block per level in every version, so there is no
    // de-interleaving to do.
    int numDataCodewords = ecBlocks.getTotalDataCodewords();
    int numECCodewords = codewords.length - numDataCodewords;
    if (numECCodewords <= 0) {
      throw FormatException.getFormatInstance();
    }

    int[] ints = new int[codewords.length];
    for (int i = 0; i < codewords.length; i++) {
      ints[i] = codewords[i] & 0xFF;
    }
    try {
      RS_DECODER.decode(ints, numECCodewords);
    } catch (ReedSolomonException notCorrectable) {
      throw ChecksumException.getChecksumInstance();
    }

    byte[] data = new byte[numDataCodewords];
    for (int i = 0; i < numDataCodewords; i++) {
      data[i] = (byte) ints[i];
    }
    return data;
  }

  @Override
  public void reset() {
    // No state is carried between images.
  }
}
