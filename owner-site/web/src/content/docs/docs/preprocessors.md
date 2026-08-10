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

Order of execution
------------------

When resolving a property, OWNER applies **method-level preprocessors first**, then the **interface-level ones**, in
the order they are listed in each annotation. In the `helloWorld()` example above, the applied chain is
`ToLowerCase`, `SkipInlineComments`, `Trim`.

Preprocessing happens on the raw property value, *before* [variables expansion](/owner/docs/variables-expansion/),
[decryption](/owner/docs/crypto/), [parametrized formatting](/owner/docs/parametrized-properties/) and
[type conversion](/owner/docs/type-conversion/): whatever your preprocessors return is what the rest of the
pipeline will see.

<div class="note">
  <h5>Undefined properties are not preprocessed</h5>
  <p>
    If a property cannot be resolved to any value, preprocessors are not invoked: the method returns
    <code>null</code> as explained in <a href="/owner/docs/usage/">Basic usage</a>.
  </p>
</div>

<div class="note info">
  <h5>Preprocessor classes need a public no-arg constructor</h5>
  <p>
    OWNER instantiates the classes listed in <code>@PreprocessorClasses</code> via reflection, so they must be
    public and provide a public default constructor (nested classes must be <code>static</code>).
  </p>
</div>
