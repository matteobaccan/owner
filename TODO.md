TODO LIST
=========

Things that should be done, sometime next. For bugs and feature requests see
[GitHub issues](https://github.com/matteobaccan/owner/issues) — which, as of 2026-08-19, hold Renovate's
dashboard and nothing else that is work: #165 is built and waits only on its documentation.

**Emptied on 2026-08-18.** This file had grown into a record of everything done during the 2.0.0 work, with
the reasoning behind each decision. That reasoning was not thrown away: it lives in the working documents
beside this one, in the release notes, and in the commit that made each change. What is left here is what
is left to do.

| Document | What it holds |
|---|---|
| `INCLUDES.md` | #165: the four decisions, why each of them, and what the tests found |
| `FORMATS.md` | what each format costs and the questions each one raised |
| `CRYPTO.md` | the cipher, the marker, the field, and what was deliberately not shipped |
| `COMPARISON.md` | what the rest of the field does, verified against their sources |
| `RELEASING.md` | the release procedure — including running the previous release's suite against the new code |
| `WRITING.md` | the rules of the file this library writes back |

WEBSITE
-------

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
- [ ] Correct the comment at `pom.xml:31`, which says `aeonbits.org` "no longer exists". It exists and
      belongs to somebody else now — the deferred half of the 2026-08-18 finding that every old
      documentation link is unrecoverable. **The maintainer asked for this to wait for the beta.**

THE ONE FEATURE LEFT
--------------------

- [x] **Inheritance between properties files**, [#165](https://github.com/matteobaccan/owner/issues/165):
      a file naming the file it builds on. **Built 2026-08-19**, specification and record in `INCLUDES.md`.
      The hard part named there was real and is done: the watched set is worked out again after every load,
      so a file that starts naming an include gets it watched and one that stops naming it stops.
      **50 tests** across `owner`, `owner-formats` and `owner-extras`; the formats module needed no code
      at all. **Documented**: `docs/includes.md` on the site, one example per format, and the pair that
      sounds like one question — the position of the directive means nothing, the order inside it means
      everything.
- [x] **What an include's path is relative to** — decided and built 2026-08-19, **Spring's rule**, which is
      also C's: a spec naming a scheme is fixed, a spec naming none is looked for beside the source that
      named it, and it chains. It cost nothing in compatibility, the schemeless form having been an error
      before. Works inside a jar, where a leading `/` means the jar's own root — `URI.resolve` would have
      failed there in silence, a `jar:` URI being opaque, so the resolution goes through `java.net.URL`, as
      Spring's and Commons' do. `@Sources` deliberately untouched. See `INCLUDES.md`, decision 7.

WORTH DOING, NOBODY WAITING
---------------------------

- [ ] **Remote and cloud sources** — S3, Vault, Consul — as loaders in `owner-extras`, where JNDI and
      ZooKeeper already live. Gestalt covers all of these.
      Issue [#130](https://github.com/matteobaccan/owner/issues/130). The rule JNDI settled and this would
      inherit: **a source that carries its own scheme is a configuration file turning into a request to
      somebody else's server**, so the loader refuses the remote form and the way in is Java code — the
      same rule as the encryption passphrase.
- [ ] **The line a value came from.** `Traceable` says which *source* answered; Spring and Typesafe both
      carry the line number too. It needs every loader to report positions, and `Origin` was made a type
      rather than a `String` precisely so that it can grow one without a second API.
- [ ] **Generate the documentation of a configuration** from the mapping interface — the other half of
      `TemplateTool` and a different product. Quarkus generates a reference from `@ConfigMapping`, Spring
      generates `spring-configuration-metadata.json` and gets IDE completion out of it; both with an
      annotation processor, at build time. Worth its own issue if it is wanted. The interesting question is
      whether to aim at Spring's JSON, that being the one with tooling already reading it.
- [ ] **The terminal prompt of `EncryptTool` has no automated test** — reading a passphrase twice without
      echo, refusing an empty one, refusing two that differ. Not an oversight: a JVM under Surefire never
      has a terminal, so `Console.readPassword` is unreachable from a test. What *is* covered, and
      behaviourally, is the half that matters more: with the streams redirected and no `OWNER_PASSPHRASE`,
      the piped value is not mistaken for the passphrase — the JDK 22 `Console.isTerminal()` hazard the
      tool is built around. Making the rest reachable means a production seam, and it is **worth doing only
      if that seam earns its keep otherwise**: a parameter that exists so a test can reach a branch is a
      parameter that lies about the design.

DECIDED, AND DELIBERATELY NOT OPENED AS ISSUES
----------------------------------------------

Kept here rather than on GitHub because opening an issue advertises a feature to people who do not need it.
Each becomes an issue the day somebody asks.

- [ ] **A section read by key, relative to the section.** The shape is settled and has two precedents:
      Typesafe Config's `getConfig("section")` returns a configuration **rooted at that path**, Commons
      Configuration's `subset(prefix)` returns one with the **prefix stripped**. The other camp — SmallRye,
      Spring, Coat, Gestalt — puts no key-based API on a nested object at all.
      A nested interface may not extend `Accessible`, `Mutable` or `Traceable` since 2.0.0, refused when
      the configuration is created, precisely so that this stays possible: **allowing it later breaks
      nobody, correcting it later would break everybody.** Without that refusal a section answered
      `getProperty("host")` with the root's `host` — a different property, no error — and `clear()` on a
      section emptied the whole configuration.
      What has to be decided before writing it: what `store()` on a section writes and whether it can be
      read back, what `clear()` clears, what `load(InputStream)` merges, and which key `originOf` is asked
      with. `@Sensitive` needs nothing, the masking being computed over the whole tree already. **The one
      thing genuinely missing today** is that there is no public way to ask a nested object for its own
      path — `KeyPrefix` is package-private — so a section reached through a list or through an accessor
      taking arguments has a path only the caller can reconstruct.
- [ ] **A hook that lets a container build the classes named in annotations** — `@ConverterClass`,
      `@TokenizerClass`, `@PreprocessorClasses`, `@DecryptorClass`. Designed on 2026-08-18 while answering
      [#222](https://github.com/matteobaccan/owner/issues/222) and deliberately not built: one interface,
      `instantiate(Class, AnnotatedElement)`, returning `null` to mean "build it yourself", which is what
      Jackson's `HandlerInstantiator` and Bean Validation's `ConstraintValidatorFactory` both do. It is not
      built because everything asked for in eight years is already served by registering an **object** — a
      `Loader`, a `ValueHandler`, and since 2.0.0 a `Converter` by type. What would justify it is somebody
      wanting a container-built converter or decryptor **for one particular method**.
- [ ] **GraalVM native image metadata.** The chapter is written; shipping reachability metadata is not
      something this project can do, since every entry a native build needs is the *user's* code — their
      interface is the proxy's interface list, their classes are named in `@ConverterClass`, their files
      are the resources. **Reopen when somebody opens an issue about native image**: that is when a GraalVM
      job in CI would start paying for itself, and only then could anything shipped be verified. An
      annotation processor emitting metadata for the user's interfaces is the shape that would work, and is
      a build plugin to maintain rather than a file to write.

BEFORE ANY OF THE ABOVE IS CALLED MODULARISATION
------------------------------------------------

- [ ] A real `module-info` is blocked by two things beyond the Java 8 baseline: a third party already
      declares `package org.aeonbits.owner` — `TechnologyBrewery/krausening` does it to subclass
      `DefaultFactory`, whose constructor is package-private — and would break twice over. That is also a
      signal of missing public API: "build a `Factory` with my own scheduler". Decide deliberately.

STRATEGIC, NOT TASKS
--------------------

- The runtime proxy is both our ergonomics and our ceiling: native image and raw speed structurally favour
  compile-time approaches. Do not change the model.
- Java 8 is the moat and the swamp. It is the only segment with no competition, and it costs us records,
  sealed types and modern switch. Keep it for 2.x; plan a 3.0 on 17 once 2.x has settled, not before.
