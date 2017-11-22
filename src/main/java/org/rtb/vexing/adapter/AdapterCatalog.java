package org.rtb.vexing.adapter;

import io.vertx.core.http.HttpClient;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdapterCatalog {

    private final Map<String, Adapter> adapters;

    private AdapterCatalog(Map<String, Adapter> adapters) {
        this.adapters = adapters;
    }

    public static AdapterCatalog create(ApplicationConfig config, HttpClient httpClient) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(httpClient);

        final Map<String, Adapter> adapters = Stream.of(rubicon(config, httpClient))
                .collect(Collectors.toMap(Adapter::code, Function.identity()));

        return new AdapterCatalog(adapters);
    }

    private static RubiconAdapter rubicon(ApplicationConfig config, HttpClient httpClient) {
        return new RubiconAdapter(
                config.getString("adapters.rubicon.endpoint"),
                config.getString("adapters.rubicon.usersync_url"),
                config.getString("adapters.rubicon.XAPI.Username"),
                config.getString("adapters.rubicon.XAPI.Password"),
                httpClient);
    }

    public Adapter getByCode(String code) {
        return adapters.get(code);
    }

    public boolean isValidCode(String code) {
        return adapters.containsKey(code);
    }
}
