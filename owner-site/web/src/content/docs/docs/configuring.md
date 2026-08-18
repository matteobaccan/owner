---
title: "Configuring the ConfigFactory"
---

There isn't much to configure in OWNER since most of the things are done via annotations.

Still somebody recently asked: I need to decide the sources locations at runtime, is it possible to pass them
explicitly? (Issue [#36] on GitHub).

  [#36]: https://github.com/matteobaccan/owner/issues/36

The short answer was no, even though there was some options to work around this limit.

But at the end, we agreed to add some configuration properties to configure the OWNER ConfigFactory itself.

Let's take this as example:

```java
// notice ${mypath} here
@Sources("file:${mypath}/myconfig.properties")
interface MyConfig extends Config { ... }

MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

The variable ${mypath} would be expanded from the System properties or from the environment properties, if defined
there. But this isn't convenient enough: environment properties are read only, and sometimes it is not convenient to
change system properties, especially for applications deployed in shared JVMs.

So OWNER does have some context properties associated to the ConfigFactory itself:

```java
// notice ${mypath} here
@Sources("file:${mypath}/myconfig.properties")
interface MyConfig extends Config { ... }

// notice ${mypath} here
ConfigFactory.setProperty("mypath", "/foo/bar/baz");
MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

When the `create()` method is executed the "file:${mypath}/myconfig.properties" specified by `@Sources` will be expanded
to "file:/foo/bar/baz/myconfig.properties".

Notice that, if you later change the value in the ConfigFactory and try to reload the Config object, this will not be
affected, since the config object works on a snapshot of the those properties generated when the `create()` method is
invoked.

Nothing prevents you to do something like:


```java
// notice ${myurl} here
@Sources("${myurl}")
interface MyConfig extends Config { ... }

// notice ${myurl} here
ConfigFactory.setProperty(
  "myurl", "http://somewhere.com/conf.properties");
MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

You are completely free to define the sources of your configuration at runtime.

The properties the factory understands itself
---------------------------------------------

Everything you set on the factory is available for expanding `@Sources`, as above. A few names are read by
the factory itself, and all of them arrived in 2.0.0:

| property | effect |
|---|---|
| [`owner.key.prefix`](/owner/docs/key-prefix/) | a literal, prepended to the key of every property |
| [`owner.key.prefix.from.package`](/owner/docs/key-prefix/) | `true` derives that prefix from the package of the interface declaring the method |
| [`owner.strict`](/owner/docs/loading-strategies/#refusing-everything-that-would-only-have-been-a-warning) | `true` turns the library's warnings into refusals, so that a configuration which would have carried on with its default values is not created at all |
| [`owner.declared.only`](/owner/docs/accessible-mutable/#showing-only-what-this-interface-declares) | `true` restricts every `Accessible` view and `toString()` to the properties each interface declares — for the interfaces you did not write and cannot annotate |

They are read when a Config object is **created**, and the object keeps them for the rest of its life —
changing one afterwards does not move a configuration that already exists, which is what keeps its keys
from shifting under it and lets the mapping travel with the object when it is serialized.

They belong to the factory and not to the JVM, which matters most for `owner.strict`: an application that
turns it on does not thereby make a library which happens to use OWNER strict as a side effect.
