package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.UserDtos.CreateUserCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UserView;
import ec.inamhi.pazysalvo.api.UserDtos.QuestionOption;
import ec.inamhi.pazysalvo.api.UserDtos.UpdateQuestionsCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UpdateUserCommand;
import ec.inamhi.pazysalvo.api.UserDtos.SetUserEnabledCommand;
import ec.inamhi.pazysalvo.api.UserDtos.ResetPasswordView;
import ec.inamhi.pazysalvo.api.UserDtos.ResetPasswordCommand;
import ec.inamhi.pazysalvo.api.UserDtos.AddQuestionCommand;
import ec.inamhi.pazysalvo.api.UserDtos.CustomQuestionView;
import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.service.UserService;
import ec.inamhi.pazysalvo.service.QuestionCatalog;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ec.inamhi.pazysalvo.domain.UserAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.List;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('TALENTO_HUMANO')")
public class UserController {

    private final UserService userService;
    private final QuestionCatalog questionCatalog;

    public UserController(UserService userService, QuestionCatalog questionCatalog) {
        this.userService = userService;
        this.questionCatalog = questionCatalog;
    }

    @GetMapping
    public List<UserView> listUsers() {
        return userService.listUsers();
    }

    @GetMapping("/questions")
    public List<QuestionOption> questionsForArea(@RequestParam Area area,
                                                 @RequestParam(required = false) String workUnit) {
        return userService.questionsForArea(area, workUnit);
    }

    @GetMapping("/units")
    public List<String> directionUnits() {
        return userService.directionUnits();
    }

    @PostMapping("/questions")
    public ResponseEntity<CustomQuestionView> addQuestion(@Valid @RequestBody AddQuestionCommand command,
                                                          @AuthenticationPrincipal UserAccount actor) {
        var question = questionCatalog.addQuestion(command.area(), command.label(), actor.getUsername());
        return ResponseEntity.status(201).body(new CustomQuestionView(question.getId(), question.getArea(), question.getLabel()));
    }

    @PostMapping
    public ResponseEntity<UserView> createUser(@Valid @RequestBody CreateUserCommand command) {
        return ResponseEntity.status(201).body(userService.create(command));
    }

    @PutMapping("/{userId}/questions")
    public UserView updateQuestions(@PathVariable Long userId,
                                    @RequestBody UpdateQuestionsCommand command) {
        return userService.updateQuestionAssignments(userId, command.questionKeys());
    }

    @PatchMapping("/{userId}")
    public UserView updateUser(@PathVariable Long userId,
                               @Valid @RequestBody UpdateUserCommand command) {
        return userService.updateProfile(userId, command);
    }

    @PatchMapping("/{userId}/status")
    public UserView setEnabled(@PathVariable Long userId,
                               @RequestBody SetUserEnabledCommand command) {
        return userService.setEnabled(userId, command.enabled());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reset-password")
    public ResetPasswordView resetPassword(@PathVariable Long userId,
                                           @Valid @RequestBody ResetPasswordCommand command) {
        return new ResetPasswordView(userService.resetPassword(userId, command.newPassword()));
    }

    @PostMapping("/{userId}/reset-password/identification")
    public ResetPasswordView resetPasswordToIdentification(@PathVariable Long userId) {
        return new ResetPasswordView(userService.resetPasswordToIdentification(userId));
    }
}
