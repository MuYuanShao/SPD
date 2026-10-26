-- A rejected receipt is a durable fence: delayed retries of the same operation must never execute.
ALTER TABLE mobile_operation_receipt DROP CHECK ck_mobile_receipt_status,
    ADD CONSTRAINT ck_mobile_receipt_status
    CHECK (status IN ('pending', 'succeeded', 'rejected'));
