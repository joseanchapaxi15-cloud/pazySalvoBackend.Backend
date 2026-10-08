package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "paz_salvo_section")
public class AreaSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private EmployeeRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Area area;

    @Column(nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SectionStatus status = SectionStatus.PENDIENTE;

    private String signerName;
    private String signerEmail;
    private Instant signedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserAccount assignedUser;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<Answer> answers = new HashSet<>();

    protected AreaSection() {
    }

    public AreaSection(Area area, int sequenceNumber) {
        this.area = area;
        this.sequenceNumber = sequenceNumber;
    }

    public void addAnswer(Answer answer) {
        answers.add(answer);
        answer.setSection(this);
    }

    public void setRequest(EmployeeRequest request) { this.request = request; }
    public Long getId() { return id; }
    public Area getArea() { return area; }
    public int getSequenceNumber() { return sequenceNumber; }
    public SectionStatus getStatus() { return status; }
    public void setStatus(SectionStatus status) { this.status = status; }
    public String getSignerName() { return signerName; }
    public String getSignerEmail() { return signerEmail; }
    public Instant getSignedAt() { return signedAt; }
    public UserAccount getAssignedUser() { return assignedUser; }
    public void assignTo(UserAccount user) {
        assignedUser = user;
    }
    public void setSignature(String name, String email) {
        signerName = name;
        signerEmail = email;
        signedAt = Instant.now();
        status = SectionStatus.COMPLETADA;
    }
    public void complete() {
        signedAt = Instant.now();
        status = SectionStatus.COMPLETADA;
    }
    public Set<Answer> getAnswers() { return answers; }
}
