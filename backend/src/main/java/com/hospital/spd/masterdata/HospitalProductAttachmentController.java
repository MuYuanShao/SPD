package com.hospital.spd.masterdata;

import com.hospital.spd.masterdata.service.HospitalProductAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Binds authenticated hospital catalog attachment reads without requiring pending-approval access. */
@RestController
public class HospitalProductAttachmentController {
    private final HospitalProductAttachmentService files;
    public HospitalProductAttachmentController(HospitalProductAttachmentService files){this.files=files;}
    @GetMapping("/master-data/hospital-products/{productCode}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> file(@PathVariable String productCode,@PathVariable long attachmentId) {
        return files.file(productCode,attachmentId);
    }
}
