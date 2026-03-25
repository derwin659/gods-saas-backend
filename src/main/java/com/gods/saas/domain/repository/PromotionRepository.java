package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByTenant_IdOrderByOrdenVisualAscCreatedAtDesc(Long tenantId);

    Optional<Promotion> findByIdAndTenant_Id(Long id, Long tenantId);

    @Query(value = """
        select p.*
        from promotion p
        where p.tenant_id = :tenantId
          and p.activo = true
          and (p.fecha_inicio is null or p.fecha_inicio <= (now() at time zone 'UTC'))
          and (p.fecha_fin is null or p.fecha_fin >= (now() at time zone 'UTC'))
          and (
                p.solo_clientes_con_puntos = false
                or coalesce(:puntosDisponibles, 0) >= coalesce(p.puntos_minimos, 0)
          )
        order by p.destacado desc, p.orden_visual asc, p.creado_en desc
        """, nativeQuery = true)
    List<Promotion> findActiveClientPromotions(
            @Param("tenantId") Long tenantId,
            @Param("puntosDisponibles") Integer puntosDisponibles
    );



}
