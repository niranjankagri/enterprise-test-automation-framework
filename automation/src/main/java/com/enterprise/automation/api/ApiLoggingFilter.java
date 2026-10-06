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
 * Logs every API call: one INFO line ("POST /api/customers -> 201 in 35 ms") and, at DEBUG, the
 * request and response bodies with secrets masked.
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
        // Send the request (and run any later filters)
        Response response = context.next(request, responseSpec);
        long millis = (System.nanoTime() - start) / 1_000_000;

        // Always: one compact line per call
        LOG.info("{} {} -> {} in {} ms", request.getMethod(), request.getDerivedPath(), response.getStatusCode(), millis);
        // Bodies only at DEBUG (the file log); the check avoids building big strings when not needed
        if (LOG.isDebugEnabled()) {
            Object body = request.getBody();
            LOG.debug("Request {} {}{}", request.getMethod(), request.getURI(),
                    body == null ? "" : "\n" + SecretMasker.mask(String.valueOf(body)));
            // The request id links this line to the application's own log
            LOG.debug("Response {} [X-Request-Id {}]\n{}", response.getStatusCode(), response.getHeader("X-Request-Id"),
                    SecretMasker.mask(response.asString()));
        }
        return response;
    }
}
