package cloud.localstack.LocalStackClient;

import java.util.List;

public class CheckActionAllowedResponse {
    public Boolean allowed;
    public List<ActionResult> implicit_deny;
    public List<ActionResult> explicit_deny;
    public SourcePrincipal source_principal;
}
