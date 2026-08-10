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

You can download GnuPG from [here](https://gnupg.org/download/) or try MacPorts; I've had issues with brew version of GnuPG on OS X so my recommendation is to download the most updated binaries built for OS X; that at the time of writing is from sourceforge project [gpgosx](https://sourceforge.net/p/gpgosx/docu/Download/).

RELEASE PROCEDURE
-----------------

This will upload release artifacts to Sonatype.

First you need to make sure all tests are passing and packages can be created without errors.

```bash
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

SITE DEPLOY
-----------

The site is an [Astro](https://astro.build) project using the
[Starlight](https://starlight.astro.build) documentation theme, in `owner-site/web`.
It needs Node, and nothing else — no Ruby, no Jekyll.

```bash
$ cd owner-site/web
$ npm install
$ npm run dev            # local preview on http://localhost:4321/owner/
$ npm run build          # writes the static site to dist/
$ npm run check:external # fails if anything would load from a foreign host
```

Note the `/owner/` in the preview URL: it mirrors the path the site is served
from in production, so the links you see locally are the real ones.

**You do not publish the site by hand.** The "Publish site" GitHub Actions
workflow builds it and syncs it onto the `gh-pages` branch on every push to
master that touches `owner-site/web`, and it can also be run from the Actions
tab. It refuses to publish a build that would load a resource from an external
host.

The sync deletes whatever the build no longer produces, so that stale files do
not accumulate on the branch, but it leaves `apidocs/` and `.well-known/` alone:
the first is published separately (below), the second holds a verification file
that is not ours to regenerate.

### Javadoc

The Javadoc *is* published by hand, with ant, and it is the only thing that ant
publishes:

```bash
$ cd owner-site
$ ant javadoc publish
```

That generates the aggregate Javadoc and pushes it to `apidocs/latest/` on
`gh-pages`, using the git command line tools, which have to be installed.
