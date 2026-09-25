/*
 * Copyright 2016 Nu-book Inc.
 * Copyright 2016 ZXing authors
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/GridSampler.cpp (tag v3.1.1,
 * commit 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;

import java.util.Collections;
import java.util.List;

/**
 * Reads a symbol's modules out of an image, given a transform from module
 * coordinates to pixel coordinates.
 *
 * <p>This is the last step of detection and the first of decoding: everything
 * before it works in pixels and is about finding the symbol, everything after
 * it works in modules and is about reading it.</p>
 *
 * <p>A module is sampled at its centre, which is why the transform is applied
 * to {@code (x + 0.5, y + 0.5)} rather than to the module's corner.</p>
 */
public final class GridSampler {

  private GridSampler() {
  }

  /** One rectangle of the module grid and the transform that maps it. */
  public static final class ROI {
    public final int x0;
    public final int x1;
    public final int y0;
    public final int y1;
    public final PerspectiveTransform mod2Pix;

    public ROI(int x0, int x1, int y0, int y1, PerspectiveTransform mod2Pix) {
      this.x0 = x0;
      this.x1 = x1;
      this.y0 = y0;
      this.y1 = y1;
      this.mod2Pix = mod2Pix;
    }
  }

  public static DetectorResult sampleGrid(BitMatrix image, int width, int height,
                                          PerspectiveTransform mod2Pix) {
    return sampleGrid(image, width, height,
        Collections.singletonList(new ROI(0, width, 0, height, mod2Pix)));
  }

  /**
   * @return the sampled modules and the symbol's corners in the image, or null
   *         if any part of the grid falls outside the image
   */
  public static DetectorResult sampleGrid(BitMatrix image, int width, int height, List<ROI> rois) {
    if (width <= 0 || height <= 0) {
      return null;
    }

    // Check each region's corners first, to give up early on a grid that
    // obviously does not lie inside the image.
    for (ROI roi : rois) {
      if (!roi.mod2Pix.isValid()
          || !isInside(image, roi.mod2Pix, roi.x0, roi.y0)
          || !isInside(image, roi.mod2Pix, roi.x1 - 1, roi.y0)
          || !isInside(image, roi.mod2Pix, roi.x1 - 1, roi.y1 - 1)
          || !isInside(image, roi.mod2Pix, roi.x0, roi.y1 - 1)) {
        return null;
      }
    }

    BitMatrix bits = new BitMatrix(width, height);
    for (ROI roi : rois) {
      for (int y = roi.y0; y < roi.y1; y++) {
        for (int x = roi.x0; x < roi.x1; x++) {
          PointF p = roi.mod2Pix.apply(centered(x, y));
          // Even with every boundary point projecting inside the image, an
          // interior one may not: the transform generation is not perfectly
          // stable numerically, and a true perspective transform cannot have
          // this property. Upstream found checking every point cheaper than
          // feared, so it is checked.
          if (!isIn(image, p)) {
            return null;
          }
          if (image.get((int) p.x, (int) p.y)) {
            bits.set(x, y);
          }
        }
      }
    }

    ResultPoint[] corners = {
        projectCorner(rois, 0, 0),
        projectCorner(rois, width, 0),
        projectCorner(rois, width, height),
        projectCorner(rois, 0, height),
    };
    return new DetectorResult(bits, corners);
  }

  private static ResultPoint projectCorner(List<ROI> rois, int x, int y) {
    for (ROI roi : rois) {
      if (roi.x0 <= x && x <= roi.x1 && roi.y0 <= y && y <= roi.y1) {
        // Corners are taken at the module's corner, not its centre, then
        // rounded to the pixel they fall in.
        PointF p = roi.mod2Pix.apply(new PointF(x, y)).plus(new PointF(0.5, 0.5));
        return new ResultPoint((int) p.x, (int) p.y);
      }
    }
    return new ResultPoint(0, 0);
  }

  private static boolean isInside(BitMatrix image, PerspectiveTransform mod2Pix, int x, int y) {
    return isIn(image, mod2Pix.apply(centered(x, y)));
  }

  private static boolean isIn(BitMatrix image, PointF p) {
    return p.x >= 0 && p.x < image.getWidth() && p.y >= 0 && p.y < image.getHeight();
  }

  private static PointF centered(int x, int y) {
    return new PointF(x + 0.5, y + 0.5);
  }
}
