WRITING THE CONFIGURATION BACK
==============================

**Internal working document — not published on the project site.**

Started 2026-08-14, out of [#190](https://github.com/matteobaccan/owner/issues/190) ("how do I edit the
properties file?") and [#16](https://github.com/matteobaccan/owner/issues/16) ("a write that replaces a
value without destroying the file", open since 2013, seventeen comments — the most discussed thing we
have). The companion documents are `CRYPTO.md`, `FORMATS.md` and `COMPARISON.md`.

Nothing here is built. The open questions are collected at the bottom.


Where we are, measured
----------------------

`Mutable` + `Accessible.store` already do a round trip, and 2026-08-14 gave it a test
(`WritingTheFileBackTest`). What it does to a hand-written file was measured rather than assumed:

```
# the database we talk to        #written back by the application
host = localhost          -->    #Fri Aug 14 17:52:00 CEST 2026
                                 host=localhost
# in milliseconds                port=9090
port = 8080
```

Comments gone, blank line gone, order changed, a timestamp added. Underneath is
`java.util.Properties.store`, which serialises a map, and a map has no comments and no order.

That is fine for a file only the application writes and wrong for one a person maintains — which is
exactly what #16 said in 2013.


What the field does
-------------------

Checked 2026-08-14. **Nobody writes the user's configuration file**, and the nearest thing to this is
solving a different problem.

**Spring Boot** generates `META-INF/spring-configuration-metadata.json` at compile time, with
`spring-boot-configuration-processor`, and **the description comes from the javadoc** on the
`@ConfigurationProperties` field. IDEs read it for completion, and third-party plugins turn it into
documentation. It is the same principle — the code is the source of truth for what a property means — put
to a different use: tooling metadata in the jar, not comments in the file.

**Coat** generates the implementation, so its equivalent question does not arise.

**Commons Configuration** has `PropertiesConfiguration`, which *does* keep the layout of a file it
loaded (`PropertiesConfigurationLayout`). It is the only precedent for preserving a file, and it comes
with a whole layout object the user has to know about.

One conclusion is free, and it decides a question rather than opening one: **javadoc is not available to
us.** Spring can read it because an annotation processor runs at compile time; we are a runtime library
and the compiler has thrown it away by the time we look. So a description has to be an **annotation**.


The design
----------

### The decision: the interface is the source of truth

Settled 2026-08-14 by the user, and it is the choice that shapes everything else:

> *If you document the interface, OWNER reads the configuration and writes it back commented. The user
> does not update the description — they update the code, as the single source of truth.*

```java
public interface AppConfig extends Mutable, Accessible {

    @Description("The database we talk to. A host name or an address; the port is separate.")
    @DefaultValue("localhost")
    String host();

    @Description("How long to wait for a connection before giving up.")
    @DefaultValue("30s")
    Duration timeout();
}
```

writes

```properties
# The database we talk to. A host name or an address; the port is separate.
# default: localhost
host = db.internal

# How long to wait for a connection before giving up.
# default: 30s
timeout = 30s
```

**A description edited in the file is lost at the next write, and that is the point rather than a
casualty.** The recurring failure of configuration documentation is that it drifts: a key is renamed, a
default changes, and the sentence beside it keeps saying what used to be true. Next to the method the
sentence moves with the thing it describes, and the compiler is watching.

### What follows for free

- **The orphan comment stops being a question.** Comments are generated, so a removed key takes its
  comment with it. Preserving a human's comment would have needed a rule for what to do with the comment
  above a key that no longer exists, and the format has no rule to borrow.
- **It is testable.** A generated file can be asserted whole; "preserve whatever somebody wrote" can only
  be tested against the cases you thought of.
- **Nested interfaces become sections.** A `@Description` on a nested type is a heading over the block of
  keys under its prefix, which is a shape a properties file has never been able to express.
- **It answers [#3](https://github.com/matteobaccan/owner/issues/3)** — "a tool that generates a
  properties file from the Config interfaces", open since 2013 — with the same machinery and no second
  feature. A configuration with no file yet is the same code path with nothing to merge.

### What it does not settle, and must be decided

**1. This does not close #16.** #16 asked for the file to be *preserved*; this regenerates it. They are
different answers to the same complaint, and this one is better for a file the application owns and
worse for a file a person owns. Whether #16 closes as "we chose the other answer" or stays open for
somebody who genuinely needs layout preservation is a separate decision, and it is not this document's
to take.

**2. A file holds more than one interface reads.** `list()` prints every property, and a real
`application.properties` is often read by several interfaces and by things that are not OWNER at all.
Regenerating from one interface must **not** drop the rest. The keys we do not know go through unchanged
and uncommented — probably in a block at the end, under a line saying they were not recognised, which is
also a diagnosis worth having.

**3. Which values get written, which is the one `store()` gets wrong today.** A configuration merging
`system:properties`, the environment and a file holds all three, and `store()` writes all three — so
saving a configuration writes the environment into your file. Nobody noticed because nobody writes.
**`Traceable` makes the right answer possible for the first time**: every property knows its `Origin`, so
the writer can emit only what came from *this file* and what has been `setProperty`-ed since, and leave
the environment where it belongs. That is a correctness fix hiding inside a formatting feature.

**4. It cannot be `store(OutputStream)`.** To write one file you must know which file, and to keep the
keys you do not recognise you must read it first. A stream cannot be reread and does not know its own
name. So this is a **new method taking a `File` or a `Path`**, and that is very likely why it was never
built: the existing interface had nowhere to put it.

**5. Escaping becomes ours.** `Properties.store` escapes `=`, `:`, `#`, `!`, leading spaces, and anything
outside Latin-1 as `\uXXXX`. Writing the file ourselves means doing exactly that, or producing files we
read back differently than we wrote them. It is the least interesting part and the easiest to get subtly
wrong; it wants its own tests, round-tripping every awkward character.


Open questions
--------------

1. ~~**Can we even order the keys?**~~ **Answered by measuring, and the answer is no.** Declaration
   order is the obvious choice and is what would make a generated file readable, and it is not available
   at run time. `Class.getMethods()` is documented as returning methods in no particular order, and the
   JDK means it — five methods declared `zebra, apple, mango, banana, cherry` come back as:

   ```
   JDK 17   zebra, cherry, apple, mango, banana
   JDK 24   mango, apple, cherry, zebra, banana
   ```

   Not source order on either, and **not the same order on the two**. So a file generated on the
   developer's JDK and regenerated on the build's would differ in nothing but the order of its blocks,
   which is the kind of diff that makes people stop reading diffs.

   Which leaves: **alphabetical** — stable, dull, defensible, and the only thing that costs nothing; a
   declared `@Description(order = ...)`, which is a knob and will be got wrong; or an **annotation
   processor**, which is the only way to see source order at all and is what Spring and Coat both do.
   That is now an argument in question 5 rather than a preference.
2. **Does the comment carry more than the sentence?** `default: localhost` is nearly free and genuinely
   useful; the type is too (`# a duration, e.g. 30s`); `@Mandatory` might be (`# required`). Each is a
   line in every block of a file somebody has to read, so this is a question about restraint rather than
   about capability.
3. **What about a value that is `@Sensitive`?** `store()` writes the real value today, deliberately —
   masking on the way out would write `********` into the file and lose the password. A generated file
   inherits that, and it is worth saying out loud in the documentation rather than leaving it to be
   discovered.
4. **Does `@Description` belong on the interface as well as the method?** A header at the top of the
   generated file, saying what the whole thing configures, is cheap and probably right.
5. **Is the annotation retained at runtime, or read by a processor?** Runtime is the obvious answer for
   us and needs no build change. But Spring's precedent is a processor, and a processor could also emit
   the IDE metadata Spring ships — which is a different and possibly larger prize. Deciding this decides
   whether `@Description` is `RUNTIME` or `SOURCE`, and it cannot be changed later without breaking
   somebody.
6. **Is the writer a `Loader`'s business?** A `.env`, an INI and a YAML each have their own idea of a
   comment. If the answer is only ever "properties files", say so; if not, writing belongs beside
   reading in the `Loader` SPI, and that is a much bigger change.
