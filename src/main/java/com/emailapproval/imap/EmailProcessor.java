package com.emailapproval.imap;

import com.emailapproval.approval.WorkItemClient;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses approval emails with subject "{workitemid}-{decision}" and
 * triggers the complete work item API.
 * <p>
 * Expected subject format: {caseOid}-{workItemId}-{decision}
 * Example: 3091ccc5-f3f6-4a06-92b5-803afce1ce57-4-approve
 * <p>
 * Decision must be "approve" or "reject".
 */
public class EmailProcessor {

    private static final Logger LOG = Logger.getLogger(EmailProcessor.class.getName());

    // Matches: caseOid-workItemId-decision
    // Case OID is UUID format, work item id is numeric, decision is approve/reject
    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})-([0-9]+)-(approve|reject)"
    );

    private final WorkItemClient workItemClient;

    public EmailProcessor(WorkItemClient workItemClient) {
        this.workItemClient = workItemClient;
    }

    /**
     * Process an email. Returns true if the email was successfully processed
     * and should be moved to the Agent-Processed folder.
     */
    public boolean process(Message message, String subject) {
        subject = subject.trim();
        Matcher matcher = SUBJECT_PATTERN.matcher(subject);

        if (!matcher.matches()) {
            LOG.fine("Skipping email - subject does not match pattern: " + subject);
            return false;
        }

        String caseOid = matcher.group(1);
        String workItemId = matcher.group(2);
        String decision = matcher.group(3).toLowerCase();

        LOG.info("Processing approval email: case=" + caseOid + ", workItem=" + workItemId + ", decision=" + decision);

        String senderEmail = extractSenderEmail(message);
        if (senderEmail == null) {
            LOG.warning("Unable to determine sender email, skipping message");
            return false;
        }

        try {
            String approverUserOid = workItemClient.getApproverUserOid(caseOid, workItemId);
            if (approverUserOid == null) {
                LOG.warning("Could not resolve approver user OID for case " + caseOid + ", work item " + workItemId);
                return false;
            }

            String approverEmail = workItemClient.getUserEmail(approverUserOid);
            if (approverEmail == null) {
                LOG.warning("Could not resolve approver email for user " + approverUserOid);
                return false;
            }

            if (!senderEmail.equalsIgnoreCase(approverEmail)) {
                LOG.warning("Sender email '" + senderEmail + "' does not match approver email '" + approverEmail +
                        "' for case " + caseOid + ", work item " + workItemId + ". Skipping.");
                return false;
            }

            boolean success = workItemClient.completeWorkItem(caseOid, workItemId, decision);
            if (success) {
                LOG.info("Successfully completed work item " + workItemId + " for case " + caseOid);
                return true;
            } else {
                LOG.warning("Failed to complete work item - API returned error");
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to validate approver or complete work item via API", e);
            return false;
        }
    }

    private String extractSenderEmail(Message message) {
        try {
            Address[] from = message.getFrom();
            if (from == null || from.length == 0) {
                return null;
            }

            Address address = from[0];
            if (address instanceof InternetAddress) {
                String email = ((InternetAddress) address).getAddress();
                return email != null ? email.trim().toLowerCase() : null;
            }

            String raw = address.toString();
            if (raw == null || raw.isBlank()) {
                return null;
            }

            int lt = raw.indexOf('<');
            int gt = raw.indexOf('>');
            if (lt >= 0 && gt > lt) {
                return raw.substring(lt + 1, gt).trim().toLowerCase();
            }

            return raw.trim().toLowerCase();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to extract sender email", e);
            return null;
        }
    }
}
