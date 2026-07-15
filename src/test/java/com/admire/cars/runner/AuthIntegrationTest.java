package com.admire.cars.runner;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.UserRepository;
import com.admire.cars.runner.security.PasswordCryptoService;
import io.jsonwebtoken.Jwts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.quartz.Trigger.TriggerState;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "spring.quartz.job-store-type=memory",
        "jwt.secret=test-secret",
        "jwt.expiration-seconds=60"
})
public class AuthIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-seconds}")
    private long jwtExpirationSeconds;

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private AdsMatrixInfoRepository adsMatrixInfoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private PasswordCryptoService passwordCryptoService;

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

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "testuser", "password", "pass123"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("User not found with provided email or phone number");

        // login
        Map<String, String> payload = Map.of("loginId", "test@example.com", "password", "pass123");
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
    public void loginTokenHasConfiguredExpiration() {
        User user = new User();
        user.setUserName("expiryuser");
        user.setUserEmail("expiry@example.com");
        user.setUserPhoneNumber("1234500000");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        var response = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "expiry@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = response.getResponseBody().get("amToken").toString();
        String key = Base64.getEncoder().encodeToString(jwtSecret.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();

        long issuedAtMillis = claims.getIssuedAt().getTime();
        long expirationMillis = claims.getExpiration().getTime();
        long diffSeconds = (expirationMillis - issuedAtMillis) / 1000;

        org.junit.jupiter.api.Assertions.assertEquals(jwtExpirationSeconds, diffSeconds);
    }

    @Test
    public void registerAcceptsDateOnlyExpireDate() {
        webTestClient.post().uri("/api/users/register")
                .bodyValue(Map.of(
                        "userName", "dateduser",
                        "userEmail", "dated@example.com",
                        "userPhoneNumber", "8888888888",
                        "userPassword", "pass123",
                        "expireDate", "2026-08-09"))
                .exchange()
                .expectStatus().isCreated();

        User savedUser = userRepository.findByUserName("dateduser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.time.LocalDateTime.of(2026, 8, 9, 0, 0),
                savedUser.getExpireDate());
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

        // login via phone using legacy username field for backward compatibility
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

    @Test
    public void loginReturnsSpecificFailureDetails() {
        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("password", "pass123"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("loginId is required");

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "missing@example.com"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("password is required");

        User user = new User();
        user.setUserName("loginfailureuser");
        user.setUserEmail("loginfailure@example.com");
        user.setUserPhoneNumber("1555000001");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "unknown@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("User not found with provided email or phone number");

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "loginfailure@example.com", "password", "wrongpass"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Password is incorrect");

        User savedUser = userRepository.findByUserPhoneNumber("1555000001").orElseThrow();
        savedUser.setStatus("DISABLED");
        userRepository.save(savedUser);

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "loginfailure@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("User is disabled");
    }

    @Test
    public void changePasswordRequiresOldPasswordAndInvalidatesOldToken() {
        User user = new User();
        user.setUserName("changepwduser");
        user.setUserEmail("changepwd@example.com");
        user.setUserPhoneNumber("13500000000");
        user.setUserPassword("oldpass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        var loginResponse = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "changepwd@example.com", "password", "oldpass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/users/change-password")
                .header("AMtoken", token)
                .bodyValue(Map.of("oldPassword", "wrong-old", "newPassword", "newpass123"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Old password is incorrect");

        webTestClient.post().uri("/api/users/change-password")
                .header("AMtoken", token)
                .bodyValue(Map.of("oldPassword", "oldpass123", "newPassword", "newpass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Password changed successfully");

        webTestClient.get().uri("/api/secure/ping")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "changepwd@example.com", "password", "oldpass123"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Password is incorrect");

        webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "changepwd@example.com", "password", "newpass123"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void loginIncludesUserProfileAndAdCounts() {
        User user = new User();
        user.setUserName("profileuser");
        user.setUserEmail("profile@example.com");
        user.setUserPhoneNumber("1111222233");
        user.setUserPassword("pass123");
        user.setUserRole("Admin,Matrix");
        user.setExpireDate(java.time.LocalDateTime.of(2026, 12, 31, 23, 59));

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        AdsNormalInfo normalOne = new AdsNormalInfo();
        normalOne.setCampainName("normal-1");
        normalOne.setCampainCountry("US");
        normalOne.setPlatformName("Facebook");
        normalOne.setAdsOwner("1111222233");
        adsNormalInfoRepository.save(normalOne);

        AdsNormalInfo normalTwo = new AdsNormalInfo();
        normalTwo.setCampainName("normal-2");
        normalTwo.setCampainCountry("US");
        normalTwo.setPlatformName("Google");
        normalTwo.setAdsOwner("1111222233");
        adsNormalInfoRepository.save(normalTwo);

        AdsMatrixInfo matrix = new AdsMatrixInfo();
        matrix.setCampainName("profile-campaign-1");
        matrix.setCampainCountry("SG");
        matrix.setAdsOwner("1111222233");
        adsMatrixInfoRepository.save(matrix);

        Map<String, String> payload = Map.of("loginId", "profile@example.com", "password", "pass123");
        webTestClient.post().uri("/api/auth/login")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amToken").exists()
                .jsonPath("$.userName").isEqualTo("profileuser")
                .jsonPath("$.userRole").isEqualTo("Admin,Matrix")
                .jsonPath("$.roles").isEqualTo("Admin,Matrix")
                .jsonPath("$.normalAdsTotalCount").isEqualTo(2)
                .jsonPath("$.matrixAdsTotalCount").isEqualTo(1)
                .jsonPath("$.expireDate").exists();
    }

    @Test
    public void queryUsersWithPaginationAndFilters() {
        User first = new User();
        first.setUserName("pageduser1");
        first.setUserEmail("pageduser1@example.com");
        first.setUserPhoneNumber("1111111111");
        first.setUserPassword("pass123");

        User second = new User();
        second.setUserName("pageduser2");
        second.setUserEmail("pageduser2@example.com");
        second.setUserPhoneNumber("2222222222");
        second.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(first)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/users/register").bodyValue(second)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> payload = Map.of("loginId", "pageduser1@example.com", "password", "pass123");
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = response.getResponseBody().get("amToken").toString();

        webTestClient.get().uri("/api/users?page=0&size=1&userName=pageduser")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].userName").isEqualTo("pageduser2");

        webTestClient.get().uri("/api/users?page=0&size=10&phoneNumber=1111111111")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].userPhoneNumber").isEqualTo("1111111111");

        webTestClient.get().uri("/api/users?page=0&size=10&email=pageduser2@example.com")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].userEmail").isEqualTo("pageduser2@example.com");
    }

    @Test
    public void crudRoles() {
        User user = new User();
        user.setUserName("roleadmin");
        user.setUserEmail("roleadmin@example.com");
        user.setUserPhoneNumber("3333333333");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("loginId", "roleadmin@example.com", "password", "pass123");
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = response.getResponseBody().get("amToken").toString();

        Map<String, String> rolePayload = Map.of("roleName", "Admin");
        var createRoleResponse = webTestClient.post().uri("/api/roles")
                .header("AMtoken", token)
                .bodyValue(rolePayload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long roleId = ((Number) createRoleResponse.getResponseBody().get("id")).longValue();
        webTestClient.post().uri("/api/roles")
                .header("AMtoken", token)
                .bodyValue(Map.of("roleName", "Matrix"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/roles")
                .header("AMtoken", token)
                .bodyValue(Map.of("roleName", "Both"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/roles")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].roleName").exists();

        webTestClient.put().uri("/api/roles/" + roleId)
                .header("AMtoken", token)
                .bodyValue(Map.of("roleName", "Normal"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.roleName").isEqualTo("Normal");

        webTestClient.delete().uri("/api/roles/" + roleId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void crudPlatformsWithPagination() {
        User user = new User();
        user.setUserName("platformadmin");
        user.setUserEmail("platformadmin@example.com");
        user.setUserPhoneNumber("4444444444");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("loginId", "platformadmin@example.com", "password", "pass123");
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = response.getResponseBody().get("amToken").toString();

        var createResponse = webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "platformName", "Google Ads",
                        "paymentMethod", "CPC",
                        "remarks", "Search platform"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long platformId = ((Number) createResponse.getResponseBody().get("id")).longValue();
        org.junit.jupiter.api.Assertions.assertEquals("CPC", ((Map<?, ?>) createResponse.getResponseBody().get("data")).get("paymentMethod"));

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "platformName", "Meta Ads",
                        "paymentMethod", "CPM",
                        "remarks", "Social platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/platforms?page=0&size=1")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1);

        var allPlatformsResponse = webTestClient.get().uri("/api/platforms/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allPlatforms = (List<Map<String, Object>>) (List<?>) allPlatformsResponse.getResponseBody();
        org.junit.jupiter.api.Assertions.assertTrue(allPlatforms.stream()
                .anyMatch(platform -> "Google Ads".equals(platform.get("platformName"))));
        org.junit.jupiter.api.Assertions.assertTrue(allPlatforms.stream()
                .anyMatch(platform -> "Meta Ads".equals(platform.get("platformName"))));

        webTestClient.get().uri("/api/platforms/" + platformId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.platformName").isEqualTo("Google Ads");

        webTestClient.put().uri("/api/platforms/" + platformId)
                .header("AMtoken", token)
                .bodyValue(Map.of("paymentMethod", "CPA", "remarks", "Updated platform"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.paymentMethod").isEqualTo("CPA")
                .jsonPath("$.data.remarks").isEqualTo("Updated platform");

        webTestClient.delete().uri("/api/platforms/" + platformId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void crudNormalAdsWithPaginationAndFilters() {
        User user = new User();
        user.setUserName("normaladsowner");
        user.setUserEmail("normaladsowner@example.com");
        user.setUserPhoneNumber("5555555555");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("loginId", "normaladsowner@example.com", "password", "pass123");
        var loginResponse = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "TikTok Ads", "remarks", "Short video platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Summer Sale",
                        "campainCountry", "US",
                        "platformName", "TikTok Ads",
                        "affiliteUrl", "https://example.com/aff",
                        "landingPageUrl", "https://example.com/landing",
                        "dynamicProxyInfo", "proxy-1",
                        "dynamicProxyInfoBackup", "proxy-2",
                        "intervalTime", 30,
                        "status", "RUNNING",
                        "adsOwner", "5555555555"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Winter Sale",
                        "campainCountry", "US",
                        "platformName", "TikTok Ads",
                        "status", "PAUSED",
                        "adsOwner", "5555555555"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/normal-ads?page=0&size=1&campainName=Sale")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(2);

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&platformName=TikTok")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&status=PAUSED")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].status").isEqualTo("PAUSED");

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&adsOwner=5555555555")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    public void nonAdminOnlySeesOwnNormalMatrixAndShiftLinkRecordsWhileAdminSeesAll() {
        User admin = new User();
        admin.setUserName("scopeadmin");
        admin.setUserEmail("scopeadmin@example.com");
        admin.setUserPhoneNumber("7000000001");
        admin.setUserPassword("pass123");
        admin.setUserRole("Admin");

        User ownerOne = new User();
        ownerOne.setUserName("scopeowner1");
        ownerOne.setUserEmail("scopeowner1@example.com");
        ownerOne.setUserPhoneNumber("7000000002");
        ownerOne.setUserPassword("pass123");

        User ownerTwo = new User();
        ownerTwo.setUserName("scopeowner2");
        ownerTwo.setUserEmail("scopeowner2@example.com");
        ownerTwo.setUserPhoneNumber("7000000003");
        ownerTwo.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(admin).exchange().expectStatus().isCreated();
        webTestClient.post().uri("/api/users/register").bodyValue(ownerOne).exchange().expectStatus().isCreated();
        webTestClient.post().uri("/api/users/register").bodyValue(ownerTwo).exchange().expectStatus().isCreated();

        String adminToken = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "scopeadmin@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody().get("amToken").toString();

        String ownerOneToken = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "scopeowner1@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody().get("amToken").toString();

        String ownerTwoToken = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "scopeowner2@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", adminToken)
                .bodyValue(Map.of("platformName", "Scoped Normal Platform", "remarks", "scope"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", adminToken)
                .bodyValue(Map.of("platformName", "Scoped Matrix Platform", "remarks", "scope"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", ownerOneToken)
                .bodyValue(Map.of(
                        "campainName", "Scope Normal One",
                        "campainCountry", "US",
                        "platformName", "Scoped Normal Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", ownerTwoToken)
                .bodyValue(Map.of(
                        "campainName", "Scope Normal Two",
                        "campainCountry", "US",
                        "platformName", "Scoped Normal Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", ownerOneToken)
                .bodyValue(Map.of(
                        "campainName", "Scope Matrix One",
                        "campainCountry", "US",
                        "status", "RUNNING",
                        "affiliateInfos", java.util.List.of(
                                Map.of("platformName", "Scoped Matrix Platform", "affiliteUrl", "https://example.com/one"))))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", ownerTwoToken)
                .bodyValue(Map.of(
                        "campainName", "Scope Matrix Two",
                        "campainCountry", "US",
                        "status", "RUNNING",
                        "affiliateInfos", java.util.List.of(
                                Map.of("platformName", "Scoped Matrix Platform", "affiliteUrl", "https://example.com/two"))))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", ownerOneToken)
                .bodyValue(Map.of(
                        "adsType", "Normal",
                        "adsName", "Scope Normal One",
                        "platformName", "Scoped Normal Platform",
                        "fullUrl", "https://example.com/shift-one",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", ownerTwoToken)
                .bodyValue(Map.of(
                        "adsType", "Normal",
                        "adsName", "Scope Normal Two",
                        "platformName", "Scoped Normal Platform",
                        "fullUrl", "https://example.com/shift-two",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", ownerOneToken)
                .bodyValue(Map.of("platformName", "Scoped Filter Platform", "remarks", "filter scope"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", ownerOneToken)
                .bodyValue(Map.of(
                        "adsType", "Normal",
                        "adsName", "Scope Filter Target",
                        "platformName", "Scoped Filter Platform",
                        "fullUrl", "https://example.com/shift-filter-target",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&campainName=Scope&adsOwner=7000000003")
                .header("AMtoken", ownerOneToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].adsOwner").isEqualTo("7000000002");

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&campainName=Scope")
                .header("AMtoken", ownerOneToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].adsOwner").isEqualTo("7000000002");

        webTestClient.get().uri("/api/shift-links?page=0&size=10&platformName=Scoped Normal Platform&adsOwner=7000000003")
                .header("AMtoken", ownerOneToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].adsOwner").isEqualTo("7000000002");

        webTestClient.get().uri("/api/shift-links?page=0&size=10&adsType=Normal&adsName=Scope Filter Target&platformName=Scoped Filter Platform")
                .header("AMtoken", ownerOneToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].adsType").isEqualTo("Normal")
                .jsonPath("$.content[0].adsName").isEqualTo("Scope Filter Target")
                .jsonPath("$.content[0].platformName").isEqualTo("Scoped Filter Platform");

        var nonAdminShiftLinks = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", ownerOneToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();
        long nonAdminScopedCount = ((java.util.List<?>) nonAdminShiftLinks.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "7000000002".equals(map.get("adsOwner"))
                        && "Scoped Normal Platform".equals(map.get("platformName")))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(1L, nonAdminScopedCount);

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&campainName=Scope")
                .header("AMtoken", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&campainName=Scope")
                .header("AMtoken", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);

        webTestClient.get().uri("/api/shift-links?page=0&size=10&platformName=Scoped Normal Platform")
                .header("AMtoken", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);

        var adminShiftLinks = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();
        long adminScopedCount = ((java.util.List<?>) adminShiftLinks.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && ("7000000002".equals(map.get("adsOwner")) || "7000000003".equals(map.get("adsOwner")))
                        && "Scoped Normal Platform".equals(map.get("platformName")))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(2L, adminScopedCount);
    }

    @Test
    public void createShiftLinkResolvesAdsIdAndFallsBackToZeroWhenAdsMissing() {
        User user = new User();
        user.setUserName("shiftlinkowner");
        user.setUserEmail("shiftlinkowner@example.com");
        user.setUserPhoneNumber("1777000001");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        var loginResponse = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "1777000001", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Shift Link Platform", "remarks", "Shift link platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Shift Linked Campaign",
                        "campainCountry", "US",
                        "platformName", "Shift Link Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        Long expectedAdsId = adsNormalInfoRepository.findByCampainNameAndAdsOwner("Shift Linked Campaign", "1777000001")
                .orElseThrow()
                .getId();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "adsType", "Normal",
                        "adsName", "Shift Linked Campaign",
                        "platformName", "Shift Link Platform",
                        "fullUrl", "https://example.com/shift-linked",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.adsOwner").isEqualTo("1777000001")
                .jsonPath("$.data.adsId").isEqualTo(expectedAdsId.intValue());

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "adsType", "Normal",
                        "adsName", "Shift Missing Campaign",
                        "platformName", "Shift Link Platform",
                        "fullUrl", "https://example.com/shift-missing",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.adsOwner").isEqualTo("1777000001")
                .jsonPath("$.data.adsId").isEqualTo(0);
    }

    @Test
    public void shiftLinkBulkUploadUsesUpdatedHeadersAndMappings() throws Exception {
        User user = new User();
        user.setUserName("bulkshiftuser");
        user.setUserEmail("bulkshift@example.com");
        user.setUserPhoneNumber("1777000002");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "bulkshift@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Bulk Upload Platform", "remarks", "Bulk upload platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Bulk Upload Normal",
                        "campainCountry", "US",
                        "platformName", "Bulk Upload Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Bulk Upload Matrix",
                        "campainCountry", "US",
                        "status", "RUNNING",
                        "affiliateInfos", java.util.List.of(
                                Map.of("platformName", "Bulk Upload Platform", "affiliteUrl", "https://example.com/matrix-bulk"))))
                .exchange()
                .expectStatus().isCreated();

        Long expectedNormalAdsId = adsNormalInfoRepository.findByCampainNameAndAdsOwner("Bulk Upload Normal", "1777000002")
                .orElseThrow()
                .getId();
        Long expectedMatrixAdsId = adsMatrixInfoRepository.findByCampainNameAndAdsOwner("Bulk Upload Matrix", "1777000002")
                .orElseThrow()
                .getId();

        byte[] templateBytes = webTestClient.get().uri("/api/shift-links/template")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(templateBytes))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            org.junit.jupiter.api.Assertions.assertEquals("Ads_Type", header.getCell(0).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Ads_Name", header.getCell(1).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Platform_Name", header.getCell(2).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Full_URL", header.getCell(3).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Landing_Page_URL", header.getCell(4).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Dsplay_Number", header.getCell(5).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Remarks", header.getCell(6).getStringCellValue());
        }

        byte[] uploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ads_Type");
            header.createCell(1).setCellValue("Ads_Name");
            header.createCell(2).setCellValue("Platform_Name");
            header.createCell(3).setCellValue("Full_URL");
            header.createCell(4).setCellValue("Landing_Page_URL");
            header.createCell(5).setCellValue("Dsplay_Number");
            header.createCell(6).setCellValue("Remarks");

            Row normalRow = sheet.createRow(1);
            normalRow.createCell(0).setCellValue("Normal");
            normalRow.createCell(1).setCellValue("Bulk Upload Normal");
            normalRow.createCell(2).setCellValue("Bulk Upload Platform");
            normalRow.createCell(3).setCellValue("https://example.com/bulk-normal");
            normalRow.createCell(4).setCellValue("https://example.com/landing-normal");
            normalRow.createCell(5).setCellValue(12);
            normalRow.createCell(6).setCellValue("Bulk normal remarks");

            Row matrixRow = sheet.createRow(2);
            matrixRow.createCell(0).setCellValue("Matrix");
            matrixRow.createCell(1).setCellValue("Bulk Upload Matrix");
            matrixRow.createCell(2).setCellValue("Bulk Upload Platform");
            matrixRow.createCell(3).setCellValue("https://example.com/bulk-matrix");
            matrixRow.createCell(4).setCellValue("https://example.com/landing-matrix");
            matrixRow.createCell(5).setCellValue(18);
            matrixRow.createCell(6).setCellValue("Bulk matrix remarks");

            workbook.write(out);
            uploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(uploadBytes) {
                    @Override
                    public String getFilename() {
                        return "shift-link-upload.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.rowCount").isEqualTo(2)
                .jsonPath("$.insertedCount").isEqualTo(2);

        var shiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.Map<?, ?> normalLink = ((java.util.List<?>) shiftLinksResponse.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "https://example.com/bulk-normal".equals(map.get("fullUrl")))
                .map(item -> (java.util.Map<?, ?>) item)
                .findFirst()
                .orElseThrow();
        java.util.Map<?, ?> matrixLink = ((java.util.List<?>) shiftLinksResponse.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "https://example.com/bulk-matrix".equals(map.get("fullUrl")))
                .map(item -> (java.util.Map<?, ?>) item)
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals("Normal", normalLink.get("adsType"));
        org.junit.jupiter.api.Assertions.assertEquals("Bulk Upload Normal", normalLink.get("adsName"));
        org.junit.jupiter.api.Assertions.assertEquals("1777000002", normalLink.get("adsOwner"));
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com/landing-normal", normalLink.get("landingPageUrl"));
        org.junit.jupiter.api.Assertions.assertEquals(12, ((Number) normalLink.get("displayNumber")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(expectedNormalAdsId.intValue(), ((Number) normalLink.get("adsId")).intValue());

        org.junit.jupiter.api.Assertions.assertEquals("Matrix", matrixLink.get("adsType"));
        org.junit.jupiter.api.Assertions.assertEquals("Bulk Upload Matrix", matrixLink.get("adsName"));
        org.junit.jupiter.api.Assertions.assertEquals("1777000002", matrixLink.get("adsOwner"));
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com/landing-matrix", matrixLink.get("landingPageUrl"));
        org.junit.jupiter.api.Assertions.assertEquals(18, ((Number) matrixLink.get("displayNumber")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(expectedMatrixAdsId.intValue(), ((Number) matrixLink.get("adsId")).intValue());
    }

    @Test
    public void shiftLinkBulkUploadReplacesScopedRowsAndResetsSeqNumbers() throws Exception {
        User user = new User();
        user.setUserName("bulkreplaceuser");
        user.setUserEmail("bulkreplace@example.com");
        user.setUserPhoneNumber("1777000006");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "bulkreplace@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Bulk Replace Platform", "remarks", "Bulk replace platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Bulk Replace Campaign",
                        "campainCountry", "US",
                        "platformName", "Bulk Replace Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        byte[] firstUploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ads_Type");
            header.createCell(1).setCellValue("Ads_Name");
            header.createCell(2).setCellValue("Platform_Name");
            header.createCell(3).setCellValue("Full_URL");
            header.createCell(4).setCellValue("Landing_Page_URL");
            header.createCell(5).setCellValue("Dsplay_Number");
            header.createCell(6).setCellValue("Remarks");

            Row rowOne = sheet.createRow(1);
            rowOne.createCell(0).setCellValue("Normal");
            rowOne.createCell(1).setCellValue("Bulk Replace Campaign");
            rowOne.createCell(2).setCellValue("Bulk Replace Platform");
            rowOne.createCell(3).setCellValue("https://example.com/replace-1");
            rowOne.createCell(4).setCellValue("https://example.com/landing-1");
            rowOne.createCell(5).setCellValue(11);
            rowOne.createCell(6).setCellValue("First row");

            Row rowTwo = sheet.createRow(2);
            rowTwo.createCell(0).setCellValue("Normal");
            rowTwo.createCell(1).setCellValue("Bulk Replace Campaign");
            rowTwo.createCell(2).setCellValue("Bulk Replace Platform");
            rowTwo.createCell(3).setCellValue("https://example.com/replace-2");
            rowTwo.createCell(4).setCellValue("https://example.com/landing-2");
            rowTwo.createCell(5).setCellValue(22);
            rowTwo.createCell(6).setCellValue("Second row");

            workbook.write(out);
            firstUploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder firstBodyBuilder = new MultipartBodyBuilder();
        firstBodyBuilder.part("file", new ByteArrayResource(firstUploadBytes) {
                    @Override
                    public String getFilename() {
                        return "bulk-replace-first.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(firstBodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rowCount").isEqualTo(2)
                .jsonPath("$.insertedCount").isEqualTo(2);

        var firstShiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.List<java.util.Map<?, ?>> firstScopedLinks = new java.util.ArrayList<>();
        for (Object item : (java.util.List<?>) firstShiftLinksResponse.getResponseBody()) {
            if (item instanceof java.util.Map<?, ?> map
                    && "Bulk Replace Campaign".equals(map.get("adsName"))
                    && "1777000006".equals(map.get("adsOwner"))
                    && "Normal".equals(map.get("adsType"))) {
                firstScopedLinks.add(map);
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(2, firstScopedLinks.size());
        org.junit.jupiter.api.Assertions.assertTrue(firstScopedLinks.stream().anyMatch(map -> ((Number) map.get("seqNumber")).intValue() == 1));
        org.junit.jupiter.api.Assertions.assertTrue(firstScopedLinks.stream().anyMatch(map -> ((Number) map.get("seqNumber")).intValue() == 2));

        byte[] secondUploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ads_Type");
            header.createCell(1).setCellValue("Ads_Name");
            header.createCell(2).setCellValue("Platform_Name");
            header.createCell(3).setCellValue("Full_URL");
            header.createCell(4).setCellValue("Landing_Page_URL");
            header.createCell(5).setCellValue("Dsplay_Number");
            header.createCell(6).setCellValue("Remarks");

            Row replacementRow = sheet.createRow(1);
            replacementRow.createCell(0).setCellValue("Normal");
            replacementRow.createCell(1).setCellValue("Bulk Replace Campaign");
            replacementRow.createCell(2).setCellValue("Bulk Replace Platform");
            replacementRow.createCell(3).setCellValue("https://example.com/replace-final");
            replacementRow.createCell(4).setCellValue("https://example.com/landing-final");
            replacementRow.createCell(5).setCellValue(33);
            replacementRow.createCell(6).setCellValue("Replacement row");

            workbook.write(out);
            secondUploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder secondBodyBuilder = new MultipartBodyBuilder();
        secondBodyBuilder.part("file", new ByteArrayResource(secondUploadBytes) {
                    @Override
                    public String getFilename() {
                        return "bulk-replace-second.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(secondBodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rowCount").isEqualTo(1)
                .jsonPath("$.insertedCount").isEqualTo(1);

        var secondShiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.List<java.util.Map<?, ?>> secondScopedLinks = new java.util.ArrayList<>();
        for (Object item : (java.util.List<?>) secondShiftLinksResponse.getResponseBody()) {
            if (item instanceof java.util.Map<?, ?> map
                    && "Bulk Replace Campaign".equals(map.get("adsName"))
                    && "1777000006".equals(map.get("adsOwner"))
                    && "Normal".equals(map.get("adsType"))) {
                secondScopedLinks.add(map);
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(1, secondScopedLinks.size());
        java.util.Map<?, ?> replacementLink = secondScopedLinks.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com/replace-final", replacementLink.get("fullUrl"));
        org.junit.jupiter.api.Assertions.assertEquals(1, ((Number) replacementLink.get("seqNumber")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(33, ((Number) replacementLink.get("displayNumber")).intValue());
    }

    @Test
    public void shiftLinkBulkUploadSupportsLegacyHeadersWithoutAdsType() throws Exception {
        User user = new User();
        user.setUserName("legacybulkuser");
        user.setUserEmail("legacybulk@example.com");
        user.setUserPhoneNumber("1777000003");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "legacybulk@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Legacy Bulk Platform", "remarks", "Legacy bulk platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Legacy Bulk Normal",
                        "campainCountry", "US",
                        "platformName", "Legacy Bulk Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        Long expectedAdsId = adsNormalInfoRepository.findByCampainNameAndAdsOwner("Legacy Bulk Normal", "1777000003")
                .orElseThrow()
                .getId();

        byte[] uploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Campain Name = Ads_Name");
            header.createCell(1).setCellValue("Platform Name = Platform_Name");
            header.createCell(2).setCellValue("Full URL = Full_URL");
            header.createCell(3).setCellValue("Display Number = Dsplay_Number");
            header.createCell(4).setCellValue("Remarks");

            Row normalRow = sheet.createRow(1);
            normalRow.createCell(0).setCellValue("Legacy Bulk Normal");
            normalRow.createCell(1).setCellValue("Legacy Bulk Platform");
            normalRow.createCell(2).setCellValue("https://example.com/legacy-bulk-normal");
            normalRow.createCell(3).setCellValue(9);
            normalRow.createCell(4).setCellValue("Legacy bulk remarks");

            workbook.write(out);
            uploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(uploadBytes) {
                    @Override
                    public String getFilename() {
                        return "legacy-shift-link-upload.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.rowCount").isEqualTo(1)
                .jsonPath("$.insertedCount").isEqualTo(1);

        var shiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.Map<?, ?> legacyLink = ((java.util.List<?>) shiftLinksResponse.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "https://example.com/legacy-bulk-normal".equals(map.get("fullUrl")))
                .map(item -> (java.util.Map<?, ?>) item)
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals("Normal", legacyLink.get("adsType"));
        org.junit.jupiter.api.Assertions.assertEquals("Legacy Bulk Normal", legacyLink.get("adsName"));
        org.junit.jupiter.api.Assertions.assertEquals(expectedAdsId.intValue(), ((Number) legacyLink.get("adsId")).intValue());
    }

    @Test
    public void shiftLinkBulkUploadFallsBackToColumnOrderWhenHeadersAreUnexpected() throws Exception {
        User user = new User();
        user.setUserName("orderedbulkuser");
        user.setUserEmail("orderedbulk@example.com");
        user.setUserPhoneNumber("1777000004");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "orderedbulk@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Ordered Bulk Platform", "remarks", "Ordered bulk platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Ordered Bulk Normal",
                        "campainCountry", "US",
                        "platformName", "Ordered Bulk Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        Long expectedAdsId = adsNormalInfoRepository.findByCampainNameAndAdsOwner("Ordered Bulk Normal", "1777000004")
                .orElseThrow()
                .getId();

        byte[] uploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("TypeColumn");
            header.createCell(1).setCellValue("NameColumn");
            header.createCell(2).setCellValue("PlatformColumn");
            header.createCell(3).setCellValue("UrlColumn");
            header.createCell(4).setCellValue("NumberColumn");
            header.createCell(5).setCellValue("RemarkColumn");

            Row normalRow = sheet.createRow(1);
            normalRow.createCell(0).setCellValue("Normal");
            normalRow.createCell(1).setCellValue("Ordered Bulk Normal");
            normalRow.createCell(2).setCellValue("Ordered Bulk Platform");
            normalRow.createCell(3).setCellValue("https://example.com/ordered-bulk-normal");
            normalRow.createCell(4).setCellValue(15);
            normalRow.createCell(5).setCellValue("Ordered bulk remarks");

            workbook.write(out);
            uploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(uploadBytes) {
                    @Override
                    public String getFilename() {
                        return "ordered-shift-link-upload.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.rowCount").isEqualTo(1)
                .jsonPath("$.insertedCount").isEqualTo(1);

        var shiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.Map<?, ?> orderedLink = ((java.util.List<?>) shiftLinksResponse.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "https://example.com/ordered-bulk-normal".equals(map.get("fullUrl")))
                .map(item -> (java.util.Map<?, ?>) item)
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals("Normal", orderedLink.get("adsType"));
        org.junit.jupiter.api.Assertions.assertEquals("Ordered Bulk Normal", orderedLink.get("adsName"));
        org.junit.jupiter.api.Assertions.assertEquals(expectedAdsId.intValue(), ((Number) orderedLink.get("adsId")).intValue());
    }

    @Test
    public void shiftLinkBulkUploadDoesNotTreatFirstColumnAsAdsTypeWhenDataIsCampaignName() throws Exception {
        User user = new User();
        user.setUserName("sixcolumnlegacyuser");
        user.setUserEmail("sixcolumnlegacy@example.com");
        user.setUserPhoneNumber("1777000005");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "sixcolumnlegacy@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Six Column Legacy Platform", "remarks", "Six column legacy platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Six Column Legacy Campaign",
                        "campainCountry", "US",
                        "platformName", "Six Column Legacy Platform",
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        Long expectedAdsId = adsNormalInfoRepository.findByCampainNameAndAdsOwner("Six Column Legacy Campaign", "1777000005")
                .orElseThrow()
                .getId();

        byte[] uploadBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("SHIFT_LINK_TEMPLATE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("LegacyCampaign");
            header.createCell(1).setCellValue("LegacyPlatform");
            header.createCell(2).setCellValue("LegacyUrl");
            header.createCell(3).setCellValue("LegacyNumber");
            header.createCell(4).setCellValue("LegacyRemarks");
            header.createCell(5).setCellValue("UnusedExtraColumn");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Six Column Legacy Campaign");
            row.createCell(1).setCellValue("Six Column Legacy Platform");
            row.createCell(2).setCellValue("https://example.com/six-column-legacy");
            row.createCell(3).setCellValue(22);
            row.createCell(4).setCellValue("Six column legacy remarks");
            row.createCell(5).setCellValue("ignored");

            workbook.write(out);
            uploadBytes = out.toByteArray();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(uploadBytes) {
                    @Override
                    public String getFilename() {
                        return "six-column-legacy-upload.xlsx";
                    }
                })
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        webTestClient.post().uri("/api/shift-links/bulk-upload")
                .header("AMtoken", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.rowCount").isEqualTo(1)
                .jsonPath("$.insertedCount").isEqualTo(1);

        var shiftLinksResponse = webTestClient.get().uri("/api/shift-links/all")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult();

        java.util.Map<?, ?> legacyLink = ((java.util.List<?>) shiftLinksResponse.getResponseBody()).stream()
                .filter(item -> item instanceof java.util.Map<?, ?> map
                        && "https://example.com/six-column-legacy".equals(map.get("fullUrl")))
                .map(item -> (java.util.Map<?, ?>) item)
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals("Normal", legacyLink.get("adsType"));
        org.junit.jupiter.api.Assertions.assertEquals("Six Column Legacy Campaign", legacyLink.get("adsName"));
        org.junit.jupiter.api.Assertions.assertEquals(expectedAdsId.intValue(), ((Number) legacyLink.get("adsId")).intValue());
    }

    @Test
    public void crudMatrixAdsWithPaginationAndAffiliates() {
        User user = new User();
        user.setUserName("matrixowner");
        user.setUserEmail("matrixowner@example.com");
        user.setUserPhoneNumber("6666666666");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("loginId", "matrixowner@example.com", "password", "pass123");
        var loginResponse = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Matrix TikTok", "remarks", "TikTok platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Matrix Meta", "remarks", "Meta platform"))
                .exchange()
                .expectStatus().isCreated();

        var createResponse = webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Matrix Campaign",
                        "campainCountry", "US",
                        "landingPageUrl", "https://example.com/matrix",
                        "dynamicProxyInfo", "proxy-main",
                        "dynamicProxyInfoBackup", "proxy-backup",
                        "intervalTime", 15,
                        "status", "RUNNING",
                        "adsOwner", "6666666666",
                        "affiliateInfos", java.util.List.of(
                                Map.of(
                                        "platformName", "Matrix TikTok",
                                        "affiliteUrl", "https://example.com/tiktok",
                                        "displayNumber", 10,
                                        "remarks", "Primary affiliate"),
                                Map.of(
                                        "platformName", "Matrix Meta",
                                        "affiliteUrl", "https://example.com/meta",
                                        "displayNumber", 20,
                                        "remarks", "Backup affiliate"))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long matrixId = ((Number) createResponse.getResponseBody().get("id")).longValue();

        webTestClient.get().uri("/api/matrix-ads?page=0&size=1&campainName=Matrix")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].affiliateInfos.length()").isEqualTo(2);

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&platformName=Matrix TikTok")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1);

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&status=RUNNING")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1);

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1);

        webTestClient.put().uri("/api/matrix-ads/" + matrixId)
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "status", "PAUSED",
                        "affiliateInfos", java.util.List.of(
                                Map.of(
                                        "platformName", "Matrix TikTok",
                                        "affiliteUrl", "https://example.com/tiktok-new",
                                        "displayNumber", 30,
                                        "remarks", "Updated affiliate"))))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("PAUSED")
                .jsonPath("$.data.affiliateInfos.length()").isEqualTo(1);

        webTestClient.delete().uri("/api/matrix-ads/" + matrixId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void normalAndMatrixAdsExposeExecuteTimesInResponses() throws Exception {
        User user = new User();
        user.setUserName("executeowner");
        user.setUserEmail("executeowner@example.com");
        user.setUserPhoneNumber("7777000001");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "executeowner@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Execute Platform", "remarks", "Execute platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Execute Normal Campaign",
                        "campainCountry", "US",
                        "platformName", "Execute Platform",
                        "intervalTime", 5,
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Execute Matrix Campaign",
                        "campainCountry", "US",
                        "intervalTime", 5,
                        "status", "RUNNING",
                        "affiliateInfos", java.util.List.of(
                                Map.of("platformName", "Execute Platform", "affiliteUrl", "https://example.com/execute-matrix"))))
                .exchange()
                .expectStatus().isCreated();

        waitForJobGroup("7777000001-Normal", 1);
        waitForJobGroup("7777000001-Matrix", 1);

        webTestClient.get().uri("/api/normal-ads?page=0&size=10&campainName=Execute Normal Campaign")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].lastExecuteTime").exists()
                .jsonPath("$.content[0].nextExecuteTime").exists();

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&campainName=Execute Matrix Campaign")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].lastExecuteTime").exists()
                .jsonPath("$.content[0].nextExecuteTime").exists();
    }

    @Test
    public void registerGeneratesApiKeyFromPhoneNumber() {
        User user = new User();
        user.setUserName("apikeyuser");
        user.setUserEmail("apikeyuser@example.com");
        user.setUserPhoneNumber("18812345678");
        user.setUserPassword("pass123");

        var registerResponse = webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        String expectedApiKey = Base64.getEncoder().encodeToString("18812345678".getBytes(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(expectedApiKey, registerResponse.getResponseBody().get("apiKey"));
        org.junit.jupiter.api.Assertions.assertNotNull(registerResponse.getResponseBody().get("user"));
        org.junit.jupiter.api.Assertions.assertFalse(((Map<?, ?>) registerResponse.getResponseBody().get("user")).containsKey("userPassword"));
        User saved = userRepository.findByUserName("apikeyuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(expectedApiKey, saved.getApiKey());
        org.junit.jupiter.api.Assertions.assertNotEquals("pass123", saved.getUserPassword());
        org.junit.jupiter.api.Assertions.assertEquals("pass123", passwordCryptoService.decrypt(saved.getUserPassword()));
    }

    @Test
    public void consumesNormalAndMatrixAdsWithApiKey() throws Exception {
        User user = new User();
        user.setUserName("consumeruser");
        user.setUserEmail("consumeruser@example.com");
        user.setUserPhoneNumber("19912345678");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        String apiKey = Base64.getEncoder().encodeToString("19912345678".getBytes(StandardCharsets.UTF_8));

        String token = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "consumeruser@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Consume Platform", "remarks", "Consume platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Summer Sale Matrix",
                        "campainCountry", "US",
                        "status", "RUNNING",
                        "affiliateInfos", java.util.List.of(
                                Map.of("platformName", "Consume Platform", "affiliteUrl", "https://example.com/matrix-source"))))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "adsType", "Matrix",
                        "adsName", "Summer Sale Matrix",
                        "platformName", "Consume Platform",
                        "fullUrl", "https://example.com/matrix-shift-1",
                        "landingPageUrl", "https://example.com/matrix-landing-1",
                        "displayNumber", 2,
                        "seqNumber", 1,
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/shift-links")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "adsType", "Matrix",
                        "adsName", "Summer Sale Matrix",
                        "platformName", "Consume Platform",
                        "fullUrl", "https://example.com/matrix-shift-2",
                        "landingPageUrl", "https://example.com/matrix-landing-2",
                        "displayNumber", 2,
                        "seqNumber", 2,
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated();

        var normalResponse = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/normal/ads")
                        .queryParam("campain_name", "Summer Sale")
                        .queryParam("api_key", apiKey)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String normalUrl = normalResponse.getResponseBody().get("uniqueUrl").toString();

        var matrixResponse = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/matrix/ads")
                        .queryParam("campain_name", "Summer Sale Matrix")
                        .queryParam("api_key", apiKey)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String firstMatrixUrl = matrixResponse.getResponseBody();
        java.util.Set<String> allowedMatrixUrls = java.util.Set.of(
                "https://example.com/matrix-shift-1",
                "https://example.com/matrix-shift-2");
        org.junit.jupiter.api.Assertions.assertTrue(allowedMatrixUrls.contains(firstMatrixUrl));

        waitForCondition(() -> shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc("19912345678", "Summer Sale Matrix", "Matrix")
                .stream()
                .mapToLong(link -> link.getDisplayTimes() == null ? 0L : link.getDisplayTimes())
                .sum() == 1L);

        var secondMatrixResponse = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/matrix/ads")
                        .queryParam("campain_name", "Summer Sale Matrix")
                        .queryParam("api_key", apiKey)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String secondMatrixUrl = secondMatrixResponse.getResponseBody();
        org.junit.jupiter.api.Assertions.assertTrue(allowedMatrixUrls.contains(secondMatrixUrl));

        waitForCondition(() -> shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc("19912345678", "Summer Sale Matrix", "Matrix")
                .stream()
                .mapToLong(link -> link.getDisplayTimes() == null ? 0L : link.getDisplayTimes())
                .sum() == 2L);

        var currentScopedLinks = shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc("19912345678", "Summer Sale Matrix", "Matrix");
        org.junit.jupiter.api.Assertions.assertEquals(2, currentScopedLinks.size());
        long totalDisplayTimes = currentScopedLinks.stream()
                .mapToLong(link -> link.getDisplayTimes() == null ? 0L : link.getDisplayTimes())
                .sum();
        org.junit.jupiter.api.Assertions.assertEquals(2L, totalDisplayTimes);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/normal/ads")
                        .queryParam("campain_name", "Summer Sale")
                        .build())
                .header("AMtoken", "legacy-token-not-allowed")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/matrix/ads")
                        .queryParam("campain_name", "Summer Sale Matrix")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .isEqualTo("api_key is required");
    }

    @Test
    public void registersQuartzJobsAfterAdsCreate() throws Exception {
        User user = new User();
        user.setUserName("quartzowner");
        user.setUserEmail("quartzowner@example.com");
        user.setUserPhoneNumber("8899000001");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("loginId", "quartzowner@example.com", "password", "pass123");
        var loginResponse = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Quartz Normal", "remarks", "Quartz platform"))
                .exchange()
                .expectStatus().isCreated();

        var normalResponse = webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Quartz Normal Campaign",
                        "campainCountry", "US",
                        "platformName", "Quartz Normal",
                        "intervalTime", 5,
                        "adsOwner", "8899000001"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();
        Long normalId = ((Number) normalResponse.getResponseBody().get("id")).longValue();

        var matrixResponse = webTestClient.post().uri("/api/matrix-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Quartz Matrix Campaign",
                        "campainCountry", "US",
                        "intervalTime", 7,
                        "adsOwner", "8899000001"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();
        Long matrixId = ((Number) matrixResponse.getResponseBody().get("id")).longValue();

        waitForJobGroup("8899000001-Normal", 1);
        waitForJobGroup("8899000001-Matrix", 1);
        waitForJobClass(JobKey.jobKey("ads-task-" + normalId, "8899000001-Normal"),
                "com.admire.cars.runner.job.NormalAdsAutoTaskJob");
        waitForJobClass(JobKey.jobKey("ads-task-" + matrixId, "8899000001-Matrix"),
                "com.admire.cars.runner.job.MatrixAdsAutoTaskJob");

        webTestClient.put().uri("/api/normal-ads/" + normalId)
                .header("AMtoken", token)
                .bodyValue(Map.of("status", "PAUSED"))
                .exchange()
                .expectStatus().isOk();

        waitForTriggerState(TriggerKey.triggerKey("ads-trigger-" + normalId, "8899000001-Normal"), TriggerState.PAUSED);

        webTestClient.put().uri("/api/normal-ads/" + normalId)
                .header("AMtoken", token)
                .bodyValue(Map.of("status", "RUNNING"))
                .exchange()
                .expectStatus().isOk();

        waitForTriggerState(TriggerKey.triggerKey("ads-trigger-" + normalId, "8899000001-Normal"), TriggerState.NORMAL);

        webTestClient.delete().uri("/api/normal-ads/" + normalId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();

        waitForJobGroup("8899000001-Normal", 0);
        waitForJobGroup("8899000001-Matrix", 1);
    }

    @Test
    public void crudToolPaypalIncomeOutcomeWithOwnerScopedQueries() {
        webTestClient.post().uri("/api/users/register")
                .bodyValue(Map.of(
                        "userName", "financeowner1",
                        "userEmail", "financeowner1@example.com",
                        "userPhoneNumber", "1888000101",
                        "userPassword", "pass123"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/users/register")
                .bodyValue(Map.of(
                        "userName", "financeowner2",
                        "userEmail", "financeowner2@example.com",
                        "userPhoneNumber", "1888000102",
                        "userPassword", "pass123"))
                .exchange()
                .expectStatus().isCreated();

        String token1 = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "financeowner1@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        String token2 = webTestClient.post().uri("/api/auth/login")
                .bodyValue(Map.of("loginId", "financeowner2@example.com", "password", "pass123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("amToken")
                .toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token1)
                .bodyValue(Map.of("platformName", "Finance Platform 101", "remarks", "Finance platform 101"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token2)
                .bodyValue(Map.of("platformName", "Finance Platform 102", "remarks", "Finance platform 102"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/tool-emails")
                .header("AMtoken", token1)
                .bodyValue(Map.of(
                        "userName", "financeu101",
                        "emailAddress", "financeu101@example.com",
                        "emaliPwd", "pwd101",
                        "remarks", "owner1 tool email"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/api/tool-emails")
                .header("AMtoken", token2)
                .bodyValue(Map.of(
                        "userName", "financeu102",
                        "emailAddress", "financeu102@example.com",
                        "emaliPwd", "pwd102",
                        "remarks", "owner2 tool email"))
                .exchange()
                .expectStatus().isCreated();

        var paypal1 = webTestClient.post().uri("/api/tool-paypals")
                .header("AMtoken", token1)
                .bodyValue(Map.of(
                        "paypalEmail", "paypal101@example.com",
                        "primaryEmail", "primary101@example.com",
                        "paypalId", "paypal-id-101",
                        "adsOwner", "should-be-overridden"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        webTestClient.post().uri("/api/tool-paypals")
                .header("AMtoken", token2)
                .bodyValue(Map.of(
                        "paypalEmail", "paypal102@example.com",
                        "primaryEmail", "primary102@example.com",
                        "paypalId", "paypal-id-102"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        org.junit.jupiter.api.Assertions.assertEquals(
                "1888000101",
                ((Map<?, ?>) paypal1.getResponseBody().get("data")).get("adsOwner"));

        webTestClient.post().uri("/api/tool-incomes")
                .header("AMtoken", token1)
                .bodyValue(Map.of(
                        "platformName", "Finance Platform 101",
                        "userName", "financeu101",
                        "incomeAmount", 123.45,
                        "currency", "usd",
                        "paymentMethod", "Wire",
                        "paypalAccount", "paypal101@example.com",
                        "payoutDate", "2026-07-10",
                        "remarks", "owner1 income",
                        "adsOwner", "override-me"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        var income2 = webTestClient.post().uri("/api/tool-incomes")
                .header("AMtoken", token2)
                .bodyValue(Map.of(
                        "platformName", "Finance Platform 102",
                        "userName", "financeu102",
                        "incomeAmount", 88.88,
                        "currency", "CNY",
                        "paymentMethod", "Paypal",
                        "paypalAccount", "paypal102@example.com",
                        "payoutDate", "2026-07-11",
                        "remarks", "owner2 income"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long income2Id = ((Number) income2.getResponseBody().get("id")).longValue();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/tool-incomes")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("platformName", "Finance Platform 101")
                        .build())
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].adsOwner").isEqualTo("1888000101")
                .jsonPath("$.content[0].currency").isEqualTo("USD");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/tool-incomes")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("userName", "financeu101")
                        .queryParam("paypalAccount", "paypal101@example.com")
                        .queryParam("payoutDateBegin", "2026-07-01")
                        .queryParam("payoutDateEnd", "2026-07-31")
                        .build())
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/tool-incomes")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("platformName", "Finance Platform 102")
                        .build())
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0);

        webTestClient.get()
                .uri("/api/tool-incomes/" + income2Id)
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post().uri("/api/tool-outcomes")
                .header("AMtoken", token1)
                .bodyValue(Map.of(
                        "outcomeType", "IP Proxy",
                        "outcomeAmount", 11.11,
                        "currency", "usd",
                        "payDate", "2026-07-12",
                        "remarks", "owner1 outcome"))
                .exchange()
                .expectStatus().isCreated();

        var outcome2 = webTestClient.post().uri("/api/tool-outcomes")
                .header("AMtoken", token2)
                .bodyValue(Map.of(
                        "outcomeType", "SEMRUSH",
                        "outcomeAmount", 22.22,
                        "currency", "CNY",
                        "payDate", "2026-07-13",
                        "remarks", "owner2 outcome"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long outcome2Id = ((Number) outcome2.getResponseBody().get("id")).longValue();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/tool-outcomes")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("outcomeType", "ip proxy")
                        .queryParam("payDateBegin", "2026-07-01")
                        .queryParam("payDateEnd", "2026-07-31")
                        .build())
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].outcomeType").isEqualTo("IP PROXY")
                .jsonPath("$.content[0].adsOwner").isEqualTo("1888000101");

        webTestClient.get()
                .uri("/api/tool-outcomes/" + outcome2Id)
                .header("AMtoken", token1)
                .exchange()
                .expectStatus().isNotFound();
    }

    private void waitForJobGroup(String groupName, int expectedSize) throws Exception {
        for (int i = 0; i < 30; i++) {
            int size = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName)).size();
            if (size == expectedSize) {
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Expected " + expectedSize + " Quartz jobs in group " + groupName);
    }

    private void waitForJobClass(JobKey jobKey, String expectedClassName) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (scheduler.checkExists(jobKey)) {
                String actualClassName = scheduler.getJobDetail(jobKey).getJobClass().getName();
                if (expectedClassName.equals(actualClassName)) {
                    return;
                }
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Expected Quartz job " + jobKey + " to use " + expectedClassName);
    }

    private void waitForTriggerState(TriggerKey triggerKey, TriggerState expectedState) throws Exception {
        for (int i = 0; i < 30; i++) {
            TriggerState state = scheduler.getTriggerState(triggerKey);
            if (state == expectedState) {
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Expected Quartz trigger " + triggerKey + " to be " + expectedState);
    }

    private void waitForCondition(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Expected condition to be met");
    }
}
