package ec.inamhi.pazysalvo.config;

import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.domain.UserRole;
import ec.inamhi.pazysalvo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String fullName;
    private final String email;
    private final String password;

    public BootstrapAdmin(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap-admin.username}") String username,
                          @Value("${app.bootstrap-admin.full-name}") String fullName,
                          @Value("${app.bootstrap-admin.email}") String email,
                          @Value("${app.bootstrap-admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsernameIgnoreCase(username)) {
            userRepository.save(new UserAccount(username, fullName, email,
                passwordEncoder.encode(password), UserRole.TALENTO_HUMANO, null));
        }
    }
}
