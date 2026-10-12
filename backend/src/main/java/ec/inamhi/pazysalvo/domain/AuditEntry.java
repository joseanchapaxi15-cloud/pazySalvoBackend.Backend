package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "paz_salvo_audit")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(length = 2000)
    private String details;

    @Column(length = 45)
    private String ipAddress;

    protected AuditEntry() {
    }

    public AuditEntry(Long requestId, String action, String actor, String details) {
        this(requestId, action, actor, details, null);
    }

    public AuditEntry(Long requestId, String action, String actor, String details, String ipAddress) {
        this.requestId = requestId;
        this.action = action;
        this.actor = actor;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
}
