package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteResponse {
    private Long id;
    private String name;
    private String addressLine;
    private String city;
}
