package com.emailapproval.config;

/**
 * Configuration for the Email Approval Agent.
 * All values are read from environment variables.
 */
public class AgentConfig {

    // IMAP
    public static final String IMAP_HOST = "IMAP_HOST";
    public static final String IMAP_PORT = "IMAP_PORT";
    public static final String IMAP_USER = "IMAP_USER";
    public static final String IMAP_PASSWORD = "IMAP_PASSWORD";
    public static final String IMAP_USE_SSL = "IMAP_USE_SSL";
    public static final String IMAP_INBOX_FOLDER = "IMAP_INBOX_FOLDER";
    public static final String IMAP_PROCESSED_FOLDER = "IMAP_PROCESSED_FOLDER";

    // Approval API
    public static final String APPROVAL_API_BASE_URL = "APPROVAL_API_BASE_URL";
    public static final String APPROVAL_API_USER = "APPROVAL_API_USER";
    public static final String APPROVAL_API_PASSWORD = "APPROVAL_API_PASSWORD";
    public static final String APPROVAL_API_APPROVE_OUTCOME = "APPROVAL_API_APPROVE_OUTCOME";
    public static final String APPROVAL_API_REJECT_OUTCOME = "APPROVAL_API_REJECT_OUTCOME";

    // Agent
    public static final String POLL_INTERVAL_SECONDS = "POLL_INTERVAL_SECONDS";

    private final String imapHost;
    private final int imapPort;
    private final String imapUser;
    private final String imapPassword;
    private final boolean imapUseSsl;
    private final String imapInboxFolder;
    private final String imapProcessedFolder;
    private final String approvalApiBaseUrl;
    private final String approvalApiUser;
    private final String approvalApiPassword;
    private final String approvalApiApproveOutcome;
    private final String approvalApiRejectOutcome;
    private final int pollIntervalSeconds;

    public AgentConfig(String imapHost, int imapPort, String imapUser, String imapPassword,
                       boolean imapUseSsl, String imapInboxFolder, String imapProcessedFolder,
                       String approvalApiBaseUrl, String approvalApiUser, String approvalApiPassword,
                       String approvalApiApproveOutcome, String approvalApiRejectOutcome,
                       int pollIntervalSeconds) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.imapUser = imapUser;
        this.imapPassword = imapPassword;
        this.imapUseSsl = imapUseSsl;
        this.imapInboxFolder = imapInboxFolder;
        this.imapProcessedFolder = imapProcessedFolder;
        this.approvalApiBaseUrl = approvalApiBaseUrl;
        this.approvalApiUser = approvalApiUser;
        this.approvalApiPassword = approvalApiPassword;
        this.approvalApiApproveOutcome = approvalApiApproveOutcome;
        this.approvalApiRejectOutcome = approvalApiRejectOutcome;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public static AgentConfig fromEnvironment() {
        String imapHost = requireEnv(IMAP_HOST);
        int imapPort = Integer.parseInt(getEnv(IMAP_PORT, "993"));
        String imapUser = requireEnv(IMAP_USER);
        String imapPassword = requireEnv(IMAP_PASSWORD);
        boolean imapUseSsl = Boolean.parseBoolean(getEnv(IMAP_USE_SSL, "true"));
        String imapInboxFolder = getEnv(IMAP_INBOX_FOLDER, "INBOX");
        String imapProcessedFolder = getEnv(IMAP_PROCESSED_FOLDER, "Agent-Processed");
        String approvalApiBaseUrl = requireEnv(APPROVAL_API_BASE_URL);
        String approvalApiUser = requireEnv(APPROVAL_API_USER);
        String approvalApiPassword = requireEnv(APPROVAL_API_PASSWORD);
        String approvalApiApproveOutcome = getEnv(APPROVAL_API_APPROVE_OUTCOME,
                "http://midpoint.evolveum.com/xml/ns/public/model/approval/outcome#approve");
        String approvalApiRejectOutcome = getEnv(APPROVAL_API_REJECT_OUTCOME,
                "http://midpoint.evolveum.com/xml/ns/public/model/approval/outcome#reject");
        int pollIntervalSeconds = Integer.parseInt(getEnv(POLL_INTERVAL_SECONDS, "60"));

        return new AgentConfig(
                imapHost, imapPort, imapUser, imapPassword,
                imapUseSsl, imapInboxFolder, imapProcessedFolder,
                approvalApiBaseUrl, approvalApiUser, approvalApiPassword,
                approvalApiApproveOutcome, approvalApiRejectOutcome,
                pollIntervalSeconds
        );
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value.trim();
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    public String getImapHost() { return imapHost; }
    public int getImapPort() { return imapPort; }
    public String getImapUser() { return imapUser; }
    public String getImapPassword() { return imapPassword; }
    public boolean isImapUseSsl() { return imapUseSsl; }
    public String getImapInboxFolder() { return imapInboxFolder; }
    public String getImapProcessedFolder() { return imapProcessedFolder; }
    public String getApprovalApiBaseUrl() { return approvalApiBaseUrl; }
    public String getApprovalApiUser() { return approvalApiUser; }
    public String getApprovalApiPassword() { return approvalApiPassword; }
    public String getApprovalApiApproveOutcome() { return approvalApiApproveOutcome; }
    public String getApprovalApiRejectOutcome() { return approvalApiRejectOutcome; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
}
