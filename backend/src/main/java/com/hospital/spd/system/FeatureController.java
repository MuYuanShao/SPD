package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Serves feature metadata used by placeholder and menu-driven pages.
 */
@RestController
@RequestMapping("/features")
public class FeatureController {

    private static final Map<String, FeatureMetadata> FEATURES = Map.ofEntries(
            entry("supplier-management", "供应商管理", "供应商准入、资质、联系人、合同与绩效管理", List.of("供应商列表", "准入申请", "资质管理", "合同管理")),
            entry("hospital-product-catalog", "医院目录", "维护审批通过后的院内正式可用商品", List.of("商品列表", "价格信息", "资质信息", "仓储信息")),
            entry("manufacturer-management", "厂家管理", "维护生产厂家档案与生产许可信息", List.of("厂家列表", "厂家详情", "启停管理")),
            entry("department-management", "科室管理", "维护医院科室层级、负责人、院区与财务科室映射", List.of("科室树", "新增科室", "编辑科室")),
            entry("warehouse-location-management", "库房 / 货位管理", "维护中心库、二级库、虚拟库及货位", List.of("库房维护", "货位维护", "散货引导货位")),
            entry("purchase-management", "采购管理", "采购需求、采购计划、采购订单与订单跟踪", List.of("采购需求", "采购计划", "采购订单")),
            entry("inventory-management", "库存管理", "库存余额、批次、流水和库存预警", List.of("库存查询", "批次管理", "库存流水")),
            entry("settlement-reconciliation", "结算对账", "结算单生成、供应商对账和结算异常处理", List.of("结算单", "对账明细", "异常处理"))
            ,
            entry("batch-price-adjustment", "价格调整", "独立处理商品目录采购价与库存批次单价调整，保留调价影响清单和审批记录。", List.of("商品目录调价", "库存批次调价", "影响清单", "审批记录")),
            entry("user-management", "用户管理", "管理系统操作用户全生命周期，操作入口集中在用户列表查询界面。", List.of("用户列表查询", "用户状态管理", "角色关联维护", "操作审计")),
            entry("user-list-query", "用户列表查询", "按用户名、真实姓名、状态、科室筛选用户，并展示用户名、真实姓名、科室、角色、状态。", List.of("用户名筛选", "真实姓名筛选", "状态筛选", "科室筛选", "分页列表")),
            entry("user-create", "新增用户", "录入用户名、密码、真实姓名、手机号、邮箱、性别、所属科室、角色和启用状态。", List.of("基础信息", "所属科室", "角色多选", "状态开关")),
            entry("user-edit", "编辑用户", "修改用户资料，用户名不可修改。", List.of("真实姓名", "手机号", "邮箱", "性别", "所属科室", "角色", "状态")),
            entry("user-delete", "删除用户", "逻辑删除用户，限制删除当前登录用户和系统预置管理员。", List.of("单条删除", "批量删除", "删除限制校验")),
            entry("password-reset", "重置密码", "将用户密码重置为默认密码，并支持首次登录强制修改。", List.of("重置默认密码", "强制改密标记", "操作审计")),
            entry("user-role-assign", "分配角色", "为用户分配一个或多个角色，至少选择一个角色。", List.of("角色多选", "保存授权", "授权审计")),
            entry("role-permission", "角色权限", "基于 RBAC 管理系统角色、菜单权限、按钮权限与数据权限范围，操作入口集中在角色列表查询界面。", List.of("角色列表查询", "菜单权限树", "按钮权限", "数据范围控制")),
            entry("role-list-query", "角色列表查询", "按角色名称、角色编码、状态筛选角色。", List.of("角色名称筛选", "角色编码筛选", "状态筛选", "分页列表")),
            entry("role-create", "新增角色", "设置角色名称、角色编码、数据范围和状态。", List.of("角色名称", "角色编码", "数据范围", "状态")),
            entry("role-edit", "编辑角色", "修改角色名称、数据范围和状态，角色编码不可修改。", List.of("基础信息维护", "编码只读", "状态维护")),
            entry("role-delete", "删除角色", "限制删除系统预置角色和已被用户关联的角色。", List.of("单条删除", "关联校验", "预置角色保护")),
            entry("permission-assign", "分配权限", "为角色分配菜单权限和按钮权限，按权限树勾选保存。", List.of("菜单权限树", "按钮权限", "批量勾选", "保存授权")),
            entry("data-permission", "数据权限", "设置角色可查看的数据范围。", List.of("全部数据", "本部门数据", "本部门及子部门", "仅本人数据", "自定义部门"))
    );

    @GetMapping("/{code}")
    public ApiResponse<FeatureMetadata> getFeature(@PathVariable String code) {
        return ApiResponse.ok(FEATURES.getOrDefault(
                code,
                new FeatureMetadata(code, code, "该功能已接入后端元数据接口，业务数据接口待按模块继续补齐。", List.of())
        ));
    }

    private static Map.Entry<String, FeatureMetadata> entry(String code, String title, String description, List<String> capabilities) {
        return Map.entry(code, new FeatureMetadata(code, title, description, capabilities));
    }
}
