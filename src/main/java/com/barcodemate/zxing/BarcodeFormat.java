/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.barcodemate.zxing;

/**
 * Enumerates barcode formats known to this package. Please keep alphabetized.
 *
 * @author Sean Owen
 */
public enum BarcodeFormat {

  /** Aztec 2D barcode format. */
  AZTEC,

  /** CODABAR 1D format. */
  CODABAR,

  /** Code 39 1D format. */
  CODE_39,

  /** Code 93 1D format. */
  CODE_93,

  /** Code 128 1D format. */
  CODE_128,

  /** Data Matrix 2D barcode format. */
  DATA_MATRIX,

  /** EAN-8 1D format. */
  EAN_8,

  /** EAN-13 1D format. */
  EAN_13,

  /** ITF (Interleaved Two of Five) 1D format. */
  ITF,

  /** MaxiCode 2D barcode format. */
  MAXICODE,

  /** PDF417 format. */
  PDF_417,

  /** QR Code 2D barcode format. */
  QR_CODE,

  /** RSS 14 */
  RSS_14,

  /** RSS EXPANDED */
  RSS_EXPANDED,

  /** UPC-A 1D format. */
  UPC_A,

  /** UPC-E 1D format. */
  UPC_E,

  /** UPC/EAN extension format. Not a stand-alone format. */
  UPC_EAN_EXTENSION,

  // ---------------------------------------------------------------------------
  // barcodemate-zxing-extended: formats ZXing-C++ can decode and ZXing for
  // Java cannot.
  //
  // These are appended at the end rather than merged into the alphabetical
  // order the comment above asks for, so that the ordinal() of every
  // pre-existing constant stays what it is in com.google.zxing:core 3.5.4.
  //
  // Each constant's Javadoc states whether it is implemented. A constant
  // marked as not implemented is never returned by any reader, and putting it
  // in DecodeHintType.POSSIBLE_FORMATS has no effect.
  // ---------------------------------------------------------------------------

  /**
   * GS1 DataBar Limited, ISO/IEC 24724. Upstream Java calls the DataBar family
   * RSS ({@link #RSS_14}, {@link #RSS_EXPANDED}); this uses the current name.
   * <p>Not implemented yet.
   */
  DATA_BAR_LIMITED,

  /**
   * Telepen, requesting both of its data modes. Use this in
   * {@link DecodeHintType#POSSIBLE_FORMATS} to accept either; results are
   * reported as {@link #TELEPEN_ALPHA} or {@link #TELEPEN_NUMERIC}, never as
   * this constant.
   * <p>Implemented, see {@code com.barcodemate.zxing.oned.TelepenReader}.
   */
  TELEPEN,

  /**
   * DX Film Edge, the DX barcode along the edge of 135 film.
   * <p>Not implemented yet.
   */
  DX_FILM_EDGE,

  /**
   * Compact PDF417, the PDF417 variant whose right row indicators are
   * truncated.
   * <p>Not implemented yet.
   */
  COMPACT_PDF_417,

  /**
   * MicroPDF417, ISO/IEC 24728. Addressed by RAP (Row Address Patterns); it is
   * not a scaled-down PDF417.
   * <p>Not implemented yet.
   */
  MICRO_PDF_417,

  /**
   * Aztec Rune, the fixed 11x11 Aztec variant.
   * <p>Not implemented yet.
   */
  AZTEC_RUNE,

  /**
   * QR Code Model 1, the original QR Code that predates {@link #QR_CODE}
   * (Model 2).
   * <p>Not implemented yet. Note that ZXing-C++ cannot generate this format
   * either, so test symbols have to be sourced elsewhere.
   */
  QR_CODE_MODEL_1,

  /**
   * Micro QR Code, ISO/IEC 18004 annex.
   * <p>Not implemented yet.
   */
  MICRO_QR_CODE,

  /**
   * rMQR Code (rectangular micro QR), ISO/IEC 23941.
   * <p>Not implemented yet.
   */
  RMQR_CODE,

  /**
   * Telepen carrying full ASCII data. Returned by the reader; request it
   * directly to reject compressed numeric symbols.
   * <p>Implemented.
   */
  TELEPEN_ALPHA,

  /**
   * Telepen carrying compressed numeric data, where one codeword holds two
   * digits. Returned by the reader; request it directly to reject full ASCII
   * symbols.
   * <p>Implemented.
   */
  TELEPEN_NUMERIC

}
