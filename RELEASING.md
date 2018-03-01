RELEASING
=========

I'm writing this in order to keep some notes on how to release on maven central repository.

After some time I am not releasing this project, things have changed and I forgot the intricate mechanism to deploy jars
on Maven/Sonatype. Now I am trying again, and this time I will keep some notes for future releases here, hoping this 
will help.

RESOURCES
---------

Some links first:

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

RELEASE PROCEDURE
-----------------

This will upload release artifacts to Sonatype.

First you need to make sure all tests are passing and packages can be created without errors.


```
# Check that everything builds smoothly and tests are all passing
$ mvn clean install                               

# First you need to remove the `-SNAPSHOT` thing and commit on git
$ mvn versions:set -DnewVersion=1.0.10            
$ mvn versions:commit
$ git commit -am "prepare release owner 1.0.10"
$ git tag owner-1.0.10
$ git push origin owner-1.0.10:owner-1.0.10

# Deploy the signed jars on Sonatype
$ mvn clean deploy -P release-sign-artifacts

# Prepare for next development iteration
$ mvn versions:set -DnewVersion=1.0.11-SNAPSHOT
$ mvn versions:commit
$ git commit -am "prepare for next development iteration"
$ git push
```

That should do.

Maybe I should script this, not urgent anyway since releasing is not a daily routine.