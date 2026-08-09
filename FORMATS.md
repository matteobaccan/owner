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
| **C1** | Indexed keys / lists (`list[0]`) — issue #48 | JSON, YAML, TOML, HOCON, CBOR | **missing.** The universal blocker for every tree-shaped format |
| **C2** | A documented flattening convention, with escaping for keys that contain a dot | every tree-shaped format | **done 2026-08-09**, minus the escaping: `PropertyKeys` in `org.aeonbits.owner.loaders` states the convention and is public, since a loader in another artifact will need it. The escaping was deliberately not built — see below |
| **C3** | Explicit `null` | JSON, YAML, CBOR | **missing.** `Properties` cannot hold a null value. TOML has no null; `.env` and INI have none |
| **C4** | ~~Extension-less `accept()`~~ | — | **withdrawn.** `.env` is a file that is all extension, and the existing test shape matches it |
| **C5** | More than one extension per format | YAML (`.yaml`/`.yml`), HOCON (`.conf`), INI (`.ini`/`.cfg`) | `defaultSpecFor` returns a single `String`. An additive `defaultSpecsFor` would do it, and the SPI must stay compatible — two external projects implement `Loader` by hand |
| **C6** | Loader enablement and per-loader options | all of them | **partly there.** Shipped with `.env`: options per source, in the URI query, and a dialect per factory by registering the loader. Still missing: the `owner.loaders.*` settings over `ConfigFactory.setProperty`, `ServiceLoader` discovery, and the query on a `classpath:` source (see below) |
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

0. ~~**`.env` alone.**~~ **Done 2026-08-09**, `d04c500`. It depended on nothing else here and shipped
   on its own, as intended.
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

- Per source, in the URI query: `@Sources("file:.env?dialect=dotenv")`, and one rule at a time with
  `?quotes=strip`. Finer than an annotation on the interface would be, since it distinguishes one
  source from another, so no new annotation was needed. `dialect` sets the starting point wherever in
  the query it appears, and the single rules apply over it. An unknown option or setting is refused,
  not ignored.
- Per factory, by registering the loader that suits:
  `factory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV))`, which needs no new machinery at all —
  registration pushes to the front of the list, so it takes over from the built-in one.

**Not built, and it belongs to C6:** the `owner.loaders.env.*` settings over
`ConfigFactory.setProperty`. The convention exists and is right, but nothing in phase 0 needed it, and
the naming should be settled once for every loader rather than invented for this one.

**A known limitation.** The query works on `file:` and on any other scheme, and **not on
`classpath:`**: `ConfigURIFactory.newURI` hands a `classpath:` path straight to
`ClassLoader.getResource`, where `?dialect=dotenv` becomes part of the resource name and the lookup
fails. Stripping it there is a small change to the core and was left out of phase 0 rather than
smuggled in. In the loader itself both `accept()` and `load()` do strip the query, as they must, or
`endsWith(".env")` would fail and `openStream()` would look for a file called `.env?dialect=dotenv`.

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


Open questions
--------------

1. ~~**`.env` default dialect**~~ — **settled and shipped 2026-08-09: `docker`**, with `dotenv` and
   `compose` as presets, seven rules adjustable one at a time, and a warning when a value looks quoted.
2. **Do we call a YAML subset "YAML"?** Proposed: yes in the title, no in the documentation — a
   chapter listing exactly what is in and what is out, and a hard error on anchors and tags.
3. ~~**`list[0]` or `list.0`?**~~ — **settled 2026-08-09: `list[0]`**, and for a better reason than the
   one written here first. See *Indexed keys* below.
4. **Where do the parsers live?** Half answered by shipping: `.env` is in the core and that was
   right. Still open, and still uncommitted either way: whether the tree-shaped formats get an
   `owner-formats` of their own, which is a third artifact to maintain and release.
5. **TOON: verify or close?**
6. **Does `ServiceLoader` discovery imply enablement?** Proposed no: discovered and registered, but
   probed only when asked.
7. **New.** Should `ConfigURIFactory` strip a query from a `classpath:` spec, so that per-source
   options work there as they do on `file:`? Small, and the only reason it is a question is that it
   touches the core rather than a loader.
8. **New.** Are the `owner.loaders.*` setting names worth having at all, now that registering the
   loader you want covers the same ground per factory? They would only earn their keep for something
   that cannot be expressed by choosing a loader — turning default probing on, most likely.
