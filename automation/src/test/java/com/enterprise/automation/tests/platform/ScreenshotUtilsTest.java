package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.utils.ScreenshotUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
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

    /** Marked text and password values are masked in screenshot pixels, then restored in the live page. */
    public void masksSensitivePageValuesForScreenshotAndRestoresThem() throws Exception {
        WebDriver driver = DriverManager.startDriver();
        String secretText = "SYNTHETIC_SCREENSHOT_SECRET_7F2A";
        String secretPassword = "Synthetic-Password-7F2A";
        String html = "<html><body><div data-sensitive>" + secretText + "</div>"
                + "<input type='password' value='" + secretPassword + "'></body></html>";
        String dataUrl = "data:text/html;base64," + Base64.getEncoder()
                .encodeToString(html.getBytes(StandardCharsets.UTF_8));
        driver.get(dataUrl);

        byte[] raw = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        byte[] masked = ScreenshotUtils.capture(driver);
        assertThat(masked).startsWith(PNG_SIGNATURE);
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return [document.querySelector('[data-sensitive]').textContent,"
                        + "document.querySelector('input[type=password]').value];"))
                .isEqualTo(java.util.List.of(secretText, secretPassword));

        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-sensitive]').textContent='[REDACTED]';"
                        + "document.querySelector('input[type=password]').value='[REDACTED]';");
        byte[] expected = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        assertThat(samePixels(masked, expected)).as("capture pixels match the redacted page").isTrue();
        assertThat(samePixels(masked, raw)).as("capture pixels do not show the synthetic text value").isFalse();
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

    private static boolean samePixels(byte[] first, byte[] second) throws Exception {
        BufferedImage firstImage = ImageIO.read(new ByteArrayInputStream(first));
        BufferedImage secondImage = ImageIO.read(new ByteArrayInputStream(second));
        if (firstImage.getWidth() != secondImage.getWidth() || firstImage.getHeight() != secondImage.getHeight()) {
            return false;
        }
        for (int y = 0; y < firstImage.getHeight(); y++) {
            for (int x = 0; x < firstImage.getWidth(); x++) {
                if (firstImage.getRGB(x, y) != secondImage.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
