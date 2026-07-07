package com.admire.cars.runner;

import com.admire.cars.runner.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
    private TestRestTemplate restTemplate;

    @Test
    public void registerLoginPingLogoutFlow() {
        // register
        User u = new User();
        u.setUserName("testuser");
        u.setUserEmail("test@example.com");
        u.setUserPhoneNumber("1234567890");
        u.setUserPassword("pass123");

        ResponseEntity<Map> reg = restTemplate.postForEntity("/api/users/register", u, Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // login
        Map<String, String> payload = Map.of("username", "testuser", "password", "pass123");
        ResponseEntity<Map> login = restTemplate.postForEntity("/api/auth/login", payload, Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) ((Map)login.getBody()).get("amToken");
        assertThat(token).isNotNull();

        // ping protected
        HttpHeaders headers = new HttpHeaders();
        headers.add("AMtoken", token);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<Map> ping = restTemplate.exchange("/api/secure/ping", HttpMethod.GET, req, Map.class);
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ping.getBody().get("userId")).isNotNull();

        // logout
        ResponseEntity<Void> logout = restTemplate.exchange("/api/auth/logout", HttpMethod.POST, req, Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ping after logout should fail
        ResponseEntity<String> ping2 = restTemplate.exchange("/api/secure/ping", HttpMethod.GET, req, String.class);
        assertThat(ping2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
