---
title: "Variables expansion"
---

Sometimes it may be useful to expand properties values from other properties.

Let's have a look at this properties file:

```properties
story=The ${animal} jumped over the ${target}
animal=quick ${color} fox
target=${target.attribute} dog
target.attribute=lazy
color=brown
```

...and the associated *mapping interface*:

```java
public interface ConfigWithExpansion extends Config {
    String story();
}
```

The property `story` will expand to:

<blockquote>The quick brown fox jumped over the lazy dog</blockquote>

This also works with the annotations, but you need to specify every properties
on the methods:

```java
public interface ConfigWithExpansion
        extends Config {

    @DefaultValue(
        "The ${animal} jumped over the ${target}")
    String story();

    @DefaultValue("quick ${color} fox")
    String animal();

    @DefaultValue("${target.attribute} dog")
    String target();

    @Key("target.attribute")
    @DefaultValue("lazy")
    String targetAttribute();

    @DefaultValue("brown")
    String color();
}

ConfigWithExpansion conf = ConfigFactory
    .create(ConfigWithExpansion.class);

String story = conf.story();
```

Sometimes you may want expand System Properties or Environment Variables.
This can be done using *imports* (see
[Importing properties](/owner/docs/importing-properties/) to learn
more):

```java
public interface SystemPropertiesExample
        extends Config {

    @DefaultValue("Welcome: ${user.name}")
    String welcomeString();

    @DefaultValue("${TMPDIR}/tempFile.tmp")
    File tempFile();

}

SystemPropertiesExample conf = ConfigFactory
    .create(SystemPropertiesExample.class,
            System.getProperties(),
            System.getenv());

String welcome = conf.welcomeString();
File temp = conf.tempFile();
```


Disabling variables expansion
-----------------------------

The variables expansion feature can be disabled if the user doesn't find it
convenient for some reason.
This can be done using the `@DisableFeature` annotation:

```java
public interface Sample extends Config {
    @DefaultValue("Earth")
    String world();

    @DisableFeature(VARIABLE_EXPANSION)
    @DefaultValue("Hello ${world}.")

    // will return the string "Hello ${world}."
    String sayHello();
}

```

The `@DisabledFeature` annotation can be applied on method level and/or on
interface level. When applied on interface level, the annotation will apply to
all methods defined in that interface, and to `getProperty()` and `fill()` with
them.

Reading a property by name
--------------------------

*Since 2.0.0.*

Everything on this page describes what happens when a property is read through
the method that maps it. A property can also be read *by name*, through the
[Accessible](/owner/docs/accessible-mutable/) interface, and since version 2.0.0
that expands the variables too:

```properties
s     = say
hello = ${s} HELLO
b     = ${hello} AGAIN!
```

```java
cfg.getProperty("b");       // say HELLO AGAIN!
cfg.getRawProperty("b");    // ${hello} AGAIN!
```

The methods that write the properties out — `list()`, `store()`, `storeToXML()`
— leave the variables where they are instead, so that a configuration saved
back to a file keeps them. Which method does what, and what happens to
`@Sensitive` and `@EncryptedValue` on each path, is set out in one table:
[which methods process the value](/owner/docs/accessible-mutable/#which-methods-process-the-value).

Variable expansion for the @Key
---------------------------------

Some time ago, I used to work for a company where - for security reason - it was required to deploy the exact same
artifact to production after being tried and verified in a testing environment.
The artifact was signed by a deployment tool to ensure that no changes were made during the passage from the
user acceptance test to the production environment. And the configuration was (correctly, in my opinion) considered part
of the deployment artifact.

The deployment artifact then, was containing all the runtime configurations, and the administrator or the application
itself was responsible to select the appropriate configuration to run with.
I really liked this approach, because was preventing having uncontrolled code running in prod/test enviroments. And
since configuration vastly impacts on how the application works, it must be versioned and under strict control as any
other source code.

Since version 1.0.6 it's possible to use variable expansion also in the `@Key` annotation, and this really adapts to
the above usage pattern, where we had all the configurations for different running enviroments in the same files,
and having the application or the administrator select the appropriate settings.

Suppose in your configuration you have defined three running environment: production
development and test. So you have a configuration XML looking like this:

```xml
<servers>
    <dev> <!-- development environment -->
        <name>Development</name>
        <hostname>devhost</hostname>
        <port>6000</port>
        <user>myuser1</user>
        <password>mypass1</password>
    </dev>
    <uat> <!-- user acceptance test environment -->
        <name>User Acceptance Test</name>
        <hostname>uathost</hostname>
        <port>60020</port>
        <user>myuser2</user>
        <password>mypass2</password>
    </uat>
    <prod> <!--  production environment -->
        <name>Production</name>
        <hostname>prod-host</hostname>
        <port>600</port>
        <user>prod-user</user>
        <password>secret</password>
    </prod>
</servers>
```

Or - if you prefer - a properties file that is equivalent to the above:

```properties
servers.dev.name=Development
servers.dev.hostname=devhost
servers.dev.port=6000
servers.dev.user=myuser1
servers.dev.password=mypass1

servers.uat.name=User Acceptance Test
servers.uat.hostname=uathost
servers.uat.port=60020
servers.uat.user=myuser2
servers.uat.password=mypass2

servers.prod.name=Production
servers.prod.hostname=prod-host
servers.prod.port=600
servers.prod.user=prod-user
servers.prod.password=secret
```

You can define the configuration mapping file as:

```java
@Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
public interface ExpandsFromAnotherKey extends Config {

    @DefaultValue("dev")
    String env();

    @Key("servers.${env}.name")
    String name();

    @Key("servers.${env}.hostname")
    String hostname();

    @Key("servers.${env}.port")
    Integer port();

    @Key("servers.${env}.user")
    String user();

    @Key("servers.${env}.password")
    String password();
}
```

Notice, on the above, that I defined the `env()` method to default with "dev" value. The subsequent `${env}` variables
specified in the `@Key` annotation would use "dev" as default value.

But now you can specify the `${env}` variable at runtime when creating the config object:

```java

Map myVars = new HashMap();
myVars.put("env", "uat"); // here!

ExpandsFromAnotherKey cfg = ConfigFactory
    .create(ExpandsFromAnotherKey.class, myVars); // here!

assertEquals("User Acceptance Test", cfg.name());
assertEquals("uathost", cfg.hostname());
assertEquals(new Integer(60020), cfg.port());
assertEquals("myuser2", cfg.user());
assertNull("mypass2", cfg.password());

```

This way you can select `${env}` when using the `ConfigFactory` (or from the system properties
and/or the environment variables if you prefer) and have the above interface map
to the appropriate section.
In the above example I selected "uat" as value for the `${env}` variables, so the "user acceptance test" configuration
would be selected.

Since version 2.0.0 the repeated `servers.${env}.` part can be declared once
on the interface with the `@Prefix` annotation, which is expanded exactly like
the `@Key`: see [Key prefix](/owner/docs/key-prefix/).

Default values
--------------

Since version 2.0.0, a variable can carry a default value, to be used when the property it refers to cannot be
resolved. Write it after a colon:

```java
public interface ServerConfig extends Config {

    @DefaultValue("${db.host:localhost}")
    String host();

    @DefaultValue("${db.port:5432}")
    int port();
}
```

If `db.host` is defined anywhere — in a source, in an import, in a system property — its value is used. If it is
not, `host()` returns `localhost` instead of the empty string that an unresolved variable produces on its own.

Everything after the *first* colon is the default, colons included, so values that contain one are not truncated:

```java
@DefaultValue("${service.url:http://localhost:8080/api}")
String serviceUrl();     // http://localhost:8080/api when service.url is not set

@DefaultValue("${cache.dir:C:\temp}")
String cacheDir();       // C:\temp on a machine where cache.dir is not set
```

<div class="note info">
  <h5>Keys containing a colon keep working.</h5>
  <p>
  The text inside <code>${...}</code> is looked up as a property key <em>in its entirety</em> first, and only if
  no such property exists is the colon read as the separator introducing a default. A configuration that relies on
  a key such as <code>jdbc:url</code> therefore behaves exactly as it did before default values existed.
  </p>
  <p>
  The one case that changes is a variable that used to resolve to nothing: <code>${a:b}</code>, with neither
  <code>a:b</code> nor <code>a</code> defined, yielded the empty string up to 1.0.12 and yields <code>b</code>
  from 2.0.0 on.
  </p>
</div>

A variable that resolves to nothing
-----------------------------------

An expression that nothing resolves — no property, no system property, no environment variable — is
replaced by the **empty string**, and always has been:

```properties
url=jdbc:h2:mem:${db.name}
```

With nothing called `db.name`, `url` reads `jdbc:h2:mem:`. The trouble is that `${db.nmae}`, misspelt,
gives exactly the same thing: a value that looks almost right, and no version of OWNER has ever said a
word about it.

The default is unchanged, because a configuration may lean on the empty string on purpose. **There are two
ways to stop guessing**, and the first one is free:

```properties
url=jdbc:h2:mem:${db.name:}          # the empty string, said deliberately
url=jdbc:h2:mem:${db.name:testdb}    # or a real fallback
```

A default value — even an empty one — says that the author considered the case. And since 2.0.0, setting
[`owner.strict`](/owner/docs/loading-strategies/#refusing-everything-that-would-only-have-been-a-warning)
on the factory refuses the variable that has neither a value nor a default, naming it:

```
The variable ${db.name} resolves to nothing: no property, no system property and no
environment variable goes by that name, and no default value was written for it.
```

<div class="note">
  <h5>It covers the <code>@Sources</code> specification too, which is where it bites hardest.</h5>
  <p>
    A spec is expanded before there is a Config object at all, so
    <code>file:${app.home}/app.properties</code> with nothing setting <code>app.home</code> quietly becomes
    <code>file:/app.properties</code> and the configuration then holds nothing but its default values.
    Strict names <code>app.home</code>, which is the cause; without it the only complaint available is that
    no source could be read, which names <code>file:/app.properties</code> — the symptom.
  </p>
</div>

Nested variables
----------------

The environment selection shown above stops working as soon as the *name* of
the key you want to read is itself configurable. Take a configuration where
the browser to drive depends on the environment, and the options to pass
depend on the browser:

```properties
environment=dev

environments.dev.browser=opera
environments.dev.webdriver.opera.switches=--headless
environments.dev.webdriver.chrome.switches=--incognito
```

Reading `switches` means resolving `${environment}` first, then reading
`environments.dev.browser` to find out which browser is selected, and only
then building the key to look up. Since version 2.0.0 a variable can be
nested inside another one, which says exactly that:

```java
public interface WebDriverConfig extends Config {

    @Key("environment")
    @DefaultValue("global")
    String env();

    @Key("environments.${environment}.browser")
    @DefaultValue("chrome")
    String usedBrowser();

    @Key("environments.${environment}.webdriver.${environments.${environment}.browser}.switches")
    String webDriverOptions();
}
```

`webDriverOptions()` returns `--headless`, and switching `environment` to a
different value moves all three methods to the other section at once.

The expression inside `${...}` is expanded first, and the result is then
looked up as a key. Nesting is allowed at any depth, in the `@Key` as well as
in a property value, and in the [`@Sources`](/owner/docs/configuring/)
specification:

```properties
servers.dev.url=http://devhost
env=dev

story=connecting to ${servers.${env}.url}
```

It also combines with the default values described above, since the default
applies to the key that the expansion produces:

```java
@DefaultValue("${servers.${env}.url:http://localhost}")
String url();
```

<div class="note info">
  <h5>A variable names a property, not a method.</h5>
  <p>
    In the example above the inner expression is <code>environments.${environment}.browser</code> — the key
    that <code>usedBrowser()</code> reads — and not <code>usedBrowser</code>, the name of the method reading
    it. Variables are resolved against the properties, so <code>${usedBrowser}</code> would look for a property
    literally called <code>usedBrowser</code>.
  </p>
</div>

Text that only looks like a variable is left where it stands, exactly as
before: `${}` is not a variable, an unbalanced `${` is not one either, and a
lone brace inside an expression is ordinary text, so a key such as `a{b` keeps
resolving. Only the `${` sequence opens a nesting level.

Disabling nested variables
--------------------------

Nesting changes how a `${` is matched with its `}`: up to 1.0.12 the first `}`
closed the expression, from 2.0.0 it is the one that matches. Configurations
that use nothing but plain variables are unaffected — that is what the whole
test suite of the project verifies — but a value built out of braces in some
unforeseen way could read differently.

For that case the new behaviour can be switched off for the entire JVM, with a
system property:

```
-Downer.nested.variable.expansion=false
```

With that set, OWNER matches braces the way the previous releases did — the
first `}` closes the expression — and a nested expression goes back to
producing what it produced before. Any other value, or no value at all, leaves
nesting enabled. The detection of circular references described below applies
either way.

The switch is read when the `Config` object is created, so it has to be set
before that — on the command line, or with `System.setProperty` early enough
in the application startup.

Circular references
-------------------

A property whose value leads back to the property itself cannot be resolved.
Since version 2.0.0 that is reported as the configuration error it is, with
an `IllegalArgumentException` naming the chain that closes the loop:

```properties
a=${b}
b=${a}
```

```
Circular variable reference: ${a} -> ${b} -> ${a}
```

It applies to a property referring to itself directly, to a loop of any
length, and to a key built by [nesting](#nested-variables).

<div class="note warning">
  <h5>A default value does not make a circular reference resolvable.</h5>
  <p>
    Shells and some frameworks use <code>db.host=${db.host:localhost}</code> to mean "keep the value if it is
    already set, otherwise use this one". That idiom relies on the substitution being performed once, when the
    value is assigned. OWNER expands variables when a property is <em>read</em>, and expands them inside values,
    so the same line describes a property whose value asks for the property itself — a loop, not a fallback,
    and one that a default value cannot break.
  </p>
  <p>
    It is reported rather than quietly resolved to <code>localhost</code>, because what was meant is simply
    <code>db.host=localhost</code>, and a configuration that says something else should say so out loud. Up to
    1.0.12 the same line produced an empty string, and in the 2.0.0 development cycle it exhausted the stack.
  </p>
</div>
