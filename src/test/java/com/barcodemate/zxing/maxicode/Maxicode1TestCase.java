/*
 * Copyright 2016 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.barcodemate.zxing.maxicode;

import com.barcodemate.zxing.BarcodeFormat;
import com.barcodemate.zxing.MultiFormatReader;
import com.barcodemate.zxing.common.AbstractBlackBoxTestCase;

/**
 * Tests {@link MaxiCodeReader} against a fixed set of test images.
 */
public final class Maxicode1TestCase extends AbstractBlackBoxTestCase {

  public Maxicode1TestCase() {
    super("src/test/resources/blackbox/maxicode-1", new MultiFormatReader(), BarcodeFormat.MAXICODE);
    addTest(9, 9, 0.0f);
  }

}
