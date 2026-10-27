# Contributing to the QR component suite

## Component rules

Every new component must be a public JavaBean with a public no-argument constructor, implement
`Serializable`, expose a stable `COMPONENT_ID` such as `renderer.pdf.v1`, and provide normal
getter/setter properties. Configuration properties are bound through `BoundPropertySource` when the
surrounding components use that pattern. Components communicate through the interfaces in
`src/main/java/ke/ku/sco310/qr/api/`; callers must not depend on a concrete implementation.

The engine in `engine/` remains dependency-free. ZXing is allowed only in its optional adapter and
as an independent verification oracle. Target Java 11 with `--release 11`, avoid new runtime
dependencies, use explicit imports, and add Javadoc with pre-conditions, post-conditions and
failure conditions to public contracts.

## Verification

Use the in-house harness rather than JUnit:

```bash
javac --release 11 -Xlint:all -cp lib/core-3.5.3.jar \
  -d out/classes src/main/java/ke/ku/sco310/qr/*/*.java
java -Djava.awt.headless=true -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder test
mvn test
```

Add meaningful checks to the existing test classes or register a new class in
`VerificationRunner`. Include the successful user flow and each new invalid or failure state.
ZXing is the independent oracle for standard QR features; Micro QR uses the in-repository
reference decoder because ZXing 3.5.3 does not support Micro QR. Keep generated evidence outside
the working tree with `QR_EVIDENCE_DIR` when running locally or in CI.

## SPI providers

Add providers to the appropriate file under `src/main/resources/META-INF/services/`. Keep the
existing component ID and assembly XML role names compatible. Confirm `ProjectBuilder jar` includes
the service descriptors, and check the provider through `ComponentRegistry.listComponents()`.

## Review checklist

- the engine has no third-party imports;
- the component is discoverable and has a catalogue entry;
- properties validate bad values and fire bound-property events where applicable;
- tests exercise the real artifact or endpoint and its negative path;
- `-Xlint:all`, `ProjectBuilder test`, and `mvn test` pass on Java 11.
