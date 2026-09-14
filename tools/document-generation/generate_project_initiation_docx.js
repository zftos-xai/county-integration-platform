const fs = require('fs');
const path = require('path');
const {
  AlignmentType, BorderStyle, Document, Footer, HeadingLevel, LevelFormat,
  PageBreak, PageNumber, Paragraph, Packer, Table, TableCell, TableRow,
  TextRun, WidthType, ShadingType,
} = require('docx');

const projectRoot = path.resolve(__dirname, '..', '..');
const plansDir = path.join(projectRoot, 'docs', 'plans');
const input = path.join(plansDir, '县人民医院与基层卫生院中间接口平台项目立项书.md');
const output = path.join(plansDir, '县人民医院与基层卫生院中间接口平台项目立项书.docx');
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
  text: clean(text), font: 'Microsoft YaHei', size: 20, ...options,
});

const paragraph = (text, options = {}) => new Paragraph({
  children: [run(text, options.run || {})],
  spacing: { before: options.before || 0, after: options.after ?? 105, line: options.line || 315 },
  alignment: options.alignment,
  heading: options.heading,
  keepNext: Boolean(options.heading),
  keepLines: Boolean(options.heading),
});

const bullet = (text) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'bullets', level: 0 },
  spacing: { after: 65, line: 300 },
});

const numbered = (text) => new Paragraph({
  children: [run(text)],
  numbering: { reference: 'numbers', level: 0 },
  spacing: { after: 70, line: 300 },
});

const checkbox = (text) => new Paragraph({
  children: [run(`□ ${text}`)],
  spacing: { after: 65, line: 300 },
  indent: { left: 240 },
});

const widthsFor = (count) => {
  if (count === 2) return [2700, 6500];
  if (count === 3) return [2200, 3500, 3500];
  if (count === 4) return [1500, 2700, 2400, 2600];
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
  margins: { top: 70, bottom: 70, left: 85, right: 85 },
  borders: { top: border, bottom: border, left: border, right: border },
  verticalAlign: 'center',
  children: [new Paragraph({
    children: [run(text, options.header ? { bold: true, color: 'FFFFFF', size: 18 } : { size: 18 })],
    spacing: { after: 0, line: 260 },
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
        align: /^\d+人$/.test(value) || value === '5人' ? AlignmentType.CENTER : undefined,
      })),
      tableHeader: rowIndex === 0,
      cantSplit: true,
    })),
  });
};

const parseTableRow = (line) => line
  .slice(1, -1)
  .split('|')
  .map((part) => clean(part));

const isSeparator = (line) => /^\|(?:\s*:?-+:?\s*\|)+$/.test(line.trim());

const markdown = fs.readFileSync(input, 'utf8').replace(/^\uFEFF/, '');
const lines = markdown.split(/\r?\n/);
const children = [];

children.push(new Paragraph({
  spacing: { before: 1350, after: 260 }, alignment: AlignmentType.CENTER,
  children: [run('县人民医院与基层卫生院', { bold: true, size: 38, color: navy })],
}));
children.push(new Paragraph({
  spacing: { after: 300 }, alignment: AlignmentType.CENTER,
  children: [run('中间接口平台项目立项书', { bold: true, size: 46, color: blue })],
}));
children.push(new Paragraph({
  spacing: { after: 750 }, alignment: AlignmentType.CENTER,
  children: [run('院内私有化部署 · 两个月完整交付', { size: 24, color: gray })],
}));
children.push(makeTable([
  ['文档属性', '内容'],
  ['文档版本', 'V0.2（立项评审稿）'],
  ['编制日期', '2026年9月10日'],
  ['建设周期', '2个月（完整交付）'],
  ['建设方式', '院内私有化部署'],
  ['核心人员', '5人：县人民医院HIS改造2人、中间接口平台2人、项目对接与协调1人'],
]));
children.push(paragraph('用于项目立项评审、范围确认、资源安排和实施决策。', {
  before: 300,
  alignment: AlignmentType.CENTER,
  run: { bold: true, color: red, size: 18 },
}));
children.push(new Paragraph({ children: [new PageBreak()] }));

let i = 0;
while (i < lines.length) {
  const line = lines[i].trim();
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
    children.push(paragraph('', { after: 55, line: 100 }));
    continue;
  }

  const checkboxMatch = line.match(/^- \[ \] (.+)$/);
  if (checkboxMatch) {
    children.push(checkbox(checkboxMatch[1]));
    i += 1;
    continue;
  }

  const bulletMatch = line.match(/^- (.+)$/);
  if (bulletMatch) {
    children.push(bullet(bulletMatch[1]));
    i += 1;
    continue;
  }

  const numberMatch = line.match(/^\d+\. (.+)$/);
  if (numberMatch) {
    children.push(numbered(numberMatch[1]));
    i += 1;
    continue;
  }

  children.push(paragraph(line));
  i += 1;
}

const doc = new Document({
  creator: 'Codex',
  title: '县人民医院与基层卫生院中间接口平台项目立项书',
  description: '县人民医院与所辖基层卫生院中间接口平台立项评审文件',
  styles: {
    default: {
      document: {
        run: { font: 'Microsoft YaHei', size: 20, color: '222222' },
        paragraph: { spacing: { line: 315 } },
      },
      heading1: {
        run: { font: 'Microsoft YaHei', size: 29, bold: true, color: navy },
        paragraph: { spacing: { before: 230, after: 130 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: 'Microsoft YaHei', size: 24, bold: true, color: blue },
        paragraph: { spacing: { before: 165, after: 95 }, outlineLevel: 1 },
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
        margin: { top: 1000, right: 1000, bottom: 1000, left: 1000, header: 420, footer: 480 },
      },
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          run('县人民医院与基层卫生院中间接口平台项目立项书  |  ', { size: 16, color: gray }),
          new TextRun({ children: [PageNumber.CURRENT], font: 'Microsoft YaHei', size: 16, color: gray }),
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
