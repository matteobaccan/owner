---
layout: docs
title: Why should I use OWNER?
prev_section: features
next_section: faq
permalink: /docs/why/
---

The reason why OWNER was written is because the code dealing with the configuration is frequently repetitive,
redundant, it's made of boring and ugly static classes, singletons, long list of methods just doing conversion from a
string property to a named method returning a Java primitive or a basic Java object.

I don't want to provide negative examples, but just have a look around on how java.util.Properties is used.
The main reason why I wrote this library is to get rid of that ugly code.

OWNER solves the problem providing an interface object that

 - it's easy to mock (see [Mockito](http://mockito.org)), easy to pass to other objects (via dependency injection).
 - declaratively maps your configuration without any redundancy.
 - can easily expand your loading logic in order to have multiple configuration files, multiple level of
   overriding (global configuration, user-level, defaults, etc).
 - doesn't need to have an actual properties file backing your configuration, if you use `@DefaultValue`.
 - provides a lot of features, like hot reloading, variables expansion, etc.
 - leaves you free to do everything you are already doing with java.util.Properties.
 - does support a super powerful [type conversion]({{ site.url }}/docs/type-conversion/), which includes arrays,
   collections, many standard Java objects, and even the possibility to plug your own conversion logic.
 - it does not bring any transitive dependency to your project, so no conflicting jars, no dependency issues.
 - it is developed to work with any reasonably recent JDK. Each commit on OWNER repository is
   [tested](https://travis-ci.org/lviggiano/owner) on Oracle JDK and Open JDK from version 7 to version 9.

...and more than everything, it's not an ugly, rigid, boring, repetitive list of methods doing all the same thing.
