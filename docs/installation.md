---
layout: docs
title: Installation
prev_section: hoome
next_section: usage
permalink: /docs/installation/
---

OWNER is a Java library. The distribution form is a [JAR file](http://docs.oracle.com/javase/tutorial/deployment/jar/).

However OWNER is available on Maven Central Repository, this means that if
you want you can still download the library jar, the javadoc.jar, the sources.jar
or a prepackaged archive containing the distributable binaries (including sources 
and javadocs) in bin.tar.bz2 or bin.tar.gz or bin.zip format
from [this link](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.aeonbits.owner%22%20AND%20a%3A%22owner%22).

Once downloaded what you need, you are ready to configure your IDE; and here, 
it's up to you and your chosen IDE.
Generally speaking you just need to reference the library jar in the CLASSPATH
environment variable, as explained in the [Java tutorial](http://docs.oracle.com/javase/tutorial/essential/environment/paths.html).


Maven
-----

If you are using Maven, things are quite simple, just add the following section
to your pom.xml:

{% highlight xml %}
<dependencies>
    <dependency>
        <groupId>org.aeonbits.owner</groupId>
        <artifactId>owner</artifactId>
        <version>1.0.3.1</version>
    </dependency>
</dependencies>
{% endhighlight %}

<div class="note">
  <h5>Finding the latest version released</h5>
  <p>At the moment I am writing the last released version is the 1.0.3.1.</p>
  <p>You can search on the <a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.aeonbits.owner%22%20AND%20a%3A%22owner%22">Maven Central Repository</a> 
  to verify the latest available release.</p>
</div>

Many modern IDEs integrate well with maven, so after adding the above section
in your pom file and refreshing your project in your IDE, you should be ready to 
use the library APIs.

<div class="note info">
  <h5>No dependencies, full freedom!</h5>
  <p>
  The OWNER library does not introduce any transitive dependency to your project,
  so this should prevent any conflict with libraries from which your project 
  depends on.
  </p>
</div>


Building from the sources
-------------------------

Building OWNER from the sources has some advantages. For instance you can get 
the latest features still under development on github master branch.

Building OWNER requires following software being installed and configured in 
your system:

 - [JDK](http://docs.oracle.com/javase/7/docs/webnotes/install/) 1.5 or superior.
 - [Maven](http://maven.apache.org/download.cgi#Installation_Instructions) 3.0.5 
   or superior (Maven 2 should also be ok). 
 - [GIT](http://git-scm.com/book/en/Getting-Started-Installing-Git) any recent version should be ok.

Then follow these steps:

{% highlight bash %}
# Download the sources
$ git clone https://github.com/lviggiano/owner.git owner
$ cd owner
# Compile, execute test, and generate the artifacts
$ mvn install
{% endhighlight %}

This will build and install OWNER jars in your local maven repository.
At the end of the process, you should find the generated artifacts in the 
`target` subdirectory.

<div class="note">
  <h5>GIT URLs</h5>
  <p>
The above examples uses the https url to clone the GIT repository, alternatively
- if your firewall allows - you can use the GIT native URL that may be 
faster: git://github.com/lviggiano/owner.git.  
  </p>
</div>


Building a specific version
---------------------------

The `git clone` command downloads the full repository with the complete history
on your local computer. That also contains tags for the released versions.

For instance, if I want to build the version 1.0.2

{% highlight bash %}
# as example, this time we use the git:// URL
$ git clone git://github.com/lviggiano/owner.git owner
$ cd owner
# show all available tags
$ git tag -l
owner-1.0
owner-1.0.1
owner-1.0.2
owner-1.0.3
owner-1.0.3.1
$ git checkout owner-1.0.2
HEAD is now at d2e4bbf... [maven-release-plugin] prepare release owner-1.0.2
$ mvn install
{% endhighlight %}

Please refer to GIT documentation to learn how to work with tags.
