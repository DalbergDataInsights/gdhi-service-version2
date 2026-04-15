package it.gdhi.service;

import it.gdhi.ai.dto.BedrockCountrySummaryData;
import it.gdhi.ai.dto.BedrockToolResponse;
import it.gdhi.dto.CountrySummaryDto;
import it.gdhi.internationalization.service.CountryNameTranslator;
import it.gdhi.repository.ICountryPhaseRepository;
import it.gdhi.repository.ICountryRepository;
import it.gdhi.utils.LanguageCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BedrockToolsServiceTest {

    @Mock
    private CountryService countryService;

    @Mock
    private CountryHealthIndicatorService countryHealthIndicatorService;

    @Mock
    private RegionService regionService;

    @Mock
    private CategoryIndicatorService categoryIndicatorService;

    @Mock
    private PhaseService phaseService;

    @Mock
    private DefaultYearDataService defaultYearDataService;

    @Mock
    private ICountryPhaseRepository countryPhaseRepository;

    @Mock
    private ICountryRepository countryRepository;

    @Mock
    private CountryNameTranslator countryNameTranslator;

    private BedrockToolsService service;

    @BeforeEach
    public void setUp() {
        service = new BedrockToolsService(countryService, countryHealthIndicatorService, regionService,
                categoryIndicatorService, phaseService, defaultYearDataService, countryPhaseRepository,
                countryRepository, countryNameTranslator);
    }

    @Test
    public void shouldTranslateCountrySummaryCountryNameForRequestedLanguage() {
        CountrySummaryDto summary = CountrySummaryDto.builder()
                .countryId("IND")
                .countryName("India")
                .countryAlpha2Code("IN")
                .summary("Summary")
                .build();
        when(countryService.fetchCountrySummary("IND", "2024")).thenReturn(summary);
        when(countryNameTranslator.getCountryTranslationForLanguage(LanguageCode.fr, "IND")).thenReturn("Inde");

        BedrockToolResponse<BedrockCountrySummaryData> response = service.getCountrySummary("IND", "2024", "fr");

        assertEquals("Inde", response.data().countryName());
        assertEquals("fr", response.filters().get("language"));
        verify(countryNameTranslator).getCountryTranslationForLanguage(LanguageCode.fr, "IND");
    }
}
