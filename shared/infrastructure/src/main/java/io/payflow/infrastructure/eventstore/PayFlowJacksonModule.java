package io.payflow.infrastructure.eventstore;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * Jackson module that registers (de)serializers for PayFlow domain value objects.
 *
 * <p>Required types:
 * <ul>
 *   <li>{@link Money} — no default constructor; needs custom (de)serializer
 *   <li>{@link AccountId} — record wrapping UUID; needs custom (de)serializer
 *   <li>{@link TransferId} — record wrapping UUID; needs custom (de)serializer
 *   <li>{@link java.util.Currency} — serialized as ISO-4217 code string
 * </ul>
 *
 * <p>Use {@link #createObjectMapper()} to get a fully configured ObjectMapper.
 */
public class PayFlowJacksonModule extends SimpleModule {

    public PayFlowJacksonModule() {
        super("PayFlowJacksonModule");
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
        addSerializer(AccountId.class, new AccountIdSerializer());
        addDeserializer(AccountId.class, new AccountIdDeserializer());
        addSerializer(TransferId.class, new TransferIdSerializer());
        addDeserializer(TransferId.class, new TransferIdDeserializer());
        addSerializer(Currency.class, new CurrencySerializer());
        addDeserializer(Currency.class, new CurrencyDeserializer());
    }

    /**
     * Creates a fully configured {@link ObjectMapper} for PayFlow event serialization.
     */
    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new PayFlowJacksonModule());
    }

    // -------------------------------------------------------------------------
    // Money
    // -------------------------------------------------------------------------

    static class MoneySerializer extends StdSerializer<Money> {
        MoneySerializer() { super(Money.class); }

        @Override
        public void serialize(Money value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("amount", value.amount().toPlainString());
            gen.writeStringField("currency", value.currency().getCurrencyCode());
            gen.writeEndObject();
        }
    }

    static class MoneyDeserializer extends StdDeserializer<Money> {
        MoneyDeserializer() { super(Money.class); }

        @Override
        public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            BigDecimal amount = new BigDecimal(node.get("amount").asText());
            Currency currency = Currency.getInstance(node.get("currency").asText());
            return new Money(amount, currency);
        }
    }

    // -------------------------------------------------------------------------
    // AccountId
    // -------------------------------------------------------------------------

    static class AccountIdSerializer extends StdSerializer<AccountId> {
        AccountIdSerializer() { super(AccountId.class); }

        @Override
        public void serialize(AccountId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("value", value.value().toString());
            gen.writeEndObject();
        }
    }

    static class AccountIdDeserializer extends StdDeserializer<AccountId> {
        AccountIdDeserializer() { super(AccountId.class); }

        @Override
        public AccountId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return AccountId.of(UUID.fromString(node.get("value").asText()));
        }
    }

    // -------------------------------------------------------------------------
    // TransferId
    // -------------------------------------------------------------------------

    static class TransferIdSerializer extends StdSerializer<TransferId> {
        TransferIdSerializer() { super(TransferId.class); }

        @Override
        public void serialize(TransferId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("value", value.value().toString());
            gen.writeEndObject();
        }
    }

    static class TransferIdDeserializer extends StdDeserializer<TransferId> {
        TransferIdDeserializer() { super(TransferId.class); }

        @Override
        public TransferId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return TransferId.of(UUID.fromString(node.get("value").asText()));
        }
    }

    // -------------------------------------------------------------------------
    // Currency
    // -------------------------------------------------------------------------

    static class CurrencySerializer extends StdSerializer<Currency> {
        CurrencySerializer() { super(Currency.class); }

        @Override
        public void serialize(Currency value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.getCurrencyCode());
        }
    }

    static class CurrencyDeserializer extends StdDeserializer<Currency> {
        CurrencyDeserializer() { super(Currency.class); }

        @Override
        public Currency deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Currency.getInstance(p.getValueAsString());
        }
    }
}
