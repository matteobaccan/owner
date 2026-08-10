TODO LIST
=========

This file is used to keep track of things that should be done, sometime next.
For bugs and features request please see [GitHub issues](https://github.com/matteobaccan/owner/issues).

WEBSITE
-------

- [x] Write documentation for pre processing feature (see [Preprocessors](https://matteobaccan.github.io/owner/docs/preprocessors/))
- [x] Write documentation for JMX support (see [JMX support](https://matteobaccan.github.io/owner/docs/jmx/))
- [ ] Update the release note: the 2.0.0 announcement is already on the site, at the top of
      `owner-site/web/src/content/docs/news.md`, behind a notice saying the version is not out yet.
      When 2.0.0 is published, remove that notice and add the release date.

WHERE TO PICK UP
----------------

Updated 2026-08-10. `FORMATS.md` has the detail and the open questions; this is the short version.

**C6 is the one being built**, chosen over JSON and over nested interfaces. Before any of it was written,
a day went into checking the open questions against Spring Boot, SmallRye/MicroProfile and Gestalt rather
than deciding them from taste, and that changed the work itself: **a third of C6 no longer exists**, and
the rule at the centre of it is the opposite of what was planned. The three pieces left, in order:

1. **One shared way of reading the options on a source, and refusing an option it does not recognise.**
   `DotEnvLoader` already refuses; `PropertiesLoader` and `XMLLoader` do not so much as strip a query,
   so a misspelt `?dilaect=docker` passes in silence today. Together with the `classpath:` query, which
   currently resolves no resource and reports nothing — a silent failure of the kind 2026-08-09 was spent
   removing. Spring lived this exact story in their #17241, and the lesson to copy is not only "refuse"
   but **"put the likely cause in the message"**, because the day strictness arrives it hits hardest the
   people whose configuration was already broken.
2. **`ServiceLoader` discovery, which enables what it discovers** — the field is unanimous on that and we
   were about to do the opposite. The safe shape for us is to split two orderings that are one list
   today: head of the `accept()` list, tail of the default-spec list, so a jar added to the classpath
   cannot make a forgotten `MyConfig.yaml` win over a working `MyConfig.properties`.
3. **C5, more than one extension per format**, in the same additive touch to the SPI.

**Cancelled, not postponed**: the `owner.loaders.*` settings and any name on `Loader`. Nobody in the field
has a settings namespace for a loader, and the single thing ours was to express stops existing once
discovery enables.

**`null` is settled by being moved**: the core keeps its three states and learns nothing new, and a format
that has a null decides in its own loader, as an option of that format. Two core rules already constrain
that decision and are written down in `FORMATS.md` so the JSON loader does not rediscover them.

**Two small things left on the floor**, neither urgent:

- [ ] The two open [code scanning alerts](https://github.com/matteobaccan/owner/security/code-scanning),
      #235 and #218, both `java/internal-representation-exposure`. Read on 2026-08-09 and both are false
      positives: the fields are already an unmodifiable view over a defensive copy, and the analysis that
      still reports them ran on a commit that contains the fix. They want dismissing with that reason,
      which is a maintainer's call rather than a change to make.
- [x] ~~`PropertiesLoader` wraps its stream in an `InputStreamReader` that is never closed~~ — **done
      2026-08-10**, with try-with-resources on both, as `7af2529` did for `DotEnvLoader`. No descriptor
      was being lost, so nothing observable changes; what changes is that each of the two now closes
      what it opened, which is the rule the next loader will follow.

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
      builds another proxy. **Gained a second reason on 2026-08-09**: the flattening convention now
      produces `servers[0].host` out of any tree-shaped source and nothing can read it, so a JSON or YAML
      file holding a list of objects — which is most of them — will flatten correctly and be unreachable
      until this lands. Issues [#129](https://github.com/matteobaccan/owner/issues/129),
      [#2](https://github.com/matteobaccan/owner/issues/2),
      [#72](https://github.com/matteobaccan/owner/issues/72).
- [ ] **Further formats as `Loader`s, written by hand, with no external dependency.** The SPI has
      existed since 1.0.5, a loader is a three-method class, and two external projects have already
      hand-written a YAML loader and a JSON one against it. Being properties-only is the top reason
      people pick Typesafe Config over us. **`FORMATS.md` holds the whole analysis** — what each format
      costs, what the core is missing, and the order.
      **Done 2026-08-09**: `.env` in the core, `DotEnvLoader` with an `EnvDialect` of seven rules and
      three presets, `docker` by default (`d04c500`); indexed keys (`aace753`); and the flattening
      convention, `PropertyKeys`, with `XMLLoader` emitting indices for repeated elements (`d77165c`).
      **The data model is no longer the blocker** — what queued behind it can now be written. What is
      left: loader enablement and per-loader options, then JSON, then YAML, then INI and TOML. Issues
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
- [x] **Indexed keys**, `list[0]`, `list[1]`, complementing the `Map` grouping added in 2.0.0. Done
      2026-08-09: an indexed key wins over a single value, the elements are not tokenized, and a gap in the
      sequence is refused rather than closed up. `XMLLoader` emits them for repeated sibling elements,
      which is a change of behaviour and is in the release note draft. The reasoning, and what the other
      three libraries do instead — they disagree with each other — is in `FORMATS.md`.
      Issue [#48](https://github.com/matteobaccan/owner/issues/48) **can be closed**, saying why the 2013
      refusal no longer holds: it was right while properties was the only format, and JSON or YAML cannot
      express a list without this.
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
