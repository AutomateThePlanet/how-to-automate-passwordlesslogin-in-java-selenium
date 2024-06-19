/*
 * Copyright 2021 Automate The Planet Ltd.
 * Author: Anton Angelov
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sso;

import com.twilio.Twilio;
import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.account.Message;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSPasswordlessLoginTests {
    private WebDriver driver;
    private static String TWILIO_ACCOUNT_SID = "YOUR_ACCOUNT_SID";
    private static String TWILIO_AUTH_TOKEN = "YOUR_AUTH_TOKEN";
    private static String TWILIO_PHONE_NUMBER = "+13135137320";

    @BeforeAll
    public static void beforeAll() {
        WebDriverManager.chromedriver().setup();
        TWILIO_ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
        TWILIO_AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");
        Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
    }

    @BeforeEach
    public void testInit() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--ignore-certificate-errors");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
    }

    @AfterEach
    public void testCleanup() {
        driver.quit();
    }

    @Test
    public void loginSuccessfully_usingSms() {
        driver.navigate().to("https://localhost:3000/");
        var smsTab = driver.findElement(By.xpath("//a[text()='SMS']"));
        smsTab.click();
        var phoneInput = driver.findElement(By.id("phoneNumber"));
        phoneInput.sendKeys(TWILIO_PHONE_NUMBER);
        var sendSmsButton = driver.findElement(By.xpath("//button[text()='Send SMS Code']"));
        sendSmsButton.click();

        Message latestMessage = getLatestSMS();

        var smsCodeInput = driver.findElement(By.id("smsCode"));
        String smsCode = extractCode(latestMessage.getBody());
        smsCodeInput.sendKeys(smsCode);

        var verifyButton = driver.findElement(By.xpath("//button[text()='Verify SMS Code']"));
        verifyButton.click();


        var userName = driver.findElement(By.id("username"));

        Assertions.assertTrue(userName.getText().contains("User"));

        var logoutButton = driver.findElement(By.xpath("//a[text()='Logout']"));
        logoutButton.click();


    }


    public static Message getLatestSMS() {
        ResourceSet<Message> messages = Message.reader().limit(1).read();

        if (messages.iterator().hasNext()) {
            return messages.iterator().next();
        }
        return null;
    }

    public static String extractCode(String message) {
        // The regex pattern looks for a sequence of digits at the end of the string.
        Pattern pattern = Pattern.compile(".*\\bcode is: (\\d+)$");
        Matcher matcher = pattern.matcher(message);

        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}