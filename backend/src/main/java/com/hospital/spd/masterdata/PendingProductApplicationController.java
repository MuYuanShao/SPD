package com.hospital.spd.masterdata;

import com.hospital.spd.common.ApiResponse;
import static com.hospital.spd.common.SqlHelper.isBlank;
import com.hospital.spd.masterdata.service.ProductApprovalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes product approval workflow endpoints while keeping approval rules in ProductApprovalService.
 */
@RestController
@RequestMapping("/pending-product-applications")
public class PendingProductApplicationController {

    private static final Logger log = LoggerFactory.getLogger(PendingProductApplicationController.class);

    private final ProductApprovalService productApprovalService;

    public PendingProductApplicationController(ProductApprovalService productApprovalService) {
        this.productApprovalService = productApprovalService;
    }

    @GetMapping("/partner-options")
    public ApiResponse<Map<String, List<Map<String, Object>>>> partnerOptions() {
        return ApiResponse.ok(productApprovalService.partnerOptions());
    }

    @GetMapping("/{applicationNo}")
    public ApiResponse<PendingProductApplicationDetail> detail(@PathVariable String applicationNo) {
        return ApiResponse.ok(productApprovalService.getDetail(applicationNo));
    }

    @GetMapping
    public ApiResponse<PendingProductApplicationPage> list(
            @RequestParam(defaultValue = "new") String type,
            @RequestParam(defaultValue = "todo") String scope,
            @RequestParam(defaultValue = "pending") String mineStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam Map<String, String> params
    ) {
        return ApiResponse.ok(productApprovalService.listApplications(type, scope, mineStatus, keyword, params));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.createApplication(request));
    }

    @PutMapping("/{applicationNo}/action")
    public ApiResponse<Map<String, Object>> action(@PathVariable String applicationNo,
                                                   @RequestBody PendingProductApprovalActionRequest request) {
        return ApiResponse.ok(productApprovalService.processAction(applicationNo, request));
    }

    @PutMapping("/batch-action")
    public ApiResponse<Map<String, Object>> batchAction(@RequestBody PendingProductBatchApprovalRequest request) {
        if (request.applicationNos() == null || request.applicationNos().isEmpty()) {
            return ApiResponse.error(400, "请选择至少一条审批单");
        }
        return ApiResponse.ok(productApprovalService.batchProcessAction(
                request.applicationNos(),
                new PendingProductApprovalActionRequest(request.action(), request.opinion())
        ));
    }

    @PutMapping("/{applicationNo}/update")
    public ApiResponse<Map<String, Object>> updateApplication(@PathVariable String applicationNo,
                                                              @RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.updateApplicationData(applicationNo, request));
    }

    @PutMapping("/{applicationNo}/resubmit")
    public ApiResponse<Map<String, Object>> resubmit(@PathVariable String applicationNo,
                                                     @RequestBody PendingProductApplicationRequest request) {
        return ApiResponse.ok(productApprovalService.resubmitApplication(applicationNo, request));
    }

    @GetMapping("/export")
    public ApiResponse<List<PendingProductApplicationRow>> export(@RequestParam(defaultValue = "new") String type,
                                                                  @RequestParam(defaultValue = "todo") String scope,
                                                                  @RequestParam(defaultValue = "pending") String mineStatus,
                                                                  @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productApprovalService.listApplications(type, scope, mineStatus, keyword).rows());
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importApplications(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择待审批目录导入文件");
        }
        int importedRows = 0;
        int skippedRows = 0;
        int duplicateRows = 0;
        int totalRows = 0;
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeaderIndexes(sheet.getRow(0));
            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // 跳过表头行（第 0 行）
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                totalRows++;
                String productCode = getCellString(row, column(columns, 1, "商品编码"), "");
                String productName = getCellString(row, column(columns, 2, "商品名称"), "");
                String specModel = getCellString(row, column(columns, 3, "规格型号"), "");
                if (isBlank(productCode) || isBlank(productName) || isBlank(specModel)) {
                    skippedRows++;
                    continue;
                }
                try {
                    productApprovalService.createApplication(new PendingProductApplicationRequest(
                            getCellString(row, column(columns, 0, "申请类型"), "新品准入"),
                            productCode,                              // 商品编码
                            productName,                              // 商品名称
                            specModel,                                // 规格型号
                            getCellString(row, column(columns, 4, "品牌"), ""),
                            getCellString(row, column(columns, 5, "生产厂家", "厂家"), ""),
                            getCellString(row, column(columns, 6, "供应商"), ""),
                            getCellString(row, column(columns, 7, "单位"), "支"),
                            getCellDecimal(row, column(columns, 8, "采购价"), BigDecimal.ZERO),
                            getCellDecimal(row, column(columns, 9, "零售价"), null),
                            getCellDecimal(row, column(columns, 10, "最小采购量"), BigDecimal.ONE),
                            getCellString(row, column(columns, 11, "采购单位"), ""),
                            getCellDecimal(row, column(columns, 12, "换算系数", "中包装数量"), BigDecimal.ONE),
                            null,                                     // 采购包装数量（导入暂不支持）
                            getCellString(row, column(columns, 13, "UDI编码", "UDI 编码"), ""),
                            getCellString(row, column(columns, 14, "注册证号"), ""),
                            getCellString(row, column(columns, 15, "注册证有效期"), ""),
                            getCellString(row, column(columns, 16, "生产许可证号"), ""),
                            getCellString(row, column(columns, 17, "经营许可证号"), ""),
                            getCellBoolean(row, column(columns, 23, "是否带量"), false),
                            getCellBoolean(row, column(columns, 24, "是否集采"), false),
                            getCellBoolean(row, column(columns, 25, "是否国产"), false),
                            getCellString(row, column(columns, 18, "合同编码"), ""),
                            getCellString(row, column(columns, 20, "一级分类"), ""),
                            getCellString(row, column(columns, 21, "二级分类"), ""),
                            getCellString(row, column(columns, 22, "三级分类"), ""),
                            getCellBoolean(row, column(columns, 26, "是否收费"), false),
                            getCellString(row, column(columns, 19, "招采子编码"), ""),
                            getCellInt(row, column(columns, 31, "附件数量"), 0),
                            getCellBoolean(row, column(columns, 27, "是否高值耗材"), false),
                            getCellBoolean(row, column(columns, 28, "是否冷链"), false),
                            getCellBoolean(row, column(columns, 29, "是否定数管理", "定数管理"), false),
                            getCellString(row, column(columns, 30, "储存条件"), "常温"),
                            getCellString(row, column(columns, 32, "变更原因"), "")
                    ));
                    importedRows++;
                } catch (Exception e) {
                    skippedRows++;
                    if (e.getMessage() != null && e.getMessage().contains("商品目录已存在")) {
                        duplicateRows++;
                    }
                    log.warn("Skipped import row {}: {}", totalRows, e.getMessage());
                }
            }
        }
        if (totalRows > 0 && importedRows == 0) {
            if (duplicateRows > 0) {
                return ApiResponse.error(400, "导入失败：存在已在目录中的商品，请检查商品名称、规格型号、单价、注册证号、厂家、供应商是否重复");
            }
            return ApiResponse.error(400, "导入失败：请确认文件内容和列顺序与导入模板一致，且商品编码、商品名称、规格型号为必填项");
        }
        String msg = "已导入 " + importedRows + " 条待审批单";
        if (skippedRows > 0) {
            msg += "，" + skippedRows + " 行因数据格式问题被跳过";
        }
        if (totalRows == 0) {
            msg = "Excel 文件中未找到有效数据行，请确认文件内容";
        }
        return ApiResponse.ok(Map.of(
                "importedRows", importedRows,
                "skippedRows", skippedRows,
                "duplicateRows", duplicateRows,
                "message", msg
        ));
    }

    private static Map<String, Integer> readHeaderIndexes(Row headerRow) {
        if (headerRow == null) {
            return Collections.emptyMap();
        }
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = getCellString(headerRow, cell.getColumnIndex(), "");
            if (!header.isBlank()) {
                indexes.put(normalizeHeader(header), cell.getColumnIndex());
            }
        }
        return indexes;
    }

    private static int column(Map<String, Integer> columns, int fallback, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalizeHeader(name));
            if (index != null) {
                return index;
            }
        }
        return columns.isEmpty() ? fallback : -1;
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("\\s+", "").trim();
    }

    /** 获取单元格字符串值，空时返回默认值 */
    private static String getCellString(Row row, int index, String defaultValue) {
        if (index < 0) {
            return defaultValue;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return defaultValue;
        }
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "是" : "否";
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
        return value.isBlank() ? defaultValue : value.trim();
    }

    /** 获取布尔字段，支持模板中的“是/否”、true/false、1/0。 */
    private static boolean getCellBoolean(Row row, int index, boolean defaultValue) {
        if (index < 0) {
            return defaultValue;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return defaultValue;
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        String value = getCellString(row, index, defaultValue ? "是" : "否").trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        return "是".equals(value)
                || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "y".equalsIgnoreCase(value)
                || "1".equals(value);
    }

    /** 获取单元格数值，空或非数字时返回默认值 */
    private static BigDecimal getCellDecimal(Row row, int index, BigDecimal defaultValue) {
        if (index < 0) {
            return defaultValue;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return defaultValue;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String val = cell.getStringCellValue().trim();
                    if (val.isBlank()) {
                        yield defaultValue;
                    }
                    try {
                        yield new BigDecimal(val);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse decimal from '{}' at row {}, using default", val, row.getRowNum() + 1);
                        yield defaultValue;
                    }
                }
                case BOOLEAN -> cell.getBooleanCellValue() ? BigDecimal.ONE : BigDecimal.ZERO;
                default -> defaultValue;
            };
        } catch (Exception e) {
            log.warn("Error reading decimal cell at row {}: {}", row.getRowNum() + 1, e.getMessage());
            return defaultValue;
        }
    }

    /** 获取单元格整数值，空或非数字时返回默认值 */
    private static int getCellInt(Row row, int index, int defaultValue) {
        if (index < 0) {
            return defaultValue;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return defaultValue;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> {
                    String val = cell.getStringCellValue().trim();
                    if (val.isBlank()) {
                        yield defaultValue;
                    }
                    try {
                        yield Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse int from '{}' at row {}, using default", val, row.getRowNum() + 1);
                        yield defaultValue;
                    }
                }
                case BOOLEAN -> cell.getBooleanCellValue() ? 1 : 0;
                default -> defaultValue;
            };
        } catch (Exception e) {
            log.warn("Error reading int cell at row {}: {}", row.getRowNum() + 1, e.getMessage());
            return defaultValue;
        }
    }
}
