package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    List<ServiceEntity> findByTenant_IdAndActivoTrue(Long tenantId);


    List<ServiceEntity> findByTenant_IdAndActivoTrueOrderByNombreAsc(Long tenantId);



    List<ServiceEntity> findByTenant_Id(Long tenantId);

    Optional<ServiceEntity> findByIdAndTenant_Id(Long id, Long tenantId);
}
