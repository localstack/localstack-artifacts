package cloud.localstack.localstack_client;

import java.util.List;

import org.apache.tinkerpop.shaded.jackson.annotation.JsonProperty;

public class CheckActionAllowedResponse {
    public Boolean allowed;
    public SourcePrincipal source_principal;

    @JsonProperty("implicit_deny")
    public List<ActionResult> implicitDeny;

    @JsonProperty("explicit_deny")
    public List<ActionResult> explicitDeny;

}
