package swdchatbox.system.subject.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubjectRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    private Boolean active;
}
