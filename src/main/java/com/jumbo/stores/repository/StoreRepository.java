package com.jumbo.stores.repository;

import com.jumbo.stores.domain.Store;
import java.util.List;

public interface StoreRepository {
    List<Store> findAll();
}
