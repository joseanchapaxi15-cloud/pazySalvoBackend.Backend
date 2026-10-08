# Informe de estado y respaldo — Sistema de Paz y Salvo

**Fecha:** 7 de octubre de 2026  
**Proyecto:** aplicación de prácticas para gestionar solicitudes institucionales de paz y salvo.  
**Carpeta de trabajo:** `C:\Users\CORE I7\Documents\ChatGPT\paz y salvo`

## 1. Objetivo y requisitos acordados

El sistema digitaliza el trámite de paz y salvo mediante un flujo por etapas y áreas. Las condiciones que se recogieron de los documentos iniciales y de las aclaraciones posteriores por chat son:

- Talento Humano crea solicitudes y administra las cuentas de las personas que responden y firman.
- Al crear un usuario responsable, Talento Humano asigna su área y las preguntas específicas que atenderá. Un área puede repartir sus preguntas entre varias personas.
- Cada usuario ve únicamente las solicitudes/preguntas que tiene asignadas. No se habilita la etapa siguiente hasta que las respuestas del área estén completas y firmadas.
- Cada persona firma en FirmaEC un PDF que agrupa todas sus preguntas asignadas en ese apartado. Una vez cargado el PDF firmado, las respuestas de ese grupo quedan bloqueadas para edición.
- El sistema envía avisos por correo al responsable cuando se habilita un trabajo en su bandeja, si se configura SMTP.
- Talento Humano puede editar, desactivar/reactivar y eliminar usuarios; también puede eliminar solicitudes creadas por error.
- La persona solicitante dispone de una cuenta de solo seguimiento para consultar el avance de su trámite; Talento Humano define el usuario y la contraseña inicial (por defecto se propone la cédula).
- Tecnologías acordadas: Angular, Spring Boot, PostgreSQL y despliegue final en un servidor Ubuntu.

## 2. Tecnologías y herramientas utilizadas

| Componente | Tecnología / versión configurada |
| --- | --- |
| Frontend | Angular (aplicación standalone), TypeScript, HTML y CSS |
| Backend | Java 21, Spring Boot 3.5.16, Spring Web, Spring Security, Spring Data JPA, Bean Validation y Spring Mail |
| Construcción backend | Apache Maven 3.10.0 instalado en Windows |
| Base de datos | PostgreSQL 17 en Docker Compose |
| Contenedores | Docker Compose con servicios `postgres`, `api` y `web` |
| Firma | FirmaEC: descarga de PDF, firma en FirmaEC y carga del documento resultante |
| Correo | SMTP configurable mediante variables de entorno |
| Desarrollo local | Angular en `http://localhost:4200`, con API Spring Boot local en `http://localhost:8081/api` |
| Despliegue Docker | Aplicación web publicada en el puerto `8088`; API y PostgreSQL en la red de Compose |

Herramientas de trabajo usadas para revisar y compilar el código: PowerShell, Maven, npm/Angular CLI, Docker Compose, búsqueda de código con `rg` y el editor del workspace.

## 3. Funcionalidades implementadas hasta ahora

### Solicitudes y flujo

- Creación de solicitudes con nombre, cédula, correo, cargo y unidad de trabajo.
- Detección de cédulas duplicadas.
- Creación de etapas para las áreas en el orden establecido por el catálogo del sistema.
- Bandeja, filtros, búsqueda y detalle de solicitudes.
- Solo queda habilitada el área que está en curso; una etapa posterior espera la terminación de las anteriores.
- Respuestas Sí/No, observaciones y valores a descontar.
- Talento Humano puede eliminar una solicitud con confirmación; se eliminan sus archivos y su historial asociados.

### Usuarios, permisos y asignaciones

- Inicio de sesión y cuentas con roles de Talento Humano y Responsable de área.
- Talento Humano crea cuentas y asigna un área y las preguntas correspondientes al crear el usuario.
- El sistema evita asignar la misma pregunta a dos usuarios del área. Las solicitudes nuevas toman esa distribución guardada.
- Talento Humano puede cambiar nombre, usuario y correo de una cuenta, activar/desactivar, restablecer contraseña y eliminarla cuando no tenga referencias que deban conservarse.
- Si una cuenta ya está vinculada a solicitudes o firmas, la aplicación recomienda desactivarla en lugar de borrarla para preservar el historial.
- Los cambios de usuarios están protegidos también desde el backend, no solo ocultos en la interfaz.

### Firma y documentos

- Generación de un PDF por responsable y apartado, reuniendo las preguntas que tiene asignadas.
- El usuario firma ese PDF en FirmaEC y carga el PDF firmado al sistema.
- Al cargarlo, se guardan huella SHA-256, fecha y datos del archivo; el documento firmado no se reemplaza y las preguntas firmadas quedan bloqueadas.
- Talento Humano puede cargar el PDF final firmado por el exfuncionario.
- Se mantiene un historial de auditoría de las acciones relevantes.

**Alcance de FirmaEC:** esta versión usa un flujo de descarga/firma/carga. No tiene una integración directa institucional con FirmaEC ni valida completamente la cadena, vigencia o revocación del certificado; esto depende de los servicios y credenciales que entregue la institución.

### Notificaciones por correo

- Hay un servicio de notificación SMTP para avisar a la persona responsable cuando se le asigna una etapa.
- La configuración se realiza con `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM`.
- Sin SMTP válido, el sistema registra que no pudo enviar el mensaje; la solicitud se guarda igualmente.

### Acceso de seguimiento para la persona solicitante

- Al crear una solicitud se genera una cuenta `SOLICITANTE` vinculada únicamente a esa solicitud.
- El formulario propone `solicitante-<cédula>` como usuario y la cédula como contraseña inicial. Talento Humano puede cambiar los valores antes de crear la solicitud; luego se muestran las credenciales para entregarlas a la persona.
- El solicitante ve el estado general y qué áreas están pendientes, en curso o completadas.
- La vista de seguimiento no muestra las respuestas internas ni los datos detallados de la solicitud. Los endpoints de escritura y descarga de documentos no le dan permisos para actuar como responsable.
- Talento Humano puede restablecer la contraseña desde administración de usuarios. La contraseña temporal se muestra en pantalla para entregarla.

## 4. Incidencias atendidas

### Puerto de PostgreSQL

En el equipo existía un conflicto al publicar PostgreSQL en el puerto local `5432`. Se dejó PostgreSQL publicado en `127.0.0.1:5433` para evitar competir con una instalación local que ocupe el puerto estándar. Dentro de Docker, la API sigue conectándose al puerto `5432` del contenedor.

### Error 403 al eliminar solicitudes

La configuración CORS del backend no incluía `DELETE`. Se agregó ese método a la lista permitida, junto a `GET`, `POST`, `PUT` y `OPTIONS`. El endpoint de eliminación además requiere rol Talento Humano.

### Error 409 al crear solicitud

El backend devuelve `409 Conflict` intencionalmente si la cédula ya tiene una solicitud o si todavía hay preguntas de áreas sin responsable asignado. Antes de crear solicitudes, revisar **Administrar usuarios** y repartir todas las preguntas de todas las áreas. La respuesta del backend contiene el detalle del conflicto.

La ventana de creación ahora consulta las siete áreas antes de habilitar el envío y muestra el número de preguntas que no tengan una cuenta responsable activa. Los usuarios desactivados ya no cuentan como responsables para solicitudes nuevas. La cédula se limita a un máximo de 10 dígitos y se valida también en backend.

El `500` reportado después se identificó con el registro adjunto: la restricción PostgreSQL `paz_salvo_user_role_check` solo aceptaba `TALENTO_HUMANO` y `RESPONSABLE_AREA`, y rechazaba el rol `SOLICITANTE`. Se amplió la restricción en la base de prácticas actual y se agregó el script `backend/src/main/resources/db/migration/V2__add_solicitante_role.sql` para aplicarlo manualmente a bases existentes.

La ventana fue simplificada para mostrar solo el total de asignaciones pendientes y un acceso directo a administración. También incluye campos editables para el usuario y contraseña inicial de seguimiento; propone la cédula como contraseña y permite cambiarla o generar otra.

La cuenta de seguimiento se guarda junto con la solicitud dentro de una transacción. Para evitar una colisión con el correo real del solicitante en la tabla de usuarios, se utiliza un correo técnico interno en esa cuenta; la dirección real permanece en la solicitud. El usuario de seguimiento se propone con la cédula y la contraseña inicial también se propone igual a la cédula, aunque puede cambiarse antes de enviar.

La sesión de inicio se conserva en `sessionStorage` para no cerrar sesión al recargar la página; el botón **Cerrar sesión** elimina los datos guardados. El modal de administración de usuarios ya no se cierra al hacer clic fuera, para evitar perder el formulario accidentalmente.

También se desactivó el cierre por clic fuera del modal **Nueva solicitud**. Ambos modales se cierran mediante sus controles visibles. Para errores HTTP 500, la interfaz ahora indica revisar la terminal del backend, donde aparece la excepción concreta.

## 5. Archivos principales modificados o revisados

- `backend/src/main/java/ec/inamhi/pazysalvo/config/SecurityConfig.java` — seguridad y métodos CORS permitidos.
- `backend/src/main/java/ec/inamhi/pazysalvo/api/RequestController.java` — endpoints de solicitudes, creación, seguimiento y documentos.
- `backend/src/main/java/ec/inamhi/pazysalvo/api/RequestMapper.java` y `RequestDtos.java` — respuestas de API y vista limitada de seguimiento.
- `backend/src/main/java/ec/inamhi/pazysalvo/api/UserController.java` y `UserDtos.java` — administración de usuarios y restablecimiento de claves.
- `backend/src/main/java/ec/inamhi/pazysalvo/domain/UserAccount.java` y `UserRole.java` — cuentas, permisos y rol de solicitante.
- `backend/src/main/java/ec/inamhi/pazysalvo/service/RequestService.java` — creación, asignación, flujo, cuenta vinculada y restricciones de acceso.
- `backend/src/main/java/ec/inamhi/pazysalvo/service/UserService.java` — administración de cuentas, preguntas y claves.
- `backend/src/main/java/ec/inamhi/pazysalvo/service/NotificationService.java` — correo SMTP a responsables.
- `frontend/src/app/app.component.ts` y `app.component.html` — interfaz, administración, seguimiento, formularios y visibilidad de contraseñas.
- `frontend/src/app/paz-salvo-api.service.ts`, `auth.service.ts` y `models.ts` — llamadas a API y modelos de usuario.
- `frontend/src/styles.css` — presentación de formularios, estados y acciones de usuarios.
- `docker-compose.yml`, `backend/pom.xml` y `README.md` — ejecución y configuración del proyecto.

## 6. Compilaciones realizadas

Las compilaciones de backend y frontend finalizaron correctamente después de los últimos cambios:

```powershell
# Desde backend
mvn -q -DskipTests compile

# Desde frontend
npm run build
```

En esta sesión no se ejecutó una prueba integral con la aplicación levantada contra una base PostgreSQL real; la compilación verifica que el código construye, pero no sustituye pruebas de los flujos en ejecución.

## 7. Cómo ejecutar en desarrollo local (Windows)

Desde PowerShell, en la raíz del proyecto:

```powershell
docker compose up -d postgres
```

En otra terminal:

```powershell
cd "C:\Users\CORE I7\Documents\ChatGPT\paz y salvo\backend"
mvn spring-boot:run
```

En una tercera terminal:

```powershell
cd "C:\Users\CORE I7\Documents\ChatGPT\paz y salvo\frontend"
npm install
npm start
```

Abrir `http://localhost:4200`. Si PowerShell no reconoce `mvn`, usar el ejecutable instalado en:

```powershell
& 'C:\Users\CORE I7\AppData\Local\Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd' spring-boot:run
```

Para reconstruir y reiniciar las imágenes Docker de API y web después de editar código:

```powershell
docker compose up -d --build api web
```

## 8. Cuenta inicial y configuración

La cuenta inicial de práctica documentada es `talento.humano`, con la clave temporal `cambiar-esta-clave-inicial` si no se reemplazó mediante configuración. Cambiarla antes de usar datos reales.

PostgreSQL local del Compose usa base `pazysalvo`, usuario `pazysalvo`, puerto publicado `5433`; el ejemplo de clave está en `docker-compose.yml` / `.env.example`. No guardar claves reales en el repositorio.

Variables relevantes:

- Base de datos: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- Archivos: `UPLOAD_DIR`.
- Correo: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`.
- Usuario inicial: `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_FULL_NAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`.

## 9. Pendientes recomendados antes de producción

1. Configurar secretos reales fuera del código y del control de versiones.
2. Desplegar detrás de HTTPS en Ubuntu y revisar el mecanismo de autenticación; la práctica actual usa HTTP Basic para las llamadas protegidas.
3. Configurar SMTP institucional y probar entrega, rebotes y destinatarios.
4. Confirmar con la institución la integración FirmaEC y cómo verificar certificados y firmas de forma criptográfica.
5. Definir respaldos/restauración de PostgreSQL y de los PDFs, retención documental y permisos de archivos.
6. Añadir y ejecutar pruebas de integración para los permisos, duplicados, asignaciones, transición entre áreas, firma, recuperación de contraseña y acceso de solicitantes.
7. Probar el cambio de esquema automático en una copia de la base antes de actualizar datos de producción; para producción conviene formalizar migraciones.
8. Definir un canal seguro para entregar las credenciales iniciales y temporales a cada persona. Actualmente se muestran en pantalla a Talento Humano, no se envían por correo.

## 10. Estado resumido

El proyecto contiene una aplicación funcional de prácticas Angular + Spring Boot + PostgreSQL, con flujo secuencial, roles, asignación de preguntas, carga de firmas FirmaEC, correo configurable, administración de cuentas y una vista de seguimiento para el solicitante. Las compilaciones actuales pasan. La integración institucional de FirmaEC, el despliegue seguro, la configuración real del SMTP y las pruebas de ejecución siguen pendientes para producción.

## 11. Cambios recientes (7 de octubre de 2026)

- El PDF del apartado para FirmaEC ahora se genera y descarga desde el navegador; ya no abre la ventana de impresión. El botón del formulario general también descarga un PDF directamente.
- La descarga del PDF firmado permite que el responsable descargue su propio archivo usando su sesión. Talento Humano puede solicitar el documento de una cuenta firmante. La cuenta SOLICITANTE no accede a documentos internos de firma.
- Talento Humano puede abrir el formulario de restablecimiento, escribir una contraseña nueva y verla una vez en el cuadro de entrega de credenciales. Para cuentas SOLICITANTE también existe la opción “Usar cédula”.
- La pantalla de seguimiento se identifica explícitamente para el rol SOLICITANTE y muestra un aviso si su cuenta no tiene una solicitud vinculada.
- Dependencias frontend: `jspdf` y `jspdf-autotable`.
- El formulario de nueva solicitud captura modalidad laboral, fechas de ingreso/salida, domicilio, teléfonos, provincia, cantón, contacto de emergencia, correo alternativo, lugar de trabajo desconcentrado y grupo ocupacional. La API agrega columnas nuevas manteniendo las solicitudes existentes.
- El PDF consolidado sigue el encabezado INAMHI-RH-001, las secciones numeradas 01–10, datos personales, dirección/unidad, tablas por departamento y autorización final PAdES. Los PDF por área conservan identidad básica y las preguntas del firmante para mantener una firma por grupo.
- Los datos adicionales de contacto y domicilio se devuelven únicamente a Talento Humano; las cuentas responsables de área reciben la información básica necesaria para el trámite.
- Se confirmó en el registro enviado que Spring Boot se ejecuta local en el puerto `8081`; el proxy Angular apunta a ese puerto. La web completa con Docker sigue disponible en `http://localhost:8088`.
- La API convierte las solicitudes a JSON dentro de una transacción para evitar errores 500 al leer relaciones JPA diferidas. También se elimina el aviso de error anterior después de cargar las solicitudes con éxito.
- La descarga de PDFs firmados resuelve el único firmante cuando falta `userId`; si el apartado contiene varios firmantes, muestra un error explícito para que se elija el correspondiente.
- Después de cambiar el proxy hay que reiniciar Angular (`Ctrl+C` y `npm start`) y Spring Boot (`Ctrl+C` y `mvn spring-boot:run`) para cargar los cambios.
- Verificación de esta corrección: compilación Maven (`-DskipTests compile`), compilación Angular (`npm run build`) y reconstrucción/arranque de Docker (`docker compose up -d --build api web`) completados.
- Verificación realizada: `npm run build` del frontend y `mvn -q -DskipTests compile` del backend completaron correctamente. Maven `package` no se pudo verificar porque Surefire intentó descargar dependencias y Java rechazó el certificado TLS del repositorio Maven.
- La cuenta solicitante mostrada en las capturas aparece vinculada al trámite #9 con rol `SOLICITANTE` en PostgreSQL. Las imágenes Docker de API y web se reconstruyeron y reiniciaron con `docker compose up -d --build api web`; ambos contenedores quedaron activos.

## 12. Aclaración de roles y asignación por unidad (actualización del 7 de octubre de 2026)

La estructura que se implementó para reflejar el flujo explicado por Talento Humano es:

- **Talento Humano** crea la solicitud con los datos personales y selecciona en un desplegable al **director de unidad** correspondiente (por ejemplo, Dario para TIC o Kevin para Recursos Humanos).
- El director seleccionado recibe las preguntas de **Dirección / unidad** de esa solicitud y completa lugar de trabajo, grupo ocupacional, unidad y cargo. Lugar de trabajo es obligatorio y se elige entre “Planta central” y “Procesos desconcentrados”. El backend no permite guardar respuestas/firma de esa etapa mientras falten esos datos.
- Los **responsables de área** son cuentas separadas. En Administración se les asigna un área (TIC, Gestión Financiera, Recursos Humanos, etc.) y preguntas concretas. Las preguntas de TIC, por ejemplo, pueden asignarse a Fernando aunque Dario sea el director de TIC; el director no recibe automáticamente esas preguntas de TIC.
- Para una persona de Recursos Humanos, Talento Humano selecciona al director de esa unidad (por ejemplo, Kevin), mientras las preguntas de Recursos Humanos se asignan a las cuentas que tengan esas preguntas distribuidas. Cada firmante ve únicamente sus preguntas.
- Talento Humano puede agregar preguntas nuevas a un área. Las preguntas nuevas aparecen en solicitudes creadas después de agregarlas; luego deben asignarse a una cuenta responsable del área desde la distribución de preguntas. Las preguntas personalizadas se guardan en PostgreSQL.

**Compatibilidad de cuentas:** la pantalla distingue “Director de unidad” y “Responsable de área”. En la base de datos ambos se representan como rol `RESPONSABLE_AREA`, diferenciados por el área `DIRECCION_UNIDAD` para directores. Esto conserva compatibilidad con las cuentas y restricciones existentes, sin exigir cambiar manualmente el constraint de roles de PostgreSQL.

**Archivos relacionados con esta actualización:**

- `frontend/src/app/app.component.html` y `app.component.ts`: formulario de alta, opción de director, vista de datos laborales para la unidad, administración de preguntas y selección del director.
- `frontend/src/app/paz-salvo-api.service.ts`: solicitudes HTTP para alta de solicitudes, guardar datos laborales y crear preguntas.
- `backend/src/main/java/ec/inamhi/pazysalvo/domain/CustomQuestion.java` y `repository/CustomQuestionRepository.java`: almacenamiento de preguntas personalizadas.
- `backend/src/main/java/ec/inamhi/pazysalvo/service/QuestionCatalog.java`: catálogo base y preguntas añadidas por Talento Humano.
- `backend/src/main/java/ec/inamhi/pazysalvo/service/RequestService.java`: selección del director para la solicitud, asignación a preguntas y validación antes de responder/firma.
- `backend/src/main/java/ec/inamhi/pazysalvo/api/RequestController.java`, `RequestDtos.java`, `UserController.java` y `UserDtos.java`: endpoints y datos de entrada/salida.
- `backend/src/main/java/ec/inamhi/pazysalvo/domain/EmployeeRequest.java` y `Answer.java`: persistencia de datos laborales y campos escritos de preguntas.
- `backend/src/main/java/ec/inamhi/pazysalvo/api/RequestMapper.java` y `service/SignedDocumentService.java`: visibilidad por rol y validación de campos escritos.

**Verificación de esta actualización:** Angular `npm run build`, backend `mvn -q compile` y `git diff --check` terminaron correctamente. La compilación Angular conserva advertencias de optimización CommonJS en dependencias de generación PDF, pero produjo el bundle. No se ejecutaron pruebas automatizadas ni una verificación funcional contra una base activa en esta pasada.

**Aplicación local:** reinicia Spring Boot para que Hibernate aplique el esquema nuevo de preguntas personalizadas (`ddl-auto: update`). Angular normalmente se recarga con `npm start`; si el proceso no actualiza, reinícialo. En el panel de usuarios crea directores con “Director de unidad” e indica su unidad. Crea responsables de área por separado y distribuye las preguntas de cada área. Después Talento Humano crea la solicitud y escoge el director correcto.

### Edición del tipo de cuenta

En **Administrar usuarios → Editar datos**, las cuentas operativas permiten cambiar entre **Director de unidad** y **Responsable de área**. Para Director se solicita la unidad que dirige; para Responsable de área se escoge el área operativa. Si una cuenta cambia de área, se limpian sus preguntas asignadas para evitar que conserve permisos de la unidad anterior; después de guardar, Talento Humano debe volver a distribuir las preguntas desde **Editar preguntas**. Las cuentas de Talento Humano y SOLICITANTE conservan sus tipos especiales y solo permiten editar sus datos de perfil desde ese formulario.

La identidad de la cuenta no cambia al editar su tipo; por eso las asignaciones de solicitudes ya creadas siguen ligadas a esa misma cuenta. La compilación más reciente del frontend y backend pasó después de este cambio; `git diff --check` tampoco encontró errores de formato.

### Selección automática del área del director

Al crear o editar una cuenta como Director de unidad, Dirección / unidad se elige de un desplegable con las áreas operativas configuradas (TIC, Gestión administrativa, Gestión financiera, Seguridad de la información, Recursos Humanos y Recepción de documentos). No se escribe el nombre manualmente. En la solicitud, el director ve su área ya asignada y el backend valida que no pueda registrar otra unidad distinta a la de su cuenta. La verificación `npm run build` y `mvn compile` terminó correctamente; Angular conserva avisos CommonJS en dependencias de PDF.

### Error 403 al editar una cuenta (CORS)

La captura mostró `403 Invalid CORS request` al actualizar `/api/users/{id}`. La edición usa el método HTTP `PATCH`, pero la configuración CORS de Spring Boot no lo permitía. Se añadió `PATCH` y el origen local `http://127.0.0.1:4200` junto a `http://localhost:4200`. `mvn compile` terminó correctamente. Hay que reiniciar Spring Boot para que empiece a aceptar la actualización; después recarga Angular e intenta guardar otra vez.

### Error 400 al crear usuarios

La captura reportó `400 Bad Request` en `/api/users`. Para impedir que el formulario envíe campos incompatibles con las validaciones de Spring, se alinearon las reglas visibles de usuario (3–60 caracteres permitidos), nombre, correo y contraseña. Se agregó una respuesta de validación legible para indicar el campo o formato rechazado por la API. Compilaciones Angular y Maven completadas. Reiniciar ambos procesos antes de volver a crear la cuenta; el nuevo mensaje dará el detalle si algún dato aún no cumple.

### Preguntas asignadas a los directores

Las cuentas Director de unidad también muestran el botón **Editar preguntas**. Talento Humano puede seleccionar preguntas de Dirección / unidad para ese director. Al crear una solicitud, el director elegido recibe solo las preguntas que tiene asignadas para su unidad; las preguntas de las otras áreas continúan asignándose a sus responsables propios. Si faltan asignaciones de Dirección / unidad, el backend rechaza la solicitud con detalle de las preguntas sin responsable. La compilación Maven y Angular pasó después de este ajuste.

### Director: preguntas obligatorias de unidad y preguntas adicionales

Se corrigió la asignación de preguntas para reflejar el flujo acordado. El director seleccionado recibe siempre las preguntas de **Dirección / unidad** y debe completar esos datos; esas preguntas no se configuran desde “Editar preguntas”. En esa pantalla Talento Humano puede asignarle preguntas adicionales del área operativa que dirige (por ejemplo, TIC). Esas preguntas extra quedan separadas de Dirección / unidad y se muestran en la sección operativa correspondiente de las solicitudes nuevas. Si cambia la unidad del director, se limpian sus asignaciones adicionales para que Talento Humano las distribuya de nuevo.

Se amplió el reconocimiento de nombres de unidad para asociarlos con el área operativa (por ejemplo, TIC/Tecnologías, Gestión financiera, Seguridad de la información, Recursos Humanos, Recepción de documentos y Gestión administrativa). Verificación: `npm run build`, `mvn -q -DskipTests compile` y `git diff --check` completados. No se corrieron pruebas automatizadas ni se verificó la interacción con PostgreSQL activo. Reinicia Spring Boot y Angular para aplicar el cambio.

### Error 400 al guardar preguntas adicionales del director

Se ajustó el guardado de asignaciones de cuentas director para descartar asignaciones antiguas de **Dirección / unidad**. Esas preguntas se asignan automáticamente al director y no forman parte de la selección editable; se conservan únicamente preguntas adicionales válidas del área operativa de su unidad. Al editar el perfil también se limpian claves antiguas de Dirección / unidad para que no bloqueen cambios posteriores. Verificación: `mvn -q -DskipTests compile` y `git diff --check` completados. Reiniciar Spring Boot antes de volver a guardar; no se probó con una base activa.

### Aviso de 28 preguntas sin responsable al abrir una solicitud

Se corrigió la verificación previa del formulario. Antes, comparaba las preguntas con la lista de usuarios mientras esa lista todavía podía estar cargando; por esa carrera de carga podía marcar todas las preguntas como pendientes. Ahora comprueba para cada pregunta el responsable que devuelve el endpoint de preguntas, sin depender del orden en que carguen los usuarios. Las preguntas de Dirección / unidad siguen excluidas de esta lista porque se asignan automáticamente al director. Verificación: `npm run build` y `git diff --check` completados. Reiniciar/recargar Angular para aplicar.

### Guardado de datos laborales y respuestas de Dirección / unidad

La captura mostró que los selectores laborales se renderizaban demasiado estrechos por una regla CSS global aplicada a todos los `select.control`; se limitó esa regla al filtro de la bandeja y se ajustó la cuadrícula de datos laborales. El guardado ahora conserva valores que ya están cargados en la solicitud o en la cuenta del director, exige los cuatro datos y mantiene la Dirección/unidad dentro del área asignada. Las respuestas y la generación de PDF de esa primera etapa quedan deshabilitadas hasta guardar los datos laborales, evitando respuestas HTTP 409 por intentar continuar antes de completar la etapa. Verificación: `npm run build`, `mvn -q -DskipTests compile` y `git diff --check` completados. El HTTP 500 al cargar `/api/requests` no se puede atribuir con certeza a partir de la captura de DevTools; se necesita la excepción completa de la terminal de Spring Boot.
