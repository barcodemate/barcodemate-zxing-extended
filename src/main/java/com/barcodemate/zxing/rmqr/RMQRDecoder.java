/*
 * Copyright 2007 ZXing authors
 * Copyright 2023 gitlost
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.reedsolomon.GenericGF;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.barcodemate.zxing.common.reedsolomon.ReedSolomonException;

/**
 * Decodes an rMQR symbol from its sampled module matrix.
 *
 * <p>The sequence is the one every QR-family symbol follows: read the format
 * information to learn the version and error correction level, read and unmask
 * the codewords, split them back into the blocks they were interleaved from,
 * correct each block, then parse the reassembled data as a bit stream.</p>
 *
 * <p>The Reed-Solomon code is QR's, over GF(256) with the same generator, so
 * ZXing's existing decoder is used unchanged.</p>
 */
public final class RMQRDecoder {

  private static final ReedSolomonDecoder RS_DECODER =
      new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);

  private RMQRDecoder() {
  }

  public static DecoderResult decode(BitMatrix matrix) throws FormatException, ChecksumException {
    RMQRVersion version = RMQRVersion.forSize(matrix.getWidth(), matrix.getHeight());
    if (version == null) {
      throw FormatException.getFormatInstance();
    }

    RMQRFormatInformation formatInfo = RMQRBitMatrixParser.readFormatInformation(matrix);
    if (!formatInfo.isValid()) {
      throw FormatException.getFormatInstance();
    }
    // The format information carries the version too. When it disagrees with
    // the matrix's own dimensions, the matrix is what was actually measured,
    // so that wins; a mismatch means the format bits are damaged beyond what
    // their hamming distance admitted.
    if (formatInfo.getVersionNumber() != version.getVersionNumber()) {
      throw FormatException.getFormatInstance();
    }

    boolean high = formatInfo.getErrorCorrectionLevel()
        == com.barcodemate.zxing.qrcode.decoder.ErrorCorrectionLevel.H;

    byte[] codewords = RMQRBitMatrixParser.readCodewords(matrix, version, formatInfo);
    RMQRDataBlock[] dataBlocks = RMQRDataBlock.getDataBlocks(codewords, version, high);

    int totalBytes = 0;
    for (RMQRDataBlock block : dataBlocks) {
      totalBytes += block.getNumDataCodewords();
    }
    byte[] resultBytes = new byte[totalBytes];
    int resultOffset = 0;

    for (RMQRDataBlock dataBlock : dataBlocks) {
      byte[] blockBytes = dataBlock.getCodewords();
      int numDataCodewords = dataBlock.getNumDataCodewords();
      correctErrors(blockBytes, numDataCodewords);
      System.arraycopy(blockBytes, 0, resultBytes, resultOffset, numDataCodewords);
      resultOffset += numDataCodewords;
    }

    return RMQRDecodedBitStreamParser.decode(resultBytes, version);
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
