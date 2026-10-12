import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/** Offline regression checks; throws on failure even without Java -ea. */
public final class GithubRequestsChecks {
    private static int checks;

    public static void main(String[] args) {
        for (String valid : new String[] {"a", "FuriousRec", "user-name", "a1", "a".repeat(39)}) {
            equal(valid, GithubRequests.username(valid));
        }
        equal("FuriousRec", GithubRequests.username("  FuriousRec  "));
        for (String invalid : new String[] {null, "", " ", "-user", "user-", "a--b",
                "a/b", "a?x=y", "a#fragment", "a b", "a_b", "ümlaut", "a".repeat(40)}) {
            rejects(invalid);
        }
        equal(GithubRequests.CONNECT_TIMEOUT, GithubRequests.client().connectTimeout().orElseThrow());
        for (String url : new String[] {
                "https://api.github.com/users/FuriousRec/events",
                "https://api.github.com/users/FuriousRec/repos?per_page=100&page=2"}) {
            HttpRequest request = GithubRequests.get(url);
            equal(url, request.uri().toString());
            equal("GET", request.method());
            equal(GithubRequests.REQUEST_TIMEOUT, request.timeout().orElseThrow());
            equal("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
            equal("github-user-activity-cli", request.headers().firstValue("User-Agent").orElseThrow());
        }
        equal(true, GithubRequests.isRateLimited(403));
        equal(true, GithubRequests.isRateLimited(429));
        equal(false, GithubRequests.isRateLimited(401));
        equal("GitHub API rate limit reached. Please try again in 1 second.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "1")));
        equal("GitHub API rate limit reached. Please try again in 120 seconds.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "120")));
        equal("GitHub API rate limit reached. Please try again after 2023-11-14T22:13:20Z.",
                GithubRequests.rateLimitMessage(headers("X-RateLimit-Reset", "1700000000")));
        equal("GitHub API rate limit reached. Please try again later.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "invalid")));
        equal("GitHub API rate limit reached. Please try again later.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "9".repeat(30))));
        System.out.println("Passed " + checks + " offline checks.");
    }

    private static HttpHeaders headers(String name, String value) {
        return HttpHeaders.of(Map.of(name, List.of(value)), (key, item) -> true);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
        checks++;
    }

    private static void rejects(String input) {
        try {
            GithubRequests.username(input);
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid username: " + input);
    }
}
