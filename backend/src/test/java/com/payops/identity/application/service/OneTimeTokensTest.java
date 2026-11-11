package com.payops.identity.application.service;

import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.TokenPurpose;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OneTimeTokensTest {

    private final IdentityFakes fx = new IdentityFakes();

    @Test
    void tokensAreHighEntropyAndOnlyTheirHashIsStored() {
        String raw = fx.tokens.issue(7L, TokenPurpose.PASSWORD_RESET);
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(raw).isNotEqualTo(fx.tokens.issue(8L, TokenPurpose.PASSWORD_RESET));

        ActionToken stored = fx.tokenStore.rows.get(0);
        assertThat(stored.tokenHash()).hasSize(64).isEqualTo(ActionToken.hash(raw)).isNotEqualTo(raw);
        assertThat(fx.tokenStore.rows).noneMatch(t -> t.tokenHash().contains(raw));
    }

    @Test
    void tokensExpireAfterTheirPurposeLifetime() {
        String reset = fx.tokens.issue(7L, TokenPurpose.PASSWORD_RESET);
        String invite = fx.tokens.issue(7L, TokenPurpose.INVITATION);
        fx.clock.advance(Duration.ofMinutes(29));
        assertThat(fx.tokens.resolve(reset, TokenPurpose.PASSWORD_RESET).userId()).isEqualTo(7L);
        fx.clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(() -> fx.tokens.resolve(reset, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(fx.tokens.resolve(invite, TokenPurpose.INVITATION)).isNotNull();
        fx.clock.advance(Duration.ofHours(72));
        assertThatThrownBy(() -> fx.tokens.resolve(invite, TokenPurpose.INVITATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokensAreSingleUse() {
        String raw = fx.tokens.issue(7L, TokenPurpose.EMAIL_VERIFICATION);
        ActionToken token = fx.tokens.resolve(raw, TokenPurpose.EMAIL_VERIFICATION);
        fx.tokens.consume(token);
        assertThatThrownBy(() -> fx.tokens.consume(token)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> fx.tokens.resolve(raw, TokenPurpose.EMAIL_VERIFICATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void newTokenSupersedesOutstandingOnesAndPurposesDoNotMix() {
        String first = fx.tokens.issue(7L, TokenPurpose.PASSWORD_RESET);
        String second = fx.tokens.issue(7L, TokenPurpose.PASSWORD_RESET);
        assertThatThrownBy(() -> fx.tokens.resolve(first, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(fx.tokens.resolve(second, TokenPurpose.PASSWORD_RESET)).isNotNull();
        assertThatThrownBy(() -> fx.tokens.resolve(second, TokenPurpose.INVITATION))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> fx.tokens.resolve("not-a-token", TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> fx.tokens.resolve(null, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
    }
}
