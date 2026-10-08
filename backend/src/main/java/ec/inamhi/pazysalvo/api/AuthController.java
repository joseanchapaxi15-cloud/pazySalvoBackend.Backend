package ec.inamhi.pazysalvo.api;

import ec.inamhi.pazysalvo.api.UserDtos.LoginCommand;
import ec.inamhi.pazysalvo.api.UserDtos.UserView;
import ec.inamhi.pazysalvo.domain.UserAccount;
import ec.inamhi.pazysalvo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = {"http://localhost:4200"})
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginCommand command) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(command.username(), command.password()));
            return UserService.toView((UserAccount) authentication.getPrincipal());
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Usuario o contraseña incorrectos.");
        }
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserAccount user) {
        return UserService.toView(userService.findByUsername(user.getUsername()));
    }
}
