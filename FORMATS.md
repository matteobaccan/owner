CONFIGURATION FORMATS
=====================

**Internal working document — not published on the project site.**

Started 2026-08-09. Where the "further formats" item of `TODO.md` stands: what each format would
cost, what the core is missing, and in which order they are worth doing. The open questions are
collected at the bottom.

The companion documents are `COMPARISON.md`, which records what the other libraries support and how
that was established, and `TODO.md`, which holds the ordered backlog. This file is the level of
detail neither of those wants.

**Shipped 2026-08-09, commit `d04c500`: phase 0, `.env` in the core.** `DotEnvLoader` and
`EnvDialect` in `org.aeonbits.owner.loaders`, registered by default, 833 core tests green. Three
things about it came out differently from what the rest of this file proposed before it was written,
and each is marked *shipped* or *not built* where it belongs below. The short version: the
interpolation flag was dropped because OWNER already expands `${…}` itself, a line-continuation flag
was added in its place, and the `owner.loaders.env.*` settings were **not** built — they belong to
C6, and what carries the dialect today is registering the loader or a query on the source.

**Amended 2026-08-10, before writing C6 rather than after.** A day was spent checking the open questions
against the other libraries instead of deciding them from taste, and it paid before a line was written:
question 8 is cancelled outright — nobody in the field has per-loader settings, so neither do we and
`Loader` gets no name; question 6 is **inverted**, because all three of Spring Boot, MicroProfile/SmallRye
and Gestalt probe what they discover, and the reason they can is a difference in precedence models that
tells us exactly what to do differently; question 5 is closed and the line that stood here about TOON was
factually wrong; question 9 is answered by being moved out of the core entirely. `COMPARISON.md` holds the
evidence, each claim next to its source. The one thing to take from all of it: **two of the four checks
that day found the field in open disagreement**, which is not a failure of the method but its point — it
says which decisions have to be argued rather than aligned.


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

Verified against the source on 2026-08-09, because two of these were assumed wrong at first. **Three
of them were overtaken by C6 on 2026-08-10** and are corrected in place, since a survey that is quietly
out of date is worse than no survey.

- **`accept(URI)` handles extension-only filenames already.** A file named `.env` has a path ending in
  `.env`, so one test accepts both a bare `.env` and a `local.env`. ~~`XMLLoader` matches with
  `url.getFile().toLowerCase().endsWith(".xml")`, and there is nothing to add.~~ **There was.**
  `URL.getFile()` is the path *plus the query*, so `config.xml?v=2` failed that test, fell through to
  `PropertiesLoader` — which accepts anything it can resolve — and was read as a properties file, in
  silence. Matching now goes through `SourceOptions.path(URI)`, which also handles the opaque URIs that
  `file:.env` and a resource inside a jar both produce, and where `getPath()` is `null`.
- **A loader can be registered without being probed.** `SystemLoader.defaultSpecFor` returns `null`
  and `LoadersManager.defaultSpecs` skips nulls. So a loader can answer `@Sources("...")` without
  adding a classpath lookup to every `create()`. This is what makes "support many formats"
  affordable — see the cost note below.
- **A global and a local settings mechanism exist**, with an established naming convention:
  `ConfigFactory.setProperty("owner.key.prefix", ...)`, `owner.key.prefix.from.package`,
  `owner.nested.variable.expansion`. Global through `ConfigFactory`, local through
  `ConfigFactory.newInstance()`, which carries its own properties. ~~Loader settings should be
  `owner.loaders.*` and nothing new needs inventing.~~ **Cancelled 2026-08-10**: nobody in the field has
  a settings namespace for a loader, and the one thing ours was to express stopped existing when
  discovery was made to enable. See question 8. The mechanism is still there and still right — for
  settings that belong to the factory, which these did not.
- **`defaultSpecs` always builds `prefix + suffix`**, where the prefix is `classpath:` plus the
  class name with dots turned into slashes. A loader is free to ignore the prefix, or to return
  `null`, so this is a convention to choose rather than a limitation to work around.
- ~~**Query strings survive on `file:` but not on `classpath:`**, so per-source options work for files
  and need a small change for the classpath.~~ **Superseded 2026-08-10.** The options are not in the
  query at all: they are in the **fragment**, for every loader and every scheme, because a query on a
  remote source belongs to the server and cutting it — which is what phase 0 did, on every scheme — sent
  the request without its token. The fragment is excluded from `URL.getFile()`, so nothing has to be
  stripped anywhere; it is never sent to a server; and it is readable on an opaque `jar:` URI, where
  `getQuery()` returns `null`. `ConfigURIFactory` splits it off before the `classpath:` lookup and puts
  it back on what it resolved, so that scheme is no longer the exception either.
- **The specs are variable-expanded** before use, so `${...}` already works inside a source spec.

The cost to keep in mind: without `@Sources`, one spec per registered loader is probed on the
classpath for every configuration interface loaded. Three loaders, three lookups; ten loaders, ten.
**Measured properly on 2026-08-10, and it is paid whatever the order**: `PropertiesManager.toURIs`
resolves every spec to a URI before any of them is loaded, so `LoadType.FIRST` short-circuits the
*loading* and not the *resolution*. Putting the discovered loaders last therefore fixes precedence and
not cost. An interface that names its sources pays none of it, `defaultSpecs` never being called.

Two more rules, learnt writing the first of these loaders, that every one after it has to keep:

- **A content error must be unchecked.** `Config.LoadType.FIRST` and `MERGE` both catch `IOException`
  and move on, which is right for a source that is not there and fatal for anything else: a malformed
  file reported as an `IOException` is swallowed and the caller gets a configuration full of defaults
  with no explanation. `DotEnvLoader` raises `Util.unsupported(...)` for everything it refuses, as
  `LoadersManager.findLoader` already did, and keeps `IOException` for genuine I/O.
- **Which means a loader can now throw during a hot reload**, and
  `ScheduledExecutorService.scheduleAtFixedRate` suppresses every later run of a task that throws. That
  hole was open before — `findLoader` could already do it — and is now closed in
  `PropertiesManager.checkAndReloadKeepingTheSchedule`, but it is the reason the two rules are worth
  stating together.


What the core is missing
------------------------

| # | Gap | Needed by | State |
|---|---|---|---|
| **C1** | Indexed keys / lists (`list[0]`) — issue #48 | JSON, YAML, TOML, HOCON, CBOR | **done 2026-08-09**, `aace753`: `IndexedProperties` in the core, and `XMLLoader` emitting them for repeated elements. The universal blocker, now out of the way |
| **C2** | A documented flattening convention, with escaping for keys that contain a dot | every tree-shaped format | **done 2026-08-09**, minus the escaping: `PropertyKeys` in `org.aeonbits.owner.loaders` states the convention and is public, since a loader in another artifact will need it. The escaping was deliberately not built — see below |
| **C3** | Explicit `null` | JSON, YAML, CBOR | **withdrawn as a core gap, 2026-08-10.** `Properties` cannot hold a null value and the core will not learn to: the formats it ships have no null, and a format that has one decides for itself, as an option of that format. Two core rules constrain that decision already — see *Null, and why it is not a core rule* |
| **C4** | ~~Extension-less `accept()`~~ | — | **withdrawn.** `.env` is a file that is all extension, and the existing test shape matches it |
| **C5** | More than one extension per format | YAML (`.yaml`/`.yml`), HOCON (`.conf`), INI (`.ini`/`.cfg`) | **done 2026-08-10**, `512ab586`: `defaultSpecsFor` returning `String[]`, and `defaultSpecFor` no longer abstract — both default methods, so the two external projects implementing `Loader` by hand need neither a change nor a recompilation. INI is its first consumer |
| **C6** | Loader enablement and per-loader options | all of them | **done 2026-08-10**, `673c6ee6` and `8b12f8e2`: options on a source in the **fragment**, read and refused through the public `SourceOptions`; `ServiceLoader` discovery that enables, with the accept order and the default-spec order deliberately reversed; a `CONFIG` line naming what was discovered. The `owner.loaders.*` settings were cancelled rather than postponed — see question 8 |
| **C7** | Binary payloads and non-string keys | CBOR | missing: byte strings need base64 or hex, integer keys need a canonical form |
| **C8** | Selecting a document or a section | YAML multi-document, TOML `[[array of tables]]`, INI sections | a loader option, so **C6** |
| **C9** | Duplicate keys and merge policy | HOCON merges, TOML forbids, INI varies, JSON leaves it undefined | a loader option, so **C6** |
| **C10** | Strict mode — refuse unsupported constructs loudly | YAML above all | a loader option, so **C6** |

C8, C9 and C10 collapse into C6.

**Where that leaves things, end of 2026-08-10. Nothing in this table blocks a format any more.** The data
model shipped on the 9th — `C1` and `C2`, and with them the XML hole they were always going to fix, where
`<tag>a</tag><tag>b</tag>` used to keep only the second value. The loader plumbing shipped on the 10th —
`C6` and `C5`, in three commits, after a day spent checking the open questions against the field, which
cancelled a third of the work before any of it was written. **C3 is off the list** not by being built but by
being placed where it belongs, in the loader of a format that has a null.

What is left is `C7`, which belongs to CBOR and waits for it, and `C8`/`C9`/`C10`, which are options on a
source and therefore no longer gaps at all: the machinery for them exists, and **INI is the first format
that needs two of them** — a section to select and a policy for duplicate keys. That is a large part of why
it goes first.


The formats
-----------

Line counts are estimates for a hand-written parser only; tests run another 1.5–2×.

| Format | Formal spec | Lines | Risk | What makes it hard |
|---|---|---:|---|---|
| `.env` | none | 150–250 | low | No standard at all; the dialects genuinely disagree. See below |
| ~~INI~~ | none | **done 2026-08-10** | — | Same: `;` or `#`, `=` or `:`, nested `[a.b]`, duplicates. Three dialects; see below |
| JSON | RFC 8259 | 350–450 | low | Nothing subtle: `\uXXXX`, surrogate pairs, numbers. JSONTestSuite exists to check against |
| CBOR | RFC 8949 | 450–650 | low-medium | A small deterministic spec — easier to get right than YAML or TOML, for all that it is binary |
| ~~TOON~~ | toonformat.dev | ~400 | — | **closed 2026-08-10.** Not a configuration format. See below |
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
in an LLM prompt, optimised for uniform tabular arrays. **Verified 2026-08-10 and closed** — and half
of what stood here was wrong. The specification is public at `toonformat.dev`, and Java tooling is not
nil: there is JToon, the community implementation with Jackson integration, `json-io` has added TOON as
an output format, and there is even a Spring Boot module. What was right is the half that decides it:
TOON is an encoding for *feeding an LLM*, not a format anybody writes a configuration in by hand. It is
closed for irrelevance, not for absence of tooling, which is a different reason and would be reopened by
a different fact — somebody asking for it.


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

0. ~~**`.env` alone.**~~ **Done 2026-08-09**, `d04c500`. It depended on nothing else here and shipped
   on its own, as intended.
1. ~~**C6, the keystone.**~~ **Done 2026-08-10**, in three commits, and a day of checking the open
   questions against the field first — which cancelled a third of it before a line was written. The
   plan that stood here was `ServiceLoader` discovery plus `owner.loaders.*`, with the rule "always
   registered, probed only on request"; the verification cancelled the second half and inverted the
   rule.
   1. **`673c6ee6`** — `SourceOptions`, public: one way to read the options on a source and to refuse
      what a loader does not recognise, used by every loader in the tree. The options moved from the
      query to the **fragment**, which removed the stripping that had caused two silent failures — a
      `.env` over HTTPS losing its token, and an XML with a query read as properties — and made the
      `classpath:` case work like the others.
   2. **`8b12f8e2`** — discovery, which enables. Head of the `accept()` list, tail of the default-spec
      list, context class loader with a fallback, and a `CONFIG` line naming what was found.
   3. **`512ab586`** — C5, several names per format, as additive default methods.
2. ~~**C1 + C2, the data model.**~~ **Done 2026-08-09**, `aace753` and `d77165c`. Indexed lists, the
   flattening convention stated in `PropertyKeys`, and the XML hole repaired.
3. ~~**INI.**~~ **Done 2026-08-10**, `c3fab93b`: `IniLoader` and `IniDialect`, three dialects, eleven rules,
   34 tests. The rules were settled against five implementations before any of it was written — Python
   `configparser`, git config, systemd, Commons Configuration, the AWS SDK for Java — and they disagree
   three ways on the only question that matters, which is what a repeated key means. See below.
4. **JSON, then YAML.**
5. **TOML, then HOCON, CBOR, TOON if ever.**

### Why INI moved in front of JSON and YAML

Decided 2026-08-10. What stood here was "YAML, then JSON — in order of demand rather than ease", with INI
after them. Two things changed that had nothing to do with taste.

**The deadline that made demand the criterion is gone.** Ordering by demand was right while the risk was
that the data model — public in `PropertyKeys`, and `list[0]` already in the release note — would freeze at
the 2.0.0 release before anything with real nesting had tested it. **2.0.0 now ships at the end of the
format work**, so JSON will validate C1 and C2 before the release whatever order they are written in. The
window does not close, so nothing has to race for it.

**And C6 did not exist when that line was written.** Now it does, it is public, and it has no consumer:
nothing in the tree needs two extensions, a section, or a duplicate-key policy. INI needs all three. It is
also the second format with no standard, so it re-uses the dialect machinery `.env` proved rather than
inventing anything — which is the cheapest kind of validation there is.

The rest of the case for INI is arithmetic. It is the cheapest real format left, 150–250 lines. It belongs
in the **core** by the rule above, so it does not force question 4, the `owner-formats` artifact, which is
still open — JSON does, on the day it is written. And its repeated keys are the same shape as the repeated
XML elements already shipped, so it exercises a corner of C1 as well.

**The one thing against it, stated rather than buried: nobody has ever asked for INI.** Checked against the
issue tracker on 2026-08-10 — YAML appears in four issues (#14, #53, #34, #212), JSON in three (#240, #53,
#14), TOML in two, HOCON in one, and INI in **none, ever**. That objection would be decisive if INI shipped
on its own. It does not: every format goes out together in 2.0.0, so the order decides what *we* learn
first, not what anybody receives.

### INI in detail

Decided 2026-08-10 before writing any of it, and **shipped the same day**, as indexed keys were. The survey,
because it is the evidence the decisions rest on:

| | Comments | Separator | Duplicate key | Duplicate section | Key case |
|---|---|---|---|---|---|
| Python `configparser` | `#` `;`, inline **off** by default | `=` and `:` | **error** (`strict=True`) | **error** | folded to lower |
| git config | `#` `;`, inline on | `=` | **list** | merged | sections insensitive |
| systemd | `#` `;`, inline off | `=` | **list**, for the keys documented as such | merged | sensitive |
| Commons Configuration | `#` `;` | `=` and `:` | **list** | merged | sensitive |
| AWS SDK for Java 2.x | `#` `;` | `=` | **last wins** | merged | sensitive |

**A repeated key is a list, and not by majority.** Three of the five say list, but the reason is nearer
home: it is the answer this library gave to a repeated XML element on 2026-08-09, and reading the same
shape two ways would be the incoherence. It takes the same form too — a key occurring once keeps its plain
key, only a repeat is numbered, and the first moves to `[0]` when the second arrives — for the same reason,
which is that a parser reading a stream cannot look ahead. `error`, `first` and `last` are available.

**A section is the prefix**, which needed no invention: the dot is already `PropertyKeys.NESTING`, so
`[a.b]` and a nested structure land on the same keys and a section costs one line. Keys before any section
have no prefix; a section met twice is one section.

**The default dialect is the column every tool agrees with**: `=`, `#` and `;` at the start of a line, no
inline comments, quotes and backslashes kept, no continuation. Inline comments off is the one worth
defending: the value most likely to hold a `#` is a password, and losing half of one silently is the trade
this project keeps refusing.

**`git` earns a preset** because of subsections. `[remote "origin"]` with a `url` becomes
`remote.origin.url`, which is the key `git config` prints, so an interface written against it names what
the tool names. Nothing else in the survey has that shape.

**`python` was the interesting one, and the answer to "what does it cost".** The lexical rules are cheap and
so is `[DEFAULT]` inheritance. What is not cheap is the name: `ConfigParser` interpolates `%(name)s` by
default and we never will, since `${…}` is expanded after loading and across every source. A preset that
handed the literal back would be the first place this library promised something it does not do. So a value
holding `%(…)s` under that dialect is **refused**, naming the key and pointing at `${…}` — five lines that
turn a silent divergence into a message, which is the standard already written down for parsers: *refuse
what you do not understand rather than half-read it*.

**INI is C5's first consumer**, `.ini` and `.cfg`, one day after C5 was built with none.

Deliberately not built: partial quoting inside a value (git's `a" b"c`), and blank lines inside an indented
Python value. Both are refusals rather than misreadings, and both can be added when somebody has a file.

### The two that are not formats

- ~~**Nested configuration interfaces**, [#129](https://github.com/matteobaccan/owner/issues/129)~~ —
  **done 2026-08-11**. `servers[0].host` was produced by the flattener and read by nobody; a
  `List<ServerConfig>` now reads it, and the end-to-end test that proves it starts from an XML document
  with a repeated element and ends at the typed elements. A JSON or YAML source holding a list of objects
  is therefore reachable on the day its loader is written, which was the condition for YAML being worth
  having. Also `Map<String, ServerConfig>` for sections the file names, and a parametrized accessor for one
  known by name.
- **A configuration that explains itself** — the diagnostics, at `CONFIG` for what was decided and at
  `WARNING` for what went wrong. See `TODO.md`, where the two halves are listed together because they are
  one reading of the same code.

The honest ordering argument is that **JSON first tells us whether C1 and C2 were right** while the
reasoning is still fresh, and everything after it is cheaper for knowing.

**Chosen 2026-08-10: C6 first**, and the day of verification that went into it earned its keep before a
line was written — it cancelled a third of the work, inverted the discovery rule, and turned up two
loaders that accept a query nobody handles. JSON follows, and finds its own question about `null`
already framed.


`.env` in detail
----------------

### There is no such thing as the .env format

At least four dialects disagree, and two of them are both Docker. The headline: **`docker run
--env-file` does not strip quotes and dotenv does.** Write `NAME="Matteo"` and Docker gives you a
value of `"Matteo"`, quotes included, where dotenv gives you `Matteo`.

Verified 2026-08-09 against the sources rather than against articles, and one of them changed the
conclusion.

| | `docker run --env-file` | `docker compose` `env_file` | dotenv-java | dotenv Node/Python/Go | **SmallRye Config** | systemd |
|---|---|---|---|---|---|---|
| Quotes | **literal** | delimiters | delimiters | delimiters | **literal** | delimiters |
| `\n` inside `"…"` | literal | expanded | expanded | expanded | `Properties` escaping | limited |
| `export VAR=x` | not recognised | not recognised | **not** recognised | prefix stripped | not recognised | not recognised |
| Trailing comment | no, `#` only at line start | yes, needs `" #"` | yes | yes | no | yes |
| Multi-line values | no | no, in v2 | yes | yes, inside quotes | backslash continuation | backslash continuation |
| `${VAR}` | no | yes | no | varies | no | no |
| Bare `VAR`, no `=` | from the host environment | from the host environment | ignored | ignored | ignored | error |

**The finding that matters: SmallRye Config reads `.env` with `java.util.Properties.load()`.**
`DotEnvConfigSourceProvider` delegates to `ConfigSourceUtil.urlToMap`, which calls
`properties.load(reader)` and converts the result to a map. No quote handling of any kind. So in the
Java ecosystem — and SmallRye is the MicroProfile implementation, therefore the default in Quarkus
and Open Liberty — **the incumbent behaviour is not dotenv's.**

That choice has its own traps, and we should not copy it wholesale: `Properties.load` treats
backslash as an escape, so a Windows path loses its separators; it also accepts `:` and space as
key/value separators and treats `!` as a comment. None of that belongs in a `.env`.

Note also that there is no de facto standard to appeal to. Inline comments are the worst of it:
`TOKEN=abc#123 # comment` yields a different value in nearly every ecosystem. Node.js's own built-in
`--env-file` is yet another dialect, and diverges from the `dotenv` package it resembles.

Sources: [docker run --env-file](https://docs.docker.com/reference/cli/docker/container/run/#env),
[docker/cli#3630](https://github.com/docker/cli/issues/3630),
[moby#46773](https://github.com/moby/moby/issues/46773),
[docker/compose#8388](https://github.com/docker/compose/issues/8388),
[SmallRye DotEnvConfigSourceProvider](https://github.com/smallrye/smallrye-config/blob/main/implementation/src/main/java/io/smallrye/config/DotEnvConfigSourceProvider.java),
[dotenv-java](https://github.com/cdimascio/dotenv-java),
[nodejs/node#54134](https://github.com/nodejs/node/issues/54134),
[.env syntax comparison](https://env.dev/guides/env-file-syntax).

### Flags with presets, rather than a choice of dialect

A dialect is not a format, it is a name for a bundle of answers. So the model is a set of
independent flags, with named presets that set them together, and every flag overridable on its own
over the preset. **Shipped**, as `EnvDialect`, with seven rules:

| Flag | Settings | Query name |
|---|---|---|
| `quotesStripped` | true / false | `quotes=strip\|literal` |
| `escapesExpanded` | true / false | `escapes=expand\|literal` |
| `exportPrefixStripped` | true / false | `export=strip\|keep` |
| `inlineComments` | true / false | `comments=inline\|none` |
| `multilineValues` | true / false | `multiline=allow\|deny` |
| `lineContinuation` | true / false | `continuation=allow\|deny` |
| `bareNames` | `FROM_ENVIRONMENT` / `IGNORE` / `ERROR` | `bare=env\|ignore\|error` |

Two changes from the list this file first proposed:

- **`interpolation` was dropped.** OWNER expands `${…}` in property values itself, after loading and
  across every source, so a loader doing its own would expand them twice. No dialect interpolates.
- **`lineContinuation` was added** — a trailing backslash joining the next line, which is a
  mechanism of its own and not the same thing as a quoted value spanning lines.

Presets shipped: **`docker` (the default)**, `dotenv`, `compose`. **`systemd` was not shipped**: its
exact rules on trailing comments were not verified, and guessing them would put a wrong answer behind
a name that claims authority. The seven flags describe it perfectly well in the meantime, which is
the point of having flags rather than only presets.

**`docker` is the default**, which reverses what this file proposed on the first draft. The evidence
above is what turned it round:

1. **In Java, the incumbent does not strip quotes.** Matching dotenv would put us out of step with
   SmallRye, and so with what someone arriving from Quarkus or MicroProfile expects.
2. **It fails visibly.** Reading a dotenv-style file under the docker dialect yields `"Matteo"`,
   quotes included — noticed at once. Reading a docker-style file under the dotenv dialect removes
   characters that were meant to be there, and says nothing.
3. **It does the least.** On a format with no standard, the most defensible default is the one that
   transforms nothing: whatever follows the `=` is the value.

The one weakness of that default is worth spending five lines on: **under `docker`, when a value
both begins and ends with the same quote character, log a warning** naming the key and suggesting
`dialect=dotenv`. It is almost certainly a dotenv file being read with the wrong dialect. Docker
semantics stay exactly as they are, but they stop being silent.

We should not copy `Properties.load` semantics the way SmallRye does — no backslash escapes, no `:`
or space as a separator, no `!` comment. The `docker` preset means *no processing*, not *properties
processing*.

### Where the setting goes

**Shipped:**

- Per source, **in the fragment**: `@Sources("file:.env#dialect=dotenv")`, and one rule at a time with
  `#quotes=strip`, several separated by `&`. Finer than an annotation on the interface would be, since
  it distinguishes one source from another, so no new annotation was needed. `dialect` sets the starting
  point wherever it appears, and the single rules apply over it. An unknown option or setting is refused,
  not ignored.
- Per factory, by registering the loader that suits:
  `factory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV))`, which needs no new machinery at all —
  registration pushes to the front of the list, so it takes over from the built-in one.

**It shipped in the query and moved to the fragment on 2026-08-10**, with C6, once the rule had to be
stated for every loader rather than for this one. The query cannot be claimed: on an
`https://config/app.env?token=abc` it belongs to the server, and cutting it — which is what phase 0 did,
on every scheme — sent the request without the token. The fragment is never sent to a server, is excluded
from `URL.getFile()` so nothing has to be stripped at all, and is readable on the opaque `jar:` URI that
a resource inside a jar resolves to, where `getQuery()` is `null`. Nothing was released with the query
form, so the change cost nobody anything.

**Cancelled, not postponed:** the `owner.loaders.env.*` settings over `ConfigFactory.setProperty`. Nobody
in the field has a settings namespace for a loader, and the one thing ours was to express — turning
default probing on — stopped existing when discovery was made to enable. See question 8.

**The `classpath:` limitation is gone.** `ConfigURIFactory` splits the fragment off before the resource
lookup and re-attaches it to what it resolved, so options work there exactly as on a file.

An in-file directive — a first-line `# owner:dialect=docker` — was considered and rejected. It travels
with the file, which is genuinely attractive, but it writes our vendor name into somebody else's
`.env`.

### Default probing

**Shipped as proposed**: `defaultSpecFor` returns `null`, as `SystemLoader` does. A `.env` is rarely on
the classpath and never named after the configuration class: it lives at `file:.env` or at whatever
path the container mounts. Declaring it explicitly is right, and it means the new loader costs nothing
to a configuration that does not use one. If automatic pickup is wanted later, it belongs behind an
opt-in setting naming the location, not behind a class-name convention.


Indexed keys, which is C1
-------------------------

Decided 2026-08-09, before writing any of it, and **shipped the same day**: `IndexedProperties` in the
core, with the rules below exactly as they are described here. This is the piece every tree-shaped format
waits on, so they were settled first rather than discovered while implementing them.

Two things came out of the writing that the plan had not said. The choice of concrete collection for a
return type had to move out of the body of `Converters.COLLECTION`, where it was private, and become
something both paths share — reading a list from indexed keys makes exactly the same choice. And the
element type of an `Optional<List<E>>` is resolved in one place now instead of two.

### Why issue #48 is worth reopening at all

[#48](https://github.com/matteobaccan/owner/issues/48) asked for `server.0=`, `server.1=` reading into a
`String[]`, and was turned down in 2013 on the grounds that properties files support multi-line values and
OWNER already has collections, so indexed keys only serve to map legacy files. **That argument was right
for as long as properties was the only format.** It stops being right the moment we read JSON, YAML or
TOML, where a list is native and the only alternative to an index is joining with a comma — which loses
information, since `["a,b"]` and `["a","b"]` become the same string.

So this is no longer a concession to legacy files: it is the representation without which the other
formats cannot be read honestly. Worth saying in the issue when it is closed.

### The notation: `list[0]`

Not because SmallRye, Gestalt and Spring Boot all write it that way, though they do. Because **the dot is
already taken**: `PropertiesAggregator` has owned `prefix + "."` since 2.0.0, and

```properties
something.foo=1
something.bar=2
```
```java
Map<String, Integer> something();   // {foo=1, bar=2}
```

already works. With `list.0` the same layout of keys would mean two different things according to the
return type alone, and worse, **a map with numeric keys would be indistinguishable from a list**:
`errors.404=Not Found` is an entry, `errors.0=…` would be an index, and nothing in the file could tell
them apart. With `list[0]` there is no collision: the aggregator looks for `list.` and finds nothing.

### Precedence: if there is an indexed key, that is the list

The single value is not consulted at all. SmallRye is the only one of the three with an explicit rule and
says the same — *"the indexed property format is prioritized when both styles are found in the same
configuration source"*.

The alternative, "the single value wins and indexed keys are a fallback", reads as more cautious and runs
straight into a fact of the code: `@DefaultValue` is merged into the same `Properties` at load time, so
afterwards **a value from the file cannot be told from a default**. Under the simple rule the question does
not arise. It is backwards compatible either way, since `list[0]` today is a property nothing reads.

### Gaps: refuse

If any indexed key is present there must be a `[0]`, and the sequence must be consecutive. A lone
`servers[5]` is an error, not a list of one.

There is no field to align with here — the three existing implementations do three incompatible things:

| | `[0]` and `[2]`, no `[1]` |
|---|---|
| **Spring Boot** | error: *"Omitting indices will lead to an `UnboundConfigurationPropertiesException`"* |
| **SmallRye** | compacts: the values are collected and sorted, with no empty elements |
| **Gestalt** | inserts `null`, with `setTreatMissingArrayIndexAsError` to make it an error instead |

Refusing, for four reasons. It is what the largest installed base of the three does, so it is the behaviour
most people have already met. **Gestalt thought a switch to the strict behaviour worth adding**, which
suggests the lenient default bit somebody. SmallRye's compaction has a fault of its own: `[0]` and `[2]`
yields a list whose second element is at index 1, so reading it back in Java gives something other than
what the file says, silently. And a silently dropped element is the class of failure this project spent
2026-08-09 removing.

One objection deserves an answer, because it looked fatal at first. Spring can afford strictness because
**it never merges a collection across sources** — the whole list comes from the highest-precedence source
that defines it — and that rule is not available to us: our loaders merge into one `Properties` before
anything is resolved, and by the time a method is called there is no record of which source gave which
key. That is origin tracking, #277, which we do not have. So under `@LoadPolicy(MERGE)` a gap could in
principle arise between two files rather than from a typo. It does not survive examination: because we
merge **by key**, two files contributing to one list overwrite each other index by index, so splitting a
list across files is not a working pattern we would be breaking — it is already broken, and more quietly.

No switch for now. Gestalt has one and we could copy it, but a flag is easy to add when somebody asks and
hard to remove afterwards.

### Elements are not tokenized

Each indexed property is exactly one element: `@Separator` and `@Tokenizer` do not apply to it. This is the
main thing gained over joining with a comma — `servers[0]=a,b` is one element — and it has to be stated,
because the existing array conversion works the other way round. Type conversion per element is unchanged.

### What is deliberately not in C1

`servers[0].host=a` is this plus [nested interfaces](https://github.com/matteobaccan/owner/issues/129),
and belongs to that. But the flattening convention has to be chosen now so that it produces exactly that
shape, so the two meet without either being reworked.

**They met on 2026-08-11, and neither was reworked**: the list of sections reuses the index parsing and the
rules written here — from zero, no gaps, index order rather than file order — and the only thing it had to
add was telling `servers[0]`, an element that *is* a value, from `servers[0].`, an element that *holds*
some. That distinction is the closing bracket at the end of the name, which is exactly what `indexIn`
already checked for its own reasons.

### Two things to fix rather than inherit

- ~~`@Sensitive` does not reach the keys of a group~~ — **done 2026-08-09**, before C1 rather than after, so
  that a list read from indexed keys is covered on the day it is written rather than reopening the hole.
- ~~**`XMLLoader` overwrites repeated sibling elements**~~ — **done 2026-08-09**, with the line in the
  release note draft. An element occurring once keeps its plain key and only a repeat is numbered, because
  a stream cannot look ahead and numbering everything would rename the keys of every XML file written so
  far; the first is moved to `[0]` when the second arrives, subtree and all.

  Writing the tests turned up something the plan had not: the handler wrote straight into the `Properties`
  it was given, and under MERGE that is the same object for every source, so renumbering could carry off a
  key that had come from another file. It now reads into a map of its own that the loader merges at the
  end, which is the right shape anyway — what a source contributes should be what it would contribute if
  it were read alone.

### The escaping that was not built

C2 was written down as "a flattening convention, with escaping for keys that contain a dot". The convention
is built and the escaping is not, deliberately.

`{"a.b": 1}` under `x` flattens to `x.a.b=1`, and so does `{"a": {"b": 1}}`. Quoting the segment, as
SmallRye does, would tell them apart — and would cost every reader of an ordinary key the quoting as well,
since `@Key("x.a.b")` works today for either file and would have to become something less obvious. It would
also want `PropertiesAggregator` to understand the quotes, which is a change to how a `Map` reads its
entries.

What settles it is that **nothing in the library turns a flattened key back into a tree**. A configuration
method names the key it wants and gets it; the ambiguity has no victim. Inventing a quoting scheme for a
reader that does not exist is the worse trade, so it is written down instead — in `PropertyKeys` and on the
formats page — and can be revisited the day something does need to reverse a flattening.


Null, and why it is not a core rule
-----------------------------------

Decided 2026-08-10, and the decision is about **scope** before it is about behaviour: `null` does not
become a concept in the core. Properties, XML, `.env` and INI have no null to represent, so nothing in
the core needs a fourth state; a format that *has* one answers for itself, as **an option of that
format**, in the loader that reads it. Question 9 is therefore closed here and reopened, deliberately,
in the first loader that meets it.

What the core offers a loader to write into is three states, and they were measured rather than assumed:

| the source | what the method returns |
|---|---|
| no key | the `@DefaultValue`; without one, `null` or `Optional.empty()`; `MissingMandatoryPropertyException` if the method is `@Mandatory` |
| key with an empty value | **not** the default — unless the method asked with `@DefaultValue(useOnEmpty = true)`, which is `false` unless written. Then per type: an error for numbers, `boolean`, `char`, enum, `Class`, `URL`, `BigDecimal`; `""` for `String`; an empty path for `File`, `Path`, `URI`; an **empty collection** for arrays and collections |
| key with a value | the value |

The mechanism behind the first two rows is one line of ordering, in `PropertiesManager.load()`: the
defaults are written into the properties first and the sources are merged **over** them. So a source that
writes a key overrides the default, and a source that omits it leaves the default standing. Every option
for `null` is a choice between those two, and nothing else — which is exactly why the core does not need
to know about it.

### Two constraints the core has already imposed on whoever decides later

Neither is negotiable by a format option, and both were found by working the question through rather than
by reading the code, so they are written here to save the rediscovery.

- **A `null` element inside a list cannot be omitted.** C1 refuses a gap: if any indexed key is present
  there must be a `[0]` and the sequence must be consecutive. So `["a", null, "c"]` cannot drop
  `servers[1]` — that is an error by our own rule, and a worse one, because it reports a malformed list
  rather than the null that caused it. A format option may write an empty value there, or refuse the
  document; it may not omit.
- **An empty list has a representation and a `null` does not.** `servers=` already yields an empty
  collection, by the table above, so `{"servers": []}` has a faithful reading available and should use
  it — including the consequence that it overrides a `@DefaultValue`, which is what the file says.
  A `null` has no such reading: `Properties` cannot hold one.

Which suggests the shape the first loader will most likely land on — write what `Properties` can
represent, omit only what it cannot — but that is a suggestion to the loader, not a rule of the core,
and it is not decided here.

### Why this is a format option and not a default

Because the same rule lands differently on two formats we both intend to ship. In JSON you have to type
`null` for it to be there. **In YAML you do not**: `host:` followed by nothing *is* null, not the empty
string, and it is the most innocuous line anybody writes. A single core rule would decide the meaning of
that line for everyone, and it is almost certainly why Spring chose to write `""` and has been unable to
move since — see `COMPARISON.md`, where SmallRye drops the key and Spring writes the empty string, in
open disagreement, with a decade of issues on Spring's side.

There is one cost to state plainly whichever way a loader goes, because it is the same cost: **no method
signature in OWNER can tell "absent" from "explicitly null"** — both resolve to `null` or
`Optional.empty()`. A loader that omits the key therefore lets a `@DefaultValue` win over an explicit
`null`, which is not what the author of `{"proxy": null}` meant. The escape is documentation, and it is
the same trade already accepted for the escaping in C2: a distinction no reader can observe is not worth
inventing machinery for. The one thing that must not happen is that it gets *arrived at* — which is what
this section exists to prevent.


Where the parsers live
----------------------

With no external dependency there is no *technical* reason to split the artifact, so the question is
open on other grounds. The measurements, taken 2026-08-09: the core is **6,852 lines across 61
files**, `owner-extras` is **84 lines in one class**. The parsers estimated above come to roughly
5,700 lines, plus 1.5–2× that in tests. Putting them all in the core would more than double it.

Size is not the deciding argument, though. **A parser is code that chews on untrusted input.** A bug
in a YAML or CBOR parser sitting in the core is a CVE that forces an upgrade on everyone, including
the majority who never load a YAML file. In its own artifact it reaches only the people who use that
format, and it can be patched and released on its own.

There is already a line of principle in the code, and it draws the boundary for us: **the core ships
the formats the JDK can already parse.** `PropertiesLoader` uses `Properties`, `XMLLoader` uses SAX
— XML is an enormous format that costs the core nothing, because the parser is the JDK's and so is
its security. Following the same rule:

- **core** — properties, XML, system, plus **`.env` and INI**. Around 400 lines together, flat, no
  dependency on the data-model work, and little more than variations on `Properties`.
- **`owner-formats`** (new) — YAML, JSON, TOML, HOCON, CBOR. Real parsers, written and maintained by
  us, released and patched on their own cadence.
- **`owner-extras`** — remote and cloud sources: ZooKeeper today, S3, Vault and Consul later
  (#130, #143). That finally gives it a coherent theme instead of being the drawer with 84 unused
  lines in it.

The practical consequence is worth having: **`.env` in the core means phase 0 needs neither
`ServiceLoader` nor a new artifact**, and can be done immediately. It was, and it did not: the loader
and the dialect together are 742 lines of the core, against 6,852 before them.

That settles half of question 4 below by doing it. The other half — a third artifact for the
tree-shaped formats — is still open, and nothing so far commits us to it.

**What C6 changed about that question, 2026-08-10.** Until discovery existed, a format in a separate
artifact reached the user only if the user called `registerLoader` — so the split cost every one of them a
line of code and a piece of documentation to find. It does not any more: an artifact declaring its loader
in `META-INF/services` is on as soon as it is on the classpath, which is what people expect of a
dependency. That does not decide question 4, but it removes the argument that used to weigh most against
splitting.


Open questions
--------------

1. ~~**`.env` default dialect**~~ — **settled and shipped 2026-08-09: `docker`**, with `dotenv` and
   `compose` as presets, seven rules adjustable one at a time, and a warning when a value looks quoted.
2. **Do we call a YAML subset "YAML"?** Proposed: yes in the title, no in the documentation — a
   chapter listing exactly what is in and what is out, and a hard error on anchors and tags. **Still
   open, with a precedent found 2026-08-10**: StrictYAML calls itself *"a restricted subset of the YAML
   specification"*, does **not** call itself YAML, and gives the features it removes — implicit typing,
   tags, anchors, flow style — a documentation page of their own rather than a paragraph. Whichever way
   the name goes, the list of what is missing wants that much room.
3. ~~**`list[0]` or `list.0`?**~~ — **settled 2026-08-09: `list[0]`**, and for a better reason than the
   one written here first. See *Indexed keys* below.
4. **Where do the parsers live?** Half answered by shipping: `.env` is in the core and that was
   right. Still open, and still uncommitted either way: whether the tree-shaped formats get an
   `owner-formats` of their own, which is a third artifact to maintain and release. **Checked
   2026-08-10**: the field splits *per format*, finer than what is proposed here — Gestalt publishes
   `gestalt-json`, `gestalt-yaml`, `gestalt-toml` and `gestalt-hocon` separately, SmallRye ships the
   YAML source as its own artifact, and only Spring keeps the loaders in the core with the parser as
   an optional dependency. A single `owner-formats` sits between the two practices; that is a position
   to hold deliberately, not one to arrive at.
5. ~~**TOON: verify or close?**~~ — **verified and closed 2026-08-10**, and the line that used to be
   here was wrong. See the table above.
6. ~~**Does `ServiceLoader` discovery imply enablement?**~~ — **settled 2026-08-10: yes, it does**, and
   the "proposed no" that stood here is what the verification overturned. All three of Spring Boot,
   MicroProfile/SmallRye and Gestalt probe what they discover. What we take from their disagreement with
   us is not the answer but the reason: they merge every source by ordinal, so a discovered loader adds
   one, while we resolve `FIRST` to a single source and a discovered loader would replace it. The rule
   that lets us follow them safely is to **split the two orderings** — head of the `accept()` list, tail
   of the default-spec list. `COMPARISON.md` has the table.
7. ~~**Should `ConfigURIFactory` strip a query from a `classpath:` spec?**~~ — **overtaken and settled
   2026-08-10.** The question assumed the options were in the query, and they are not any more: they are
   in the **fragment**, for every loader and every scheme, because a query on a remote source belongs to
   the server and cannot be claimed. `ConfigURIFactory` splits the fragment off before the resource
   lookup and puts it back on what it resolved, so the classpath is no longer the exception.
   Three things came out of the measuring, and they are why the fragment won: `URL.getFile()` excludes
   it, so no loader has anything to strip and the code that caused two silent failures is gone rather
   than fixed; it is never sent to a server; and it survives on the opaque `jar:` URI a resource inside a
   jar resolves to, where `getQuery()` returns `null` and options in a query would simply have been
   unreadable — pinned by a test that builds a jar, that being the part of the reasoning trusted least.
8. ~~**Are the `owner.loaders.*` setting names worth having at all?**~~ — **settled 2026-08-10: no.**
   Nobody in the field has a settings namespace for a loader: Gestalt registers a typed `ModuleConfig`
   object, Spring has nothing and expects you to register a different loader, SmallRye's settings are
   per source rather than per parser. The one thing the namespace was to express — turning default
   probing on — stops existing once question 6 is answered the way it is. **`Loader` gets no name.**
9. ~~**How does a format that has `null` say so?**~~ — **settled 2026-08-10, and the answer is that the
   core does not answer it.** See *Null, and why it is not a core rule* below.
10. **New, and cheap.** Should the flattener be reachable as a `Properties`-shaped helper — "here is a
    tree, give me the keys" — rather than only as the two naming methods `PropertyKeys` exposes? Writing
    JSON will answer it by needing it or not. Deliberately not designed in advance.
