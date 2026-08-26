package com.gods.saas.domain.repository;
import com.gods.saas.domain.model.FeaturedCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FeaturedCustomerRepository extends JpaRepository<FeaturedCustomer,Long>{
 List<FeaturedCustomer> findAllByOrderBySortOrderAscIdAsc();
 List<FeaturedCustomer> findByVisibleTrueOrderBySortOrderAscIdAsc();
}