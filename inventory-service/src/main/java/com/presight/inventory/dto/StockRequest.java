package com.presight.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Shared payload for both /reserve and /release. `referenceId` is the
 * order ID that triggered the movement, kept purely for audit/log
 * correlation across services.
 */
public record StockRequest(
        @NotBlank String sku,
        @Positive int quantity,
        String referenceId
) {
}
