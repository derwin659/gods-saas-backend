package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.utils.EstadoSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SesionIARepository extends JpaRepository<SesionIa, String> {

    long countByEstado(EstadoSesion estado);

    Optional<SesionIa> findFirstByEstadoOrderByCreadoEnAsc(EstadoSesion estado);
}

