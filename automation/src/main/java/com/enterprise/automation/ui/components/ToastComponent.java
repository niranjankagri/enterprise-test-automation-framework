package com.enterprise.automation.ui.components;

import com.enterprise.automation.ui.BaseComponent;
import com.enterprise.automation.ui.TestId;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Toast notifications ("Customer created", errors...). They disappear after a few seconds, so
 * reads wait for a toast with the expected text instead of reading whatever is there.
 */
public class ToastComponent extends BaseComponent {

    private static final By TOAST = TestId.of("toast");
    private static final By MESSAGE = TestId.of("toast-message");

    public ToastComponent() {
        super(By.id("toasts"));
    }

    /** Waits for a toast containing {@code text} and returns its full message. */
    public String waitForMessage(String text) {
        return wait.until(d -> d.findElements(TOAST).stream()
                .map(t -> t.findElement(MESSAGE).getText().trim())
                .filter(m -> m.contains(text))
                .findFirst().orElse(null), "toast containing '" + text + "'");
    }

    /** Waits for any toast and returns the newest message. */
    public String latestMessage() {
        List<WebElement> toasts = wait.allVisible(TOAST);
        return toasts.get(toasts.size() - 1).findElement(MESSAGE).getText().trim();
    }

    /** Type of the newest toast: {@code success} or {@code error}. */
    public String latestType() {
        List<WebElement> toasts = wait.allVisible(TOAST);
        return toasts.get(toasts.size() - 1).getDomAttribute("data-type");
    }
}
