# TallerMeco — Fase 2

> Antecedente de diseño. El estado vigente V6, los contratos y las verificaciones están en [Entrega UC-CV-02](entrega-uc-cv-02.md) y [Administración de clientes y talleres](administracion-clientes-talleres.md).


> **Documento de alcance e historial.** Las secciones de arquitectura, autenticación y clientes describen los objetivos y criterios definidos para esta fase. Para el avance vigente, usa el [estado del proyecto](estado-del-proyecto.md).

## Avance documentado

| Entregable de Fase 2 | Estado actual | Evidencia |
|---|---|---|
| Autenticación, sesiones y roles | ✅ Implementado en código | `backend/src/main/java/mx/tallermeco/config/SecurityConfig.java` y módulo `identity` |
| Alta, consulta, edición y duplicados de clientes | ✅ Implementado en código | Módulo `backend/src/main/java/mx/tallermeco/customer/` |
| Repository de clientes y facade del frontend | ✅ Implementado en código | `CustomerRepository.java` y `frontend/src/modules/customers/facade/` |
| Fotografía de clientes y perfil | ✅ Implementado en código | Servicios de foto de clientes y cuenta |
| Configuración de empresa/taller | 🟡 API disponible; falta interfaz inicial | `POST /api/workshops/setup` |
| Diagramas técnicos | ✅ Disponibles | SVG en `docs/diagrams/` |
| Pruebas | 🟡 Pruebas presentes; consultar fecha de ejecución | `backend/src/test/`, `tests/` y registro de revisión visual al final de este documento |

## 1. Objetivo de la fase

La Fase 2 tiene como objetivo convertir el prototipo actual de TallerMeco en una aplicación funcional con autenticación real, persistencia en base de datos, control de roles, servicios RESTful, arquitectura modular y los patrones de diseño solicitados.

### Tecnologías y arquitectura requeridas

| Elemento | Implementación |
|---|---|
| Backend | Spring Boot |
| Frontend | VueJS 3 + TypeScript |
| Comunicación | Servicios RESTful mediante JSON |
| Base de datos | MariaDB |
| Seguridad | Spring Security + controles basados en OWASP |
| Patrón Backend | Repository |
| Patrón Frontend | Facade |
| Arquitectura | Backend y frontend modularizados |
| Sesiones | Sesiones reales administradas por Spring Security |

---

# 2. Estado actual del proyecto

La tabla de esta sección en la versión inicial era una línea base previa a la implementación. Para evitar mantener dos reportes distintos, el avance actual, los pendientes y la evidencia por módulo se mantienen en el [reporte de estado](estado-del-proyecto.md).

Las secciones siguientes conservan el alcance, las reglas de negocio y los criterios de diseño definidos para la Fase 2. Deben leerse como especificación de la fase y registro de decisiones; una frase en futuro o pendiente no necesariamente representa el estado actual del código.

# 3. Arquitectura objetivo

## Backend

```text
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
MariaDB
```

El patrón **Repository** será utilizado para separar completamente el acceso a datos de la lógica de negocio.

Los Controllers no deberán contener SQL.

Los Services no deberán ejecutar consultas SQL directamente.

Ejemplo:

```text
CustomerController
       │
       ▼
CustomerService
       │
       ▼
CustomerRepository
       │
       ▼
MariaDB
```

---

# 4. Arquitectura VueJS

En el frontend se utilizará el patrón **Facade**.

```text
Vue View
    │
    ▼
Facade
    │
    ├── API
    ├── Estado
    ├── Sesión
    └── Transformaciones
```

Las vistas no deberán conocer detalles internos de autenticación, llamadas HTTP o estructura del backend.

Ejemplo:

```text
CustomerCreateView.vue
          │
          ▼
    CustomerFacade
          │
          ▼
     CustomerApi
          │
          ▼
 Spring REST API
```

Ejemplo conceptual:

```typescript
customerFacade.createCustomer(form)
customerFacade.searchCustomers()
customerFacade.uploadPhoto(file)
```

La vista solamente interactúa con el Facade.

---

# 5. Autenticación y sesiones

El prototipo actual permite seleccionar manualmente el tipo de usuario.

Este mecanismo deberá eliminarse.

El flujo definitivo será:

| Paso | Acción |
|---|---|
| 1 | El usuario introduce correo y contraseña. |
| 2 | Spring Security valida las credenciales. |
| 3 | El servidor crea una sesión autenticada. |
| 4 | La sesión identifica al usuario. |
| 5 | El backend obtiene sus roles desde la base de datos. |
| 6 | Vue consulta `/api/auth/me`. |
| 7 | La interfaz habilita únicamente las funciones correspondientes al usuario. |

El usuario nunca podrá seleccionar manualmente su rol.

Los permisos deberán comprobarse siempre en el backend.

---

# 6. Roles iniciales

| Rol | Función |
|---|---|
| ADMIN | Administración completa del sistema. |
| RECEPTIONIST | Registro y administración básica de clientes y recepción. |
| MECHANIC | Gestión de trabajos asignados. |
| CLIENT | Consulta de sus vehículos y servicios. |

Para registrar nuevos clientes se requiere:

```text
ADMIN
o
RECEPTIONIST
```

---

# 7. Base de datos inicial

La base de datos deberá iniciar sin clientes, vehículos, órdenes ni información ficticia.

No se utilizarán registros de demostración en el sistema real.

El esquema y las migraciones podrán existir, pero las tablas de negocio deberán comenzar vacías.

El usuario administrador inicial deberá configurarse mediante un mecanismo seguro de inicialización y nunca mediante credenciales escritas directamente en el código.

---

# 8. Módulo: Registro de clientes

## Objetivo

Permitir que un usuario autorizado registre un nuevo cliente dentro del sistema.

## Actores autorizados

| Actor | Puede registrar clientes |
|---|---:|
| ADMIN | Sí |
| RECEPTIONIST | Sí |
| MECHANIC | No |
| CLIENT | No |

## Precondición

El usuario que realiza el registro deberá:

1. Estar autenticado.
2. Tener una sesión válida.
3. Tener rol `ADMIN` o `RECEPTIONIST`.

La validación deberá realizarse en el backend.

---

# 9. Información del cliente

| Campo | Obligatorio | Observaciones |
|---|---:|---|
| Nombre completo | Sí | Nombre legal o habitual del cliente. |
| Contacto alternativo | Sí | Nombre de una persona alternativa de contacto. |
| Fecha de nacimiento | Sí | Formato de fecha válido. |
| Edad | Calculada | Se calcula utilizando la fecha de nacimiento. |
| Teléfono personal | Sí | Formato validado. |
| Teléfono de trabajo | Sí | Formato validado. |
| Email personal | Sí | Formato de correo válido. |
| Email de trabajo | No | Campo opcional. |
| Fotografía | Sí | Archivo de imagen validado. |
| Calle | Sí | Parte de la dirección. |
| Colonia | Sí | Parte de la dirección. |
| Municipio | Sí | Parte de la dirección. |
| Estado | Sí | Parte de la dirección. |
| Código postal | Sí | Formato validado. |

La edad no deberá almacenarse como un valor fijo.

Se calculará utilizando:

```text
fecha actual - fecha de nacimiento
```

Esto evita que la edad almacenada quede desactualizada.

---

# 10. Flujo de datos — Registro de cliente

| Paso | Proceso | Resultado |
|---:|---|---|
| 1 | Usuario abre Registro de Cliente. | Se muestra formulario. |
| 2 | Sistema verifica sesión. | Usuario autenticado. |
| 3 | Sistema verifica rol. | ADMIN o RECEPTIONIST autorizado. |
| 4 | Usuario captura información. | Datos enviados al frontend. |
| 5 | Vue realiza validaciones básicas. | Errores visibles antes del envío. |
| 6 | CustomerFacade procesa la solicitud. | Datos preparados para API. |
| 7 | REST API recibe la solicitud. | DTO validado. |
| 8 | Backend normaliza los datos. | Email, teléfono y textos consistentes. |
| 9 | Sistema comprueba duplicados. | Se determina si el cliente ya existe. |
| 10 | Si existe, se rechaza el registro. | No se crea duplicado. |
| 11 | Si no existe, se registra mediante CustomerRepository. | Cliente persistido. |
| 12 | Se asocia el cliente con el taller actual. | Relación cliente-taller creada. |
| 13 | Se registra evento de auditoría. | Operación trazable. |
| 14 | API devuelve resultado. | Cliente registrado correctamente. |

---

# 11. Regla crítica: duplicados

## Regla

> Bajo ninguna circunstancia se deberá crear un segundo registro de la misma persona.

El sistema deberá revisar posibles coincidencias antes del `INSERT`.

Los principales identificadores para detectar duplicados serán:

| Dato | Uso |
|---|---|
| Email personal normalizado | Comparación exacta. |
| Teléfono personal normalizado | Comparación exacta. |
| Nombre completo + fecha de nacimiento | Comprobación adicional de identidad. |

También deberán existir restricciones en la base de datos para impedir duplicados incluso si dos solicitudes son procesadas simultáneamente.

Cuando se encuentre una coincidencia:

```text
409 Conflict
```

Ejemplo de respuesta:

```json
{
  "message": "Ya existe un cliente registrado con estos datos.",
  "existingCustomerId": 42
}
```

No deberá crearse un nuevo cliente.

---

# 12. Clientes y múltiples talleres

Aunque inicialmente TallerMeco utilizará un solo taller, la estructura deberá permitir que una empresa tenga varios talleres.

Ejemplo:

```text
Empresa TallerMeco
│
├── Taller Centro
├── Taller Norte
└── Taller Sur
```

Un cliente no deberá duplicarse al visitar otra sucursal.

Ejemplo:

```text
Cliente: Juan Pérez

Taller Centro ──┐
                ├── mismo cliente
Taller Norte ───┘
```

Modelo conceptual:

```text
Company
   │
   └── Workshop

Customer
   │
   └── CustomerWorkshop
            │
            └── Workshop
```

Relación:

```text
CUSTOMER
   │
   │ N:M
   ▼
CUSTOMER_WORKSHOP
   │
   ▼
WORKSHOP
   │
   ▼
COMPANY
```

Esto permitirá reutilizar el mismo cliente dentro de diferentes talleres pertenecientes a una misma empresa.

En esta fase solamente existirá una sucursal activa en la interfaz, pero la base quedará preparada para futuras sucursales.

---

# 13. Repository del módulo de clientes

Estructura propuesta:

```text
customer/
├── controller/
│   └── CustomerController.java
│
├── dto/
│   ├── CreateCustomerRequest.java
│   └── CustomerResponse.java
│
├── model/
│   └── Customer.java
│
├── repository/
│   └── CustomerRepository.java
│
└── service/
    └── CustomerService.java
```

Responsabilidades:

| Componente | Responsabilidad |
|---|---|
| CustomerController | Exponer REST API. |
| CustomerService | Reglas de negocio. |
| CustomerRepository | Consultas y persistencia. |
| DTO | Entrada y salida de datos. |
| Model | Representación del dominio. |

---

# 14. Facade VueJS

Estructura propuesta:

```text
src/modules/customers/
├── api/
│   └── customerApi.ts
│
├── facade/
│   └── customerFacade.ts
│
├── views/
│   ├── CustomerListView.vue
│   └── CustomerCreateView.vue
│
├── components/
│   └── CustomerForm.vue
│
└── types/
    └── customer.ts
```

Responsabilidades:

| Componente | Responsabilidad |
|---|---|
| CustomerCreateView | Mostrar la interfaz. |
| CustomerForm | Capturar datos. |
| CustomerFacade | Coordinar comportamiento del módulo. |
| customerApi | Comunicación REST. |
| customer.ts | Tipos TypeScript. |

La vista no deberá usar `fetch()` directamente.

---

# 15. Seguridad del módulo

| Control | Implementación |
|---|---|
| Autenticación | Spring Security |
| Autorización | ADMIN o RECEPTIONIST |
| CSRF | Token de Spring Security |
| Validación | Backend + frontend |
| SQL Injection | Consultas parametrizadas |
| Duplicados | Validación + restricciones de BD |
| Fotografías | Validación de tipo, extensión y tamaño |
| Errores | Sin revelar SQL o información interna |
| Contraseñas | Hash seguro |
| Sesiones | Cookies HttpOnly |
| Auditoría | Registrar creación del cliente |

La interfaz nunca sustituye las comprobaciones de seguridad del backend.

---

# 16. Resultado esperado de la Fase 2

Al finalizar esta parte deberá ser posible:

```text
Usuario real
    ↓
Login
    ↓
Sesión Spring Security
    ↓
ADMIN / RECEPTIONIST
    ↓
Registrar cliente
    ↓
Validar información
    ↓
Detectar duplicados
    ↓
Repository
    ↓
MariaDB
    ↓
Asociar cliente con taller
    ↓
Cliente registrado
```

El resultado final será un cliente único, persistido correctamente y asociado al taller correspondiente.


## Ajuste visual — 29 de septiembre de 2026

| Área | Cambio |
|---|---|
| Interfaz compartida | Tipografía, contraste, espaciado, tarjetas, tablas, botones y modales coherentes. |
| Clientes | Directorio con búsqueda y estados de carga/error; ficha agrupada; formulario por secciones con fotografía independiente. |
| Móvil | Formulario de una columna, directorio en tarjetas y menú lateral desplazable. |
| Interacción | Estados de carga, error y reintento en listados y reportes. Transiciones de página, ficha y notificaciones; animaciones desactivadas con `prefers-reduced-motion`. |
| Accesibilidad | Foco visible, acceso directo al contenido, menú móvil con recorrido de teclado y Escape, etiquetas y control de contraseña con iconos. |
| Verificación | Compilación Vue/TypeScript y Spring; revisión en navegador local de login, resumen, clientes, ficha, edición, preview local, órdenes, inventario, reportes y cuenta. Escape y retorno del foco en modales; recorrido Tab/Shift+Tab y cierre del menú móvil. `git diff --check` sin errores. |

La revisión visual inicial no certificó el alta completa de clientes; en ese momento no había talleres configurados. Se corrigieron los permisos locales de SELECT sobre workshop y customer_workshop para cargar esas vistas.


## Taller, recepción y perfil — 29 de septiembre de 2026

- Creado por petición del usuario **Taller del Titan Camara**, con empresa inicial del mismo nombre. No se agregaron clientes ni empleados ficticios a la base real.
- Recepción puede consultar, registrar y editar clientes y sus fotografías. Vue permite estas rutas y Equipo ofrece el rol Recepcionista. Las operaciones administrativas y los métodos de clientes no concedidos a recepción quedan reservados a ADMIN en Spring Security.
- V5 añade datos de cuenta independientes: nombre visible, teléfono, nacimiento, descripción y referencia de foto. `/api/auth/me` los devuelve; `PUT /api/account` solo actualiza el perfil propio. Correo y permisos no son editables desde ese formulario.
- `/api/account/photo`: POST multipart, GET privado y DELETE propios. JPG/PNG, máximo 15 MB/16 megapíxeles, recodificación PNG sin metadatos, almacenamiento fuera de los recursos públicos y auditoría. Reemplazo/eliminación limpia archivos después del commit; un rollback elimina la nueva foto.
- `scripts/db-grants-p2.py` aplica SELECT/INSERT a company, workshop y customer_workshop para la cuenta de ejecución local tras V4.
- Verificación: compilación offline y pruebas HTTP con MariaDB temporal (recepción crea/edita, duplicado 409, permisos 403, perfil persistente, foto privada, reemplazo/eliminación, imagen inválida y CSRF). Las bases temporales y sus archivos se eliminan al terminar.
