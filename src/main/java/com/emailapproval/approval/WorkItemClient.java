package com.emailapproval.approval;

import com.emailapproval.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
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

    /**
     * Fetch the case object and resolve the approver's user OID for the given work item.
     *
     * @return approver user OID, or null if it cannot be determined
     */
    public String getApproverUserOid(String caseOid, String workItemId) {
        String path = "/cases/" + caseOid + "?options=raw&options=resolveNames";
        try {
            JsonNode root = get(path);
            if (root == null) {
                return null;
            }

            JsonNode caseNode = root.path("case");
            if (caseNode.isMissingNode()) {
                LOG.warning("Case object not found in response for case " + caseOid);
                return null;
            }

            int workItemIdInt;
            try {
                workItemIdInt = Integer.parseInt(workItemId);
            } catch (NumberFormatException e) {
                LOG.warning("Invalid work item id: " + workItemId);
                return null;
            }

            JsonNode workItemNode = caseNode.path("workItem");

            JsonNode matchingWorkItem = null;
            if (workItemNode.isArray()) {
                for (JsonNode wi : workItemNode) {
                    if (wi.path("@id").asInt(-1) == workItemIdInt) {
                        matchingWorkItem = wi;
                        break;
                    }
                }
            } else if (workItemNode.isObject()) {
                if (workItemNode.path("@id").asInt(-1) == workItemIdInt) {
                    matchingWorkItem = workItemNode;
                }
            }

            if (matchingWorkItem == null) {
                LOG.warning("Work item " + workItemId + " not found in case " + caseOid);
                return null;
            }

            JsonNode assigneeRefNode = matchingWorkItem.path("assigneeRef");
            if (assigneeRefNode.isArray() && assigneeRefNode.size() > 0) {
                assigneeRefNode = assigneeRefNode.get(0);
            }

            String approverOid = assigneeRefNode.path("oid").asText(null);
            if (approverOid == null || approverOid.isBlank()) {
                LOG.warning("AssigneeRef oid not found for work item " + workItemId + " in case " + caseOid);
                return null;
            }

            return approverOid;
        } catch (Exception e) {
            LOG.severe("Error fetching approver for case " + caseOid + ", work item " + workItemId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the user object and return the primary email address.
     *
     * @return email address, or null if it cannot be determined
     */
    public String getUserEmail(String userOid) {
        String path = "/users/" + userOid + "?options=raw";
        try {
            JsonNode root = get(path);
            if (root == null) {
                return null;
            }

            JsonNode userNode = root.path("user");
            if (userNode.isMissingNode()) {
                LOG.warning("User object not found in response for user " + userOid);
                return null;
            }

            // MidPoint commonly uses "emailAddress" for the primary email.
            String email = userNode.path("emailAddress").asText(null);

            if (email == null || email.isBlank()) {
                LOG.warning("Email address not found for user " + userOid);
                return null;
            }

            return email.trim();
        } catch (Exception e) {
            LOG.severe("Error fetching user " + userOid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Execute a GET request against the approval API and parse the JSON response.
     */
    private JsonNode get(String path) throws Exception {
        String url = config.getApprovalApiBaseUrl().replaceAll("/$", "") + path;

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Basic " + base64Credentials());

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            LOG.warning("GET " + url + " returned " + responseCode);
            return null;
        }

        try (InputStream is = conn.getInputStream()) {
            return objectMapper.readTree(is);
        }
    }

    private String base64Credentials() {
        String credentials = config.getApprovalApiUser() + ":" + config.getApprovalApiPassword();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
