---
title: "Disabling features"
---

If for some reasons some feature is causing some problems it is possible, for
some of them, to be disabled. For instance, if you are implementing the variable
expansion by yourself, you may want to disable the variable expansion provided
by OWNER.


This can be done using the [`@DisabledFeature`][df] annotation.

The `@DisabledFeature` can also be combined with multiple
[`DisableableFeature`][dfe] and it can be used on method level or on class level:

```java
// on class level...
@DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
public interface SampleConfig extends Config {
    @DefaultValue("Earth")
    public String planet();

    // on method level...
    @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
    @DefaultValue("Hello %s, welcome on ${planet}!")
    public String hello(String name);
}
```

In the above example the method `String hello(String name)` will return the
String "Hello %s, welcome on ${planet}!", ignoring the parameter passed.

The features that can be disabled are:

| Feature | Effect when disabled | Since |
|---|---|---|
| `VARIABLE_EXPANSION` | `${...}` variables are left untouched, both in the property values and in the keys. See [Variables expansion](/owner/docs/variables-expansion/). | 1.0.4 |
| `PARAMETER_FORMATTING` | The property value is returned as it is, instead of being used as a format for the method arguments. See [Parametrized properties](/owner/docs/parametrized-properties/). | 1.0.4 |
| `PREFIX` | Every prefix is ignored and the property is looked up with its bare key: the `@Prefix` declared on the interface and the one configured on the factory alike. See [Key prefix](/owner/docs/key-prefix/). | 2.0.0 |

Not everything that can be switched off is a `DisableableFeature`. The
expansion of [nested variables](/owner/docs/variables-expansion/#nested-variables),
introduced in 2.0.0, is turned off for the whole JVM with the
`-Downer.nested.variable.expansion=false` system property rather than per
method, since it exists to restore the substitution of the previous releases
in one move.

<div class="note info">
  <h5>What "class level" means for an inherited method.</h5>
  <p>
    When the annotation is placed on an interface, it applies to the methods <em>declared</em> in that
    interface. A method inherited from a super-interface keeps whatever the super-interface says, so
    disabling a feature on a sub-interface does not reach the methods it inherits.
  </p>
</div>

  [dfe]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.DisableableFeature.html
  [df]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Config.DisableFeature.html
