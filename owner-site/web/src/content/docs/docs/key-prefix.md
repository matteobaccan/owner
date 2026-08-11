---
title: "Key prefix"
---

Configuration keys are commonly grouped under a common prefix: every property
belonging to the server is called `server.something`, every property belonging
to the database is called `db.something`, and so on.

Mapping such a file with the `@Key` annotation alone means repeating that
prefix on every single method:

```properties
# ServerConfig.properties
server.hostname=foobar.com
server.port=80
server.max.threads=100
```

```java
public interface ServerConfig extends Config {
    @Key("server.hostname")
    String hostname();

    @Key("server.port")
    int port();

    @Key("server.max.threads")
    @DefaultValue("42")
    int maxThreads();
}
```

Since version 2.0.0, the `@Prefix` annotation lets you state the common part
once, on the interface:

```java
@Prefix("server.")
public interface ServerConfig extends Config {
    String hostname();

    int port();

    @Key("max.threads")
    @DefaultValue("42")
    int maxThreads();
}
```

The two interfaces above read exactly the same properties. The prefix is
prepended to the key of every property declared in the interface: to the key
derived from the method name, as for `hostname()` and `port()`, and to the one
given by `@Key`, as for `maxThreads()` — which is looked up as
`server.max.threads`.

<div class="note">
  <h5>The prefix is concatenated literally.</h5>
  <p>
    Nothing is inserted between the prefix and the key: <code>@Prefix("server.")</code> gives
    <code>server.hostname</code>, while <code>@Prefix("server")</code> — without the trailing dot —
    gives <code>serverhostname</code>. Ending the prefix with the separator you want to use is the
    recommended way to write it, and the one all the examples in this chapter follow.
  </p>
  <p>
    The concatenation stays literal on purpose, and OWNER neither adds a separator nor complains
    about a missing one: a prefix is just the leading part of a key, so a naming scheme that does
    not use a separator — <code>@Prefix("db_")</code>, or a prefix ending in the middle of a word —
    is a legitimate use of the annotation rather than a mistake to be corrected.
  </p>
</div>

The prefix is only about *lookup*: it changes the key a method resolves to, and
nothing else. A `@DefaultValue` declared on a prefixed method is registered
under the prefixed key, and the methods of
[`Accessible` and `Mutable`](/owner/docs/accessible-mutable/) keep
taking plain property names, so they too need the full key, prefix included:

```java
@Prefix("server.")
public interface ServerConfig extends Config, Accessible {
    @Key("max.threads")
    @DefaultValue("42")
    int maxThreads();
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);

cfg.maxThreads();                         // 42
cfg.getProperty("server.max.threads");    // "42"
cfg.getProperty("max.threads");           // null
```

For the same reason, a missing
[mandatory property](/owner/docs/usage/#mandatory-properties) is reported by its
prefixed key, which is the one that could not be resolved.


Prefixes and interface inheritance
----------------------------------

**Every method takes the prefix of the interface where it is declared.** A
prefix therefore never leaks onto the methods a sub-interface inherits, and a
method keeps the prefix of the interface that declares it however deep the
hierarchy goes:

```java
@Prefix("datasource.")
public interface DataSourceConfig extends Config {
    String url();                 // datasource.url
}

@Prefix("pool.")
public interface PoolConfig extends DataSourceConfig {
    int maxSize();                // pool.maxSize
                                  // url() is still datasource.url
}

@Prefix("metrics.")
public interface MonitoredPoolConfig extends PoolConfig {
    boolean enabled();            // metrics.enabled
                                  // maxSize() is still pool.maxSize
                                  // url() is still datasource.url
}
```

The rule holds in both directions, so mixing prefixed and unprefixed
interfaces does what the rule says and nothing more.

An interface that declares no prefix of its own does not remove the prefix of
what it inherits:

```java
public interface PlainConfig extends DataSourceConfig {
    String name();                // name
}
```

`name()` resolves to `name` and `url()` still resolves to `datasource.url`.

Symmetrically, a prefixed interface does not push its prefix onto what it
inherits from an unprefixed one — the methods of the super-interface keep their
bare keys:

```java
public interface PlainConfig extends Config {
    String name();                // name
}

@Prefix("pool.")
public interface PoolConfig extends PlainConfig {
    int maxSize();                // pool.maxSize
                                  // name() is still name
}
```

The same applies to the `@DefaultValue` of an inherited method: it is
registered under the key of the interface that declares it, so a default
declared in `PlainConfig` stays under `name`, not under `pool.name`.

This makes `@Prefix` a natural fit for composing a configuration out of
reusable pieces: each interface describes one section of the properties file
and carries the name of that section with it, so an interface that extends
several of them reads every property under the section it belongs to.

<div class="note info">
  <h5>Overriding the prefix of an inherited method.</h5>
  <p>
    Since the prefix follows the declaration, re-declaring a method in the sub-interface moves it
    under the prefix of the sub-interface. Writing <code>String url();</code> again inside
    <code>PoolConfig</code> makes it resolve to <code>pool.url</code> instead of
    <code>datasource.url</code>.
  </p>
</div>

Composing a configuration out of reusable pieces has a second form, in which
the sections are objects of their own rather than interfaces to inherit from:
see [Nested configuration](/owner/docs/nested-configuration/). A `@Prefix`
declared on a nested interface **composes** with the path it hangs from,
where the one configured on a factory is overridden by it.


Variables in the prefix
-----------------------

The prefix is part of the key, so it goes through
[variables expansion](/owner/docs/variables-expansion/#variable-expansion-for-the-key) like the
rest of it. Given the multi-environment properties file used in that chapter:

```properties
servers.dev.name=Development
servers.dev.hostname=devhost
servers.dev.port=6000

servers.uat.name=User Acceptance Test
servers.uat.hostname=uathost
servers.uat.port=60020

servers.prod.name=Production
servers.prod.hostname=prod-host
servers.prod.port=600
```

the interface that selects one environment at runtime can be written without
repeating `servers.${env}.` on every method:

```java
@Prefix("servers.${env}.")
public interface ServerConfig extends Config {

    @DisableFeature(PREFIX)
    @DefaultValue("dev")
    String env();

    String name();

    String hostname();

    Integer port();
}
```

```java
Map<String, String> myVars = new HashMap<String, String>();
myVars.put("env", "uat");

ServerConfig cfg = ConfigFactory.create(ServerConfig.class, myVars);

cfg.name();        // User Acceptance Test
cfg.hostname();    // uathost
cfg.port();        // 60020
```

Notice the `@DisableFeature(PREFIX)` on `env()`: the variable that *selects*
the section is not itself part of the section, so it has to opt out of the
prefix — see the next paragraph.

The same mechanism makes a prefix *optional*, which is what you want when one
deployment namespaces everything and another does not:

```java
@Prefix("${env.prefix:}")
public interface ServerConfig extends Config {
    String host();
    int port();
}
```

With `env.prefix` undefined the keys are `host` and `port`; setting it to
`FOO_` — as a system property, an environment variable, or an import — moves
every method of the interface onto `FOO_host` and `FOO_port` at once. The
empty default is what makes the prefix disappear when nothing is set.

If the only reason for that method is to give `${env}` a fallback, a
[default value](/owner/docs/variables-expansion/#default-values) in the prefix
itself says the same thing in one line, and the interface goes back to
describing nothing but the section:

```java
@Prefix("servers.${env:dev}.")
public interface ServerConfig extends Config {

    String name();

    String hostname();

    Integer port();
}
```

The `dev` section is read when `env` is defined nowhere, and passing
`env=uat` at creation time selects the other one exactly as above.


Disabling the prefix
--------------------

`PREFIX` is a [disableable feature](/owner/docs/disabling-features/):
`@DisableFeature(PREFIX)` makes a method resolve to its bare key, ignoring the
prefix declared on the interface.

```java
@Prefix("server.")
public interface ServerConfig extends Config {

    String hostname();                 // server.hostname

    @DisableFeature(PREFIX)
    @DefaultValue("UTF-8")
    String encoding();                 // encoding
}
```

As with the other disableable features, the annotation can also be placed on
the interface. It is read from the same interface the prefix is read from — the
one *declaring* the method — so it switches the prefix off for the methods
declared in that interface, and it does not reach the methods it inherits:

```java
@Prefix("datasource.")
public interface DataSourceConfig extends Config {
    String url();                 // still datasource.url
}

@DisableFeature(PREFIX)
@Prefix("pool.")
public interface PoolConfig extends DataSourceConfig {
    int maxSize();                // maxSize, the pool. prefix is off
}
```


A prefix for the whole factory
------------------------------

`@Prefix` states the prefix in the source code, one interface at a time. Since
version 2.0.0 a prefix can also be configured on the
[factory](/owner/docs/singleton/), for the interfaces that do not
declare one of their own. It is set through the factory properties, which are
the place where the factory itself is configured, so there is no new method to
learn:

```java
Factory factory = ConfigFactory.newInstance();
factory.setProperty("owner.key.prefix.from.package", "true");

ServerConfig cfg = factory.create(ServerConfig.class);
```

Two forms are available, and they compose — the literal one comes first:

| property | effect |
|---|---|
| `owner.key.prefix` | a literal, prepended to every key |
| `owner.key.prefix.from.package` | the package of the interface **declaring** the method, followed by a dot |

With `owner.key.prefix.from.package` set to `true`, an interface written like
this:

```java
package com.example;

public interface ServerConfig extends Config {
    @DefaultValue("8080")
    int port();
}
```

reads:

```properties
com.example.port=80
```

which is the point of the derived form: the prefix is not a string somebody
typed, so moving the interface to another package moves its keys with it,
instead of leaving a literal behind. Setting both properties nests one inside
the other: `owner.key.prefix=myapp.` together with the derived form gives
`myapp.com.example.port`.

This is less of a new idea than it looks. OWNER already derives the name of the
[default properties file](/owner/docs/loading-strategies/) from the
package and name of the interface — it was only the *keys* that were left out
of that convention.

<div class="note">
  <h5>The literal form belongs to an application, not to a library.</h5>
  <p>
    <code>owner.key.prefix</code> moves the keys of every interface created by that factory, including
    the ones you did not write. The derived form does not have that problem: it puts each interface
    under its own package, so a configuration interface shipped by a library stays consistent with
    itself. If you are writing a library, prefer <code>@Prefix</code> or the derived form.
  </p>
</div>

### Which prefix wins

An interface declaring `@Prefix` keeps it: the annotation is the explicit
statement of the two, and it is not appended to the one of the factory.
`@DisableFeature(PREFIX)` switches off both, so a method or an interface that
opts out resolves to its bare key whatever the factory says.

### One factory, one mapping

The prefix belongs to the factory, not to the JVM, so two factories do not
interfere:

```java
Factory prefixed = ConfigFactory.newInstance();
prefixed.setProperty("owner.key.prefix.from.package", "true");

Factory plain = ConfigFactory.newInstance();

prefixed.create(ServerConfig.class).port();   // reads com.example.port
plain.create(ServerConfig.class).port();      // reads port
```

The static `ConfigFactory` is a factory like any other: setting the property
through `ConfigFactory.setProperty()` applies to what `ConfigFactory.create()`
builds from that moment on, and leaves the factories you created yourself
alone.

The prefix is read **when the Config object is created**, and the object keeps
it for the rest of its life. Reconfiguring the factory afterwards cannot rename
the keys of an object that already exists, a
[reload](/owner/docs/reload/) resolves the same keys it resolved the
first time, and the mapping travels with the object when it is serialized. For
the same reason, an instance taken from
[`ConfigCache`](/owner/docs/singleton/) keeps the mapping it was born
with: the cache returns the object created the first time, prefix included.

### What else it reaches

Being a prefix like the one of the annotation, it applies wherever a key is
built:

* a [parametrized key](/owner/docs/parametrized-properties/) keeps it
  in front of the key it completes at call time;
* a [`Map` return type](/owner/docs/type-conversion/) reads its group
  of properties below the prefixed key;
* a missing [mandatory property](/owner/docs/usage/#mandatory-properties) is reported
  by its prefixed key, and so is a value that fails to convert;
* an interface in the **default package** has no package name to build a
  prefix out of, so the derived form leaves its keys alone rather than
  prefixing them with a bare dot.

### The properties keep their names

The prefix says where a *method* looks; it does not rename the properties. The
file, the imports and the
[`Accessible`](/owner/docs/accessible-mutable/) methods all use the full
key:

```java
factory.create(ServerConfig.class, map("com.example.port", "80"));   // found
factory.create(ServerConfig.class, map("port", "80"));               // not found
```

<div class="note info">
  <h5>Why isn't the prefix applied to the imports too?</h5>
  <p>
    Because the prefix belongs to the interface, while the properties are shared. The same imported
    map, the same file and the same <code>System</code> properties are read by every interface, and
    each of them can have a different prefix — so a rule that depends on <i>who is reading</i> can
    only be applied where the reading happens, never to the store itself.
  </p>
  <p>
    Applying it to the store would also break what a key is: <code>getProperty("com.example.port")</code>
    would stop matching the property it just wrote with <code>store()</code>, and
    <code>${com.example.port}</code> in a variable would point at something else again. This is not a
    rule the factory prefix introduces: <code>@Prefix("server.")</code> has always needed
    <code>server.port</code> in the file, and this is the same rule with the prefix stated elsewhere.
  </p>
</div>
