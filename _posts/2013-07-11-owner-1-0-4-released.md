---
layout: news_item
title: "Owner 1.0.4 Released"
date: "2013-07-11 21:02:13 +0200"
author: lviggiano
version: 1.0.4
categories: [release]
---

v1.0.4 contains some key enhancements and bug fixes:

 * Fixed bug [#40][issue-40] about tilde expansion. 
 * New `@ConverterClass` annotation. See [#38][issue-38].
 * Hot reload for file based sources. See [#15][issue-15].
 * toString() method can be invoked on the Config object to get some useful text for debugging. See [#33][issue-33].
 * Added [`Mutable`][mutable-intf] interface for the methods giving *write* access to the underlying properties structure:
   setProperty, removeProperty, clear. See [#31][issue-31].
 * Added [`Accessible`][accessible-intf] interface for the `list()` methods used to aid debugging, and other methods
   giving read access to the underlying properties structure.
 * Added the `reload()` method that can be exposed implementing the interface [`Reloadable`][reloadable-intf]. At the 
   current time I am thinking to merge this interface with `Mutable`, before releasing, but that reloading() is a 
   different operation and purpose than programmatically alter things... so for now, it's here.
 * Fist class Java Arrays and Collections support in type conversion. Thanks [ffbit][].
 * Implemented `@DisableFeature` annotation to provide the possibility to disable variable expansion and parametrized
   formatting. See [#20][issue-20].
 * Fixed bug [#17][issue-17] Substitution and format not working as expected when used together.
 
  [issue-40]: https://github.com/lviggiano/owner/issues/40
  [issue-38]: https://github.com/lviggiano/owner/issues/38
  [issue-33]: https://github.com/lviggiano/owner/issues/33
  [issue-17]: https://github.com/lviggiano/owner/issues/17
  [issue-20]: https://github.com/lviggiano/owner/issues/20
  [issue-31]: https://github.com/lviggiano/owner/issues/31
  [issue-15]: https://github.com/lviggiano/owner/issues/15
  [ffbit]: https://github.com/ffbit
  [ming13]: https://github.com/ming13
  [travis-ci]: https://travis-ci.org/lviggiano/owner
  [accessible-intf]: http://owner.newinstance.it/latest/apidocs/org/aeonbits/owner/Accessible.html
  [reloadable-intf]: http://owner.newinstance.it/latest/apidocs/org/aeonbits/owner/Reloadable.html
  [mutable-intf]: http://owner.newinstance.it/latest/apidocs/org/aeonbits/owner/Mutable.html

See the [History](/docs/history/) page for more information on this release.
