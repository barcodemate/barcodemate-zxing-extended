/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.micropdf417;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Invariants of the MicroPDF417 tables.
 *
 * <p>Upstream has no unit tests for this symbology at all, so there is nothing
 * to port and nothing to compare against. What can be done instead is to assert
 * the properties the tables must have if they were transcribed correctly -- and
 * these are exactly the properties a slip would break. Both tables were
 * extracted from upstream's source by script rather than retyped, and these
 * checks are what stands behind that.</p>
 */
public final class MicroPDF417TablesTestCase {

  @Test
  public void testRowAddressPatternsSpanTenModules() {
    // Every Row Address Pattern is six elements across ten modules. A dropped
    // or duplicated digit shows up here immediately.
    for (MicroPDF417RAP.Position position : MicroPDF417RAP.Position.values()) {
      for (int address = 1; address <= MicroPDF417RAP.count(); address++) {
        int[] pattern = MicroPDF417RAP.pattern(address, position);
        assertEquals(position + " " + address + " elements", 6, pattern.length);
        int sum = 0;
        for (int element : pattern) {
          assertTrue(position + " " + address + " element width", element >= 1);
          sum += element;
        }
        assertEquals(position + " " + address + " modules", 10, sum);
      }
    }
  }

  @Test
  public void testRowAddressPatternsAreDistinct() {
    // If two addresses shared a pattern the symbology could not work, since
    // the pattern is what says which row this is.
    for (MicroPDF417RAP.Position position : MicroPDF417RAP.Position.values()) {
      Set<String> seen = new HashSet<>();
      for (int address = 1; address <= MicroPDF417RAP.count(); address++) {
        String key = java.util.Arrays.toString(MicroPDF417RAP.pattern(address, position));
        assertTrue(position + " duplicate at " + address, seen.add(key));
      }
    }
  }

  @Test
  public void testLeftAndCentrePatternsAreTellableApart() {
    // The first bar is how a scanner knows which sequence it is looking at:
    // centre patterns start with a single module, edge patterns with at least
    // two.
    for (int address = 1; address <= MicroPDF417RAP.count(); address++) {
      assertTrue("edge " + address,
          MicroPDF417RAP.pattern(address, MicroPDF417RAP.Position.LEFT)[0] >= 2);
      assertEquals("centre " + address,
          1, MicroPDF417RAP.pattern(address, MicroPDF417RAP.Position.CENTRE)[0]);
    }
  }

  @Test
  public void testAddressLookupRoundTrips() {
    for (MicroPDF417RAP.Position position : MicroPDF417RAP.Position.values()) {
      for (int address = 1; address <= MicroPDF417RAP.count(); address++) {
        int[] pattern = MicroPDF417RAP.pattern(address, position);
        assertEquals(position + " " + address, address,
            MicroPDF417RAP.addressOf(pattern, position));
      }
    }
    assertEquals("not a row address pattern",
        0, MicroPDF417RAP.addressOf(new int[] {1, 1, 1, 1, 1, 5}, MicroPDF417RAP.Position.LEFT));
  }

  @Test
  public void testClustersRotateThroughThree() {
    // As in PDF417: 0, 3, 6, repeating, so that a decoded codeword also says
    // which row it came from modulo three.
    assertEquals(0, MicroPDF417RAP.cluster(1));
    assertEquals(3, MicroPDF417RAP.cluster(2));
    assertEquals(6, MicroPDF417RAP.cluster(3));
    assertEquals(0, MicroPDF417RAP.cluster(4));
    for (int address = 1; address <= MicroPDF417RAP.count(); address++) {
      int cluster = MicroPDF417RAP.cluster(address);
      assertTrue("address " + address, cluster == 0 || cluster == 3 || cluster == 6);
    }
  }

  @Test
  public void testSymbolVariants() {
    MicroPDF417Symbol[] symbols = MicroPDF417Symbol.values();
    assertEquals("ISO/IEC 24728 defines 34 variants", 34, symbols.length);

    for (MicroPDF417Symbol symbol : symbols) {
      assertTrue(symbol + " columns", symbol.getColumns() >= 1 && symbol.getColumns() <= 4);
      assertTrue(symbol + " rows", symbol.getRows() >= 1);
      // Every variant spends some of its capacity on error correction, and
      // never all of it.
      assertTrue(symbol + " ec", symbol.getErrorCorrectionCodewords() > 0);
      assertTrue(symbol + " data", symbol.getDataCodewords() > 0);
      assertEquals(symbol + " capacity",
          symbol.getTotalCodewords(),
          symbol.getDataCodewords() + symbol.getErrorCorrectionCodewords());
      // Row addresses come from the 52 entry sequence and cannot run past it.
      assertTrue(symbol + " last row", symbol.getLastRow() <= MicroPDF417RAP.count());
      assertTrue(symbol + " address range",
          symbol.getLastRowAddress() <= MicroPDF417RAP.count());
      assertTrue(symbol + " address order",
          symbol.getFirstRowAddress() <= symbol.getLastRowAddress());
    }
  }

  @Test
  public void testSymbolGeometry() {
    // Width is the data columns plus the row address patterns either side,
    // with a third pair added once there are more than two columns.
    MicroPDF417Symbol narrow = MicroPDF417Symbol.forShape(1, 11);
    assertNotNull(narrow);
    assertEquals(21 + 17, narrow.getWidth());
    assertEquals(22, narrow.getHeight());

    MicroPDF417Symbol wide = MicroPDF417Symbol.forShape(4, 44);
    assertNotNull(wide);
    assertEquals(21 + 4 * 17 + 10, wide.getWidth());
    assertEquals(88, wide.getHeight());

    assertNull("not a legal shape", MicroPDF417Symbol.forShape(5, 10));
  }

  @Test
  public void testSymbolShapesAreUnique() {
    // The decoder identifies a variant by its shape, so two variants sharing
    // one would make that impossible.
    Set<String> seen = new HashSet<>();
    for (MicroPDF417Symbol symbol : MicroPDF417Symbol.values()) {
      assertTrue("duplicate shape " + symbol,
          seen.add(symbol.getColumns() + "x" + symbol.getRows()));
    }
  }

  @Test
  public void testKnownVariantsFromTheSpecification() {
    // Spot checks against ISO/IEC 24728 table 1: the smallest symbol, and one
    // from each column count.
    assertArrayEquals(new int[] {1, 11, 7}, shape(MicroPDF417Symbol.forShape(1, 11)));
    assertArrayEquals(new int[] {2, 8, 8}, shape(MicroPDF417Symbol.forShape(2, 8)));
    assertArrayEquals(new int[] {4, 44, 50}, shape(MicroPDF417Symbol.forShape(4, 44)));
  }

  private static int[] shape(MicroPDF417Symbol symbol) {
    assertNotNull(symbol);
    return new int[] {symbol.getColumns(), symbol.getRows(), symbol.getErrorCorrectionCodewords()};
  }
}
