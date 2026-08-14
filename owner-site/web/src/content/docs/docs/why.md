---
title: "Why should I use OWNER?"
---


The reason why OWNER was written is because the code dealing with the configuration is frequently repetitive,
redundant, it's made of boring and ugly static classes, singletons, long list of methods just doing conversion from a
string property to a named method returning a Java primitive or a basic Java object.

I don't want to provide negative examples, but just have a look around on how java.util.Properties is used.
The main reason why I wrote this library is to get rid of that ugly code.

OWNER solves the problem providing an interface object that

 - it's easy to mock (see [Mockito](http://mockito.org)), easy to pass to other objects (via dependency injection).
 - declaratively maps your configuration without any redundancy.
 - can easily expand your loading logic in order to have multiple configuration files, multiple level of
   overriding (global configuration, user-level, defaults, etc).
 - doesn't need to have an actual properties file backing your configuration, if you use `@DefaultValue`.
 - provides a lot of features, like hot reloading, variables expansion, etc.
 - leaves you free to do everything you are already doing with java.util.Properties.
 - does support a super powerful [type conversion](/owner/docs/type-conversion/), which includes arrays,
   collections, many standard Java objects, and even the possibility to plug your own conversion logic.
 - it does not bring any transitive dependency to your project, so no conflicting jars, no dependency issues.
 - it is developed to work with any reasonably recent JDK. Each commit on OWNER repository is
   [tested](https://github.com/matteobaccan/owner/actions) on all the supported LTS JDKs (11, 17, 21 and 25),
   while the produced bytecode remains compatible with Java 8 at runtime.

...and more than everything, it's not an ugly, rigid, boring, repetitive list of methods doing all the same thing.

What you will not find easily elsewhere
---------------------------------------

The Java ecosystem has no shortage of configuration libraries, and several of them read a value out of a
file perfectly well. What follows is not a comparison — those age badly, and the ones written about OWNER
still recommend projects that stopped being maintained years ago — but the short list of things this
library does that turn out to be genuinely rare, each of them documented in the chapter it belongs to.

**A reload you can refuse.** A
[`TransactionalReloadListener`](/owner/docs/event-support/) is consulted *before* a reload is
applied and can reject it, rolling the configuration back to the state it had. Plenty of libraries reload
and then notify you; being able to say "no, this configuration is not valid, keep the previous one" is
another matter, and it is the difference between a bad file taking down a running service and being
refused at the door.

**Reading is only half of it.** A configuration object can be
[`Mutable`](/owner/docs/accessible-mutable/): set and remove properties, and store the result back
to a file or a stream. Most configuration libraries are a one-way street from the file into your program.

**Nothing comes with it.** The `owner` artifact has no runtime dependency at all — not a logging facade,
not a collections library, nothing — so it cannot conflict with anything you already use. The optional
extras that do need a third party library are in a separate artifact, and even there the dependency is
declared optional.

**It runs where your code runs.** Java 8 is the minimum, and it is a deliberate position rather than
neglect: the build runs on the current LTS releases and the bytecode stays compatible with 8, so a project
that has not moved yet is not left without an option.

**Kilobytes and kibibytes are not the same thing.** The
[byte size support](/owner/docs/type-conversion/) distinguishes the SI and the IEC families, so
`KB` is a thousand bytes and `KiB` is 1024, each named for what it is. It is a small thing until the day
the size in your configuration file and the size your monitoring reports disagree by 2.4%.

**A password in a configuration file, without writing the cipher yourself.** Since 2.0.0 a value can name
what decrypts it — `db.password=${$aes-gcm::…}` — and the [cipher is shipped](/owner/docs/crypto/):
AES-256/GCM over PBKDF2, in the core jar, with no dependency and no framework. Or a key pair, so that
whoever adds a secret to the file cannot read the ones already there, which no other Java configuration
library offers without a server. Values that refer to it get the secret, `store()` writes the marker back,
and the tool that produces one is in the same jar.

**The things you would otherwise write by hand.** Properties
[required](/owner/docs/usage/#mandatory-properties) to be present, checked when the object is
created rather than when it is first read; passwords
[kept out of your logs](/owner/docs/debugging/); a configuration
[exposed over JMX](/owner/docs/jmx/) and editable at runtime; keys and values
[computed from other keys](/owner/docs/variables-expansion/), at any depth.

None of this is compulsory. The chapter on [features](/owner/docs/features/) exists because you
can pick what you need and ignore the rest: the two line example at the top of this site is still the
whole of what most projects ever use.
