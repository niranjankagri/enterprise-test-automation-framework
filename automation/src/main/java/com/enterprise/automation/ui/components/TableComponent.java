package com.enterprise.automation.ui.components;

import com.enterprise.automation.ui.BaseComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * A data table read by column name: each row becomes a {@code Map<column, cell text>}.
 *
 * <p>Tests then say {@code table.row("Email", email).get("City")} instead of counting columns,
 * so adding or moving a column does not break them.
 */
public class TableComponent extends BaseComponent {

    private static final By HEADERS = By.cssSelector("thead th");
    private static final By BODY_ROWS = By.cssSelector("tbody tr");
    private static final By EMPTY_ROW = By.cssSelector("tbody tr[data-testid='empty-row']");

    public TableComponent(By root) {
        super(root);
    }

    /** Column names as written in the markup (CSS shows them upper-case; the DOM text is used). */
    public List<String> headers() {
        return rootElement().findElements(HEADERS).stream()
                .map(th -> th.getDomProperty("textContent").trim())
                .toList();
    }

    /** All rows; an "empty" placeholder row counts as no rows. Read inside a wait, so a re-render is retried. */
    public List<Map<String, String>> rows() {
        return wait.until(d -> {
            WebElement table = d.findElement(root);
            if (!table.findElements(EMPTY_ROW).isEmpty()) {
                return List.<Map<String, String>>of();
            }
            List<String> headers = headers();
            List<Map<String, String>> rows = new ArrayList<>();
            for (WebElement tr : table.findElements(BODY_ROWS)) {
                List<WebElement> cells = tr.findElements(By.tagName("td"));
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < cells.size(); i++) {
                    row.put(headers.get(i), cells.get(i).getText().trim());
                }
                rows.add(row);
            }
            return rows;
        }, "rows of " + root);
    }

    public int rowCount() {
        return rows().size();
    }

    public boolean isEmpty() {
        return rows().isEmpty();
    }

    /** All values of one column, top to bottom. */
    public List<String> column(String name) {
        return rows().stream().map(r -> r.get(name)).toList();
    }

    /** The first row whose {@code column} equals {@code value}; fails with the table content if none. */
    public Map<String, String> row(String column, String value) {
        return rows().stream().filter(r -> value.equals(r.get(column))).findFirst()
                .orElseThrow(() -> new AssertionError("No row with " + column + " = '" + value + "' in " + root
                        + ". Rows: " + rows()));
    }

    public boolean hasRow(String column, String value) {
        return rows().stream().anyMatch(r -> value.equals(r.get(column)));
    }

    /** Clicks {@code element} (e.g. an Edit button) in the row whose {@code column} equals {@code value}. */
    public void clickInRow(String column, String value, By element) {
        wait.until(d -> {
            int index = indexOf(column, value);
            if (index < 0) {
                return null;
            }
            WebElement tr = d.findElement(root).findElements(BODY_ROWS).get(index);
            tr.findElement(element).click();
            return true;
        }, "click " + element + " in row " + column + " = '" + value + "'");
    }

    private int indexOf(String column, String value) {
        List<Map<String, String>> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            if (value.equals(rows.get(i).get(column))) {
                return i;
            }
        }
        return -1;
    }
}
