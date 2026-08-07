---
layout: docs
title: Key prefix
prev_section: usage
next_section: loading-strategies
permalink: /docs/key-prefix/
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
[`Accessible` and `Mutable`]({{ site.url }}/docs/accessible-mutable/) keep
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
[mandatory property]({{ site.url }}/docs/usage/#toc_5) is reported by its
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


Variables in the prefix
-----------------------

The prefix is part of the key, so it goes through
[variables expansion]({{ site.url }}/docs/variables-expansion/#toc_1) like the
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
[default value]({{ site.url }}/docs/variables-expansion/#toc_2) in the prefix
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

`PREFIX` is a [disableable feature]({{ site.url }}/docs/disabling-features/):
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
