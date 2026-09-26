/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.microqr;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Reader;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.DetectorResult;

import java.util.List;
import java.util.Map;

/**
 * Reads Micro QR Code symbols, ISO/IEC 18004.
 *
 * <p>Micro QR is QR stripped to its minimum: 11x11 to 17x17 modules, one finder
 * pattern, and as little overhead as the format can get away with. ZXing for
 * Java has never supported it.</p>
 */
public final class MicroQRReader implements Reader {

  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    DetectorResult detected = MicroQRDetector.detectPure(image.getBlackMatrix());
    DecoderResult decoded = MicroQRDecoder.decode(detected.getBits());

    Result result = new Result(decoded.getText(), decoded.getRawBytes(), detected.getPoints(),
        BarcodeFormat.MICRO_QR_CODE);
    List<byte[]> byteSegments = decoded.getByteSegments();
    if (byteSegments != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
    }
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]Q1");
    return result;
  }

  @Override
  public void reset() {
    // No state is carried between images.
  }
}
