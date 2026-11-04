package com.gods.saas.web.controller;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.service.impl.GoogleReviewLinks;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/public/google-reviews") @RequiredArgsConstructor
public class PublicGoogleReviewController {
    private final BranchRepository branches;
    @GetMapping("/{branchId}")
    public ResponseEntity<Map<String,String>> invitation(@PathVariable Long branchId) {
        var branch=branches.findByIdAndActivoTrue(branchId).filter(b -> b.getTenant()!=null && !Boolean.FALSE.equals(b.getTenant().getActive()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitación no disponible"));
        String url=GoogleReviewLinks.forBranch(branch);
        if(url==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Esta sede aún no tiene enlace de reseñas de Google");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("businessName",branch.getTenant().getNombre(),"branchName",branch.getNombre(),"googleReviewUrl",url));
    }
}
