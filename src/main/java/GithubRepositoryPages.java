import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Filters paginated repository arrays while preserving GitHub's response order. */
final class GithubRepositoryPages {
    private GithubRepositoryPages() {}

    static List<JsonNode> unique(JsonNode page, Set<String> seenRepositoryIds) {
        if (page == null || !page.isArray()) {
            throw new IllegalArgumentException(
                    "GitHub repository response must be an array.");
        }

        List<JsonNode> uniqueRepositories = new ArrayList<>();
        for (JsonNode repository : page) {
            String repositoryId = repository.path("id").asText("");
            if (repositoryId.isBlank() || seenRepositoryIds.add(repositoryId)) {
                uniqueRepositories.add(repository);
            }
        }
        return uniqueRepositories;
    }
}
