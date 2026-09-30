package com.reviewsales.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;

/** collector(FastAPI) 내부 API 호출. collector 의 에러 코드를 그대로 BusinessException 으로 옮긴다. */
@Component
public class CollectorClient {

    private static final Logger log = LoggerFactory.getLogger(CollectorClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CollectorClient(AppProperties props, ObjectMapper objectMapper) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.collector().connectTimeout());
        factory.setReadTimeout(props.collector().readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(props.collector().baseUrl())
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    public PlaceInfo resolvePlace(String placeUrl) {
        JsonNode data = post("/internal/v1/places/resolve", Map.of("placeUrl", placeUrl));
        return new PlaceInfo(data.path("placeId").asText(), data.path("placeName").asText(null),
                data.path("address").asText(null));
    }

    public CollectResult collectReviews(String placeId, LocalDate since, int maxRequests) {
        JsonNode data = post("/internal/v1/reviews/collect",
                Map.of("placeId", placeId, "since", since.toString(), "maxRequests", maxRequests));
        return objectMapper.convertValue(data, CollectResult.class);
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode root = restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                throw new BusinessException(ErrorCode.COLLECTOR_UNAVAILABLE, "수집 서버 응답이 비어 있습니다.");
            }
            return root.has("data") ? root.get("data") : root;
        } catch (RestClientResponseException e) {
            throw toBusinessException(e);
        } catch (ResourceAccessException e) {
            log.warn("collector 연결 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.COLLECTOR_UNAVAILABLE);
        }
    }

    private BusinessException toBusinessException(RestClientResponseException e) {
        try {
            JsonNode err = objectMapper.readTree(e.getResponseBodyAsString()).path("error");
            String code = err.path("code").asText("");
            String message = err.path("message").asText(null);
            for (ErrorCode c : ErrorCode.values()) {
                if (c.name().equals(code)) {
                    return new BusinessException(c, message != null ? message : c.defaultMessage());
                }
            }
        } catch (Exception ignore) {
            // 아래 기본 처리
        }
        log.warn("collector 오류 응답 {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new BusinessException(ErrorCode.COLLECTOR_UNAVAILABLE,
                "수집 서버 오류 (" + e.getStatusCode().value() + ")");
    }

    public record PlaceInfo(String placeId, String placeName, String address) {
    }

    public record CollectResult(String status, String stoppedReason, int requestCount, List<CollectedReview> reviews) {
    }

    public record CollectedReview(LocalDate writtenAt, LocalDate visitedAt, BigDecimal rating, String content,
                                  String authorHash, String dedupKey) {
    }
}
