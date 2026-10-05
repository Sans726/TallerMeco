# TALLERMECO — P2-02

Trabaja únicamente en:

`/home/sun/Projects/TallerMeco/`

## OBJETIVO

Preparar la base de datos definitiva necesaria para el módulo Registro de Clientes.

Esta tarea es únicamente de modelo de datos y migración.

No implementes todavía frontend, REST ni Repository.

## CONTEXTO

Ya existe:

- Spring Boot
- MariaDB
- Flyway
- JdbcTemplate
- autenticación real
- roles ADMIN, RECEPTIONIST, MECHANIC y CLIENT

Las migraciones V1, V2 y V3 NO deben modificarse.

## MODELO MULTITALLER

Preparar:

```text
Company 1:N Workshop

Customer N:M Workshop
```

Utilizar una relación intermedia equivalente a:

```text
CustomerWorkshop
```

Actualmente solamente existirá un taller operativo, pero el modelo debe soportar varios talleres de la misma empresa en el futuro.

Un cliente que visite otra sucursal NO debe convertirse en otro Customer.

## DATOS DEL CLIENTE

El modelo Customer debe soportar:

- fullName
- alias
- alternativeContactName
- birthDate
- personalPhone
- workPhone
- personalEmail
- workEmail opcional
- photoReference
- street
- neighborhood
- municipality
- state
- postalCode

NO almacenar edad.

La edad se calculará posteriormente mediante `birthDate`.

## DUPLICADOS

Preparar restricciones razonables para evitar duplicados inequívocos.

Normalizar donde corresponda:

- email personal
- email laboral
- teléfono personal
- teléfono laboral

No utilizar únicamente el nombre como restricción UNIQUE.

La detección avanzada:

```text
email
teléfono
nombre + fecha nacimiento
```

se implementará en CustomerService posteriormente.

## BASE VACÍA

No insertar:

- clientes
- vehículos
- órdenes
- talleres demostrativos
- empresas demostrativas

El sistema debe poder desplegarse sin datos de negocio ficticios.

Los roles internos y bootstrap administrativo no cuentan como datos demo.

## FLYWAY

Crear una nueva migración posterior a V3.

No:

- modificar V1
- modificar V2
- modificar V3
- añadir dependencias
- usar JPA/Hibernate
- navegar por Internet

## VERIFICACIÓN

Verifica solamente:

1. migración completa desde base vacía;
2. Company creada;
3. Workshop creado;
4. Customer actualizado correctamente;
5. relación Customer-Workshop creada;
6. claves foráneas;
7. índices/restricciones;
8. ausencia de datos ficticios;
9. backend compila;
10. `git diff --check`.

## ENTREGA

Responde únicamente:

### CHANGED
Archivos modificados.

### DATABASE
Tablas, columnas, índices y relaciones.

### IMPLEMENTED
Qué quedó funcionando.

### TESTS
Pruebas ejecutadas y resultado.

### DIAGRAM FACTS
Resumen corto de entidades y relaciones implementadas, para generar posteriormente los diagramas del proyecto.

### ISSUES
Solo problemas que requieran decisión.

No continúes con P2-03.