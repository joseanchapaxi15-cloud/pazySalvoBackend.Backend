package ec.inamhi.pazysalvo.repository;

import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.CustomQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomQuestionRepository extends JpaRepository<CustomQuestion, Long> {
    List<CustomQuestion> findAllByAreaOrderByIdAsc(Area area);
    boolean existsByAreaAndLabelIgnoreCase(Area area, String label);
}
