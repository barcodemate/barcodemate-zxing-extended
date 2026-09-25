/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;

/**
 * A cursor that walks Bresenham style: the direction is scaled so its larger
 * component is 1, so each step advances one pixel along the dominant axis and
 * a fraction along the other. Positions are therefore generally between
 * pixels, and reads truncate to the pixel the position falls in.
 */
public final class BitMatrixCursorF extends BitMatrixCursor {

  public BitMatrixCursorF(BitMatrix image, PointF p, PointF d) {
    super(image, p, d.bresenhamDirection());
  }

  @Override
  public void setDirection(PointF dir) {
    d = dir.bresenhamDirection();
  }

  @Override
  public BitMatrixCursorF movedBy(PointF offset) {
    return new BitMatrixCursorF(image, p.plus(offset), d);
  }

  @Override
  public BitMatrixCursorF turnedBack() {
    return new BitMatrixCursorF(image, p, back());
  }
}
