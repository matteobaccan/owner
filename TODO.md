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

RELEASING
---------

- [ ] Move publishing off OSSRH, once 2.0.0 is otherwise closed. Sonatype retired
      `oss.sonatype.org` in favour of the Central Portal at `central.sonatype.com`, and three places in
      `pom.xml` still point at the old one: both `distributionManagement` urls and the
      `nexus-staging-maven-plugin`, which the Portal does not support at all — the replacement is
      `central-publishing-maven-plugin`. As it stands the release would fail at the deploy step, which
      is the worst moment to find out. `RELEASING.md` has the detail. Verify against Sonatype's current
      documentation rather than that note: this moves.
- [ ] While in there: `distributionManagement/site` still deploys over FTP to `newinstance.it`, a host
      the project no longer controls. Harmless unless somebody runs `mvn site-deploy`.

WHERE TO PICK UP
----------------

Updated at the end of 2026-08-11. `FORMATS.md` and `COMPARISON.md` have the detail and the open questions;
this is the short version.

**Two of the three largest gaps closed on 2026-08-11.**

- **Nested configuration interfaces**, in the four shapes the other libraries have between them: a section,
  a list of sections from `servers[0].host`, a map of them from `servers.alpha.host`, and one asked for by
  name with a parametrized key. The flattening convention and the reader met without either being reworked,
  which was the bet made when the convention was chosen: an XML tree is now read end to end into typed
  elements. Issues #129, #2, #72, #126 and #209 are answered and **not yet closed**.
- **Origin tracking**, `Traceable` and `Origin`: which source a merged property actually came from. Issue
  #277 is answered and **not yet closed**.

Both were preceded by a survey of what SmallRye, Archaius, Coat, Gestalt, Spring and Typesafe do, and both
times it changed the design rather than confirming it — the survey is in `COMPARISON.md` so that the two
minority positions we hold can be defended instead of remembered.

**The queue below this line is now the whole of it**: JSON, then YAML; the two diagnostics still missing;
the four silent failures; and the gaps against the other libraries further down.

**C6 and C5 are done**, in three commits, and the day spent checking the open questions against Spring
Boot, SmallRye/MicroProfile and Gestalt before writing any of it paid for itself: it cancelled a third of
the work, inverted the rule at the centre of it, and turned up two silent failures nobody had noticed.

- **`673c6ee6`** — `SourceOptions`, public: one way to read the options on a source and to refuse what a
  loader does not recognise. The options live in the **fragment** now, which is a rule for every loader and
  every scheme — the query belongs to the protocol. That removed the stripping that had been losing a token
  from a `.env` fetched over HTTPS, and repaired an XML with a query string being read as a properties file.
  The `classpath:` case works like every other.
- **`8b12f8e2`** — `ServiceLoader` discovery, which **enables** what it finds. The two orderings are
  separated: head of the `accept()` list so a format loader is asked before `PropertiesLoader` takes its
  files, tail of the default-spec list so a jar on the classpath cannot make a forgotten `MyConfig.yaml`
  beat a working `MyConfig.properties`. Context class loader with a fallback, and a `CONFIG` line naming
  what was found — including when nothing was.
- **`512ab586`** — C5: a format may go by more than one name, as additive default methods, so no
  implementation of `Loader` anywhere needs a change or a recompilation.

**Cancelled, not postponed**: the `owner.loaders.*` settings and any name on `Loader`. Nobody in the field
has a settings namespace for a loader, and the single thing ours was to express stopped existing once
discovery was made to enable.

**`null` is settled by being moved**: the core keeps its three states and learns nothing new, and a format
that has a null decides in its own loader, as an option of that format. Two core rules already constrain
that decision and are written down in `FORMATS.md` so the JSON loader does not rediscover them.

INI IS DONE, AND WHAT IT PROVED
------------------------------

**Shipped 2026-08-10**, `c3fab93b`. It went ahead of JSON and YAML for reasons argued in `FORMATS.md`, and
the ones that were bets came good: C6 got its first consumer and held, C5 got the two extensions it was
built for, and the duplicate-key question — **C9** — is now answered for YAML, TOML and HOCON as well.

The rules were settled against five implementations before a line was written, and they disagree three ways
on the only question that matters. A repeated key is a **list** here, because that is what this library
already does with a repeated XML element; reading the same shape two ways would have been the incoherence.

The `python` dialect answered a question worth remembering: **the rules were cheap and the name was
expensive.** `ConfigParser` interpolates `%(name)s` and we never will, so the preset refuses such a value
rather than handing back the literal — five lines that keep a file from meaning one thing to Python and
another here.

WHAT IS NEXT
------------

**JSON**, and it arrives with everything cleared out of its way. Its own question is framed rather than
open — `null` is its decision, not the core's, and the two rules the core imposes on that decision are
written down in `FORMATS.md`. The condition that stood behind it is met: a JSON or YAML source holding a
list of objects flattens to `servers[0].host` and is now **read**, so YAML is worth having the day its
loader is written. JSON first all the same, because it is the cheaper of the two and it tells us whether
C1 and C2 were designed right while the reasoning is still fresh.

Second, and the one that would close the most support questions: **the rest of a configuration that
explains itself**. Three of its five lines shipped with #170; the two left are below.

Third, **the five issues answered on 2026-08-11 and still open**: #129, #2, #72, #126, #209 and #277. Each
wants the usual comment — what landed, what it does exactly, and what it deliberately does not do.

A CONFIGURATION THAT EXPLAINS ITSELF
-----------------------------------

`WARNING` says something went wrong; `CONFIG` says what was decided. The four silent failures further down
are the first kind. These are the second, and they matter more, because most of "it does not work" is not a
failure at all — everything succeeded, just not on the file or the key somebody thought.

One switch, `org.aeonbits.owner.level = CONFIG`, and the library says what it did. In the order of how many
real questions each would close — **the first three shipped with
[#170](https://github.com/matteobaccan/owner/issues/170); the last two are what is left**:

- [x] **Which sources were resolved, and which one answered** — **done**, with the two lines
      `PropertiesManager.toURIs` now writes: what was looked for, and which of those was not there. It
      subsumes the second silent failure below, since a spec that `newURI` cannot resolve is dropped with
      `if (uri != null)` and never reaches a loader; that one is therefore *visible* now, though still not
      a warning.
- [x] **Which loader answered for each source** — **done**, `Reading %s with %s` in `LoadersManager`, plus
      a line naming the loaders discovered on the classpath, including when none were.
- [x] **The specs looked for when there is no `@Sources`** — **done**, the same line as the first: it says
      `no @Sources, looking for:` and lists them.
- [ ] **The effective key prefix**, from `KeyPrefix`, `@Prefix` or `@DisableFeature(PREFIX)`. A wrong prefix
      makes *every* property vanish at once, which is the most disorienting failure there is and the least
      visible: nothing errors.
- [ ] **Hot reload: whether it is on, of which kind, how often, and on which sources.** "I changed the file
      and nothing happened" — because `ASYNC` watches only `file:` sources, or the source is inside a jar.

Two rules, and the first was nearly missed: **a URI can carry credentials.** `https://user:pass@host/app.properties`
is legal and used, and it is exactly what these lines would print. Every URI that reaches a log goes through
something that blanks the userinfo first — the existing rule about never logging a *value* does not cover
this, because here the secret is in the *source*. And **at `CONFIG` the "say it once" rule inverts**: it is
off unless somebody turned it on to look into something, so seeing every reload is the reason it was turned
on. What is decided once — prefix, hot reload, discovery — is said at creation; what depends on the load is
said per load.

Worth doing together with the four `WARNING` items below: they are one reading of the same code paths and
the same two rules.

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
- [x] ~~**XML that is malformed and is not in the Java properties format.**~~ — **done 2026-08-11**, and
      the entry above described both the cause and the cure wrongly, which a probe found before a line was
      written. Throwing outright breaks **every** ordinary XML: with validation on, a parser reports a
      validity error for any document that declares no grammar — *no grammar found* — and that is what the
      `isJavaPropertiesFormat` test was really shielding. Nor were the properties "partial": a validity
      error is recoverable, the parse runs to the end, and the caller got *more* than the grammar allows,
      not less. The rule now is whether the document **declares a grammar**, read off its `DOCTYPE`; an
      external DTD counts as none, since the hardening neutralizes it and a document cannot be held to a
      rule we refused to read. `#validate=false` on the source is the way out, for either kind of grammar.
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

- [x] **Nested configuration interfaces** — `ServerConfig server();` resolving `server.host`,
      `server.port`. Was the largest visible gap: SmallRye, Gestalt and Coat all have it. **Done
      2026-08-11**, `NestedProperties`: the accessor names the section, the nested object shares its
      parent's `PropertiesManager` and loads nothing of its own, the objects are built when the
      configuration is created so that `@Mandatory` one level down is checked then and a cycle is refused
      there. Also `List<ServerConfig>` from `servers[0].host` — which closes the loop with the flattening
      convention, an XML tree now being read end to end — `Map<String, ServerConfig>` from
      `servers.alpha.host`, and `@Key("servers.%s") ServerConfig server(String name)`, which together
      answer [#126](https://github.com/matteobaccan/owner/issues/126) and
      [#209](https://github.com/matteobaccan/owner/issues/209) without the by-hand recipe.
      Issues [#129](https://github.com/matteobaccan/owner/issues/129),
      [#2](https://github.com/matteobaccan/owner/issues/2),
      [#72](https://github.com/matteobaccan/owner/issues/72) **can be closed**.
      Two decisions to carry: a `@Prefix` on a nested interface **composes** with the path, where the
      factory prefix is overridden by it (Gestalt composes, SmallRye and Archaius ignore the annotation
      outright — see `COMPARISON.md`); and an `Optional` section is present as soon as anything is below
      its path, defaults included, which is what SmallRye settled on in its version 3 after shipping the
      opposite. `@Mandatory` on the accessor of a section is refused for the same reason.
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
- [x] **Origin tracking** — which source a merged property actually came from. **Done 2026-08-11**:
      `Traceable`, a fourth interface of the `Accessible` family, and `Origin`, which says the kind
      (`SOURCE`, `IMPORT`, `DEFAULT_VALUE`, `RUNTIME`) and names the source with its credentials masked.
      `LoadType.load` now reads each source into a map of its own so that what each contributed is known
      while it still can be, and under `MERGE` the origin recorded is the source whose value survived. The
      set of purely-defaulted keys added the same day was the degenerate case of this and is gone.
      Issue [#277](https://github.com/matteobaccan/owner/issues/277) **can be closed**; related to
      [#170](https://github.com/matteobaccan/owner/issues/170), which asked for the same information as a
      log rather than as an API.
      **Not done, and the obvious next ask**: the line number a value came from, which Spring and Typesafe
      both carry. It needs every loader to report positions, and `Origin` was made a type rather than a
      `String` so that it can grow one without a second API.
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
