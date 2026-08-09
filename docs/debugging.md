---
layout: docs
title: Debugging
prev_section: accessible-mutable
next_section: disabling-features
permalink: /docs/debugging/
---

There are some debugging facilities that are available in Properties files that
we wanted to keep in the OWNER API.

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
  itself unreadable use <a href="{{ site.url }}/docs/crypto/"><code>@EncryptedValue</code></a>, which stores
  it encrypted and decrypts it on access — and note that an encrypted property is already printed as its
  ciphertext, so it does not need this annotation, unless even the ciphertext should stay out of the log.
  </p>
</div>

### A whole group at once

A method that returns a `Map` reads a [group of properties]({{ site.url }}/docs/usage/) under a common
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
[parametrized property]({{ site.url }}/docs/parametrized-properties/) cannot be masked.
