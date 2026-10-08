package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.domain.AuditEntry;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.AreaSection;
import ec.inamhi.pazysalvo.domain.Answer;
import ec.inamhi.pazysalvo.domain.RequestStatus;
import ec.inamhi.pazysalvo.domain.SectionStatus;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.repository.AuditRepository;
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
    private final AuditRepository auditRepository;
    private final Path uploadDirectory;
    private final ObjectMapper objectMapper;

    public SignedDocumentService(RequestRepository requestRepository,
                                 AuditRepository auditRepository,
                                 @Value("${app.upload-dir}") String uploadDirectory,
                                 ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.auditRepository = auditRepository;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmployeeRequest upload(Long requestId, MultipartFile file, String actor) {
        EmployeeRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la solicitud."));

        if (request.getStatus() != RequestStatus.PENDIENTE_FIRMA_EXFUNCIONARIO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Las áreas deben completar el flujo antes de cargar el documento firmado.");
        }
        if (request.getSignedDocumentPath() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un documento firmado. No se permite reemplazarlo.");
        }
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Selecciona un PDF de hasta 20 MB.");
        }

        try {
            byte[] content = file.getBytes();
            if (content.length < 5 || content[0] != '%'
                || content[1] != 'P' || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo no tiene una cabecera PDF válida.");
            }

            String hash = sha256(content);
            Files.createDirectories(uploadDirectory);
            Path destination = uploadDirectory.resolve(requestId + "-" + hash + ".pdf").normalize();
            if (!destination.startsWith(uploadDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ruta de archivo no válida.");
            }
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            request.attachSignedDocument(destination.toString(), hash);
            auditRepository.save(new AuditEntry(requestId, "PDF_FIRMADO_CARGADO", actor,
                "SHA-256: " + hash + "; bytes: " + content.length));
            return request;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo guardar el documento firmado.", exception);
        }
    }

    @Transactional
    public EmployeeRequest uploadSection(Long requestId, Long sectionId,
                                         MultipartFile file, UserAccount actor) {
        EmployeeRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la solicitud."));
        AreaSection section = request.getSections().stream()
            .filter(item -> item.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el apartado."));

        List<Answer> assignedAnswers = section.getAnswers().stream()
            .filter(answer -> {
                UserAccount assignee = answer.getAssignedUser() == null
                    ? section.getAssignedUser() : answer.getAssignedUser();
                return assignee != null && assignee.getId().equals(actor.getId());
            })
            .toList();
        if (section.getStatus() != SectionStatus.EN_CURSO || assignedAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes preguntas asignadas en el apartado activo.");
        }
        List<Answer> pendingAnswers = assignedAnswers.stream()
            .filter(answer -> answer.getSignedAt() == null)
            .toList();
        if (pendingAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Todas tus preguntas asignadas ya fueron firmadas.");
        }
        if (pendingAnswers.stream().anyMatch(answer ->
            !"SI".equals(answer.getAnswerValue()) && !"NO".equals(answer.getAnswerValue()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Responde todas tus preguntas antes de firmarlas.");
        }
        if (pendingAnswers.stream().anyMatch(answer -> !hasAllWrittenFields(answer))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Completa todos los campos escritos de tus preguntas antes de cargar la firma.");
        }
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Selecciona un PDF firmado de hasta 20 MB.");
        }

        try {
            byte[] content = file.getBytes();
            if (content.length < 5 || content[0] != '%' || content[1] != 'P'
                || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo no tiene una cabecera PDF válida.");
            }
            //String pdfStructure = new String(content, java.nio.charset.StandardCharsets.ISO_8859_1);
            //if (!pdfStructure.contains("/ByteRange") || !pdfStructure.contains("/Contents")) {
              //  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                //    "No se detectó una firma electrónica PDF. Firma el documento con FirmaEC antes de cargarlo.");
           // }
            String hash = sha256(content);
            Files.createDirectories(uploadDirectory);
            Path destination = uploadDirectory.resolve(
                requestId + "-apartado-" + sectionId + "-usuario-" + actor.getId() + "-" + hash + ".pdf").normalize();
            if (!destination.startsWith(uploadDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de archivo no válida.");
            }
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            for (Answer answer : pendingAnswers) {
                answer.markSigned(actor.getFullName(), actor.getEmail(), destination.toString(), hash);
            }
            auditRepository.save(new AuditEntry(requestId, "APARTADO_FIRMADO_CARGADO",
                actor.getUsername(), pendingAnswers.size() + " preguntas de " + section.getArea()
                    + "; SHA-256: " + hash));
            return request;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo guardar el PDF firmado del apartado.", exception);
        }
    }

    @Transactional(readOnly = true)
    public Path locateSection(Long requestId, Long sectionId, Long userId) {
        EmployeeRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la solicitud."));
        AreaSection section = request.getSections().stream()
            .filter(item -> item.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el apartado."));
        Answer signedAnswer = section.getAnswers().stream()
            .filter(answer -> {
                UserAccount assignee = answer.getAssignedUser() == null
                    ? section.getAssignedUser() : answer.getAssignedUser();
                return assignee != null && assignee.getId().equals(userId)
                    && answer.getSignedDocumentPath() != null;
            })
            .findFirst()
            .orElse(null);
        if (signedAnswer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró un PDF firmado para esa persona en el apartado.");
        }
        Path document = Path.of(signedAnswer.getSignedDocumentPath()).toAbsolutePath().normalize();
        if (!document.startsWith(uploadDirectory) || !Files.exists(document)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el archivo almacenado.");
        }
        try {
            if (!sha256(Files.readAllBytes(document)).equals(signedAnswer.getSignedDocumentSha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El archivo no coincide con el hash registrado.");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo verificar el archivo almacenado.", exception);
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Path locate(Long requestId) {
        EmployeeRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la solicitud."));
        if (request.getSignedDocumentPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "La solicitud todavía no tiene un PDF firmado.");
        }
        Path document = Path.of(request.getSignedDocumentPath()).toAbsolutePath().normalize();
        if (!document.startsWith(uploadDirectory) || !Files.exists(document)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el archivo almacenado.");
        }
        try {
            String actualHash = sha256(Files.readAllBytes(document));
            if (!actualHash.equals(request.getSignedDocumentSha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El archivo no coincide con el hash registrado y no se puede entregar.");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo verificar el archivo almacenado.", exception);
        }
        return document;
    }

    public void deleteStoredFiles(EmployeeRequest request) {
        List<String> storedPaths = new java.util.ArrayList<>();
        storedPaths.add(request.getSignedDocumentPath());
        request.getSections().forEach(section -> section.getAnswers().forEach(answer ->
            storedPaths.add(answer.getSignedDocumentPath())));
        storedPaths.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(path -> Path.of(path).toAbsolutePath().normalize())
            .filter(path -> path.startsWith(uploadDirectory))
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo eliminar uno de los PDF asociados a la solicitud.", exception);
                }
            });
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private boolean hasAllWrittenFields(Answer answer) {
        List<String> fields = answer.getWritingFields().isEmpty()
            ? QuestionCatalog.writingFieldsForKey(answer.getQuestionKey()) : answer.getWritingFields();
        if (fields.isEmpty()) return answer.getDetail() != null && !answer.getDetail().isBlank();
        if (answer.getDetail() == null || !answer.getDetail().startsWith("__FIELDS__:")) return false;
        try {
            var values = objectMapper.readValue(answer.getDetail().substring("__FIELDS__:".length()),
                new TypeReference<java.util.Map<String, String>>() { });
            return fields.stream().allMatch(field -> field.toLowerCase(java.util.Locale.ROOT).contains("valor a descontar")
                ? answer.getAmount() != null && answer.getAmount().signum() >= 0
                : values.get(field) != null && !values.get(field).isBlank());
        } catch (IOException exception) {
            return false;
        }
    }
}
