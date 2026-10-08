# Entrega UC-CV-02 — clientes y talleres

> Documento histórico de V6. Para contratos actuales de estatus, canonicalización y vehículos, consultar [UC-CV-03](entrega-uc-cv-03.md).
## Funciones y decisiones

| Área | Implementación | Fuente |
|---|---|---|
| Arquitectura | View → Component → Facade/API; Controller → Service → Repository → MariaDB | Módulos customers/workshops; Spring JDBC |
| Registro | Nombres/apellidos, CURP/RFC, nacimiento, edad derivada, tres teléfonos, emails y dirección | CustomerData, CustomerForm, InputRules |
| Administración | Ficha, edición con versión, suspensión y reactivación sin borrado | CustomerService, CustomerListView |
| Asociaciones | ASSOCIATE conserva origen; REASSIGN desactiva solo el origen seleccionado; estados de vínculo e historial | CustomerAssociations, customer_workshop |
| Aislamiento | Contexto workshopId obligatorio; recepción solo talleres activos asignados; SQL filtrado incluso por ID/foto | WorkshopAccess, CustomerRepository |
| Autorización | ADMIN/RECEPTIONIST; MECHANIC/CLIENT sin administración de clientes | SecurityConfig y servicios |
| Talleres | ADMIN crea/edita talleres completos, banner y alcance de recepción | workshop/management, frontend/modules/workshops |
| Paginación | Server-side, página 1, tamaño ≤10, ASC/DESC; whitelist name/id | CustomerListQuery, CustomerPage |
| SEPOMEX | CP estricto de cinco dígitos; ubicación/colonias completadas localmente | AddressFields, PostalCatalog |
| Duplicados | Normalización backend + índices/constraints + reservas de contactos cruzados; 409 con rollback | V6, customer_contact, triggers |
| Imagen | PNG/JPEG real ≤15 MiB; decode/reencode, límites de píxeles, UUID/ruta segura; reemplazo tras commit | SafeImageStorage |
| Auditoría | Actor, operación y referencias; sin CURP/RFC/contactos/imágenes en payload | Audit, servicios |
| Presentación | Nombres/dirección legibles, CURP/RFC mayúsculas, nacimiento DD/MM/AAAA, teléfonos separados | shared/presentation.ts |
| Captura de fecha | DD/MM/AAAA explícito; API ISO; 12/05/2006 da 20 años al 2026-10-05 | ValidatedInput, validation.ts |
| Cuenta pública | Conserva CLIENT y flujo previo; ficha pendiente de asociación por ADMIN | IdentityService y cola unassigned |

## Validación por campo

| Campo | Regla en frontend y backend |
|---|---|
| Nombres/apellidos | Letras Unicode, acentos, ñ, apóstrofes, guiones y espacios internos; no números/comas/controles. |
| CURP | 18 caracteres, estructura mexicana, entidad y fecha real/coherente; error específico cuando nacimiento no coincide. |
| RFC | Estructura de 12/13 caracteres y fecha válida; normalización antes de persistir/comparar. |
| Email | Formato razonable, límites, sin espacios internos; minúsculas para comparación. |
| Teléfonos | Formatos mexicanos definidos; diez dígitos canónicos, campos independientes. |
| Nacimiento | Fecha real, no futura, máximo 130 años; edad calculada, no escrita. |
| CP | Exactamente 5 dígitos ASCII; sin espacios, letras, comas, signos o notación científica; ceros conservados. |
| Dirección | Campos obligatorios, caracteres y longitudes acotados; SEPOMEX reutilizado. |
| Taller | Nombre/razón social/RFC/contacto/dirección completos; duplicados rechazados. |
| Imagen | Validación real de contenido; extensión/Content-Type no son autoridad. |
| Captura y pegado | Rechazo explícito de contenido incompatible; validación reactiva y antes de enviar; backend vuelve a validar. |

## Archivos y migraciones

| Entregable | Ruta |
|---|---|
| Migración nueva | [V6](../backend/src/main/resources/db/migration/V6__customer_administration.sql) |
| Clientes backend | [customer](../backend/src/main/java/mx/tallermeco/customer/) |
| Talleres backend | [workshop/management](../backend/src/main/java/mx/tallermeco/workshop/management/) |
| Reglas / imagen | [InputRules](../backend/src/main/java/mx/tallermeco/shared/InputRules.java), [SafeImageStorage](../backend/src/main/java/mx/tallermeco/shared/SafeImageStorage.java) |
| Frontend | [customers](../frontend/src/modules/customers/), [workshops](../frontend/src/modules/workshops/), [shared](../frontend/src/modules/shared/) |
| Pruebas aisladas | [test-customer-admin.py](../scripts/test-customer-admin.py), [customer-validation.mjs](../tests/customer-validation.mjs) |
| Diagramas | [Índice HTML/SVG](README.md#diagramas-técnicos) |

## Evidencia ejecutada el 5 de octubre de 2026

| Verificación | Resultado / alcance |
|---|---|
| `python scripts/test-customer-admin.py` | 30 backend aprobadas; cero fallos/errores/omitidas; esquemas temporales eliminados. |
| `node --test tests/customer-validation.mjs tests/auth-frontend.mjs` | 14 frontend aprobadas tras correcciones de fecha y presentación. |
| `npm --prefix frontend run build` | Vue/TypeScript/Vite correcto. |
| Maven `clean package -DskipTests` | Backend Java 21 empaquetado; pruebas ejecutadas por separado. |
| Maven `process-resources` | Últimas correcciones frontend publicadas al servicio sin reiniciar sesiones. |
| V5 → V6 | Compatibilidad con registros y relaciones existentes comprobada en esquema temporal. |
| Base real | V6 aplicada con respaldo previo; registros y asociaciones conservados al aplicar. |
| HTTP | Roles, CSRF, invalidación de sesiones, ID/foto/mutaciones fuera de alcance, versiones, duplicados concurrentes, talleres e imágenes. |
| Navegador | Alta/edición/estado/asociación/reasignación, SEPOMEX, páginas 10/10/5, ASC/DESC, error de duplicado, escritura/pegado y ficha legible. |
| Regresión de fecha | Antes/al/después del cumpleaños; DD/MM/AAAA; CURP coincidente y fecha invertida; 20 años para nacimiento 2006-05-12. |
| Integración legacy acotada | Alta de vehículo con selector de cliente por taller en entorno temporal. |
| Servicio local | `tallermeco-app-real.service`; http://127.0.0.1:8080/#/login; CSRF/login/listados/alcance comprobados. |

## Operación y límites

| Tema | Acción / límite |
|---|---|
| Recepción | ADMIN asigna talleres desde Talleres; no se otorga alcance global automáticamente. |
| Datos históricos | Completar identidad/dirección desconocidas al editar; no se inventaron apellidos. |
| Contactos históricos conflictivos | Resolver con criterio humano antes de migrar; no fusionar ni resetear DB para sortear constraints. |
| IP del contenedor | `python scripts/db-sync-host.py` copia permisos existentes a su IP privada actual, sin comodín ni exposición de hashes. |
| Permisos V6 | `python scripts/db-grants-p2.py`; runtime SELECT únicamente en reserva de contactos. |
| Arranque | app-start inicia DB fuera de la unidad Java; db-start comprueba socket interno y puerto publicado. |
| CURP/RFC | Validación sintáctica/fechas; sin consulta oficial RENAPO/SAT. |
| Teléfonos | Sin comprobación de titularidad/disponibilidad. |
| Homónimos | Sin fecha/identificadores y con contactos diferentes no se presume misma persona. |
| Módulos legacy | No se repitió la verificación integral de órdenes, inventario, pagos ni SMTP. |
| Diagramas HTML | Abrir localmente; GitHub muestra código HTML y previsualiza los SVG enlazados. |
