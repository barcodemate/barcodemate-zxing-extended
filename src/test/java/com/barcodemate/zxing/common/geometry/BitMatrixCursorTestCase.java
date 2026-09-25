/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Numeric comparison against the reference implementation.
 *
 * <p>Every expected value was produced by compiling zxing-cpp's own
 * {@code BitMatrixCursor.h} (tag v3.1.1, commit 287c85d) against the same test
 * image and printing its results. They are not this port's output blessed
 * afterwards.</p>
 *
 * <p>The image is a standard 7x7 QR finder pattern at (2,2)-(8,8) in a 21x21
 * field, so a scan through its centre gives the 1:1:3:1:1 ratio the detector
 * looks for.</p>
 */
public final class BitMatrixCursorTestCase {

  private static BitMatrix finderPatternImage() {
    BitMatrix image = new BitMatrix(21, 21);
    for (int y = 2; y <= 8; y++) {
      for (int x = 2; x <= 8; x++) {
        image.set(x, y);
      }
    }
    for (int y = 3; y <= 7; y++) {
      for (int x = 3; x <= 7; x++) {
        image.unset(x, y);
      }
    }
    for (int y = 4; y <= 6; y++) {
      for (int x = 4; x <= 6; x++) {
        image.set(x, y);
      }
    }
    return image;
  }

  private static BitMatrixCursorI cursor(BitMatrix image, int x, int y, int dx, int dy) {
    return new BitMatrixCursorI(image, new PointI(x, y), new PointI(dx, dy));
  }

  @Test
  public void testReadsFinderPatternRatio() {
    BitMatrixCursorI c = cursor(finderPatternImage(), 2, 5, 1, 0);
    int[] pattern = c.readPattern(new int[5]);

    assertArrayEquals(new int[] {1, 1, 3, 1, 1}, pattern);
    assertEquals(9.0, c.p.x, 0);
    assertEquals(5.0, c.p.y, 0);
  }

  @Test
  public void testStepToEdge() {
    BitMatrix image = finderPatternImage();

    BitMatrixCursorI c = cursor(image, 0, 5, 1, 0);
    assertEquals(2, c.stepToEdge());
    assertEquals(2.0, c.p.x, 0);
    assertEquals(2, c.stepToEdge(2, 0));
    assertEquals(4.0, c.p.x, 0);

    // Range exhausted before an edge: returns 0, but the cursor still moved.
    BitMatrixCursorI limited = cursor(image, 0, 5, 1, 0);
    assertEquals(0, limited.stepToEdge(1, 1));
    assertEquals(1.0, limited.p.x, 0);

    // backup stops one step short of the edge.
    BitMatrixCursorI backed = cursor(image, 0, 5, 1, 0);
    assertEquals(1, backed.stepToEdge(1, 0, true));
    assertEquals(1.0, backed.p.x, 0);
  }

  @Test
  public void testLeavingTheImageCountsAsAnEdge() {
    // Row 0 is entirely white, so the only "edge" is the image border. The
    // cursor ends up outside, which several callers rely on.
    BitMatrixCursorI c = cursor(finderPatternImage(), 0, 0, 1, 0);
    assertEquals(21, c.stepToEdge());
    assertEquals(21.0, c.p.x, 0);
    assertEquals(0.0, c.p.y, 0);
    assertFalse(c.isIn());
  }

  @Test
  public void testCountEdges() {
    BitMatrixCursorI c = cursor(finderPatternImage(), 0, 5, 1, 0);
    assertEquals(6, c.countEdges(20));
    assertEquals(20.0, c.p.x, 0);
  }

  @Test
  public void testEdgeAt() {
    BitMatrix image = finderPatternImage();

    BitMatrixCursorI onEdge = cursor(image, 2, 5, 1, 0);
    assertEquals(BitMatrixCursor.BLACK, onEdge.edgeAtFront());
    assertEquals(BitMatrixCursor.BLACK, onEdge.edgeAtBack());
    assertEquals(BitMatrixCursor.INVALID, onEdge.edgeAtLeft());
    assertEquals(BitMatrixCursor.INVALID, onEdge.edgeAtRight());

    BitMatrixCursorI inCentre = cursor(image, 5, 5, 1, 0);
    assertEquals(BitMatrixCursor.INVALID, inCentre.edgeAtFront());
    assertEquals(BitMatrixCursor.INVALID, inCentre.edgeAtBack());
  }

  @Test
  public void testStepAlongEdge() {
    BitMatrixCursorI c = cursor(finderPatternImage(), 2, 2, 0, 1);
    for (int i = 0; i < 6; i++) {
      assertTrue("step " + i, c.stepAlongEdge(BitMatrixCursor.Direction.LEFT));
      assertEquals("step " + i, 3.0 + i, c.p.x, 0);
      assertEquals("step " + i, 2.0, c.p.y, 0);
      assertEquals("step " + i, 1.0, c.d.x, 0);
      assertEquals("step " + i, 0.0, c.d.y, 0);
    }
  }

  @Test
  public void testFloatCursorWalksBresenham() {
    BitMatrix image = finderPatternImage();

    BitMatrixCursorF c = new BitMatrixCursorF(image, new PointF(2.5, 5.5), new PointF(2, 1));
    assertEquals(1.0, c.d.x, 0);
    assertEquals(0.5, c.d.y, 0);

    c.step();
    assertEquals(3.5, c.p.x, 0);
    assertEquals(6.0, c.p.y, 0);
    assertFalse(c.isBlack());

    c.step(2);
    assertEquals(5.5, c.p.x, 0);
    assertEquals(7.0, c.p.y, 0);
    assertFalse(c.isBlack());

    BitMatrixCursorF straight = new BitMatrixCursorF(image, new PointF(2.5, 5.5), new PointF(1, 0));
    assertArrayEquals(new int[] {1, 1, 3, 1, 1}, straight.readPattern(new int[5]));
    assertEquals(9.5, straight.p.x, 0);
    assertEquals(5.5, straight.p.y, 0);
  }

  @Test
  public void testTurnsAndMovedBy() {
    BitMatrixCursorI c = cursor(finderPatternImage(), 5, 5, 1, 0);

    c.turnRight();
    assertEquals(0.0, c.d.x, 0);
    assertEquals(1.0, c.d.y, 0);

    c.turnBack();
    assertEquals(0.0, c.d.x, 0);
    assertEquals(-1.0, c.d.y, 0);

    BitMatrixCursor moved = c.movedBy(new PointF(1, 1));
    assertEquals(6.0, moved.p.x, 0);
    assertEquals(6.0, moved.p.y, 0);
    assertEquals(0.0, moved.d.x, 0);
    assertEquals(-1.0, moved.d.y, 0);
  }
}
