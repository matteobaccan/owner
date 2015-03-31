---
layout: news_item
title: "Owner 1.0.7 Released"
date: "2015-03-30 22:30:00 +0200"
author: lviggiano
version: 1.0.7
categories: [release]
---

<div class="note warning">
  <h5>Release 1.0.7 failed deployment in Maven Central Repository</h5>
  <p>Some required pom was skipped, and if you try to use it as dependency in your project, it may raise some
     maven error or other issues. So, avoid using 1.0.7 and jump to 1.0.8!</p>
</div>

v1.0.7 contains following enhancements and bug fixes.

Enhancements
------------
 * Added JMX Support. See [#107](https://github.com/lviggiano/owner/pull/107) and
   [#19](https://github.com/lviggiano/owner/issues/19).
   Thanks [@robinmeiss](https://github.com/robinmeiss).
   I still need to write the documentation on how to use it (sorry).
 * Added examples module, containing some example Maven Java projects to show some of the API features.
   This gets packaged in the [released archive artifacts (zip and tarballs)](https://github.com/lviggiano/owner/releases/tag/owner-parent-1.0.7).

Site Enhancements
-----------------
 * None.

Bugs fixes
----------
 * Fixed packaging: the `owner-extras.jar` was missing required classes.
   See [#114](https://github.com/lviggiano/owner/issues/114). Thanks [@ksaritek](https://github.com/ksaritek) for the patience.

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-parent-1.0.7) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.7/).
