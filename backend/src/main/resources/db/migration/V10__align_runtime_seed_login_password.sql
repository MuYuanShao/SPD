-- Align runtime seed login credentials with the documented deployment Signoff account.
-- V6 inserted the seed users with a hash that does not match admin123; keep V6 immutable
-- and repair both existing and fresh Flyway databases here.

UPDATE `sys_user`
   SET `password` = '$2a$10$uI1qPLHX19yBlWucKapwNOhj038aVPtbfw8hMPOx28KcCxr1kQK4S'
 WHERE `username` IN ('admin', 'operator01', 'nurse01');
