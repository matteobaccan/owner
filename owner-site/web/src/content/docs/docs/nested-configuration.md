---
title: "Nested configuration"
---

A configuration is rarely flat. The properties of the server sit together, the
properties of the database sit together, and the file says so by repeating a
common leading part of the key:

```properties
server.host=localhost
server.port=8080
database.url=jdbc:postgresql://localhost/app
```

Since version 2.0.0 an interface can be shaped the same way. A method whose
return type is another interface extending `Config` reads the section below its
own key:

```java
public interface AppConfig extends Config {
    String name();

    ServerConfig server();

    DatabaseConfig database();
}

public interface ServerConfig extends Config {
    String host();

    @DefaultValue("8080")
    int port();
}
```

```java
AppConfig cfg = ConfigFactory.create(AppConfig.class);

cfg.server().host();       // "localhost"
cfg.server().port();       // 8080
cfg.database().url();      // "jdbc:postgresql://localhost/app"
```

**The accessor names the section, not the type it returns.** `server()` reads
everything below `server.`, and two methods can therefore return the same
interface without colliding — `primary()` and `backup()` read `primary.host`
and `backup.host`. The key an accessor resolves to is the ordinary one, so
[`@Key`](/owner/docs/usage/) renames the section and everything else that
decides a key applies unchanged:

```java
public interface AppConfig extends Config {
    @Key("http")
    ServerConfig server();     // http.host, http.port
}
```

A `@Key("")` gives the section no segment of its own: the nested interface then
reads the keys of the object holding it, which is a way of splitting a large
interface into several smaller ones without moving a single property.

<div class="note">
  <h5>The nested object loads nothing of its own.</h5>
  <p>
    It is a view over the properties its parent has already resolved, and the two share everything:
    one set of <code>@Sources</code>, one <a href="/owner/docs/reload/">reload</a>, one set of
    listeners, one mutable state for the whole tree. A <code>@Sources</code> written on a nested
    interface is <b>not read</b> — the object it describes is not loaded, it is looked up.
  </p>
  <p>
    For the same reason the methods of <a href="/owner/docs/accessible-mutable/"><code>Accessible</code>
    and <code>Mutable</code></a>, called on a nested object, answer about the whole configuration
    and take whole keys: they are the same manager underneath.
  </p>
</div>

The objects are built when the configuration is created, once, and kept: the
same accessor returns the same object every time, a
[mandatory property](/owner/docs/usage/#mandatory-properties) declared inside a
nested interface is checked at creation like any other, and a cycle in the
types — an interface holding itself, at any depth — is refused there rather
than left to exhaust the stack at the first call.


Nesting and prefixes
--------------------

A [`@Prefix`](/owner/docs/key-prefix/) written on a nested interface
**composes** with the path it hangs from, the path coming first:

```java
public interface AppConfig extends Config {
    ServerConfig server();
}

@Prefix("http.")
public interface ServerConfig extends Config {
    String host();                  // server.http.host
}
```

This is where nesting differs from the prefix configured on a factory, which
`@Prefix` *overrides* instead. The two cases are not the same question: a
factory prefix and an interface prefix are two answers to *where do this
interface's keys live*, and the explicit one wins; a nesting path and an
interface prefix answer two different questions — where the object was hung,
and how that object names its own keys — so neither is a default for the other
and both are used.

The practical consequence is worth stating: an interface that already carries a
prefix keeps carrying it when nested, so `@Prefix("server.")` on an interface
reached through `server()` gives `server.server.host`. Write the prefix on one
of the two, not on both.

`@DisableFeature(PREFIX)` switches off every prefix, the nesting path included,
which is the way to reach a key at the top of the file from inside a nested
interface.


Lists of sections
-----------------

Where a configuration holds several of the same thing, the elements are
[indexed](/owner/docs/type-conversion/) and each of them is a section:

```properties
servers[0].host=alpha
servers[0].port=1
servers[1].host=beta
servers[1].port=2
```

```java
public interface ClusterConfig extends Config {
    List<ServerConfig> servers();
}
```

Arrays and the other collection types work the same way. The rules are the ones
of any indexed list: the indices start at zero and run consecutively, a gap is
refused rather than closed up, and the order is the index rather than the order
of the file.

This is the shape every tree-structured source flattens to, so a document like

```xml
<cluster>
  <servers><host>alpha</host><port>1</port></servers>
  <servers><host>beta</host><port>2</port></servers>
</cluster>
```

is read by the interface above with no further ceremony.

A type holding a `List` of itself describes a tree, and is allowed where a type
holding itself is refused: the properties say how deep it goes, and the
recursion ends where the keys end.

<div class="note">
  <h5>A sensitive property inside an element masks the whole group.</h5>
  <p>
    The keys of a section whose name the properties decide — an element of a list, a value of a map,
    the answer of an accessor taking arguments — are not known when the configuration is created, so
    there is no key to put in the list of the ones to
    <a href="/owner/docs/debugging/">mask</a>. Everything under the group is masked instead: a secret
    printed because nobody could name it in advance is the mistake that costs something, and a value
    masked that need not have been is over-caution and no more.
  </p>
  <p>
    <a href="/owner/docs/crypto/">Decryption</a> is not affected, being declared on the method rather
    than on a key: an encrypted value inside an element is decrypted like any other.
  </p>
</div>

<div class="note">
  <h5>An element that is a value and an element that holds values.</h5>
  <p>
    <code>servers[0]=alpha</code> is a list of strings; <code>servers[0].host=alpha</code> is a list
    of sections. The two notations do not overlap and neither reading collects the other's data, so
    the return type decides which one applies and nothing is ambiguous.
  </p>
</div>


Sections named by the file
--------------------------

When it is the file that decides the names, a `Map` reads them all:

```properties
servers.alpha.host=one
servers.beta.host=two
```

```java
public interface AppConfig extends Config {
    Map<String, ServerConfig> servers();     // {alpha=…, beta=…}
}
```

The name of the section is the key of the entry and goes through the ordinary
[type conversion](/owner/docs/type-conversion/), so the map need not be keyed by
strings. The entries come out in the order of their names, which does not depend
on the order of the file.

The same section can also be asked for by name, with a
[parametrized key](/owner/docs/parametrized-properties/):

```java
public interface AppConfig extends Config {
    @Key("servers.%s")
    ServerConfig server(String name);
}

cfg.server("alpha").host();     // "one"
```

Both together answer the question of *objects whose names are only known at run
time*: the `Map` discovers which ones are there, and the parametrized accessor
reads the one you already know the name of.


Optional sections
-----------------

An `Optional` return type asks whether the section was written at all:

```java
public interface AppConfig extends Config {
    Optional<ServerConfig> server();
}
```

It is present as soon as **any** property below its path exists, and empty when
none does.

<div class="note">
  <h5>A default inside the section makes it always present.</h5>
  <p>
    A <code>@DefaultValue</code> declared on any method of the nested interface puts a property
    below the path, and from that moment the section is there. The defaults are merged into the same
    properties as the values read from the sources, and afterwards nothing distinguishes them.
  </p>
  <p>
    An <code>Optional</code> section and a <code>@DefaultValue</code> written inside it therefore say
    the opposite of each other, and the default wins. Declare the defaults outside the optional
    section, or accept that the section is always present.
  </p>
</div>

For the same reason `@Mandatory` on the accessor of a section is **refused**
when the configuration is created: a section counts as present as soon as any
key below it exists, so the check could never fail, and a check that cannot fail
reads like a guarantee it does not give. Write `@Mandatory` on the properties
inside that are really required — those are checked at creation, section by
section, and reported by their whole key. `@Mandatory` on an *interface* keeps
its meaning and leaves the nested accessors alone.

A section is not read by key
----------------------------

  [Accessible]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html
  [Mutable]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Mutable.html
  [Traceable]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Traceable.html
  [Reloadable]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Reloadable.html

A nested interface **may not extend [Accessible], [Mutable] or [Traceable]**,
and a configuration that declares one is refused when it is created:

```java
public interface ServerConfig extends Config, Accessible {  // refused
    String host();
}
```

Those three interfaces address properties **by key**, and a section has no key
space of its own. It is a view over the properties of the whole configuration —
that is what makes it share the reload, the listeners and the sources instead of
being a copy — so `server.getProperty("host")` would answer with the `host` of
the root and not with `server.host`, a different property and no error, while
`server.clear()` would empty the entire configuration.

**Everything a section could have offered is available from the configuration
object itself**, where keys are the ones written in the file:

```java
public interface AppConfig extends Config, Accessible, Mutable {
    ServerConfig server();       // ServerConfig extends Config, and nothing else
}

cfg.getProperty("server.host");                  // nested.example.org
cfg.setProperty("server.host", "elsewhere");
cfg.server().host();                             // elsewhere — the section sees the write
```

The root may extend all three, as above, and `@Sensitive` inside a section is
masked in the root's `list()` under its whole key: the masking was always
computed over the tree.

[Reloadable] is the exception and may be extended by a nested interface: it acts
on the configuration as a whole, there is exactly one of those, and so it means
the same thing called from any point of the tree.

<div class="note info">
  <h5>Why refused, rather than made relative to the section.</h5>
  <p>
    Reading a section's keys <em>relative to the section</em> is a reasonable
    feature, and the shape it would take is well established: Typesafe Config
    gives it with <code>getConfig("section")</code>, which returns a
    configuration rooted at that path, and Commons Configuration with
    <code>subset(prefix)</code>, which strips the prefix from the keys. This
    library may well grow it. Refusing now is what keeps that door open:
    allowing later breaks nobody, whereas shipping the root's answers under the
    section's name and correcting them afterwards would break everyone who had
    come to rely on them.
  </p>
</div>
