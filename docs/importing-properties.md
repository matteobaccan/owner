---
layout: docs
title: Importing properties
permalink: /docs/importing-properties/
---
You can use another mechanism to load your properties into a *mapping interface*.
And this mechanism is to specify a [Properties][properties] object programmatically when calling
`ConfigFactory.create()`:

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

ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props); // props imported!

assertEquals("pineapple", cfg.foo());
assertEquals("lime", cfg.bar());
assertEquals("orange", cfg.baz());
```

You can specify multiple properties to import on the same line:

```java
ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props1, props2, ...);
```

If there are prop1 and prop2 defining two different values for the same property key, the one specified first will
prevail:

```java
Properties p1 = new Properties();
p1.setProperty("foo", "pineapple");
p1.setProperty("bar", "lime");

Properties p2 = new Properties();
p2.setProperty("bar", "grapefruit");
p2.setProperty("baz", "blackberry");


ImportConfig cfg = ConfigFactory.create(ImportConfig.class, p1, p2); // props imported!

assertEquals("pineapple", cfg.foo());
assertEquals("lime", cfg.bar()); // p1 prevails, so this is lime and not grapefruit
assertEquals("blackberry", cfg.baz());
```

This is pretty handy if you want to reference system properties or environment variables:

```java
interface SystemEnvProperties extends Config {
    @Key("file.separator")
    String fileSeparator();

    @Key("java.home")
    String javaHome();

    @Key("HOME")
    String home();

    @Key("USER")
    String user();

    void list(PrintStream out);
}

SystemEnvProperties cfg = ConfigFactory.create(SystemEnvProperties.class, System.getProperties(), System.getenv());
assertEquals(File.separator, cfg.fileSeparator());
assertEquals(System.getProperty("java.home"), cfg.javaHome());
assertEquals(System.getenv().get("HOME"), cfg.home());
assertEquals(System.getenv().get("USER"), cfg.user());
```

Importing properties and other loading strategies
-------------------------------------------------

Notice that the "importing properties" feature is just additional to the properties loading mechanism explained in chapter [Loading strategies](loading-strategies.html)
Properties imported have lower priority regarding the properties loaded from the `@Sources` attribute.

Example:

```java
{% raw %}
    private static final String spec = "file:target/test-resources/ImportConfig.properties";

    @Sources(spec)
    public static interface ImportConfig extends Config {

        @DefaultValue("apple")
        String foo();

        @DefaultValue("pear")
        String bar();

        @DefaultValue("orange")
        String baz();

    }

    @Test
    public void testThatImportedPropertiesHaveLowerPriorityThanPropertiesLoadedBySources() throws IOException {
        File target = new File(new URL(spec).getFile());

        save(target, new Properties() {{
            setProperty("foo", "strawberries");
        }});

        try {
            Properties props = new Properties();
            props.setProperty("foo", "pineapple");
            props.setProperty("bar", "lime");
            ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props); // props imported!
            assertEquals("strawberries", cfg.foo());
            assertEquals("lime", cfg.bar());
            assertEquals("orange", cfg.baz());
        } finally {
            target.delete();
        }
    }
{% endraw %}
```

As you can see in above example, the property `foo` is defined with a `@DefaultValue` as 'apple' but it is redefined
both in the imported properties `props` as 'pineapple' and in the file identified by `@Sources`
(file:target/test-resources/ImportConfig.properties) as 'strawberries'.
The behavior assumed by the OWNER API is that the one specified by the `@Sources` wins, so the value, in this particular
case will be 'strawberries'.

There is no specific reason why we gave `@Sources` higher priority over the imported properties, it is an arbitrary
decision based on the fact that something defined in a file by a DevOp or a User should be considered more influential.
