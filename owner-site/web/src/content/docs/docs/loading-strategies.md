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
found wins — the one on the interface if it has one, otherwise the one on the
first super-interface that declares it, in declaration order.

<div class="note warning">
  <h5>Only the direct super-interfaces are considered.</h5>
  <p>
    All three annotations — <code>@Sources</code>, <code>@LoadPolicy</code> and <code>@HotReload</code> — are
    looked up on the mapping interface and on the interfaces it extends <em>directly</em>. An annotation sitting
    two levels up, on the parent of a parent, is silently ignored: its sources are not loaded, and its policy
    or reload interval does not apply.
  </p>
  <p>
    This is a known limitation rather than a design decision, and it differs from
    <a href="/owner/docs/key-prefix/"><code>@Prefix</code></a>, which counts at any depth of the
    hierarchy. Until it is addressed, declare these annotations on the interface you pass to the
    <code>ConfigFactory</code>, or on one it extends directly.
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
`MyConfig.properties`, `MyConfig.xml`, `MyConfig.ini` and `MyConfig.cfg` and nothing more.
`SystemLoader` and `DotEnvLoader` offer none: they answer when they are named and cost nothing when
they are not, a `.env` being neither on the classpath nor named after the configuration class.

  [props]: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#load-java.io.Reader-

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
the children of a ZooKeeper node. It is not registered by default, since it needs
[Apache Curator][curator] on the classpath: declare that dependency, register the loader on a factory of
your own, and address the source with the `zookeeper` scheme.

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
Factory factory = ConfigFactory.newInstance();
factory.registerLoader(new ZooKeeperLoader());

ServerConfig cfg = factory.create(ServerConfig.class);
```

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

The only built-in loader with options of its own is the `.env` one; see
[File formats](/owner/docs/file-formats/#one-rule-at-a-time).

### What 2.0.0 added, and what it did not break

Everything above is additive. `defaultSpecsFor` and a default `defaultSpecFor` are `default` methods, so a
`Loader` written against 1.x compiles and runs unchanged, and does not need recompiling; a loader offering a
single file name may still say so with `defaultSpecFor`, and one whose format goes by two names overrides
`defaultSpecsFor` instead. Returning `null` from the first, or an empty array from the second, means the
loader adds nothing to what is looked for — which is what `SystemLoader` and `DotEnvLoader` both do.
