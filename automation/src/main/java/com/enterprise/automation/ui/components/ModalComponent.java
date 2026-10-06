package com.enterprise.automation.ui.components;

import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.ui.BaseComponent;
import com.enterprise.automation.ui.TestId;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * The application's modal dialog: forms (add/edit) and confirmations (delete, cancel order).
 *
 * <p>Form fields are found by their visible label, the way a user finds them; the label's
 * {@code for} attribute leads to the input, so the component works for every form.
 */
public class ModalComponent extends BaseComponent {

    private static final By TITLE = TestId.of("modal-title");
    private static final By SUBMIT = TestId.of("modal-submit");
    private static final By CANCEL = TestId.of("modal-cancel");
    private static final By ERROR = TestId.of("modal-error");
    private static final By MESSAGE = TestId.of("modal-message");

    public ModalComponent() {
        super(TestId.of("modal"));
    }

    public String title() {
        return child(TITLE).getText().trim();
    }

    public String message() {
        return child(MESSAGE).getText().trim();
    }

    /** Text of the element with test id {@code testId} inside the dialog. */
    public String text(String testId) {
        return child(TestId.of(testId)).getText().trim();
    }

    /** A table inside the dialog, e.g. the items of an order. */
    public TableComponent table(String testId) {
        return new TableComponent(By.cssSelector("[data-testid='modal'] [data-testid='" + testId + "']"));
    }

    /** Whether the submit button is offered (e.g. "Cancel order" only for open orders). */
    public boolean hasSubmit() {
        return !driver.findElement(root).findElements(SUBMIT).isEmpty();
    }

    /** Sets the field labelled {@code label}: types into inputs, picks the visible text in selects. */
    public ModalComponent fill(String label, String value) {
        Report.step("Fill '" + label + "' with '" + value + "'", () -> {
            WebElement field = field(label);
            if ("select".equals(field.getTagName())) {
                new Select(field).selectByVisibleText(value);
            } else {
                field.clear();
                if (value != null && !value.isEmpty()) {
                    field.sendKeys(value);
                }
            }
        });
        return this;
    }

    public String value(String label) {
        return field(label).getDomProperty("value");
    }

    public void submit() {
        actions.click(SUBMIT);
    }

    public void cancel() {
        actions.click(CANCEL);
        waitUntilClosed();
    }

    /** Submits and waits for the dialog to close (the expected outcome of a valid form). */
    public void submitAndWaitUntilClosed() {
        submit();
        waitUntilClosed();
    }

    public void waitUntilClosed() {
        wait.invisible(root);
    }

    /** Validation message under field {@code name} (the API field name, e.g. {@code email}). */
    public String fieldError(String name) {
        By error = TestId.of("error-" + name);
        wait.until(d -> !d.findElement(root).findElement(error).getText().isBlank(), "error for " + name);
        return child(error).getText().trim();
    }

    /** General error shown at the top of the dialog. */
    public String error() {
        wait.until(d -> !d.findElement(root).findElement(ERROR).getText().isBlank(), "modal error");
        return child(ERROR).getText().trim();
    }

    private WebElement field(String label) {
        return wait.until(d -> {
            WebElement labelElement = d.findElement(root)
                    .findElement(By.xpath(".//label[normalize-space()='" + label + "']"));
            return d.findElement(root).findElement(By.id(labelElement.getDomAttribute("for")));
        }, "field labelled '" + label + "'");
    }
}
