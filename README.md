# Email Approval Agent

A Java agent that polls an IMAP mailbox for approval emails, completes work items via the REST API, and moves processed emails to an "Agent-Processed" folder.

## How It Works

1. **Connects to IMAP** – Opens the configured mailbox (supports SSL/TLS)
2. **Reads emails** – Looks for messages with subject matching `{caseOid}-{workItemId}-{decision}`
3. **Completes work item** – Calls the Complete Work Item REST API
4. **Moves email** – On success, moves the email to the "Agent-Processed" folder

## Email Subject Format

The subject must match:

```
{caseOid}-{workItemId}-{decision}
```

Example: `3091ccc5-f3f6-4a06-92b5-803afce1ce57-4-approve`

- **caseOid**: Case OID (UUID)
- **workItemId**: Work item identifier (numeric)
- **decision**: `approve` or `reject`

## Velocity snippet: mailto approve/reject links

If your notification email template is written in Velocity, you can generate clickable `mailto:` links whose **subject matches the required format**.

Snippet (add it wherever your template renders action links/buttons):

```velocity
#set($caseOid = $event.getCase().getOid())
#set($workItemId = $event.getWorkItem().getId())
#set($approveSubject = "${caseOid}-${workItemId}-approve")
#set($rejectSubject  = "${caseOid}-${workItemId}-reject")

<a href="mailto:approvals@example.com?subject=${approveSubject}">Approve</a>
<a href="mailto:approvals@example.com?subject=${rejectSubject}">Reject</a>
```

Notes:
- Replace `approvals@example.com` with the mailbox your agent polls (`IMAP_USER`).
- Don’t add extra prefixes/suffixes to the subject; the agent expects **exactly** `{caseOid}-{workItemId}-{decision}`.

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `IMAP_HOST` | Yes | - | IMAP server hostname |
| `IMAP_PORT` | No | 993 | IMAP port |
| `IMAP_USER` | Yes | - | Mailbox username |
| `IMAP_PASSWORD` | Yes | - | Mailbox password |
| `IMAP_USE_SSL` | No | true | Use SSL/TLS for IMAP |
| `IMAP_INBOX_FOLDER` | No | INBOX | Folder to poll |
| `IMAP_PROCESSED_FOLDER` | No | Agent-Processed | Folder for processed emails |
| `APPROVAL_API_BASE_URL` | Yes | - | Approval API base URL (e.g. `http://localhost:8080/rest`) |
| `APPROVAL_API_USER` | Yes | - | API username |
| `APPROVAL_API_PASSWORD` | Yes | - | API password |
| `APPROVAL_API_APPROVE_OUTCOME` | No | (see docs) | Outcome URI for approve |
| `APPROVAL_API_REJECT_OUTCOME` | No | (see docs) | Outcome URI for reject |
| `POLL_INTERVAL_SECONDS` | No | 60 | Seconds between inbox polls |

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
java -jar target/email-approval-agent.jar
```

## Docker

```bash
docker build -t email-approval-agent .
docker run -e IMAP_HOST=imap.example.com \
           -e IMAP_USER=approvals@example.com \
           -e IMAP_PASSWORD=secret \
           -e APPROVAL_API_BASE_URL=http://localhost:8080/rest \
           -e APPROVAL_API_USER=administrator \
           -e APPROVAL_API_PASSWORD=secret \
           email-approval-agent
```

## Approval API

The agent calls:

```
POST {APPROVAL_API_BASE_URL}/cases/{caseOid}/workItems/{workItemId}/complete
```

With JSON body:

```json
{
  "output": {
    "@type": "c:AbstractWorkItemOutputType",
    "comment": "Processed by Email Approval Agent",
    "outcome": "{APPROVAL_API_APPROVE_OUTCOME or APPROVAL_API_REJECT_OUTCOME}"
  }
}
```

Outcome is taken from `APPROVAL_API_APPROVE_OUTCOME` or `APPROVAL_API_REJECT_OUTCOME` based on the email subject.
