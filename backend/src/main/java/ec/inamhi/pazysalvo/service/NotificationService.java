package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.domain.Area;
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

    public void notifyNextSection(Long requestId, String employeeName, Area area,
                                  String username, String email) {
        if (email == null || email.isBlank()) {
            log.info("La persona asignada no tiene correo: solicitud {}, usuario {}", requestId, username);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("Paz y salvo disponible en tu bandeja - solicitud " + requestId);
            message.setText("El trámite de paz y salvo de " + employeeName + " ya está disponible en tu bandeja.\n\n"
                + "Área: " + areaLabel(area) + "\n"
                + "Ingresa al sistema para revisar las preguntas que tienes asignadas y firmar el apartado con tu certificado P12.");
            mailSender.send(message);
            log.info("Aviso enviado a {} para solicitud {}, área {}", username, requestId, area);
        } catch (RuntimeException exception) {
            log.error("No se pudo avisar a {} sobre la solicitud {}, área {}", username, requestId, area, exception);
        }
    }

    public void notifyApplicantCredentials(Long requestId, String employeeName, String email,
                                            String username, String temporaryPassword) {
        if (email == null || email.isBlank()) {
            log.info("El solicitante no tiene correo para enviar sus credenciales: solicitud {}", requestId);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("Credenciales de seguimiento de tu paz y salvo - solicitud " + requestId);
            message.setText("Hola " + employeeName + ",\n\n"
                + "Tu solicitud de paz y salvo fue registrada. Puedes consultar su avance en el sistema con estas credenciales:\n\n"
                + "Usuario: " + username + "\n"
                + "Contraseña temporal: " + temporaryPassword + "\n\n"
                + "Solicitud: " + requestId + "\n"
                + "Conserva este mensaje en un lugar seguro. Talento Humano también puede entregarte estas credenciales directamente.");
            mailSender.send(message);
            log.info("Credenciales de seguimiento enviadas al solicitante de la solicitud {}", requestId);
        } catch (RuntimeException exception) {
            log.error("No se pudieron enviar las credenciales al solicitante de la solicitud {}", requestId, exception);
        }
    }

    public void notifyFinalSigner(Long requestId, String employeeName, String email) {
        if (email == null || email.isBlank()) {
            log.info("El solicitante no tiene correo registrado: solicitud {}", requestId);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("Tu paz y salvo está listo para la firma final - solicitud " + requestId);
            message.setText("Todas las áreas completaron y firmaron el paz y salvo de " + employeeName + ".\n\n"
                + "Ingresa al sistema con tu cuenta de seguimiento para revisar y firmar el documento consolidado con tu certificado P12.");
            mailSender.send(message);
            log.info("Aviso de firma final enviado a solicitud {}", requestId);
        } catch (RuntimeException exception) {
            log.error("No se pudo avisar al solicitante de la solicitud {}", requestId, exception);
        }
    }

    private String areaLabel(Area area) {
        return switch (area) {
            case DIRECCION_UNIDAD -> "Dirección / unidad";
            case GESTION_ADMINISTRATIVA -> "Gestión administrativa";
            case TIC -> "Tecnologías de la información";
            case GESTION_FINANCIERA -> "Gestión financiera";
            case SEGURIDAD_INFORMACION -> "Seguridad de la información";
            case RECURSOS_HUMANOS -> "Recursos humanos";
            case RECEPCION_DOCUMENTOS -> "Recepción de documentos";
        };
    }
}
