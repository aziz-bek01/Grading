package uz.hrlab.grading.methodology.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyQueries;

import java.util.List;
import java.util.UUID;

/** Factor + FactorLevel resource endpoints. */
@RestController
@RequestMapping("/api/v1")
public class FactorController {

    private final FactorService factorService;
    private final FactorLevelService levelService;
    private final MethodologyQueries queries;

    public FactorController(FactorService factorService,
                            FactorLevelService levelService,
                            MethodologyQueries queries) {
        this.factorService = factorService;
        this.levelService = levelService;
        this.queries = queries;
    }

    @GetMapping("/factors/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public FactorResponse getFactor(@PathVariable UUID id) {
        return FactorResponse.from(queries.findFactorById(id));
    }

    @PatchMapping("/factors/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public FactorResponse updateFactor(@PathVariable UUID id,
                                       @Valid @RequestBody FactorRequest req) {
        FactorCommand cmd = new FactorCommand(req.code(), req.nameI18n(),
                req.descriptionI18n(), req.weight(), req.maxPoints(),
                req.sortOrder(), req.required());
        return FactorResponse.from(factorService.update(id, cmd));
    }

    @DeleteMapping("/factors/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public ResponseEntity<Void> deleteFactor(@PathVariable UUID id) {
        factorService.remove(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List a factor's levels. Broadened to EVALUATION_READ: the scoring sheet's
     * version-detail aggregator ({@code fetchMethodologyVersion}) hydrates each
     * factor's levels here to render the level picker, and an assigned evaluator
     * does not hold METHODOLOGY_READ. READ-only, non-sensitive structure; every
     * level write endpoint below stays METHODOLOGY_EDIT-gated. Tenant scoping is
     * enforced inside {@code queries.listLevelsByFactor}.
     */
    @GetMapping("/factors/{id}/levels")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ') or hasAuthority('EVALUATION_READ')")
    public List<FactorLevelResponse> listLevels(@PathVariable UUID id) {
        return queries.listLevelsByFactor(id).stream()
                .map(FactorLevelResponse::from)
                .toList();
    }

    @PostMapping("/factors/{id}/levels")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public ResponseEntity<FactorLevelResponse> addLevel(@PathVariable UUID id,
                                                        @Valid @RequestBody FactorLevelRequest req) {
        FactorLevelCommand cmd = new FactorLevelCommand(req.code(), req.levelOrder(),
                req.points(), req.scaleValue(),
                req.labelI18n(), req.descriptionI18n());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FactorLevelResponse.from(levelService.add(id, cmd)));
    }

    @PostMapping("/factors/{id}/levels/reorder")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public ResponseEntity<Void> reorderLevels(@PathVariable UUID id,
                                              @Valid @RequestBody ReorderRequest req) {
        levelService.reorder(id, req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/factor-levels/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public FactorLevelResponse updateLevel(@PathVariable UUID id,
                                           @Valid @RequestBody FactorLevelRequest req) {
        FactorLevelCommand cmd = new FactorLevelCommand(req.code(), req.levelOrder(),
                req.points(), req.scaleValue(),
                req.labelI18n(), req.descriptionI18n());
        return FactorLevelResponse.from(levelService.update(id, cmd));
    }

    @DeleteMapping("/factor-levels/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT') or hasAuthority('METHODOLOGY_EDIT_APPROVED')")
    public ResponseEntity<Void> deleteLevel(@PathVariable UUID id) {
        levelService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
