package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.RequestStatus;
import ec.inamhi.pazysalvo.domain.SectionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RequestDtos {

    private RequestDtos() {
    }

    public record CreateRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 100)
        @Pattern(regexp = "[\\p{L} .'-]+", message = "El nombre solo puede contener letras y espacios.") String fullName,
        @NotBlank @Pattern(regexp = "[0-9]{1,10}", message = "La cédula debe contener solo números y máximo 10 dígitos.") String identification,
        @NotBlank @Email String email,
        @NotBlank @Email String secondaryEmail,
        @NotBlank String workUnit,
        String jobTitle,
        String workplace,
        String occupationalGroup,
        @NotNull Long directionResponsibleId,
        @NotBlank @Pattern(regexp = "Nombramiento permanente|Nombramiento provisional|Contrato ocasional|Contrato de trabajo") String employmentType,
        @jakarta.validation.constraints.NotNull LocalDate startDate,
        @jakarta.validation.constraints.NotNull LocalDate departureDate,
        @NotBlank @jakarta.validation.constraints.Size(max = 250) String homeAddress,
        @NotBlank @Pattern(regexp = "[0-9]{1,10}", message = "El teléfono de domicilio debe contener solo números y máximo 10 dígitos.") String homePhone,
        @NotBlank String province,
        @NotBlank @Pattern(regexp = "[0-9]{1,10}", message = "El celular debe contener solo números y máximo 10 dígitos.") String mobilePhone,
        @NotBlank String canton,
        @NotBlank @Pattern(regexp = "[0-9]{1,10}", message = "El contacto de emergencia debe contener solo números y máximo 10 dígitos.") String emergencyContact,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String trackingUsername,
        @NotBlank @jakarta.validation.constraints.Size(min = 1, max = 72) String trackingPassword
    ) {
    }

    public record WorkDetailsUpdate(@NotBlank String workplace, @NotBlank String occupationalGroup,
                                    @NotBlank String workUnit, @NotBlank String jobTitle) { }

    public record AnswerUpdate(
        @NotBlank String questionKey,
        String answerValue,
        String detail,
        BigDecimal amount
    ) {
    }

    public record SaveAnswers(
        @NotEmpty List<@Valid AnswerUpdate> answers
    ) {
    }

    public record SectionSignature(
        @NotBlank String signerName,
        @NotBlank @Email String signerEmail
    ) {
    }

    public record AnswerView(
        String questionKey,
        String questionLabel,
        String answerValue,
        String detail,
        BigDecimal amount,
        Long assignedUserId,
        String assignedUserName,
        boolean assignedUserEnabled,
        String signerName,
        String signerEmail,
        Instant signedAt,
        String signedDocumentSha256,
        List<String> writingFields
    ) {
    }

    public record SectionView(
        Long id,
        Area area,
        int sequenceNumber,
        SectionStatus status,
        Long assignedUserId,
        String assignedUserName,
        String assignedUserEmail,
        String signerName,
        String signerEmail,
        Instant signedAt,
        List<AnswerView> answers
    ) {
    }

    public record RequestView(
        Long id,
        String fullName,
        String identification,
        String email,
        String jobTitle,
        String workUnit,
        String employmentType,
        LocalDate startDate,
        LocalDate departureDate,
        String homeAddress,
        String homePhone,
        String province,
        String mobilePhone,
        String canton,
        String emergencyContact,
        String secondaryEmail,
        String workplace,
        String occupationalGroup,
        RequestStatus status,
        Instant createdAt,
        String signedDocumentSha256,
        Instant signedDocumentUploadedAt,
        List<SectionView> sections
    ) {
    }

    public record CreateRequestResult(RequestView request, String trackingUsername,
                                     String trackingPassword) {
    }

    public record AuditView(
        Long id,
        String action,
        String actor,
        Instant occurredAt,
        String details,
        String ipAddress
    ) {
    }
}
