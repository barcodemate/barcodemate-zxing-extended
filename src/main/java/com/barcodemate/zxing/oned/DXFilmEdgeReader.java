/*
 * Copyright 2023 Antoine Mérino
 * Copyright 2023 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/oned/ODDXFilmEdgeReader.cpp
 * (tag v3.1.1, commit 287c85d), including the pattern-matching tolerances from
 * core/src/Pattern.h.
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.BitArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>Decodes the DX Film Edge barcode latent-exposed along the edge of 135
 * film, which carries the DX number: film type and generation, and on the
 * longer variant the frame number. "36-2/21" means DX1 36, DX2 2, frame 21.</p>
 *
 * <p>This symbology does not fit ZXing's one-reader-one-row model. A symbol is
 * two tracks on <em>different</em> scan lines: a clock track of evenly spaced
 * bars that establishes the module size and the symbol's extent, and a data
 * track beside it. Nothing in the data track says how wide a module is, so it
 * can only be read once the clock track next to it has been seen on another
 * row.</p>
 *
 * <p>The clock tracks found so far are therefore kept between rows. That state
 * belongs to one image: {@link #decode} clears it before and after each call,
 * so nothing can leak into the next image even if a caller never invokes
 * {@link #reset()}. Calling {@link #decodeRow} directly, outside a decode, will
 * simply never find anything, because no clock track will have been
 * registered.</p>
 */
public final class DXFilmEdgeReader extends OneDReader {

  /** Clock track length in modules, long (with frame number) and short variants. */
  private static final int CLOCK_LENGTH_FN = 31;
  private static final int CLOCK_LENGTH_NO_FN = 23;

  /** Data track length in bits, excluding the start and stop patterns. */
  private static final int DATA_LENGTH_FN = 23;
  private static final int DATA_LENGTH_NO_FN = 15;

  private static final int[] CLOCK_PATTERN_FN =
      {5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3};
  private static final int[] CLOCK_PATTERN_NO_FN =
      {5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3};
  private static final int[] DATA_START_PATTERN = {1, 1, 1, 1, 1};
  private static final int[] DATA_STOP_PATTERN = {1, 1, 1};

  /** On the frame-number variant the printed decimal can sit very close to the clock. */
  private static final double CLOCK_FN_QUIET_ZONE = 0.5;
  private static final double CLOCK_NO_FN_QUIET_ZONE = 2.0;
  private static final double DATA_QUIET_ZONE = 0.5;

  /** Minimum data track: one product-class bit plus one parity bit. */
  private static final int MIN_SYMBOL_ELEMENTS = 10;

  private final List<Clock> clocks = new ArrayList<>();
  private int centerRow = Integer.MIN_VALUE;

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, FormatException {
    reset();
    try {
      return super.decode(image, hints);
    } finally {
      reset();
    }
  }

  @Override
  public void reset() {
    clocks.clear();
    centerRow = Integer.MIN_VALUE;
  }

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType,?> hints)
      throws NotFoundException {

    if (centerRow == Integer.MIN_VALUE) {
      centerRow = rowNumber; // ZXing scans outward from the middle row
    }
    // Upstream only walks downward from the centre, so that a clock track is
    // always seen before the data track beside it. ZXing alternates above and
    // below, so without this the two halves would race. TRY_HARDER lifts it.
    boolean tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER);
    if (!tryHarder && rowNumber < centerRow - 1) {
      throw NotFoundException.getNotFoundInstance();
    }

    int size = row.getSize();
    int[] runs = new int[size + 2];
    int[] starts = new int[size + 2];
    int runCount = RunLengths.toRuns(row, runs, starts);

    // Bars are at odd indices; a track can only begin on one.
    for (int p = 1; p + MIN_SYMBOL_ELEMENTS <= runCount; p += 2) {
      if (!is4x1(runs, p, runCount)) {
        continue;
      }

      Clock clock = checkForClock(runs, starts, runCount, p, rowNumber);
      if (clock != null) {
        addClock(clock);
        continue; // a row is either a clock track or a data track, not both
      }

      Result result = tryDataTrack(runs, starts, runCount, p, rowNumber);
      if (result != null) {
        return result;
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  /**
   * The rough check upstream uses to find something that could be part of
   * either track: four consecutive elements of similar width, preceded by a
   * space of at least half that width.
   */
  private static boolean is4x1(int[] runs, int p, int runCount) {
    if (p + 4 >= runCount) {
      return false;
    }
    int spaceInFront = p == 1 ? Integer.MAX_VALUE : runs[p - 1];
    int a = runs[p + 1];
    int b = runs[p + 2];
    int c = runs[p + 3];
    int d = runs[p + 4];
    int diff = Math.abs(a - b) + Math.abs(a - c) + Math.abs(a - d);
    return diff < a && spaceInFront > a / 2;
  }

  private static Clock checkForClock(int[] runs, int[] starts, int runCount, int p, int rowNumber) {
    boolean hasFrameNr;
    int[] pattern;
    if (matches(runs, runCount, p, CLOCK_PATTERN_FN, CLOCK_LENGTH_FN, CLOCK_FN_QUIET_ZONE)) {
      hasFrameNr = true;
      pattern = CLOCK_PATTERN_FN;
    } else if (matches(runs, runCount, p, CLOCK_PATTERN_NO_FN, CLOCK_LENGTH_NO_FN, CLOCK_NO_FN_QUIET_ZONE)) {
      hasFrameNr = false;
      pattern = CLOCK_PATTERN_NO_FN;
    } else {
      return null;
    }

    Clock clock = new Clock();
    clock.hasFrameNr = hasFrameNr;
    clock.rowNumber = rowNumber;
    clock.xStart = starts[p];
    clock.xStop = starts[p + pattern.length - 1] + runs[p + pattern.length - 1];
    return clock;
  }

  private Result tryDataTrack(int[] runs, int[] starts, int runCount, int p, int rowNumber) {
    if (clocks.isEmpty()) {
      return null; // nothing to establish the module size with
    }
    if (!matches(runs, runCount, p, DATA_START_PATTERN, DATA_START_PATTERN.length, DATA_QUIET_ZONE)) {
      return null;
    }

    int xStart = starts[p];
    Clock clock = findClock(xStart, rowNumber);
    if (clock == null) {
      return null;
    }

    double moduleSize = clock.moduleSize();
    int startWidth = 0;
    for (int i = 0; i < DATA_START_PATTERN.length; i++) {
      startWidth += runs[p + i];
    }
    if (Math.abs(startWidth / moduleSize - 5) > 1.0) {
      return null;
    }

    // Skip the start pattern. The first data element is always white: it
    // separates the start pattern from the product number.
    int pos = p + DATA_START_PATTERN.length;
    int dataLength = clock.dataLength();
    BitArray dataBits = new BitArray();
    while (pos < runCount && dataBits.getSize() < dataLength) {
      int modules = (int) Math.round(runs[pos] / moduleSize);
      if (modules < 1 || modules > 20) { // 20 spaces is the widest real run
        return null;
      }
      boolean bar = (pos & 1) == 1;
      for (int i = 0; i < modules; i++) {
        dataBits.appendBit(bar);
      }
      pos++;
    }
    if (dataBits.getSize() != dataLength) {
      return null;
    }

    if (!isRightGuard(runs, runCount, pos, DATA_STOP_PATTERN, moduleSize)) {
      return null;
    }

    // Separator bits are always white.
    if (dataBits.get(0) || dataBits.get(8)
        || (clock.hasFrameNr ? dataBits.get(20) || dataBits.get(22) : dataBits.get(14))) {
      return null;
    }

    int signalSum = 0;
    for (int i = 0; i < dataLength - 2; i++) {
      if (dataBits.get(i)) {
        signalSum++;
      }
    }
    if (signalSum % 2 != (dataBits.get(dataLength - 2) ? 1 : 0)) {
      return null;
    }

    int productNumber = toInt(dataBits, 1, 7);
    if (productNumber == 0) {
      return null;
    }
    int generationNumber = toInt(dataBits, 9, 4);

    StringBuilder text = new StringBuilder(10);
    text.append(productNumber).append('-').append(generationNumber);
    if (clock.hasFrameNr) {
      text.append('/').append(toInt(dataBits, 13, 6));
      if (dataBits.get(19)) {
        text.append('A'); // half-frame exposure
      }
    }

    int xStop = starts[pos + DATA_STOP_PATTERN.length - 1] + runs[pos + DATA_STOP_PATTERN.length - 1];
    if (!clock.isCloseToStop(xStop, rowNumber)) {
      return null;
    }

    // Tighten the clock's extent with what this data track just showed, which
    // helps the rows still to be scanned.
    clock.xStart = xStart;
    clock.xStop = xStop;

    Result result = new Result(text.toString(),
        null,
        new ResultPoint[] {
            new ResultPoint(xStart, rowNumber),
            new ResultPoint(xStop, rowNumber),
        },
        BarcodeFormat.DX_FILM_EDGE);
    // ISO/IEC 15424 leaves 'X' to the decoder vendor; upstream uses ]XF.
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]XF");
    return result;
  }

  private Clock findClock(int x, int y) {
    for (Clock clock : clocks) {
      if (clock.rowNumber != y && clock.isCloseToStart(x, y)) {
        return clock;
      }
    }
    return null;
  }

  private void addClock(Clock clock) {
    Clock existing = findClock(clock.xStart, clock.rowNumber);
    if (existing == null) {
      clocks.add(clock);
    } else {
      existing.hasFrameNr = clock.hasFrameNr;
      existing.rowNumber = clock.rowNumber;
      existing.xStart = clock.xStart;
      existing.xStop = clock.xStop;
    }
  }

  /**
   * Proportional pattern match, following zxing-cpp's IsPattern: the elements
   * are compared against the pattern scaled by the module size derived from
   * the view itself, with a half-module tolerance plus a constant that keeps
   * near-one-module symbols from failing on quantisation alone.
   */
  private static boolean matches(int[] runs, int runCount, int offset, int[] pattern, int sum, double minQuietZone) {
    if (offset + pattern.length > runCount) {
      return false;
    }
    double width = 0;
    for (int i = 0; i < pattern.length; i++) {
      width += runs[offset + i];
    }
    if (sum > pattern.length && width < sum) {
      return false;
    }
    double moduleSize = width / sum;
    int spaceInFront = offset == 1 ? Integer.MAX_VALUE : runs[offset - 1];
    if (minQuietZone > 0 && spaceInFront < minQuietZone * moduleSize - 1) {
      return false;
    }
    double threshold = moduleSize * 0.5 + 0.5;
    for (int i = 0; i < pattern.length; i++) {
      if (Math.abs(runs[offset + i] - pattern[i] * moduleSize) > threshold) {
        return false;
      }
    }
    return true;
  }

  private static boolean isRightGuard(int[] runs, int runCount, int offset, int[] pattern, double moduleSize) {
    if (offset + pattern.length > runCount) {
      return false;
    }
    double width = 0;
    for (int i = 0; i < pattern.length; i++) {
      width += runs[offset + i];
    }
    double guardModule = width / pattern.length;
    double threshold = guardModule * 0.5 + 0.5;
    for (int i = 0; i < pattern.length; i++) {
      if (Math.abs(runs[offset + i] - pattern[i] * guardModule) > threshold) {
        return false;
      }
    }
    int after = offset + pattern.length;
    return after >= runCount || runs[after] >= DATA_QUIET_ZONE * moduleSize - 1;
  }

  private static int toInt(BitArray bits, int offset, int count) {
    int value = 0;
    for (int i = 0; i < count; i++) {
      value = (value << 1) | (bits.get(offset + i) ? 1 : 0);
    }
    return value;
  }

  /** A clock track seen on some row, which a data track on another row can lean on. */
  private static final class Clock {
    boolean hasFrameNr;
    int rowNumber;
    int xStart;
    int xStop;

    int dataLength() {
      return hasFrameNr ? DATA_LENGTH_FN : DATA_LENGTH_NO_FN;
    }

    double moduleSize() {
      return (xStop + 1 - xStart) / (double) (hasFrameNr ? CLOCK_LENGTH_FN : CLOCK_LENGTH_NO_FN);
    }

    private boolean isCloseTo(int x, int y, int toX) {
      double module = moduleSize();
      return Math.abs(x - toX) <= (int) (module * 0.5) && Math.abs(y - rowNumber) <= (int) (module * 4);
    }

    boolean isCloseToStart(int x, int y) {
      return isCloseTo(x, y, xStart);
    }

    boolean isCloseToStop(int x, int y) {
      return isCloseTo(x, y, xStop);
    }
  }
}
