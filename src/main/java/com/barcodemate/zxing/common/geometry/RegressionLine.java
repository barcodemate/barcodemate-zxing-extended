/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/RegressionLine.h (tag v3.1.1,
 * commit 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A line fitted to a set of points, as the detection code uses to follow the
 * edge of a symbol.
 *
 * <p>The fit is a total least squares one -- it minimises the perpendicular
 * distance to the line, not the vertical distance -- which matters because
 * these lines run in every direction, including exactly vertical, where an
 * ordinary y-on-x regression has no answer at all.</p>
 *
 * <p>The line is stored in normal form, {@code a*x + b*y = c} with
 * {@code (a,b)} a unit normal. An inward direction is set first and the normal
 * is flipped to agree with it, so "which side is inside the symbol" stays
 * meaningful; {@link #signedDistance} is positive on the inward side.</p>
 */
public final class RegressionLine {

  private final List<PointF> points = new ArrayList<>(16);
  private PointF directionInward = PointF.ZERO;
  private double a = Double.NaN;
  private double b = Double.NaN;
  private double c = Double.NaN;

  public RegressionLine() {
  }

  /** The line through two points. Leaves the inward direction unset. */
  public RegressionLine(PointF p, PointF q) {
    List<PointF> two = new ArrayList<>(2);
    two.add(p);
    two.add(q);
    evaluate(two);
  }

  /**
   * The line fitted to a slice of a point list, without keeping the points.
   * Mirrors upstream's iterator-range constructor.
   */
  public static RegressionLine through(List<PointF> pts, int from, int to) {
    RegressionLine line = new RegressionLine();
    line.evaluate(pts.subList(from, to));
    return line;
  }

  public List<PointF> points() {
    return points;
  }

  public boolean isValid() {
    return !Double.isNaN(a);
  }

  /**
   * The unit normal, or the inward direction while no fit has been made, so
   * that a line with a single point still answers sensibly.
   */
  public PointF normal() {
    return isValid() ? new PointF(a, b) : directionInward;
  }

  public double signedDistance(PointF p) {
    return normal().dot(p) - c;
  }

  public double distance(PointF p) {
    return Math.abs(signedDistance(p));
  }

  public PointF project(PointF p) {
    return p.minus(normal().times(signedDistance(p)));
  }

  public PointF centroid() {
    PointF sum = PointF.ZERO;
    for (PointF p : points) {
      sum = sum.plus(p);
    }
    return sum.dividedBy(points.size());
  }

  /** Distance between the first and last point, truncated, or 0 below two points. */
  public int length() {
    return points.size() >= 2 ? (int) points.get(0).distance(points.get(points.size() - 1)) : 0;
  }

  public void reset() {
    points.clear();
    directionInward = PointF.ZERO;
    a = b = c = Double.NaN;
  }

  public void add(PointF p) {
    if (directionInward.isZero()) {
      throw new IllegalStateException("setDirectionInward must be called before add");
    }
    points.add(p);
    if (points.size() == 1) {
      c = normal().dot(p);
    }
  }

  public void popBack() {
    points.remove(points.size() - 1);
  }

  public void popFront() {
    points.remove(0);
  }

  public void setDirectionInward(PointF d) {
    directionInward = d.normalized();
  }

  public boolean evaluate() {
    return evaluate(-1, false);
  }

  /**
   * Fits the line, optionally discarding outliers and refitting until it
   * settles.
   *
   * @param maxSignedDist when positive, points more than this far inward, or
   *        more than twice this far outward, are dropped and the line refitted
   * @param updatePoints whether to keep the surviving points
   * @return whether the fitted normal still agrees with the inward direction to
   *         within 60 degrees; false also when too many points had to be dropped
   */
  public boolean evaluate(double maxSignedDist, boolean updatePoints) {
    boolean result = evaluate(points);
    if (maxSignedDist > 0) {
      List<PointF> kept = new ArrayList<>(points);
      while (true) {
        int before = kept.size();
        for (Iterator<PointF> it = kept.iterator(); it.hasNext(); ) {
          double sd = signedDistance(it.next());
          if (sd > maxSignedDist || sd < -2 * maxSignedDist) {
            it.remove();
          }
        }
        // Throwing away half the points means the line was wrong to begin with.
        if (kept.size() < before / 2 || kept.size() < 2) {
          return false;
        }
        if (before == kept.size()) {
          break;
        }
        result = evaluate(kept);
      }
      if (updatePoints) {
        points.clear();
        points.addAll(kept);
      }
    }
    return result;
  }

  /**
   * Whether the points span enough of both axes for extrapolation to be
   * trustworthy. A short line close to horizontal or vertical extrapolates
   * badly, because aliasing dominates it.
   */
  public boolean isHighRes() {
    PointF first = points.get(0);
    double minX = first.x;
    double maxX = first.x;
    double minY = first.y;
    double maxY = first.y;
    for (PointF p : points) {
      minX = Math.min(minX, p.x);
      maxX = Math.max(maxX, p.x);
      minY = Math.min(minY, p.y);
      maxY = Math.max(maxY, p.y);
    }
    PointF diff = new PointF(maxX - minX, maxY - minY);
    double len = diff.maxAbsComponent();
    double steps = Math.min(Math.abs(diff.x), Math.abs(diff.y));
    return steps > 2 || len > 50;
  }

  private boolean evaluate(List<PointF> pts) {
    PointF sum = PointF.ZERO;
    for (PointF p : pts) {
      sum = sum.plus(p);
    }
    PointF mean = sum.dividedBy(pts.size());

    double sumXX = 0;
    double sumYY = 0;
    double sumXY = 0;
    for (PointF p : pts) {
      PointF d = p.minus(mean);
      sumXX += d.x * d.x;
      sumYY += d.y * d.y;
      sumXY += d.x * d.y;
    }

    // Pick the formulation that stays conditioned for this point spread.
    if (sumYY >= sumXX) {
      double l = Math.sqrt(sumYY * sumYY + sumXY * sumXY);
      a = +sumYY / l;
      b = -sumXY / l;
    } else {
      double l = Math.sqrt(sumXX * sumXX + sumXY * sumXY);
      a = +sumXY / l;
      b = -sumXX / l;
    }

    if (directionInward.dot(normal()) < 0) {
      a = -a;
      b = -b;
    }
    c = normal().dot(mean);
    return directionInward.dot(normal()) > 0.5;
  }

  double a() {
    return a;
  }

  double b() {
    return b;
  }

  double c() {
    return c;
  }

  /** The intersection of two fitted lines. Both must be valid and not parallel. */
  public static PointF intersect(RegressionLine l1, RegressionLine l2) {
    double d = l1.a * l2.b - l1.b * l2.a;
    double x = (l1.c * l2.b - l1.b * l2.c) / d;
    double y = (l1.a * l2.c - l1.c * l2.a) / d;
    return new PointF(x, y);
  }
}
