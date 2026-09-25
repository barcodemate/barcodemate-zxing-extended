/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/Quadrilateral.h (tag v3.1.1,
 * commit 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

/**
 * Four corner points, in the order top-left, top-right, bottom-right,
 * bottom-left.
 *
 * <p>Those names are relative to the <em>symbol</em>, not to the image. A
 * symbol photographed upside down has its top-left corner at the bottom right
 * of the picture, and this class keeps the symbol's own sense of the word, so
 * that the sampling grid built from it comes out the right way up.</p>
 */
public final class Quadrilateral {

  private final PointF[] corners;

  public Quadrilateral(PointF topLeft, PointF topRight, PointF bottomRight, PointF bottomLeft) {
    this.corners = new PointF[] {topLeft, topRight, bottomRight, bottomLeft};
  }

  public PointF get(int index) {
    return corners[index];
  }

  public PointF topLeft() {
    return corners[0];
  }

  public PointF topRight() {
    return corners[1];
  }

  public PointF bottomRight() {
    return corners[2];
  }

  public PointF bottomLeft() {
    return corners[3];
  }

  /**
   * The angle of the horizontal centre line, in radians: 0 when it is parallel
   * to the x-axis, positive clockwise.
   */
  public double orientation() {
    PointF centerLine = topRight().plus(bottomRight()).minus(topLeft().plus(bottomLeft()));
    if (centerLine.isZero()) {
      return 0;
    }
    PointF normalized = centerLine.normalized();
    return Math.atan2(normalized.y, normalized.x);
  }

  public PointF center() {
    PointF sum = PointF.ZERO;
    for (PointF p : corners) {
      sum = sum.plus(p);
    }
    return sum.dividedBy(4);
  }

  /** An axis-aligned rectangle inset by {@code margin} on every side. */
  public static Quadrilateral rectangle(double width, double height, double margin) {
    return new Quadrilateral(
        new PointF(margin, margin),
        new PointF(width - margin, margin),
        new PointF(width - margin, height - margin),
        new PointF(margin, height - margin));
  }

  /** A square of the given size centred on the origin. Halved by integer division, as upstream. */
  public static Quadrilateral centeredSquare(int size) {
    int half = size / 2;
    return new Quadrilateral(
        new PointF(-half, -half), new PointF(half, -half),
        new PointF(half, half), new PointF(-half, half));
  }

  public Quadrilateral scaled(double factor) {
    return new Quadrilateral(corners[0].times(factor), corners[1].times(factor),
        corners[2].times(factor), corners[3].times(factor));
  }

  public Quadrilateral moved(PointF offset) {
    return new Quadrilateral(corners[0].plus(offset), corners[1].plus(offset),
        corners[2].plus(offset), corners[3].plus(offset));
  }

  /**
   * The same quadrilateral with its corners relabelled, as when a symbol turns
   * out to be rotated a quarter turn from what was assumed.
   *
   * @param n quarter turns, may be negative
   * @param mirror whether to also swap the two off-diagonal corners
   */
  public Quadrilateral rotatedCorners(int n, boolean mirror) {
    int offset = ((n % 4) + 4) % 4;
    PointF[] rotated = new PointF[4];
    for (int i = 0; i < 4; i++) {
      rotated[i] = corners[(i + offset) % 4];
    }
    if (mirror) {
      PointF swap = rotated[1];
      rotated[1] = rotated[3];
      rotated[3] = swap;
    }
    return new Quadrilateral(rotated[0], rotated[1], rotated[2], rotated[3]);
  }

  public Quadrilateral rotatedCorners(int n) {
    return rotatedCorners(n, false);
  }

  public Quadrilateral boundingBox() {
    double minX = Math.min(Math.min(corners[0].x, corners[1].x), Math.min(corners[2].x, corners[3].x));
    double maxX = Math.max(Math.max(corners[0].x, corners[1].x), Math.max(corners[2].x, corners[3].x));
    double minY = Math.min(Math.min(corners[0].y, corners[1].y), Math.min(corners[2].y, corners[3].y));
    double maxY = Math.max(Math.max(corners[0].y, corners[1].y), Math.max(corners[2].y, corners[3].y));
    return new Quadrilateral(new PointF(minX, minY), new PointF(maxX, minY),
        new PointF(maxX, maxY), new PointF(minX, maxY));
  }

  /**
   * Whether the corners are in convex position <em>and</em> not too lopsided.
   *
   * <p>Convexity alone is not enough. A quadrilateral with one corner almost in
   * line with two others is convex, yet the perspective transform built from it
   * projects points near that corner outside the image even though the corners
   * themselves land inside. Upstream found the ratio between the largest and
   * smallest cross product stays under 2 across its whole sample set, reaches
   * about 3 for genuinely skewed symbols, and 14 for a case that triggered the
   * instability; 4 is the line it draws, and this keeps it.</p>
   */
  public boolean isConvex() {
    boolean sign = false;
    double min = Double.POSITIVE_INFINITY;
    double max = 0;

    for (int i = 0; i < 4; i++) {
      PointF d1 = corners[(i + 2) % 4].minus(corners[(i + 1) % 4]);
      PointF d2 = corners[i].minus(corners[(i + 1) % 4]);
      double cp = d1.cross(d2);

      double magnitude = Math.abs(cp);
      min = Math.min(min, magnitude);
      max = Math.max(max, magnitude);

      if (i == 0) {
        sign = cp > 0;
      } else if (sign != cp > 0) {
        return false;
      }
    }
    return max / min < 4.0;
  }

  /** Whether the point lies on the same side of every edge. */
  public boolean isInside(PointF p) {
    int positive = 0;
    int negative = 0;
    for (int i = 0; i < 4; i++) {
      if (p.minus(corners[i]).cross(corners[(i + 1) % 4].minus(corners[i])) < 0) {
        negative++;
      } else {
        positive++;
      }
    }
    return positive == 0 || negative == 0;
  }

  /**
   * The average of two quadrilaterals believed to describe the same symbol.
   *
   * <p>The two need not label their corners the same way, so the other one is
   * rotated until its first corner is the one nearest this one's before the
   * corners are averaged pairwise.</p>
   */
  public Quadrilateral blend(Quadrilateral other) {
    int offset = 0;
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < 4; i++) {
      double d = other.corners[i].distance(corners[0]);
      if (d < best) { // strictly less, so ties keep the first, as upstream does
        best = d;
        offset = i;
      }
    }

    PointF[] blended = new PointF[4];
    for (int i = 0; i < 4; i++) {
      blended[i] = corners[i].plus(other.corners[(i + offset) % 4]).dividedBy(2);
    }
    return new Quadrilateral(blended[0], blended[1], blended[2], blended[3]);
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      if (i > 0) {
        out.append(' ');
      }
      out.append(corners[i]);
    }
    return out.toString();
  }
}
