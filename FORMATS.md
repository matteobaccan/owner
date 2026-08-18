CONFIGURATION FORMATS
=====================

**Internal working document — not published on the project site.**

Started 2026-08-09 as a plan: what each format would cost, what the core was missing, in which order they
were worth doing. **Every format it planned shipped, and on 2026-08-18 the plan became a record.** What is
worth reading here now is not the ordering — that is spent — but the rules each format settled and the
questions each one answered, because those are the things a sixth format would have to be consistent with
and the things nobody will re-derive from the code.

| Format | Where it lives | Shipped |
|---|---|---|
| `.env` | core, `DotEnvLoader` with three dialects | 2026-08-09 |
| INI | core, `IniLoader` with three dialects | 2026-08-10 |
| JSON | `owner-formats` | 2026-08-11 |
| YAML | `owner-formats`, a **documented subset** | 2026-08-11 |
| TOML | `owner-formats`, held to `toml-test` | 2026-08-12 |
| HOCON | `owner-extras`, through Typesafe Config | 2026-08-12 |

**Nothing is planned after these.** A seventh format is not on `TODO.md` and should not be until somebody
asks for one: what the four hand-written parsers proved is that the cost is in the *rules* rather than in
the parsing, and every rule below was argued against several implementations before a line was written.

The companion documents are `COMPARISON.md`, which records what the other libraries support and how that
was established, and `TODO.md`, which holds what is left to do — one feature, as of 2026-08-18. This file is
the level of detail neither of those wants.

**Trimmed on 2026-08-18**, and what went is worth naming so that nobody looks for it: the description of
the SPI as it stood before the work, the list of what the core was missing — all of it built — and the
ordering, the priorities and the plan in phases, two hundred and seventy-six lines of a plan that
completed. The public [File formats](https://matteobaccan.github.io/owner/docs/file-formats/) page holds
what a reader needs, in more detail than this ever did; what is left here is the arguing.

**The method that produced most of it, and the one thing to carry from the whole exercise.** On 2026-08-10
a day was spent checking the open questions against the other libraries instead of deciding them from
taste, before a line of the next format was written. It paid immediately: one decision was **inverted** —
whether discovering a loader on the class path implies enabling it, where all of Spring Boot, SmallRye and
Gestalt say yes and the reason they can is a difference in precedence models that told us exactly what to
do differently — one was **cancelled outright**, since nobody in the field has a settings namespace per
loader and so `Loader` got no name, and one line that stood here about TOON was simply **wrong**.
`COMPARISON.md` holds the evidence, each claim next to its source.

**Two of the four checks that day found the field in open disagreement**, which is not a failure of the
method but its point: it says which decisions have to be argued rather than aligned.

<small>The sections below refer to **C1**, **C6** and to numbered *questions*, which were the names the
removed list of missing pieces used: **C1** is indexed keys, `list[0]`; **C6** is per-source options plus
loader discovery; the numbered questions were that list's open ones, all since answered. *Phase 0* was
`.env`. Nothing depends on the names, and `git log` has the list that gave them.</small>


The constraint that shapes everything
-------------------------------------

**No external dependencies.** Every parser is written by hand, on the Java 8 baseline. That single
constraint reorders the whole list: HOCON, which is nearly free if we are willing to depend on
Typesafe Config, becomes the most expensive and the riskiest thing on it, while `.env` — which needs
no dependency in any scenario — moves to the front.

**Amended 2026-08-12, and the amendment has a test so that it cannot spread.** The rule stands with one
exception: **a format whose specification *is* an implementation is delegated, not written.** HOCON is the
only one that qualifies today and it is now read that way. TOML does not qualify — its specification is a
document, `toml-test` is a conformance suite anyone can run, and several independent implementations agree
on what it means — so TOML will still be written here. The criterion is what keeps this from becoming an
excuse: it is checkable, and it says no to the next format that asks.

The arrangement is that the exception costs nothing to anybody who does not use it, and that is
structural rather than a promise. `com.typesafe:config` is optional in `owner-extras`, so it is not
transitive and we never ship it; the loader names it nowhere, so `ServiceLoader` creates the loader on a
class path without it exactly as it creates any other; and only reading a `.conf` source fails, saying
which artifact to add. See *Where a delegated format lives* below.

It also sets the standard of honesty we owe: a hand-written parser for a large format is a
*documented subset*, and it must refuse what it does not understand rather than half-read it.


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
| TOML | v1.0.0 | ~~1200–1800~~ **700–1000**, revised 2026-08-12 | medium-high | Dotted keys, four string forms, `_`/hex/oct/bin integers, inf/nan floats, four date-time types, inline tables, `[[arrays of tables]]`, strict redefinition rules. `toml-test` exists |
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

**HOCON.** ~~The hard part is not the omitted braces or the `//` comments, it is **substitutions**~~ —
**done 2026-08-12, by delegating rather than by writing.** Everything this paragraph said was right and
led to the opposite conclusion from the one it implied: `${foo}` and `${?foo}` resolve after merging and
may be self-referential, objects merge, `include` runs mid-parse, and we already read `${}` with different
semantics. A subset would therefore not fail on the files it could not handle — it would read them and
mean something else. That is not an argument for a careful subset; it is an argument for not writing one.

The field agreed unanimously and that is what settled it: **nobody hand-writes a HOCON parser.**
`gestalt-hocon` declares `com.typesafe:config`, Micronaut reaches the same library through Config4k,
Spring Boot has no native HOCON at all, and avaje-config has none. HOCON is the one format where the
specification and the implementation are the same artifact, so everyone delegates and so do we.

Three facts checked before committing to it, each of which could have killed it: `com.typesafe:config`
declares **no compile or runtime dependencies** — its three are `test`, so it is one self-contained jar,
nothing like Curator's tree; it is **Apache 2.0** (the GitHub API reports no licence only because the file
is named `LICENSE-2.0.txt`); and it is **maintained against Java 8**, our baseline, with the last push on
2026-07-01.

What the adapter had to decide, since these do not follow from the format: a value arrives as Typesafe
understood it rather than as written — `1e3` becomes `1000` — because it parses eagerly and does not keep
the text, which is the one place a HOCON source differs from every other source here. Substitutions are
resolved in **two passes**, the document against itself first and the system properties and environment
after; one pass with `resolveWith` alone refuses a self-referential substitution outright, which is an
everyday line in these files. And only the *lookup* falls back, so not one system property becomes a
property of the configuration.

**TOON.** Token-Oriented Object Notation, aimed at cutting token counts when structured data is put
in an LLM prompt, optimised for uniform tabular arrays. **Verified 2026-08-10 and closed** — and half
of what stood here was wrong. The specification is public at `toonformat.dev`, and Java tooling is not
nil: there is JToon, the community implementation with Jackson integration, `json-io` has added TOON as
an output format, and there is even a Spring Boot module. What was right is the half that decides it:
TOON is an encoding for *feeding an LLM*, not a format anybody writes a configuration in by hand. It is
closed for irrelevance, not for absence of tooling, which is a different reason and would be reopened by
a different fact — somebody asking for it.


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

That settles half of question 4 below by doing it. **The other half was settled on 2026-08-11 by JSON:
`owner-formats` exists**, holding the parsers we write ourselves, and the argument that decided it is the
one above — a defect in a hand-written parser is a security release for everybody, and in its own artifact
it reaches only the people who asked for that format. Adding the dependency is the whole of the user's
work, discovery doing the rest.

**What C6 changed about that question, 2026-08-10.** Until discovery existed, a format in a separate
artifact reached the user only if the user called `registerLoader` — so the split cost every one of them a
line of code and a piece of documentation to find. It does not any more: an artifact declaring its loader
in `META-INF/services` is on as soon as it is on the classpath, which is what people expect of a
dependency. That does not decide question 4, but it removes the argument that used to weigh most against
splitting.


