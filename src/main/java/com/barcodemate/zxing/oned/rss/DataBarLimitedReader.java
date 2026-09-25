/*
 * Copyright 2024 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/oned/ODDataBarLimitedReader.cpp
 * (tag v3.1.1, commit 287c85d), together with the edge-to-edge normalisation it
 * relies on from core/src/Pattern.h and core/src/oned/ODDataBarCommon.h.
 */

package com.barcodemate.zxing.oned.rss;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitArray;
import com.barcodemate.zxing.oned.OneDReader;
import com.barcodemate.zxing.oned.RunLengths;

import java.util.Map;

/**
 * <p>Decodes GS1 DataBar Limited, ISO/IEC 24724.</p>
 *
 * <p>DataBar Limited carries a 14 digit GTIN whose indicator digit is 0 or 1,
 * in a symbol 26 modules wide per data character. It is the one member of the
 * DataBar family ZXing for Java never implemented; {@link RSS14Reader} covers
 * DataBar and DataBar Stacked, and {@code RSSExpandedReader} covers the
 * expanded variants.</p>
 *
 * <p>Results are GS1 data: the text is the AI (01) element string, and
 * {@link ResultMetadataType#SYMBOLOGY_IDENTIFIER} is "]e0".</p>
 */
public final class DataBarLimitedReader extends OneDReader {

  private static final int CHAR_LEN = 14;
  /** One guard bar, three data characters, two trailing elements. */
  private static final int SYMBOL_LEN = 1 + 3 * CHAR_LEN + 2;

  private static final int LEFT = 1;
  private static final int CHECK = 1 + CHAR_LEN;
  private static final int RIGHT = 1 + 2 * CHAR_LEN;

  private static final int DATA_MODULES = 26;
  private static final int CHECK_MODULES = 18;

  private static final int[] G_SUM = {0, 183064, 820064, 1000776, 1491021, 1979845, 1996939};
  private static final int[] T_EVEN = {28, 728, 6454, 203, 2408, 1, 16632};
  private static final int[] ODD_SUM = {17, 13, 9, 15, 11, 19, 7};
  private static final int[] ODD_WIDEST = {6, 5, 3, 5, 4, 8, 1};

  /**
   * The 89 valid check characters, as 18 module bitmaps (a set bit is a bar).
   * Transcribed from upstream; the position in this table is the checksum
   * value the two data characters have to agree with.
   */
  private static final int[] CHECK_CHARS = {
      0x2AAE2, 0x2AA72, 0x2AA3A, 0x2A972, 0x2A93A,
      0x2A8BA, 0x2A572, 0x2A53A, 0x2A4BA, 0x2A2BA,
      0x29572, 0x2953A, 0x294BA, 0x292BA, 0x28ABA,
      0x25572, 0x2553A, 0x254BA, 0x252BA, 0x24ABA,
      0x22ABA, 0x2AB62, 0x2AB32, 0x2AB1A, 0x2A9B2,
      0x2A99A, 0x2A8DA, 0x2A5B2, 0x2A59A, 0x2A4DA,
      0x2A2DA, 0x295B2, 0x2959A, 0x294DA, 0x292DA,
      0x28ADA, 0x255B2, 0x2559A, 0x254DA, 0x252DA,
      0x24ADA, 0x22ADA, 0x2ABA2, 0x2AB92, 0x2A9D2,
      0x295D2, 0x255D2, 0x2AD62, 0x2AD32, 0x2AD1A,
      0x2ACB2, 0x296B2, 0x2969A, 0x2965A, 0x2935A,
      0x28B5A, 0x256B2, 0x2569A, 0x24B5A, 0x2B562,
      0x2B532, 0x2B51A, 0x2B4B2, 0x2B49A, 0x2B2B2,
      0x25AB2, 0x25A9A, 0x25A5A, 0x2595A, 0x24D5A,
      0x22D5A, 0x2D562, 0x2D532, 0x2D51A, 0x2D4B2,
      0x2D49A, 0x2D45A, 0x2D2B2, 0x2D29A, 0x2CAB2,
      0x35532, 0x3551A, 0x354B2, 0x3549A, 0x3545A,
      0x3529A, 0x3525A, 0x34A9A, 0x35592
  };

  /** 2D linkage flag for GS1 Composite symbols, ISO/IEC 24724:2011 section 6.2.3. */
  private static final long LINKAGE_FLAG = 2015133531096L;

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType,?> hints)
      throws NotFoundException {

    int size = row.getSize();
    int[] runs = new int[size + 2];
    int[] starts = new int[size + 2];
    int runCount = RunLengths.toRuns(row, runs, starts);

    // Bars are at odd indices, so a symbol can only start there.
    for (int p = 1; p + SYMBOL_LEN <= runCount; p += 2) {
      Result result = tryDecode(runs, starts, runCount, p, rowNumber);
      if (result != null) {
        return result;
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private static Result tryDecode(int[] runs, int[] starts, int runCount, int p, int rowNumber) {
    boolean atFirstBar = p == 1;
    boolean atLastBar = p + SYMBOL_LEN >= runCount;

    if (!isGuard(runs[p + 27], runs[p + 43])) {
      return null;
    }
    int spaceSize = (runs[p + 27] + runs[p + 43]) / 2;
    if (!atFirstBar && runs[p - 1] < spaceSize) {
      return null;
    }
    if (!atLastBar && runs[p + SYMBOL_LEN] < 4 * spaceSize) {
      return null;
    }

    int minBar = Math.min(runs[p], Math.min(runs[p + 28], runs[p + 44]));
    int maxBar = Math.max(runs[p], Math.max(runs[p + 28], runs[p + 44]));
    if (maxBar > minBar * 4 / 3 + 1) {
      return null;
    }

    int leftWidth = sum(runs, p + LEFT, CHAR_LEN);
    int checkWidth = sum(runs, p + CHECK, CHAR_LEN);
    int rightWidth = sum(runs, p + RIGHT, CHAR_LEN);
    if (!has26to18Ratio(leftWidth, checkWidth) || !has26to18Ratio(rightWidth, checkWidth)) {
      return null;
    }

    double moduleSize = (leftWidth + checkWidth + rightWidth)
        / (double) (DATA_MODULES + CHECK_MODULES + DATA_MODULES);
    if (!atFirstBar && runs[p - 1] < moduleSize) {
      return null;
    }
    if (!atLastBar && runs[p + SYMBOL_LEN] < 5 * moduleSize) {
      return null;
    }

    int[] checkPattern = normalizedPatternFromE2E(runs, p + CHECK, CHECK_MODULES);
    int checksum = checkPattern == null ? -1 : indexOf(CHECK_CHARS, toInt(checkPattern));
    if (checksum < 0) {
      return null;
    }

    Character left = readDataCharacter(runs, p + LEFT);
    Character right = readDataCharacter(runs, p + RIGHT);
    if (left == null || right == null) {
      return null;
    }
    if ((left.checksum + 20 * right.checksum) % 89 != checksum) {
      return null;
    }

    int leftEdge = starts[p];
    int rightEdge = starts[p + SYMBOL_LEN - 1] + runs[p + SYMBOL_LEN - 1];
    Result result = new Result(constructText(left.value, right.value),
        null,
        new ResultPoint[] {
            new ResultPoint(leftEdge, rowNumber),
            new ResultPoint(rightEdge, rowNumber),
        },
        BarcodeFormat.DATA_BAR_LIMITED);
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]e0");
    return result;
  }

  private static Character readDataCharacter(int[] runs, int offset) {
    int[] pattern = normalizedPatternFromE2E(runs, offset, DATA_MODULES);
    if (pattern == null) {
      return null;
    }

    int checksum = 0;
    for (int i = pattern.length - 1; i >= 0; i--) {
      checksum = 3 * checksum + pattern[i];
    }

    int[] odd = new int[CHAR_LEN / 2];
    int[] even = new int[CHAR_LEN / 2];
    for (int i = 0; i < CHAR_LEN; i++) {
      if ((i & 1) == 0) {
        odd[i / 2] = pattern[i];
      } else {
        even[i / 2] = pattern[i];
      }
    }

    int group = indexOf(ODD_SUM, sum(odd));
    if (group < 0) {
      return null;
    }
    int oddWidest = ODD_WIDEST[group];
    int evenWidest = 9 - oddWidest;
    int oddValue = RSSUtils.getRSSvalue(odd, oddWidest, false);
    int evenValue = RSSUtils.getRSSvalue(even, evenWidest, true);
    return new Character(oddValue * T_EVEN[group] + evenValue + G_SUM[group], checksum);
  }

  private static String constructText(int left, int right) {
    long value = 2013571L * left + right;
    if (value >= LINKAGE_FLAG) {
      value -= LINKAGE_FLAG; // strip the GS1 Composite linkage flag
    }
    StringBuilder digits = new StringBuilder(13);
    digits.append(value);
    while (digits.length() < 13) {
      digits.insert(0, '0');
    }
    return "01" + digits + checkDigit(digits);
  }

  /** GS1 mod 10 check digit; the rightmost payload digit has weight 3. */
  private static char checkDigit(CharSequence digits) {
    int sum = 0;
    boolean weightThree = true;
    for (int i = digits.length() - 1; i >= 0; i--) {
      sum += (digits.charAt(i) - '0') * (weightThree ? 3 : 1);
      weightThree = !weightThree;
    }
    return (char) ('0' + (10 - sum % 10) % 10);
  }

  /**
   * Derives element widths from edge-to-similar-edge measurements, which are
   * far less sensitive to ink spread than measuring bars and spaces directly.
   *
   * <p>The measurements alone are ambiguous, so the specification fixes that
   * either the odd or the even numbered elements contain at least one element
   * one module wide. For DataBar Limited it is the even ones.</p>
   *
   * @return the {@link #CHAR_LEN} element widths, or null if they are not
   *         self-consistent
   */
  private static int[] normalizedPatternFromE2E(int[] runs, int offset, int modules) {
    double moduleSize = sum(runs, offset, CHAR_LEN) / (double) modules;
    if (moduleSize <= 0) {
      return null;
    }

    int[] widths = new int[CHAR_LEN];
    int barSum = widths[0] = 1; // assume the first bar is one module
    for (int i = 0; i < CHAR_LEN - 2; i++) {
      int e2e = (int) ((runs[offset + i] + runs[offset + i + 1]) / moduleSize + 0.5);
      widths[i + 1] = e2e - widths[i];
      barSum += widths[i + 1];
    }
    widths[CHAR_LEN - 1] = modules - barSum; // the last space completes the symbol

    int minEven = widths[1];
    for (int i = 3; i < CHAR_LEN; i += 2) {
      minEven = Math.min(minEven, widths[i]);
    }
    if (minEven > 1) { // re-centre so the narrowest even element is one module
      for (int i = 0; i < CHAR_LEN; i += 2) {
        widths[i] += minEven - 1;
        widths[i + 1] -= minEven - 1;
      }
    }

    for (int width : widths) {
      if (width < 1) { // not a valid element sequence
        return null;
      }
    }
    return widths;
  }

  /** Packs element widths into a module bitmap, bars set. */
  private static int toInt(int[] widths) {
    int pattern = 0;
    for (int i = 0; i < widths.length; i++) {
      int width = widths[i];
      if (width < 0 || width > 31) {
        return -1;
      }
      pattern = (pattern << width) | ((i & 1) == 0 ? (1 << width) - 1 : 0);
    }
    return pattern;
  }

  private static boolean isGuard(int a, int b) {
    return a > b * 3 / 4 - 2 && a < b * 5 / 4 + 2;
  }

  private static boolean has26to18Ratio(int v26, int v18) {
    return v26 + 1.5 * v26 / 26 > v18 / 18.0 * 26.0
        && v26 - 1.5 * v26 / 26 < v18 / 18.0 * 26.0;
  }

  private static int sum(int[] values) {
    return sum(values, 0, values.length);
  }

  private static int sum(int[] values, int offset, int count) {
    int total = 0;
    for (int i = 0; i < count; i++) {
      total += values[offset + i];
    }
    return total;
  }

  private static int indexOf(int[] values, int needle) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == needle) {
        return i;
      }
    }
    return -1;
  }

  /** A decoded data character: its value and its contribution to the checksum. */
  private static final class Character {
    final int value;
    final int checksum;

    Character(int value, int checksum) {
      this.value = value;
      this.checksum = checksum;
    }
  }
}
