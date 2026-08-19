INCLUDING ONE PROPERTIES FILE FROM ANOTHER
==========================================

**Internal working document — the specification of [#165](https://github.com/matteobaccan/owner/issues/165),
decided on 2026-08-18 and built on 2026-08-19.** It is here rather than in the issue because the decisions
below were taken one at a time and each of them has a reason that will not survive in anybody's memory.
What is worth keeping still has to move to the site; until it does, this file is the only place the
feature is written down.

**What shipped**: `Includes` (new), `Config.LoadType` (rewritten around it), `PropertiesManager`
(`doLoad`, `rewatch`, `includeNotRead`, `directiveWrittenTwice`),
`HotReloadLogic.setupWatchableResources` (callable again), `Factory` (the property documented). Behind it:
`Issue165Test` (36), `Issue165HotReloadTest` (3), `Issue165FormatsTest` (9) in `owner-formats` and
`Issue165HoconTest` (2) in `owner-extras` — **50 tests**. Core 1628, extras 92, formats 170, all green.
**The site page is written**: `docs/includes.md`, "One file building on another", in the sidebar after
Loading strategies, linked from there and from File formats. The site builds.

The request, from nagkumar in March 2016
----------------------------------------

That a `.properties` file be able to say for itself which other file it builds on:

```properties
# b.properties
needs = a
```

His own sentence is the requirement: *"this way even the properties file dependencies are decided **at
properties file level and not in code**"*. His second idea — a naming convention, `a1.properties`
inheriting from `a.properties` — is refused: a convention that guesses is a convention that surprises.

vgarmash answered in 2016 that `@Sources` in the right order, or the imports, already cover it. **That is
true and beside the point**, and the answer to him belongs in the eventual comment: `@Sources` puts the
decision in the *code*, so a deployment that wants one more file has to be recompiled. That is exactly
what the issue asks to avoid.

What the field does, checked against the documentation on 2026-08-18
--------------------------------------------------------------------

**Spring Boot**, `spring.config.import`, written inside the file:

> *"Imports are processed as they are discovered, and are treated as additional documents inserted
> immediately below the one that declares the import."*
> *"Values from the imported `dev.properties` will take precedence over the file that triggered the
> import."*
> *"An import will only be imported once no matter how many times it is declared."*

**Commons Configuration**: the keys `include` and `includeOptional`, inside the file. A missing file stops
the load for `include` and is passed over for `includeOptional`. On cycles and on how a relative path is
resolved, **the documentation says nothing**.

So the shape is **a key inside the file** and not a naming convention — the two libraries that have this
agree, and nagkumar's first proposal was the right one.

The four decisions
------------------

### 1. The file that includes wins over the file it includes

**Against Spring, and deliberately.** There, `import` means *"the more specific file for this
environment"*, so what is imported takes precedence. Here the word is *inheritance*: the included file is
a **template, and the file that includes it specialises it**. The child overrides the parent.

It is also what this library already says, in `LoadType.MERGE`: *"If the same property key is specified
from more than one source, the one specified first will prevail"* — and an included file is inserted
below the one that declares it.

### 2. A file is included once, however many times it is named — Spring's rule

Two situations, one rule:

- **the same file reached twice**, `b` and `c` both including `common`;
- **a cycle**, `a` including `b` which includes `a`.

Re-reading a file yields the same values, so "the second overwrites the first" changes nothing about the
*content*. What it would change is the **position of that file in the precedence order** — a file nobody
edited moving because a third file named it again, which is the kind of effect nobody can debug. So: the
first occurrence fixes the position, later ones are skipped. The cycle then terminates by itself, with no
error to report and nothing to explain.

**Note that this is a different answer from the one variables get.** A circular *variable* reference
throws, and that was decided for 2.0.0. A file named twice is not a mistake; a value that resolves to
itself is.

### 3. The key is `owner.include`, and the token can be changed

`include` is Commons' spelling and is also a plausible key for somebody's own configuration.
`owner.include` cannot collide by accident and sits with `owner.strict` and `owner.declared.only`.

The token is read from the factory property **`owner.include.key`**, and the empty value switches the
feature off entirely. The second use is what makes the first defensible: whoever already has a legitimate
property called `owner.include` must be able to say *"not here"*, or we would be changing what files that
already exist mean.

It is a **factory** property and never a directive inside the file: a directive that redefines itself is a
maze. The one precedent in this project for a switch over a parsing rule is
`owner.nested.variable.expansion`, and it exists for the same reason.

### 4. A missing included file follows the rule this library already has

Passed over with a warning, and refused when `owner.strict` is on. That covers by itself the distinction
Commons needs two different keys for.

**Written more carefully once it was built**: it is *not* the rule a declared source gets, and the code
says so in a method of its own. A declared source that is merely absent stays silent, because `FIRST` is a
chain of fallbacks in which every miss but the last is how the feature works. Nobody builds a fallback
chain out of a file naming another file, so an include that is not there is a file somebody meant to write.
A spec carrying `#required=true` is refused without `owner.strict` having to be on, the person who wrote
the spec having asked for that themselves.

### 5. Where in the file the directive is written does not matter — but the order inside it decides everything

**Asked on 2026-08-19: should we say the directive must stand at the top of the file?** No — and it is not
a preference. By the time the directive is read, the file is a **map**: `Properties` is a `Hashtable`, and
every loader in this project — properties, XML, INI, `.env`, JSON, YAML, TOML — hands back a flat one.
The line order the author saw is gone before anything can look at it.

So "it must be at the top" would be a rule nothing enforces. Enforcing it would mean parsing every format
a **second** time, to find out which line a key was on, and answering a different question per format:
what is "the top" of a JSON object or a YAML mapping, whose keys are unordered by their own specification?
That is machinery invented to reject a file that would otherwise work.

What *is* worth writing on the site is the **convention**: put it at the top, because a person reading the
file should see what it builds on before they read anything that might have come from there. A convention
for the writer, enforced nowhere. `whereInTheFileTheDirectiveIsWrittenDoesNotMatter` pins it.

**The question that came next, and is the better half of the pair: does the *order* matter?** It is the
only ordering a file can express, so it carries all the meaning. `owner.include = a, b` reads `a` first and
`a` therefore wins — the rule `@Sources` already has — and a file brings everything **it** includes before
the next file beside it, the walk being depth first, so a file's own parent outranks its sibling. Swap the
two names and the answer swaps. The pair is worth stating together and in those words, because they sound
like the same question:

> The position of the directive means nothing. The order inside it means everything.

**The position question that does have an answer is depth, not line.** The directive is recognised at the
**root of the document and nowhere else**: the key has to be exactly the token. Written one level down in a
nested format it arrives here as `database.owner.include`, and it stays somebody's property. A rule that
matched the end of a key would turn any `owner.include` at any depth into a directive, and a tree-shaped
format is precisely where somebody would write one meaning something else. Two tests, one flat
(`IniLoader` sections) and one nested (YAML).

### 6. Written twice, three formats answer three different ways — and one of them silently

Measured on 2026-08-19 rather than assumed, and it changed the code:

| format | two `owner.include` lines |
|---|---|
| `.properties`, `.env` | the **last** wins, the first is lost, in silence — `Properties.load` decides it and nothing here can see it |
| `.ini`, `.cfg` | they become `owner.include[0]` and `owner.include[1]`, that loader's convention for a repeated key — so there is **no key called the token** and nothing at all is included |
| JSON, YAML, TOML | the parser **refuses the whole document**, naming the key and the line: *"the name 'owner.include' is given twice in the same object"* |

The INI one is the only one that would be invisible, so it is the one that got a warning:
`PropertiesManager.directiveWrittenTwice` names the spelling the directive does have and hands back the
sources that were written, so the fix can be copied out of the message. A warning and not a refusal even
under `owner.strict` — what was written is not wrong, it is written in a spelling this directive has not
got.

**Deliberately not read as a list of sources**, though it easily could be. The directive would then have
two spellings, one of which exists only in one format, and the same two lines would mean two sources in an
INI file and one in a properties file. One spelling: one line, commas.

### 7. A spec with no scheme is looked for beside the source that named it

**Decided and built 2026-08-19**, after the measurement below. Spring Boot's rule, in their words: *"A
location starting with a forward slash (`/`) or a URL style prefix (`file:`, `classpath:`, etc.) is
considered fixed. All other locations are considered import relative"*, and *"an import relative location
resolves relative to the file that declares the `spring.config.import` property"*. It chains. The user's
own reading, and it is the right one: **it is C's rule** — `#include "next door"` against `#include <on the
search path>` — since 1972.

**What made it free**, measured on the code as built before the change:

| written in the directive | before |
|---|---|
| `sibling.properties`, `./sibling.properties`, `/sibling.properties` | **threw** — `Can't resolve a Loader for the URL ...` |
| `file:sibling.properties` | resolved against the **process working directory** |

A spec with no scheme was not a different behaviour, it was an **error** — the grammar has always required
one. So the form was unused and giving it a meaning breaks nothing: every configuration written before this
release names a scheme and is therefore *fixed*, which is exactly what it was.

What it fixes is the mistake this feature would otherwise have made most often: whoever writes
`/etc/myapp/production.properties` and names `local.properties` means the file next to it, and used to get
the file next to wherever the JVM started — silently, a missing include being a warning.

### The jar, which is the part worth writing down

**`java.net.URI.resolve` is the wrong tool and fails in silence.** Measured:

```
jar:file:/opt/app.jar!/config/base.properties + local.properties
    URL -> jar:file:/opt/app.jar!/config/local.properties
    URI -> local.properties
```

A `jar:` URI is **opaque**, so `URI.resolve` hands the relative reference straight back and the source is
then looked for under a name with no scheme at all. `java.net.URL` resolves it, the JDK's `jar:` handler
understanding the part after the `!` — `../other/x.properties` included. **Spring reaches for the same
constructor for the same reason**: `UrlResource.createRelative` goes to
`ResourceUtils.toRelativeURL`, which ends in `new URL(root, path)`. Commons gets there through
`FileLocatorUtils` and the same URL machinery. Nobody does string surgery on the `!`, and neither do we.

So a leading `/` means **the root of wherever the declaring source lives**: the filesystem for a file, the
jar for an entry in a jar, the server for an http source. That is what a URL has always meant by it, it is
the useful reading, and it is what the jar test asserts.

Three more things the measurement settled:

- **A Windows drive letter is refused by name.** `C:/app/config.properties` is the scheme `c`, and the JDK
  says `unknown protocol: c`, which tells nobody anything. The pattern that recognises a scheme therefore
  wants **two characters at least**, and a drive letter gets a message saying to write
  `file:/C:/app/config.properties`. Half the CI runs on Windows and this is a thing people write.
- **The fragment is left alone**, where Spring escapes `#` in a relative path: here the fragment is how a
  source carries its options, so `local.properties#required=true` has to keep meaning what it says. The
  context's own fragment is dropped by the resolution, which is correct.
- **Spaces get the same `%20` fix a `file:` spec gets.**

**`@Sources` is untouched**, by the user's decision on 2026-08-19: `newURI(spec)` delegates with a null
context and takes the old path exactly. An annotation is written in no file, so there is nothing for a
relative spec to be beside. (Its error for a schemeless entry — `Can't resolve a Loader for the URL
sibling.properties` — was offered as a thing to improve alongside and was **not** taken up. It stands.)

The syntax
----------

```properties
owner.include = classpath:common.properties, file:${env}/base.properties
```

- the value is a comma-separated list of **source specs**, the same grammar as the entries of `@Sources`,
  expanded by the same `VariablesExpander` — so a spec may refer to the factory's properties — **plus the
  one form an annotation has no use for**: a spec naming no scheme is looked for beside the source that
  named it, see decision 7;
- the key is a **directive and not a property**: it is removed once processed and appears in no view —
  not in `propertyNames()`, not in `store()`, not in `toString()`.

The algorithm
-------------

1. `LoadType.load` receives a `List<URI>` that is **closed** today. It becomes a queue: take the next URI,
   load it, and look for the directive in what has just been loaded **before merging it**.
2. Each spec is resolved by the machinery `@Sources` already uses (`ConfigURIFactory`), which now also
   takes the source that named it, for the spec that names no scheme.
3. **The included files are inserted immediately after the file that declares them**, depth first: `b`
   includes `a`, `a` includes `z`, and the order is `b, a, z`. With `MERGE` — first specified prevails —
   that is decision 1.
4. A **set of the URIs already scheduled**: one that is in it is skipped. That is decision 2, and cycles
   terminate on it.
5. A file that is not there is passed over, with the warning every absent source gets.

What it has to leave standing
-----------------------------

| | |
|---|---|
| `FIRST` | the source that answers wins and the ones after it are never read — but **its own includes are loaded**, merged below it: what a file includes is part of what that file means |
| `Traceable` | an included file is **a source in its own right**, so `originOf(key)` names the included file and not the one that included it. This is why the includes must join the list of sources rather than have their values poured into a map |
| `@HotReload` | **the hard part.** `PropertiesManager.uris` is computed in the constructor and handed to `HotReloadLogic`, which calls `setupWatchableResources(uris)` once. With includes the list changes at every load — a file can add one or stop naming one — so the watched set has to be worked out again after each load |
| `save(File)` | the `owner.include` line lives in the file and not among the properties, so the writer keeps it as a line it does not own. To be tested rather than assumed |
| `@DeclaredOnly` | nothing: the directive is not a property, so it is in nobody's view |

Where the code changed
----------------------

As predicted, plus one: `Config.LoadType.load` (both implementations, now one), `PropertiesManager`
(`doLoad`, `rewatch`, `includeNotRead`, `directiveWrittenTwice`; `uris` stayed `final` after all — what
changed is the list handed to the watcher, not the declared one), `HotReloadLogic.setupWatchableResources`
made callable again and idempotent, and `ConfigURIFactory`, which took **more** than expected: it grew
`newURI(String, URI)` and `resolveAgainst`, the whole of decision 7 being URI grammar and belonging where
the URI grammar already lived.

What was written, and what it found
-----------------------------------

**`Issue165Test`, 36 tests** — the algorithm first: a file reached twice loaded once and keeping its first
position (a diamond, so that "which position" is measurable), a cycle terminating with no exception, the
child winning over the parent, a chain of three in depth where the *middle* file beats the bottom one —
which is the assertion a two-level test cannot make and the one that shows there is no special case at the
second level. Then `originOf` naming the included file, the directive absent from all four views, the token
changed and the token emptied, the missing include warned about and refused under `owner.strict`, a
required one refused on its own, a directive naming nothing, `MERGE` following the includes of every
declared source and `FIRST` following those of the one that answered, a spec expanded through the factory's
variables, a classpath include, `save(File)` keeping the line, and `reload()` working the list out again.
The formats the core can read have one test each — `.ini` (root *and* section), `.env`, `.xml` — plus one
chain crossing three of them. Decision 7 added six more: a sibling, a walk up that chains, a scheme still
being fixed, a classpath resource naming its neighbour, a Windows drive letter refused by name, and **a jar
whose entry names both the entry beside it and one at the jar's root**.

**`Issue165HotReloadTest`, 3 tests** — the hard part named in this file. Touching the *included* file
reloads; a file that **starts** naming an include gets it watched, though it was not a source when the
object was created; a file that **stops** naming one stops watching it. The third is counted with a
`ReloadListener` and not read off the values: a configuration that reloads for no reason answers exactly
the same as one that never reloads, and the only difference is that it happened.

**`Issue165FormatsTest`, 9 tests, in `owner-formats`** — JSON, YAML and TOML each carrying the directive;
the same key one level down staying a property; a chain four formats long (JSON → YAML → TOML →
properties) checking precedence at every level, an undeclared key travelling the whole way, and `originOf`
naming the right document four steps out; and a flat file including a document **with sections in it**,
read as sections, with the including file overriding one key of a section rather than the whole section.
Under `@DeclaredOnly` the same, restricted.

Nothing in the formats module needed a line of code. That is the finding worth keeping: a loader hands back
a flat map, the directive is read out of that map, and crossing formats works because nothing was written
to make it work.

**Declared and undeclared keys** were asked about and are two tests in each module. There is no second
class of property here: what an included file holds arrives the way everything a source holds arrives, and
`@DeclaredOnly` restricts the *views* while includes decide what was *read*. The two features do not meet.
The directive is invisible under either, having never been a property at all.

What is left
------------

- **Nothing on #165 but the release.** The feature, the tests and both documents are done.
