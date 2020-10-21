package org.prebid.server.bidder.conversant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.conversant.ExtImpConversant;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conversant {@link Bidder} implementation.
 */
public class ConversantBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConversant>> CONVERSANT_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpConversant>>() {
            };

    // List of API frameworks supported by the publisher
    private static final Set<Integer> APIS = IntStream.range(1, 7).boxed().collect(Collectors.toSet());

    // Options for the various bid response protocols that could be supported by an exchange
    private static final Set<Integer> PROTOCOLS = IntStream.range(1, 11).boxed().collect(Collectors.toSet());

    // Position of the ad as a relative measure of visibility or prominence
    private static final Set<Integer> AD_POSITIONS = IntStream.range(0, 8).boxed().collect(Collectors.toSet());

    private static final String DISPLAY_MANAGER = "prebid-s2s";
    private static final String DISPLAY_MANAGER_VER = "2.0.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConversantBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final BidRequest outgoingRequest;
        try {
            outgoingRequest = createOutgoingRequest(bidRequest);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .build()),
                Collections.emptyList());
    }

    private BidRequest createOutgoingRequest(BidRequest bidRequest) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        final List<Imp> modifiedImps = new ArrayList<>();

        for (int i = 0; i < bidRequest.getImp().size(); i++) {
            final Imp imp = bidRequest.getImp().get(i);
            final ExtImpConversant impExt;
            try {
                impExt = parseImpExt(imp);
            } catch (PreBidException e) {
                throw new PreBidException(String.format(e.getMessage(), i));
            }
            if (i == 0) {
                updateRequestWithSiteId(requestBuilder, bidRequest, impExt);
            }
            modifiedImps.add(modifyImp(imp, impExt));
        }
        requestBuilder.imp(modifiedImps);
        return requestBuilder.build();
    }

    private void updateRequestWithSiteId(BidRequest.BidRequestBuilder requestBuilder,
                                         BidRequest bidRequest, ExtImpConversant impExt) {
        if (bidRequest.getSite() != null) {
            requestBuilder.site(bidRequest.getSite().toBuilder().id(impExt.getSiteId()).build());
        } else if (bidRequest.getApp() != null) {
            requestBuilder.app(bidRequest.getApp().toBuilder().id(impExt.getSiteId()).build());
        }
    }

    private ExtImpConversant parseImpExt(Imp imp) {
        final ExtImpConversant extImp;
        try {
            extImp = mapper.mapper().convertValue(imp.getExt(), CONVERSANT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Impression[%d] missing ext.bidder object");
        }

        if (StringUtils.isBlank(extImp.getSiteId())) {
            throw new PreBidException("Impression[%d] requires ext.bidder.site_id");
        }
        return extImp;
    }

    private static Imp modifyImp(Imp imp, ExtImpConversant impExt) {
        final BigDecimal extBidfloor = impExt.getBidfloor();
        final String extTagId = impExt.getTagId();
        final Integer extSecure = impExt.getSecure();
        final boolean shouldChangeSecure = extSecure != null && (imp.getSecure() == null || imp.getSecure() == 0);
        final Banner impBanner = imp.getBanner();
        final Integer extPosition = impExt.getPosition();
        final Video impVideo = imp.getVideo();

        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (impBanner != null && extPosition != null) {
            impBuilder.banner(impBanner.toBuilder()
                    .pos(AD_POSITIONS.contains(extPosition) ? extPosition : null)
                    .build());
        }

        return impBuilder
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VER)
                .bidfloor(extBidfloor != null ? extBidfloor : imp.getBidfloor())
                .tagid(extTagId != null ? extTagId : imp.getTagid())
                .secure(shouldChangeSecure ? extSecure : imp.getSecure())
                .video(impVideo != null ? modifyVideo(impVideo, impExt) : null)
                .build();
    }

    private static Video modifyVideo(Video video, ExtImpConversant impExt) {
        final List<String> extMimes = impExt.getMimes();
        final Integer extMaxduration = impExt.getMaxduration();
        final Integer extPosition = impExt.getPosition();
        final List<Integer> extProtocols = impExt.getProtocols();
        final List<Integer> extApi = impExt.getApi();
        return video.toBuilder()
                .mimes(extMimes != null ? extMimes : video.getMimes())
                .maxduration(extMaxduration != null ? extMaxduration : video.getMaxduration())
                .pos(makePosition(extPosition, video.getPos()))
                .api(makeApi(extApi, video.getApi()))
                .protocols(makeProtocols(extProtocols, video.getProtocols()))
                .build();
    }

    private static Integer makePosition(Integer position, Integer videoPos) {
        return isValidPosition(position) ? position : isValidPosition(videoPos) ? videoPos : null;
    }

    private static boolean isValidPosition(Integer position) {
        return position != null && AD_POSITIONS.contains(position);
    }

    private static List<Integer> makeApi(List<Integer> extApi, List<Integer> videoApi) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extApi) ? extApi : videoApi;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(APIS::contains).collect(Collectors.toList()) : videoApi;
    }

    private static List<Integer> makeProtocols(List<Integer> extProtocols, List<Integer> videoProtocols) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extProtocols) ? extProtocols : videoProtocols;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(PROTOCOLS::contains).collect(Collectors.toList()) : videoProtocols;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty bid request");
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(0);
        return firstSeatBid.getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
