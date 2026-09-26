/*
 * Copyright 2021 Axel Waggershauser
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Code 32 and PZN rules are ported from zxing-cpp's
 * core/src/oned/ODCode39Reader.cpp (tag v3.1.1, commit 287c85d).
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.common.BitArray;

import java.util.Map;

/**
 * Reports the interpretations a Code 39 symbol may carry beyond its raw
 * characters: Code 32, PZN, and Code 39 Extended.
 *
 * <p>None of these is a symbology. They are all ordinary Code 39 symbols whose
 * contents follow a further convention, and ZXing for Java could always read
 * the characters -- it simply had no way to say what they meant. This reader
 * adds that, and nothing else.</p>
 *
 * <p>Order matters and follows upstream: PZN is tested first, then Code 32,
 * then the extended encoding. A symbol that satisfies none of them is left as
 * plain {@link BarcodeFormat#CODE_39}, which is what the inherited reader would
 * have returned anyway.</p>
 */
public final class Code39VariantReader extends OneDReader {

  /** Code 32's alphabet: the digits and the letters less A, E, I and O. */
  private static final String TABELLA = "0123456789BCDFGHJKLMNPQRSTUVWXYZ";

  /** The shift characters that mark an extended encoding. */
  private static final String SHIFT_CHARS = "$%/+";

  private final Code39Reader plain = new Code39Reader(false, false);
  private final Code39Reader extended = new Code39Reader(false, true);

  private final boolean readPzn;
  private final boolean readCode32;
  private final boolean readExtended;

  public Code39VariantReader(boolean readPzn, boolean readCode32, boolean readExtended) {
    this.readPzn = readPzn;
    this.readCode32 = readCode32;
    this.readExtended = readExtended;
  }

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    Result result = plain.decodeRow(rowNumber, row, hints);
    String text = result.getText();

    if (readPzn && isPZN(text)) {
      return reinterpret(result, text, BarcodeFormat.PZN);
    }
    if (readCode32) {
      String pharmacode = decodeCode32(text);
      if (pharmacode != null) {
        return reinterpret(result, pharmacode, BarcodeFormat.CODE_32);
      }
    }
    if (readExtended && containsShiftCharacter(text)) {
      // The inherited reader does the full ASCII decoding; all that is added
      // here is saying so in the format.
      Result full = extended.decodeRow(rowNumber, row, hints);
      return reinterpret(full, full.getText(), BarcodeFormat.CODE_39_EXTENDED);
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private static Result reinterpret(Result source, String text, BarcodeFormat format) {
    Result result = new Result(text, source.getRawBytes(), source.getResultPoints(), format);
    Object symbology = source.getResultMetadata() == null ? null
        : source.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER);
    if (symbology != null) {
      result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, symbology);
    }
    return result;
  }

  /** A hyphen and eight digits, the last a weighted modulo 11 check. */
  private static boolean isPZN(String text) {
    if (text.length() != 9 || text.charAt(0) != '-') {
      return false;
    }
    for (int i = 1; i < 9; i++) {
      if (text.charAt(i) < '0' || text.charAt(i) > '9') {
        return false;
      }
    }
    int checksum = 0;
    for (int i = 1; i < 8; i++) {
      checksum += (text.charAt(i) - '0') * i;
    }
    return checksum % 11 == text.charAt(8) - '0';
  }

  /**
   * Six characters read as a base 32 number give a nine digit pharmacode,
   * whose last digit is a Luhn-style check over the other eight.
   *
   * @return the pharmacode with its leading "A", or null if this is not one
   */
  private static String decodeCode32(String text) {
    if (text.length() != 6) {
      return null;
    }
    int value = 0;
    for (int i = 0; i < 6; i++) {
      int digit = TABELLA.indexOf(text.charAt(i));
      if (digit < 0) {
        return null;
      }
      value = value * 32 + digit;
    }
    if (value < 0 || value >= 1000000000) {
      return null;
    }

    StringBuilder digits = new StringBuilder(Integer.toString(value));
    while (digits.length() < 9) {
      digits.insert(0, '0');
    }

    int checksum = 0;
    for (int i = 0; i < 8; i += 2) {
      int doubled = 2 * (digits.charAt(i + 1) - '0');
      checksum += (digits.charAt(i) - '0') + doubled % 10 + (doubled >= 10 ? 1 : 0);
    }
    if (checksum % 10 != digits.charAt(8) - '0') {
      return null;
    }
    return "A" + digits;
  }

  private static boolean containsShiftCharacter(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (SHIFT_CHARS.indexOf(text.charAt(i)) >= 0) {
        return true;
      }
    }
    return false;
  }
}
