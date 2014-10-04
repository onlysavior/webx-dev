package com.alibaba.citrus.test2.context;

import com.alibaba.citrus.service.resource.support.ResourceLoadingSupport;
import com.alibaba.citrus.springext.support.context.XmlWebApplicationContext;
import com.alibaba.citrus.test2.util.SpringVersionUtil;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

/**
 * Created by onlysavior on 14-10-4.
 */
public class WebxTestContext extends XmlWebApplicationContext {
    private WebxTestComponentsLoader loader;
    static {
        SpringVersionUtil.assertSpring3_2_x();
    }

    public WebxTestContext() {
        setResourceLoadingExtender(new ResourceLoadingSupport(this));
    }

    public WebxTestContext(String[] configLocations, ApplicationContext parentContext,
                           boolean refresh) {
        setResourceLoadingExtender(new ResourceLoadingSupport(this));
        setParent(parentContext);
        setConfigLocations(configLocations);

        if (refresh) {
            refresh();
        }
    }

//    public WebxTestContext(WebxComponent component) {
//
//    }

    public WebxTestComponentsLoader getLoader() {
        return loader;
    }

    public void setLoader(WebxTestComponentsLoader loader) {
        this.loader = loader;
    }

    @Override
    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        super.postProcessBeanFactory(beanFactory);
        getLoader().postProcessBeanFactory(beanFactory);
    }
}
