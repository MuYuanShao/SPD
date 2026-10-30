package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hospital.spd.common.service.AuditLogService;
import java.util.*;

/** Receives supplier collaboration receipts without changing settlement, payment or inventory facts. */
@Service
public class McpPhase3FeedbackService {
    private final JdbcTemplate db;private final Environment env;private final AuditLogService audit;
    public McpPhase3FeedbackService(JdbcTemplate db,Environment env,AuditLogService audit){this.db=db;this.env=env;this.audit=audit;}
    @Transactional public Map<String,Object> receive(String key,JsonNode request) {
        UUID.fromString(key);
        require(key.equals(request.path("messageId").asText()),"反馈消息编号不一致");
        require("MCP-UI".equals(request.path("sourceSystem").asText()) && "3.0".equals(request.path("schemaVersion").asText()),"反馈来源或协议不合法");
        require(!env.getProperty("SPD_MCP_ORGANIZATION","").isBlank() && env.getProperty("SPD_MCP_ORGANIZATION").equals(request.path("organization").asText()),"反馈机构不匹配");
        JsonNode payload=request.path("payload");long id=Long.parseLong(payload.path("settlementId").asText());
        long revision=Long.parseLong(payload.path("settlementRevision").asText());
        String supplier=payload.path("supplierCode").asText(),type=payload.path("feedbackType").asText();
        require(Set.of("received","disputed","withdrawn").contains(type),"反馈类型不合法");
        require(payload.path("reason").asText().length()<=1000 && payload.path("externalOperator").asText().length()<=100,"反馈内容过长");
        require(!type.equals("disputed") || !payload.path("reason").asText().isBlank(),"差异反馈必须填写原因");
        String hash=Phase3Json.hash(request);
        var old=db.queryForList("SELECT payload_hash FROM mcp_phase3_feedback WHERE message_id=?",key);
        if(!old.isEmpty()){require(hash.equals(old.get(0).get("payload_hash")),"同一反馈编号不能用于不同内容");return Map.of("state","processed","messageId",key);}
        var document=db.queryForMap("SELECT revision FROM mcp_phase3_document WHERE document_type='settlement' AND source_id=? FOR UPDATE",id);
        require(((Number)document.get("revision")).longValue()==revision,"结算版本已变化，请刷新后反馈");
        var bills=db.queryForList("SELECT s.supplier_code FROM settlement_bill bill JOIN supplier s ON s.supplier_id=bill.supplier_id WHERE bill.settlement_id=? FOR SHARE",String.class,id);
        require(bills.size()==1 && bills.get(0).equals(supplier),"反馈结算已撤销或供应商不匹配");
        // Recheck after the document lock so concurrent retries receive the original acknowledgement.
        old=db.queryForList("SELECT payload_hash FROM mcp_phase3_feedback WHERE message_id=? FOR SHARE",key);
        if(!old.isEmpty()){require(hash.equals(old.get(0).get("payload_hash")),"同一反馈编号不能用于不同内容");return Map.of("state","processed","messageId",key);}
        if(type.equals("withdrawn")) {
            String previous=payload.path("withdrawsMessageId").asText();UUID.fromString(previous);
            var originals=db.queryForList("SELECT payload,feedback_type FROM mcp_phase3_feedback WHERE message_id=? AND settlement_id=? AND supplier_code=? FOR SHARE",previous,id,supplier);
            require(originals.size()==1 && !"withdrawn".equals(originals.get(0).get("feedback_type")),"原反馈尚未接收或不能撤回");
            JsonNode original=Phase3Json.parse(originals.get(0).get("payload").toString()).path("payload");
            require(!payload.path("externalOperatorId").asText().isBlank() && original.path("externalOperatorId").asText().equals(payload.path("externalOperatorId").asText()),"只能撤回本人反馈");
            require(db.queryForObject("SELECT COUNT(*) FROM mcp_phase3_feedback WHERE feedback_type='withdrawn' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.withdrawsMessageId'))=?",Long.class,previous)==0,"原反馈已撤回");
        }
        db.update("INSERT INTO mcp_phase3_feedback(message_id,payload_hash,settlement_id,settlement_revision,supplier_code,feedback_type,payload) VALUES(?,?,?,?,?,?,CAST(? AS JSON))",key,hash,id,revision,supplier,type,Phase3Json.json(request));
        audit.record("mcp_settlement_feedback","receive",id,key,"供应商协同反馈："+type);
        return Map.of("state","processed","messageId",key);
    }
    public List<Map<String,Object>> list(){return db.queryForList("SELECT message_id AS messageId,CAST(settlement_id AS CHAR) AS settlementId,CAST(settlement_revision AS CHAR) AS revision,supplier_code AS supplierCode,feedback_type AS feedbackType,payload,received_at AS receivedAt FROM mcp_phase3_feedback ORDER BY received_at DESC LIMIT 100");}
    private void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
