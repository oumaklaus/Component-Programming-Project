# Automated verification results

| Group | Check | Outcome | Detail |
|---|---|---|---|
| engine | ISO 18004 tables match ZXing for 40 versions x 4 levels | PASS | `0 mismatching combinations ` |
| engine | payloads survive the ZXing decode round trip (15 x 4) | PASS | `60 decoded, 0 failed` |
| engine | numeric payload selects numeric mode | PASS | `expected=Numeric actual=Numeric` |
| engine | upper-case payload selects alphanumeric mode | PASS | `expected=Alphanumeric actual=Alphanumeric` |
| engine | mixed case payload selects byte mode | PASS | `expected=Byte actual=Byte` |
| engine | compact mode lowers the symbol version | PASS | `numeric v1 vs alphanumeric v1` |
| engine | all-Kanji payloads round trip through the ZXing decoder | PASS | `3 decoded, 0 failed` |
| engine | all-Kanji payload selects the kanji mode | PASS | `expected=Kanji actual=Kanji` |
| engine | non-ASCII byte payload is flagged with a UTF-8 ECI segment | PASS | `Byte round trip true` |
| engine | a mixed ASCII+Japanese payload decodes as UTF-8 | PASS | `expected=éèê café naïve résumé 漢字 actual=éèê café naïve résumé 漢字` |
| engine | mixed-mode segmentation does not exceed a single byte segment | PASS | `mixed v2 vs byte v2` |
| engine | mixed-mode payloads round trip through the ZXing decoder | PASS | `6 decoded, 0 failed` |
| engine | full capacity round trip for all 40 versions x 4 levels | PASS | `160 decoded, 0 failed ` |
| engine | encoding is deterministic for equal input | PASS | `expected=QrCode{version=2, size=25, ec=Q, mode=Byte, mask=0, chars=17, dataCodewords=22, eccCodewords=22, producer='encoder.pure-java.v1'} actual=QrCode{version=2, size=25, ec=Q, mode=Byte, mask=0, chars=17, dataCodewords=22, eccCodewords=22, producer='encoder.pure-java.v1'}` |
| engine | every forced mask pattern still decodes (0..7) | PASS | `8/8 masks decoded, masks used [0, 1, 2, 3, 4, 5, 6, 7]` |
| engine | forced mask is reported in the symbol | PASS | `expected=5 actual=5` |
| engine | encoder publishes its capability metadata | PASS | `SCO310 in-house QR engine 1.0.0 [versions 1-40, modes [Numeric, Alphanumeric, Byte, Kanji, Eci], in-house]` |
| engine | maximum byte capacity reported for level L | PASS | `expected=2953 actual=2953` |
| engine | empty payload is rejected | PASS | `rejected as expected: QrEncodingException: The payload is empty; a QR Code must carry at least one byte` |
| engine | null payload is rejected | PASS | `rejected as expected: QrEncodingException: The payload is empty; a QR Code must carry at least one byte` |
| engine | over-capacity payload is rejected with a clear error | PASS | `rejected as expected: QrEncodingException: Payload of 200 characters does not fit version 1 at level H (needs 1113 bits, capacity 72 bits). Shorten the payload or lower the error-correction level.` |
| engine | unknown error-correction label is rejected | PASS | `rejected as expected: IllegalArgumentException: Unknown error-correction level 'Z'. Expected one of L, M, Q, H` |
| engine | invalid forced mask is rejected | PASS | `rejected as expected: IllegalArgumentException: forcedMaskPattern must be -1 or 0..7` |
| engine | constrained minimumVersion vetoes an inconsistent window | PASS | `rejected as expected: PropertyVetoException: minimumVersion 9 would exceed maximumVersion 5` |
| micro | M1 uses the compact numeric matrix | PASS | `expected=11 actual=11` |
| micro | M1 has detection-only error correction | PASS | `expected=null actual=null` |
| micro | M1 reference decoder recovers the payload | PASS | `expected=123 actual=123` |
| micro | M2 reference decoder recovers alphanumeric data | PASS | `expected=HELLO actual=HELLO` |
| micro | M3 reference decoder recovers byte data | PASS | `expected=abc-123 actual=abc-123` |
| micro | M4 reference decoder recovers Kanji data | PASS | `expected=漢字 actual=漢字` |
| micro | all four Micro mask patterns pass the reference decoder | PASS | `4/4 masks decoded` |
| micro | HIGH error correction is rejected | PASS | `rejected as expected: QrEncodingException: Micro QR supports requested levels L, M and Q only` |
| micro | a non-numeric payload is rejected by M1 | PASS | `rejected as expected: QrEncodingException: Payload does not fit M1..M1 at level L` |
| extensions.decoder | PureJavaQrDecoderBean is a serializable bound JavaBean | PASS | `public=true noArg=true` |
| extensions.renderer | RoundedModuleRendererBean is a serializable bound JavaBean | PASS | `public=true noArg=true` |
| extensions.renderer | PdfRendererBean is a serializable bound JavaBean | PASS | `public=true noArg=true` |
| extensions.encoder | MicroQrEncoderBean is a serializable bound JavaBean | PASS | `public=true noArg=true` |
| extensions.decoder | ZxingQrDecoderAdapterBean is a serializable bound JavaBean | PASS | `public=true noArg=true` |
| extensions.decoder | pure decoder reads a symbol matrix | PASS | `expected=decoder round trip: é 漢字 actual=decoder round trip: é 漢字` |
| extensions.decoder | pure decoder reads version-one format bits | PASS | `expected=A actual=A` |
| extensions.decoder | pure decoder reads PNG output | PASS | `expected=decoder round trip: é 漢字 actual=decoder round trip: é 漢字` |
| extensions.decoder | pure decoder reads SVG output | PASS | `expected=decoder round trip: é 漢字 actual=decoder round trip: é 漢字` |
| extensions.decoder | pure decoder reads ASCII output | PASS | `expected=decoder round trip: é 漢字 actual=decoder round trip: é 漢字` |
| extensions.decoder | pure decoder rejects a damaged matrix | PASS | `rejected as expected: QrDecodingException: Reed-Solomon error correction rejected the symbol` |
| extensions.decoder | ZXing decoder adapter reads PNG output | PASS | `expected=decoder round trip: é 漢字 actual=decoder round trip: é 漢字` |
| extensions.decoder | ZXing adapter rejects a non-PNG artifact | PASS | `rejected as expected: QrDecodingException: ZXing adapter accepts PNG artifacts only, not SVG` |
| extensions.rounded | rounded renderer produces PNG output | PASS | `RenderedArtifact{format=PNG, 410x410, bytes=11666, by=renderer.rounded-png.v1}` |
| extensions.rounded | unsafe logo coverage is refused | PASS | `rejected as expected: QrRenderingException: logo covers 64.0% of the symbol but level H nominally recovers 30%` |
| extensions.rounded | unsafe logo coverage can be explicitly warned | PASS | `logo covers 64.0% of the symbol but level H nominally recovers 30%` |
| extensions.pdf | PDF is a single-page vector document | PASS | `10220 bytes, rectangle operators present` |
| extensions.pdf | pure decoder rejects unsupported PDF input | PASS | `rejected as expected: QrDecodingException: Unsupported artifact format: PDF` |
| extensions.spi | ServiceLoader catalogue contains new providers | PASS | `catalogue entries=15` |
| extensions.spi | ServiceLoader creates providers by stable id | PASS | `decoder and PDF renderer loaded through SPI` |
| extensions.spi | unknown SPI component id is rejected | PASS | `rejected as expected: IllegalArgumentException: Unknown or unavailable decoder component id: decoder.missing.v1` |
| frontends.cli | generate writes the requested renderer artifact | PASS | `status=0 bytes=6200` |
| frontends.cli | decode recovers a generated artifact | PASS | `wrote /home/lacoste/component-programming/qr-component-studio/docs/evidence/work/frontends/cli/hello.svg (6200 bytes, v1)
CLI hello
` |
| frontends.cli | list-components publishes the SPI catalogue | PASS | `status=0` |
| frontends.batch | batch writes successes and reports row errors | PASS | `total=2 succeeded=1 failed=1
OK row 2: /home/lacoste/component-programming/qr-component-studio/docs/evidence/work/frontends/cli/batch/one.svg (SVG)
ERROR row 3: Unknown error-correction level 'Z'. Expected one of L, M, Q, H
` |
| tooling | compilation enables all lint categories portably | PASS | `[--release, 11, -Xlint:all, -d, scratch/classes, -cp, test-classpath]` |
| tooling | compilation targets Java 11 with the supplied output and classpath | PASS | `[--release, 11, -Xlint:all, -d, scratch/classes, -cp, test-classpath]` |
| tooling | verification child inherits explicit headless mode | PASS | `[/usr/lib/jvm/java-21-openjdk/bin/java, -Djava.awt.headless=true, -cp, test-classpath, test.Main, evidence]` |
| tooling | explicit graphical child mode is not silently overridden | PASS | `explicit false propagated` |
| renderer.png | artifact size follows moduleSize and quietZone | PASS | `expected=198 actual=198` |
| renderer.png | PNG mime type is published | PASS | `expected=image/png actual=image/png` |
| renderer.png | PNG artifact decodes back to the payload | PASS | `expected=Kenyatta University actual=Kenyatta University` |
| renderer.png | quiet zone is painted with the background colour | PASS | `expected=java.awt.Color[r=255,g=255,b=255] actual=java.awt.Color[r=255,g=255,b=255]` |
| renderer.png | every module is painted at the right position and colour | PASS | `0 of 625 modules mismatched` |
| renderer.png | bound moduleSize property resizes the artifact | PASS | `before=198px after=363px` |
| renderer.svg | document declares absolute millimetre dimensions | PASS | `first 60 chars: <?xml version="1.0" encoding="UTF-8"?> <svg xmlns="http://ww` |
| renderer.svg | declares the vector namespace | PASS | `svg root present` |
| renderer.svg | horizontal run merging reduces the element count | PASS | `merged=167 single=322` |
| renderer.svg | parsing the SVG back reproduces the module matrix | PASS | `0 module mismatches` |
| renderer.svg | switching the optimisation off restores one rect per module | PASS | `rects=322 darkModules=322` |
| renderer.ascii | parsing the text art reproduces the module matrix | PASS | `0 module mismatches` |
| renderer.ascii | artifact is flagged as textual | PASS | `expected=true actual=true` |
| renderer | three renderer components fill the same role without changes | PASS | `PNG, SVG, ASCII` |
| renderer | artifacts carry the identifier of the component that produced them | PASS | `renderer.png.v1 / renderer.svg.v1 (167 rects) / renderer.ascii.v1` |
| renderer | custom property editor parses #RRGGBB | PASS | `expected=#1B5E20 actual=#1B5E20` |
| renderer | invalid colour text is rejected | PASS | `rejected as expected: IllegalArgumentException: '#12GG45' is not a hexadecimal colour` |
| renderer | invalid module size is rejected | PASS | `rejected as expected: IllegalArgumentException: moduleSize must be 1..64` |
| renderer | negative control: a corrupted PNG no longer decodes | PASS | `decoder rejected the damaged artifact as expected` |
| beans | PureJavaQrEncoderBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | PureJavaQrEncoderBean: exposes writable properties through the design patterns | PASS | `5 writable properties of 8 discovered` |
| beans | PureJavaQrEncoderBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | ZxingQrEncoderAdapterBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | ZxingQrEncoderAdapterBean: exposes writable properties through the design patterns | PASS | `3 writable properties of 6 discovered` |
| beans | ZxingQrEncoderAdapterBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | PngRendererBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | PngRendererBean: exposes writable properties through the design patterns | PASS | `4 writable properties of 4 discovered` |
| beans | PngRendererBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | SvgRendererBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | SvgRendererBean: exposes writable properties through the design patterns | PASS | `6 writable properties of 11 discovered` |
| beans | SvgRendererBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | AsciiRendererBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | AsciiRendererBean: exposes writable properties through the design patterns | PASS | `3 writable properties of 8 discovered` |
| beans | AsciiRendererBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | FileSystemRepositoryBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | FileSystemRepositoryBean: exposes writable properties through the design patterns | PASS | `2 writable properties of 7 discovered` |
| beans | FileSystemRepositoryBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | QrHistoryBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | QrHistoryBean: exposes writable properties through the design patterns | PASS | `2 writable properties of 2 discovered` |
| beans | QrHistoryBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | AuditLogListenerBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | AuditLogListenerBean: exposes writable properties through the design patterns | PASS | `3 writable properties of 7 discovered` |
| beans | AuditLogListenerBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | QrStudioFacadeBean: public class with a no-argument constructor | PASS | `public=true noArgConstructor=true` |
| beans | QrStudioFacadeBean: exposes writable properties through the design patterns | PASS | `8 writable properties of 12 discovered` |
| beans | QrStudioFacadeBean: implements Serializable and BoundPropertySource | PASS | `serializable=true boundPropertySource=true` |
| beans | encoder event set is visible without a BeanInfo | PASS | `expected=propertyChange actual=propertyChange` |
| beans.bound | changing moduleSize fires a PropertyChangeEvent | PASS | `moduleSize 8 -> 12` |
| beans.constrained | a listener can veto a capacity reduction | PASS | `rejected as expected: PropertyVetoException: capacity may not be reduced` |
| beans.constrained | the vetoed value was not applied | PASS | `expected=5 actual=5` |
| beans.constrained | removing the listener allows the change again | PASS | `expected=7 actual=7` |
| beans.indexed | addEntry fires an IndexedPropertyChangeEvent | PASS | `4 events observed` |
| beans.indexed | indexed accessor returns the stored element | PASS | `expected=1 actual=1` |
| beans.indexed | capacity policy discards the oldest entry | PASS | `count=3 discarded=1` |
| beans.indexed | overflow flag is exposed as a read-only property | PASS | `expected=true actual=true` |
| beans.introspection | BeanInfo attaches the custom colour editor | PASS | `expected=HexColorPropertyEditor actual=HexColorPropertyEditor` |
| beans.introspection | BeanInfo marks quietZone as an expert feature | PASS | `expected=expert actual=expert` |
| beans.introspection | BeanInfo publishes a display name for builder tools | PASS | `expected=PNG Renderer Bean actual=PNG Renderer Bean` |
| beans.events | the facade notifies every connected listener | PASS | `listeners notified=1 audit events=1` |
| beans.events | the event object carries the symbol and the artifact | PASS | `symbol=QrCode{version=2, size=25, ec=M, mode=Byte, mask=6, chars=17, dataCodewords=28, eccCodewords=16, producer='encoder.pure-java.v1'} artifact=RenderedArtifact{format=PNG, 264x264, bytes=5865, by=renderer.png.v1}` |
| beans.persistence | the assembled bean graph survives an XML round trip | PASS | `facade[encoder=encoder.pure-java.v1, renderer=renderer.png.v1, repository=repository.file-system.v1, history=history.bounded-memory.v1, listeners=1]` |
| beans.persistence | design-time property values survive the round trip | PASS | `moduleSize=8 xmlBytes=1246` |
| beans.persistence | the restored assembly can generate without recompiling | PASS | `artifact written by the restored graph` |
| beans.persistence | the assembly profile survives an XML round trip | PASS | `Assembly profile 'round-trip':   encoder     -> encoder.pure-java.v1   renderer    -> renderer.svg.v1                  moduleSize = 9   repository  -> repository.file-system.v1   history     -> history.bounded-memory.v1   listener    -> listener.audit-log.v1   facade      -> facade.qr-studio.v1 ` |
| beans.optional | the assembly detects the optional third-party component | PASS | `com.google.zxing.core present=true` |
| beans.optional | the adapter reports itself as a third-party component | PASS | `ZXing ("Zebra Crossing") core 3.5.3 [versions 1-40, modes [Numeric, Alphanumeric, Byte], third-party, optional library required]` |
| assembly.catalogue | every role has at least one implementation | PASS | `encoders=2 renderers=5 repositories=2` |
| assembly.catalogue | the optional component declares its context dependency | PASS | `adapter declared with an explicit required interface` |
| assembly.wiring | every declared dependency of the assembly is satisfied | PASS | `encoder       -> facade.setEncoder     : OK \| renderer      -> facade.setRenderer    : OK \| repository    -> facade.setRepository  : OK \| history       -> facade.setHistory     : OK \| listener      -> facade event source   : OK \| encoder       -> optional ZXing library : PRESENT` |
| assembly.wiring | profile properties reached the component instances | PASS | `moduleSize=7` |
| assembly.end-to-end | generation stores an artifact and returns the path | PASS | `/home/lacoste/component-programming/qr-component-studio/docs/evidence/work/assembly-test-1793734114376/qr-code-v2-q-20261103-222834.png` |
| assembly.end-to-end | the stored PNG decodes back to the payload | PASS | `expected=Kenyatta University actual=Kenyatta University` |
| assembly.end-to-end | history and audit counters were updated | PASS | `history=1 audit=1 facade=1` |
| assembly.end-to-end | the repository persisted the history record | PASS | `expected=1 actual=1` |
| assembly.replace | the assembly runs with a different renderer component | PASS | `expected=SVG actual=SVG` |
| assembly.replace | the choice is recorded in the profile | PASS | `renderer.svg.v1` |
| assembly.replace | a third renderer drops in without recompiling | PASS | `expected=ASCII actual=ASCII` |
| assembly.replace | all three renderers produced the same symbol | PASS | `version 2, mask 2 for PNG, SVG and ASCII` |
| assembly.replace | the third-party encoder satisfies the same contract | PASS | `expected=Kenyatta University actual=Kenyatta University` |
| assembly.replace | the facade now reports the adapter as its encoder | PASS | `encoder.zxing-adapter.v1` |
| assembly.negative | generating without an encoder fails loudly | PASS | `rejected as expected: QrEncodingException: No encoder component is connected: call setEncoder(...) before generating` |
| assembly.negative | an unknown component id is rejected at assembly time | PASS | `rejected as expected: IllegalArgumentException: Unknown or unavailable renderer component id: renderer.does-not-exist.v1` |
| assembly.negative | a profile property the selected component does not declare is reported, not fatal | PASS | `[not applicable to component renderer.ascii.v1 (property not declared): component renderer.ascii.v1.moduleSize]` |
| assembly.repository | clearHistory empties the persisted history | PASS | `expected=0 actual=0` |
| assembly.repository | a new record is persisted immediately | PASS | `expected=1 actual=1` |
| assembly.repository | reinstantiating the repository component reads the same history file | PASS | `history file /home/lacoste/component-programming/qr-component-studio/docs/evidence/work/assembly-test-1793734114376/history.ser` |
| assembly.history | the in-memory history honours its capacity policy | PASS | `entries=2 discarded=5` |
| gallery | the third-party adapter component produces decodable artifacts | PASS | `v2 mask 2 by encoder.zxing-adapter.v1` |
| gallery | the assembled application writes gallery artifacts | PASS | `/home/lacoste/component-programming/qr-component-studio/docs/evidence/gallery/assembled-app.png` |
| gallery | gallery contains PNG, SVG and ASCII artifacts | PASS | `/home/lacoste/component-programming/qr-component-studio/docs/evidence/gallery` |


Summary: 152 checks, 152 passed, 0 failed.
