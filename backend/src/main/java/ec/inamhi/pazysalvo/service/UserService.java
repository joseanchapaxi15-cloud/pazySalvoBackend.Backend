package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.api.UserDtos.CreateUserCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UpdateUserCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UserView;
import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.Answer;
import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.domain.UserRole;
import ec.inamhi.pazysalvo.repository.RequestRepository;
import ec.inamhi.pazysalvo.repository.DirectoryEmployeeRepository;
import ec.inamhi.pazysalvo.repository.UserRepository;
import ec.inamhi.pazysalvo.api.UserDtos.QuestionOption;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.security.SecureRandom;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final DirectoryEmployeeRepository directoryEmployeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuestionCatalog questionCatalog;

    public UserService(UserRepository userRepository, RequestRepository requestRepository,
                       DirectoryEmployeeRepository directoryEmployeeRepository, PasswordEncoder passwordEncoder,
                       QuestionCatalog questionCatalog) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.directoryEmployeeRepository = directoryEmployeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.questionCatalog = questionCatalog;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario o clave incorrectos."));
    }

    @Transactional(readOnly = true)
    public UserAccount findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la cuenta."));
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        return userRepository.findAllByOrderByFullNameAsc().stream()
            .map(UserService::toView)
            .toList();
    }

    @Transactional
    public UserView create(CreateUserCommand command) {
        String username = command.username().trim().toLowerCase();
        String email = command.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ese nombre de usuario ya existe.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ese correo ya está asociado a otra cuenta.");
        }
        validateArea(command.role(), command.area(), command.workUnit());
        if (command.role() == UserRole.RESPONSABLE_AREA && command.area() == Area.DIRECCION_UNIDAD) {
            ensureUnitHasNoOtherActiveDirector(command.workUnit(), null);
        }
        Set<String> questionKeys = new HashSet<>(
            command.questionKeys() == null ? List.of() : command.questionKeys());
        validateQuestionAssignments(command.role(), command.area(), command.workUnit(), questionKeys, null,
            command.area() != Area.DIRECCION_UNIDAD);

        UserAccount user = new UserAccount(
            username,
            command.fullName().trim(),
            email,
            passwordEncoder.encode(command.password()),
            command.role(),
            command.role() == UserRole.RESPONSABLE_AREA ? command.area() : null
        );
        user.setWorkUnit(command.area() == Area.DIRECCION_UNIDAD
            ? command.workUnit().trim() : null);
        user.setAssignedQuestionKeys(storedQuestionKeys(user, questionKeys));
        UserAccount saved = userRepository.save(user);
        if (command.role() == UserRole.RESPONSABLE_AREA && command.area() != Area.DIRECCION_UNIDAD) {
            transferQuestionsFromAreaDirector(command.area(), saved, questionKeys);
        }
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<QuestionOption> questionsForArea(Area area, String workUnit) {
        java.util.Map<String, UserAccount> owners = new java.util.HashMap<>();
        List<UserAccount> eligibleUsers = new java.util.ArrayList<>(userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, area));
        if (area != Area.DIRECCION_UNIDAD) {
            userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
                .filter(user -> Area.fromUnitLabel(user.getWorkUnit()) == area)
                .forEach(eligibleUsers::add);
        }
        eligibleUsers.stream()
            .filter(UserAccount::isEnabled)
            .filter(user -> area != Area.DIRECCION_UNIDAD || workUnit == null || sameUnit(user.getWorkUnit(), workUnit))
            .forEach(user -> questionCatalog.optionsForArea(area).forEach(option -> {
                if (hasQuestion(user, option.key())) owners.put(option.key(), user);
            }));
        return questionCatalog.optionsForArea(area).stream()
            .map(option -> {
                UserAccount owner = owners.get(option.key());
                return new QuestionOption(option.key(), option.label(),
                    owner == null ? null : owner.getId(), owner == null ? null : owner.getFullName(),
                    option.writingFields());
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<String> directionUnits() {
        List<String> units = directoryEmployeeRepository.findAll().stream()
            .map(employee -> employee.getWorkUnit())
            .filter(unit -> unit != null && !unit.isBlank())
            .map(String::trim)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        if (!units.isEmpty()) return units;
        return userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
            .filter(UserAccount::isEnabled)
            .map(UserAccount::getWorkUnit)
            .filter(unit -> unit != null && !unit.isBlank())
            .map(String::trim)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    @Transactional
    public UserView updateQuestionAssignments(Long userId, List<String> requestedKeys) {
        UserAccount user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró la cuenta."));
        if (user.getRole() != UserRole.RESPONSABLE_AREA || user.getArea() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Solo las cuentas responsables de área tienen preguntas asignadas.");
        }
        Set<String> questionKeys = new HashSet<>(requestedKeys == null ? List.of() : requestedKeys);
        if (user.getArea() == Area.DIRECCION_UNIDAD) {
            // Dirección / unidad is automatic for directors; discard old manually assigned keys.
            questionKeys.removeIf(key -> key == null || baseQuestionKey(user, key).startsWith("direccion_unidad_"));
            Area operationalArea = Area.fromUnitLabel(user.getWorkUnit());
            if (operationalArea != null) {
                Set<String> keysOwnedByAreaResponsibles = userRepository
                    .findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, operationalArea).stream()
                    .filter(UserAccount::isEnabled)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .flatMap(other -> other.getAssignedQuestionKeys().stream())
                    .map(key -> baseQuestionKey(user, key))
                    .collect(java.util.stream.Collectors.toSet());
                questionKeys.removeAll(keysOwnedByAreaResponsibles);
            }
        }
        boolean allowDirectorTransfer = user.getArea() != Area.DIRECCION_UNIDAD;
        validateQuestionAssignments(user.getRole(), user.getArea(), user.getWorkUnit(), questionKeys,
            user.getId(), allowDirectorTransfer);
        user.setAssignedQuestionKeys(storedQuestionKeys(user, questionKeys));
        UserAccount saved = userRepository.save(user);
        if (allowDirectorTransfer) {
            transferQuestionsFromAreaDirector(user.getArea(), saved, questionKeys);
        }
        reassignUnsignedAnswers(user.getArea(), saved, questionKeys);
        return toView(saved);
    }

    @Transactional
    public UserView updateProfile(Long userId, UpdateUserCommand command) {
        UserAccount user = getUser(userId);
        String username = command.username().trim().toLowerCase();
        String email = command.email().trim().toLowerCase();
        userRepository.findByUsernameIgnoreCase(username).filter(other -> !other.getId().equals(userId))
            .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya existe."); });
        userRepository.findAllByOrderByFullNameAsc().stream()
            .filter(other -> !other.getId().equals(userId) && other.getEmail().equalsIgnoreCase(email))
            .findAny().ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está asociado a otra cuenta."); });
        if (user.getRole() != UserRole.RESPONSABLE_AREA) {
            user.updateProfile(username, command.fullName().trim(), email);
            return toView(userRepository.save(user));
        }
        Area nextArea = command.area();
        if (nextArea == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona Director de unidad o un área responsable.");
        }
        boolean isDirector = nextArea == Area.DIRECCION_UNIDAD;
        boolean areaChanged = user.getArea() != nextArea;
        if (isDirector && (command.workUnit() == null || command.workUnit().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la unidad que dirige esta cuenta.");
        }
        if (isDirector) ensureUnitHasNoOtherActiveDirector(command.workUnit(), userId);
        if (areaChanged) {
            user.setArea(nextArea);
            user.setAssignedQuestionKeys(Set.of());
        }
        if (isDirector) {
            String nextWorkUnit = command.workUnit().trim();
            boolean workUnitChanged = !sameUnit(user.getWorkUnit(), nextWorkUnit);
            if (workUnitChanged) {
                user.setAssignedQuestionKeys(Set.of());
            }
            Set<String> assignedKeys = user.getAssignedQuestionKeys().stream()
                .map(key -> baseQuestionKey(user, key))
                .filter(key -> !isDirectionUnitQuestion(key))
                .collect(java.util.stream.Collectors.toSet());
            // A profile-only edit (name, username, or email) must not be blocked by
            // legacy question assignments. Revalidate only when their area scope changes.
            if (areaChanged || workUnitChanged) {
                validateQuestionAssignments(user.getRole(), user.getArea(), nextWorkUnit, assignedKeys, userId, false);
            }
            user.setWorkUnit(nextWorkUnit);
            user.setAssignedQuestionKeys(storedQuestionKeys(user, assignedKeys));
        } else {
            user.setWorkUnit(null);
        }
        user.updateProfile(username, command.fullName().trim(), email);
        return toView(userRepository.save(user));
    }

    @Transactional
    public UserView setEnabled(Long userId, boolean enabled) {
        UserAccount user = getUser(userId);
        if (enabled && user.getRole() == UserRole.RESPONSABLE_AREA && user.getArea() == Area.DIRECCION_UNIDAD) {
            ensureUnitHasNoOtherActiveDirector(user.getWorkUnit(), userId);
        }
        user.setEnabled(enabled);
        if (!enabled) {
            user.setAssignedQuestionKeys(Set.of());
        }
        return toView(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long userId) {
        UserAccount user = getUser(userId);
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede eliminar esta cuenta porque está vinculada a solicitudes o firmas. Desactívala para conservar el historial.");
        }
    }

    @Transactional
    public String resetPassword(Long userId, String newPassword) {
        UserAccount user = getUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return newPassword;
    }

    @Transactional
    public String resetPasswordToIdentification(Long userId) {
        UserAccount user = getUser(userId);
        if (user.getRole() != UserRole.SOLICITANTE || user.getTrackedRequest() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Solo una cuenta de seguimiento puede restablecerse con la cédula del solicitante.");
        }
        String identification = user.getTrackedRequest().getIdentification();
        user.setPasswordHash(passwordEncoder.encode(identification));
        userRepository.save(user);
        return identification;
    }

    private UserAccount getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la cuenta."));
    }

    private void ensureUnitHasNoOtherActiveDirector(String workUnit, Long excludedUserId) {
        boolean occupied = userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
            .filter(UserAccount::isEnabled)
            .filter(user -> excludedUserId == null || !user.getId().equals(excludedUserId))
            .anyMatch(user -> sameUnit(user.getWorkUnit(), workUnit));
        if (occupied) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un director activo para esta dirección/unidad. Edita esa cuenta o desactívala primero.");
        }
    }

    public static UserView toView(UserAccount user) {
        return new UserView(user.getId(), user.getUsername(), user.getFullName(),
            user.getEmail(), user.getRole(), user.getArea(), user.getWorkUnit(), user.isEnabled(), user.getCreatedAt(),
            user.getAssignedQuestionKeys().stream().map(key -> baseQuestionKey(user, key)).sorted().distinct().toList());
    }

    private void validateArea(UserRole role, Area area, String workUnit) {
        if (role == UserRole.RESPONSABLE_AREA && area == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Asigna un área a la cuenta responsable.");
        }
        if (role == UserRole.TALENTO_HUMANO && area != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Las cuentas de Talento Humano no pertenecen a un área operativa.");
        }
        if (role == UserRole.RESPONSABLE_AREA && area == Area.DIRECCION_UNIDAD
            && (workUnit == null || workUnit.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Indica la unidad de trabajo que dirige esta cuenta.");
        }
    }

    private void validateQuestionAssignments(UserRole role, Area area, String workUnit, Set<String> questionKeys,
                                             Long excludedUserId, boolean allowDirectorTransfer) {
        if (role == UserRole.TALENTO_HUMANO) {
            if (!questionKeys.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las cuentas de Talento Humano no llevan preguntas de área asignadas.");
            }
            return;
        }
        if (questionKeys.isEmpty() && area != Area.DIRECCION_UNIDAD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Asigna al menos una pregunta a la cuenta responsable.");
        }

        Area assignableArea = area == Area.DIRECCION_UNIDAD ? Area.fromUnitLabel(workUnit) : area;
        if (assignableArea == null) {
            if (!questionKeys.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La unidad del director debe corresponder a un área operativa para asignarle preguntas adicionales.");
            return;
        }
        Set<String> validKeys = questionCatalog.optionsForArea(assignableArea).stream()
            .map(QuestionOption::key)
            .collect(java.util.stream.Collectors.toSet());
        if (!validKeys.containsAll(questionKeys)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Hay preguntas que no pertenecen al área seleccionada.");
        }

        List<UserAccount> eligibleUsers = new java.util.ArrayList<>(userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, assignableArea));
        if (area == Area.DIRECCION_UNIDAD) {
            userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
                .filter(user -> Area.fromUnitLabel(user.getWorkUnit()) == assignableArea)
                .forEach(eligibleUsers::add);
        }
        boolean alreadyAssigned = eligibleUsers.stream()
            .filter(UserAccount::isEnabled)
            .filter(user -> !user.getId().equals(excludedUserId))
            .filter(user -> !(allowDirectorTransfer && isDirectorForArea(user, assignableArea)))
            .anyMatch(user -> questionKeys.stream().anyMatch(key -> hasQuestion(user, key)));
        if (alreadyAssigned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Una o más preguntas ya están asignadas a otro usuario de esta área.");
        }
    }

    private void transferQuestionsFromAreaDirector(Area area, UserAccount newOwner, Set<String> questionKeys) {
        if (questionKeys.isEmpty()) return;
        userRepository.findAllByRoleAndArea(UserRole.RESPONSABLE_AREA, Area.DIRECCION_UNIDAD).stream()
            .filter(UserAccount::isEnabled)
            .filter(director -> isDirectorForArea(director, area))
            .forEach(director -> {
                Set<String> remainingKeys = new HashSet<>(director.getAssignedQuestionKeys());
                questionKeys.forEach(key -> {
                    remainingKeys.remove(key);
                    remainingKeys.remove(key + "::" + normalizeUnit(director.getWorkUnit()));
                });
                director.setAssignedQuestionKeys(remainingKeys);
                userRepository.save(director);
            });
        reassignUnsignedAnswers(area, newOwner, questionKeys);
    }

    /** Apply administrative reassignment to unanswered/un-signed work already in progress.
     * Signed answers remain attached to their original signer as immutable evidence.
     */
    private void reassignUnsignedAnswers(Area area, UserAccount newOwner, Set<String> questionKeys) {
        if (area == null || newOwner == null || questionKeys.isEmpty()) return;
        requestRepository.findAll().stream()
            .flatMap(request -> request.getSections().stream())
            .filter(section -> section.getArea() == area)
            .flatMap(section -> section.getAnswers().stream())
            .filter(answer -> questionKeys.contains(answer.getQuestionKey()))
            .filter(answer -> answer.getSignedAt() == null)
            .forEach(answer -> answer.assignTo(newOwner));
    }

    private boolean isDirectorForArea(UserAccount user, Area area) {
        return user.getRole() == UserRole.RESPONSABLE_AREA
            && user.getArea() == Area.DIRECCION_UNIDAD
            && Area.fromUnitLabel(user.getWorkUnit()) == area;
    }

    private Set<String> storedQuestionKeys(UserAccount user, Set<String> questionKeys) {
        if (user.getArea() != Area.DIRECCION_UNIDAD) return questionKeys;
        String scope = normalizeUnit(user.getWorkUnit());
        return questionKeys.stream().map(key -> key.startsWith("direccion_unidad_") ? key + "::" + scope : key)
            .collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasQuestion(UserAccount user, String key) {
        return user.getAssignedQuestionKeys().contains(key)
            || user.getAssignedQuestionKeys().contains(key + "::" + normalizeUnit(user.getWorkUnit()));
    }

    private static String baseQuestionKey(UserAccount user, String key) {
        if (user.getArea() != Area.DIRECCION_UNIDAD) return key;
        int scopeIndex = key.indexOf("::");
        return scopeIndex < 0 ? key : key.substring(0, scopeIndex);
    }

    private boolean isDirectionUnitQuestion(String key) {
        return key != null && key.startsWith("direccion_unidad_");
    }

    private boolean sameUnit(String left, String right) {
        return left != null && right != null && normalizeUnit(left).equals(normalizeUnit(right));
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : java.text.Normalizer.normalize(unit.trim(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT)
            .replace("adminsitrativa", "administrativa");
    }
}
