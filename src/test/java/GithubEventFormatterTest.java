import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GithubEventFormatterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode event(String type, String payload) throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", type);
        event.putObject("repo").put("name", "owner/project");
        event.set("payload", mapper.readTree(payload));
        return event;
    }

    private void expect(String expected, String type, String payload) throws Exception {
        assertEquals(expected, GithubEventFormatter.format(event(type, payload)));
    }

    @Test void formatsPushAndPreservesEmbeddedRefText() throws Exception {
        expect("Pushed commits to owner/project on branch main",
                "PushEvent", "{\"ref\":\"refs/heads/main\"}");
        expect("Pushed commits to owner/project on branch feature/refs/heads/demo",
                "PushEvent", "{\"ref\":\"refs/heads/feature/refs/heads/demo\"}");
    }

    @Test void formatsStarsAndForks() throws Exception {
        expect("Starred owner/project", "WatchEvent", "{}");
        expect("Forked owner/project as user/fork", "ForkEvent",
                "{\"forkee\":{\"full_name\":\"user/fork\"}}");
    }

    @Test void formatsCreatedRepositoriesAndBranches() throws Exception {
        expect("Created repository owner/project", "CreateEvent",
                "{\"ref_type\":\"repository\",\"ref\":null}");
        expect("Created branch \"feature\" in owner/project", "CreateEvent",
                "{\"ref_type\":\"branch\",\"ref\":\"feature\"}");
        expect("Created a tag in owner/project", "CreateEvent", "{\"ref_type\":\"tag\"}");
    }

    @Test void formatsDeletedTags() throws Exception {
        expect("Deleted tag \"v1\" from owner/project", "DeleteEvent",
                "{\"ref_type\":\"tag\",\"ref\":\"v1\"}");
    }

    @Test void formatsIssueActions() throws Exception {
        expect("Opened issue #12 in owner/project", "IssuesEvent",
                "{\"action\":\"opened\",\"issue\":{\"number\":12}}");
    }

    @Test void supportsTopLevelPullRequestNumbers() throws Exception {
        expect("Opened pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"opened\",\"number\":42,\"pull_request\":{}}");
    }

    @Test void supportsNestedPullRequestNumbers() throws Exception {
        expect("Reopened pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"reopened\",\"pull_request\":{\"number\":42}}");
    }

    @Test void distinguishesMergedFromClosedPullRequests() throws Exception {
        expect("Merged pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"closed\",\"number\":42,\"pull_request\":{\"merged\":true}}");
        expect("Closed pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"closed\",\"number\":42,\"pull_request\":{\"merged\":false}}");
        expect("Closed pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"closed\",\"number\":42}");
        expect("Merged pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"merged\",\"number\":42}");
    }

    @Test void doesNotInferMergeFromOpenEvent() throws Exception {
        expect("Opened pull request #42 in owner/project", "PullRequestEvent",
                "{\"action\":\"opened\",\"number\":42,\"pull_request\":{\"merged\":true}}");
    }

    @Test void handlesUnknownTypesAndMissingFields() throws Exception {
        expect("Performed FutureEvent in owner/project", "FutureEvent", "{}");
        assertEquals("Performed UnknownEvent in Unknown repository",
                GithubEventFormatter.format(mapper.createObjectNode()));
        expect("Pushed commits to owner/project on branch Unknown branch", "PushEvent", "{}");
    }
}
