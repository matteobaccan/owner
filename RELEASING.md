RELEASING
=========

I'm writing this in order to keep some notes on how to release on maven central repository. Since this is for me, a real
nightmare.

After some time I am not releasing this project, things have changed and I forgot the intricate mechanism to deploy jars
on maven. Now I am trying again, and this time I will keep some notes for future releases here, hoping this will help
me, or anyone willing to contribute and help with the project development.

Today is 27th Feb 2018 and I am trying to release the new version, even though the documentation on the new features is
not ready, and I am against releasing something that is undocumented: if some feature is not documented, how this can be
used, when even documented features are not known enough well by users to raise questions?
But there are bug fixes and improvements that poeople can benefit, and I will follow up on the users' requests with this
release. And I hope to get back in development, if my life and my mood allows (and donations would help, thanks!).

RESOURCES
---------

Some links first, from the very source of the information, Sonatype:

- [OSSRH Guide][]
- [Apache Maven][]

  [OSSRH Guide]: http://central.sonatype.org/pages/ossrh-guide.html
  [Apache Maven]: http://central.sonatype.org/pages/apache-maven.html


SIGNING JARS
------------

In order to sign artifact jars, you need GnuPG.

So, from OS X:

```bash
$ sudo port selfupdate
$ sudo port -p upgrade outdated
$ sudo port install gnupg2
```

DEPLOY
------

This will upload snapshot artifacts to Sonatype.

```
$ mvn clean deploy
```

RELEASE
-------

This will upload release artifacts to Sonatype.

First you need to make sure all tests are passing and packages can be created without errors.


```
$ mvn clean install
```


First you need to remove the `-SNAPSHOT` thing:

```
$ mvn versions:set -DnewVersion=1.0.10
$ git commit -am "prepare release owner 1.0.10"
```


```
$ mvn clean deploy -P release-sign-artifacts
```
