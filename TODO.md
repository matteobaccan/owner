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

- [ ] **Break the package cycle between `org.aeonbits.owner` and `org.aeonbits.owner.converters`.**
      `Converters.DURATION` imports `DurationConverter`, which implements `Converter` from the parent
      package, so the two now point at each other. It compiles, bundles and modularises fine — one
      artifact, one bundle, one module — but it welds the two packages together permanently and static
      analysers report the cycle. The fix reverses the direction rather than removing anything: move the
      parsing into a package-private class of the core and leave `DurationConverter` as the public
      adapter that delegates to it. `parseDuration` is already private, so no public API changes, and the
      existing tests cover it.
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
- [ ] **Further formats — YAML, TOML, JSON, HOCON — as optional `Loader`s in `owner-extras`**, with
      `ServiceLoader` discovery, since registration is programmatic only today. The SPI has existed since
      1.0.5 and a loader is a three-method class; the dependencies stay out of the core. This is the top
      reason people pick Typesafe Config over us, and two external projects have already hand-written a
      YAML loader and a JSON one against our own SPI. Issues
      [#14](https://github.com/matteobaccan/owner/issues/14),
      [#65](https://github.com/matteobaccan/owner/issues/65),
      [#240](https://github.com/matteobaccan/owner/issues/240).
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
