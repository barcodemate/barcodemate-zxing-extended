/*
 * Copyright 2007 ZXing authors
 * Copyright 2016 Nu-book Inc.
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/PerspectiveTransform.cpp (tag
 * v3.1.1, commit 287c85d), which is itself the hardened descendant of ZXing for
 * Java's own com.google.zxing.common.PerspectiveTransform -- same algorithm,
 * from section 3.4.2 of George Wolberg's "Digital Image Warping", pages 54-56.
 */

package com.barcodemate.zxing.common.geometry;

/**
 * Maps one quadrilateral onto another: here, module coordinates in a symbol's
 * own grid onto pixel coordinates in the image.
 *
 * <p>ZXing for Java already has a class of this name and the same algorithm, so
 * a word on why this one exists rather than reusing it.</p>
 *
 * <ul>
 * <li><b>It refuses degenerate input.</b> A quadrilateral with one corner
 * nearly in line with two others is convex, yet the transform built from it
 * projects points near that corner outside the image while the corners
 * themselves land inside -- a true perspective transform cannot do that. Both
 * quadrilaterals are checked (see {@link Quadrilateral#isConvex()}) and the
 * transform is left invalid if either fails.</li>
 * <li><b>It works in double precision.</b> The upstream Java class uses float,
 * which is where some of that instability comes from.</li>
 * <li><b>It takes quadrilaterals rather than sixteen loose floats</b>, which is
 * hard to pass in the wrong order.</li>
 * </ul>
 *
 * <p>The Java class never needed any of this: its detector only ever builds a
 * transform from three finder patterns that have already been validated against
 * each other. The detectors here feed it quadrilaterals derived from a single
 * finder pattern, which is exactly the input the checks exist for.</p>
 */
public final class PerspectiveTransform {

  private final double a11;
  private final double a12;
  private final double a13;
  private final double a21;
  private final double a22;
  private final double a23;
  private final double a31;
  private final double a32;
  private final double a33;

  /** The invalid transform, which every accessor reports as not usable. */
  public static final PerspectiveTransform INVALID =
      new PerspectiveTransform(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN);

  private PerspectiveTransform(double a11, double a21, double a31,
                               double a12, double a22, double a32,
                               double a13, double a23, double a33) {
    this.a11 = a11;
    this.a12 = a12;
    this.a13 = a13;
    this.a21 = a21;
    this.a22 = a22;
    this.a23 = a23;
    this.a31 = a31;
    this.a32 = a32;
    this.a33 = a33;
  }

  /** The transform taking {@code src} onto {@code dst}, or an invalid one. */
  public static PerspectiveTransform of(Quadrilateral src, Quadrilateral dst) {
    if (!src.isConvex() || !dst.isConvex()) {
      return INVALID;
    }
    return unitSquareTo(dst).times(unitSquareTo(src).inverse());
  }

  public boolean isValid() {
    return !Double.isNaN(a33);
  }

  /** Projects a point from the grid into the image. */
  public PointF apply(PointF p) {
    double denominator = a13 * p.x + a23 * p.y + a33;
    return new PointF((a11 * p.x + a21 * p.y + a31) / denominator,
        (a12 * p.x + a22 * p.y + a32) / denominator);
  }

  private static PerspectiveTransform unitSquareTo(Quadrilateral q) {
    double x0 = q.get(0).x;
    double y0 = q.get(0).y;
    double x1 = q.get(1).x;
    double y1 = q.get(1).y;
    double x2 = q.get(2).x;
    double y2 = q.get(2).y;
    double x3 = q.get(3).x;
    double y3 = q.get(3).y;

    PointF d3 = q.get(0).minus(q.get(1)).plus(q.get(2)).minus(q.get(3));
    if (d3.isZero()) {
      // Affine: opposite sides are parallel, so there is no vanishing point.
      return new PerspectiveTransform(
          x1 - x0, x2 - x1, x0,
          y1 - y0, y2 - y1, y0,
          0.0, 0.0, 1.0);
    }

    PointF d1 = q.get(1).minus(q.get(2));
    PointF d2 = q.get(3).minus(q.get(2));
    double denominator = d1.cross(d2);
    double a13 = d3.cross(d2) / denominator;
    double a23 = d1.cross(d3) / denominator;
    return new PerspectiveTransform(
        x1 - x0 + a13 * x1, x3 - x0 + a23 * x3, x0,
        y1 - y0 + a13 * y1, y3 - y0 + a23 * y3, y0,
        a13, a23, 1.0);
  }

  /** The adjoint, which serves as the inverse here since scale does not matter. */
  private PerspectiveTransform inverse() {
    return new PerspectiveTransform(
        a22 * a33 - a23 * a32,
        a23 * a31 - a21 * a33,
        a21 * a32 - a22 * a31,
        a13 * a32 - a12 * a33,
        a11 * a33 - a13 * a31,
        a12 * a31 - a11 * a32,
        a12 * a23 - a13 * a22,
        a13 * a21 - a11 * a23,
        a11 * a22 - a12 * a21);
  }

  private PerspectiveTransform times(PerspectiveTransform o) {
    return new PerspectiveTransform(
        a11 * o.a11 + a21 * o.a12 + a31 * o.a13,
        a11 * o.a21 + a21 * o.a22 + a31 * o.a23,
        a11 * o.a31 + a21 * o.a32 + a31 * o.a33,
        a12 * o.a11 + a22 * o.a12 + a32 * o.a13,
        a12 * o.a21 + a22 * o.a22 + a32 * o.a23,
        a12 * o.a31 + a22 * o.a32 + a32 * o.a33,
        a13 * o.a11 + a23 * o.a12 + a33 * o.a13,
        a13 * o.a21 + a23 * o.a22 + a33 * o.a23,
        a13 * o.a31 + a23 * o.a32 + a33 * o.a33);
  }
}
