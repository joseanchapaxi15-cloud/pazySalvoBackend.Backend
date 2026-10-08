package ec.inamhi.pazysalvo.repository;

import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    List<UserAccount> findAllByOrderByFullNameAsc();
    List<UserAccount> findAllByRoleAndArea(UserRole role, Area area);
    Optional<UserAccount> findByTrackedRequestId(Long requestId);
}
