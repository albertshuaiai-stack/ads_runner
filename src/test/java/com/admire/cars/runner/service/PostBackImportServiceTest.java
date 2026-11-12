package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostBackImportServiceTest {

    @Mock
    private AdsNormalPostBackRepository postBackRepository;
    @Mock
    private PostBackAttributionService postBackAttributionService;

    private PostBackImportService service;

    private static final String OWNER = "18565545084";

    private static final String CSV = """
            "Order ID","Action Time",Status,"Advertiser Name","Advertiser ID",GMV,Commission,"Promotion Space","Sub ID",Country
            22921.6889.2392436,"2026-11-12 02:50:27",pending,"Ninja Australia",25128,178.37,9.99,lunalvabeau.com-4216,,GB
            ,"2026-11-12 02:38:43",pending,"Fresh CA",137072,0.00,0.00,lunalvabeau.com-4216,,
            """;

    @BeforeEach
    void setUp() {
        service = new PostBackImportService(postBackRepository, postBackAttributionService);
    }

    @Test
    void importCsv_mapsColumnsAndSkipsRowsWithoutOrderId() throws Exception {
        when(postBackRepository.findByAdsOwnerAndOrderNo(anyString(), anyString()))
                .thenReturn(Optional.empty());

        PostBackImportService.ImportResult result = service.importCsvForOwner(OWNER,
                new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, result.totalRows());
        assertEquals(1, result.imported());
        assertEquals(1, result.skipped());

        ArgumentCaptor<AdsNormalPostBack> captor = ArgumentCaptor.forClass(AdsNormalPostBack.class);
        verify(postBackRepository).save(captor.capture());
        AdsNormalPostBack saved = captor.getValue();
        assertEquals("22921.6889.2392436", saved.getOrderNo());
        assertEquals("2026-11-12 02:50:27", saved.getOrderTime());
        assertEquals("pending", saved.getStatus());
        assertEquals("Ninja Australia", saved.getAdvertiserShopName());
        assertEquals("25128", saved.getAdvertiserShopId());
        assertEquals(new BigDecimal("178.37"), saved.getOrderAmount());
        assertEquals(new BigDecimal("9.99"), saved.getUserCommissionAmount());
        assertEquals("BonusArrive", saved.getAffiliateSite());
        assertEquals(OWNER, saved.getAdsOwner());
        assertNull(saved.getSubId());
        verify(postBackAttributionService).attribute(OWNER, "22921.6889.2392436");
    }

    @Test
    void importCsv_isIdempotentByOrderNo() throws Exception {
        when(postBackRepository.findByAdsOwnerAndOrderNo(OWNER, "22921.6889.2392436"))
                .thenReturn(Optional.of(new AdsNormalPostBack()));

        PostBackImportService.ImportResult result = service.importCsvForOwner(OWNER,
                new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, result.imported());
        assertEquals(2, result.skipped());
        verify(postBackRepository, times(0)).save(any(AdsNormalPostBack.class));
    }
}
