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

    @EntityGraph(attributePaths = {"sections", "sections.answers", "sections.assignedUser",
        "sections.answers.assignedUser"})
    @org.springframework.data.jpa.repository.Query(
        "select request from UserAccount account join account.trackedRequest request where account.id = :userId")
    Optional<EmployeeRequest> findTrackedRequestByUserId(
        @org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query(
        "select account.id from UserAccount account "
            + "where account.id = :userId and account.trackedRequest.id = :requestId")
    Optional<Long> findTrackingUserLink(
        @org.springframework.data.repository.query.Param("userId") Long userId,
        @org.springframework.data.repository.query.Param("requestId") Long requestId);

    boolean existsByIdentification(String identification);
}
