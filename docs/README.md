# Documentación de TallerMeco

Esta carpeta reúne el estado vigente del trabajo, las decisiones del modelo y el alcance funcional de las fases del proyecto.

## Guía de lectura

| Documento | Para qué sirve |
|---|---|
| [Entrega UC-CV-02 en tablas](entrega-uc-cv-02.md) | Funciones, archivos, pruebas ejecutadas, operación y límites comprobados. |
| [Administración de clientes y talleres](administracion-clientes-talleres.md) | Contratos, permisos, formatos, migración V6 y verificaciones del incremento. |
| [Estado del proyecto](estado-del-proyecto.md) | Reporte de avance por entregable, evidencia, pendientes y diagramas actuales. |
| [Direcciones por código postal](direcciones-codigo-postal.md) | Autollenado, catálogo SEPOMEX, API interna y mantenimiento. |
| [Modelo de datos](modelo.md) | Entidades, relaciones y reglas de integridad de inventario, pagos y reportes. |
| [Fase 2 — TallerMeco](FASE%202%20%E2%80%94%20TallerMeco.md) | Alcance, requisitos y decisiones de la fase de autenticación y clientes. Su lista de avance inicial se conserva como antecedente; el estado actual está en el reporte anterior. |
| [Modelo de clientes y talleres](Astra%20%E2%80%94%20P2-02%20Modelo%20de%20clientes%20y%20talleres.md) | Diseño del registro, duplicados, asociación multitaller y persistencia de clientes. |

## Diagramas técnicos

| Diagrama vigente V6 | HTML autónomo (abrir localmente) | SVG (vista en GitHub) |
|---|---|---|
| Arquitectura | [HTML](diagrams/architecture/architecture.html) | [SVG](diagrams/architecture/architecture.svg) |
| Autenticación y alcance | [HTML](diagrams/authentication/authentication.html) | [SVG](diagrams/authentication/authentication.svg) |
| Registro y administración de clientes | [HTML](diagrams/customer-registration/customer-registration.html) | [SVG](diagrams/customer-registration/customer-registration.svg) |
| Modelo de datos | [HTML](diagrams/data-model/data-model.html) | [SVG](diagrams/data-model/data-model.svg) |
| Índice de diagramas | [HTML](diagrams/index.html) | Cuatro archivos SVG versionados |

Los HTML incluyen el diagrama y sus tablas sin librerías, fuentes ni scripts externos. GitHub muestra su código; descargarlos o clonar el repo permite abrirlos directamente en el navegador. Los SVG se previsualizan en GitHub.

Para conocer qué está cerrado y qué falta, usa el [reporte de avance](estado-del-proyecto.md). Para instrucciones de arranque, revisa el [README principal](../README.md).
