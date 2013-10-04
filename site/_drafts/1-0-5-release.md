---
layout: news_item
title: "Owner 1.0.5 Released"
author: lviggiano
version: 1.0.5
categories: [release]
---

v1.0.5 contains following enhancements and bug fixes.

Enhancements
------------

 * Support for XML. OWNER is now able to load not only from properties files, but also from XML files. The XML
   can follow the [Java XML Properties format](http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html),
   or can be freely defined by the user.<br/>
   See [XML support]({{ site.url }}/docs/xml-support/), [#5](https://github.com/lviggiano/owner/issues/5)).
 * Added method `registerLoader()` to `ConfigFactory`, so the user can define new loaders for more file formats.
   (See [#55](https://github.com/lviggiano/owner/issues/55)).
 * Support for "classpath:" URLs in HotReload. Also it works with the default files associated to the mapping
   interface, when `@Sources` is not specified.
 * Added method `Set<String> propertyNames()` in the `Accessible` interface.
  (See [#46](https://github.com/lviggiano/owner/issues/46)).
 * Added event support for property changes. The user can now be notified by an event when a property changes or when
   a reload happens. An event is issued before and after a property changes or a reload event occurs, and the user
   can also intervene to check what is changed and eventually rollback the reload or property change operation.
   See more in the documentation. (And see [#47](https://github.com/lviggiano/owner/issues/47)).
 * Added non-static Config Factory, so one can create independent instances of Factory objects.
   (See [#43](https://github.com/lviggiano/owner/issues/43)).
 * Added implementation on `hashCode()` and `equals()`.
 * Added serialization capability to OWNER `Config` objects, so now they can be transferred through the network or
   transformed to byte streams. (See [#54](https://github.com/lviggiano/owner/issues/54)).
 * Allow `@ConverterClass` annotation to override default converters (i.e. primitive types, etc).
 * The interfaces `Reloadable`, `Mutable` and `Accessible` now extend from `Config`, so you don't need anymore to extend
   directly from Config. For instance, your interface can now extend just from Mutable to generate an object which is
   Mutable, Reloadable and a valid Config object.

Site Enhancements
-----------------
 * Website sources reorganized: moved from `gh-pages` trunk to `master`, with publish ant scripts `build.xml`.
 * Added news section, with release announcements and blog posts.

Bugs fixes
----------

 * Fixed bugs on tests that were making the build failing on Windows systems.
 * Fixed bug [#51](https://github.com/lviggiano/owner/pull/51), variables expansion, and path expansion not working
   properly with string containing the backslash characters `'\'`. Thanks [NiXXeD](https://github.com/NiXXeD).
 * Fixed bug [#42](https://github.com/lviggiano/owner/issues/42), regarding the incompatibility of the OWNER
   library with the Google App Engine security restrictions.


