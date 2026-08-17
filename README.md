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

WHAT ELSE IT DOES
-----------------

Everything below is a link into the documentation, and everything below works on
an interface like the one above — there is no context to build, no container to
be inside, and no dependency to add unless the row says so.

| | |
|---|---|
| [Where the properties come from](https://matteobaccan.github.io/owner/docs/loading-strategies/) | several sources, tried in turn or merged; a file, the classpath, the environment, system properties, a URL, JNDI, ZooKeeper — or the file named after the interface, which needs no configuration at all |
| [Formats](https://matteobaccan.github.io/owner/docs/file-formats/) | `.properties`, XML, `.env` and INI in the core; JSON, YAML and TOML in `owner-formats`, parsed by hand so that adding them adds nothing else to your build; HOCON in `owner-extras`, through Typesafe Config |
| [Types](https://matteobaccan.github.io/owner/docs/type-conversion/) | primitives, enums, `URL`, `Duration`, arrays and collections, anything with a `valueOf` or a one-`String` constructor, `Optional<T>`, or a converter of yours |
| [Nested configuration](https://matteobaccan.github.io/owner/docs/nested-configuration/) | a method returning another mapping interface reads a section of the file; a `List` of them reads `servers[0].host`, a `Map` reads `servers.alpha.host` |
| [Defaults, variables and parameters](https://matteobaccan.github.io/owner/docs/variables-expansion/) | `@DefaultValue`, `${...}` expansion between properties with a default of its own, and values used as a format for the method's arguments |
| [The key, spelt any of four ways](https://matteobaccan.github.io/owner/docs/usage/#how-the-key-may-be-written) | `firstName()` finds `firstName`, `first-name`, `first_name` or the environment's `FIRST_NAME` |
| [Reload and hot reload](https://matteobaccan.github.io/owner/docs/reload/) | on demand, or by watching the file, synchronously or on a schedule |
| [Reading and writing the properties](https://matteobaccan.github.io/owner/docs/accessible-mutable/) | `Accessible` to look at them, `Mutable` to change them, `Traceable` to ask which source a value came from, and `save(File)` to write the file back **keeping its comments and its order** |
| [Secrets](https://matteobaccan.github.io/owner/docs/crypto/) | an encrypted value in the file, with the cipher included — one passphrase, or a key pair so that whoever adds a secret cannot read the others |
| [Bean Validation](https://matteobaccan.github.io/owner/docs/validation/) | `@Min`, `@NotNull` and the rest checked on the accessors this library teaches you to write; a constraint nobody is checking is reported rather than ignored |
| [When it does not work](https://matteobaccan.github.io/owner/docs/debugging/) | one switch and the library says what it looked for, what it found, which loader answered and which key each method resolves to — and `@Sensitive` keeps a secret out of that output |

DOWNLOAD
--------

```xml
<dependency>
    <groupId>org.aeonbits.owner</groupId>
    <artifactId>owner</artifactId>
    <version>1.0.12</version>
</dependency>
```

That is the latest published release. **2.0.0 is in preparation** — it requires
Java 8, it is what this documentation describes,
and [what's new](https://matteobaccan.github.io/owner/news/) lists what it adds
and the little it removes. Until it is out, check
[Maven Central](https://central.sonatype.com/artifact/org.aeonbits.owner/owner)
for the newest version, and see
[Installation](https://matteobaccan.github.io/owner/docs/installation/) for the
`owner-formats` and `owner-extras` artifacts.

Releases are also on the [GitHub Releases](https://github.com/matteobaccan/owner/releases) page.


DOCUMENTATION
-------------

Make sure to have a look at the documentation on [project website][website]
to learn how flexible and powerful OWNER is, and why you may need it!

The [API documentation](https://matteobaccan.github.io/owner/apidocs/latest/) is
published from `master`.

Chinese documentation is provided by [Yunfeng Cheng](https://github.com/cyfonly) via a GitHub independent project at
[this address][chinese-docs].

  [website]: https://matteobaccan.github.io/owner
  [presentation]: https://matteobaccan.github.io/owner/presentation/owner-java-properties-reinvented.html
  [chinese-docs]: https://github.com/cyfonly/owner-doc

QUESTIONS AND DOCUMENTATION
---------------------------

To interact with the **Owner Documentation**, visit [Deep Wiki](https://deepwiki.com/matteobaccan/owner).

CONTRIBUTING
------------

Bug reports and pull requests are welcome. What makes one easy to say yes to is
written down in [Contributing](https://matteobaccan.github.io/owner/docs/contributing/);
`TODO.md` in this repository is the working list, with the reasons behind what is
being done and what has deliberately not been.

LICENSE
-------

OWNER is released under the BSD license.
See [LICENSE][] file included for the details.

  [LICENSE]: https://raw.github.com/matteobaccan/owner/master/LICENSE
