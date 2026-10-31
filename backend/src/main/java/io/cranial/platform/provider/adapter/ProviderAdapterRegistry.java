package io.cranial.platform.provider.adapter;

import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderAdapterRegistry {

    private final Map<String, ModelProviderAdapter> adapters = new ConcurrentHashMap<>();

    public ProviderAdapterRegistry(List<ModelProviderAdapter> adapterList) {
        for (ModelProviderAdapter adapter : adapterList) {
            adapters.put(adapter.providerCode().toLowerCase(), adapter);
        }
    }

    public ModelProviderAdapter getAdapter(String providerCode) {
        if (providerCode == null) {
            throw new CranialException(ErrorCode.PROVIDER_UNAVAILABLE, "Provider code cannot be null");
        }
        ModelProviderAdapter adapter = adapters.get(providerCode.toLowerCase());
        if (adapter == null) {
            throw new CranialException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    "No adapter registered for provider: " + providerCode
            );
        }
        return adapter;
    }

    public boolean hasAdapter(String providerCode) {
        return providerCode != null && adapters.containsKey(providerCode.toLowerCase());
    }
}

