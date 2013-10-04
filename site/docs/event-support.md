---
layout: docs
title: Event support
prev_section: xml-support
next_section: features
permalink: /docs/event-support/
---

The HotReload is a cool feature, however it would be nice to know which properties has changed so that you can
re-configure only the affected services.

This is why OWNER implements a feature rich event system that allow the user to know when a reload event occurs, when a
property is changed, and also let the user to check if the new value is compliant to a certain requirement and, if not
to rollback the property change or the entire change set.

Listening for Reloads
---------------------

As explained in [Reload and Hot Reload]({{site.url}}/docs/reload/) section, OWNER supports programmatic reload and
automatic reload (when a configuration file is changed). In both cases a reload event is generated.

To get notified when a reload event occurs, your interface must extend `Reloadable` that exposes some methods to
register a `ReloadListener`.

There is also the possibility to implement a `TransactionalReloadListener` which does notify you before the reload
is performed and allow you to check what is changed and eventually to rollback the reload operation if the changes you
see are not compliant to your application's requirements.

![reload-event]({{site.url}}/img/reload-event.png)

The `Reloadable` interfaces allows to attach adn remove `ReloadListener` objects.

The `ReloadListener` interface defines a method called `reloadPerformed()` that receives a `ReloadEvent` which contains
the `oldProperties` (before the reload) and the `newProperties` (after the reload) plus a list of `PropertyChangeEvent`
for every property which is changed.

The `PropertyChangeEvent` contains the `propertyName`, the `oldValue` and the `newValue`.

When calling the method `Reloadable.addReloadListener()` it is possible to pass an instance of
`TransactionalReloadListener` which extends the `ReloadListener` adding the method `beforeReload()`.
This listener receives the notification just after the reload is triggered and before the changes are effective. In this
way it is possible for this listener to check what is changed and, if the changes are not welcome, it can trigger the
exception `RollbackBatchException` which means that all the changes in the reload will be discarded, and the current
values will be kept.
If the `RollbackBatchException` is thrown, the `ReloadListener.reloadPerformed()` will not be invoked, since the reload
is aborted by the listener.

Example:

```java

@Sources("Example.properties")
interface MyConfig extends Reloadable {
    @DefaultValue("5")
    Integer someInteger();

    @DefaultValue("foobar")
    String someString();

    @DefaultValue("3.14")
    Double someDouble();

    String nullsByDefault();
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);

final boolean[] reloadPerformed = new boolean[] {false};

cfg.addReloadListener(new TransactionalReloadListener() {

    public void beforeReload(ReloadEvent event)
        throws RollbackBatchException {

        String notAllowedValue = "42";

        String newSomeInteger = event.getNewProperties()
            .getProperty("someInteger");

        // 42 makes the reload to rollback completely!
        if (notAllowedValue.equals(newSomeInteger))
            throw new RollbackBatchException(
              "42 is not allowed for property 'someInteger'");

    }

    public void reloadPerformed(ReloadEvent event) {
        reloadPerformed[0] = true;
    }

});

// we update the properties file in the filesystem
File target = new File("Example.properties");
save(target, new Properties() { {
    setProperty("someInteger", "41");
    setProperty("someString", "bazbar");
    setProperty("someDouble", "2.718");
    setProperty("nullsByDefault", "NotNullNow");
}});

cfg.reload();

// reload happened
assertTrue(reloadPerformed[0]);
assertEquals(new Integer(41), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());

reloadPerformed[0] = false; // reset the flag

save(target, new Properties() { {
    setProperty("someInteger", "42"); // not allowed!
    setProperty("someString", "blahblah");
    setProperty("someDouble", "1.234");
}});

cfg.reload();

// reload was rolled back since 42 is not allowed
assertFalse(reloadPerformed[0]);
assertEquals(new Integer(41), cfg.someInteger());
assertEquals("bazbar", cfg.someString());
assertEquals(new Double("2.718"), cfg.someDouble());
assertNotNull(cfg.nullsByDefault());

```

Listening for PropertyChanges
-----------------------------




Conclusions
-----------

