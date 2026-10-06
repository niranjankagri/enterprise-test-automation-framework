package com.enterprise.automation.api;

import com.enterprise.automation.reporting.Report;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.stream.Collectors;

/**
 * Puts every API call into the report as a step ("POST /api/customers -> 201") with the request
 * and the response attached: method, URL, headers, body.
 *
 * <p>Written here instead of using Allure's REST Assured filter because that one attaches
 * {@code Authorization} headers and password fields as they are. Here everything goes through
 * {@link SecretMasker} first, so reports can be shared.
 */
public final class ReportingApiFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        String call = request.getMethod() + " " + request.getDerivedPath();
        Response[] holder = new Response[1];
        Report.step(call, () -> {
            Report.attachText("Request", describeRequest(request));
            holder[0] = context.next(request, responseSpec);
            Report.attachText("Response " + holder[0].getStatusCode(), describeResponse(holder[0]));
            Report.log("-> " + holder[0].getStatusCode() + " in " + holder[0].getTime() + " ms");
        });
        return holder[0];
    }

    private static String describeRequest(FilterableRequestSpecification request) {
        String headers = request.getHeaders().asList().stream().map(ReportingApiFilter::header)
                .collect(Collectors.joining("\n"));
        Object body = request.getBody();
        return request.getMethod() + " " + request.getURI() + "\n" + headers
                + (body == null ? "" : "\n\n" + SecretMasker.mask(String.valueOf(body)));
    }

    private static String describeResponse(Response response) {
        String headers = response.getHeaders().asList().stream().map(ReportingApiFilter::header)
                .collect(Collectors.joining("\n"));
        String body = response.asString();
        return response.getStatusLine() + "\n" + headers + (body.isEmpty() ? "" : "\n\n" + SecretMasker.mask(body));
    }

    private static String header(Header header) {
        boolean secret = header.getName().equalsIgnoreCase("Authorization") || header.getName().equalsIgnoreCase("Cookie");
        return header.getName() + ": " + (secret ? SecretMasker.mask(header.getValue()).replaceAll("^(?!Bearer).*", "****")
                : header.getValue());
    }
}
