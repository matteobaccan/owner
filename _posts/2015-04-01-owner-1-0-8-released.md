---
layout: news_item
title: "Owner 1.0.8 Released"
date: "2015-04-01 00:41:00 +0200"
author: lviggiano
version: 1.0.8
categories: [release]
---

<div class="note info">
  <h5>Release 1.0.7 failed deployment in Maven Central Repository</h5>
  <p>Some required pom was skipped, and if you try to use it as dependency in your project, it may raise some
     maven error or other issues. So here the hotfix: 1.0.8 is out!</p>
</div>

v1.0.8 contains following enhancements and bug fixes.

Enhancements
------------
 * Fixed the javadocs included in the tarballs/zips released.

Site Enhancements
-----------------
 * None.

Bugs fixes
----------
 * No `owner-parent` pom in Maven Central Repository. See [#121](https://github.com/lviggiano/owner/issues/121),
 thanks [@rajatvig](https://github.com/rajatvig) for quickly spotting the issue.

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-parent-1.0.8) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.8/).
