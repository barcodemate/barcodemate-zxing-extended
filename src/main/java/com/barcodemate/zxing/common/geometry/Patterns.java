/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/Pattern.h (tag v3.1.1, commit
 * 287c85d).
 */

package com.barcodemate.zxing.common.geometry;

/**
 * Proportional matching of a run of bars and spaces against a fixed pattern,
 * such as the 1:1:3:1:1 of a QR finder pattern.
 *
 * <p>The comparison is scale free: the module size is derived from the run
 * itself and the pattern is then scaled to it, so the same code matches a
 * symbol printed at any size. What it returns is that module size, or 0 for no
 * match, so a caller gets the scale for free when the match succeeds.</p>
 *
 * <p>Two measures are offered. The plain one compares each element against the
 * pattern scaled by a single module size. The edge-to-edge one derives separate
 * module sizes for bars and for spaces, which matters on anything printed or
 * photographed: ink spread and blooming widen every bar at the expense of every
 * space, leaving the edge-to-edge distances intact while the individual widths
 * drift. It also tolerates more on bars than on spaces, for the same reason.</p>
 *
 * <p>Index convention follows upstream: element 0 of a pattern is a bar, so
 * even indices are bars and odd ones are spaces. Note that a caller holding a
 * whole scan line has a leading white run at index 0, and must offset past it.
 * </p>
 */
public final class Patterns {

  /** Passed as the space in front when there is none to speak of, at a row edge. */
  public static final int NO_QUIET_ZONE_LIMIT = Integer.MAX_VALUE;

  private Patterns() {
  }

  /**
   * @param view the run lengths to test
   * @param offset where in {@code view} the first bar of the candidate sits
   * @param pattern the expected proportions, starting with a bar
   * @param patternSum the pattern's total in modules
   * @param spaceInPixel the white run in front, for the quiet zone check
   * @param minQuietZone required quiet zone in modules, or 0 to not check
   * @param moduleSizeRef a module size to scale the pattern by instead of the
   *        one derived from {@code view}, or 0 to derive it
   * @return the module size, or 0 if this is not the pattern
   */
  public static double isPattern(int[] view, int offset, int[] pattern, int patternSum,
                                 int spaceInPixel, double minQuietZone, double moduleSizeRef) {
    int len = pattern.length;
    double width = 0;
    for (int i = 0; i < len; i++) {
      width += view[offset + i];
    }
    // A pattern wider in modules than it has elements cannot fit in fewer
    // pixels than it has modules.
    if (patternSum > len && width < patternSum) {
      return 0;
    }

    double moduleSize = width / patternSum;
    if (minQuietZone != 0 && spaceInPixel < minQuietZone * moduleSize - 1) {
      return 0;
    }

    double ref = moduleSizeRef != 0 ? moduleSizeRef : moduleSize;
    // The half module allowance plus a constant, which keeps symbols printed
    // at close to one pixel per module from failing on quantisation alone.
    double threshold = ref * 0.5 + 0.5;
    for (int i = 0; i < len; i++) {
      if (Math.abs(view[offset + i] - pattern[i] * ref) > threshold) {
        return 0;
      }
    }
    return moduleSize;
  }

  /**
   * As {@link #isPattern}, but with bars and spaces measured against their own
   * module sizes, which survives the ink spread that shifts width from spaces
   * to bars without moving the edges between them.
   *
   * @return the mean of the two module sizes, or 0 if this is not the pattern
   */
  public static double isPatternEdgeToEdge(int[] view, int offset, int[] pattern, int spaceInPixel,
                                           double minQuietZone) {
    int len = pattern.length;
    double barWidth = 0;
    double spaceWidth = 0;
    int barModules = 0;
    int spaceModules = 0;
    for (int i = 0; i < len; i++) {
      if ((i & 1) == 0) {
        barWidth += view[offset + i];
        barModules += pattern[i];
      } else {
        spaceWidth += view[offset + i];
        spaceModules += pattern[i];
      }
    }

    double barModuleSize = barWidth / barModules;
    double spaceModuleSize = spaceWidth / spaceModules;

    // Bars and spaces may disagree about the module size, but not wildly.
    double smaller = Math.min(barModuleSize, spaceModuleSize);
    double larger = Math.max(barModuleSize, spaceModuleSize);
    if (larger > 4 * smaller) {
      return 0;
    }

    if (minQuietZone != 0 && spaceInPixel < minQuietZone * spaceModuleSize) {
      return 0;
    }

    double barThreshold = barModuleSize * 0.75 + 0.5;
    double spaceThreshold = spaceModuleSize * 0.6 + 0.5;
    for (int i = 0; i < len; i++) {
      boolean bar = (i & 1) == 0;
      double ms = bar ? barModuleSize : spaceModuleSize;
      double threshold = bar ? barThreshold : spaceThreshold;
      if (Math.abs(view[offset + i] - pattern[i] * ms) > threshold) {
        return 0;
      }
    }
    return (barModuleSize + spaceModuleSize) / 2;
  }

  /** Sum of a pattern's elements, in modules. */
  public static int sum(int[] pattern) {
    int total = 0;
    for (int value : pattern) {
      total += value;
    }
    return total;
  }
}
