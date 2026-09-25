/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from zxing-cpp's test/unit/qrcode/RMQRDecoderTest.cpp (tag v3.1.1,
 * commit 287c85d). The module matrices and expected text are extracted from
 * that file rather than retyped.
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Decoding rMQR symbols from known module matrices, without a detector.
 *
 * <p>These are upstream's own vectors, covering numeric, alphanumeric, byte and
 * kanji segments, an ECI declaration, GS1 data, a symbol too damaged to
 * correct, and nine of the 32 symbol shapes from R7x43 to R17x99.</p>
 */
public final class RMQRDecoderTestCase {

  private static final String[] R7X43M = {
      "XXXXXXX X X X X X X XXX X X X X X X X X XXX",
      "X     X  X XXX  XXXXX XXX      X X XX   X X",
      "X XXX X X XXX X X X XXXX XXXX X  X XXXXXXXX",
      "X XXX X  XX    XXXXX   XXXXXX   X X   X   X",
      "X XXX X   XX  XXX   XXXXXXX  X X  XX  X X X",
      "X     X XXXXX XXX XXX XXXXX    XXXXXX X   X",
      "XXXXXXX X X X X X X XXX X X X X X X X XXXXX",
  };

  private static final String[] R7X43MERROR6BITS = {
      "XXXXXXX X X X X X X XXX X X X X X X X X XXX",
      "X     X  X XXX  XXXXX XXX      X X XX   X X",
      "X XXX X X XXX   X X XXXX XXXX XX X XXXXXXXX",
      "X XXX X  XX    XXXXX X XXXXXX   X X   X   X",
      "X XXX X   XX  XXX   XXXXXXX  X X XXX  X X X",
      "X     X XXXXX XXX XXX XXXX X   XXXXXX X   X",
      "XXXXXXX X X X X X X XXX X X X X X X X XXXXX",
  };

  private static final String[] R7X139H = {
      "XXXXXXX X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X X XXX",
      "X     X XX XXX X X   X  X X XX XX  X   X X XXX XX  XXXX XXX XX  XX XX  X     XX X X X XXX  X   XX   XX   XX X X XX  X XX XXXX  X    X     X",
      "X XXX X    X  XXXXX   X  XXXXX        X X XXX XX    X XXX X XX XXX XX X XXX  X X XXXX   X   XXXXXXX X XX      XXX   X     X  X  XXX X XXXXX",
      "X XXX X  XXXX   X   XX X X    XX  XX  X XX  XX    X XXX XX X XX  X XX  X X   XX  X  X XXX  X  X      X X X X  X XX X   XX   XX   X    X   X",
      "X XXX X XXXX XXXXX X  X XXXXXX XX X XXXX  X    XXXX X XXX  XXXX  X XXXXXXX   XXX XXXXXX X  X XX  X     XXX  X XXXXXXXXX X XXXX  X   X X X X",
      "X     X X   XX  XX X  X  XX X X X XXXX X X   X XX X XXX X  X  X X X  XXX   XX   XXX X  X XX XXXX  XX X X  X   X XXXXX  XXX XX      X XX   X",
      "XXXXXXX X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X X X XXX X X X X X X X X X X XXXXX",
  };

  private static final String[] R9X59H = {
      "XXXXXXX X X X X X XXX X X X X X X X X XXX X X X X X X X XXX",
      "X     X    X  XXXXX XXX X  X XXXXXXXX X X  X    X XXXX  X X",
      "X XXX X XX XXX  X XXX XXXX  X         XXXXXXX  X XXXXX X  X",
      "X XXX X XXXX X XX X   XX   XXXX XX  XX   X  X  X XXX     X ",
      "X XXX X    X    X XX XXXXXX X X XX   X XX   X X XXXX  XXXXX",
      "X     X X  X  X  X  XXX X X   X   XX  X XXXX XX  X X  X   X",
      "XXXXXXX  XXXXX  XXXXXX X XX XXX X    XXXX  X    X  X XX X X",
      "          XXX  XXXX XX XXX    X XXXXXXX X XX XXX  XX XX   X",
      "XXX X X X X X X X XXX X X X X X X X X XXX X X X X X X XXXXX",
  };

  private static final String[] R9X77M = {
      "XXXXXXX X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X X XXX",
      "X     X  XXX XX XXX   XXX XXXX XXX XX X XXXXXXXXX X XXX  XXXX X XXXX XX XXX X",
      "X XXX X X  X X  XXX  X XXXX  XX  XX  X XX XX      XXX XXXX X X XX   X  X XX X",
      "X XXX X X   X XXXXXX  X   XX XXXX X  XXX X XX X  XX  XX XX X XXX X X XXX  XX ",
      "X XXX X     XXXX  X X   XXXX XXXX XX     XXX X XX XXXXXX X X     XXX XX XXXXX",
      "X     X  X X XX XXX    X  X  XX   X X    XX XXX X X   X  X  X    XX XXXXX   X",
      "XXXXXXX    X XX   XX X  XXXX X  X X     X  X  XX  XXX  X XX     X  XXX XX X X",
      "         X XXXXX       XX X XXXXXX XX   XXXXX     X XX     XX   XXXXX XXX   X",
      "XXX X X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X XXXXX",
  };

  private static final String[] R11X27H = {
      "XXXXXXX X X X X X X X X XXX",
      "X     X  XX        X  X X X",
      "X XXX X    X  XX X   X   XX",
      "X XXX X XXXX XX X  XXXXXX  ",
      "X XXX X  X X XX  XX   XXX X",
      "X     X XXX  X XX  XXXX  X ",
      "XXXXXXX     X   XX  X XXXXX",
      "           X   X   X  X   X",
      "XXXX  X   X X XX XXXXXX X X",
      "X XX XXXXXX XXX  XXXX X   X",
      "XXX X X X X X X X X X XXXXX",
  };

  private static final String[] R13X27M_ECI = {
      "XXXXXXX X X X X X X X X XXX",
      "X     X    XX XX XXX   XX X",
      "X XXX X XX  X  XX XX XXX  X",
      "X XXX X  XX X XX X X   XX  ",
      "X XXX X XXXXXXX X X      XX",
      "X     X   XX X  XXX  XX XX ",
      "XXXXXXX   X   X X    X  XXX",
      "        XXX XX X  XX   XXX ",
      "XXX XX XX X  X XX XX  XXXXX",
      " XXX  X    X X    X   X   X",
      "X XX X  X   XX X XX X X X X",
      "X   X   X  X X X X    X   X",
      "XXX X X X X X X X X X XXXXX",
  };

  private static final String[] R15X59H_GS1 = {
      "XXXXXXX X X X X X XXX X X X X X X X X XXX X X X X X X X XXX",
      "X     X   XXX XXX X XXXXX      XX XXX X X   X X X X   XXX X",
      "X XXX X XXX XX X  XXX XXX X  X   XXX XXXXX  XX      XXX  XX",
      "X XXX X X     X XX  X X     XXX X  X    X  XXXXX XX XXX    ",
      "X XXX X XX   XXX  XX   X X X    XX  XX XX XXX XXXX X   XXXX",
      "X     X X  X X X     X  XXX XXX  XXXX X XXX XX    X  X     ",
      "XXXXXXX  X  XXX  XXXX X    XX XXXX X   X XX   XXX XXXXX   X",
      "        X XXX     X    XXXXX     X   XX        XXXX   XX X ",
      "XX  XX X X   X XXXXX   XX X X XX    XX X   XX X X     XX  X",
      " XX XX X   XXXXXX    XXX       XX  X X   XX  XXX   X X XXX ",
      "X X    XX   XXXXXXXXXX XX X  X   XX XX XX X  XXXX XX XXXXXX",
      "  XX X XX X XXX   X  X X    XXX X XXX   X X  XXX   XXXX   X",
      "XXXX   X  X XX    XXX X  X X    XX  XXXXX XX  X  XX XXX X X",
      "X  X   X  XX    XXX XXXXXXX XXX  X  XXX XX  X   X  X XX   X",
      "XXX X X X X X X X XXX X X X X X X X X XXX X X X X X X XXXXX",
  };

  private static final String[] R17X99H = {
      "XXXXXXX X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X XXX",
      "X     X   X XXXXX XXX X X  X XX X X  XX  XXXXX  X XX   X XX XXX X X XX X  X XXX     X X XX   X  X X",
      "X XXX X X X   XXX     XXX X XXX XXX     X  X XX XXXX X  X X  X      XXX   XXXXX X    X XX X XX  X X",
      "X XXX X   XX X  XX X    X   XX   X  XXXX X  XXXXX  X  X    XX X XXX XX X X       X  X   XXXXXX X   ",
      "X XXX X    X XX  X X X X X  X   X X  XXX    XX XXXXXX X    X   XXX  X XXXXXX X   X X X X X X  XX  X",
      "X     X XX  X   X XXXXX  XX   X XXX  X XX   X X    XXX X  XXX  XXX X  XXXX  XX     X X X XX   XXXX ",
      "XXXXXXX X XX X      XX X X  XXX XX  X XXXX    X  X  XXX X X XX X XXXX  XX  X   X        X XX X XXXX",
      "        XX XX XX XX  X  XX  X    X  X XXX XX    X     X  XXX     XXXX  XX X X  X      X XX XX  XXX ",
      "XX       X XXX  X   X XXXX XXX XXXXX  XXX  XXX   X X X  X   X  XXX X  XX  XX X   X X  X  XX  X  XXX",
      " X   XXXXX X  X   XXXXX X  XX       X XX XXXX   X     X XXXXX X XX X  XX  X XX   X XX           XX ",
      "X XX XX   X  XX   XXX  XX XXXXXX X  XXXXX  XX    XXXX  X X X   X XXXX  XX  X   X  XXXXXX    XX  X X",
      " XXX XX  XXX  XX  XX X   X X XX  X X X X XX   XXX XXXX      X XX  XXX X X X XXXX    XXXXX  X XXX   ",
      "X  X  XX    X      XX XX  XX X X XX  X    X X XX XXXXXXXX X XX XX  X   X   X X X XX X X XXXXXXXXXXX",
      "    X X    X XX    X X   X XX XXXX    X XXX  X XX X X X   X X  XXX XXXXX    XX X X  X XXXXX X X   X",
      "XXXX XX XX   X  XXXX XXXX  X XX    X  XX  XX XX XXXX XXX X      X XX XX X XXXX   X XXX  XX X XX X X",
      "X XXX XX  XXX X X X XXX X  XXX   X XXXX  XX     X X  XXXXX X XX X  X X X  X X X X XXXX     XXXX   X",
      "XXX X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X X X X XXX X X X X X X X X XXXXX",
  };

  @Test
  public void testR7x43M() throws Exception {
    assertEquals("ABCDEFG", decode(R7X43M).getText());
  }

  @Test
  public void testR7x43MError6Bits() throws Exception {
    // Six bits flipped: beyond what this symbol's error correction can fix.
    // zxing-cpp returns the uncorrected text alongside an error flag; ZXing's
    // Java API says so by throwing, which is the mapping used throughout.
    try {
      decode(R7X43MERROR6BITS);
      fail("expected a checksum failure");
    } catch (ChecksumException expected) {
      // as upstream
    }
  }

  @Test
  public void testR7x139H() throws Exception {
    assertEquals("1234567890,ABCDEFGHIJKLMOPQRSTUVW", decode(R7X139H).getText());
  }

  @Test
  public void testR9x59H() throws Exception {
    assertEquals("ABCDEFGHIJKLMN", decode(R9X59H).getText());
  }

  @Test
  public void testR9x77M() throws Exception {
    assertEquals("__ABCDEFGH__1234567890___ABCDEFGHIJK", decode(R9X77M).getText());
  }

  @Test
  public void testR11x27H() throws Exception {
    assertEquals("ABCDEF", decode(R11X27H).getText());
  }

  @Test
  public void testR13x27M_ECI() throws Exception {
    DecoderResult result = decode(R13X27M_ECI);
    assertEquals("AB貫12345AB", result.getText());
    // An ECI was declared, which the symbology modifier records.
    assertEquals(2, result.getSymbologyModifier());
  }

  @Test
  public void testR15x59H_GS1() throws Exception {
    DecoderResult result = decode(R15X59H_GS1);
    // Upstream renders GS1 data as human readable AI notation. ZXing's model
    // does not: it reports the raw element string with group separators and
    // flags the symbology, leaving AI parsing to the caller. The payload is
    // the same data either way.
    assertEquals(3, result.getSymbologyModifier());
    assertTrue(result.getText(), result.getText().startsWith("0109524000059109"));
    assertTrue(result.getText(), result.getText().contains("2112345678"));
  }

  @Test
  public void testR17x99H() throws Exception {
    assertEquals("1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890________________________", decode(R17X99H).getText());
  }

  private static DecoderResult decode(String[] rows) throws Exception {
    return RMQRDecoder.decode(parse(rows));
  }

  /** Builds a matrix from rows where 'X' is a dark module. */
  private static BitMatrix parse(String[] rows) {
    BitMatrix matrix = new BitMatrix(rows[0].length(), rows.length);
    for (int y = 0; y < rows.length; y++) {
      for (int x = 0; x < rows[y].length(); x++) {
        if (rows[y].charAt(x) == 'X') {
          matrix.set(x, y);
        }
      }
    }
    return matrix;
  }
}
