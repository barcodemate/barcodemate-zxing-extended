/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Numeric comparison against the reference implementation.
 *
 * <p>Every expected value here was produced by compiling zxing-cpp's own
 * {@code RegressionLine.h} (tag v3.1.1, commit 287c85d) against a small harness
 * and printing its results to 17 significant digits. They are not values this
 * port produced and were then blessed. That distinction is the whole point:
 * this layer decodes nothing by itself, so an end-to-end test cannot tell
 * whether it is right, and a bug here would only surface later as six
 * symbologies that mostly work.</p>
 *
 * <p>The tolerance is 1e-12 rather than exact equality because the two
 * compilers are free to contract or reorder floating point differently; the
 * arithmetic is double precision on both sides, as upstream's PointF is
 * {@code PointT<double>}.</p>
 */
public final class RegressionLineTestCase {

  private static final double EPS = 1e-12;

  private static RegressionLine build(List<PointF> points, PointF inward,
                                      double maxSignedDist, boolean updatePoints,
                                      boolean[] result) {
    RegressionLine line = new RegressionLine();
    line.setDirectionInward(inward);
    for (PointF p : points) {
      line.add(p);
    }
    result[0] = line.evaluate(maxSignedDist, updatePoints);
    return line;
  }

  @Test
  public void testVerticalLine() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(10, 0), new PointF(10, 5), new PointF(10, 10), new PointF(10, 15)),
        new PointF(1, 0), -1, false, ret);

    assertTrue(ret[0]);
    assertTrue(line.isValid());
    assertEquals(1.0, line.normal().x, EPS);
    assertEquals(0.0, line.normal().y, EPS);
    assertEquals(15, line.length());
    assertFalse(line.isHighRes());
    assertEquals(10.0, line.centroid().x, EPS);
    assertEquals(7.5, line.centroid().y, EPS);

    assertEquals(2.0, line.signedDistance(new PointF(12, 7)), EPS);
    assertEquals(10.0, line.project(new PointF(12, 7)).x, EPS);
    assertEquals(7.0, line.project(new PointF(12, 7)).y, EPS);
    assertEquals(-5.0, line.signedDistance(new PointF(5, 3)), EPS);
    assertEquals(10.0, line.project(new PointF(5, 3)).x, EPS);
    assertEquals(3.0, line.project(new PointF(5, 3)).y, EPS);
  }

  @Test
  public void testHorizontalLine() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(0, 20), new PointF(6, 20), new PointF(12, 20), new PointF(18, 20)),
        new PointF(0, 1), -1, false, ret);

    assertTrue(ret[0]);
    assertEquals(0.0, line.normal().x, EPS);
    assertEquals(1.0, line.normal().y, EPS);
    assertEquals(18, line.length());
    assertFalse(line.isHighRes());
    assertEquals(9.0, line.centroid().x, EPS);
    assertEquals(20.0, line.centroid().y, EPS);

    assertEquals(5.0, line.signedDistance(new PointF(3, 25)), EPS);
    assertEquals(3.0, line.project(new PointF(3, 25)).x, EPS);
    assertEquals(20.0, line.project(new PointF(3, 25)).y, EPS);
    assertEquals(-6.0, line.signedDistance(new PointF(9, 14)), EPS);
  }

  @Test
  public void testDiagonalLine() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(0, 0), new PointF(3, 3), new PointF(6, 6),
            new PointF(9, 9), new PointF(12, 12)),
        new PointF(1, -1), -1, false, ret);

    assertTrue(ret[0]);
    assertEquals(0.70710678118654746, line.normal().x, EPS);
    assertEquals(-0.70710678118654746, line.normal().y, EPS);
    assertEquals(16, line.length());
    assertTrue(line.isHighRes());
    assertEquals(6.0, line.centroid().x, EPS);
    assertEquals(6.0, line.centroid().y, EPS);

    assertEquals(4.2426406871192848, line.signedDistance(new PointF(6, 0)), EPS);
    assertEquals(3.0000000000000004, line.project(new PointF(6, 0)).x, EPS);
    assertEquals(2.9999999999999996, line.project(new PointF(6, 0)).y, EPS);
    assertEquals(-4.2426406871192848, line.signedDistance(new PointF(0, 6)), EPS);
  }

  @Test
  public void testNoisyPoints() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(0, 0.4), new PointF(5, -0.3), new PointF(10, 0.5),
            new PointF(15, -0.2), new PointF(20, 0.6)),
        new PointF(0, 1), -1, false, ret);

    assertTrue(ret[0]);
    assertEquals(-0.0099995000374968734, line.normal().x, EPS);
    assertEquals(0.99995000374968745, line.normal().y, EPS);
    assertEquals(20, line.length());
    assertFalse(line.isHighRes());
    assertEquals(10.0, line.centroid().x, EPS);
    assertEquals(0.20000000000000001, line.centroid().y, EPS);

    assertEquals(1.8299085068619281, line.signedDistance(new PointF(7, 2)), EPS);
    assertEquals(7.0182981701829821, line.project(new PointF(7, 2)).x, EPS);
    assertEquals(0.1701829817018301, line.project(new PointF(7, 2)).y, EPS);
    assertEquals(-3.2298385121114905, line.signedDistance(new PointF(13, -3)), EPS);
    assertEquals(12.967703229677033, line.project(new PointF(13, -3)).x, EPS);
    assertEquals(0.22967703229676983, line.project(new PointF(13, -3)).y, EPS);
  }

  @Test
  public void testOutlierIsDiscarded() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(0, 0), new PointF(5, 0), new PointF(10, 0),
            new PointF(15, 9), new PointF(20, 0), new PointF(25, 0)),
        new PointF(0, 1), 1.5, true, ret);

    assertTrue(ret[0]);
    assertEquals("the outlier is dropped and the line refitted", 5, line.points().size());
    assertEquals(0.0, line.normal().x, EPS);
    assertEquals(1.0, line.normal().y, EPS);
    assertEquals(25, line.length());
    assertEquals(12.0, line.centroid().x, EPS);
    assertEquals(0.0, line.centroid().y, EPS);
    assertEquals(1.0, line.signedDistance(new PointF(12, 1)), EPS);
  }

  @Test
  public void testTwoPointConstructionAndIntersection() {
    RegressionLine horizontal = new RegressionLine(new PointF(0, 0), new PointF(10, 0));
    RegressionLine vertical = new RegressionLine(new PointF(4, -5), new PointF(4, 5));

    assertEquals(0.0, horizontal.normal().x, EPS);
    assertEquals(-1.0, horizontal.normal().y, EPS);
    assertEquals(1.0, vertical.normal().x, EPS);
    assertEquals(0.0, vertical.normal().y, EPS);

    PointF crossing = RegressionLine.intersect(horizontal, vertical);
    assertEquals(4.0, crossing.x, EPS);
    assertEquals(0.0, crossing.y, EPS);
  }

  @Test
  public void testShortFlatLineIsNotHighRes() {
    boolean[] ret = new boolean[1];
    RegressionLine line = build(
        Arrays.asList(new PointF(0, 0), new PointF(1, 0), new PointF(2, 0), new PointF(3, 0)),
        new PointF(0, 1), -1, false, ret);

    assertFalse("too short and too close to horizontal to extrapolate", line.isHighRes());
    assertEquals(3, line.length());
  }
}
