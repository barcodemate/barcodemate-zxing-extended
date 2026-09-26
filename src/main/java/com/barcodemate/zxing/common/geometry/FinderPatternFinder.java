/*
 * Copyright 2020 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/qrcode/QRDetector.cpp (tag
 * v3.1.1, commit 287c85d), FindFinderPatterns.
 */

package com.barcodemate.zxing.common.geometry;

import com.barcodemate.zxing.common.BitMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the concentric finder patterns in an image, wherever they are and
 * however the symbol is turned.
 *
 * <p>The search is two-stage, and the split is the point. Scanning rows for a
 * 1:1:3:1:1 run is cheap but says little: any three bars in roughly those
 * proportions match, and most of what it finds is not a finder pattern.
 * Confirming a candidate is expensive but decisive, because
 * {@link ConcentricFinder#locateConcentricPattern} checks the same proportions
 * along all four axes and walks the rings all the way round. So the cheap pass
 * proposes and the expensive one disposes, and the expensive one runs on
 * relatively few positions.</p>
 *
 * <p>This is what ZXing's own QR detector does differently, and why a separate
 * one was needed: it looks for three finder patterns and reasons about their
 * arrangement. rMQR and Micro QR have one, so each candidate has to stand on
 * its own.</p>
 */
public final class FinderPatternFinder {

  /** The 1:1:3:1:1 of a finder pattern. */
  private static final int[] PATTERN = {1, 1, 3, 1, 1};
  private static final int PATTERN_MODULES = 7;

  /** One pixel per module times three modules of centre. */
  private static final int MIN_SKIP = 3;

  /** Fast scans assume the symbol fills at least a quarter of the image. */
  private static final int MAX_MODULES_FAST = 20 * 4 + 17;

  private FinderPatternFinder() {
  }

  /**
   * @param tryHarder scan every third row rather than sampling sparsely, which
   *        finds smaller symbols at proportionate cost
   */
  public static List<ConcentricPattern> find(BitMatrix image, boolean tryHarder) {
    int height = image.getHeight();
    int width = image.getWidth();
    int skip = (3 * height) / (4 * MAX_MODULES_FAST);
    if (skip < MIN_SKIP || tryHarder) {
      skip = MIN_SKIP;
    }

    List<ConcentricPattern> found = new ArrayList<>();
    int[] runs = new int[width + 2];
    int[] starts = new int[width + 2];
    int[] window = new int[PATTERN.length];

    for (int y = skip - 1; y < height; y += skip) {
      int runCount = toRuns(image, y, runs, starts);
      // Bars sit at odd indices, since index 0 is the leading white run.
      for (int i = 1; i + PATTERN.length <= runCount; i += 2) {
        System.arraycopy(runs, i, window, 0, window.length);
        if (Patterns.isPattern(window, 0, PATTERN, PATTERN_MODULES, 0, 0, 0) == 0) {
          continue;
        }

        PointF centre = new PointF(
            starts[i] + runs[i] + runs[i + 1] + runs[i + 2] / 2.0, y + 0.5);
        if (isInsideKnownPattern(found, centre)) {
          continue;
        }

        // Twice the run width, which allows for a symbol seen at up to 4:1
        // through perspective.
        int candidateWidth = 2 * sum(window);
        ConcentricPattern pattern = ConcentricFinder.locateConcentricPattern(
            image, PATTERN, centre, candidateWidth, false);
        if (pattern != null) {
          found.add(pattern);
        }
      }
    }
    return found;
  }

  private static boolean isInsideKnownPattern(List<ConcentricPattern> found, PointF centre) {
    for (ConcentricPattern pattern : found) {
      if (centre.distance(pattern.center) < pattern.size / 2) {
        return true;
      }
    }
    return false;
  }

  /** Run lengths along one row; index 0 is the leading white run. */
  private static int toRuns(BitMatrix image, int y, int[] runs, int[] starts) {
    int width = image.getWidth();
    int count = 0;
    int x = 0;
    boolean white = true;
    while (x < width) {
      int run = 0;
      int at = x;
      while (x < width && !image.get(x, y) == white) {
        run++;
        x++;
      }
      runs[count] = run;
      starts[count] = at;
      count++;
      white = !white;
    }
    return count;
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }
}
