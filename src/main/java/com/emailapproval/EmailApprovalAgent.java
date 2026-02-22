package com.emailapproval;

import com.emailapproval.config.AgentConfig;
import com.emailapproval.imap.EmailProcessor;
import com.emailapproval.imap.ImapMailboxService;
import com.emailapproval.approval.WorkItemClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Email Approval Agent - polls an IMAP mailbox for approval emails,
 * completes work items based on email subject, and moves
 * processed emails to the Agent-Processed folder.
 */
public class EmailApprovalAgent {

    private static final Logger LOG = Logger.getLogger(EmailApprovalAgent.class.getName());

    public static void main(String[] args) {
        try {
            AgentConfig config = AgentConfig.fromEnvironment();
            LOG.info("Starting Email Approval Agent - polling interval: " + config.getPollIntervalSeconds() + "s");

            WorkItemClient workItemClient = new WorkItemClient(config);
            ImapMailboxService imapService = new ImapMailboxService(config);
            EmailProcessor processor = new EmailProcessor(workItemClient);

            runPollingLoop(config, imapService, processor);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Fatal error", e);
            System.exit(1);
        }
    }

    private static void runPollingLoop(AgentConfig config, ImapMailboxService imapService, EmailProcessor processor) {
        int pollIntervalSeconds = config.getPollIntervalSeconds();

        while (true) {
            try {
                imapService.processInbox(processor);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error during inbox processing", e);
            }

            try {
                Thread.sleep(pollIntervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("Agent interrupted, shutting down");
                break;
            }
        }
    }
}
