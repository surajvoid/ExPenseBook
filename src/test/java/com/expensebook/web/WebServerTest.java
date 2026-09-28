package com.expensebook.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class WebServerTest {

    private static WebServer server;
    private static final int TEST_PORT = 8089;
    private static HttpClient client;

    @BeforeAll
    public static void setUp() throws Exception {
        server = new WebServer(TEST_PORT);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testServeIndexHtml() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ExPense Book"));
        assertTrue(response.body().contains("TRACK • ANALYSE • CONTROL"));
    }

    @Test
    public void testServeStyleCss() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/style.css"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("--primary-sage"));
    }

    @Test
    public void testServeAppJs() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/app.js"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ExPense Book"));
    }

    @Test
    public void testAuthEndpointRequiresMethod() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/auth/login"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    @Test
    public void testLoginAndDashboardCycle() throws Exception {
        String loginJson = "{\"email\":\"admin@expensebook.com\",\"password\":\"Admin@123\"}";
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());
        assertTrue(loginRes.body().contains("\"token\""));

        // Extract token
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(loginRes.body()).getAsJsonObject();
        String token = json.get("token").getAsString();
        assertNotNull(token);

        // Fetch Dashboard with Bearer token
        HttpRequest dashReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/dashboard"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> dashRes = client.send(dashReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, dashRes.statusCode());
        assertTrue(dashRes.body().contains("totalSpent"));
        assertTrue(dashRes.body().contains("isBudgetMode"));

        // Fetch categories
        HttpRequest catReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/categories"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> catRes = client.send(catReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, catRes.statusCode());
        assertTrue(catRes.body().contains("name"));

        // Test POST /api/expenses
        String expenseJson = "{\"amount\":150.0,\"categoryId\":1,\"paymentMode\":\"UPI\",\"expenseDate\":\"2026-09-08\",\"description\":\"Morning Coffee\"}";
        HttpRequest expReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/expenses"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(expenseJson))
                .build();
        HttpResponse<String> expRes = client.send(expReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, expRes.statusCode(), "Expected 201 Created but got: " + expRes.body());
        assertTrue(expRes.body().contains("Morning Coffee"));
    }
}
