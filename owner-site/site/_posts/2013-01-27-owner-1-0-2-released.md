---
layout: news_item
title: "Owner 1.0.2 Released"
date: "2013-01-27 21:02:13 +0200"
author: lviggiano
version: 1.0.2
categories: [release]
---

v1.0.2 contains some key enhancements and bug fixes:

 * Changed package name from `owner` to `org.aeonbits.owner`.
   Sorry to break backward compatibility, but this has been necessary in order to publish the artifact on Maven Central
   Repository.
 * Custom & special return types.
 * Properties variables expansion.
 * Added possibility to specify [Properties][properties] to import with the method `ConfigFactory.create()`.
 * Added list() methods to aide debugging. User can specify these methods in his properties mapping interfaces.
 * Improved the documentation (this big file that you are reading), and Javadocs.

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html

See [what's new][intr] and [what's new part 2][intr-2] articles (most of them applies to 1.0.3 and 1.0.2 as well) for
more information on this release.

 [intr]: http://en.newinstance.it/2013/02/04/owner-1-0-3-whats-new-part-1-variable-expansion/
 [intr-2]: http://en.newinstance.it/2013/05/29/owner-1-0-3-whats-new-part-2/
