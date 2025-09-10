package org.dacrewj.agent.service.jira;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.dacrewj.agent.jira.JiraCommentService;
import org.dacrewj.contract.AdfDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-mode test using a lightweight in-JVM HTTP server instead of WireMock/Jetty
 * to avoid external dependencies. It validates that JiraCommentService sends:
 *  - Authorization: Bearer <token>
 *  - JSON Content-Type
 *  - ADF JSON body with the expected text content
 */
public class JiraCommentServiceRealModeTest {

    private static AdfDocument sampleAdf(String text) {
        AdfDocument.Node textNode = new AdfDocument.Node("text", null, null, text, null);
        AdfDocument.Node para = new AdfDocument.Node("paragraph", null, List.of(textNode), null, null);
        return new AdfDocument("doc", 1, List.of(para));
    }

    private HttpServer server;
    private volatile String lastRequestBody;
    private volatile String lastAuthHeader;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rest/api/3/issue/BTS-11/comment", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
                try (var is = exchange.getRequestBody(); var bos = new ByteArrayOutputStream()) {
                    is.transferTo(bos);
                    lastRequestBody = bos.toString(StandardCharsets.UTF_8);
                }
                String response = "{\"self\":\"http://jira/comment/123\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void addComment_realMode_postsToJiraAndParsesSuccess() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        // Prefer env token if set; otherwise any non-blank dummy token is fine for the local server
        String token = System.getenv().getOrDefault("JIRA_TOKEN", "dummy-token");
        JiraCommentService localTool = new JiraCommentService(baseUrl, token, false, 1024);

        JiraCommentService.Result res = localTool.addComment("BTS-11", sampleAdf("Hello real mode"));

        assertTrue(res.success());
        assertEquals("created", res.status());
        assertNotNull(res.url());
        assertNotNull(lastRequestBody);
        assertNotNull(lastAuthHeader);
        assertTrue(lastAuthHeader.startsWith("Bearer "));
        assertTrue(lastRequestBody.contains("\"body\""));
        assertTrue(lastRequestBody.contains("\"text\":\"Hello real mode\""));
    }

    @Test
    void addComment_realMode_handlesFailure() throws IOException {
        // Reconfigure handler to return 400
        server.removeContext("/rest/api/3/issue/BTS-11/comment");
        server.createContext("/rest/api/3/issue/BTS-11/comment", exchange -> {
            String response = "{\"errorMessages\":[\"bad\"]}";
            exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        String token = System.getenv().getOrDefault("JIRA_TOKEN", "dummy-token");
        JiraCommentService localTool = new JiraCommentService(baseUrl, token, false, 1024);

        JiraCommentService.Result res = localTool.addComment("BTS-11", sampleAdf("x"));
        assertFalse(res.success());
        assertNotNull(res.error());
    }
}
