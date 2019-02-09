---
layout: docs
title: Loading strategies
prev_section: usage
next_section: importing-properties
permalink: /docs/loading-strategies/
---

The properties file for a *mapping interface* is automatically resolved by OWNER 
API by matching the class name and the file name (appending `.properties` of 
course).  

But this logic can be tailored to your needs using some additional annotations:  

```java
@Sources({ "file:~/.myapp.config", 
           "file:/etc/myapp.config", 
           "classpath:foo/bar/baz.properties" })
public interface ServerConfig extends Config {
    @Key("server.http.port")
    int port();
    
    @Key("server.host.name")
    String hostname();
    
    @Key("server.max.threads");
    @DefaultValue("42")
    int maxThreads();
}
```

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html


In the above example, OWNER will try to load the properties from several `@Sources`:

 1. First, it will try to load the properties file from user's home directory ~/.myapp.config, if this is found, this
    file alone will be used.
 2. If the previous attempt fails, then it will try to load the properties file from /etc/myapp.config, and if this is
    found, this one will be used.
 3. As last resort, it will try to load the properties from the classpath loading the resource identified by the
    path foo/bar/baz.properties.
 4. If none of the previous URL resources is found, then the Java interface will not be associated to any file, and only
    `@DefaultValue` will be used where specified. Where properties don't have a default value, `null` will be returned
    (as it happens for [`java.util.Properties`][properties]).

In the above case, the properties values will be loaded from only one file: the first that is found.
*Only the first available properties file will be loaded, others will be ignored*.

This load logic, is identified as *"FIRST"*, since only the first file found will be considered, and it is the default
logic adopted when the `@Source` annotation is specified with multiple URLs.  
You can also specify this load policy explicitly using `@LoadPolicy(LoadType.FIRST)` on the interface declaration.

But what if you want to have some *overriding* between properties? This is definitely possible: you can do it with
the annotation `@LoadPolicy(LoadType.MERGE)`:

```java
@LoadPolicy(LoadType.MERGE)
@Sources({ "file:~/.myapp.config", 
           "file:/etc/myapp.config", 
           "classpath:foo/bar/baz.properties",
           "system:properties",
           "system:env" })
public interface ServerConfig extends Config {
    ...
}
```

In this case, for *every property* all the specified URLs will be queries, and the first resource defining the property
will prevail.
More in detail, this is what will happen:

 1. First, it will try to load the given property from ~/.myapp.config;
    if the given property is found the associated value will be returned.
 2. Then it will try to load the given property from /etc/myapp.config;
    if the property is found the value associated will be returned.
 3. Then it will try to load the given property from the classpath from the resource identified
    by the path foo/bar/baz.properties; if the property is found, the associated value is returned.
 4. Otherwise, it will try to load the given property from the <a  href="https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html">Java system properties</a>;
 if such property is defined, the associated value is returned.
 5. As last resort, it will try to load the given property from the <a    href="https://docs.oracle.com/javase/tutorial/essential/environment/env.html">operating system's environment variables</a>;
 if an environment variable with the same name is found, its value will be returned.    
 6. If the given property is not found of any of the above cases, it will be returned the value specified by the
    `@DefaultValue` if specified, otherwise null will be returned.

So basically we produce a merge between the properties files where the first property files overrides latter ones.

The `@Sources` annotation considers system properties and/or environment variables with the syntax
`file:${user.home}/.myapp.config` (this gets resolved by 'user.home' system property) or `file:${HOME}/.myapp.config`
(this gets resolved by the$HOME environment variable). The `~` used in the previous example is another example of
variable expansion, and it is equivalent to `${user.home}`.
