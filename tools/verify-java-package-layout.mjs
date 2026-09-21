import { readFileSync, readdirSync } from "node:fs";
import { extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = fileURLToPath(new URL("../", import.meta.url));
const sourceRoot = join(projectRoot, "backend", "src", "main", "java");
const masterDataRoot = join(sourceRoot, "cn", "zqkj", "platform", "masterdata");
const hisRoot = join(sourceRoot, "cn", "zqkj", "platform", "his");
const systemRoot = join(sourceRoot, "cn", "zqkj", "platform", "system");
const commonUtilsRoot = join(sourceRoot, "cn", "zqkj", "platform", "common", "utils");
const violations = [];

/**
 * 递归查找生产Java源码，避免异常类随实现层随意散落。
 *
 * @param {string} directory 当前扫描目录
 * @returns {string[]} 目录内全部Java源码路径
 */
function findJavaSources(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? findJavaSources(path) : extname(entry.name) === ".java" ? [path] : [];
  });
}

/**
 * 校验基础数据域以“批次、医院综合目录、医疗目录”分组，防止对象重新堆回领域根目录。
 *
 * @param {string} sourcePath 生产Java源码绝对路径
 * @param {string} source Java源码文本
 */
function verifyMasterDataDomainLayout(sourcePath, source) {
  const pathFromDomain = relative(masterDataRoot, sourcePath);
  if (pathFromDomain.endsWith("package-info.java")) {
    return;
  }
  const packageMatch = source.match(/^package\s+([\w.]+);/m);
  const packageName = packageMatch?.[1] ?? "";
  const expectedPackage = `cn.zqkj.platform.masterdata.${relative(masterDataRoot, join(sourcePath, ".."))
    .split("/")
    .join(".")}`;
  if (packageName !== expectedPackage) {
    violations.push(`${relative(projectRoot, sourcePath)}: 包名必须与物理目录一致，期望 ${expectedPackage}`);
  }

  const packagePath = relative(masterDataRoot, join(sourcePath, ".."));
  const allowed = /^(?:controller|mapper)\/(?:batch|hospitaldirectory|medicaldirectory)$|^domain\/(?:batch|hospitaldirectory|medicaldirectory)(?:\/(?:dto|model|vo))?$|^service\/(?:batch|hospitaldirectory|medicaldirectory)(?:\/impl)?$/;
  if (!allowed.test(packagePath)) {
    violations.push(`${relative(projectRoot, sourcePath)}: masterdata 只能按 batch、hospitaldirectory、medicaldirectory 业务域归属`);
  }
}

/**
 * 校验HIS域按协议交易划分请求和响应模型，避免不同交易对象再次堆回domain根目录。
 *
 * @param {string} sourcePath 生产Java源码绝对路径
 * @param {string} source Java源码文本
 */
function verifyHisDomainLayout(sourcePath, source) {
  const pathFromDomain = relative(hisRoot, sourcePath);
  if (pathFromDomain.endsWith("package-info.java")) {
    return;
  }
  const packageMatch = source.match(/^package\s+([\w.]+);/m);
  const packageName = packageMatch?.[1] ?? "";
  const expectedPackage = `cn.zqkj.platform.his.${relative(hisRoot, join(sourcePath, ".."))
    .split("/")
    .join(".")}`;
  if (packageName !== expectedPackage) {
    violations.push(`${relative(projectRoot, sourcePath)}: 包名必须与物理目录一致，期望 ${expectedPackage}`);
  }

  const packagePath = relative(hisRoot, join(sourcePath, ".."));
  const allowed = /^client$|^exception$|^service(?:\/impl)?$|^domain\/(?:hospitaldirectory|medicaldirectory|organization)(?:\/(?:dto|model))?$|^domain\/(?:protocol|endpointverification)(?:\/model)?$/;
  if (!allowed.test(packagePath)) {
    violations.push(`${relative(projectRoot, sourcePath)}: his 只能按SOAP适配层、协议或具体HIS交易模型归属`);
  }
}

/**
 * 校验系统管理域按独立业务责任分组，防止重新回退为无边界的system大包。
 *
 * @param {string} sourcePath 生产Java源码绝对路径
 * @param {string} source Java源码文本
 */
function verifySystemDomainLayout(sourcePath, source) {
  const pathFromSystem = relative(systemRoot, sourcePath);
  if (pathFromSystem.endsWith("package-info.java")) {
    return;
  }
  const packageMatch = source.match(/^package\s+([\w.]+);/m);
  const packageName = packageMatch?.[1] ?? "";
  const expectedPackage = `cn.zqkj.platform.system.${relative(systemRoot, join(sourcePath, ".."))
    .split("/")
    .join(".")}`;
  if (packageName !== expectedPackage) {
    violations.push(`${relative(projectRoot, sourcePath)}: 包名必须与物理目录一致，期望 ${expectedPackage}`);
  }

  const packagePath = relative(systemRoot, join(sourcePath, ".."));
  const allowed = /^(?:identity|organization|configuration|audit|bootstrap)\/(?:controller|mapper)$|^(?:identity|organization|configuration|audit|bootstrap)\/domain\/(?:dto|model|vo)$|^(?:identity|organization|configuration|audit|bootstrap)\/service(?:\/impl)?$/;
  if (!allowed.test(packagePath)) {
    violations.push(`${relative(projectRoot, sourcePath)}: system 必须按 identity、organization、configuration、audit、bootstrap 子域归属`);
  }
}

for (const sourcePath of findJavaSources(sourceRoot)) {
  const source = readFileSync(sourcePath, "utf8");
  if (sourcePath.startsWith(masterDataRoot)) {
    verifyMasterDataDomainLayout(sourcePath, source);
  }
  if (sourcePath.startsWith(hisRoot)) {
    verifyHisDomainLayout(sourcePath, source);
  }
  if (sourcePath.startsWith(systemRoot)) {
    verifySystemDomainLayout(sourcePath, source);
  }
  const fromMasterDataPublicBoundary = sourcePath.startsWith(join(masterDataRoot, "domain"))
    || sourcePath.startsWith(join(masterDataRoot, "mapper"));
  if (fromMasterDataPublicBoundary && source.includes("import cn.zqkj.platform.his.domain.")) {
    violations.push(`${relative(projectRoot, sourcePath)}: masterdata 的领域对象和Mapper不得依赖HIS协议模型`);
  }
  if (source.includes("import cn.zqkj.platform.system.") && source.includes(".service.impl.")) {
    violations.push(`${relative(projectRoot, sourcePath)}: 不得跨包依赖system服务实现类，必须依赖服务接口`);
  }
  if (!sourcePath.startsWith(commonUtilsRoot)
      && /^import cn\.zqkj\.platform\.common\.utils\.(?!Func;)/m.test(source)) {
    violations.push(`${relative(projectRoot, sourcePath)}: 公共无状态工具必须通过Func门面调用，不得直接导入内部分类工具类`);
  }
  if (!sourcePath.startsWith(commonUtilsRoot)
      && /cn\.zqkj\.platform\.common\.utils\.(?!Func\b)/.test(source)) {
    violations.push(`${relative(projectRoot, sourcePath)}: 公共无状态工具必须通过Func门面调用，不得使用内部分类工具类的全限定名`);
  }
  if (!sourcePath.startsWith(commonUtilsRoot)
      && (source.includes("import java.util.Base64;") || source.includes("java.util.Base64."))) {
    violations.push(`${relative(projectRoot, sourcePath)}: Base64编解码必须通过Func门面调用，不得直接依赖JDK实现`);
  }
  if (!sourcePath.startsWith(commonUtilsRoot)
      && source.includes("withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()")) {
    violations.push(`${relative(projectRoot, sourcePath)}: UTC本地时间转换必须通过Func门面调用，避免业务域各自实现时区规则`);
  }
  if (!sourcePath.endsWith("Exception.java")) {
    continue;
  }

  const packageMatch = source.match(/^package\s+([\w.]+);/m);
  const packageName = packageMatch?.[1] ?? "";
  if (!packageName.endsWith(".exception")) {
    violations.push(`${relative(projectRoot, sourcePath)}: 异常类必须放在业务域的 exception 包中`);
  }
}

if (violations.length > 0) {
  console.error("Java异常包结构检查失败：");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("Java package layout verification passed.");
