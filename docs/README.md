# 项目资料索引

项目业务关系严格限定为“县人民医院 ⇄ 所辖基层卫生院”。本目录保存正式项目资料和工程决策，不保存项目管理优先级台账。

## 方案与立项材料

`plans/` 包含总体对接方案、业务协同对接方案、中间接口平台技术方案和项目立项书。

## 工程建设规范

`standards/` 包含工程建设规范的 Markdown、Word 和 PDF 版本。

Java 项目执行规则见 [Java 编码规范](standards/java-coding-guidelines.md)，参考 Alibaba Java Coding Guidelines，并由后端 Checkstyle 执行其中的基础静态规则。

## 接口对接

[接口对接实施方案](integration/接口对接实施方案.md) 说明准入条件、分阶段交付与当前阻塞边界；具体接口优先级和责任人由项目管理台账维护。

## 接口参考资料

`reference/interfaces/` 包含接口清单、接口矩阵、基层 HIS 与云平台接口文档及其配套图片资源。这些资料是需求和联调输入，不等同于已经完成接口确认或生产验收。

## 工程决策

`decisions/` 保存技术基线及后续工程决策记录。

## 文档生成

生成脚本位于 `../tools/document-generation/`。脚本输入输出均已固定到本目录下的对应分类目录。
