package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for the Step 4 Site API. Separate from {@link SiteResponse}, which is
 * the customer-portal shape and is left unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteDetailResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String name;
    private String addressLine;
    private String city;
    private Instant createdAt;
}
