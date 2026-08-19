---
title: "Loading strategies"
---


The properties file for a *mapping interface* is automatically resolved by OWNER 
API by matching the class name and the file name (appending `.properties` of 
course).  

But this logic can be tailored to your needs using some additional annotations:  

```java
@Sources({ "file:~/.myapp.config", 
           "file:/etc/myapp.config", 
           "classpath:foo/bar/baz.properties" })
public interface ServerConfig extends Config {
    @Key("server.http.port")
    int port();
    
    @Key("server.host.name")
    String hostname();
    
    @Key("server.max.threads")
    @DefaultValue("42")
    int maxThreads();
}
```

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html


In the above example, OWNER will try to load the properties from several `@Sources`:

 1. First, it will try to load the properties file from user's home directory ~/.myapp.config, if this is found, this
    file alone will be used.
 2. If the previous attempt fails, then it will try to load the properties file from /etc/myapp.config, and if this is
    found, this one will be used.
 3. As last resort, it will try to load the properties from the classpath loading the resource identified by the
    path foo/bar/baz.properties.
 4. If none of the previous URL resources is found, then the Java interface will not be associated to any file, and only
    `@DefaultValue` will be used where specified. Where properties don't have a default value, `null` will be returned
    (as it happens for [`java.util.Properties`][properties]).

In the above case, the properties values will be loaded from only one file: the first that is found.
*Only the first available properties file will be loaded, others will be ignored*.

This load logic, is identified as *"FIRST"*, since only the first file found will be considered, and it is the default
logic adopted when the `@Source` annotation is specified with multiple URLs.  
You can also specify this load policy explicitly using `@LoadPolicy(LoadType.FIRST)` on the interface declaration.

But what if you want to have some *overriding* between properties? This is definitely possible: you can do it with
the annotation `@LoadPolicy(LoadType.MERGE)`:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({ "file:~/.myapp.config", 
           "file:/etc/myapp.config", 
           "classpath:foo/bar/baz.properties",
           "system:properties",
           "system:env" })
public interface ServerConfig extends Config {
    ...
}
```

In this case, for *every property* all the specified URLs will be queries, and the first resource defining the property
will prevail.

> A source can also be named **inside a file**, so that a deployment wanting one more of them needs no
> recompiling: see [One file building on another](/owner/docs/includes/). The sources a file names are read
> immediately below it, and the same first-named-wins rule decides between them.
More in detail, this is what will happen:

 1. First, it will try to load the given property from ~/.myapp.config;
    if the given property is found the associated value will be returned.
 2. Then it will try to load the given property from /etc/myapp.config;
    if the property is found the value associated will be returned.
 3. Then it will try to load the given property from the classpath from the resource identified
    by the path foo/bar/baz.properties; if the property is found, the associated value is returned.
 4. Otherwise, it will try to load the given property from the <a  href="https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html">Java system properties</a>;
 if such property is defined, the associated value is returned.
 5. As last resort, it will try to load the given property from the <a    href="https://docs.oracle.com/javase/tutorial/essential/environment/env.html">operating system's environment variables</a>;
 if an environment variable with the same name is found, its value will be returned.    
 6. If the given property is not found of any of the above cases, it will be returned the value specified by the
    `@DefaultValue` if specified, otherwise null will be returned.

So basically we produce a merge between the properties files where the first property files overrides latter ones.

The `@Sources` annotation considers system properties and/or environment variables with the syntax
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' system property) or `file:${HOME}/.myapp.config`
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of
variable expansion, and it is equivalent to `${user.home}`.

Sources and interface inheritance
---------------------------------

When a *mapping interface* extends other interfaces, `@Sources` behaves
differently from the other interface-level annotations, **and that is
deliberate**: it does not pick one annotation and ignore the rest, it
**accumulates**. The URIs declared on the interface come first, followed by
those declared on each of its super-interfaces, and the resulting list is the
one the load policy is applied to.

```java
@Sources("classpath:common.properties")
public interface CommonConfig extends Config {
    String applicationName();
}

@Sources("classpath:server.properties")
@LoadPolicy(LoadType.MERGE)
public interface ServerConfig extends CommonConfig {
    int port();
}
```

`ServerConfig` reads `server.properties` *and* `common.properties`, in that
order, so a property defined in both is taken from `server.properties`. This
is what makes it possible to describe a shared set of defaults once and let
each configuration override the part it cares about, which is the whole point
of extending a mapping interface.

Contrast this with `@LoadPolicy` and `@HotReload`, which describe a single
setting and therefore cannot be accumulated: for those, the first annotation
found wins — the one on the interface if it has one, otherwise the nearest one
above it.

All three are looked for on the whole hierarchy, and the order the interfaces
are visited in is what "nearest" means: the mapping interface first, then every
interface it extends **directly**, in the order of the `extends` clause, then
the ones those extend, and so on. Every direct parent is therefore asked before
any grandparent, and an interface reached by two paths is read once — so it
contributes its `@Sources` once.

<div class="note info">
  <h5>Before 2.0.0 the lookup stopped at the direct super-interfaces.</h5>
  <p>
    An annotation sitting two levels up, on the parent of a parent, used to be silently ignored: its sources
    were not loaded, and its policy or reload interval did not apply. It differed from
    <a href="/owner/docs/key-prefix/"><code>@Prefix</code></a>, which has always counted at any depth, and each
    of the three annotations had its own copy of the lookup, which is how the three came to disagree with a
    fourth. They share one now, with every other annotation that describes the configuration object rather
    than the methods of one interface — see
    <a href="/owner/docs/annotation-scope/">where an annotation counts</a>.
  </p>
  <p>
    If you were working around this by repeating an annotation on the interface handed to the
    <code>ConfigFactory</code>, the repetition is no longer needed — and for <code>@Sources</code> it is
    no longer harmless, since the same file would now be listed twice.
  </p>
</div>

<div class="note warning">
  <h5>Declaring <code>@Sources</code> switches the convention off.</h5>
  <p>
    When nobody in the hierarchy declares <code>@Sources</code>, the properties are looked for by convention,
    under the name of the mapping interface — see <a href="#where-the-properties-come-from">below</a>. That is
    a fallback for a configuration that names no source, <em>not</em> an extra source appended to the ones it
    named: an interface carrying <code>@Sources</code> reads what it asked for and nothing else.
  </p>
  <p>
    Before 2.0.0 the convention was appended to the declared sources as well, because the two were the same
    lookup: every interface without the annotation contributed the default list, and <code>Config</code> itself
    has none. So a <code>MyConfig.properties</code> on the classpath was read even by a configuration that had
    declared its sources elsewhere. If that is what you want, declare it: <code>@Sources({"file:my.properties",
    "classpath:com/acme/MyConfig.properties"})</code> says so, and says it where it can be seen.
  </p>
</div>

Where the properties come from
------------------------------

A `@Sources` entry is a URI, and the loader that reads it is the first one that declares it accepts it.
Five are available out of the box, and they are consulted in this order:

| Loader | Accepts |
|---|---|
| `SystemLoader` | the `system:properties` and `system:env` pseudo-URIs, and nothing else |
| `IniLoader` | a URI whose path ends in `.ini` or `.cfg` — see [File formats](/owner/docs/file-formats/#ini) |
| `DotEnvLoader` | a URI whose path ends in `.env` — see [File formats](/owner/docs/file-formats/#env) |
| `XMLLoader` | a URI whose path ends in `.xml` — see [File formats](/owner/docs/file-formats/#xml) |
| `PropertiesLoader` | anything that is a valid URL, in the [standard properties format][props] |

`PropertiesLoader` comes last because it accepts everything the others turned down: it is the
fallback, not a candidate among equals.

Three of them offer default file names, so a configuration with no `@Sources` looks for
`MyConfig.properties`, `MyConfig.xml`, `MyConfig.ini` and `MyConfig.cfg` and nothing more —
**in that order**, which is the subject of the next section. `SystemLoader` and `DotEnvLoader` offer
none: they answer when they are named and cost nothing when they are not, a `.env` being neither on
the classpath nor named after the configuration class.

The conventional sources
------------------------

The files named after the mapping interface are *the convention*, and three questions come with them:
which one wins when there is more than one, what happens when that is not what you meant, and how to
ask for them by name from a configuration that also declares sources of its own.

### Which one wins

The order is the one above — `.properties`, `.xml`, `.ini`, `.cfg`, and then any file name offered by
a [loader found on the classpath](#where-a-loader-of-your-own-sits-among-them) — and under the default `LoadType.FIRST` the
first one that **exists** is the one that answers.

It is **the reverse of the table above**, and deliberately:

| Question | Order | Why |
|---|---|---|
| *Can you read this URI?* | `PropertiesLoader` **last** | it accepts every URL it can resolve, so asked earlier it would answer for the `.ini` and `.xml` files of the loaders after it |
| *Which file is this configuration's own?* | `PropertiesLoader` **first** | `MyConfig.properties` is the convention this library was built on and the file an application has been reading for years |

Being able to read anything is a liability in the first question and an asset in the second. `.cfg`
is last of the four for the same reason read backwards: it is the most generic of the names and the
likeliest to belong to somebody else's tool.

<div class="note warning">
  <h5>Until 2.0.0 this order was never chosen.</h5>
  <p>
    It was the registration order of the loaders — which exists to answer the <em>first</em> question —
    so <code>MyConfig.ini</code> and <code>MyConfig.cfg</code> silently outranked
    <code>MyConfig.properties</code>. An application reading its <code>.properties</code> for years would
    have stopped, without a word, the day somebody dropped a <code>.cfg</code> in the same directory.
  </p>
</div>

### When there is more than one, the library says so

No order can do better than choose *which* of two silences you get: the file you did not expect being
read, or the file you did expect being ignored. So when more than one conventional file exists, it is
said out loud — a `WARNING`, naming both, saying which was read and how to end the ambiguity:

```
MyConfig: more than one conventional source exists: classpath:com/acme/MyConfig.properties,
classpath:com/acme/MyConfig.xml. With LoadType.FIRST only classpath:com/acme/MyConfig.properties is
read. Name the one you mean with @Sources, or owner:default.<extension>.
```

Under [`owner.strict`](/owner/docs/configuring/) it is a refusal, like every other warning that has a
caller to refuse.

### Asking for the convention by name

`owner:default` — the constant `Config.Sources.CONVENTIONAL` — stands, **where you write it**, for
everything the configuration would look for if it declared no sources at all:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({"file:~/myapp.conf", "system:env", Sources.CONVENTIONAL})
public interface MyConfig extends Config { }
```

Two things are worth knowing about it:

- **the name is that of the interface you hand to the `ConfigFactory`**, not of the one carrying the
  annotation, so a base interface can tell twenty configurations to read their own conventional file
  and each of them reads its own;
- **followed by an extension** — `owner:default.xml` — it stands for that one conventional source
  instead of all of them. An extension no loader offers is **refused** rather than passed over: a
  source that resolves to nothing is skipped by design, so a misspelt `owner:default.propertis` would
  otherwise leave a configuration reading nothing and saying nothing about it.

The alternative is to write `classpath:com/acme/MyConfig.properties` by hand. That works, and it has
two costs: it repeats the package, and it is a string no refactoring will follow — move the interface
to another package and that source stops resolving, silently.

### The use cases, and how each is written

| You want | Write |
|---|---|
| your own sources **and** your conventional file, without spelling its path — [#267](https://github.com/matteobaccan/owner/issues/267) | `@Sources({"file:~/foo.config", Sources.CONVENTIONAL})` |
| the environment to win, the conventional file as the base | `@Sources({"system:env", Sources.CONVENTIONAL})` with `@LoadPolicy(MERGE)` |
| one base interface, twenty configurations, each reading its own file | the same `@Sources` on the base — the name follows the interface being created |
| only the XML one, because a `.cfg` in that directory belongs to another tool | `@Sources("owner:default.xml")` |
| YAML if it is there, properties otherwise | `@Sources({"owner:default.yaml", "owner:default.properties"})` with `FIRST` |
| to know why your new `MyConfig.ini` is not being read | nothing: the `WARNING` above names the file that won |

  [props]: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#load-java.io.Reader-

### A source over the network

Every loader opens its source with `uri.toURL().openStream()`, so **a `@Sources` entry may name any
protocol the JVM knows** — and that has been true since 1.0.5 without ever being written down here:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({
    "https://config.example.com/myapp.properties",
    "classpath:myapp.properties" })
public interface MyConfig extends Config { }
```

The format is still chosen from the extension **after** the stream is open, so a `.yaml` or a `.env`
served over HTTPS is read by the loader for that format like any local file.

That covers most of what is usually meant by keeping configuration outside the artifact: **a pre-signed
S3 URL, an Azure Blob SAS, a signed Google Cloud Storage URL, a public bucket, a config server and a raw
file from a Git host are all HTTPS**. Nothing needs to be registered and no dependency is involved.

A remote source behaves like every other one under the [loading
strategies](#loading-strategies-loadpolicy-and-loadtype): first-wins under `FIRST`, merged under `MERGE`.
Two cases are worth telling apart, and the library does:

| what the server answers | what happens |
|---|---|
| **404** | the source was **not there**, which is the network's version of a missing file. Passed over in silence, `owner.strict` or not — refusing it would break the commonest shape a configuration has |
| **500, a refused connection, a timeout** | the source **is** there and something is wrong with it. A `WARNING` naming it, and a refusal under [`owner.strict`](#refusing-everything-that-would-only-have-been-a-warning) |

<div class="note warning">
  <h5>What is not covered, and what to do about it</h5>
  <p>
    A URL is all the authentication there is. A token in the query string works —
    <code>?token=abc</code> is left alone, since the query belongs to the protocol — but anything needing
    a signature or a header does not: <code>s3://bucket/key</code> signed with SigV4, a Vault token, Google
    Application Default Credentials.
  </p>
  <p>
    For those, <b>teach the JVM the protocol rather than teaching OWNER</b> — see below. Writing a
    <a href="#where-a-loader-of-your-own-sits-among-them">loader of your own</a> is the other way, and the
    right one when the source is not a file at all: a key/value API rather than a document.
  </p>
</div>

### A protocol the JVM does not know

`@Sources` names a URI and every loader opens it with `uri.toURL()`, so **the question is never whether
OWNER speaks a protocol — it is whether the JVM does**. And that is extensible, by the application rather
than by us:

```java
URL.setURLStreamHandlerFactory(protocol -> {
    if (!"s3".equals(protocol)) return null;   // everything else: the JVM's own handlers, untouched
    return new S3UrlStreamHandler(s3Client);   // yours, holding whatever credentials it needs
});
```

```java
@Sources("s3://bucket/app.properties")
public interface MyConfig extends Config { }
```

Nothing is registered with OWNER and no loader is written. The source merges like any other, the format is
still chosen from the extension after the stream is open, and a handler that cannot produce the object
takes the same path as any unreadable source — a warning, or a refusal under `owner.strict`.

All of that is pinned down in
[`UnknownProtocolSourceTest`](https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/UnknownProtocolSourceTest.java),
which teaches the test JVM a made-up scheme and reads a configuration over it.

<div class="note info">
  <h5>Once per JVM, which is why it is yours and not ours</h5>
  <p>
    <code>URL.setURLStreamHandlerFactory</code> may be called <b>once</b> for the life of a JVM. That is
    exactly why a library must not call it on your behalf: it is a decision that belongs to the
    application, next to the credentials such a handler needs — the same rule this library applies to an
    <a href="/owner/docs/crypto/">encryption passphrase</a>. Since Java 9 there is a tidier route with no
    such limit, the <code>java.net.spi.URLStreamHandlerProvider</code> service, and a reader on 9 or later
    should prefer it; the example above uses the older call because it works on the Java 8 baseline this
    library still supports.
  </p>
</div>

### Where a loader of your own sits among them

*Since 2.0.0.* There are two ways in, and they land in different places:

| | Matching a source | Guessing the file name |
|---|---|---|
| **registered** with `registerLoader` | first of all | first of all |
| **found** on the classpath | after the registered ones, before the built-in ones | **last of all** |
| built in | last, `PropertiesLoader` last of those | in the middle |

Both come before the built-in loaders when a source is being matched — otherwise `PropertiesLoader`, which
accepts every URL it can resolve, would take their files before they were asked.

The second column is deliberately not the same, and it is the one that matters when a configuration
declares no `@Sources` at all. Those names are tried in order, and under `LoadType.FIRST` the first that
resolves is the one that answers. If a loader found on the classpath contributed its name first, adding a
jar to a build would be enough to make a stray `MyConfig.yaml` start beating the `MyConfig.properties` an
application already reads — a change of behaviour nobody asked for and nothing announced. Placed last, it
can only answer for a name nothing else claimed. Registering a loader by hand is a different matter: that is
something the application said on purpose, so it keeps the front in both columns.

Reading from ZooKeeper
----------------------

The `owner-extras` artifact ships a further loader, `ZooKeeperLoader`, which reads the properties from
the children of a ZooKeeper node. It needs [Apache Curator][curator], which `owner-extras` declares as an
optional dependency and therefore does not bring along: declare that dependency yourself, and address the
source with the `zookeeper` scheme.

```xml
<dependency>
    <groupId>org.aeonbits.owner</groupId>
    <artifactId>owner-extras</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-framework</artifactId>
    <version>5.9.0</version>
</dependency>
```

```java
@Sources("zookeeper://zookeeper.example.com:2181/config/myapp")
public interface ServerConfig extends Config {
    int port();
    String hostname();
}
```

```java
ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
```

*Since 2.0.0* the loader is found on the classpath like every other, so there is nothing to register and
no factory of your own to create. Before then it had to be registered by hand, and code that still does
keeps working — registering a loader that was also discovered changes only where it sits in the order,
and it is already ahead of everything that could compete for a `zookeeper:` source.

<div class="note">
  <h5>Nothing is loaded until a <code>zookeeper:</code> source is read.</h5>
  <p>
    Being discovered means the loader is created in every application that carries
    <code>owner-extras</code>, most of which will not have Curator. Nothing in it refers to Curator, so
    that costs nothing and breaks nothing: a configuration reading any other source is unaffected, and it
    contributes no file name to the ones tried when a configuration declares no <code>@Sources</code>.
    Only reading a <code>zookeeper:</code> source without Curator fails, and it fails by naming the source
    and the artifact to add rather than with a <code>NoClassDefFoundError</code>.
  </p>
</div>

Each child of the node named by the path becomes a property: the name of the child is the key and its
data, read as a string, is the value. The port may be omitted, in which case the default of the client is
used. Connecting is given thirty seconds before it gives up, which the
`owner.zookeeper.connection.timeout.seconds` System Property changes; a failure to connect surfaces as an
`IOException`, so it behaves like any other unreachable source and
[`@LoadPolicy(MERGE)`](/owner/docs/loading-strategies/) can fall back on another one.

  [curator]: https://curator.apache.org

<div class="note warning">
  <h5>The class moved in 2.0.0</h5>
  <p>
    <code>ZooKeeperLoader</code> is now in <code>org.aeonbits.owner.extras.loaders</code>, where it used to
    be in <code>org.aeonbits.owner.loaders</code>. Only the import changes. The old package belongs to the
    core artifact, and a package cannot live in two modules, which kept the two jars from being placed on
    the module path together.
  </p>
</div>

Writing your own loader
-----------------------

A loader is an implementation of [`Loader`][loader], which answers three questions: whether it accepts a
given URI, how to read one into a `java.util.Properties`, and what file name — or names — to look for when
the configuration declares no `@Sources` at all.

```java
public class YamlLoader implements Loader {

    private static final String[] SUFFIXES = {".yaml", ".yml"};

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIXES);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();   // this loader takes no options
        // ...read the source into result...
    }

    @Override
    public String[] defaultSpecsFor(String uriPrefix) {
        return new String[] { uriPrefix + ".yaml", uriPrefix + ".yml" };
    }
}
```

Register it on a factory as shown above and it takes part in the resolution like the built-in ones.

  [loader]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/loaders/Loader.html

### Letting it be found instead

*Since 2.0.0.* A loader declared in `META-INF/services/org.aeonbits.owner.loaders.Loader` is picked up when
a factory is created, without anyone calling `registerLoader`:

```
org.example.YamlLoader
```

It has to be a public class with a public no-argument constructor — that is
[`ServiceLoader`][serviceloader]'s requirement, not OWNER's. Being found **enables** it: it answers for its
formats at once, and its default file names join the ones looked for when an interface declares no
`@Sources`. That is what a jar shipping a format is for, so there is nothing further to turn on.

<div class="note warning">
  <h5>Which class loader does the looking, and when that matters</h5>
  <p>
    The context class loader of the thread that creates the factory, falling back on the one that loaded
    OWNER when the thread has none. That is right in an ordinary application, where there is effectively one
    class loader, and it is right in an application server, where OWNER sits in the shared libraries and
    only the context class loader can see a jar in a web application's <code>WEB-INF/lib</code> — a parent
    never sees a child's jars.
  </p>
  <p>
    It is not right everywhere. On a thread a container or a pool set up, the context class loader may point
    at something that knows nothing of your application, and the loader is not found although it is on the
    class path. Under OSGi there is no class path to search and <code>ServiceLoader</code> needs help from
    the container to work at all. In both cases, call <code>registerLoader</code>: that route depends on
    nothing.
  </p>
</div>

<div class="note warning">
  <h5>A loader that is not found does not look like an error</h5>
  <p>
    <code>PropertiesLoader</code> accepts every URL it can resolve and is consulted last, so a
    <code>app.yaml</code> whose loader was not found is not left unread — it is read <b>as a properties
    file</b>, and the configuration comes back holding almost nothing, with nothing said. When a format
    seems to be ignored, put <code>org.aeonbits.owner.level = CONFIG</code> in a
    <code>logging.properties</code>: OWNER then names the loaders it found, including when it found none,
    which tells a loader that is absent from a loader that is broken.
  </p>
</div>

  [serviceloader]: https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html

### Options on a source

*Since 2.0.0.* A loader can be told something about one particular source, written in the fragment of its
URI — after the `#`, several separated by `&`:

```java
@Sources("file:.env#dialect=dotenv&quotes=strip")
```

**The query belongs to the protocol and the fragment belongs to OWNER.** A query is never touched, because
on a remote source it means something to the server; the fragment is never sent to one. It is also the only
place the options can be written for a resource inside a jar, whose URI has no query to speak of.
`SourceOptions.of(uri)` reads them and `refuseUnknown(...)` turns away the ones a loader does not
understand, naming the offender, the source and what would have been accepted — because an option that is
misspelt and ignored is a configuration that is wrong and says nothing.

Two built-in loaders have options of their own: the `.env` one, whose
[dialect](/owner/docs/file-formats/#one-rule-at-a-time) it is, and the XML one, which can be told
[not to validate](/owner/docs/file-formats/#a-document-is-held-to-the-grammar-it-declares).

One option is not a loader's at all. **`required` is read by the library**, before any loader is chosen,
and every loader therefore accepts it without declaring it — see below.

### A source that was named and did not arrive

*Since 2.0.0.* A source that cannot be read does not stop the others: that is what a fallback chain is for.
But not every failure is a fallback, and until 2.0.0 they were all treated as one and passed over in
silence. Three cases are now distinguished:

- **A source that is simply not there says nothing.** With `LoadType.FIRST` every miss but the last is how
  the feature works, and a configuration with no `@Sources` probes four names for every interface. A
  warning here would be noise, and noise is how a real warning stops being read.
- **A source that is there and cannot be read is a `WARNING`.** A wrong permission, a network source
  refusing, a host that does not answer: nobody designs a fallback on one of those, and what they produce
  is a configuration full of defaults.
- **Sources declared and not one of them readable is a `WARNING` of its own**, because that is what a
  mistyped path looks like — every miss legitimate on its own, and only their sum wrong. A configuration
  that declares no `@Sources` is left alone: finding no file among the four probed names is how a
  configuration made entirely of defaults is written.

Each is said **once**, and again only if the failure changes, since a reload runs the whole load again and
a hot reload runs it at its interval for as long as the process lives.

<div class="note">
  <h5>Which of the two it is, is not read off the exception.</h5>
  <p>
    <code>FileInputStream</code> throws <code>FileNotFoundException</code> for a file that is missing, for
    a directory named where a file was meant, and for one it may not open — the three things this rule
    exists to tell apart. For a file the filesystem is asked instead; only a source that is not a file
    falls back on the exception, where a <code>FileNotFoundException</code> is a 404 or a missing entry in
    a jar.
  </p>
</div>

### Saying that a source has to be there

*Since 2.0.0.* `required=true` on a source turns its absence from a fallback into an error, refused when the
configuration is created:

```java
@Sources({"file:/etc/myapp.properties#required=true", "classpath:defaults.properties"})
```

It is the counterpart of Spring's `optional:` prefix, the other way up: their sources must exist unless
marked optional, ours may be missing unless marked required — which is what keeps every fallback chain
written so far working as it did.

It applies to a source that is absent, to one that cannot be read, and to a `classpath:` resource that
resolves to nothing, which is the case that never reaches a loader at all and would otherwise have been the
one place where the promise was quietly dropped.

Unlike a dialect or a validation flag, `required` is **not** an option of a loader — whether a source may be
missing is decided before a loader is chosen, and is the same question for all of them. That is why no
loader has to declare it and none of them refuses it.

### Refusing everything that would only have been a warning

*Since 2.0.0.* `required=true` says it for one source. **`owner.strict` says it for the whole
configuration**, on the [factory](/owner/docs/configuring/):

```java
Factory factory = ConfigFactory.newInstance();
factory.setProperty("owner.strict", "true");

ServerConfig cfg = factory.create(ServerConfig.class);   // refuses instead of warning
```

The default is off, and off is how OWNER has always behaved. It is worth knowing why the switch exists at
all: **this library's way of failing is to keep working.** A source that cannot be read is passed over, the
object is built out of default values, and the caller gets something that works and lies. That is a
deliberate choice — a fallback is meant to work — but it is invisible until somebody reads the wrong value
in production. Most of the field either throws or refuses to start; OWNER carries on, so the warnings above
carry more weight here than they would elsewhere, and until 2.0.0 they were all a caller could get.

**What counts as a failure is not a list of its own: it is the warnings.** They were already chosen to
leave the legitimate cases alone, so strict inherits that care instead of restating it and drifting from
it. Five things are refused:

| refused under `owner.strict` | |
|---|---|
| a source that was named and could not be read | the second bullet above |
| not one of the declared sources could be read | the third bullet above |
| `@HotReload` over a source nobody can watch | see [Reload](/owner/docs/reload/) |
| a value built out of an `@EncryptedValue` one | see [Crypto support](/owner/docs/crypto/) — and note that a [marker](/owner/docs/crypto/) does not have this problem at all, so this refusal is about the older annotation |
| a variable that resolves to nothing | see [Variables expansion](/owner/docs/variables-expansion/#a-variable-that-resolves-to-nothing) |

And what it leaves alone matters as much:

- **A source that is merely absent stays silent**, strict or not. `LoadType.FIRST` expects misses by
  design, and refusing them would make the property unusable with the commonest shape a configuration has.
- **A configuration made entirely of defaults** declares no sources, so nothing failed to be read.
- **A reload that fails** is outside this on purpose: it happens later, on a scheduled thread with nobody
  to refuse, and turning a transient failure into a crash is worse than the warning. The
  [event API](/owner/docs/event-support/) is where a reload problem is answered.

It raises the `UnsupportedOperationException` that `required=true` already raises for the identical case,
and it belongs to the **factory** rather than to the JVM: an application turning it on does not make a
library that happens to use OWNER strict as a side effect.

<div class="note info">
  <h5>Two loaders warn and are not covered.</h5>
  <p>
    The XML one when the parser will not support the hardening, and the <code>.env</code> one when values
    are quoted under a dialect that keeps quotes. <code>Loader.load(Properties, URI)</code> is a public SPI
    with no way to reach the factory, so strictness cannot be handed to a loader without changing it. Both
    already have a per-source answer in the fragment — <code>#validate=false</code> and
    <code>#dialect=</code> — so neither is left without one.
  </p>
</div>

### What 2.0.0 added, and what it did not break

Everything above is additive. `defaultSpecsFor` and a default `defaultSpecFor` are `default` methods, so a
`Loader` written against 1.x compiles and runs unchanged, and does not need recompiling; a loader offering a
single file name may still say so with `defaultSpecFor`, and one whose format goes by two names overrides
`defaultSpecsFor` instead. Returning `null` from the first, or an empty array from the second, means the
loader adds nothing to what is looked for — which is what `SystemLoader` and `DotEnvLoader` both do.
