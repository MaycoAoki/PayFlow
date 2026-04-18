package io.payflow.transfer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.payflow.transfer.kafka.command.CreditCommand;
import io.payflow.transfer.kafka.command.DebitCommand;
import io.payflow.transfer.kafka.command.ReverseDebitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferCommandPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String transferCommandsTopic;

    public TransferCommandPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${payflow.kafka.topics.transfer-commands}") String transferCommandsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transferCommandsTopic = transferCommandsTopic;
    }

    public void publishDebitCommand(String transferId, String sourceAccountId, String amount, String currency) {
        send(sourceAccountId, DebitCommand.of(transferId, sourceAccountId, amount, currency));
    }

    public void publishCreditCommand(String transferId, String targetAccountId, String amount, String currency) {
        send(targetAccountId, CreditCommand.of(transferId, targetAccountId, amount, currency));
    }

    public void publishReverseDebitCommand(String transferId, String sourceAccountId, String amount, String currency) {
        send(sourceAccountId, ReverseDebitCommand.of(transferId, sourceAccountId, amount, currency));
    }

    private void send(String partitionKey, Object command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(transferCommandsTopic, partitionKey, payload);
            log.info("Published command commandType={} partitionKey={}", command.getClass().getSimpleName(), partitionKey);
        } catch (Exception e) {
            log.error("Failed to publish command commandType={}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to publish Kafka command", e);
        }
    }
}
