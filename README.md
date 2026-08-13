OWNER
=====

OWNER, an API to ease Java property files usage.

[![Owner build](https://github.com/matteobaccan/owner/actions/workflows/maven.yml/badge.svg)](https://github.com/matteobaccan/owner/actions/workflows/maven.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=matteobaccan_owner&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=matteobaccan_owner)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=matteobaccan_owner&metric=coverage)](https://sonarcloud.io/component_measures?id=matteobaccan_owner&metric=coverage)

[![Built with Maven](http://maven.apache.org/images/logos/maven-feather.png)](http://maven.apache.org)


INTRODUCTION
------------

The goal of OWNER API is to minimize the code required to handle
application configuration through Java properties files.

**In a hurry?** The [slide deck][presentation] is the whole library in one
pass — what it does, what it looks like, and what is new in 2.0.0 — without
reading the documentation. It opens in the browser; use the arrow keys. Full
documentation is on the [project website][website].

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
an easy to use piece of code.

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

Continue reading here: [Basic usage](https://matteobaccan.github.io/owner/docs/usage/).

DOWNLOAD
--------

Public Releases can be downloaded from [GitHub Releases](https://github.com/matteobaccan/owner/releases) page or
[Maven Central Repository](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.aeonbits.owner%22%20AND%20a%3A%22owner%22).


DOCUMENTATION
-------------

Make sure to have a look at the documentation on [project website][website]
to learn how flexible and powerful OWNER is, and why you may need it!

Chinese documentation is provided by [Yunfeng Cheng](https://github.com/cyfonly) via a GitHub independent project at
[this address][chinese-docs].

  [website]: https://matteobaccan.github.io/owner
  [presentation]: https://matteobaccan.github.io/owner/presentation/owner-java-properties-reinvented.html
  [chinese-docs]: https://github.com/cyfonly/owner-doc

QUESTIONS AND DOCUMENTATION
---------------------------

To interact with the **Owner Documentation**, visit [Deep Wiki](https://deepwiki.com/matteobaccan/owner).

LICENSE
-------

OWNER is released under the BSD license.
See [LICENSE][] file included for the details.

  [LICENSE]: https://raw.github.com/matteobaccan/owner/master/LICENSE
