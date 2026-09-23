#!/usr/bin/env node

import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoots = [
  path.join(projectRoot, 'backend/src/main/java'),
  path.join(projectRoot, 'backend/src/test/java'),
];
const mapperRoot = path.join(projectRoot, 'backend/src/main/resources/mapper');
const migrationRoot = path.join(projectRoot, 'backend/src/main/resources/db/migration');

const persistenceContracts = new Map([
  ['cn.zqkj.platform.system.configuration.domain.model.EncryptedExternalEndpointCredential', ['sys_external_endpoint_credential']],
  ['cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanItemSnapshot', ['db_contract_plan_item', 'db_contract_plan']],
  ['cn.zqkj.platform.databasecontract.domain.model.DatabaseContractPlanSnapshot', ['db_contract_plan']],
  ['cn.zqkj.platform.exchange.domain.model.ExchangeRuntimeRecord', ['exch_exchange_record']],
  ['cn.zqkj.platform.exchange.domain.vo.ExchangeRecordVO', ['exch_exchange_record']],
  ['cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRecord', ['md_hospital_directory', 'org_organization', 'md_sync_batch', 'md_hospital_directory_relation']],
  ['cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectoryRelationRecord', ['md_hospital_directory_relation']],
  ['cn.zqkj.platform.masterdata.domain.hospitaldirectory.model.HospitalDirectorySourceRecord', ['md_hospital_directory']],
  ['cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectorySyncResultVO', ['md_hospital_directory_sync_result']],
  ['cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectorySyncResultVO', ['md_medical_directory_sync_result']],
  ['cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10SyncResultVO', ['md_icd10_sync_result']],
  ['cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10HisInvocationVO', ['md_icd10_his_invocation']],
  ['cn.zqkj.platform.masterdata.domain.icd10.model.Icd10DirectoryRecord', ['md_icd10_diagnosis', 'md_sync_batch']],
  ['cn.zqkj.platform.masterdata.domain.icd10.vo.Icd10DirectoryCountVO', ['md_icd10_diagnosis']],
  ['cn.zqkj.platform.masterdata.domain.medicaldirectory.model.MedicalDirectoryRecord', ['md_medical_directory', 'org_organization', 'md_sync_batch']],
  ['cn.zqkj.platform.masterdata.domain.medicaldirectory.vo.MedicalDirectoryCountVO', ['md_medical_directory', 'org_organization']],
  ['cn.zqkj.platform.masterdata.domain.hospitaldirectory.vo.HospitalDirectoryCountVO', ['md_hospital_directory', 'org_organization']],
  ['cn.zqkj.platform.masterdata.domain.batch.model.MasterDataBatchSnapshot', ['md_sync_batch', 'org_organization']],
  ['cn.zqkj.platform.system.configuration.domain.model.DictionaryItem', ['sys_dictionary_item', 'sys_dictionary_type']],
  ['cn.zqkj.platform.system.configuration.domain.model.DictionaryType', ['sys_dictionary_type']],
  ['cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint', ['sys_external_endpoint', 'org_organization', 'sys_external_endpoint_credential']],
  ['cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointCredential', ['sys_external_endpoint_credential', 'sys_external_endpoint']],
  ['cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointScope', ['sys_external_endpoint', 'sys_external_system', 'org_organization', 'sys_external_endpoint_credential']],
  ['cn.zqkj.platform.system.configuration.domain.model.ExternalSystem', ['sys_external_system']],
  ['cn.zqkj.platform.system.identity.domain.model.ManagedUserSummary', ['sys_user', 'org_organization']],
  ['cn.zqkj.platform.system.identity.domain.model.UserRoleAssignment', ['sys_user_role']],
  ['cn.zqkj.platform.system.identity.domain.model.UserOrganizationAssignment', ['sys_user_organization_scope']],
  ['cn.zqkj.platform.system.identity.domain.model.RolePermissionAssignment', ['sys_role_permission']],
  ['cn.zqkj.platform.system.audit.domain.model.ManagementAuditEvent', ['audit_management_event']],
  ['cn.zqkj.platform.system.configuration.domain.model.ParameterValue', ['sys_parameter_value', 'org_organization']],
  ['cn.zqkj.platform.system.identity.domain.model.RoleSummary', ['sys_role', 'sys_role_permission']],
  ['cn.zqkj.platform.system.identity.domain.model.UserAccount', ['sys_user', 'org_organization']],
  ['cn.zqkj.platform.system.organization.domain.vo.OrganizationVO', ['org_organization']],
  ['cn.zqkj.platform.system.identity.domain.vo.PermissionVO', ['sys_permission']],
]);

const failures = [];

async function collectFiles(directory, suffix) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return collectFiles(target, suffix);
    return entry.isFile() && entry.name.endsWith(suffix) ? [target] : [];
  }));
  return nested.flat();
}

function relative(file) {
  return path.relative(projectRoot, file);
}

function lineNumber(source, offset) {
  return source.slice(0, offset).split('\n').length;
}

function splitRecordComponents(raw) {
  const components = [];
  let start = 0;
  let angle = 0;
  let round = 0;
  let square = 0;
  for (let index = 0; index < raw.length; index += 1) {
    const character = raw[index];
    if (character === '<') angle += 1;
    else if (character === '>') angle = Math.max(0, angle - 1);
    else if (character === '(') round += 1;
    else if (character === ')') round = Math.max(0, round - 1);
    else if (character === '[') square += 1;
    else if (character === ']') square = Math.max(0, square - 1);
    else if (character === ',' && angle === 0 && round === 0 && square === 0) {
      components.push(raw.slice(start, index).trim());
      start = index + 1;
    }
  }
  components.push(raw.slice(start).trim());
  return components.filter(Boolean);
}

function recordComponentName(component) {
  const withoutAnnotations = component.replace(/@[\w.]+(?:\([^)]*\))?\s*/g, '').trim();
  return withoutAnnotations.match(/([A-Za-z_$][\w$]*)\s*$/)?.[1];
}

function precedingJavadoc(source, declarationOffset) {
  return source.slice(0, declarationOffset).match(/\/\*\*[\s\S]*?\*\/\s*$/)?.[0] ?? null;
}

for (const javaRoot of javaRoots) {
  for (const file of await collectFiles(javaRoot, '.java')) {
    const source = await readFile(file, 'utf8');
    for (const match of source.matchAll(/^.*@(param|return|throws)\b.*@(param|return|throws)\b.*$/gm)) {
      failures.push(`${relative(file)}:${lineNumber(source, match.index)} 一个Javadoc行只能包含一个块标签`);
    }
    for (const match of source.matchAll(/\/\*\*\s*\n\s*\*\s*@(param|return|throws)\b/g)) {
      failures.push(`${relative(file)}:${lineNumber(source, match.index)} Javadoc必须先说明职责，不能直接从块标签开始`);
    }
    for (const match of source.matchAll(/\bpublic\s+record\s+([A-Za-z_$][\w$]*)\s*\(([\s\S]*?)\)\s*(?:implements\s+[^\{]+)?\{/g)) {
      const [, recordName, rawComponents] = match;
      const comment = precedingJavadoc(source, match.index);
      if (!comment) continue;
      const documented = new Set([...comment.matchAll(/@param\s+([A-Za-z_$][\w$]*)\b/g)].map((item) => item[1]));
      for (const component of splitRecordComponents(rawComponents)) {
        const name = recordComponentName(component);
        if (name && !documented.has(name)) {
          failures.push(`${relative(file)}:${lineNumber(source, match.index)} record ${recordName} 缺少组件 @param ${name}`);
        }
      }
    }
  }
}

const mapperTypes = new Set();
for (const file of await collectFiles(mapperRoot, '.xml')) {
  const source = await readFile(file, 'utf8');
  for (const match of source.matchAll(/\b(?:type|resultType)="(cn\.zqkj\.platform\.[^"]+)"/g)) {
    mapperTypes.add(match[1]);
  }
}
for (const mappedType of mapperTypes) {
  if (!persistenceContracts.has(mappedType)) {
    failures.push(`MyBatis映射类型 ${mappedType} 未登记数据表注释合同`);
  }
}

const migrationFiles = await collectFiles(migrationRoot, '.sql');
const migrationText = (await Promise.all(migrationFiles.map((file) => readFile(file, 'utf8')))).join('\n').toLowerCase();
for (const [qualifiedName, tableNames] of persistenceContracts) {
  const javaFile = path.join(projectRoot, 'backend/src/main/java', `${qualifiedName.replaceAll('.', '/')}.java`);
  let source;
  try {
    source = await readFile(javaFile, 'utf8');
  } catch {
    failures.push(`数据表注释合同对应的Java类型不存在：${qualifiedName}`);
    continue;
  }
  const simpleName = qualifiedName.slice(qualifiedName.lastIndexOf('.') + 1);
  const declaration = source.search(new RegExp(`\\bpublic\\s+(?:final\\s+)?(?:class|record)\\s+${simpleName}\\b`));
  const comment = declaration < 0 ? null : precedingJavadoc(source, declaration);
  if (!comment) {
    failures.push(`${relative(javaFile)} 缺少类级Javadoc，无法核对数据表关联说明`);
    continue;
  }
  if (!/(业务说明|持久化目标)/.test(comment)) {
    failures.push(`${relative(javaFile)} 类级Javadoc必须包含“业务说明”或“持久化目标”`);
  }
  for (const tableName of tableNames) {
    if (!comment.includes(`dbo.${tableName}`)) {
      failures.push(`${relative(javaFile)} 类级Javadoc缺少表名 dbo.${tableName}`);
    }
    if (!migrationText.includes(tableName.toLowerCase())) {
      failures.push(`迁移脚本中未找到注释合同登记的数据表 ${tableName}`);
    }
  }
}

if (failures.length > 0) {
  console.error('Java注释质量检查失败：');
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log(`Java注释质量检查通过：已检查 ${persistenceContracts.size} 个数据表关联模型及全部Java Javadoc结构。`);
