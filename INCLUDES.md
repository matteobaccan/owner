INCLUDING ONE PROPERTIES FILE FROM ANOTHER
==========================================

**Internal working document — the specification of [#165](https://github.com/matteobaccan/owner/issues/165),
decided on 2026-08-18 and not yet built.** It is here rather than in the issue because the decisions below
were taken one at a time and each of them has a reason that will not survive in anybody's memory. When the
feature ships, what is worth keeping moves to the site and this file becomes the record of why.

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

The syntax
----------

```properties
owner.include = classpath:common.properties, file:${env}/base.properties
```

- the value is a comma-separated list of **source specs**, the same grammar as the entries of `@Sources`,
  expanded by the same `VariablesExpander` — so a spec may refer to the factory's properties;
- the key is a **directive and not a property**: it is removed once processed and appears in no view —
  not in `propertyNames()`, not in `store()`, not in `toString()`.

The algorithm
-------------

1. `LoadType.load` receives a `List<URI>` that is **closed** today. It becomes a queue: take the next URI,
   load it, and look for the directive in what has just been loaded **before merging it**.
2. Each spec is resolved into URIs by the machinery `@Sources` already uses (`ConfigURIFactory`).
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

Where the code changes
----------------------

`Config.LoadType.load` (both implementations), `PropertiesManager.uris` which stops being `final`,
`HotReloadLogic.setupWatchableResources` which has to be callable again, `ConfigURIFactory` to resolve the
specs, and `PropertiesManager`'s origin recording, which already attributes a value to the source it came
from.

The tests, in the order they should be written
----------------------------------------------

The first four fix the algorithm; the two after them are where a dynamic list of sources is found to work
or not.

1. a file included twice from two branches is loaded **once**, and its position is the first one;
2. `a → b → a` terminates, each file read once, **no exception**;
3. `b` includes `a`: the values of `a` are there, and **`b`'s own win** over the ones they share;
4. a chain of three, in depth, in the right order;
5. `originOf(key)` names the **included** file;
6. `@HotReload`: touching the **included** file reloads the configuration;
7. an included file that is missing is passed over; with `owner.strict`, refused;
8. the directive does not appear in `propertyNames()`;
9. a custom `owner.include.key`, and the empty value switching the feature off;
10. `save(File)` does not lose the directive's line.
