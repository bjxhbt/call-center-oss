import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DemoHealthCheck {
    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault("OCH_API_PORT", "14320");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().contains("\"openapi\"")) System.exit(1);
    }
}
