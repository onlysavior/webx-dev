package com.alibaba.citrus.test2.loader;

import static com.alibaba.citrus.test.TestEnvStatic.*;
import static com.alibaba.citrus.util.Assert.*;
import static com.alibaba.citrus.util.StringUtil.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletContext;

import com.alibaba.citrus.service.resource.support.context.ResourceLoadingXmlApplicationContext;
import com.alibaba.citrus.springext.support.resolver.XmlBeanDefinitionReaderProcessor;
import com.alibaba.citrus.test2.context.WebxTestComponentsLoader;
import com.alibaba.citrus.test2.context.WebxTestContext;
import com.alibaba.citrus.util.ClassUtil;
import com.alibaba.citrus.util.io.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.web.AbstractGenericWebContextLoader;
import org.springframework.test.context.web.WebMergedContextConfiguration;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

/**
 * Created by onlysavior on 14-10-4.
 */
public class WebxContextLoader extends AbstractGenericWebContextLoader {
    protected final Logger                                log               = LoggerFactory.getLogger(getClass());
    private         ThreadLocal<WebxTestComponentsLoader> threadLocalLoader = new ThreadLocal<WebxTestComponentsLoader>() {
        @Override
        protected WebxTestComponentsLoader initialValue() {
            return new WebxTestComponentsLoader();
        }
    };

//    protected final static ApplicationContext testResourceLoader = getTestResourceLoader();
//
//    /** 取得可装载测试环境的资源的resource loader。 */
//    private static ApplicationContext getTestResourceLoader() {
//        try {
//            System.setProperty("test.srcdir", srcdir.getAbsolutePath());
//            System.setProperty("test.destdir", destdir.getAbsolutePath());
//
//            Resource testResourceConfig = new ClassPathResource(
//                    ClassUtil.getResourceNameForPackage(SpringextContextLoader.class) + "/test-resources.xml");
//
//            return new ResourceLoadingXmlApplicationContext(testResourceConfig);
//        } finally {
//            System.clearProperty("test.srcdir");
//            System.clearProperty("test.destdir");
//        }
//    }

    @Override
    protected void configureWebResources(GenericWebApplicationContext context, WebMergedContextConfiguration webMergedConfig) {
        ApplicationContext parent = context.getParent();
        if (parent == null || (!(parent instanceof WebApplicationContext))) {
            String resourceBasePath = webMergedConfig.getResourceBasePath();
            ResourceLoader resourceLoader = resourceBasePath.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX) ? new DefaultResourceLoader()
                                                                                                             : new FileSystemResourceLoader();

            ServletContext servletContext = new MockServletContext(resourceBasePath, resourceLoader);
            WebApplicationContext webApplicationContext = loadWebx(servletContext, context, webMergedConfig);

            if (webApplicationContext != null) {
                if (webApplicationContext instanceof GenericWebApplicationContext) {
                    ((GenericWebApplicationContext) webApplicationContext).setParent(context);
                    ((GenericWebApplicationContext) webApplicationContext).setServletContext(servletContext);
                }
                servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
            } else {
                context.setServletContext(servletContext);
                servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
            }
        } else {
            ServletContext servletContext = null;

            // find the Root WAC
            while (parent != null) {
                if (parent instanceof WebApplicationContext && !(parent.getParent() instanceof WebApplicationContext)) {
                    servletContext = ((WebApplicationContext) parent).getServletContext();
                    break;
                }
                parent = parent.getParent();
            }
            Assert.state(servletContext != null, "Failed to find Root WebApplicationContext in the context hierarchy");
            WebApplicationContext webApplicationContext = loadWebx(servletContext, context, webMergedConfig);
            if (webApplicationContext != null) {
                if (webApplicationContext instanceof GenericWebApplicationContext) {
                    ((GenericWebApplicationContext) webApplicationContext).setParent(context);
                    ((GenericWebApplicationContext) webApplicationContext).setServletContext(servletContext);
                }
            } else {
                context.setServletContext(servletContext);
            }
        }
    }

    @Override
    protected void loadBeanDefinitions(GenericWebApplicationContext context, WebMergedContextConfiguration webMergedConfig) {
        XmlBeanDefinitionReader rawReader = new XmlBeanDefinitionReader(context);
        //hack configuration point
        new XmlBeanDefinitionReaderProcessor(rawReader).addConfigurationPointsSupport();

        rawReader.loadBeanDefinitions(webMergedConfig.getLocations());
    }

    @Override
    protected String getResourceSuffix() {
        return "-context.xml";
    }

    @Override
    protected String[] generateDefaultLocations(Class<?> clazz) {
        assertNotNull(clazz, "Class must not be null");

        String location = "/" + toCamelCase(clazz.getSimpleName())
                          + assertNotNull(trimToNull(getResourceSuffix()), "Resource suffix must not be empty");

        if (isGenerateContextConfigurations()) {
            File configLocation = new File(srcdir, location);

            if (!configLocation.exists()) {
                try {
                    configLocation.getParentFile().mkdirs();

                    OutputStream os = new FileOutputStream(configLocation);
                    InputStream is = getClass().getResourceAsStream("context-template.xml");

                    StreamUtil.io(is, os, true, true);

                    log.warn("Generated context configuration file: " + configLocation.getAbsolutePath());
                } catch (IOException e) {
                    log.warn("Could not generate context configuration file: " + configLocation.getAbsolutePath(), e);
                }
            }
        }

        return new String[] { location };
    }

    @Override
    protected String[] modifyLocations(Class<?> clazz, String... locations) {
        return locations;
    }

    /** 如果默认的配置文件不存在，是否生成样本？ */
    protected boolean isGenerateContextConfigurations() {
        return true;
    }

    private WebApplicationContext loadWebx(ServletContext servletContext, ApplicationContext parent,
                                           WebMergedContextConfiguration mergedContextConfiguration) {
        //hock to init webxComponentsContext
        WebxTestComponentsLoader webxComponentsLoader = threadLocalLoader.get();
        try {
            WebxTestContext webxTestConext = (WebxTestContext)webxComponentsLoader.loadContext(mergedContextConfiguration);
            webxTestConext.setLoader(webxComponentsLoader);
            webxComponentsLoader.setServletContext(servletContext);

            return webxTestConext;
        } catch (Exception e) {
            log.warn("can't init Webx Context",e);
            return null;
        }
    }
}
