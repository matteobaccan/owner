WHY ONE FILE INCLUDING ANOTHER WORKS THE WAY IT DOES
====================================================

**Internal working document — not published on the project site.**

[#165](https://github.com/matteobaccan/owner/issues/165), decided 2026-08-18 and built 2026-08-19.

**What the feature *is* lives on the site**, at
[docs/includes](https://matteobaccan.github.io/owner/docs/includes/): the syntax, an example per format,
what wins, what it does to everything else, and how to see what happened. **None of that is repeated
here**, and nothing here should ever be the second place a rule is written down — when the two disagree
the page is right and this file is stale.

What is here is what a page for users cannot hold: **why** each decision went the way it did, what was
rejected on the way, the measurements that settled the arguments, and where the code is. It is for
whoever opens `Includes.java` in two years and asks why.

The request, from nagkumar in March 2016
----------------------------------------

That a `.properties` file be able to say for itself which other file it builds on:

```properties
# b.properties
needs = a
```

His own sentence is the requirement: *"this way even the properties file dependencies are decided **at
properties file level and not in code**"*. His second idea — a naming convention, `a1.properties`
inheriting from `a.properties` — was refused: a convention that guesses is a convention that surprises.

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
the load for `include` and is passed over for `includeOptional`.

So the shape is **a key inside the file** and not a naming convention — the two libraries that have this
agree, and nagkumar's first proposal was the right one.

The decisions, and what each of them cost
-----------------------------------------

### 1. The child wins, not the parent

**Against Spring, and deliberately.** There, `import` means *"the more specific file for this
environment"*, so what is imported takes precedence. Here the word is *inheritance*: the included file is
a **template, and the file that includes it specialises it**.

The tie-breaker was not taste. `LoadType.MERGE` already says *"if the same property key is specified from
more than one source, the one specified first will prevail"*, and an included file is inserted below the
one that declares it. Taking Spring's direction would have meant one precedence rule for `@Sources` and
the opposite one for the directive, in the same configuration.

### 2. A file is read once, and its **position** is what that protects

The obvious reading is "re-reading is wasteful". It is not the reason: re-reading a file yields the same
values, so overwriting would change nothing about the *content*. What it would change is the **position
of that file in the precedence order** — a file nobody edited moving because a third file named it again.
That is an effect nobody can debug, and it is what the rule exists to prevent.

Cycles then terminate on that rule, with no rule of their own and nothing to report. **Note that this is a
different answer from the one variables get**: a circular *variable* reference throws, decided for 2.0.0.
A file named twice is not a mistake; a value that resolves to itself is.

### 3. `owner.include`, and why the switch had to exist

`include` is Commons' spelling and is also a plausible key for somebody's own configuration.
`owner.include` cannot collide by accident and sits with `owner.strict` and `owner.declared.only`.

**The switch is what makes the name defensible, not a convenience.** Whoever already has a legitimate
property called `owner.include` must be able to say *not here*, or this release changes the meaning of
files that already exist. It is a **factory** property and never a directive inside the file: a directive
that redefines itself is a maze. The one precedent for a switch over a parsing rule is
`owner.nested.variable.expansion`, and it exists for the same reason.

### 4. A missing include is louder than a missing source

This was written in the specification as "follows the rule this library already has". **Building it showed
that is wrong**, and the code says so in a method of its own.

A declared source that is merely absent stays silent, because `FIRST` is a chain of fallbacks in which
every miss but the last is how the feature works. **Nobody builds a fallback chain out of a file naming
another file**: nothing else was going to answer in its place. So it warns, and refuses under
`owner.strict`. A spec carrying `#required=true` is refused either way, the person who wrote the spec
having asked for that themselves.

### 5. Position cannot be enforced; order is the only ordering a file has

**Asked on 2026-08-19: should the directive be required at the top of the file?** No, and not as a
preference. By the time it is read the file is a **map** — `Properties` is a `Hashtable`, and every loader
in this project hands back a flat one — so the line order is gone before anything can look at it.

Enforcing it would mean parsing every format a **second** time to find out which line a key was on, and
answering a different question per format: what is "the top" of a JSON object or a YAML mapping, whose
keys are unordered by their own specification? Machinery invented to reject a file that would otherwise
work.

The order *inside* the directive is therefore the only ordering a file can express, which is why it
carries all the meaning. The pair is worth stating together, because they sound like the same question:
*the position of the directive means nothing, the order inside it means everything.*

**The position question that does have an answer is depth, not line.** Recognised only at the root, so a
nested format's `database.owner.include` stays somebody's property. A rule matching the end of a key would
turn any `owner.include` at any depth into a directive, and a tree-shaped format is precisely where
somebody would write one meaning something else.

### 6. Written twice: three formats, three answers, one of them silent

Measured on 2026-08-19 rather than assumed, and it changed the code:

| format | two `owner.include` lines |
|---|---|
| `.properties`, `.env` | the **last** wins, the first is lost, in silence — `Properties.load` decides it and nothing here can see it |
| `.ini`, `.cfg` | they become `owner.include[0]` and `owner.include[1]`, that loader's convention for a repeated key — so there is **no key called the token** and nothing at all is included |
| JSON, YAML, TOML | the parser **refuses the whole document**: *"the name 'owner.include' is given twice in the same object"*, with the line |

The INI one is the only one that would be invisible, so it is the only one that got a warning. A warning
and not a refusal even under `owner.strict`: what was written is not wrong, it is written in a spelling
this directive has not got.

**Deliberately not read as a list of sources**, though it easily could be. The directive would then have
two spellings, one of which exists only in one format, and the same two lines would mean two sources in an
INI file and one in a properties file.

### 7. A spec with no scheme, and the jar that decided how

Spring Boot's rule, in their words: *"A location starting with a forward slash (`/`) or a URL style prefix
(`file:`, `classpath:`, etc.) is considered fixed. All other locations are considered import relative"*,
and *"an import relative location resolves relative to the file that declares the
`spring.config.import` property"*. **It is C's rule** — `#include "next door"` against `#include <on the
search path>` — since 1972, which is the reading the maintainer gave it.

**What made it free**, measured on the code as built before the change:

| written in the directive | before |
|---|---|
| `sibling.properties`, `./sibling.properties`, `/sibling.properties` | **threw** — `Can't resolve a Loader for the URL ...` |
| `file:sibling.properties` | resolved against the **process working directory** |

A spec with no scheme was not a different behaviour, it was an **error** — the grammar has always required
one. So the form was unused, and every configuration written before this release names a scheme and is
therefore *fixed*, which is exactly what it was. The mistake it removes is the one this feature would
otherwise have made most often: whoever writes `/etc/myapp/production.properties` and names
`local.properties` means the file next to it, and used to get the file next to wherever the JVM started —
silently, a missing include being a warning.

**`java.net.URI.resolve` is the wrong tool and fails in silence.** Measured:

```
jar:file:/opt/app.jar!/config/base.properties + local.properties
    URL -> jar:file:/opt/app.jar!/config/local.properties
    URI -> local.properties
```

A `jar:` URI is **opaque**, so `URI.resolve` hands the relative reference straight back and the source is
then looked for under a name with no scheme at all. `java.net.URL` resolves it, the JDK's `jar:` handler
understanding the part after the `!`, `../other/x.properties` included. **Spring reaches for the same
constructor for the same reason**: `UrlResource.createRelative` goes to `ResourceUtils.toRelativeURL`,
which ends in `new URL(root, path)`. Commons gets there through `FileLocatorUtils` and the same URL
machinery. **Nobody does string surgery on the `!`**, and neither do we.

Three more things the measurement settled:

- **A Windows drive letter is refused by name.** `C:/app/config.properties` is the scheme `c`, and the JDK
  says `unknown protocol: c`, which tells nobody anything. The pattern that recognises a scheme therefore
  wants **two characters at least**. Half the CI runs on Windows and this is a thing people write.
- **The fragment is left alone**, where Spring escapes `#` in a relative path: here the fragment is how a
  source carries its options, so `local.properties#required=true` has to keep meaning what it says. The
  context's own fragment is dropped by the resolution, which is correct.
- **Spaces get the same `%20` fix a `file:` spec gets.**

**`@Sources` is untouched**, by the maintainer's decision: `newURI(spec)` delegates with a null context and
takes the old path exactly. An annotation is written in no file, so there is nothing for a relative spec to
be beside. Its error for a schemeless entry — `Can't resolve a Loader for the URL sibling.properties` — was
offered as a thing to improve alongside and was **not** taken up. It stands.

### 8. The list of sources became invisible, so it is now reported

Found while writing the page, which is the point of writing one: with includes there is **no single place
a person can read the list of sources**. The interface names some; the files name the rest.

So `PropertiesManager` reports the resolved list at `CONFIG`, in precedence order, and **only when a file
actually named something** — a configuration whose files include nothing has its sources on its interface,
where they always were, and the line that already names them is still the whole truth.

The algorithm
-------------

1. `LoadType.load` used to receive a **closed** `List<URI>`. It is now a walk: read a URI, take the
   directive out of what was just read **before merging it**, and schedule what it names.
2. Each spec is resolved by `ConfigURIFactory`, which now also takes the source that named it, for the
   spec that names no scheme.
3. **Included files are inserted immediately after the file that declares them, depth first**: `b` includes
   `a`, `a` includes `z`, and the order is `b, a, z`. Reading runs forwards so a file is read before the
   files it includes; merging runs backwards so the first source declared is applied last and prevails.
4. A **set of the URIs already scheduled**: one that is in it is skipped. That is decision 2, and cycles
   terminate on it.

Where the code is
-----------------

As predicted, plus one. `Includes` is new and holds the walk, the token, and the one thing that knows an
include from a declared source; it is built for one load and thrown away, so it needs no serialised form —
`PropertiesManager` keeps the token, which is a string.

`Config.LoadType` lost its two `load` implementations and kept one, the policies now differing only in
`stopsAtTheFirstThatAnswers()`. `PropertiesManager` grew `doLoad`, `rewatch`, `includeNotRead`,
`directiveWrittenTwice` and `reportWhatWasActuallyRead`; `uris` stayed `final` after all — what changed is
the list handed to the watcher, not the declared one. `HotReloadLogic.setupWatchableResources` is callable
again and idempotent, keeping the watchers it already has so that a rebuild does not move their baselines.

`ConfigURIFactory` took **more** than expected: `newURI(String, URI)` and `resolveAgainst`, the whole of
decision 7 being URI grammar and belonging where the URI grammar already lived.

What the tests found
--------------------

**52 tests**: `Issue165Test` 38, `Issue165HotReloadTest` 3, `Issue165FormatsTest` 9 in `owner-formats`,
`Issue165HoconTest` 2 in `owner-extras`.

Three findings worth keeping:

- **The formats module needed no code at all.** A loader hands back a flat map, the directive is read out
  of that map, and a JSON file including a YAML one including a TOML one works because nothing was written
  to make it work. The test exists to catch the day that stops being true.
- **The hard part named in the specification was real.** `PropertiesManager.uris` was computed once in the
  constructor and handed to `HotReloadLogic`, which watched it and never looked again. The test that proves
  the watched set *shrinks* is counted with a `ReloadListener` rather than read off the values: a
  configuration that reloads for no reason answers exactly the same as one that never reloads, and the only
  difference is that it happened.
- **A chain of two levels proves nothing.** The assertion that matters is the *middle* file beating the
  bottom one, which is what shows there is no special case at the second level — three levels in the core,
  four formats deep in `owner-formats`.

**Declared and undeclared keys** were asked about and are two tests in each module. There is no second
class of property: what an included file holds arrives the way everything a source holds arrives.
`@DeclaredOnly` restricts the *views* while includes decide what was *read*, and the two features do not
meet.
