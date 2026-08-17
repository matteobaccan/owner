---
title: "Basic usage"
---

The approach used by OWNER APIs, is to define a Java interface associated to a
properties file.

Suppose your properties file is defined as `ServerConfig.properties`:  

```properties
port=80
hostname=foobar.com
maxThreads=100
```

To access this properties file you need to define a convenient Java interface 
`ServerConfig.java` in the same package:

```java
import org.aeonbits.owner.Config;

public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
```

Notice that the above interface extends from `Config`, that
is a marker interface recognized by OWNER as valid to work with.

We'll call this interface the *Properties Mapping Interface* or just
*Mapping Interface* since its goal is to map Properties into a an easy to use
piece of code.


How does the mapping work?
--------------------------

Since the properties file does have the same name as the Java class, and they
are located in the same package, the OWNER API will be able to automatically
associate them.  
For instance, if your *mapping interface* is called `com.foo.bar.ServerConfig`, 
OWNER will try to associate it to `com.foo.bar.ServerConfig.properties`, 
loading from the classpath.  


The properties names defined in the properties file will be associated to the
methods in the Java class having the same name.  
For instance, the property `port` defined in the properties file will be 
associated to the method `int port()` in the Java class, the property `hostname`
will be associated to the method `String hostname()` and the appropriate type
conversion will apply automatically, so the method `port()` will return an int
while the method `hostname()` will return a Java string, since the interface is
defined in this way.

The mapping mechanism is fully customizable, as well the automatic type 
conversion we just introduced is flexible enough to cover most of the Java types 
as well as object types defined by the user.  
You can see how in the next chapters.

## Using the Config object

At this point, you can create the ServerConfig object and use it in your code:

```java
ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
System.out.println("Server " + cfg.hostname() + ":" + cfg.port() +
                   " will run " + cfg.maxThreads());
```


Using @DefaultValue and @Key annotations
----------------------------------------

Did you notice that in the above example it is specified `@DefaultValue("42")` 
annotation? 

```java
public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")    // here!!!
    int maxThreads();
}
```

It is used in case the `maxThread` key is missing from the
properties file.

This annotation gets automatically converted to `int`, since `maxThreads()`
returns an `int`. 

Using the annotations, you can also customize the property keys:

```properties
# Example of property file 'ServerConfig.properties'
server.http.port=80
server.host.name=foobar.com
server.max.threads=100
```

This time, as commonly happens in Java applications, the properties names are
separated by dots. Instead of just "port" we have "server.http.port", so we
need to map this property name to the associated method using the `@Key`
annotation.

```java
/*
 * Example of ServerConfig.java interface mapping the previous 
 * properties file.
 */
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

The `@DefaultValue` and `@Key` annotations are the basics to start using the
OWNER API.

Notice that the three keys above share the same `server.` prefix, as it
commonly happens. Since version 2.0.0 you can state that prefix once, on the
interface, instead of repeating it on every method: see
[Key prefix](/owner/docs/key-prefix/).

<div class="note">
  <h5>You can leave the properties file away during development!</h5>
  <p>
     During the development you may decide to just use the `@DefaultValue` to
     provide a default configuration, without really adding the properties file.
     You can add the properties file later or leave this task to the end user.
  </p>
</div>

How the key may be written
--------------------------

The same setting gets spelled differently depending on where it is written. Java code says `firstName`,
a `.properties` or a `.yaml` file usually says `first-name`, a shell says `FIRST_NAME`. Since version
2.0.0 a method finds its property under any of them, which is what
[#116](https://github.com/matteobaccan/owner/issues/116) asked for:

```java
public interface Person extends Config {
    String firstName();
}
```

```properties
firstName  = Luigi     # the key the method resolves to
first-name = Luigi     # kebab-case
first_name = Luigi     # snake_case
FIRST_NAME = Luigi     # the environment variable form
```

Any one of those four answers `firstName()`.

**Four forms, and no more.** The set is closed: `firstname`, `FirstName`, `first.name` and `FIRST-NAME`
are *not* spellings of this key. Spring Boot 1 matched loosely — separators dropped, case ignored, several
spellings collapsing onto one property — and Boot 2 deliberately narrowed it, because with the loose
version you could not say which key a value would be read from without running the program. This is the
narrow side of that split.

**The forms are derived from the key, not from the method name**, so `@Key` takes part in it: a method
annotated `@Key("first-name")` is equally found under `firstName` or `FIRST_NAME`. `@Key` still means
exactly what it says — it is the key that is tried first, the one every error message names, and the one
the [key report](/owner/docs/debugging/) prints.

**One form applies to the whole key at once**, prefixes and [nesting](/owner/docs/nested-configuration/)
included. `server.maxThreads` is looked for as `server.max-threads`, `server.max_threads` and
`SERVER.MAX_THREADS` — never as a mixture such as `server.MAX_THREADS`, since a file is written in one
convention throughout. That is also what makes a whole section work: with `myDb()` returning a nested
interface, `my-db.user-name` is simply one of the spellings of `myDb.userName`.

### Which one wins

A value that was **written** beats one that was only **defaulted**, whichever spelling holds it — so
`max-threads=7` in the file beats the `@DefaultValue("42")` on `maxThreads()`, as anybody would expect.
Among values that were all written, the key the method resolves to comes first, and the other spellings
follow in the order they are listed above: kebab, snake, upper snake, camel.

That order only matters when a configuration holds two spellings of one property at once, and when it
does, one of them is read and the other is inert. OWNER does not fail silently, so it says so:

```
WARNING: com.acme.Person: 'firstName' is written in 2 spellings at once - [firstName, first-name].
Relaxed binding reads 'firstName' and never looks at [first-name]. Keep one spelling, or switch the
feature off for the method with @DisableFeature(RELAXED_BINDING) if they are meant to be different
properties.
```

With [`owner.strict`](/owner/docs/loading-strategies/#refusing-everything-that-would-only-have-been-a-warning)
on, the same case is refused when the configuration is
created instead of being reported. A `@DefaultValue` is never one of the two: it lives under the key of
its own method and pairing it with what the file wrote would report every defaulted property in every
kebab-case configuration.

### What it does not touch

* **The properties keep the names they were written with.** `store()`, `list()`, `propertyNames()` and
  `toString()` show `first-name`, and nothing is ever added under the key of the method. See
  [Accessible and Mutable](/owner/docs/accessible-mutable/).
* **The methods that take a key answer about that key.** `getProperty("firstName")` returns `null` when
  the file says `first-name`, and a [`Traceable`](/owner/docs/accessible-mutable/#the-traceable-interface)
  origin stays attached to the key that really exists. These are addressed by key, and relaxing them
  would mean `getProperty(k)` reading something that is not `k`.
* **The prefix of a group is matched as it is written.** A method returning a `Map`, or an indexed list
  such as `hostNames[0]`, reads everything below a prefix — there the prefix decides which keys *are* the
  group, and choosing among four of them could silently merge two groups or answer with the wrong one.

One thing does follow the spellings, and has to: what
[`@Sensitive`](/owner/docs/debugging/#keeping-a-property-out-of-the-output) masks is matched by name
against the properties as they were loaded, so a `@Sensitive String dbPassword()` reading `DB_PASSWORD`
out of the environment is masked in `list()` and `toString()` under that name too.

The feature is on by default and is switched off per method or per interface with
`@DisableFeature(RELAXED_BINDING)`; see [Disabling features](/owner/docs/disabling-features/). Switching
it off is worth it where a file deliberately holds two spellings as two different properties, and where a
property that is often absent is read in a tight loop: a key that is found costs nothing extra, while a
key that is missing is looked for three more times before the answer is `null`.

A property that is set, but empty
---------------------------------

The default covers a property that is **missing**. A property that is there but empty is a value like any
other:

```properties
server.max.threads=
```

`@DefaultValue("42")` is not used here, and since an empty text is not a number the conversion fails with
`Cannot convert '' to int for property 'server.max.threads'`. That is deliberate, and it is the same
distinction drawn by [MicroProfile Config](https://download.eclipse.org/microprofile/microprofile-config-2.0/microprofile-config-spec-2.0.html),
by [Quarkus](https://quarkus.io/guides/config-reference) and by Spring Boot: leaving a property empty is a
way of saying *this is not set here*, which with a
[MERGE load policy](/owner/docs/loading-strategies/) is how a value coming from another file is
overridden. Falling back on the default whenever a conversion fails would also turn a typo like
`server.max.threads=4O` — written with the letter O — into a silent 42, which is exactly the kind of quiet
wrongness a default is not there to produce.

There is one case where the distinction gets in the way, though: a value left empty by a template that
nobody filled in.

```properties
server.max.threads=${MAX_THREADS}
```

Since version 2.0.0 a single method can opt into having the default cover that case too:

```java
@Key("server.max.threads")
@DefaultValue(value = "42", useOnEmpty = true)
int maxThreads();
```

With `useOnEmpty = true` an empty value — including one made of whitespace only, and one that becomes empty
after the variables are expanded — is treated as if the property were not there at all, and the default is
used in its place. A value that is *wrong* rather than empty still fails: `useOnEmpty` is about the absence
of information, not about recovering from a mistake. Use `@Mandatory` when a property must be set.

The flag applies to the annotated method only, and it does not change what is stored: `getProperty()` and
the other [Accessible](/owner/docs/accessible-mutable/) methods keep returning the empty value. The
default replacing an empty value goes through variable expansion, preprocessing, decryption and parameter
formatting exactly as the value it replaces, so the result is the same as if the property had been missing.

Undefined properties
--------------------

Suppose you have defined a method in your *mapping interface* that cannot be 
resolved to any property loaded from a properties file, and this method doesn't 
define a `@DefaultValue` what happens? Simple: it will return null, or a 
NullPointerException;

Suppose our ServerConfig class was looking like this:

```java
public static interface ServerConfig extends Config {
    String hostname();
    int port();
    Boolean debugEnabled();
}
```

If we don't have any ServerConfig.properties associated to it, when we call the
method `String hostname()` it will return null, as well as when we call the 
method `Boolean debugEnabled()` since the return types String and Boolean are
java objects. But if we call the method `int port()` then a 
`NullPointerException` will be raised.

<div class="note">
  <h5>You don't want the NullPointerException?</h5>
  <p>
    If you don't want to get the NullPointerException, you can just define
    a default value. For instance, you can set <code>@DefaultValue("0")</code> for
    an <code>int</code> return type, or a <code>@DefaultValue("false")</code> for a 
    <code>boolean</code> return type, and so on...
  </p>
</div>

Mandatory properties
--------------------

Sometimes a configuration property is required and there is no sensible default
for it: think of a database URL or an API key. Since version 2.0.0 you can mark
such properties with the `@Mandatory` annotation:

```java
public interface ServerConfig extends Config {
    @Mandatory
    String hostname();

    @DefaultValue("8080")
    int port();
}
```

When the `Config` object is created, OWNER verifies that every mandatory
property can be resolved (from the loaded sources, the imports or a
`@DefaultValue`): if any of them is missing, `ConfigFactory.create()` throws a
`MissingMandatoryPropertyException` listing *all* the missing keys, so you can
fix your configuration in a single pass. The exception also exposes the missing
keys programmatically via `getKeys()`.

The check is enforced on every access too: if a mandatory property becomes
unavailable later — for instance after a [hot reload](/owner/docs/reload/)
or a `removeProperty()` on a [Mutable](/owner/docs/accessible-mutable/)
config — reading it throws `MissingMandatoryPropertyException` instead of
returning null.

`@Mandatory` can also be applied to the interface, making all the properties
declared in that interface mandatory:

```java
@Mandatory
public interface DatabaseConfig extends Config {
    String url();      // mandatory
    String username(); // mandatory

    @DefaultValue("10")
    int poolSize();    // mandatory, but satisfied by the default value
}
```

<div class="note">
  <h5>Methods taking parameters</h5>
  <p>
    Properties whose method takes parameters cannot be validated at creation
    time, since the property key may depend on the invocation arguments: for
    those, the check happens when the method is invoked.
  </p>
</div>

Overriding a property in a sub-interface
---------------------------------------

A *mapping interface* can extend another one and re-declare one of its
methods, to give it a different key or a different default value:

```java
public interface BaseConfig extends Config {
    @Key("feature.default.setting")
    @DefaultValue("-1")
    long setting();
}

public interface FeatureConfig extends BaseConfig {
    @Key("feature.concrete.setting")
    @DefaultValue("42")
    @Override
    long setting();
}
```

An override **redirects** the property rather than adding one: `setting()`
reads `feature.concrete.setting`, and `feature.default.setting` is no longer
part of this configuration — `getProperty()` returns `null` for it, and its
`@DefaultValue` is not registered. There is one method, so there is one key.
That follows from Java itself: an overriding declaration hides the one it
overrides, and OWNER sees a single `setting()` method.

Two things are commonly wanted here, and both are written down explicitly
rather than inferred from the override.

**To keep reading the base key as well**, declare an accessor for it instead
of relying on the overridden declaration:

```java
public interface FeatureConfig extends BaseConfig {
    @Key("feature.concrete.setting")
    @DefaultValue("42")
    @Override
    long setting();

    @Key("feature.default.setting")
    @DefaultValue("-1")
    long baseSetting();
}
```

**To make the concrete setting fall back to the base one** — an overlay,
rather than a replacement — say so with a
[variable](/owner/docs/variables-expansion/):

```java
@Key("feature.concrete.setting")
@DefaultValue("${feature.default.setting:-1}")
@Override
long setting();
```

`setting()` now returns `feature.concrete.setting` when it is defined,
otherwise `feature.default.setting`, otherwise `-1`. Note that this is a
chain of three and that the fallback works on the *properties* too, not only
on the default values: setting `feature.default.setting` in a properties file
changes the answer, which is not something an inherited `@DefaultValue` could
ever do.

What a class-level annotation reaches
------------------------------------

Several annotations are written on the interface rather than on a method, and
once interfaces start extending each other the question is where each of them
counts. There are two answers, and which one applies follows from what the
annotation is about.

**The ones that describe the configuration object** are found wherever in the
hierarchy they are written: the interface handed to the `ConfigFactory`, then
every interface it extends **directly** in the order of the `extends` clause,
then theirs, each visited once. The nearest declaration wins — except
`@Sources`, which accumulates, and `@DisableFeature`, which carries a set and
is therefore read everywhere it appears.

| Annotation | Describes |
|---|---|
| [`@Sources`](/owner/docs/loading-strategies/) | which files the configuration reads |
| [`@LoadPolicy`](/owner/docs/loading-strategies/) | how those files combine |
| [`@HotReload`](/owner/docs/reload/) | whether and how it reloads |
| [`@DecryptorClass`](/owner/docs/crypto/) | who decrypts its `@EncryptedValue` properties |
| `@Description` | the header written into a file it [saves](/owner/docs/accessible-mutable/) |
| [`@DisableFeature`](/owner/docs/disabling-features/) | *asked of the object* — see below |

**The ones that describe the methods an interface declares** are read off the
interface that declares the method, and neither climb nor descend:

| Annotation | Describes |
|---|---|
| [`@Prefix`](/owner/docs/key-prefix/) | the prefix its keys are read with |
| [`@Mandatory`](#mandatory-properties) | that its properties must be present |
| [`@Sensitive`](/owner/docs/debugging/) | that its values are not to be printed |
| [`@Separator`, `@TokenizerClass`](/owner/docs/type-conversion/) | how its list values are cut |
| [`@PreprocessorClasses`](/owner/docs/preprocessors/) | how its values are rewritten before use |
| [`@DisableFeature`](/owner/docs/disabling-features/) | *asked of a method* — see below |

An interface governs what it declares. A sub-interface saying `@Sensitive`
does not mask the keys its parent declared, and a sub-interface saying
`@Separator(";")` does not change how a list its parent described is cut —
otherwise two interfaces would be describing one key, and the one that wrote
the property would not be the one deciding what it means. Say it where the
method is declared, or on the method.

<div class="note info">
  <h5>Why <code>@DisableFeature</code> is in both tables.</h5>
  <p>
    It is asked both questions. <em>Is this feature off for this method?</em> — the method and the interface
    declaring it, second table. <em>Has this configuration asked for the feature to be off?</em> — the whole
    hierarchy, first table. The second question is the one the
    <a href="/owner/docs/accessible-mutable/"><code>Accessible</code></a> methods ask, since
    <code>getProperty</code> and <code>fill</code> are declared on <code>Accessible</code> and never on the
    interface you wrote, so there is no declaring class of yours for them to read.
  </p>
  <p>
    Until 2.0.0 one lookup answered both, and a configuration could contradict itself: with
    <code>@DisableFeature(VARIABLE_EXPANSION)</code> on a super-interface, the method returned the value
    unexpanded while <code>getProperty()</code> expanded it.
  </p>
</div>

Java is no help with any of this: `@Inherited` applies to classes extending
classes, and does nothing for an interface. Every lookup of this kind is
written out, which is exactly why they used to disagree — three stopped at the
direct super-interfaces, three read the interface handed to the factory and
nothing above it, and `@Prefix` counted at any depth. Since 2.0.0 they share
one walk.

Conclusions
-----------

Now you know the minimum to get productive with the OWNER API. But this is just
the beginning. OWNER is a rich API that allows you to add additional behaviors
and have more interesting features, so that you should be able to use this 
library virtually in any other context where you where using the 
`java.util.Properties` class.
