---
layout: docs
title: Loading strategies
prev_section: key-prefix
next_section: importing-properties
permalink: /docs/loading-strategies/
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
    <a href="{{ site.url }}/docs/key-prefix/"><code>@Prefix</code></a>, which counts at any depth of the
    hierarchy. Until it is addressed, declare these annotations on the interface you pass to the
    <code>ConfigFactory</code>, or on one it extends directly.
  </p>
</div>

Where the properties come from
------------------------------

A `@Sources` entry is a URI, and the loader that reads it is the first one that declares it accepts it.
Three are available out of the box, and they are consulted in this order:

| Loader | Accepts |
|---|---|
| `SystemLoader` | the `system:properties` and `system:env` pseudo-URIs, and nothing else |
| `XMLLoader` | a URI whose path ends in `.xml` — see [XML support]({{ site.url }}/docs/xml-support/) |
| `PropertiesLoader` | anything that is a valid URL, in the [standard properties format][props] |

`PropertiesLoader` comes last because it accepts everything the other two turned down: it is the
fallback, not a candidate among equals.

A loader you register yourself goes in **front of all of these**, so it takes precedence over the
built-in ones and can be used to take over a URI that one of them would otherwise have accepted.

  [props]: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#load-java.io.Reader-

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
[`@LoadPolicy(MERGE)`]({{ site.url }}/docs/loading-strategies/) can fall back on another one.

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
given URI, how to read one into a `java.util.Properties`, and what default file name to look for when the
configuration declares no `@Sources` at all. Register it on a factory as shown above, and it takes part in
the resolution like the built-in ones.

  [loader]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/loaders/Loader.html
