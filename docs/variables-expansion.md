---
layout: docs
title: Variables expansion
permalink: /docs/variables-expansion/
---

Sometimes it may be useful to expand properties values from other properties:

```properties
story=The ${animal} jumped over the ${target}
animal=quick ${color} fox
target=${target.attribute} dog
target.attribute=lazy
color=brown
```

```java
public interface ConfigWithExpansion  extends Config {
    String story();
}
```

The property `story` will expand to *The quick brown fox jumped over the lazy
 dog*.

This also works with the annotations, but you need to specify every properties 
on the methods:

```java
public interface ConfigWithExpansion
        extends Config {

    @DefaultValue(
        "The ${animal} jumped over the ${target}")
    String story();

    @DefaultValue("quick ${color} fox")
    String animal();

    @DefaultValue("${target.attribute} dog")
    String target();

    @Key("target.attribute")
    @DefaultValue("lazy")
    String targetAttribute();

    @DefaultValue("brown")
    String color();
}

ConfigWithExpansion conf = ConfigFactory
    .create(ConfigWithExpansion.class);
    
String story = conf.story();
```

Sometimes you may want expand System Properties or Environment Variables.
This can be done using *imports* (see dedicated paragraph to learn more):

```java
public interface SystemPropertiesExample 
        extends Config {

    @DefaultValue("Welcome: ${user.name}")
    String welcomeString();

    @DefaultValue("${TMPDIR}/tempFile.tmp")
    File tempFile();
    
}

SystemPropertiesExample conf = ConfigFactory
    .create(SystemPropertiesExample.class, 
            System.getProperties(), 
            System.getenv());
            
String welcome = conf.welcomeString();
File temp = conf.tempFile();
```


Disabling variables expansion
-----------------------------

The variables expansion feature can be disabled if the user doesn't find it 
convenient for some reason.  
This can be done using the `@DisableFeature` annotation:

```java
public interface Sample extends Config {
    @DefaultValue("Earth")
    String world();

    @DisableFeature(VARIABLE_EXPANSION)
    @DefaultValue("Hello ${world}.")
    
    // will return the string "Hello ${world}."
    String sayHello(); 
}

```

The `@DisabledFeature` annotation can be applied on method level and/or on 
class level.


