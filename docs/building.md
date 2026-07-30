---
layout: docs
title: Building from sources
prev_section: support
next_section: contributing
permalink: /docs/building/
---

Building OWNER from the sources has some advantages. For instance you can get
the latest features still under development on GitHub master branch.

Building OWNER requires following software being installed and configured in
your system:

 - [JDK](https://adoptium.net/) 11 or superior.
 - [Maven](http://maven.apache.org/download.cgi#Installation_Instructions) 3.6.3
   or superior (enforced by the build).
 - [GIT](http://git-scm.com/book/en/Getting-Started-Installing-Git) any recent version should be ok.

The produced bytecode remains compatible with Java 8 at runtime, but a JDK 11 or
superior is required to compile the project. The build is continuously tested on
JDK 11, 17, 21 and 25.

Runtime compatibility in older releases: Java 5 and 6 are supported until OWNER
1.0.9; with OWNER 1.0.10 Java 9 support was introduced and Java 5 and 6 support
was dropped.

Then follow these steps:

```bash
# Download the sources
$ git clone https://github.com/matteobaccan/owner.git owner
$ cd owner
# Compile, execute test, and generate the artifacts
$ mvn install
```

This will build and install OWNER jars in your local maven repository.
At the end of the process, you should find the generated artifacts in the
`target` subdirectory.

<div class="note">
  <h5>GIT URLs</h5>
  <p>
The above examples uses the https url to clone the GIT repository, alternatively
- if you have an SSH key configured on GitHub - you can use the SSH URL:
git@github.com:matteobaccan/owner.git.
  </p>
</div>


Support for Java 8 and superior
-------------------------------

OWNER supports the language features introduced by Java 8 and superior, such as
[`default` methods][def-methods] in interfaces, directly in the core `owner` module: the library
requires Java 8 as minimum runtime, so the separate `owner-java8` module of older versions is gone.

  [def-methods]: http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html


Building a specific version
---------------------------

The `git clone` command downloads the full repository with the complete history
on your local computer. That also contains tags for the released versions.

For instance, if I want to build the version 1.0.2

```bash
# as example, this time we use the SSH URL
$ git clone git@github.com:matteobaccan/owner.git owner
$ cd owner
# show all available tags
$ git tag -l
owner-1.0
owner-1.0.1
owner-1.0.2
owner-1.0.3
...
$ git checkout owner-1.0.2
HEAD is now at d2e4bbf... [maven-release-plugin] prepare release owner-1.0.2
$ mvn install
```

Please refer to [GIT documentation](http://git-scm.com/documentation) to learn
how to work with tags.


Running the tests
-----------------

OWNER codebase is very compact and fully tested.

To execute the tests, you need maven properly installed and configured in your
system, then run the following command from the project root:

```
$ mvn test
```


Continuous Integration
----------------------

Every push and pull request is built by
[GitHub Actions](https://github.com/matteobaccan/owner/actions) on all the
supported LTS JDKs (11, 17, 21 and 25), and the code is analyzed by
[SonarCloud](https://sonarcloud.io/project/overview?id=matteobaccan_owner)
for quality and test coverage.

