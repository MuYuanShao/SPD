-- Freeze the route actually used before this upgrade, before changing active configuration.
SET @catalog_legacy_flow = (
  SELECT af.flow_id FROM approval_flow af
  LEFT JOIN approval_flow_step s ON s.flow_id=af.flow_id AND s.status=1
  WHERE af.feature_code='pending-product-catalog' AND af.status=1 AND af.deleted=0
  GROUP BY af.flow_id, af.node_code
  ORDER BY COUNT(s.step_id) DESC, CASE WHEN af.node_code='initial-review' THEN 0 ELSE 1 END, af.flow_id LIMIT 1
);
CREATE TEMPORARY TABLE catalog_upgrade_steps AS
SELECT 'numbered' AS route_kind, s.flow_id, s.step_id, s.step_order, s.step_name, s.approver_type,
       s.role_id, s.user_id, s.dept_id, s.min_approvals, s.allow_self_approve, s.data_scope,
       f.feature_code, f.node_code, CAST(s.step_order AS UNSIGNED) AS route_order
  FROM approval_flow_step s JOIN approval_flow f ON f.flow_id=s.flow_id
 WHERE s.flow_id=@catalog_legacy_flow AND s.status=1
UNION ALL
SELECT 'staged', s.flow_id, s.step_id, s.step_order, s.step_name, s.approver_type,
       s.role_id, s.user_id, s.dept_id, s.min_approvals, s.allow_self_approve, s.data_scope,
       f.feature_code, f.node_code,
       ROW_NUMBER() OVER (ORDER BY CASE WHEN f.node_code='initial-review' THEN 0 ELSE 1 END, s.step_order, s.step_id)
  FROM approval_flow f JOIN approval_flow_step s ON s.flow_id=f.flow_id AND s.status=1
 WHERE f.feature_code='pending-product-catalog' AND f.node_code IN ('initial-review','final-review')
   AND f.scope_type='global' AND f.scope_id='default' AND f.deleted=0 AND f.status=1;

SET @legacy_initial_order = (SELECT MIN(route_order) FROM catalog_upgrade_steps WHERE route_kind='staged');
SET @legacy_final_order = (SELECT MIN(route_order) FROM catalog_upgrade_steps WHERE route_kind='staged' AND node_code='final-review');
CREATE TEMPORARY TABLE catalog_upgrade_applications AS
SELECT a.application_id, a.approval_round, a.submit_time,
       CASE WHEN a.approval_status LIKE 'pending_step_%' THEN 'numbered' ELSE 'staged' END AS route_kind,
       CASE WHEN a.approval_status='pending_initial' THEN @legacy_initial_order
            WHEN a.approval_status='pending_final' THEN @legacy_final_order
            ELSE CAST(SUBSTRING(a.approval_status,LENGTH('pending_step_')+1) AS UNSIGNED) END AS current_order
  FROM pending_product_application a
 WHERE (a.approval_status LIKE 'pending_step_%' OR a.approval_status IN ('pending_initial','pending_final'))
   AND NOT EXISTS (SELECT 1 FROM pending_product_approval_route_step r WHERE r.application_id=a.application_id AND r.approval_round=a.approval_round);

-- Abort rather than invent a route for an unmappable or post-snapshot damaged record.
CREATE TEMPORARY TABLE catalog_upgrade_guard (invalid_count INT NOT NULL, CONSTRAINT chk_catalog_legacy_route_must_be_recoverable CHECK (invalid_count=0));
INSERT INTO catalog_upgrade_guard
SELECT COUNT(*) FROM catalog_upgrade_applications a
LEFT JOIN catalog_upgrade_steps s ON s.route_kind=a.route_kind AND s.route_order=a.current_order
WHERE s.step_id IS NULL OR a.submit_time > (SELECT MIN(installed_on) FROM flyway_schema_history WHERE version='71' AND success=1);

INSERT INTO pending_product_approval_route_step
(application_id,approval_round,route_order,feature_code,node_code,flow_id,source_step_id,source_step_order,
 step_name,approver_type,role_id,user_id,dept_id,min_approvals,allow_self_approve,data_scope,route_status)
SELECT a.application_id,a.approval_round,s.route_order,s.feature_code,s.node_code,s.flow_id,s.step_id,s.step_order,
       s.step_name,s.approver_type,s.role_id,s.user_id,s.dept_id,s.min_approvals,s.allow_self_approve,s.data_scope,
       CASE WHEN s.route_order<a.current_order THEN 'completed' WHEN s.route_order=a.current_order THEN 'pending' ELSE 'waiting' END
  FROM catalog_upgrade_applications a JOIN catalog_upgrade_steps s ON s.route_kind=a.route_kind;
UPDATE pending_product_application a JOIN catalog_upgrade_applications u ON u.application_id=a.application_id AND u.approval_round=a.approval_round
   SET a.approval_status=CONCAT('pending_step_',u.current_order);
DROP TEMPORARY TABLE catalog_upgrade_guard;
DROP TEMPORARY TABLE catalog_upgrade_applications;
DROP TEMPORARY TABLE catalog_upgrade_steps;

-- Configuration edits archive step revisions instead of deleting referenced source steps.
ALTER TABLE approval_flow ADD COLUMN revision_no INT NOT NULL DEFAULT 1;
ALTER TABLE approval_flow_step ADD COLUMN revision_no INT NOT NULL DEFAULT 1,
  DROP INDEX uk_approval_flow_step_order,
  ADD UNIQUE KEY uk_approval_flow_step_revision (flow_id,revision_no,step_order);

-- Adopt the existing four-step catalog sequence agreed for this installation.
-- Fresh installations retain their original default initial-review configuration.
SET @catalog_sequence_flow = COALESCE(
 (SELECT flow_id FROM approval_flow WHERE feature_code='pending-product-catalog' AND node_code='initial-review'
    AND scope_type='global' AND scope_id='ai-e2e-20260827' AND deleted=0 AND status=1 LIMIT 1),
 (SELECT flow_id FROM approval_flow WHERE feature_code='pending-product-catalog' AND node_code='initial-review'
    AND scope_type='global' AND scope_id='default' AND deleted=0 LIMIT 1)
);
UPDATE approval_flow SET status=0, scope_id=CONCAT('legacy-default-',flow_id), node_name='旧目录初审（仅历史）'
 WHERE feature_code='pending-product-catalog' AND node_code='initial-review' AND scope_type='global'
   AND scope_id='default' AND flow_id<>@catalog_sequence_flow;
UPDATE approval_flow SET status=0, node_name='旧目录终审（仅历史）'
 WHERE feature_code='pending-product-catalog' AND node_code='final-review';
UPDATE approval_flow SET scope_id='default', dept_id=NULL, node_name='目录顺序审批', status=1
 WHERE flow_id=@catalog_sequence_flow;

-- A global scope identifier cannot create a second effective global catalog flow.
ALTER TABLE approval_flow ADD COLUMN active_catalog_scope VARCHAR(191) GENERATED ALWAYS AS (
 CASE WHEN feature_code='pending-product-catalog' AND node_code='initial-review' AND status=1 AND deleted=0
 THEN CONCAT(scope_type,':',CASE WHEN scope_type='global' THEN 'default'
   WHEN scope_type='department' THEN COALESCE(CAST(dept_id AS CHAR),scope_id)
   WHEN scope_type='role' THEN LOWER(REPLACE(scope_id,'ROLE_','')) ELSE scope_id END) ELSE NULL END
) STORED, ADD UNIQUE KEY uk_active_catalog_scope (active_catalog_scope);

-- Other modules retain the original scope uniqueness; inactive catalog flows may coexist.
ALTER TABLE approval_flow ADD COLUMN non_catalog_scope_guard TINYINT GENERATED ALWAYS AS (
 CASE WHEN feature_code='pending-product-catalog' AND node_code='initial-review' THEN NULL ELSE 1 END
) STORED, DROP INDEX uk_approval_flow_node_scope,
 ADD UNIQUE KEY uk_non_catalog_flow_scope (feature_code,node_code,scope_type,scope_id,non_catalog_scope_guard);
