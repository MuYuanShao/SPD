package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpConnectionAddressTest {
  final ObjectMapper mapper=new ObjectMapper();
  @Test void buildsApiUrlForIpDomainAndIpv6() {
    assertEquals("http://192.168.1.20:8080/api",McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","http","host","192.168.1.20","port",8080))));
    assertEquals("https://mcp.example.com:443/api",McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","https","host","mcp.example.com","port",443))));
    assertEquals("http://[::1]:8080/api",McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","http","host","::1","port",8080))));
  }
  @Test void rejectsInvalidPortCredentialsPathsAndSchemes() {
    for(Object port:List.of(0,65536,1.5,"8080")) assertThrows(IllegalArgumentException.class,()->McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","http","host","localhost","port",port))));
    for(String host:List.of("http://localhost","user@localhost","localhost/path","localhost?key=value")) assertThrows(IllegalArgumentException.class,()->McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","http","host",host,"port",8080))));
    assertThrows(IllegalArgumentException.class,()->McpConnectionAddress.apiUrl(mapper.valueToTree(Map.of("protocol","file","host","localhost","port",8080))));
  }
  @Test void savedSettingsTakePriorityAndDisabledSettingsDoNotFallBack() throws Exception {
    var db=mock(JdbcTemplate.class);var env=new MockEnvironment().withProperty("SPD_MCP_UI_URL","http://old-host:8080/api");
    var job=new McpUploadJob(db,mapper,env);
    when(db.queryForList(anyString(),eq(McpConnectionAddress.CONFIG_KEY))).thenReturn(List.of(Map.of("status",1,"config_value","{\"protocol\":\"http\",\"host\":\"192.168.1.30\",\"port\":9000}")));
    assertEquals("http://192.168.1.30:9000/api",job.connectionUrl());
    when(db.queryForList(anyString(),eq(McpConnectionAddress.CONFIG_KEY))).thenReturn(List.of(Map.of("status",0,"config_value","{}")));
    assertThrows(IllegalArgumentException.class,job::connectionUrl);
    when(db.queryForList(anyString(),eq(McpConnectionAddress.CONFIG_KEY))).thenReturn(List.of());
    assertEquals("http://old-host:8080/api",job.connectionUrl());
  }
}
