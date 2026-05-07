package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.SaleItem;
import com.gods.saas.domain.repository.projection.CustomerHistorySaleItemProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @Query(value = """
    select
        si.sale_item_id as id,
        coalesce(
            nullif(trim(si.nombre_item), ''),
            se.nombre,
            p.nombre,
            'Item'
        ) as nombre,
        case
            when si.product_id is not null then 'PRODUCT'
            else 'SERVICE'
        end as tipo,
        coalesce(si.cantidad, 1) as cantidad,
        coalesce(si.precio_unitario, 0) as precioUnitario,
        coalesce(si.subtotal, 0) as subtotal,
        coalesce(
            nullif(trim(concat_ws(' ', u.nombre, u.apellido)), ''),
            'Sin asignar'
        ) as barbero,
        coalesce(u.photo_url, '') as barberPhotoUrl
    from sale_item si
    join sale s
      on s.sale_id = si.sale_id
    left join service se
      on se.service_id = si.service_id
    left join product p
      on p.product_id = si.product_id
    left join app_user u
      on u.user_id = coalesce(si.barber_user_id, s.user_id)
    where s.tenant_id = :tenantId
      and s.customer_id = :customerId
      and s.sale_id = :saleId
    order by si.sale_item_id asc
    """, nativeQuery = true)
    List<CustomerHistorySaleItemProjection> findCustomerHistoryItemsBySale(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId,
            @Param("saleId") Long saleId
    );
}
