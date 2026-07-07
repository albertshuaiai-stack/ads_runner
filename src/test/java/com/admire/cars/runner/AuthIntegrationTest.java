package com.admire.cars.runner;

import com.admire.cars.runner.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

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

    @Value("${local.server.port}")
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    public void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

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
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        
        String token = response.getResponseBody().get("amToken").toString();

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
    public void loginWithPhoneNumber() {
        // register
        User u = new User();
        u.setUserName("phoneuser");
        u.setUserEmail("phone@example.com");
        u.setUserPhoneNumber("9876543210");
        u.setUserPassword("phonepass");

        webTestClient.post().uri("/api/users/register").bodyValue(u)
                .exchange()
                .expectStatus().isCreated();

        // login via phone
        Map<String, String> payload = Map.of("username", "9876543210", "password", "phonepass");
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        
        String token = response.getResponseBody().get("amToken").toString();

        // ping with phone token
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
}
