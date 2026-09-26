/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.micropdf417;

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
import com.barcodemate.zxing.common.BitMatrix;
import com.barcodemate.zxing.common.DecoderResult;
import com.barcodemate.zxing.pdf417.decoder.MicroPDF417CodewordDecoder;

import java.util.Map;

/**
 * Reads MicroPDF417 symbols, ISO/IEC 24728.
 *
 * <p>Handles the symbol upright and filling the image. See
 * {@link MicroPDF417Detector} for why that limit is where it is.</p>
 */
public final class MicroPDF417Reader implements Reader {

  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    BitMatrix matrix = image.getBlackMatrix();
    MicroPDF417Detector.Detected detected = MicroPDF417Detector.detect(matrix);

    // PDF417 expects a leading symbol length descriptor, which MicroPDF417
    // does not carry; a zero stands in for it and is filled in downstream.
    int[] codewords = new int[detected.codewords.length + 1];
    System.arraycopy(detected.codewords, 0, codewords, 1, detected.codewords.length);

    DecoderResult decoded = MicroPDF417CodewordDecoder.decode(
        codewords, detected.symbol.getErrorCorrectionCodewords());

    int[] box = matrix.getEnclosingRectangle();
    ResultPoint[] points = {
        new ResultPoint(box[0], box[1]),
        new ResultPoint(box[0] + box[2] - 1, box[1]),
        new ResultPoint(box[0] + box[2] - 1, box[1] + box[3] - 1),
        new ResultPoint(box[0], box[1] + box[3] - 1),
    };

    Result result = new Result(decoded.getText(), decoded.getRawBytes(), points,
        BarcodeFormat.MICRO_PDF_417);
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]L2");
    result.putMetadata(ResultMetadataType.ERRORS_CORRECTED, decoded.getErrorsCorrected());
    return result;
  }

  @Override
  public void reset() {
    // No state is carried between images.
  }
}
