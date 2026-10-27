# 📦 QR Studio — component-oriented QR generation in Java

[![CI](https://github.com/f4zzie/Component-Programming-Project/actions/workflows/ci.yml/badge.svg)](https://github.com/f4zzie/Component-Programming-Project/actions/workflows/ci.yml)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)
[![Verification](https://img.shields.io/badge/verification-159%20passing-brightgreen)](src/test/java/ke/ku/sco310/qr/test/VerificationRunner.java)
[![Coursework](https://img.shields.io/badge/course-SCO%20310%20%2F%20SIT%20300-blue)](docs/Documentation.md)

**QR Studio** is a QR Code generator assembled from interchangeable JavaBeans components for
Kenyatta University's SCO 310 / SIT 300 Component Programming coursework.

The application is assembled rather than written as one monolithic program. The same component set
drives a Swing desktop application, an HTTP service and a command-line interface. Encoders,
renderers and decoders are replaceable at runtime through provided interfaces and Java's
`ServiceLoader` mechanism.

The standard QR engine is pure Java with no third-party runtime dependency. It supports numeric,
alphanumeric, byte, Kanji, ECI and optimal mixed-mode segmentation for versions 1–40. A separate
Micro QR M1–M4 encoder is exposed through its own interface. ZXing is used only by optional adapter
components and as an independent verification oracle.

**Try it in one command:**

```bash
java -jar QrStudio.jar
```

---

## Contents

- [Background](#background)
- [Features](#features)
- [Screenshots](#screenshots)
- [Component catalogue](#component-catalogue)
- [Getting started](#getting-started)
- [CLI](#cli)
- [HTTP service](#http-service)
- [Architecture and SPI discovery](#architecture-and-spi-discovery)
- [Testing and quality](#testing-and-quality)
- [Project structure](#project-structure)
- [Limitations and verification notes](#limitations-and-verification-notes)

## Background

This project demonstrates the JavaBeans component model through a working QR Code application.
Components have stable identities, provided and required interfaces, configurable properties,
bound-property events and replaceable implementations. `ComponentRegistry` assembles the selected
beans from `config/assembly.xml`; callers depend on the interfaces in `api/`, not concrete classes.

The complete coursework report is available in [`docs/Documentation.md`](docs/Documentation.md),
with submission-format copies in `docs/` and generated verification evidence kept separate from the
source tree.

## Features

### QR encoding

- ISO/IEC 18004 standard QR versions 1–40.
- Numeric, alphanumeric, byte, Kanji and UTF-8 ECI segments.
- Dynamic programming for optimal numeric/alphanumeric/byte/Kanji mixtures.
- Error-correction levels L, M, Q and H, with optional boosting.
- All eight mask patterns and Reed–Solomon error correction over GF(2⁸).
- Separate Micro QR M1–M4 encoding path with its own `QrMicroEncoder` contract.

### Renderers and decoders

- PNG, SVG and ASCII renderers.
- Rounded-module PNG rendering with configurable corner radius.
- Optional centre-logo overlay with error-correction coverage checks.
- Hand-written single-page vector PDF output with no PDF library.
- Pure-Java decoder for symbol matrices and suite-produced PNG, SVG and ASCII artifacts.
- Optional ZXing-backed decoder adapter behind the same `QrCodeDecoder` interface.

### Front ends

- Swing desktop application in `QrStudio.jar`.
- CLI commands for generation, decoding, CSV batch processing and component listing.
- HTTP endpoints for QR generation, health, history, statistics and catalogue discovery.
- HTTP payload limits, request logging, renderer reuse, JSON escaping and optional CORS.

## Screenshots

The existing coursework evidence includes the running desktop assembly, renderer replacement, HTTP
service and component architecture:

| Desktop PNG assembly | Desktop SVG assembly |
|---|---|
| [![PNG renderer](docs/screenshots/03-gui-png-assembly.png)](docs/screenshots/03-gui-png-assembly.png) | [![SVG renderer](docs/screenshots/04-gui-svg-assembly.png)](docs/screenshots/04-gui-svg-assembly.png) |
| **ASCII renderer** | **HTTP service** |
| [![ASCII renderer](docs/screenshots/05-gui-ascii-assembly.png)](docs/screenshots/05-gui-ascii-assembly.png) | [![HTTP service](docs/screenshots/15-http-service.png)](docs/screenshots/15-http-service.png) |

The architecture and verification evidence are also available as full-size images:

- [Component model](docs/diagrams/figure-1-component-model.png)
- [Verification transcript](docs/screenshots/12-verification-transcript.png)
- [Build and JAR packaging](docs/screenshots/13-build-and-package.png)
- [Component registry source](docs/screenshots/20-code-registry.png)

## Component catalogue

| Role | Component and ID | Provided contract | Dependency |
|---|---|---|---|
| Encoder | `PureJavaQrEncoderBean` · `encoder.pure-java.v1` | `QrCodeEncoder` | — |
| Encoder | `ZxingQrEncoderAdapterBean` · `encoder.zxing-adapter.v1` | `QrCodeEncoder` | Optional ZXing core |
| Micro encoder | `MicroQrEncoderBean` · `encoder.micro-qr.v1` | `QrMicroEncoder` | — |
| Renderer | `PngRendererBean` · `renderer.png.v1` | `QrCodeRenderer` | — |
| Renderer | `SvgRendererBean` · `renderer.svg.v1` | `QrCodeRenderer` | — |
| Renderer | `AsciiRendererBean` · `renderer.ascii.v1` | `QrCodeRenderer` | — |
| Renderer | `RoundedModuleRendererBean` · `renderer.rounded-png.v1` | `QrCodeRenderer` | — |
| Renderer | `PdfRendererBean` · `renderer.pdf.v1` | `QrCodeRenderer` | — |
| Decoder | `PureJavaQrDecoderBean` · `decoder.pure-java.v1` | `QrCodeDecoder` | — |
| Decoder | `ZxingQrDecoderAdapterBean` · `decoder.zxing-adapter.v1` | `QrCodeDecoder` | Optional ZXing core |
| Repository | `FileSystemRepositoryBean` · `repository.file-system.v1` | `QrCodeRepository` | File system |
| History | `QrHistoryBean` · `history.bounded-memory.v1` | Indexed/constrained bean properties | — |
| Listener | `AuditLogListenerBean` · `listener.audit-log.v1` | `QrCodeGenerationListener` | Generation events |
| Facade | `QrStudioFacadeBean` · `facade.qr-studio.v1` | Generation and rendering facade | Encoder, renderer, repository, history |

## Getting started

### Requirements

- Java 11 or newer; a JDK is required to build.
- Maven is optional. The project also builds with the JDK alone.
- ZXing 3.5.3 jars in `lib/` are used by optional adapters and verification tests.

### Build and run

```bash
# Bootstrap compile the build tool and components
mkdir -p out/classes
javac --release 11 -Xlint:all -cp lib/core-3.5.3.jar \
  -d out/classes src/main/java/ke/ku/sco310/qr/*/*.java

# Verify, compare and package
java -Djava.awt.headless=true \
  -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder all

# Launch the desktop application
java -jar QrStudio.jar
```

`ProjectBuilder all` rewrites `QrStudio.jar` and generated evidence. Use a scratch checkout when
you need to preserve existing generated artifacts. `ProjectBuilder test` is safe for normal local
development when `QR_EVIDENCE_DIR` points outside the repository:

```bash
QR_EVIDENCE_DIR=/tmp/qr-evidence \
java -Djava.awt.headless=true -cp out/classes \
  ke.ku.sco310.qr.tools.ProjectBuilder test
```

### Use the assembly directly

```java
AssemblyProfile profile = AssemblyProfile.load(new File("config/assembly.xml"));
QrAssembly assembly = new ComponentRegistry().build(profile);
QrGenerationResult result = assembly.generate(
        "https://www.ku.ac.ke/sco310", QrErrorCorrection.QUARTILE);

assembly.replaceRenderer(new SvgRendererBean());
System.out.println(result.getStoredFile());
```

## CLI

Run the CLI with the compiled classes and optional libraries on the class path:

```bash
CP="out/classes:lib/*"

# Generate an artifact
java -cp "$CP" ke.ku.sco310.qr.cli.QrCli generate \
  --text "Hello QR" --renderer svg --ec M --out hello.svg

# Decode PNG, SVG or ASCII output
java -cp "$CP" ke.ku.sco310.qr.cli.QrCli decode --in hello.svg

# Process text, filename, ec, format rows without aborting at the first error
java -cp "$CP" ke.ku.sco310.qr.cli.QrCli batch \
  --csv jobs.csv --out-dir out/batch

# Print the live ServiceLoader catalogue
java -cp "$CP" ke.ku.sco310.qr.cli.QrCli list-components
```

`generate` accepts `--text`, `--renderer`, `--ec` and `--out`. Textual output can be printed to
standard output. `batch` writes `batch-summary.txt`, reports each failed row and returns exit code
10 when one or more rows fail. Other meaningful exit codes are 2 for usage, 3 for invalid input,
4 for an operation failure and 5 for I/O errors.

## HTTP service

Start the HTTP assembly with:

```bash
java -cp "out/classes:lib/*" \
  ke.ku.sco310.qr.web.QrHttpServiceComponent \
  --port 8099 --max-payload-bytes 4096 --cors-origin '*'
```

| Endpoint | Purpose |
|---|---|
| `GET /` | HTML form and assembly summary |
| `GET /qr?text=HELLO&format=svg&ec=M` | Generate PNG, SVG, ASCII, rounded PNG or PDF output |
| `GET /components` | JSON catalogue of discovered providers |
| `GET /stats` | JSON request, generation, history and wiring statistics |
| `GET /history?limit=10` | JSON persisted generation history |
| `GET /health` | `OK` health response |

The default UTF-8 payload limit is 4096 bytes; oversized requests receive HTTP 413. Each response
produces a small request-log line. Renderer instances are cached by format and configuration,
JSON strings escape control characters and non-ASCII characters, and CORS is opt-in.

## Architecture and SPI discovery

Provider files under `src/main/resources/META-INF/services/` register encoders, renderers, decoders
and the Micro QR encoder. `ComponentRegistry` discovers them with `ServiceLoader` and selects them
by stable component ID. `ProjectBuilder` copies the resources into compiled output and packages
them into both deployable JARs.

The XML deployment shape remains backward compatible:

```xml
<component role="encoder" id="encoder.pure-java.v1"/>
<component role="renderer" id="renderer.png.v1"/>
```

The engine never imports ZXing. Standard QR behavior is checked against ZXing where supported;
Micro QR uses an in-repository reference decoder because ZXing 3.5.3 does not decode Micro QR.

## Testing and quality

```bash
# Full dependency-free project verification
java -Djava.awt.headless=true -cp out/classes \
  ke.ku.sco310.qr.tools.ProjectBuilder test

# Maven invokes the same VerificationRunner during the test phase
mvn test
```

The current suite reports **159 checks, 159 passed, 0 failed**, including:

- 160/160 ISO/IEC 18004 parameter combinations matching ZXing tables;
- standard QR payload round trips across all error-correction levels;
- Kanji, UTF-8 ECI and mixed-mode segmentation checks;
- Micro QR M1–M4 reference-decoder vectors and negative cases;
- decoder round trips for matrices, PNG, SVG and ASCII artifacts;
- renderer, PDF, logo-coverage and JavaBean contract checks;
- CLI generation, decoding, batch errors and live component listing;
- HTTP status, CORS, JSON escaping, payload-limit and renderer-cache checks;
- JDK 11-targeted `-Xlint:all` compilation and SPI-inclusive JAR packaging.

Generated evidence can be redirected with `QR_EVIDENCE_DIR`, `qr.evidence.dir` or an explicit
runner argument so CI does not dirty the source tree.

## Project structure

```text
src/main/java/ke/ku/sco310/qr/api/          provided and required interfaces
src/main/java/ke/ku/sco310/qr/components/   encoder and decoder beans
src/main/java/ke/ku/sco310/qr/engine/       dependency-free QR and Micro QR engines
src/main/java/ke/ku/sco310/qr/render/       PNG, SVG, ASCII, rounded and PDF renderers
src/main/java/ke/ku/sco310/qr/facade/       registry, profile, facade and assembly
src/main/java/ke/ku/sco310/qr/cli/          command-line front end
src/main/java/ke/ku/sco310/qr/web/          HTTP front end and JSON helper
src/main/java/ke/ku/sco310/qr/ui/           Swing desktop front end
src/main/resources/META-INF/services/      ServiceLoader provider declarations
src/test/java/ke/ku/sco310/qr/test/         custom verification harness and tests
config/assembly.xml                         backward-compatible deployment descriptor
manifest/MANIFEST.MF                        JavaBeans and application manifest
docs/                                       report, diagrams, screenshots and evidence notes
```

## Limitations and verification notes

- Micro QR is verified with the in-repository reference decoder because ZXing 3.5.3 lacks Micro QR
  support.
- The pure-Java decoder handles standard QR and artifacts produced by this suite; the ZXing decoder
  adapter accepts PNG artifacts.
- The engine has no third-party runtime dependency, while ZXing remains an optional adapter and
  test dependency.
- Logo overlays are refused when their configured square coverage exceeds the selected level's
  nominal recovery percentage, unless warning-only mode is explicitly enabled.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for component rules and the full report in
[`docs/Documentation.md`](docs/Documentation.md) for the coursework discussion.
