package com.emailapproval.imap;

import com.emailapproval.config.AgentConfig;

import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for connecting to an IMAP mailbox and processing emails.
 */
public class ImapMailboxService {

    private static final Logger LOG = Logger.getLogger(ImapMailboxService.class.getName());

    private final AgentConfig config;

    public ImapMailboxService(AgentConfig config) {
        this.config = config;
    }

    public void processInbox(EmailProcessor processor) {
        Store store = null;
        Folder inbox = null;
        Folder processedFolder = null;

        try {
            store = connect();
            inbox = store.getFolder(config.getImapInboxFolder());
            inbox.open(Folder.READ_WRITE);

            processedFolder = getOrCreateProcessedFolder(store, inbox);
            processedFolder.open(Folder.READ_WRITE);

            Message[] messages = inbox.getMessages();
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            inbox.fetch(messages, fetchProfile);

            for (Message message : messages) {
                try {
                    String subject = getSubject(message);
                    if (subject == null || subject.isBlank()) continue;

                    if (processor.process(message, subject)) {
                        moveToProcessed(message, inbox, processedFolder);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to process message: " + e.getMessage(), e);
                }
            }
        } catch (MessagingException e) {
            throw new RuntimeException("IMAP error: " + e.getMessage(), e);
        } finally {
            closeQuietly(processedFolder);
            closeQuietly(inbox);
            closeQuietly(store);
        }
    }

    private Store connect() throws MessagingException {
        Properties props = new Properties();
        String protocol = config.isImapUseSsl() ? "imaps" : "imap";
        props.setProperty("mail.store.protocol", protocol);
        props.setProperty("mail.imap.host", config.getImapHost());
        props.setProperty("mail.imap.port", String.valueOf(config.getImapPort()));

        Store store = Session.getDefaultInstance(props).getStore(protocol);
        store.connect(config.getImapHost(), config.getImapUser(), config.getImapPassword());
        LOG.fine("Connected to IMAP mailbox");
        return store;
    }

    private Folder getOrCreateProcessedFolder(Store store, Folder parent) throws MessagingException {
        String folderName = config.getImapProcessedFolder();
        Folder folder = store.getFolder(folderName);

        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES);
            LOG.info("Created folder: " + folderName);
        }
        return folder;
    }

    private void moveToProcessed(Message message, Folder sourceFolder, Folder destFolder) {
        try {
            sourceFolder.copyMessages(new Message[]{message}, destFolder);
            message.setFlag(Flags.Flag.DELETED, true);
            sourceFolder.expunge();
            LOG.info("Moved email to " + config.getImapProcessedFolder());
        } catch (MessagingException e) {
            LOG.log(Level.SEVERE, "Failed to move email to processed folder", e);
        }
    }

    private String getSubject(Message message) throws MessagingException {
        String subject = message.getSubject();
        if (subject == null) return null;
        try {
            return MimeUtility.decodeText(subject);
        } catch (Exception e) {
            return subject;
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException e) {
                LOG.fine("Error closing folder: " + e.getMessage());
            }
        }
    }

    private void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                LOG.fine("Error closing store: " + e.getMessage());
            }
        }
    }
}
