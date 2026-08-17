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

### Loading an XML document

*Since 2.0.0.* `loadFromXML(InputStream)` reads an XML document into the configuration, and it is **not** a
delegate to [`java.util.Properties.loadFromXML`][jdk-loadfromxml]: it reads the document the way this library
reads an [XML source](/owner/docs/file-formats/#xml), so both the Java properties format and an XML of your
own work.

```java
config.loadFromXML(new FileInputStream("server.xml"));
```

```xml
<server><http><port>8080</port></http></server>
```

That document is `server.http.port=8080` here, and is refused outright by `java.util.Properties`. It is what
[#62](https://github.com/matteobaccan/owner/issues/62) asked for in 2013, and it closes the asymmetry with
`storeToXML`, which has been on [Accessible] since 1.0.5 with nothing on this side to read back what it
writes.

Everything else is as `load(InputStream)`: the properties are merged into the ones already held, the
listeners are told, and a transactional listener may refuse the change. **The stream is closed** when the
method returns, which `load(InputStream)` does not do to its own — the asymmetry is the JDK's, where
`Properties.load` leaves the stream open and `Properties.loadFromXML` closes it, and it is followed rather
than corrected so that whoever knows that pair knows this one.

  [jdk-loadfromxml]: https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#loadFromXML-java.io.InputStream-

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

### Changing a properties file and writing it back

The two interfaces together are the whole round trip, which is the commonest thing people come here
looking for. The configuration is methods rather than fields, so the setter is not on the interface — it
is `setProperty`, keyed by name:

```java
@Sources("file:/etc/myapp/app.properties")
public interface AppConfig extends Mutable, Accessible {
    String host();
    int port();
}
```

```java
AppConfig config = ConfigFactory.create(AppConfig.class);
config.setProperty("port", "9090");          // or removeProperty("port")

try (OutputStream out = Files.newOutputStream(Paths.get("/etc/myapp/app.properties"))) {
    config.store(out, "written back by the application");
}
```

Everything you did not touch is written back unchanged, and a configuration created afterwards reads the
new value. Pinned down in
[`WritingTheFileBackTest`](https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/WritingTheFileBackTest.java)
against a real file.

<div class="note warning">
  <h5>This rewrites the file; it does not edit it.</h5>
  <p>
    Underneath is
    <a href="https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#store-java.io.OutputStream-java.lang.String-"><code>Properties.store</code></a>,
    which serialises a map — so <b>comments, blank lines and the original order do not survive</b>, and a
    timestamp line is added. A hand-written file that somebody maintains comes back machine-written:
  </p>
  <pre><code># the database we talk to        #written back by the application
host = localhost          -->    #Fri Aug 14 17:52:00 CEST 2026
                                 host=localhost
# in milliseconds                port=9090
port = 8080
</code></pre>
  <p>
    That is fine for a file only the application writes, and wrong for one a person edits. Saving without
    destroying the file is <a href="https://github.com/matteobaccan/owner/issues/16">#16</a> and is not
    implemented; until it is, the honest options are to keep machine-written state in a file of its own,
    or to edit the text yourself and let OWNER only read it.
  </p>
</div>

Which methods process the value
-------------------------------

A configuration is read in two ways — through the mapping methods, and through the methods of
[Accessible] — and the two do not do the same amount of work to the value. **The rule is that a method
answering with a value processes it, and a method writing the properties out does not**, because what
goes out has to be able to come back: a `${...}` expanded on the way out is a `${...}` lost from the file
at the next save.

| | expands `${...}` | masks [`@Sensitive`](/owner/docs/debugging/#keeping-a-property-out-of-the-output) | applies `@EncryptedValue`, `@ConverterClass`, preprocessors |
|---|:---:|:---:|:---:|
| `String url();` — a mapping method | yes | no, it returns the real value | yes |
| `getProperty(key)`, `getProperty(key, default)` | **yes** | no | no |
| `fill(map)` | **yes** | no | no |
| `getRawProperty(key)`, `getRawProperty(key, default)` | no | no | no |
| `list(out)` and `toString()` | no | **yes** | no |
| `store(out, comments)`, `storeToXML(out, comment)` | no | no | no |
| the JMX attributes | no | no | no |

Only the expansion crosses from one column of methods to the other, and the reason is worth knowing
because it is what makes the table predictable rather than arbitrary: **a variable lives in the value, so
it is resolved wherever the value is read; `@EncryptedValue`, `@ConverterClass` and the preprocessors are
declared on a method, and a property asked for by name has no method to read the declaration from.**

The same sentence settles the *key* as well. A mapping method also accepts the other spellings of the key
it resolves to — `first-name` for `firstName()`, see
[How the key may be written](/owner/docs/usage/#how-the-key-may-be-written) — and none of the methods in
the table does: they were handed a key, and they answer about that key. `getProperty("firstName")` returns
`null` where the file says `first-name`, `propertyNames()` and `store()` show `first-name`, and nothing is
ever added under the name of the method.

<div class="note info">
  <h5>Which is exactly why an encrypted value is better written in the value.</h5>
  <p>
    Since 2.0.0 a value can name what decrypts it —
    <a href="/owner/docs/crypto/"><code>db.password=${$aes-gcm::…}</code></a> — and being expansion rather
    than a declaration on a method, it is in the first column and not the third: <code>fill()</code> and
    <code>getProperty()</code> answer with the <em>secret</em>, and so does a value that refers to it.
    <code>store()</code> still writes the marker back, because the properties hold its text rather than its
    answer, so the round trip the whole table exists to protect is intact.
  </p>
</div>

```java
// config.properties
//   s     = say
//   hello = ${s} HELLO
//   b     = ${hello} AGAIN!

cfg.getProperty("b");       // say HELLO AGAIN!   — the value, ready to use
cfg.getRawProperty("b");    // ${hello} AGAIN!    — the value, as it was written
```

<div class="note warning">
  <h5>This changed in 2.0.0.</h5>
  <p>
    Up to 1.0.12 <code>getProperty()</code> and <code>fill()</code> returned the text as it was written,
    which meant that the same property answered differently depending on whether it was read through its
    method or by name (issue
    <a href="https://github.com/matteobaccan/owner/issues/319">#319</a>). If you were relying on the old
    behaviour — typically to write the properties back to a file — <code>getRawProperty()</code> is that
    behaviour under a name that says what it does.
  </p>
</div>

<div class="note info">
  <h5>Masking is per key, and that is why a listing is not expanded.</h5>
  <p>
    With <code>password</code> masked and <code>jdbc.url=…&amp;password=${password}</code> not, the
    listing shows the second line as it was written and the secret does not appear anywhere. Expanding it
    would print the masked value in clear inside the line referring to it: the mask is applied to a key,
    and a reference goes around it.
  </p>
</div>

Variable expansion can be switched off, for a single method or for the whole interface, with
`@DisableFeature(VARIABLE_EXPANSION)` — see
[Variables expansion](/owner/docs/variables-expansion/#disabling-variables-expansion). Written on the
interface it reaches `getProperty()` and `fill()` as well, and they then behave like the `getRaw` pair.

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
        mine.setProperty(entry.getKey(), cfg.getRawProperty(entry.getKey()));

mine.store(new FileOutputStream("config/app.properties"), null);
```

`getRawProperty()` and not `getProperty()`, for the reason given in the table
above: this value is on its way back to a file, and saving `http://prod-host:80`
where the file said `http://${host}:${port}` would resolve the configuration
once and for all, silently.

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
