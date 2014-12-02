HOT RELOAD EXAMPLE
==================

This example is a demonstration on how Hot Reload works.

To run the example, from the current directory type the following command at prompt

```console
$ mvn compile exec:java
```

You'll see something like:

```console
$ mvn compile exec:java

[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building OWNER :: Examples :: Hot Reload 1.0.7-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]

## some more maven logs here...

 HOT RELOAD EXAMPLE

The program is running.
Now you can change the file located at:

	/Users/luigi/foo/bar/baz/target/examples-generated-resources/HotReloadExample.properties

 ...and see the changes reflected below


someValue is: 10
```

As the program explains, you can change the `HotReloadExample.properties` and see that the change is
immediately noticed and the configuration is instantaneously reloaded and printed in the console.

Set the property someValue to -1, or any other negative value, in the configuration file and the program will
take that value as instruction to terminate.

Have a look at the source code to see how this has been implemented.
