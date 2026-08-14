---
title: "Preprocessors"
---

Since version 1.0.9, OWNER allows you to pre-process a property value before it is used by the library.
This is useful, for example, to trim values, normalize the case, or strip inline comments from properties files.

A preprocessor is a class implementing the `Preprocessor` interface:

```java
public interface Preprocessor {
    String process(String input);
}
```

Preprocessors are attached to the configuration through the `@PreprocessorClasses` annotation, which can be applied
on a single method or on the whole interface:

```java
@PreprocessorClasses({ SkipInlineComments.class, Trim.class })
public interface ConfigWithPreprocessors extends Config {

    @DefaultValue("  300  ")
    Integer pollingPeriod();      // returns 300: trimmed, then converted

    @PreprocessorClasses(ToLowerCase.class)
    @DefaultValue("  HelloWorld  ")
    String helloWorld();          // returns "helloworld"

    @DefaultValue("This is the value ! this is an inline comment")
    String skipsInlineComments(); // returns "This is the value"
}
```

...with the preprocessors implemented as:

```java
public class Trim implements Preprocessor {
    public String process(String input) {
        return input.trim();
    }
}

public class ToLowerCase implements Preprocessor {
    public String process(String input) {
        return input.toLowerCase();
    }
}
```

A preprocessor does not have to be public
-----------------------------------------

Since 2.0.0 the class named in the annotation may have any visibility, and so may its constructor: it can be
package-private next to the interface that uses it, or a `private static` class nested inside it.

```java
public class Example {

    public interface MyConfig extends Config {
        @Key("prop.a")
        @DefaultValue("a")
        @PreprocessorClasses(ToUpperCase.class)
        String propA();
    }

    private static class ToUpperCase implements Preprocessor {
        @Override
        public String process(String input) {
            return input.toUpperCase();
        }
    }
}
```

A preprocessor is an implementation detail of the configuration that names it, and until 2.0.0 it had to be
`public` — so a library using OWNER had to widen its own published API to satisfy ours. It was not being asked
to be visible *to OWNER*, which would be fair: it was being asked to be visible to everyone, and even a
package-private class sitting beside the interface was refused.

The same now holds for every class named in an annotation: a [`Converter`](/owner/docs/type-conversion/), a
`Tokenizer` and a [decryptor](/owner/docs/crypto/). What has not changed is that the class needs a constructor
taking no arguments — that is a reason no annotation can work around, and it is still refused with the class
named in the message. Asked for in
[#186](https://github.com/matteobaccan/owner/issues/186).

Order of execution
------------------

When resolving a property, OWNER applies **method-level preprocessors first**, then the **interface-level ones**, in
the order they are listed in each annotation. In the `helloWorld()` example above, the applied chain is
`ToLowerCase`, `SkipInlineComments`, `Trim`.

Preprocessing happens on the raw property value, *before* [variables expansion](/owner/docs/variables-expansion/),
[decryption](/owner/docs/crypto/), [parametrized formatting](/owner/docs/parametrized-properties/) and
[type conversion](/owner/docs/type-conversion/): whatever your preprocessors return is what the rest of the
pipeline will see.

When the property is not there
------------------------------

`process(String)` is never handed a `null`. If a property cannot be resolved to any value, a preprocessor
is asked through a different method instead — `processAbsent(String key)`, which is given **the key** and
answers `null` by default, leaving the property absent exactly as before:

```java
public class FromTheVault implements Preprocessor {

    public String process(String input) {
        return input;
    }

    @Override
    public String processAbsent(String key) {
        return vault.lookup(key);   // null if the vault has not got it either
    }
}
```

The key comes with the call because without it there would be nothing useful to do: a preprocessor that
only learned of the absence could answer with a constant, and a constant is what `@DefaultValue` already
is. With it, a preprocessor can look the value up somewhere OWNER knows nothing about, or name the
property in what it throws.

What it returns carries on through the rest of the chain — the preprocessors after it see it through
`process(String)` — and then through expansion, decryption, formatting and conversion, exactly like a
value read from a file. What supplied it is not asked to process it again.

Asked for in [#188](https://github.com/matteobaccan/owner/issues/188).

<div class="note">
  <h5>To require a value, use @Mandatory instead</h5>
  <p>
    <code>processAbsent</code> is for <em>computing</em> a value, not for insisting on one. A property that
    must be set is <a href="/owner/docs/usage/#mandatory-properties"><code>@Mandatory</code></a>: it throws
    <code>MissingMandatoryPropertyException</code> naming the key, needs no code, and reports every
    missing property at once when the configuration is created.
  </p>
  <p>
    The two compose. <code>@Mandatory</code> is checked at creation, and a preprocessor that supplies a
    value <strong>satisfies</strong> it rather than failing startup for a property that would have
    worked — which costs one call to <code>processAbsent</code> then, and another when the property is
    first read.
  </p>
</div>

<div class="note info">
  <h5>Preprocessor classes need a public no-arg constructor</h5>
  <p>
    OWNER instantiates the classes listed in <code>@PreprocessorClasses</code> via reflection, so they must be
    public and provide a public default constructor (nested classes must be <code>static</code>).
  </p>
</div>
