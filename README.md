# Sistema de Paz y Salvo

Aplicación inicial para gestionar el trámite institucional de paz y salvo. El proyecto está separado en un frontend Angular y una API Spring Boot con PostgreSQL.

## Funciones implementadas

- Registro y consulta de solicitudes.
- Flujo secuencial por áreas. La siguiente área solo se habilita cuando la anterior completa todas sus preguntas y firma su apartado.
- Bandeja filtrable por el área actualmente responsable.
- Inicio de sesión y permisos por rol: Talento Humano administra usuarios y asigna preguntas individualmente; cada responsable solo ve sus preguntas asignadas.
- Asignación de un usuario responsable a cada apartado de cada solicitud, con notificación por correo a esa persona.
- Respuestas Sí/No, observaciones y valores a descontar.
- Un único PDF de FirmaEC por apartado que agrupa todas las respuestas del área.
- Generación de un PDF por apartado para firmarlo con FirmaEC, carga del PDF firmado, hash SHA-256 y bloqueo de reemplazo antes de habilitar la siguiente área.
- Registro de acciones en auditoría.
- Carga del PDF firmado por el exfuncionario. El archivo se almacena con hash SHA-256 y no se reemplaza si ya existe uno cargado.
- Eliminación de solicitudes equivocadas por Talento Humano, con confirmación; también se eliminan los archivos firmados y el historial de esa solicitud.
- Aviso por correo configurable para la próxima persona responsable.
- Despliegue local de PostgreSQL mediante Docker Compose.

## Requisitos

- Java 21 o superior y Maven 3.9 o superior.
- Node.js 22.22 o 24.15 o superior y npm.
- Docker Desktop o Docker Engine con Docker Compose.

## Inicio en desarrollo

1. Inicia PostgreSQL desde la raíz:

   ```powershell
   docker compose up -d postgres
   ```

PostgreSQL se publica localmente en el puerto `5433` para evitar conflictos con una instalación que ya use `5432`.

2. En una terminal inicia Spring Boot:

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

   La API queda en `http://localhost:8081/api`.

3. En otra terminal inicia Angular:

   ```powershell
   cd frontend
   npm install
   npm start
   ```

   Abre `http://localhost:4200`. El proxy reenvía `/api` a la API local en el puerto `8081`.

Para ejecutar todo con Docker, usa `docker compose up -d --build`; la web queda disponible en `http://localhost:8088`.

Al iniciar por primera vez, se crea una cuenta inicial de Talento Humano. Ingresa con `talento.humano` y la clave `cambiar-esta-clave-inicial`. En **Administrar usuarios**, Talento Humano crea las cuentas de responsables y les asigna su área. Cambia la clave inicial antes de usar información real; para la práctica local se configura en `backend/src/main/resources/application.yml`.

Al crear cada cuenta responsable, Talento Humano selecciona su área y las preguntas que atenderá. El sistema evita asignar la misma pregunta a dos usuarios de esa área. Las solicitudes nuevas heredan esa distribución automáticamente; no se asignan preguntas dentro de cada solicitud. Antes de registrar una solicitud, configura responsables para todas las preguntas de todas las áreas.

Al crear una solicitud se registran los datos del formulario INAMHI-RH-001: modalidad laboral, fechas de ingreso y salida, dirección, teléfonos, provincia, cantón, contacto de emergencia, correo alternativo, lugar de trabajo y grupo ocupacional, además de los datos básicos del servidor. El PDF descargable sigue las secciones numeradas 01 a 10 de la referencia. Los datos de domicilio y contacto solo se entregan a Talento Humano en la respuesta de la API.

Varias personas pueden repartirse un apartado: cada una responde y firma en un único PDF el grupo de preguntas que tiene asignado. El área solo se completa cuando todas las preguntas han sido firmadas, y la siguiente queda bloqueada hasta entonces. Talento Humano puede eliminar una solicitud mal creada desde su detalle; la confirmación informa que también se borrarán los PDF y el historial asociados.

Flujo por responsable: responde las preguntas asignadas, pulsa **Generar PDF para FirmaEC** para descargar el PDF, firma ese archivo en la aplicación FirmaEC y luego usa **Cargar PDF firmado con FirmaEC**. El sistema registra ese PDF para todas las preguntas del grupo, detecta una estructura de firma, conserva el hash y no permite reemplazarlo. La detección estructural no valida la cadena del certificado ni su revocación.

## Configuración opcional

La API admite estas variables de entorno:

| Variable | Uso |
| --- | --- |
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USER` | Usuario de PostgreSQL |
| `DB_PASSWORD` | Clave de PostgreSQL |
| `UPLOAD_DIR` | Carpeta privada para los PDF firmados; por defecto `./uploads` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Envío SMTP de notificaciones al responsable asignado |

Sin SMTP, las solicitudes y transiciones se guardan; el backend registra que la notificación no pudo enviarse.

## Despliegue de práctica con Docker

En Ubuntu, copia `.env.example` como `.env`, cambia las claves de PostgreSQL y de la cuenta inicial de Talento Humano, y configura SMTP. Luego ejecuta `docker compose up -d --build`. La aplicación web queda en `http://SERVIDOR:8088` y PostgreSQL no necesita exponerse fuera del servidor para su uso normal.

## FirmaEC y producción

La versión permite descargar el documento final desde la interfaz, cargar el PDF firmado y conservarlo sin sobrescribirlo. La validación criptográfica del certificado y la integración directa con FirmaEC no se simulan: requieren definir el mecanismo institucional de FirmaEC y sus credenciales/endpoints. El backend guarda el SHA-256, tamaño y fecha de carga para identificar exactamente el archivo recibido.

El inicio de sesión y los permisos por rol están implementados para la práctica. Antes de producción se debe configurar HTTPS mediante un proxy inverso (el acceso HTTP Basic envía credenciales en cada solicitud), gestión de secretos, respaldos, política de retención, migraciones de base de datos, validación criptográfica de firma/certificado FirmaEC y revisión de privacidad. FirmaEC también ofrece integración institucional centralizada; requiere registrar el sistema, una clave API y configurar el servicio de retorno HTTPS de la institución. Esta configuración no se puede activar sin esos datos institucionales.
