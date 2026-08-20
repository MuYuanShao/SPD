package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the module tree displayed by the system shell and menu overview.
 */
@RestController
@RequestMapping("/modules")
public class ModuleController {

    @GetMapping
    public ApiResponse<List<SpdModule>> listModules() {
        return ApiResponse.ok(List.of(
                new SpdModule("foundation", "基础管理", List.of("登录认证", "用户管理", "角色权限管理", "科室管理")),
                new SpdModule("master-data", "主数据管理", List.of("商品目录管理", "待审批商品目录管理", "供应商管理", "厂家管理")),
                new SpdModule("supply-chain", "供应链业务", List.of("采购管理", "库存管理", "科室申领", "结算对账")),
                new SpdModule("specialty", "专项管理", List.of("UDI追溯", "高值耗材管理", "冷链监控")),
                new SpdModule("operations", "运营支撑", List.of("报表中心", "系统配置", "SaaS化与配置中心")),
                new SpdModule("hospital-extension", "院内SPD业务扩展", List.of("基础信息扩展", "请补货与采购扩展", "收货与上架管理", "拣配出库与配送", "库内作业管理", "科室业务管理", "退回业务管理", "手术临床管理", "结算与发票扩展")),
                new SpdModule("compliance-extension", "合规与扩展业务", List.of("多院区与组织扩展", "合同与招采管理", "供应商扩展管理", "库存扩展业务", "召回与质量闭环", "临床扩展业务", "财务与医保扩展", "决策支持扩展", "系统集成扩展"))
        ));
    }
}
