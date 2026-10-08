package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.RequestDtos.RequestView;
import ec.inamhi.pazysalvo.service.RequestService;
import ec.inamhi.pazysalvo.domain.UserAccount;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@CrossOrigin(origins = {"http://localhost:4200"})
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
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Solicitud,Servidor,Cédula,Correo,Cargo,Unidad,Estado,Área,Pregunta,Respuesta,Observación,Valor,Responsable asignado,Firmante de pregunta,Fecha de firma\r\n");

        for (RequestView request : requests) {
            if (request.sections().isEmpty()) {
                appendRow(csv, request, null, null);
                continue;
            }
            request.sections().forEach(section -> {
                if (section.answers().isEmpty()) {
                    appendRow(csv, request, section, null);
                    return;
                }
                section.answers().forEach(answer -> appendRow(csv, request, section, answer));
            });
        }

        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"reporte-paz-y-salvo-" + LocalDate.now() + ".csv\"")
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
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
            if (index > 0) csv.append(',');
            csv.append(escapeCsv(values[index]));
        }
        csv.append("\r\n");
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String safe = value.replaceAll("^[\\s]*[=+@]", "'$0");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
