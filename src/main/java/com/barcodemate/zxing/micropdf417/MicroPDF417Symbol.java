/*
 * Copyright 2026 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported to pure Java from zxing-cpp's core/src/pdf417/MicroPDFReader.cpp (tag
 * v3.1.1, commit 287c85d). Table is ISO/IEC 24728:2006 tables 1, 10, 11, 12.
 */

package com.barcodemate.zxing.micropdf417;

/**
 * One of MicroPDF417's 34 symbol variants.
 *
 * <p>Where PDF417 lets the encoder pick any number of rows and columns and says
 * so in the row indicators, MicroPDF417 has a closed list: 34 combinations of
 * columns, rows and error correction codewords, and nothing else is legal. That
 * is how it does without row indicators -- a decoder that knows the column
 * count and can see which row addresses are present can work out which variant
 * it is looking at, and everything else follows from the table.</p>
 *
 * <p>Each variant also fixes the range of row addresses it uses, which is what
 * makes the identification possible: two variants with the same column count
 * occupy different stretches of the 52 address sequence.</p>
 */
public final class MicroPDF417Symbol {

  private final int columns;
  private final int rows;
  private final int errorCorrectionCodewords;
  private final int rotationFamily;
  private final int startRow;
  private final int firstRowAddress;
  private final int lastRowAddress;

  MicroPDF417Symbol(int columns, int rows, int errorCorrectionCodewords, int rotationFamily,
                    int startRow, int firstRowAddress, int lastRowAddress) {
    this.columns = columns;
    this.rows = rows;
    this.errorCorrectionCodewords = errorCorrectionCodewords;
    this.rotationFamily = rotationFamily;
    this.startRow = startRow;
    this.firstRowAddress = firstRowAddress;
    this.lastRowAddress = lastRowAddress;
  }

  /** The 34 legal variants, ISO/IEC 24728 tables 1 and 10 to 12. */
  private static final MicroPDF417Symbol[] SYMBOLS = {
      new MicroPDF417Symbol(1, 11, 7, 8, 1, 1, 8),
      new MicroPDF417Symbol(1, 14, 7, 0, 8, 8, 18),
      new MicroPDF417Symbol(1, 17, 7, 0, 36, 39, 52),
      new MicroPDF417Symbol(1, 20, 8, 0, 19, 22, 35),
      new MicroPDF417Symbol(1, 24, 8, 8, 9, 12, 24),
      new MicroPDF417Symbol(1, 28, 8, 8, 25, 33, 52),
      new MicroPDF417Symbol(2, 8, 8, 0, 1, 1, 7),
      new MicroPDF417Symbol(2, 11, 9, 8, 1, 1, 8),
      new MicroPDF417Symbol(2, 14, 9, 0, 8, 9, 18),
      new MicroPDF417Symbol(2, 17, 10, 0, 36, 39, 52),
      new MicroPDF417Symbol(2, 20, 11, 0, 19, 22, 35),
      new MicroPDF417Symbol(2, 23, 13, 8, 9, 12, 26),
      new MicroPDF417Symbol(2, 26, 15, 8, 27, 32, 52),
      new MicroPDF417Symbol(3, 6, 12, 0, 1, 1, 6),
      new MicroPDF417Symbol(3, 8, 14, 0, 7, 7, 14),
      new MicroPDF417Symbol(3, 10, 16, 0, 15, 15, 24),
      new MicroPDF417Symbol(3, 12, 18, 0, 25, 25, 36),
      new MicroPDF417Symbol(3, 15, 21, 0, 37, 37, 51),
      new MicroPDF417Symbol(3, 20, 26, 16, 1, 1, 14),
      new MicroPDF417Symbol(3, 26, 32, 8, 1, 1, 20),
      new MicroPDF417Symbol(3, 32, 38, 8, 21, 27, 52),
      new MicroPDF417Symbol(3, 38, 44, 16, 15, 21, 52),
      new MicroPDF417Symbol(3, 44, 50, 24, 1, 1, 44),
      new MicroPDF417Symbol(4, 4, 8, 24, 47, 47, 50),
      new MicroPDF417Symbol(4, 6, 12, 0, 1, 1, 6),
      new MicroPDF417Symbol(4, 8, 14, 0, 7, 7, 14),
      new MicroPDF417Symbol(4, 10, 16, 0, 15, 15, 24),
      new MicroPDF417Symbol(4, 12, 18, 0, 25, 25, 36),
      new MicroPDF417Symbol(4, 15, 21, 0, 37, 37, 51),
      new MicroPDF417Symbol(4, 20, 26, 16, 1, 1, 14),
      new MicroPDF417Symbol(4, 26, 32, 8, 1, 1, 20),
      new MicroPDF417Symbol(4, 32, 38, 8, 21, 27, 52),
      new MicroPDF417Symbol(4, 38, 44, 16, 15, 21, 52),
      new MicroPDF417Symbol(4, 44, 50, 24, 1, 1, 44),
  };

  public int getColumns() {
    return columns;
  }

  public int getRows() {
    return rows;
  }

  public int getErrorCorrectionCodewords() {
    return errorCorrectionCodewords;
  }

  /** Used to reject variants whose row addresses cannot match what was seen. */
  public int getRotationFamily() {
    return rotationFamily;
  }

  public int getStartRow() {
    return startRow;
  }

  public int getLastRow() {
    return startRow + rows - 1;
  }

  public int getFirstRowAddress() {
    return firstRowAddress;
  }

  public int getLastRowAddress() {
    return lastRowAddress;
  }

  public int getTotalCodewords() {
    return columns * rows;
  }

  public int getDataCodewords() {
    return getTotalCodewords() - errorCorrectionCodewords;
  }

  /** Symbol width in modules, including the row address patterns. */
  public int getWidth() {
    return 21 + columns * 17 + (columns > 2 ? 10 : 0);
  }

  /** Symbol height in modules; every row is two modules tall. */
  public int getHeight() {
    return rows * 2;
  }

  public static MicroPDF417Symbol[] values() {
    return SYMBOLS.clone();
  }

  /** @return the variant with this shape, or null if it is not a legal one */
  public static MicroPDF417Symbol forShape(int columns, int rows) {
    for (MicroPDF417Symbol symbol : SYMBOLS) {
      if (symbol.columns == columns && symbol.rows == rows) {
        return symbol;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return columns + "x" + rows + " (" + errorCorrectionCodewords + " ec)";
  }
}
