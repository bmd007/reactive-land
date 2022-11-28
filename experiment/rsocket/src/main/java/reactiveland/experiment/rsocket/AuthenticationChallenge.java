package reactiveland.experiment.rsocket;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@JsonDeserialize(builder = AuthenticationChallenge.AuthenticationChallengeBuilder.class)
@Builder(toBuilder = true)
@Data
@Table("authentication_challenge")
public class AuthenticationChallenge {

    @Id
    @NotBlank
    String id;
    @NotBlank
    String nonce;
    @NotNull
    Instant expiresAt;
    @NotNull
    States state;
    @Nullable
    String customerId;

    public static AuthenticationChallenge createNew() {
        return AuthenticationChallenge.builder()
                .id(UUID.randomUUID().toString())
                .nonce(UUID.randomUUID().toString())
                .state(States.AWAITING_CAPTURE)
                .expiresAt(Instant.now().plusSeconds(30))
                .build();
    }

    public AuthenticationChallenge capture() {
        if (canBeCaptured()) {
            return toBuilder()
                    .state(States.CAPTURED)
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
        throw new IllegalStateException("wrong state transition is requested. Only AWAITING_CAPTURE challenges can be CAPTURED. isAlive:" + isAlive());
    }

    public boolean isSingable() {
        return (this.getState().equals(States.CAPTURED) || this.getState().equals(States.AWAITING_CAPTURE)) && isAlive();
    }

    public boolean canBeCaptured() {
        return this.getState().equals(States.AWAITING_CAPTURE) && isAlive();
    }

    public AuthenticationChallenge sign(String customerId) {
        if (isSingable()) {
            return toBuilder()
                    .state(States.SIGNED)
                    .expiresAt(Instant.now().plusSeconds(100))
                    .customerId(customerId)
                    .build();
        }
        throw new IllegalStateException("wrong state transition is requested. =>"+ this);
    }

    public boolean authenticate(String nonce) {
        return nonce.equals(this.nonce);
    }

    public boolean isAlive() {
        return Instant.now().isBefore(this.expiresAt);
    }

    public enum States {
        AWAITING_CAPTURE, CAPTURED, SIGNED
    }

}
