/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRDetector.cpp (tag
 * v3.1.1, commit 287c85d), DetectPureMQR.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.geometry.BitMatrixCursorI;
import com.barcodemate.zxing.common.geometry.ConcentricPattern;
import com.barcodemate.zxing.common.geometry.FinderPatternFinder;
import com.barcodemate.zxing.common.geometry.PointI;
import com.barcodemate.zxing.common.geometry.Patterns;

/**
 * Finds a Micro QR symbol in a binarised image.
 *
 * <p>With one finder pattern and no alignment patterns, there is less to go on
 * than in QR but more than in rMQR: the symbol is square, so its bounding box
 * and the finder's own width are enough to fix the module size and the
 * dimension.</p>
 */
public final class MicroQRDetector {

  private static final int[] FINDER = {1, 1, 3, 1, 1};
  private static final int FINDER_MODULES = 7;

  /** M1, the smallest Micro QR, is 11 modules square. */
  private static final int MIN_MODULES = 11;

  private MicroQRDetector() {
  }

  /**
   * Detects a symbol anywhere in the image, at any rotation.
   *
   * <p>Tries the pure path first, which is cheap and handles the common case
   * of an image that is nothing but the symbol, then falls back to searching
   * for finder patterns and sampling from each.</p>
   */
  public static DetectorResult detect(BitMatrix image) throws NotFoundException {
    try {
      return detectPure(image);
    } catch (NotFoundException notPure) {
      // Fall through to the general search.
    }
    DetectorResult result = search(image);
    if (result != null) {
      return result;
    }
    // Symbols printed light on dark are legal and do occur. Inverting costs
    // one pass and is only reached when the normal one found nothing.
    BitMatrix inverted = image.clone();
    inverted.flip();
    result = search(inverted);
    if (result != null) {
      return result;
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private static DetectorResult search(BitMatrix image) {
    for (ConcentricPattern finder : FinderPatternFinder.find(image, true)) {
      DetectorResult result = MicroQRSampler.sample(image, finder);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** Detects a symbol that occupies the whole image, upright. */
  public static DetectorResult detectPure(BitMatrix image) throws NotFoundException {
    int[] box = image.getEnclosingRectangle();
    if (box == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    int left = box[0];
    int top = box[1];
    int width = box[2];
    int height = box[3];
    // Micro QR is square, give or take a pixel of aliasing.
    if (width < MIN_MODULES || height < MIN_MODULES || Math.abs(width - height) > 1) {
      throw NotFoundException.getNotFoundInstance();
    }

    // The finder pattern, read along the diagonal from the top left corner.
    int[] diagonal = new int[FINDER.length];
    new BitMatrixCursorI(image, new PointI(left, top), new PointI(1, 1))
        .readPatternFromBlack(diagonal, 1, 0, 0);
    if (Patterns.isPattern(diagonal, 0, FINDER, FINDER_MODULES, 0, 0, 0) == 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    // The finder is 7 modules across its diagonal, which fixes the scale.
    double moduleSize = sum(diagonal) / 7.0;
    if (moduleSize <= 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    int dimension = (int) Math.round(width / moduleSize);
    if (!MicroQRVersion.isValidDimension(dimension)) {
      throw NotFoundException.getNotFoundInstance();
    }

    // The far corner of the grid must still be inside the image, or the
    // module size was measured too small and the grid runs off the edge.
    double lastX = left + moduleSize / 2 + (dimension - 1) * moduleSize;
    double lastY = top + moduleSize / 2 + (dimension - 1) * moduleSize;
    if (lastX >= image.getWidth() || lastY >= image.getHeight()) {
      throw NotFoundException.getNotFoundInstance();
    }

    BitMatrix bits = new BitMatrix(dimension, dimension);
    for (int y = 0; y < dimension; y++) {
      int py = (int) (top + moduleSize / 2 + y * moduleSize);
      for (int x = 0; x < dimension; x++) {
        int px = (int) (left + moduleSize / 2 + x * moduleSize);
        if (image.get(px, py)) {
          bits.set(x, y);
        }
      }
    }

    int right = left + width - 1;
    int bottom = top + height - 1;
    return new DetectorResult(bits, new ResultPoint[] {
        new ResultPoint(left, top),
        new ResultPoint(right, top),
        new ResultPoint(right, bottom),
        new ResultPoint(left, bottom),
    });
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }
}
