package com.demo.inventory.repository;

import com.demo.inventory.model.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, String> {

    /**
     * 依距離排序倉庫（Haversine 公式計算球面距離）
     * 讓系統優先選擇離客戶最近的有貨倉庫
     */
    @Query(value = """
        SELECT w.*, (
            6371 * ACOS(
                COS(RADIANS(:lat)) * COS(RADIANS(w.latitude))
                * COS(RADIANS(w.longitude) - RADIANS(:lng))
                + SIN(RADIANS(:lat)) * SIN(RADIANS(w.latitude))
            )
        ) AS distance
        FROM warehouses w
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<WarehouseEntity> findAllOrderByDistance(double lat, double lng);
}
