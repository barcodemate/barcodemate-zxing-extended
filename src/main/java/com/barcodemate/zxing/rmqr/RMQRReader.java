/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.ChecksumException;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.FormatException;
import com.barcodemate.zxing.NotFoundException;
import com.barcodemate.zxing.Reader;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.ResultPoint;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.common.DetectorResult;

import java.util.List;
import java.util.Map;

/**
 * Reads rMQR Code symbols, ISO/IEC 23941.
 *
 * <p>rMQR is the rectangular member of the QR family: 32 shapes from R7x43 to
 * R17x139, meant for narrow labels where a square symbol would not fit or would
 * waste the space around it. ZXing for Java has never supported it; this is the
 * format the rest of this project's geometry work was built for.</p>
 */
public final class RMQRReader implements Reader {

  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    DetectorResult detected = RMQRDetector.detectPure(image.getBlackMatrix());
    DecoderResult decoded = RMQRDecoder.decode(detected.getBits());

    ResultPoint[] points = detected.getPoints();
    Result result = new Result(decoded.getText(), decoded.getRawBytes(), points,
        BarcodeFormat.RMQR_CODE);

    List<byte[]> byteSegments = decoded.getByteSegments();
    if (byteSegments != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
    }
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER,
        "]Q" + decoded.getSymbologyModifier());
    return result;
  }

  @Override
  public void reset() {
    // No state is carried between images.
  }
}
