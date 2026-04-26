import * as XLSX from "xlsx";

/**
 * 获取表头
 */
export const getHeaderRow = (sheet: XLSX.WorkSheet): string[] => {
  const headers = [];
  const range = XLSX.utils.decode_range(sheet["!ref"] ?? "");
  let C;
  const R = range.s.r;
  /* start in the first row */
  for (C = range.s.c; C <= range.e.c; ++C) {
    /* walk every column in the range */
    const cell = sheet[XLSX.utils.encode_cell({ c: C, r: R })];
    /* find the cell in the first row */
    let hdr = "UNKNOWN " + C; // <-- replace with your desired default
    if (cell && cell.t) hdr = XLSX.utils.format_cell(cell);
    headers.push(hdr);
  }
  return headers;
};

/**
 * 判断是否为 excel 文件
 */
export const isExcel = (file: File) => {
  const reg = /\.(xlsx|xls|csv)$/i;
  return reg.test(file.name);
};
