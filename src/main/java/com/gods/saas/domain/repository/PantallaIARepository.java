package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.Pantalla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PantallaIARepository extends JpaRepository<Pantalla, String> {
    Optional<Pantalla> findBySesionActualId(String sesionActualId);

}

