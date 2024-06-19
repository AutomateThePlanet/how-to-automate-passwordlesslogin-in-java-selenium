package infrastructure;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

public class AuthCookieGenerator {

    private static final String BASE_URI = "https://localhost:3000/";

    public static String generateAuthCookie(String displayName, String password, String userid) {
        Response response = RestAssured.given()
                .baseUri(BASE_URI)
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "displayName", displayName,
                        "password", password,
                        "userid", userid
                ))
                .when()
                .post("/generate-auth-cookie")
                .then()
                .statusCode(200)
                .extract()
                .response();

        return response.getDetailedCookie("auth").getValue();
    }
}
