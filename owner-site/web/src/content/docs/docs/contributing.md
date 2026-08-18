---
title: "Contributing"
---

So you've got an awesome idea to throw into OWNER.
Great!

There are many ways to help improving OWNER:

1. If you want to implement some change, you can
   [fork the project on GitHub][fork] then send me a pull request.
   See the [workflow](#workflow).
2. If you have some idea, you can submit it as change request on
   [GitHub issues][issues].
3. If you've found some defect, you can submit the bug on
   [GitHub issues][issues].
4. If you want to help the development, you can pick a
   [bug or an enhancement][issues] then contribute your patches following
   the [workflow](#workflow).
5. Also you can contribute on improving the documentation.
   See [Updating Documentation](#updating-documentation).

  [fork]: https://help.github.com/articles/fork-a-repo
  [issues]: https://github.com/matteobaccan/owner/issues
  [collaborating]: https://help.github.com/categories/63/articles


And Please keep the following in mind:

* If you're creating a small fix or patch to an existing feature, just a simple
  test will do. Please stay in the confines of the current test suite.
* Also, some help on documentation would be appreciated. Documentation sources
  can be found on the site folder of the master branch.
  Great docs make a great project!
* Please follow the project code style.

<div class="note warning">
  <h5>Contributions will not be accepted without tests.</h5>
</div>


Workflow
--------

Here's the most direct way to get your work merged into the project:

* Fork the project.
* Clone down your fork:

```bash
git clone git://github.com/<your-username>/owner.git
```

* Create a topic branch to contain your change:

```bash
git checkout -b my_awesome_feature
```


* Hack away, add tests. Not necessarily in that order.
* Make sure everything still passes by running `mvn test`.
* If necessary, rebase your commits into logical chunks, without errors.
* Push the branch up:

```bash
git push origin my_awesome_feature
```

* Create a pull request against matteobaccan/owner and describe what your change
  does and the why you think it should be merged.

### Seeing what the analysers say, without an account

Every push is read by SonarCloud, CodeQL and Codacy, and a pull request shows their verdict. For
SonarCloud you do not need an account to see *what* it found: the project is public and so is its issue
API, which answers plain JSON —

```bash
curl "https://sonarcloud.io/api/issues/search?componentKeys=matteobaccan_owner&issueStatuses=OPEN&ps=100"
```

Add `&rules=java:S1128` for one rule at a time. Each finding carries the file, the line and the message,
which is worth having before changing anything: a rule rarely flags what its title suggests. `java:S1128`,
*"unnecessary imports"*, turned out to be seventeen imports of `Config.Key` and its neighbours inside
interfaces that **extend** `Config` — where the nested annotation is already in scope, so the import is
redundant although the name does appear in the file.

Updating Documentation
----------------------

We want the OWNER documentation to be the best it can be. We've
open-sourced our docs and we welcome any pull requests if you find it
lacking.

You can find the documentation for matteobaccan.github.io/owner in the
[owner-site/site](https://github.com/matteobaccan/owner/tree/master/owner-site/site) folder of the
master branch.

All documentation pull requests should be directed at `master`.  Pull
requests directed at another branch will not be accepted.

The [OWNER wiki](https://github.com/matteobaccan/owner/wiki) on GitHub
can be freely updated without a pull request as all GitHub users have
read and write access.
