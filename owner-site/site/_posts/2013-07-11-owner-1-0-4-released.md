---
layout: news_item
title: "Owner 1.0.4 Released"
date: "2013-07-11 21:02:13 +0200"
author: lviggiano
version: 1.0.4
categories: [release]
---

v1.0.4 contains some key enhancements and bug fixes.

Enhancements
------------

 * New `@ConverterClass` annotation.
   See [The @ConverterClass annotation]({{ site.url }}/docs/type-conversion/#the-converterclass-annotation), [#38][issue-38].
 * Hot reload for file based sources.
   See [Automatic "hot reload"]({{ site.url }}/docs/reload/#automatic-hot-reload), [#15][issue-15].
 * toString() method can be invoked on the Config object to get some useful text for debugging.
   See [The toString() method]({{ site.url }}/docs/debugging/#the-tostring-method), [#33][issue-33].
 * Added [`Mutable`][mutable-intf] interface for the methods giving *write* access to the underlying properties structure:
   setProperty, removeProperty, clear.
   See [The Mutable interface]({{ site.url }}/docs/accessible-mutable/#the-mutable-interface), [#31][issue-31].
 * Added [`Accessible`][accessible-intf] interface for the `list()` methods used to aid debugging, and other methods
   giving read access to the underlying properties structure.
   See [The Accessible interface]({{ site.url }}/docs/accessible-mutable/#the-accessible-interface).
 * Added the `reload()` method that can be exposed implementing the interface [`Reloadable`][reloadable-intf].
   See [Programmatic reload]({{ site.url }}/docs/reload/#programmatic-reload).
 * Fist class Java Arrays and Collections support in type conversion. Thanks [ffbit][].
   See [Arrays and Collections]({{ site.url }}/docs/type-conversion/#arrays-and-collections), [#21][issue-21], [#22][issue-22] and [#24][issue-24].
 * Implemented `@DisableFeature` annotation to provide the possibility to disable variable expansion and parametrized
   formatting.
   See [Disabling Features]({{ site.url }}/docs/disabling-features/), [#20][issue-20].

Site Enhancements
-----------------

 * New website for documentation.
 * Added [sonar](http://dev.aeonbits.org:9000) to keep high attention on code quality.
 * Added [Travis CI][travis-ci] to the project to track changes and run tests on different JDK versions.
 * Website code snippets now have syntax highlighting. Thanks [ming13][].


Bugs fixes
----------

 * Fixed bug [#40][issue-40] about tilde expansion.
 * Fixed bug [#17][issue-17] Substitution and format not working as expected when used together.

  [issue-21]: https://github.com/matteobaccan/owner/issues/21
  [issue-22]: https://github.com/matteobaccan/owner/issues/22
  [issue-24]: https://github.com/matteobaccan/owner/issues/24
  [issue-40]: https://github.com/matteobaccan/owner/issues/40
  [issue-38]: https://github.com/matteobaccan/owner/issues/38
  [issue-33]: https://github.com/matteobaccan/owner/issues/33
  [issue-17]: https://github.com/matteobaccan/owner/issues/17
  [issue-20]: https://github.com/matteobaccan/owner/issues/20
  [issue-31]: https://github.com/matteobaccan/owner/issues/31
  [issue-15]: https://github.com/matteobaccan/owner/issues/15
  [ffbit]: https://github.com/ffbit
  [ming13]: https://github.com/ming13
  [accessible-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html
  [reloadable-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Reloadable.html
  [mutable-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Mutable.html
  [travis-ci]: https://travis-ci.org/lviggiano/owner

