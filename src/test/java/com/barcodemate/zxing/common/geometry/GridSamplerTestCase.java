/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DetectorResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Numeric comparison against the reference implementation: expected values were
 * printed by zxing-cpp's own {@code PerspectiveTransform.cpp} and
 * {@code GridSampler.cpp} (tag v3.1.1, commit 287c85d), compiled directly.
 */
public final class GridSamplerTestCase {

  private static final double EPS = 1e-9;

  private static final Quadrilateral SRC = Quadrilateral.rectangle(7, 7, 0.5);

  private static void assertMaps(PerspectiveTransform t, double x, double y, double toX, double toY) {
    PointF p = t.apply(new PointF(x, y));
    assertEquals("x", toX, p.x, EPS);
    assertEquals("y", toY, p.y, EPS);
  }

  @Test
  public void testAffineBranch() {
    Quadrilateral dst = new Quadrilateral(
        new PointF(10, 10), new PointF(38, 10), new PointF(38, 38), new PointF(10, 38));
    PerspectiveTransform t = PerspectiveTransform.of(SRC, dst);
    assertTrue(t.isValid());
    assertMaps(t, 0.5, 0.5, 10, 10);
    assertMaps(t, 3.5, 3.5, 24, 24);
    assertMaps(t, 6.5, 6.5, 38, 38);
    assertMaps(t, 2, 5, 17, 31);
  }

  @Test
  public void testAffineBranchTakesParallelograms() {
    // Opposite sides parallel means no vanishing point, so the affine path
    // applies even though the shape is not a rectangle.
    Quadrilateral dst = new Quadrilateral(
        new PointF(10, 10), new PointF(38, 14), new PointF(42, 42), new PointF(14, 38));
    PerspectiveTransform t = PerspectiveTransform.of(SRC, dst);
    assertTrue(t.isValid());
    assertMaps(t, 0.5, 0.5, 10, 10);
    assertMaps(t, 3.5, 3.5, 26, 26);
    assertMaps(t, 6.5, 6.5, 42, 42);
  }

  @Test
  public void testPerspectiveBranch() {
    Quadrilateral dst = new Quadrilateral(
        new PointF(10, 10), new PointF(40, 16), new PointF(36, 44), new PointF(12, 38));
    PerspectiveTransform t = PerspectiveTransform.of(SRC, dst);
    assertTrue(t.isValid());
    assertMaps(t, 0.5, 0.5, 10, 10);
    assertMaps(t, 3.5, 3.5, 24.125984251968507, 28.472440944881896);
    assertMaps(t, 6.5, 6.5, 36, 44);
    assertMaps(t, 1, 6, 13.818181818181818, 36.571428571428569);
  }

  @Test
  public void testDegenerateQuadrilateralsAreRefused() {
    Quadrilateral concave = new Quadrilateral(
        new PointF(0, 0), new PointF(10, 0), new PointF(1, 1), new PointF(0, 10));
    Quadrilateral fine = new Quadrilateral(
        new PointF(10, 10), new PointF(38, 10), new PointF(38, 38), new PointF(10, 38));

    // Either side being degenerate is enough; this is the check ZXing's own
    // PerspectiveTransform does not make, because its detector never feeds it
    // anything degenerate.
    assertFalse(PerspectiveTransform.of(SRC, concave).isValid());
    assertFalse(PerspectiveTransform.of(concave, fine).isValid());
  }

  @Test
  public void testSampleGridReadsTheFinderPattern() {
    BitMatrix image = new BitMatrix(60, 60);
    drawFinder(image, 10, 10, 4);

    PerspectiveTransform mod2Pix = PerspectiveTransform.of(SRC, new Quadrilateral(
        new PointF(12, 12), new PointF(36, 12), new PointF(36, 36), new PointF(12, 36)));
    DetectorResult result = GridSampler.sampleGrid(image, 7, 7, mod2Pix);

    assertNotNull(result);
    String[] expected = {
        "XXXXXXX",
        "X.....X",
        "X.XXX.X",
        "X.XXX.X",
        "X.XXX.X",
        "X.....X",
        "XXXXXXX",
    };
    BitMatrix bits = result.getBits();
    for (int y = 0; y < 7; y++) {
      StringBuilder row = new StringBuilder();
      for (int x = 0; x < 7; x++) {
        row.append(bits.get(x, y) ? 'X' : '.');
      }
      assertEquals("row " + y, expected[y], row.toString());
    }

    ResultPoint[] corners = result.getPoints();
    assertCorner(corners[0], 10, 10);
    assertCorner(corners[1], 38, 10);
    assertCorner(corners[2], 38, 38);
    assertCorner(corners[3], 10, 38);
  }

  @Test
  public void testSampleGridRefusesAGridOutsideTheImage() {
    BitMatrix image = new BitMatrix(60, 60);
    drawFinder(image, 10, 10, 4);

    PerspectiveTransform mod2Pix = PerspectiveTransform.of(SRC, new Quadrilateral(
        new PointF(50, 50), new PointF(80, 50), new PointF(80, 80), new PointF(50, 80)));
    assertNull(GridSampler.sampleGrid(image, 7, 7, mod2Pix));
  }

  private static void assertCorner(ResultPoint p, double x, double y) {
    assertEquals("x", x, p.getX(), EPS);
    assertEquals("y", y, p.getY(), EPS);
  }

  private static void drawFinder(BitMatrix image, int ox, int oy, int module) {
    fill(image, ox, oy, module, 0, 0, 6, 6, true);
    fill(image, ox, oy, module, 1, 1, 5, 5, false);
    fill(image, ox, oy, module, 2, 2, 4, 4, true);
  }

  private static void fill(BitMatrix image, int ox, int oy, int module,
                           int mx0, int my0, int mx1, int my1, boolean value) {
    for (int y = my0 * module; y < (my1 + 1) * module; y++) {
      for (int x = mx0 * module; x < (mx1 + 1) * module; x++) {
        if (value) {
          image.set(ox + x, oy + y);
        } else {
          image.unset(ox + x, oy + y);
        }
      }
    }
  }
}
