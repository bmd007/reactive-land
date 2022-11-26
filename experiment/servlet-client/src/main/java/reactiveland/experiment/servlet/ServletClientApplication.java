package reactiveland.experiment.servlet;

import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootApplication
public class ServletClientApplication {

    private final WebClient webClient;

    public ServletClientApplication(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(codec -> codec.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .baseUrl("http://servlet")
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ServletClientApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        deleteAllPreviousChallenges().block();
        for (int i = 0; i < 1_000_000; i++) {
            askForChallengeAndUseIt().block();
        }
    }

    private Mono<Integer> deleteAllPreviousChallenges() {
        return webClient
                .delete()
                .uri("challenges")
                .retrieve()
                .bodyToMono(Integer.class)
                .doOnError(error -> log.error("error while deleting challenge", error))
                .doOnNext(nonce -> log.info("challenge is deleted"));
    }

    private Mono<AuthenticationChallenge> askForChallengeAndUseIt() {
        return webClient
                .post()
                .uri("challenges")
                .retrieve()
                .bodyToMono(AuthenticationChallenge.class)
                .doOnError(error -> log.error("error while asking for a challenge", error))
                .flatMap(challenges -> webClient
                        .post()
                        .uri("challenges/{nonce}/response", challenges.nonce())
                        .bodyValue(new ChallengeResponse(challenges.nonce(), challenges.nonce()))
                        .retrieve()
                        .bodyToMono(AuthenticationChallenge.class)
                        .doOnError(error -> log.error("error while signing challenges {}", challenges, error))
                )
                .doOnNext(challenges -> Metrics.counter("reactiveland_experiment_servlet_challenge_round_counter").increment());
    }
}