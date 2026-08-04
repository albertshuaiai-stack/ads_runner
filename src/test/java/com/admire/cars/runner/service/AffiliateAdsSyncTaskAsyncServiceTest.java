package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateAdsSyncTaskAsyncServiceTest {

    @Mock
    private AffiliateAdsSyncTaskRepository taskRepository;

    @Mock
    private AffiliateAdsSyncConfigRepository configRepository;

    @Mock
    private AffiliateAdsSyncRepository syncRepository;

    @Mock
    private RestTemplate restTemplate;

    private AffiliateAdsSyncTaskAsyncService service;

    @BeforeEach
    void setUp() {
        service = new AffiliateAdsSyncTaskAsyncService(
                taskRepository,
                configRepository,
                syncRepository,
                restTemplate);
    }

    @Test
    void syncAdsNow_parsesHtmlContentTypeResponse() {
        AffiliateAdsSyncTask task = new AffiliateAdsSyncTask();
        task.setId(1L);
        task.setAffiliateAdsSyncConfigId(2L);
        task.setRegion("US");
        task.setAdsOwner("owner-1");
        task.setStatus("PENDING");
        task.setPreStartDate(LocalDateTime.now().minusSeconds(5));

        AffiliateAdsSyncConfig config = new AffiliateAdsSyncConfig();
        config.setId(2L);
        config.setMethod("POST");
        config.setUrl("https://example.test/api");
        config.setAffiliateNetwork("BonusArrive");

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(configRepository.findById(2L)).thenReturn(Optional.of(config));
        when(syncRepository.deleteByAffiliateNetworkAndAdsOwnerAndRegion("BonusArrive", "owner-1", "US")).thenReturn(0L);
        when(restTemplate.exchange(eq("https://example.test/api"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body("{\"status\":1,\"info\":\"success\",\"data\":{\"total_items\":11,\"total_page\":6,\"list\":[{\"m_id\":\"27161\",\"site_name\":\"Anker [CPS] Many Geos\",\"site_url\":\"https://www.anker.com/\",\"site_logo_url\":\"https://cdn.admitad-connect.com/campaign/images/2023/3/13/24871-dfed52244c54ba6d.svg\",\"update_time\":\"2024-06-27 19:05:54\",\"region\":\"US\",\"merchant_status\":\"active\",\"tracking_url\":\"https://www.bonusarrive.com/link?c=45&ad=26955&subid=&sub2id=&url=\",\"deeplink\":\"Supported\",\"commissions\":[\"{'name':'Contact for rates','type':'','id':'','commission_type':'percent','commission_val':'00','commission_currency':'USD'}\"],\"adv_catagory\":\"E-commerce and Shopping\"}]}}"));

        service.syncAdsNow(1L);

        ArgumentCaptor<List<AffiliateAdsSync>> syncCaptor = ArgumentCaptor.forClass(List.class);
        verify(syncRepository, times(6)).saveAll(syncCaptor.capture());
        assertEquals(6, syncCaptor.getAllValues().size());
        AffiliateAdsSync saved = syncCaptor.getAllValues().get(0).get(0);
        assertNotNull(saved);
        assertEquals("Anker [CPS] Many Geos", saved.getSiteName());
        assertEquals("https://www.anker.com/", saved.getSiteUrl());
        assertEquals("active", saved.getMerchantStatus());
        assertEquals("US", saved.getRegion());
        assertEquals("{'name':'Contact for rates','type':'','id':'','commission_type':'percent','commission_val':'00','commission_currency':'USD'}", saved.getCommissions());
        assertNotNull(task.getPreEndDate());
        assertNotNull(task.getPreDuration());
        verify(taskRepository).save(task);
    }
}
