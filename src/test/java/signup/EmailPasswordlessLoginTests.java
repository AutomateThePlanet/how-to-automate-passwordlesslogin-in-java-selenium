package signup;

import factories.UserFactory;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.mailslurp.apis.*;
import com.mailslurp.clients.*;
import com.mailslurp.models.*;
import utilities.ResourcesReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailPasswordlessLoginTests {
    private static ApiClient defaultClient;
    private static InboxControllerApi inboxControllerApi;
    private static String API_KEY = System.getenv("MAILSLURP_KEY");
    private WebDriver driver;
    private static final Long TIMEOUT = 30000L;

    @BeforeAll
    public static void setUpClass() {
        WebDriverManager.chromedriver().clearDriverCache().setup();
       WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    public void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--ignore-certificate-errors");
        driver = new ChromeDriver(options);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .build();

        defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setConnectTimeout(TIMEOUT.intValue());
        defaultClient.setWriteTimeout(TIMEOUT.intValue());
        defaultClient.setReadTimeout(TIMEOUT.intValue());
        defaultClient.setHttpClient(httpClient);
        defaultClient.setApiKey(API_KEY);

        inboxControllerApi = new InboxControllerApi(defaultClient);
    }

    @Test
    public void loginSuccessfully_usingEmail() throws ApiException {
        driver.navigate().to("https://localhost:3000/");

        var emailTab = driver.findElement(By.xpath("//a[text()='Email']"));
        emailTab.click();

        InboxDto inbox = inboxControllerApi.createInbox(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        var emailInput = driver.findElement(By.id("email"));
        emailInput.sendKeys(inbox.getEmailAddress());

        var sendLoginCode = driver.findElement(By.xpath("//button[text()='Send Login Code']"));
        sendLoginCode.click();

        var waitForControllerApi = new WaitForControllerApi(defaultClient);
        var currentTime = OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        Email receivedEmail = waitForControllerApi
                .waitForLatestEmail(inbox.getId(), TIMEOUT, false, null, currentTime, null, 10000L);

        var emailCodeInput = driver.findElement(By.id("code"));
        String emailCode = extractCode(receivedEmail.getBody());
        emailCodeInput.sendKeys(emailCode);

        var verifyButton = driver.findElement(By.xpath("//button[text()='Verify Code']"));
        verifyButton.click();

        var userName = driver.findElement(By.id("username"));

        Assertions.assertTrue(userName.getText().contains("User"));

        var logoutButton = driver.findElement(By.xpath("//a[text()='Logout']"));
        logoutButton.click();
    }

    @Test
    public void interactWithEmailBody() throws ApiException, IOException {
        var user = UserFactory.createDefault();

        var inboxControllerApi = new InboxControllerApi(defaultClient);
        InboxDto inbox = inboxControllerApi.createInbox(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        String email = inbox.getEmailAddress();
        user.setEmail(email);

        sendEmail(inbox, email);

        var currentTime = OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        var waitForControllerApi = new WaitForControllerApi(defaultClient);
        Email receivedEmail = waitForControllerApi
                .waitForLatestEmail(inbox.getId(), TIMEOUT, false, null, currentTime, null, 10000L);

        loadEmailBody(driver, receivedEmail.getBody());

        var myAccountLink = driver.findElement(By.xpath("//a[contains(text(), 'My Account')]"));
        myAccountLink.click();

        var wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.urlToBe("https://accounts.lambdatest.com/login"));
    }

    @SneakyThrows
    private static void sendEmail(InboxDto inbox, String toEmail) throws ApiException {
        var emailBody = ResourcesReader.getFileAsString(EmailPasswordlessLoginTests.class, "sample-email.html");
        // send HTML body email
        SendEmailOptions sendEmailOptions = new SendEmailOptions()
                .to(Collections.singletonList(toEmail))
                .subject("HTML BODY email Interaction")
                .body(emailBody);

        inboxControllerApi.sendEmail(inbox.getId(), sendEmailOptions);
    }

    @SneakyThrows
    private static String loadEmailBody(WebDriver driver, String htmlBody) throws IOException {
        htmlBody = htmlBody.replace("\n", "").replace("\\/", "/").replace("\\\"", "\"");
        //String fileName = String.format("%s.html", TimestampBuilder.getGuid());
        var file = writeStringToTempFile(htmlBody);
        driver.get(file.toPath().toUri().toString());

        //driver.get("http://local-folder.lambdatest.com/" + fileName);

        return htmlBody;
    }

    private static File writeStringToTempFile(String fileContent) throws IOException {
        Path tempFile = Files.createTempFile(null, ".html");
        try (var bw = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
            bw.write(fileContent);
        }
        return tempFile.toFile();
    }

    public static String extractCode(String message) {
        // The regex pattern looks for a sequence of digits at the end of the string.
        Pattern pattern = Pattern.compile(".*\\bcode is: (\\d+)\\s*$");
        Matcher matcher = pattern.matcher(message);

        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
