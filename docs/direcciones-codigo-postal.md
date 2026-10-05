# Direcciones por código postal

> **Entrega:** autollenado en alta y edición de clientes. La consulta se resuelve dentro de TallerMeco, sin claves, cuotas ni conexión a internet durante la captura.

## Experiencia de captura

| Paso | Resultado |
|---|---|
| Escribir cinco dígitos, incluidos ceros iniciales | Tras 300 ms sin cambios, el formulario consulta el catálogo. |
| Código encontrado | Completa estado y municipio cuando hay una única opción. |
| Una colonia o asentamiento | Completa también la colonia. |
| Varias colonias o municipios | Muestra todas las opciones; al elegir una colonia completa su estado y municipio correspondientes. No adivina una opción ambigua. |
| Abrir un cliente existente | Conserva los datos guardados y ofrece las opciones del código postal. |
| Código ausente o fallo de consulta | Informa el problema y permite continuar la captura manual. |
| Corregir datos | Estado, municipio y calle son editables; «Capturar otra colonia manualmente» permite ingresar una colonia distinta. |

**Calle, número exterior e interior se capturan manualmente:** un código postal no identifica un domicilio individual. El catálogo contiene también barrios, pueblos, ejidos y otros asentamientos.

El componente ignora respuestas de códigos anteriores y respeta cambios manuales hechos mientras espera la consulta. No modifica registros existentes en la base de datos por sí solo; los datos se persisten al guardar el formulario. Se conserva la validación postal existente para compatibilidad; solamente los códigos mexicanos de cinco dígitos activan la consulta.

## Método elegido

Se evaluaron servicios externos y un catálogo local:

| Alternativa | Observación | Decisión |
|---|---|---|
| [COPOMEX](https://api.copomex.com/documentacion/inicio) | API con token y consumo por créditos; el token de pruebas devuelve datos aleatorios. | No usar datos de prueba para domicilios reales. |
| [PostalKit](https://api.postalkit.mx/) | API con token y cuota de consultas. | Requiere cuenta y seguimiento de consumo. |
| [SEPOMEX de IcaliaLabs](https://github.com/IcaliaLabs/sepomex) | Exportación normalizada del catálogo oficial; su instancia pública no garantiza disponibilidad. | Integrar una copia local de los datos. |

TallerMeco usa la exportación normalizada de **IcaliaLabs/sepomex**, basada en el [catálogo de Correos de México](https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx). Se importó desde el commit `3899c9e68c5ab46a9a789911d298833254a88b26`, cuya actualización de catálogo está fechada el **23 de julio de 2026**; incorporación a TallerMeco: **5 de octubre de 2026**. Esa fecha corresponde al commit de la fuente, no a una certificación de vigencia de cada asentamiento.

La copia contiene **31,878 códigos postales**, **157,849 combinaciones únicas de estado, municipio y asentamiento**, y **32 estados**. Se eliminaron duplicados que no aportan opciones distintas al formulario. La procedencia y huella SHA-256 están registradas en [source.json](../backend/src/main/resources/postal/source.json); se conserva la [licencia MIT del proyecto fuente](../backend/src/main/resources/postal/LICENSE-SEPOMEX.txt).

## API interna

```http
GET /api/addresses/postal-codes/09810
```

Requiere sesión de **ADMIN o RECEPTIONIST**. No envía información del cliente a terceros y no necesita cambios de base de datos ni variables de entorno.

```json
{
  "postalCode": "09810",
  "settlements": [
    { "state": "Ciudad de México", "municipality": "Iztapalapa", "neighborhood": "Granjas Esmeralda" },
    { "state": "Ciudad de México", "municipality": "Iztapalapa", "neighborhood": "Los Cipreses" },
    { "state": "Ciudad de México", "municipality": "Iztapalapa", "neighborhood": "Minerva" },
    { "state": "Ciudad de México", "municipality": "Iztapalapa", "neighborhood": "Progreso del Sur" }
  ]
}
```

| Respuesta | Significado |
|---|---|
| 200 | Código encontrado; todas las opciones de asentamiento. |
| 400 | El código no contiene exactamente cinco dígitos. |
| 404 | No existe en la copia local del catálogo. |
| 401 / 403 | Falta una sesión válida o el rol no está autorizado. |

El servidor carga el archivo comprimido una sola vez al iniciar y mantiene un índice inmutable en memoria. Un recurso ausente, mal formado o incompleto impide el inicio para evitar presentar un catálogo parcial como válido.

## Mantenimiento

1. Obtener una nueva exportación normalizada `lib/sepomex_db.csv` del proyecto fuente, fijando el commit utilizado.
2. Ejecutar desde la raíz del repositorio:

   ```sh
   node scripts/import-postal-catalog.mjs /ruta/sepomex_db.csv
   ```

3. Actualizar `source.json` con commit, fecha, huella SHA-256 y conteos impresos por el importador; registrar la actualización en este documento.
4. Compilar y reiniciar TallerMeco para cargar la nueva copia.

El importador exige las 15 columnas del archivo normalizado, cinco dígitos por código y presencia de los 32 estados antes de reemplazar el recurso. No hay actualización automática ni descarga de datos al iniciar. Conviene revisar la fuente periódicamente y antes de cada entrega académica.

## Alcance de revisión de esta entrega

Compilación completada en Camerabox el 5 de octubre de 2026: Vue/TypeScript y Vite sin errores; backend Java 21 con Maven `compile` exitoso. Queda pendiente revisar visualmente el recorrido en navegador con una sesión de recepción. No se declara una ejecución de suites automáticas en esta actualización.
