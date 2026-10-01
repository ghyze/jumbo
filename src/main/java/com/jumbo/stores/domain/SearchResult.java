package com.jumbo.stores.domain;

import java.util.List;
import lombok.Builder;

@Builder
public record SearchResult(List<NearestStore> stores, List<SearchWarning> warnings) {
    public SearchResult {
        stores = List.copyOf(stores);
        warnings = List.copyOf(warnings);
    }
}
