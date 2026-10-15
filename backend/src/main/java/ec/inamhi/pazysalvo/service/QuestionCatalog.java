package ec.inamhi.pazysalvo.service;

import ec.inamhi.pazysalvo.domain.Area;
import ec.inamhi.pazysalvo.domain.CustomQuestion;
import ec.inamhi.pazysalvo.repository.CustomQuestionRepository;
import ec.inamhi.pazysalvo.api.UserDtos.QuestionOption;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QuestionCatalog {

    private final CustomQuestionRepository customQuestions;

    public QuestionCatalog(CustomQuestionRepository customQuestions) {
        this.customQuestions = customQuestions;
    }

    private final Map<Area, List<String>> questions = Map.of(

            Area.DIRECCION_UNIDAD, List.of(
                    "Entregó el informe de fin de gestión con detalles de actividades ejecutadas y pendientes",
                    "Fe de presentación de la entrega del informe de fin de gestión en recepción de documentos",
                    "Entregó el archivo físico y digital de la documentación",
                    "Es administrador de contrato",
                    "Entregó los trámites asignados en el sistema documental",
                    "Tiene claves de acceso asignadas en su unidad",
                    "Realizó entrega de claves de su unidad"
            ),

            Area.GESTION_ADMINISTRATIVA, List.of(
                    "¿Es administrador de contrato?",
                    "¿Entregó bienes muebles y equipos?",
                    "¿Tiene valores pendientes por deducibles?",
                    "¿Tiene pasajes aéreos por justificar?"
            ),

            Area.TIC, List.of(
                    "Se realizó la verificación del equipo informático",
                    "¿Tiene acceso a IP, Wi-Fi, móvil u otros servicios?",
                    "Se realizó la liberación",
                    "Se retiró el control de acceso a sistemas de información",
                    "Se realizó el cierre de correo institucional",
                    "Se realizó el cierre de quipux",
                    "Se realizó el cierre de eSIGEFSPRYNeSBye",
                    "Se realizó la entrega y desactivación de la tarjeta de acceso",
                    "Entregó el backup de información generado"
            ),

            Area.GESTION_FINANCIERA, List.of(
                    "¿Tiene valores pendientes de pago por saldos contables?",
                    "¿Tiene valores pendientes por anticipo de sueldos?",
                    "¿Tiene valores pendientes por recuperación de valores?",
                    "¿Tiene valores pendientes por devolución de muebles y equipos?"
            ),

            Area.SEGURIDAD_INFORMACION, List.of(
                    "Entregó archivos digitales",
                    "Entregó archivos físicos",
                    "Entregó copia del informe de actividades",
                    "Se verificó la información institucional"
            ),

            Area.RECURSOS_HUMANOS, List.of(
                    "Capacitación certifica la situación del servidor saliente",
                    "Evaluación del desempeño certifica la situación del servidor saliente",
                    "Se realizó la liquidación de cursos recibidos",
                    "Se aplicó la evaluación del desempeño del año en curso",
                    "Se realizó la liquidación de viajes y capacitación",
                    "El servidor fue desvinculado del puesto que renuncia",
                    "Entregó constancia y declaración juramentada de fin de gestión"
            ),

            Area.RECEPCION_DOCUMENTOS, List.of(
                    "Se recibió el documento de paz y salvo"
            )
    );

    public List<String> forArea(Area area) {
        return questions.get(area);
    }

    public List<QuestionOption> optionsForArea(Area area) {

        List<String> areaQuestions = forArea(area);
        List<QuestionOption> options = new ArrayList<>();

        // Preguntas fijas
        for (int index = 0; index < areaQuestions.size(); index++) {

            String key = keyFor(area, index);

            options.add(
                    new QuestionOption(
                            key,
                            areaQuestions.get(index),
                            null,
                            null,
                            writingFieldsForKey(key)
                    )
            );
        }

        // Preguntas personalizadas
        // No tienen campos adicionales: solamente Sí / No
        customQuestions.findAllByAreaOrderByIdAsc(area).forEach(question ->
                options.add(
                        new QuestionOption(
                                area.name().toLowerCase(java.util.Locale.ROOT)
                                        + "_custom_"
                                        + question.getId(),
                                question.getLabel(),
                                null,
                                null,
                                List.of()
                        )
                )
        );

        return options;
    }

    public CustomQuestion addQuestion(
            Area area,
            String label,
            String createdBy
    ) {

        String normalized = label.trim();

        if (customQuestions.existsByAreaAndLabelIgnoreCase(
                area,
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una pregunta igual en esta área."
            );
        }

        return customQuestions.save(
                new CustomQuestion(
                        area,
                        normalized,
                        createdBy
                )
        );
    }

    public String keyFor(Area area, int index) {
        return area.name().toLowerCase(
                java.util.Locale.ROOT
        ) + "_" + index;
    }

    public static List<String> writingFieldsForKey(
            String questionKey
    ) {

        if (questionKey == null) {
            return List.of();
        }

        return switch (questionKey) {

            // DIRECCIÓN DE UNIDAD
            case "direccion_unidad_3" -> List.of(
                    "Descripción del contrato",
                    "N.º de memorando de designación",
                    "Nombre del jefe inmediato",
                    "Cargo del jefe inmediato",
                    "Nombre del servidor que recibe trámites",
                    "Cargo del servidor que recibe",
                    "Observación"
            );

            case "direccion_unidad_4" -> List.of(
                    "Nombre del servidor que recibe trámites pendientes",
                    "Cargo del servidor que recibe"
            );

            // GESTIÓN ADMINISTRATIVA
            case "gestion_administrativa_1" ->
                    List.of(
                            "N.º de acta de entrega"
                    );

            case "gestion_administrativa_2",
                 "gestion_administrativa_3",

                 // GESTIÓN FINANCIERA
                 "gestion_financiera_0",
                 "gestion_financiera_1",
                 "gestion_financiera_2",
                 "gestion_financiera_3" ->

                    List.of(
                            "Valor a descontar (USD)"
                    );

            // TIC
            case "tic_0" ->
                    List.of(
                            "Observación de la verificación del equipo"
                    );

          //  case "tic_2",
            //     "tic_3",
              //   "tic_5" ->

                //    List.of(
                  //          "Correo institucional",
                   //         "Quipux",
                     //       "eSIGEF",
                       //     "SPRYN",
                         //   "eSByE",
                           // "Observación"
                    //);

            case "tic_4" ->
                    List.of(
                            "Observación sobre entrega/desactivación de tarjeta"
                    );

            // RECURSOS HUMANOS
            case "recursos_humanos_4" ->
                    List.of(
                            "Número de certificado o documento"
                    );

            case "recursos_humanos_5" ->
                    List.of(
                            "Número de declaración juramentada de fin de gestión recibida"
                    );

            // RECEPCIÓN DE DOCUMENTOS
            case "recepcion_documentos_0" ->
                    List.of(
                            "Fecha de entrega",
                            "Número de hojas recibidas",
                            "Nombre de quien recibe",
                            "Cargo de quien recibe"
                    );

            // Cualquier pregunta no configurada
            // solamente tendrá Sí / No
            default -> List.of();
        };
    }

    public List<Area> orderedAreas() {
        return List.of(
                Area.DIRECCION_UNIDAD,
                Area.GESTION_ADMINISTRATIVA,
                Area.TIC,
                Area.GESTION_FINANCIERA,
                Area.SEGURIDAD_INFORMACION,
                Area.RECURSOS_HUMANOS,
                Area.RECEPCION_DOCUMENTOS
        );
    }
}