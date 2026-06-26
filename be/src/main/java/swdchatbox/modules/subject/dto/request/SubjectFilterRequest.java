package swdchatbox.modules.subject.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubjectFilterRequest {
    private Boolean active;
    private String keyword;
}
