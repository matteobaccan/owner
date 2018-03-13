---
layout: docs
title: Disabling features
prev_section: debugging
next_section: configuring
permalink: /docs/disabling-features/
---

If for some reasons some feature is causing some problems it is possible, for 
some of them, to be disabled. For instance, if you are implementing the variable
expansion by yourself, you may want to disable the variable expansion provided
by OWNER.


This can be done using the [`@DisabledFeature`][df] annotation.

The `@DisabledFeature` can also be combined with multiple 
[`DisableableFeature`][dfe] and it can be used on method level or on class level:

```java
// on class level...
@DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING}) 
public interface SampleConfig extends Config {
    @DefaultValue("Earth")
    public String planet();

    // on method level...
    @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING}) 
    @DefaultValue("Hello %s, welcome on ${planet}!")
    public String hello(String name); 
}
```

In the above example the method `String hello(String name)` will return the 
String "Hello %s, welcome on ${planet}!", ignoring the parameter passed.

  [dfe]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.DisableableFeature.html
  [df]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.DisableFeature.html
