TODO LIST
=========

This file is used to keep track of things that should be done, sometime next.
For bugs and features request please see [GitHub issues](https://github.com/matteobaccan/owner/issues).

WEBSITE
-------

- [x] Write documentation for pre processing feature (see [Preprocessors](https://matteobaccan.github.io/owner/docs/preprocessors/))
- [x] Write documentation for JMX support (see [JMX support](https://matteobaccan.github.io/owner/docs/jmx/))
- [ ] Update the release note: a draft for 2.0.0 is ready in `owner-site/site/_drafts/owner-2-0-0-released.md`,
      to be moved into `_posts/` (with the release date in the file name and front matter) when 2.0.0 is published.

CODE
----

- [x] **Break the package cycle between `org.aeonbits.owner` and `org.aeonbits.owner.converters`.** The
      parsing now lives in `org.aeonbits.owner.util.DurationParser`, which both the core and
      `DurationConverter` call; the converter is the public adapter and the core no longer imports the
      `converters` package.
- [ ] **Break the remaining package cycle, between `org.aeonbits.owner` and `org.aeonbits.owner.util`.**
      Older than the one above and the last one left: `Util.isFeatureDisabled` reads
      `Config.DisableFeature`, so `util` points back at the core while most of the core points at `util`.
      The method is the only edge. Moving it into the core would break the cycle for good, but `Util` is
      public, so it is an API removal and belongs to a major version — decide it alongside the
      `module-info` question below rather than on its own.

SILENT FAILURES STILL TO LOOK AT
--------------------------------

Found by reading the code on 2026-08-09, while adding the first two diagnostics the library has ever had:
the hot reload that used to die on its first failure, and the XXE hardening that could be absent without
anyone being told. `java.util.logging` is now used for both — the JDK's, so still no dependency, and
`org.aeonbits.owner.level = OFF` quietens the lot. These four are the rest of what that reading turned up,
in the order they seem worth doing.

Two rules any of them has to keep. **Never log a value**: `@Sensitive` exists to keep values out of logs and
a diagnostic that leaked a password would be a poor trade. And **say it once**: a configuration is long
lived and `reload()` runs the whole load again, so anything reported per load has to be guarded the way
`PropertiesManager.lastReportedReloadFailure` is, or it repeats at the hot reload interval for ever.

- [ ] **A source that was named and never arrived.** `Config.LoadType.FIRST` and `MERGE` both do
      `catch (IOException) { ignore(); }`, with a comment admitting it covers two different things: a file
      legitimately absent, which is how the fallback is *meant* to work, and a file that is there but cannot
      be read — wrong permissions, a typo in the path, a network source down. In the second case the caller
      gets a configuration full of defaults and no hint whatever. A typo in
      `@Sources("file:/etc/myap.env")` yields an object that works and lies.
      Warning on every absent file would be unbearable noise, since `FIRST` expects misses by design and the
      default probe tries `MyConfig.properties` and `MyConfig.xml` for every interface. The rule that seems
      right: **`FileNotFoundException` stays silent, every other `IOException` is a warning**; and one
      further warning when `@Sources` was declared explicitly and *nothing at all* could be read, which is
      exactly the typo case and costs nothing in the normal one.
- [ ] **A classpath source that disappears before any loader sees it.** `ConfigURIFactory.newURI` returns
      `null` when `getResource` finds nothing, and `PropertiesManager.toURIs` drops it with
      `if (uri != null)`. So `@Sources("classpath:missing.properties")` never reaches a loader at all: it is
      simply not in the list. Same family as the item above but earlier, so a fix there would not cover it —
      the two belong in one piece of work.
- [ ] **XML that is malformed and is not in the Java properties format.** `XmlToPropsHandler.error` reads
      `if (isJavaPropertiesFormat) throw e;`, so for a user-defined XML a validation error is swallowed and
      the caller keeps whatever partial properties had been collected before it.
- [ ] **A `@Key` whose value is not a legal format string.** `PropertiesInvocationHandler` catches the
      failure from `String.format` and returns the template unexpanded, which is documented and probably the
      right behaviour — a property value has no obligation to be a format string. Worth a `FINE`, not a
      `WARNING`, and only if it costs nothing.

Deliberately **not** on this list: the five `ignore()` calls in `PropertiesManager`. They all catch
`RollbackBatchException` or `RollbackOperationException`, which means a listener chose to veto the change.
That is the event API working, not a failure, and it should stay quiet.

GAPS AGAINST THE OTHER CONFIGURATION LIBRARIES
----------------------------------------------

From the comparison run on 2026-08-07. The evidence, the state of the field and the reasoning are in
`COMPARISON.md`; this is only the list of work, in the order the value came out. Nearly every line has
an issue behind it, which is the point: what the others shipped is what our reporters asked for.

- [ ] **Nested configuration interfaces** — `ServerConfig server();` resolving `server.host`,
      `server.port`. The largest visible gap: SmallRye, Gestalt and Coat all have it. `@Prefix` from
      2.0.0 already does half the work — key composition exists, what is missing is a return type that
      builds another proxy. Issues [#129](https://github.com/matteobaccan/owner/issues/129),
      [#2](https://github.com/matteobaccan/owner/issues/2),
      [#72](https://github.com/matteobaccan/owner/issues/72).
- [ ] **Further formats as `Loader`s, written by hand, with no external dependency.** The SPI has
      existed since 1.0.5, a loader is a three-method class, and two external projects have already
      hand-written a YAML loader and a JSON one against it. Being properties-only is the top reason
      people pick Typesafe Config over us. **`FORMATS.md` holds the whole analysis** — what each format
      costs, what the core is missing, and the order.
      **`.env` is done** (2026-08-09, `d04c500`): in the core, `DotEnvLoader` with an `EnvDialect` of
      seven rules and three presets, `docker` by default. What is left, in order: loader enablement and
      per-loader options; indexed keys and a documented flattening, which unblock every tree-shaped
      format at once; then YAML and JSON; then INI and TOML. Issues
      [#14](https://github.com/matteobaccan/owner/issues/14),
      [#65](https://github.com/matteobaccan/owner/issues/65),
      [#240](https://github.com/matteobaccan/owner/issues/240), and
      [#48](https://github.com/matteobaccan/owner/issues/48) is on the critical path rather than beside
      it.
- [ ] **Origin tracking** — which source a merged property actually came from. Only Spring does this
      decently, and with `@Sources` plus `LoadType.MERGE` it is a question our users really do ask.
      Issue [#277](https://github.com/matteobaccan/owner/issues/277); related to
      [#170](https://github.com/matteobaccan/owner/issues/170).
- [ ] **Configurable naming strategy / relaxed binding** — kebab-case, snake_case, verbatim, as SmallRye,
      Gestalt and Spring all offer. It hooks into the factory-prefix machinery built for 2.0.0.
      Issue [#116](https://github.com/matteobaccan/owner/issues/116).
- [ ] **Bean Validation (JSR-380)** as an optional `owner-validation` module, never in the core.
      SmallRye, Gestalt and Spring have it. Issue
      [#201](https://github.com/matteobaccan/owner/issues/201).
- [ ] **Indexed keys**, `list[0]`, `list[1]`, complementing the `Map` grouping added in 2.0.0. SmallRye
      and Gestalt have it. Issue [#48](https://github.com/matteobaccan/owner/issues/48).
- [ ] **Remote and cloud sources** — S3, Vault, Consul, JNDI — as loaders in `owner-extras`, which is
      what that artifact is for now that it holds nothing else. Gestalt covers all of these. Issues
      [#130](https://github.com/matteobaccan/owner/issues/130),
      [#143](https://github.com/matteobaccan/owner/issues/143).
- [ ] **GraalVM native image**: ship reachability metadata for the dynamic proxies and write the chapter.
      Defensive rather than competitive — the proxy is our design and Coat wins on this ground by
      construction — but today someone trying it hits the wall unaided and leaves.
- [ ] **Dependency injection**: a documented, supported way to obtain a Config from Spring, CDI or Guice.
      Every framework has its own story and we have none written down. Issues
      [#222](https://github.com/matteobaccan/owner/issues/222),
      [#147](https://github.com/matteobaccan/owner/issues/147).
- [ ] **Generate the properties file, or its documentation, from the mapping interface.** Quarkus
      generates a configuration reference this way. Issue
      [#3](https://github.com/matteobaccan/owner/issues/3).

### Before any of the above is called modularisation

- [ ] A real `module-info` is blocked by two things beyond the Java 8 baseline: a third party already
      declares `package org.aeonbits.owner` — `TechnologyBrewery/krausening` does it to subclass
      `DefaultFactory`, whose constructor is package-private — and would break twice over. That is also a
      signal of missing public API: "build a `Factory` with my own scheduler". Decide deliberately.

### Strategic, not tasks

- The runtime proxy is both our ergonomics and our ceiling: native image and raw speed structurally
  favour compile-time approaches. Do not change the model; do ship the metadata.
- Java 8 is the moat and the swamp. It is the only segment with no competition, and it costs us records,
  sealed types and modern switch. Keep it for 2.x; plan a 3.0 on 17 once 2.x has settled, not before.
