package com.buildflow.erp.common.export;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Excel exports.
 *
 * <pre>
 *   GET /api/v1/export/xlsx            every section, one sheet each
 *   GET /api/v1/export/ACHATS/xlsx     that section on its own
 * </pre>
 *
 * Both accept an optional {@code ?month=YYYY-MM}, which scopes the indicators
 * sheet the same way the dashboard does.
 */
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;

    @GetMapping("/xlsx")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'FINANCE')")
    public ResponseEntity<byte[]> exportTout(@RequestParam(required = false) String month) {
        return file(exportService.exportTout(month), "buildflow-tableau-de-bord");
    }

    @GetMapping("/{section}/xlsx")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'FINANCE')")
    public ResponseEntity<byte[]> exportSection(
            @PathVariable ExportService.Section section,
            @RequestParam(required = false) String month) {
        return file(exportService.export(section, month),
                "buildflow-" + section.name().toLowerCase().replace('_', '-'));
    }

    private ResponseEntity<byte[]> file(byte[] body, String baseName) {
        // Dated filename so repeated exports don't overwrite each other in the
        // browser's downloads folder.
        String filename = baseName + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
