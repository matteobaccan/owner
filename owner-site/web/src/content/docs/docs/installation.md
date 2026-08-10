---
title: "Installation"
---


OWNER is a Java library. The distribution form is a [JAR file][1].

However OWNER is available on Maven Central Repository, this means that if
you want you can still download the library jar, the javadoc.jar, the sources.jar
or a prepackaged archive containing the distributable binaries (including sources
and javadocs) in bin.tar.bz2 or bin.tar.gz or bin.zip format
from [this link][2].

Once downloaded what you need, you are ready to configure your IDE; and here,
it's up to you and your chosen IDE.
Generally speaking you just need to reference the library jar in the CLASSPATH
environment variable, as explained in the [Java tutorial][3].

  [1]: http://docs.oracle.com/javase/tutorial/deployment/jar/
  [2]: https://central.sonatype.com/artifact/org.aeonbits.owner/owner
  [3]: http://docs.oracle.com/javase/tutorial/essential/environment/paths.html

Maven
-----

If you are using Maven, things are quite simple, just add the following section
to your pom.xml:

```xml
<dependencies>
    <dependency>
        <groupId>org.aeonbits.owner</groupId>
        <artifactId>owner</artifactId>
        <version>1.0.12</version>
    </dependency>
</dependencies>
```

Replace 1.0.12 with the latest version available. At the time of writing this page, the latest version is 1.0.12, but
you need to check if there is any newer version.

<div class="note">
  <h5>Finding the latest version released</h5>
  <p>You can search on the <a href="https://central.sonatype.com/artifact/org.aeonbits.owner/owner">Maven Central Repository</a>
  to verify the latest available release.</p>
</div>

Many modern IDEs integrate well with maven, so after adding the above section
in your pom file and refreshing your project in your IDE, you should be ready to
use the library APIs.

Java 8 and superior
-------------------

Java 8 language features, such as [`default` methods][def-methods] in interfaces, are fully
supported by the `owner` artifact itself: no additional dependency is needed.

Older versions of the library shipped this support in a separate `owner-java8` artifact, since
the core had to run on Java 6/7. That artifact is no longer needed nor published: if you have it
in your dependencies, just replace it with `owner`.

  [def-methods]: http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html


<div class="note info">
  <h5>No transitive dependencies, full freedom!</h5>
  <p>
  The OWNER library does not introduce any transitive dependency to third party libraries
  into your project, so this should prevent any conflict with libraries from which your
  project depends on.
  </p>
</div>

On the module path
------------------

*Since 2.0.0.*

The jars declare the name they take as automatic modules, so a `requires` written against them keeps
resolving from one release to the next:

```java
module com.example.myapp {
    requires org.aeonbits.owner;
}
```

| Artifact | Module name |
|---|---|
| `owner` | `org.aeonbits.owner` |
| `owner-extras` | `org.aeonbits.owner.extras` |

The name matters because without it the module system derives one from the file name, which carries the
version: a `requires` written against `owner-2.0.0.jar` would stop resolving against the next release.
Declaring it pins the name to the artifact instead, and it is the same name the OSGi bundle already had.

Both artifacts can be placed on the module path together, since they share no package: up to 1.0.12 the
`ZooKeeperLoader` of `owner-extras` lived under `org.aeonbits.owner.loaders`, which the core also fills, and
a package cannot belong to two modules.

<div class="note">
  <h5>Automatic modules, not explicit ones</h5>
  <p>
  The jars declare a name, they do not yet carry a <code>module-info</code>: OWNER is compiled for Java 8.
  An automatic module reads every other module and exports all of its packages, so the arrangement above is
  what makes the library usable on the module path, not a full modularisation of it.
  </p>
</div>


Building from the sources
-------------------------

You can install the version under development to get advantage of the latest
features.

If you want to do so, please consult the chapter
[Building from sources](/owner/docs/building).
