COMPARISON WITH OTHER CONFIGURATION LIBRARIES
=============================================

**Internal working document — not published on the project site.**

This file is a snapshot taken on **2026-08-07**, for our own use in deciding what to build next.
It is deliberately not part of the documentation: a comparison of somebody else's API dates fast,
and this project is the proof — every page on the web comparing OWNER to something else still names
cfg4j as *the* alternative, and cfg4j has not seen a release since 2017. Statements about OWNER
belong on the [why page](owner-site/site/docs/why.md) and stay true; statements about other people's
libraries belong here and need re-checking before they are relied on.

Everything below was verified against the projects themselves — the GitHub API for release dates,
the actual source files for the APIs — rather than taken from articles.

**Amended 2026-08-09**, in the two places marked with that date: what SmallRye does with a `.env`,
checked in its source while deciding our own, and the backlog line on formats, which the analysis in
`FORMATS.md` has superseded. Everything else still dates from the snapshot above and still needs
re-checking before it is relied on.


The landscape
-------------

| Library | ★ | Last release | State |
|---|---|---|---|
| Typesafe / Lightbend Config | 6310 | 1.4.9 — Jun 2026 | alive, the de facto standard |
| Netflix Archaius | 2496 | 2.8.8 — Jun 2026 | alive |
| **OWNER** | **939** | **1.0.12 — Jun 2020** | alive, 2.0.0 in preparation |
| cfg4j | 555 | 4.4.1 — **Jul 2017** | **dead** (last push 2022) |
| Configurate (Sponge) | 466 | 4.2.0 — Feb 2025 | alive |
| NightConfig | 284 | 3.9.0 — Jun 2026 | alive |
| Apache Commons Configuration | 215 | — | alive (pushed Aug 2026) |
| SmallRye Config | 206 | 3.18.1 — Jul 2026 | alive, very |
| avaje-config | 108 | 5.2 — Jun 2026 | alive |
| Gestalt | 102 | 0.38.0 — Jun 2026 | alive, growing |
| Coat | 20 | — | alive (pushed Aug 2026) |
| Apache Tamaya | — | — | **retired to the Apache Attic** |

Star counts understate the Apache mirrors. The figure that matters for us is the third row: we have
the third largest installed base in the field and the oldest release of anyone in it.


The four that do what we do
---------------------------

"Interface + proxy = typed configuration" is no longer ours alone.

- **SmallRye Config `@ConfigMapping`** — the real competitor, because it implements MicroProfile
  Config and is therefore the default in Quarkus and Open Liberty: those users already have it.
  Nested groups, configurable naming strategy (kebab-case by default), `Map` with dynamic keys,
  indexed collections `prop[0]`, `Optional`, a `Secret<T>` type excluded from listings, Bean
  Validation, interface hierarchies with overrides, `@WithConverter`.
- **Netflix Archaius `ConfigProxyFactory`** — nearly our design: `@Configuration(prefix)`,
  `@PropertyName`, `@DefaultValue`, `default` methods, parametrized methods. The philosophical
  difference is that *their* default is dynamic — every call re-reads the current value,
  `immutable()` freezes — where ours is static with `@HotReload` as opt-in.
- **Coat** — generates the code at compile time with an annotation processor instead of proxying.
  No runtime dependency, no reflection, natively GraalVM-friendly. It attacks our most exposed
  flank.
- **Gestalt** — the most ambitious of the lot and the one to watch. Binds to interfaces, records,
  beans and Kotlin data classes; sources from files, env, K8s secrets, Git, S3, Azure Blob, GCS,
  Vault; 40+ decoders; load-time `${}` and run-time `#{}` substitution; `$include` for config trees;
  reload on file watch and on a timer; temporary secrets that expire after N reads; Micrometer
  metrics; zero-dependency core and JPMS support.

Other models, briefly: **Typesafe Config** (HOCON) leads on adoption but is a node tree, not a typed
binding. **Commons Configuration 2** covers every format and has reload and events, but pulls in
commons-lang3/text/beanutils and its API is `getString(key)`. **Configurate** and **NightConfig** are
strong on formats (TOML, YAML, HOCON, JSON) but neither starts from an interface. **avaje-config** is
modern and light but is a static `Config.getInt(...)` API. **Spring Boot** and **Micronaut** are not
competitors: they are frameworks that bring their own configuration.


What is ours
------------

Checked against all of the above:

- **Transactional reload with rollback** (`TransactionalReloadListener`, `RollbackBatchException`):
  a listener can refuse a reload and restore the previous state. **Found in no other library.**
- **Zero runtime dependencies in the core** — verified in the pom: test scope only.
- **Java 8 baseline.** Coat needs 11, Gestalt 11, SmallRye 17. We are the only modern option for
  anyone still on 8.
- **Encryption of values built in** (`@EncryptedValue`, `@DecryptorClass`), with no cloud module.
- **JMX**, **preprocessors**, **`Mutable`/`Accessible`** (we write, the others mostly only read),
  **parametrized properties**, **`@DisableFeature`** granularity, **prefix derived from the package**.
- BSD licence, no framework lock-in.
- **A `.env` whose dialect you choose** (added 2026-08-09; see `FORMATS.md`). Reading a `.env` is not
  ours — SmallRye does it, and so does a handful of standalone libraries. Being able to say *which*
  rules to read it by appears to be: SmallRye hands the file to `java.util.Properties.load` and takes
  what comes, and the dotenv ports each implement one dialect and only that one. Since `docker run
  --env-file`, Compose and the dotenv family genuinely disagree, and the same file is often read by
  more than one of them, having the reader say so is worth something. Small, but real, and it cost
  no dependency.


ByteSize against the equivalents
--------------------------------

State after the hardening of 2026-08-07.

| | OWNER `ByteSize` | Spring `DataSize` | Typesafe `ConfigMemorySize` | Quarkus `MemorySize` |
|---|---|---|---|---|
| Public types | 3 (+`ByteSizeUnit` 17 const., `ByteSizeStandard`) | 2 (+`DataUnit` 5 const.) | 1 | 1 |
| Internal representation | `BigDecimal` + unit | `long bytes` | `BigInteger` | `BigInteger` |
| **Keeps the written form** | **yes** | no | no | no |
| SI vs IEC | **distinguished** (KB=1000, KiB=1024) | no: "KB" is 1024 | in the parser | none |
| Fractions (`0.5 GB`) | **yes** | no | no | no |
| Range | unbounded | `long`, ~8 EB | unbounded | unbounded |
| `final` + immutable | yes | yes | yes | yes |
| `equals`/`hashCode` consistent | yes | yes | yes | yes |
| `Comparable` | yes | yes | no | yes |
| `Serializable` | yes | yes | no | no |
| Stream validation | **yes** (`readObject`) | n/a | — | — |
| Rejects null arguments | yes | n/a | n/a | n/a |
| Locale-independent parsing | yes | yes | yes | yes |
| Convert to a named unit | `convertTo(unit)` | `toKilobytes()`, … | no | no |
| **Pick the unit for me** | **`in(standard)`** | no | no | no |
| Arithmetic | no (deliberate) | no | no | yes |
| Negatives | accepted | accepted + `isNegative()` | — | accepted + `MINUS_1` |

Notes worth keeping:

- Spring also exposes a unit enum, so a unit model is not eccentric. But `DataSize` holds a single
  `long bytes` field: the unit is a construction and accessor flavour, not part of the value.
  Keeping the written form is ours alone.
- Spring labels 1024 bytes `KB`. We are the only one of the four that names the two families
  correctly.
- Arithmetic was considered and dropped. Three of the four do not have it; the one that does uses it
  internally for the framework, and pairs it with a `MINUS_1` "unlimited" sentinel that its own
  arithmetic cannot handle (`-1 + 1 MB` = 999999 bytes).
- One known defect survives that decision: `equals` compares the **rounded** byte count, so
  `0.4 B` equals `0.6 B` and `compareTo` agrees. It only bites on sub-byte fractions. The fix, if it
  is ever wanted, is to require a whole number of bytes at construction, which would also make an
  arithmetic correct by construction.


What the gaps line up with
--------------------------

Our open issues are, almost one for one, the features the others have shipped. That is not a
coincidence; it is evidence the demand is real.

| Gap | Who has it | Our issue |
|---|---|---|
| Nested config interfaces | SmallRye, Gestalt, Coat | #129, #2, #72 |
| YAML / JSON / HOCON / TOML | everyone but us | #14, #65, #240 |
| `.env` files | SmallRye, Spring via env, the dotenv ports | — (**closed 2026-08-09**) |
| Bean Validation (JSR-380) | SmallRye, Gestalt, Spring | #201 |
| Relaxed binding / kebab-case | SmallRye, Gestalt, Spring | #116 |
| Indexed keys `list[0]` | SmallRye, Gestalt | #48 |
| "Which source provided this?" | Spring (origin tracking), Gestalt | #277 |
| Cloud sources (S3, Vault, Consul) | Gestalt, cfg4j | #130, #143 |
| DI integration | every framework | #222, #147 |
| GraalVM native image | Coat, by construction | — |


Field evidence
--------------

- **Nobody uses `ZooKeeperLoader`.** Every apparent hit in a code search is our own build logs,
  mirrored inside research datasets. It was undocumented until 2026-08-07.
- **The `Loader` SPI *is* used**, contrary to what we assumed. Two external implementations found:
  `krevelen/coala-binder` → `io.coala.config.YamlLoader`, and `ansonliao/Selenium-Extensions` →
  `com.github.ansonliao.selenium.json.JsonLoader`. A YAML loader and a JSON loader, hand-written by
  two projects that do not know each other — exactly what we would ship. They live in their own
  packages and import only `org.aeonbits.owner.loaders.Loader`, so that interface must not move.
- **Someone squats in our root package.** `TechnologyBrewery/krausening` declares
  `package org.aeonbits.owner;` in `KrauseningConfigFactory` so it can subclass `DefaultFactory`,
  whose constructor is package-private. A real `module-info` in 3.0 breaks them twice over — split
  package, and no access. It is also a signal of missing public API: "build a Factory with my own
  scheduler". Decide deliberately before modularising.


Backlog, highest value first
----------------------------

1. **Nested config interfaces** (`ServerConfig server();`) — the largest visible gap against
   SmallRye, and `@Prefix` from 2.0.0 already does half the work: key composition exists, what is
   missing is a return type that builds another proxy.
2. **Further formats** — a loader is a three-method class, the SPI has existed since 1.0.5, and people
   are already writing these by hand (see above). Removes the "properties only" objection, which is
   the top reason people pick Typesafe Config. **`FORMATS.md` supersedes this line**: it was written
   here as "YAML/JSON/TOML as optional loaders in owner-extras", and the analysis that followed
   changed both halves of that. No external dependency means every parser is ours to write, which
   makes HOCON the most expensive item rather than the cheapest; and `.env`, not YAML, turned out to
   be the place to start — it is the most widespread format in container work and the only one
   needing none of the data-model work. It shipped on 2026-08-09, in the core.
3. **Origin tracking** (#277).
4. **Configurable naming strategy** (#116) — hooks into the factory-prefix machinery from 2.0.0.
5. **Bean Validation** (#201) as an optional module, never in the core.
6. **GraalVM reachability metadata** plus a documentation chapter — defensive: today people hit the
   proxy wall and leave for Coat.


Two strategic risks
-------------------

**The runtime proxy** is our ergonomics and our ceiling: native image and raw speed structurally
favour Coat and Micronaut. Do not change the model; do ship the metadata.

**Java 8 is both the moat and the swamp.** It is the only segment where we have no competition, and
it costs us records, sealed types and modern switch. Keep it for 2.x, plan a 3.0 on 17 once 2.x has
settled — not before.
