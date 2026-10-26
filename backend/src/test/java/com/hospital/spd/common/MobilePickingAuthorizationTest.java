package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Checks mobile command routing without broadening any account's persisted grants. */
class MobilePickingAuthorizationTest {
    @Test void orderRoutesStayUnderPickingPermission() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);var service=new RbacAuthorizationService(jdbc);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(81L))).thenReturn(List.of("department-requisition:pick"));
        when(jdbc.queryForList(anyString(),eq(String.class),eq(82L))).thenReturn(List.of("picking-delivery:write"));
        for(String path:List.of("/api/mobile/picking/orders","/api/mobile/picking/orders/5")){
            assertThat(service.isAllowed(81L,List.of(),"GET",path)).isTrue();assertThat(service.isAllowed(82L,List.of(),"GET",path)).isFalse();
        }
        assertThat(service.isAllowed(81L,List.of(),"POST","/api/mobile/picking/orders/5/scans/resolve")).isTrue();
        assertThat(service.isAllowed(82L,List.of(),"POST","/api/mobile/picking/orders/5/scans/resolve")).isFalse();
        assertThat(service.isAllowed(81L,List.of(),"DELETE","/api/mobile/picking/orders/5")).isFalse();
    }
    @Test void eachMobileOperationRequiresItsOwnPermissionAndKnownMethod() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);var service=new RbacAuthorizationService(jdbc);
        var paths=List.of("/api/mobile/operations/recover","/api/mobile/picking/operations/recover","/api/mobile/consumption/operations/recover");
        var permissions=List.of("picking-delivery:write","department-requisition:pick","department-consumption:write");
        for(int i=0;i<3;i++) {
            when(jdbc.queryForList(anyString(),eq(String.class),eq(80L))).thenReturn(List.of(permissions.get(i)));
            for(int j=0;j<3;j++) {
                assertThat(service.isAllowed(80L,List.of(),"POST",paths.get(j))).isEqualTo(i==j);
                assertThat(service.isAllowed(80L,List.of(),"GET",paths.get(j))).isFalse();
            }
        }
        assertThat(service.isAllowed(80L,List.of(),"POST","/api/mobile/picking/unknown")).isFalse();
    }
}
