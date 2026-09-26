/*
 * Copyright 2023 gitlost
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRDetector.cpp (tag
 * v3.1.1, commit 287c85d), the rMQR paths.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.geometry.BitMatrixCursorI;
import com.barcodemate.zxing.common.geometry.PointI;
import com.barcodemate.zxing.common.geometry.Patterns;

/**
 * Finds an rMQR symbol in a binarised image.
 *
 * <p>This is where rMQR stops resembling QR. ZXing's QR detector looks for
 * three finder patterns and fits a perspective transform through their centres.
 * An rMQR symbol has one finder pattern, a much smaller subpattern in the
 * opposite corner, and timing patterns running along all four edges. There is
 * no triangle to fit anything to.</p>
 *
 * <p>The pure path implemented here handles the common case of an image that is
 * nothing but the symbol, upright and unrotated -- a generated image, a crop, a
 * screenshot. It measures the module size from five independent places and
 * cross-checks them, which is cheap and refuses far more than it accepts.</p>
 */
public final class RMQRDetector {

  /** The 1:1:3:1:1 of the finder pattern. */
  private static final int[] FINDER = {1, 1, 3, 1, 1};
  private static final int FINDER_MODULES = 7;

  /** The finder subpattern in the opposite corner is a plain 3x3 in a ring. */
  private static final int[] SUBPATTERN = {1, 1, 1, 1};
  private static final int SUBPATTERN_MODULES = 4;

  /** Ten alternating modules of an edge timing pattern. */
  private static final int[] TIMING = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
  private static final int TIMING_MODULES = 10;

  /** The shortest rMQR symbol is 7 modules high. */
  private static final int MIN_MODULES = 7;

  private RMQRDetector() {
  }

  /**
   * Detects a symbol that occupies the whole image, upright.
   *
   * @return the sampled modules and the symbol's corners
   * @throws NotFoundException if the image does not hold such a symbol
   */
  public static DetectorResult detectPure(BitMatrix image) throws NotFoundException {
    int[] box = image.getEnclosingRectangle();
    if (box == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    int left = box[0];
    int top = box[1];
    int width = box[2];
    int height = box[3];
    // rMQR is always wider than it is tall, and never smaller than R7x43.
    if (width < MIN_MODULES || height < MIN_MODULES || height >= width) {
      throw NotFoundException.getNotFoundInstance();
    }

    int right = left + width - 1;
    int bottom = top + height - 1;
    PointI tl = new PointI(left, top);
    PointI tr = new PointI(right, top);
    PointI bl = new PointI(left, bottom);
    PointI br = new PointI(right, bottom);

    // The finder pattern, read along the diagonal from the top left corner.
    // One module of white is allowed in front, for aliasing at the corner.
    int[] diagonal = new int[FINDER.length];
    new BitMatrixCursorI(image, tl, new PointI(1, 1))
        .readPatternFromBlack(diagonal, 1, 0, 0);
    if (Patterns.isPattern(diagonal, 0, FINDER, FINDER_MODULES, 0, 0, 0) == 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    // The subpattern, read along the diagonal from the bottom right corner.
    int[] subDiagonal = new int[SUBPATTERN.length];
    new BitMatrixCursorI(image, br, new PointI(-1, -1))
        .readPatternFromBlack(subDiagonal, 1, 0, 0);
    if (Patterns.isPattern(subDiagonal, 0, SUBPATTERN, SUBPATTERN_MODULES, 0, 0, 0) == 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    double moduleSizeTotal = sum(diagonal) + sum(subDiagonal);

    // The four edge timing patterns. Measuring the module size from all of
    // them, rather than from the finder alone, is what lets this reject an
    // image that merely starts like a symbol.
    PointI[] starts = {tr, bl, tl, br};
    PointI[] directions = {new PointI(-1, 0), new PointI(1, 0), new PointI(1, 0), new PointI(-1, 0)};
    for (int i = 0; i < 4; i++) {
      BitMatrixCursorI cursor = new BitMatrixCursorI(image, starts[i], directions[i]);
      // Step over the corner, the finder and the subpattern edge.
      cursor.stepToEdge(2 + (cursor.isWhite() ? 1 : 0), 0, false);
      int[] timing = new int[TIMING.length];
      cursor.readPattern(timing);
      if (Patterns.isPattern(timing, 0, TIMING, TIMING_MODULES, 0, 0, 0) == 0) {
        throw NotFoundException.getNotFoundInstance();
      }
      moduleSizeTotal += sum(timing);
    }

    double moduleSize = moduleSizeTotal / (FINDER_MODULES + SUBPATTERN_MODULES + 4 * TIMING_MODULES);
    if (moduleSize <= 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    int dimW = (int) Math.round(width / moduleSize);
    int dimH = (int) Math.round(height / moduleSize);
    if (!RMQRVersion.isValidSize(dimW, dimH)) {
      throw NotFoundException.getNotFoundInstance();
    }

    BitMatrix bits = deflate(image, dimW, dimH,
        top + moduleSize / 2, left + moduleSize / 2, moduleSize);
    return new DetectorResult(bits, new ResultPoint[] {
        new ResultPoint(left, top),
        new ResultPoint(right, top),
        new ResultPoint(right, bottom),
        new ResultPoint(left, bottom),
    });
  }

  /** Crops and subsamples: one image pixel read per module, at its centre. */
  private static BitMatrix deflate(BitMatrix image, int width, int height,
                                   double top, double left, double subSampling) {
    BitMatrix result = new BitMatrix(width, height);
    for (int y = 0; y < height; y++) {
      double yOffset = top + y * subSampling;
      for (int x = 0; x < width; x++) {
        int px = (int) (left + x * subSampling);
        int py = (int) yOffset;
        if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()
            && image.get(px, py)) {
          result.set(x, y);
        }
      }
    }
    return result;
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }
}
