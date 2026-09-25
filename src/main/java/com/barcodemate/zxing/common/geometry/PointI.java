/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/Point.h (tag v3.1.1, commit
 * 287c85d), the PointT<int> instantiation.
 */

package com.barcodemate.zxing.common.geometry;

/**
 * An immutable 2D point in integer coordinates: a pixel position.
 *
 * <p>Kept separate from {@link PointF} rather than folded into it, because the
 * arithmetic genuinely differs. {@code bresenhamDirection} divides by the
 * larger component, and on integers that truncation is the point: it turns a
 * direction into one of the eight pixel steps. Doing the same in floating point
 * and rounding afterwards is not the same operation.</p>
 */
public final class PointI {

  public static final PointI ZERO = new PointI(0, 0);

  public final int x;
  public final int y;

  public PointI(int x, int y) {
    this.x = x;
    this.y = y;
  }

  /** Truncates towards zero, matching a C++ {@code PointI(PointF)} conversion. */
  public PointI(PointF p) {
    this((int) p.x, (int) p.y);
  }

  public PointF toPointF() {
    return new PointF(x, y);
  }

  public PointI plus(PointI b) {
    return new PointI(x + b.x, y + b.y);
  }

  public PointI minus(PointI b) {
    return new PointI(x - b.x, y - b.y);
  }

  public PointI negated() {
    return new PointI(-x, -y);
  }

  public PointI times(int s) {
    return new PointI(s * x, s * y);
  }

  /** Integer division, truncating towards zero. */
  public PointI dividedBy(int d) {
    return new PointI(x / d, y / d);
  }

  public int dot(PointI b) {
    return x * b.x + y * b.y;
  }

  public int cross(PointI b) {
    return x * b.y - b.x * y;
  }

  public int sumAbsComponent() {
    return Math.abs(x) + Math.abs(y);
  }

  public double length() {
    return Math.sqrt((double) dot(this));
  }

  public int maxAbsComponent() {
    return Math.max(Math.abs(x), Math.abs(y));
  }

  public double distance(PointI b) {
    return minus(b).length();
  }

  public PointF normalized() {
    return toPointF().normalized();
  }

  /** Integer division, so the result is one of the eight pixel steps. */
  public PointI bresenhamDirection() {
    return dividedBy(maxAbsComponent());
  }

  public PointI mainDirection() {
    return Math.abs(x) > Math.abs(y) ? new PointI(x, 0) : new PointI(0, y);
  }

  public PointI right() {
    return new PointI(-y, x);
  }

  public PointI left() {
    return new PointI(y, -x);
  }

  /** The centre of this pixel, as a floating point coordinate. */
  public PointF centered() {
    return new PointF(x + 0.5, y + 0.5);
  }

  public boolean isZero() {
    return x == 0 && y == 0;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof PointI)) {
      return false;
    }
    PointI o = (PointI) other;
    return x == o.x && y == o.y;
  }

  @Override
  public int hashCode() {
    return x * 31 + y;
  }

  @Override
  public String toString() {
    return x + "x" + y;
  }
}
