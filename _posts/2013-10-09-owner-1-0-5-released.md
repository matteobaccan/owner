---
layout: news_item
title: "Owner 1.0.5 Released"
date: "2013-10-09 16:25:00 +0200"
author: lviggiano
version: 1.0.5
categories: [release]
---

v1.0.5 contains following enhancements and bug fixes.

Enhancements
------------

 * [Support for XML]({{site.url}}/docs/xml-support/).
   OWNER is now able to load not only from properties files, but also from XML files. The XML
   can follow the [Java XML Properties format](http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html),
   or can be freely defined by the user.<br/>
   (See more in the documentation: [XML support]({{site.url}}/docs/xml-support/) and see
   [#5](https://github.com/lviggiano/owner/issues/5)).
 * Added method `registerLoader()` to `ConfigFactory`, so the user can define new loaders for more file formats.<br/>
   (See [#55](https://github.com/lviggiano/owner/issues/55)).
 * Support for `classpath:` URLs in HotReload. Also it works with the default files associated to the mapping
   interface, when `@Sources` is not specified.
 * Added method `Set<String> propertyNames()` in the `Accessible` interface.<br/>
   (See [#46](https://github.com/lviggiano/owner/issues/46)).
 * Added [Event support]({{site.url}}/docs/event-support/) for property changes and reload.
   Both the events can now be transactional: the listener can be notified by an event before and after a property change
   or a reload takes place. The listener can check what is changed and eventually rollback the reload or property change
   operation.<br/>
   (See more in the documentation: [Event support]({{site.url}}/docs/event-support/) and see
   [#47](https://github.com/lviggiano/owner/issues/47)).
 * Added non-static `ConfigFactory`, so one can create independent instances of OWNER `Factory` objects.<br/>
   (See [#43](https://github.com/lviggiano/owner/issues/43)).
 * Added implementation on `hashCode()` and `equals()`.
 * Added serialization capability to OWNER `Config` objects, so now they can be transferred through the network or
   transformed to byte streams. <br/>
   (See [#54](https://github.com/lviggiano/owner/issues/54)).
 * Allow `@ConverterClass` annotation to override default converters (i.e. primitive types, etc).
 * The interfaces `Reloadable`, `Mutable` and `Accessible` now extend from `Config`, so you don't need anymore to extend
   directly from Config. For instance, your interface can now extend just from Mutable to generate an object which is
   also a valid `Config` object that can be instantiated by the `ConfigFactory`:<br/><br/>
   ![config-hierarchy]({{site.url}}/img/config-hierarchy.png)


Site Enhancements
-----------------
 * Website sources reorganized: moved from `gh-pages` branch to `master`, with publish ant scripts `build.xml`.
 * Added news section, with release announcements and blog posts.

Bugs fixes
----------

 * Fixed bugs on tests that were making the build failing on Windows systems.
 * Fixed bug [#51](https://github.com/lviggiano/owner/pull/51), variables expansion, and path expansion not working
   properly with string containing the backslash characters `'\'`. <br/>
   Thanks [NiXXeD](https://github.com/NiXXeD).
 * Fixed bug [#42](https://github.com/lviggiano/owner/issues/42), regarding the incompatibility of the OWNER
   library with the Google App Engine security restrictions.

Downloadable artifacts are published on
[Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner/1.0.5/).
