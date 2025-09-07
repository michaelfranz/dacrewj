package org.dacrewj.agent.service.jira;

import org.dacrewj.agent.AgentApplication;
import org.dacrewj.contract.AdfDocument;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgentApplication.class, properties = {
        "app.jira.base-url=https://karakun-agent.atlassian.net",
        "app.jira.dry-run=true",
        "app.jira.max-comment-length=64"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JiraCommentToolTest {

    @Autowired
    private JiraCommentTool tool;

    private static AdfDocument sampleAdf(String text) {
        AdfDocument.Node textNode = new AdfDocument.Node("text", null, null, text, null);
        AdfDocument.Node para = new AdfDocument.Node("paragraph", null, List.of(textNode), null, null);
        return new AdfDocument("doc", 1, List.of(para));
    }

    @Test
    @Order(1)
    void addComment_dryRun_returnsDryRunAndDoesNotRequireToken() {
        JiraCommentTool.Result res = tool.addComment("BTS-11", sampleAdf("Hello from dry run"));
        assertTrue(res.success());
        assertEquals("dry-run", res.status());
        assertNull(res.url());
        assertNull(res.error());
    }
}
