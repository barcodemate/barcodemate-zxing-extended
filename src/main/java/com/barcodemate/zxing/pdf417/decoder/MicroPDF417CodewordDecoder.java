/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.pdf417.decoder;

import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.pdf417.decoder.ec.ErrorCorrection;

/**
 * Turns a MicroPDF417 symbol's codewords into text, reusing PDF417's.
 *
 * <p>MicroPDF417 shares PDF417's codeword set, its GF(929) error correction and
 * its high level encoding entirely; the two differ in how a symbol is framed
 * and addressed, not in what the codewords mean. So the whole back half of the
 * decode is PDF417's and there is nothing to reimplement.</p>
 *
 * <p>This class exists in PDF417's package only because the pieces it needs --
 * {@code DecodedBitStreamParser} and the error correction plumbing -- are
 * package private upstream. It adds a file rather than widening their
 * visibility, which would have meant editing upstream code for no other
 * reason.</p>
 *
 * <p>One substantive difference is handled here. PDF417's first codeword is a
 * length descriptor; MicroPDF417 has none, because the variant already fixes
 * how many codewords there are. A zero is prepended in its place, which
 * {@code verifyCodewordCount} then fills in.</p>
 */
public final class MicroPDF417CodewordDecoder {

  private MicroPDF417CodewordDecoder() {
  }

  /**
   * @param codewords the symbol's codewords in reading order, with a leading
   *        zero standing in for the absent length descriptor; -1 marks a
   *        codeword that could not be read
   * @param numECCodewords how many of them are error correction, from the
   *        variant table
   */
  public static DecoderResult decode(int[] codewords, int numECCodewords)
      throws FormatException, ChecksumException {
    if (codewords.length < 4) {
      throw FormatException.getFormatInstance();
    }

    int erasureCount = 0;
    for (int codeword : codewords) {
      if (codeword == -1) {
        erasureCount++;
      }
    }
    if (erasureCount > numECCodewords) {
      // More unreadable codewords than the error correction can replace.
      throw ChecksumException.getChecksumInstance();
    }
    int[] erasures = new int[erasureCount];
    int at = 0;
    for (int i = 0; i < codewords.length; i++) {
      if (codewords[i] == -1) {
        codewords[i] = 0;
        erasures[at++] = i;
      }
    }

    int correctedErrors = new ErrorCorrection().decode(codewords, numECCodewords, erasures);

    // Fill in the length descriptor MicroPDF417 does not carry.
    if (codewords[0] == 0) {
      if (numECCodewords >= codewords.length) {
        throw FormatException.getFormatInstance();
      }
      codewords[0] = codewords.length - numECCodewords;
    } else if (codewords[0] > codewords.length) {
      throw FormatException.getFormatInstance();
    }

    // The error correction level string is only carried through to the result
    // metadata; MicroPDF417's level is fixed by its variant.
    DecoderResult result = DecodedBitStreamParser.decode(codewords, String.valueOf(numECCodewords));
    result.setErrorsCorrected(correctedErrors);
    result.setErasures(erasures.length);
    return result;
  }
}
