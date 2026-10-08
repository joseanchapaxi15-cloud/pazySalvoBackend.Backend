package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumn;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "paz_salvo_user")
public class UserAccount implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Area area;

    @Column(length = 120)
    private String workUnit;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "paz_salvo_user_question", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "question_key", nullable = false, length = 255, unique = true)
    private Set<String> assignedQuestionKeys = new HashSet<>();

    @OneToOne
    @JoinColumn(name = "tracked_request_id", unique = true)
    private EmployeeRequest trackedRequest;

    protected UserAccount() {
    }

    public UserAccount(String username, String fullName, String email,
                       String passwordHash, UserRole role, Area area) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.area = area;
    }

    public Long getId() { return id; }
    @Override public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public Area getArea() { return area; }
    public String getWorkUnit() { return workUnit; }
    public void setWorkUnit(String workUnit) { this.workUnit = workUnit; }
    public void setArea(Area area) { this.area = area; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public EmployeeRequest getTrackedRequest() { return trackedRequest; }
    public void setTrackedRequest(EmployeeRequest trackedRequest) { this.trackedRequest = trackedRequest; }
    public void updateProfile(String username, String fullName, String email) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }
    public Instant getCreatedAt() { return createdAt; }
    public Set<String> getAssignedQuestionKeys() { return Set.copyOf(assignedQuestionKeys); }
    public void setAssignedQuestionKeys(Set<String> questionKeys) {
        assignedQuestionKeys.clear();
        assignedQuestionKeys.addAll(questionKeys);
    }
    @Override public String getPassword() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
