package signup;

import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import com.mailslurp.models.SendEmailOptions;
import factories.UserFactory;
import infrastructure.MailslurpService;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import utilities.ResourcesReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailInboxesLambdatestTests {
    private WebDriver driver;
    private static ApiClient defaultClient = Configuration.getDefaultApiClient();
    private static InboxControllerApi  inboxControllerApi;
    private String API_KEY = System.getenv("MAILSLURP_KEY");
    private static final Long TIMEOUT = 30000L;

    @BeforeAll
    public static void setUpClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    public void setUp() throws MalformedURLException {
        String username = System.getenv("LT_USERNAME");
        String authkey = System.getenv("LT_ACCESSKEY");
        String hub = "@hub.lambdatest.com/wd/hub";

        var capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "Chrome");
        capabilities.setCapability("browserVersion", "latest");
        HashMap<String, Object> ltOptions = new HashMap<String, Object>();
        ltOptions.put("user", username);
        ltOptions.put("accessKey", authkey);
        ltOptions.put("build", "Selenium 4");
        ltOptions.put("name",this.getClass().getName());
        ltOptions.put("platformName", "Windows 10");
        ltOptions.put("console", true);
        ltOptions.put("seCdp", true);
        ltOptions.put("selenium_version", "4.0.0");
        capabilities.setCapability("LT:Options", ltOptions);

        driver = new RemoteWebDriver(new URL("https://" + username + ":" + authkey + hub), capabilities);
        driver.manage().window().maximize();

        // IMPORTANT set timeout for the http client
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .build();

        defaultClient = Configuration.getDefaultApiClient();

        // IMPORTANT set api client timeouts
        defaultClient.setConnectTimeout(TIMEOUT.intValue());
        defaultClient.setWriteTimeout(TIMEOUT.intValue());
        defaultClient.setReadTimeout(TIMEOUT.intValue());

        // IMPORTANT set API KEY and client
        defaultClient.setHttpClient(httpClient);
        defaultClient.setApiKey(API_KEY);

        inboxControllerApi = new InboxControllerApi(defaultClient);
    }

    @Test
    public void loginSuccessfully_usingEmail() throws ApiException {
        driver.navigate().to("https://localhost:3000/");

        var emailTab = driver.findElement(By.xpath("//a[text()='Email']"));
        emailTab.click();

        // TODO: check send grid whether it sends emails
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