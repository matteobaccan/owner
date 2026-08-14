---
title: "Debugging"
---


There are some debugging facilities that are available in Properties files that
we wanted to keep in the OWNER API.

Which key is my method reading?
-------------------------------

*Since 2.0.0.* It is the question behind most of "it does not work", and the failure that provokes it is
the least visible one there is: a wrong prefix makes **every** property vanish at once. Nothing errors,
every method answers with `null` or its default, and the file is full of values that look right.

Turn the library's own logging up and it says so. In a `logging.properties`:

```properties
org.aeonbits.owner.level = FINE
```

and every method reports the key it resolves to, the nested sections walked with it:

```
FINE  MyConfig: ServerConfig.host() reads 'server.host'
FINE  MyConfig: ServerConfig.maxThreads() reads 'server.max.threads'
FINE  MyConfig: ServerConfig.section() is the section under 'server.section.'
FINE  MyConfig: SectionConfig.name() reads 'server.section.name'
```

A key that is not yet what it will be says which kind it is, so that it does not read as a mistake:

```
FINE  MyConfig: ServerConfig.version() reads 'version', with no prefix at all: it disables the feature
FINE  MyConfig: ServerConfig.pool() reads 'server.pool.%s', its arguments formatted in at each call
FINE  MyConfig: ServerConfig.url() reads 'server.servers.${env}.url', before the variables in it are expanded
```

One line per method is a lot of lines, which is why this sits at `FINE` and costs nothing until it is
asked for. At `CONFIG` — the level that says what the library *decided* — one line is worth having on its
own:

```
CONFIG  MyConfig: every key is prefixed with 'myapp.', from owner.key.prefix
```

That one is the [prefix configured on the factory](/owner/docs/key-prefix/), and it is singled out because
it is the only prefix written in no source file at all: an interface carries its `@Prefix` where anyone
reading it can see it, while this one is set on the factory and moves the keys of every configuration that
factory creates.

At the same level, one more line covers the other silence in this area. A method that takes arguments makes
its value a [format](/owner/docs/parametrized-properties/), and a value that is not a legal one is returned
as it was written — which is right, since a password holding a `%` is the ordinary case rather than a
mistake. But the same silence covers a placeholder mistyped in a value that *was* meant as a format, and
then the method quietly answers with the template instead of the text:

```
FINE  greeting() takes arguments, so the value of 'greeting' was used as a format, and it is not one
      (UnknownFormatConversionException). It is returned as it was written.
```

Neither the value nor the reason quoting a piece of it appears in that line: **a value never reaches a
log**, which is what [`@Sensitive`](#sensitive-values) is for, and the message of a formatting failure
quotes the part of the format it choked on.

<div class="note">
  <h5>The errors already name the key.</h5>
  <p>
    A mandatory property that cannot be resolved is reported by its whole key, and so is a value that will
    not convert — <code>Cannot convert 'abc' to int for property 'server.port'</code>. What these two lines
    add is the case where nothing goes wrong: everything resolved, and to keys nobody expected.
  </p>
</div>

The toString() method
---------------------

The `toString()` method is helpful to see the content of a Config object using a
log statement:

```java
interface MyConfig extends Config {
    @Key("max.threads")
    @DefaultValue("25")
    int maxThreads();

    @Key("max.folders")
    @DefaultValue("99")
    int maxFolders();

    @Key("default.name")
    @DefaultValue("untitled")
    String defaultName();
}

public static void main(String[] args) {
    MyConfig cfg = ConfigFactory.create(MyConfig.class);
    System.out.println("cfg = " + cfg);
    // output will be:
    // "cfg = {default.name=untitled, max.folders=99, max.threads=25}"
}
```

The list() methods
------------------

In your *mapping interfaces* you can optionally define one of the following
methods that may be convenient for debugging:

```java
void list(PrintStream out);
void list(PrintWriter out);
```

Those two methods were available in Java [Properties][1] to help the
debugging process.
You can implement [Accessible][2] that defines the above methods,
or just add them manually.

  [1]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html
  [2]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html

You can use them to print the resolved properties (and eventual overrides that
may occur when using the `LoadType.MERGE`):

```java
public interface SampleConfig extends Config {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();

    void list(PrintStream out); // manually defined
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
cfg.list(System.out);
```

You can also do the same implementing the Accessible interface, that declares
the `list()` methods for you:

```java
public interface SampleConfig extends Accessible {
    @Key("server.http.port")
    @DefaultValue("80")
    int httpPort();
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
cfg.list(System.out); // list() is defined in Accessible interface
```

Keeping a property out of the output
------------------------------------

*Since 2.0.0.*

The debugging facilities on this page print the configuration as it is, passwords included: a
`cfg.list(System.out)` written while chasing a problem, and then forgotten in the code, is how a
credential ends up in a log file. Mark the property with `@Sensitive` and its value is replaced by
`********`:

```java
public interface DbConfig extends Accessible {
    String url();

    String username();

    @Sensitive
    String password();
}
```

```java
cfg.list(System.out);
// -- listing properties --
// url=jdbc:postgresql://localhost/app
// username=app
// password=********
```

The annotation can also be written on the interface, and then every property declared in it is masked.

**Only the output meant to be read by a human is masked** — `list()` and `toString()`. The method itself,
`getProperty()`, `fill()`, `store()`, `storeToXML()` and the JMX attributes keep returning the real value:
those are how a configuration is read and written back, and masking them would replace the password with
`********` in the file the next time it is saved.

<div class="note warning">
  <h5>Masking is not encryption</h5>
  <p>
  <code>@Sensitive</code> keeps a value from being printed by accident; it does nothing to protect the value
  where it is stored, and anyone who can read the properties file can read the password. To keep the value
  itself unreadable, write it as a <a href="/owner/docs/crypto/">marker</a> —
  <code>db.password=${$aes-gcm::…}</code> — which stores it encrypted and decrypts it on access. Such a
  property does not need this annotation either: what is printed is the marker, and a marker in a listing
  is unreadable already.
  </p>
</div>

### A whole group at once

A method that returns a `Map` reads a [group of properties](/owner/docs/usage/) under a common
prefix rather than a single one, so there is no property named after it to mask. Marking it sensitive masks
**everything under the prefix**:

```java
public interface AppConfig extends Accessible {
    String appName();

    @Sensitive
    Map<String, String> credentials();
}
```

```java
cfg.list(System.out);
// -- listing properties --
// app.name=invoicing
// credentials.user=********
// credentials.password=********
```

Note that `credentials.user` goes too. The annotation says the group is sensitive, and all of it is
treated as such: picking out the entries whose name looks like a secret would be guesswork, and guesswork
is the thing this annotation exists to avoid. If only part of the group needs hiding, read that part with
a method of its own.

Where two methods disagree — one declaring a group sensitive, another reading a key inside it and not —
the mask wins. Printing a secret that was declared as one is the mistake that costs something; masking a
value that need not have been is read as over-caution and no more.

### Why the annotation, and what it cannot do

Every other configuration library that hides values decides from the **key**, not from the method that
reads it. Spring Boot 2.x matched the property name against patterns — `password`, `secret`, `token`,
`*credentials.*`; Gestalt searches the path of a node for keywords; Spring Boot 3 went further and hides
every value in its actuator endpoints unless you turn them on for an authorized role.

OWNER decides from the method, which is a deliberate difference and buys precision: a password called
`pwd`, or `dsn`, or `apiKey2`, is masked because you said it was a secret, not because its name happened
to match somebody's regular expression. Nothing is guessed and nothing is missed by an unlucky name.

It costs one thing, and it is worth knowing: **a property that no method of your interface reads cannot be
masked**, because there is nothing to write the annotation on — and `list()` prints every property it
holds, not only the ones with an accessor. If that matters for your configuration, read the values through
the interface rather than leaving them loose in the file.

The keys to mask are worked out when the Config object is created, from the methods that take no parameters.
A key that depends on the invocation arguments is not known in advance, so a
[parametrized property](/owner/docs/parametrized-properties/) cannot be masked.
