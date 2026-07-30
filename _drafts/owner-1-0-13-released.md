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
of dependency updates accumulated since 1.0.12.

RELEASE NOTES
=============

OWNER v1.0.13 contains following enhancements and bug fixes.

Enhancements
------------
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
 * Documentation refreshed to the current state of the project: installation instructions, build requirements,
   FAQ (encrypted properties are supported since 1.0.12), links and navigation updated to the maintained
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
