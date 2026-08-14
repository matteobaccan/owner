---
title: "Features"
---

> Simple things should be simple, complex things should be possible.
>
> — [Alan Kay](https://en.wikiquote.org/wiki/Alan_Kay) (Computer Scientist)

The design of the OWNER APIs has been developed with this motto in mind.

OWNER is a features rich API, but while adding more features, we wanted to keep the
[basic usages](/owner/docs/usage/) as simple as it could possibly be.

OWNER API supports a long list of features:

- [Key prefix](/owner/docs/key-prefix/)
- [Nested configuration](/owner/docs/nested-configuration/)
- [Loading strategies](/owner/docs/loading-strategies/)
- [Importing properties](/owner/docs/importing-properties/)
- [Parametrized properties](/owner/docs/parametrized-properties/)
- [Type conversion](/owner/docs/type-conversion/)
- [Variables expansion](/owner/docs/variables-expansion/)
- [Reload and Hot Reload](/owner/docs/reload/)
- [Accessible, Mutable and Traceable](/owner/docs/accessible-mutable/)
- [Debugging and sensitive values](/owner/docs/debugging/)
- [Disabling features](/owner/docs/disabling-features/)
- [Metaconfiguring](/owner/docs/configuring/)
- [File formats](/owner/docs/file-formats/)
- [Event support](/owner/docs/event-support/)
- [Singleton](/owner/docs/singleton/)
- [Bean Validation](/owner/docs/validation/) — `@Min`, `@NotNull` and the rest, checked on OWNER-style accessors too
- [Crypto support](/owner/docs/crypto/) — a value that names what decrypts it, with two ciphers shipped
- [Preprocessors](/owner/docs/preprocessors/)
- [JMX support](/owner/docs/jmx/)
- [GraalVM native image](/owner/docs/graalvm/) — what a native build needs, and why we ship none of it

The design of the new features is intended not to make existing things more complicated: you can pick what you need and
ignore what you don't need. Backward compatibility between versions is also in our goals.

If you need something special that is missing in OWNER, feel free to open a
[request](https://github.com/matteobaccan/owner/issues) for it.
We'll do our best to make OWNER do what you need.
