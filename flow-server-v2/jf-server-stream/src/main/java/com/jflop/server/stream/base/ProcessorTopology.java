package com.jflop.server.stream.base;

import java.lang.annotation.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/21/17
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProcessorTopology {
    String[] parentSources() default  {};
    Class<StatefulProcessor>[] parentProcessors() default {};
    String[] childSinks() default {};
}
