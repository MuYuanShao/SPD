package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.PendingProductApplicationRequest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.hospital.spd.common.SqlHelper.isBlank;

/** Owns spreadsheet generation and parsing for pending catalog applications. */
@Service
public class PendingProductImportExportService {
    private static final Logger log = LoggerFactory.getLogger(PendingProductImportExportService.class);
    private final ProductApprovalService productApprovalService;

    public PendingProductImportExportService(ProductApprovalService productApprovalService) {
        this.productApprovalService = productApprovalService;
    }

    public byte[] template() throws Exception {
        String[] header = {"申请类型","商品编码","商品名称","规格型号","品牌","生产厂家","供应商","单位",
                "采购价","零售价","最小采购量","采购单位","中包装数量","UDI编码","注册证号","注册证有效期",
                "生产许可证号","经营许可证号","合同编码","招采子编码","一级分类","二级分类","三级分类",
                "是否带量","是否集采","是否国产","是否收费","是否高值耗材","是否冷链","是否定数管理",
                "储存条件","附件数量","变更原因","重点监控"};
        Object[] example = {"新品准入","P-NEW-003","一次性使用输液器","0.55mm","康莱德","康德莱器械","九州通","支",
                1.60,3.20,1,"盒",1,"(01)06901234567890","械注准20260003","2029-12-31","XK-2025-00123",
                "JJ-2025-00456","HT-2025-888","TENDER-SUB-001","一级分类","二级分类","三级分类","是","否",
                "是","是","否","否","是","常温",0,"","是"};
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("模板");
            Row headerRow = sheet.createRow(0);
            Row exampleRow = sheet.createRow(1);
            for (int index = 0; index < header.length; index++) {
                headerRow.createCell(index).setCellValue(header[index]);
                Object value = example[index];
                if (value instanceof Number number) exampleRow.createCell(index).setCellValue(number.doubleValue());
                else exampleRow.createCell(index).setCellValue(String.valueOf(value));
                sheet.autoSizeColumn(index);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public ImportOutcome importApplications(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择待审批目录导入文件");
        int imported = 0, skipped = 0, duplicates = 0, total = 0;
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeaderIndexes(sheet.getRow(0));
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                total++;
                String code = string(row, column(columns, 1, "商品编码"), "");
                String name = string(row, column(columns, 2, "商品名称"), "");
                String spec = string(row, column(columns, 3, "规格型号"), "");
                if (isBlank(code) || isBlank(name) || isBlank(spec)) { skipped++; continue; }
                try {
                    productApprovalService.createApplication(request(row, columns, code, name, spec));
                    imported++;
                } catch (Exception exception) {
                    skipped++;
                    if (exception.getMessage() != null && exception.getMessage().contains("商品目录已存在")) duplicates++;
                    log.warn("Skipped import row {}: {}", total, exception.getMessage());
                }
            }
        }
        String error = null;
        if (total > 0 && imported == 0) {
            error = duplicates > 0
                    ? "导入失败：存在已在目录中的商品，请检查商品名称、规格型号、单价、注册证号、厂家、供应商是否重复"
                    : "导入失败：请确认文件内容和列顺序与导入模板一致，且商品编码、商品名称、规格型号为必填项";
        }
        String message = total == 0 ? "Excel 文件中未找到有效数据行，请确认文件内容" : "已导入 " + imported + " 条待审批单";
        if (skipped > 0 && total > 0) message += "，" + skipped + " 行因数据格式问题被跳过";
        return new ImportOutcome(Map.of("importedRows", imported, "skippedRows", skipped,
                "duplicateRows", duplicates, "message", message), error);
    }

    private static PendingProductApplicationRequest request(Row row, Map<String, Integer> c,
                                                            String code, String name, String spec) {
        return new PendingProductApplicationRequest(
                string(row,column(c,0,"申请类型"),"新品准入"), code, name, spec,
                string(row,column(c,4,"品牌"),""), string(row,column(c,5,"生产厂家","厂家"),""),
                string(row,column(c,6,"供应商"),""), string(row,column(c,7,"单位"),"支"),
                decimal(row,column(c,8,"采购价"),BigDecimal.ZERO), decimal(row,column(c,9,"零售价"),null),
                decimal(row,column(c,10,"最小采购量"),BigDecimal.ONE), string(row,column(c,11,"采购单位"),""),
                decimal(row,column(c,12,"换算系数","中包装数量"),BigDecimal.ONE), null,
                string(row,column(c,13,"UDI编码","UDI 编码"),""), string(row,column(c,14,"注册证号"),""),
                string(row,column(c,15,"注册证有效期"),""), string(row,column(c,16,"生产许可证号"),""),
                string(row,column(c,17,"经营许可证号"),""), bool(row,column(c,23,"是否带量"),false),
                bool(row,column(c,24,"是否集采"),false), bool(row,column(c,25,"是否国产"),false),
                string(row,column(c,18,"合同编码"),""), string(row,column(c,20,"一级分类"),""),
                string(row,column(c,21,"二级分类"),""), string(row,column(c,22,"三级分类"),""),
                bool(row,column(c,26,"是否收费"),false), string(row,column(c,19,"招采子编码"),""), 0,
                bool(row,column(c,27,"是否高值耗材"),false), bool(row,column(c,28,"是否冷链"),false),
                bool(row,column(c,29,"是否定数管理","定数管理"),false), bool(row,column(c,33,"重点监控"),false),
                string(row,column(c,30,"储存条件"),"常温"), string(row,column(c,32,"变更原因"),""));
    }

    private static Map<String,Integer> readHeaderIndexes(Row row) {
        if (row == null) return Collections.emptyMap();
        Map<String,Integer> result = new HashMap<>();
        for (Cell cell : row) {
            String value = string(row, cell.getColumnIndex(), "");
            if (!value.isBlank()) result.put(normalize(value), cell.getColumnIndex());
        }
        return result;
    }
    private static int column(Map<String,Integer> columns, int fallback, String... names) {
        for (String name : names) if (columns.containsKey(normalize(name))) return columns.get(normalize(name));
        return columns.isEmpty() ? fallback : -1;
    }
    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").trim(); }
    private static String string(Row row, int index, String fallback) {
        if (index < 0 || row.getCell(index) == null) return fallback;
        Cell cell = row.getCell(index);
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> cell.getBooleanCellValue() ? "是" : "否";
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
        return value.isBlank() ? fallback : value.trim();
    }
    private static boolean bool(Row row, int index, boolean fallback) {
        String value = string(row,index,fallback ? "是" : "否");
        return "是".equals(value) || "1".equals(value) || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value);
    }
    private static BigDecimal decimal(Row row, int index, BigDecimal fallback) {
        String value = string(row,index,"");
        if (value.isBlank()) return fallback;
        try { return new BigDecimal(value); } catch (NumberFormatException exception) { return fallback; }
    }

    public record ImportOutcome(Map<String,Object> data, String error) {}
}
