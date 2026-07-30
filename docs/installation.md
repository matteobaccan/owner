---
layout: docs
title: Installation
prev_section: welcome
next_section: usage
permalink: /docs/installation/
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


Building from the sources
-------------------------

You can install the version under development to get advantage of the latest
features.

If you want to do so, please consult the chapter
[Building from sources]({{ site.url }}/docs/building).
