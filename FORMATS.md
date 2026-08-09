CONFIGURATION FORMATS
=====================

**Internal working document — not published on the project site.**

Started 2026-08-09. Where the "further formats" item of `TODO.md` stands: what each format would
cost, what the core is missing, and in which order they are worth doing. Nothing here is decided
yet; the open questions are collected at the bottom.

The companion documents are `COMPARISON.md`, which records what the other libraries support and how
that was established, and `TODO.md`, which holds the ordered backlog. This file is the level of
detail neither of those wants.


The constraint that shapes everything
-------------------------------------

**No external dependencies.** Every parser is written by hand, on the Java 8 baseline. That single
constraint reorders the whole list: HOCON, which is nearly free if we are willing to depend on
Typesafe Config, becomes the most expensive and the riskiest thing on it, while `.env` — which needs
no dependency in any scenario — moves to the front.

It also sets the standard of honesty we owe: a hand-written parser for a large format is a
*documented subset*, and it must refuse what it does not understand rather than half-read it.


What the SPI already gives us
-----------------------------

Verified against the source on 2026-08-09, because two of these were assumed wrong at first.

- **`accept(URI)` handles extension-only filenames already.** `XMLLoader` matches with
  `url.getFile().toLowerCase().endsWith(".xml")`. A file named `.env` has a path ending in `.env`,
  so the same shape of test accepts both a bare `.env` and a `local.env`. There is nothing to add.
- **A loader can be registered without being probed.** `SystemLoader.defaultSpecFor` returns `null`
  and `LoadersManager.defaultSpecs` skips nulls. So a loader can answer `@Sources("...")` without
  adding a classpath lookup to every `create()`. This is what makes "support many formats"
  affordable — see the cost note below.
- **A global and a local settings mechanism exist**, with an established naming convention:
  `ConfigFactory.setProperty("owner.key.prefix", ...)`, `owner.key.prefix.from.package`,
  `owner.nested.variable.expansion`. Global through `ConfigFactory`, local through
  `ConfigFactory.newInstance()`, which carries its own properties. Loader settings should be
  `owner.loaders.*` and nothing new needs inventing.
- **`defaultSpecs` always builds `prefix + suffix`**, where the prefix is `classpath:` plus the
  class name with dots turned into slashes. A loader is free to ignore the prefix, or to return
  `null`, so this is a convention to choose rather than a limitation to work around.
- **Query strings survive on `file:` but not on `classpath:`.** `ConfigURIFactory.newURI` passes a
  `classpath:` path to `ClassLoader.getResource`, where a `?dialect=docker` would become part of the
  resource name and fail the lookup; `file:` and other schemes go through `new URI(...)` intact.
  Per-source options in the URI therefore work today for files and need a small change for the
  classpath.
- **The specs are variable-expanded** before use, so `${...}` already works inside a source spec.

The cost to keep in mind: without `@Sources`, one spec per registered loader is probed on the
classpath for every configuration interface loaded. Three loaders, three lookups; ten loaders, ten.


What the core is missing
------------------------

| # | Gap | Needed by | State |
|---|---|---|---|
| **C1** | Indexed keys / lists (`list[0]`) — issue #48 | JSON, YAML, TOML, HOCON, CBOR | **missing.** The universal blocker for every tree-shaped format |
| **C2** | A documented flattening convention, with escaping for keys that contain a dot | every tree-shaped format | exists *de facto* in `XMLLoader`, undocumented, unshared, no escaping |
| **C3** | Explicit `null` | JSON, YAML, CBOR | **missing.** `Properties` cannot hold a null value. TOML has no null; `.env` and INI have none |
| **C4** | ~~Extension-less `accept()`~~ | — | **withdrawn.** `.env` is a file that is all extension, and the existing test shape matches it |
| **C5** | More than one extension per format | YAML (`.yaml`/`.yml`), HOCON (`.conf`), INI (`.ini`/`.cfg`) | `defaultSpecFor` returns a single `String`. An additive `defaultSpecsFor` would do it, and the SPI must stay compatible — two external projects implement `Loader` by hand |
| **C6** | Loader enablement and per-loader options | all of them | **most of it exists**: `owner.*` settings plus a `defaultSpecFor` that may return `null`. Missing: `ServiceLoader` discovery and the `owner.loaders.*` names |
| **C7** | Binary payloads and non-string keys | CBOR | missing: byte strings need base64 or hex, integer keys need a canonical form |
| **C8** | Selecting a document or a section | YAML multi-document, TOML `[[array of tables]]`, INI sections | a loader option, so **C6** |
| **C9** | Duplicate keys and merge policy | HOCON merges, TOML forbids, INI varies, JSON leaves it undefined | a loader option, so **C6** |
| **C10** | Strict mode — refuse unsupported constructs loudly | YAML above all | a loader option, so **C6** |

C8, C9 and C10 collapse into C6. What is left is two pieces of core work — **C1 + C2**, the data
model, and **C6**, the keystone — and then parsers.

C1 + C2 is not new debt. `XMLLoader.endElement` calls `props.setProperty(key, value)` with no index,
so `<tag>a</tag><tag>b</tag>` under one parent silently overwrites the first value. The same work
fixes that.


The formats
-----------

Line counts are estimates for a hand-written parser only; tests run another 1.5–2×.

| Format | Formal spec | Lines | Risk | What makes it hard |
|---|---|---:|---|---|
| `.env` | none | 150–250 | low | No standard at all; the dialects genuinely disagree. See below |
| INI | none | 150–250 | low | Same: `;` or `#`, `=` or `:`, nested `[a.b]`, duplicates |
| JSON | RFC 8259 | 350–450 | low | Nothing subtle: `\uXXXX`, surrogate pairs, numbers. JSONTestSuite exists to check against |
| CBOR | RFC 8949 | 450–650 | low-medium | A small deterministic spec — easier to get right than YAML or TOML, for all that it is binary |
| TOON | recent, unverified | ~400 | ? | See below |
| YAML (subset) | large | 700–1000 | medium | See below |
| TOML | v1.0.0 | 1200–1800 | medium-high | Dotted keys, four string forms, `_`/hex/oct/bin integers, inf/nan floats, four date-time types, inline tables, `[[arrays of tables]]`, strict redefinition rules. `toml-test` exists |
| HOCON | Lightbend | 1500–2500 | high | See below |
| YAML (complete) | — | 5000+ | out of reach | SnakeYAML is around 20k lines |
| CDDL | RFC 8610 | — | — | **Not a data format**: a schema language for CBOR and JSON. It belongs with validation, issue #201, not with loaders |
| JSONB | — | — | — | **Not a file format**: either PostgreSQL's binary column type, which is a *source* and needs a driver, or Jakarta JSON Binding, which is a mapping API |

**YAML.** The full parser is out of the question without a dependency, but the `Loader` contract
saves us from the hardest part. Because we hand back `String` to `String` and our own converters
decide the types, YAML's implicit type resolution — the "Norway problem", where `no` becomes
`false` — is work we simply do not have to do. We need structure, indentation, quoting and block
scalars, and we keep the literal scalar text. That is the difference between 700–1000 lines and
5000. The price is that we would ship a subset: no anchors or aliases, no tags, no complex keys.
Those must raise an error, never be guessed at.

**HOCON.** The hard part is not the omitted braces or the `//` comments, it is **substitutions**:
`${foo}` and `${?foo}` resolve *after* merging, may be self-referential, and have an algorithm of
their own. And HOCON's value is compatibility with the `application.conf` files people already have,
so a partial implementation that resolves a substitution differently changes the meaning of their
configuration in silence — worse than not supporting it. We do have `${}` expansion already, in
`VariablesExpander`, but with different semantics from HOCON's, which is one more trap rather than a
head start.

**TOON.** Token-Oriented Object Notation, aimed at cutting token counts when structured data is put
in an LLM prompt, optimised for uniform tabular arrays. Not intended for hand-written configuration,
and Java tooling appears to be nil. This is the least certain line in the table and needs checking
before it is either scheduled or closed.


Priority
--------

Ordered by adoption in container environments and by what users have actually asked for — not by
ease. That the first three are also among the cheapest is luck, not the criterion.

| # | Format | Where it shows up | Cost | Needs C1? | Issue |
|---|---|---|---|---|---|
| 1 | **.env** | `docker run --env-file`, compose `env_file`, Kubernetes `envFrom`, GitHub Actions, systemd, 12-factor | 150–250 | **no** — it is flat | — |
| 2 | **YAML** | Kubernetes, Helm, compose, GitHub Actions, Spring Boot, OpenAPI | 700–1000 | yes | #14, #65 |
| 3 | **JSON** | ConfigMaps, the AWS/GCP/Azure secret managers all hand back JSON, Terraform | 350–450 | yes | #240 |
| 4 | **INI** | Not growing, but `~/.aws/credentials` and `~/.aws/config` are INI, and so is git config | 150–250 | no | — |
| 5 | **TOML** | Growing — Cargo, `pyproject.toml` — but marginal in Java container work | 1200–1800 | yes | — |
| 6 | **HOCON** | Akka, Play, Kafka. Share falling against YAML | 1500–2500 | yes | — |
| 7 | **CBOR** | IoT, COSE, WebAuthn — not configuration | 450–650 | yes, plus C7 | — |
| 8 | **TOON** | LLM prompts — not configuration | ~400 | yes | — |

`.env` leads on merit and not only on cost: it is the most widespread configuration format in
container work, and it is **the only one on the list that does not need C1**, so it can ship before
any of the data-model work. It also sits next to something we already have, `system:env` in
`SystemLoader`.


A plan in phases
----------------

0. **`.env` alone.** Depends on nothing else here. Deliverable on its own.
1. **C6, the keystone.** `ServiceLoader` discovery plus `owner.loaders.*` over the `setProperty`
   machinery that exists. With the rule that settles the probe cost: **always registered, probed
   only on request.** `@Sources("classpath:app.yaml")` is already explicit and works immediately;
   having `config.yaml` found automatically is opt-in. Nothing costs anything to whoever uses none
   of it.
2. **C1 + C2, the data model.** Indexed lists and a documented, escaped flattening. Unblocks every
   tree-shaped format at once and repairs the XML hole.
3. **YAML, then JSON** — in order of demand rather than ease.
4. **INI, then TOML.**
5. **HOCON, CBOR, TOON if ever.**


`.env` in detail
----------------

### There is no such thing as the .env format

At least four dialects disagree, and two of them are both Docker. The headline: **`docker run
--env-file` does not strip quotes and dotenv does.** Write `NAME="Matteo"` and Docker gives you a
value of `"Matteo"`, quotes included, where dotenv gives you `Matteo`.

| | `docker run --env-file` | `docker compose` `env_file` | dotenv (Node/Python/Go) | systemd `EnvironmentFile` |
|---|---|---|---|---|
| Quotes | literal, part of the value | delimiters | delimiters | delimiters |
| `\n` inside `"…"` | literal | expanded | expanded | limited |
| `export VAR=x` | not recognised | not recognised | prefix stripped | not recognised |
| Trailing comment | no | yes | yes | yes |
| Multi-line values | no | yes | yes, inside quotes | via backslash continuation |
| `${VAR}` interpolation | no | yes | python-dotenv yes, Node only with `dotenv-expand` | no |
| Bare `VAR`, no `=` | taken from the host environment | taken from the host environment | ignored | error |

The fine detail — Docker's exact trimming, what changed between Compose v1 and v2 — is **not
verified** and must be checked against the current documentation before any of this is implemented.

### Flags with presets, rather than a choice of dialect

A dialect is not a format, it is a name for a bundle of answers. So the model is a set of
independent flags, with named presets that set them together, and every flag overridable on its own
over the preset:

- `quotes` — `strip` | `literal`
- `escapes` — `expand` | `literal`
- `exportPrefix` — `strip` | `reject`
- `inlineComments` — `allow` | `deny`
- `multiline` — `allow` | `deny`
- `interpolation` — `none` | `owner` (our own `${}`) | `posix`
- `bareName` — `fromEnvironment` | `ignore` | `error`

Presets: `dotenv` (the default), `docker`, `compose`, `systemd`.

Proposed default **`dotenv`**, because someone writing a `.env` by hand and putting quotes around a
value nearly always means them as delimiters. Docker's literal-quote behaviour is a known trap, and
a trap is a poor default even when it is faithful.

### Where the setting goes

- Global, on the convention that already exists:
  `ConfigFactory.setProperty("owner.loaders.env.dialect", "docker")`, and per-factory through
  `ConfigFactory.newInstance()`.
- Per source, in the URI query: `@Sources("file:.env?dialect=docker")`. This works today for
  `file:`; `classpath:` needs the query stripped before the resource lookup.
- Single flags override the preset: `owner.loaders.env.quotes=literal`.

An in-file directive — a first-line `# owner:dialect=docker` — was considered. It travels with the
file, which is genuinely attractive, but it writes our vendor name into somebody else's `.env`.
Probably no.

### Default probing

`defaultSpecFor` should almost certainly return `null`, as `SystemLoader` does. A `.env` is rarely on
the classpath and never named after the configuration class: it lives at `file:.env` or at whatever
path the container mounts. Declaring it explicitly is right. If automatic pickup is wanted later, it
belongs behind an opt-in setting naming the location, not behind a class-name convention.


Open questions
--------------

1. **`.env` default dialect** — `dotenv` proposed. This is the only answer needed to start phase 0.
2. **Do we call a YAML subset "YAML"?** Proposed: yes in the title, no in the documentation — a
   chapter listing exactly what is in and what is out, and a hard error on anchors and tags.
3. **`list[0]` or `list.0`?** `list[0]` is what SmallRye and Gestalt use, which would align #48 with
   the field.
4. **Where do the parsers live?** `owner-extras`, as originally planned, or the core, now that they
   bring no dependencies? The core would stay dependency-free either way but would grow by a few
   thousand lines.
5. **TOON: verify or close?**
6. **Does `ServiceLoader` discovery imply enablement?** Proposed no: discovered and registered, but
   probed only when asked.
