/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/aztec/AZDetector.cpp (tag
 * v3.1.1, commit 287c85d), the rune path.
 */

package com.barcodemate.zxing.aztecrune;

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
import com.barcodemate.zxing.common.reedsolomon.GenericGF;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonException;

import java.util.Map;

/**
 * Reads Aztec Runes, the 11x11 fixed-size member of the Aztec family.
 *
 * <p>A rune carries exactly one byte, 0 to 255, and nothing else: no data
 * layers, no length, no character set. It is a marker, used where something
 * has to be identified rather than described -- a shelf position, a bin, a
 * pallet slot.</p>
 *
 * <p>Structurally a rune is a compact Aztec symbol with zero layers, so it is
 * only the bullseye and the mode message ring around it. The eight data bits
 * live in that ring, where a real Aztec symbol keeps its layer count and block
 * count, and the two are told apart by a trick: a rune's mode message words are
 * inverted with 1010 before the error correction is applied. A decoder tries
 * the message as-is, and if the error correction refuses it, tries again
 * inverted. Whichever passes says which of the two it was looking at.</p>
 */
public final class AztecRuneReader implements Reader {

  /** A rune is always 11 modules square. */
  private static final int DIMENSION = 11;

  /** The mode message is 7 words of 4 bits, of which 2 are data. */
  private static final int NUM_CODEWORDS = 7;
  private static final int NUM_DATA_CODEWORDS = 2;

  /** Runes invert their mode message words to distinguish themselves. */
  private static final int RUNE_MASK = 0b1010;

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
    // A rune is square; a pixel of aliasing either way is tolerated.
    if (width < DIMENSION || Math.abs(width - height) > Math.max(2, width / 11)) {
      throw NotFoundException.getNotFoundInstance();
    }

    double moduleWidth = width / (double) DIMENSION;
    double moduleHeight = height / (double) DIMENSION;
    boolean[][] modules = new boolean[DIMENSION][DIMENSION];
    for (int y = 0; y < DIMENSION; y++) {
      int py = (int) (top + (y + 0.5) * moduleHeight);
      for (int x = 0; x < DIMENSION; x++) {
        int px = (int) (left + (x + 0.5) * moduleWidth);
        if (px < 0 || py < 0 || px >= matrix.getWidth() || py >= matrix.getHeight()) {
          throw NotFoundException.getNotFoundInstance();
        }
        modules[y][x] = matrix.get(px, py);
      }
    }

    if (!hasBullseye(modules)) {
      throw NotFoundException.getNotFoundInstance();
    }

    int value = decodeModeMessage(readModeMessageBits(modules));
    if (value < 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    int right = left + width - 1;
    int bottom = top + height - 1;
    Result result = new Result(String.valueOf(value), new byte[] {(byte) value},
        new ResultPoint[] {
            new ResultPoint(left, top),
            new ResultPoint(right, top),
            new ResultPoint(right, bottom),
            new ResultPoint(left, bottom),
        },
        BarcodeFormat.AZTEC_RUNE);
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]z12");
    return result;
  }

  /** The concentric rings at the centre, which every Aztec symbol carries. */
  private static boolean hasBullseye(boolean[][] modules) {
    int c = DIMENSION / 2;
    // Centre module black, then rings alternating white, black outward.
    for (int ring = 0; ring <= 2; ring++) {
      boolean expected = ring % 2 == 0;
      for (int dy = -ring; dy <= ring; dy++) {
        for (int dx = -ring; dx <= ring; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
            continue;
          }
          if (modules[c + dy][c + dx] != expected) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * The 28 bits between the corner markers, read clockwise from the top edge.
   */
  private static long readModeMessageBits(boolean[][] modules) {
    int c = DIMENSION / 2;
    int radius = c;
    int[][] directions = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
    long bits = 0;
    for (int[] d : directions) {
      int cornerX = radius * d[0];
      int cornerY = radius * d[1];
      int nextX;
      int nextY;
      if (d[0] == d[1]) {
        nextX = -d[0];
        nextY = 0;
      } else {
        nextX = 0;
        nextY = -d[1];
      }
      // The corner modules themselves are orientation markers, so the run
      // starts two in and stops two short.
      for (int i = 2; i <= 2 * radius - 2; i++) {
        int x = c + cornerX + i * nextX;
        int y = c + cornerY + i * nextY;
        bits = (bits << 1) | (modules[y][x] ? 1 : 0);
      }
    }
    return bits;
  }

  /**
   * @return the rune's value, or -1 if these bits are not a valid rune mode
   *         message
   */
  private static int decodeModeMessage(long bits) {
    int[] words = new int[NUM_CODEWORDS];
    for (int i = NUM_CODEWORDS - 1; i >= 0; i--) {
      words[i] = (int) (bits & 0xF);
      bits >>= 4;
    }
    // A rune's words are inverted; an ordinary Aztec symbol's are not. Only
    // the inverted reading is tried here, so a real Aztec symbol is refused
    // rather than reported as a rune with a nonsense value.
    for (int i = 0; i < words.length; i++) {
      words[i] ^= RUNE_MASK;
    }

    try {
      new ReedSolomonDecoder(GenericGF.AZTEC_PARAM)
          .decode(words, NUM_CODEWORDS - NUM_DATA_CODEWORDS);
    } catch (ReedSolomonException notARune) {
      return -1;
    }

    int value = 0;
    for (int i = 0; i < NUM_DATA_CODEWORDS; i++) {
      value = (value << 4) + words[i];
    }
    return value;
  }

  @Override
  public void reset() {
    // No state is carried between images.
  }
}
