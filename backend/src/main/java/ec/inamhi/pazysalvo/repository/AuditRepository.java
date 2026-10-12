package ec.inamhi.pazysalvo.repository;

import ec.inamhi.pazysalvo.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByRequestIdOrderByOccurredAtDesc(Long requestId);
    List<AuditEntry> findAllByOrderByOccurredAtDesc();
    long deleteByRequestId(Long requestId);
}
