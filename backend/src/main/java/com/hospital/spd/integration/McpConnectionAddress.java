package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;

/** Validates the system-settings host and port and builds the fixed MCP API root. */
public final class McpConnectionAddress {
  public static final String CONFIG_KEY="integration.mcp-ui.connection";
  private McpConnectionAddress() {}
  public static String apiUrl(JsonNode value) {
    if(value==null || !value.isObject()) throw new IllegalArgumentException("MCP-UI连接配置必须为JSON对象");
    if(value.has("phase2Enabled") && !value.path("phase2Enabled").isBoolean()) throw new IllegalArgumentException("第二期同步开关必须为布尔值");
    String protocol=value.path("protocol").asText();String host=value.path("host").asText().trim();
    if(!java.util.Set.of("http","https").contains(protocol)) throw new IllegalArgumentException("连接协议必须为 HTTP 或 HTTPS");
    if(host.isBlank() || host.length()>253 || host.matches(".*[\\s/@?#].*")) throw new IllegalArgumentException("请输入有效的 IP 地址或域名，不包含协议和路径");
    JsonNode port=value.path("port");
    if(!port.isIntegralNumber() || !port.canConvertToInt() || port.asInt()<1 || port.asInt()>65535) throw new IllegalArgumentException("端口必须为1至65535的整数");
    try {return new URI(protocol,null,host,port.asInt(),"/api",null,null).toASCIIString();}
    catch(java.net.URISyntaxException e) {throw new IllegalArgumentException("IP 地址或域名格式不正确");}
  }
}
