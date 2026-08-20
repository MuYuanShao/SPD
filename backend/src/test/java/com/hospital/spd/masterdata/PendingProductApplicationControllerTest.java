package com.hospital.spd.masterdata;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.masterdata.service.ProductApprovalService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingProductApplicationControllerTest {

    @Test
    @DisplayName("导入待审批目录时按表头映射字段")
    void should_import_pending_product_application_by_header_names() throws Exception {
        ProductApprovalService service = mock(ProductApprovalService.class);
        when(service.createApplication(any(PendingProductApplicationRequest.class)))
                .thenReturn(Map.of("applicationNo", "SP20260622001"));
        PendingProductApplicationController controller = new PendingProductApplicationController(service);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "待审批目录导入模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookWithReorderedColumns()
        );

        ApiResponse<Map<String, Object>> response = controller.importApplications(file);

        assertThat(response.code()).isZero();
        assertThat(response.data()).containsEntry("importedRows", 1);

        ArgumentCaptor<PendingProductApplicationRequest> captor =
                ArgumentCaptor.forClass(PendingProductApplicationRequest.class);
        verify(service).createApplication(captor.capture());
        PendingProductApplicationRequest request = captor.getValue();
        assertThat(request.manufacturerName()).isEqualTo("测试厂家");
        assertThat(request.purchaseUnit()).isEqualTo("盒");
        assertThat(request.registrationNo()).isEqualTo("械注准20260003");
        assertThat(request.quotaManaged()).isTrue();
        assertThat(request.purchasePrice()).isEqualByComparingTo(new BigDecimal("1.6"));
    }

    @Test
    @DisplayName("导入待审批目录时重复目录返回明确错误")
    void should_return_duplicate_error_when_imported_catalog_exists() throws Exception {
        ProductApprovalService service = mock(ProductApprovalService.class);
        when(service.createApplication(any(PendingProductApplicationRequest.class)))
                .thenThrow(new IllegalArgumentException("商品目录已存在"));
        PendingProductApplicationController controller = new PendingProductApplicationController(service);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "待审批目录导入模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookWithReorderedColumns()
        );

        ApiResponse<Map<String, Object>> response = controller.importApplications(file);

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.message()).contains("已在目录中的商品");
    }

    private byte[] workbookWithReorderedColumns() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("模板");
            var header = sheet.createRow(0);
            var data = sheet.createRow(1);
            String[] headers = {
                    "商品编码", "商品名称", "规格型号", "采购单位", "注册证号", "是否定数管理",
                    "生产厂家", "采购价", "单位", "申请类型"
            };
            Object[] values = {
                    "P-NEW-003", "一次性使用输液器", "0.55mm", "盒", "械注准20260003", "是",
                    "测试厂家", 1.6, "支", "新品准入"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                if (values[i] instanceof Number number) {
                    data.createCell(i).setCellValue(number.doubleValue());
                } else {
                    data.createCell(i).setCellValue(String.valueOf(values[i]));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
