---
title: "Parametrized properties"
---

Another neat feature, is the possibility to provide parameters on method 
interfaces.  
The property values shall respect the positional notation specified by the 
[`java.util.Formatter`][fmt] class:

  [fmt]: http://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax

```java
public interface Sample extends Config {
    @DefaultValue("Hello Mr. %s!")
    String helloMr(String name);
}

Sample cfg = ConfigFactory.create(Sample.class);
print(cfg.helloMr("Luigi")); // will println 'Hello Mr. Luigi!'
```

Parametrized keys
-----------------

The method parameters are applied to the property *key* as well, and not only to
the property value: this allows to pick a property at runtime, out of a group of
properties sharing the same naming convention.

```properties
servers.dev.hostname=devhost
servers.uat.hostname=uathost
```

```java
public interface ServerConfig extends Config {
    @Key("servers.%s.hostname")
    String hostname(String setup);
}

ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
print(cfg.hostname("dev")); // will println 'devhost'
print(cfg.hostname("uat")); // will println 'uathost'
```

Any argument accepted by [`java.util.Formatter`][fmt] can be used, so numeric
indexes and enums work too (enums are formatted through `toString()`, which by
default returns the enum constant name):

```properties
foo.1=a
queue.GREEN=jms/QueueA
```

```java
public interface Sample extends Config {
    @Key("foo.%s")   String foo(int index);
    @Key("queue.%s") String queue(Colour colour); // Colour is an enum
}
```

When the key resulting from the substitution is not defined, the method returns
`null`, unless a `@DefaultValue` is specified: the default value is then
formatted with the very same parameters.

```java
public interface Sample extends Config {
    @Key("foo.%s")
    @DefaultValue("no value for %s")
    String foo(String index);
        // foo("bar") returns "no value for bar" when 'foo.bar' is not defined.
}
```

<div class="note">
  <h5>Parameters and variables cannot be mixed in the same key</h5>
  <p>
    When a key contains a <code>${...}</code> variable, only
    <a href="/owner/docs/variables-expansion/">variables expansion</a> is performed on it, and the
    <code>%s</code> placeholders are left untouched: a key like
    <code>@Key("${prefix}.%s")</code> is <em>not</em> formatted with the method parameters.
  </p>
</div>

<div class="note info">
  <h5>Parametrized keys are governed by VARIABLE_EXPANSION</h5>
  <p>
    The key is resolved by the same step that performs variables expansion, hence disabling
    <code>VARIABLE_EXPANSION</code> (see <a href="/owner/docs/disabling-features/">Disabling features</a>)
    disables parametrized keys too, and the key is used as it is, <code>%s</code> included.
    Disabling <code>PARAMETER_FORMATTING</code>, instead, only affects the property values: keys keep being
    parametrized.
  </p>
</div>

Disabling parameters expansion
------------------------------

The parametrized properties feature can be disabled if the user doesn't find it 
convenient for some reason.

This can be done using the `@DisableFeature` annotation:

```java
public interface Sample extends Config {

    @DisableFeature(PARAMETER_FORMATTING)
    @DefaultValue("Hello %s.")    
    public String hello(String name); 
        // will return "Hello %s." ignoring the parameter.

}
```

The `@DisabledFeature` annotation can be applied on method level and/or on 
the interface level. When applied on the interface level, it will apply to all 
the methods of the interface:

```java
@DisableFeature(PARAMETER_FORMATTING)
public interface Sample extends Config {

    @DefaultValue("Hello %s.")    
    public String hello(String name); 
        // will return "Hello %s." ignoring the parameter.

}
```
