/*
 * Copyright 2007 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.qrmodel1;

import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitSource;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a Model 1 symbol's data codewords into text.
 *
 * <p>The encoding is Model 2's, with two differences. The stream opens with
 * four zero bits that are simply dropped, and ECI is not available, so a byte
 * segment is whatever the default character set makes of it.</p>
 */
final class QRModel1BitStreamParser {

  private static final char[] ALPHANUMERIC_CHARS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toCharArray();

  private QRModel1BitStreamParser() {
  }

  static DecoderResult decode(byte[] bytes, QRModel1Version version) throws FormatException {
    BitSource bits = new BitSource(bytes);
    StringBuilder result = new StringBuilder(30);
    List<byte[]> byteSegments = new ArrayList<>(1);

    try {
      bits.readBits(4); // Model 1 leads with four zero bits
      while (bits.available() >= 4) {
        int modeBits = bits.readBits(4);
        if (modeBits == 0) {
          break; // terminator
        }
        int countBits = characterCountBits(modeBits, version);
        if (countBits == 0) {
          throw FormatException.getFormatInstance();
        }
        switch (modeBits) {
          case 1:
            decodeNumeric(bits, countBits, result);
            break;
          case 2:
            decodeAlphanumeric(bits, countBits, result);
            break;
          case 4:
            decodeByte(bits, countBits, result, byteSegments);
            break;
          case 8:
            decodeKanji(bits, countBits, result);
            break;
          case 7:
            // ECI, which Model 1 does not have; the stream is not what it says.
            throw FormatException.getFormatInstance();
          default:
            throw FormatException.getFormatInstance();
        }
      }
    } catch (IllegalArgumentException ranOff) {
      throw FormatException.getFormatInstance();
    }

    return new DecoderResult(bytes, result.toString(),
        byteSegments.isEmpty() ? null : byteSegments, null, 0);
  }

  /** Model 2's widths, which Model 1 shares. */
  private static int characterCountBits(int modeBits, QRModel1Version version) {
    int number = version.getVersionNumber();
    int band = number <= 9 ? 0 : number <= 26 ? 1 : 2;
    switch (modeBits) {
      case 1: return new int[] {10, 12, 14}[band];
      case 2: return new int[] {9, 11, 13}[band];
      case 4: return new int[] {8, 16, 16}[band];
      case 8: return new int[] {8, 10, 12}[band];
      default: return 0;
    }
  }

  private static void decodeNumeric(BitSource bits, int countBits, StringBuilder result)
      throws FormatException {
    int count = bits.readBits(countBits);
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

  private static void decodeAlphanumeric(BitSource bits, int countBits, StringBuilder result)
      throws FormatException {
    int count = bits.readBits(countBits);
    while (count >= 2) {
      int pair = bits.readBits(11);
      if (pair / 45 >= ALPHANUMERIC_CHARS.length) {
        throw FormatException.getFormatInstance();
      }
      result.append(ALPHANUMERIC_CHARS[pair / 45]);
      result.append(ALPHANUMERIC_CHARS[pair % 45]);
      count -= 2;
    }
    if (count == 1) {
      int index = bits.readBits(6);
      if (index >= ALPHANUMERIC_CHARS.length) {
        throw FormatException.getFormatInstance();
      }
      result.append(ALPHANUMERIC_CHARS[index]);
    }
  }

  private static void decodeByte(BitSource bits, int countBits, StringBuilder result,
                                 List<byte[]> byteSegments) throws FormatException {
    int count = bits.readBits(countBits);
    if (8 * count > bits.available()) {
      throw FormatException.getFormatInstance();
    }
    byte[] readBytes = new byte[count];
    for (int i = 0; i < count; i++) {
      readBytes[i] = (byte) bits.readBits(8);
    }
    result.append(new String(readBytes, StringUtils.guessCharset(readBytes, null)));
    byteSegments.add(readBytes);
  }

  private static void decodeKanji(BitSource bits, int countBits, StringBuilder result)
      throws FormatException {
    int count = bits.readBits(countBits);
    if (count * 13 > bits.available()) {
      throw FormatException.getFormatInstance();
    }
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(2 * count);
    while (count > 0) {
      int twoBytes = bits.readBits(13);
      int assembled = ((twoBytes / 0x0C0) << 8) | (twoBytes % 0x0C0);
      assembled += assembled < 0x01F00 ? 0x08140 : 0x0C140;
      buffer.write((byte) (assembled >> 8));
      buffer.write((byte) assembled);
      count--;
    }
    try {
      result.append(buffer.toString(StringUtils.SHIFT_JIS_CHARSET.name()));
    } catch (java.io.UnsupportedEncodingException e) {
      throw FormatException.getFormatInstance();
    }
  }
}
