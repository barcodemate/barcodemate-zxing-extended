/*
 * Copyright 2007 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitSource;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/** Turns a Micro QR symbol's corrected codewords into text. */
final class MicroQRDecodedBitStreamParser {

  private static final char[] ALPHANUMERIC_CHARS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toCharArray();

  private MicroQRDecodedBitStreamParser() {
  }

  static DecoderResult decode(byte[] bytes, MicroQRVersion version) throws FormatException {
    BitSource bits = new BitSource(bytes);
    StringBuilder result = new StringBuilder(20);
    List<byte[]> byteSegments = new ArrayList<>(1);
    int modeBits = MicroQRMode.getModeBitsLength(version);
    int terminatorBits = MicroQRMode.getTerminatorBitsLength(version);

    try {
      while (!isEndOfStream(bytes, bits, terminatorBits)) {
        MicroQRMode mode;
        if (modeBits == 0) {
          // M1 has no mode indicator at all: it is numeric or it is nothing.
          mode = MicroQRMode.NUMERIC;
        } else {
          mode = MicroQRMode.forBits(bits.readBits(modeBits));
        }
        int countBits = mode.getCharacterCountBits(version);
        if (countBits == 0 && mode != MicroQRMode.NUMERIC) {
          // This version cannot carry this mode, so the bits are not what
          // they claim to be.
          throw FormatException.getFormatInstance();
        }
        switch (mode) {
          case NUMERIC:
            decodeNumeric(bits, countBits, result);
            break;
          case ALPHANUMERIC:
            decodeAlphanumeric(bits, countBits, result);
            break;
          case BYTE:
            decodeByte(bits, countBits, result, byteSegments);
            break;
          case KANJI:
            decodeKanji(bits, countBits, result);
            break;
          default:
            throw FormatException.getFormatInstance();
        }
        if (modeBits == 0) {
          break; // M1 holds exactly one numeric segment
        }
      }
    } catch (IllegalArgumentException ignored) {
      throw FormatException.getFormatInstance();
    }

    return new DecoderResult(bytes, result.toString(),
        byteSegments.isEmpty() ? null : byteSegments, null, 1);
  }

  /**
   * Whether the stream has ended.
   *
   * <p>Micro QR has no terminator mode the way QR does -- mode value 0 is
   * numeric, not a terminator -- so the end is recognised by the next few bits
   * all being zero, or by there being none left. How many bits count as "the
   * next few" widens with the version: 3, 5, 7, 9.</p>
   */
  private static boolean isEndOfStream(byte[] bytes, BitSource bits, int terminatorBits) {
    int available = Math.min(bits.available(), terminatorBits);
    if (available == 0) {
      return true;
    }
    // BitSource cannot peek, so read the bits back out of the array at the
    // position it has reached.
    int position = bits.getByteOffset() * 8 + bits.getBitOffset();
    for (int i = 0; i < available; i++) {
      int bit = position + i;
      if (((bytes[bit / 8] >> (7 - (bit % 8))) & 1) != 0) {
        return false;
      }
    }
    return true;
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
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(2 * count);
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
    } catch (UnsupportedEncodingException e) {
      throw FormatException.getFormatInstance();
    }
  }
}
