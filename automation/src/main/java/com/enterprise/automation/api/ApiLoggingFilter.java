package com.enterprise.automation.api;

import com.enterprise.automation.reporting.SecretMasker;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs every API call: one INFO line ("POST /api/customers -> 201 in 35 ms [X-Request-Id ...]") and,
 * at DEBUG, the request and response bodies with secrets masked.
 *
 * <p>Masking happens here, in one place, so passwords and tokens never reach a log file or a
 * report, whichever test makes the call.
 */
public final class ApiLoggingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiLoggingFilter.class);

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        // nanoTime: a monotonic clock, right for measuring durations
        long start = System.nanoTime();
        // The correlation id set by ApiClient, also logged when the call fails without a response
        String requestId = request.getHeaders().getValue(ApiClient.REQUEST_ID);
        Response response;
        try {
            // Send the request (and run any later filters)
            response = context.next(request, responseSpec);
        } catch (RuntimeException e) {
            LOG.error("{} {} failed [X-Request-Id {}]: {}", request.getMethod(), request.getDerivedPath(), requestId, e.toString());
            throw e;
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        // Always: one compact line per call
        LOG.info("{} {} -> {} in {} ms [X-Request-Id {}]", request.getMethod(), request.getDerivedPath(),
                response.getStatusCode(), millis, requestId);
        // Bodies only at DEBUG (the file log); the check avoids building big strings when not needed
        if (LOG.isDebugEnabled()) {
            Object body = request.getBody();
            LOG.debug("Request {} {}{}", request.getMethod(), request.getURI(),
                    body == null ? "" : "\n" + SecretMasker.mask(String.valueOf(body)));
            LOG.debug("Response {}\n{}", response.getStatusCode(), SecretMasker.mask(response.asString()));
        }
        return response;
    }
}
