# barcodemate-zxing-extended

A pure-Java barcode **decoding** library that starts from the final release of ZXing for Java and adds the symbologies that ZXing-C++ can read but ZXing for Java cannot.

Published by [barcodemate.com](https://barcodemate.com). Not affiliated with the upstream ZXing project.

中文说明：[README.zh-CN.md](README.zh-CN.md)

---

## Why this project exists

ZXing for Java has stopped taking new symbologies. From its own README:

> The project is in maintenance mode, meaning, changes are driven by contributed patches. Only bug fixes and minor enhancements will be considered. […] There is otherwise no active development or roadmap for this project. It is "DIY".

Meanwhile [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) is actively developed and has grown well past the Java feature set. The result is a gap: a Java project that needs Micro QR, rMQR, MicroPDF417, Telepen and a handful of other symbologies has no pure-Java option. JNI/JNA bindings to the C++ library exist, but they bring native build dependencies, larger images, and a crash in native code takes down the JVM.

This project closes that gap in pure Java, with no native dependencies.

## What it actually is

- **Baseline**: a derivative of ZXing for Java `zxing-3.5.4` (2025-11-11, `f651b0a`), Apache-2.0.
- **Package relocation**: `com.google.zxing` → `com.barcodemate.zxing`, so this artifact can coexist with the official `com.google.zxing:core` in the same dependency tree without conflict. Apart from the package name, upstream logic is unmodified.
- **A superset, not a plugin**: new formats are first-class `BarcodeFormat` constants, so `MultiFormatReader` and `DecodeHintType.POSSIBLE_FORMATS` work with them the same way they work with `QR_CODE`.
- **Decoding only**. Generation is out of scope for now — see [Why no encoders](#why-no-encoders).
- **Ported from zxing-cpp** `v3.1.1` (2026-07-29, `287c85d`), Apache-2.0, algorithm by algorithm.

## Format support

Read = can decode, Write = can generate. The "ZXing-C++" column distinguishes native writers from generation delegated to [Zint](https://github.com/zint/zint).

### 2D

| Symbology | ZXing Java 3.5.4 | ZXing-C++ 3.1.1 | This project |
|---|:-:|:-:|:-:|
| PDF417 | read + write | read + write (native) | read + write |
| Compact PDF417 | **read** *(as PDF417)* | read *(as PDF417)* | **read** *(as PDF417)* |
| **MicroPDF417** | — | read (write: zint) | **read** *(experimental, upright only)* |
| Aztec | read + write | read + write (native) | read + write |
| **Aztec Rune** | — | read (write: zint) | **read** |
| QR Code (Model 2) | read + write | read + write (native) | read + write |
| **QR Code Model 1** | — | read (cannot write) | **read** *(one sample verified)* |
| **Micro QR Code** | — | read (write: zint) | **read** |
| **rMQR Code** | — | read (write: zint) | **read** |
| Data Matrix (incl. DMRE) | read + write | read + write (native) | read + write |
| MaxiCode | read | read (write: zint) | read |

### 1D

| Symbology | ZXing Java 3.5.4 | ZXing-C++ 3.1.1 | This project |
|---|:-:|:-:|:-:|
| Codabar, Code 39, Code 93, Code 128, ITF | read + write | read + write (native) | read + write |
| **Code 39 Extended** | read (reader option, no format constant) | read (own constant) | **read + own constant** |
| **Code 32 (Italian pharmacode)** | — | read (write: zint) | **read** |
| **PZN (German pharmacode)** | — | read (write: zint) | **read** |
| ITF-14 | read *(as ITF)* | read *(as ITF)* | read *(as ITF)* |
| EAN-13, EAN-8, UPC-A, UPC-E | read + write | read + write (native) | read + write |
| EAN-2 / EAN-5 | read (`UPC_EAN_EXTENSION`) | not readable standalone | read |
| ISBN | read *(as EAN-13)* | read *(as EAN-13)* | read *(as EAN-13)* |
| DataBar / Omni (RSS-14) | read | read (write: zint) | read |
| DataBar Stacked / Stacked Omni | read *(claimed; being verified)* | read (write: zint) | read |
| DataBar Expanded / Expanded Stacked | read | read (write: zint) | read |
| **DataBar Limited** | — | read (write: zint) | **read** |
| **Telepen / Alpha / Numeric** | — | read (write: zint) | **read** |
| **DX Film Edge** | — | read (write: zint) | **read** |

Two differences worth calling out, because comparing the two `BarcodeFormat` enums alone will mislead you:

- **DMRE** (ISO/IEC 21471) is supported by *both* sides. It lives inside the `DATA_MATRIX` constant on both, so it does not show up as an enum difference.
- **API-level capabilities** — multi-barcode reads, pure/fast/slow tiers, ECI, GS1 semantics, structured append, misread control — are not expressed in the enums at all. This project does not attempt to close those; it closes the format gap only.

## Roadmap

Ordered by shared foundation, not by perceived value: every 2D format below depends on the same geometry layer, so it is built once, first.

| Stage | Contents | Status |
|---|---|---|
| 0 | Relocated baseline, license/attribution, `BarcodeFormat` constants, build & test harness | **done** |
| 1 | Telepen, full ASCII and compressed numeric | **done** |
| 2 | DataBar Limited | **done** |
| 3 | DX Film Edge | **done** |
| 4 | Geometry layer ported from zxing-cpp (`Pattern`, `BitMatrixCursor`, `RegressionLine`, `ConcentricFinder`, `GridSampler`, `Quadrilateral`) | next |
| 5 | **rMQR** | **done** (upright images; rotation and perspective to follow) |
| 6 | **Micro QR** | **done** (upright images) |
| 7 | **QR Code Model 1**, **Aztec Rune** | **done** |
| 8 | **MicroPDF417** (experimental); Compact PDF417 needed nothing | **done** |
| 9 | Code 32, PZN, ITF-14, ISBN, Code 39 Extended constant -- identification layers, not decoders | next |

The three 1D formats came first because none of them needs the 2D geometry
layer: ZXing's existing `OneDReader` row scanning hosts them directly. Every
remaining 2D format waits on stage 4, which is ~1500 lines of infrastructure
that decodes nothing on its own, so shipping working decoders before that
stretch seemed worth more than strict dependency order.

Unlike Telepen, **DataBar Limited is in the default scan set**, alongside the
DataBar variants upstream already scans for. Its structure is far more
constrained -- guard proportions, an 89 entry check character table, a mod 89
cross-check between the two data characters and a GS1 check digit all have to
agree -- and adding it caused no misreads across the upstream blackbox
corpus, including the falsepositives suites.

DX Film Edge is opt-in like Telepen, for a different reason: alone among the
readers here it carries state between scan lines. A symbol is two tracks on
*different* rows -- a clock track that establishes the module size, and the
data track beside it -- so the data cannot be read until the clock next to it
has been seen. That state is scoped to a single `decode()` call and cleared
before and after it, so nothing leaks into the next image.

The `BarcodeFormat` constants for unimplemented formats already exist so that downstream code and this project's own internals can compile against a stable API. **A constant marked "planned" is never returned by any reader** — putting it in `POSSIBLE_FORMATS` currently has no effect. Each constant's Javadoc states its status.

### A correction: Compact PDF417 was never a gap

The format comparison above originally listed Compact PDF417 as something
ZXing-C++ reads and ZXing for Java does not, on the strength of the C++ side
having a `CompactPDF417` constant that Java lacks. That was wrong twice over.

ZXing-C++'s PDF417 reader never returns that constant. It reports `PDF417` for
compact symbols like any other; the constant exists to request the reader and
to name a generation target. And the inherited Java reader decodes compact
symbols perfectly well, which `CompactPDF417TestCase` now demonstrates against
symbols generated as `pdf417compact`.

ITF-14 and ISBN turned out the same way, and were found the same way: both
appear only in ZXing-C++'s format enum, never in anything a reader returns.
Its ITF reader reports `ITF` and its EAN/UPC reader reports `EAN13`, exactly as
the Java ones do.

So three of the nine entries in the original comparison were not gaps at all.
The tables are corrected rather than quietly adjusted, because an enum
difference that looks like a capability difference is exactly the trap this
project set out to avoid, and it caught this project three times.

### MicroPDF417 is experimental

It is marked so deliberately, and this is what that means.

Every other format here rests on something external: ported upstream unit
tests, or sample images that decode to expected text. MicroPDF417 has neither
in any quantity. Upstream ships no unit tests for it at all, and of its ten
sample images this reader decodes three -- the upright ones. The other seven
are rotated, photographed at an angle, or otherwise need the general scanner
that zxing-cpp has and this does not yet.

What the three do establish is that the tables, the row addressing, the
codeword reading and the error correction are right, because a symbol does not
decode to its expected text by accident. What they do not establish is
robustness. There are no misreads across the ten, which matters more than the
three: refusing to answer is a limitation, answering wrongly is a defect.

Treat it as a reader for generated images and crops, not for photographs, until
the general scanner lands.

### Using Telepen

Telepen must be requested explicitly; it is not in the default scan set:

```java
Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.TELEPEN));
Result result = new MultiFormatReader().decode(bitmap, hints);
```

Its start pattern is ten narrow elements, which is weak enough that scanning
for it unconditionally would raise the misread rate of every other format.
That stays opt-in until it has been measured against the falsepositives
suites. Request `TELEPEN` for either data mode, or `TELEPEN_ALPHA` /
`TELEPEN_NUMERIC` to accept only one. Results carry the AIM mode in
`ResultMetadataType.SYMBOLOGY_IDENTIFIER` (`]B0` through `]B4`).

## How correctness is established

No format is declared supported on the strength of "it decoded my test image".

1. **Upstream unit tests are ported where they exist.** zxing-cpp's decoder tests use ASCII bit-matrix literals, not images, so they translate to JUnit directly and are platform-independent. Available for Micro QR (`MQRDecoderTest`, 124 lines), rMQR (`RMQRDecoderTest`, 206 lines, 9 cases covering R7x43M through R17x99H plus a deliberate 6-bit error, ECI and GS1), Telepen (`ODTelepenReaderTest`, 150 lines) and Code 39 Extended.
2. **Upstream blackbox expectations are matched, not exceeded.** zxing-cpp's own pass thresholds are the target, including the rotation matrix (0°/90°/180°/270° plus pure mode) and the misread ceiling — a misread is worse than a miss. For reference, upstream itself requires only 2 of 3 rMQR images in fast mode, and **0 of 10** MicroPDF417 images at 90°/270° in fast mode. Claims of "100% parity" would be false and are not made.
3. **A synthetic corpus is generated locally** with [bwip-js](https://github.com/metafloor/bwip-js) across every valid symbol size, error-correction level, rotation, skew, inversion, noise level and resolution; results are diffed A/B against [zxing-wasm](https://github.com/Sec-ant/zxing-wasm) as the golden reference.
4. **Formats with no upstream test assets at all** (Compact PDF417, Aztec Rune, QR Code Model 1) require hand-built codeword-level tests before being declared supported; otherwise they ship marked experimental.
5. The full upstream ZXing Java test suite (561 tests, including all 59 blackbox suites) passes against the relocated baseline and is kept green as a regression net.

Decoding successfully in software is not a claim that any given phone camera or scanner will read a printed symbol, nor a print-quality certification.

## Why no encoders

Generation is deliberately out of scope for the first releases, for a concrete reason rather than a preference: **zxing-cpp does not implement encoders for any of these formats either.** Every one of them is generated by delegating to Zint (`core/src/libzint/`, BSD-3-Clause), which this project does not port — it is generation-only code, and mixing it in would mix licenses and provenance for no benefit. Writers, if added later, will be implemented from the specifications (ISO/IEC 23941 for rMQR, ISO/IEC 24728 for MicroPDF417, and so on).

## Scope and boundaries

Please read this before filing an issue:

- **This project maintains the added formats.** It does not take on ZXing's own historical bugs. A defect reproducible against official `com.google.zxing:core` 3.5.4 belongs upstream, not here — though if it blocks a format this project adds, it will be fixed here and documented.
- The baseline is pinned. Following zxing-cpp's `master` is not promised; any resync is a deliberate, versioned change.
- API-level features of zxing-cpp not present in ZXing Java are not in scope.

## Usage

```xml
<dependency>
  <groupId>com.barcodemate</groupId>
  <artifactId>zxing-extended</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The API is ZXing's, under a different package name:

```java
import com.barcodemate.zxing.*;
import com.barcodemate.zxing.common.HybridBinarizer;

int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
LuminanceSource source = new RGBLuminanceSource(w, h, pixels);
BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
Result result = new MultiFormatReader().decode(bitmap);
```

Migrating existing ZXing code is a one-time find-and-replace of `com.google.zxing` with `com.barcodemate.zxing`.

Only ZXing's `core` module is forked here. The upstream `javase`, `android` and `android-core` helper modules are not included; `RGBLuminanceSource` and `PlanarYUVLuminanceSource` in `core` cover most integration needs, and the official helper modules can still be used alongside this artifact since the package names do not clash.

## Building

```bash
mvn test                                        # 561 upstream tests; image-based suites skip
mvn test -Dzxing.blackbox.base=/path/to/zxing/core   # include the blackbox suites
```

The upstream blackbox image corpus (~132 MB) is not vendored here. Point `zxing.blackbox.base` at a ZXing checkout's `core` directory to run those suites; without it they are skipped via JUnit assumptions rather than silently passing.

Requires JDK 8 or later to consume (built with `--release 8`, matching upstream's floor); JDK 17+ to build.

## License and attribution

Apache License 2.0 — the same license as both upstream projects. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Every file derived from upstream keeps its original copyright headers and SPDX identifiers. Files ported from zxing-cpp credit their original authors (Nu-book Inc., ZXing authors, Axel Waggershauser, gitlost and others). The ZXing authors and the zxing-cpp maintainers did the work this project builds on; any defect in the port is this project's own.
