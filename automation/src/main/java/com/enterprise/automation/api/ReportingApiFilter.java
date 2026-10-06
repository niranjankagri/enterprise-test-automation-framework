package com.enterprise.automation.api;

import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.reporting.SecretMasker;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.stream.Collectors;

/**
 * Puts every API call into the report as a step ("POST /api/customers -> 201") with the request
 * and the response attached: method, URL, headers, body, status, duration and the correlation id
 * ({@code X-Request-Id}, sent by {@link ApiClient} and also written to the application's log).
 *
 * <p>Written here instead of using Allure's REST Assured filter because that one attaches
 * {@code Authorization} headers and password fields as they are. Here everything goes through
 * {@link SecretMasker} first, so reports can be shared.
 */
public final class ReportingApiFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        // Step name, e.g. "POST /customers"
        String call = request.getMethod() + " " + request.getDerivedPath();
        // A lambda cannot assign a local variable, so the response is passed out through an array
        Response[] holder = new Response[1];
        Report.step(call, () -> {
            // Attach the request before sending, so it is in the report even if the call throws
            Report.attachText("Request", describeRequest(request));
            holder[0] = context.next(request, responseSpec);
            // Summary first (status, duration, correlation id), then headers and body
            Report.attachText("Response " + holder[0].getStatusCode(), describeResponse(holder[0]));
            Report.log("-> " + holder[0].getStatusCode() + " in " + holder[0].getTime() + " ms [X-Request-Id "
                    + holder[0].getHeader(ApiClient.REQUEST_ID) + "]");
        });
        return holder[0];
    }

    /** Method, URL, headers and body of a request as readable text, secrets masked. */
    private static String describeRequest(FilterableRequestSpecification request) {
        String headers = request.getHeaders().asList().stream().map(ReportingApiFilter::header)
                .collect(Collectors.joining("\n"));
        Object body = request.getBody();
        return request.getMethod() + " " + request.getURI() + "\n" + headers
                + (body == null ? "" : "\n\n" + SecretMasker.mask(String.valueOf(body)));
    }

    /** Status line, duration, request id, headers and body of a response as readable text, secrets masked. */
    private static String describeResponse(Response response) {
        String headers = response.getHeaders().asList().stream().map(ReportingApiFilter::header)
                .collect(Collectors.joining("\n"));
        String body = response.asString();
        return response.getStatusLine() + "\nDuration: " + response.getTime() + " ms\nRequest ID: "
                + response.getHeader(ApiClient.REQUEST_ID) + "\n\n" + headers +(body.isEmpty() ? "" : "\n\n" + SecretMasker.mask(body));
    }

    /** "Name: value"; Authorization keeps "Bearer ****", other secret headers (Cookie, X-Api-Key) become "****". */
    private static String header(Header header) {
        return header.getName() + ": " + SecretMasker.maskHeader(header.getName(), header.getValue());
    }
}
