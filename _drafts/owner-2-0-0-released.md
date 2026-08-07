---
layout: news_item
title: "Owner 2.0.0 Released"
date: "2026-01-01 00:00:00 +0200"
author: matteobaccan
version: 2.0.0
categories: [release]
---

<!-- DRAFT: move this file to _posts/ renaming it to YYYY-MM-DD-owner-2-0-0-released.md
     and update the date above when the release is published. -->

Version 2.0.0 is the first release since the project maintenance moved from Luigi Viggiano to Matteo Baccan.
It brings a set of new features, a security hardening pass, a fully modernized build infrastructure, and a long
list of dependency updates accumulated since 1.0.12. Java 8 is now the minimum runtime, which allowed the
removal of some compatibility leftovers dating back to Java 6/7: see the "Removals" section below for the
(short) migration instructions.

Why 2.0.0
---------
This release was prepared as 1.0.13 and renumbered before publication, because two of its changes alter the
result of a configuration that used to work, and a patch number would have been a quiet place to put them.
Neither is expected to affect a real configuration — the whole test suite of the project passes unchanged, and
each is described in full below — but the number should say so rather than the changelog alone:

 * **Braces are matched, not counted from the left.** Up to 1.0.12 a `${` was closed by the first `}` that
   followed it; now it is closed by the one that matches it, which is what makes nested variables possible.
   Only the `${` sequence opens a level, so a lone brace inside an expression is still ordinary text. Should
   an unforeseen combination of braces read differently, `-Downer.nested.variable.expansion=false` restores
   the previous behaviour for the whole JVM, by running the previous implementation unchanged.
 * **A circular variable reference is an error.** A property whose value leads back to itself used to exhaust
   the stack, or — for the shape `a=${a:default}` — to produce an empty string. It now throws an
   `IllegalArgumentException` naming the chain. No cycle ever produced a useful value, but a configuration
   that quietly resolved to the empty string will now fail loudly, which is the point.

Everything else is additive. In particular, a `Map` return type used to throw on every access, so the new
grouping behaviour described below cannot change the result of any configuration that worked.

Beyond the individual changes, this release has an explicit goal: to bring the test coverage as high as it can
practically go, and to bring the number of warnings reported by the static analysers down to zero. A library
that other projects depend on for their configuration has to be trustworthy first and featureful second, and
after several years without a release the most valuable thing to do was to verify — line by line — that the
existing behaviour is the intended one. The "Code quality and test coverage" section below explains what this
means in practice, and what it turned up.

RELEASE NOTES
=============

OWNER v2.0.0 contains following enhancements and bug fixes.

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
   [documentation]({{ site.url }}/docs/usage/#toc_5). Originally proposed by Alexander Poulikakos in
   [#216](https://github.com/matteobaccan/owner/pull/216).
 * New `@Prefix` annotation: declare the common prefix of a group of keys once, on the interface, instead of
   repeating it in the `@Key` of every method. `@Prefix("server.")` makes `String hostname()` resolve to
   `server.hostname`, and it is prepended to the `@Key` value as well. The prefix belongs to the interface that
   *declares* the method, so it never leaks onto the methods a sub-interface inherits, at any depth of the
   hierarchy; it is expanded like the rest of the key, so `@Prefix("servers.${env}.")` selects a section at
   runtime; and it can be switched off per method or per interface with
   `@DisableFeature(PREFIX)`, a new value of `DisableableFeature`. Nothing changes for existing configurations:
   an interface without `@Prefix` resolves its keys exactly as before. See the
   [documentation]({{ site.url }}/docs/key-prefix/). Originally proposed by Gmugra in
   [#273](https://github.com/matteobaccan/owner/pull/273).
 * New `@DefaultValue(useOnEmpty = true)` flag: a property that is present but **empty** is normally a value
   like any other — `port=` is not a missing property, and on a numeric type it fails the conversion — which is
   the distinction MicroProfile Config, Quarkus and Spring Boot all draw, and what keeps a typo like
   `port=8O80`, written with the letter O, from silently becoming the default. The flag covers the one case
   where the distinction gets in the way: a value left empty by a template, as in `port=${PORT}` with `PORT`
   unset. With it, an empty value — whitespace included, and after the variables are expanded — falls back on
   the default as if the property were missing, while a value that is *wrong* rather than empty keeps failing.
   It is opt-in and per method, so nothing changes for existing configurations. See the
   [documentation]({{ site.url }}/docs/usage/#toc_3) and the
   [table of what an empty value does on each type]({{ site.url }}/docs/type-conversion/). Partially answers
   [#191](https://github.com/matteobaccan/owner/issues/191).
 * [#320](https://github.com/matteobaccan/owner/pull/320): `EnumSet` and `Set<Enum>` are now supported by type
   conversion (thanks to @dexman545).
 * [#187](https://github.com/matteobaccan/owner/issues/187): `java.nio.file.Path` is converted, with a leading
   `~` expanded to the user home exactly as `java.io.File` already was — the two ways of naming a path no
   longer disagree. Arrays and collections of `Path` follow. The reporter asked in 2016 whether this belonged
   in a separate module for Java 7; the question no longer arises, since Java 8 is the minimum runtime.
 * Variables can now carry a default value: `${db.host:localhost}` resolves to `localhost` when `db.host` is
   defined nowhere, instead of to the empty string. Everything after the first colon is the default, colons
   included, so URLs, Windows paths and `host:port` pairs survive intact. Existing configurations are unaffected:
   the text inside `${...}` is looked up as a property key in its entirety first, and only if there is no such
   property is the colon read as a separator — so a key like `jdbc:url` keeps resolving as before. The one
   behaviour that changes is a variable that used to resolve to nothing: `${a:b}`, with neither `a:b` nor `a`
   defined, yielded the empty string up to 1.0.12 and yields `b` from now on. See the
   [documentation]({{ site.url }}/docs/variables-expansion/). Proposed by Ilya Koshaleu in
   [#256](https://github.com/matteobaccan/owner/pull/256).
 * Variables can now be nested: the expression inside `${...}` is expanded first, and the result is then looked
   up as a key, so `${servers.${env}.url}` reads the key named by the value of `env`. It works at any depth, in
   the `@Key`, in a property value and in the `@Sources` specification, and it combines with the default values
   above — `${servers.${env}.url:http://localhost}`. This is what makes a key depend on a key that itself
   depends on another one, the case that produced silently wrong lookups before. See the
   [documentation]({{ site.url }}/docs/variables-expansion/#toc_3). Proposed by Tomek in
   [#326](https://github.com/matteobaccan/owner/pull/326).

   **Compatibility.** This is the one change in this release that touches an existing parsing rule: up to 1.0.12
   a `${` was closed by the first `}` that followed it, now it is closed by the one that matches it. Only the
   `${` sequence opens a nesting level, so a lone brace inside an expression remains ordinary text and a key such
   as `a{b` keeps resolving; `${}` and an unbalanced `${` are left alone as before. A configuration that uses
   plain variables is therefore unaffected. Should some unforeseen combination of braces read differently, the
   whole behaviour can be switched off for the JVM with `-Downer.nested.variable.expansion=false`, which runs the
   substitution of the previous releases unchanged.
 * A `Map` return type now reads the **group of properties below the key of the method**, closing a request open
   since 2013 ([#41](https://github.com/matteobaccan/owner/issues/41)):

   ```properties
   something.foo=1
   something.bar=2
   ```

   ```java
   Map<String, Integer> something();     // {foo=1, bar=2}
   ```

   Both sides of the entry go through the regular type conversion, so `Map<Integer, String>` and
   `Map<Colour, String>` work as well; the group is named like any other key, so `@Key`, `@Prefix` and variable
   expansion all apply — `@Key("servers.${env}")` picks the section at runtime. A name with further dots keeps
   them, so `something.a.b` becomes the entry `a.b`; no match gives an empty map rather than `null`; the
   declared map type is honoured, a `SortedMap` coming back as a `TreeMap`; and `@DefaultValue` is refused on
   such a method, since a default belongs to the individual properties. A `@ConverterClass` still takes
   precedence, which is how the other shape of the request — one property whose value holds the pairs, as asked
   in [#286](https://github.com/matteobaccan/owner/issues/286) — keeps working. Nothing can break: a `Map`
   return type used to throw on every access, so no working configuration relied on it. See the
   [documentation]({{ site.url }}/docs/type-conversion/).
 * A circular variable reference is now reported instead of being followed. A property whose value leads back to
   the property itself cannot be resolved, and an `IllegalArgumentException` names the chain that closes the
   loop — `Circular variable reference: ${a} -> ${b} -> ${a}` — where up to 1.0.12 the same configuration
   exhausted the stack with a `StackOverflowError`. A default value does not rescue it: `db.host=${db.host:localhost}`
   is the shell idiom for "keep it if set, otherwise use this", but it relies on the substitution happening once
   at assignment, while OWNER expands variables when a property is read, and inside values. That line therefore
   describes a loop rather than a fallback, and it is reported as one — what was meant is `db.host=localhost`.
   See the [documentation]({{ site.url }}/docs/variables-expansion/#toc_5).
 * New `@CollectionConverterClass` annotation: hands the raw property value to a single converter instead of
   splitting it first and converting one element at a time, as `@ConverterClass` does. It is the way to opt out of
   the built-in tokenization — for a property holding a single JSON document, say — or to return a collection type
   OWNER cannot instantiate itself, such as an immutable one or an implementation without a no-argument
   constructor. Using it on a method that does not return a `Collection` reports which method is at fault instead
   of failing later with a `ClassCastException`. See the
   [documentation]({{ site.url }}/docs/type-conversion/). Contributed by Adam Huječek in
   [#248](https://github.com/matteobaccan/owner/pull/248), closing
   [#206](https://github.com/matteobaccan/owner/issues/206).
 * Security hardening of the `XMLLoader` against XXE attacks: external DTDs and entities are neutralized, secure
   processing limits entity expansion; the standard Java properties XML format keeps working as before.
 * [#325](https://github.com/matteobaccan/owner/pull/325): temporary files are now created with owner-only
   permissions via `Files.createTempFile` (thanks to @JLLeitschuh); when storing a Config to an existing file,
   the file permissions are preserved.
 * Bytecode is still compatible with Java 8 at runtime, while the project is built with modern JDKs
   (`compiler-release=8`); a JDK 11 or superior is required to build from sources.
 * Javadoc completed and improved across the codebase.
 * Dependencies updated across the board, including security-driven pins: JUnit 4.13.2, Mockito 5.x, SLF4J 2.x,
   commons-codec 1.22, Curator with ZooKeeper forced to 3.9.5 and Netty aligned via BOM to address published
   vulnerabilities.

Code quality and test coverage
------------------------------
A large part of the work that went into 2.0.0 is not visible in the API. The objective was to raise the test
coverage as far as it reasonably goes and to leave no warning unexamined, so that future changes start from a
codebase that says what it does.

 * **Test coverage extended**, with the crypto, loaders, util and ConfigCache packages at or near 100%. The
   tests were written to pin down actual behaviour, not to move a percentage: several of them document
   decisions that were previously only implicit in the code.
 * **Every warning triaged, one by one.** The project is analysed on each push by
   [CodeQL](https://github.com/matteobaccan/owner/security/code-scanning) with the `security-and-quality`
   query pack, by [SonarCloud](https://sonarcloud.io/project/overview?id=matteobaccan_owner), and by the
   inspections built into the IDEs used for development. Every finding was either fixed, or dismissed with a
   written technical justification explaining why the construct is correct as it stands — an analyser being
   wrong is a legitimate outcome, an unread warning is not.
 * **The exercise was not cosmetic.** Chasing warnings that looked like style issues surfaced genuine defects
   that had gone unnoticed for years. The array and collection conversion bug listed under "Bugs fixes" was
   found exactly this way: a CodeQL note about an inner class that could be made `static` turned out to be
   hiding a test that passed for the wrong reason, and behind it a real failure affecting any user converting
   a list of custom objects.
 * **Tests that passed for the wrong reason were corrected.** A green test suite is only meaningful if each
   test fails when the behaviour it describes breaks. Where a test was found to be satisfied by an accident of
   its fixtures rather than by the behaviour under test, the fixture was fixed and the assertion re-verified.

The intention is to keep this state: warnings are not allowed to accumulate between releases, and a finding is
closed only when it has been understood.

Site Enhancements
-----------------
 * New documentation for the [Preprocessors]({{ site.url }}/docs/preprocessors/) feature (available since 1.0.9,
   never documented).
 * New documentation for the [JMX support]({{ site.url }}/docs/jmx/) (available since 1.0.10, never documented).
 * New chapter on the [Key prefix]({{ site.url }}/docs/key-prefix/) feature, and the list of the
   [disableable features]({{ site.url }}/docs/disabling-features/) is now spelled out, with the version each
   one appeared in.
 * New sections on [nested variables]({{ site.url }}/docs/variables-expansion/#toc_3) and on
   [how to switch them off]({{ site.url }}/docs/variables-expansion/#toc_4) in Variables expansion.
 * New section on [Sources and interface inheritance]({{ site.url }}/docs/loading-strategies/#toc_1), writing down
   what was until now only implicit in the code: `@Sources` accumulates across the interfaces a mapping interface
   extends — by design, since it describes a set and not a single setting — while `@LoadPolicy` and `@HotReload`
   take the first annotation found. The section also documents the limitation the three of them share, that only
   the direct super-interfaces are read, so an annotation two levels up is silently ignored. The behaviour is
   unchanged in 2.0.0 and is now covered by tests, so that changing it will be a deliberate step.
 * New section on [Mandatory properties]({{ site.url }}/docs/usage/#toc_5) in Basic usage.
 * [Type conversion]({{ site.url }}/docs/type-conversion/) no longer stops at "`Map` is not supported", a
   sentence that read as "cannot be done" and had been sending people away since at least
   [#41](https://github.com/matteobaccan/owner/issues/41). The chapter now describes the grouping above, and
   keeps the `@ConverterClass` recipe for the case where a single property value holds the pairs — arrays of
   maps included.
 * New section on [overriding a property in a sub-interface]({{ site.url }}/docs/usage/#toc_6), answering
   [#421](https://github.com/matteobaccan/owner/issues/421): an override redirects a property instead of adding
   one, since there is one method and therefore one key. Both of the things usually wanted there — keeping the
   base key readable, and making the concrete setting fall back to the base one — are shown written down
   explicitly, the second one as a three-level chain built with a variable.
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
 * The BSD license header is now enforced on every Java source file by the
   [license-maven-plugin](https://oss.carbou.me/license-maven-plugin/): `mvn license:format` adds or fixes it,
   and `mvn license:check` — bound to the `verify` phase, so it also runs in CI — fails the build when a file is
   missing it. Nineteen files had drifted without one over the years, and the copyright line existed in three
   different variants; both are now uniform. The Maven wrapper sources are excluded, as they ship under Apache 2.0.

Bugs fixes
----------
 * [#195](https://github.com/matteobaccan/owner/issues/195): imported `Map` entries whose key or value is not a
   `String` are now rejected with an `IllegalArgumentException` naming the offending key, instead of being accepted
   and then silently misbehaving. Originally reported and fixed by Stefán Freyr Stefánsson in
   [#197](https://github.com/matteobaccan/owner/pull/197), extended here to cover keys as well as values.

   Imports are merged into a `java.util.Properties`, whose contract only admits `String` keys and values, but
   which extends `Hashtable<Object, Object>` and therefore accepts anything through `putAll`. The entry then
   became invisible to `getProperty`, in two different ways:

   ```java
   public interface MyConfig extends Config {
       @Key("some.key")
       @DefaultValue("1")
       Integer someValue();
   }
   ```

   | Import | Up to 1.0.12 | Since 2.0.0 |
   |---|---|---|
   | `imports.put("some.key", 42)` | `someValue()` returns `null`, *shadowing* `@DefaultValue("1")` | `IllegalArgumentException` at `create()` |
   | `imports.put(42, "42")` | the entry is dropped, `someValue()` returns the default `1` | `IllegalArgumentException` at `create()` |
   | `imports.put("some.key", new StringBuilder("42"))` | `someValue()` returns `null` | `IllegalArgumentException` at `create()` |
   | `imports.put("some.key", "42")` | `someValue()` returns `42` | unchanged, returns `42` |

   **Compatibility.** Code that imported only `String` keys and values is unaffected. Code that imported anything
   else was already getting a wrong value, or none, so no working behaviour is lost — but a heterogeneous `Map`
   that happened to be read only through its `String` entries will now fail fast at `create()` time rather than
   half-working. Note that a `CharSequence` is not sufficient, as `Properties` compares against `String`
   specifically: call `toString()` on `StringBuilder`/`StringBuffer` values before importing them. See the
   [documentation]({{ site.url }}/docs/importing-properties/).

   The validation lives in the factory, so it applies uniformly to `ConfigFactory.create()`, to a `Factory`
   obtained from `ConfigFactory.newInstance()`, and to `ConfigCache.getOrCreate()`; previously the equivalent
   check on null keys and values only covered the first of the three.
 * Fixed the conversion of arrays and collections when a single element cannot be converted. The converter is
   chosen once from the first element, so the remaining ones could still fail: their internal "skip" marker
   ended up being stored into the resulting array, surfacing as an
   `IllegalArgumentException: array element type mismatch` instead of the documented
   [`UnsupportedOperationException`](https://docs.oracle.com/javase/8/docs/api/java/lang/UnsupportedOperationException.html).
   A property like `@DefaultValue("1, 2, foo, 4")` mapped to a custom type now reports
   `Cannot convert 'foo' to MyType`, consistently with what already happened for a non-array property. For the
   same reason, a `@ConverterClass` returning `null` for an element now yields a `null` element instead of
   failing the whole conversion.
 * Conversion errors now name the property they come from: `Cannot convert 'abc' to int` became
   `Cannot convert 'abc' to int for property 'server.port'`. The message used to say what could not be converted
   but not where to go and fix it, which in a file with fifty properties left the search to be done by hand. The
   key named is the one the property is read with, `@Key` and `@Prefix` included
   ([#191](https://github.com/matteobaccan/owner/issues/191)).
 * Fixed a `NullPointerException` masking the real error in the hot reload example when the configuration URI is
   invalid.
 * Test suite stability fixes (thread handling in multi-threading tests, wait times).

Downloadable artifacts are published on
[GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-2.0.0) and on
[Maven Central Repository](https://central.sonatype.com/artifact/org.aeonbits.owner/owner/2.0.0).
