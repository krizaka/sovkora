package com.krizaka.sovkora.core.infrastructure.adapter.routing;

import com.krizaka.sovkora.core.domain.model.RoutingPolicy;
import com.krizaka.sovkora.core.domain.model.StepModelCandidate;
import com.krizaka.sovkora.core.domain.ports.outbound.StepRouteProvider;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a capability's policy and ordered fallback models from runtime DB rows.
 *
 * <p>{@code routing.<capability>.<policy>} in {@code orazaka_runtime_config} holds a JSON array of
 * model names, primary first. Each name references {@code orazaka_models}; only an explicit {@code
 * runs_on=local} declares a sovereign candidate. Missing configuration never uses a catalog
 * default.
 */
public class RuntimeStepRouteProvider implements StepRouteProvider {

  private final RuntimeConfigProvider config;
  private final CatalogModelManager catalog;
  private final ObjectMapper mapper;

  public RuntimeStepRouteProvider(
      RuntimeConfigProvider config, CatalogModelManager catalog, ObjectMapper mapper) {
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
    this.catalog = Objects.requireNonNull(catalog, "CatalogModelManager cannot be null");
    this.mapper = Objects.requireNonNull(mapper, "ObjectMapper cannot be null");
  }

  @Override
  public List<StepModelCandidate> candidates(String capability, RoutingPolicy policy) {
    String key = "routing." + capability + "." + policy.name();
    String declaration = config.getString(key, null);
    if (declaration == null) {
      return List.of();
    }
    List<String> names = modelNames(key, declaration);
    Map<String, CatalogModelDto> models =
        catalog.getAllModels().stream()
            .collect(Collectors.toMap(CatalogModelDto::modelName, Function.identity()));
    return names.stream()
        .map(
            name -> {
              CatalogModelDto model = models.get(name);
              return new StepModelCandidate(
                  name,
                  model != null && "local".equals(model.runsOn()),
                  model != null && CatalogModelDto.STATUS_AVAILABLE.equals(model.status()));
            })
        .toList();
  }

  private List<String> modelNames(String key, String declaration) {
    try {
      var array = mapper.readTree(declaration);
      if (array == null || !array.isArray()) {
        throw new IllegalStateException("Routing declaration must be a model-name array: " + key);
      }
      var names = new ArrayList<String>();
      var seen = new HashSet<String>();
      for (var name : array) {
        if (!name.isString() || name.asString().isBlank() || !seen.add(name.asString())) {
          throw new IllegalStateException(
              "Routing model names must be nonblank and unique: " + key);
        }
        names.add(name.asString());
      }
      return List.copyOf(names);
    } catch (JacksonException e) {
      throw new IllegalStateException("Invalid routing declaration: " + key, e);
    }
  }
}
