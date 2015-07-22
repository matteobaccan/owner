---
layout: news_item
title: "Owner 1.0.9 Released"
date: "2015-07-22 17:17:00 +0200"
author: lviggiano
version: 1.0.9
categories: [release]
---

v1.0.9 contains following enhancements and bug fixes.

Enhancements
------------
 * Added `fill(java.util.Map)` method to the `Accessible` interface.
 * Added pre-processing feature. See [#120](https://github.com/lviggiano/owner/issues/120), thanks
   [@a1730](https://github.com/a1730) for the feedback.

Site Enhancements
-----------------
 * None.

Bugs fixes
----------
 * Config.Sources with ~ doesn't create a valid URI on Windows.
   See [#123](https://github.com/lviggiano/owner/issues/123), thanks [@outofrange](https://github.com/outofrange) for
   spotting this bug.

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-parent-1.0.9) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.9/).
