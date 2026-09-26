/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.rmqr;

import com.barcodemate.zxing.BinaryBitmap;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * rMQR symbols from zxing-cpp's own sample images, read through the general
 * detector rather than the upright one.
 *
 * <p>These are photographs and renders rather than clean crops, so they are
 * what the finder pattern search and the geometry layer were built for. A
 * wrong answer fails the test; not answering on one is recorded and reported,
 * because upstream's own pass thresholds for this folder are not 3 of 3
 * either.</p>
 */
public final class RMQRSampleImageTestCase {

  @Test
  public void testDecodesUpstreamSamples() throws Exception {
    Path folder = samplesBase().resolve("rmqrcode-1");
    Assume.assumeTrue("zxing-cpp samples not available", Files.isDirectory(folder));

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
    Assume.assumeFalse("no sample images", expectations.isEmpty());

    List<String> decoded = new ArrayList<>();
    List<String> notDecoded = new ArrayList<>();
    List<String> wrong = new ArrayList<>();

    for (Map.Entry<Path,String> sample : expectations.entrySet()) {
      BufferedImage image;
      try {
        image = ImageIO.read(sample.getKey().toFile());
      } catch (IOException unsupported) {
        image = null;
      }
      if (image == null) {
        notDecoded.add(sample.getKey().getFileName() + " (unreadable)");
        continue;
      }
      try {
        Result result = read(image);
        if (sample.getValue().equals(result.getText())) {
          decoded.add(sample.getKey().getFileName().toString());
        } else {
          wrong.add(sample.getKey().getFileName() + ": got " + result.getText());
        }
      } catch (Exception notFound) {
        notDecoded.add(sample.getKey().getFileName().toString());
      }
    }

    System.out.println("rMQR samples decoded: " + decoded);
    System.out.println("rMQR samples not decoded: " + notDecoded);

    assertEquals("misreads", java.util.Collections.emptyList(), wrong);
    assertFalse("no sample decoded at all", decoded.isEmpty());
  }

  private static Result read(BufferedImage image) throws Exception {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    return new RMQRReader().decode(bitmap);
  }

  private static Path samplesBase() {
    String configured = System.getProperty("zxing.cpp.samples.base");
    return Paths.get(configured == null ? "upstream-zxing-cpp/test/samples" : configured);
  }
}
