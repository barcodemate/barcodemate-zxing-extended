/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.oned;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.BinaryBitmap;
import com.barcodemate.zxing.DecodeHintType;
import com.barcodemate.zxing.MultiFormatReader;
import com.barcodemate.zxing.RGBLuminanceSource;
import com.barcodemate.zxing.Result;
import com.barcodemate.zxing.common.HybridBinarizer;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Decodes the DX Film Edge samples zxing-cpp keeps in
 * {@code test/samples/dxfilmedge-1}, asserting against upstream's own
 * expectation files.
 *
 * <p>The samples are not vendored here; point {@code zxing.cpp.samples.base} at
 * a zxing-cpp checkout's {@code test/samples} to run this. Without it the test
 * is skipped rather than silently passed.</p>
 *
 * <p>Every image the running JVM can actually read is decoded. Upstream keeps
 * three, one of which is WebP; stock ImageIO cannot read that one, so it is
 * skipped unless a WebP plugin is on the classpath. That matters here, because
 * the WebP sample happens to be the only one of the short variant -- the one
 * without a frame number -- so on a stock JVM this exercises the long variant
 * only. Said plainly rather than papered over.</p>
 */
public final class DXFilmEdgeReaderTestCase {

  @Test
  public void testDecodesUpstreamSamples() throws Exception {
    Path folder = samplesBase().resolve("dxfilmedge-1");
    Assume.assumeTrue("zxing-cpp samples not available; set -Dzxing.cpp.samples.base=<checkout>/test/samples",
        Files.isDirectory(folder));

    Map<Path,String> expectations = new TreeMap<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
      for (Path entry : entries) {
        String name = entry.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || name.endsWith(".txt")) {
          continue;
        }
        Path expected = folder.resolve(name.substring(0, dot) + ".txt");
        if (Files.exists(expected)) {
          expectations.put(entry, new String(Files.readAllBytes(expected), StandardCharsets.UTF_8).trim());
        }
      }
    }
    Assume.assumeFalse("no sample images found", expectations.isEmpty());

    List<String> decoded = new ArrayList<>();
    List<String> unreadable = new ArrayList<>();
    for (Map.Entry<Path,String> sample : expectations.entrySet()) {
      BufferedImage image;
      try {
        image = ImageIO.read(sample.getKey().toFile());
      } catch (IOException unsupported) {
        image = null;
      }
      if (image == null) { // e.g. WebP without a plugin
        unreadable.add(sample.getKey().getFileName().toString());
        continue;
      }
      Result result = decode(image);
      assertEquals(sample.getKey().getFileName().toString(), sample.getValue(), result.getText());
      assertEquals(BarcodeFormat.DX_FILM_EDGE, result.getBarcodeFormat());
      decoded.add(sample.getKey().getFileName().toString());
    }

    assertFalse("no sample image could be read: " + unreadable, decoded.isEmpty());
  }

  private static Result decode(BufferedImage image) throws Exception {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));

    Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.DX_FILM_EDGE));
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    return new MultiFormatReader().decode(bitmap, hints);
  }

  private static Path samplesBase() {
    String configured = System.getProperty("zxing.cpp.samples.base");
    return Paths.get(configured == null ? "upstream-zxing-cpp/test/samples" : configured);
  }
}
