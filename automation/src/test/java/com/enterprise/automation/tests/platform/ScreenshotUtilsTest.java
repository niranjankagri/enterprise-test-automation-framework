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

    @AfterMethod(alwaysRun = true)
    public void quitBrowser() {
        DriverManager.quitDriver();
    }

    public void savesAPngOfTheCurrentPage() throws Exception {
        DriverManager.startDriver().get("data:text/html,<h1>Screenshot</h1>");

        Path file = ScreenshotUtils.save(DriverManager.getDriver(), "ScreenshotUtilsTest");

        assertThat(file).exists();
        assertThat(Files.readAllBytes(file)).startsWith(PNG_SIGNATURE);
    }

    public void aClosedBrowserGivesNoScreenshotInsteadOfAnError() {
        var driver = DriverManager.startDriver();
        driver.quit();

        assertThat(ScreenshotUtils.save(driver, "closed-browser")).isNull();
    }
}
