package com.enterprise.automation.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.config.ConfigLoader;
import com.enterprise.automation.config.TestConfig;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

/** Unit tests for browser session setup and cleanup failures. */
@Test(groups = "unit")
public class DriverFactoryTest {

    /** A cleanup error remains secondary when configuring an already-created session fails. */
    public void setupFailureRemainsPrimaryWhenQuitAlsoFails() {
        TestConfig config = new ConfigLoader(Map.of(), Map.of()).load();
        RuntimeException setupFailure = new IllegalStateException("window configuration failed");
        RuntimeException cleanupFailure = new IllegalStateException("browser quit failed");
        AtomicInteger quitCalls = new AtomicInteger();

        WebDriver.Timeouts timeouts = proxy(WebDriver.Timeouts.class, (proxy, method, arguments) -> {
            if (method.getName().equals("pageLoadTimeout")) {
                throw setupFailure;
            }
            return null;
        });
        WebDriver.Window window = proxy(WebDriver.Window.class,
                (proxy, method, arguments) -> null);
        WebDriver.Options options = proxy(WebDriver.Options.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "timeouts" -> timeouts;
            case "window" -> window;
            default -> null;
        });
        WebDriver driver = proxy(WebDriver.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "manage" -> options;
            case "quit" -> {
                quitCalls.incrementAndGet();
                throw cleanupFailure;
            }
            default -> null;
        });

        assertThatThrownBy(() -> DriverFactory.configureAndCloseOnFailure(driver, config))
                .isSameAs(setupFailure);
        assertThat(quitCalls).hasValue(1);
        assertThat(setupFailure.getSuppressed()).containsExactly(cleanupFailure);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
