package com.admire.cars.runner;

import com.admire.cars.runner.entity.User;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

        Map<String, String> payload = Map.of("username", "pageduser1", "password", "pass123");
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

        Map<String, String> login = Map.of("username", "roleadmin", "password", "pass123");
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

        Map<String, String> login = Map.of("username", "platformadmin", "password", "pass123");
        var response = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();

        String token = response.getResponseBody().get("amToken").toString();

        var createResponse = webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Google Ads", "remarks", "Search platform"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();

        Long platformId = ((Number) createResponse.getResponseBody().get("id")).longValue();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Meta Ads", "remarks", "Social platform"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/platforms?page=0&size=1")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1);

        webTestClient.get().uri("/api/platforms/" + platformId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.platformName").isEqualTo("Google Ads");

        webTestClient.put().uri("/api/platforms/" + platformId)
                .header("AMtoken", token)
                .bodyValue(Map.of("remarks", "Updated platform"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
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

        Map<String, String> login = Map.of("username", "normaladsowner", "password", "pass123");
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
    public void crudMatrixAdsWithPaginationAndAffiliates() {
        User user = new User();
        user.setUserName("matrixowner");
        user.setUserEmail("matrixowner@example.com");
        user.setUserPhoneNumber("6666666666");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("username", "matrixowner", "password", "pass123");
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

        webTestClient.get().uri("/api/matrix-ads?page=0&size=10&adsOwner=6666666666")
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
    public void crudShiftLinksWithBulkUploadAndTemplate() throws Exception {
        User user = new User();
        user.setUserName("shiftowner");
        user.setUserEmail("shiftowner@example.com");
        user.setUserPhoneNumber("7777777777");
        user.setUserPassword("pass123");

        webTestClient.post().uri("/api/users/register").bodyValue(user)
                .exchange()
                .expectStatus().isCreated();

        Map<String, String> login = Map.of("username", "shiftowner", "password", "pass123");
        var loginResponse = webTestClient.post().uri("/api/auth/login").bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult();
        String token = loginResponse.getResponseBody().get("amToken").toString();

        webTestClient.post().uri("/api/platforms")
                .header("AMtoken", token)
                .bodyValue(Map.of("platformName", "Shift Search", "remarks", "Shift link platform"))
                .exchange()
                .expectStatus().isCreated();

        var normalResponse = webTestClient.post().uri("/api/normal-ads")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Shift Campaign",
                        "campainCountry", "US",
                        "platformName", "Shift Search",
                        "status", "RUNNING",
                        "adsOwner", "7777777777"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();
        Long normalId = ((Number) normalResponse.getResponseBody().get("id")).longValue();

        webTestClient.get().uri("/api/ads-urls/template")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Campain Name");
            header.createCell(1).setCellValue("Campain Country");
            header.createCell(2).setCellValue("Platform");
            header.createCell(3).setCellValue("Full URL");
            header.createCell(4).setCellValue("Display Number");
            header.createCell(5).setCellValue("Remarks");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Shift Campaign");
            row1.createCell(1).setCellValue("US");
            row1.createCell(2).setCellValue("Shift Search");
            row1.createCell(3).setCellValue("https://example.com/shift-1");
            row1.createCell(4).setCellValue(1);
            row1.createCell(5).setCellValue("Row 1");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Shift Campaign");
            row2.createCell(1).setCellValue("US");
            row2.createCell(2).setCellValue("Shift Search");
            row2.createCell(3).setCellValue("https://example.com/shift-2");
            row2.createCell(4).setCellValue(2);
            row2.createCell(5).setCellValue("Row 2");

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray()) {
                @Override
                public String getFilename() {
                    return "shift-links.xlsx";
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", resource)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);
            builder.part("adsType", "Normal");
            builder.part("campainId", String.valueOf(normalId));

            webTestClient.post().uri("/api/ads-urls/bulk-upload")
                    .header("AMtoken", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.insertedCount").isEqualTo(2);
        }

        webTestClient.get().uri("/api/ads-urls?page=0&size=10&platformName=Shift Search&status=RUNNING&adsOwner=7777777777")
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);

        var createResponse = webTestClient.post().uri("/api/ads-urls")
                .header("AMtoken", token)
                .bodyValue(Map.of(
                        "campainName", "Direct Shift",
                        "campainCountry", "US",
                        "platformName", "Shift Search",
                        "fullUrl", "https://example.com/direct",
                        "displayNumber", 3,
                        "remarks", "Direct create",
                        "adsType", "Normal",
                        "campainId", normalId,
                        "status", "RUNNING"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult();
        Long adsUrlId = ((Number) createResponse.getResponseBody().get("id")).longValue();

        webTestClient.get().uri("/api/ads-urls/" + adsUrlId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.campainName").isEqualTo("Direct Shift");

        webTestClient.put().uri("/api/ads-urls/" + adsUrlId)
                .header("AMtoken", token)
                .bodyValue(Map.of("remarks", "Updated shift"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.remarks").isEqualTo("Updated shift");

        webTestClient.delete().uri("/api/ads-urls/" + adsUrlId)
                .header("AMtoken", token)
                .exchange()
                .expectStatus().isOk();
    }
}
