package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.domain.Answer;
import ec.inamhi.pazysalvo.domain.AreaSection;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.domain.UserRole;
import ec.inamhi.pazysalvo.service.QuestionCatalog;

import java.util.List;
import java.util.Comparator;

import static ec.inamhi.pazysalvo.api.RequestDtos.*;

public final class RequestMapper {

    private RequestMapper() {
    }

    public static RequestView toView(EmployeeRequest request, UserAccount actor) {
        List<SectionView> sections = request.getSections().stream()
            .filter(section -> actor.getRole() == UserRole.TALENTO_HUMANO
                || actor.getRole() == UserRole.SOLICITANTE
                || section.getAnswers().stream().anyMatch(answer -> assignedTo(answer, section, actor)))
            .sorted(Comparator.comparingInt(section -> section.getSequenceNumber()))
            .map(section -> toSectionView(section, actor))
            .toList();

        return new RequestView(
            request.getId(),
            request.getFullName(),
            request.getIdentification(),
            request.getEmail(),
            (canSeeWorkDetails(actor) || actor.getRole() == UserRole.SOLICITANTE) ? request.getJobTitle() : null,
            (canSeeWorkDetails(actor) || actor.getRole() == UserRole.SOLICITANTE) ? request.getWorkUnit() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getEmploymentType() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getStartDate() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getDepartureDate() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getHomeAddress() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getHomePhone() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getProvince() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getMobilePhone() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getCanton() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getEmergencyContact() : null,
            (actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE) ? request.getSecondaryEmail() : null,
            (canSeeWorkDetails(actor) || actor.getRole() == UserRole.SOLICITANTE) ? request.getWorkplace() : null,
            (canSeeWorkDetails(actor) || actor.getRole() == UserRole.SOLICITANTE) ? request.getOccupationalGroup() : null,
            request.getStatus(),
            request.getCreatedAt(),
            actor.getRole() == UserRole.RESPONSABLE_AREA ? null : request.getSignedDocumentSha256(),
            request.getSignedDocumentUploadedAt(),
            sections
        );
    }

    private static boolean canSeeWorkDetails(UserAccount actor) {
        return actor.getRole() == UserRole.TALENTO_HUMANO
            || (actor.getRole() == UserRole.RESPONSABLE_AREA
                && actor.getArea() == ec.inamhi.pazysalvo.domain.Area.DIRECCION_UNIDAD);
    }

    private static SectionView toSectionView(AreaSection section, UserAccount actor) {
        List<AnswerView> answers = section.getAnswers().stream()
            .filter(answer -> actor.getRole() == UserRole.TALENTO_HUMANO || actor.getRole() == UserRole.SOLICITANTE
                || assignedTo(answer, section, actor))
            .sorted(Comparator.comparing(Answer::getQuestionKey))
            .map(answer -> toAnswerView(answer, section, actor))
            .toList();

        return new SectionView(
            section.getId(),
            section.getArea(),
            section.getSequenceNumber(),
            section.getStatus(),
            section.getAssignedUser() == null ? null : section.getAssignedUser().getId(),
            section.getAssignedUser() == null ? null : section.getAssignedUser().getFullName(),
            actor.getRole() == UserRole.SOLICITANTE || section.getAssignedUser() == null
                ? null : section.getAssignedUser().getEmail(),
            section.getSignerName(),
            section.getSignerEmail(),
            section.getSignedAt(),
            answers
        );
    }

    private static AnswerView toAnswerView(Answer answer, AreaSection section, UserAccount actor) {
        UserAccount assignee = answer.getAssignedUser() == null
            ? section.getAssignedUser() : answer.getAssignedUser();
        return new AnswerView(
            answer.getQuestionKey(),
            answer.getQuestionLabel(),
            answer.getAnswerValue(),
            answer.getDetail(),
            answer.getAmount(),
            assignee == null ? null : assignee.getId(),
            assignee == null ? null : assignee.getFullName(),
            assignee != null && assignee.isEnabled(),
            answer.getSignerName(),
            actor.getRole() == UserRole.SOLICITANTE ? null : answer.getSignerEmail(),
            answer.getSignedAt(),
            answer.getSignedDocumentSha256(),
            answer.getQuestionKey().contains("_custom_")
                ? answer.getWritingFields()
                : QuestionCatalog.writingFieldsForKey(answer.getQuestionKey())
        );
    }

    private static boolean assignedTo(Answer answer, AreaSection section, UserAccount actor) {
        UserAccount assigned = answer.getAssignedUser() == null
            ? section.getAssignedUser() : answer.getAssignedUser();
        return assigned != null && assigned.getId().equals(actor.getId());
    }
}
