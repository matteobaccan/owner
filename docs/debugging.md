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
  [2]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Accessible.html

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
