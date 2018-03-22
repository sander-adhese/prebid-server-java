package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.handler.AuctionHandler;
import org.prebid.server.handler.BidderParamHandler;
import org.prebid.server.handler.CookieSyncHandler;
import org.prebid.server.handler.GetuidsHandler;
import org.prebid.server.handler.NoCacheHandler;
import org.prebid.server.handler.OptoutHandler;
import org.prebid.server.handler.SetuidHandler;
import org.prebid.server.handler.StatusHandler;
import org.prebid.server.handler.ValidateHandler;
import org.prebid.server.handler.info.InfoBidderDetailsHandler;
import org.prebid.server.handler.info.InfoBiddersHandler;
import org.prebid.server.handler.openrtb2.AmpHandler;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;

@Configuration
public class WebConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    Router router(Vertx vertx,
                  CookieHandler cookieHandler,
                  BodyHandler bodyHandler,
                  NoCacheHandler noCacheHandler,
                  CorsHandler corsHandler,
                  AuctionHandler auctionHandler,
                  org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler,
                  AmpHandler openrtbAmpHandler,
                  StatusHandler statusHandler,
                  CookieSyncHandler cookieSyncHandler,
                  SetuidHandler setuidHandler,
                  GetuidsHandler getuidsHandler,
                  OptoutHandler optoutHandler,
                  ValidateHandler validateHandler,
                  BidderParamHandler bidderParamHandler,
                  InfoBiddersHandler infoBiddersHandler,
                  InfoBidderDetailsHandler infoBidderDetailsHandler,
                  StaticHandler staticHandler) {

        final Router router = Router.router(vertx);

        router.route().handler(TimeoutHandler.create(10000)); // kick off long processing requests
        router.route().handler(cookieHandler);
        router.route().handler(bodyHandler);
        router.route().handler(noCacheHandler);
        router.route().handler(corsHandler);
        router.post("/auction").handler(auctionHandler);
        router.post("/openrtb2/auction").handler(openrtbAuctionHandler);
        router.get("/openrtb2/amp").handler(openrtbAmpHandler);
        router.get("/status").handler(statusHandler);
        router.post("/cookie_sync").handler(cookieSyncHandler);
        router.get("/setuid").handler(setuidHandler);
        router.get("/getuids").handler(getuidsHandler);
        router.post("/optout").handler(optoutHandler);
        router.get("/optout").handler(optoutHandler);
        router.post("/validate").handler(validateHandler);
        router.get("/bidders/params").handler(bidderParamHandler);
        router.get("/info/bidders").handler(infoBiddersHandler);
        router.get("/info/bidders/:bidderName").handler(infoBidderDetailsHandler);
        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves indexAdapter.html by default

        return router;
    }

    @Bean
    CookieHandler cookieHandler() {
        return CookieHandler.create();
    }

    @Bean
    BodyHandler bodyHandler(@Value("${vertx.uploads-dir}") String uploadsDir) {
        return BodyHandler.create(uploadsDir);
    }

    @Bean
    NoCacheHandler noCacheHandler() {
        return NoCacheHandler.create();
    }

    @Bean
    ValidateHandler schemaValidationHandler() {
        return ValidateHandler.create("static/pbs_request.json");
    }

    @Bean
    CorsHandler corsHandler() {
        return CorsHandler.create(".*")
                .allowCredentials(true)
                .allowedHeaders(new HashSet<>(Arrays.asList(HttpHeaders.ORIGIN.toString(),
                        HttpHeaders.ACCEPT.toString(), HttpHeaders.CONTENT_TYPE.toString(), "X-Requested-With")))
                .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.HEAD,
                        HttpMethod.OPTIONS)));
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    AuctionHandler auctionHandler(
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            PreBidRequestContextFactory preBidRequestContextFactory,
            CacheService cacheService,
            Metrics metrics,
            HttpAdapterConnector httpAdapterConnector,
            Clock clock) {

        return new AuctionHandler(applicationSettings, bidderCatalog, preBidRequestContextFactory, cacheService,
                metrics, httpAdapterConnector, clock);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler(
            @Value("${auction.default-timeout-ms}") int defaultTimeoutMs,
            ExchangeService exchangeService,
            AuctionRequestFactory auctionRequestFactory,
            UidsCookieService uidsCookieService,
            Metrics metrics,
            Clock clock,
            TimeoutFactory timeoutFactory) {

        return new org.prebid.server.handler.openrtb2.AuctionHandler(defaultTimeoutMs, exchangeService,
                auctionRequestFactory, uidsCookieService, metrics, clock, timeoutFactory);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    AmpHandler openrtbAmpHandler(
            @Value("${auction.default-timeout-ms}") int defaultTimeoutMs,
            AmpRequestFactory ampRequestFactory,
            ExchangeService exchangeService,
            UidsCookieService uidsCookieService,
            Metrics metrics,
            Clock clock,
            TimeoutFactory timeoutFactory) {

        return new AmpHandler(defaultTimeoutMs, ampRequestFactory, exchangeService, uidsCookieService, metrics,
                clock, timeoutFactory);
    }

    @Bean
    StatusHandler statusHandler() {
        return new StatusHandler();
    }

    @Bean
    CookieSyncHandler cookieSyncHandler(
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            Metrics metrics) {

        return new CookieSyncHandler(uidsCookieService, bidderCatalog, metrics);
    }

    @Bean
    SetuidHandler setuidHandler(UidsCookieService uidsCookieService, Metrics metrics) {
        return new SetuidHandler(uidsCookieService, metrics);
    }

    @Bean
    GetuidsHandler getuidsHandler(UidsCookieService uidsCookieService) {
        return new GetuidsHandler(uidsCookieService);
    }

    @Bean
    OptoutHandler optoutHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${host-cookie.opt-out-url}") String optoutUrl,
            @Value("${host-cookie.opt-in-url}") String optinUrl,
            GoogleRecaptchaVerifier googleRecaptchaVerifier,
            UidsCookieService uidsCookieService) {

        return new OptoutHandler(googleRecaptchaVerifier,
                uidsCookieService,
                OptoutHandler.getOptoutRedirectUrl(externalUrl),
                HttpUtil.validateUrl(optoutUrl),
                HttpUtil.validateUrl(optinUrl)
        );
    }

    @Bean
    BidderParamHandler bidderParamHandler(BidderParamValidator bidderParamValidator) {
        return new BidderParamHandler(bidderParamValidator);
    }

    @Bean
    InfoBiddersHandler infoBiddersHandler(BidderCatalog bidderCatalog) {
        return new InfoBiddersHandler(bidderCatalog);
    }

    @Bean
    InfoBidderDetailsHandler infoBidderDetailsHandler(BidderCatalog bidderCatalog) {
        return new InfoBidderDetailsHandler(bidderCatalog);
    }

    @Bean
    StaticHandler staticHandler() {
        return StaticHandler.create("static").setCachingEnabled(false);
    }
}