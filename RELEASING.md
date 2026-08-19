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

BEFORE 2.0.0: THE PUBLISHING TARGET HAS MOVED
---------------------------------------------

**The procedure below has not been run since 1.0.12, in June 2020, and it very
likely no longer works as written.** Deal with this before starting a release,
not in the middle of one.

Sonatype retired OSSRH — the `oss.sonatype.org` Nexus this project deploys to —
and moved publishing to the Central Portal at `central.sonatype.com`, which
speaks a different API. Three things in `pom.xml` still point at the old one:

- `distributionManagement/snapshotRepository`, at
  `https://oss.sonatype.org/content/repositories/snapshots/`
- `distributionManagement/repository`, at
  `https://oss.sonatype.org/service/local/staging/deploy/maven2/`
- `nexus-staging-maven-plugin`, whose `nexusUrl` is `https://oss.sonatype.org/`
  and which the Central Portal does not support at all — the replacement is
  `central-publishing-maven-plugin`

Check the current Sonatype documentation rather than trusting this note: the
migration has its own deadlines and its own account steps, and what is required
may have moved again since this was written.

`distributionManagement/site` used to sit beside those, deploying over FTP to
`newinstance.it` — a host this project does not control either. **Removed on
2026-08-19**, along with the `maven-site-plugin` execution that ran `site` and
`stage` on every `deploy`: the Maven site published nothing that is not already
on the documentation site, in the Javadoc, in the Actions logs or on SonarCloud.
`mvn site` still works by hand, and the plugin's version is still pinned for it.

BEFORE A MAJOR: RUN THE PREVIOUS RELEASE'S TESTS AGAINST THE NEW CODE
--------------------------------------------------------------------

The project's own suite says the library does what *this* version promises. It cannot say what an upgrade
costs, having been written alongside the code it tests. **The suite of the previous release can**: every one
of its assertions is a promise that version made to somebody, so whatever it refuses is a promise this one
takes back — intentionally or otherwise, and telling those two apart is the point.

Done for the first time on 2026-08-18, 1.0.12 against 2.0.0: **216 tests, 212 green**, and the paragraph
that went into the release notes out of it is under *Why 2.0.0*. The four were two things — two were a
feature of 2.0.0 doing its job, and two were a Mockito spy on `java.util.Properties`, whose internals a spy
does not carry on a current JDK.

```bash
# 1. the version being released, into the local repository
mvn -DskipTests -pl owner install

# 2. the previous release's tests and resources, into a scratch directory
git archive owner-1.0.12 owner/src/test | tar -x -C /tmp/compat

# 3. a pom in /tmp/compat with NO main sources: a dependency on the version from step 1,
#    plus exactly the test dependencies that release declared - for 1.0.12 they were
#    hamcrest-all 1.3, junit 4.12, mockito-core 2.23.4, commons-codec 1.11.
#    Set testFailureIgnore so that one broken class does not hide the rest.

# 4. run it
cd /tmp/compat && mvn test
```

Three things to know before reading the output, each of which cost an hour the first time:

- **separate the two kinds of failure.** A test that does not *compile* because it uses an internal is a
  different fact from a test that runs and disagrees. Only the second is a compatibility break; the first
  is a removal, and it should already be in the release notes.
- **adapt the helpers, not the tests.** In 1.0.12 `UtilTest` doubles as a bag of test utilities that
  delegate to `Util`, so reimplementing four of them on the JDK methods that replaced them — `Properties.store`,
  `JarOutputStream`, `Files`, `Objects.equals` — brought back the seventy-odd behavioural tests that only
  ever used them to write a file.
- **do not commit the harness.** It is a copy of another release's tests. What belongs in the repository is
  the paragraph in the release notes and this section.

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

Nothing to do: the **Publish Javadoc** workflow builds the aggregate Javadoc and
pushes it onto `gh-pages` itself, into `apidocs/latest/` on every push to master
that touches the published sources, and into `apidocs/<tag>/` as well when a
release is published.

It used to be published by hand, with `ant javadoc publish` from `owner-site`.
That is why the published Javadoc sat on 1.0.12 from June 2020 until August 2026,
describing a library that had moved on without it: a release step that depends on
somebody remembering is a release step that stops happening. The ant script is
gone, so that the directory has one owner.
