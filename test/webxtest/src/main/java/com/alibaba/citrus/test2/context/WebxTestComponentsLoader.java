package com.alibaba.citrus.test2.context;

import static com.alibaba.citrus.springext.util.SpringExtUtil.*;
import static com.alibaba.citrus.util.Assert.*;
import static com.alibaba.citrus.util.BasicConstant.*;
import static com.alibaba.citrus.util.CollectionUtil.*;
import static com.alibaba.citrus.util.FileUtil.*;
import static com.alibaba.citrus.util.StringUtil.*;
import static com.alibaba.citrus.util.regex.PathNameWildcardCompiler.compilePathName;
import static com.alibaba.citrus.webx.WebxConstant.COMPONENT_CONTEXT_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import com.alibaba.citrus.springext.util.SpringExtUtil;
import com.alibaba.citrus.test2.loader.AbstractContextLoader;
import com.alibaba.citrus.test2.util.SpringVersionUtil;
import com.alibaba.citrus.util.ToStringBuilder;
import com.alibaba.citrus.util.ToStringBuilder.MapBuilder;
import com.alibaba.citrus.webx.WebxComponent;
import com.alibaba.citrus.webx.WebxComponents;
import com.alibaba.citrus.webx.WebxController;
import com.alibaba.citrus.webx.WebxRootController;
import com.alibaba.citrus.webx.config.WebxConfiguration;
import com.alibaba.citrus.webx.config.WebxConfiguration.ComponentConfig;
import com.alibaba.citrus.webx.config.WebxConfiguration.ComponentsConfig;
import com.alibaba.citrus.webx.config.impl.WebxConfigurationImpl;
import com.alibaba.citrus.webx.config.impl.WebxConfigurationImpl.ComponentsConfigImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.ServletContextResourcePatternResolver;

/**
 * Created by onlysavior on 14-10-4.
 */
public class WebxTestComponentsLoader extends AbstractContextLoader {
    static {
        SpringVersionUtil.assertSpring3_2_x();
    }
    private WebxTestContext topContext;
    private WebxComponentsImpl    components;
    private ServletContext servletContext;

    public WebxTestComponentsLoader() {
    }

    public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for merged context configuration [" + mergedConfig + "].");
        }

        WebxTestContext context = new WebxTestContext(mergedConfig.getLocations(), null, false);

        context.getEnvironment().setActiveProfiles(mergedConfig.getActiveProfiles());
        prepareContext(context);
        //context.refresh();
        context.registerShutdownHook();
        topContext = context;

        return context;
    }

    public ApplicationContext loadContext(String... locations) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Loading ApplicationContext for locations [" + StringUtils.arrayToCommaDelimitedString(locations)
                      + "].");
        }

        WebxTestContext context = new WebxTestContext(locations, null, false);

        prepareContext(context);
        //context.refresh();
        context.registerShutdownHook();
        topContext = context;

        return context;
    }

    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(WebxTestContextCreator.class);
        builder.addConstructorArgValue(this);
        BeanDefinition componentsCreator = builder.getBeanDefinition();
        componentsCreator.setAutowireCandidate(false);

        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        String name = SpringExtUtil.generateBeanName(WebxTestContextCreator.class.getName(), registry);

        registry.registerBeanDefinition(name, componentsCreator);
    }

    protected void prepareContext(WebxTestContext context) {
    }

    public WebxTestContext getTopContext() {
        return topContext;
    }

    public void setTopContext(WebxTestContext topContext) {
        this.topContext = topContext;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
        topContext.setServletContext(servletContext);
        topContext.refresh();
    }

    public static class WebxTestContextCreator implements BeanFactoryPostProcessor, Ordered {
        private WebxTestComponentsLoader loader;

        public WebxTestContextCreator(WebxTestComponentsLoader loader) {
            this.loader = assertNotNull(loader, "WebxComponentsLoader");
        }

        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (loader.components == null) {
                WebxComponentsImpl components = loader.createComponents(loader.getParentConfiguration(), beanFactory);
                AbstractApplicationContext wcc = (AbstractApplicationContext) components.getParentApplicationContext();
                wcc.addApplicationListener(new SourceFilteringListener(wcc, components));
                loader.components = components;
            }
        }

        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    private WebxComponentsImpl createComponents(WebxConfiguration parentConfiguration,
                                                ConfigurableListableBeanFactory beanFactory) {
        ComponentsConfig componentsConfig = getComponentsConfig(parentConfiguration);

        // 假如isAutoDiscoverComponents==true，试图自动发现components
        Map<String, String> componentNamesAndLocations = findComponents(componentsConfig, getServletContext());

        // 取得特别指定的components
        Map<String, ComponentConfig> specifiedComponents = componentsConfig.getComponents();

        // 实际要初始化的comonents，为上述两种来源的并集
        Set<String> componentNames = createTreeSet();

        componentNames.addAll(componentNamesAndLocations.keySet());
        componentNames.addAll(specifiedComponents.keySet());

        // 创建root controller
        WebxRootController rootController = componentsConfig.getRootController();

        if (rootController == null) {
            rootController = (WebxRootController) BeanUtils.instantiateClass(componentsConfig.getRootControllerClass());
        }

        // 创建并将components对象置入resolvable dependencies，以便注入到需要的bean中
        WebxComponentsImpl components = new WebxComponentsImpl(topContext,
                                                               componentsConfig.getDefaultComponent(), rootController, parentConfiguration);

        beanFactory.registerResolvableDependency(WebxComponents.class, components);

        // 初始化每个component
        for (String componentName : componentNames) {
            ComponentConfig componentConfig = specifiedComponents.get(componentName);

            String componentPath = null;
            WebxController controller = null;

            if (componentConfig != null) {
                componentPath = componentConfig.getPath();
                controller = componentConfig.getController();
            }

            if (controller == null) {
                controller = (WebxController) BeanUtils.instantiateClass(componentsConfig.getDefaultControllerClass());
            }

            WebxComponentImpl component = new WebxComponentImpl(components, componentName, componentPath,
                                                                componentName.equals(componentsConfig.getDefaultComponent()), controller,
                                                                getWebxConfigurationName());

            components.addComponent(component);

            prepareComponent(component, componentNamesAndLocations.get(componentName));
        }

        return components;
    }

    private ComponentsConfig getComponentsConfig(WebxConfiguration parentConfiguration) {
        ComponentsConfig componentsConfig = assertNotNull(parentConfiguration, "parentConfiguration")
                .getComponentsConfig();

        if (componentsConfig == null) {
            // create default components configuration
            componentsConfig = new ComponentsConfigImpl();
        }

        //hack here
        ((ComponentsConfigImpl)componentsConfig).setComponentConfigurationLocationPattern("classpath*:*.xml");
        return componentsConfig;
    }

    private void prepareComponent(WebxComponentImpl component, String componentLocation) {
        String componentName = component.getName();
        WebxTestComponent wcc = new WebxTestComponent(component);

        wcc.setServletContext(getServletContext());
        wcc.setNamespace(componentName);
        wcc.addApplicationListener(new SourceFilteringListener(wcc, component));

        if (componentLocation != null) {
            wcc.setConfigLocation(componentLocation);
        }

        component.setApplicationContext(wcc);

        // 将context保存在servletContext中
        String attrName = getComponentContextAttributeName(componentName);
        getServletContext().setAttribute(attrName, wcc);

        log.debug("Published WebApplicationContext of component {} as ServletContext attribute with name [{}]",
                  componentName, attrName);
    }

    private WebxConfiguration getParentConfiguration() {
        try {
            return (WebxConfiguration) topContext.getBean(getWebxConfigurationName());
        } catch (BeansException e) {
            // create default configuration
            WebxConfigurationImpl parentConfiguration = new WebxConfigurationImpl();
            parentConfiguration.setApplicationContext(topContext);

            try {
                parentConfiguration.afterPropertiesSet();
            } catch (RuntimeException ee) {
                throw ee;
            } catch (Exception ee) {
                throw new RuntimeException(ee);
            }

            return parentConfiguration;
        }
    }

    private Map<String, String> findComponents(ComponentsConfig componentsConfig, ServletContext servletContext) {
        String locationPattern = componentsConfig.getComponentConfigurationLocationPattern();
        String[] prefixAndPattern = checkComponentConfigurationLocationPattern(locationPattern);
        String prefix = prefixAndPattern[0];
        String pathPattern = prefixAndPattern[1];

        Map<String, String> componentNamesAndLocations = createTreeMap();

        if (componentsConfig.isAutoDiscoverComponents()) {
            try {
                ResourcePatternResolver resolver = new ServletContextResourcePatternResolver(servletContext);
                Resource[] componentConfigurations = resolver.getResources(locationPattern);
                Pattern pattern = compilePathName(pathPattern);

                if (componentConfigurations != null) {
                    for (Resource resource : componentConfigurations) {
                        String path = resource.getURL().getPath();
                        Matcher matcher = pattern.matcher(path);

                        assertTrue(matcher.find(), "unknown component configuration file: %s", path);
                        String componentName = trimToNull(matcher.group(1));

                        if (componentName != null) {
                            componentNamesAndLocations.put(componentName,
                                                           prefix + pathPattern.replace("*", componentName));
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return componentNamesAndLocations;
    }

    private String[] checkComponentConfigurationLocationPattern(String componentConfigurationLocationPattern) {
        if (componentConfigurationLocationPattern != null) {
            // 允许并剔除classpath*:前缀。
            boolean classpath = componentConfigurationLocationPattern.startsWith("classpath*:");
            String pathPattern = componentConfigurationLocationPattern;

            if (classpath) {
                pathPattern = componentConfigurationLocationPattern.substring("classpath*:".length()).trim();
            }

            // 检查路径。
            int index = pathPattern.indexOf("*");

            if (index >= 0) {
                index = pathPattern.indexOf("*", index + 1);

                if (index < 0) {
                    if (pathPattern.startsWith("/")) {
                        pathPattern = pathPattern.substring(1);
                    }

                    return new String[] { classpath ? "classpath:" : EMPTY_STRING, pathPattern };
                }
            }
        }

        throw new IllegalArgumentException("Invalid componentConfigurationLocationPattern: "
                                           + componentConfigurationLocationPattern);
    }

    public String getWebxConfigurationName() {
        return "webxConfiguration";
    }

    public String getComponentContextAttributeName(String componentName) {
        return COMPONENT_CONTEXT_PREFIX + componentName;
    }

    private static class WebxComponentsImpl implements WebxComponents, ApplicationListener {
        private final WebxConfiguration          parentConfiguration;
        private final WebApplicationContext      parentContext;
        private final Map<String, WebxComponent> components;
        private final RootComponent              rootComponent;
        private final String                     defaultComponentName;
        private final WebxRootController         rootController;

        public WebxComponentsImpl(WebApplicationContext parentContext, String defaultComponentName,
                                  WebxRootController rootController, WebxConfiguration parentConfiguration) {
            this.parentConfiguration = assertNotNull(parentConfiguration, "no parent webx-configuration");
            this.parentContext = parentContext;
            this.components = createHashMap();
            this.rootComponent = new RootComponent();
            this.defaultComponentName = defaultComponentName;
            this.rootController = assertNotNull(rootController, "no rootController");

            rootController.init(this);
        }

        public WebxConfiguration getParentWebxConfiguration() {
            return parentConfiguration;
        }

        private void addComponent(WebxComponent component) {
            components.put(component.getName(), component);
        }

        public WebxComponent getComponent(String componentName) {
            if (componentName == null) {
                return rootComponent;
            } else {
                return components.get(componentName);
            }
        }

        public String[] getComponentNames() {
            String[] names = components.keySet().toArray(new String[components.size()]);
            Arrays.sort(names);
            return names;
        }

        public WebxComponent getDefaultComponent() {
            return defaultComponentName == null ? null : components.get(defaultComponentName);
        }

        public Iterator<WebxComponent> iterator() {
            return components.values().iterator();
        }

        public WebxComponent findMatchedComponent(String path) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            WebxComponent defaultComponent = getDefaultComponent();
            WebxComponent matched = null;

            // 前缀匹配componentPath。
            for (WebxComponent component : this) {
                if (component == defaultComponent) {
                    continue;
                }

                String componentPath = component.getComponentPath();

                if (!path.startsWith(componentPath)) {
                    continue;
                }

                // path刚好等于componentPath，或者path以componentPath/为前缀
                if (path.length() == componentPath.length() || path.charAt(componentPath.length()) == '/') {
                    matched = component;
                    break;
                }
            }

            // fallback to default component
            if (matched == null) {
                matched = defaultComponent;
            }

            return matched;
        }

        public WebxRootController getWebxRootController() {
            return rootController;
        }

        public WebApplicationContext getParentApplicationContext() {
            return parentContext;
        }

        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ContextRefreshedEvent) {
                // autowire and init root controller
                autowireAndInitialize(rootController, getParentApplicationContext(),
                                      AbstractBeanDefinition.AUTOWIRE_AUTODETECT, "webxRootController");

                rootController.onRefreshContext();
            }
        }

        @Override
        public String toString() {
            MapBuilder mb = new MapBuilder();

            mb.append("parentContext", parentContext);
            mb.append("defaultComponentName", defaultComponentName);
            mb.append("components", components);
            mb.append("rootController", rootController);

            return new ToStringBuilder().append("WebxComponents").append(mb).toString();
        }

        /** 这是一个特殊的component实现，对应于root context。 */
        private class RootComponent implements WebxComponent {
            public WebxComponents getWebxComponents() {
                return WebxComponentsImpl.this;
            }

            public String getName() {
                return null;
            }

            public String getComponentPath() {
                return EMPTY_STRING;
            }

            public WebxConfiguration getWebxConfiguration() {
                return getParentWebxConfiguration();
            }

            public WebxController getWebxController() {
                unsupportedOperation("RootComponent.getWebxController()");
                return null;
            }

            public WebApplicationContext getApplicationContext() {
                return getParentApplicationContext();
            }

            @Override
            public String toString() {
                return WebxComponentsImpl.this.toString();
            }
        }
    }

    private static class WebxComponentImpl implements WebxComponent, ApplicationListener {
        private final WebxComponents        components;
        private final String                name;
        private final String                componentPath;
        private final WebxController        controller;
        private final String                webxConfigurationName;
        private       WebApplicationContext context;

        public WebxComponentImpl(WebxComponents components, String name, String path, boolean defaultComponent,
                                 WebxController controller, String webxConfigurationName) {
            this.components = assertNotNull(components, "components");
            this.name = assertNotNull(name, "componentName");
            this.controller = assertNotNull(controller, "controller");
            this.webxConfigurationName = assertNotNull(webxConfigurationName, "webxConfigurationName");

            // 规格化path，去除尾部的/；空路径则设为null
            path = trimToNull(normalizeAbsolutePath(path, true));

            if (defaultComponent) {
                assertTrue(path == null, "default component \"%s\" should not have component path \"%s\"", name, path);
                this.componentPath = EMPTY_STRING;
            } else if (path != null) {
                this.componentPath = path;
            } else {
                this.componentPath = "/" + name;
            }

            controller.init(this);
        }

        public WebxComponents getWebxComponents() {
            return components;
        }

        public String getName() {
            return name;
        }

        public String getComponentPath() {
            return componentPath;
        }

        public WebxController getWebxController() {
            return controller;
        }

        public WebxConfiguration getWebxConfiguration() {
            return (WebxConfiguration) context.getBean(webxConfigurationName);
        }

        public WebApplicationContext getApplicationContext() {
            return context;
        }

        private void setApplicationContext(WebApplicationContext context) {
            this.context = context;
        }

        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ContextRefreshedEvent) {
                // autowire and init controller
                autowireAndInitialize(controller, getApplicationContext(), AbstractBeanDefinition.AUTOWIRE_AUTODETECT,
                                      "webxController." + getName());

                controller.onRefreshContext();
            }
        }

        @Override
        public String toString() {
            MapBuilder mb = new MapBuilder();

            mb.append("name", name);
            mb.append("path", componentPath);
            mb.append("controller", controller);
            mb.append("context", context);

            return new ToStringBuilder().append("WebxComponent").append(mb).toString();
        }
    }
}
