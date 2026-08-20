-- Retained RBAC verification accounts use password: RbacTest@2026
UPDATE `sys_user`
   SET `password` = '$2a$10$AyhLd.KkyO4uS1fn40w3VuK.XrDOOwYwiB2.kfIrM.svMhxK3X4He'
 WHERE `username` IN ('rbac_catalog_reader', 'rbac_department_reader')
   AND `deleted` = 0;
