---
layout: news_item
title: "Owner 1.0.12 Released"
date: "2020-06-07 02:44:00 +0200"
author: lviggiano
version: 1.0.12
categories: [release]
---

I just released version 1.0.12, it contains all the bug fixes included in 1.0.11 plus a fix to a 
multi threading issue that appeared in 1.0.11.

--Luigi.
     
  
RELEASE NOTES
=============

OWNER v1.0.12 contains following enhancements and bug fixes.

Enhancements
------------
 * None
 
Site Enhancements
-----------------
 * None
 
Bugs fixes
----------
 * Fixed [#268](https://github.com/lviggiano/owner/issues/268): Calling a value is not thread safe (return another value)
 * Fixed [#266](https://github.com/lviggiano/owner/issues/266): PropertyEditor - Concurrency Issues

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-1.0.12) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.12/).


<div class="note info">
  <h5>The 1.0.11 was not announced, in fact it introduced a bug in multi threading.</h5>
  <p>if you are using 1.0.11, please update to 1.0.12 asap, so that you should have the bug fixed.</p>
</div>

OWNER v1.0.11 contains following enhancements and bug fixes. 

Enhancements
------------
 * [#234](https://github.com/lviggiano/owner/pull/234): Allowing to format Key value by method arguments as 
   with DefaultValue.
 * [#255](https://github.com/lviggiano/owner/pull/255): Solves the thread contention problem reported on Issue #254; 
   Note this has partially been rolled back in 1.0.12 due to bugs [#268](https://github.com/lviggiano/owner/issues/268) 
   and [#266](https://github.com/lviggiano/owner/issues/266).
 * [64a7c07](https://github.com/lviggiano/owner/commit/64a7c07bd79287b1d9debacfe60ad6e4e597cc39): 
   Updated dependencies to work with Java 11 LTS.
 
Site Enhancements
-----------------
 * [#247](https://github.com/lviggiano/owner/pull/247): Documentation for system:properties and system:env.
 * Fixed [Sonar](https://sonarcloud.io/dashboard?id=org.aeonbits.owner%3Aowner-parent) and 
   [Travis](https://travis-ci.org/github/lviggiano/owner); but still it looks that 
   [Coveralls](https://coveralls.io/github/lviggiano/owner) has issue to link source files to github, I need to look 
   more into it.
 * [#274](https://github.com/lviggiano/owner/pull/247): 
   Documentation for system:properties and system:env, Update importing-properties.md. 
 * [#246](https://github.com/lviggiano/owner/pull/246): Fixed doc typos & errors and improved readability.
 * [#242](https://github.com/lviggiano/owner/issues/242): FAQ broken link.
 * [#224](https://github.com/lviggiano/owner/pull/224): Adding some documentation for bug 
   [#184](https://github.com/lviggiano/owner/issues/184) (Maps with null values cause an unclear exception). 
 * Fixed Javadocs.
 * Updated documentation.
 
Bugs fixes
----------
 * [2479d47](https://github.com/lviggiano/owner/commit/2479d4718c5996e432f6cc0dedcbb4f250b29c43): decryption not working when used in combination with variable substitution
 * [0b2d209](https://github.com/lviggiano/owner/commit/0b2d209b0fe661a1596aa55921fff16e2ba5bc92): removed [double check locking] anti pattern.
 * [#227](https://github.com/lviggiano/owner/pull/227): Fixes properties issue in loading file URLs.
 * [#239](https://github.com/lviggiano/owner/pull/239): Allow property values to contain a '%' character without being a format string.
 * [#203](https://github.com/lviggiano/owner/issues/203), [#241](https://github.com/lviggiano/owner/pull/241): 
   ConcurrentModificationException on creating Config.
 * [#226](https://github.com/lviggiano/owner/issues/226), [#227](https://github.com/lviggiano/owner/pull/227):
   Empty system variables for file paths in @Sources cause URISyntaxException failures.
   Fixes properties issue in loading file URLs.
