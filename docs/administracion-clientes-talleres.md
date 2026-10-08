# Administración de clientes y talleres — UC-CV-02

> Documento histórico de V6. Para contratos actuales de estatus, canonicalización y vehículos, consultar [UC-CV-03](entrega-uc-cv-03.md).
Incremento verificado el 5 de octubre de 2026. Conserva Spring JDBC, MariaDB, Flyway, sesiones, CSRF y CSP. No incorpora JPA ni JWT.

## Arquitectura y permisos

Backend: Controller → Service → Repository → MariaDB. Frontend: View → Component → Facade/API → backend. La autorización definitiva se aplica en servicios y SQL, incluidos recursos individuales, fotos y asociaciones.

- ADMIN consulta todos los talleres, administra sus datos/banners y asigna alcance a recepción.
- RECEPTIONIST administra clientes exclusivamente en talleres activos asignados mediante `user_workshop`. La revocación se comprueba en cada petición.
- MECHANIC y CLIENT no acceden a estas operaciones.
- Listados y fichas requieren `workshopId`; no hay una consulta global de clientes accesible a recepción. Una ficha sin asociación activa con el contexto responde 404.
- La ficha del cliente es compartida entre sus talleres asociados. Editar datos o suspenderlo afecta a ese cliente compartido. Los vínculos tienen un estado separado.
- ADMIN dispone de una cola paginada de clientes sin taller, para asignar los registros históricos o creados mediante cuenta pública. Este flujo no inventa identidad, direcciones ni alcance de recepción.

## Formularios y normalización

La normalización backend usa Unicode NFC, minúsculas, espacios exteriores/interiores normalizados y apóstrofe canónico. Las restricciones de formato se validan antes de guardar; no se convierten contenidos ambiguos. El frontend rechaza el pegado incompatible completo, sin truncar silenciosamente, valida durante captura y al enviar. Los errores identifican el campo y mantienen los valores capturados.

| Campo | Regla |
|---|---|
| Nombre y apellidos | Letras Unicode, acentos, ñ, espacios internos, apóstrofes y guiones; sin números, comas ni controles. Nombre y apellido paterno obligatorios; máximo 50 cada uno. Materno opcional. |
| CURP | Opcional, 18 caracteres, estructura mexicana/estado conocido y fecha real; coherencia con nacimiento si se proporciona. Persistencia en minúsculas. |
| RFC | Opcional en cliente; obligatorio en taller. 12/13 caracteres, estructura y fecha real; coherencia con nacimiento cuando procede. |
| Nacimiento/edad | Nacimiento opcional, fecha real, no futura y antigüedad máxima de 130 años. Edad calculada; no se persiste una edad editable. |
| Email | Exterior recortado, minúsculas, máximo 254/local 64; formato razonable y sin espacios internos ni puntos consecutivos. |
| Teléfonos | Campos personal/celular/trabajo independientes. Diez dígitos mexicanos; formatos admitidos: `5512345678`, `55 1234 5678`, `55-1234-5678`, `(55) 1234-5678`, con prefijo opcional `+52`. Persistencia de diez dígitos. |
| Contacto mínimo | Al menos un teléfono o email válido; email personal no es obligatorio si hay otro contacto. |
| CP | Exactamente cinco dígitos ASCII. Se rechaza `01,000`, `0100A`, espacios, signos, decimales, negativos y notación científica. Se conservan ceros iniciales. |
| Dirección | CP, estado, municipio, colonia y calle/número obligatorios; longitudes 5/120/120/120/180. SEPOMEX local completa ubicación/colonias y permite captura manual acotada. |
| Alias/contacto alternativo | Opcionales, mismos caracteres de nombre, máximos 120/160. |
| Taller | Nombre y razón social obligatorios, máximo 160 cada uno; letras/números/puntuación comercial definida, sin controles. RFC, teléfono, email y dirección completos obligatorios. |
| Imagen | Opcional; PNG/JPEG auténtico, hasta 15 MiB; contenido decodificado y recodificado; máximo 12000 píxeles por dimensión y 40 millones totales. |

La validación CURP/RFC comprueba formato y fechas; no consulta RENAPO/SAT ni acredita que el identificador esté oficialmente asignado. Los teléfonos se validan sintácticamente, sin comprobar titularidad o disponibilidad.

## Duplicados e integridad

V6 añade índices únicos para CURP, RFC, celular y nombre completo + nacimiento; conserva las restricciones anteriores. `customer_contact` reserva emails y teléfonos entre todos los campos y clientes, incluidos registros de cuenta pública, mediante triggers con propietario de migración. La aplicación no puede modificar esas reservas directamente. Un conflicto devuelve 409 y revierte la transacción, incluso en peticiones concurrentes.

La combinación nombre + nacimiento solo identifica duplicados si hay fecha. Cuando faltan fecha/identificadores y los contactos son distintos, no se presume que dos homónimos sean la misma persona.

Talleres: RFC único, nombre + razón social único y restricción existente empresa + nombre. Normalización backend previa a comparación. La versión de ficha/taller evita sobrescribir cambios concurrentes: enviar una versión antigua devuelve 409.

## Asociaciones

- **ASSOCIATE:** activa el destino y conserva los vínculos actuales.
- **REASSIGN:** activa el destino y desactiva el contexto de origen; conserva otros vínculos e historial.
- **Activar/desactivar:** modifica el vínculo, sin borrar cliente ni asociación. No permite retirar el último vínculo utilizable; debe reasignarse primero.
- Alta de cliente + asociación + auditoría se ejecutan en una transacción. Taller inexistente, inactivo o ajeno al usuario se rechaza.
- Las imágenes tienen UUID interno y rutas acotadas sin enlaces simbólicos. En rollback se retira el archivo nuevo; el anterior se retira después del commit. Las lecturas requieren permiso y devuelven `Cache-Control: no-store`.
- Auditoría registra actor, operación, cliente/taller y referencias de vínculo; no guarda CURP, RFC, teléfonos, emails ni imagen en el payload.

## Contratos principales

| Operación | Endpoint / cuerpo |
|---|---|
| Listar | `GET /api/customers?workshopId=1&page=1&pageSize=10&sort=name&direction=ASC&query=` |
| Ficha | `GET /api/customers/{id}?workshopId=1` |
| Alta | `POST /api/customers` → `{ "customer": { ...campos... }, "workshopId": 1 }` |
| Editar | `PUT /api/customers/{id}` → `{ "customer": { ...campos... }, "workshopId": 1, "version": 0 }` |
| Estado | `PATCH /api/customers/{id}/status` → `{ "workshopId": 1, "active": false }` |
| Talleres del cliente | `GET /api/customers/{id}/workshops?workshopId=1` |
| Asociar/reasignar | `POST /api/customers/{id}/workshops` → `{ "workshopId": 1, "targetWorkshopId": 2, "mode": "REASSIGN" }` |
| Estado del vínculo | `PATCH /api/customers/{id}/workshops/{target}` → `{ "workshopId": 1, "active": false }` |
| Foto cliente | `POST /api/customers/{id}/photo?workshopId=1` (multipart `file`); `GET /api/customers/photos/{reference}?workshopId=1` |
| Pendientes ADMIN | `GET /api/customers/unassigned?page=1&pageSize=10&direction=ASC&query=` |
| Asignación inicial ADMIN | `POST /api/customers/{id}/initial-workshop` → `{ "workshopId": 1 }` |
| Talleres disponibles | `GET /api/workshops?activeOnly=true`; filtra alcance en backend |
| Talleres ADMIN | `POST /api/workshops` → `{ "workshop": { ...campos... }, "companyId": 1 }`; companyId opcional solo cuando no hay empresa activa |
| Editar taller ADMIN | `PUT /api/workshops/{id}` → `{ "workshop": { ...campos... }, "version": 0, "active": true }` |
| Banner | `POST/GET /api/workshops/{id}/banner`; multipart `file` en POST, escritura ADMIN |
| Acceso de recepción ADMIN | `GET /api/workshops/access-users`, `GET /api/workshops/{id}/users`, `PUT /api/workshops/{id}/users/{user}` → `{ "active": true }` |

Paginación: página inicial 1, tamaño 1–10, respuesta `items/page/pageSize/totalItems/totalPages`. SQL aplica filtros, orden y límite; whitelist de `name`/`id` y `ASC`/`DESC`, parámetros ligados para búsqueda. Vue no descarga el conjunto completo.

## Migración y operación

Nueva migración: `backend/src/main/resources/db/migration/V6__customer_administration.sql`. V1–V5 se conservan intactas. La prueba de compatibilidad ejecuta V6 sobre registros y asociaciones V5, sin resetearlos. Datos históricos desconocidos permanecen NULL y se completan al editar; no se separan apellidos automáticamente.

Antes de aplicar a otro entorno, respaldar con `./scripts/db-backup.sh` y revisar contactos/name+nacimiento duplicados tras normalización. Un conflicto histórico debe resolverse con criterio humano: la migración falla para evitar fusionar identidades. MariaDB ejecuta DDL con commits implícitos; una migración fallida exige revisar su estado parcial antes de reintentar, no borrar la base.

Si cambia la IP del contenedor rootless, `python scripts/db-sync-host.py` copia las credenciales y permisos existentes de localhost a la IP privada actual, sin acceso comodín y sin imprimir hashes. Se conservan las cuentas anteriores utilizadas por definers. `app-start.sh` inicia MariaDB fuera de la unidad Java; `db-start.sh` verifica socket interno y puerto TCP publicado.

Después de aplicar V6, ejecutar `python scripts/db-grants-p2.py` para los permisos específicos de las tablas nuevas. Los triggers mantienen reservas con el propietario de migración. ADMIN debe asignar talleres a cada RECEPTIONIST desde **Talleres → Acceso de recepción**; no se otorga alcance global por defecto.

## Verificación realizada

- `python scripts/test-customer-admin.py`: 30 pruebas backend, cero errores/fallos/omitidas; MariaDB local con esquemas temporales y limpieza automática.
- `node --test tests/customer-validation.mjs tests/auth-frontend.mjs`: 12 aprobadas.
- `npm --prefix frontend run build`: Vue/TypeScript y Vite correctos.
- `.local/apache-maven-3.9.11/bin/mvn -B -f backend/pom.xml clean package -DskipTests`: paquete Java correcto.
- HTTP: ADMIN/RECEPTIONIST/MECHANIC/CLIENT, CSRF, invalidación de roles/credenciales, aislamiento por ID/lista/foto/mutación, asociaciones, versiones, duplicados concurrentes, entradas inválidas, talleres/banner y límites.
- Migración real de V5 con registros/relaciones históricos a V6 en esquema temporal; FKs/uniqueness y triggers comprobados.
- Navegador sobre API temporal con permisos de ejecución restringidos: taller/banner, SEPOMEX, cliente/edición/suspensión/reactivación, asociación/reasignación, cambio de taller, ASC/DESC, páginas 10/10/5, búsqueda, duplicado entre talleres con captura preservada y retorno al contexto seleccionado.
- Integración de vehículos: alta en navegador con propietario obtenido mediante el selector de clientes por taller.
- Escritura y pegado reales: CP con letras/coma, CURP/RFC con símbolos, teléfonos con letras y email con espacios, mensajes específicos y rechazo.

No se ejecutó una nueva verificación integral de todos los flujos legacy de órdenes, inventario, pagos o correo SMTP. No se comprobó la vigencia administrativa de cada registro SEPOMEX ni la identidad oficial de CURP/RFC.

## Arranque real comprobado

V6 se aplicó correctamente a la base existente el 2026-10-05, después de crear `backups/tallermeco-20261006T000526Z.sql`. Se conservaron 3 clientes, 1 taller y 2 asociaciones. Se ajustaron permisos de ejecución y el registro de contactos quedó con SELECT únicamente para `taller_app`.

Servicio `tallermeco-app-real.service` activo en `http://127.0.0.1:8080/#/login`. Comprobación HTTP real: CSRF 200, login ADMIN 200, listado por taller 200 (2 clientes), pendientes 200 (1), empresas/administración de alcance 200. No se introdujeron datos sintéticos en esta base. Los esquemas temporales quedaron eliminados.

## Corrección de captura de nacimiento

El control nativo de fecha podía mostrar mes/día según el navegador. Se sustituyó por captura explícita DD/MM/AAAA, conservando ISO YYYY-MM-DD en el modelo/API. La CURP distingue estructura inválida de fecha incompatible con nacimiento.

Regresión comprobada: 12/05/2006 → 2006-05-12 → 20 años al 2026-10-05; 05/12/2006 → 2006-12-05 → 19 años. Se probaron el día anterior/al cumpleaños/día posterior, escritura y pegado DD/MM/AAAA, pegado incompatible, coincidencia CURP y mensaje específico de fecha. La suite frontend queda en 13 pruebas aprobadas; build correcto. El cambio frontend se publicó mediante process-resources, sin reiniciar la sesión del usuario ni registrar un cliente de prueba en la base real.

## Presentación de la ficha

`frontend/src/modules/shared/presentation.ts` aplica formato únicamente al mostrar: nombres/direcciones/talleres con capitalización legible y conectores, CURP/RFC en mayúsculas, nacimiento DD/MM/AAAA y teléfonos separados para lectura. Emails conservan su representación canónica. La ficha, el directorio y las asociaciones reutilizan estos helpers; no cambian los valores del modelo/API ni los criterios de duplicados.

Build frontend correcto y 14 pruebas aprobadas. Verificación en navegador de la ficha existente: nombre y dirección legibles, CURP/RFC en mayúsculas, nacimiento 12/05/2006 y edad 20. No se escribieron cambios de cliente durante esta verificación.
