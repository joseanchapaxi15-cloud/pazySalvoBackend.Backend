package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.UserDtos.LoginCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UserView;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginCommand command,
                          HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(command.username(), command.password()));
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return UserService.toView((UserAccount) authentication.getPrincipal());
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Usuario o contraseña incorrectos.");
        }
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // Force creation of the readable CSRF cookie for Angular's XSRF header.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserAccount user) {
        return UserService.toView(userService.findByUsername(user.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        return ResponseEntity.noContent().build();
    }
}
