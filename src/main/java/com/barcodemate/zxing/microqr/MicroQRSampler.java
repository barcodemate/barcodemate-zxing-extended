/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRDetector.cpp (tag
 * v3.1.1, commit 287c85d), SampleMQR.
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.geometry.ConcentricFinder;
import com.barcodemate.zxing.common.geometry.ConcentricPattern;
import com.barcodemate.zxing.common.geometry.GridSampler;
import com.barcodemate.zxing.common.geometry.PerspectiveTransform;
import com.barcodemate.zxing.common.geometry.PointF;
import com.barcodemate.zxing.common.geometry.Quadrilateral;

/**
 * Samples a Micro QR symbol from a located finder pattern, at any rotation.
 *
 * <p>With one finder pattern there is nothing to say which way up the symbol
 * is, so all four quarter turns are tried and the one whose format information
 * decodes with the fewest wrong bits wins. The format information is doing
 * double duty here: it carries the version and error correction level, and by
 * being readable at all it establishes the orientation.</p>
 */
final class MicroQRSampler {

  /** Where the format information sits, in module coordinates. */
  private static final int[][] FORMAT_INFO_COORDS = {
      {0, 8}, {1, 8}, {2, 8}, {3, 8}, {4, 8}, {5, 8}, {6, 8}, {7, 8}, {8, 8},
      {8, 7}, {8, 6}, {8, 5}, {8, 4}, {8, 3}, {8, 2}, {8, 1}, {8, 0},
  };

  private MicroQRSampler() {
  }

  static DetectorResult sample(BitMatrix image, ConcentricPattern finder) {
    Quadrilateral finderQuad = ConcentricFinder.findConcentricPatternCorners(
        image, finder.center, (int) finder.size, 2);
    if (finderQuad == null) {
      return null;
    }
    Quadrilateral srcQuad = Quadrilateral.rectangle(7, 7, 0.5);

    MicroQRFormatInformation bestInfo = null;
    PerspectiveTransform bestTransform = null;

    for (int turn = 0; turn < 4; turn++) {
      PerspectiveTransform mod2Pix =
          PerspectiveTransform.of(srcQuad, finderQuad.rotatedCorners(turn));
      if (!mod2Pix.isValid()) {
        continue;
      }
      // Both innermost timing modules must be where this orientation says.
      if (!isBlack(image, mod2Pix, FORMAT_INFO_COORDS[0])
          || !isIn(image, mod2Pix, FORMAT_INFO_COORDS[8])
          || !isBlack(image, mod2Pix, FORMAT_INFO_COORDS[16])) {
        continue;
      }

      int bits = 0;
      for (int i = 1; i <= 15; i++) {
        bits = (bits << 1) | (isBlack(image, mod2Pix, FORMAT_INFO_COORDS[i]) ? 1 : 0);
      }
      MicroQRFormatInformation info = MicroQRFormatInformation.decode(bits);
      if (bestInfo == null || info.getHammingDistance() < bestInfo.getHammingDistance()) {
        bestInfo = info;
        bestTransform = mod2Pix;
      }
    }

    if (bestInfo == null || !bestInfo.isValid()) {
      return null;
    }
    MicroQRVersion version = bestInfo.getVersion();
    if (version == null) {
      return null;
    }
    int dimension = version.getDimension();

    // Guard against mistaking the corner of an ordinary QR symbol for a Micro
    // QR one: outside a Micro QR symbol is quiet zone, and a QR symbol's
    // interior is about half black.
    int blackPixels = 0;
    for (int i = 0; i < dimension; i++) {
      boolean beyondRight = isBlack(image, bestTransform, new int[] {i, dimension});
      boolean beyondBottom = isBlack(image, bestTransform, new int[] {dimension, i});
      if (beyondRight && beyondBottom) {
        blackPixels++;
      }
    }
    if (blackPixels > 2 * dimension / 3) {
      return null;
    }

    return GridSampler.sampleGrid(image, dimension, dimension, bestTransform);
  }

  private static boolean isBlack(BitMatrix image, PerspectiveTransform mod2Pix, int[] module) {
    PointF p = mod2Pix.apply(new PointF(module[0] + 0.5, module[1] + 0.5));
    return isIn(image, p) && image.get((int) p.x, (int) p.y);
  }

  private static boolean isIn(BitMatrix image, PerspectiveTransform mod2Pix, int[] module) {
    return isIn(image, mod2Pix.apply(new PointF(module[0] + 0.5, module[1] + 0.5)));
  }

  private static boolean isIn(BitMatrix image, PointF p) {
    return p.x >= 0 && p.y >= 0 && p.x < image.getWidth() && p.y < image.getHeight();
  }
}
