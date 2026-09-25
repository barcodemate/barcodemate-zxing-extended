/*
 * Copyright 2007 ZXing authors
 * Copyright 2023 gitlost
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitSource;
import com.barcodemate.zxing.common.CharacterSetECI;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns an rMQR symbol's corrected data codewords into text.
 *
 * <p>The bit stream is a sequence of segments, each introduced by a 3 bit mode
 * indicator and, for the data modes, a character count whose width depends on
 * the version. The segment encodings themselves are QR's, unchanged: digits
 * three to a 10 bit group, alphanumerics two to an 11 bit group, bytes as they
 * are, kanji 13 bits apiece.</p>
 */
final class RMQRDecodedBitStreamParser {

  private static final char[] ALPHANUMERIC_CHARS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toCharArray();

  /** Group separator, which is what FNC1 becomes in GS1 data. */
  private static final char GROUP_SEPARATOR = 0x1D;

  private RMQRDecodedBitStreamParser() {
  }

  static DecoderResult decode(byte[] bytes, RMQRVersion version) throws FormatException {
    BitSource bits = new BitSource(bytes);
    StringBuilder result = new StringBuilder(50);
    List<byte[]> byteSegments = new ArrayList<>(1);
    Charset currentCharset = null;
    boolean fnc1First = false;
    boolean fnc1Second = false;
    int symbologyModifier;

    try {
      while (bits.available() >= 3) {
        RMQRMode mode = RMQRMode.forBits(bits.readBits(3));
        if (mode == RMQRMode.TERMINATOR) {
          break;
        }
        switch (mode) {
          case FNC1_FIRST_POSITION:
            fnc1First = true;
            break;
          case FNC1_SECOND_POSITION:
            bits.readBits(8); // application indicator
            fnc1Second = true;
            break;
          case ECI: {
            int value = parseECIValue(bits);
            CharacterSetECI eci = CharacterSetECI.getCharacterSetECIByValue(value);
            if (eci == null) {
              throw FormatException.getFormatInstance();
            }
            currentCharset = eci.getCharset();
            break;
          }
          case NUMERIC:
            decodeNumeric(bits, mode.getCharacterCountBits(version), result);
            break;
          case ALPHANUMERIC:
            decodeAlphanumeric(bits, mode.getCharacterCountBits(version), result, fnc1First || fnc1Second);
            break;
          case BYTE:
            decodeByte(bits, mode.getCharacterCountBits(version), currentCharset, result, byteSegments);
            break;
          case KANJI:
            decodeKanji(bits, mode.getCharacterCountBits(version), result);
            break;
          default:
            throw FormatException.getFormatInstance();
        }
      }
    } catch (IllegalArgumentException ignored) {
      // Ran off the end of the bit stream, or hit an unsupported character
      // set; either way the symbol did not say what it claimed to.
      throw FormatException.getFormatInstance();
    }

    // ISO/IEC 15424 symbology identifiers for rMQR, mirroring QR's.
    if (fnc1First) {
      symbologyModifier = currentCharset != null ? 4 : 3;
    } else if (fnc1Second) {
      symbologyModifier = currentCharset != null ? 6 : 5;
    } else {
      symbologyModifier = currentCharset != null ? 2 : 1;
    }

    return new DecoderResult(bytes, result.toString(),
        byteSegments.isEmpty() ? null : byteSegments, null, symbologyModifier);
  }

  private static void decodeNumeric(BitSource bits, int countBits, StringBuilder result)
      throws FormatException {
    int count = bits.readBits(countBits);
    // Three digits packed into ten bits, then whatever is left over.
    while (count >= 3) {
      int threeDigits = bits.readBits(10);
      if (threeDigits >= 1000) {
        throw FormatException.getFormatInstance();
      }
      result.append((char) ('0' + threeDigits / 100));
      result.append((char) ('0' + (threeDigits / 10) % 10));
      result.append((char) ('0' + threeDigits % 10));
      count -= 3;
    }
    if (count == 2) {
      int twoDigits = bits.readBits(7);
      if (twoDigits >= 100) {
        throw FormatException.getFormatInstance();
      }
      result.append((char) ('0' + twoDigits / 10));
      result.append((char) ('0' + twoDigits % 10));
    } else if (count == 1) {
      int digit = bits.readBits(4);
      if (digit >= 10) {
        throw FormatException.getFormatInstance();
      }
      result.append((char) ('0' + digit));
    }
  }

  private static void decodeAlphanumeric(BitSource bits, int countBits, StringBuilder result,
                                         boolean fnc1) throws FormatException {
    int start = result.length();
    int count = bits.readBits(countBits);
    while (count >= 2) {
      int nextTwoChars = bits.readBits(11);
      if (nextTwoChars / 45 >= ALPHANUMERIC_CHARS.length) {
        throw FormatException.getFormatInstance();
      }
      result.append(ALPHANUMERIC_CHARS[nextTwoChars / 45]);
      result.append(ALPHANUMERIC_CHARS[nextTwoChars % 45]);
      count -= 2;
    }
    if (count == 1) {
      int index = bits.readBits(6);
      if (index >= ALPHANUMERIC_CHARS.length) {
        throw FormatException.getFormatInstance();
      }
      result.append(ALPHANUMERIC_CHARS[index]);
    }
    if (fnc1) {
      // In GS1 data a literal '%' is doubled, and a single one is the
      // separator between variable length fields.
      for (int i = start; i < result.length(); i++) {
        if (result.charAt(i) == '%') {
          if (i < result.length() - 1 && result.charAt(i + 1) == '%') {
            result.deleteCharAt(i + 1);
          } else {
            result.setCharAt(i, GROUP_SEPARATOR);
          }
        }
      }
    }
  }

  private static void decodeByte(BitSource bits, int countBits, Charset currentCharset,
                                 StringBuilder result, List<byte[]> byteSegments)
      throws FormatException {
    int count = bits.readBits(countBits);
    if (8 * count > bits.available()) {
      throw FormatException.getFormatInstance();
    }
    byte[] readBytes = new byte[count];
    for (int i = 0; i < count; i++) {
      readBytes[i] = (byte) bits.readBits(8);
    }
    Charset charset = currentCharset != null ? currentCharset
        : StringUtils.guessCharset(readBytes, null);
    result.append(new String(readBytes, charset));
    byteSegments.add(readBytes);
  }

  private static void decodeKanji(BitSource bits, int countBits, StringBuilder result)
      throws FormatException {
    int count = bits.readBits(countBits);
    if (count * 13 > bits.available()) {
      throw FormatException.getFormatInstance();
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(2 * count);
    while (count > 0) {
      int twoBytes = bits.readBits(13);
      int assembled = ((twoBytes / 0x0C0) << 8) | (twoBytes % 0x0C0);
      // Shift_JIS has two disjoint double-byte ranges; the encoding folds
      // them together and this puts them back.
      assembled += assembled < 0x01F00 ? 0x08140 : 0x0C140;
      buffer.write((byte) (assembled >> 8));
      buffer.write((byte) assembled);
      count--;
    }
    try {
      result.append(buffer.toString(StringUtils.SHIFT_JIS_CHARSET.name()));
    } catch (UnsupportedEncodingException e) {
      throw FormatException.getFormatInstance();
    }
  }

  /** ECI designators are 1, 2 or 3 bytes, distinguished by their high bits. */
  private static int parseECIValue(BitSource bits) throws FormatException {
    int firstByte = bits.readBits(8);
    if ((firstByte & 0x80) == 0) {
      return firstByte & 0x7F;
    }
    if ((firstByte & 0xC0) == 0x80) {
      return ((firstByte & 0x3F) << 8) | bits.readBits(8);
    }
    if ((firstByte & 0xE0) == 0xC0) {
      return ((firstByte & 0x1F) << 16) | bits.readBits(16);
    }
    throw FormatException.getFormatInstance();
  }
}
