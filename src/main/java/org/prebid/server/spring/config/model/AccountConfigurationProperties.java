package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
public class AccountConfigurationProperties {

    private String priceGranularity;

    private Integer bannerCacheTtl;

    private Integer videoCacheTtl;

    private Boolean eventsEnabled;

    private Boolean enforceCcpa;

    private String gdpr;

    private Integer analyticsSamplingFactor;

    private Integer truncateTargetAttr;

    private String defaultIntegration;

    private String analyticsConfig;

    public Account toAccount(JacksonMapper mapper) {
        return Account.builder()
                .priceGranularity(getPriceGranularity())
                .bannerCacheTtl(getBannerCacheTtl())
                .videoCacheTtl(getVideoCacheTtl())
                .eventsEnabled(getEventsEnabled())
                .enforceCcpa(getEnforceCcpa())
                .gdpr(toModel(mapper, getGdpr(), AccountGdprConfig.class))
                .analyticsSamplingFactor(getAnalyticsSamplingFactor())
                .truncateTargetAttr(getTruncateTargetAttr())
                .defaultIntegration(getDefaultIntegration())
                .analyticsConfig(toModel(mapper, getAnalyticsConfig(), AccountAnalyticsConfig.class))
                .build();
    }

    private static <T> T toModel(JacksonMapper mapper, String source, Class<T> targetClass) {
        return source != null ? mapper.decodeValue(source, targetClass) : null;
    }
}