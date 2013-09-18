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

 * Allow `@ConverterClass` annotation to override default converters (i.e. primitive types, etc)
 * Added event support for property changes. (See [#47](https://github.com/lviggiano/owner/issues/47)).
 * Website reorganized, moved from `gh-pages` trunk to `master`, with publish ant scripts `build.xml`.
 * Added method `Set<String> propertyNames()` in the `Accessible` interface.
  (See [#46](https://github.com/lviggiano/owner/issues/46)).
 * Support for "classpath:" URLs in HotReload. Also it works with the default files associated to the mapping
   interface, when `@Sources` is not specified.
 * Support for XML. OWNER is now able to load not only from properties files, but also from XML files. The XML
   can follow the [Java XML Properties format](http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html),
   or can be freely defined by the user.<br/>
   See [XML support]({{ site.url }}/docs/xml-support/), [#5](https://github.com/lviggiano/owner/issues/5)).


Bugs fixes
----------

 * Fixed bug [#42](https://github.com/lviggiano/owner/issues/42), regarding the incompatibility of the OWNER
   library with the Google App Engine security restrictions.


