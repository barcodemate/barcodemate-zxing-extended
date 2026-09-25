/*
 * Copyright 2007 ZXing authors
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

package com.barcodemate.zxing;

/**
 * Enumerates barcode formats known to this package. Please keep alphabetized.
 *
 * @author Sean Owen
 */
public enum BarcodeFormat {

  /** Aztec 2D barcode format. */
  AZTEC,

  /** CODABAR 1D format. */
  CODABAR,

  /** Code 39 1D format. */
  CODE_39,

  /** Code 93 1D format. */
  CODE_93,

  /** Code 128 1D format. */
  CODE_128,

  /** Data Matrix 2D barcode format. */
  DATA_MATRIX,

  /** EAN-8 1D format. */
  EAN_8,

  /** EAN-13 1D format. */
  EAN_13,

  /** ITF (Interleaved Two of Five) 1D format. */
  ITF,

  /** MaxiCode 2D barcode format. */
  MAXICODE,

  /** PDF417 format. */
  PDF_417,

  /** QR Code 2D barcode format. */
  QR_CODE,

  /** RSS 14 */
  RSS_14,

  /** RSS EXPANDED */
  RSS_EXPANDED,

  /** UPC-A 1D format. */
  UPC_A,

  /** UPC-E 1D format. */
  UPC_E,

  /** UPC/EAN extension format. Not a stand-alone format. */
  UPC_EAN_EXTENSION,

  // ---------------------------------------------------------------------------
  // barcodemate-zxing-extended 新增：ZXing-C++ 可解码而 ZXing Java 缺失的格式。
  //
  // 这些常量一律追加在末尾，不插入上游的字母序中，以保持已有常量的 ordinal() 不变
  // （上游注释要求按字母序排列，此处刻意不遵守，理由即向后兼容）。
  //
  // 每项的实现状态见 README 的路线图表格。标注"尚未实现"的常量当前不会被任何
  // Reader 返回，把它放进 DecodeHintType.POSSIBLE_FORMATS 不会有任何效果。
  // 实现完成后此处的状态注释会同步更新。
  // ---------------------------------------------------------------------------

  /**
   * GS1 DataBar Limited, ISO/IEC 24724。上游 Java 的 DataBar 家族沿用旧称 RSS
   * （{@link #RSS_14}、{@link #RSS_EXPANDED}），此处采用规范现名。
   * <p>状态：尚未实现。
   */
  DATA_BAR_LIMITED,

  /**
   * Telepen，含 Alpha 与 Numeric 两种模式（同一解码器）。
   * <p>状态：尚未实现。
   */
  TELEPEN,

  /**
   * DX Film Edge，135 胶片边缘上的 DX 条码。
   * <p>状态：尚未实现。
   */
  DX_FILM_EDGE,

  /**
   * Compact PDF417（右侧行指示符被截断的 PDF417 变体）。
   * <p>状态：尚未实现。
   */
  COMPACT_PDF_417,

  /**
   * MicroPDF417, ISO/IEC 24728。使用 RAP（Row Address Patterns）寻址，
   * 不是"缩小版 PDF417"。
   * <p>状态：尚未实现。
   */
  MICRO_PDF_417,

  /**
   * Aztec Rune，11×11 的定长 Aztec 变体。
   * <p>状态：尚未实现。
   */
  AZTEC_RUNE,

  /**
   * QR Code Model 1，{@link #QR_CODE}（Model 2）之前的初版 QR。
   * <p>状态：尚未实现。注意 ZXing-C++ 亦无法生成此格式，测试图片需另行取得。
   */
  QR_CODE_MODEL_1,

  /**
   * Micro QR Code, ISO/IEC 18004 附录。
   * <p>状态：尚未实现。
   */
  MICRO_QR_CODE,

  /**
   * rMQR Code（矩形微型 QR）, ISO/IEC 23941。
   * <p>状态：尚未实现。
   */
  RMQR_CODE

}
