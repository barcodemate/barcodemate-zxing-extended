/*
 * Copyright 2026 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/oned/ODTelepenReader.cpp
 * (tag v3.1.1, commit 287c85d). The decoding algorithm -- start/stop
 * patterns, the bar/space pair to bit-pair mapping, the running
 * narrow/wide threshold, the parity and checksum rules and the
 * compressed-numeric interpretation -- follows that implementation.
 *
 * The scanning scaffolding does not: zxing-cpp drives its 1D readers from
 * a PatternView over a pre-extracted run-length row, which has no
 * counterpart in ZXing for Java. This class instead converts the row to
 * run lengths itself and uses OneDReader.patternMatchVariance, so it
 * behaves like every other reader in this package.
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitArray;

import java.util.Map;

/**
 * <p>Decodes Telepen barcodes, as specified by AIM Europe USS Telepen (1991)
 * and ISO/IEC 15424:2025.</p>
 *
 * <p>Telepen encodes full 7-bit ASCII. A symbol may also carry "compressed
 * numeric" data, in which one codeword holds two digits; which of the two
 * interpretations applies is selected by the start pattern, and the two can be
 * combined in one symbol with DLE (0x10) acting as the shift. The AIM mode in
 * use is reported as a symbology identifier in
 * {@link ResultMetadataType#SYMBOLOGY_IDENTIFIER} ("]B0" through "]B4").</p>
 *
 * <p>ZXing for Java has never supported this symbology; this implementation
 * exists because ZXing-C++ does. See the project README.</p>
 */
public final class TelepenReader extends OneDReader {

  /** Start patterns, 12 elements spanning 16 modules. Index 0 is a bar. */
  private static final int[][] START_PATTERNS = {
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3}, // START 1: full ASCII
      {1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 3}, // START 2: compressed numeric (+ full ASCII)
      {1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 1, 3}, // START 3: full ASCII + compressed numeric
  };

  /** Stop patterns, 11 elements spanning 15 modules. Index 0 is a bar. */
  private static final int[][] STOP_PATTERNS = {
      {3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1}, // STOP 1
      {3, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1}, // STOP 2
      {3, 1, 1, 1, 1, 3, 1, 1, 1, 1, 1}, // STOP 3
  };

  private static final float MAX_AVG_VARIANCE = 0.38f;
  private static final float MAX_INDIVIDUAL_VARIANCE = 0.5f;

  /** The specification asks for 10 modules; zxing-cpp accepts 5 and so do we. */
  private static final int MIN_QUIET_ZONE_MODULES = 5;

  private static final int START_PATTERN_MODULES = 16;
  private static final int MIN_CHARACTER_COUNT = 1;

  /** DLE, the codeword that shifts between full ASCII and compressed numeric. */
  private static final char SHIFT = 0x10;

  private final boolean readAlpha;
  private final boolean readNumeric;

  public TelepenReader() {
    this(true, true);
  }

  /**
   * @param readAlpha accept symbols carrying full ASCII data
   * @param readNumeric accept symbols carrying compressed numeric data
   */
  public TelepenReader(boolean readAlpha, boolean readNumeric) {
    this.readAlpha = readAlpha;
    this.readNumeric = readNumeric;
  }

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    int size = row.getSize();
    int[] runs = new int[size + 2];
    int[] runStarts = new int[size + 2];
    int runCount = toRuns(row, runs, runStarts);

    int[] window = new int[START_PATTERNS[0].length];

    // Bars sit at odd indices, since index 0 is the row's leading white run.
    for (int start = 1; start + window.length <= runCount; start += 2) {
      System.arraycopy(runs, start, window, 0, window.length);

      int variant = matchVariant(window, START_PATTERNS);
      if (variant < 0) {
        continue;
      }
      // Start patterns 2 and 3 declare compressed numeric data.
      if (variant > 0 && !readNumeric) {
        continue;
      }

      float moduleWidth = sum(window) / (float) START_PATTERN_MODULES;
      if (!hasQuietZone(runs, start, moduleWidth)) {
        continue;
      }

      try {
        return decodeSymbol(runs, runStarts, runCount, start, variant, moduleWidth, rowNumber);
      } catch (NotFoundException ignored) {
        // Not a Telepen symbol after all; keep scanning this row.
      }
    }

    throw NotFoundException.getNotFoundInstance();
  }

  private Result decodeSymbol(int[] runs,
                              int[] runStarts,
                              int runCount,
                              int start,
                              int variant,
                              float moduleWidth,
                              int rowNumber)
      throws NotFoundException, ChecksumException, FormatException {

    int[] startPattern = START_PATTERNS[variant];
    int[] stopPattern = STOP_PATTERNS[variant];

    // Seed the narrow/wide thresholds from the start pattern, which contains
    // both narrow and wide elements of both colours.
    int[] threshold = narrowWideThreshold(runs, start, startPattern.length);
    if (threshold == null) {
      throw NotFoundException.getNotFoundInstance();
    }

    int pos = start + startPattern.length;
    StringBuilder raw = new StringBuilder();

    while (pos + stopPattern.length <= runCount && !isStopPattern(runs, pos, stopPattern, moduleWidth, runCount)) {
      int bits = 0;
      int bitCount = 0;
      boolean inBlock = false;
      // Running sums per colour: [0] bars, [1] spaces.
      int[] wideSum = new int[2];
      int[] wideNum = new int[2];
      int[] narrowSum = new int[2];
      int[] narrowNum = new int[2];

      while (pos + 1 < runCount && bitCount < 8) {
        int bar = runs[pos];
        int space = runs[pos + 1];
        if (bar > threshold[0] * 3 || space > threshold[1] * 3) {
          throw NotFoundException.getNotFoundInstance();
        }

        boolean wideBar = bar > threshold[0];
        boolean wideSpace = space > threshold[1];
        if (!wideBar && !wideSpace) {
          bits = (bits << 1) | 1;
          bitCount += 1;
        } else if (!wideBar) {
          // A wide space opens a block on its first occurrence and closes it
          // on the next, so the same pair encodes 01 or 10 by position.
          bits = (bits << 2) | (inBlock ? 0b10 : 0b01);
          bitCount += 2;
          inBlock = !inBlock;
        } else if (!wideSpace) {
          bits = bits << 2;
          bitCount += 2;
        } else {
          bits = (bits << 3) | 0b010;
          bitCount += 3;
        }

        for (int i = 0; i < 2; i++) {
          int width = runs[pos + i];
          if (width > threshold[i]) {
            wideSum[i] += width;
            wideNum[i]++;
          } else {
            narrowSum[i] += width;
            narrowNum[i]++;
          }
        }
        pos += 2;
      }

      // The bit groups above are 1, 2 or 3 bits wide, so a character can
      // overshoot; only an exact 8 is a character.
      if (bitCount != 8) {
        throw NotFoundException.getNotFoundInstance();
      }
      bits &= 0xFF;
      if ((8 - Integer.bitCount(bits)) % 2 != 0) { // even parity over the zero bits
        throw NotFoundException.getNotFoundInstance();
      }
      // Bits arrive most significant first but are stored least significant
      // first; reversing and masking drops the parity bit in one step.
      raw.append((char) ((Integer.reverse(bits) >>> 24) & 0x7F));

      for (int i = 0; i < 2; i++) {
        if (wideNum[i] != 0 && narrowNum[i] != 0) {
          threshold[i] = ((wideSum[i] + wideNum[i] / 2) / wideNum[i]
              + (narrowSum[i] + narrowNum[i] / 2) / narrowNum[i]) / 2;
        } else if (narrowNum[i] != 0) {
          threshold[i] = (2 * narrowSum[i] + narrowNum[i] / 2) / narrowNum[i];
        }
      }
    }

    if (raw.length() < MIN_CHARACTER_COUNT + 1
        || pos + stopPattern.length > runCount
        || !isStopPattern(runs, pos, stopPattern, moduleWidth, runCount)) {
      throw NotFoundException.getNotFoundInstance();
    }

    int left = runStarts[start];
    int right = runStarts[pos + stopPattern.length - 1] + runs[pos + stopPattern.length - 1];

    String text = raw.substring(0, raw.length() - 1);
    char expected = raw.charAt(raw.length() - 1);
    if (checksum(text) != expected) {
      throw ChecksumException.getChecksumInstance();
    }

    return buildResult(text, variant, left, right, rowNumber);
  }

  private Result buildResult(String text, int variant, int left, int right, int rowNumber)
      throws FormatException {

    int startChar = variant + 1;

    // A symbol encoded with START 1 may still carry compressed numeric data in
    // the wild. zxing-cpp guesses, and so do we: at most one DLE and not in
    // first position, some control characters present, but none below 16 --
    // which are the codewords compressed numeric never produces.
    if (startChar == 1 && readNumeric) {
      if (!readAlpha || (count(text, SHIFT) <= 1 && text.charAt(0) != SHIFT
          && anyBelow(text, 32) && !anyBelow(text, 16))) {
        startChar = 2;
      }
    }

    int shift = text.indexOf(SHIFT);
    char modifier;
    if (startChar == 1) {
      modifier = '0';
    } else if (startChar == 2 && shift != 0) {
      String numeric = decodeNumeric(shift == -1 ? text : text.substring(0, shift));
      if (numeric == null) {
        throw FormatException.getFormatInstance();
      }
      text = numeric + (shift == -1 ? "" : text.substring(shift + 1));
      modifier = shift == -1 ? '1' : '2';
    } else if (startChar == 3 && shift != 0) {
      String alpha = shift == -1 ? text : text.substring(0, shift);
      String numeric = shift == -1 ? "" : decodeNumeric(text.substring(shift + 1));
      if (numeric == null) {
        throw FormatException.getFormatInstance();
      }
      text = alpha + numeric;
      modifier = shift == -1 ? '0' : '4';
    } else {
      // DLE as the first character would shift into the mode already active.
      throw FormatException.getFormatInstance();
    }

    BarcodeFormat format = modifier == '1' ? BarcodeFormat.TELEPEN_NUMERIC : BarcodeFormat.TELEPEN_ALPHA;
    Result result = new Result(text,
        null,
        new ResultPoint[] {
            new ResultPoint(left, rowNumber),
            new ResultPoint(right, rowNumber),
        },
        format);
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]B" + modifier);
    return result;
  }

  /**
   * Expands compressed numeric codewords, where one codeword carries two
   * digits. Returns null if the sequence is not valid compressed numeric.
   */
  private static String decodeNumeric(String encoded) {
    StringBuilder decoded = new StringBuilder(encoded.length() * 2);
    for (int i = 0; i < encoded.length(); i++) {
      int codeword = encoded.charAt(i);
      if (codeword < 16 || codeword == 127) {
        decoded.append((char) codeword); // control characters pass through
      } else if (codeword >= 17 && codeword < 27) {
        decoded.append((char) ('0' + codeword - 17)).append('X');
      } else if (codeword >= 27 && codeword < 127) {
        int pair = codeword - 27;
        decoded.append((char) ('0' + pair / 10)).append((char) ('0' + pair % 10));
      } else {
        // 16 is the shift codeword and cannot appear inside numeric data.
        return null;
      }
    }
    return decoded.toString();
  }

  private static char checksum(String text) {
    int sum = 0;
    for (int i = 0; i < text.length(); i++) {
      sum += text.charAt(i);
    }
    return (char) ((127 - (sum % 127)) % 127);
  }

  /**
   * Derives separate narrow/wide thresholds for bars and spaces from a view
   * known to contain both, rejecting views whose proportions cannot be a
   * narrow/wide code.
   */
  private static int[] narrowWideThreshold(int[] runs, int offset, int length) {
    int[] min = {runs[offset], runs[offset + 1]};
    int[] max = {min[0], min[1]};
    for (int i = 2; i < length; i++) {
      int value = runs[offset + i];
      int c = i & 1;
      min[c] = Math.min(min[c], value);
      max[c] = Math.max(max[c], value);
    }

    // How far bars and spaces may differ depends on whether both colours have
    // actually shown a wide element.
    int maxSpread = max[0] >= 2 * min[0] && max[1] >= 2 * min[1] ? 2 : 4;

    int[] threshold = new int[2];
    for (int i = 0; i < 2; i++) {
      int other = 1 - i;
      if (max[i] > 4 * (min[i] + 1)
          || max[i] > maxSpread * max[other]
          || min[i] > maxSpread * (min[other] + 1)) {
        return null;
      }
      threshold[i] = Math.max((min[i] + max[i]) / 2, min[i] * 3 / 2);
    }
    return threshold;
  }

  private static boolean isStopPattern(int[] runs, int pos, int[] stopPattern, float moduleWidth, int runCount) {
    if (pos + stopPattern.length > runCount) {
      return false;
    }
    int[] window = new int[stopPattern.length];
    System.arraycopy(runs, pos, window, 0, window.length);
    if (patternMatchVariance(window, stopPattern, MAX_INDIVIDUAL_VARIANCE) >= MAX_AVG_VARIANCE) {
      return false;
    }
    // Trailing quiet zone. The row's last run is the end of the data, which is
    // as good as a quiet zone.
    int after = pos + stopPattern.length;
    return after >= runCount - 1 || runs[after] >= MIN_QUIET_ZONE_MODULES * moduleWidth;
  }

  private static int matchVariant(int[] window, int[][] patterns) {
    for (int variant = 0; variant < patterns.length; variant++) {
      if (patternMatchVariance(window, patterns[variant], MAX_INDIVIDUAL_VARIANCE) < MAX_AVG_VARIANCE) {
        return variant;
      }
    }
    return -1;
  }

  private static boolean hasQuietZone(int[] runs, int start, float moduleWidth) {
    // start == 1 means the symbol begins at the row edge, which ZXing treats
    // as an acceptable quiet zone throughout this package.
    return start == 1 || runs[start - 1] >= MIN_QUIET_ZONE_MODULES * moduleWidth;
  }

  /**
   * Converts a row to run lengths. Index 0 is the leading white run, which is
   * zero when the row starts on a bar, so bars always sit at odd indices.
   */
  private static int toRuns(BitArray row, int[] runs, int[] runStarts) {
    int size = row.getSize();
    int count = 0;
    int index = 0;
    boolean white = true;

    runStarts[0] = 0;
    while (index < size) {
      int next = white ? row.getNextSet(index) : row.getNextUnset(index);
      runs[count] = next - index;
      runStarts[count] = index;
      count++;
      index = next;
      white = !white;
    }
    if (count == 0) {
      runs[0] = 0;
      runStarts[0] = 0;
      count = 1;
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

  private static int count(String text, char c) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }

  private static boolean anyBelow(String text, int limit) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) < limit) {
        return true;
      }
    }
    return false;
  }
}
