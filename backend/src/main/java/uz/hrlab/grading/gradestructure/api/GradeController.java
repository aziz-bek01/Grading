package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.gradestructure.application.GradeCommand;
import uz.hrlab.grading.gradestructure.application.GradeService;

import java.util.UUID;

/** Individual grade + band endpoints. Parent structure must be DRAFT. */
@RestController
@RequestMapping("/api/v1/grades")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public GradeResponse update(@PathVariable UUID id,
                                @Valid @RequestBody GradeRequest req) {
        return GradeResponse.from(gradeService.updateGrade(id, new GradeCommand.Update(
                req.gradeNumber(), req.sortOrder(), req.nameI18n(), req.descriptionI18n())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        gradeService.removeGrade(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/band")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<GradeBandResponse> upsertBand(@PathVariable UUID id,
                                                        @Valid @RequestBody GradeBandRequest req) {
        var band = gradeService.upsertBand(id, new GradeCommand.UpsertBand(
                req.minScore(), req.maxScore()));
        return ResponseEntity.status(HttpStatus.CREATED).body(GradeBandResponse.from(band));
    }

    /** BE-6: explicit band delete — clears a grade's band without deleting the
     *  grade. DRAFT-only. Idempotent 204 whether or not a band existed. */
    @DeleteMapping("/{id}/band")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<Void> deleteBand(@PathVariable UUID id) {
        gradeService.removeBand(id);
        return ResponseEntity.noContent().build();
    }
}
