-- Keep applied V88/V90 immutable. Their legacy triggers are superseded by the current service-layer updates.
-- Existing databases validate the exact original scripts; newly provisioned databases also converge here.
DROP TRIGGER IF EXISTS mcp_order_item_insert;
DROP TRIGGER IF EXISTS mcp_order_item_update;
DROP TRIGGER IF EXISTS mcp_order_item_delete;
DROP TRIGGER IF EXISTS mcp_receiving_item_insert;
DROP TRIGGER IF EXISTS mcp_receiving_item_update;
DROP TRIGGER IF EXISTS mcp_receiving_item_delete;
