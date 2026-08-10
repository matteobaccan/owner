---
title: "Frequently Asked Questions"
---

## What does "OWNER" name mean?

Since this API is used to access *Properties* files, and we implement mapping interfaces to deal with those,
somehow interfaces are *owners* for the properties. So here comes the name OWNER, all uppercase because when you speak
about it you must shout it out :-)

The true story, is that I tried to find a decent name for the project, but I didn't come out with anything better.
Sorry.

## Is OWNER a stable API?

The codebase is very compact, and I try to keep the test coverage to 100%, developing many tests for each new feature.
You have the source, you can help improving the library and fix the bugs if you find some.

Still, OWNER API is a very early project, and APIs may change in the future to add/change some behaviors. But the
philosophy is to keep always backward compatibility (unless not possible).

## What happens if some property is not set to any value?

See: [Undefined properties](/owner/docs/usage/#undefined-properties) in [Basic usage](/owner/docs/usage/) chapter.
If a property is required and has no sensible default, you can annotate it with `@Mandatory` to fail fast with a
meaningful exception: see [Mandatory properties](/owner/docs/usage/#mandatory-properties).

## How about the security of storing password in properties? Does OWNER support encryptable properties like in [Jasypt](http://www.jasypt.org/encrypting-configuration.html) ?

Yes. Since version 1.0.10 OWNER supports encrypted properties out of the box through the `@EncryptedValue` and
`@DecryptorClass` annotations: see the [Encrypted properties](/owner/docs/crypto/) chapter
(this was tracked in [#49](https://github.com/matteobaccan/owner/issues/49)).

Before 1.0.10, OWNER APIs were flexible enough to let the user implement that: an example is [here][enc-props].

  [enc-props]: https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/examples/EncryptedPropertiesExample.java

## Why OWNER API doesn't implement this ${pretty.neat.feature} ?

Explain it on [GitHub issues][issues]. If I like the idea I will implement it.
Or, you can implement by yourself and send me a push request on GitHub.
See also [Contributing](/owner/docs/contributing/).

The idea is to keep things minimal and code clean and easy. And for every new feature, having a complete test suite to
verify all cases.

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html
  [issues]: https://github.com/matteobaccan/owner/issues

## Who is using OWNER?

I have no idea. I implemented OWNER for a web application I was working on, then I decided to share it. And it looks
like [somebody][#32] is now using it. Including my friend [Bruno] who is a great guy and makes me proud of it.

If you like us to know you are using OWNER, drop a comment [here][#32].
Or maybe you just want to go [here](https://www.openhub.net/p/owner/users) and click on "I use this!".

The fact I am receiving feedback, request for features and changes, mails and questions, makes me think that this
library is useful to some people around the world, and this encourages me to work and improve it.
So, don't be shy to introduce yourself.

  [#32]: https://github.com/matteobaccan/owner/issues/32
  [Bruno]: https://github.com/matteobaccan/owner/issues/32#issuecomment-19466459
