package com.hospital.spd.integration;

import com.hospital.spd.common.RbacAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates only the dedicated MCP receiver using a hospital-bound shared secret and persisted service operator. */
@Configuration
public class McpReceiveSecurity {
  @Bean @Order(1)
  SecurityFilterChain mcpReceiveChain(HttpSecurity http,Environment env,JdbcTemplate db,RbacAuthorizationService rbac) throws Exception {
    var filter=new OncePerRequestFilter() {
      @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String secret=env.getProperty("SPD_MCP_SHARED_TOKEN","");
        String auth=req.getHeader("Authorization");
        if(!env.getProperty("SPD_MCP_ENABLED",Boolean.class,false) || secret.isBlank() || auth==null || !auth.startsWith("Bearer ") ||
            !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),auth.substring(7).getBytes(StandardCharsets.UTF_8))) {
          res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"state\":\"failed\",\"message\":\"MCP对接未启用或鉴权失败\"}");return;
        }
        Long operator=env.getProperty("SPD_MCP_OPERATOR_ID",Long.class,0L);
        var users=db.queryForList("SELECT u.user_id,u.username,u.dept_id,COALESCE((SELECT MIN(r.data_scope) FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.role_id WHERE ur.user_id=u.user_id AND r.status=1 AND r.deleted=0),4) AS data_scope FROM sys_user u WHERE u.user_id=? AND u.status=1 AND u.deleted=0",operator);
        if(users.size()!=1) {res.sendError(403,"MCP operator missing or disabled");return;}
        var user=users.get(0);
        var roles=db.queryForList("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.role_id WHERE ur.user_id=? AND r.status=1 AND r.deleted=0",String.class,operator);
        List<String> normalized=roles.stream().map(r->r.startsWith("ROLE_")?r:"ROLE_"+r.toUpperCase()).toList();
        req.setAttribute("currentUserId",operator);req.setAttribute("currentUsername",user.get("username"));req.setAttribute("currentDeptId",user.get("dept_id"));
        req.setAttribute("currentDataScope",user.get("data_scope"));req.setAttribute("currentRoles",normalized);req.setAttribute("currentPermissions",rbac.permissionCodes(operator));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.get("username"),null,normalized.stream().map(SimpleGrantedAuthority::new).toList()));
        try {chain.doFilter(req,res);} finally {SecurityContextHolder.clearContext();}
      }
    };
    return http.securityMatcher("/mcp/receive").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a->a.anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
  }
}
