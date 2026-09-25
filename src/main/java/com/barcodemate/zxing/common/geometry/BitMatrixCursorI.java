/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;

/**
 * A cursor that steps whole pixels: its direction is taken exactly as given,
 * so it moves horizontally, vertically or diagonally and never lands between
 * pixels.
 */
public final class BitMatrixCursorI extends BitMatrixCursor {

  public BitMatrixCursorI(BitMatrix image, PointI p, PointI d) {
    super(image, p.toPointF(), d.toPointF());
  }

  private BitMatrixCursorI(BitMatrix image, PointF p, PointF d) {
    super(image, p, d);
  }

  @Override
  public void setDirection(PointF dir) {
    d = dir;
  }

  @Override
  public BitMatrixCursorI movedBy(PointF offset) {
    return new BitMatrixCursorI(image, p.plus(offset), d);
  }

  @Override
  public BitMatrixCursorI turnedBack() {
    return new BitMatrixCursorI(image, p, back());
  }
}
