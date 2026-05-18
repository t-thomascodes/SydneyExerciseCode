package property;

import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;

import java.util.List;
import java.util.Optional;

public class PropertyController {

    private final PropertyDAO properties;

    public PropertyController(PropertyDAO properties) {
        this.properties = properties;
    }

    @OpenApi(
        summary = "Create a property",
        operationId = "createProperty",
        path = "/property",
        methods = HttpMethod.POST,
        tags = {"Property"},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = Property.class)}),
        responses = {
            @OpenApiResponse(status = "201", description = "Plain text confirmation"),
            @OpenApiResponse(status = "400", description = "Validation or insert failure")
        }
    )
    public void createProperty(Context ctx) {

        // Extract Property from request body
        // TO DO override Validator exception method to report better error message
        Property property = ctx.bodyValidator(Property.class)
                                .get();

        // store new property in data set
        if (properties.newProperty(property)) {
            ctx.result("Property Created");
            ctx.status(201);
        } else {
            ctx.result("Failed to add property");
            ctx.status(400);
        }
    }

    @OpenApi(
        summary = "List properties (HTML table)",
        operationId = "getAllProperties",
        path = "/property",
        methods = HttpMethod.GET,
        tags = {"Property"},
        queryParams = {
            @OpenApiParam(name = "minPrice", type = Long.class, required = false),
            @OpenApiParam(name = "maxPrice", type = Long.class, required = false)
        },
        responses = {
            @OpenApiResponse(status = "200", description = "HTML table"),
            @OpenApiResponse(status = "404", description = "HTML error page")
        }
    )
    public void getAllProperties(Context ctx) {
        String minParam = ctx.queryParam("minPrice");
        String maxParam = ctx.queryParam("maxPrice");

        List<Property> allProperties;
        if (minParam != null || maxParam != null) {
            long min = minParam != null ? Long.parseLong(minParam) : Long.MIN_VALUE;
            long max = maxParam != null ? Long.parseLong(maxParam) : Long.MAX_VALUE;
            allProperties = properties.getPropertiesByPriceRange(min, max);
        } else {
            allProperties = properties.getAllProperties();
        }

        if (allProperties.isEmpty()) {
            ctx.html(errorHtml("No Properties Found"));
            ctx.status(404);
        } else {
            ctx.html(propertyListHtml("All Properties", allProperties, properties.wasLastResultCapped()));
            ctx.status(200);
        }
    }

    @OpenApi(
        summary = "Get one property by id (HTML)",
        operationId = "getPropertyById",
        path = "/property/{propertyID}",
        methods = HttpMethod.GET,
        tags = {"Property"},
        pathParams = {@OpenApiParam(name = "propertyID", type = String.class, description = "Property id")},
        responses = {
            @OpenApiResponse(status = "200", description = "HTML table"),
            @OpenApiResponse(status = "404", description = "HTML error page")
        }
    )
    public void getPropertyByID(Context ctx) {
        String id = ctx.pathParam("propertyID");
        Optional<Property> property = properties.getPropertyById(id);
        if (property.isPresent()) {
            properties.incrementPropertyAccessCount(id);
            ctx.html(propertyListHtml("Property " + id, List.of(property.get()), false));
            ctx.status(200);
        } else {
            ctx.html(errorHtml("Property not found"));
            ctx.status(404);
        }
    }

    @OpenApi(
        summary = "Find properties by postcode (HTML)",
        operationId = "findPropertyByPostcode",
        path = "/property/postcode/{postcode}",
        methods = HttpMethod.GET,
        tags = {"Property"},
        pathParams = {@OpenApiParam(name = "postcode", type = String.class)},
        responses = {
            @OpenApiResponse(status = "200", description = "HTML table"),
            @OpenApiResponse(status = "404", description = "HTML error page")
        }
    )
    public void findPropertyByPostCode(Context ctx) {
        String postCode = ctx.pathParam("postcode");
        properties.incrementPostCodeSearchCount(postCode);
        List<Property> result = properties.getPropertiesByPostCode(postCode);
        if (result.isEmpty()) {
            ctx.html(errorHtml("No properties for postcode found"));
            ctx.status(404);
        } else {
            ctx.html(propertyListHtml("Properties in Postcode " + postCode, result, properties.wasLastResultCapped()));
            ctx.status(200);
        }
    }

    private String propertyListHtml(String title, List<Property> props, boolean capped) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><title>").append(title).append("</title></head><body>");
        sb.append("<h1>").append(title).append("</h1>");
        sb.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\">");
        sb.append("<tr><th>Property ID</th><th>Postcode</th><th>Price</th><th>For Sale</th></tr>");
        for (Property p : props) {
            sb.append("<tr>")
              .append("<td>").append(p.propertyID).append("</td>")
              .append("<td>").append(p.postcode).append("</td>")
              .append("<td>").append(p.propertyPrice).append("</td>")
              .append("<td>").append(p.forSale).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
        if (capped) {
            sb.append("<p>Showing the first ")
              .append(PropertyDAO.LIST_LIMIT)
              .append(" matching properties. Narrow the query with minPrice, maxPrice, or postcode.</p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String errorHtml(String message) {
        return "<!DOCTYPE html><html><head><title>Error</title></head><body>"
             + "<h1>Error</h1><p>" + message + "</p></body></html>";
    }
}
