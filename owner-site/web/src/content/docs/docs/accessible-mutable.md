---
title: "Accessible, Mutable and Traceable"
---

By default, object created by OWNER are immutable and promote information hiding.

This means that once the Config object is created its properties cannot be modified, and cannot be accessed in any other
way than using the methods that are properties mapping methods.

Those limitations are imposed by design, but sometime users may find this problematic. So here they come in the play the
interfaces [Mutable] and [Accessible].

  [Mutable]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Mutable.html
  [Accessible]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html
  [Traceable]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Traceable.html
  [Origin]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Origin.html

This is the hierarchy of the `Mutable` and `Accessible` interfaces:

![config-hierarchy](/owner/img/config-hierarchy.png)

The Mutable interface
---------------------

The [Mutable] interface allows the developer for runtime modifications of the properties contained in the Config object.

Example:

```java

interface MyConfig extends Mutable {
    @DefaultValue("18")
    public Integer minAge();
    public Integer maxAge();
}


MyConfig cfg = ConfigFactory.create(MyConfig.class);

// this comes from the @DefaultValue
assertEquals(Integer.valueOf(18), cfg.minAge());

// now we change the minAge to 21 using setProperty
String oldValue = cfg.setProperty("minAge", "21");
assertEquals("18", oldValue); // the old value was 18
assertEquals(Integer.valueOf(21), cfg.minAge()); // now is 21

// now we remove the minAge property
oldValue = cfg.removeProperty("minAge");
assertEquals("21", oldValue); // the old value is 21
assertNull(cfg.minAge()); // now is null
```

In the above example we saw `setProperty` and `removeProperty` in action, but the Mutable interface adds even more
methods like `clear()`, `load(InputStream)` and `load(Reader)`, and it should allow you to achieve complete write access
to the properties contained inside a Config object.

The Accessible interface
------------------------

As the [Mutable] interface allows for write access to the properties contained inside a Config object, the [Accessible]
interface allows for read access.

Example:

```java
interface MyConfig extends Accessible {
    @DefaultValue("Bohemian Rapsody - Queen")
    String favoriteSong();

    @Key("salutation.text")
    @DefaultValue("Good Morning")
    String salutation();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
assertEquals("Good Morning", cfg.getProperty("salutation.text"));

// print all properties to a PrintWriter
cfg.list(System.out);

// saves properties to an OutputStream
File tmp = File.createTempFile("owner-", ".tmp");
cfg.store(new FileOutputStream(tmp), "no comments");

```

As you can see, [Accessible] is not limited to the `getProperty()` method, but you can also use this
interface to `list()` or `store()` the properties.

The Traceable interface
-----------------------

A configuration merged out of several sources answers each property with one
value, and which of the sources that value came from is not something the merged
properties can still say: they are one map, and a value read from a file is
indistinguishable from one that came from the environment or from a
`@DefaultValue`. Since version 2.0.0 the [Traceable] interface keeps that
distinction available.

```java
@LoadPolicy(LoadType.MERGE)
@Sources({"system:env", "file:config/app.properties"})
interface MyConfig extends Config, Traceable {
    int port();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);

cfg.originOf("port");                  // file:config/app.properties
cfg.originOf("port").kind();           // Origin.Kind.SOURCE
cfg.originOf("nowhere");               // null: there is no such property
```

An [Origin] says what kind of place the property came from and, when there is
one to name, which source it was:

| Kind | Where the property came from | `source()` |
|---|---|---|
| `SOURCE` | one of the `@Sources` | the source, as written |
| `IMPORT` | one of the maps handed to `ConfigFactory.create` | `import[0]`, `import[1]`, … |
| `DEFAULT_VALUE` | a `@DefaultValue` on the method: nobody wrote it | `null` |
| `RUNTIME` | written afterwards, through [Mutable] | `null` |

Under `LoadType.MERGE` a key may be written in more than one source, and the
origin names **the one whose value survived** — the first declared. Under
`LoadType.FIRST` the sources after the one that answered are never read, so
nothing is attributed to them.

The origins follow the properties: a `reload()` works them out again, so a value
that came back from a file after being overwritten at run time is the file's
once more; `setProperty` makes a property one that was written at run time; and
removing a property removes its origin with it.

### Saving back only what you own

This is what the interface was asked for. With `MERGE` over `system:env` and a
file, `store()` writes the whole environment back into your configuration file
along with the three properties that belong to the application. Knowing where
each key came from is what makes it possible to save only your own:

```java
interface MyConfig extends Config, Accessible, Traceable { ... }

Properties mine = new Properties();
for (Map.Entry<String, Origin> entry : cfg.origins().entrySet())
    if ("file:config/app.properties".equals(entry.getValue().source()))
        mine.setProperty(entry.getKey(), cfg.getProperty(entry.getKey()));

mine.store(new FileOutputStream("config/app.properties"), null);
```

`origins()` hands back a snapshot taken under the same lock as
`propertyNames()`, so it does not change underneath while a reload runs.

<div class="note">
  <h5>A source never carries its credentials.</h5>
  <p>
    A URI may hold them — <code>https://user:secret@config/app.properties</code> is legal and used —
    and an origin handed to the caller would be one more place they appear. It is masked exactly as
    the log lines and the exception messages are: <code>https://***@config/app.properties</code>.
  </p>
</div>
