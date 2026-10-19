package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.utils.ScreenshotUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/** The failure-screenshot mechanism produces a real PNG file and never throws. */
@Test(groups = "platform")
public class ScreenshotUtilsTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    /** Every test leaves no browser behind. */
    @AfterMethod(alwaysRun = true)
    public void quitBrowser() {
        DriverManager.quitDriver();
    }

    /** A screenshot is saved as a real PNG file. */
    public void savesAPngOfTheCurrentPage() throws Exception {
        DriverManager.startDriver().get("data:text/html,<h1>Screenshot</h1>");

        Path file = ScreenshotUtils.save(DriverManager.getDriver(), "ScreenshotUtilsTest");

        assertThat(file).exists();
        assertThat(Files.readAllBytes(file)).startsWith(PNG_SIGNATURE);
    }

    /**
     * A closed browser gives no screenshot instead of an error, so evidence collection never hides the real failure.
     */
    public void aClosedBrowserGivesNoScreenshotInsteadOfAnError() {
        var driver = DriverManager.startDriver();
        // Quit behind DriverManager's back: the browser is gone, the reference is not
        driver.quit();

        assertThat(ScreenshotUtils.save(driver, "closed-browser")).isNull();
    }
}
