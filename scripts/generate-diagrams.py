from pathlib import Path
from html import escape
root=Path(__file__).resolve().parents[1]/'docs/diagrams'
items={
 'architecture':('Arquitectura',[
 ('Interfaz Vue','View → Component → Facade/API','Clientes, talleres, vehículos y catálogos reutilizan módulos.'),
 ('Spring Security','Sesión + CSRF + roles','ADMIN/RECEPTIONIST; CSP e invalidación de credenciales/roles.'),
 ('Controller','Contratos HTTP','Recibe DTO; no contiene SQL.'),
 ('Service','Reglas + alcance + transacción','Valida, normaliza, autoriza taller y registra auditoría.'),
 ('Repository','Spring JDBC','Consultas ligadas, filtro por taller y paginación SQL.'),
 ('MariaDB / Flyway','V1–V7 + constraints','FKs, índices únicos y reserva de contactos por triggers.')],[
 ('Frontend','View → Component → Facade/API','frontend/src/modules/customers y workshops'),
 ('Backend','Controller → Service → Repository → MariaDB','backend/src/main/java/mx/tallermeco'),
 ('Aislamiento','El servicio verifica user_workshop; SQL exige customer_workshop activo','WorkshopAccess y CustomerRepository'),
 ('Paginación','Página 1; máximo 10; ASC/DESC; whitelist name/id','CustomerListQuery y CustomerPage'),
 ('Catálogos','ADMIN administra; recepción consulta','StatusService y StatusRepository independientes por catálogo'),
 ('Vehículos','Módulo especializado con VIN único, paginación y alcance','VehicleService y VehicleRepository'),
 ('Legacy','Conserva Store en módulos ajenos a este incremento','Sin refactor global')]),
 'authentication':('Autenticación y alcance',[
 ('CSRF inicial','GET /api/auth/csrf','El navegador obtiene el token de la sesión.'),
 ('Login','POST /api/auth/login','Credenciales con CSRF; identidad y roles vienen de DB.'),
 ('Sesión','JSESSIONID','Rotación al entrar; cookie HttpOnly y SameSite.'),
 ('Autorización','Spring Security + Actor','ADMIN/RECEPTIONIST para administración de clientes.'),
 ('Alcance del taller','WorkshopAccess','Recepción necesita user_workshop activo; ADMIN global.'),
 ('Recurso individual','SQL con asociación','ID, ficha, foto y mutaciones requieren contexto autorizado.')],[
 ('ADMIN','Clientes de todos los talleres; CRUD de talleres; asignación de recepción','Autoridad final en backend'),
 ('RECEPTIONIST','Solo clientes de talleres activos asignados','La revocación se verifica en cada petición'),
 ('MECHANIC / CLIENT','Sin administración de clientes/talleres','Conservan flujos legacy autorizados propios'),
 ('Credenciales / roles','Los cambios invalidan la sesión almacenada','Pruebas de autenticación'),
 ('Cuenta pública','Se crea CLIENT; queda pendiente de taller','ADMIN asigna desde cola paginada; sin alcance automático')]),
 'customer-registration':('Registro y administración de clientes',[
 ('Captura','Identidad + contacto + dirección','DD/MM/AAAA explícito; al menos un contacto válido.'),
 ('SEPOMEX / taller','CP de cinco dígitos + selector','Estado/municipio/colonia; taller obligatorio y disponible.'),
 ('Frontend','Captura, pegado y envío','Errores por campo; sin truncar contenido ambiguo.'),
 ('Backend','Autorizar + validar + normalizar','CURP/RFC mayúsculas; email minúsculas; nacimiento coherente.'),
 ('Transacción','Cliente + vínculo + auditoría','409 ante duplicados; rollback ante taller inválido.'),
 ('Ficha / directorio','Presentación legible','Capitalización, CURP/RFC mayúsculas, edad derivada; SQL ≤10.')],[
 ('Edición','Modal reutiliza CustomerForm; versión; 409 si obsoleta','Normaliza otra vez y respeta alcance'),
 ('Suspensión','status_id → ACTIVE/SUSPENDED; permite custom','allows_operations decide; cambio auditado e idempotente'),
 ('ASSOCIATE','Activa destino y conserva origen','Preserva los demás vínculos'),
 ('REASSIGN','Activa destino y desactiva el origen seleccionado','Preserva vínculos anteriores e historial'),
 ('Desactivar vínculo','No permite retirar el último vínculo utilizable','Reasignar antes de desactivar'),
 ('Imagen','PNG/JPEG real ≤15 MiB; UUID seguro; recodificación','Reemplazo tras commit y retirada del nuevo en rollback')]),
 'data-model':('Modelo de datos V7',[
 ('company → workshop','1:N','Empresa y talleres activos; razón social, RFC y banner.'),
 ('app_user → user_workshop','1:N','Asignación explícita de alcance a recepción.'),
 ('workshop → user_workshop','1:N','PK compuesta: user_id + workshop_id; active.'),
 ('customer ↔ workshop','N:M por customer_workshop','PK compuesta, estado del vínculo e integridad FK.'),
 ('customer → customer_contact','1:N','PK kind + value evita contactos compartidos entre clientes.'),
 ('customer → vehicle','1:N; acceso vía customer_workshop','VIN único; color, km, versión; órdenes derivan en servicio.')],[
 ('customer','Nombre/apellidos, CURP, RFC, nacimiento, tres teléfonos, emails y dirección','Edad derivada; versión y estado'),
 ('workshop','company_id, nombre, razón social, RFC, teléfono/email, dirección, banner','Versión, estado, FK empresa'),
 ('customer_workshop','customer_id + workshop_id','Estado independiente; no borrado para reasignar'),
 ('user_workshop','user_id + workshop_id','ADMIN administra acceso; recepción sin alcance global'),
 ('customer_contact','kind + value únicos; FK customer_id','Triggers definer; runtime SELECT únicamente'),
 ('V7','Migra V6 sin booleanos customer.active/vehicle.active','V1–V6 intactas; datos desconocidos NULL'),
 ('customer_status / vehicle_status','Catálogos independientes; FK status_id','ACTIVE/SUSPENDED protegidos; custom con allows_operations'),
 ('Unicidad','CURP/RFC/celular, nombre+nacimiento, contactos cruzados','Taller: RFC y nombre+razón social; conserva empresa+nombre')])}

items['status-catalogs']=('Catálogos y transiciones de estatus',[
 ('Catálogo separado','customer_status / vehicle_status','ACTIVE y SUSPENDED sembrados; FK por entidad.'),
 ('Administración','Solo ADMIN','Custom: crear/editar/borrar sin uso. Recepción consulta.'),
 ('Protección','system + allows_operations','Code y semántica protegidos por servicio y triggers.'),
 ('Transición','Scope + bloqueo + version','Cliente/vehículo pertenece al taller autorizado.'),
 ('Sin cambio','status_id igual al actual','No incrementa versión ni duplica eventos.'),
 ('Auditoría','Anterior + nuevo + actor + taller','CUSTOMER_STATUS_CHANGED / VEHICLE_STATUS_CHANGED.')],[
 ('Fuente de verdad','status_id; sin active paralelo','Condición operativa derivada de allows_operations'),
 ('Eliminar custom','409 si está en uso; FK vigente','Version y system también verificados'),
 ('Estado del sistema','Descripción editable; code y comportamiento inmutables','ACTIVE operativo; SUSPENDED bloquea nuevas operaciones'),
 ('En servicio','EXISTS de órdenes abiertas','No existe IN_SERVICE en vehicle_status')])
items['vehicle-registration']=('Registro y administración de vehículos',[
 ('Taller y cliente','Selector paginado de clientes','CLIENT registra únicamente para su propia ficha.'),
 ('Precondición','Cliente con allows_operations=true','Asociación activa al taller; backend bloquea y valida.'),
 ('Datos automotrices','VIN 17; placa; marca/modelo/versión','Año 1900–siguiente; color; kilometraje entero opcional.'),
 ('Canonicalización','VIN y placa mayúsculas','Datos comerciales normalizados; captura/pegado validado.'),
 ('Persistencia','JDBC + VIN único + FK status_id','409 duplicado/version; auditoría en transacción.'),
 ('Directorio y modal','Máximo 10; ASC/DESC; búsqueda/status','Ficha, edición y transición; en servicio derivado.')],[
 ('Scope','vehicle → customer → customer_workshop','ADMIN/RECEPTIONIST; require WorkshopAccess'),
 ('Datos históricos','Campos desconocidos NULL','Edición completa campos obligatorios; no inventa VIN'),
 ('Roles propios','CLIENT consulta propios y registra; MECHANIC consulta asignados','Sin nuevas capacidades administrativas'),
 ('Fuera de alcance','Sin REPUVE ni situación legal ficticia','Sin número de serie separado ni vehicle_workshop')])

def diagram(title,nodes):
 width=1120;height=510
 parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-labelledby="title desc">',f'<title id="title">TallerMeco — {escape(title)}</title><desc id="desc">Diagrama vigente V7. Las tablas del documento explican cada elemento y sus reglas.</desc>', '<rect width="100%" height="100%" fill="#f6f8fa"/><defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0 0L10 5L0 10" fill="#526b61"/></marker></defs>',f'<text x="30" y="42" font-family="Arial,sans-serif" font-size="25" font-weight="bold" fill="#18372c">TallerMeco · {escape(title)}</text><text x="30" y="70" font-family="Arial,sans-serif" font-size="14" fill="#526b61">Código vigente · Flyway V7 · 8 de octubre de 2026</text>']
 # Reading order is left to right on each row. Sequence connectors are explicit only for flows.
 flow=title!='Modelo de datos V7'
 for i,(name,subtitle,detail) in enumerate(nodes):
  x=30+(i%3)*365;y=110+(i//3)*190
  if flow and i%3<2:
   parts.append(f'<path d="M{x+325} {y+64}H{x+350}" stroke="#526b61" stroke-width="2" fill="none" marker-end="url(#arrow)"/>')
  parts.append(f'<rect x="{x}" y="{y}" width="325" height="128" rx="10" fill="white" stroke="#9bad9e"/><text x="{x+16}" y="{y+27}" font-family="Arial,sans-serif" font-size="16" font-weight="bold" fill="#18372c">{i+1}. {escape(name)}</text>')
  import textwrap
  for j,line in enumerate(textwrap.wrap(subtitle,38)):
   parts.append(f'<text x="{x+16}" y="{y+53+j*19}" font-family="Arial,sans-serif" font-size="13" fill="#31463d">{escape(line)}</text>')
  for j,line in enumerate(textwrap.wrap(detail,43)):
   parts.append(f'<text x="{x+16}" y="{y+85+j*17}" font-family="Arial,sans-serif" font-size="12" fill="#526b61">{escape(line)}</text>')
 if flow:
  parts.append('<path d="M922 238V272H192V300" stroke="#526b61" stroke-width="2" fill="none" marker-end="url(#arrow)"/>')
 parts.append('<text x="30" y="483" font-family="Arial,sans-serif" font-size="13" fill="#526b61">Fuente: código, contratos y migraciones del repositorio. Reglas detalladas en las tablas HTML/Markdown.</text></svg>')
 return ''.join(parts)

style='''body{margin:0;background:#f6f8fa;color:#213c31;font:16px/1.55 system-ui,sans-serif}main{max-width:1180px;margin:auto;padding:28px}h1{font-size:30px}nav a{margin-right:18px;color:#21543e}.diagram{overflow:auto;background:white;border:1px solid #ccd7ce;border-radius:8px;margin:24px 0}.diagram svg{display:block;max-width:100%;height:auto}table{width:100%;border-collapse:collapse;background:white;margin:20px 0}caption{text-align:left;font-weight:700;padding:8px 0}th,td{border:1px solid #ccd7ce;padding:12px;text-align:left;vertical-align:top}th{background:#eaf0e7}code{overflow-wrap:anywhere}footer{margin:24px 0;font-size:14px}@media(max-width:650px){main{padding:12px}.table-wrap{overflow:auto}th,td{min-width:150px;padding:9px}}@media print{nav{display:none}body{background:white}main{padding:0}.diagram svg{width:100%}}'''
for slug,(title,nodes,rows) in items.items():
 directory=root/slug;directory.mkdir(parents=True,exist_ok=True);svg=diagram(title,nodes)
 (directory/f'{slug}.svg').write_text(svg)
 bodyrows=''.join('<tr>'+''.join(f'<td>{escape(cell)}</td>' for cell in row)+'</tr>' for row in rows)
 links=' '.join(f'<a href="../{key}/{key}.html">{escape(val[0])}</a>' for key,val in items.items())
 (directory/f'{slug}.html').write_text(f'''<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TallerMeco — {escape(title)}</title><style>{style}</style></head><body><main><nav><a href="../index.html">Índice</a>{links}</nav><h1>{escape(title)}</h1><p>Estado del código al 8 de octubre de 2026. HTML autónomo, sin dependencias externas. SVG y tablas incluidos en el repositorio.</p><div class="diagram">{svg}</div><div class="table-wrap"><table><caption>Elementos y reglas verificables</caption><thead><tr><th>Elemento</th><th>Regla / función</th><th>Detalle</th></tr></thead><tbody>{bodyrows}</tbody></table></div><footer><a href="{slug}.svg">SVG para previsualización en GitHub</a> · <a href="../../entrega-uc-cv-03.md">Contratos y verificación</a></footer></main></body></html>''')
indexrows=''.join(f'<tr><td>{escape(value[0])}</td><td><a href="{key}/{key}.html">Abrir HTML</a></td><td><a href="{key}/{key}.svg">Ver SVG</a></td></tr>' for key,value in items.items())
(root/'index.html').write_text(f'<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Diagramas TallerMeco</title><style>{style}</style></head><body><main><h1>Diagramas TallerMeco · V7</h1><p>HTML autónomos para abrir localmente; SVG para previsualizar en GitHub. Documentación vigente en tablas.</p><table><thead><tr><th>Diagrama</th><th>HTML</th><th>SVG</th></tr></thead><tbody>{indexrows}</tbody></table></main></body></html>')
print('Updated six HTML/SVG diagrams and HTML index.')
