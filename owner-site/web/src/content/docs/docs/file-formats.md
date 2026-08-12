---
title: "File formats"
---

Every source named in `@Sources` is read by the first [loader](/owner/docs/loading-strategies/)
that says it recognises it, and the format is decided by the path. This page is the whole list: what is
read out of the box, how each format is recognised, and where each one has a rule of its own.

| Format | Recognised by | Looked for without `@Sources` | Parser |
|---|---|---|---|
| [Properties](#properties) | anything the others turn down | `MyConfig.properties` | `java.util.Properties` |
| [XML](#xml) | a path ending in `.xml` | `MyConfig.xml` | the JDK's SAX parser |
| [`.env`](#env) | a path ending in `.env` | — never looked for on its own | ours |
| [INI](#ini) | a path ending in `.ini` or `.cfg` | `MyConfig.ini`, `MyConfig.cfg` | ours |
| [JSON](#json) | a path ending in `.json` | `MyConfig.json` | ours, in [`owner-formats`](/owner/docs/installation/#the-formats-that-are-not-in-the-core) |
| [YAML](#yaml) | a path ending in `.yaml` or `.yml` | `MyConfig.yaml`, `MyConfig.yml` | ours, in [`owner-formats`](/owner/docs/installation/#the-formats-that-are-not-in-the-core), and **a subset** |
| [TOML](#toml) | a path ending in `.toml` | `MyConfig.toml` | ours, in [`owner-formats`](/owner/docs/installation/#the-formats-that-are-not-in-the-core), **all of v1.0.0** |
| [HOCON](#hocon) | a path ending in `.conf` | `MyConfig.conf` | **not ours**, and the only one: see [below](#hocon) |
| [System properties and environment](#system-properties-and-the-environment) | the `system:properties` and `system:env` pseudo-URIs | — | — |

Four of them are worth reading twice. **`.env` and INI have no standard**, so which rules they are read by
is something you choose; see [below](#env) and [below](#ini). **XML is parsed with hardening turned on**,
which in rare cases cannot be applied, and **a document that declares a grammar is held to it**; see
[below](#xml). And **HOCON is the one format that needs a dependency**, which you add and we do not ship;
see [below](#hocon).

<div class="note info">
  <h5>Nothing here is on your classpath unless you ask for it</h5>
  <p>
    The core has no dependencies at all: Properties and XML are read with parsers the JDK already ships,
    and <code>.env</code> and INI with ones of ours. JSON and YAML live in
    <code>owner-formats</code>, which has no dependencies either — they are parsers we wrote.
  </p>
  <p>
    HOCON is the single exception in the whole project, for the reason given <a href="#hocon">below</a>,
    and it is arranged so that it costs nothing to anybody else: the dependency is optional, it is not
    transitive, we do not ship it, and only reading a <code>.conf</code> source needs it.
  </p>
</div>


How a tree becomes keys
-----------------------

A loader hands back a `java.util.Properties`, which is flat, and most formats are trees. Every one of them
therefore answers the same two questions the same way — a dot for a child, square brackets for an element:

```json
{"server": {"host": "localhost", "ports": [80, 443]}}
```

```properties
server.host=localhost
server.ports[0]=80
server.ports[1]=443
```

A dot because that is what properties files have always used and what `@Key("server.host")` already
expects. Square brackets because the dot is taken: a method returning a `Map` reads everything under
`server.` as a group, so `ports.0` would make one layout of keys mean two things according only to the
return type.

The two compose, in both directions: `servers[0].host` is the host of the first server and `grid[0][1]` a
cell of a list of lists. Reading the first of those needs nested configuration interfaces and does not work
yet, but a source flattened today already produces the key that will be read then.

<div class="note info">
  <h5>A dot inside a name is ambiguous, on purpose</h5>
  <p>
    A JSON object <code>{"a.b": 1}</code> nested under <code>x</code> flattens to <code>x.a.b=1</code>, and
    so does <code>{"a": {"b": 1}}</code>. Escaping would tell them apart, and would cost every reader of an
    ordinary key the escape as well: <code>@Key("x.a.b")</code> works today for either file. Since nothing
    in the library turns a flattened key back into a tree — a configuration method names the key it wants
    and gets it — the ambiguity has nobody to hurt, and a quoting scheme invented for a reader that does
    not exist would be the worse bargain.
  </p>
</div>


Properties
----------

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

### Repeated elements are a list

*Since 2.0.0.* Two or more elements of the same name under the same parent are numbered, so that a list
written the way XML writes one can be read as one:

```xml
<servers>
    <server port="80">alpha</server>
    <server port="443">beta</server>
</servers>
```

```properties
servers.server[0]=alpha
servers.server[0].port=80
servers.server[1]=beta
servers.server[1].port=443
```

```java
List<String> servers();   // @Key("servers.server") -> [alpha, beta]
```

**An element that occurs once keeps its plain key**, exactly as before: `server.http.hostname` is not
`server.http.hostname[0]`. Only a name that repeats is numbered, and only from the second occurrence on —
at which point the first is moved to `[0]`, subtree and attributes with it.

That asymmetry is not elegance, it is compatibility: a stream cannot look ahead, so when the first
`<server>` arrives there is no telling whether a second will follow, and numbering every element on the
chance of it would rename the keys of every XML configuration written against this library so far.

<div class="note warning">
  <h5>This changed in 2.0.0, and it changed for files that were already broken</h5>
  <p>
    Before 2.0.0 the second element overwrote the first: <code>&lt;tag&gt;a&lt;/tag&gt;&lt;tag&gt;b&lt;/tag&gt;</code>
    left <code>parent.tag=b</code> and the first value was simply lost, with nothing said. Those files now
    produce <code>parent.tag[0]</code> and <code>parent.tag[1]</code>, and no <code>parent.tag</code> at
    all. If a configuration of yours reads <code>parent.tag</code> and the XML repeats that element, the
    key it reads has moved — and what it was reading before was the last of several values, chosen by
    accident. Documents without repeated elements are unaffected.
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

### A document is held to the grammar it declares

If the document carries a `<!DOCTYPE>`, it is validated against it and a violation is **refused**:

```xml
<!DOCTYPE config [<!ELEMENT config (host)><!ELEMENT host (#PCDATA)>]>
<config>
  <host>alpha</host>
  <port>8080</port>     <!-- the DTD says config contains only host -->
</config>
```

Reading this fails, naming `port`. The same holds for the Java XML properties format, which is what the
JDK's own `loadFromXML` does, and it is the rule Commons Configuration follows too when it is asked to
validate at all.

A document that declares **no** grammar is read as it is — there is nothing to hold it to — and so is one
naming an **external** DTD, which the hardening below neutralizes: the grammar never arrives, and a
document cannot be held to a rule that was refused a reading.

`#validate=false` on the source turns the checking off, for both kinds of grammar:

```java
@Sources("file:~/app.xml#validate=false")
```

It is worth having for a file that is out of step with a DTD nobody maintains any more, and it is written
on the source rather than configured globally because it is a property of that file, not of the
application.

<div class="note warning">
  <h5>This changed in 2.0.0.</h5>
  <p>
    Until 1.0.12 a violation of the document's own DTD was <b>ignored</b>, and the properties came back
    including the part the grammar forbids — a validity error does not stop the parse, so nothing was
    truncated and nothing was said. The Java properties format was already refused; what changes is that a
    grammar of your own now counts the same way.
  </p>
</div>

### Writing XML back

[`storeToXML`][storeToXML] on the `Accessible` interface writes the Java XML properties format, whichever
shape was read.

  [storeToXML]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html#storeToXML(java.io.OutputStream,+java.lang.String)


`.env`
------

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
[type conversion](/owner/docs/type-conversion/), so `port()` is an `int` and `timeout()` a
`Duration` exactly as from a properties file, and a `.env` merges with other sources under
[`@LoadPolicy(MERGE)`](/owner/docs/loading-strategies/) like any other.

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

Per source, after a `#` in the URI, which is the finest and the one to reach for first:

```java
@Sources("file:.env#dialect=dotenv")
public interface ServerConfig extends Config {
    String name();
}
```

<div class="note info">
  <h5>The query belongs to the protocol, the fragment belongs to OWNER</h5>
  <p>
    <em>Since 2.0.0.</em> Options on a source are written in the fragment — after the <code>#</code>, several
    of them separated by <code>&amp;</code> — and that is the rule for every loader and every scheme, not a
    quirk of <code>.env</code>. A query is left strictly alone, because on a remote source it means something
    to the server:
    <code>@Sources("https://config/app.env?token=abc#dialect=dotenv")</code> sends the token and keeps the
    dialect. It is also the only place the options can be written at all for a resource inside a jar, whose
    URI has no query to speak of.
  </p>
</div>

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

| Option | Settings | What it decides |
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
@Sources("file:/etc/myapp.env#quotes=strip&escapes=literal&continuation=allow")
```

The same rules are available in Java, over any dialect:

```java
EnvDialect systemd = EnvDialect.DOCKER
        .withQuotesStripped(true)
        .withLineContinuation(true)
        .withBareNames(EnvDialect.BareNames.ERROR);

factory.registerLoader(new DotEnvLoader(systemd));
```

An option or a setting that is not one of the above is **refused, not ignored**, so `#dilaect=docker` fails
at once instead of quietly reading the file by the wrong rules. The message names the option, the source it
was written on, and the options that would have been accepted. The same goes for a loader that takes no
options at all: `classpath:app.properties#dialect=dotenv` is an error, not a line that does nothing.

One thing no dialect does is interpolate `${...}`, because OWNER expands variables in property values
[itself](/owner/docs/variables-expansion/), after loading and across every source, so a loader doing its own
would expand them twice.

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
`WARNING` per file naming the first key concerned and suggesting `#dialect=dotenv`.

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

This is one of only three things OWNER ever says without being asked. The others are a hot reload that
failed — reported once, because the configuration keeps the values it had and tries again at the next check
— and the XML parser warning [above](#xml). All three sit under `org.aeonbits.owner`, so
`org.aeonbits.owner.level = OFF` turns off every one of them at a stroke.

There is one thing it says only when asked, and it is worth knowing about before you need it. At the
`CONFIG` level — which is below `INFO` and therefore silent unless you turn it on — OWNER reports the
loaders it found on the classpath, including when it found none:

```properties
org.aeonbits.owner.level = CONFIG
```

That is the line to reach for when a format seems to be ignored, because a loader that was not found does
not fail: its file falls through to the properties loader and is read as properties, quietly. See
[writing your own loader](/owner/docs/loading-strategies/#writing-your-own-loader).


INI
---

*Since 2.0.0.*

Sections in square brackets, `key = value` below them — the shape of `~/.aws/credentials`, `~/.gitconfig`,
a systemd unit, and a great deal of what is already on a machine.

```ini
name = owner

[server]
host = localhost
port = 8080
```

```properties
name=owner
server.host=localhost
server.port=8080
```

**A section is the prefix of the keys below it**, and that needs no convention of its own: the dot is
already what OWNER uses for [nesting](#how-a-tree-becomes-keys), so `[server.http]` and a nested structure
land on the same keys. Keys written before any section have no prefix at all.

A source whose path ends in `.ini` or `.cfg` is read this way, and both names are looked for beside the
configuration class when no `@Sources` is declared.

### A repeated key is a list

```ini
[servers]
host = alpha
host = beta
```

```properties
servers.host[0]=alpha
servers.host[1]=beta
```

Exactly as [repeated XML elements](#repeated-elements-are-a-list) are numbered, and for the same reason: a
key occurring once keeps its plain key, and only a repeat is numbered — at which point the first moves to
`[0]`. The same name under two different sections is two keys, not a repeat.

This is the point on which the tools that read INI disagree most: Python's `configparser` refuses the file,
git and systemd and Commons Configuration read a list, and the AWS SDK for Java keeps the last. A list is
the answer here because it is the one this library already gives to a repeated XML element, and reading the
same shape two ways would be the surprise. The other three answers are available as `duplicates=error`,
`first` and `last`.

### There is no INI format either

So OWNER implements a **dialect**, as it does for [`.env`](#env), and the dialect is yours to choose.

| | `ini` | `git` | `python` |
|---|---|---|---|
| separator | `=` | `=` | `=` or `:` |
| `[a "b"]` | one section named `a "b"` | subsection → `a.b` | one section |
| `key = "x"` | keeps the quotes | delimiters, escapes expanded | keeps the quotes |
| comment after a value | part of the value | starts a comment | part of the value |
| a line ending in `\` | its own line | joins the next | its own line |
| a line indented further | an error | an error | continues the value |
| a name with no `=` | an error | means `true` | an error |
| a repeated key | a list | a list | an error |
| `[DEFAULT]` | an ordinary section | an ordinary section | inherited by every section |
| key case | as written | as written | lower case |

**`ini` is the default**, and it is the conservative common denominator: everything in that column is
something all five of the tools surveyed agree on. Inline comments are off for a reason worth knowing —
the value most likely to contain a `#` is a password, and losing half of one in silence is worse than
keeping a comment somebody meant.

**`git` earns its name** by reading a subsection: `[remote "origin"]` holding a `url` becomes
`remote.origin.url`, which is the key `git config` itself prints. A mapping interface written against it
reads the same names the tool does.

```java
@Sources("file:${user.home}/.gitconfig#dialect=git")
public interface GitConfig extends Config {
    @Key("user.email")
    String email();

    @Key("remote.origin.url")
    String originUrl();
}
```

### One rule at a time

Over any dialect, in the fragment, as for `.env`:

| Option | Settings |
|---|---|
| `dialect` | `ini`, `git`, `python` |
| `separator` | `equals`, `colon` |
| `duplicates` | `list`, `error`, `first`, `last` |
| `keys` | `literal`, `lower` |
| `bare` | `error`, `ignore`, `true` |
| `comments` | `inline`, `none` |
| `quotes` | `strip`, `literal` |
| `continuation` | `none`, `backslash`, `indent` |
| `subsections` | `allow`, `deny` |
| `default` | `inherit`, `section` |
| `interpolation` | `refuse`, `literal` |

```java
@Sources("file:/etc/myapp.cfg#duplicates=last&comments=inline")
```

<div class="note warning">
  <h5>The <code>python</code> dialect refuses what it would have interpolated</h5>
  <p>
    Python's <code>ConfigParser</code> expands <code>%(name)s</code> inside a value by default, and OWNER
    never will: it expands <a href="/owner/docs/variables-expansion/"><code>${…}</code></a> itself, after
    loading and across every source, so a loader doing its own would expand twice and the two syntaxes would
    mean different things in one value. Handing back <code>%(home)s/log</code> as literal text would make
    the same file mean one thing to Python and another here, quietly — so under this dialect it is an
    <b>error</b> naming the key, and the message points at <code>${…}</code>. Under the other dialects a
    <code>%</code> is an ordinary character.
  </p>
</div>

### When the file is wrong

Refused loudly, each naming the file and the line: a section header that never closes, a section with no
name, an assignment with nothing on the left of the separator, and — unless `bare` says otherwise — a line
that is neither a comment nor an assignment.

JSON
----

*Since 2.0.0, in the [`owner-formats`](/owner/docs/installation/#the-formats-that-are-not-in-the-core)
artifact.* A source whose path ends in `.json` is read as JSON, and the document's shape becomes the keys:

```json
{
  "server": { "host": "localhost", "port": 8080 },
  "servers": [ { "host": "alpha" }, { "host": "beta" } ]
}
```

```properties
server.host=localhost
server.port=8080
servers[0].host=alpha
servers[1].host=beta
```

Which is the same flattening [described above](#how-a-tree-becomes-keys), so a JSON document is read by the
same [nested interfaces](/owner/docs/nested-configuration/), indexed lists and grouped maps as anything
else:

```java
public interface AppConfig extends Config {
    ServerConfig server();

    List<ServerConfig> servers();
}
```

**RFC 8259 and no more.** No comments, no trailing commas, no unquoted names, no single quotes, no leading
zeros: those are JSON5 and JavaScript, and a file this accepted would be one that other tools refuse —
which is the failure a configuration library can least afford, since the same file is nearly always read by
something else too. Every complaint names the line and the column.

**A value is kept as it was written.** `1e3` stays `1e3` and a long past 2<sup>53</sup> keeps its last
digits, because every value here is text until a converter is asked for a type.

Three things the specification leaves to whoever reads it, decided as follows:

| | |
|---|---|
| `"proxy": null` | **writes no key at all.** `Properties` cannot hold a null, so this is the only faithful reading available |
| `"servers": []` | **writes an empty value**, `servers=`, which is already read as an empty collection — and which overrides a `@DefaultValue`, as the document says. An empty *object* writes nothing: a section with nothing in it has nothing to say |
| `{"a": 1, "a": 2}` | **is refused.** JSON has a real way to write a list, so a repeated name is a mistake rather than a shorthand — unlike INI and XML, where a repetition *is* the list |

<div class="note">
  <h5>A null cannot be told from an absent key.</h5>
  <p>
    No method signature in this library distinguishes them: both are <code>null</code>, or an empty
    <code>Optional</code>. So a <code>@DefaultValue</code> wins over a <code>null</code> written on
    purpose, which is not what the author of <code>{"proxy": null}</code> meant. Where that matters, leave
    the key out of the document, or give the method no default.
  </p>
</div>

YAML
----

*Since 2.0.0, in the [`owner-formats`](/owner/docs/installation/#the-formats-that-are-not-in-the-core)
artifact.* A source whose path ends in `.yaml` or `.yml` is read as YAML:

```yaml
server:
  host: localhost
  port: 8080
servers:
  - host: alpha
  - host: beta
ports: [80, 443]
banner: |
  welcome
  to owner
```

```properties
server.host=localhost
server.port=8080
servers[0].host=alpha
servers[1].host=beta
ports[0]=80
ports[1]=443
banner=welcome\nto owner\n
```

Which is the same flattening [described above](#how-a-tree-becomes-keys), so a YAML document is read by the
same [nested interfaces](/owner/docs/nested-configuration/), indexed lists and grouped maps as anything
else.

### A subset, and this is the whole of it

**Read:** block mappings and sequences nested by indentation; a mapping opened on the same line as its dash
(`- host: alpha`); plain, single-quoted and double-quoted scalars with their escapes; the block scalars `|`
and `>` with the chomping indicators `+` and `-`; flow collections, `[80, 443]` and `{A: 1}` — which also
means a JSON document is read, being valid YAML; comments; a leading `---` and a trailing `...`.

**Refused, by name and with the line it is on:**

| | |
|---|---|
| anchors, aliases, merge keys | `&name`, `*name`, `<<:` — write the value where it is used, or merge the sources with `@LoadPolicy(MERGE)` |
| tags | `!!str`, `!Ref` — the type of a value is decided by the method that reads it |
| complex keys | `? ` — a name here is a plain scalar |
| a value continued on the next line | without `|` or `>`; it is indistinguishable from a nested block, and guessing would be wrong half the time |
| a second document | a configuration is one document |
| a tab used as indentation | which YAML forbids, and which no two editors agree about |

Nothing in that list is guessed at, quietly ignored or half-read. A parser that half-understood one of them
would change the meaning of a file rather than decline to read it, which is the one outcome a configuration
library cannot afford.

<div class="note warning">
  <h5>Types are not guessed, which is not what YAML 1.1 does.</h5>
  <p>
    A scalar is kept exactly as written and the method that reads it decides what it means. So
    <code>enabled: yes</code> is the text <code>yes</code>, not a boolean — write <code>true</code> when a
    boolean is meant — and <code>country: no</code> is the string <code>no</code> rather than
    <code>false</code>, which is the famous "Norway problem" not arising.
  </p>
  <p>
    It is also what makes this parser possible at all: implicit type resolution is most of what a complete
    YAML implementation does, and none of it is needed when the interface is where the types are declared.
  </p>
</div>

`host:` with nothing after it, `~` and `null` are the same thing and **write no key at all**, as in
[JSON](#json) and for the same reason. An empty flow sequence writes an empty value, which is read as an
empty collection; an empty mapping writes nothing.

TOML
----

*Since 2.0.0.* A source whose path ends in `.toml` is read as
[TOML v1.0.0](https://toml.io/en/v1.0.0), and `MyConfig.toml` is one of the names tried when a
configuration declares no `@Sources`.

```toml
datacentre = "eu-west"
ports = [80, 443]

[server]
host = "localhost"
port = 8080

[[servers]]
host = "alpha"

[[servers]]
host = "beta"
```

```java
public interface ClusterConfig extends Config {
    String datacentre();
    List<Integer> ports();
    ServerConfig server();
    List<ServerConfig> servers();
}
```

**TOML is the format this library's convention was already shaped like.** An `[[array of tables]]` *is*
`servers[0].host`, a dotted key *is* the [flattening](#how-a-tree-becomes-keys), and a `[table]` is a
prefix — nothing had to be adapted on either side.

### All of v1.0.0, not a subset

[YAML](#yaml) ships as a documented subset because a complete YAML implementation is out of reach without
a dependency. TOML is held to a different standard, and deliberately: its specification is a document
rather than an implementation, and [`toml-test`](https://github.com/toml-lang/toml-test) is a conformance
suite anyone can run — which is the reason this format is written here rather than delegated the way
[HOCON](#hocon) is. Shipping a subset would empty that argument, so the suite is what decides when it is
finished rather than us.

### Values are kept as written, except where TOML writes one value several ways

Everywhere else the text is handed over exactly — our JSON reader answers `1e3` for `1e3`, that being
JSON's only way of writing the number. TOML deliberately offers **several spellings of one value**, and
those are canonicalised, because otherwise they would convert to nothing at all:

| Written | Read as | Why |
|---|---|---|
| `1_000`, `0xDEADBEEF`, `0o755`, `0b1101` | `1000`, `3735928559`, `493`, `13` | four spellings of an integer, and `Integer.parseInt` accepts none of them |
| `inf`, `-inf`, `nan` | `Infinity`, `-Infinity`, `NaN` | how Java writes the same values |
| `1979-05-27 07:32:00` | `1979-05-27T07:32:00` | TOML allows the space; `LocalDateTime.parse` wants the `T` |

Strings are untouched, being the value rather than a spelling of it, and so are ordinary decimals: `3.1415`
and `5e+22` arrive as written.

The four date-time types need nothing registered — `LocalDate`, `LocalTime`, `LocalDateTime` and
`OffsetDateTime` are built by the
[static factory](/owner/docs/type-conversion/#types-built-by-a-static-factory) the conversion chain learnt
in 2.0.0:

```toml
released = 2026-08-12
createdAt = 1979-05-27T07:32:00Z
```

```java
LocalDate released();
OffsetDateTime createdAt();
```

### A key written twice is refused

As for [JSON](#json), and as TOML itself requires: a table defined twice, a table that is already a value,
an inline table something tries to extend, a `[header]` reopening a path a dotted key already created. This
is the opposite of what [HOCON](#hocon) does, and both are right — HOCON says a repetition merges, TOML
says it is a mistake, and each format is read by its own rule.

Every complaint carries the line and the column.

HOCON
-----

*Since 2.0.0.* A source whose path ends in `.conf` is read as
[HOCON](https://github.com/lightbend/config/blob/main/HOCON.md), and `MyConfig.conf` is one of the names
tried when a configuration declares no `@Sources`.

```hocon
datacentre = "eu-west"

server {
  host = localhost      // comments, and no quotes needed
  port = 8080
}

servers = [
  { host = alpha, region = ${datacentre} },   # substitutions
  { host = beta,  region = ${datacentre} }
]
```

```java
public interface ClusterConfig extends Config {
    ServerConfig server();
    List<ServerConfig> servers();
}
```

The document becomes the [same keys every format here flattens to](#how-a-tree-becomes-keys), so nested
interfaces, indexed lists and maps of sections read it the way they read anything else.

### It is the one format we do not parse ourselves

Every other format on this page has a parser written in this project, with no dependency. HOCON has one
too — Lightbend's — and **that is exactly the reason we do not write a second one**. HOCON's specification
*is* an implementation, and the point of the format is reading the `application.conf` files that already
exist. Its substitutions resolve after merging and may refer to the key they are defining; objects written
twice merge instead of replacing one another; `include` pulls in another document mid-parse. A subset
without those would be JSON with comments, which is nobody's reason for choosing HOCON.

Worse, this library already reads `${...}`, with different semantics and at a different moment. An
approximation would not fail on the files it could not handle — it would read them and quietly mean
something else. So the reference implementation is what reads a `.conf`, and a HOCON document means here
what it means everywhere.

### Adding it

`owner-extras` declares the dependency as optional, which means it is yours to add and nobody else
receives it:

```xml
<dependency>
    <groupId>org.aeonbits.owner</groupId>
    <artifactId>owner-extras</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>com.typesafe</groupId>
    <artifactId>config</artifactId>
    <version>1.4.9</version>
</dependency>
```

There is nothing to register: the loader is found on the classpath like every other. It brings no
dependencies of its own — Typesafe Config is a single jar — and it is Apache 2.0.

<div class="note">
  <h5>Nothing is loaded until a <code>.conf</code> source is read.</h5>
  <p>
    The loader is created in every application carrying <code>owner-extras</code>, most of which will not
    have Typesafe Config. Nothing in it refers to Typesafe Config, so that costs nothing: a configuration
    reading any other source is unaffected. Only reading a <code>.conf</code> without the dependency
    fails, and it fails by naming the source and the artifact to add.
  </p>
</div>

### Two rules of its own

**A value comes back as the reference implementation understood it, not as it was written.** Everywhere
else here the text is kept exactly — our JSON reader answers `1e3` for `1e3` — because those parsers hand
over the characters. Typesafe Config parses eagerly into typed values and does not keep the original text,
so `1e3` arrives as `1000` and `1.50` as `1.5`. Nothing a converter needs is lost, and strings, durations
like `10s` and sizes like `512K` are untouched, those being strings in HOCON's own model.

**Substitutions are looked up in the document, then in the system properties, then in the environment** —
and only looked up: not one system property or environment variable becomes a property of your
configuration. A required `${foo}` that resolves to nothing is refused, as HOCON refuses it; an optional
`${?foo}` leaves its key out, which is what a missing key does everywhere here.

`proxy = null` **writes no key at all**, as in [JSON](#json) and [YAML](#yaml) and for the same reason. An
empty list writes an empty value, which is read as an empty collection.

System properties and the environment
-------------------------------------

Not files, but sources all the same, and named the same way:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({"file:~/.myapp.config", "system:properties", "system:env"})
public interface ServerConfig extends Config {
    String hostname();
}
```

See [importing properties](/owner/docs/importing-properties/) for the other ways to bring these in.


What is not read yet
--------------------

**Nothing on the usual list is missing any more.** Properties, XML, `.env`, INI, JSON, YAML, TOML and HOCON
are all read, which is the whole of what a Java configuration library is normally asked for. Being able to
say what is *not* there is half the point of this page, so what is genuinely absent: binary formats such as
CBOR, and anything else nobody has asked for.

The rule that decides how a new one would arrive is worth stating, since the two most recent went opposite
ways. **A format is written here unless its specification *is* an implementation**, in which case it is
delegated and the dependency is optional and yours to add. HOCON is the only one that qualifies so far;
TOML did not, having a written specification, a conformance suite anyone can run, and several independent
implementations that agree on what it means — so TOML is ours and HOCON is Lightbend's.

Two other routes work whatever the format:

- **A loader of your own**, and 2.0.0 made that easier in two ways: it can be **found on the classpath**
  rather than registered by hand, and it can read options off the source it was given. See
  [writing your own loader](/owner/docs/loading-strategies/#writing-your-own-loader). Two projects outside
  this one have been reading YAML and JSON through the [`Loader`][loader] interface for years, which has
  been stable since 1.0.5 and stays that way — everything added in 2.0.0 is a default method, so an
  implementation written against the old interface needs no change and no recompilation.
- **[ZooKeeper](/owner/docs/loading-strategies/)**, in the `owner-extras` artifact, for
  configuration that does not come from a file at all.

  [loader]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/loaders/Loader.html
