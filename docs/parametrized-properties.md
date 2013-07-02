---
layout: docs
title: Parametrized properties
prev_section: importing-properties
next_section: type-conversion
permalink: /docs/parametrized-properties/
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
