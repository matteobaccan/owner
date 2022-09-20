OWNER
=====

although converter class can do it, converter class essentially makes the standard optional rather than enforced.
We would like enforced standards

The purpose of this fork is to change the return type from bare type to Optional

The forker thinks this would be problematic with respect to Java 8+, because of the following:

1. It is against the Optional (link)
2. We are forcing a standard that null should never be returned (link)

We want to essentially have null check at _compile-time_ 

We turn this

```java
import org.aeonbits.owner.Config;

public interface ServerConfig extends Config {
    
    int port();
    
    String hostname();
    
    @DefaultValue("42")
    int maxThreads();
    
    List<String> listProperty();
}
```

into this

```java
import org.aeonbits.owner.Config;

public interface ServerConfig extends Config {
    
    Optional<Integer> port();
    
    Optional<String> hostname();
    
    @DefaultValue("42")
    Optional<Integer> maxThreads();

    List<String> listProperty();
}
```

Note that collection type stays the same in accordance with the standard described in Effective Java section X


OWNER, an API to ease Java property files usage.

[![Build Status](https://travis-ci.org/lviggiano/owner.png?branch=master)](https://travis-ci.org/lviggiano/owner)
[![Coverage Status](https://coveralls.io/repos/lviggiano/owner/badge.png)](https://coveralls.io/r/lviggiano/owner)
[![security status](https://www.meterian.com/badge/gh/lviggiano/owner/security)](https://www.meterian.com/report/gh/lviggiano/owner)
[![stability status](https://www.meterian.com/badge/gh/lviggiano/owner/stability)](https://www.meterian.com/report/gh/lviggiano/owner)
[![Built with Maven](http://maven.apache.org/images/logos/maven-feather.png)](http://maven.apache.org)


INTRODUCTION
------------

The goal of OWNER API is to minimize the code required to handle
application configuration through Java properties files.

Full documentation available on [project website][website].

BASIC USAGE
-----------

The approach used by OWNER APIs, is to define a Java interface
associated to a properties file.

Suppose your properties file is defined
as `ServerConfig.properties`:

```properties
port=80
hostname=foobar.com
maxThreads=100
```

To access this property you need to define a convenient Java
interface in `ServerConfig.java`:

```java
public interface ServerConfig extends Config {
    int port();
    String hostname();
    int maxThreads();
}
```

We'll call this interface the *Properties Mapping Interface* or
just *Mapping Interface* since its goal is to map Properties into
a an easy to use piece of code.

Then, you can use it from inside your code:

```java
public class MyApp {
    public static void main(String[] args) {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        System.out.println("Server " + cfg.hostname() + ":" + cfg.port() +
                           " will run " + cfg.maxThreads());
    }
}
```

But this is just the tip of the iceberg.

Continue reading here: [Basic usage](http://owner.aeonbits.org/docs/usage/).

DOWNLOAD
--------

Public Releases can be downloaded from [GitHub Releases](https://github.com/lviggiano/owner/releases) page or
[Maven Central Repository](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.aeonbits.owner%22%20AND%20a%3A%22owner%22).


DOCUMENTATION
-------------

Make sure to have a look at the documentation on [project website][website]
to learn how flexible and powerful OWNER is, and why you may need it!


LICENSE
-------

OWNER is released under the BSD license.
See [LICENSE][] file included for the details.

  [LICENSE]: https://raw.github.com/lviggiano/owner/master/LICENSE


[website]: http://owner.aeonbits.org