import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;

/** Combines paginated event arrays while retaining GitHub's newest-first order. */
final class GithubEventPages {
    private GithubEventPages() {}

    static void appendUnique(
            JsonNode page,
            List<JsonNode> events,
            Set<String> seenEventIds) {

        if (page == null || !page.isArray()) {
            throw new IllegalArgumentException(
                    "GitHub event response must be an array.");
        }

        for (JsonNode event : page) {
            String eventId = event.path("id").asText("");
            if (eventId.isBlank() || seenEventIds.add(eventId)) {
                events.add(event);
            }
        }
    }
}
