/*
 * Copyright 2026 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/pdf417/MicroPDFReader.cpp (tag
 * v3.1.1, commit 287c85d). Tables are ISO/IEC 24728:2006.
 */

package com.barcodemate.zxing.micropdf417;

/**
 * MicroPDF417's Row Address Patterns, and the lookup that turns a measured one
 * back into a row address.
 *
 * <p>This is where MicroPDF417 parts company with PDF417, and it is worth being
 * precise about why, because "small PDF417" is a misleading name. A PDF417 row
 * is framed by a start pattern and a stop pattern, identical on every row; the
 * decoder finds those, and the row indicators inside carry the row number.
 * MicroPDF417 cannot afford them. Instead each row is framed by a pair of Row
 * Address Patterns, drawn from a 52 entry sequence, and <em>which</em> patterns
 * they are is what says where the row sits. The frame and the addressing are
 * the same ink.</p>
 *
 * <p>There are two sequences: one used on the left and right edges, one used in
 * the middle of multi-column symbols. Every entry is six elements spanning ten
 * modules. The left and right patterns begin with a bar of at least two
 * modules, the centre ones with a single module, which is the first thing a
 * scanner can tell them apart by.</p>
 */
public final class MicroPDF417RAP {

  /** Which of the three positions a Row Address Pattern occupies. */
  public enum Position {
    LEFT,
    CENTRE,
    RIGHT,
  }

  /** The 52 left and right Row Address Patterns, in address order. */
  private static final int[][] LEFT_RIGHT = {
      {2, 2, 1, 3, 1, 1}, {3, 1, 1, 3, 1, 1}, {3, 1, 2, 2, 1, 1}, {2, 2, 2, 2, 1, 1},
      {2, 1, 3, 2, 1, 1}, {2, 1, 4, 1, 1, 1}, {2, 2, 3, 1, 1, 1}, {3, 1, 3, 1, 1, 1},
      {3, 2, 2, 1, 1, 1}, {4, 1, 2, 1, 1, 1}, {4, 2, 1, 1, 1, 1}, {3, 3, 1, 1, 1, 1},
      {2, 4, 1, 1, 1, 1}, {2, 3, 2, 1, 1, 1}, {2, 3, 1, 2, 1, 1}, {3, 2, 1, 2, 1, 1},
      {4, 1, 1, 2, 1, 1}, {4, 1, 1, 1, 2, 1}, {4, 1, 1, 1, 1, 2}, {3, 2, 1, 1, 1, 2},
      {3, 1, 2, 1, 1, 2}, {3, 1, 1, 2, 1, 2}, {3, 1, 1, 2, 2, 1}, {3, 1, 1, 1, 3, 1},
      {3, 1, 1, 1, 2, 2}, {3, 1, 1, 1, 1, 3}, {2, 2, 1, 1, 1, 3}, {2, 2, 1, 1, 2, 2},
      {2, 2, 1, 1, 3, 1}, {2, 2, 1, 2, 2, 1}, {2, 2, 2, 1, 2, 1}, {3, 1, 2, 1, 2, 1},
      {3, 2, 1, 1, 2, 1}, {2, 3, 1, 1, 2, 1}, {2, 3, 1, 1, 1, 2}, {2, 2, 2, 1, 1, 2},
      {2, 1, 3, 1, 1, 2}, {2, 1, 2, 2, 1, 2}, {2, 1, 2, 2, 2, 1}, {2, 1, 2, 1, 3, 1},
      {2, 1, 2, 1, 2, 2}, {2, 1, 2, 1, 1, 3}, {2, 1, 1, 2, 1, 3}, {2, 1, 1, 1, 2, 3},
      {2, 1, 1, 1, 3, 2}, {2, 1, 1, 1, 4, 1}, {2, 1, 1, 2, 3, 1}, {2, 1, 1, 2, 2, 2},
      {2, 1, 1, 3, 1, 2}, {2, 1, 1, 3, 2, 1}, {2, 1, 1, 4, 1, 1}, {2, 1, 2, 3, 1, 1},
  };

  /** The 52 centre Row Address Patterns, in address order. */
  private static final int[][] CENTRE = {
      {1, 1, 2, 2, 3, 1}, {1, 2, 1, 2, 3, 1}, {1, 2, 2, 1, 3, 1}, {1, 3, 1, 1, 3, 1},
      {1, 3, 1, 2, 2, 1}, {1, 3, 2, 1, 2, 1}, {1, 4, 1, 1, 2, 1}, {1, 4, 1, 2, 1, 1},
      {1, 4, 2, 1, 1, 1}, {1, 3, 3, 1, 1, 1}, {1, 3, 2, 2, 1, 1}, {1, 3, 1, 3, 1, 1},
      {1, 2, 2, 3, 1, 1}, {1, 2, 3, 2, 1, 1}, {1, 2, 4, 1, 1, 1}, {1, 1, 5, 1, 1, 1},
      {1, 1, 4, 2, 1, 1}, {1, 1, 4, 1, 2, 1}, {1, 2, 3, 1, 2, 1}, {1, 2, 3, 1, 1, 2},
      {1, 2, 2, 2, 1, 2}, {1, 2, 2, 2, 2, 1}, {1, 2, 1, 3, 2, 1}, {1, 2, 1, 4, 1, 1},
      {1, 1, 2, 4, 1, 1}, {1, 1, 3, 3, 1, 1}, {1, 1, 3, 2, 2, 1}, {1, 1, 3, 2, 1, 2},
      {1, 1, 3, 1, 2, 2}, {1, 2, 2, 1, 2, 2}, {1, 3, 1, 1, 2, 2}, {1, 3, 1, 1, 1, 3},
      {1, 2, 2, 1, 1, 3}, {1, 1, 3, 1, 1, 3}, {1, 1, 2, 2, 1, 3}, {1, 1, 2, 2, 2, 2},
      {1, 1, 2, 3, 1, 2}, {1, 1, 2, 3, 2, 1}, {1, 1, 1, 4, 2, 1}, {1, 1, 1, 3, 3, 1},
      {1, 1, 1, 3, 2, 2}, {1, 1, 1, 2, 3, 2}, {1, 1, 1, 2, 2, 3}, {1, 1, 1, 1, 3, 3},
      {1, 1, 1, 1, 2, 4}, {1, 1, 1, 2, 1, 4}, {1, 1, 2, 1, 1, 4}, {1, 2, 1, 1, 1, 4},
      {1, 2, 1, 1, 2, 3}, {1, 2, 1, 1, 3, 2}, {1, 1, 2, 1, 3, 2}, {1, 1, 2, 1, 4, 1},
  };

  private MicroPDF417RAP() {
  }

  public static int count() {
    return LEFT_RIGHT.length;
  }

  /** @param address 1 to 52 */
  public static int[] pattern(int address, Position position) {
    int[][] table = position == Position.CENTRE ? CENTRE : LEFT_RIGHT;
    return table[address - 1];
  }

  /**
   * The address of a measured pattern.
   *
   * @return 1 to 52, or 0 if these proportions are not a Row Address Pattern
   */
  public static int addressOf(int[] pattern, Position position) {
    int[][] table = position == Position.CENTRE ? CENTRE : LEFT_RIGHT;
    for (int i = 0; i < table.length; i++) {
      if (java.util.Arrays.equals(table[i], pattern)) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * The codeword cluster a row addressed this way uses.
   *
   * <p>PDF417 rotates through three clusters, 0, 3 and 6, so that a decoder
   * reading a codeword also learns which row it came from modulo three.
   * MicroPDF417 keeps that, indexed off the row address.</p>
   */
  public static int cluster(int address) {
    return ((address - 1) % 3) * 3;
  }
}
