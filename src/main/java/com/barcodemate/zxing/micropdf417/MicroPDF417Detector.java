/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.micropdf417;

import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.pdf417.PDF417Common;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds and reads an upright MicroPDF417 symbol.
 *
 * <p>MicroPDF417 has neither a start pattern nor a stop pattern nor row
 * indicators. What it has instead is a closed list of 34 legal shapes and a
 * row address sequence, and this detector leans on exactly that: it measures
 * the symbol's bounding box, works out how many columns it must have from the
 * width, and then tries each variant with that column count, accepting the one
 * whose row addresses actually read out in the order the table says they
 * should.</p>
 *
 * <p>That last step is the real check. The row addresses are drawn from a 52
 * entry sequence and each variant uses a different stretch of it, so a wrong
 * guess at the shape fails immediately rather than producing plausible
 * nonsense.</p>
 *
 * <p>This handles the symbol upright and filling the image. zxing-cpp's
 * general scanner, which follows rows through rotation and perspective, is a
 * different and much larger piece of machinery; see the project README for
 * where this one stops.</p>
 */
public final class MicroPDF417Detector {

  /** A Row Address Pattern spans ten modules, a codeword seventeen. */
  private static final int RAP_MODULES = 10;
  private static final int CODEWORD_MODULES = PDF417Common.MODULES_IN_CODEWORD;

  /** The decoded codewords of a symbol, with the variant they came from. */
  public static final class Detected {
    public final MicroPDF417Symbol symbol;
    /** Codewords in reading order, -1 where one could not be read. */
    public final int[] codewords;

    Detected(MicroPDF417Symbol symbol, int[] codewords) {
      this.symbol = symbol;
      this.codewords = codewords;
    }
  }

  private MicroPDF417Detector() {
  }

  public static Detected detect(BitMatrix image) throws NotFoundException {
    int[] box = image.getEnclosingRectangle();
    if (box == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    int left = box[0];
    int top = box[1];
    int width = box[2];
    int height = box[3];

    // The narrowest legal symbol is 38 modules wide and 8 tall.
    if (width < 38 || height < 8) {
      throw NotFoundException.getNotFoundInstance();
    }

    // Find the left Row Address Pattern on a few scan lines and let it give
    // up the module width and where the symbol really starts. Deriving those
    // from the bounding box instead looks simpler but is wrong as soon as the
    // box holds anything but the symbol -- a caption, a stray mark, a thicker
    // quiet zone on one side -- and then every row lands slightly off.
    List<double[]> seeds = new ArrayList<>();
    int step = Math.max(1, height / 24);
    for (int y = top; y < top + height; y += step) {
      double[] seed = findLeftAddressPattern(image, left, y, width);
      if (seed != null) {
        seeds.add(seed);
      }
    }
    for (MicroPDF417Symbol symbol : MicroPDF417Symbol.values()) {
      // Two independent ways to place the grid horizontally, tried in turn.
      // The bounding box is right when the box holds nothing but the symbol;
      // a Row Address Pattern measured off an actual scan line is right when
      // it does not. Neither is reliable alone.
      List<double[]> placements = new ArrayList<>();
      placements.add(new double[] {left, width / (double) symbol.getWidth()});
      for (double[] seed : seeds) {
        double implied = symbol.getWidth() * seed[1];
        if (Math.abs((seed[0] + implied) - (left + width)) <= 3 * seed[1]) {
          placements.add(seed);
        }
        // A third combination, and in practice the most useful one: the left
        // edge from the scan line, which is exact, with the module width from
        // the box, which averages over the whole symbol. Measuring the module
        // width off a single Row Address Pattern overestimates it whenever
        // ink spread has thickened the bars, and a few percent is enough to
        // walk off the end of a wide symbol.
        double spanned = (left + width) - seed[0];
        if (spanned > 0) {
          placements.add(new double[] {seed[0], spanned / symbol.getWidth()});
        }
      }

      for (double[] placement : placements) {
        int startX = (int) Math.round(placement[0]);
        double moduleWidth = placement[1];
        if (moduleWidth < 1 || startX + symbol.getWidth() * moduleWidth > image.getWidth() + moduleWidth) {
          continue;
        }
        double[] moduleHeights = {moduleWidth, height / (double) symbol.getHeight()};
        for (double moduleHeight : moduleHeights) {
          if (moduleHeight < 1) {
            continue;
          }
          double needed = symbol.getHeight() * moduleHeight;
          if (needed > height + moduleHeight) {
            continue;
          }
          int slack = (int) Math.max(0, height - needed);
          for (int offset = 0; offset <= slack; offset++) {
            int[] codewords = tryVariant(image, startX, top + offset, symbol, moduleWidth, moduleHeight);
            if (codewords != null) {
              return new Detected(symbol, codewords);
            }
          }
        }
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  /**
   * Looks for a left Row Address Pattern at the start of one scan line.
   *
   * @return {@code {startX, moduleWidth}} or null
   */
  private static double[] findLeftAddressPattern(BitMatrix image, int left, int y, int width) {
    if (y < 0 || y >= image.getHeight()) {
      return null;
    }
    // Walk to the first bar, then take six runs: that is a candidate pattern.
    int x = left;
    while (x < left + width && !image.get(x, y)) {
      x++;
    }
    if (x >= left + width) {
      return null;
    }
    int startX = x;

    int[] runs = new int[6];
    boolean black = true;
    for (int i = 0; i < 6; i++) {
      int run = 0;
      while (x < left + width && image.get(x, y) == black) {
        run++;
        x++;
      }
      if (run == 0) {
        return null;
      }
      runs[i] = run;
      black = !black;
    }

    // A Row Address Pattern spans ten modules, so the module width follows.
    int total = 0;
    for (int run : runs) {
      total += run;
    }
    double moduleWidth = total / 10.0;
    if (moduleWidth < 1) {
      return null;
    }

    int[] normalized = new int[6];
    int sum = 0;
    for (int i = 0; i < 6; i++) {
      normalized[i] = (int) Math.round(runs[i] / moduleWidth);
      if (normalized[i] < 1) {
        return null;
      }
      sum += normalized[i];
    }
    if (sum != 10 || MicroPDF417RAP.addressOf(normalized, MicroPDF417RAP.Position.LEFT) == 0) {
      return null;
    }
    return new double[] {startX, moduleWidth};
  }

  /**
   * @return the symbol's codewords if its rows address themselves the way this
   *         variant says they should, otherwise null
   */
  private static int[] tryVariant(BitMatrix image, int left, int top, MicroPDF417Symbol symbol,
                                  double moduleWidth, double moduleHeight) {
    int columns = symbol.getColumns();
    int rows = symbol.getRows();
    int[] codewords = new int[columns * rows];
    java.util.Arrays.fill(codewords, -1);

    int matched = 0;
    int mismatched = 0;
    // A wrong variant gets almost every address wrong, so a handful of bad
    // rows is tolerated and a quarter of them is not.
    int allowedMismatches = Math.max(1, rows / 4);

    for (int row = 0; row < rows; row++) {
      // Every row is two modules tall; sample down the middle of it.
      double y = top + (row * 2 + 1) * moduleHeight;
      boolean[] modules = sampleRow(image, left, y, moduleWidth, symbol.getWidth());
      if (modules == null) {
        return null;
      }

      int at = 0;
      int leftAddress = readAddress(modules, at, MicroPDF417RAP.Position.LEFT);
      at += RAP_MODULES;
      if (leftAddress == 0) {
        continue; // unreadable row; its codewords stay erased
      }

      // The addresses run consecutively through the 52 entry sequence, so a
      // row's place in the symbol is fixed by the variant's first address.
      if (leftAddress != wrapAddress(symbol.getStartRow() + row)) {
        if (++mismatched > allowedMismatches) {
          return null; // not this variant
        }
        continue;
      }
      matched++;

      boolean rowOk = true;
      int[] rowCodewords = new int[columns];
      for (int column = 0; column < columns && rowOk; column++) {
        // Three column symbols carry a centre address pattern after the first
        // column, four column symbols after the second.
        if ((columns == 3 && column == 1) || (columns == 4 && column == 2)) {
          if (readAddress(modules, at, MicroPDF417RAP.Position.CENTRE) == 0) {
            rowOk = false;
            break;
          }
          at += RAP_MODULES;
        }
        rowCodewords[column] = readCodeword(modules, at);
        at += CODEWORD_MODULES;
      }
      if (!rowOk || readAddress(modules, at, MicroPDF417RAP.Position.RIGHT) == 0) {
        continue; // keep the variant, lose this row to error correction
      }
      System.arraycopy(rowCodewords, 0, codewords, row * columns, columns);
    }

    // Enough rows must have addressed themselves correctly for this to be a
    // claim about the shape rather than a coincidence.
    if (matched * 2 < rows) {
      return null;
    }
    return codewords;
  }

  /** Samples one module row across the symbol's width. */
  private static boolean[] sampleRow(BitMatrix image, int left, double y,
                                     double moduleWidth, int widthInModules) {
    int py = (int) y;
    if (py < 0 || py >= image.getHeight()) {
      return null;
    }
    boolean[] modules = new boolean[widthInModules];
    for (int x = 0; x < widthInModules; x++) {
      int px = (int) (left + (x + 0.5) * moduleWidth);
      if (px < 0 || px >= image.getWidth()) {
        return null;
      }
      modules[x] = image.get(px, py);
    }
    return modules;
  }

  /** Converts ten modules into six run lengths and looks the address up. */
  private static int readAddress(boolean[] modules, int offset, MicroPDF417RAP.Position position) {
    if (offset + RAP_MODULES > modules.length) {
      return 0;
    }
    int[] runs = new int[6];
    int run = 0;
    boolean expected = true; // a Row Address Pattern starts with a bar
    for (int i = 0; i < RAP_MODULES; i++) {
      if (modules[offset + i] == expected) {
        runs[run]++;
      } else {
        run++;
        if (run == runs.length) {
          return 0; // more than six elements: not a Row Address Pattern
        }
        expected = !expected;
        runs[run] = 1;
      }
    }
    if (run != runs.length - 1) {
      return 0; // fewer than six elements
    }
    return MicroPDF417RAP.addressOf(runs, position);
  }

  /** Seventeen modules make one codeword, which PDF417's table decodes. */
  private static int readCodeword(boolean[] modules, int offset) {
    if (offset + CODEWORD_MODULES > modules.length) {
      return -1;
    }
    int symbol = 0;
    for (int i = 0; i < CODEWORD_MODULES; i++) {
      symbol = (symbol << 1) | (modules[offset + i] ? 1 : 0);
    }
    return PDF417Common.getCodeword(symbol);
  }

  /** Row addresses run 1 to 52 and wrap. */
  private static int wrapAddress(int address) {
    return ((address - 1) % MicroPDF417RAP.count()) + 1;
  }
}
