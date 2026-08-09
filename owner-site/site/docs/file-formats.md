---
layout: docs
title: File formats
prev_section: configuring
next_section: event-support
permalink: /docs/file-formats/
---

Every source named in `@Sources` is read by the first [loader]({{ site.url }}/docs/loading-strategies/)
that says it recognises it, and the format is decided by the path. This page is the whole list: what is
read out of the box, how each format is recognised, and where each one has a rule of its own.

| Format | Recognised by | Looked for without `@Sources` | Parser |
|---|---|---|---|
| [Properties](#properties) | anything the others turn down | `MyConfig.properties` | `java.util.Properties` |
| [XML](#xml) | a path ending in `.xml` | `MyConfig.xml` | the JDK's SAX parser |
| [`.env`](#env) | a path ending in `.env` | — never looked for on its own | ours |
| [System properties and environment](#system) | the `system:properties` and `system:env` pseudo-URIs | — | — |

Two of them are worth reading twice. **`.env` has no standard**, so which rules it is read by is something
you choose; see [below](#env). And **XML is parsed with hardening turned on**, which in rare cases cannot be
applied; see [below](#xml).

Nothing else is read yet — no YAML, JSON, TOML or HOCON. What that would take, and in which order it is
coming, is in [what is not read yet](#not-yet).

<div class="note info">
  <h5>The core has no dependencies, and these do not change that</h5>
  <p>
    Properties and XML are read with parsers the JDK already ships, and <code>.env</code> with one of ours
    that is a few hundred lines. Adding a format has never meant adding a jar, and it is not going to.
  </p>
</div>


Properties
----------
{: #properties}

The baseline, and the fallback: a source that no other loader claims is read as a
[standard properties file][props], by `java.util.Properties`, in UTF-8.

```properties
server.http.port=80
server.http.hostname=localhost
```

```java
interface ServerConfig extends Config {
    @Key("server.http.port")
    int httpPort();

    @Key("server.http.hostname")
    String httpHostname();
}
```

Being the fallback means it accepts any URL it can resolve, whatever the extension, which is why it is
consulted last of all the loaders. A file called `settings.conf` full of `key=value` lines is read as
properties, and correctly so.

  [props]: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#load-java.io.Reader-


XML
---
{: #xml}

A source whose path ends in `.xml` is read as XML, in either of two shapes.

### The Java XML properties format

The one [`java.util.Properties`](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html)
itself defines, and the one OWNER writes when you call `storeToXML`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <comment>this is an example</comment>
    <entry key="server.http.port">80</entry>
    <entry key="server.http.hostname">localhost</entry>
    <entry key="server.ssh.port">22</entry>
    <entry key="server.ssh.address">127.0.0.1</entry>
    <entry key="server.ssh.alive.interval">60</entry>
    <entry key="server.ssh.user">admin</entry>
</properties>
```

It is XML in name more than in shape: a flat list of entries with the structure carried in the key. Which
is why OWNER also reads the other kind.

### An XML of your own

Any XML at all. Elements and attributes become the parts of the key, element and attribute values become
the values:

```xml
<server>
    <http port="80">
        <hostname>localhost</hostname>
    </http>
    <ssh port="22">
        <address>127.0.0.1</address>
        <alive interval="60"/>
        <user>admin</user>
    </ssh>
</server>
```

That is read as exactly the same properties as the example above:

```properties
server.http.port=80
server.http.hostname=localhost
server.ssh.port=22
server.ssh.address=127.0.0.1
server.ssh.alive.interval=60
server.ssh.user=admin
```

so the same mapping interface reads either file, and you can move from one to the other without touching
any Java.

### Two things to know

<div class="note warning">
  <h5>Repeated sibling elements overwrite each other</h5>
  <p>
    Two elements with the same name under the same parent produce the same key, and the second wins:
    <code>&lt;tag&gt;a&lt;/tag&gt;&lt;tag&gt;b&lt;/tag&gt;</code> yields <code>parent.tag=b</code> and the
    first value is lost. There is now a notation that could carry them —
    <a href="{{ site.url }}/docs/type-conversion/">indexed keys</a>, <code>parent.tag[0]</code> and
    <code>parent.tag[1]</code> — but this loader does not yet emit it, so for the moment put the values in
    one element with a separator between them.
  </p>
</div>

<div class="note warning">
  <h5>Hardening, and when it is not there</h5>
  <p>
    The parser is set up to refuse external entities and to cap entity expansion, so an XML source cannot
    be used to read a file off the machine (XXE) or to exhaust it (billion laughs). Those are features of
    the parser, and a parser that does not support one cannot be made to honour it: when that happens OWNER
    logs a <code>WARNING</code> naming the feature, because the protection is then not in force. It does not
    refuse to read the file — that would be worse — so if the warning appears and the XML comes from
    somewhere you do not control, it is worth acting on.
  </p>
</div>

### Writing XML back

[`storeToXML`][storeToXML] on the `Accessible` interface writes the Java XML properties format, whichever
shape was read.

  [storeToXML]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html#storeToXML(java.io.OutputStream,+java.lang.String)


`.env`
------
{: #env}

*Since 2.0.0.*

A list of `NAME=value` lines, and the way container tooling carries configuration into a process:
`docker run --env-file`, `env_file` in Compose, `envFrom` in Kubernetes, the secrets of a CI pipeline.

```
# the server this instance talks to
host=example.org
port=8080
debug=true
timeout=30 s
tags=alpha,beta
```

```java
@Sources("file:.env")
public interface ServerConfig extends Config {
    String host();
    int port();
    boolean debug();
    Duration timeout();
    List<String> tags();
}
```

Any source whose path ends in `.env` is read this way, which covers both a file that is all extension and
one named `staging.env`. Values arrive as text and go through the usual
[type conversion]({{ site.url }}/docs/type-conversion/), so `port()` is an `int` and `timeout()` a
`Duration` exactly as from a properties file, and a `.env` merges with other sources under
[`@LoadPolicy(MERGE)`]({{ site.url }}/docs/loading-strategies/) like any other.

<div class="note info">
  <h5>It is never looked for on its own</h5>
  <p>
    Unlike the properties and XML loaders, this one adds nothing to the files OWNER looks for when an
    interface declares no <code>@Sources</code>. A <code>.env</code> is rarely on the classpath and is never
    named after the configuration interface, so it is always named explicitly — and configurations that do
    not use one pay no extra lookup.
  </p>
</div>

### There is no .env format

Every tool that reads a `.env` has invented its own rules, and they disagree. The one that bites hardest is
quoting. Given

```
NAME="Matteo"
```

`docker run --env-file` gives you `"Matteo"`, quotes included, because it treats them as part of the value.
The `dotenv` family — the Node, Python, Ruby and Go packages — gives you `Matteo`, because it treats them as
delimiters. Docker Compose, confusingly, does not agree with `docker run`: `env_file` strips them too.

OWNER therefore does not implement "the .env format". It implements a **dialect**, and the dialect is
yours to choose.

| | `docker` | `dotenv` | `compose` |
|---|---|---|---|
| `NAME="Matteo"` | `"Matteo"` | `Matteo` | `Matteo` |
| `TEXT="a\nb"` | `a\nb` | a real newline | a real newline |
| `export HOST=x` | name is `export HOST` | name is `HOST` | name is `export HOST` |
| `HOST=x # note` | `x # note` | `x` | `x` |
| a value spanning lines | no | yes, inside quotes | no |
| `HOME` with no `=` | taken from the environment | ignored | taken from the environment |

**`docker` is the default.** It does nothing at all to a value: whatever follows the `=` is the value. On a
format with no standard that is the defensible choice, and it is close to what the rest of the Java world
does — SmallRye Config, the MicroProfile implementation, reads a `.env` with `java.util.Properties.load` and
strips no quotes either. It also fails visibly: a value that arrives with its quotes still attached is
noticed at once, where silently removing quotes that were meant to be kept is not.

Whatever the dialect, blank lines are skipped, a line whose first non-blank character is `#` is a comment,
only the first `=` separates, the file is read as UTF-8, and a leading byte order mark is discarded.

### Choosing the dialect

Per source, in the URI, which is the finest and the one to reach for first:

```java
@Sources("file:.env?dialect=dotenv")
public interface ServerConfig extends Config {
    String name();
}
```

Or for a whole factory, by registering the loader you want. It goes in front of the built-in one:

```java
Factory factory = ConfigFactory.newInstance();
factory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV));

ServerConfig cfg = factory.create(ServerConfig.class);
```

`ConfigFactory.registerLoader(...)` does the same for the default factory, and so for the whole
application.

### One rule at a time

A dialect is not a format: it is a name for a bundle of answers, and each answer can be given separately. A
tool that is not one of the three is still describable.

| Option in the URI | Settings | What it decides |
|---|---|---|
| `dialect` | `docker`, `dotenv`, `compose` | the bundle to start from |
| `quotes` | `strip`, `literal` | whether matching quotes around a value delimit it or belong to it |
| `escapes` | `expand`, `literal` | whether `\n`, `\t`, `\r`, `\f`, `\b`, `\"`, `\'` and `\\` are expanded inside double quotes |
| `export` | `strip`, `keep` | whether a leading `export` is dropped |
| `comments` | `inline`, `none` | whether a `#` preceded by whitespace and outside quotes starts a comment |
| `multiline` | `allow`, `deny` | whether a quoted value may run past the end of its line |
| `continuation` | `allow`, `deny` | whether a line ending in a backslash is joined to the next |
| `bare` | `env`, `ignore`, `error` | what a line naming a variable without assigning to it does |

```java
// systemd writes an EnvironmentFile that quotes, does not escape, and continues lines with a backslash
@Sources("file:/etc/myapp.env?quotes=strip&escapes=literal&continuation=allow")
```

The same rules are available in Java, over any dialect:

```java
EnvDialect systemd = EnvDialect.DOCKER
        .withQuotesStripped(true)
        .withLineContinuation(true)
        .withBareNames(EnvDialect.BareNames.ERROR);

factory.registerLoader(new DotEnvLoader(systemd));
```

An option or a setting that is not one of the above is **refused, not ignored**, so a typo in a query fails
at once instead of quietly reading the file by the wrong rules.

Two things the query cannot do yet. It does not work on a `classpath:` source, where it would end up part
of the resource name; and no dialect interpolates `${...}`, because OWNER expands variables in property
values [itself]({{ site.url }}/docs/variables-expansion/), after loading and across every source, so a
loader doing its own would expand them twice.

### When the file is wrong

A source that cannot be read is not an error — it is how `@LoadPolicy` falls back on another one — but a
file that *can* be read and does not make sense is a different matter, and is refused loudly rather than
half-understood:

- a quoted value that is never closed;
- a line that is neither a comment nor an assignment;
- an assignment to an empty name;
- a name with no value, when `bare=error`.

Each raises an `UnsupportedOperationException` naming the file and the line.

### Turning off the warning about quotes

Under a dialect that keeps quotes — the default — a value written `NAME="Matteo"` almost certainly came
from a file meant for `dotenv`. Reading it verbatim is correct, but silent, so `DotEnvLoader` writes one
`WARNING` per file naming the first key concerned and suggesting `?dialect=dotenv`.

It uses `java.util.logging`, which is part of the JDK: OWNER adds no logging dependency, and never will.
The message is worth reading once and a nuisance afterwards, so here is how to stop it.

**Pick the right dialect.** If quotes are meant to delimit, say so and the warning never fires. This is the
real fix, and the rest are for when reading them verbatim is what you want.

**In a `logging.properties` file**, pointed at with `-Djava.util.logging.config.file=…`:

```properties
org.aeonbits.owner.loaders.DotEnvLoader.level = OFF
```

**In code**, before the first configuration is created:

```java
Logger.getLogger(DotEnvLoader.class.getName()).setLevel(Level.OFF);
```

**Through your own logging framework**, if the application routes `java.util.logging` into SLF4J with
`jul-to-slf4j` or into Log4j 2 with its JUL adapter: the message arrives in the ordinary logs, under the
logger name `org.aeonbits.owner.loaders.DotEnvLoader`.

This is one of only three things OWNER ever says. The others are a hot reload that failed — reported once,
because the configuration keeps the values it had and tries again at the next check — and the XML parser
warning [above](#xml). All three sit under `org.aeonbits.owner`, so `org.aeonbits.owner.level = OFF` turns
off every one of them at a stroke.


System properties and the environment
-------------------------------------
{: #system}

Not files, but sources all the same, and named the same way:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({"file:~/.myapp.config", "system:properties", "system:env"})
public interface ServerConfig extends Config {
    String hostname();
}
```

See [importing properties]({{ site.url }}/docs/importing-properties/) for the other ways to bring these in.


What is not read yet
--------------------
{: #not-yet}

**YAML, JSON, TOML and HOCON are not supported.** Being able to say what is *not* there is half the point of
this page, so: there is no partial support, no experimental flag, nothing to turn on. A `.yaml` source given
to `@Sources` today falls through to the properties loader, which will read it as best it can and give you
nonsense.

They are coming, in that order of demand rather than of ease, and they are being written by hand so that
the core keeps its promise of no dependencies. What holds them up is not the parsers but a decision that
has to come first: a flattened key has no way of expressing a list, which is
[issue #48](https://github.com/matteobaccan/owner/issues/48), and every one of those formats needs one.

In the meantime, two things work today:

- **A loader of your own.** The [`Loader`][loader] interface has three methods — does it accept this URI,
  read it into a `Properties`, and what file name should be looked for by default — and it has been stable
  since 1.0.5. Register it with `factory.registerLoader(...)` and it takes precedence over the built-in
  ones. Two projects outside this one have been reading YAML and JSON through it for years.
- **[ZooKeeper]({{ site.url }}/docs/loading-strategies/)**, in the `owner-extras` artifact, for
  configuration that does not come from a file at all.

  [loader]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/loaders/Loader.html
