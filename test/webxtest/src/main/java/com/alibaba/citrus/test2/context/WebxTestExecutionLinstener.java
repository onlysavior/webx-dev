package com.alibaba.citrus.test2.context;

import javax.servlet.ServletContext;

import com.alibaba.citrus.test2.annotation.WebxAppConfiguration;
import com.alibaba.citrus.webx.context.WebxComponentsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Conventions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Created by onlysavior on 14-10-4.
 */
public class WebxTestExecutionLinstener extends AbstractTestExecutionListener {
    private static final Logger logger                                     = LoggerFactory.getLogger(WebxTestExecutionLinstener.class);
    public static final  String RESET_REQUEST_CONTEXT_HOLDER_ATTRIBUTE     = Conventions.getQualifiedAttributeName(
            WebxTestExecutionLinstener.class, "resetRequestContextHolder");
    public static final  String POPULATED_REQUEST_CONTEXT_HOLDER_ATTRIBUTE = Conventions.getQualifiedAttributeName(
            WebxTestExecutionLinstener.class, "populatedRequestContextHolder");

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        setUpRequestContextIfNecessary(testContext);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        setUpRequestContextIfNecessary(testContext);
    }

    public void afterTestMethod(TestContext testContext) throws Exception {
        if (Boolean.TRUE.equals(testContext.getAttribute(RESET_REQUEST_CONTEXT_HOLDER_ATTRIBUTE))) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Resetting RequestContextHolder for test context %s.", testContext));
            }
            RequestContextHolder.resetRequestAttributes();
        }
        testContext.removeAttribute(POPULATED_REQUEST_CONTEXT_HOLDER_ATTRIBUTE);
        testContext.removeAttribute(RESET_REQUEST_CONTEXT_HOLDER_ATTRIBUTE);
    }

    private void setUpRequestContextIfNecessary(TestContext testContext) {
        if (notAnnotatedWithWebxAppConfiguration(testContext)
            || alreadyPopulatedWebxRequestContextHolder(testContext)) {
            return;
        }

        ApplicationContext context = testContext.getApplicationContext();
        if (context instanceof WebxComponentsContext) {
            WebxComponentsContext wac = (WebxComponentsContext) context;
            ServletContext servletContext = wac.getServletContext();
            Assert.state(servletContext instanceof MockServletContext, String.format(
                    "The WebxComponentsContext for test context %s must be configured with a MockServletContext.",
                    testContext));

            if (logger.isDebugEnabled()) {
                logger.debug(String.format(
                        "Setting up MockHttpServletRequest, MockHttpServletResponse, ServletWebRequest, and RequestContextHolder for test context %s.",
                        testContext));
            }

            MockServletContext mockServletContext = (MockServletContext) servletContext;
            MockHttpServletRequest request = new MockHttpServletRequest(mockServletContext);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ServletWebRequest servletWebRequest = new ServletWebRequest(request, response);

            RequestContextHolder.setRequestAttributes(servletWebRequest);
            testContext.setAttribute(POPULATED_REQUEST_CONTEXT_HOLDER_ATTRIBUTE, Boolean.TRUE);
            testContext.setAttribute(RESET_REQUEST_CONTEXT_HOLDER_ATTRIBUTE, Boolean.TRUE);

            if (wac instanceof ConfigurableApplicationContext) {
                @SuppressWarnings("resource")
                ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) wac;
                ConfigurableListableBeanFactory bf = configurableApplicationContext.getBeanFactory();
                bf.registerResolvableDependency(MockHttpServletResponse.class, response);
                bf.registerResolvableDependency(ServletWebRequest.class, servletWebRequest);
            }
        }
    }

    private boolean notAnnotatedWithWebxAppConfiguration(TestContext testContext) {
        return AnnotationUtils.findAnnotation(testContext.getTestClass(), WebxAppConfiguration.class) == null;
    }

    private boolean alreadyPopulatedWebxRequestContextHolder(TestContext testContext) {
        return Boolean.TRUE.equals(testContext.getAttribute(POPULATED_REQUEST_CONTEXT_HOLDER_ATTRIBUTE));
    }
}
