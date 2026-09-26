# barcodemate-zxing-extended

一个纯 Java 的条码**解码**库：以 ZXing for Java 的最后一个版本为基线，补齐 ZXing-C++ 能解、ZXing Java 不能解的那些码制。

由 [barcodemate.com](https://barcodemate.com) 发布。与上游 ZXing 项目无隶属关系。

English: [README.md](README.md)

---

## 这个项目为什么存在

ZXing for Java 已经停止扩展新码制。其 README 原文：

> The project is in maintenance mode, meaning, changes are driven by contributed patches. Only bug fixes and minor enhancements will be considered. […] There is otherwise no active development or roadmap for this project. It is "DIY".

而 [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) 仍在活跃开发，支持的码制早已超出 Java 版。于是出现了一个缺口：一个需要 Micro QR、rMQR、MicroPDF417、Telepen 等码制的 Java 项目，没有纯 Java 的选择。通过 JNI/JNA 调用 C++ 库是可行的，但会带来 native 编译依赖、更大的镜像体积，而且 native 代码崩溃会直接带走整个 JVM。

本项目用纯 Java 补上这个缺口，无任何 native 依赖。

## 它到底是什么

- **基线**：ZXing for Java `zxing-3.5.4`（2025-11-11，`f651b0a`）的派生作品，Apache-2.0。
- **包名重定位**：`com.google.zxing` → `com.barcodemate.zxing`。这样本 artifact 可以与官方的 `com.google.zxing:core` 共存于同一依赖树而不冲突。除包名外，上游代码逻辑未作改动。
- **是超集，不是插件**：新增格式是一等的 `BarcodeFormat` 常量，`MultiFormatReader` 与 `DecodeHintType.POSSIBLE_FORMATS` 对它们的用法与 `QR_CODE` 完全一致。
- **只做解码**。生成暂不在范围内，原因见[为什么不做编码器](#为什么不做编码器)。
- **算法移植自 zxing-cpp** `v3.1.1`（2026-07-29，`287c85d`），Apache-2.0，逐个算法移植。

## 格式支持情况

读＝可解码，写＝可生成。ZXing-C++ 一列区分**原生** writer 与转调 [Zint](https://github.com/zint/zint) 生成。

### 二维码

| 码制 | ZXing Java 3.5.4 | ZXing-C++ 3.1.1 | 本项目 |
|---|:-:|:-:|:-:|
| PDF417 | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| Compact PDF417 | **可读**（报为 PDF417） | 读（报为 PDF417） | **可读**（报为 PDF417） |
| **MicroPDF417** | — | 读（写：zint） | **实验性**（仅正置图像） |
| Aztec | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| **Aztec Rune** | — | 读（写：zint） | **已支持解码** |
| QR Code（Model 2） | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| **QR Code Model 1** | — | 读（无法生成） | **已支持解码**（单张样例验证） |
| **Micro QR Code** | — | 读（写：zint） | **已支持解码**（正置图像） |
| **rMQR Code** | — | 读（写：zint） | **已支持解码**（正置图像） |
| Data Matrix（含 DMRE） | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| MaxiCode | 读 | 读（写：zint） | 读 |

### 一维码

| 码制 | ZXing Java 3.5.4 | ZXing-C++ 3.1.1 | 本项目 |
|---|:-:|:-:|:-:|
| Codabar、Code 39、Code 93、Code 128、ITF | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| Code 39 Extended | 读（解码器选项，无格式常量） | 读（有独立常量） | 读 + 独立常量（*计划中*） |
| Code 32（意大利药品码） | — | 读（写：zint） | **计划中**（解释层） |
| PZN（德国药品码） | — | 读（写：zint） | **计划中**（解释层） |
| ITF-14 | 读（按普通 ITF） | 读（有独立常量） | 读 + 独立常量（*计划中*） |
| EAN-13、EAN-8、UPC-A、UPC-E | 读 + 写 | 读 + 写（原生） | 读 + 写 |
| EAN-2 / EAN-5 | 读（`UPC_EAN_EXTENSION`） | 不可单独解码 | 读 |
| ISBN | 读（按 EAN-13） | 读（有独立常量） | 读 + 独立常量（*计划中*） |
| DataBar / Omni（RSS-14） | 读 | 读（写：zint） | 读 |
| DataBar Stacked / Stacked Omni | 读（*声明支持，待实测核对*） | 读（写：zint） | 读 |
| DataBar Expanded / Expanded Stacked | 读 | 读（写：zint） | 读 |
| **DataBar Limited** | — | 读（写：zint） | **已支持解码** |
| **Telepen / Alpha / Numeric** | — | 读（写：zint） | **已支持解码** |
| **DX Film Edge** | — | 读（写：zint） | **已支持解码** |

有两点必须单独说明，因为只对比两边的 `BarcodeFormat` 枚举会得出错误结论：

- **DMRE**（ISO/IEC 21471）**两边都支持**。它在两边都归属于 `DATA_MATRIX` 常量内部，所以不会表现为枚举差异。
- **API 层能力**——多码读取、pure/fast/slow 档位、ECI、GS1 语义、Structured Append、误读控制——枚举里完全不体现。本项目不试图补齐这些，只补格式缺口。

## 路线图

按"共用地基"排序，而不是按码制价值：下表所有二维码都依赖同一套几何层，所以它先做、只做一次。

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 重定位后的基线、许可与出处声明、`BarcodeFormat` 常量、构建与测试骨架 | **已完成** |
| 1 | Telepen，全 ASCII 与压缩数字两种模式 | **已完成** |
| 2 | DataBar Limited | **已完成** |
| 3 | DX Film Edge | **已完成** |
| 4 | 从 zxing-cpp 移植几何层（`Pattern`、`BitMatrixCursor`、`RegressionLine`、`ConcentricFinder`、`GridSampler`、`Quadrilateral`） | 进行中 |
| 5 | **rMQR** | **已完成**（正置图像；旋转与透视待补） |
| 6 | **Micro QR** | **已完成**（正置图像） |
| 7 | **QR Code Model 1**、**Aztec Rune** | **已完成** |
| 8 | **MicroPDF417**（实验性）；Compact PDF417 无需实现 | **已完成** |
| 9 | Code 32、PZN、ITF-14、ISBN、Code 39 Extended 独立常量 —— 标识层，非解码器 | 进行中 |

三个一维格式先做，是因为它们都不需要 2D 几何层——ZXing 现成的 `OneDReader`
行扫描框架直接就能承载。其余二维格式都卡在阶段 4，而那是约 1500 行本身不解码
任何东西的基础设施；先交付能用的解码器，比严格按依赖顺序推进更有价值。

与 Telepen 不同，**DataBar Limited 在默认扫描集里**，和上游已经默认扫描的其他
DataBar 变体并列。它的结构约束强得多——guard 比例、89 项校验字符表、两个数据
字符之间的 mod-89 交叉校验、以及 GS1 校验位必须同时成立——而且把它加进默认集
后，上游整个 blackbox 语料（含 falsepositives 套件）没有出现任何误读。

DX Film Edge 和 Telepen 一样按需启用，但理由不同：它是这里唯一跨扫描行带状态的
reader。一个符号由**不同行**上的两条轨道组成——确定模块尺寸的时钟轨，和它旁边的
数据轨——没看到时钟轨就读不了数据轨。这份状态的作用域被限制在一次 `decode()` 调用
内，调用前后都会清空，不会泄漏到下一张图。

尚未实现格式的 `BarcodeFormat` 常量已经存在，以便下游代码和本项目内部能针对稳定 API 编译。**标注"计划中"的常量不会被任何 Reader 返回**——现在把它放进 `POSSIBLE_FORMATS` 不会有任何效果。每个常量的 Javadoc 都写明了当前状态。

### 一处更正：Compact PDF417 从来不是缺口

上面的格式对比表最初把 Compact PDF417 列为"C++ 能读、Java 不能"，依据是 C++ 侧有
一个 Java 没有的 `CompactPDF417` 常量。这个判断错了两层。

**zxing-cpp 的 PDF417 reader 从不返回那个常量**——它对 compact 符号一律报 `PDF417`；
那个常量只用于"请求该 reader"和作为生成目标。**而继承来的 Java reader 本来就能解
compact 符号**，`CompactPDF417TestCase` 用 `pdf417compact` 生成的符号证明了这一点。

所以这里没有任何东西需要实现。这处更正是明写出来而不是悄悄改掉的，因为
"**枚举差异看起来像能力差异**"正是本项目一开始就想避开的陷阱——而它也确实把本项目
坑了一次。

### MicroPDF417 是实验性的

这个标注是刻意的，含义如下。

这里的其他每个格式都有外部依据：移植的上游单元测试，或能解出预期文本的样例图。
MicroPDF417 两样都严重不足——上游对它**没有任何单元测试**，而它的十张样例图里这个
reader 能解出三张（正置的那些）。另外七张是旋转的、斜拍的，或者需要 zxing-cpp 有
而这里还没有的通用扫描器。

这三张能证明的是：表、行地址、码字读取和纠错都是对的——一个符号不会碰巧解出它的
预期文本。它们**不能**证明的是鲁棒性。十张里没有出现任何误读，这一点比"解出三张"
更重要：**拒绝作答是局限，答错才是缺陷**。

在通用扫描器落地之前，请把它当作面向生成图像和裁剪图的 reader，而不是面向照片的。

### 使用 Telepen

Telepen 必须显式请求，它不在默认扫描集里：

```java
Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.TELEPEN));
Result result = new MultiFormatReader().decode(bitmap, hints);
```

它的起始图形是十个窄元素，特征偏弱；无条件参与扫描会抬高其他所有格式的误读率。
在用 falsepositives 测试集实测之前，它保持按需启用。请求 `TELEPEN` 表示两种数据
模式都接受，也可以用 `TELEPEN_ALPHA` / `TELEPEN_NUMERIC` 只接受其中一种。识别结果
的 AIM 模式记录在 `ResultMetadataType.SYMBOLOGY_IDENTIFIER` 里（`]B0` 到 `]B4`）。

## 正确性是怎么建立的

没有任何格式会仅凭"我的测试图能解出来"就宣布支持。

1. **上游单元测试有则必移植。** zxing-cpp 的解码器测试用的是 ASCII 位图字面量而非图片，因此可以直接译成 JUnit 且与平台无关。Micro QR（`MQRDecoderTest`，124 行）、rMQR（`RMQRDecoderTest`，206 行，9 个用例覆盖 R7x43M 到 R17x99H，含一个刻意的 6 比特错误、ECI 与 GS1）、Telepen（`ODTelepenReaderTest`，150 行）和 Code 39 Extended 都有。
2. **对齐上游 blackbox 期望值，不追求超过。** 以 zxing-cpp 自己的通过阈值为目标，包括 0°/90°/180°/270° 加 pure 模式的旋转矩阵，以及误读上限——误读比漏读更严重。作为参照：上游自己对 rMQR 在 fast 档只要求 3 张过 2 张，MicroPDF417 在 90°/270° 的 fast 档要求是 **10 张过 0 张**。"100% 一致"是不成立的说法，本项目不会这样声称。
3. **本地自建合成图集**：用 [bwip-js](https://github.com/metafloor/bwip-js) 覆盖每种合法规格、纠错等级、旋转、倾斜、反色、噪声与分辨率，再以 [zxing-wasm](https://github.com/Sec-ant/zxing-wasm) 作黄金参考跑 A/B 比对。
4. **完全没有上游测试资产的格式**（Compact PDF417、Aztec Rune、QR Code Model 1）必须先自建码字级测试才能声明支持，否则以实验性标注发布。
5. 上游 ZXing Java 的完整测试套件（561 个测试，含全部 59 个 blackbox 套件）在重定位后的基线上通过，并作为回归网持续保持绿色。

软件解码成功不等于任何手机摄像头或扫描枪能读出实物标签，也不是印刷质量认证。

## 为什么不做编码器

首批版本刻意不做生成，理由是事实而非偏好：**zxing-cpp 自己也没有实现这些格式的编码器。** 它们全部是转调 Zint（`core/src/libzint/`，BSD-3-Clause）生成的，而本项目不移植该目录——那是纯生成用代码，混入只会同时混淆许可与出处，且没有收益。日后若增加 Writer，将按各自规范自行实现（rMQR 依 ISO/IEC 23941，MicroPDF417 依 ISO/IEC 24728，等等）。

## 范围与边界

提 issue 前请先读这一节：

- **本项目维护的是新增的那些格式**，不承担 ZXing 自身的历史缺陷。能在官方 `com.google.zxing:core` 3.5.4 上复现的缺陷属于上游，不属于这里——除非它阻塞了本项目新增的某个格式，那种情况下会在这里修并记录说明。
- 基线是锁定的。不承诺跟随 zxing-cpp 的 master；任何重新同步都是一次刻意的、带版本号的变更。
- zxing-cpp 有而 ZXing Java 没有的 API 层特性，不在范围内。

## 用法

```xml
<dependency>
  <groupId>com.barcodemate</groupId>
  <artifactId>zxing-extended</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

API 就是 ZXing 的 API，只是包名不同：

```java
import com.barcodemate.zxing.*;
import com.barcodemate.zxing.common.HybridBinarizer;

int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
LuminanceSource source = new RGBLuminanceSource(w, h, pixels);
BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
Result result = new MultiFormatReader().decode(bitmap);
```

迁移已有 ZXing 代码只需一次全局替换：`com.google.zxing` → `com.barcodemate.zxing`。

这里只 fork 了 ZXing 的 `core` 模块。上游的 `javase`、`android`、`android-core` 辅助模块未纳入；`core` 里的 `RGBLuminanceSource` 与 `PlanarYUVLuminanceSource` 能覆盖大部分接入需求，而且由于包名不冲突，官方辅助模块仍可与本 artifact 一起使用。

## 构建

```bash
mvn test                                             # 561 个上游测试；图片类套件自动跳过
mvn test -Dzxing.blackbox.base=/path/to/zxing/core   # 连 blackbox 套件一起跑
```

上游 blackbox 图片集（约 132 MB）不随本仓库分发。把 `zxing.blackbox.base` 指向一份 ZXing 检出的 `core` 目录即可运行这些套件；不指向时它们通过 JUnit assumption 跳过，而不是悄悄算作通过。

使用需 JDK 8 及以上（以 `--release 8` 构建，与上游门槛一致）；构建需 JDK 17 及以上。

## 许可与出处

Apache License 2.0——与两个上游项目相同。见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

所有源自上游的文件都保留其原始版权头与 SPDX 标识。移植自 zxing-cpp 的文件会标注其原作者（Nu-book Inc.、ZXing authors、Axel Waggershauser、gitlost 等）。本项目建立在 ZXing 作者们与 zxing-cpp 维护者的工作之上；移植中的任何缺陷由本项目自己负责。
