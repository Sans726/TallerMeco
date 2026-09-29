# TallerMeco — Fase 2

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

| Elemento | Estado | Observación |
|---|---|---|
| Proyecto VueJS | ✅ Terminado | Existe frontend funcional. |
| Proyecto Spring Boot | ✅ Terminado | Backend base creado. |
| MariaDB | 🟡 Parcial | Existe estructura previa, pero falta consolidar la integración definitiva. |
| Servicios REST | 🟡 Parcial | Existen endpoints, pero requieren reorganización modular. |
| Spring Security | 🟡 Parcial | Existe configuración inicial, pero el flujo completo de autenticación debe terminarse y probarse. |
| Login real | ❌ Pendiente | El prototipo permite seleccionar manualmente un usuario/rol. |
| Sesiones reales | ❌ Pendiente | La identidad del usuario debe provenir de una sesión autenticada. |
| Roles reales | ❌ Pendiente | El usuario no debe seleccionar manualmente su rol. |
| Rol ADMIN | 🟡 Parcial | Está contemplado, pero debe integrarse con autenticación real. |
| Rol RECEPTIONIST | ❌ Pendiente | Debe agregarse. |
| Registro real de clientes | ❌ Pendiente | Se implementará en esta fase. |
| Repository Pattern | ❌ Pendiente | Actualmente existe acceso SQL mediante `Store`. |
| Facade Pattern en VueJS | ❌ Pendiente | Las vistas consumen directamente funciones generales de API. |
| Backend modular | 🟡 Parcial | Existen dominios, pero falta separar Controller, Service, Repository y DTO. |
| Frontend modular | 🟡 Parcial | Existen views y componentes, pero falta separar módulos y Facades. |
| Validación de duplicados | ❌ Pendiente | Debe impedirse cualquier doble registro de cliente. |
| Asociación cliente-taller | ❌ Pendiente | Debe prepararse para múltiples talleres de una empresa. |
| Fotografía del cliente | ❌ Pendiente | Debe implementarse carga y almacenamiento seguro. |
| OWASP | 🟡 Parcial | Ya existen controles de seguridad, pero deben formalizarse y probarse. |
| Datos demostrativos | ⚠️ Temporal | Deben eliminarse del funcionamiento real. |
| Base de datos limpia | ❌ Pendiente | El sistema final no deberá depender de datos precargados de demostración. |

---

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