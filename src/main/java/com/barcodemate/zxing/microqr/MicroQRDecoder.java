/*
 * Copyright 2007 ZXing authors
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.reedsolomon.GenericGF;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonException;
import com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Decodes a Micro QR symbol from its module matrix.
 *
 * <p>Simpler than QR in one respect: every Micro QR version holds a single
 * error correction block, so there is no interleaving to undo.</p>
 */
public final class MicroQRDecoder {

  private static final ReedSolomonDecoder RS_DECODER =
      new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);

  private MicroQRDecoder() {
  }

  public static DecoderResult decode(BitMatrix matrix) throws FormatException, ChecksumException {
    if (matrix.getWidth() != matrix.getHeight()) {
      throw FormatException.getFormatInstance();
    }
    MicroQRVersion version = MicroQRVersion.forDimension(matrix.getWidth());
    if (version == null) {
      throw FormatException.getFormatInstance();
    }

    MicroQRFormatInformation formatInfo = MicroQRBitMatrixParser.readFormatInformation(matrix);
    if (!formatInfo.isValid() || formatInfo.getVersionNumber() != version.getVersionNumber()) {
      throw FormatException.getFormatInstance();
    }

    ErrorCorrectionLevel level = formatInfo.getErrorCorrectionLevel();
    MicroQRVersion.ECBlocks ecBlocks = version.getECBlocks(level);
    if (ecBlocks == null) {
      // The format information named a level this version does not offer,
      // which means it was misread rather than merely damaged.
      throw FormatException.getFormatInstance();
    }

    byte[] codewords = MicroQRBitMatrixParser.readCodewords(matrix, version, formatInfo);
    int numDataCodewords = ecBlocks.getTotalDataCodewords();
    correctErrors(codewords, numDataCodewords);

    byte[] dataBytes = new byte[numDataCodewords];
    System.arraycopy(codewords, 0, dataBytes, 0, numDataCodewords);
    return MicroQRDecodedBitStreamParser.decode(dataBytes, version);
  }

  private static void correctErrors(byte[] codewordBytes, int numDataCodewords)
      throws ChecksumException {
    int[] codewordsInts = new int[codewordBytes.length];
    for (int i = 0; i < codewordBytes.length; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    try {
      RS_DECODER.decode(codewordsInts, codewordBytes.length - numDataCodewords);
    } catch (ReedSolomonException ignored) {
      throw ChecksumException.getChecksumInstance();
    }
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
  }
}
