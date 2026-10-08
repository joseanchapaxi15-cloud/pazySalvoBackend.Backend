package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.domain.EmployeeRequest;
import ec.inamhi.pazysalvo.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public NotificationService(JavaMailSender mailSender,
                               @Value("${app.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void notifyUser(EmployeeRequest request, UserAccount user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("La persona asignada no tiene correo: solicitud {}, usuario {}",
                request.getId(), user.getUsername());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("Tienes una tarea de paz y salvo - solicitud " + request.getId());
            message.setText("Se te asignó el apartado de paz y salvo de " + request.getFullName()
                + ". Ingresa al sistema para revisar y completar las preguntas asignadas.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.error("No se pudo avisar a {} sobre la solicitud {}",
                user.getUsername(), request.getId(), exception);
        }
    }
}
