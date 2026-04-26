package it.gdhi.service;

import it.gdhi.ai.dto.BedrockCountrySummaryData;
import it.gdhi.ai.dto.BedrockToolResponse;
import it.gdhi.ai.dto.BedrockCountryPhaseData;
import it.gdhi.dto.CountryHealthScoreDto;
import it.gdhi.dto.CountrySummaryDto;
import it.gdhi.internationalization.service.CountryNameTranslator;
import it.gdhi.model.Country;
import it.gdhi.model.CountryPhase;
import it.gdhi.repository.ICountryPhaseRepository;
import it.gdhi.repository.ICountryRepository;
import it.gdhi.utils.LanguageCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
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

    @Test
    public void shouldUseLatestCountryPhaseYearForCountrySummaryWhenYearIsMissing() {
        CountryPhase latestPhase = new CountryPhase("KEN", 3, "2024", true);
        CountrySummaryDto summary = CountrySummaryDto.builder()
                .countryId("KEN")
                .countryName("Kenya")
                .countryAlpha2Code("KE")
                .summary("Summary")
                .build();
        when(countryPhaseRepository.findLatestByCountryId("KEN")).thenReturn(latestPhase);
        when(countryService.fetchCountrySummary("KEN", "2024")).thenReturn(summary);

        BedrockToolResponse<BedrockCountrySummaryData> response = service.getCountrySummary("KEN", null, "en");

        assertEquals("2024", response.filters().get("year"));
        assertEquals("2024", response.data().year());
        assertEquals(3, response.data().countryPhase());
        verify(defaultYearDataService, never()).fetchDefaultYear();
    }

    @Test
    public void shouldUseLatestCountryPhaseForCountryPhaseWhenYearIsMissing() {
        CountryPhase latestPhase = new CountryPhase("KEN", 3, "2024", true);
        when(countryPhaseRepository.findLatestByCountryId("KEN")).thenReturn(latestPhase);
        when(countryRepository.findById("KEN")).thenReturn(new Country("KEN", "Kenya", null, "KE"));

        BedrockToolResponse<BedrockCountryPhaseData> response = service.getCountryPhase("KEN", null, "en");

        assertEquals("2024", response.filters().get("year"));
        assertEquals("2024", response.data().year());
        assertEquals(3, response.data().countryPhase());
        verify(defaultYearDataService, never()).fetchDefaultYear();
    }

    @Test
    public void shouldUseLatestCountryHealthIndicatorsWhenYearIsMissing() {
        CountryPhase latestPhase = new CountryPhase("KEN", 3, "2024", true);
        CountryHealthScoreDto healthScore = new CountryHealthScoreDto();
        when(countryPhaseRepository.findLatestByCountryId("KEN")).thenReturn(latestPhase);
        when(countryHealthIndicatorService.fetchLatestCountryHealthScore("KEN", LanguageCode.en))
                .thenReturn(healthScore);

        BedrockToolResponse<CountryHealthScoreDto> response =
                service.getCountryHealthIndicators("KEN", null, "en");

        assertEquals("2024", response.filters().get("year"));
        assertEquals(healthScore, response.data());
        verify(countryHealthIndicatorService).fetchLatestCountryHealthScore("KEN", LanguageCode.en);
        verify(defaultYearDataService, never()).fetchDefaultYear();
    }
}
