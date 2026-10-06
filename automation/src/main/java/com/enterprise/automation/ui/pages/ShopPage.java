package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.BasePage;
import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.HeaderComponent;
import com.enterprise.automation.ui.components.NavigationComponent;
import com.enterprise.automation.ui.components.ToastComponent;
import org.openqa.selenium.By;

/**
 * Base of every signed-in page of ShopEase Admin: the shared components (header, navigation,
 * toasts) and the application's "page is ready" contract.
 *
 * <p>Ready means: the body names this page ({@code data-page}), the page has finished loading its
 * data ({@code data-ready="true"}) and the loading bar is hidden. Waiting for that real condition
 * replaces any fixed sleep.
 *
 * @param <T> the concrete page, so {@link #waitUntilLoaded()} returns it for fluent calls
 */
public abstract class ShopPage<T extends ShopPage<T>> extends BasePage {

    private static final By LOADER = TestId.of("loader");
    private static final By PAGE_TITLE = TestId.of("page-title");

    /** The value of {@code body[data-page]}, e.g. {@code customers}. */
    protected abstract String pageId();

    /** Path of the page, e.g. {@code /customers.html}. */
    protected abstract String path();

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    /** Opens the page by URL (the browser must already be signed in). */
    public T open() {
        navigateTo(path());
        return waitUntilLoaded();
    }

    public T waitUntilLoaded() {
        wait.attributeIs(By.tagName("body"), "data-page", pageId());
        wait.attributeIs(By.tagName("body"), "data-ready", "true");
        wait.invisible(LOADER);
        return self();
    }

    public String heading() {
        return actions.text(PAGE_TITLE);
    }

    public HeaderComponent header() {
        return new HeaderComponent();
    }

    public NavigationComponent navigation() {
        return new NavigationComponent();
    }

    public ToastComponent toast() {
        return new ToastComponent();
    }
}
