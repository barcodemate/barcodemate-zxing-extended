/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.oned.rss;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.MultiFormatReader;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Decodes the DataBar Limited sample that zxing-cpp keeps in
 * {@code test/samples/databarltd-1}, and asserts against upstream's own
 * expectation file rather than a transcribed string.
 *
 * <p>The samples are not vendored here; point {@code zxing.cpp.samples.base} at
 * a zxing-cpp checkout's {@code test/samples} to run this. Without it the test
 * is skipped, not silently passed.</p>
 *
 * <p>Upstream's corpus holds two symbols for this format, of which only one is
 * a PNG; the other is WebP, which stock ImageIO cannot read. This therefore
 * verifies the port against one real image, which is thin. The stronger check
 * is that the 89 entry check character table, the mod 89 cross-check between
 * the two data characters and the GS1 check digit all have to agree before a
 * result is produced at all.</p>
 */
public final class DataBarLimitedReaderTestCase {

  @Test
  public void testDecodesUpstreamSample() throws Exception {
    Path base = samplesBase();
    Path image = base.resolve("databarltd-1/00.png");
    Path expected = base.resolve("databarltd-1/00.txt");
    Assume.assumeTrue("zxing-cpp samples not available; set -Dzxing.cpp.samples.base=<checkout>/test/samples",
        Files.exists(image) && Files.exists(expected));

    Result result = decode(image);

    assertEquals(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8).trim(), result.getText());
    assertEquals(BarcodeFormat.DATA_BAR_LIMITED, result.getBarcodeFormat());
    assertEquals("]e0", result.getResultMetadata().get(ResultMetadataType.SYMBOLOGY_IDENTIFIER));
  }

  private static Result decode(Path file) throws Exception {
    BufferedImage image = ImageIO.read(file.toFile());
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));

    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.DATA_BAR_LIMITED));
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    return new MultiFormatReader().decode(bitmap, hints);
  }

  private static Path samplesBase() {
    String configured = System.getProperty("zxing.cpp.samples.base");
    return Paths.get(configured == null ? "upstream-zxing-cpp/test/samples" : configured);
  }
}
