/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.qrmodel1;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.ResultMetadataType;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * The one QR Code Model 1 symbol that exists as a test asset anywhere.
 *
 * <p>Upstream has no unit tests for Model 1, ZXing-C++ cannot generate the
 * format, and no encoder available here produces it either. What there is, is a
 * single PNG tucked into zxing-cpp's {@code qrcode-2} sample folder. That one
 * image is the whole of the external evidence for this reader, and the format
 * is marked accordingly.</p>
 *
 * <p>It is still worth something: the symbol decodes to its expected text, and
 * a Model 1 symbol does not do that by accident. The version table, the format
 * information, the three-region codeword walk with its timing and extension
 * exceptions, and the error correction all have to be right to get there.</p>
 */
public final class QRModel1ReaderTestCase {

  @Test
  public void testDecodesTheUpstreamSample() throws Exception {
    Path image = samplesBase().resolve("qrcode-2/qr-model-1.png");
    Path expected = samplesBase().resolve("qrcode-2/qr-model-1.txt");
    Assume.assumeTrue("zxing-cpp samples not available",
        Files.exists(image) && Files.exists(expected));

    BufferedImage picture = ImageIO.read(image.toFile());
    Assume.assumeTrue("sample image unreadable", picture != null);

    int width = picture.getWidth();
    int height = picture.getHeight();
    int[] pixels = picture.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));

    Result result = new QRModel1Reader().decode(bitmap);

    assertEquals(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8).trim(),
        result.getText().trim());
    assertEquals(BarcodeFormat.QR_CODE_MODEL_1, result.getBarcodeFormat());
    // Upstream records the symbology identifier for this sample as ]Q0, the
    // modifier that distinguishes Model 1 from Model 2's ]Q1.
    assertEquals("]Q0", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
    assertEquals("M", result.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
  }

  private static Path samplesBase() {
    String configured = System.getProperty("zxing.cpp.samples.base");
    return Paths.get(configured == null ? "upstream-zxing-cpp/test/samples" : configured);
  }
}
