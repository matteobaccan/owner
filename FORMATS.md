WHY THE FORMATS ARE READ THE WAY THEY ARE
=========================================

**Internal working document — not published on the project site.**

Started 2026-08-09 as a plan; every format it planned shipped between 2026-08-09 and 2026-08-12. Trimmed
2026-08-18 and again 2026-08-19.

**What each format does lives on the site**, at
[File formats](https://matteobaccan.github.io/owner/docs/file-formats/): how each one is recognised, how a
tree becomes keys, the dialects and every flag in them, what is refused and with which message, the YAML
subset, indexed keys and their rules. **What everybody else supports is in `COMPARISON.md`**, each claim
next to its source. Neither is repeated here, and nothing here should ever be the second place a rule is
written down — when this file and the page disagree, the page is right and this is stale.

What is left is the arguing: the rules each format **settled**, the questions each one **answered**, and
what a seventh format would have to be consistent with. None of it is derivable from the code.

> `.env` and INI are in the core; JSON, YAML and TOML in `owner-formats`; HOCON in `owner-extras`, through
> Typesafe Config. The sections below refer to **C1** (indexed keys) and **C6** (per-source options plus
> loader discovery), which were the names of a list of missing pieces that has since been built and
> removed; `git log` has it.

**The method, and the one thing to carry from the whole exercise.** On 2026-08-10 a day was spent checking
the open questions against the other libraries instead of deciding them from taste, before a line of the
next format was written. It paid immediately: one decision was **inverted** — whether discovering a loader
on the classpath implies enabling it, where Spring Boot, SmallRye and Gestalt all say yes and the reason
they can is a difference in precedence models that told us exactly what to do differently — one was
**cancelled outright**, since nobody in the field has a settings namespace per loader and so `Loader` got
no name, and one line about TOON was simply **wrong**. **Two of the four checks found the field in open
disagreement**, which is not a failure of the method but its point: it says which decisions have to be
argued rather than aligned.


The constraint that shapes everything
-------------------------------------

**No external dependencies.** Every parser is written by hand, on the Java 8 baseline. That single
constraint reordered the whole plan: HOCON, nearly free if we depend on Typesafe Config, became the most
expensive thing on it, while `.env` moved to the front.

**Amended 2026-08-12, and the amendment has a test so that it cannot spread.** The rule stands with one
exception: **a format whose specification *is* an implementation is delegated, not written.** HOCON is the
only one that qualifies. TOML does not — its specification is a document, `toml-test` is a conformance
suite anyone can run, and several independent implementations agree on what it means. **The criterion is
what keeps this from becoming an excuse: it is checkable, and it says no to the next format that asks.**

It also sets the standard of honesty we owe: a hand-written parser for a large format is a *documented
subset*, and it must refuse what it does not understand rather than half-read it.


The formats that were considered and not built
----------------------------------------------

The estimates and the ordering are spent. What is worth keeping is why these four are not on the list, so
that nobody re-derives it:

| | |
|---|---|
| **CBOR** | RFC 8949, 450–650 lines, a small deterministic spec — genuinely easier to get right than YAML or TOML for all that it is binary. Not built because nobody asked; there is no argument against it |
| **TOON** | **closed 2026-08-10, for irrelevance and not for absence of tooling** — the specification is public and JToon, `json-io` and even a Spring Boot module exist. It is an encoding for feeding an LLM, not a format anybody writes a configuration in by hand. Reopened by somebody asking, which is a different fact |
| **CDDL** | **not a data format**: a schema language for CBOR and JSON. It belongs with validation, [#201](https://github.com/matteobaccan/owner/issues/201), not with loaders |
| **JSONB** | **not a file format**: either PostgreSQL's binary column type, which is a *source* and needs a driver, or Jakarta JSON Binding, which is a mapping API |

**Nothing is planned after these**, and a seventh format should not be on `TODO.md` until somebody asks.
What the four hand-written parsers proved is that the cost is in the *rules* rather than in the parsing.

**Why YAML could be a subset at all.** The `Loader` contract saves us the hardest part: because we hand
back `String` to `String` and our own converters decide the types, YAML's implicit type resolution — the
"Norway problem", where `no` becomes `false` — is work we simply do not have to do. That is the difference
between 700–1000 lines and 5000.

**Why HOCON was delegated.** Everything the plan said about it was right and led to the opposite conclusion
from the one it implied: `${foo}` and `${?foo}` resolve after merging and may be self-referential, objects
merge, `include` runs mid-parse, and we already read `${}` with different semantics. A subset would
therefore **not fail on the files it could not handle — it would read them and mean something else.** That
is not an argument for a careful subset; it is an argument for not writing one. The field agreed
unanimously and that is what settled it: `gestalt-hocon` declares `com.typesafe:config`, Micronaut reaches
the same library through Config4k, Spring Boot has no native HOCON at all, avaje-config has none.

Three facts checked before committing, each of which could have killed it: `com.typesafe:config` declares
**no compile or runtime dependencies** — its three are `test` — it is **Apache 2.0** (the GitHub API
reports no licence only because the file is named `LICENSE-2.0.txt`), and it is **maintained against Java
8**, with the last push on 2026-07-01.


`.env`: the survey that inverted the default
---------------------------------------------

Verified 2026-08-09 against the sources rather than against articles, and it changed the conclusion. Kept
here because it is evidence about other projects rather than a statement of ours:

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
`DotEnvConfigSourceProvider` delegates to `ConfigSourceUtil.urlToMap`, which calls `properties.load(reader)`.
No quote handling of any kind. So in the Java ecosystem — and SmallRye is the MicroProfile implementation,
therefore the default in Quarkus and Open Liberty — **the incumbent behaviour is not dotenv's.**

That is what made `docker` the default, reversing the first draft, for three reasons:

1. **In Java, the incumbent does not strip quotes.** Matching dotenv would put us out of step with what
   somebody arriving from Quarkus expects.
2. **It fails visibly.** A dotenv-style file read under `docker` yields `"Matteo"`, quotes included, and is
   noticed at once. A docker-style file read under `dotenv` removes characters that were meant to be there,
   and says nothing.
3. **It does the least.** On a format with no standard, the most defensible default is the one that
   transforms nothing.

**We did not copy `Properties.load` semantics the way SmallRye does**, and that is deliberate: backslash as
an escape loses the separators of a Windows path, `:` and space as key/value separators and `!` as a
comment belong to a different format. `docker` means *no processing*, not *properties processing*.

Two changes from the flags the plan proposed: **`interpolation` was dropped**, because OWNER expands `${…}`
in property values itself, after loading and across every source, so a loader doing its own would expand
them twice — and no dialect interpolates anyway. **`lineContinuation` was added**, a trailing backslash
joining the next line being a mechanism of its own rather than the same thing as a quoted value spanning
lines.

**`systemd` was not shipped as a preset**: its exact rules on trailing comments were not verified, and
guessing them would put a wrong answer behind a name that claims authority. The flags describe it perfectly
well in the meantime, which is the point of having flags rather than only presets.

Sources: [docker run --env-file](https://docs.docker.com/reference/cli/docker/container/run/#env),
[docker/cli#3630](https://github.com/docker/cli/issues/3630),
[moby#46773](https://github.com/moby/moby/issues/46773),
[docker/compose#8388](https://github.com/docker/compose/issues/8388),
[SmallRye DotEnvConfigSourceProvider](https://github.com/smallrye/smallrye-config/blob/main/implementation/src/main/java/io/smallrye/config/DotEnvConfigSourceProvider.java),
[dotenv-java](https://github.com/cdimascio/dotenv-java),
[nodejs/node#54134](https://github.com/nodejs/node/issues/54134),
[.env syntax comparison](https://env.dev/guides/env-file-syntax).


Why the options are in the fragment, and not in the query
----------------------------------------------------------

**It shipped in the query and moved to the fragment on 2026-08-10**, with C6, once the rule had to be
stated for every loader rather than for this one. **The query cannot be claimed**: on an
`https://config/app.env?token=abc` it belongs to the server, and cutting it — which is what the first
implementation did, on every scheme — sent the request without the token. The fragment is never sent to a
server, is excluded from `URL.getFile()` so nothing has to be stripped, and is readable on the opaque
`jar:` URI that a resource inside a jar resolves to, where `getQuery()` is `null`. Nothing had been
released with the query form, so the change cost nobody anything.

**Cancelled, not postponed:** `owner.loaders.env.*` settings over `ConfigFactory.setProperty`. Nobody in
the field has a settings namespace for a loader, and the one thing ours was to express — turning default
probing on — stopped existing when discovery was made to enable.

**An in-file directive was considered and rejected** — a first-line `# owner:dialect=docker`. It travels
with the file, which is genuinely attractive, but it writes our vendor name into somebody else's `.env`.
(The `owner.include` directive of #165 is not a counter-example: it is written in *our* file by whoever
configures the application, not in a file a container tool also reads.)

**`.env` is not probed for by default**, `defaultSpecFor` returning `null` as `SystemLoader` does: a `.env`
is never named after the configuration class — it lives at `file:.env` or wherever the container mounts it.
If automatic pickup is ever wanted, it belongs behind an opt-in setting naming the location, not behind a
class-name convention.


Indexed keys, which is C1
-------------------------

Decided and shipped 2026-08-09, before any tree-shaped format was written, because every one of them waits
on it.

**Why [#48](https://github.com/matteobaccan/owner/issues/48) was worth reopening.** It asked for
`server.0=`, `server.1=` reading into a `String[]` and was turned down in 2013 on the grounds that
properties files support multi-line values and OWNER already has collections, so indexed keys only serve to
map legacy files. **That was right for as long as properties was the only format.** It stops being right
the moment we read JSON, YAML or TOML, where a list is native and the only alternative to an index is
joining with a comma — which loses information. So this is not a concession to legacy files: it is the
representation without which the other formats cannot be read honestly. Worth saying in the issue when it
is closed.

**Refusing a gap: there was no convention to follow.** The three libraries that have this do three
incompatible things:

| | `[0]` and `[2]`, no `[1]` |
|---|---|
| **Spring Boot** | error: *"Omitting indices will lead to an `UnboundConfigurationPropertiesException`"* |
| **SmallRye** | compacts: the values are collected and sorted, with no empty elements |
| **Gestalt** | inserts `null`, with `setTreatMissingArrayIndexAsError` to make it an error instead |

Refusing, for four reasons. It is what the largest installed base does. **Gestalt thought a switch to the
strict behaviour worth adding**, which suggests the lenient default bit somebody. SmallRye's compaction has
a fault of its own: `[0]` and `[2]` yields a list whose second element is at index 1, so reading it back in
Java gives something other than what the file says, silently. And a silently dropped element is the class
of failure this project spent 2026-08-09 removing.

**One objection deserved an answer, because it looked fatal.** Spring can afford strictness because it
never merges a collection across sources — the whole list comes from the highest-precedence source that
defines it — and that rule is not available to us: our loaders merge into one `Properties` before anything
is resolved. So under `@LoadPolicy(MERGE)` a gap could in principle arise between two files rather than
from a typo. It does not survive examination: because we merge **by key**, two files contributing to one
list overwrite each other index by index, so splitting a list across files is not a working pattern we
would be breaking — it is already broken, and more quietly.

**No switch.** Gestalt has one and we could copy it, but a flag is easy to add when somebody asks and hard
to remove afterwards.

**What the writing added that the plan had not.** The choice of concrete collection for a return type had
to move out of the body of `Converters.COLLECTION`, where it was private, and become something both paths
share; and the element type of an `Optional<List<E>>` is resolved in one place now instead of two.

**C1 and nested interfaces met on 2026-08-11 and neither was reworked.** The list of sections reuses the
index parsing and the rules above, and the only thing it had to add was telling `servers[0]`, an element
that *is* a value, from `servers[0].`, an element that *holds* some — which is the closing bracket at the
end of the name, exactly what `indexIn` already checked for its own reasons.

**Two things fixed rather than inherited**, both on 2026-08-09 and both *before* C1 rather than after, so
that a list read from indexed keys was covered on the day it was written: `@Sensitive` not reaching the
keys of a group, and `XMLLoader` overwriting repeated sibling elements. Writing the tests for the second
turned up something the plan had not — the handler wrote straight into the `Properties` it was given, and
under MERGE that is the same object for every source, so renumbering could carry off a key that had come
from another file. It now reads into a map of its own that the loader merges at the end, which is the right
shape anyway: **what a source contributes should be what it would contribute if it were read alone.**

**The escaping that was not built.** C2 was written down as "a flattening convention, with escaping for
keys that contain a dot". The convention is built and the escaping is not, deliberately — and what settles
it is that **nothing in the library turns a flattened key back into a tree**. Inventing a quoting scheme
for a reader that does not exist is the worse trade. It can be revisited the day something does need to
reverse a flattening.


Null is not a core rule, and that is a decision about scope
-------------------------------------------------------------

Decided 2026-08-10. Properties, XML, `.env` and INI have no null to represent, so nothing in the core needs
a fourth state; a format that *has* one answers for itself, **as an option of that format**, in the loader
that reads it.

The mechanism that makes this possible is one line of ordering in `PropertiesManager.load()`: the defaults
are written into the properties first and the sources are merged **over** them. So a source that writes a
key overrides the default, and a source that omits it leaves the default standing. **Every option for
`null` is a choice between those two, and nothing else** — which is exactly why the core does not need to
know about it.

**Why it is a format option and not a default.** The same rule lands differently on two formats we both
intend to ship. In JSON you have to type `null` for it to be there. **In YAML you do not**: `host:`
followed by nothing *is* null, and it is the most innocuous line anybody writes. A single core rule would
decide the meaning of that line for everyone, and it is almost certainly why Spring chose to write `""` and
has been unable to move since — see `COMPARISON.md`, where SmallRye drops the key and Spring writes the
empty string, in open disagreement, with a decade of issues on Spring's side.

### Two constraints the core has already imposed on whoever decides later

Neither is negotiable by a format option, and both were found by working the question through rather than
by reading the code, so they are written here to save the rediscovery.

- **A `null` element inside a list cannot be omitted.** C1 refuses a gap, so `["a", null, "c"]` cannot drop
  `servers[1]` — that is an error by our own rule, and a worse one, because it reports a malformed list
  rather than the null that caused it. A format option may write an empty value there, or refuse the
  document; it may not omit.
- **An empty list has a representation and a `null` does not.** `servers=` already yields an empty
  collection, so `{"servers": []}` has a faithful reading available and should use it — including the
  consequence that it overrides a `@DefaultValue`, which is what the file says. A `null` has no such
  reading: `Properties` cannot hold one.

Which suggests the shape the first loader will most likely land on — write what `Properties` can represent,
omit only what it cannot — but that is a suggestion to the loader, not a rule of the core.

The cost, whichever way a loader goes, is the same and has to be stated plainly: **no method signature in
OWNER can tell "absent" from "explicitly null"**. The escape is documentation, and it is the same trade
already accepted for the escaping in C2: a distinction no reader can observe is not worth inventing
machinery for. **The one thing that must not happen is that it gets *arrived at*** — which is what this
section exists to prevent.


Why the parsers live in their own artifact
-------------------------------------------

Measured 2026-08-09: the core was **6,852 lines across 61 files**, `owner-extras` **84 lines in one class**.
The parsers came to roughly 5,700 lines plus 1.5–2× that in tests, so putting them all in the core would
more than double it.

**Size is not the deciding argument, though. A parser is code that chews on untrusted input.** A bug in a
YAML or CBOR parser sitting in the core is a CVE that forces an upgrade on everyone, including the majority
who never load a YAML file. In its own artifact it reaches only the people who use that format, and it can
be patched and released on its own.

There was already a line of principle in the code and it drew the boundary for us: **the core ships the
formats the JDK can already parse.** `PropertiesLoader` uses `Properties`, `XMLLoader` uses SAX — XML is an
enormous format that costs the core nothing, because the parser is the JDK's and so is its security. `.env`
and INI joined them at 742 lines, being little more than variations on `Properties`; everything with a real
parser went to `owner-formats`; and `owner-extras` finally got a coherent theme — remote and cloud sources
— instead of being the drawer with 84 unused lines in it.

**What C6 changed about this question.** Until discovery existed, a format in a separate artifact reached
the user only if the user called `registerLoader`, so the split cost every one of them a line of code and a
piece of documentation to find. It does not any more: an artifact declaring its loader in
`META-INF/services` is on as soon as it is on the classpath, which is what people expect of a dependency.
That did not decide the question, but it removed the argument that weighed most against splitting.
