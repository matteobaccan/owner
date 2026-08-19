---
title: "One file building on another"
---

`@Sources` decides which files a configuration reads, and it decides it **in the code**. A deployment that
wants one more file has to be recompiled.

Since 2.0.0 a file can say for itself which other file it builds on, with a key called `owner.include`.

## A worked example

An application with settings that are the same everywhere, and a few that are not. The shared ones go in a
file nobody edits per environment:

```properties
# base.properties — shipped in the jar
database.host    = localhost
database.pool    = 10
database.timeout = 30s
feature.beta     = false
log.level        = INFO
```

Each environment gets a file that names it and changes only what differs:

```properties
# /etc/myapp/production.properties
owner.include = classpath:base.properties

database.host = db.internal.example.com
database.pool = 40
log.level     = WARN
```

The interface names the environment file and nothing else:

```java
@Sources("file:/etc/myapp/production.properties")
public interface MyConfig extends Config {
    @Key("database.host")    String host();
    @Key("database.pool")    int pool();
    @Key("database.timeout") String timeout();
    @Key("feature.beta")     boolean beta();
    @Key("log.level")        String logLevel();
}
```

What it answers:

| | value | from |
|---|---|---|
| `host()` | `db.internal.example.com` | production, which overrode base |
| `pool()` | `40` | production, which overrode base |
| `timeout()` | `30s` | base — production never mentions it |
| `beta()` | `false` | base |
| `logLevel()` | `WARN` | production |

**The file that includes wins over the file it includes.** The included file is the template; the file
naming it is the specialisation. Nothing in the Java changed, and a new environment is a new file rather
than a new build.

## Where the directive goes, and in what order

Two questions that sound like one and have opposite answers.

**Where the line stands in the file does not matter.** Top, bottom, in the middle of the values — same
configuration. By the time OWNER reads the directive the file has been parsed into a map and the order of
the lines is gone. There is no rule here to break.

**Put it at the top anyway.** Not because the library asks, but because whoever opens the file next should
see what it builds on before reading a value that might have come from there.

**The order of the sources inside the directive decides everything.** It is the one ordering a file *can*
express, so it carries all the meaning:

```properties
owner.include = classpath:base.properties, classpath:regional.properties
```

`base` is named first, so `base` is read first, so **`base` wins** where the two disagree — the same rule
[`@Sources`](/owner/docs/loading-strategies/) has always had. Swap the two names and you get the other
answer.

> The position of the directive means nothing. The order inside it means everything.

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
`spring.config.import`.

**It chains.** Each file resolves against *itself*, not against the first one, so a tree of configuration
files can be moved as a tree:

```properties
# /etc/myapp/production.properties
owner.include = tenants/acme.properties
```

```properties
# /etc/myapp/tenants/acme.properties
owner.include = ../shared.properties      →  /etc/myapp/shared.properties, not /etc/shared.properties
```

This works the same **inside a jar**. An entry at `config/base.properties` naming `local.properties` gets
`config/local.properties` in the same jar, and a leading `/` means the jar's own root. A classpath resource
resolves against wherever it turned out to be — a directory under `target/classes` in development, an entry
in a jar once packaged — and the file says the same thing in both.

> A path with a Windows drive letter is **refused**, with a message saying so: `C:/app/config.properties`
> is not a drive and a path, it is the scheme `c`. Write `file:/C:/app/config.properties`.

`@Sources` has no relative form and cannot have one: an annotation is written in no file, so there is
nothing for it to be beside.

## A file per format

The directive is an ordinary key at the **root** of the document, so every format OWNER reads can carry it.
A file of one format may include a file of another — the format of each source is decided by its own path.

### Properties

```properties
owner.include = classpath:base.properties, local.properties
database.host = db.example.com
```

### INI and CFG

At the root, **before the first section**. Written inside `[database]` it is the ordinary property
`database.owner.include` and does nothing.

```ini
owner.include = base.ini

[database]
host = db.example.com
```

### .env

```dotenv
owner.include=base.env
DATABASE_HOST=db.example.com
```

### XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="owner.include">base.properties</entry>
  <entry key="database.host">db.example.com</entry>
</properties>
```

### JSON

```json
{
  "owner.include": "base.json",
  "database": { "host": "db.example.com" }
}
```

### YAML

```yaml
owner.include: base.yaml
database:
  host: db.example.com
```

### TOML

```toml
"owner.include" = "base.toml"

[database]
host = "db.example.com"
```

Unquoted works too, though it is a different document: `owner.include = "..."` is a *dotted key*, which TOML
reads as `include` inside a table called `owner`. It flattens to the same string, so OWNER finds the
directive either way. Quote it if you want the file to say what it means.

### HOCON

```hocon
owner.include = "base.properties"

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
  owner.include: base.yaml      # a property called database.owner.include
```

That is deliberate. In a nested format, `owner.include` under some section is exactly where a person would
write a key of their own meaning something else, and a rule matching the end of a key would turn it into a
directive by accident.

## What else a spec may carry

A spec is expanded like an `@Sources` entry, so it may name a variable set on the
[factory](/owner/docs/configuring/), a system property, or an environment variable:

```properties
owner.include = classpath:base.properties, file:${app.home}/local.properties
```

The variables come from **outside** the configuration, never from the file being read: the properties are
the thing being assembled, and a source list that depended on them would have to be worked out before it
could be worked out.

A source may also say for itself that it has to be there:

```properties
owner.include = local.properties#required=true
```

## Several files, and the same file twice

A file is read **once**, however many times it is named, and its place in the precedence order is the
**first** one it was given.

```properties
# production.properties
owner.include = eu.properties, us.properties
```

If `eu.properties` and `us.properties` both carry `owner.include = common.properties`, the sources come out
in this order — each file bringing what *it* includes before the next file beside it:

```
production.properties  →  eu.properties  →  common.properties  →  us.properties
      wins over                wins over          wins over
```

`common` sits where `eu` put it, above `us`, and `us` does not get to overrule it. Reading it a second time
would produce the same values, so the only thing a second reading could change is that file's *position* —
and a file moving in the precedence order because a third file mentioned it is not something anybody can
debug.

A cycle follows from the same rule and needs no rule of its own: `a` includes `b` includes `a`, the second
mention is skipped, and it **terminates with no error**. A file named twice is not a mistake. (A circular
[variable reference](/owner/docs/variables-expansion/) still throws — a value that resolves to itself *is* a
mistake.)

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

## When a file is not there

A source named inside another source and missing is a **warning**, and a refusal under
[`owner.strict`](/owner/docs/configuring/):

```
WARNING  MyConfig: the source 'local.properties' is named by owner.include inside another source and
         could not be read. The configuration goes on without it, so this is not an error - but a source
         a file names is not a fallback the way a declared one is, and nothing else was going to answer
         in its place.
```

This is stricter than a source named in `@Sources`, which is passed over in silence — and deliberately so.
With [`LoadType.FIRST`](/owner/docs/loading-strategies/) a declared source that is missing is how a fallback
chain works, every miss but the last being the feature doing its job. Nobody builds a fallback chain out of
a file naming another file.

## Seeing what happened

With includes, the list of sources is no longer written anywhere one person can read it: the interface names
some of them and the files name the rest. So OWNER says what it came to, at `CONFIG`, and **only when a file
actually named something**:

```properties
org.aeonbits.owner.level = CONFIG
```

```
CONFIG  MyConfig: the files named more sources through owner.include. Read in this order, the first
        prevailing: file:/etc/myapp/production.properties,
        file:/opt/myapp/lib/myapp.jar!/base.properties
```

For a single value rather than the whole list, [`Traceable`](/owner/docs/accessible-mutable/) answers — and
an **included file is a source in its own right**, so it names the file the value actually came from and not
the one that included it:

```java
@Sources("file:/etc/myapp/production.properties")
interface MyConfig extends Config, Traceable {
    @Key("database.host")    String host();
    @Key("database.timeout") String timeout();
}
```

```java
cfg.originOf("database.host");      // file:/etc/myapp/production.properties
cfg.originOf("database.timeout");   // the included file, resolved — see below
```

That is worth the two lines it costs. "Where does this value come from" is the question a layered
configuration provokes, and without it the answer is a search through files that name each other.

Both of these name the source **as it was resolved**, not as it was written. A `classpath:base.properties`
comes back as the place it was actually found:

```
file:/home/me/myapp/target/classes/base.properties     during development
file:/opt/myapp/lib/myapp.jar!/base.properties          once packaged
```

which is more use than the spec you already have in front of you — two jars carrying the same resource name
look identical until something says which one answered.

## How it fits with everything else

| | |
|---|---|
| **It is not a property** | `owner.include` is removed once it has been read: it is in no `propertyNames()`, no `store()`, no `toString()`, and `originOf` is never asked about it. The one place it survives is the file itself, which [`save(File)`](/owner/docs/accessible-mutable/) keeps as a line it does not own |
| **[`@HotReload`](/owner/docs/reload/)** | watches the included files too, and works the list out again after every load — so a file that *starts* naming an include gets it watched, and one that stops naming it stops |
| **[`LoadType.FIRST`](/owner/docs/loading-strategies/)** | the first source that answers wins and the ones after it are never read — but **its own includes are**: what a file includes is part of what that file means |
| **[`LoadType.MERGE`](/owner/docs/loading-strategies/)** | every declared source is read, and so is everything each of them includes, each one immediately below the file that named it |
| **[`@DeclaredOnly`](/owner/docs/accessible-mutable/)** | nothing: it restricts the views, while includes decide what was read. The directive is invisible under it either way, having never been a property |
| **[Sections](/owner/docs/nested-configuration/)** | an included document may carry them, and the including file overrides them key by key rather than whole: a file naming a YAML document with a `server` block can change `server.port` and keep its `server.host` |

## Turning it off, or renaming it

The key is read from the factory property `owner.include.key`:

```java
ConfigFactory.setProperty("owner.include.key", "needs");   // the directive is now 'needs'
ConfigFactory.setProperty("owner.include.key", "");        // no directive at all
```

The empty value is what the switch is for. If you already have a legitimate property called
`owner.include`, you must be able to say *not here* — otherwise upgrading to 2.0.0 would change what your
existing files mean. With the feature off, `owner.include` is an ordinary property again.

It is a **factory** property and never a directive inside a file: a directive that redefines itself is a
maze.

## What it does not do

- **No conditions.** There is no "include this file if that property is set". A spec may name a variable —
  `file:${env}/local.properties` — and that is as far as it goes.
- **It cannot read the file it is in.** The variables in a spec come from the factory, the system properties
  and the environment, never from the properties being assembled.
- **It is not a [preprocessor](/owner/docs/preprocessors/).** It adds sources; it does not transform values.

## Why the child wins, and not the parent

Spring Boot's `spring.config.import` does the opposite: what is imported **wins** over the file that
imported it. There, `import` means *the more specific file for this environment*.

Here the word is *inheritance*. The included file is the template and the file naming it is the
specialisation, so the child overrides the parent — which is also what OWNER already says everywhere else:
of two sources, the one named first prevails.
