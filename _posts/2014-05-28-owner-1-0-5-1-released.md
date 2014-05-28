---
layout: news_item
title: "Owner 1.0.5.1 Released"
date: "2014-05-28 12:45:00 +0200"
author: lviggiano
version: 1.0.5.1
categories: [release]
---

v1.0.5.1 contains following enhancements and bug fixes.

Enhancements
------------
 * Java8 fixes, so now it is officially supported.
 * Added UTF-8 Support for properties files. (See [#77](https://github.com/lviggiano/owner/issues/77) and
   [#78](https://github.com/lviggiano/owner/issues/78), thanks [@SvetaNesterenko](https://github.com/SvetaNesterenko) )
 * Added [ConfigCache (Singleton)]({{ site.url }}/docs/singleton/) feature. (See [#64](https://github.com/lviggiano/owner/issues/64))
 * Improved support for Android. Somebody wants to verify/help with this? (See [#75](https://github.com/lviggiano/owner/issues/75))
 * Implemented variable expansion for `@Key` annotation. (See [#63](https://github.com/lviggiano/owner/issues/63))
 * Restructured maven project to allow sub-modules.

Site Enhancements
-----------------
 * Dyndns dropped free service, so updated links for Sonar, from sheldon.dyndns.tv ->  dev.aeonbits.org
 * Documentation website minor style/layout, updates and improvements.
 * Added [SlideShare presentation](http://www.slideshare.net/LuigiViggiano/owner-31716769) in home page.
 * Added [Coveralls](https://coveralls.io/r/lviggiano/owner). (See [#59](https://github.com/lviggiano/owner/issues/59))

Bugs fixes
----------
 * Code cleanup, removed warnings.
 * Fixed compatibility issue on exception raised by Java7 and Java6. (See [#71](https://github.com/lviggiano/owner/issues/71))

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-1.0.5.1) and on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner/1.0.5.1/).
