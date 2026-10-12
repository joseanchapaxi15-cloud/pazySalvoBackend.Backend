package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.RequestDtos.RequestView;
import ec.inamhi.pazysalvo.domain.AuditEntry;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.service.RequestService;
import ec.inamhi.pazysalvo.domain.UserAccount;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final RequestService requestService;

    public ReportController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal UserAccount actor) {
        List<RequestView> requests = requestService.findAll().stream()
            .map(request -> RequestMapper.toView(request, actor))
            .toList();
        StringBuilder csv = new StringBuilder("\uFEFFsep=;\r\n");
        csv.append("Solicitud;Servidor;Cédula;Correo;Cargo;Unidad;Estado;Área;Pregunta;Respuesta;Observación;Valor;Responsable asignado;Firmante de pregunta;Fecha de firma\r\n");

        for (RequestView request : requests) {
            request.sections().forEach(section -> {
                section.answers().stream()
                    .filter(this::hasRecordedAnswer)
                    .forEach(answer -> appendRow(csv, request, section, answer));
            });
        }

        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_16LE))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"reporte-paz-y-salvo-" + LocalDate.now() + ".csv\"")
            .body(csv.toString().getBytes(StandardCharsets.UTF_16LE));
    }

    @GetMapping(value = "/audit.csv", produces = "text/csv")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    public ResponseEntity<byte[]> exportAuditCsv() {
        List<AuditEntry> entries = requestService.auditHistory();
        var requestNames = requestService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(EmployeeRequest::getId, EmployeeRequest::getFullName));
        StringBuilder csv = new StringBuilder("\uFEFFsep=;\r\n");
        csv.append("Solicitud;Servidor;Acción;Usuario;Fecha y hora;Dirección IP;Detalle\r\n");
        for (AuditEntry entry : entries) {
            String[] values = {
                String.valueOf(entry.getRequestId()),
                requestNames.getOrDefault(entry.getRequestId(), "Solicitud eliminada"),
                entry.getAction(),
                entry.getActor(),
                entry.getOccurredAt() == null ? "" : entry.getOccurredAt().toString(),
                entry.getIpAddress(),
                entry.getDetails()
            };
            for (int index = 0; index < values.length; index++) {
                if (index > 0) csv.append(';');
                csv.append(escapeCsv(values[index]));
            }
            csv.append("\r\n");
        }
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_16LE))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"auditoria-paz-y-salvo-" + LocalDate.now() + ".csv\"")
            .body(csv.toString().getBytes(StandardCharsets.UTF_16LE));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    public List<AuditReportView> auditHistory() {
        var requestNames = requestService.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(EmployeeRequest::getId, EmployeeRequest::getFullName));
        return requestService.auditHistory().stream()
            .map(entry -> new AuditReportView(entry.getId(), entry.getRequestId(),
                requestNames.getOrDefault(entry.getRequestId(), "Solicitud eliminada"),
                entry.getAction(), entry.getActor(), entry.getOccurredAt(), entry.getDetails(), entry.getIpAddress()))
            .toList();
    }

    private void appendRow(StringBuilder csv, RequestView request,
                           RequestDtos.SectionView section,
                           RequestDtos.AnswerView answer) {
        String[] values = {
            String.valueOf(request.id()),
            request.fullName(),
            request.identification(),
            request.email(),
            request.jobTitle(),
            request.workUnit(),
            request.status().name(),
            section == null ? "" : section.area().name(),
            answer == null ? "" : answer.questionLabel(),
            answer == null ? "" : answer.answerValue(),
            answer == null ? "" : answer.detail(),
            answer == null || answer.amount() == null ? "" : answer.amount().toPlainString(),
            answer == null ? "" : answer.assignedUserName(),
            answer == null ? "" : answer.signerName(),
            answer == null || answer.signedAt() == null ? "" : answer.signedAt().toString()
        };
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(';');
            csv.append(escapeCsv(values[index]));
        }
        csv.append("\r\n");
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String safe = value.replaceAll("^[\\s]*[=+@]", "'$0");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private boolean hasRecordedAnswer(RequestDtos.AnswerView answer) {
        return answer.answerValue() != null && !answer.answerValue().isBlank();
    }

    public record AuditReportView(Long id, Long requestId, String requestName, String action,
                                  String actor, java.time.Instant occurredAt,
                                  String details, String ipAddress) {}
}
