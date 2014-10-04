package com.alibaba.citrus.test2.context;

import static com.alibaba.citrus.util.Assert.assertNotNull;

import com.alibaba.citrus.webx.WebxComponent;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.web.context.WebApplicationContext;

/**
 * Created by onlysavior on 14-10-4.
 */
public class WebxTestComponent extends WebxTestContext {
    private final WebxComponent component;

    public WebxTestComponent(WebxComponent component) {
        this.component = assertNotNull(component, "component");
        WebApplicationContext parentContext = component.getWebxComponents().getParentApplicationContext();
        setParent(parentContext);
    }

    @Override
    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        super.postProcessBeanFactory(beanFactory);
        beanFactory.registerResolvableDependency(WebxComponent.class, component);
    }
}
