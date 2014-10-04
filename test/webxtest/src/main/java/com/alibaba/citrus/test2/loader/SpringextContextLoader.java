package com.alibaba.citrus.test2.loader;

import com.alibaba.citrus.service.resource.support.context.ResourceLoadingXmlApplicationContext;
import com.alibaba.citrus.springext.support.context.AbstractXmlApplicationContext;
import com.alibaba.citrus.test2.util.SpringVersionUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.StringUtils;

/**
 * Created by onlysavior on 14-10-4.
 */
public class SpringextContextLoader extends AbstractContextLoader{
    static {
        SpringVersionUtil.assertSpring3_2_x();
    }

    public final ApplicationContext loadContext(String... locations) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for locations [" + StringUtils.arrayToCommaDelimitedString(locations)
                      + "].");
        }

        ResourceLoadingXmlApplicationContext context = new ResourceLoadingXmlApplicationContext(locations, null, false);

        prepareContext(context);
        context.refresh();
        context.registerShutdownHook();

        return context;
    }

    public final ConfigurableApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for merged context configuration [" + mergedConfig + "].");
        }

        ResourceLoadingXmlApplicationContext context = new ResourceLoadingXmlApplicationContext(mergedConfig.getLocations(), null, false);

        context.getEnvironment().setActiveProfiles(mergedConfig.getActiveProfiles());
        prepareContext(context);
        context.refresh();
        context.registerShutdownHook();

        return context;
    }

    protected void prepareContext(AbstractXmlApplicationContext context) {
    }
}
