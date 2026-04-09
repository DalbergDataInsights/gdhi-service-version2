package it.gdhi.ai.dto;

import it.gdhi.dto.CountrySummaryDto;

import java.util.List;

public record BedrockCountrySummaryData(
        String countryId,
        String countryName,
        String countryAlpha2Code,
        String summary,
        Boolean govtApproved,
        List<String> resources
) {
    public static BedrockCountrySummaryData from(CountrySummaryDto dto) {
        return new BedrockCountrySummaryData(
                dto.getCountryId(),
                dto.getCountryName(),
                dto.getCountryAlpha2Code(),
                dto.getSummary(),
                dto.getGovtApproved(),
                dto.getResources()
        );
    }
}
