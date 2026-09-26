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
   * <p>Implemented, see {@code com.barcodemate.zxing.oned.rss.DataBarLimitedReader}.
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
   * <p>Implemented, see {@code com.barcodemate.zxing.oned.DXFilmEdgeReader}.
   */
  DX_FILM_EDGE,

  /**
   * Compact PDF417, the PDF417 variant whose right row indicators are
   * truncated.
   * <p>Not a reading gap after all, and kept only so the constant can be
   * named. The inherited PDF417 reader decodes these symbols, and ZXing-C++
   * reports {@link #PDF_417} for them as well -- its own reader never returns
   * its {@code CompactPDF417} constant. Requesting this format is therefore
   * the same as requesting {@link #PDF_417}.
   */
  COMPACT_PDF_417,

  /**
   * MicroPDF417, ISO/IEC 24728. Addressed by RAP (Row Address Patterns); it is
   * not a scaled-down PDF417.
   * <p><b>Experimental.</b> Implemented for upright symbols that fill the
   * image, see {@code com.barcodemate.zxing.micropdf417.MicroPDF417Reader}.
   * Rotated, skewed and photographed symbols are not read. Of upstream's ten
   * sample images this reads three, with no misreads; that is a weaker footing
   * than every other format here, and the README says why.
   */
  MICRO_PDF_417,

  /**
   * Aztec Rune, the fixed 11x11 Aztec variant carrying a single byte.
   * <p>Implemented, see {@code com.barcodemate.zxing.aztecrune.AztecRuneReader}.
   */
  AZTEC_RUNE,

  /**
   * QR Code Model 1, the original QR Code that predates {@link #QR_CODE}
   * (Model 2).
   * <p>Implemented, see {@code com.barcodemate.zxing.qrmodel1.QRModel1Reader}.
   * Model 1 and Model 2 share their dimensions and finder patterns, so nothing
   * about a symbol's outline says which it is; they are told apart by the mask
   * applied to the format information. Request this format explicitly.
   * <p>Verification rests on a single sample image, the only one in existence
   * among either upstream's assets: no encoder available generates Model 1,
   * ZXing-C++ included.
   */
  QR_CODE_MODEL_1,

  /**
   * Micro QR Code, ISO/IEC 18004 annex.
   * <p>Implemented, see {@code com.barcodemate.zxing.microqr.MicroQRReader}.
   * As with {@link #RMQR_CODE}, the detector currently handles upright,
   * unrotated images only, so request this format explicitly.
   */
  MICRO_QR_CODE,

  /**
   * rMQR Code (rectangular micro QR), ISO/IEC 23941.
   * <p>Implemented, see {@code com.barcodemate.zxing.rmqr.RMQRReader}. The
   * detector currently handles upright, unrotated images only, so request this
   * format explicitly; it is not in the default scan set.
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
  TELEPEN_NUMERIC,

  /**
   * Code 32, the Italian pharmaceutical code, ISO/IEC 16388 Annex.
   * <p>Not a symbology of its own: a Code 39 symbol of six characters whose
   * value, read base 32, is a nine digit pharmacode. Reported in place of
   * {@link #CODE_39} when the characters and the check digit both agree, which
   * makes a false positive unlikely but not impossible -- an ordinary six
   * character Code 39 symbol could in principle read as one.
   */
  CODE_32,

  /**
   * PZN, the German pharmaceutical code.
   * <p>As with {@link #CODE_32}, a Code 39 symbol rather than a symbology:
   * a hyphen followed by eight digits, the last a weighted modulo 11 check.
   */
  PZN,

  /**
   * Code 39 Extended, which encodes the full ASCII set using shift characters.
   * <p>ZXing for Java has always been able to read this -- {@code Code39Reader}
   * takes an {@code extendedMode} flag -- but had no format constant to report
   * it with. This is that constant, not a new decoder.
   */
  CODE_39_EXTENDED

}
