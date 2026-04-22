package it.gdhi.ai.dto;

import it.gdhi.dto.CountrySummaryDto;

import java.util.List;

public record BedrockCountrySummaryData(
        String countryId,
        String countryName,
        String countryAlpha2Code,
        String year,
        Integer countryPhase,
        String phaseLabel,
        String summary,
        Boolean govtApproved,
        List<String> resources
) {
    public static BedrockCountrySummaryData from(CountrySummaryDto dto, String year, Integer countryPhase) {
        return new BedrockCountrySummaryData(
                dto.getCountryId(),
                dto.getCountryName(),
                dto.getCountryAlpha2Code(),
                year,
                countryPhase,
                BedrockCountryPhaseData.phaseLabelFor(countryPhase),
                dto.getSummary(),
                dto.getGovtApproved(),
                dto.getResources()
        );
        // TODO: consider using a builderå pattern if the number of parameters grows or if we want to make some of them optional
        // also consider if we want to include all fields from CountrySummaryDto or if we want to transform some of them differently for the Bedrock response   
        // for example, we might want to format the summary differently, or include additional computed fields based on the countryPhase or other data.
    }
}
