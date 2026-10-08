package ec.inamhi.pazysalvo.repository;

import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<EmployeeRequest, Long> {

    @Override
    @EntityGraph(attributePaths = {"sections", "sections.answers", "sections.assignedUser", "sections.answers.assignedUser"})
    List<EmployeeRequest> findAll();

    @Override
    @EntityGraph(attributePaths = {"sections", "sections.answers", "sections.assignedUser", "sections.answers.assignedUser"})
    Optional<EmployeeRequest> findById(Long id);

    boolean existsByIdentification(String identification);
}
