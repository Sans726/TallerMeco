package mx.tallermeco.customer.repository;

import mx.tallermeco.customer.dto.*;
import mx.tallermeco.customer.model.Customer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@Repository
public class CustomerRepository {
 private final JdbcTemplate jdbc;
 public CustomerRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<CustomerResponse> findById(long id){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE c.id=?",id);}
 public Optional<CustomerResponse> findScoped(long id,long workshopId){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id JOIN customer_workshop cw ON cw.customer_id=c.id WHERE c.id=? AND cw.workshop_id=? AND cw.active=true",id,workshopId);}
 public Optional<CustomerResponse> lockScoped(long id,long workshopId){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id JOIN customer_workshop cw ON cw.customer_id=c.id WHERE c.id=? AND cw.workshop_id=? AND cw.active=true FOR UPDATE",id,workshopId);}
 public Optional<CustomerResponse> lockUnassigned(long id){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE c.id=? AND NOT EXISTS(SELECT 1 FROM customer_workshop cw WHERE cw.customer_id=c.id AND cw.active=true) FOR UPDATE",id);}
 public Optional<CustomerResponse> byPhoto(String reference){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE c.photo_reference=?",reference);}
 public CustomerPage page(CustomerListQuery q){return page(q,false);}
 public CustomerPage unassigned(CustomerListQuery q){return page(q,true);}
 private CustomerPage page(CustomerListQuery q,boolean unassigned){
  String direction="DESC".equals(q.direction())?"DESC":"ASC";
  // sort column is selected from this whitelist, never interpolated from a request.
  String column=switch(q.sort()){case "name"->"c.full_name";case "id"->"c.id";default->throw new IllegalArgumentException("Orden inválido");};
  String filter=" FROM customer c JOIN customer_status cs ON cs.id=c.status_id JOIN customer_workshop cw ON cw.customer_id=c.id WHERE cw.workshop_id=? AND cw.active=true AND (?='' OR LOCATE(?,c.full_name)>0 OR LOCATE(?,COALESCE(c.personal_email,''))>0 OR LOCATE(?,COALESCE(c.personal_phone,''))>0 OR LOCATE(?,COALESCE(c.cell_phone,''))>0 OR LOCATE(?,COALESCE(c.work_phone,''))>0)";
  if(unassigned)filter=filter.replace(" JOIN customer_workshop cw ON cw.customer_id=c.id WHERE cw.workshop_id=? AND cw.active=true"," WHERE NOT EXISTS(SELECT 1 FROM customer_workshop cw WHERE cw.customer_id=c.id AND cw.active=true)");
  Object[] args=unassigned?new Object[]{q.query(),q.query(),q.query(),q.query(),q.query(),q.query()}:new Object[]{q.workshopId(),q.query(),q.query(),q.query(),q.query(),q.query(),q.query()};
  var filteredArgs=new ArrayList<>(Arrays.asList(args));if(q.statusId()!=null){filter+=" AND c.status_id=?";filteredArgs.add(q.statusId());}args=filteredArgs.toArray();
  long count=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,args));
  var pageArgs=new ArrayList<>(Arrays.asList(args));pageArgs.add(q.pageSize());pageArgs.add(((long)q.page()-1)*q.pageSize());
  var items=jdbc.query("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations"+filter+" ORDER BY "+column+" "+direction+",c.id "+direction+" LIMIT ? OFFSET ?",this::map,pageArgs.toArray());
  return new CustomerPage(items.stream().map(CustomerResponse::from).toList(),q.page(),q.pageSize(),count,(count+q.pageSize()-1)/q.pageSize());
 }
 public long create(CustomerData r){
  var keys=new GeneratedKeyHolder();
  jdbc.update(conn->{var s=conn.prepareStatement("INSERT INTO customer(full_name,alias,alternative_contact_name,birth_date,personal_phone,work_phone,personal_email,work_email,street,neighborhood,municipality,state,postal_code,given_name,paternal_surname,maternal_surname,curp,rfc,cell_phone) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);set(s,r);return s;},keys);
  long id=Objects.requireNonNull(keys.getKey()).longValue();return id;
 }
 private static void set(PreparedStatement s,CustomerData r)throws SQLException{
  Object[] values={r.fullName(),r.alias(),r.alternativeContactName(),r.birthDate(),r.personalPhone(),r.workPhone(),r.personalEmail(),r.workEmail(),r.street(),r.neighborhood(),r.municipality(),r.state(),r.postalCode(),r.givenName(),r.paternalSurname(),r.maternalSurname(),r.curp(),r.rfc(),r.cellPhone()};
  for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);
 }
 public void update(long id,CustomerData r){
  jdbc.update("UPDATE customer SET full_name=?,alias=?,alternative_contact_name=?,birth_date=?,personal_phone=?,work_phone=?,personal_email=?,work_email=?,street=?,neighborhood=?,municipality=?,state=?,postal_code=?,given_name=?,paternal_surname=?,maternal_surname=?,curp=?,rfc=?,cell_phone=?,phone=?,version=version+1 WHERE id=?",r.fullName(),r.alias(),r.alternativeContactName(),r.birthDate(),r.personalPhone(),r.workPhone(),r.personalEmail(),r.workEmail(),r.street(),r.neighborhood(),r.municipality(),r.state(),r.postalCode(),r.givenName(),r.paternalSurname(),r.maternalSurname(),r.curp(),r.rfc(),r.cellPhone(),r.personalPhone(),id);
 }
 public void status(long id,long status){jdbc.update("UPDATE customer SET status_id=?,version=version+1 WHERE id=?",status,id);}
 public void associateWithWorkshop(long id,long workshop){jdbc.update("INSERT INTO customer_workshop(customer_id,workshop_id) VALUES (?,?)",id,workshop);}
 public void associate(long id,long workshop){jdbc.update("INSERT INTO customer_workshop(customer_id,workshop_id,active) VALUES (?,?,true) ON DUPLICATE KEY UPDATE active=true",id,workshop);}
 public void associationActive(long id,long workshop,boolean active){jdbc.update("UPDATE customer_workshop SET active=? WHERE customer_id=? AND workshop_id=?",active,id,workshop);}
 public boolean isAssociatedWithWorkshop(long id,long workshop){return exists("SELECT 1 FROM customer_workshop WHERE customer_id=? AND workshop_id=? AND active=true",id,workshop);}
 public long activeAssociations(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM customer_workshop cw JOIN workshop w ON w.id=cw.workshop_id JOIN company co ON co.id=w.company_id WHERE cw.customer_id=? AND cw.active=true AND w.active=true AND co.active=true",Long.class,id);}
 public List<Map<String,Object>> scopedWorkshops(long id,long user,boolean admin){return jdbc.queryForList("SELECT w.id,w.company_id companyId,w.name,w.active,cw.active associationActive FROM workshop w JOIN customer_workshop cw ON cw.workshop_id=w.id WHERE cw.customer_id=? AND (? OR EXISTS(SELECT 1 FROM user_workshop uw WHERE uw.workshop_id=w.id AND uw.user_id=? AND uw.active=true)) ORDER BY w.name,w.id",id,admin,user);}
 public List<WorkshopResponse> findWorkshops(long id){return jdbc.query("SELECT w.id,w.company_id,w.name,w.active FROM workshop w JOIN customer_workshop cw ON cw.workshop_id=w.id WHERE cw.customer_id=? ORDER BY w.id",(rs,n)->new WorkshopResponse(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBoolean(4)),id);}
 public void updatePhotoReference(long id,String ref){jdbc.update("UPDATE customer SET photo_reference=?,version=version+1 WHERE id=?",ref,id);}
 public Optional<CustomerResponse> findByPersonalEmail(String email){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE personal_email_normalized=?",normalizeEmail(email));}
 public Optional<CustomerResponse> findByPersonalPhone(String phone){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE personal_phone_normalized=?",normalizePhone(phone));}
 public Optional<CustomerResponse> findByNameAndBirthDate(String name,LocalDate date){return one("SELECT c.*,cs.code status_code,cs.description status_description,cs.allows_operations FROM customer c JOIN customer_status cs ON cs.id=c.status_id WHERE full_name=? AND birth_date=?",name,date);}
 public boolean existsByPersonalEmail(String email){return findByPersonalEmail(email).isPresent();}
 public boolean existsByPersonalPhone(String phone){return findByPersonalPhone(phone).isPresent();}
 public boolean existsByNameAndBirthDate(String name,LocalDate date){return findByNameAndBirthDate(name,date).isPresent();}
 private Optional<CustomerResponse> one(String sql,Object...args){return jdbc.query(sql,this::map,args).stream().findFirst().map(CustomerResponse::from);}
 private boolean exists(String sql,Object...args){return !jdbc.queryForList(sql,args).isEmpty();}
 private Customer map(ResultSet rs,int row)throws SQLException{
  java.sql.Date date=rs.getDate("birth_date");
  return new Customer(rs.getLong("id"),(Long)rs.getObject("user_id"),rs.getString("full_name"),rs.getString("alias"),rs.getString("alternative_contact_name"),date==null?null:date.toLocalDate(),rs.getString("personal_phone"),rs.getString("work_phone"),rs.getString("personal_email"),rs.getString("work_email"),rs.getString("photo_reference"),rs.getString("street"),rs.getString("neighborhood"),rs.getString("municipality"),rs.getString("state"),rs.getString("postal_code"),rs.getLong("status_id"),rs.getString("status_code"),rs.getString("status_description"),rs.getBoolean("allows_operations"),rs.getLong("version"),rs.getString("given_name"),rs.getString("paternal_surname"),rs.getString("maternal_surname"),rs.getString("curp"),rs.getString("rfc"),rs.getString("cell_phone"));
 }
 public static String normalizeEmail(String v){return v==null||v.isBlank()?null:v.trim().toLowerCase(Locale.ROOT);}
 public static String normalizePhone(String v){if(v==null||v.isBlank())return null;String digits=v.replaceAll("[^0-9]","");return digits.length()==12&&digits.startsWith("52")?digits.substring(2):digits;}
}
