package com.emailapproval.approval;

import com.emailapproval.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Client for the Complete Work Item REST API.
 * <p>
 * POST {baseUrl}/cases/{caseOid}/workItems/{workItemId}/complete
 */
public class WorkItemClient {

    private static final Logger LOG = Logger.getLogger(WorkItemClient.class.getName());

    private final AgentConfig config;
    private final ObjectMapper objectMapper;

    public WorkItemClient(AgentConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Complete a work item with the given decision (approve or reject).
     *
     * @return true if the API call succeeded (HTTP 204), false otherwise
     */
    public boolean completeWorkItem(String caseOid, String workItemId, String decision) {
        String outcome = "approve".equalsIgnoreCase(decision)
                ? config.getApprovalApiApproveOutcome()
                : config.getApprovalApiRejectOutcome();

        String path = "/cases/" + caseOid + "/workItems/" + workItemId + "/complete";
        String url = config.getApprovalApiBaseUrl().replaceAll("/$", "") + path;

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Basic " + base64Credentials());

            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode output = objectMapper.createObjectNode();
            output.put("@type", "c:AbstractWorkItemOutputType");
            output.put("comment", "Processed by Email Approval Agent");
            output.put("outcome", outcome);
            body.set("output", output);

            String json = objectMapper.writeValueAsString(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 204) {
                return true;
            } else {
                LOG.warning("Approval API returned " + responseCode + " for " + url);
                return false;
            }
        } catch (Exception e) {
            LOG.severe("Approval API error: " + e.getMessage());
            throw new RuntimeException("Failed to complete work item", e);
        }
    }

    private String base64Credentials() {
        String credentials = config.getApprovalApiUser() + ":" + config.getApprovalApiPassword();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
