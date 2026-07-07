package com.admire.cars.runner;

import com.admire.cars.runner.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "jwt.secret=test-secret",
        "jwt.expiration-seconds=60"
})
public class AuthIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void registerLoginPingLogoutFlow() {
        // register
        User u = new User();
        u.setUserName("testuser");
        u.setUserEmail("test@example.com");
        u.setUserPhoneNumber("1234567890");
        u.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(u)
                .exchange()
                .expectStatus().isCreated();

        // login
        Map<String, String> payload = Map.of("username", "testuser", "password", "pass123");
        var loginResp = webTestClient.post().uri("/api/auth/login").bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody();

        assertThat(loginResp).isNotNull();
        String token = (String) loginResp.get("amToken");
        assertThat(token).isNotNull();

        // ping protected
        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();

        // logout
        webTestClient.post().uri("/api/auth/logout")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();

        // ping after logout should fail
        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    public void loginWithEmailAndPhone() {
        // register a user usable by email and phone
        User u = new User();
        u.setUserName("multiuser");
        u.setUserEmail("multi@example.com");
        u.setUserPhoneNumber("1999999999");
        u.setUserPassword("secret");

        webTestClient.post().uri("/api/users/register").bodyValue(u)
                .exchange()
                .expectStatus().isCreated();

        // login via email
        Map<String, String> payloadEmail = Map.of("username", "multi@example.com", "password", "secret");
        var loginEmailResp = webTestClient.post().uri("/api/auth/login").bodyValue(payloadEmail)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody();

        assertThat(loginEmailResp).isNotNull();
        String tokenEmail = (String) loginEmailResp.get("amToken");
        assertThat(tokenEmail).isNotNull();

        // ping via email token
        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", tokenEmail)
                .exchange()
                .expectStatus().isOk();

        // logout email token
        webTestClient.post().uri("/api/auth/logout")
                .header("AMtoken", tokenEmail)
                .exchange()
                .expectStatus().isOk();

        // login via phone
        Map<String, String> payloadPhone = Map.of("username", "1999999999", "password", "secret");
        var loginPhoneResp = webTestClient.post().uri("/api/auth/login").bodyValue(payloadPhone)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody();

        assertThat(loginPhoneResp).isNotNull();
        String tokenPhone = (String) loginPhoneResp.get("amToken");
        assertThat(tokenPhone).isNotNull();

        // ping via phone token
        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", tokenPhone)
                .exchange()
                .expectStatus().isOk();

        // logout phone token
        webTestClient.post().uri("/api/auth/logout")
                .header("AMtoken", tokenPhone)
                .exchange()
                .expectStatus().isOk();

        // ping after logout should fail
        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", tokenPhone)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
