/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.TokenizerClass;

import java.lang.reflect.Method;

import static org.aeonbits.owner.util.Util.newInstance;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * @author Luigi R. Viggiano
 */
final class TokenizerResolver {

    /** Don't let anyone instantiate this class */
    private TokenizerResolver() {}

    private static final Tokenizer DEFAULT_TOKENIZER = new SplitAndTrimTokenizer(",");

    static Tokenizer resolveTokenizer(Method targetMethod) {
        Tokenizer methodLevelTokenizer = resolveTokenizerOnMethodLevel(targetMethod);
        if (methodLevelTokenizer != null)
            return methodLevelTokenizer;

        Tokenizer classLevelTokenizer = resolveTokenizerOnClassLevel(targetMethod.getDeclaringClass());
        if (classLevelTokenizer != null)
            return classLevelTokenizer;

        return DEFAULT_TOKENIZER;
    }

    private static Tokenizer resolveTokenizerOnClassLevel(Class<?> declaringClass) {
        Separator separatorAnnotationOnClassLevel = declaringClass.getAnnotation(Separator.class);
        TokenizerClass tokenizerClassAnnotationOnClassLevel = declaringClass.getAnnotation(TokenizerClass.class);

        if (separatorAnnotationOnClassLevel != null && tokenizerClassAnnotationOnClassLevel != null)
            throw unsupported(
                    "You cannot specify @Separator and @TokenizerClass both together on class level for '%s'",
                    declaringClass.getCanonicalName());

        if (separatorAnnotationOnClassLevel != null)
            return new SplitAndTrimTokenizer(separatorAnnotationOnClassLevel.value());

        if (tokenizerClassAnnotationOnClassLevel != null)
            return newInstance(tokenizerClassAnnotationOnClassLevel.value());

        return null;
    }

    private static Tokenizer resolveTokenizerOnMethodLevel(Method targetMethod) {
        Separator separatorAnnotationOnMethodLevel = targetMethod.getAnnotation(Separator.class);
        TokenizerClass tokenizerClassAnnotationOnMethodLevel = targetMethod.getAnnotation(TokenizerClass.class);

        if (separatorAnnotationOnMethodLevel != null && tokenizerClassAnnotationOnMethodLevel != null)
            throw unsupported(
                    "You cannot specify @Separator and @TokenizerClass both together on method level for '%s'",
                    targetMethod);

        if (separatorAnnotationOnMethodLevel != null)
            return new SplitAndTrimTokenizer(separatorAnnotationOnMethodLevel.value());

        if (tokenizerClassAnnotationOnMethodLevel != null)
            return newInstance(tokenizerClassAnnotationOnMethodLevel.value());

        return null;
    }

}
