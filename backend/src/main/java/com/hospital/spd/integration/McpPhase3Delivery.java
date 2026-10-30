package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.*;

/** Delivers leased immutable messages after business commits, with resumable atomic large-document uploads. */
@Service
public class McpPhase3Delivery {
    @FunctionalInterface public interface Transport {JsonNode request(String method,String path,Object body,String key,Duration timeout) throws Exception;}
    private sealed interface ClaimResult permits Unclaimed,ClaimedMessage {}
    private enum Unclaimed implements ClaimResult { EMPTY, CONFIGURATION_BLOCKED }
    private record ClaimedMessage(String id,long revision,int attempts,JsonNode envelope,String payloadHash) implements ClaimResult {}
    private final JdbcTemplate db;private final Environment env;private final TransactionTemplate tx;
    public McpPhase3Delivery(JdbcTemplate db,Environment env,PlatformTransactionManager manager){this.db=db;this.env=env;this.tx=new TransactionTemplate(manager);}

    public void deliver(String target,Transport transport) {
        long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
        for(int count=0;count<50 && System.nanoTime()<deadline;count++) {
            String lease=UUID.randomUUID().toString();ClaimResult result=claim(target,lease);
            if(result==Unclaimed.EMPTY)return;
            if(!(result instanceof ClaimedMessage message))continue;
            String id=message.id();
            try {
                JsonNode envelope=message.envelope();JsonNode receipt;
                String base="/integration/instances/"+env.getProperty("SPD_MCP_UI_INSTANCE_ID")+"/phase3";
                String route=envelope.path("documentType").asText().equals("purchase_plan")?"purchase-plans":"settlements";
                JsonNode items=envelope.path("payload").path("items");
                if(items.size()<=1000) receipt=transport.request("POST",base+"/"+route,envelope,id,remaining(deadline)).path("data");
                else {
                    ObjectNode header=envelope.deepCopy();((ObjectNode)header.path("payload")).remove("items");
                    var manifest=Phase3Json.MAPPER.createObjectNode();manifest.set("envelope",header);
                    manifest.put("payloadHash",message.payloadHash());manifest.put("totalLines",items.size());manifest.put("totalParts",(items.size()+999)/1000);
                    var session=transport.request("POST",base+"/upload-sessions",manifest,id,remaining(deadline)).path("data");
                    if("processed".equals(session.path("state").asText())) receipt=session;
                    else {
                        String sessionId=session.path("sessionId").asText();if(sessionId.isBlank())throw new IllegalStateException("大单暂存回执缺少会话");
                        var uploaded=new HashSet<Integer>();session.path("uploadedParts").forEach(part->uploaded.add(part.asInt()));
                        for(int part=0;part<(items.size()+999)/1000;part++) if(!uploaded.contains(part)) {
                            var payload=Phase3Json.MAPPER.createObjectNode();var lines=payload.putArray("items");
                            for(int i=part*1000;i<Math.min(items.size(),(part+1)*1000);i++)lines.add(items.get(i));
                            payload.put("payloadHash",Phase3Json.hash(lines));
                            transport.request("PUT",base+"/upload-sessions/"+sessionId+"/parts/"+part,payload,id,remaining(deadline));
                        }
                        receipt=transport.request("POST",base+"/upload-sessions/"+sessionId+"/commit",Map.of(),id,remaining(deadline)).path("data");
                    }
                }
                String state=receipt.path("state").asText();
                if(!Set.of("processed","ignored").contains(state))throw new IllegalStateException("第三期回执未确认成功");
                if(receipt.path("storedRevision").asLong()<message.revision())throw new IllegalStateException("第三期回执版本不完整");
                db.update("UPDATE mcp_phase3_outbox SET state=?,message='处理成功',lease_id=NULL,lease_until=NULL WHERE message_id=? AND lease_id=?",state,id,lease);
            } catch(Continuation error) {
                db.update("UPDATE mcp_phase3_outbox SET state='pending',message='大单分片待继续',next_attempt=DATE_ADD(NOW(6),INTERVAL 5 SECOND),lease_id=NULL,lease_until=NULL WHERE message_id=? AND lease_id=?",id,lease);
                return;
            } catch(Exception error) {
                if(error instanceof InterruptedException)Thread.currentThread().interrupt();
                int attempts=message.attempts();int delay=Math.min(900,1<<Math.min(attempts,9))+java.util.concurrent.ThreadLocalRandom.current().nextInt(5);
                db.update("UPDATE mcp_phase3_outbox SET state=CASE WHEN revision<(SELECT doc.revision FROM mcp_phase3_document doc WHERE doc.document_type=mcp_phase3_outbox.document_type AND doc.source_id=mcp_phase3_outbox.source_id) THEN 'superseded' ELSE 'failed' END,message=?,next_attempt=DATE_ADD(NOW(6),INTERVAL ? SECOND),lease_id=NULL,lease_until=NULL WHERE message_id=? AND lease_id=?",
                        safeMessage(error),delay,id,lease);
                if(Thread.currentThread().isInterrupted())return;
            }
        }
    }

    private ClaimResult claim(String target,String lease) {
        return tx.execute(status->{
            var rows=db.queryForList("""
                    SELECT box.message_id FROM mcp_phase3_outbox box
                     WHERE ((box.state IN ('pending','failed') AND box.next_attempt<=NOW(6)) OR (box.state='sending' AND box.lease_until<NOW(6)))
                       AND (box.target_key IS NULL OR box.target_key=?)
                       AND NOT EXISTS(SELECT 1 FROM mcp_phase3_outbox older WHERE older.document_type=box.document_type AND older.source_id=box.source_id
                         AND older.revision<box.revision AND older.state IN ('pending','failed','sending'))
                     ORDER BY box.created_at,box.message_id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """,target);
            if(rows.isEmpty())return Unclaimed.EMPTY;
            String id=rows.get(0).get("message_id").toString();
            // Sort only the narrow identity above: sorting JSON snapshots can exhaust MySQL sort memory.
            var row=new LinkedHashMap<>(db.queryForMap("SELECT * FROM mcp_phase3_outbox WHERE message_id=? FOR UPDATE",id));
            if(row.get("wire_payload")==null) {
                ObjectNode payload=Phase3Json.parse(row.get("source_payload").toString()).deepCopy();
                if(row.get("document_type").equals("settlement")) {
                    boolean preserved=payload.has("currency") || payload.has("taxIncluded");
                    String currency=preserved?payload.path("currency").asText():env.getProperty("SPD_MCP_CURRENCY","");
                    String tax=preserved?(payload.path("taxIncluded").isBoolean()?payload.path("taxIncluded").asText():""):env.getProperty("SPD_MCP_TAX_INCLUDED","");
                    if(!currency.matches("[A-Z]{3}") || !Set.of("true","false").contains(tax)) {
                        db.update("UPDATE mcp_phase3_outbox SET state='failed',message='必须明确配置币种和税口径',next_attempt=DATE_ADD(NOW(6),INTERVAL 60 SECOND) WHERE message_id=?",id);return Unclaimed.CONFIGURATION_BLOCKED;
                    }
                    payload.put("currency",currency);payload.put("taxIncluded",Boolean.parseBoolean(tax));
                }
                var wire=Phase3Json.MAPPER.createObjectNode();wire.put("schemaVersion","3.0");wire.put("messageId",id);wire.put("sourceSystem","SPD-PR");
                wire.put("organization",env.getProperty("SPD_MCP_ORGANIZATION",""));wire.put("documentType",row.get("document_type").toString());
                wire.put("sourceId",row.get("source_id").toString());wire.put("revision",row.get("revision").toString());wire.put("operation",row.get("operation").toString());
                wire.put("sourceUpdatedAt",row.get("source_updated_at").toString());wire.put("documentNo",payload.path("documentNo").asText());wire.set("payload",payload);
                String text=Phase3Json.json(wire),hash=Phase3Json.hash(wire);row.put("wire_payload",text);row.put("payload_hash",hash);
                db.update("UPDATE mcp_phase3_outbox SET wire_payload=CAST(? AS JSON),payload_hash=? WHERE message_id=?",text,hash,id);
            }
            db.update("UPDATE mcp_phase3_outbox SET state='sending',attempts=attempts+1,lease_id=?,lease_until=DATE_ADD(NOW(6),INTERVAL 60 SECOND),target_key=? WHERE message_id=?",lease,target,id);
            return new ClaimedMessage(id,((Number)row.get("revision")).longValue(),((Number)row.get("attempts")).intValue()+1,
                    Phase3Json.parse(row.get("wire_payload").toString()),row.get("payload_hash").toString());
        });
    }
    private String safeMessage(Exception error) {
        String message=(error instanceof IllegalStateException || error instanceof IllegalArgumentException)
                ? Objects.toString(error.getMessage(),"来源校验失败") : "网络连接异常："+error.getClass().getSimpleName();
        return message.substring(0,Math.min(1000,message.length()));
    }
    private Duration remaining(long deadline) throws Continuation {long millis=(deadline-System.nanoTime())/1_000_000;if(millis<100)throw new Continuation();return Duration.ofMillis(Math.min(15000,millis));}
    private static final class Continuation extends Exception {}
}
