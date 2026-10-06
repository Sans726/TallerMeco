import assert from 'node:assert/strict'
import test from 'node:test'
import {readFile} from 'node:fs/promises'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../frontend/package.json',import.meta.url)),ts=require('typescript')
const source=await readFile(new URL('../frontend/src/modules/shared/validation.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText
const v=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
await test('postal validation and input/paste prefixes reject incompatible formats',()=>{
 for(const raw of ['0100','010000','01,000','0100A','01000,','12e3','-0100',' 01000','01000 '])assert.ok(v.validate('postal',raw,true),raw)
 assert.equal(v.validate('postal','01000',true),'')
 for(const raw of ['01,000','0100A','12e3','-0100','010000',' 01000'])assert.equal(v.acceptsInput('postal',raw),false,raw)
 for(const raw of ['','0','0100','01000'])assert.equal(v.acceptsInput('postal',raw),true)
})
await test('names accept Mexican names and reject digits, commas, controls',()=>{
 for(const name of ['María José','Muñoz',"O'Neill",'Ana-María','D’Ávila','O','李','  María   José  '])assert.equal(v.validate('name',name,true),'',name)
 for(const name of ['Ana123','Ana, Pérez','Ana\nPérez','<script>']){assert.ok(v.validate('name',name,true));assert.equal(v.acceptsInput('name',name),false)}
 assert.equal(v.canonical('  MARÍA   MUÑOZ '),'maría muñoz')
})
await test('phones have bounded Mexican formats; pasted letters/signs rejected',()=>{
 for(const phone of ['5512345678','55 1234 5678','+52 55 1234 5678','(55) 1234-5678'])assert.equal(v.validate('phone',phone),'')
 for(const phone of ['123','55123456789','551ABC5678','55.1234.5678','--5512345678','1e10'])assert.ok(v.validate('phone',phone),phone)
 assert.equal(v.acceptsInput('phone','551ABC5678'),false)
})
await test('CURP, RFC, email and date structure with date consistency',()=>{
 assert.equal(v.validate('curp','GODE900203HDFMNN01',false,18,'1990-02-03'),'')
 assert.equal(v.validate('rfc','GODE900203ABC',false,13,'1990-02-03'),'')
 assert.equal(v.validate('rfc','ABC900203A12',true),'')
 for(const raw of ['GODE900230HDFMNN01','GODE900203HXXMNN01','GODE900203HDFMNN0!','GODE900203HDFMNN0',' GODE900203HDFMNN01'])assert.ok(v.validate('curp',raw))
 for(const raw of ['GODE900230ABC','ABC900203!!1','ABCDE900203ABC','GODE900203 ABC'])assert.ok(v.validate('rfc',raw))
 assert.ok(v.validate('curp','GODE900203HDFMNN01',false,18,'1991-02-03'))
 assert.equal(v.validate('email',' ANA@example.com '),'')
 for(const raw of ['ana @example.com','a..b@example.com','a@example','a@-bad.com'])assert.ok(v.validate('email',raw))
 for(const raw of ['2026-02-30','2099-01-01','invalid'])assert.ok(v.validate('date',raw))
 assert.equal(v.age('1990-02-03'),36)
})
await test('customer requires at least one valid contact and complete address',()=>{
 const form={givenName:'Ana',paternalSurname:'Muñoz',street:'Calle 1',neighborhood:'Centro',municipality:'Ciudad',state:'México',postalCode:'01000',personalEmail:'ana@example.com'}
 assert.deepEqual(v.validateCustomer(form),{})
 assert.ok(v.validateCustomer({...form,personalEmail:''}).personalEmail)
 assert.ok(v.validateCustomer({...form,cellPhone:'abc'}).cellPhone)
 assert.ok(v.validateCustomer({...form,postalCode:'01,000'}).postalCode)
})
await test('workshop uses same strict rules and required fields',()=>{
 const form={name:'Taller 1',legalName:'Servicios S.A. de C.V.',rfc:'ABC900203A12',phone:'5512345678',email:'taller@example.com',street:'Calle 1',neighborhood:'Centro',municipality:'Ciudad',state:'México',postalCode:'01000'}
 assert.deepEqual(v.validateWorkshop(form),{})
 for(const [key,raw] of [['postalCode','01,000'],['rfc','invalid'],['phone','55ABC5678'],['email','bad email'],['name','<bad>']])assert.ok(v.validateWorkshop({...form,[key]:raw})[key])
})
await test('birth date display is day/month/year and CURP matches the same date',t=>{
 t.mock.timers.enable({apis:['Date'],now:new Date('2026-10-05T12:00:00')})
 assert.equal(v.birthDateFromDisplay('12/05/2006'),'2006-05-12')
 assert.equal(v.birthDateToDisplay('2006-05-12'),'12/05/2006')
 assert.equal(v.birthDateFromDisplay('2006-05-12'),'2006-05-12')
 assert.equal(v.age(v.birthDateFromDisplay('12/05/2006')),20)
 const fictionalCurp='AODE060512MDFKRLA1'
 assert.equal(v.validate('curp',fictionalCurp,false,18,'2006-05-12'),'')
 assert.match(v.validate('curp',fictionalCurp,false,18,'2006-12-05'),/no coincide/)
 assert.equal(v.age('2006-12-05'),19)
 for(const raw of ['','1','12','12/','12/05','12/05/2006','2006-05-12'])assert.equal(v.acceptsBirthDateInput(raw),true,raw)
 for(const raw of ['12052006','12,05,2006','12/05/20066','12/05/2e06',' 12/05/2006','12/05/2006\n'])assert.equal(v.acceptsBirthDateInput(raw),false,raw)
 assert.ok(v.validate('date',v.birthDateFromDisplay('31/02/2006')))
 t.mock.timers.setTime(new Date('2026-05-11T12:00:00').getTime());assert.equal(v.age('2006-05-12'),19)
 t.mock.timers.setTime(new Date('2026-05-12T12:00:00').getTime());assert.equal(v.age('2006-05-12'),20)
 t.mock.timers.setTime(new Date('2026-05-13T12:00:00').getTime());assert.equal(v.age('2006-05-12'),20)
 t.mock.timers.reset()
})
await test('customer presentation is readable without changing normalized data',async()=>{
 const presentationSource=await readFile(new URL('../frontend/src/modules/shared/presentation.ts',import.meta.url),'utf8')
 const output=ts.transpileModule(presentationSource,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText
 const p=await import('data:text/javascript;base64,'+Buffer.from(output).toString('base64'))
 const original={name:"maría del carmen muñoz o'neill",curp:'aode060512mdfkrla1',street:'av. de los insurgentes 120',birth:'2006-05-12',phone:'5512345678'}
 assert.equal(p.displayText(original.name),"María del Carmen Muñoz O'Neill")
 assert.equal(p.displayText('ana-maría d’ávila'),'Ana-María D’Ávila')
 assert.equal(p.displayText(original.street),'Av. de los Insurgentes 120')
 assert.equal(p.displayText('ciudad de méxico'),'Ciudad de México')
 assert.equal(p.displayIdentifier(original.curp),'AODE060512MDFKRLA1')
 assert.equal(p.displayBirthDate(original.birth),'12/05/2006')
 assert.equal(p.displayPhone(original.phone),'55 1234 5678')
 assert.equal(p.displayPhone('7731234567'),'773 123 4567')
 assert.equal(p.displayPhone('12345678901'),'12345678901')
 assert.equal(p.displayText(null),'')
 assert.equal(original.name,"maría del carmen muñoz o'neill")
 assert.equal(original.curp,'aode060512mdfkrla1')
})
