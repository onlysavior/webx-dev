package com.alibaba.citrus.test2.context;

import static com.alibaba.citrus.util.Assert.*;

import com.alibaba.citrus.service.resource.support.context.ResourceLoadingXmlApplicationContext;
import com.alibaba.citrus.springext.support.context.AbstractXmlApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.SpringVersion;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.StringUtils;

/**
 * Created by onlysavior on 14-10-4.
 */
public class SpringextContextLoader extends AbstractContextLoader{
    static {
        assertSpring3_2_x();
    }

    private static void assertSpring3_2_x() {
        ClassLoader cl = SpringVersion.class.getClassLoader();

        try {
            cl.loadClass("org.springframework.test.web.servlet.MockMvc");
        } catch (ClassNotFoundException e) {
            fail("Unsupported Spring version: %s, requires Spring 3.2.x or later", SpringVersion.getVersion());
        }
    }

    public final ApplicationContext loadContext(String... locations) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for locations [" + StringUtils.arrayToCommaDelimitedString(locations)
                      + "].");
        }

        ResourceLoadingXmlApplicationContext context = new ResourceLoadingXmlApplicationContext(locations, testResourceLoader, false);

        prepareContext(context);
        context.refresh();
        context.registerShutdownHook();

        return context;
    }

    public final ConfigurableApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for merged context configuration [" + mergedConfig + "].");
        }

        ResourceLoadingXmlApplicationContext context = new ResourceLoadingXmlApplicationContext(mergedConfig.getLocations(), testResourceLoader, false);

        context.getEnvironment().setActiveProfiles(mergedConfig.getActiveProfiles());
        prepareContext(context);
        context.refresh();
        context.registerShutdownHook();

        return context;
    }

    protected void prepareContext(AbstractXmlApplicationContext context) {
    }
}
