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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Numeric comparison against the reference implementation.
 *
 * <p>Expected values were printed by zxing-cpp's own {@code ConcentricFinder}
 * (tag v3.1.1, commit 287c85d), compiled directly against the same image: a
 * 7x7 QR finder pattern at module size 4, drawn at (10,10) in a 60x60 field, so
 * its true centre is (24,24) and its true width 28 pixels.</p>
 */
public final class ConcentricFinderTestCase {

  private static final double EPS = 1e-9;

  /** The 1:1:3:1:1 of a QR finder pattern. */
  private static final int[] FINDER = {1, 1, 3, 1, 1};

  private static final int MODULE = 4;
  private static final int ORIGIN = 10;
  private static final int WIDTH = 7 * MODULE;
  private static final PointI TRUE_CENTER = new PointI(ORIGIN + 7 * MODULE / 2, ORIGIN + 7 * MODULE / 2);

  private static BitMatrix finderImage() {
    BitMatrix image = new BitMatrix(60, 60);
    fill(image, 0, 0, 6, 6, true);
    fill(image, 1, 1, 5, 5, false);
    fill(image, 2, 2, 4, 4, true);
    return image;
  }

  private static void fill(BitMatrix image, int mx0, int my0, int mx1, int my1, boolean value) {
    for (int y = my0 * MODULE; y < (my1 + 1) * MODULE; y++) {
      for (int x = mx0 * MODULE; x < (mx1 + 1) * MODULE; x++) {
        if (value) {
          image.set(ORIGIN + x, ORIGIN + y);
        } else {
          image.unset(ORIGIN + x, ORIGIN + y);
        }
      }
    }
  }

  private static void assertPattern(ConcentricPattern actual, double x, double y, double size) {
    assertNotNull(actual);
    assertEquals("x", x, actual.center.x, EPS);
    assertEquals("y", y, actual.center.y, EPS);
    assertEquals("size", size, actual.size, EPS);
  }

  private static void assertCorners(Quadrilateral q, double... xy) {
    assertNotNull(q);
    for (int i = 0; i < 4; i++) {
      assertEquals("corner " + i + " x", xy[2 * i], q.get(i).x, EPS);
      assertEquals("corner " + i + " y", xy[2 * i + 1], q.get(i).y, EPS);
    }
  }

  @Test
  public void testCenterOfRing() {
    BitMatrix image = finderImage();
    assertPattern(ConcentricFinder.centerOfRing(image, TRUE_CENTER, WIDTH, 1),
        24, 24, 12.156816113441076);
    assertPattern(ConcentricFinder.centerOfRing(image, TRUE_CENTER, WIDTH, 2),
        24, 24, 20.128211556307743);
    assertPattern(ConcentricFinder.centerOfRing(image, TRUE_CENTER, WIDTH, 1, false),
        24, 24, 12.156816113441076);
    // Started off centre, the ring walk still converges on the true centre.
    assertPattern(ConcentricFinder.centerOfRing(image, new PointI(26, 23), WIDTH, 1),
        24, 24, 12.512853881798677);
  }

  @Test
  public void testCenterOfRings() {
    BitMatrix image = finderImage();
    assertPattern(ConcentricFinder.centerOfRings(image, TRUE_CENTER.toPointF(), WIDTH, 2),
        24, 24, 20.128211556307743);
    assertPattern(ConcentricFinder.centerOfRings(image, TRUE_CENTER.toPointF(), WIDTH, 3),
        24, 24, 28.10912398304383);
  }

  @Test
  public void testFitSquareToPoints() {
    BitMatrix image = finderImage();
    assertCorners(ConcentricFinder.fitSquareToPoints(image, TRUE_CENTER.toPointF(), WIDTH, 1, false),
        30.5, 30.5, 17.5, 30.5, 17.5, 17.5, 30.5, 17.5);
    assertCorners(ConcentricFinder.fitSquareToPoints(image, TRUE_CENTER.toPointF(), WIDTH, 2, true),
        33.5, 33.5, 14.5, 33.5, 14.5, 14.5, 33.5, 14.5);
  }

  @Test
  public void testFindConcentricPatternCorners() {
    BitMatrix image = finderImage();
    // Averaging the inside and the outside of the ring lands exactly on the
    // module boundary, which is the point of doing it that way.
    assertCorners(ConcentricFinder.findConcentricPatternCorners(image, TRUE_CENTER.toPointF(), WIDTH, 1),
        32, 32, 16, 32, 16, 16, 32, 16);
    assertCorners(ConcentricFinder.findConcentricPatternCorners(image, TRUE_CENTER.toPointF(), WIDTH, 2),
        36, 36, 12, 36, 12, 12, 36, 12);
  }

  @Test
  public void testFinetuneCenter() {
    BitMatrix image = finderImage();
    assertPattern(ConcentricFinder.finetuneConcentricPatternCenter(
        image, TRUE_CENTER.toPointF(), WIDTH, 5), 24, 24, 28.179496178830838);
    assertPattern(ConcentricFinder.finetuneConcentricPatternCenter(
        image, new PointF(26, 23), WIDTH, 5), 24, 24, 28.179496178830838);
  }

  @Test
  public void testCheckSymmetricPattern() {
    BitMatrix image = finderImage();

    BitMatrixCursorI horizontal = new BitMatrixCursorI(image, TRUE_CENTER, new PointI(1, 0));
    assertEquals(28, ConcentricFinder.checkSymmetricPattern(horizontal, FINDER, WIDTH * 2, true, false));
    assertEquals(24.0, horizontal.p.x, 0);
    assertEquals(24.0, horizontal.p.y, 0);

    BitMatrixCursorI vertical = new BitMatrixCursorI(image, TRUE_CENTER, new PointI(0, 1));
    assertEquals(28, ConcentricFinder.checkSymmetricPattern(vertical, FINDER, WIDTH * 2, false, false));

    BitMatrixCursorI diagonal = new BitMatrixCursorI(image, TRUE_CENTER, new PointI(1, 1));
    assertEquals(28, ConcentricFinder.checkSymmetricPattern(diagonal, FINDER, WIDTH * 2, false, true));

    // Empty white area: nothing to match.
    BitMatrixCursorI empty = new BitMatrixCursorI(image, new PointI(5, 5), new PointI(1, 0));
    assertEquals(0, ConcentricFinder.checkSymmetricPattern(empty, FINDER, WIDTH * 2, false, false));
  }

  @Test
  public void testReadSymmetricPattern() {
    BitMatrix image = finderImage();
    BitMatrixCursorI cursor = new BitMatrixCursorI(image, TRUE_CENTER, new PointI(1, 0));
    int[] result = new int[5];
    assertTrue(ConcentricFinder.readSymmetricPattern(cursor, result, WIDTH * 2));
    assertArrayEquals(new int[] {4, 4, 12, 4, 4}, result);
  }

  @Test
  public void testCenterFromEnd() {
    assertEquals(24.0, ConcentricFinder.centerFromEnd(new int[] {4, 4, 12, 4, 4}, 38.0), EPS);
    assertEquals(20.0, ConcentricFinder.centerFromEnd(new int[] {4, 12, 4}, 30.0), EPS);
  }

  @Test
  public void testLocateConcentricPattern() {
    BitMatrix image = finderImage();

    assertPattern(ConcentricFinder.locateConcentricPattern(
        image, FINDER, TRUE_CENTER.toPointF(), WIDTH, false), 24, 24, 28.179496178830838);
    // A candidate three pixels off still resolves to the true centre.
    assertPattern(ConcentricFinder.locateConcentricPattern(
        image, FINDER, new PointF(27, 22), WIDTH, false), 24, 24, 28.179496178830838);
    // Blank area: rejected rather than invented.
    assertNull(ConcentricFinder.locateConcentricPattern(
        image, FINDER, new PointF(5, 5), WIDTH, false));
  }
}
