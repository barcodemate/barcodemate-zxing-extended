/*
 * Copyright 2007 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.FormatException;

/**
 * One error correction block of an rMQR symbol: its data codewords followed by
 * its error correction codewords.
 *
 * <p>The codewords do not sit in the symbol block by block. They are
 * interleaved, so that a scratch or a fold damaging a run of the symbol spreads
 * its damage thinly across every block rather than destroying one of them
 * outright. Splitting them back apart is the first thing the decoder does.</p>
 */
final class RMQRDataBlock {

  private final int numDataCodewords;
  private final byte[] codewords;

  private RMQRDataBlock(int numDataCodewords, byte[] codewords) {
    this.numDataCodewords = numDataCodewords;
    this.codewords = codewords;
  }

  int getNumDataCodewords() {
    return numDataCodewords;
  }

  byte[] getCodewords() {
    return codewords;
  }

  static RMQRDataBlock[] getDataBlocks(byte[] rawCodewords, RMQRVersion version, boolean high)
      throws FormatException {
    RMQRVersion.ECBlocks ecBlocks = version.getECBlocks(high);
    if (rawCodewords.length != ecBlocks.getTotalCodewords()) {
      throw FormatException.getFormatInstance();
    }

    int numBlocks = ecBlocks.getNumBlocks();
    RMQRDataBlock[] result = new RMQRDataBlock[numBlocks];
    int shorterBlocksTotalCodewords = 0;
    for (int i = 0; i < numBlocks; i++) {
      int numDataCodewords = ecBlocks.getDataCodewords(i);
      int numBlockCodewords = ecBlocks.getECCodewordsPerBlock() + numDataCodewords;
      result[i] = new RMQRDataBlock(numDataCodewords, new byte[numBlockCodewords]);
      if (i == 0) {
        shorterBlocksTotalCodewords = numBlockCodewords;
      }
    }
    int shorterBlocksNumDataCodewords =
        shorterBlocksTotalCodewords - ecBlocks.getECCodewordsPerBlock();

    // Data codewords: one per block in turn, the longer blocks getting an
    // extra round at the end.
    int rawIndex = 0;
    for (int i = 0; i < shorterBlocksNumDataCodewords; i++) {
      for (RMQRDataBlock block : result) {
        block.codewords[i] = rawCodewords[rawIndex++];
      }
    }
    for (int j = 0; j < numBlocks; j++) {
      if (result[j].numDataCodewords > shorterBlocksNumDataCodewords) {
        result[j].codewords[shorterBlocksNumDataCodewords] = rawCodewords[rawIndex++];
      }
    }

    // Then the error correction codewords, again one per block in turn.
    int max = result[0].codewords.length;
    for (int i = shorterBlocksNumDataCodewords; i < max; i++) {
      for (int j = 0; j < numBlocks; j++) {
        int offset = result[j].numDataCodewords > shorterBlocksNumDataCodewords ? i + 1 : i;
        result[j].codewords[offset] = rawCodewords[rawIndex++];
      }
    }
    return result;
  }
}
