---
title: "One file building on another"
---

`@Sources` decides which files a configuration reads, and it decides it **in the code**. A deployment that
wants one more file has to be recompiled — which is what
[#165](https://github.com/matteobaccan/owner/issues/165) asked to avoid, in 2016.

Since 2.0.0 a file can say for itself which other file it builds on:

```properties
# production.properties
owner.include = classpath:base.properties

database.host = db.example.com
database.pool = 40
```

`base.properties` is read as a source of this configuration, **below** this one. Everything it holds is
there; everything this file also holds wins. The included file is a template, and the file that includes it
specialises it.

Nothing changes in the code:

```java
@Sources("file:/etc/myapp/production.properties")
public interface MyConfig extends Config {
    @Key("database.host")
    String host();

    @Key("database.pool")
    int pool();

    @Key("database.timeout")     // never mentioned in production.properties
    int timeout();               // it comes from base.properties
}
```

## Where it goes in the file, and in what order

Two questions that sound like one and have opposite answers.

**Where the line stands in the file does not matter.** Top, bottom, in the middle of the values — it is the
same configuration. By the time OWNER reads the directive the file has been parsed into a map, and the
order of the lines is gone. There is no rule to break here.

**Put it at the top anyway.** Not because the library asks: because whoever opens the file next should see
what it builds on before reading a value that might have come from there. It is a convention for the person
writing, and OWNER will never complain if you ignore it.

**The order of the sources inside the directive decides everything.** It is the one ordering a file *can*
express, so it carries all the meaning:

```properties
owner.include = classpath:base.properties, classpath:regional.properties
```

`base` is read before `regional`, so **`base` wins** where the two disagree — the same rule
[`@Sources`](/owner/docs/loading-strategies/) has always had: first named, first served. And a file brings
everything **it** includes before the next file beside it, so a file's own parent outranks its sibling.

Turn the list round and you get the other answer. That is the whole of it:

> The position of the directive means nothing. The order inside it means everything.

## Write it once

The directive is one key, so writing it twice is writing it once — and what happens then is decided by the
format, not by OWNER:

| Format | Two `owner.include` lines |
|---|---|
| `.properties`, `.env` | the **last** silently wins, the first is lost |
| `.ini`, `.cfg` | they become a list, so there is no `owner.include` at all and **nothing is included** — OWNER warns |
| JSON, YAML, TOML | the parser **refuses the whole document**, naming the key and the line |

Three formats, three answers, none of them the one you meant. Write one line, with the sources separated by
commas.

## A file per format

The directive is an ordinary key at the **root** of the document, so every format OWNER reads can carry it.
A file of one format may include a file of another: the format of each source is decided by its own path.

### Properties

```properties
owner.include = classpath:base.properties, file:/etc/myapp/local.properties
database.host = db.example.com
```

### INI and CFG

At the root — **before the first section**. Written inside `[database]` it is the ordinary property
`database.owner.include` and does nothing.

```ini
owner.include = classpath:base.ini

[database]
host = db.example.com
```

### .env

```dotenv
owner.include=classpath:base.env
DATABASE_HOST=db.example.com
```

### XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="owner.include">classpath:base.properties</entry>
  <entry key="database.host">db.example.com</entry>
</properties>
```

### JSON

```json
{
  "owner.include": "classpath:base.json",
  "database": { "host": "db.example.com" }
}
```

### YAML

```yaml
owner.include: classpath:base.yaml
database:
  host: db.example.com
```

### TOML

```toml
"owner.include" = "classpath:base.toml"

[database]
host = "db.example.com"
```

Unquoted works too, though it is a different document: `owner.include = "..."` is a *dotted key*, which TOML
reads as `include` inside a table called `owner`. It flattens to the same string, so OWNER finds the
directive either way. Quote it if you want the file to say what it means.

### HOCON

```hocon
owner.include = "classpath:base.properties"

database {
  host = "db.example.com"
}
```

HOCON has [an `include` of its own](https://github.com/lightbend/config/blob/main/HOCON.md#includes), read
by Typesafe Config while it parses, before OWNER sees the document — and in a `.conf` file that is the one
to reach for. `owner.include` works there as well, quoted or not, and buys you nothing extra.

## Only at the root

The directive is recognised **only as the exact key at the top level of the document**. One level down it is
somebody's property and nothing else:

```yaml
database:
  owner.include: classpath:base.yaml   # a property called database.owner.include
```

That is deliberate. In a nested format, `owner.include` under some section is exactly where a person would
write a key of their own meaning something else, and a rule matching the end of a key would turn it into a
directive by accident.

## Fixed, or next door

A source that names a **scheme** — `classpath:`, `file:`, `jar:`, `http:` — is *fixed*: it means the same
resource wherever it was written, exactly as an `@Sources` entry does.

A source that names **no scheme** is looked for **beside the file that named it**:

```properties
# /etc/myapp/production.properties
owner.include = local.properties          →  /etc/myapp/local.properties
owner.include = tenants/acme.properties   →  /etc/myapp/tenants/acme.properties
owner.include = ../shared.properties      →  /etc/shared.properties
owner.include = /srv/defaults.properties  →  the root of wherever this file lives
```

It is the rule C has had since 1972 — `#include "next door"` against `#include <on the search path>` — and
the one [Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html) uses for
`spring.config.import`. **It chains**: each file resolves against itself and not against the first one, so a
tree of configuration files can be moved as a tree.

This works the same **inside a jar**. An entry at `config/base.properties` naming `local.properties` gets
`config/local.properties` in the same jar, and a leading `/` means the jar's own root. A classpath resource
resolves against wherever it turned out to be — a directory under `target/classes` in development, an entry
in a jar once packaged — and the file says the same thing in both.

> A path with a Windows drive letter is **refused**, with a message: `C:/app/config.properties` is not a
> drive and a path, it is the scheme `c`. Write `file:/C:/app/config.properties`.

`@Sources` has no relative form and cannot: an annotation is written in no file, so there is nothing for it
to be beside.

## What else the sources may carry

A spec is expanded like an `@Sources` entry, so it may name a variable set on the
[factory](/owner/docs/configuring/):

```properties
owner.include = classpath:base.properties, file:${app.home}/local.properties
```

And a source may say for itself that it has to be there:

```properties
owner.include = local.properties#required=true
```

## A file included twice, and a cycle

A file is read **once**, however many times it is named, and its place in the precedence order is the
**first** one it was given.

```properties
# root.properties
owner.include = classpath:left.properties, classpath:right.properties
```

If `left` and `right` both include `common`, then `common` sits where `left` put it — above `right`. Reading
it a second time would produce the same values, so the only thing a second reading could change is that
file's *position*, and a file moving in the precedence order because a third file mentioned it is not
something anybody can debug.

A cycle follows from the same rule and needs no rule of its own: `a` includes `b` includes `a`, the second
mention is skipped, and it **terminates with no error**. A file named twice is not a mistake. (A circular
[variable reference](/owner/docs/variables-expansion/) still throws — a value that resolves to itself *is* a
mistake.)

## When the file is not there

A source named inside another source and missing is a **warning**, and a refusal under
[`owner.strict`](/owner/docs/configuring/).

This is stricter than a source named in `@Sources`, which is passed over in silence — and deliberately so.
With [`LoadType.FIRST`](/owner/docs/loading-strategies/) a declared source that is missing is how a fallback
chain works, every miss but the last being the feature doing its job. Nobody builds a fallback chain out of
a file naming another file: nothing else was going to answer in its place.

## What it does to everything else

| | |
|---|---|
| **It is not a property** | `owner.include` is removed once it has been read: it is in no `propertyNames()`, no `store()`, no `toString()`, and `originOf` is never asked about it. The one place it survives is the file itself, which [`save(File)`](/owner/docs/accessible-mutable/) keeps as a line it does not own |
| **`originOf`** | names the **included** file, not the one that included it: an included file is a source in its own right |
| **[`@HotReload`](/owner/docs/reload/)** | watches the included files too, and works the list out again after every load — so a file that starts naming an include gets it watched, and one that stops naming it stops |
| **[`LoadType.FIRST`](/owner/docs/loading-strategies/)** | the first source that answers wins and the ones after it are never read — but **its own includes are**: what a file includes is part of what that file means |
| **[`LoadType.MERGE`](/owner/docs/loading-strategies/)** | every declared source is read, and so is everything each of them includes, each one immediately below the file that named it |
| **[`@DeclaredOnly`](/owner/docs/accessible-mutable/)** | nothing: it restricts the views, while includes decide what was read. The directive is invisible under it either way, having never been a property |

## Turning it off, or renaming it

The key is read from the factory property `owner.include.key`:

```java
ConfigFactory.setProperty("owner.include.key", "needs");   // the directive is now 'needs'
ConfigFactory.setProperty("owner.include.key", "");        // no directive at all
```

The empty value is what the switch is for. If you already have a legitimate property called
`owner.include`, you must be able to say *not here* — otherwise upgrading to 2.0.0 would change what your
existing files mean.

It is a **factory** property and never a directive inside a file. A directive that redefines itself is a
maze.

## Why not Spring's rule

Spring Boot's `spring.config.import` does the opposite: what is imported **wins** over the file that
imported it. There, `import` means *the more specific file for this environment*.

Here the word is *inheritance*. The included file is the template and the file naming it is the
specialisation, so the child overrides the parent — which is also what OWNER already says everywhere else:
of two sources, the one named first prevails.
