package io.cranial.platform.provider.adapter;

import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.EmbeddingRequest;
import io.cranial.platform.provider.model.EmbeddingResponse;
import io.cranial.platform.provider.model.ProviderHealth;

import java.util.concurrent.CompletableFuture;

public interface ModelProviderAdapter {

    String providerCode();

    ProviderHealth checkHealth(ProviderEntity provider);

    CompletableFuture<ChatCompletionResponse> chat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request
    );

    CompletableFuture<EmbeddingResponse> embed(
            ProviderEntity provider,
            ModelEntity model,
            EmbeddingRequest request
    );

    default CompletableFuture<Void> streamChat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request,
            java.util.function.Consumer<String> chunkConsumer,
            java.util.function.Consumer<ChatCompletionResponse> completionConsumer
    ) {
        return chat(provider, model, request).thenAccept(resp -> {
            chunkConsumer.accept(resp.message().content());
            completionConsumer.accept(resp);
        });
    }
}

