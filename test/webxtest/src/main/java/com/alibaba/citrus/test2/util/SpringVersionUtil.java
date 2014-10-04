package com.alibaba.citrus.test2.util;

import static com.alibaba.citrus.util.Assert.fail;

import org.springframework.core.SpringVersion;

/**
 * Created by onlysavior on 14-10-4.
 */
public class SpringVersionUtil {
    public static void assertSpring3_2_x() {
        ClassLoader cl = SpringVersion.class.getClassLoader();

        try {
            cl.loadClass("org.springframework.test.web.servlet.MockMvc");
        } catch (ClassNotFoundException e) {
            fail("Unsupported Spring version: %s, requires Spring 3.2.x or later", SpringVersion.getVersion());
        }
    }
}
