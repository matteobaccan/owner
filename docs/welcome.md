---
layout: docs
title: Welcome
next_section: installation
permalink: /docs/welcome/
---

This site aims to be a comprehensive guide to OWNER. We'll cover topics such as
getting started with the basic features, the advanced usages, and give you some
advice on participating in the future development of OWNER itself.


So what is OWNER, exactly?
--------------------------

The OWNER API is a Java library with the goal of minimizing the code required to
handle application configuration via Java properties files.

OWNER is shipped as a JAR file, and it can also be downloaded from the Maven
Central Repository.

The inspiring idea for this API comes from GWT i18n (see [here][gwt-i18n]).
The problem in using GWT i18n for loading property files is that it only works
in client code (JavaScript), not standard Java classes.  
Also, GWT is a big library and it is designed for different purposes, than
configuration.  
Since I liked the approach I decided to implement something similar, and here
we are.

  [gwt-i18n]: https://developers.google.com/web-toolkit/doc/latest/DevGuideI18nConstants


Tips, Notes, and Warnings
-------------------------

Throughout this guide there are a number of small-but-handy pieces of
information that can make using OWNER easier, more interesting, and less
hazardous. Here’s what to look out for.

<div class="note">
  <h5>Tips help you get more from OWNER</h5>
  <p>These are tips and tricks that will help you be a OWNER wizard!</p>
</div>

<div class="note info">
  <h5>Notes are handy pieces of information</h5>
  <p>These are for the extra tidbits sometimes necessary to understand
     OWNER.</p>
</div>

<div class="note warning">
  <h5>Warnings help you not blow things up</h5>
  <p>Be aware of these messages if you wish to avoid certain death.</p>
</div>

<div class="note unreleased">
  <h5>You'll see this by a feature that hasn't been released</h5>
  <p>Some pieces of this website are for future versions of Jekyll that
    are not yet released.</p>
</div>

If you come across anything along the way that we haven’t covered, or if you
know of a tip you think others would find handy, please [file an
issue](https://github.com/lviggiano/owner/issues/new) and we’ll see about
including it in this guide.
