package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.AreaSection;
import ec.inamhi.pazysalvo.domain.Answer;
import ec.inamhi.pazysalvo.domain.RequestStatus;
import ec.inamhi.pazysalvo.domain.SectionStatus;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.repository.RequestRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class SignedDocumentService {

    private final RequestRepository requestRepository;
    private final AuditService auditService;
    private final Path uploadDirectory;
    private final ObjectMapper objectMapper;
    private final Pkcs12PdfSigner pkcs12PdfSigner;
    private final PazSalvoPdfGenerator pdfGenerator;
    private final boolean demoSigningEnabled;

    public SignedDocumentService(
            RequestRepository requestRepository,
            AuditService auditService,
            @Value("${app.upload-dir}") String uploadDirectory,
            ObjectMapper objectMapper,
            Pkcs12PdfSigner pkcs12PdfSigner,
            PazSalvoPdfGenerator pdfGenerator,
            @Value("${app.security.demo-signing-enabled:true}") boolean demoSigningEnabled) {

        this.requestRepository = requestRepository;
        this.auditService = auditService;
        this.uploadDirectory = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize();
        this.objectMapper = objectMapper;
        this.pkcs12PdfSigner = pkcs12PdfSigner;
        this.pdfGenerator = pdfGenerator;
        this.demoSigningEnabled = demoSigningEnabled;
    }

    @Transactional
    public EmployeeRequest upload(
            Long requestId,
            MultipartFile file,
            String actor) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        if (request.getStatus() != RequestStatus.PENDIENTE_FIRMA_EXFUNCIONARIO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Las áreas deben completar el flujo antes de cargar el documento firmado."
            );
        }

        if (request.getSignedDocumentPath() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un documento firmado. No se permite reemplazarlo."
            );
        }

        if (file == null || file.isEmpty()
                || file.getSize() > 20L * 1024 * 1024) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un PDF de hasta 20 MB."
            );
        }

        try {
            byte[] content = file.getBytes();

            validatePdf(content);

            String hash = sha256(content);

            Files.createDirectories(uploadDirectory);

            Path destination = uploadDirectory
                    .resolve(requestId + "-" + hash + ".pdf")
                    .normalize();

            validatePath(destination);

            Files.write(
                    destination,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            request.attachSignedDocument(
                    destination.toString(),
                    hash
            );

            auditService.record(requestId, "PDF_FIRMADO_CARGADO", actor,
                    "SHA-256: " + hash + "; bytes: " + content.length);

            return request;

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el documento firmado.",
                    exception
            );
        }
    }

    @Transactional
    public EmployeeRequest uploadSection(
            Long requestId,
            Long sectionId,
            MultipartFile file,
            UserAccount actor) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        AreaSection section = request.getSections().stream()
                .filter(item -> item.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el apartado."
                ));

        List<Answer> assignedAnswers = section.getAnswers().stream()
                .filter(answer -> {

                    UserAccount assignee = answer.getAssignedUser() == null
                            ? section.getAssignedUser()
                            : answer.getAssignedUser();

                    return assignee != null
                            && assignee.getId().equals(actor.getId());
                })
                .toList();

        if (section.getStatus() != SectionStatus.EN_CURSO
                || assignedAnswers.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes preguntas asignadas en el apartado activo."
            );
        }

        List<Answer> pendingAnswers = assignedAnswers.stream()
                .filter(answer -> answer.getSignedAt() == null)
                .toList();

        if (pendingAnswers.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Todas tus preguntas asignadas ya fueron firmadas."
            );
        }

        if (pendingAnswers.stream().anyMatch(answer ->
                !"SI".equals(answer.getAnswerValue())
                        && !"NO".equals(answer.getAnswerValue()))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Responde todas tus preguntas antes de firmarlas."
            );
        }

        if (pendingAnswers.stream()
                .anyMatch(answer -> !hasAllWrittenFields(answer))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completa todos los campos escritos de tus preguntas antes de cargar la firma."
            );
        }

        if (file == null || file.isEmpty()
                || file.getSize() > 20L * 1024 * 1024) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un PDF firmado de hasta 20 MB."
            );
        }

        try {
            byte[] content = file.getBytes();

            validatePdf(content);

            String hash = sha256(content);

            Files.createDirectories(uploadDirectory);

            Path destination = uploadDirectory.resolve(
                    requestId
                            + "-apartado-"
                            + sectionId
                            + "-usuario-"
                            + actor.getId()
                            + "-"
                            + hash
                            + ".pdf"
            ).normalize();

            validatePath(destination);

            Files.write(
                    destination,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            for (Answer answer : pendingAnswers) {

                answer.markSigned(
                        actor.getFullName(),
                        actor.getEmail(),
                        destination.toString(),
                        hash
                );
            }

            auditService.record(requestId, "APARTADO_FIRMADO_CARGADO", actor.getUsername(),
                    pendingAnswers.size() + " preguntas de " + section.getArea() + "; SHA-256: " + hash);

            return request;

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el PDF firmado del apartado.",
                    exception
            );
        }
    }

    /**
     * FIRMA DEMO DEL APARTADO.
     *
     * Permite probar el flujo completo sin certificado P12.
     * El PDF debe ser válido, pero la contraseña puede ser cualquiera.
     *
     * IMPORTANTE:
     * Esto NO constituye una firma electrónica real.
     */
    @Transactional
    public EmployeeRequest signSectionDemo(
            Long requestId,
            Long sectionId,
            UserAccount actor) {
        requireDemoSigningEnabled();
        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."));
        AreaSection section = request.getSections().stream()
                .filter(item -> item.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el apartado."));
        List<Answer> pendingAnswers = section.getAnswers().stream()
                .filter(answer -> assignedUser(answer, section) != null
                        && assignedUser(answer, section).getId().equals(actor.getId())
                        && answer.getSignedAt() == null)
                .toList();
        validateDemoAnswers(section, pendingAnswers);
        byte[] demoPdf = pdfGenerator.markAsDemo(pdfGenerator.generateSection(
                request, section, pendingAnswers, actor.getFullName()));
        String hash = sha256(demoPdf);
        Path destination = uploadDirectory.resolve(requestId + "-apartado-" + sectionId
                + "-usuario-" + actor.getId() + "-demo-" + hash + ".pdf").normalize();
        validatePath(destination);
        try {
            Files.createDirectories(uploadDirectory);
            Files.write(destination, demoPdf, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el documento de prueba.", exception);
        }
        pendingAnswers.forEach(answer -> answer.markSigned(actor.getFullName(), actor.getEmail(),
                destination.toString(), hash));
        auditService.record(requestId, "APARTADO_FIRMADO_DEMO", actor.getUsername(),
                pendingAnswers.size() + " preguntas de " + section.getArea()
                        + "; documento de prueba sin firma electrónica; SHA-256: " + hash);
        return request;
    }

    private UserAccount assignedUser(Answer answer, AreaSection section) {
        return answer.getAssignedUser() == null ? section.getAssignedUser() : answer.getAssignedUser();
    }

    private void validateDemoAnswers(AreaSection section, List<Answer> pendingAnswers) {
        if (section.getStatus() != SectionStatus.EN_CURSO || pendingAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No tienes preguntas pendientes en el apartado activo.");
        }
        List<String> unanswered = pendingAnswers.stream()
                .filter(answer -> !"SI".equals(answer.getAnswerValue())
                        && !"NO".equals(answer.getAnswerValue()))
                .map(Answer::getQuestionLabel)
                .toList();
        if (!unanswered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Responde estas preguntas antes de confirmar la prueba: "
                            + String.join("; ", unanswered));
        }
        List<String> missingFields = pendingAnswers.stream()
                .filter(answer -> !hasAllWrittenFields(answer))
                .map(Answer::getQuestionLabel)
                .toList();
        if (!missingFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Completa los campos escritos de estas preguntas antes de confirmar la prueba: "
                            + String.join("; ", missingFields));
        }
    }

    @Transactional
    public EmployeeRequest signSectionWithCertificate(
            Long requestId,
            Long sectionId,
            MultipartFile certificate,
            String certificatePassword,
            UserAccount actor) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        AreaSection section = request.getSections().stream()
                .filter(item -> item.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el apartado."
                ));

        List<Answer> assignedAnswers = section.getAnswers().stream()
                .filter(answer -> {

                    UserAccount assignee = answer.getAssignedUser() == null
                            ? section.getAssignedUser()
                            : answer.getAssignedUser();

                    return assignee != null
                            && assignee.getId().equals(actor.getId());
                })
                .toList();

        if (actor.getRole()
                != ec.inamhi.pazysalvo.domain.UserRole.RESPONSABLE_AREA
                || section.getStatus() != SectionStatus.EN_CURSO
                || assignedAnswers.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes preguntas asignadas en el apartado activo."
            );
        }

        List<Answer> pendingAnswers = assignedAnswers.stream()
                .filter(answer -> answer.getSignedAt() == null)
                .toList();

        if (pendingAnswers.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Todas tus preguntas asignadas ya fueron firmadas."
            );
        }

        if (pendingAnswers.stream().anyMatch(answer ->
                !"SI".equals(answer.getAnswerValue())
                        && !"NO".equals(answer.getAnswerValue()))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Responde todas tus preguntas antes de firmarlas."
            );
        }

        if (pendingAnswers.stream()
                .anyMatch(answer -> !hasAllWrittenFields(answer))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completa todos los campos escritos antes de firmar."
            );
        }

        if (certificate == null
                || certificate.isEmpty()
                || certificate.getSize() > 10L * 1024 * 1024
                || certificatePassword == null
                || certificatePassword.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona el archivo P12 e ingresa su contraseña."
            );
        }

        byte[] certificateBytes = null;
        char[] password = certificatePassword.toCharArray();

        try {

            certificateBytes = certificate.getBytes();

            byte[] unsignedDocument =
                    pdfGenerator.generateSection(
                            request,
                            section,
                            pendingAnswers,
                            actor.getFullName()
                    );

            byte[] signedContent =
                    pkcs12PdfSigner.sign(
                            unsignedDocument,
                            certificateBytes,
                            password,
                            actor.getFullName(),
                            actor.getEmail()
                    );

            String hash = sha256(signedContent);

            Files.createDirectories(uploadDirectory);

            Path destination = uploadDirectory.resolve(
                    requestId
                            + "-apartado-"
                            + sectionId
                            + "-usuario-"
                            + actor.getId()
                            + "-"
                            + hash
                            + ".pdf"
            ).normalize();

            validatePath(destination);

            Files.write(
                    destination,
                    signedContent,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            for (Answer answer : pendingAnswers) {

                answer.markSigned(
                        actor.getFullName(),
                        actor.getEmail(),
                        destination.toString(),
                        hash
                );
            }

            auditService.record(requestId, "APARTADO_FIRMADO_P12", actor.getUsername(),
                    pendingAnswers.size() + " preguntas de " + section.getArea() + "; SHA-256: " + hash);

            return request;

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar o guardar el PDF firmado.",
                    exception
            );

        } finally {

            java.util.Arrays.fill(password, '\0');

            if (certificateBytes != null) {
                java.util.Arrays.fill(certificateBytes, (byte) 0);
            }
        }
    }

    /**
     * FIRMA DEMO DEL DOCUMENTO FINAL.
     *
     * Permite probar la última etapa del flujo sin certificado P12.
     * El PDF puede ser cualquier PDF válido y la contraseña puede ser cualquiera.
     *
     * IMPORTANTE:
     * Esto NO constituye una firma electrónica real.
     */
    @Transactional
    public EmployeeRequest signFinalDocumentDemo(
            Long requestId,
            UserAccount actor) {
        requireDemoSigningEnabled();

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        if (actor.getRole()
                != ec.inamhi.pazysalvo.domain.UserRole.SOLICITANTE
                || requestRepository
                .findTrackingUserLink(actor.getId(), requestId)
                .isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el solicitante vinculado puede firmar este documento final."
            );
        }

        if (request.getStatus()
                != RequestStatus.PENDIENTE_FIRMA_EXFUNCIONARIO
                || request.getSignedDocumentPath() != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La solicitud aún no está lista para la firma final o ya fue firmada."
            );
        }

        boolean allSectionsComplete =
                !request.getSections().isEmpty()
                        && request.getSections().stream().allMatch(section ->
                        section.getStatus() == SectionStatus.COMPLETADA
                                && section.getAnswers().stream()
                                .allMatch(answer -> answer.getSignedAt() != null)
                );

        if (!allSectionsComplete) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Todas las áreas deben completar y firmar sus preguntas antes del documento final."
            );
        }

        byte[] content = pdfGenerator.markAsDemo(pdfGenerator.generateFinal(request));
        String hash = sha256(content);
        Path destination = uploadDirectory.resolve(requestId + "-final-demo-" + hash + ".pdf").normalize();
        validatePath(destination);
        try {
            Files.createDirectories(uploadDirectory);
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el documento final de prueba.", exception);
        }
        request.attachSignedDocument(destination.toString(), hash);
        auditService.record(requestId, "DOCUMENTO_FINAL_FIRMADO_DEMO", actor.getUsername(),
                "Documento de prueba sin firma electrónica; SHA-256: " + hash);
        return request;
    }

    @Transactional
    public EmployeeRequest signFinalDocumentWithCertificate(
            Long requestId,
            MultipartFile certificate,
            String certificatePassword,
            UserAccount actor) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        if (actor.getRole()
                != ec.inamhi.pazysalvo.domain.UserRole.SOLICITANTE
                || requestRepository
                .findTrackingUserLink(actor.getId(), requestId)
                .isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el solicitante vinculado puede firmar este documento final."
            );
        }

        if (request.getStatus()
                != RequestStatus.PENDIENTE_FIRMA_EXFUNCIONARIO
                || request.getSignedDocumentPath() != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La solicitud aún no está lista para la firma final o ya fue firmada."
            );
        }

        boolean allSectionsComplete =
                !request.getSections().isEmpty()
                        && request.getSections().stream().allMatch(section ->
                        section.getStatus() == SectionStatus.COMPLETADA
                                && section.getAnswers().stream()
                                .allMatch(answer -> answer.getSignedAt() != null)
                );

        if (!allSectionsComplete) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Todas las áreas deben completar y firmar sus preguntas antes del documento final."
            );
        }

        if (certificate == null
                || certificate.isEmpty()
                || certificate.getSize() > 10L * 1024 * 1024
                || certificatePassword == null
                || certificatePassword.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona el archivo P12 e ingresa su contraseña."
            );
        }

        byte[] certificateBytes = null;
        char[] password = certificatePassword.toCharArray();

        try {

            certificateBytes = certificate.getBytes();

            byte[] unsignedDocument =
                    pdfGenerator.generateFinal(request);

            byte[] signedContent =
                    pkcs12PdfSigner.sign(
                            unsignedDocument,
                            certificateBytes,
                            password,
                            actor.getFullName(),
                            actor.getEmail()
                    );

            String hash = sha256(signedContent);

            Files.createDirectories(uploadDirectory);

            Path destination = uploadDirectory.resolve(
                    requestId
                            + "-final-"
                            + hash
                            + ".pdf"
            ).normalize();

            validatePath(destination);

            Files.write(
                    destination,
                    signedContent,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            request.attachSignedDocument(
                    destination.toString(),
                    hash
            );

            auditService.record(requestId, "DOCUMENTO_FINAL_FIRMADO_P12", actor.getUsername(),
                    "SHA-256: " + hash + "; bytes: " + signedContent.length);

            return request;

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar o guardar el documento final firmado.",
                    exception
            );

        } finally {

            java.util.Arrays.fill(password, '\0');

            if (certificateBytes != null) {
                java.util.Arrays.fill(certificateBytes, (byte) 0);
            }
        }
    }

    @Transactional(readOnly = true)
    public Path locateSection(
            Long requestId,
            Long sectionId,
            Long userId) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        AreaSection section = request.getSections().stream()
                .filter(item -> item.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el apartado."
                ));

        Answer signedAnswer = section.getAnswers().stream()
                .filter(answer -> {

                    UserAccount assignee = answer.getAssignedUser() == null
                            ? section.getAssignedUser()
                            : answer.getAssignedUser();

                    return assignee != null
                            && assignee.getId().equals(userId)
                            && answer.getSignedDocumentPath() != null;
                })
                .findFirst()
                .orElse(null);

        if (signedAnswer == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró un PDF firmado para esa persona en el apartado."
            );
        }

        Path document = Path.of(
                signedAnswer.getSignedDocumentPath()
        ).toAbsolutePath().normalize();

        if (!document.startsWith(uploadDirectory)
                || !Files.exists(document)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró el archivo almacenado."
            );
        }

        try {

            if (!sha256(Files.readAllBytes(document))
                    .equals(signedAnswer.getSignedDocumentSha256())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El archivo no coincide con el hash registrado."
                );
            }

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo verificar el archivo almacenado.",
                    exception
            );
        }

        return document;
    }

    @Transactional(readOnly = true)
    public Path locate(Long requestId) {

        EmployeeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la solicitud."
                ));

        if (request.getSignedDocumentPath() == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "La solicitud todavía no tiene un PDF firmado."
            );
        }

        Path document = Path.of(
                request.getSignedDocumentPath()
        ).toAbsolutePath().normalize();

        if (!document.startsWith(uploadDirectory)
                || !Files.exists(document)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró el archivo almacenado."
            );
        }

        try {

            String actualHash =
                    sha256(Files.readAllBytes(document));

            if (!actualHash.equals(
                    request.getSignedDocumentSha256())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El archivo no coincide con el hash registrado y no se puede entregar."
                );
            }

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo verificar el archivo almacenado.",
                    exception
            );
        }

        return document;
    }

    public void deleteStoredFiles(EmployeeRequest request) {

        List<String> storedPaths =
                new java.util.ArrayList<>();

        storedPaths.add(
                request.getSignedDocumentPath()
        );

        request.getSections().forEach(section ->
                section.getAnswers().forEach(answer ->
                        storedPaths.add(
                                answer.getSignedDocumentPath()
                        )
                )
        );

        storedPaths.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(path ->
                        Path.of(path)
                                .toAbsolutePath()
                                .normalize()
                )
                .filter(path ->
                        path.startsWith(uploadDirectory)
                )
                .forEach(path -> {

                    try {

                        Files.deleteIfExists(path);

                    } catch (IOException exception) {

                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "No se pudo eliminar uno de los PDF asociados a la solicitud.",
                                exception
                        );
                    }
                });
    }

    private String sha256(byte[] content) {

        try {

            return HexFormat.of().formatHex(
                    MessageDigest
                            .getInstance("SHA-256")
                            .digest(content)
            );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 no está disponible.",
                    exception
            );
        }
    }

    private void validatePdf(byte[] content) {

        if (content == null
                || content.length < 5
                || content[0] != '%'
                || content[1] != 'P'
                || content[2] != 'D'
                || content[3] != 'F'
                || content[4] != '-') {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El archivo no tiene una cabecera PDF válida."
            );
        }
    }

    private void validatePath(Path path) {

        if (!path.startsWith(uploadDirectory)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ruta de archivo no válida."
            );
        }
    }

    private void requireDemoSigningEnabled() {
        if (!demoSigningEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La firma de prueba está deshabilitada.");
        }
    }

    private boolean hasAllWrittenFields(Answer answer) {

        List<String> fields =
                answer.getQuestionKey().contains("_custom_")
                        ? answer.getWritingFields()
                        : QuestionCatalog.writingFieldsForKey(
                        answer.getQuestionKey()
                );

        if (fields.isEmpty()) {
            return true;
        }

        java.util.Map<String, String> values;
        try {
            values = answer.getDetail() != null
                    && answer.getDetail().startsWith("__FIELDS__:")
                    ? objectMapper.readValue(
                            answer.getDetail().substring("__FIELDS__:".length()),
                            new TypeReference<java.util.Map<String, String>>() {})
                    : java.util.Map.of();
        } catch (IOException exception) {
            return false;
        }

        for (String field : fields) {
            if (field.toLowerCase(java.util.Locale.ROOT).contains("valor a descontar")) {
                java.math.BigDecimal amount = answer.getAmount();
                if (amount == null && values.get(field) != null) {
                    try {
                        amount = new java.math.BigDecimal(values.get(field).trim().replace(',', '.'));
                        answer.setAmount(amount);
                    } catch (NumberFormatException exception) {
                        return false;
                    }
                }
                if (amount == null || amount.signum() < 0) return false;
            } else if (values.get(field) == null || values.get(field).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
