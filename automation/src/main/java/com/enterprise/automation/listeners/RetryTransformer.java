package com.enterprise.automation.listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

/**
 * Attaches {@link RetryAnalyzer} to every test, so no test needs to remember
 * {@code @Test(retryAnalyzer = ...)}. A test that declares its own analyzer keeps it.
 */
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        Class<?> current = annotation.getRetryAnalyzerClass();
        // TestNG's "no analyzer" default is an internal class; compare by name instead of importing it
        if (current == null || "DisabledRetryAnalyzer".equals(current.getSimpleName())) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
