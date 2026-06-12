package swdchatbox.system.student.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentUpdateRequest {

    private String fullName;

    @Email
    private String email;

    @Size(min = 8)
    private String password;

    private Boolean active;

    private Boolean emailVerified;
}
