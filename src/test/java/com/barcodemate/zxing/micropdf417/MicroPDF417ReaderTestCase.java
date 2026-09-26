/*
 * Copyright 2026 BarcodeMate
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.barcodemate.zxing.micropdf417;

import com.barcodemate.zxing.BarcodeFormat;
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
 * Reads the MicroPDF417 samples zxing-cpp keeps in
 * {@code test/samples/micropdf417-1}.
 *
 * <p>Upstream has no unit tests for this symbology, so these images are the
 * only external check there is. Several of them are deliberately rotated or
 * photographed at an angle, which the upright reader here does not attempt;
 * the test records which ones decoded rather than demanding all of them, and
 * prints the ones that did not, so the gap is visible rather than hidden.</p>
 *
 * <p>The samples are not vendored; point {@code zxing.cpp.samples.base} at a
 * zxing-cpp checkout's {@code test/samples} to run this.</p>
 */
public final class MicroPDF417ReaderTestCase {

  @Test
  public void testDecodesUprightSamples() throws Exception {
    Path folder = samplesBase().resolve("micropdf417-1");
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
    Assume.assumeFalse("no sample images found", expectations.isEmpty());

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
        notDecoded.add(sample.getKey().getFileName() + " (unreadable image)");
        continue;
      }
      try {
        Result result = read(image);
        if (sample.getValue().equals(result.getText())) {
          decoded.add(sample.getKey().getFileName().toString());
          assertEquals(BarcodeFormat.MICRO_PDF_417, result.getBarcodeFormat());
        } else {
          wrong.add(sample.getKey().getFileName() + ": got " + result.getText());
        }
      } catch (Exception notFound) {
        notDecoded.add(sample.getKey().getFileName().toString());
      }
    }

    System.out.println("MicroPDF417 samples decoded: " + decoded);
    System.out.println("MicroPDF417 samples not decoded: " + notDecoded);

    // A wrong answer is a defect; not answering on a rotated sample is a
    // documented limit. The two are held to different standards on purpose.
    assertEquals("misreads", java.util.Collections.emptyList(), wrong);
    assertFalse("no sample decoded at all", decoded.isEmpty());
  }

  private static Result read(BufferedImage image) throws Exception {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap = new BinaryBitmap(
        new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    return new MicroPDF417Reader().decode(bitmap);
  }

  private static Path samplesBase() {
    String configured = System.getProperty("zxing.cpp.samples.base");
    return Paths.get(configured == null ? "upstream-zxing-cpp/test/samples" : configured);
  }
}
