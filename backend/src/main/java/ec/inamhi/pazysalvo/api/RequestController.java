package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.RequestDtos.AuditView;
import ec.inamhi.pazysalvo.api.RequestDtos.CreateRequest;
import ec.inamhi.pazysalvo.api.RequestDtos.RequestView;
import ec.inamhi.pazysalvo.api.RequestDtos.CreateRequestResult;
import ec.inamhi.pazysalvo.api.RequestDtos.SaveAnswers;
import ec.inamhi.pazysalvo.api.RequestDtos.WorkDetailsUpdate;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.RequestStatus;
import ec.inamhi.pazysalvo.domain.AreaSection;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.service.RequestService;
import ec.inamhi.pazysalvo.service.SignedDocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Validated
@RestController
@RequestMapping("/requests")
public class RequestController {

    private final RequestService requestService;
    private final SignedDocumentService signedDocumentService;

    public RequestController(RequestService requestService,
                             SignedDocumentService signedDocumentService) {
        this.requestService = requestService;
        this.signedDocumentService = signedDocumentService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RequestView> list(@AuthenticationPrincipal UserAccount actor) {
        return requestService.findVisibleTo(actor).stream()
            .map(request -> RequestMapper.toView(request, actor)).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    @Transactional
    public ResponseEntity<CreateRequestResult> create(@Valid @RequestBody CreateRequest command,
                                              @AuthenticationPrincipal UserAccount actor) {
        EmployeeRequest request = requestService.create(command, actor.getUsername());
        requestService.createTrackingAccount(request, command.trackingUsername(), command.trackingPassword());
        return ResponseEntity.status(201).body(new CreateRequestResult(
            RequestMapper.toView(request, actor), command.trackingUsername().trim().toLowerCase(),
            command.trackingPassword()));
    }

    @GetMapping("/{requestId}")
    @Transactional(readOnly = true)
    public RequestView get(@PathVariable Long requestId,
                           @AuthenticationPrincipal UserAccount actor) {
        return RequestMapper.toView(requestService.findVisibleById(requestId, actor), actor);
    }

    @PutMapping("/{requestId}/work-details")
    @Transactional
    public RequestView updateWorkDetails(@PathVariable Long requestId,
                                         @Valid @RequestBody WorkDetailsUpdate command,
                                         @AuthenticationPrincipal UserAccount actor) {
        return RequestMapper.toView(requestService.updateWorkDetails(requestId, command.workplace(),
            command.occupationalGroup(), command.workUnit(), command.jobTitle(), actor), actor);
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    public ResponseEntity<Void> delete(@PathVariable Long requestId,
                                       @AuthenticationPrincipal UserAccount actor) {
        requestService.deleteRequest(requestId, actor.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{requestId}/sections/{sectionId}/answers")
    @Transactional
    public RequestView saveAnswers(@PathVariable Long requestId,
                                   @PathVariable Long sectionId,
                                   @Valid @RequestBody SaveAnswers command,
                                   @AuthenticationPrincipal UserAccount actor) {
        EmployeeRequest request = requestService.saveAnswers(
            requestId, sectionId, command.answers(), actor);
        return RequestMapper.toView(request, actor);
    }

    @PostMapping(value = "/{requestId}/sections/{sectionId}/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public RequestView signSectionWithP12(@PathVariable Long requestId,
                                          @PathVariable Long sectionId,
                                          @RequestParam("certificate") MultipartFile certificate,
                                          @RequestParam("password") String password,
                                          @AuthenticationPrincipal UserAccount actor) {
        signedDocumentService.signSectionWithCertificate(requestId, sectionId, certificate, password, actor);
        return RequestMapper.toView(requestService.signSection(requestId, sectionId, actor), actor);
    }

    @PostMapping("/{requestId}/sections/{sectionId}/sign-demo")
    @Transactional
    public RequestView signSectionDemo(@PathVariable Long requestId,
                                       @PathVariable Long sectionId,
                                       @AuthenticationPrincipal UserAccount actor) {
        signedDocumentService.signSectionDemo(requestId, sectionId, actor);
        return RequestMapper.toView(requestService.signSection(requestId, sectionId, actor), actor);
    }

    @PostMapping(value = "/{requestId}/sign-final", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public RequestView signFinalWithP12(@PathVariable Long requestId,
                                        @RequestParam("certificate") MultipartFile certificate,
                                        @RequestParam("password") String password,
                                        @AuthenticationPrincipal UserAccount actor) {
        return RequestMapper.toView(signedDocumentService.signFinalDocumentWithCertificate(
            requestId, certificate, password, actor), actor);
    }

    @PostMapping("/{requestId}/sign-final-demo")
    @Transactional
    public RequestView signFinalDemo(@PathVariable Long requestId,
                                     @AuthenticationPrincipal UserAccount actor) {
        return RequestMapper.toView(signedDocumentService.signFinalDocumentDemo(requestId, actor), actor);
    }

    @GetMapping("/{requestId}/sections/{sectionId}/signed-document")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadSignedSection(@PathVariable Long requestId,
                                                          @PathVariable Long sectionId,
                                                          @RequestParam(required = false) Long userId,
                                                          @AuthenticationPrincipal UserAccount actor) throws IOException {
        EmployeeRequest request = requestService.findVisibleById(requestId, actor);
        AreaSection section = request.getSections().stream()
            .filter(candidate -> candidate.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "No se encontró el apartado asignado."));
        Long documentUserId = userId;
        if (actor.getRole() == ec.inamhi.pazysalvo.domain.UserRole.RESPONSABLE_AREA) {
            documentUserId = actor.getId();
        } else if (actor.getRole() == ec.inamhi.pazysalvo.domain.UserRole.SOLICITANTE) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "La cuenta de seguimiento no puede descargar documentos internos de firma.");
        } else if (documentUserId == null) {
            List<Long> signedUsers = section.getAnswers().stream()
                .filter(answer -> answer.getSignedDocumentPath() != null)
                .map(answer -> answer.getAssignedUser() == null
                    ? section.getAssignedUser() : answer.getAssignedUser())
                .filter(java.util.Objects::nonNull)
                .map(UserAccount::getId)
                .distinct()
                .toList();
            if (signedUsers.size() != 1) {
                throw new org.springframework.web.server.ResponseStatusException(
                    signedUsers.isEmpty() ? org.springframework.http.HttpStatus.NOT_FOUND
                        : org.springframework.http.HttpStatus.BAD_REQUEST,
                    signedUsers.isEmpty() ? "No existe un PDF firmado en este apartado."
                        : "El apartado tiene más de un firmante; indica el usuario correspondiente.");
            }
            documentUserId = signedUsers.get(0);
        }
        final Long targetUserId = documentUserId;
        boolean signedByUser = section.getAnswers().stream().anyMatch(answer -> {
            UserAccount assignee = answer.getAssignedUser() == null
                ? section.getAssignedUser() : answer.getAssignedUser();
            return assignee != null && assignee.getId().equals(targetUserId)
                && answer.getSignedDocumentPath() != null;
        });
        if (!signedByUser) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "No existe un PDF firmado por esa persona en el apartado.");
        }
        if (actor.getRole() != ec.inamhi.pazysalvo.domain.UserRole.TALENTO_HUMANO
            && !actor.getId().equals(targetUserId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "No se encontró el apartado asignado.");
        }
        Resource resource = new FileSystemResource(
            signedDocumentService.locateSection(requestId, sectionId, targetUserId));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(resource.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("apartado-firmado-" + requestId + "-" + sectionId + "-" + targetUserId + ".pdf").build().toString())
            .body(resource);
    }

    @GetMapping("/{requestId}/signed-document")
    public ResponseEntity<Resource> downloadSignedDocument(
        @PathVariable Long requestId,
        @AuthenticationPrincipal UserAccount actor
    ) throws IOException {
        EmployeeRequest request = requestService.findVisibleById(requestId, actor);
        if (actor.getRole() == ec.inamhi.pazysalvo.domain.UserRole.SOLICITANTE
                && request.getSignedDocumentPath() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "El documento final todavía no está firmado.");
        }
        Path path = signedDocumentService.locate(requestId);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(resource.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("paz-salvo-firmado-" + requestId + ".pdf").build().toString())
            .body(resource);
    }

    @PostMapping("/{requestId}/final-status")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    @Transactional
    public RequestView setFinalStatus(@PathVariable Long requestId,
                                      @RequestParam RequestStatus status,
                                      @RequestParam(required = false) String observation,
                                      @AuthenticationPrincipal UserAccount actor) {
        return RequestMapper.toView(requestService.setFinalStatus(
            requestId, status, observation, actor.getUsername()), actor);
    }

    @GetMapping("/{requestId}/audit")
    @PreAuthorize("hasRole('TALENTO_HUMANO')")
    public List<AuditView> audit(@PathVariable Long requestId) {
        return requestService.history(requestId).stream()
            .map(entry -> new AuditView(entry.getId(), entry.getAction(), entry.getActor(),
                entry.getOccurredAt(), entry.getDetails(), entry.getIpAddress()))
            .toList();
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
