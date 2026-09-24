# Prototipo listo para explorar

Abre **http://localhost:5173**. Entra con los botones **Administración**, **Mecánico** o **Cliente**, sin contraseña.

```sh
./scripts/prototype-start.sh
./scripts/prototype-stop.sh
```

No descargan nada. El prototipo usa las dependencias ya instaladas. También hay una compilación estática en `prototype/dist/` generada por `cd frontend && npm run build:demo`.

- Dashboard, órdenes, clientes, vehículos, equipo, inventario, cobros, reportes y bitácora.
- Puedes crear órdenes y probar los formularios. Los cambios se guardan en el navegador (`localStorage`).
- Datos de ejemplo ficticios. El modo demo **no tiene autenticación real ni conexión a MariaDB**; no usar información real.
- Restablecer ejemplos: Mi cuenta → Restablecer datos de ejemplo.
- El servidor solo escucha en loopback y no se inicia automáticamente al reiniciar la PC.
- Pruebas del prototipo: `node --experimental-strip-types tests/prototype.mjs` y `cd frontend && npm run build:demo`.
- V2 de base está escrita pero **no aplicada**. Sigue pendiente completar y probar Spring Boot, gestionar Flyway baseline V1 y conectar el frontend real. No lanzar el backend contra datos reales hasta concluir su revisión.

---

# TallerMeco

Monolito modular para un taller. Etapa actual: prototipo Vue funcional en modo local. MariaDB y esquema V1 probados. Código Spring Boot en desarrollo, todavía sin compilar ni conectar; descargas detenidas por petición del usuario.

## Stack decidido

- Java 21 LTS, Spring Boot, Maven, Spring Security, REST y Spring JDBC.
- Vue 3, TypeScript, Vite, Vue Router y Tailwind CSS.
- MariaDB nativo de esta PC; JDBC MariaDB. Flyway se integrará con el backend.
- Caddy para HTTPS cuando despleguemos. No necesitamos Docker para desarrollar la base local.

Interfaz prevista: fondos neutros, acentos azul/teal, tipografía legible, navegación por rol, tablas con filtros, formularios cortos y estados con texto además de color. Diseño móvil, teclado, foco visible y contraste WCAG AA como criterios de aceptación. Tailwind sustituye Bootstrap para controlar la estética; no añadimos ambos.

## Base local

Se usa `/usr/sbin/mariadbd` instalado en la PC, con datos aislados en `.local/mariadb`. No es el servicio global `mariadb.service`. Corre como servicio de usuario `tallermeco-db`, sin sudo, en `127.0.0.1:3306`. No se inicia automáticamente al reiniciar la PC.

```sh
./scripts/db-start.sh
./scripts/db-check.sh
./scripts/db-backup.sh
./scripts/db-stop.sh
```

Conexión para el backend:

- URL: `jdbc:mariadb://127.0.0.1:3306/tallermeco`
- Usuario: `taller_app`
- Contraseña: `DB_APP_PASSWORD` en `.env`.

Usuario de migraciones: `taller_owner`, contraseña `DB_OWNER_PASSWORD`. Las credenciales son aleatorias, `.env` tiene permisos restringidos y está excluido de Git. El backend no utilizará el usuario administrativo. Cambiar `.env` no cambia las claves del servidor: requiere una rotación explícita.

Acceso administrativo local mediante autenticación del usuario del sistema:

```sh
mariadb --no-defaults --socket="$PWD/.local/mariadb.sock" tallermeco
```

## Esquema

15 tablas en `backend/src/main/resources/db/migration/V1__core_schema.sql`. El arranque inicial se hizo con `python3 scripts/db-init.py`, que rehúsa modificar una base ya existente. Este script utiliza `MySQLdb`, disponible en esta PC mediante el paquete python-mysqlclient. Los permisos específicos de aplicación se configuran en ese mismo script.

V1 fue aplicada directamente en esta etapa, no por Flyway. Al integrar Spring Boot: validar el esquema, registrar explícitamente una baseline Flyway versión 1 en esta base y usar V2 en adelante. En bases nuevas Flyway ejecutará V1 normalmente y el aprovisionamiento asignará los permisos. No activar baseline automática indiscriminadamente. El backend usará Spring JDBC con consultas parametrizadas y transacciones.

Ver relaciones y reglas en [docs/modelo.md](docs/modelo.md).

- Roles iniciales ADMIN, MECHANIC, CLIENT. Permisos por operación se definirán en Spring Security.
- Una orden conserva el cliente al ingreso aunque cambie el propietario del vehículo.
- Movimientos de inventario conservan cantidad firmada, costo y precio históricos. Consumos/devoluciones ligados a una orden representan sus piezas utilizadas.
- Stock actualizado atómicamente por trigger, con bloqueo de fila y saldo no negativo. El usuario de aplicación no puede escribirlo directamente.
- Movimientos, pagos, auditoría e historial son append-only para la aplicación. No protege frente al administrador del servidor.
- Pagos positivos con tipo PAYMENT o REFUND; cobro y reparación tienen estados independientes. No se almacenan datos de tarjetas.
- Importes DECIMAL/NUMERIC; fechas DATETIME(6) en UTC. Moneda MXN por defecto.

Pendiente en backend: autenticación, CSRF, permisos por recurso, transiciones de estado, límites de devoluciones/reembolsos, cierre inmutable y emisión automática de auditoría. El esquema no sustituye estas reglas. También falta definir impuestos, descuentos y política de costeo antes del módulo financiero.

## Backups y operación

`./scripts/db-backup.sh` crea una copia SQL local con permisos restringidos, excluida de Git. Esta copia no está cifrada ni programada. Restaurar únicamente sobre una base de prueba vacía. Para restaurar en otro servidor hay que provisionar usuarios y revisar los definers de triggers; las cuentas no se incluyen en el dump de la base.

Antes de producción: backups cifrados externos programados, prueba periódica de restauración, secretos separados, HTTPS, MFA administrativo y pruebas de autorización. La carpeta de datos no es un backup.

## Próxima etapa

Crear Spring Boot y conectar con `taller_app`; integrar Flyway y construir identidad/acceso antes de exponer endpoints de negocio.
