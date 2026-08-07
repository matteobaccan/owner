/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

/**
 * A config interface living in the default package, which has no name to derive a key prefix from. It has no
 * package declaration on purpose, so it cannot be imported and is reached by name from
 * {@code org.aeonbits.owner.GlobalKeyPrefixTest}.
 *
 * @author Matteo Baccan
 */
public interface DefaultPackageConfig extends org.aeonbits.owner.Config {

    @DefaultValue("8080")
    int port();
}
