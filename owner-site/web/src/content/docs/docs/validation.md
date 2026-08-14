---
title: "Bean Validation"
---

Since 2.0.0 the constraint annotations you already know — `@Min`, `@NotNull`, `@Pattern`, `@Size`, and any
constraint you wrote yourself — are checked **when the configuration object is created**, on methods named
the way this library teaches:

```java
public interface ServerConfig extends Config {
    @Min(12) @DefaultValue("1") @Key("port") int port();
    @NotNull String hostname();
}
```

```
org.aeonbits.owner.validation.ConfigValidationException:
  ServerConfig: 2 properties do not satisfy the constraints declared on them:
  'port' (port()): must be greater than or equal to 12;
  'hostname' (hostname()): must not be null
```

It takes one dependency and no code. See [Turning it on](#turning-it-on).

<div class="note warning">
  <h5>Before 2.0.0 the first of those two methods checked nothing, and said nothing.</h5>
  <p>
    <code>Validator.validate(config)</code> walks JavaBean <em>properties</em>, so
    <code>@Min(12) int getPort()</code> was a property called <code>port</code> and was checked, while
    <code>@Min(12) int port()</code> was neither a property nor a field and was passed over in silence —
    with both Hibernate Validator and Apache BVal, since the two agree here and are both right. That is
    <a href="https://github.com/matteobaccan/owner/issues/201">issue #201</a>. The dangerous half of it was
    never the missing check but the silence: an annotation that reads like a guarantee, on the spelling the
    documentation teaches, checking nothing.
  </p>
</div>


Turning it on
-------------

Two things on the class path: `owner-extras`, and a validation provider.

```xml
<dependency>
    <groupId>org.aeonbits.owner</groupId>
    <artifactId>owner-extras</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
    <version>8.0.1.Final</version>
</dependency>
```

There is nothing to register and nothing to call. `owner-extras` declares a `ConfigValidator` in its
`META-INF/services`, the core finds it, and a configuration whose methods carry constraints is checked as it
is created — next to the check that
[`@Mandatory`](/owner/docs/usage/#mandatory-properties) already did, and for the same reason: a
configuration is read once, at startup, and a value that is wrong is wrong then.

A configuration with **no constraint annotation on it pays nothing**: no validator is asked anything, and
not one value is read to ask about.

### `javax` or `jakarta`: both

`javax.validation` is Bean Validation 1.1 and 2.0; `jakarta.validation` is the same specification after the
rename, 3.0 and later. Both are looked for, and whichever is present **and has a provider** is used, so you
get the namespace your framework already chose. Both APIs are *optional* dependencies of `owner-extras`:
nothing is shipped, nothing is transitive, and nobody receives either by depending on OWNER.

| Your world | The namespace | A provider that runs there |
|---|---|---|
| Spring Boot 3, Jakarta EE 10, Java 11+ | `jakarta.validation` 3.x | Hibernate Validator 8, Apache BVal 3 |
| Spring Boot 2, Java EE 8, Java 8 | `javax.validation` 2.0 | Hibernate Validator 6.2, Apache BVal 2 |

An application in the middle of the rename, with constraints of both kinds, has both checked.


What is checked, and what is not
--------------------------------

This is the whole of the issue, so it is spelled out rather than implied. A constraint is checked against
the value the method **returns**, which means the method is called when the configuration is created — that
being what a configuration is for.

| The method | Checked | Why |
|---|---|---|
| `@Min(12) int port()` | **yes** | the OWNER spelling, which is the point of all this |
| `@Min(12) int getPort()` | **yes** | the getter spelling, checked the same way and *once* — see below |
| `Optional<@Min(12) Integer> port()` | **yes** | the provider unwraps the container to reach the value |
| `List<@Min(12) Integer> ports()` | **yes** | element by element; the violation names the index |
| `@Size(min=1) List<String> ports()` | **yes** | a collection is a value, and this is a check on it |
| a constraint inside a [nested section](/owner/docs/nested-configuration/) | **yes** | sections are walked into, and the violation carries the whole key, `server.port` |
| `@Min(12) int portOf(String name)` | no | it has no key and no value until it is called, and there is nothing to call it with here |
| `@Min(12) default int computed()` | no | a `default` method is your code, not a property; this library never reads it and does not run it |
| `@NotNull ServerConfig server()` | no | a section is a *view* of the properties: never null, never absent, so the check could not fail |
| `@Min(12) Optional<Integer> port()` | no | the constraint is on the `Optional`, which is never absent. Write `Optional<@Min(12) Integer>` |
| constraints inside `Map<String, Server> servers()` | no | those sections are built when the properties name one; at creation time there is nothing to check |

**Every one of those "no" rows is reported.** See [The silence is over](#the-silence-is-over).

<div class="note info">
  <h5>A getter-named method is checked once, not twice.</h5>
  <p>
    <code>getPort()</code> is a JavaBean property <em>and</em> a method, so a mechanism that ran both
    <code>validate(object)</code> and <code>validateReturnValue(...)</code> would report the same value
    twice under two different names. Only executable validation is used, for every spelling alike. If you
    also call <code>validator.validate(config)</code> in your own code, that call is yours and will find the
    getter-named ones again.
  </p>
</div>

Validation runs when the configuration is **created**, and not again after a
[`reload()`](/owner/docs/reload/) or a [`setProperty()`](/owner/docs/accessible-mutable/) — exactly as the
`@Mandatory` check does not. What arrives afterwards is the business of whoever reads it.


What a violation looks like
---------------------------

A single `ConfigValidationException` naming **every** violation, so that a reader restarts once rather than
once per mistake:

```java
try {
    ServerConfig config = ConfigFactory.create(ServerConfig.class);
} catch (ConfigValidationException violated) {
    violated.getViolations();   // key, method name and message, one per violation
    violated.getKeys();         // just the keys: 'port', 'hostname'
}
```

The **key** comes first in every message, because the key is the line you have to go and change; the method
is only where the constraint is declared. For an element of a list or of a group the index comes too:

```
'ports' (ports()): element [1]: must be greater than or equal to 12
```

<div class="note info">
  <h5>The value that failed is not in the message.</h5>
  <p>
    A violation ends up in an exception message and an exception message ends up in a log, which is exactly
    where a configuration value must not go — it is what
    <a href="/owner/docs/debugging/">@Sensitive</a> exists for, and "password too short" is precisely the
    kind of violation that would print one. The key says which line to look at, which is all a reader needs.
  </p>
</div>


The silence is over
-------------------

A constraint that is **not** being checked is now said out loud, once, when the configuration is created.

If nothing on the class path can check them — you have the annotations, because they compiled, but not
`owner-extras` or not a provider — the configuration says so as a `WARNING`, naming the properties and what
to add. And each of the method shapes in the "no" column above is named with its reason:

```
WARNING  ServerConfig: these validation constraints are declared and are not checked.
         'portOf()' takes arguments, so it has no key and no value until it is called, and there is
         nothing to call it with here. Check it where it is called.
```

Under `-Downer.strict=true` all of that is **refused** instead of warned about, as everything else that is a
warning here is. See [Debugging](/owner/docs/debugging/).

### Saying "these are not for you"

An interface whose constraints belong to something else — another framework reading the same interface, a
code generator — says so, and both the check and the report leave it alone:

```java
@DisableFeature(VALIDATION)
public interface ServerConfig extends Config {
    @Min(12) @DefaultValue("1") int port();
}
```

It works on a single method as well as on the interface. See
[Disabling features](/owner/docs/disabling-features/).


Writing your own checker
------------------------

`ConfigValidator` is a service like a [loader](/owner/docs/file-formats/) is: implement it, declare it in
`META-INF/services/org.aeonbits.owner.validation.ConfigValidator`, and it is found. It is handed the
properties that carry constraints, each with its key and the value already read, and answers with the
violations it found — or throws, if it cannot check at all, because returning no violations means "these
hold".

The core itself depends on no validation API and never will: it recognises a constraint by the **name** of
the `@Constraint` annotation on it, which is enough to know that a check is declared and to say when nothing
is checking it.
