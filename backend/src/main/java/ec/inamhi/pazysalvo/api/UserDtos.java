package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    public record LoginCommand(
        @NotBlank String username,
        @NotBlank String password
    ) {
    }

    public record CreateUserCommand(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String username,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull UserRole role,
        Area area,
        @Size(max = 120) String workUnit,
        List<String> questionKeys
    ) {
    }

    public record UpdateQuestionsCommand(List<String> questionKeys) {
    }

    public record UpdateUserCommand(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String username,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email String email,
        Area area,
        @Size(max = 120) String workUnit
    ) {
    }

    public record SetUserEnabledCommand(boolean enabled) {
    }

    public record ResetPasswordView(String temporaryPassword) {
    }

    public record ResetPasswordCommand(@NotBlank @Size(max = 72) String newPassword) {
    }

    public record UserView(
        Long id,
        String username,
        String fullName,
        String email,
        UserRole role,
        Area area,
        String workUnit,
        boolean enabled,
        Instant createdAt,
        List<String> questionKeys
    ) {
    }

    public record QuestionOption(String key, String label,
                                 Long assignedUserId, String assignedUserName,
                                 List<String> writingFields) {
        public QuestionOption(String key, String label, Long assignedUserId, String assignedUserName) {
            this(key, label, assignedUserId, assignedUserName, List.of());
        }
    }

    public record AddQuestionCommand(@NotNull Area area, @NotBlank @Size(max = 1000) String label) { }
    public record CustomQuestionView(Long id, Area area, String label) { }

}
