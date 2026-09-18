import { readFileSync, readdirSync } from "node:fs";
import { extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = fileURLToPath(new URL("../", import.meta.url));
const sourceRoot = join(projectRoot, "backend", "src", "main", "java");
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

for (const sourcePath of findJavaSources(sourceRoot)) {
  if (!sourcePath.endsWith("Exception.java")) {
    continue;
  }

  const source = readFileSync(sourcePath, "utf8");
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
