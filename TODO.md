TODO LIST
=========

This file is used to keep track of things that should be done, sometime next.
For bugs and features request please see [GitHub issues](https://github.com/matteobaccan/owner/issues).

WEBSITE
-------

- [x] Write documentation for pre processing feature (see [Preprocessors](https://matteobaccan.github.io/owner/docs/preprocessors/))
- [x] Write documentation for JMX support (see [JMX support](https://matteobaccan.github.io/owner/docs/jmx/))
- [ ] Update the release note: a draft for 2.0.0 is ready in `owner-site/site/_drafts/owner-2-0-0-released.md`,
      to be moved into `_posts/` (with the release date in the file name and front matter) when 2.0.0 is published.

CODE
----

- [ ] **Break the package cycle between `org.aeonbits.owner` and `org.aeonbits.owner.converters`.**
      `Converters.DURATION` imports `DurationConverter`, which implements `Converter` from the parent
      package, so the two now point at each other. It compiles, bundles and modularises fine — one
      artifact, one bundle, one module — but it welds the two packages together permanently and static
      analysers report the cycle. The fix reverses the direction rather than removing anything: move the
      parsing into a package-private class of the core and leave `DurationConverter` as the public
      adapter that delegates to it. `parseDuration` is already private, so no public API changes, and the
      existing tests cover it.
- [ ] Support for further formats — YAML, TOML, JSON — as optional `Loader`s in `owner-extras`, with
      `ServiceLoader` discovery. See issues [#14](https://github.com/matteobaccan/owner/issues/14),
      [#65](https://github.com/matteobaccan/owner/issues/65) and
      [#240](https://github.com/matteobaccan/owner/issues/240), and the reasoning in `COMPARISON.md`.
