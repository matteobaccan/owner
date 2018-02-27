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