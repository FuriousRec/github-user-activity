import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.Duration;

/** Shared validation and request policy for public GitHub API calls. */
final class GithubRequests {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private GithubRequests() {}

    static String username(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Please provide a GitHub username.");
        }
        String value = input.trim();
        if (!value.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                || value.contains("--")) {
            throw new IllegalArgumentException(
                    "Use a GitHub username of 1–39 letters, digits or single hyphens; "
                    + "it must start and end with a letter or digit.");
        }
        return value;
    }

    static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    static HttpRequest get(String url) {
        return get(url, null);
    }

    static HttpRequest get(String url, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "github-user-activity-cli");

        String cleanToken = token(token);
        if (cleanToken != null) {
            request.header("Authorization", "Bearer " + cleanToken);
        }

        return request.GET().build();
    }

    static String token(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String value = input.strip();
        if (value.length() > 255
                || value.chars().anyMatch(character ->
                        character <= 32 || character >= 127)) {
            throw new IllegalArgumentException(
                    "GITHUB_TOKEN must contain 1–255 printable ASCII characters without spaces.");
        }
        return value;
    }

    static boolean isRateLimited(int statusCode, HttpHeaders headers) {
        if (statusCode == 429) {
            return true;
        }
        if (statusCode != 403) {
            return false;
        }

        // GitHub also uses 403 for ordinary authorization failures. Treat it as
        // throttling only when the response carries primary- or secondary-limit
        // evidence, so users do not receive misleading retry advice.
        String remaining = headers.firstValue("X-RateLimit-Remaining").orElse("");
        return "0".equals(remaining.trim())
                || nonNegativeLong(headers.firstValue("Retry-After").orElse(null)) != null;
    }

    static String rateLimitMessage(HttpHeaders headers) {
        Long retryAfterSeconds = nonNegativeLong(headers.firstValue("Retry-After").orElse(null));
        if (retryAfterSeconds != null) {
            return "GitHub API rate limit reached. Please try again in "
                    + retryAfterSeconds + (retryAfterSeconds == 1 ? " second." : " seconds.");
        }

        Long resetEpochSeconds =
                nonNegativeLong(headers.firstValue("X-RateLimit-Reset").orElse(null));
        if (resetEpochSeconds != null) {
            return "GitHub API rate limit reached. Please try again after "
                    + Instant.ofEpochSecond(resetEpochSeconds) + ".";
        }

        return "GitHub API rate limit reached. Please try again later.";
    }

    private static Long nonNegativeLong(String value) {
        if (value == null || !value.matches("[0-9]+")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
