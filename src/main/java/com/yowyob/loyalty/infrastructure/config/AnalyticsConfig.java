package com.yowyob.loyalty.infrastructure.config;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BonusAnalytics;
import com.yowyob.loyalty.domain.analytics.model.PaymentAnalytics;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionAnalytics;
import com.yowyob.loyalty.domain.analytics.port.in.GetBonusAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetPaymentAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetSubscriptionAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.out.BonusAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.PaymentAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.SubscriptionAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.service.AnalyticsDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class AnalyticsConfig {

    @Bean
    public AnalyticsDomainService analyticsDomainService(
            SubscriptionAnalyticsRepository subscriptionAnalyticsRepository,
            PaymentAnalyticsRepository paymentAnalyticsRepository,
            BonusAnalyticsRepository bonusAnalyticsRepository) {
        return new AnalyticsDomainService(
                subscriptionAnalyticsRepository, paymentAnalyticsRepository, bonusAnalyticsRepository);
    }

    // AnalyticsDomainService implémente les trois interfaces de use case, donc l'exposer
    // tel quel sous chacune ferait de chaque bean un candidat pour les deux autres types
    // (NoUniqueBeanDefinitionException à l'injection). Des adaptateurs mono-interface,
    // plus @Primary, ne laissent qu'un candidat non ambigu par type — même contrainte et
    // même remède que dans SubscriptionConfig.

    @Bean
    @Primary
    public GetSubscriptionAnalyticsUseCase getSubscriptionAnalyticsUseCase(AnalyticsDomainService service) {
        return new GetSubscriptionAnalyticsUseCase() {
            @Override
            public Mono<SubscriptionAnalytics> subscriptionAnalytics(AnalyticsScope scope, AnalyticsWindow window) {
                return service.subscriptionAnalytics(scope, window);
            }
        };
    }

    @Bean
    @Primary
    public GetPaymentAnalyticsUseCase getPaymentAnalyticsUseCase(AnalyticsDomainService service) {
        return new GetPaymentAnalyticsUseCase() {
            @Override
            public Mono<PaymentAnalytics> paymentAnalytics(AnalyticsScope scope, AnalyticsWindow window) {
                return service.paymentAnalytics(scope, window);
            }
        };
    }

    @Bean
    @Primary
    public GetBonusAnalyticsUseCase getBonusAnalyticsUseCase(AnalyticsDomainService service) {
        return new GetBonusAnalyticsUseCase() {
            @Override
            public Mono<BonusAnalytics> bonusAnalytics(AnalyticsScope scope, AnalyticsWindow window, int limit) {
                return service.bonusAnalytics(scope, window, limit);
            }
        };
    }
}
