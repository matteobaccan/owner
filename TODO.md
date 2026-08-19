TODO LIST
=========

What is left to do, and nothing else. For bugs and feature requests see
[GitHub issues](https://github.com/matteobaccan/owner/issues), which as of 2026-08-19 hold Renovate's
dashboard and nothing that is work.

**Emptied on 2026-08-18 and again on 2026-08-19.** This file had grown into a record of everything done
during the 2.0.0 work. That reasoning was not thrown away — it lives in the working documents below, in the
release notes, and in the commit that made each change — and it does not belong here: a list where finished
work outnumbers open work stops being read.

Everything above the rule is a task. Everything below it is a **decision**, kept so that it is not taken
again from scratch, and no part of it is waiting on anybody.

| Document | What it holds |
|---|---|
| `INCLUDES.md` | #165: **why** each decision went the way it did — the feature itself is on the site |
| `FORMATS.md` | what each format costs and the questions each one raised |
| `CRYPTO.md` | the cipher, the marker, the field, and what was deliberately not shipped |
| `COMPARISON.md` | what the rest of the field does, verified against their sources |
| `RELEASING.md` | the release procedure — including running the previous release's suite against the new code |
| `WRITING.md` | the rules of the file this library writes back |

BEFORE 2.0.0 GOES OUT
---------------------

- [ ] **Move publishing off OSSRH.** Sonatype retired `oss.sonatype.org` in favour of the Central Portal at
      `central.sonatype.com`, and three places in `pom.xml` still point at the old one: both
      `distributionManagement` urls and the `nexus-staging-maven-plugin`, which the Portal does not support
      at all — the replacement is `central-publishing-maven-plugin`. **As it stands the release fails at the
      deploy step**, which is the worst moment to find out. `RELEASING.md` has the detail; verify against
      Sonatype's current documentation rather than that note, because this moves.
- [ ] **`distributionManagement/site` still deploys over FTP to `newinstance.it`**, a host the project no
      longer controls. Harmless unless somebody runs `mvn site-deploy`. Worth doing while in the `pom.xml`
      for the item above.
- [ ] **Correct the comment at `pom.xml:31`**, which says `aeonbits.org` "no longer exists". It exists and
      belongs to somebody else now. **The maintainer asked for this to wait for the beta.**
- [ ] **The release note.** The 2.0.0 announcement is already at the top of
      `owner-site/web/src/content/docs/news.md`, behind a notice saying the version is not out yet. When
      2.0.0 is published, remove that notice and add the release date.

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

---

NOT WORK: DECISIONS, KEPT SO THEY ARE NOT TAKEN AGAIN
=====================================================

Nothing below is a task. Each item is a thing deliberately **not** built, with what would change that.

## Features decided against, and not opened as issues

Kept here rather than on GitHub because opening an issue advertises a feature to people who do not need it.
Each becomes an issue the day somebody asks.

- **A section read by key, relative to the section.** The shape is settled and has two precedents:
  Typesafe Config's `getConfig("section")` returns a configuration **rooted at that path**, Commons
  Configuration's `subset(prefix)` returns one with the **prefix stripped**. The other camp — SmallRye,
  Spring, Coat, Gestalt — puts no key-based API on a nested object at all.
  A nested interface may not extend `Accessible`, `Mutable` or `Traceable` since 2.0.0, refused when the
  configuration is created, precisely so that this stays possible: **allowing it later breaks nobody,
  correcting it later would break everybody.** Without that refusal a section answered `getProperty("host")`
  with the root's `host` — a different property, no error — and `clear()` on a section emptied the whole
  configuration.
  What has to be decided before writing it: what `store()` on a section writes and whether it can be read
  back, what `clear()` clears, what `load(InputStream)` merges, and which key `originOf` is asked with.
  `@Sensitive` needs nothing, the masking being computed over the whole tree already. **The one thing
  genuinely missing today** is that there is no public way to ask a nested object for its own path —
  `KeyPrefix` is package-private — so a section reached through a list or through an accessor taking
  arguments has a path only the caller can reconstruct.
- **A hook that lets a container build the classes named in annotations** — `@ConverterClass`,
  `@TokenizerClass`, `@PreprocessorClasses`, `@DecryptorClass`. Designed on 2026-08-18 while answering
  [#222](https://github.com/matteobaccan/owner/issues/222) and deliberately not built: one interface,
  `instantiate(Class, AnnotatedElement)`, returning `null` to mean "build it yourself", which is what
  Jackson's `HandlerInstantiator` and Bean Validation's `ConstraintValidatorFactory` both do. It is not
  built because everything asked for in eight years is already served by registering an **object** — a
  `Loader`, a `ValueHandler`, and since 2.0.0 a `Converter` by type. What would justify it is somebody
  wanting a container-built converter or decryptor **for one particular method**.
- **GraalVM native image metadata.** The chapter is written; shipping reachability metadata is not something
  this project can do, since every entry a native build needs is the *user's* code — their interface is the
  proxy's interface list, their classes are named in `@ConverterClass`, their files are the resources.
  **Reopen when somebody opens an issue about native image**: that is when a GraalVM job in CI would start
  paying for itself, and only then could anything shipped be verified. An annotation processor emitting
  metadata for the user's interfaces is the shape that would work, and is a build plugin to maintain rather
  than a file to write.

## Gaps left open on purpose

- **The terminal prompt of `EncryptTool` has no automated test** — reading a passphrase twice without echo,
  refusing an empty one, refusing two that differ. Not an oversight: a JVM under Surefire never has a
  terminal, so `Console.readPassword` is unreachable from a test. What *is* covered, and behaviourally, is
  the half that matters more: with the streams redirected and no `OWNER_PASSPHRASE`, the piped value is not
  mistaken for the passphrase — the JDK 22 `Console.isTerminal()` hazard the tool is built around. Making
  the rest reachable means a production seam, and it is **worth doing only if that seam earns its keep
  otherwise**: a parameter that exists so a test can reach a branch is a parameter that lies about the
  design.
- **There is no `module-info`**, and it is blocked by two things beyond the Java 8 baseline: a third party
  already declares `package org.aeonbits.owner` — `TechnologyBrewery/krausening` does it to subclass
  `DefaultFactory`, whose constructor is package-private — and would break twice over. That is also a signal
  of missing public API: "build a `Factory` with my own scheduler". Nothing here is called modularisation
  until that is decided deliberately.

## Strategic

- The runtime proxy is both our ergonomics and our ceiling: native image and raw speed structurally favour
  compile-time approaches. Do not change the model.
- Java 8 is the moat and the swamp. It is the only segment with no competition, and it costs us records,
  sealed types and modern switch. Keep it for 2.x; plan a 3.0 on 17 once 2.x has settled, not before.
