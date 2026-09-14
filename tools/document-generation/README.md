# 文档生成脚本

本目录保存项目方案、立项书、工程规范和接口矩阵的生成脚本。

- Node.js 脚本依赖项目根目录的 `docx` 包。
- `generate_interface_matrix.py` 依赖 Python 3 和 `openpyxl`。
- 所有输入、输出路径均按项目根目录解析，不依赖执行命令时的当前目录。

示例：

```powershell
node ./tools/document-generation/generate_project_initiation_docx.js
python ./tools/document-generation/generate_interface_matrix.py
```
