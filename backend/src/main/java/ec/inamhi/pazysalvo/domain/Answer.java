package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "paz_salvo_answer")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private AreaSection section;

    @Column(nullable = false)
    private String questionKey;

    @Column(nullable = false, length = 1000)
    private String questionLabel;

    @Column(length = 2000)
    private String writingFields;

    @Column(length = 12)
    private String answerValue;

    @Column(length = 5000)
    private String detail;

    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserAccount assignedUser;

    private String signerName;
    private String signerEmail;
    private java.time.Instant signedAt;
    private String signedDocumentPath;
    private String signedDocumentSha256;

    protected Answer() {
    }

    public Answer(String questionKey, String questionLabel) {
        this.questionKey = questionKey;
        this.questionLabel = questionLabel;
    }

    public void setSection(AreaSection section) { this.section = section; }
    public String getQuestionKey() { return questionKey; }
    public String getQuestionLabel() { return questionLabel; }
    public List<String> getWritingFields() {
        return writingFields == null || writingFields.isBlank() ? List.of() : List.of(writingFields.split("\\n"));
    }
    public void setWritingFields(List<String> fields) { this.writingFields = String.join("\n", fields); }
    public String getAnswerValue() { return answerValue; }
    public String getDetail() { return detail; }
    public BigDecimal getAmount() { return amount; }
    public UserAccount getAssignedUser() { return assignedUser; }
    public String getSignerName() { return signerName; }
    public String getSignerEmail() { return signerEmail; }
    public java.time.Instant getSignedAt() { return signedAt; }
    public String getSignedDocumentPath() { return signedDocumentPath; }
    public String getSignedDocumentSha256() { return signedDocumentSha256; }
    public void assignTo(UserAccount user) { assignedUser = user; }
    public void markSigned(String name, String email, String documentPath, String sha256) {
        if (signedAt != null) {
            throw new IllegalStateException("Esta pregunta ya fue firmada.");
        }
        signerName = name;
        signerEmail = email;
        signedAt = java.time.Instant.now();
        signedDocumentPath = documentPath;
        signedDocumentSha256 = sha256;
    }
    public void update(String value, String detail, BigDecimal amount) {
        this.answerValue = value;
        this.detail = detail;
        this.amount = amount;
    }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
