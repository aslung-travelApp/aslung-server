package com.trip.aslung.user.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PreferenceRequest {
    private String travelStyle;
    private String pace;
    private List<String> interestKeywords;
}