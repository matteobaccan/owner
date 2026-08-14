---
title: "GraalVM native image"
---

OWNER works in a native image. It needs reachability metadata to do so, because it builds your
configuration object as a **JDK dynamic proxy** over your interface, and static analysis cannot see a
class that does not exist until run time.

That is not particular to this library — it is what every proxy-based library needs — and GraalVM has
supported dynamic proxies through metadata for years.

<div class="note warning">
  <h5>Written from what the library does, not from a native image we built.</h5>
  <p>
    There is no native image in this project's continuous integration, so this chapter is derived from
    the code rather than verified against a build. If you follow it and something is still missing,
    that is worth an <a href="https://github.com/matteobaccan/owner/issues">issue</a>: it is the point at
    which adding a native build to our CI would start paying for itself, and until somebody needs it,
    shipping metadata we cannot test would be worse than shipping none.
  </p>
</div>

The short version
-----------------

**Use the tracing agent.** Run your application, or its tests, on a normal JVM with the agent attached,
and let it write the metadata:

```
$ java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
       -cp target/classes:… com.example.Main
```

Then build the image as usual. Since GraalVM for JDK 23 the agent writes a single
`reachability-metadata.json`; older versions write `proxy-config.json`, `reflect-config.json` and
friends, which still work and are deprecated rather than gone.

This is the recommendation and not a shortcut, for a reason worth knowing: one of the entries below
depends on **what is on your class path**, and is therefore easy to write by hand and get subtly wrong.


What it needs, so you can check the agent found it
--------------------------------------------------

### The proxy

The interface list is your configuration interface **and**
`javax.management.DynamicMBean` — because OWNER adds the second one whenever JMX is available, so that
every configuration can be [exposed over JMX](/owner/docs/jmx/):

```java
// DefaultFactory
return JMX_AVAILABLE ? new Class<?>[]{clazz, DynamicMBean.class} : new Class<?>[]{clazz};
```

So on an ordinary JDK the proxy is over **two** interfaces, and on a runtime without
`javax.management` it is over one. A hand-written entry that names only your interface is the mistake
this trap produces, and it fails at run time rather than at build time. The agent sees what actually
happened and writes the right one.

### Your configuration interface

Its methods and their annotations are read reflectively — that is how `@DefaultValue`, `@Key`,
`@Separator` and the rest are found — so the interface needs to be registered for reflection with its
methods.

### Any class you named in an annotation

A `@ConverterClass`, `@PreprocessorClasses`, `@TokenizerClass` or `@DecryptorClass` is instantiated by
name through its no-argument constructor, so each needs that constructor reachable. Since 2.0.0 such a
class may be package-private or nested and private, which is convenient in ordinary Java and one more
thing for the analysis not to guess.

### A return type converted through its `String` constructor

[Type conversion](/owner/docs/type-conversion/) will use a public constructor taking a single `String`
when there is nothing better, so that constructor needs to be reachable for every such type you return.

### The service file, if you use loader discovery

A [`Loader` found on the class path](/owner/docs/file-formats/) arrives through `ServiceLoader`, which
native image supports with metadata of its own. Registering the loader explicitly instead removes the
question.

### Your configuration files

The one most often forgotten, and it has nothing to do with proxies: **a native image contains no
resources unless it is told to**. `classpath:myconfig.properties` is a resource, so it needs a resource
entry or your configuration silently falls back on its `@DefaultValue`s — which is exactly the failure
mode [`owner.strict`](/owner/docs/loading-strategies/) exists to turn into an error. Turning it on for a
first native build is a cheap way to find out.


Why we ship no metadata of our own
----------------------------------

Because there is none to ship. Read the list again and notice what every entry has in common: **it is
all your code.** Your interface is the proxy's interface list; your classes are named in the
annotations; your return types have the constructors; your files are the resources. Nothing in
`org.aeonbits.owner` can be enumerated ahead of time on your behalf, so a
`META-INF/native-image` directory inside our jar would cover none of what you actually need — while
looking as though it did.

An annotation processor that generated the metadata for *your* interfaces would work, and it is the
shape [Coat](https://github.com/poiu-de/coat) takes to the whole problem by generating the
implementation instead of proxying it. It is not something this project has, and it would be a build
plugin to maintain rather than a file to write. If native image turns out to matter to more than one
person, that is the conversation to have — with an issue to have it in.


What is not expected to work
----------------------------

- **JNDI**, in `owner-extras`, needs `java.naming` and a naming provider inside the image. Reading a
  container's context from a native image is a question about the container more than about us.
- **Hot reload** is only file timestamps and has no reason to mind.
- **Everything else in the core** is ordinary Java over a proxy that the metadata above covers.
