# UC-CV-03 — Estatus y administración de vehículos

Estado de implementación: 8 de octubre de 2026. Extiende UC-CV-02; no altera V1–V6.

## Implementación y contratos

| Área | Implementación | Contrato |
|---|---|---|
| Identificadores | CURP y RFC de clientes/talleres se almacenan y retornan en MAYÚSCULAS; email sigue en minúsculas | InputRules; constraints binarios de V7; unicidad existente conservada |
| Clientes | `status_id` reemplaza `customer.active`; DTO expone código, descripción y `allowsOperations` | `PATCH /api/customers/{id}/status`: workshopId, statusId, version |
| Transición | Bloqueo de cliente dentro del scope; validación de versión; sin cambio no modifica versión ni audita | `CUSTOMER_STATUS_CHANGED`: anterior, nuevo, actor y taller |
| Catálogos | Dos tablas independientes: customer_status y vehicle_status | `GET/POST /api/statuses/{customers\|vehicles}`; `PUT/DELETE .../{id}` |
| Protección | ACTIVE/SUSPENDED de sistema; código y semántica inmutables. Descripción editable | Triggers y servicio; DELETE protegido o en uso devuelve 409; FK vigente |
| Personalizados | ADMIN crea/edita/elimina cuando no están asignados; `allows_operations` explícito | Code único 2–40 caracteres (sin DELETED/UPDATED/CANCELLED/INACTIVE de cliente ni IN_SERVICE de vehículo); descripción hasta 500; version para editar/eliminar |
| Recepción | Consulta catálogos; asigna estatus a entidades de sus talleres | Spring Security, Actor, WorkshopAccess y filtros JDBC |
| Directorio clientes | Paginación existente ≤10, búsqueda, ASC/DESC y filtro opcional statusId | COUNT + LIMIT/OFFSET; whitelist name/id conservada |
| Modal cliente | Reutiliza CustomerForm dentro del Modal nativo sin forms anidados | Foco restaurado; Escape y cierre bloqueados durante submit; errores conservados; contexto de taller estable |
| Vehículos | Módulo propio `frontend/src/modules/vehicles` y backend `vehicle` | Controller → Service → Repository; API/Facade → HTTP compartido |
| Modelo vehículo | VIN, placa, marca, modelo, versión, año y color obligatorios en escrituras modernas; km opcional | `POST /api/vehicles`: customerId, workshopId, vehicle; PUT conserva propietario y exige version |
| Directorio vehículos | ≤10; ASC/DESC; VIN/placa/marca/modelo; filtro statusId | `GET /api/vehicles?workshopId=...&page=1&pageSize=10&sort=make&direction=ASC` |
| Scope | Acceso vía vehicle → customer → customer_workshop activo y WorkshopAccess | Ficha, creación, actualización y estatus fuera del taller: 404 |
| Precondición | Cliente debe permitir operaciones para registrar vehículo, también con estatus personalizado | Bloqueo transaccional y lectura de `allows_operations`; cliente no operativo: 409 |
| En servicio | EXISTS de órdenes RECEIVED/DIAGNOSIS/AWAITING_APPROVAL/IN_PROGRESS/READY | Campo derivado `inService`; nunca un status IN_SERVICE |
| Roles propios | CLIENT conserva consulta/registro propios; MECHANIC conserva consulta de vehículos de órdenes asignadas | `/api/self/vehicles`; CLIENT usa talleres activos de su propia asociación; sin administración ni cambio de propietario |
| Órdenes | Selector de vehículos paginado por taller; creación verifica scope y operatividad de cliente/vehículo | Cambio mínimo del alta: workshopId obligatorio; workflow de órdenes existente conservado |
| Seguridad | Sesiones, CSRF, CSP, invalidación de credenciales/roles conservadas | Catálogos mutables ADMIN; módulos administrativos ADMIN/RECEPTIONIST |
| Auditoría | Cambios de estatus, altas/ediciones de vehículos y CRUD de ambos catálogos | Eventos transaccionales, sin secretos ni payload personal completo |

## Validaciones automotrices

| Campo | Captura / pegado / envío | Backend y persistencia |
|---|---|---|
| VIN | 17 caracteres; excluye I/O/Q, espacios internos y símbolos | Trim exterior y uppercase; regex `[A-HJ-NPR-Z0-9]{17}`; UNIQUE global; 409 duplicado |
| Placa | 3–20 letras/números; guiones entre grupos | Trim, uppercase y retiro de espacios; sin UNIQUE global artificial |
| Marca/modelo/versión/color | Unicode, números y separadores comerciales; sin controles ni etiquetas | NFC, trim, espacios colapsados, minúsculas; máximos 60/80/80/60 |
| Año | Input de texto numeric, maxlength 4; rechaza letras, signos, decimales y exponentes | Entero de cuatro dígitos, 1900…año actual+1; deserializador conserva y valida representación lexical |
| Kilometraje | Opcional; input numeric, maxlength 10; cero válido | Entero 0…2147483647; null si ausente; no negativos/decimales/NaN/infinito/exponentes |
| Catálogo | Code restringido durante captura y pegado; descripción sin controles; bandera operativa explícita | Code uppercase; system inmutable; version; code único; description legible NFC |
| Formularios | ValidatedInput compartido, errores por campo, loading, doble submit impedido | Backend vuelve a validar, incluidos formatos enviados omitiendo Vue |

## Migración y compatibilidad

| Decisión | Resultado |
|---|---|
| Flyway | V7__status_catalogs_and_vehicles.sql; V1–V6 intactas |
| Booleanos | true→ACTIVE; false→SUSPENDED; columnas active de customer/vehicle retiradas |
| Identificadores | Retira checks lowercase; convierte datos; agrega checks uppercase; índices únicos existentes preservados |
| Vehículos históricos | Preserva IDs, VIN existentes, asociaciones, órdenes e historial. No inventa VIN, color ni kilometraje faltantes; completa obligatorios al editar |
| Permisos runtime | `scripts/db-grants-p2.py` agrega CRUD solo a customer_status/vehicle_status. customer_contact continúa SELECT mediante triggers definer |
| API incompatible anterior | Estatus booleano pasa a statusId+version; vehículos administrativos pasan a contrato paginado; consulta propia pasa a /self/vehicles. Vue y órdenes actualizados juntos |
| Infraestructura | Misma MariaDB, sesiones y JDBC; sin JWT/JPA, servicios externos ni tablas vehicle_workshop |

## Verificación

| Verificación | Evidencia y resultado |
|---|---|
| Backend | 37 pruebas pasan, sin fallos/errores/omisiones: catálogos, transiciones, operatividad custom, vehículos, roles, CSRF, scope, autenticación y migración |
| Frontend | 14 pruebas Node pasan: reglas existentes, VIN/año/km, permisos/protección de status y estructura de modal |
| Entorno real | Respaldo privado antes de iniciar; Flyway V6→V7 aplicado, servicio activo en 8080; 4 clientes, 1 vehículo y 3 asociaciones conservados; login ADMIN, catálogos y directorios HTTP 200 |
| Migración | MigrationCompatibilityTest recorre V1–V5, V6 con datos e historial y V7; verifica uppercase, fuentes de verdad, unicidad, vínculos e historial |
| Backend build | Maven package exitoso (Java 21); ejecución final registrada tras la última revisión |
| Frontend build | vue-tsc + Vite exitosos sobre el código final |
| UI | Esquema temporal con permisos runtime: modal guarda/Escape/foco/pegado; suspensión/reactivación; catálogo custom y protección; vehículo alta/edición/status/duplicado y conservación de formulario; consola sin errores |
| Limitación de pruebas frontend | Pruebas Node de reglas y estructura; comportamiento real del modal se comprueba aparte en navegador |

## Operación y límites

| Tema | Instrucción / límite |
|---|---|
| Arranque | `./scripts/app-start.sh`; Flyway aplica V7 al iniciar. Tras migrar, `python scripts/db-grants-p2.py` aplica permisos runtime de catálogo |
| Integración aislada | `python scripts/test-customer-admin.py`; crea y retira esquemas/usuarios temporales, sin reset de DB real |
| UI aislada | Build frontend + Maven package; `python scripts/test-customer-admin.py --preview`; Ctrl-C retira esquema/usuarios/imágenes temporales |
| Frontend tests | `node --test tests/customer-validation.mjs tests/customer-presentation.mjs tests/vehicle-status-validation.mjs` |
| Diagramas | Seis HTML/SVG en `docs/diagrams`; regenerar con `python scripts/generate-diagrams.py` |
| Legacy | Consultas propias CLIENT/MECHANIC conservan límite anterior 500; paginación administrativa siempre server-side ≤10 |
| Datos previos incompletos | Se muestran como no registrados; formulario obliga completarlos al editar. No se fabrica información histórica |
| Hallazgo histórico | El único vehículo previo pertenece a un cliente sin asociación activa a taller. Se conserva, pero no aparece en el directorio del taller hasta que ADMIN asocie a su propietario |
| Herramientas | Flyway advierte que MariaDB 12.3 es más reciente que su rango probado; integración y migración local en MariaDB 12.3.2 pasan. No se cambió infraestructura |
| Fuera de alcance | REPUVE, situación legal, número de serie separado, inventario/pagos y refactor del workflow de órdenes |
| Historia UC-CV-02 | [Entrega previa](entrega-uc-cv-02.md) conserva evidencia de V6; este documento define el contrato actual V7 |
