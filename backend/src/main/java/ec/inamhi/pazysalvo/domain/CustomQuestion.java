package ec.inamhi.pazysalvo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "paz_salvo_custom_question")
public class CustomQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Area area;

    @Column(nullable = false, length = 1000)
    private String label;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CustomQuestion() {
    }

    public CustomQuestion(Area area, String label, String createdBy) {
        this.area = area;
        this.label = label;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Area getArea() { return area; }
    public String getLabel() { return label; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
