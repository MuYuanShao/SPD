-- Upgrade the stock label size while retaining customized paper settings and fields.
UPDATE print_template SET paper_width_mm = 40, paper_height_mm = 60
 WHERE template_type = 'quota_label' AND deleted = 0
   AND paper_width_mm = 100 AND paper_height_mm = 70
   AND remark = '默认定数包标签打印模板';
