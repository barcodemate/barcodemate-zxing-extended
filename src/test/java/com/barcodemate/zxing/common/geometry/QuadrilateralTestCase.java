/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Numeric comparison against the reference implementation: every expected value
 * was printed by zxing-cpp's own {@code Quadrilateral.h} (tag v3.1.1, commit
 * 287c85d), compiled directly.
 */
public final class QuadrilateralTestCase {

  private static final double EPS = 1e-12;

  private static final Quadrilateral UNIT = new Quadrilateral(
      new PointF(0, 0), new PointF(10, 0), new PointF(10, 10), new PointF(0, 10));
  private static final Quadrilateral ROT45 = new Quadrilateral(
      new PointF(5, 0), new PointF(10, 5), new PointF(5, 10), new PointF(0, 5));
  private static final Quadrilateral SKEW = new Quadrilateral(
      new PointF(0, 0), new PointF(10, 2), new PointF(11, 10), new PointF(1, 9));

  private static void assertCorners(Quadrilateral q, double... xy) {
    for (int i = 0; i < 4; i++) {
      assertEquals("corner " + i + " x", xy[2 * i], q.get(i).x, EPS);
      assertEquals("corner " + i + " y", xy[2 * i + 1], q.get(i).y, EPS);
    }
  }

  @Test
  public void testOrientation() {
    assertEquals(0.0, UNIT.orientation(), EPS);
    assertEquals(0.78539816339744828, ROT45.orientation(), EPS);
    assertEquals(0.14888994760949725, SKEW.orientation(), EPS);
  }

  @Test
  public void testCenter() {
    assertEquals(5.0, UNIT.center().x, EPS);
    assertEquals(5.0, UNIT.center().y, EPS);
    assertEquals(5.5, SKEW.center().x, EPS);
    assertEquals(5.25, SKEW.center().y, EPS);
  }

  @Test
  public void testRotatedCorners() {
    assertCorners(UNIT.rotatedCorners(-1), 0, 10, 0, 0, 10, 0, 10, 10);
    assertCorners(UNIT.rotatedCorners(0), 0, 0, 10, 0, 10, 10, 0, 10);
    assertCorners(UNIT.rotatedCorners(1), 10, 0, 10, 10, 0, 10, 0, 0);
    assertCorners(UNIT.rotatedCorners(2), 10, 10, 0, 10, 0, 0, 10, 0);
    assertCorners(UNIT.rotatedCorners(1, true), 10, 0, 0, 0, 0, 10, 10, 10);
  }

  @Test
  public void testRectangleAndSquare() {
    assertCorners(Quadrilateral.rectangle(7, 7, 0.5), 0.5, 0.5, 6.5, 0.5, 6.5, 6.5, 0.5, 6.5);
    assertCorners(Quadrilateral.rectangle(21, 21, 3.5), 3.5, 3.5, 17.5, 3.5, 17.5, 17.5, 3.5, 17.5);
    assertCorners(Quadrilateral.centeredSquare(10), -5, -5, 5, -5, 5, 5, -5, 5);
  }

  @Test
  public void testScaleMoveBoundingBox() {
    assertCorners(UNIT.scaled(3), 0, 0, 30, 0, 30, 30, 0, 30);
    assertCorners(UNIT.moved(new PointF(2, -3)), 2, -3, 12, -3, 12, 7, 2, 7);
    assertCorners(SKEW.boundingBox(), 0, 0, 11, 0, 11, 10, 0, 10);
  }

  @Test
  public void testIsConvex() {
    assertTrue(UNIT.isConvex());
    assertTrue(ROT45.isConvex());
    assertTrue(SKEW.isConvex());

    Quadrilateral concave = new Quadrilateral(
        new PointF(0, 0), new PointF(10, 0), new PointF(1, 1), new PointF(0, 10));
    assertFalse(concave.isConvex());

    // Convex, but so lopsided that a perspective transform built on it becomes
    // numerically unstable. Rejected on the cross-product ratio, not on sign.
    Quadrilateral sliver = new Quadrilateral(
        new PointF(0, 0), new PointF(10, 0), new PointF(10, 0.1), new PointF(0, 10));
    assertFalse(sliver.isConvex());
  }

  @Test
  public void testIsInside() {
    assertTrue(UNIT.isInside(new PointF(5, 5)));
    assertFalse(UNIT.isInside(new PointF(11, 5)));
    assertTrue(ROT45.isInside(new PointF(5, 1)));
    assertFalse(ROT45.isInside(new PointF(0, 0)));
  }

  @Test
  public void testBlendRealignsCorners() {
    Quadrilateral outer = new Quadrilateral(
        new PointF(0, 0), new PointF(10, 0), new PointF(10, 10), new PointF(0, 10));
    Quadrilateral inner = new Quadrilateral(
        new PointF(1, 1), new PointF(9, 1), new PointF(9, 9), new PointF(1, 9));
    assertCorners(outer.blend(inner), 0.5, 0.5, 9.5, 0.5, 9.5, 9.5, 0.5, 9.5);

    // The same inner square with its corners labelled starting elsewhere must
    // blend to the same thing; this is why blend realigns before averaging.
    Quadrilateral relabelled = new Quadrilateral(
        new PointF(9, 1), new PointF(9, 9), new PointF(1, 9), new PointF(1, 1));
    assertCorners(outer.blend(relabelled), 0.5, 0.5, 9.5, 0.5, 9.5, 9.5, 0.5, 9.5);
  }
}
