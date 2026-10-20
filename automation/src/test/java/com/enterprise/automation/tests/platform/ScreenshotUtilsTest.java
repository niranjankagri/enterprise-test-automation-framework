package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.utils.ScreenshotUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/** The failure-screenshot mechanism produces a real PNG file and never throws. */
@Test(groups = {"platform", "smoke", "regression"})
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

    /** Masking preserves nested nodes and handlers and restores them after successful or failed capture. */
    public void masksSensitivePageWithReversibleOverlaysAndRestoresAfterCaptureFailure() throws Exception {
        WebDriver driver = DriverManager.startDriver();
        String secretText = "SYNTHETIC_SCREENSHOT_SECRET_7F2A";
        String secretPassword = "Synthetic-Password-7F2A";
        String html = "<html><body><div data-sensitive><strong>" + secretText + "</strong>"
                + "<button id='control' type='button'>Press</button></div>"
                + "<input type='password' value='" + secretPassword + "'><script>"
                + "window.clickCount=0;const box=document.querySelector('[data-sensitive]');"
                + "const button=document.querySelector('#control');window.originalBox=box;"
                + "window.originalButton=button;window.originalMarkup=box.innerHTML;"
                + "button.addEventListener('click',()=>window.clickCount++);</script></body></html>";
        String dataUrl = "data:text/html;base64," + Base64.getEncoder()
                .encodeToString(html.getBytes(StandardCharsets.UTF_8));
        driver.get(dataUrl);

        byte[] raw = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        List<List<?>> observations = new ArrayList<>();
        byte[] masked = ScreenshotUtils.capture(screenshotProxy(driver, observations, false));
        assertThat(masked).startsWith(PNG_SIGNATURE);
        assertThat(observations).hasSize(1);
        assertMaskingObservation(observations.get(0), 1);
        assertPageRestored(driver, secretText, secretPassword, 1);
        assertThat(samePixels(masked, raw)).as("capture pixels are covered by the visual overlay").isFalse();

        byte[] failedCapture = ScreenshotUtils.capture(screenshotProxy(driver, observations, true));
        assertThat(failedCapture).isNull();
        assertThat(observations).hasSize(2);
        assertMaskingObservation(observations.get(1), 2);
        assertPageRestored(driver, secretText, secretPassword, 2);

        AtomicBoolean screenshotAttempted = new AtomicBoolean();
        byte[] unprotectedCapture = ScreenshotUtils.capture(maskingFailureProxy(driver, screenshotAttempted));
        assertThat(unprotectedCapture).isNull();
        assertThat(screenshotAttempted).isFalse();
        assertPageRestored(driver, secretText, secretPassword, 2);

        ((JavascriptExecutor) driver).executeScript("document.querySelector('#control').click();");
        assertPageRestored(driver, secretText, secretPassword, 3);
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

    private static WebDriver screenshotProxy(WebDriver delegate, List<List<?>> observations, boolean failCapture) {
        return (WebDriver) Proxy.newProxyInstance(
                WebDriver.class.getClassLoader(),
                new Class<?>[] {WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getScreenshotAs")) {
                        List<?> observation = (List<?>) ((JavascriptExecutor) delegate).executeScript(
                                "const box=document.querySelector('[data-sensitive]');"
                                        + "const button=document.querySelector('#control');"
                                        + "button.click();const overlays=[...document.querySelectorAll("
                                        + "'[data-codex-screenshot-mask-overlay]')];"
                                        + "return [box===window.originalBox,button===window.originalButton,"
                                        + "box.innerHTML===window.originalMarkup,overlays.length,"
                                        + "overlays.every(o=>o.textContent==='[REDACTED]' && "
                                        + "getComputedStyle(o).backgroundColor==='rgb(255, 255, 255)'),"
                                        + "String(window.clickCount)];");
                        observations.add(observation);
                        if (failCapture) {
                            throw new WebDriverException("synthetic screenshot capture failure");
                        }
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void assertMaskingObservation(List<?> observation, int clickCount) {
        assertThat(observation).hasSize(6);
        assertThat(observation.get(0)).isEqualTo(true);
        assertThat(observation.get(1)).isEqualTo(true);
        assertThat(observation.get(2)).isEqualTo(true);
        assertThat(observation.get(3)).isEqualTo(2L);
        assertThat(observation.get(4)).isEqualTo(true);
        assertThat(observation.get(5)).isEqualTo(Integer.toString(clickCount));
    }

    private static void assertPageRestored(WebDriver driver, String secretText, String secretPassword, int clickCount) {
        Object state = ((JavascriptExecutor) driver).executeScript(
                "const box=document.querySelector('[data-sensitive]');"
                        + "const button=document.querySelector('#control');"
                        + "return [box===window.originalBox,button===window.originalButton,"
                        + "box.innerHTML===window.originalMarkup,"
                        + "document.querySelector('input[type=password]').value,"
                        + "document.querySelectorAll('[data-codex-screenshot-mask-overlay]').length,"
                        + "String(window.clickCount)];");
        List<?> values = (List<?>) state;
        assertThat(values).hasSize(6);
        assertThat(values.get(0)).isEqualTo(true);
        assertThat(values.get(1)).isEqualTo(true);
        assertThat(values.get(2)).isEqualTo(true);
        assertThat(values.get(3)).isEqualTo(secretPassword);
        assertThat(values.get(4)).isEqualTo(0L);
        assertThat(values.get(5)).isEqualTo(Integer.toString(clickCount));
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return document.querySelector('[data-sensitive]').textContent;"))
                .isEqualTo(secretText + "Press");
    }

    private static WebDriver maskingFailureProxy(WebDriver delegate, AtomicBoolean screenshotAttempted) {
        return (WebDriver) Proxy.newProxyInstance(
                WebDriver.class.getClassLoader(),
                new Class<?>[] {WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getScreenshotAs")) {
                        screenshotAttempted.set(true);
                    }
                    if (method.getName().equals("executeScript")
                            && args[0].toString().contains("const marker = \"data-codex-screenshot-mask-overlay\"")) {
                        throw new WebDriverException("synthetic screenshot masking failure");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
