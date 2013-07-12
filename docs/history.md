---
layout: docs
title: History
prev_section: contributing
next_section: license
permalink: /docs/history/
---

## 1.0.4 / 11-Jul-2013

 * Fixed bug [#40][issue-40] about tilde expansion. 
 * New `@ConverterClass` annotation. See [#38][issue-38].
 * New website for documentation.
 * Added [sonar](http://sheldon.dyndns.tv:9000) to keep high attention on code quality.
 * Hot reload for file based sources. See [#15][issue-15].
 * toString() method can be invoked on the Config object to get some useful text for debugging. See [#33][issue-33].
 * Added [`Mutable`][mutable-intf] interface for the methods giving *write* access to the underlying properties structure:
   setProperty, removeProperty, clear. See [#31][issue-31].
 * Added [`Accessible`][accessible-intf] interface for the `list()` methods used to aid debugging, and other methods
   giving read access to the underlying properties structure.
 * Added the `reload()` method that can be exposed implementing the interface [`Reloadable`][reloadable-intf].
 * Added [Travis CI][travis-ci] to the project to track changes and run tests on different JDK versions.
 * Fist class Java Arrays and Collections support in type conversion. Thanks [ffbit][].
 * Implemented `@DisableFeature` annotation to provide the possibility to disable variable expansion and parametrized
   formatting. See [#20][issue-20].
 * Website code snippets now have syntax highlighting. Thanks [ming13][].
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

## 1.0.3.1 / 26-Jun-2013

 * Fixed bug [#35](https://github.com/lviggiano/owner/issues/35)

## 1.0.3 / 03-Feb-2013

 * Fixed incompatibility with JRE 6 (project was compiled using JDK 7 and in some places I was catching
   ReflectiveOperationException that has been introduced in JDK 7).
 * Minor code cleanup/optimization.

## 1.0.2 / 27-Jan-2013

 * Changed package name from `owner` to `org.aeonbits.owner`.
   Sorry to break backward compatibility, but this has been necessary in order to publish the artifact on Maven Central
   Repository.
 * Custom & special return types.
 * Properties variables expansion.
 * Added possibility to specify [Properties][properties] to import with the method `ConfigFactory.create()`.
 * Added list() methods to aide debugging. User can specify these methods in his properties mapping interfaces.
 * Improved the documentation (this big file that you are reading), and Javadocs.

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html

## 1.0.1 / 27-Dec-2012

 * Removed [commons-lang][] transitive dependency. Minor bug fixes.

  [commons-lang]: http://commons.apache.org/lang/

## 1.0 / 24-Dec-2012

 * Initial release.
