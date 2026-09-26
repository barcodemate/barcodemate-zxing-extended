/*
 * Copyright 2023 gitlost
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRDetector.cpp (tag
 * v3.1.1, commit 287c85d), SampleRMQR.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import com.barcodemate.zxing.common.geometry.ConcentricFinder;
import com.barcodemate.zxing.common.geometry.ConcentricPattern;
import com.barcodemate.zxing.common.geometry.GridSampler;
import com.barcodemate.zxing.common.geometry.PerspectiveTransform;
import com.barcodemate.zxing.common.geometry.PointF;
import com.barcodemate.zxing.common.geometry.Quadrilateral;

/**
 * Samples an rMQR symbol from its located finder pattern, at any rotation.
 *
 * <p>rMQR is the hardest case in the family for this: one finder pattern in one
 * corner, and a symbol that is not square, so neither the orientation nor the
 * shape can be assumed. Both come out of the format information, which is why
 * it is read before anything else is sampled. All four quarter turns are tried
 * and the reading with the fewest wrong bits wins; that reading also carries
 * the version, and so the symbol's width and height.</p>
 *
 * <p>The top edge timing pattern is checked first, as a cheap way to discard
 * three of the four orientations before spending anything on the format bits.</p>
 */
final class RMQRSampler {

  /** The first four modules of the top edge timing pattern. */
  private static final int[][] TIMING_COORDS = {{8, 0}, {9, 0}, {10, 0}, {11, 0}};

  /** Where the format information sits beside the finder pattern. */
  private static final int[][] FORMAT_INFO_COORDS = {
      {11, 3}, {11, 2}, {11, 1},
      {10, 5}, {10, 4}, {10, 3}, {10, 2}, {10, 1},
      {9, 5}, {9, 4}, {9, 3}, {9, 2}, {9, 1},
      {8, 5}, {8, 4}, {8, 3}, {8, 2}, {8, 1},
  };

  private RMQRSampler() {
  }

  static DetectorResult sample(BitMatrix image, ConcentricPattern finder) {
    Quadrilateral finderQuad = ConcentricFinder.findConcentricPatternCorners(
        image, finder.center, (int) finder.size, 2);
    if (finderQuad == null) {
      return null;
    }
    Quadrilateral srcQuad = Quadrilateral.rectangle(7, 7, 0.5);

    RMQRFormatInformation bestInfo = null;
    PerspectiveTransform bestTransform = null;

    for (int turn = 0; turn < 4; turn++) {
      PerspectiveTransform mod2Pix =
          PerspectiveTransform.of(srcQuad, finderQuad.rotatedCorners(turn));
      if (!mod2Pix.isValid()) {
        continue;
      }
      // The timing pattern alternates; if it does not, this is the wrong way up.
      if (!moduleIs(image, mod2Pix, TIMING_COORDS[0], true)
          || !moduleIs(image, mod2Pix, TIMING_COORDS[1], false)
          || !moduleIs(image, mod2Pix, TIMING_COORDS[2], true)
          || !moduleIs(image, mod2Pix, TIMING_COORDS[3], false)) {
        continue;
      }

      int bits = 0;
      for (int[] coord : FORMAT_INFO_COORDS) {
        bits = (bits << 1) | (isBlack(image, mod2Pix, coord) ? 1 : 0);
      }
      // Only the copy beside the finder pattern is available at this point;
      // the other one is at the far corner, which has not been located yet.
      RMQRFormatInformation info = RMQRFormatInformation.decode(bits, 0);
      if (bestInfo == null || info.getHammingDistance() < bestInfo.getHammingDistance()) {
        bestInfo = info;
        bestTransform = mod2Pix;
      }
    }

    if (bestInfo == null || !bestInfo.isValid()) {
      return null;
    }
    RMQRVersion version = bestInfo.getVersion();
    if (version == null) {
      return null;
    }
    return GridSampler.sampleGrid(image, version.getWidth(), version.getHeight(), bestTransform);
  }

  private static boolean moduleIs(BitMatrix image, PerspectiveTransform mod2Pix,
                                  int[] module, boolean black) {
    PointF p = mod2Pix.apply(new PointF(module[0] + 0.5, module[1] + 0.5));
    if (p.x < 0 || p.y < 0 || p.x >= image.getWidth() || p.y >= image.getHeight()) {
      return false;
    }
    return image.get((int) p.x, (int) p.y) == black;
  }

  private static boolean isBlack(BitMatrix image, PerspectiveTransform mod2Pix, int[] module) {
    return moduleIs(image, mod2Pix, module, true);
  }
}
