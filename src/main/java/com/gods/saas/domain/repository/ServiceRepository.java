package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    List<ServiceEntity> findByTenant_Id(Long tenantId);

    List<ServiceEntity> findByTenant_IdAndActivoTrue(Long tenantId);

    List<ServiceEntity> findByTenant_IdOrderByNombreAsc(Long tenantId);

    List<ServiceEntity> findByTenant_IdAndActivoTrueOrderByNombreAsc(Long tenantId);

    Optional<ServiceEntity> findByIdAndTenant_Id(Long id, Long tenantId);

    List<ServiceEntity> findByTenant_IdAndDeletedAtIsNullOrderByNombreAsc(Long tenantId);
    List<ServiceEntity> findByTenant_IdAndActivoTrueAndDeletedAtIsNullOrderByNombreAsc(Long tenantId);
    Optional<ServiceEntity> findByIdAndTenant_IdAndDeletedAtIsNull(Long id, Long tenantId);
    boolean existsByTenant_IdAndNombreIgnoreCaseAndDeletedAtIsNull(Long tenantId, String nombre);
    boolean existsByTenant_IdAndNombreIgnoreCaseAndIdNotAndDeletedAtIsNull(Long tenantId, String nombre, Long id);

    @Query(value = "select count(*) from appointment where service_id = :serviceId", nativeQuery = true)
    long countAppointmentReferences(@Param("serviceId") Long serviceId);

    @Query(value = "select count(*) from sale_item where service_id = :serviceId", nativeQuery = true)
    long countSaleItemReferences(@Param("serviceId") Long serviceId);

    @Query(value = "select count(*) from sale_detail where service_id = :serviceId", nativeQuery = true)
    long countSaleDetailReferences(@Param("serviceId") Long serviceId);

    @Query(value = "select count(*) from local_consumption_order_item where service_id = :serviceId", nativeQuery = true)
    long countLocalConsumptionReferences(@Param("serviceId") Long serviceId);

    @Query(value = "select count(*) from promotion where upper(coalesce(redirect_type, '')) = 'SERVICE' and trim(coalesce(redirect_value, '')) = cast(:serviceId as text)", nativeQuery = true)
    long countPromotionReferences(@Param("serviceId") Long serviceId);

    @Modifying
    @Query(value = "update promotion set activo = false where upper(coalesce(redirect_type, '')) = 'SERVICE' and trim(coalesce(redirect_value, '')) = cast(:serviceId as text)", nativeQuery = true)
    void disablePromotionsForService(@Param("serviceId") Long serviceId);

    boolean existsByTenant_IdAndNombreIgnoreCase(Long tenantId, String nombre);

    boolean existsByTenant_IdAndNombreIgnoreCaseAndIdNot(Long tenantId, String nombre, Long id);



}