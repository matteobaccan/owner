---
layout: docs
title: Importing properties
prev_section: loading-strategies
next_section: parametrized-properties
permalink: /docs/importing-properties/
---
You can use another mechanism to load your properties into a *mapping interface*.
And this mechanism is to specify a Properties object programmatically when 
calling `ConfigFactory.create()`:

```java
public interface ImportConfig extends Config {

    @DefaultValue("apple")
    String foo();

    @DefaultValue("pear")
    String bar();

    @DefaultValue("orange")
    String baz();

}

// then...

Properties props = new Properties();
props.setProperty("foo", "pineapple");
props.setProperty("bar", "lime");

ImportConfig cfg = ConfigFactory
    .create(ImportConfig.class, props); // props imported!

assertEquals("pineapple", cfg.foo());
assertEquals("lime", cfg.bar());
assertEquals("orange", cfg.baz());
```

<div class="note warning">
  <h5>Imported keys and values must be non-null Strings.</h5>

  <p>
  A <code>Properties</code> or <code>Map</code> object accepts <code>null</code> key or 
  <code>null</code> values, but that is obviously an error, 
  so starting from version 1.0.10, an <code>IllegalArgumentException</code> is thrown.
  </p>

  <p>
  Starting from version 2.0.0, the same applies to keys and values that are not
  <code>String</code>. The exception message also contains further information about the
  offending key, and its type when applicable.
  </p> 
</div>

The reason is that imports are merged into a `java.util.Properties`, and even though its
contract only admits `String` keys and values, it extends `Hashtable<Object, Object>`:
anything else is accepted by `putAll` and then becomes invisible to `getProperty`.
Before 2.0.0 such an entry was taken without complaint and then quietly misbehaved when read:

```java
public interface MyConfig extends Config {
    @Key("some.key")
    @DefaultValue("1")
    Integer someValue();
}

Map<String, Object> imports = new HashMap<>();
imports.put("some.key", 42);          // an Integer, not a String

MyConfig cfg = ConfigFactory.create(MyConfig.class, imports);

// before 2.0.0: null - the unusable entry also shadowed @DefaultValue
// since  2.0.0: IllegalArgumentException at create() time
cfg.someValue();
```

A non-`String` **key** failed in a different way: the entry was simply ignored, and the
property silently fell back to its default, as if the import had never been passed.

```java
Map<Object, String> imports = new HashMap<>();
imports.put(42, "value");             // an Integer key

// before 2.0.0: the entry is dropped without any notice
// since  2.0.0: IllegalArgumentException at create() time
ConfigFactory.create(MyConfig.class, imports);
```

Note that a `CharSequence` is not enough, since `Properties` compares against `String`
specifically: `new StringBuilder("some.key")` and `new StringBuffer("42")` are rejected too.
Convert them with `toString()` before importing.

Passing a `String` key with a `String` value keeps working exactly as before, so code
that was already correct is unaffected:

```java
Map<String, String> imports = new HashMap<>();
imports.put("some.key", "42");

assertEquals(Integer.valueOf(42),
    ConfigFactory.create(MyConfig.class, imports).someValue());
```

The check runs for every entry point that builds a Config from imports, so
`ConfigFactory.create()`, a `Factory` instance obtained from `ConfigFactory.newInstance()`
and `ConfigCache.getOrCreate()` all behave the same way.

You can specify multiple properties to import on the same line:

```java
ImportConfig cfg = ConfigFactory
    .create(ImportConfig.class, props1, props2, ...);
```

If there are prop1 and prop2 defining two different values for the same property
key, the one specified first will prevail:

```java
Properties p1 = new Properties();
p1.setProperty("foo", "pineapple");
p1.setProperty("bar", "lime");

Properties p2 = new Properties();
p2.setProperty("bar", "grapefruit");
p2.setProperty("baz", "blackberry");


ImportConfig cfg = ConfigFactory
    .create(ImportConfig.class, p1, p2); // props imported!

assertEquals("pineapple", cfg.foo());

// p1 prevails, so this is lime and not grapefruit
assertEquals("lime", cfg.bar()); 

assertEquals("blackberry", cfg.baz());
```

This is pretty handy if you want to load properties provided by other mechanisms which not accessible through any of the supported URI schemes listed under [Loading Strategies](loading-strategies.md)

For instance, a Java EE (a.k.a. Jakarta EE) servlet running on a servlet container might load properties during initialization from a resource accessible through its respective [ServletContext](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html):

```java
interface ServletContextProperties extends Config {
    /** JDBC name of a data source used by the servlet */
    @Key("ds.name")
    String dsName();

    void list(PrintStream out);
}

...

public class MyServlet extends HttpServlet {

    protected void init() {
        ServletContextProperties cfg = ConfigFactory
            .create(ServletContextProperties.class, 
                    getServletConfig().getServletContext().getResourceAsStream("/WEB-INF/myServlet.properties"));
    }
</div>
}
```

<div class="note info">
Note that this way of proceeding yields the responsibility of proper usage to the client,
whose code shall never 'forget' to include the <tt>import</tt> parameter when calling the <tt>ConfigFactory.create()</tt> ).
</div>

Thus, if you want to refer to properties provided by any of the mechanisms directly supported
by the `@Source` annotation, you should rather use them, as explained in [Loading strategies](/docs/loading-strategies/).
In particular, to refer to system properties or environment variables,
you can use (since version 1.0.10) `system:properties` or `system:env` (respectively).

Other typical usage of importing properties might involve loading them from other sources directly
provided by the execution environment, e.g. [servlet context](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletContext.html) attributes, [context or servlet initialization parameters](https://docs.oracle.com/cd/E19226-01/820-7627/bnaes/index.html), [JNDI](https://docs.oracle.com/javase/tutorial/jndi/index.html) application environment resources (i.e. entries under `java:comp/env/`), [Java preferences](https://docs.oracle.com/javase/8/docs/technotes/guides/preferences/index.html), or any other environment-dependent property sources. However, none of these sources direcly provide an API to access their contents as a `Map` object;
hence the programmer would need in that case to implement first their own method to convert from lists of names
plus individual values to `Map` object (therefore compatible with Owner API).
</div>

Interactions with loading strategies
------------------------------------

Notice that the "importing properties" feature is additional to the properties 
loading mechanism explained in chapter 
[Loading strategies](/docs/loading-strategies/).

Properties imported programmatically have higher priority regarding the 
properties loaded from the `@Sources` attribute.

Imagine the scenario where the you define your configuration with `@Sources` 
annotation, but you want to allow the user to specify a configuration file at
the command line. 

```java

@Sources(...)
interface MyConfig extends Config { 
    ...
}

public static void main(String[] args) {
    MyConfig cfg;
    if (args.lenght() > 0) {
        Properties props = new Properties();
        props.load(new FileInputStream(new File(args[0])));
        cfg = ConfigFactory.create(MyConfig.class, userProps);
    } else {
        cfg = ConfigFactory.create(MyConfig.class);
    }
}
```

In the above example, the properties file specified by the user will override 
the properties loaded by `@Sources` if there is overlapping between the 
properties names. 
This approach is used by many command line tools, that allow the user to specify
a configuration on the command line that overrides the default one.

<div class="note warning">
  <h5>This is true only with version 1.0.3.1 and superior!</h5>
  <p>Be aware that in versions prior to 1.0.3.1 imported properties have lower 
  priority than others loaded properties. This behavior has been changed in
  version 1.0.3.1 and it will be kept this way for future releases.</p>
</div>


