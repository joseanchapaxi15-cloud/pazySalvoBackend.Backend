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

### Acceso de pruebas desde otra PC en la red local

Angular debe iniciarse con `npm start -- --host 0.0.0.0`; el proxy de desarrollo reenvía `/api` al backend en `localhost:8081`. Para el origen LAN observado en la prueba, `http://10.224.159.103:4200`, se agregó permiso CORS en `SecurityConfig` y en los controladores `AuthController`, `RequestController` y `ReportController`, cuyas anotaciones CORS restringían el acceso a localhost. Reiniciar Spring Boot después de este cambio y volver a intentar el inicio de sesión desde la otra PC. Si la dirección IP cambia, actualizar el origen permitido en esos archivos.

### Exportación y consulta de auditoría

La exportación CSV usaba comas, por lo que Excel con configuración regional en español mostraba cada registro en una sola columna. Ahora ambas exportaciones utilizan punto y coma y la directiva `sep=;` para que Excel distribuya correctamente los campos. Se agregó `GET /api/reports/audit`, restringido a Talento Humano, y el botón **Ver auditoría** en el frontend para consultar el historial global con solicitud, usuario, fecha, IP y detalle. Se conserva también el historial individual desde cada solicitud.

Se ajustó la codificación de los CSV a UTF-16LE con BOM para que Excel de Windows conserve tildes y eñes al abrirlos directamente. En la auditoría, el backend usa la IP remota y, cuando la petición llega a través del proxy local de Angular, lee el último valor de `X-Forwarded-For`; el proxy de desarrollo ahora habilita ese encabezado. Los registros históricos cuyo campo IP ya quedó vacío no pueden reconstruirse retroactivamente; las acciones nuevas se capturarán tras reiniciar ambos servidores.

El reporte general ahora omite filas de preguntas sin respuesta; solo exporta las respuestas registradas. La IP de auditoría corresponde al cliente que realizó la acción (por ejemplo, el equipo del compañero al acceder desde otra PC); una acción ejecutada desde el navegador local podría quedar como loopback, y los eventos antiguos sin IP no se pueden completar después.

El solicitante ve ahora el avance global incluyendo la etapa de firma final. Cuando todas las áreas han firmado, el solicitante firma el PDF consolidado con su certificado; al pasar a revisión puede descargar ese documento. La decisión final de aprobar o negar permanece en la vista de Talento Humano. El endpoint de descarga permite al solicitante vinculado acceder únicamente al PDF final de su propia solicitud, y valida que ya exista.

Las notificaciones de responsables siguen la secuencia: solo se envían después de confirmar la transacción que activa su sección. La bandeja de responsables ahora muestra únicamente solicitudes con sección `EN_CURSO` y preguntas asignadas a esa cuenta, evitando que vean etapas futuras antes de recibir el aviso. Las credenciales del solicitante se siguen enviando al crear la solicitud, y su aviso de firma final se envía cuando todas las áreas completan sus firmas.

Al editar el perfil de un director, los cambios de nombre, usuario o correo ya no vuelven a validar asignaciones de preguntas heredadas. Esa validación ocurre cuando cambia el área o la unidad, evitando que una asignación anterior invalide la edición de datos de contacto.

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

### Firma directa con certificado P12 (apartados y documento final)

Se cambió el flujo de firma para que cada responsable firme desde el botón **Firmar electrónicamente**. El sistema prepara el PDF en memoria, solicita el certificado `.p12`/`.pfx` y su contraseña, y genera una firma criptográfica PAdES dentro del backend. Una sola firma cubre todas las preguntas que ese responsable tiene pendientes en el apartado. Se añadió el mismo flujo para la firma final del servidor saliente: el solicitante genera y firma el documento consolidado sin descargarlo y volverlo a cargar. El endpoint final valida que la cuenta solicitante esté vinculada a esa solicitud y que el flujo de áreas haya concluido. Se quitaron del API los endpoints anteriores que aceptaban PDFs firmados subidos manualmente, para evitar que se omita la firma por certificado.

El certificado y la contraseña no se almacenan; se limitan a la operación, y el PDF firmado se guarda en el directorio configurado con SHA-256 y auditoría. La cuenta de seguimiento recibe sus propios datos, respuestas y nombres/firmas de las áreas para generar el documento consolidado. Se aumentó el máximo de solicitud multipart a 32 MB para acomodar documento y certificado. Dependencias agregadas: Apache PDFBox 3.0.8 y Bouncy Castle 1.85.

**Límites actuales:** se verifica integridad criptográfica al firmar, vigencia temporal del certificado y uso de clave de firma. No está integrada la confianza institucional de la CA, revocación OCSP/CRL, sellado de tiempo ni validación LTV; esas comprobaciones requieren la cadena y servicios institucionales. El despliegue productivo debe usar HTTPS.

**Verificación:** compilación Maven `mvn -q -DskipTests compile`, `npm run build` y `git diff --check` ejecutados correctamente después de los cambios. Angular muestra advertencias CommonJS de dependencias usadas por generación de PDF. No se ejecutaron pruebas automatizadas ni una firma real con un certificado P12.

### Firma generada en backend y notificaciones en cadena

Se ajustó la firma para que Angular envíe únicamente el archivo P12/PFX y su contraseña. `SignedDocumentService` obtiene las respuestas guardadas, genera el PDF del apartado dentro del backend en memoria, firma ese PDF y almacena solo el resultado firmado. El PDF final también se genera en backend y el servidor valida que todas las áreas y preguntas estén firmadas antes de aceptar la firma del solicitante. Se eliminó la generación de PDF en Angular para estas firmas.

Al crear una solicitud se notifica al responsable inicial. Cuando el último responsable de una etapa firma y la siguiente sección se habilita, se envía un correo a cada responsable asignado a esa sección. Cuando finalizan todas las áreas, se notifica al solicitante para que firme el PDF consolidado. Los avisos se ejecutan después del commit de PostgreSQL para no notificar una transición que no se haya guardado. Si SMTP falla, se registra en el log y no se revierte la firma; para recibir correos deben configurarse `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM`.

El backend valida el estado y asignaciones, bloquea la edición de respuestas firmadas, registra SHA-256/auditoría y no persiste el P12 ni la contraseña. Verificación: `mvn -q -DskipTests compile`, `npm run build` y `git diff --check` completados. No se ejecutaron pruebas automatizadas, envío SMTP real ni firma con certificado P12 real.

### Ajustes al catálogo de preguntas enviados por el usuario

Se actualizaron las dos preguntas de Dirección / unidad indicadas y se corrigieron espacios/acentos y la redacción repetida de la segunda. Recepción de documentos queda con una sola pregunta (“Se recibió el documento de paz y salvo”). Se adaptaron los campos escritos para las claves de Dirección / unidad, Gestión administrativa, Gestión financiera, TIC, Recursos Humanos y Recepción. Se corrigió la coma sobrante en `List.of(...)`, que impedía compilar el catálogo. Verificación: `mvn -q -DskipTests compile` y `git diff --check` completados. Estos cambios afectan solicitudes nuevas; los registros existentes conservan las preguntas guardadas al crearlos.

### Ocultar detalle genérico en preguntas sin campos escritos

Se eliminó del formulario Angular el fallback que insertaba “Detalle u observación” cada vez que una pregunta tenía `writingFields` vacío. Ahora solo aparecen los campos definidos en `QuestionCatalog.writingFieldsForKey`; las preguntas sin campos requieren únicamente Sí/No. La validación del frontend y backend acepta preguntas sin campos adicionales y no exige un detalle oculto. El mapper y el PDF del servidor usan la configuración actual del catálogo para las preguntas estándar, incluso en solicitudes existentes. Verificación: `npm run build`, `mvn -q -DskipTests compile` y `git diff --check` completados. Reinicia el backend y recarga Angular con Ctrl+F5.

### Seguimiento del solicitante y corrección de carga de solicitud vinculada

Se corrigió la consulta de solicitudes del rol SOLICITANTE para obtener la solicitud asociada a su cuenta con sus secciones, respuestas y responsables cargados explícitamente. Esto evita serializar relaciones JPA perezosas fuera de su sesión y corrige el 500 que impedía mostrar el trámite. Se ajustó también la autorización de consulta y firma final para comprobar el vínculo mediante el ID de cuenta, sin navegar una relación JPA diferida del usuario autenticado.

La vista de seguimiento ahora muestra por cada área el estado, nombre de cada responsable, cuántas preguntas tiene asignadas y cuántas firmó; indica las pendientes y marca si la cuenta responsable está inactiva. Los correos de responsables no se exponen a la cuenta solicitante. Verificación: compilación Maven y `npm run build` completados. No se probó con el PostgreSQL local ni con una cuenta ya existente; hay que reiniciar ambos procesos y confirmar que `tracked_request_id` de la cuenta solicitante apunte a la solicitud.

### Error de arranque por método derivado de Spring Data

El log enviado identificó que `existsByIdAndTrackedRequest_Id` estaba declarado en `RequestRepository`, cuyo tipo de entidad es `EmployeeRequest`. Spring intentaba buscar `trackedRequest` en esa entidad y no encontraba la propiedad, por eso fallaba la creación del repositorio y en cadena `RequestService` y `ReportController`. Se eliminó ese método derivado y se reemplazó por una consulta JPQL explícita que busca el vínculo desde `UserAccount`, donde sí existe `trackedRequest`. La consulta se usa en la autorización de consulta de la solicitud y de firma final del solicitante. Verificación: `mvn -q -DskipTests compile` completado. No se ejecutó una segunda instancia del backend porque Docker ya tiene el API activo; reiniciar el backend que usa el usuario para validar el inicio.

### Firma DEMO para prácticas sin certificado ni carga de PDF

Se conectó el modo de firma de prueba que el usuario estaba desarrollando. La ventana de firma ahora permite elegir explícitamente **Firma de prueba**; Angular llama a rutas separadas `/sign-demo` y `/sign-final-demo`, que no solicitan P12, contraseña ni PDF subido. El backend genera los PDF a partir del trámite y les estampa el rótulo `FIRMA DEMO - SIN VALIDEZ ELECTRONICA`; las auditorías también identifican las operaciones como DEMO. Se conserva el flujo normal P12 en sus rutas originales. Esta firma de práctica permite probar la transición de etapas, pero no debe usarse en producción ni presentarse como firma electrónica válida. Verificación: `mvn -q -DskipTests compile`, `npm run build` y `git diff --check` completados; no se probó el recorrido en el navegador.

### Diagnóstico visible al fallar una firma DEMO

El usuario avanzó Dirección / unidad y reportó `409 Conflict` al firmar Gestión administrativa. Se añadió una respuesta JSON `detail` para errores `ResponseStatusException`, de modo que Angular pueda mostrar el motivo en vez del genérico `Http failure response`. La validación DEMO ahora enumera las preguntas sin respuesta o con campos escritos incompletos. La ayuda y el mensaje después de guardar respuestas ya no dicen que siempre hay que cargar P12. Verificación: Maven compile, Angular build y `git diff --check` completados. Reiniciar el backend y Angular antes de probar otra vez; el nuevo mensaje identificará qué respuesta/campo de la sección falta, si esa es la causa del 409.

La nueva captura mostró que los montos escritos en las preguntas 3 y 4 de Gestión administrativa no estaban llegando al backend como `amount`, aunque aparecían en los controles. El control Angular se cambió a conversión explícita a número al editar y antes de guardar; así el backend recibe `BigDecimal` y la validación reconoce esos importes. Verificación: `npm run build` completado. Recargar Angular y guardar/resolicitar la firma DEMO otra vez.

El usuario reportó que persistía el 409, y la captura reveló además un error de expresión regular HTML en el campo de nombre (la clase de caracteres no era válida bajo el modo Unicode `v` de Chrome). Se forzó una conversión adicional del importe al crear el payload de guardado y firma, y se cambió el guion de las reglas `pattern` por `\x2D` para evitar el error de sintaxis del navegador. Verificación: Angular `npm run build` completado. Recargar la interfaz con Ctrl+F5; verificar si el 409 desaparece y si no, el detalle actualizado debe señalar específicamente la causa.

Una captura posterior siguió identificando los montos de “deducibles” y “pasajes aéreos” aunque estaban escritos. Se corrigió la discrepancia de persistencia: Angular ahora copia el importe al JSON `detail` además del campo numérico; el backend puede recuperar el `BigDecimal` desde ese dato cuando la columna `amount` llega nula, y lo normaliza antes de validar/generar el PDF. Verificación: `mvn -q -DskipTests compile` y `npm run build` completados. Reiniciar Spring Boot, recargar Angular con Ctrl+F5 y volver a confirmar el apartado.

### Confirmación de notificaciones por correo y destinatario final

Se confirmó que las notificaciones se disparan al habilitar la primera etapa y cada etapa siguiente, y al quedar el trámite listo para firma final. El envío efectivo requiere configurar SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) en `.env`; con `MAIL_HOST` vacío no se enviarán correos y el servicio dejará el fallo en el log. Se corrigió el destinatario del aviso final para usar el correo real del servidor saliente (`EmployeeRequest.email`) en lugar del correo interno placeholder `@tracking.local` de la cuenta de seguimiento. Las credenciales de seguimiento no se envían por correo al registrarse. Verificación: `mvn -q -DskipTests compile` completado.

### SMTP Gmail para desarrollo local

Se preparó `.env` (ignorado por Git) con host `smtp.gmail.com`, puerto 587, STARTTLS y autenticación activados. El backend local importa ese archivo desde la raíz del proyecto cuando se ejecuta desde `backend`. Los campos de usuario y contraseña son marcadores; se deben reemplazar en el equipo local por la dirección Gmail y una contraseña de aplicación de Google, nunca por la contraseña normal ni enviándola por el chat. Verificación: `mvn -q -DskipTests compile` completado y se confirmó que `.env` está en `.gitignore`. No se probó la autenticación/envío porque no se proporcionaron credenciales reales y Docker no estaba iniciado.

### Diagnóstico y corrección de correo SMTP con certificado Avast

El registro de Spring indicaba `PKIX path building failed` durante STARTTLS. Se inspeccionó la cadena SMTP sin enviar credenciales: Windows confía en el certificado raíz `Avast Web/Mail Shield Root`, mientras que el truststore estándar de Java lo rechazaba. Se verificó que Java 21 puede establecer TLS 1.3 para `smtp.gmail.com` usando el almacén de certificados confiables de Windows (`Windows-ROOT`). El perfil Maven `windows-trust-store` activa esa integración solo en Windows para `spring-boot:run`; no desactiva la validación TLS. La importación al truststore global de JDK no se pudo realizar por falta de permisos de administrador, por lo que se evitó cambiarlo.

También se añadió el envío, después de confirmar la transacción de creación, de las credenciales temporales de seguimiento al correo del solicitante. La contraseña solo se mantiene en memoria durante el envío; no se registra en logs ni se almacena en texto plano. La interfaz aclara que el sistema intentará enviar una copia y que Talento Humano puede entregar las credenciales manualmente si no llega. `mvn -q -DskipTests compile`, `npm run build` y `git diff --check` completaron correctamente. La conexión STARTTLS de Java 21 a Gmail se verificó en modo de solo conexión usando `Windows-ROOT`; no se autenticó ni se remitió un correo real desde este entorno. Pendiente: reiniciar el backend con `mvn spring-boot:run`, crear una solicitud de prueba y confirmar en consola `Credenciales de seguimiento enviadas` y `Aviso enviado`.

### Auditoría de solicitudes y exportación

Se amplió `paz_salvo_audit` con la IP del cliente y se centralizó la captura de eventos para registrar fecha/hora, actor, acción, detalle y dirección IP remota al crear solicitudes, actualizar datos laborales/respuestas, firmar (P12 o demo), cambiar estado y eliminar solicitudes. El historial de una solicitud muestra también la IP. La eliminación conserva los eventos asociados en la tabla de auditoría para no borrar el rastro.

Talento Humano dispone de **Exportar auditoría (CSV)** compatible con Excel, separado del reporte de datos y respuestas. Incluye solicitud, servidor, acción, usuario, fecha/hora, IP y detalle. `ddl-auto: update` añade la nueva columna nullable al iniciar el backend. En localhost la IP puede mostrarse como loopback; detrás de un proxy se registrará la IP de conexión que ve Spring a menos que se configure el proxy de confianza. Verificación: `mvn -q -DskipTests compile`, `npm run build` y `git diff --check` completados; no se ejecutó un flujo con base de datos/navegador.

### Enrutamiento del aviso inicial al director de la unidad correcta

Se corrigió el alta de solicitudes para que Talento Humano registre primero la Dirección / unidad del servidor saliente y el sistema asigne automáticamente al único director activo de esa unidad. La interfaz ya no permite escoger manualmente al director; si hay cero o varias cuentas activas para esa unidad, el alta se bloquea y solicita corregir la configuración. La API valida la unicidad y guarda esa unidad en la solicitud desde su creación; el director conserva la responsabilidad de completar el resto de datos laborales. Así, el aviso inicial de `DIRECCION_UNIDAD` va al director vinculado a esa unidad.

Se unificó el comportamiento de desplazamiento para todas las ventanas modales (administración de usuarios, firma, alta, credenciales e historial). El fondo permite desplazamiento cuando la ventana es más alta que la pantalla y cada modal conserva su propio scroll vertical, también en pantallas pequeñas, para que todos los campos y acciones sean accesibles.

### Organización de cuentas de seguimiento

En Administración de usuarios, las cuentas institucionales se muestran en su propia lista y las cuentas `SOLICITANTE` quedan separadas en un bloque plegable con su cantidad. No se desactivan automáticamente al cerrar el trámite: el exservidor puede seguir consultando la decisión final y descargar el documento consolidado. Las cuentas y las solicitudes se conservan para mantener trazabilidad.

### Padrón de personal importable desde Excel

Se agregó un padrón institucional separado de las solicitudes, importable por Talento Humano desde archivos `.xlsx` o `.xls` de hasta 10 MB. La plantilla descargable define los encabezados; son obligatorios cédula, nombres y apellidos, y dirección/unidad. La importación hace upsert por cédula, conserva filas válidas y reporta errores por número de fila. El archivo se procesa en memoria y no se guarda como adjunto; los datos de cada persona sí se guardan en una tabla propia. La API de importación y búsqueda exige rol Talento Humano.

Al ingresar una cédula de diez dígitos en una solicitud nueva, el frontend busca el padrón y rellena los datos personales y laborales disponibles. Talento Humano puede revisar y corregir los campos antes de registrar la solicitud. La unidad del padrón queda seleccionada y el director responsable se asigna a partir de ella; si no hay exactamente un director activo configurado, la creación se bloquea. Si la cédula no está en el padrón, se permite continuar con el llenado manual. Verificación: `mvn -q -DskipTests compile` y `npm run build` completados; falta probar con un Excel real de Recursos Humanos.

### Endurecimiento inicial para certificados digitales

Se eliminó el guardado de la cabecera HTTP Basic (que contiene la contraseña codificada, no cifrada) en `sessionStorage`; la autenticación queda solo en memoria y al recargar la página se requiere iniciar sesión de nuevo. El navegador vacía el campo de contraseña y libera las referencias al archivo P12 al enviar la operación. En backend ya se convertía la contraseña a `char[]` y se limpiaban tanto ese arreglo como los bytes del P12 en `finally`; no se guardan esos secretos en base de datos ni en los logs de aplicación.

Se añadieron encabezados HTTP de seguridad, CORS configurable centralmente y un perfil `production` que exige HTTPS, valida el esquema existente (`ddl-auto: validate`), exige secretos/rutas/orígenes explícitos y deshabilita las rutas de firma DEMO. La firma DEMO continúa disponible fuera de ese perfil para pruebas locales. Para desplegar producción es obligatorio terminar TLS en un proxy confiable y restringir CORS al dominio institucional HTTPS. Compilación backend (`mvn '-Dmaven.test.skip=true' compile`) y frontend (`npm run build`) completadas.

Pendiente para aceptar firmas institucionales como prueba de identidad confiable: recibir la lista oficial de autoridades certificadoras/raíces de confianza y el formato oficial de identificación presente en los certificados, para validar cadena, revocación (CRL/OCSP) y correspondencia entre certificado y cuenta del funcionario. Actualmente se verifica vigencia temporal y uso de clave, pero esas comprobaciones no demuestran por sí solas que el certificado pertenezca al usuario autenticado. No desplegar como servicio de firma institucional hasta completar esa validación y una revisión de seguridad operativa (proxy, firewall, secretos, respaldos y permisos del directorio de documentos).
