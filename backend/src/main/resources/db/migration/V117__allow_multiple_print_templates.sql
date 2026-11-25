ALTER TABLE print_template
  DROP INDEX uk_print_template_type,
  ADD UNIQUE KEY uk_print_template_type_code (template_type, template_code);
