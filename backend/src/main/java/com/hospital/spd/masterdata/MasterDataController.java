package com.hospital.spd.masterdata;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.masterdata.service.CampusService;
import com.hospital.spd.masterdata.service.DepartmentWarehouseCatalogService;
import com.hospital.spd.masterdata.service.DepartmentService;
import com.hospital.spd.masterdata.service.ManufacturerService;
import com.hospital.spd.masterdata.service.ProductService;
import com.hospital.spd.masterdata.service.SupplierService;
import com.hospital.spd.masterdata.service.WarehouseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Routes master-data requests to product, supplier, manufacturer, department, and warehouse services.
 */
@RestController
@RequestMapping("/master-data")
public class MasterDataController {

    private final ProductService productService;
    private final SupplierService supplierService;
    private final ManufacturerService manufacturerService;
    private final CampusService campusService;
    private final DepartmentService departmentService;
    private final DepartmentWarehouseCatalogService departmentWarehouseCatalogService;
    private final WarehouseService warehouseService;

    public MasterDataController(ProductService productService, SupplierService supplierService,
                                ManufacturerService manufacturerService, CampusService campusService,
                                DepartmentService departmentService,
                                DepartmentWarehouseCatalogService departmentWarehouseCatalogService,
                                WarehouseService warehouseService) {
        this.productService = productService;
        this.supplierService = supplierService;
        this.manufacturerService = manufacturerService;
        this.campusService = campusService;
        this.departmentService = departmentService;
        this.departmentWarehouseCatalogService = departmentWarehouseCatalogService;
        this.warehouseService = warehouseService;
    }

    // ==================== Products ====================

    @GetMapping("/hospital-products")
    public ApiResponse<MasterDataPage> hospitalProducts(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(productService.hospitalProducts(params));
    }
    @GetMapping("/hospital-products/partner-options")
    public ApiResponse<Map<String, List<Map<String, Object>>>> hospitalProductPartnerOptions() {
        return ApiResponse.ok(productService.partnerOptions());
    }


    @PostMapping("/hospital-products")
    public ApiResponse<Map<String, Object>> createHospitalProduct(@RequestBody ProductCreateRequest request) {
        return ApiResponse.ok(productService.createHospitalProduct(request));
    }

    @PutMapping("/hospital-products/{productCode}")
    public ApiResponse<Map<String, Object>> updateHospitalProduct(@PathVariable String productCode,
                                                                  @RequestBody ProductCreateRequest request) {
        return ApiResponse.ok(productService.updateHospitalProduct(productCode, request));
    }

    @PutMapping("/hospital-products/batch-edit")
    public ApiResponse<Map<String, Object>> batchUpdateHospitalProducts(@RequestBody ProductBatchUpdateRequest request) {
        return ApiResponse.ok(productService.batchUpdateHospitalProducts(request));
    }

    @PutMapping("/hospital-products/status")
    public ApiResponse<Map<String, Object>> updateHospitalProductStatus(@RequestBody ProductStatusUpdateRequest request) {
        return ApiResponse.ok(productService.updateHospitalProductStatus(request));
    }

    @PostMapping("/hospital-products/submit")
    public ApiResponse<Map<String, Object>> submitHospitalProducts(@RequestBody ProductSubmitRequest request) {
        return ApiResponse.ok(productService.submitHospitalProducts(request));
    }

    @GetMapping("/hospital-products/export")
    public ApiResponse<List<Map<String, Object>>> exportHospitalProducts(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(productService.exportHospitalProducts(params));
    }

    @GetMapping("/hospital-products/{productCode}")
    public ApiResponse<ProductDetail> hospitalProductDetail(@PathVariable String productCode) {
        return ApiResponse.ok(productService.hospitalProductDetail(productCode));
    }

    // ==================== Suppliers ====================

    @GetMapping("/suppliers")
    public ApiResponse<MasterDataPage> suppliers(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(supplierService.suppliers(params));
    }

    @PostMapping("/suppliers")
    public ApiResponse<Map<String, Object>> createSupplier(@RequestBody SupplierUpsertRequest request) {
        return ApiResponse.ok(supplierService.createSupplier(request));
    }

    @PutMapping("/suppliers/{supplierCode}")
    public ApiResponse<Map<String, Object>> updateSupplier(@PathVariable String supplierCode,
                                                           @RequestBody SupplierUpsertRequest request) {
        return ApiResponse.ok(supplierService.updateSupplier(supplierCode, request));
    }

    @PutMapping("/suppliers/status")
    public ApiResponse<Map<String, Object>> updateSupplierStatus(@RequestBody SupplierStatusUpdateRequest request) {
        return ApiResponse.ok(supplierService.updateSupplierStatus(request));
    }

    @GetMapping("/suppliers/export")
    public ApiResponse<List<Map<String, Object>>> exportSuppliers(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(supplierService.exportSuppliers(params));
    }

    @PostMapping("/suppliers/import")
    public ApiResponse<Map<String, Object>> importSuppliers(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择供应商导入文件");
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            return ApiResponse.ok(supplierService.importSuppliers(reader));
        }
    }

    // ==================== Manufacturers ====================

    @GetMapping("/manufacturers")
    public ApiResponse<MasterDataPage> manufacturers(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(manufacturerService.manufacturers(params));
    }

    @PostMapping("/manufacturers")
    public ApiResponse<Map<String, Object>> createManufacturer(@RequestBody ManufacturerUpsertRequest request) {
        return ApiResponse.ok(manufacturerService.createManufacturer(request));
    }

    @PutMapping("/manufacturers/{manufacturerCode}")
    public ApiResponse<Map<String, Object>> updateManufacturer(@PathVariable String manufacturerCode,
                                                               @RequestBody ManufacturerUpsertRequest request) {
        return ApiResponse.ok(manufacturerService.updateManufacturer(manufacturerCode, request));
    }

    @PutMapping("/manufacturers/status")
    public ApiResponse<Map<String, Object>> updateManufacturerStatus(@RequestBody ManufacturerStatusUpdateRequest request) {
        return ApiResponse.ok(manufacturerService.updateManufacturerStatus(request));
    }

    @GetMapping("/manufacturers/export")
    public ApiResponse<List<Map<String, Object>>> exportManufacturers(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(manufacturerService.exportManufacturers(params));
    }

    @PostMapping("/manufacturers/import")
    public ApiResponse<Map<String, Object>> importManufacturers(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择厂家导入文件");
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            return ApiResponse.ok(manufacturerService.importManufacturers(reader));
        }
    }

    // ==================== Campuses ====================

    @GetMapping("/campuses")
    public ApiResponse<MasterDataPage> campuses(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(campusService.campuses(params));
    }

    @GetMapping("/campuses/options")
    public ApiResponse<List<Map<String, Object>>> campusOptions() {
        return ApiResponse.ok(campusService.campusOptions());
    }

    @PostMapping("/campuses")
    public ApiResponse<Map<String, Object>> createCampus(@RequestBody CampusUpsertRequest request) {
        return ApiResponse.ok(campusService.createCampus(request));
    }

    @PutMapping("/campuses/{campusCode}")
    public ApiResponse<Map<String, Object>> updateCampus(@PathVariable String campusCode,
                                                         @RequestBody CampusUpsertRequest request) {
        return ApiResponse.ok(campusService.updateCampus(campusCode, request));
    }

    @PutMapping("/campuses/delete")
    public ApiResponse<Map<String, Object>> deleteCampuses(@RequestBody CampusCodesRequest request) {
        return ApiResponse.ok(campusService.deleteCampuses(request));
    }

    // ==================== Departments ====================

    @GetMapping("/departments")
    public ApiResponse<MasterDataPage> departments(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(departmentService.departments(params));
    }

    @PostMapping("/departments")
    public ApiResponse<Map<String, Object>> createDepartment(@RequestBody DepartmentUpsertRequest request) {
        return ApiResponse.ok(departmentService.createDepartment(request));
    }

    @PutMapping("/departments/{deptCode}")
    public ApiResponse<Map<String, Object>> updateDepartment(@PathVariable String deptCode,
                                                             @RequestBody DepartmentUpsertRequest request) {
        return ApiResponse.ok(departmentService.updateDepartment(deptCode, request));
    }

    @PutMapping("/departments/delete")
    public ApiResponse<Map<String, Object>> deleteDepartments(@RequestBody DepartmentCodesRequest request) {
        return ApiResponse.ok(departmentService.deleteDepartments(request));
    }

    @GetMapping("/departments/export")
    public ApiResponse<List<Map<String, Object>>> exportDepartments(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(departmentService.exportDepartments(params));
    }

    @GetMapping("/departments/{deptCode}/warehouses")
    public ApiResponse<List<Map<String, Object>>> departmentWarehouses(@PathVariable String deptCode) {
        return ApiResponse.ok(departmentService.departmentWarehouses(deptCode));
    }

    @PutMapping("/departments/{deptCode}/warehouses")
    public ApiResponse<Map<String, Object>> updateDepartmentWarehouses(
            @PathVariable String deptCode,
            @RequestBody DepartmentWarehouseRelationRequest request) {
        return ApiResponse.ok(departmentService.updateDepartmentWarehouses(deptCode, request));
    }

    @PostMapping("/departments/import")
    public ApiResponse<Map<String, Object>> importDepartments(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择科室导入文件");
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            return ApiResponse.ok(departmentService.importDepartments(reader));
        }
    }

    // ==================== Department Warehouse Catalogs ====================

    @GetMapping("/department-warehouse-catalogs")
    public ApiResponse<MasterDataPage> departmentWarehouseCatalogs(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(departmentWarehouseCatalogService.catalogs(params));
    }

    @GetMapping("/department-warehouse-catalogs/product-options")
    public ApiResponse<List<Map<String, Object>>> departmentWarehouseCatalogProductOptions(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(departmentWarehouseCatalogService.catalogProductOptions(deptName, warehouseName, keyword));
    }

    @PostMapping("/department-warehouse-catalogs")
    public ApiResponse<Map<String, Object>> createDepartmentWarehouseCatalog(
            @RequestBody DepartmentWarehouseCatalogUpsertRequest request) {
        return ApiResponse.ok(departmentWarehouseCatalogService.createCatalog(request));
    }

    @PostMapping("/department-warehouse-catalogs/batch")
    public ApiResponse<Map<String, Object>> batchMaintainDepartmentWarehouseCatalogs(
            @RequestBody DepartmentWarehouseCatalogBatchRequest request) {
        return ApiResponse.ok(departmentWarehouseCatalogService.batchMaintainCatalogs(request));
    }

    @PutMapping("/department-warehouse-catalogs/{catalogId}")
    public ApiResponse<Map<String, Object>> updateDepartmentWarehouseCatalog(
            @PathVariable Long catalogId,
            @RequestBody DepartmentWarehouseCatalogUpsertRequest request) {
        return ApiResponse.ok(departmentWarehouseCatalogService.updateCatalog(catalogId, request));
    }

    @PutMapping("/department-warehouse-catalogs/delete")
    public ApiResponse<Map<String, Object>> deleteDepartmentWarehouseCatalogs(
            @RequestBody DepartmentWarehouseCatalogIdsRequest request) {
        return ApiResponse.ok(departmentWarehouseCatalogService.deleteCatalogs(request));
    }

    @PutMapping("/department-warehouse-catalogs/status")
    public ApiResponse<Map<String, Object>> updateDepartmentWarehouseCatalogStatus(
            @RequestBody DepartmentWarehouseCatalogStatusRequest request) {
        return ApiResponse.ok(departmentWarehouseCatalogService.updateCatalogStatus(request));
    }

    // ==================== Warehouses ====================

    @GetMapping("/warehouses")
    public ApiResponse<MasterDataPage> warehouses(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(warehouseService.warehouses(params));
    }

    @GetMapping("/warehouses/product-options")
    public ApiResponse<List<Map<String, Object>>> warehouseProductOptions() {
        return ApiResponse.ok(warehouseService.warehouseProductOptions());
    }

    @GetMapping("/warehouses/{warehouseCode}/products")
    public ApiResponse<List<Map<String, Object>>> warehouseProducts(@PathVariable String warehouseCode) {
        return ApiResponse.ok(warehouseService.warehouseProducts(warehouseCode));
    }

    @PostMapping("/warehouses")
    public ApiResponse<Map<String, Object>> createWarehouse(@RequestBody WarehouseUpsertRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouse(request));
    }

    @PutMapping("/warehouses/{warehouseCode}")
    public ApiResponse<Map<String, Object>> updateWarehouse(@PathVariable String warehouseCode,
                                                            @RequestBody WarehouseUpsertRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouse(warehouseCode, request));
    }

    @PutMapping("/warehouses/delete")
    public ApiResponse<Map<String, Object>> deleteWarehouses(@RequestBody WarehouseCodesRequest request) {
        return ApiResponse.ok(warehouseService.deleteWarehouses(request));
    }

    @GetMapping("/warehouses/export")
    public ApiResponse<List<Map<String, Object>>> exportWarehouses(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(warehouseService.exportWarehouses(params));
    }

    @GetMapping("/warehouses/{warehouseCode}/locations")
    public ApiResponse<List<Map<String, Object>>> warehouseLocations(@PathVariable String warehouseCode) {
        return ApiResponse.ok(warehouseService.warehouseLocations(warehouseCode));
    }

    @PostMapping("/warehouses/{warehouseCode}/locations")
    public ApiResponse<Map<String, Object>> createWarehouseLocation(@PathVariable String warehouseCode,
                                                                    @RequestBody WarehouseLocationUpsertRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouseLocation(warehouseCode, request));
    }

    @PutMapping("/warehouses/{warehouseCode}/locations/{locationId}")
    public ApiResponse<Map<String, Object>> updateWarehouseLocation(@PathVariable String warehouseCode,
                                                                    @PathVariable Long locationId,
                                                                    @RequestBody WarehouseLocationUpsertRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouseLocation(warehouseCode, locationId, request));
    }

    @DeleteMapping("/warehouses/{warehouseCode}/locations/{locationId}")
    public ApiResponse<Map<String, Object>> deleteWarehouseLocation(@PathVariable String warehouseCode,
                                                                    @PathVariable Long locationId) {
        return ApiResponse.ok(warehouseService.deleteWarehouseLocation(warehouseCode, locationId));
    }

    @PostMapping("/warehouses/import")
    public ApiResponse<Map<String, Object>> importWarehouses(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择库房导入文件");
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            return ApiResponse.ok(warehouseService.importWarehouses(reader));
        }
    }
}
