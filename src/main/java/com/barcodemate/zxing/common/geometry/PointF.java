/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/Point.h (tag v3.1.1, commit
 * 287c85d), the PointT<double> instantiation.
 */

package com.barcodemate.zxing.common.geometry;

/**
 * An immutable 2D point in double precision, with the vector arithmetic the
 * ported detection code is written against.
 *
 * <p>ZXing for Java has {@link com.barcodemate.zxing.ResultPoint}, but that is
 * a result type: single precision, and with no arithmetic beyond a couple of
 * static helpers. The detection algorithms ported from zxing-cpp do real vector
 * work -- projections, normals, line fitting -- in double precision, and
 * rounding them to float partway through would change their results.</p>
 *
 * <p>Upstream's {@code PointT<T>} is a template; its integer instantiation is
 * {@link PointI}, and the two are deliberately separate types here because
 * integer division truncates and several algorithms depend on that.</p>
 */
public final class PointF {

  public static final PointF ZERO = new PointF(0, 0);

  public final double x;
  public final double y;

  public PointF(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public PointF plus(PointF b) {
    return new PointF(x + b.x, y + b.y);
  }

  public PointF minus(PointF b) {
    return new PointF(x - b.x, y - b.y);
  }

  public PointF negated() {
    return new PointF(-x, -y);
  }

  public PointF times(double s) {
    return new PointF(s * x, s * y);
  }

  public PointF dividedBy(double d) {
    return new PointF(x / d, y / d);
  }

  public double dot(PointF b) {
    return x * b.x + y * b.y;
  }

  public double cross(PointF b) {
    return x * b.y - b.x * y;
  }

  /** L1 norm. */
  public double sumAbsComponent() {
    return Math.abs(x) + Math.abs(y);
  }

  /** L2 norm. */
  public double length() {
    return Math.sqrt(dot(this));
  }

  /** L-infinity norm. */
  public double maxAbsComponent() {
    return Math.max(Math.abs(x), Math.abs(y));
  }

  public double distance(PointF b) {
    return minus(b).length();
  }

  public PointF normalized() {
    return dividedBy(length());
  }

  /** Scales so the larger component is 1, for stepping one pixel at a time. */
  public PointF bresenhamDirection() {
    return dividedBy(maxAbsComponent());
  }

  /** Drops the smaller component, leaving an axis-aligned direction. */
  public PointF mainDirection() {
    return Math.abs(x) > Math.abs(y) ? new PointF(x, 0) : new PointF(0, y);
  }

  public PointF right() {
    return new PointF(-y, x);
  }

  public PointF left() {
    return new PointF(y, -x);
  }

  /** The centre of the pixel this point falls in. */
  public PointF centered() {
    return new PointF(Math.floor(x) + 0.5, Math.floor(y) + 0.5);
  }

  public boolean isZero() {
    return x == 0 && y == 0;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof PointF)) {
      return false;
    }
    PointF o = (PointF) other;
    return x == o.x && y == o.y;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(x) * 31 + Double.hashCode(y);
  }

  @Override
  public String toString() {
    return x + "x" + y;
  }
}
