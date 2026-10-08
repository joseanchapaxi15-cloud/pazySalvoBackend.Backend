package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "paz_salvo_request")
public class EmployeeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String identification;

    @Column(nullable = false)
    private String email;

    private String jobTitle;
    private String workUnit;
    private String employmentType;
    private LocalDate startDate;
    private LocalDate departureDate;
    private String homeAddress;
    private String homePhone;
    private String province;
    private String mobilePhone;
    private String canton;
    private String emergencyContact;
    private String secondaryEmail;
    private String workplace;
    private String occupationalGroup;

    private String signedDocumentPath;
    private String signedDocumentSha256;
    private Instant signedDocumentUploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.EN_PROCESO;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private Set<AreaSection> sections = new HashSet<>();

    protected EmployeeRequest() {
    }

    public EmployeeRequest(String fullName, String identification, String email,
                           String jobTitle, String workUnit, String employmentType,
                           LocalDate startDate, LocalDate departureDate, String homeAddress,
                           String homePhone, String province, String mobilePhone, String canton,
                           String emergencyContact, String secondaryEmail, String workplace,
                           String occupationalGroup) {
        this.fullName = fullName;
        this.identification = identification;
        this.email = email;
        this.jobTitle = jobTitle;
        this.workUnit = workUnit;
        this.employmentType = employmentType;
        this.startDate = startDate;
        this.departureDate = departureDate;
        this.homeAddress = homeAddress;
        this.homePhone = homePhone;
        this.province = province;
        this.mobilePhone = mobilePhone;
        this.canton = canton;
        this.emergencyContact = emergencyContact;
        this.secondaryEmail = secondaryEmail;
        this.workplace = workplace;
        this.occupationalGroup = occupationalGroup;
    }

    public void addSection(AreaSection section) {
        sections.add(section);
        section.setRequest(this);
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getIdentification() { return identification; }
    public String getEmail() { return email; }
    public String getJobTitle() { return jobTitle; }
    public String getWorkUnit() { return workUnit; }
    public String getEmploymentType() { return employmentType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDepartureDate() { return departureDate; }
    public String getHomeAddress() { return homeAddress; }
    public String getHomePhone() { return homePhone; }
    public String getProvince() { return province; }
    public String getMobilePhone() { return mobilePhone; }
    public String getCanton() { return canton; }
    public String getEmergencyContact() { return emergencyContact; }
    public String getSecondaryEmail() { return secondaryEmail; }
    public String getWorkplace() { return workplace; }
    public String getOccupationalGroup() { return occupationalGroup; }
    public void updateWorkDetails(String workplace, String occupationalGroup, String workUnit, String jobTitle) {
        this.workplace = workplace;
        this.occupationalGroup = occupationalGroup;
        this.workUnit = workUnit;
        this.jobTitle = jobTitle;
    }
    public String getSignedDocumentPath() { return signedDocumentPath; }
    public String getSignedDocumentSha256() { return signedDocumentSha256; }
    public Instant getSignedDocumentUploadedAt() { return signedDocumentUploadedAt; }
    public void attachSignedDocument(String path, String sha256) {
        if (signedDocumentPath != null) {
            throw new IllegalStateException("Ya existe un documento firmado cargado.");
        }
        signedDocumentPath = path;
        signedDocumentSha256 = sha256;
        signedDocumentUploadedAt = Instant.now();
        status = RequestStatus.EN_REVISION;
    }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<AreaSection> getSections() { return sections; }
}
