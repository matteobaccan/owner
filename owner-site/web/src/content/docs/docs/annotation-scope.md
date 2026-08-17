---
title: "Where an annotation counts"
---

A *mapping interface* can extend another one, and most configurations of any
size do: a base interface holds what everything shares, and each configuration
adds its own. As soon as that happens, every annotation written at **class
level** raises the same question — where does it count? On the interface
carrying it, on the interface handed to the `ConfigFactory`, on the methods
inherited from somewhere else?

There are two answers, and which one applies is not a detail of the
implementation: it follows from what the annotation is *about*. This page is
the whole of it, with the reasons, because the alternative is finding out by
experiment.

Java is no help here, which is worth knowing before reading the rest.
[`@Inherited`][inherited] applies to a class extending a class and does nothing
whatsoever for an interface, so `SomeConfig.class.getAnnotation(...)` never
sees what a super-interface declares. Every lookup on this page is therefore
written out by hand in OWNER — and until 2.0.0 each was written out separately,
which is exactly how they came to disagree with each other.

The two families
----------------

**Some annotations describe the configuration object.** Which files it reads,
how they combine, whether it reloads, who decrypts its secrets, what header it
writes into a file it saves, which features it switches off. A configuration
object is not one interface: it is the one you hand to the `ConfigFactory`
*together with everything that one extends*. So a statement about the object
counts wherever in that hierarchy it is written.

**The others describe the methods an interface declares.** The prefix its keys
carry, whether they are mandatory, whether they are secret, how their list
values are cut, how their values are rewritten before use. These are read off
the interface that **declares** the method, and they neither climb nor descend.

| Annotation | Target | What it describes | Family |
|---|---|---|---|
| [`@Sources`](/owner/docs/loading-strategies/) | type | the files the configuration reads | object |
| [`@LoadPolicy`](/owner/docs/loading-strategies/) | type | how those files combine | object |
| [`@HotReload`](/owner/docs/reload/) | type | whether and how it reloads | object |
| [`@DecryptorClass`](/owner/docs/crypto/) | type | who decrypts its `@EncryptedValue` properties | object |
| `@Description` | type + method | the header of a file it [saves](/owner/docs/accessible-mutable/) | object *(on the type)* |
| [`@DisableFeature`](/owner/docs/disabling-features/) | type + method | which features are off | **both — see below** |
| [`@Prefix`](/owner/docs/key-prefix/) | type | the prefix the keys are read with | methods |
| [`@Mandatory`](/owner/docs/usage/#mandatory-properties) | type + method | that the properties must be present | methods |
| [`@Sensitive`](/owner/docs/debugging/#keeping-a-property-out-of-the-output) | type + method | that the values are not to be printed | methods |
| [`@Separator`, `@TokenizerClass`](/owner/docs/type-conversion/) | type + method | how the list values are cut | methods |
| [`@PreprocessorClasses`](/owner/docs/preprocessors/) | type + method | how the values are rewritten | methods |

Everything else — `@Key`, `@DefaultValue`, `@ConverterClass`,
`@CollectionConverterClass`, `@EncryptedValue` — can only be written on a
method, so the question does not arise: the method carries it and the method is
what is being called.

How each family propagates
--------------------------

Take a configuration `AppConfig` handed to the `ConfigFactory`, extending
`Middle`, which extends `Base`:

```java
public interface Base extends Config { String password(); }
public interface Middle extends Base { }
public interface AppConfig extends Middle { String host(); }
```

**The object family** does not care about methods at all. The question is only
whether anybody in the hierarchy declared the annotation:

| Declared on | Applies to the configuration | Before 2.0.0 |
|---|---|---|
| `AppConfig`, the interface handed to the factory | yes | yes |
| `Middle`, a direct super-interface | yes | `@Sources`, `@LoadPolicy`, `@HotReload` only |
| `Base`, two levels up | yes | **no — silently ignored** |
| an interface reached by two paths (a diamond) | yes, and read **once** | no |
| two interfaces at different depths | the nearest wins † | — |
| nowhere in the hierarchy | the default applies | — |

† `@Sources` is the exception and always has been: it describes a *set*, so it
accumulates instead of being won — the URIs of the nearest interface first,
then those declared above it. `@DisableFeature` is an exception of the same
kind, described further down.

**The method family** does not care about depth. It cares about one thing:
which interface declares the method being called.

| Declared on | `password()`, declared on `Base` | `host()`, declared on `AppConfig` |
|---|---|---|
| `AppConfig` | **no** | yes |
| `Middle` | **no** | **no** |
| `Base` | yes | **no** |
| the method itself | yes | yes |

The two "no" in the first column are the interesting ones, and they are
deliberate. An interface governs what it declares: if `AppConfig` could mark
`password()` as `@Sensitive`, or re-cut with `@Separator(";")` a list that
`Base` described with commas, then two interfaces would be describing one key
and the one that wrote the property would not be the one deciding what it
means. Whoever wants the parent's keys treated differently says so **where they
are declared**, or on the methods themselves — which is what the method-level
form of each of these annotations is for.

<div class="note info">
  <h5>This is not new, and it is not an accident of the implementation.</h5>
  <p>
    It is the rule <a href="/owner/docs/key-prefix/"><code>@Prefix</code></a> has always followed — a
    method inherited from a super-interface keeps that interface's prefix, whatever the sub-interface
    says — extended to the annotations that behave the same way for the same reason. Each cell of the
    table above is covered by a test in <code>ClassLevelAnnotationsTest</code>, positive and negative
    case side by side, so that changing any of it has to be a decision rather than an oversight.
  </p>
</div>

The rules, and what each one prevents
-------------------------------------

| # | Rule | Why it is that way | What the other choice would do |
|---|---|---|---|
| 1 | An annotation about the **object** is found anywhere in the hierarchy | the object *is* the hierarchy: `create(AppConfig.class)` builds one thing out of all of it | a base interface holding `@DecryptorClass` for a dozen configurations would do nothing, silently |
| 2 | An annotation about the **methods** is read on the interface declaring them | an interface governs what it declares | two interfaces describing one key, the writer of the property not deciding its meaning |
| 3 | The nearest declaration wins | it is the most specific statement about this configuration | a base interface would overrule what the concrete one says |
| 4 | The walk is **breadth first** | every interface extended directly is asked before any of their parents, so "nearest" means what it says | a long first branch would beat a direct parent declared second |
| 5 | Each interface is visited **once** | a diamond is one interface, not two | `@Sources` listed twice, the same file read twice, the diagnostics saying it twice |
| 6 | `@Sources` accumulates, and every `@DisableFeature` is read | both describe a set, and sets add up | half the sources; a feature disabled by a base going unnoticed because a nearer interface disabled a different one |
| 7 | The convention (`MyConfig.properties`) applies only when **nobody** declares `@Sources` | it is a fallback for a configuration that names no source, not an extra source | a file nobody named being read by a configuration that named its own |
| 8 | The method question does **not** climb, even for `@DisableFeature` | see the note below | a blanket negative on a base cancelling a positive written explicitly below it |

The order of the walk
---------------------

Rules 3, 4 and 5 are one thing seen three ways: the order the interfaces are
visited in. It is breadth first, the `extends` clause deciding between siblings,
each interface appearing once:

```java
interface Root   extends Config { }
interface Left   extends Root { }
interface Right  extends Root { }
interface Bottom extends Left, Right { }
```

`Bottom` is asked in this order:

| Position | Interface | Why here |
|---|---|---|
| 1 | `Bottom` | the interface handed to the factory |
| 2 | `Left` | first in the `extends` clause |
| 3 | `Right` | second in the `extends` clause |
| 4 | `Root` | a grandparent: after **every** direct parent |
| 5 | `Config` | and above it, whatever that reaches |

`Root` is reached through `Left` and through `Right` and appears once, so an
annotation on it is read once — which for `@Sources` is the difference between
reading a file and reading it twice.

`@DisableFeature` is asked both questions
-----------------------------------------

It is the one annotation in both families, and that is not an inconsistency:
there are genuinely two questions to ask about it.

| Question | Who asks it | Where it looks |
|---|---|---|
| Is this feature off **for this method**? | every mapping method — `host()`, `password()` | the method, then the interface declaring it |
| Has this **configuration** asked for the feature to be off? | `getProperty`, `fill`, and the [Bean Validation](/owner/docs/validation/) report | the whole hierarchy |

The second question exists because `getProperty` and `fill` are declared on
[`Accessible`](/owner/docs/accessible-mutable/) and never on the interface you
wrote: there is no declaring class of yours for them to read, so they have to
ask about the configuration instead.

<div class="note warning">
  <h5>Until 2.0.0 one lookup answered both, and a configuration could contradict itself.</h5>
  <p>
    With <code>@DisableFeature(VARIABLE_EXPANSION)</code> written on a super-interface,
    <code>cfg.home()</code> returned <code>${user.home}</code> unexpanded — the method found the
    annotation on its declaring interface — while <code>cfg.getProperty("home")</code> expanded it,
    because that lookup read the interface handed to the factory and nothing above it. One property, two
    answers, depending on how you asked.
  </p>
  <p>
    <b>The method question still stops at the declaring interface, deliberately</b> (rule 8). Were it to
    climb, a blanket <code>@DisableFeature(PREFIX)</code> on some base interface would cancel a
    <code>@Prefix("db.")</code> written explicitly two levels below it: a negative nobody was looking at
    overruling a positive right in front of them. The configuration-wide question climbs; the one about a
    method that some interface deliberately described does not.
  </p>
</div>

What changed in 2.0.0
---------------------

Nothing on this page is a new feature: it is one rule replacing six lookups
that had been written separately and had drifted apart.

| Annotation | Before 2.0.0 | Since |
|---|---|---|
| `@Sources`, `@LoadPolicy`, `@HotReload` | the interface and its **direct** super-interfaces | the whole hierarchy |
| `@Sources`, when declared | the `MyConfig.properties` convention was appended as well | what was declared, and nothing else |
| `@DecryptorClass` | the interface handed to the factory, **and nothing above it** | the whole hierarchy |
| `@Description` on the type | the interface handed to the factory | the whole hierarchy, nearest first |
| `@DisableFeature`, asked of the object | the interface handed to the factory | the whole hierarchy, every declaration |
| `@DisableFeature`, asked of a method | the method and its declaring interface | unchanged |
| `@Prefix` and the rest of the method family | the declaring interface | unchanged |

The `@DecryptorClass` one was the worst of them, because of how it failed: an
`@EncryptedValue` property came back as the cipher text stored in the file,
which is a string like any other and breaks — if it breaks at all — at the far
end of a connection rather than where the mistake was made.

  [inherited]: https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Inherited.html
