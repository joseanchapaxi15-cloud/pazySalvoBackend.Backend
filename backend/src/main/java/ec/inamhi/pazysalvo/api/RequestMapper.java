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

        if (actor.getRole() == UserRole.SOLICITANTE) {
            return new RequestView(request.getId(), request.getFullName(), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                request.getStatus(), request.getCreatedAt(), null, null, sections);
        }
        return new RequestView(
            request.getId(),
            request.getFullName(),
            request.getIdentification(),
            request.getEmail(),
            canSeeWorkDetails(actor) ? request.getJobTitle() : null,
            canSeeWorkDetails(actor) ? request.getWorkUnit() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getEmploymentType() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getStartDate() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getDepartureDate() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getHomeAddress() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getHomePhone() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getProvince() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getMobilePhone() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getCanton() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getEmergencyContact() : null,
            actor.getRole() == UserRole.TALENTO_HUMANO ? request.getSecondaryEmail() : null,
            canSeeWorkDetails(actor) ? request.getWorkplace() : null,
            canSeeWorkDetails(actor) ? request.getOccupationalGroup() : null,
            request.getStatus(),
            request.getCreatedAt(),
            request.getSignedDocumentSha256(),
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
        if (actor.getRole() == UserRole.SOLICITANTE) {
            return new SectionView(section.getId(), section.getArea(), section.getSequenceNumber(),
                section.getStatus(), null, null, null, null, null, null, List.of());
        }
        List<AnswerView> answers = section.getAnswers().stream()
            .filter(answer -> actor.getRole() == UserRole.TALENTO_HUMANO
                || assignedTo(answer, section, actor))
            .sorted(Comparator.comparing(Answer::getQuestionKey))
            .map(answer -> toAnswerView(answer, section))
            .toList();

        return new SectionView(
            section.getId(),
            section.getArea(),
            section.getSequenceNumber(),
            section.getStatus(),
            section.getAssignedUser() == null ? null : section.getAssignedUser().getId(),
            section.getAssignedUser() == null ? null : section.getAssignedUser().getFullName(),
            section.getAssignedUser() == null ? null : section.getAssignedUser().getEmail(),
            section.getSignerName(),
            section.getSignerEmail(),
            section.getSignedAt(),
            answers
        );
    }

    private static AnswerView toAnswerView(Answer answer, AreaSection section) {
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
            answer.getSignerName(),
            answer.getSignerEmail(),
            answer.getSignedAt(),
            answer.getSignedDocumentSha256(),
            answer.getWritingFields().isEmpty() ? QuestionCatalog.writingFieldsForKey(answer.getQuestionKey()) : answer.getWritingFields()
        );
    }

    private static boolean assignedTo(Answer answer, AreaSection section, UserAccount actor) {
        UserAccount assigned = answer.getAssignedUser() == null
            ? section.getAssignedUser() : answer.getAssignedUser();
        return assigned != null && assigned.getId().equals(actor.getId());
    }
}
