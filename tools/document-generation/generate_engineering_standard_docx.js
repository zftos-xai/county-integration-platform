const fs = require('fs');
const path = require('path');
const {
  AlignmentType, BorderStyle, Document, Footer, HeadingLevel, LevelFormat,
  PageBreak, PageNumber, Paragraph, Packer, Table, TableCell, TableRow,
  TextRun, WidthType, ShadingType,
} = require('docx');

const projectRoot = path.resolve(__dirname, '..', '..');
const standardsDir = path.join(projectRoot, 'docs', 'standards');
const input = path.join(standardsDir, '县人民医院与基层卫生院中间接口平台_工程建设规范_v0.1_内部版20260910.1.md');
const output = path.join(standardsDir, '县人民医院与基层卫生院中间接口平台_工程建设规范_v0.1_内部版20260910.1.docx');
const navy = '17365D';
const blue = '1F4E78';
const pale = 'F3F6F8';
const lightBlue = 'DDEBF7';
const gray = '666666';
const red = 'C00000';
const border = { style: BorderStyle.SINGLE, size: 4, color: 'B7C9D6' };

const clean = (text) => String(text)
  .replace(/`([^`]+)`/g, '$1')
  .replace(/\*\*([^*]+)\*\*/g, '$1')
  .trim();

const run = (text, options = {}) => new TextRun({
  text: clean(text), font: 'Microsoft YaHei', size: 19, ...options,
});

const paragraph = (text, options = {}) => new Paragraph({
  children: [run(text, options.run || {})],
  spacing: { before: options.before || 0, after: options.after ?? 95, line: options.line || 300 },
  alignment: options.alignment,
  heading: options.heading,
  keepNext: Boolean(options.heading),
  keepLines: Boolean(options.heading),
  indent: options.indent,
  shading: options.shading,
});

const bullet = (text) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'bullets', level: 0 },
  spacing: { after: 55, line: 290 },
});

const numbered = (number, text) => new Paragraph({
  children: [run(`${number}. ${text}`)],
  spacing: { after: 60, line: 290 },
  indent: { left: 440, hanging: 260 },
});

const checkbox = (text) => new Paragraph({
  children: [run(`□ ${text}`)],
  spacing: { after: 55, line: 290 },
  indent: { left: 240 },
});

const checklistTable = (items) => {
  const rows = [];
  const rowCount = Math.ceil(items.length / 2);
  for (let rowIndex = 0; rowIndex < rowCount; rowIndex += 1) {
    const values = [items[rowIndex], items[rowIndex + rowCount]];
    rows.push(new TableRow({
      cantSplit: true,
      children: values.map((value) => new TableCell({
        width: { size: 4600, type: WidthType.DXA },
        margins: { top: 55, bottom: 55, left: 80, right: 80 },
        borders: { top: border, bottom: border, left: border, right: border },
        children: [new Paragraph({
          children: [run(value ? `□ ${value}` : '', { size: 17 })],
          spacing: { after: 0, line: 245 },
        })],
      })),
    }));
  }
  return new Table({
    width: { size: 9200, type: WidthType.DXA },
    columnWidths: [4600, 4600],
    rows,
  });
};

const codeLine = (text) => new Paragraph({
  children: [new TextRun({ text, font: 'Consolas', size: 17, color: '333333' })],
  spacing: { after: 10, line: 230 },
  indent: { left: 260 },
  shading: { fill: 'F4F6F7', type: ShadingType.CLEAR, color: 'auto' },
});

const widthsFor = (count) => {
  if (count === 2) return [2700, 6500];
  if (count === 3) return [1900, 3700, 3600];
  if (count === 4) return [1500, 2450, 2600, 2650];
  const base = Math.floor(9200 / count);
  return Array.from({ length: count }, (_, i) => i === count - 1 ? 9200 - base * (count - 1) : base);
};

const tableCell = (text, width, options = {}) => new TableCell({
  width: { size: width, type: WidthType.DXA },
  shading: options.header
    ? { fill: navy, type: ShadingType.CLEAR, color: 'auto' }
    : options.fill
      ? { fill: options.fill, type: ShadingType.CLEAR, color: 'auto' }
      : undefined,
  margins: { top: 65, bottom: 65, left: 80, right: 80 },
  borders: { top: border, bottom: border, left: border, right: border },
  verticalAlign: 'center',
  children: [new Paragraph({
    children: [run(text, options.header ? { bold: true, color: 'FFFFFF', size: 17 } : { size: 17 })],
    spacing: { after: 0, line: 245 },
    alignment: options.align,
  })],
});

const makeTable = (rows) => {
  const count = rows[0].length;
  const widths = widthsFor(count);
  return new Table({
    width: { size: 9200, type: WidthType.DXA },
    columnWidths: widths,
    rows: rows.map((row, rowIndex) => new TableRow({
      children: row.map((value, columnIndex) => tableCell(value, widths[columnIndex], {
        header: rowIndex === 0,
        fill: rowIndex > 0 && rowIndex % 2 === 0 ? pale : undefined,
        align: /^\d+人$/.test(value) ? AlignmentType.CENTER : undefined,
      })),
      tableHeader: rowIndex === 0,
      cantSplit: true,
    })),
  });
};

const parseTableRow = (line) => line.slice(1, -1).split('|').map((part) => clean(part));
const isSeparator = (line) => /^\|(?:\s*:?-+:?\s*\|)+$/.test(line.trim());

const markdown = fs.readFileSync(input, 'utf8').replace(/^\uFEFF/, '');
const lines = markdown.split(/\r?\n/);
const children = [];

children.push(new Paragraph({
  spacing: { before: 1250, after: 240 }, alignment: AlignmentType.CENTER,
  children: [run('县人民医院与基层卫生院', { bold: true, size: 36, color: navy })],
}));
children.push(new Paragraph({
  spacing: { after: 280 }, alignment: AlignmentType.CENTER,
  children: [run('中间接口平台工程建设规范', { bold: true, size: 44, color: blue })],
}));
children.push(new Paragraph({
  spacing: { after: 650 }, alignment: AlignmentType.CENTER,
  children: [run('单体应用 · 私有化部署 · 两个月完整交付', { size: 22, color: gray })],
}));
children.push(makeTable([
  ['文档属性', '内容'],
  ['文档版本', 'V0.1（内部评审稿）'],
  ['内部修订', '20260910.1'],
  ['编制日期', '2026年9月10日'],
  ['适用技术', 'Java 21、Spring Boot 3.5、MyBatis、SQL Server、Vue 3'],
  ['适用范围', '中间接口平台、HIS改造、管理端、嵌入页面、联调、部署及交付'],
]));
children.push(paragraph('本规范用于项目实施、代码评审、接口联调、上线和交付验收。', {
  before: 280,
  alignment: AlignmentType.CENTER,
  run: { bold: true, color: red, size: 17 },
}));
children.push(new Paragraph({ children: [new PageBreak()] }));

let i = 0;
let inCode = false;
while (i < lines.length) {
  const raw = lines[i];
  const line = raw.trim();

  if (line.startsWith('```')) {
    inCode = !inCode;
    if (!inCode) children.push(paragraph('', { after: 45, line: 100 }));
    i += 1;
    continue;
  }
  if (inCode) {
    children.push(codeLine(raw));
    i += 1;
    continue;
  }
  if (!line || line.startsWith('# ') || line.startsWith('> ')) {
    i += 1;
    continue;
  }
  if (line.startsWith('## ')) {
    children.push(paragraph(line.slice(3), { heading: HeadingLevel.HEADING_1 }));
    i += 1;
    continue;
  }
  if (line.startsWith('### ')) {
    children.push(paragraph(line.slice(4), { heading: HeadingLevel.HEADING_2 }));
    i += 1;
    continue;
  }
  if (line.startsWith('|') && i + 1 < lines.length && isSeparator(lines[i + 1])) {
    const rows = [parseTableRow(line)];
    i += 2;
    while (i < lines.length && lines[i].trim().startsWith('|')) {
      rows.push(parseTableRow(lines[i].trim()));
      i += 1;
    }
    children.push(makeTable(rows));
    children.push(paragraph('', { after: 45, line: 100 }));
    continue;
  }
  const checkboxMatch = line.match(/^- \[ \] (.+)$/);
  if (checkboxMatch) {
    const items = [];
    while (i < lines.length) {
      const match = lines[i].trim().match(/^- \[ \] (.+)$/);
      if (!match) break;
      items.push(match[1]);
      i += 1;
    }
    children.push(checklistTable(items));
    continue;
  }
  const bulletMatch = line.match(/^- (.+)$/);
  if (bulletMatch) {
    children.push(bullet(bulletMatch[1]));
    i += 1;
    continue;
  }
  const numberMatch = line.match(/^(\d+)\. (.+)$/);
  if (numberMatch) {
    children.push(numbered(numberMatch[1], numberMatch[2]));
    i += 1;
    continue;
  }
  children.push(paragraph(line));
  i += 1;
}

// Word requires a paragraph after a document-ending table. Keep it small enough
// to remain on the same page instead of creating a trailing blank page.
children.push(new Paragraph({
  children: [new TextRun({ text: '', font: 'Microsoft YaHei', size: 2 })],
  spacing: { before: 0, after: 0, line: 20 },
}));

const doc = new Document({
  creator: 'Codex',
  title: '县人民医院与基层卫生院中间接口平台工程建设规范',
  description: '县人民医院与所辖基层卫生院中间接口平台工程实施与交付规范',
  styles: {
    default: {
      document: {
        run: { font: 'Microsoft YaHei', size: 19, color: '222222' },
        paragraph: { spacing: { line: 300 } },
      },
      heading1: {
        run: { font: 'Microsoft YaHei', size: 28, bold: true, color: navy },
        paragraph: { spacing: { before: 220, after: 120 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: 'Microsoft YaHei', size: 23, bold: true, color: blue },
        paragraph: { spacing: { before: 150, after: 85 }, outlineLevel: 1 },
      },
    },
  },
  numbering: {
    config: [
      {
        reference: 'bullets',
        levels: [{
          level: 0,
          format: LevelFormat.BULLET,
          text: '●',
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 420, hanging: 220 } }, run: { font: 'Microsoft YaHei', color: blue } },
        }],
      },
      {
        reference: 'numbers',
        levels: [{
          level: 0,
          format: LevelFormat.DECIMAL,
          text: '%1.',
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 440, hanging: 260 } }, run: { font: 'Microsoft YaHei' } },
        }],
      },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 900, right: 950, bottom: 900, left: 950, header: 400, footer: 450 },
      },
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          run('县人民医院与基层卫生院中间接口平台工程建设规范  |  ', { size: 15, color: gray }),
          new TextRun({ children: [PageNumber.CURRENT], font: 'Microsoft YaHei', size: 15, color: gray }),
        ],
      })] }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then((buffer) => {
  fs.writeFileSync(output, buffer);
  console.log(`Created ${output} (${buffer.length} bytes)`);
});
