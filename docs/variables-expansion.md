---
layout: docs
title: Variables expansion
prev_section: type-conversion
next_section: reload
permalink: /docs/variables-expansion/
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
[Importing properties]({{ site.url }}/docs/importing-properties/) to learn
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
all methods defined in that interface.

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
