package io.payflow.transfer.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(
    ids = "io.payflow:account-spring:+:stubs",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
@ActiveProfiles("test")
public class MoneyDebitedContractTest {

    @Autowired
    private StubTrigger stubTrigger;

    @Test
    void shouldHandleMoneyDebitedEvent() {
        stubTrigger.trigger("moneyDebited");
        // Stub runner triggers the contract — if it's consumed without error, the test passes
        // The consumer handles the event via AccountEventConsumer
    }
}
