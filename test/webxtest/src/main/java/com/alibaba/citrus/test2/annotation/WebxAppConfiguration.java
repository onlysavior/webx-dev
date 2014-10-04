package com.alibaba.citrus.test2.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by onlysavior on 14-10-4.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WebxAppConfiguration {
    /**
     * copy form @see WebAppConfiguration
     * The resource path to the root directory of the web application.
     *
     * <p>A path that does not include a Spring resource prefix (e.g., {@code classpath:},
     * {@code file:}, etc.) will be interpreted as a file system resource, and a
     * path should not end with a slash.
     *
     * <p>Defaults to {@code "src/main/webapp"} as a file system resource. Note
     * that this is the standard directory for the root of a web application in
     * a project that follows the standard Maven project layout for a WAR.
     */
    String value() default "src/main/webapp";
}
