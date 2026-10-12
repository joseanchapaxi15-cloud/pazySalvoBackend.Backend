package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.api.RequestDtos.AnswerUpdate;
import ec.inamhi.pazysalvo.api.RequestDtos.CreateRequest;
import ec.inamhi.pazysalvo.domain.Answer;
import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.AreaSection;
import ec.inamhi.pazysalvo.domain.AuditEntry;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.RequestStatus;
import ec.inamhi.pazysalvo.domain.SectionStatus;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.domain.UserRole;
import ec.inamhi.pazysalvo.repository.AuditRepository;
import ec.inamhi.pazysalvo.repository.RequestRepository;
import ec.inamhi.pazysalvo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class RequestService {

    private record NotificationRecipient(String username, String email) { }

    private final RequestRepository requestRepository;
    private final AuditRepository auditRepository;
    private final AuditService auditService;
    private final QuestionCatalog questionCatalog;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SignedDocumentService signedDocumentService;
    private final PasswordEncoder passwordEncoder;

    public RequestService(RequestRepository requestRepository,
                          AuditRepository auditRepository,
                          AuditService auditService,
                          QuestionCatalog questionCatalog,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          SignedDocumentService signedDocumentService,
                          PasswordEncoder passwordEncoder) {
        this.requestRepository = requestRepository;
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.questionCatalog = questionCatalog;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.signedDocumentService = signedDocumentService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public EmployeeRequest create(CreateRequest command, String actor) {
        String identification = command.identification().trim();
        if (command.departureDate().isBefore(command.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La fecha de salida no puede ser anterior a la fecha de ingreso.");
        }
        if (requestRepository.existsByIdentification(identification)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe una solicitud para esa identificación.");
        }
        String trackingUsername = command.trackingUsername().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(trackingUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El usuario de seguimiento ya está ocupado. Escribe otro usuario.");
        }

        EmployeeRequest request = new EmployeeRequest(
            command.fullName().trim(),
            identification,
            command.email().trim(),
            command.jobTitle() == null || command.jobTitle().isBlank() ? null : command.jobTitle().trim(),
            command.workUnit().trim(),
            command.employmentType(),
            command.startDate(),
            command.departureDate(),
            command.homeAddress(),
            command.homePhone(),
            command.province(),
            command.mobilePhone(),
            command.canton(),
            command.emergencyContact(),
            command.secondaryEmail(),
            command.workplace() == null || command.workplace().isBlank() ? null : command.workplace().trim(),
            command.occupationalGroup() == null || command.occupationalGroup().isBlank()
                ? null : command.occupationalGroup().trim()
        );

        List<UserAccount> directorsForUnit = userRepository
            .findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
            .filter(UserAccount::isEnabled)
            .filter(user -> sameUnit(user.getWorkUnit(), command.workUnit()))
            .toList();
        if (directorsForUnit.size() != 1
            || !directorsForUnit.getFirst().getId().equals(command.directionResponsibleId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Debe existir exactamente un director activo para la unidad seleccionada.");
        }
        UserAccount directionResponsible = directorsForUnit.getFirst();

        Map<String, UserAccount> questionOwners = new HashMap<>();
        List<String> unassignedQuestions = new java.util.ArrayList<>();
        for (Area area : questionCatalog.orderedAreas()) {
            List<UserAccount> areaUsers = new java.util.ArrayList<>(userRepository.findAllByRoleAndArea(
                UserRole.RESPONSABLE_AREA, area).stream()
                .filter(UserAccount::isEnabled)
                .filter(user -> area != Area.DIRECCION_UNIDAD)
                .toList());
            if (area != Area.DIRECCION_UNIDAD) {
                userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
                    .filter(UserAccount::isEnabled)
                    .filter(user -> Area.fromUnitLabel(user.getWorkUnit()) == area)
                    .forEach(areaUsers::add);
            }
            for (var option : questionCatalog.optionsForArea(area)) {
                UserAccount owner = area == Area.DIRECCION_UNIDAD
                    ? directionResponsible
                    : areaUsers.stream()
                    .filter(user -> user.getAssignedQuestionKeys().contains(option.key()))
                    .findFirst().orElse(null);
                if (owner == null) {
                    unassignedQuestions.add(area + ": " + option.label());
                } else {
                    questionOwners.put(option.key(), owner);
                }
            }
        }
        if (!unassignedQuestions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Configura todas las preguntas desde Administración de usuarios antes de crear solicitudes. "
                    + "Sin responsable: " + String.join("; ", unassignedQuestions));
        }

        int index = 0;
        for (Area area : questionCatalog.orderedAreas()) {
            AreaSection section = new AreaSection(area, index);
            for (var option : questionCatalog.optionsForArea(area)) {
                Answer answer = new Answer(option.key(), option.label());
                answer.setWritingFields(option.writingFields());
                answer.assignTo(questionOwners.get(option.key()));
                section.addAnswer(answer);
            }
            if (index == 0) {
                section.setStatus(SectionStatus.EN_CURSO);
            }
            request.addSection(section);
            index++;
        }

        EmployeeRequest saved = requestRepository.save(request);
        audit(saved, "SOLICITUD_CREADA", actor, "Solicitud registrada");
        saved.getSections().stream()
            .filter(section -> section.getStatus() == SectionStatus.EN_CURSO)
            .findFirst()
            .ifPresent(section -> notifyUnlockedSectionAfterCommit(saved, section));
        return saved;
    }

    @Transactional
    public EmployeeRequest updateWorkDetails(Long requestId, String workplace, String occupationalGroup,
                                             String workUnit, String jobTitle, UserAccount actor) {
        EmployeeRequest request = load(requestId);
        AreaSection section = request.getSections().stream()
            .filter(item -> item.getArea() == Area.DIRECCION_UNIDAD)
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (actor.getRole() != UserRole.RESPONSABLE_AREA || actor.getArea() != Area.DIRECCION_UNIDAD
            || section.getAnswers().stream().noneMatch(answer -> isAssignedTo(answer, section, actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el responsable asignado de unidad puede registrar estos datos.");
        }
        if (section.getStatus() != SectionStatus.EN_CURSO || section.getAnswers().stream().anyMatch(answer -> answer.getSignedAt() != null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los datos laborales ya no se pueden cambiar después de iniciar o completar la firma.");
        }
        if (!List.of("Planta central", "Procesos desconcentrados").contains(workplace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona Planta central o Procesos desconcentrados.");
        }
        Area assignedArea = Area.fromUnitLabel(actor.getWorkUnit());
        Area submittedArea = Area.fromUnitLabel(workUnit);
        boolean sameAssignedArea = assignedArea != null
            ? assignedArea == submittedArea
            : actor.getWorkUnit() != null && actor.getWorkUnit().trim().equalsIgnoreCase(workUnit.trim());
        if (!sameAssignedArea) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La Dirección / unidad debe corresponder al área asignada a tu cuenta de director.");
        }
        request.updateWorkDetails(workplace, occupationalGroup.trim(), workUnit.trim(), jobTitle.trim());
        audit(request, "DATOS_LABORALES_GUARDADOS", actor.getUsername(),
            "Actualizó lugar de trabajo, grupo ocupacional, dirección/unidad y cargo desempeñado.");
        return request;
    }

    @Transactional
    public void createTrackingAccount(EmployeeRequest request, String username, String password) {
        String normalizedUsername = username.trim().toLowerCase();
        UserAccount account = new UserAccount(normalizedUsername, request.getFullName(),
            normalizedUsername + "@tracking.local",
            passwordEncoder.encode(password), UserRole.SOLICITANTE, null);
        account.setTrackedRequest(request);
        userRepository.save(account);
        Long requestId = request.getId();
        String employeeName = request.getFullName();
        String email = request.getEmail();
        afterCommit(() -> notificationService.notifyApplicantCredentials(
            requestId, employeeName, email, normalizedUsername, password));
    }

    @Transactional(readOnly = true)
    public List<EmployeeRequest> findAll() {
        return requestRepository.findAll();
    }

    @Transactional
    public void deleteRequest(Long requestId, String actor) {
        EmployeeRequest request = load(requestId);
        audit(request, "SOLICITUD_ELIMINADA", actor,
            "Se eliminó la solicitud de " + request.getFullName());
        signedDocumentService.deleteStoredFiles(request);
        userRepository.findByTrackedRequestId(requestId).ifPresent(userRepository::delete);
        requestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public List<EmployeeRequest> findVisibleTo(UserAccount actor) {
        if (actor.getRole() == UserRole.SOLICITANTE) {
            return requestRepository.findTrackedRequestByUserId(actor.getId())
                .map(List::of)
                .orElseGet(List::of);
        }
        List<EmployeeRequest> requests = requestRepository.findAll();
        if (actor.getRole() == UserRole.TALENTO_HUMANO) {
            return requests;
        }
        return requests.stream()
            .filter(request -> request.getSections().stream().anyMatch(section ->
                section.getStatus() == SectionStatus.EN_CURSO
                    && section.getAnswers().stream().anyMatch(answer -> isAssignedTo(answer, section, actor))))
            .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeRequest findById(Long id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public EmployeeRequest findVisibleById(Long id, UserAccount actor) {
        EmployeeRequest request = load(id);
        if (actor.getRole() == UserRole.SOLICITANTE) {
            if (requestRepository.findTrackingUserLink(actor.getId(), id).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró tu solicitud.");
            }
            return request;
        }
        if (actor.getRole() == UserRole.TALENTO_HUMANO) {
            return request;
        }
        boolean assigned = request.getSections().stream().anyMatch(section ->
            section.getStatus() == SectionStatus.EN_CURSO
                && section.getAnswers().stream().anyMatch(answer -> isAssignedTo(answer, section, actor)));
        if (!assigned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró una solicitud asignada a tu cuenta.");
        }
        return request;
    }

    @Transactional
    public EmployeeRequest saveAnswers(Long requestId, Long sectionId,
                                       List<AnswerUpdate> updates, UserAccount actor) {
        EmployeeRequest request = load(requestId);
        AreaSection section = loadAssignedActiveSection(request, sectionId, actor);
        if (section.getArea() == Area.DIRECCION_UNIDAD
            && (request.getWorkplace() == null || request.getOccupationalGroup() == null
                || request.getWorkUnit() == null || request.getJobTitle() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Completa y guarda los datos laborales antes de responder y firmar el apartado.");
        }

        List<String> changedQuestions = new java.util.ArrayList<>();
        for (AnswerUpdate update : updates) {
            if (update.answerValue() != null && !update.answerValue().isBlank()
                && !"SI".equals(update.answerValue()) && !"NO".equals(update.answerValue())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada pregunta debe responderse Sí o No.");
            }
            Answer answer = section.getAnswers().stream()
                .filter(item -> item.getQuestionKey().equals(update.questionKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La pregunta no pertenece a esta sección."));
            if (!isAssignedTo(answer, section, actor)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes asignada esta pregunta.");
            }
            if (answer.getSignedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta pregunta ya fue firmada y no se puede editar.");
            }
            answer.update(update.answerValue(), update.detail(), update.amount());
            changedQuestions.add(answer.getQuestionKey());
        }

        audit(request, "RESPUESTAS_GUARDADAS", actor.getUsername(),
            "Se actualizaron respuestas de " + section.getArea() + "; preguntas: "
                + String.join(", ", changedQuestions));
        return request;
    }

    @Transactional
    public EmployeeRequest signSection(Long requestId, Long sectionId,
                                       UserAccount actor) {
        EmployeeRequest request = load(requestId);
        AreaSection section = loadAssignedActiveSection(request, sectionId, actor);

        List<Answer> actorAnswers = section.getAnswers().stream()
            .filter(answer -> isAssignedTo(answer, section, actor))
            .toList();
        if (actorAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No tienes preguntas asignadas en este apartado.");
        }
        boolean unanswered = actorAnswers.stream().anyMatch(answer ->
            !"SI".equals(answer.getAnswerValue()) && !"NO".equals(answer.getAnswerValue()));
        if (unanswered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Responde todas tus preguntas antes de firmarlas.");
        }
        if (actorAnswers.stream().anyMatch(answer -> answer.getSignedDocumentPath() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Firma tus preguntas con tu certificado P12 desde el botón de firma del sistema.");
        }
        if (section.getAnswers().stream().anyMatch(answer -> answer.getSignedAt() == null)) {
            audit(request, "PREGUNTAS_FIRMADAS", actor.getUsername(),
                actorAnswers.size() + " preguntas firmadas en " + section.getArea());
            return request;
        }

        section.complete();
        AreaSection next = request.getSections().stream()
            .filter(candidate -> candidate.getSequenceNumber() > section.getSequenceNumber())
            .min(java.util.Comparator.comparingInt(AreaSection::getSequenceNumber))
            .orElse(null);

        if (next == null) {
            request.setStatus(RequestStatus.PENDIENTE_FIRMA_EXFUNCIONARIO);
            audit(request, "AREAS_COMPLETADAS", actor.getUsername(),
                "Todas las áreas completaron su apartado");
        } else {
            next.setStatus(SectionStatus.EN_CURSO);
            audit(request, "AREA_CONFIRMADA", actor.getUsername(),
                section.getArea() + " completada; se habilitó " + next.getArea());
            notifyUnlockedSectionAfterCommit(request, next);
        }
        if (next == null) {
            String applicantEmail = request.getEmail();
            String employeeName = request.getFullName();
            afterCommit(() -> notificationService.notifyFinalSigner(
                requestId, employeeName, applicantEmail));
        }
        return request;
    }

    private void notifyUnlockedSectionAfterCommit(EmployeeRequest request, AreaSection section) {
        if (section.getStatus() != SectionStatus.EN_CURSO) {
            return;
        }
        Map<Long, NotificationRecipient> recipients = new java.util.LinkedHashMap<>();
        section.getAnswers().stream()
            .map(answer -> answer.getAssignedUser() == null ? section.getAssignedUser() : answer.getAssignedUser())
            .filter(java.util.Objects::nonNull)
            .filter(UserAccount::isEnabled)
            .forEach(user -> recipients.putIfAbsent(user.getId(), new NotificationRecipient(
                user.getUsername(), user.getEmail())));
        String employeeName = request.getFullName();
        Long requestId = request.getId();
        Area area = section.getArea();
        afterCommit(() -> recipients.values().forEach(recipient -> notificationService.notifyNextSection(
            requestId, employeeName, area, recipient.username(), recipient.email())));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Transactional
    public EmployeeRequest setFinalStatus(Long requestId, RequestStatus status,
                                          String observation, String actor) {
        EmployeeRequest request = load(requestId);
        if (request.getStatus() != RequestStatus.EN_REVISION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El documento firmado debe cargarse antes de revisar el estado final.");
        }
        if (status != RequestStatus.APROBADO && status != RequestStatus.NEGADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El estado final debe ser Aprobado o Negado.");
        }
        if (status == RequestStatus.NEGADO && (observation == null || observation.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Indica el motivo cuando el trámite sea negado.");
        }
        request.setStatus(status);
        audit(request, status.name(), actor, observation);
        return request;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(Long requestId) {
        load(requestId);
        return auditRepository.findByRequestIdOrderByOccurredAtDesc(requestId);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> auditHistory() {
        return auditService.findAll();
    }

    private AreaSection loadAssignedActiveSection(EmployeeRequest request, Long sectionId,
                                                 UserAccount actor) {
        AreaSection section = request.getSections().stream()
            .filter(candidate -> candidate.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No existe esa sección en la solicitud."));

        if (section.getStatus() != SectionStatus.EN_CURSO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se puede editar el área que está activa.");
        }
        boolean hasAssignedQuestions = section.getAnswers().stream()
            .anyMatch(answer -> isAssignedTo(answer, section, actor));
        if (actor.getRole() != UserRole.RESPONSABLE_AREA || !hasAssignedQuestions) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes preguntas pendientes asignadas en este apartado.");
        }
        return section;
    }

    private AreaSection findSection(EmployeeRequest request, Long sectionId) {
        return request.getSections().stream()
            .filter(section -> section.getId().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No existe esa sección en la solicitud."));
    }

    private boolean isAssignedTo(Answer answer, AreaSection section, UserAccount actor) {
        UserAccount assigned = answer.getAssignedUser() == null
            ? section.getAssignedUser() : answer.getAssignedUser();
        return assigned != null && assigned.getId().equals(actor.getId());
    }

    private boolean sameUnit(String left, String right) {
        return left != null && right != null && normalizeUnit(left).equals(normalizeUnit(right));
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : java.text.Normalizer.normalize(unit.trim(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private EmployeeRequest load(Long id) {
        return requestRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la solicitud."));
    }

    private void audit(EmployeeRequest request, String action, String actor, String details) {
        auditService.record(request.getId(), action, actor, details);
    }
}
