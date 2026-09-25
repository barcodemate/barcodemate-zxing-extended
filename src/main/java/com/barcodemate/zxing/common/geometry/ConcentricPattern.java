/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

/**
 * A located concentric pattern: where its centre is, and how wide the whole
 * pattern is in pixels.
 *
 * <p>A QR finder pattern is concentric -- a black square inside a white ring
 * inside a black ring -- and so is the single one an rMQR symbol carries. The
 * size is what gives the detector the symbol's scale.</p>
 */
public final class ConcentricPattern {

  public final PointF center;
  public final double size;

  public ConcentricPattern(PointF center, double size) {
    this.center = center;
    this.size = size;
  }

  public ConcentricPattern withSize(double newSize) {
    return new ConcentricPattern(center, newSize);
  }

  @Override
  public String toString() {
    return center + "@" + size;
  }
}
