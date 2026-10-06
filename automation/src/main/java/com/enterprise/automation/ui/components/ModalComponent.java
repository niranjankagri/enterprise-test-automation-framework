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

    // Parts every dialog of the application has (rendered by App.modal in app.js)
    private static final By TITLE = TestId.of("modal-title");
    private static final By SUBMIT = TestId.of("modal-submit");
    private static final By CANCEL = TestId.of("modal-cancel");
    private static final By ERROR = TestId.of("modal-error");
    private static final By MESSAGE = TestId.of("modal-message");

    public ModalComponent() {
        // Only one dialog is open at a time, so the root is always the same
        super(TestId.of("modal"));
    }

    /** Dialog heading, e.g. "Edit customer". */
    public String title() {
        return child(TITLE).getText().trim();
    }

    /** Body text of a confirmation dialog, e.g. "Delete Ava Patel? ...". */
    public String message() {
        return child(MESSAGE).getText().trim();
    }

    /** Text of the element with test id {@code testId} inside the dialog. */
    public String text(String testId) {
        return child(TestId.of(testId)).getText().trim();
    }

    /** A table inside the dialog, e.g. the items of an order. */
    public TableComponent table(String testId) {
        // Scoped to the dialog, so a table with the same test id on the page behind is never used
        return new TableComponent(By.cssSelector("[data-testid='modal'] [data-testid='" + testId + "']"));
    }

    /** Whether the submit button is offered (e.g. "Cancel order" only for open orders). */
    public boolean hasSubmit() {
        // No waiting: the button is either rendered with the dialog or not at all
        return !driver.findElement(root).findElements(SUBMIT).isEmpty();
    }

    /** Sets the field labelled {@code label}: types into inputs, picks the visible text in selects. */
    public ModalComponent fill(String label, String value) {
        Report.step("Fill '" + label + "' with '" + value + "'", () -> {
            WebElement field = field(label);
            // Selects (e.g. Status) are set by their visible option text
            if ("select".equals(field.getTagName())) {
                new Select(field).selectByVisibleText(value);
            } else {
                // Inputs: clear, then type; an empty value leaves the field empty
                field.clear();
                if (value != null && !value.isEmpty()) {
                    field.sendKeys(value);
                }
            }
        });
        return this;
    }

    /** Current value of the field labelled {@code label} (e.g. to check a pre-filled form). */
    public String value(String label) {
        return field(label).getDomProperty("value");
    }

    /** Clicks the submit button and does not wait (use for forms expected to show errors). */
    public void submit() {
        actions.click(SUBMIT);
    }

    /** Closes the dialog without saving. */
    public void cancel() {
        actions.click(CANCEL);
        waitUntilClosed();
    }

    /** Submits and waits for the dialog to close (the expected outcome of a valid form). */
    public void submitAndWaitUntilClosed() {
        submit();
        waitUntilClosed();
    }

    /** Waits until the dialog is gone (it closes only after the save and the list reload finished). */
    public void waitUntilClosed() {
        wait.invisible(root);
    }

    /** Validation message under field {@code name} (the API field name, e.g. {@code email}). */
    public String fieldError(String name) {
        By error = TestId.of("error-" + name);
        // The slot exists empty from the start; wait until the API's message has been written into it
        wait.until(d -> !d.findElement(root).findElement(error).getText().isBlank(), "error for " + name);
        return child(error).getText().trim();
    }

    /** General error shown at the top of the dialog. */
    public String error() {
        wait.until(d -> !d.findElement(root).findElement(ERROR).getText().isBlank(), "modal error");
        return child(ERROR).getText().trim();
    }

    /** The input or select whose label text is exactly {@code label}. */
    private WebElement field(String label) {
        return wait.until(d -> {
            // 1. the label by its visible text (normalize-space ignores extra whitespace)
            // 2. its "for" attribute names the id of the input it belongs to
            WebElement labelElement = d.findElement(root)
                    .findElement(By.xpath(".//label[normalize-space()='" + label + "']"));
            return d.findElement(root).findElement(By.id(labelElement.getDomAttribute("for")));
        }, "field labelled '" + label + "'");
    }
}
