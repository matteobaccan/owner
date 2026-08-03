---
layout: news_item
title: "Owner 1.0.13 Released"
date: "2026-01-01 00:00:00 +0200"
author: matteobaccan
version: 1.0.13
categories: [release]
---

<!-- DRAFT: move this file to _posts/ renaming it to YYYY-MM-DD-owner-1-0-13-released.md
     and update the date above when the release is published. -->

Version 1.0.13 is the first release since the project maintenance moved from Luigi Viggiano to Matteo Baccan.
It brings two new features, a security hardening pass, a fully modernized build infrastructure, and a long list
of dependency updates accumulated since 1.0.12. Java 8 is now the minimum runtime, which allowed the removal
of some compatibility leftovers dating back to Java 6/7: see the "Removals" section below for the (short)
migration instructions.

RELEASE NOTES
=============

OWNER v1.0.13 contains following enhancements and bug fixes.

Removals
--------
Java 8 is the minimum runtime required by this release, and the machinery that existed only to support
older JVMs is gone. If you are affected, migration is a one-liner in each case:

 * The `owner-java8` artifact is gone. Its only feature, the support for
   [`default` methods](http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html) in config
   interfaces, is now built into the core: **replace the `owner-java8` dependency with `owner`** and
   everything keeps working.
 * The `owner-java8-extras` artifact is gone. The `DurationConverter`, `ByteSizeConverter` and the
   `ByteSize`/`ByteSizeUnit` classes it contained moved, with unchanged package names, into `owner-extras`:
   **replace the `owner-java8-extras` dependency with `owner-extras`**.
 * The internal utility class `org.aeonbits.owner.util.Base64` is gone. It was a runtime-selection shim
   between `java.util.Base64` (Java 8+) and `javax.xml.bind.DatatypeConverter` (Java 6/7), never used by the
   library API itself. If you referenced it, **use [`java.util.Base64`](https://docs.oracle.com/javase/8/docs/api/java/util/Base64.html)
   directly**: `Base64.encode(bytes)` becomes `Base64.getEncoder().encodeToString(bytes)` and
   `Base64.decode(string)` becomes `Base64.getDecoder().decode(string)`.
 * The internal utility method `org.aeonbits.owner.util.Util.eq(a, b)` is gone: it predated
   `java.util.Objects` and did exactly what the standard
   [`Objects.equals(a, b)`](https://docs.oracle.com/javase/8/docs/api/java/util/Objects.html#equals-java.lang.Object-java.lang.Object-)
   does — **use that instead**.
 * The test-support methods `Util.save(File, Properties)`, `Util.saveJar(File, String, Properties)`
   and `Util.delete(File)` are gone from the public API: they were never used by the library itself
   and now live in the test suite. If you relied on them, the standard
   [`Properties.store`](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#store-java.io.OutputStream-java.lang.String-),
   `java.util.jar.JarOutputStream` and [`java.nio.file.Files`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html)
   APIs cover the same ground.

Enhancements
------------
 * `default` methods in config interfaces work out of the box with the core `owner` artifact: no extra
   dependency is needed anymore (see Removals above).
 * New `Accessible.store(Writer, String)` overload, mirroring `Properties.store(Writer, String)`; the old
   javadoc note about it being unavailable dated back to the JDK 1.5 era.
 * The single-method SPI interfaces (`Converter`, `Preprocessor`, `ReloadListener`) are now marked
   `@FunctionalInterface`: converters, preprocessors and reload listeners can officially be written as lambdas.
 * New `@Mandatory` annotation: mark a property (or a whole interface) as required, and get a
   `MissingMandatoryPropertyException` listing all the unresolvable keys when the Config is created, as well as
   on access if a mandatory property disappears later (e.g. after a hot reload). See the
   [documentation]({{ site.url }}/docs/usage/#toc_4). Originally proposed by Alexander Poulikakos in
   [#216](https://github.com/matteobaccan/owner/pull/216).
 * [#320](https://github.com/matteobaccan/owner/pull/320): `EnumSet` and `Set<Enum>` are now supported by type
   conversion (thanks to @dexman545).
 * Security hardening of the `XMLLoader` against XXE attacks: external DTDs and entities are neutralized, secure
   processing limits entity expansion; the standard Java properties XML format keeps working as before.
 * [#325](https://github.com/matteobaccan/owner/pull/325): temporary files are now created with owner-only
   permissions via `Files.createTempFile` (thanks to @JLLeitschuh); when storing a Config to an existing file,
   the file permissions are preserved.
 * Bytecode is still compatible with Java 8 at runtime, while the project is built with modern JDKs
   (`compiler-release=8`); a JDK 11 or superior is required to build from sources.
 * Javadoc completed and improved across the codebase.
 * Test coverage extended (crypto, loaders, util, ConfigCache packages at or near 100%), with fixes to all the
   issues reported by static analysis.
 * Dependencies updated across the board, including security-driven pins: JUnit 4.13.2, Mockito 5.x, SLF4J 2.x,
   commons-codec 1.22, Curator with ZooKeeper forced to 3.9.5 and Netty aligned via BOM to address published
   vulnerabilities.

Site Enhancements
-----------------
 * New documentation for the [Preprocessors]({{ site.url }}/docs/preprocessors/) feature (available since 1.0.9,
   never documented).
 * New documentation for the [JMX support]({{ site.url }}/docs/jmx/) (available since 1.0.10, never documented).
 * New section on [Mandatory properties]({{ site.url }}/docs/usage/#toc_4) in Basic usage.
 * [Crypto support]({{ site.url }}/docs/crypto/) is no longer labelled as experimental: the `@EncryptedValue` and
   `@DecryptorClass` annotations have shipped unchanged since 1.0.10 and are part of the stable API.
 * Documentation refreshed to the current state of the project: installation instructions, build requirements,
   FAQ (encrypted properties are supported since 1.0.10, not 1.0.12 as previously stated;
   [#229](https://github.com/matteobaccan/owner/issues/229)), links and navigation updated to the maintained
   repository and to the current CI services.

Infrastructure
--------------
 * Continuous integration migrated from Travis CI to
   [GitHub Actions](https://github.com/matteobaccan/owner/actions): every push and pull request is built on all
   the supported LTS JDKs (11, 17, 21 and 25) with Maven caching.
 * Code quality and coverage tracked on [SonarCloud](https://sonarcloud.io/project/overview?id=matteobaccan_owner);
   security scanning via CodeQL and Dependabot; dependencies kept current by Renovate.
 * Maven wrapper added; Maven Enforcer requires Maven 3.6.3+; Travis, Coveralls and WhiteSource/Mend leftovers
   removed.

Bugs fixes
----------
 * Fixed a `NullPointerException` masking the real error in the hot reload example when the configuration URI is
   invalid.
 * Test suite stability fixes (thread handling in multi-threading tests, wait times).

Downloadable artifacts are published on
[GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-1.0.13) and on
[Maven Central Repository](https://central.sonatype.com/artifact/org.aeonbits.owner/owner/1.0.13).
