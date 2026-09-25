/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.common.BitArray;

/**
 * Converts a scan line into run lengths.
 *
 * <p>ZXing for Java's 1D readers each walk the row themselves, recording a
 * handful of runs at a time with {@link OneDReader#recordPattern}. That suits a
 * reader that only ever looks at one character at a time. Some of the
 * symbologies this project adds do not: they measure a whole symbol's
 * proportions before deciding anything, which is far easier to express against
 * the row as a run-length array. zxing-cpp reaches for the same representation
 * (its {@code PatternRow}), and the algorithms ported from it are written in
 * those terms.</p>
 *
 * <p>Index 0 always holds the leading white run, which is zero wide when the
 * row starts on a bar, so bars are always at odd indices and spaces at even
 * ones.</p>
 */
public final class RunLengths {

  private RunLengths() {
  }

  /**
   * @param row the scan line
   * @param runs receives the run lengths; must hold at least {@code row.getSize() + 2}
   * @param starts receives each run's first pixel; same size requirement
   * @return the number of runs written
   */
  public static int toRuns(BitArray row, int[] runs, int[] starts) {
    int size = row.getSize();
    int count = 0;
    int index = 0;
    boolean white = true;

    while (index < size) {
      int next = white ? row.getNextSet(index) : row.getNextUnset(index);
      runs[count] = next - index;
      starts[count] = index;
      count++;
      index = next;
      white = !white;
    }

    if (count == 0) { // an all-white row has one zero-wide run, for index sanity
      runs[0] = 0;
      starts[0] = 0;
      count = 1;
    }
    return count;
  }
}
