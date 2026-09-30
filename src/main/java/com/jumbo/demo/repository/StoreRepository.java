package com.jumbo.demo.repository;

import com.jumbo.demo.domain.Store;
import java.util.List;

public interface StoreRepository {
    List<Store> findAll();
}
