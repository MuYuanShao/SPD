package com.hospital.spd.licenses;

import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

/** Keeps immutable license versions, including the attachment identifiers visible at that version. */
final class LicenseHistoryService {
    private final JdbcTemplate jdbc;
    private final OperatorContextProvider operators;
    LicenseHistoryService(JdbcTemplate jdbc, OperatorContextProvider operators) { this.jdbc = jdbc; this.operators = operators; }

    void capture(Long id, String operation) {
        var operator = operators.current();
        jdbc.update("""
                INSERT INTO license_revision (license_id, revision_no, operation_type, snapshot_json, operator_id, operator_name)
                SELECT l.license_id, l.revision_no, ?, JSON_OBJECT(
                  'licenseName', l.license_name, 'licenseType', l.license_type, 'licenseNo', l.license_no,
                  'ownerType', l.owner_type, 'ownerId', l.owner_id, 'ownerCode', l.owner_code, 'ownerName', l.owner_name,
                  'partyA', l.party_a, 'partyB', l.party_b, 'contractAmount', l.contract_amount,
                  'issueDate', l.issue_date, 'expireDate', l.expire_date, 'status', l.status, 'deleted', l.deleted,
                  'remark', l.remark, 'attachments', COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT(
                    'id', a.attachment_id, 'fileName', a.file_name, 'fileType', a.file_type, 'size', a.file_size))
                    FROM sys_attachment a WHERE a.biz_type = 'license' AND a.biz_id = l.license_id AND a.deleted = 0), JSON_ARRAY())
                ), ?, ? FROM license_document l WHERE l.license_id = ?
                  AND NOT EXISTS (SELECT 1 FROM license_revision h WHERE h.license_id = l.license_id AND h.revision_no = l.revision_no)
                """, operation, operator.userId(), operator.username(), id);
    }

    List<Map<String, Object>> list(Long id) {
        return jdbc.queryForList("""
                SELECT revision_no AS revisionNo, operation_type AS operationType,
                       CAST(snapshot_json AS CHAR) AS snapshotJson, operator_name AS operatorName,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
                  FROM license_revision WHERE license_id = ? ORDER BY revision_no DESC
                """, id);
    }
}
