package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.EvaluatePreviewRequest;
import io.praporets.controlplane.api.dto.EvaluatePreviewResponse;
import io.praporets.controlplane.service.PreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/evaluate-preview} (CP-11). Завжди 200 (навіть
 * {@code FLAG_NOT_FOUND} — це валідний результат обчислення, H2), окрім
 * невідомого середовища → 404 (сервіс кидає {@code NotFoundException},
 * хендлер мапить). X-Actor не потрібен — dry-run нічого не змінює (H3).
 */
@RestController
@RequestMapping("/api/v1/evaluate-preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping
    EvaluatePreviewResponse evaluatePreview(@RequestBody @Valid EvaluatePreviewRequest request) {
        throw new UnsupportedOperationException("01h: твоя реалізація");
    }
}
