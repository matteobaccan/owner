---
layout: news_item
title: "Owner 1.0.10 Released"
date: "2018-03-01 13:05:00 +0200"
author: lviggiano
version: 1.0.10
categories: [release]
---

After long time (more than 2 years now), and many people asking for a new release, here we are. 
And here my apologies for the delay.
 
As you may know, I had serious health problems that kept me away from coding. Now my health is getting better, but I 
feel much slower in coding and using awesome tools like IntelliJ IDEA; in the meantime, my open source license has 
expired, so I hope the guys from JetBrains will be so nice to renew it :). 

Also, I always found the maven release process being cumbersome so that also kept me away from the effort. 
Now I took some time to simply it a little bit, and I kept some note for the future.

In this release, a huge amount of work has been conducted by contributors, and I mostly did housekeeping with 
refactoring, code review, enforcing quality standards, asking for documentation and tests, and integrating the great 
ideas coming from the users' community. 

I took back the project recently to upgrade it to have Java 9 support, and simplify release deployment, and only now 
that I am writing this release note, I realize how many things have been added and was waiting to be released. 

Documentation is very important; I hadn't had the chance to keep all in sync, so many things here need to be
documented. If you think you can help, feel free to help: this website is a sub-project 
[`owner-site`](https://github.com/lviggiano/owner/tree/master/owner-site), and uses Markdown language, which is very 
handy and quick to learn; the structure is quite easy to follow. 
[Jekyll](https://jekyllrb.com/) is used as site generator, which is written in Ruby and can be tricky for a Java dev 
like me, but it works awesomely with github. So feel free to help there too.  
There is also an [ant script](https://github.com/lviggiano/owner/blob/master/owner-site/build.xml) which allows 
you to launch Jekyll and live-preview the end result of your edits.

I don't feel very comfortable in making promises, but I'd really like to give back life to this project and, for the 
future, avoid such a long wait for a release.

Please notice that at the moment I am not professionally working, I closed my consultancy company years back, and
in this moment I am writing from a nice [Coworking Space "ImpactHub" here in Torino](https://torino.impacthub.net/).  
So, let me quickly say that [donations are very welcome](https://github.com/lviggiano/owner/#donations).   
Or if you want, you can hire me for some custom development on OWNER, training, or to help implementing your 
projects.  
This would definitely help keeping OWNER alive.

Credits to ALL the contributors of OWNER, and to the end-users of this neat library.  
To you all it goes my gratitude for this release.  

**Thank you!**

--Luigi.
  
   
*** 
  
  
RELEASE NOTES
=============

OWNER v1.0.10 contains following enhancements and bug fixes.

Enhancements
------------
 * Added Java 9 support, dropped Java 6 support. All code and tests are running and built with Java 9, so you can use
   OWNER with the latest Java version. It was not trivial. If you want to use some specific feature like default 
   methods in interfaces introduced in Java8, you still need to add `owner-java8` dependency. I know... I didn't want to
   create a new sub-module for Java 9 and every newer versions, if it's not necessary.
   Also, I updated all the dependencies (testing, and optional) and Maven plugins, in order to have it working 
   with Java 9.
   A huge thank you to my friend [@sbordet](https://github.com/sbordet).
 * Added `list()` method to `ConfigCache`. ConfigCache is a great way to centralise configuration for various
   parts of an application. This commit adds a list() method to the ConfigCache class, which lists the keys for all 
   configurations present in the cache. This allows the entire application configuration to be inspected (e.g. for 
   debugging) without the need for storing cache keys elsewhere. Thanks
    [@kevin-canadian](https://github.com/kevin-canadian), who also was so nice to update the documentation on the 
    website.
 * Added `@EncryptedValue` and `@DecryptorClass` annotations to allow hiding passwords stored in configuration
   properties. See [#49](https://github.com/lviggiano/owner/issues/49), thanks [@rrialq](https://github.com/rrialq) 
   for the implementation and the awesome documentation.
 * Added a Java 8 duration converter class: `DurationConverter.class` in `owner-java8-extras.jar` . 
   Thanks [@StFS](https://github.com/StFS).
 * Added system properties and enviroment variable as sources: example `@Sources({"system:properties", "system:env"})`. 
   See [#110](https://github.com/lviggiano/owner/issues/110). Thanks [@gintau](https://github.com/gintau) for the 
   implementation and [@kevin-canadian](https://github.com/kevin-canadian) for the idea.
 * Added `ByteSizeConverter` and `DurationConverter` classes in `owner-java8-extras` jar, see
   [#155](https://github.com/lviggiano/owner/issues/155). Thanks [@StFS](https://github.com/StFS), also for providing 
   the [necessary documentation](http://owner.aeonbits.org/docs/type-conversion/#byte-size) and unit tests.
 * Added the ability to register default converters for types and classes defined by users. 
   See [#184](https://github.com/lviggiano/owner/issues/184).
   Thanks [@StFS](https://github.com/StFS).
 * Added inheritance support for `@Sources`, `@LoadPolicy` and `@HotReload`.   
   Sources defined for all extended interfaces will be merged.
   LoadPolicy and HotReload can be inherited and override by the extended interface.
   Thanks [@chengmingwang](https://github.com/chengmingwang).

Bugs fixes
----------
 * Replaced `fixBackslashForRegex` with better implementation. Thanks [@kiefinger](https://github.com/kiefinger).
 * Have `ConfigFactory` throw an exception on imported Maps having either null keys or null values. 
   See [#185](https://github.com/lviggiano/owner/pull/185), [#184](https://github.com/lviggiano/owner/pull/184).
   Thanks [@StFS](https://github.com/StFS). 
 * Accept file URI containing spaces. Updated the uri processing to allow loading files that contain spaces in
   their paths. 
   See [#134](https://github.com/lviggiano/owner/issues/134). Thanks [@icirellik](https://github.com/icirellik).
 * Maps with null values cause an unclear exception. See [#184](https://github.com/lviggiano/owner/issues/184).
   Thanks [@StFS](https://github.com/StFS). 
 * Set tar long file mode to posix in maven assembly plugin to avoid build errors. 
   Thanks [@gdenning](https://github.com/gdenning).


Site Enhancements
-----------------
 * Added [Crypto support](http://owner.aeonbits.org/docs/crypto/) documentation page.
 * Added [ByteSize Converter](http://owner.aeonbits.org/docs/type-conversion/#byte-size) converter and
   [Duration Converter](http://owner.aeonbits.org/docs/type-conversion/#duration) documentation section.
   Thanks [@StFS](https://github.com/StFS). 
 * Chinese documentation has been contributed by [@cyfonly](https://github.com/cyfonly) and is available 
   [here](https://github.com/cyfonly/owner-doc). Sorry, I cannot check that everything is correct or update that! :-)  
   See [#172](https://github.com/lviggiano/owner/issues/172).
 * Added security/stability badges by [Meterian](https://www.meterian.com/). 
   Thanks [@fdiotalevi](https://github.com/fdiotalevi), [@bbossola](https://github.com/bbossola)

Downloadable artifacts are published on [GitHub](https://github.com/lviggiano/owner/releases/tag/owner-1.0.10) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.10/).


