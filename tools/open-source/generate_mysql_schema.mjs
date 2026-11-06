// Build a fresh MySQL schema from the sanitized, fully migrated public schema.
// Usage: node generate_mysql_schema.mjs <PGlite module path> <project root>
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
const { PGlite } = await import(pathToFileURL(resolve(process.argv[2])));
const root = resolve(process.argv[3] || '.');
const db = new PGlite();
for (const name of ['01-schema.sql','02-upgrade.sql','03-demo.sql','04-menus.sql']) {
  const sql = (await readFile(`${root}/deploy/public/${name}`, 'utf8'))
    .replaceAll('SET escape = off;', '')
    .replace(/(character(?: varying)?)\((\d+) char\)/g, '$1($2)')
    .replace(/\bblob\b/g, 'bytea').replace(/\btinyint\b/g, 'smallint')
    .replaceAll(":'admin_hash'", "'__OCH_ADMIN_HASH__'");
  await db.exec(sql);
}
const tables = (await db.query("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name")).rows;
const menuCatalog = JSON.parse(await readFile(`${root}/deploy/demo/database/menu-catalog.json`, 'utf8'));
for (const [id,name,parent,order,path,component,icon] of menuCatalog) {
  const updated = await db.query(`UPDATE sys_menu SET menu_name=$2,parent_id=$3,order_num=$4,path=$5,component=$6,icon=$7,menu_type=$8
    WHERE menu_id=$1 RETURNING menu_id`,[id,name,parent,order,path,component,icon,path==='#'?'M':'C']);
  if (!updated.rows.length) await db.query(`INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,icon,is_frame,menu_type,visible,status,create_by,create_time,del_flag)
    VALUES ($1,$2,$3,$4,$5,$6,$7,1,$8,0,0,1,CURRENT_TIMESTAMP,0)`,[id,name,parent,order,path,component,icon,path==='#'?'M':'C']);
}
await db.exec(`INSERT INTO sys_role_menu (role_id,menu_id,create_by,create_time,del_flag)
  SELECT 1,m.menu_id,1,CURRENT_TIMESTAMP,0 FROM sys_menu m
  WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu r WHERE r.role_id=1 AND r.menu_id=m.menu_id)`);
const qi = name => '`' + name.replaceAll('`','``') + '`';
const literal = value => value === null ? 'NULL' : value instanceof Date ? 'CURRENT_TIMESTAMP(6)' : typeof value === 'number' ? String(value) : "'" + String(value).replaceAll('\\','\\\\').replaceAll("'","''") + "'";
let ddl = '-- MySQL 8.4 / InnoDB / utf8mb4. Fresh EMPTY database only.\n-- Generated from sanitized deploy/public schema + upgrades; no business data.\n';
for (const {table_name: table} of tables) {
  const cols = (await db.query("SELECT column_name,data_type,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name=$1 ORDER BY ordinal_position",[table])).rows;
  const indexes = (await db.query(`SELECT ci.relname AS name, i.indisprimary AS primary_key, i.indisunique AS unique_key,
    ARRAY(SELECT a.attname FROM unnest(i.indkey) WITH ORDINALITY k(attnum,ord)
      JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.attnum ORDER BY k.ord) AS columns
    FROM pg_index i JOIN pg_class ct ON ct.oid=i.indrelid JOIN pg_class ci ON ci.oid=i.indexrelid
    JOIN pg_namespace n ON n.oid=ct.relnamespace WHERE n.nspname='public' AND ct.relname=$1`,[table])).rows;
  const auto = cols.find(c => c.column_default?.startsWith('nextval('));
  const primary = indexes.find(i=>i.primary_key);
  if (!primary && auto) indexes.push({primary_key:true,columns:[auto.column_name]});
  if (table === 'customer_crowd_rel') indexes.push({name:'uk_crowd_customer',unique_key:true,columns:['crowd_id','customer_id']});
  const lines = cols.map(c => {
    const types = {bigint:'BIGINT',integer:'INT',smallint:'SMALLINT',text:'LONGTEXT',bytea:'LONGBLOB',boolean:'BOOLEAN',json:'JSON',
      'timestamp without time zone':'DATETIME(6)','double precision':'DOUBLE',real:'FLOAT',date:'DATE',
      'time without time zone':'TIME(6)'};
    let type = types[c.data_type];
    if (c.data_type === 'character varying') type = `VARCHAR(${c.character_maximum_length})`;
    if (c.data_type === 'character') type = `CHAR(${c.character_maximum_length})`;
    if (c.data_type === 'numeric') type = `DECIMAL(${c.numeric_precision || 38},${c.numeric_scale || 0})`;
    if (!type) throw Error(`Unsupported type ${table}.${c.column_name}: ${c.data_type}`);
    let definition = `${qi(c.column_name)} ${type}`;
    definition += c.is_nullable === 'NO' || c === auto ? ' NOT NULL' : ' NULL';
    if (c === auto) definition += ' AUTO_INCREMENT';
    else if (c.column_default !== null) {
      const value = c.column_default.replace(/::[\w ]+(?:\(\d+\))?$/,'');
      if (/^(CURRENT_TIMESTAMP|now\(\))$/i.test(value)) definition += ' DEFAULT CURRENT_TIMESTAMP(6)';
      else if (/^(-?\d+(?:\.\d+)?|'(?:[^']|'')*'|true|false)$/i.test(value)) definition += ' DEFAULT ' + value;
      else throw Error(`Unsupported default: ${table}.${c.column_name} ${value}`);
    }
    return definition;
  });
  for (const i of indexes) {
    if (!i.columns.length) throw Error('Expression indexes require manual conversion');
    lines.push(`${i.primary_key?'PRIMARY KEY':(i.unique_key?'UNIQUE KEY ':'KEY ')+qi(i.name)} (${i.columns.map(qi).join(', ')})`);
  }
  ddl += `\nCREATE TABLE ${qi(table)} (\n  ${lines.join(',\n  ')}\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\n`;
}
let seed = '-- Synthetic demo configuration only. Admin hash is provided by DemoDatabase.java.\n';
for (const table of ['sys_app_user','sys_app_role','sys_user_role','sys_menu','sys_role_menu','sys_category','call_skill']) {
  const rows = (await db.query(`SELECT * FROM "${table}"`)).rows;
  for (const row of rows) seed += `INSERT INTO ${qi(table)} (${Object.keys(row).map(qi).join(', ')}) VALUES (${Object.values(row).map(literal).join(', ')});\n`;
}
await mkdir(`${root}/deploy/demo/database`,{recursive:true});
await writeFile(`${root}/deploy/demo/database/01-schema.sql`,ddl);
await writeFile(`${root}/deploy/demo/database/02-seed.sql`,seed);
console.log(JSON.stringify({tables:tables.length,seedTables:7}));
await db.close();
