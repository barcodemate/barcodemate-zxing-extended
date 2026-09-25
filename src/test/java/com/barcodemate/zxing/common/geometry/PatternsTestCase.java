/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.common.geometry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Numeric comparison against the reference implementation.
 *
 * <p>Expected values were produced by compiling zxing-cpp's own
 * {@code Pattern.h} (tag v3.1.1, commit 287c85d) and printing what its
 * {@code IsPattern} returns for these inputs, in both its plain and its
 * edge-to-edge form.</p>
 *
 * <p>Writing this oracle caught a real defect before it was written into the
 * port: upstream's {@code PatternView} constructed from a scan line skips the
 * first element, because a row begins with the white run in front of the first
 * bar. Matching against the raw row would have been silently off by one
 * element, and would have shown up much later as a detector that mostly does
 * not detect.</p>
 */
public final class PatternsTestCase {

  private static final double EPS = 1e-12;

  /** The 1:1:3:1:1 of a QR finder pattern. */
  private static final int[] FINDER = {1, 1, 3, 1, 1};
  private static final int FINDER_SUM = 7;

  /** Telepen's START 1. */
  private static final int[] START1 = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3};
  private static final int START1_SUM = 16;

  private static double dense(int[] view, int[] pattern, int sum, int spaceInPixel,
                              double minQuietZone, double moduleSizeRef) {
    return Patterns.isPattern(view, 0, pattern, sum, spaceInPixel, minQuietZone, moduleSizeRef);
  }

  private static double e2e(int[] view, int[] pattern, int spaceInPixel, double minQuietZone) {
    return Patterns.isPatternEdgeToEdge(view, 0, pattern, spaceInPixel, minQuietZone);
  }

  @Test
  public void testFinderPatternExact() {
    assertEquals(1.0, dense(new int[] {1, 1, 3, 1, 1}, FINDER, FINDER_SUM, 0, 0, 0), EPS);
    assertEquals(1.0, e2e(new int[] {1, 1, 3, 1, 1}, FINDER, 0, 0), EPS);
  }

  @Test
  public void testFinderPatternScaled() {
    assertEquals(4.0, dense(new int[] {4, 4, 12, 4, 4}, FINDER, FINDER_SUM, 0, 0, 0), EPS);
    assertEquals(4.0, e2e(new int[] {4, 4, 12, 4, 4}, FINDER, 0, 0), EPS);
  }

  @Test
  public void testFinderPatternScaledWithNoise() {
    assertEquals(4.0, dense(new int[] {4, 5, 11, 4, 4}, FINDER, FINDER_SUM, 0, 0, 0), EPS);
    assertEquals(4.1500000000000004, e2e(new int[] {4, 5, 11, 4, 4}, FINDER, 0, 0), EPS);
  }

  @Test
  public void testWrongRatioRejectedByPlainButNotEdgeToEdge() {
    // 1:1:1:1:1 is not a finder pattern, and the plain measure says so. The
    // edge-to-edge one does not, because it is free to believe bars and
    // spaces have different module sizes; it is a looser test by design, and
    // its callers apply it only where something else has already narrowed
    // things down.
    assertEquals(0.0, dense(new int[] {1, 1, 1, 1, 1}, FINDER, FINDER_SUM, 0, 0, 0), EPS);
    assertEquals(0.80000000000000004, e2e(new int[] {1, 1, 1, 1, 1}, FINDER, 0, 0), EPS);
  }

  @Test
  public void testOversizedMiddleBar() {
    assertEquals(0.0, dense(new int[] {4, 4, 20, 4, 4}, FINDER, FINDER_SUM, 0, 0, 0), EPS);
    assertEquals(4.7999999999999998, e2e(new int[] {4, 4, 20, 4, 4}, FINDER, 0, 0), EPS);
  }

  @Test
  public void testQuietZone() {
    // 5 pixels of white in front, needing 1 module of 4 pixels: passes.
    assertEquals(4.0, dense(new int[] {4, 4, 12, 4, 4}, FINDER, FINDER_SUM, 5, 1.0, 0), EPS);
    // No white in front, needing 3 modules: fails.
    assertEquals(0.0, dense(new int[] {4, 4, 12, 4, 4}, FINDER, FINDER_SUM, 0, 3.0, 0), EPS);
  }

  @Test
  public void testExternalModuleSizeReference() {
    // Told the module size it already believes, the match still succeeds.
    assertEquals(4.0, dense(new int[] {4, 4, 12, 4, 4}, FINDER, FINDER_SUM, 0, 0, 4.0), EPS);
    // Told a module size that does not fit, it rejects: this is how a
    // detector refuses a candidate that disagrees with the rest of a symbol.
    assertEquals(0.0, dense(new int[] {4, 4, 12, 4, 4}, FINDER, FINDER_SUM, 0, 0, 6.0), EPS);
  }

  @Test
  public void testLongerPattern() {
    assertEquals(1.0, dense(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3},
        START1, START1_SUM, 0, 0, 0), EPS);
    assertEquals(3.0, dense(new int[] {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 9, 9},
        START1, START1_SUM, 0, 0, 0), EPS);
    assertEquals(3.0, dense(new int[] {3, 4, 3, 2, 3, 3, 4, 3, 3, 3, 8, 9},
        START1, START1_SUM, 0, 0, 0), EPS);
    assertEquals(3.0, e2e(new int[] {3, 4, 3, 2, 3, 3, 4, 3, 3, 3, 8, 9}, START1, 0, 0), EPS);
  }
}
