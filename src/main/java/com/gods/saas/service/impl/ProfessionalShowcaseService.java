package com.gods.saas.service.impl;
import com.gods.saas.domain.dto.response.ShowcaseResponse;
import com.gods.saas.domain.enums.ShowcaseStatus;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProfessionalShowcaseService {
 private final ProfessionalShowcaseRepository repository; private final TenantRepository tenantRepository; private final BranchRepository branchRepository; private final AppUserRepository appUserRepository; private final ServiceRepository serviceRepository; private final UserTenantRoleRepository roleRepository; private final CloudinaryStorageService storage;
 @Transactional(readOnly=true) public List<ShowcaseResponse> mine(Long tenantId,Long userId){return repository.findByTenant_IdAndProfessional_IdOrderByCreatedAtDesc(tenantId,userId).stream().map(ShowcaseResponse::from).toList();}
 @Transactional public ShowcaseResponse create(Long tenantId,Long userId,Long branchId,Long serviceId,String title,String description,String mediaType,Integer durationSeconds,boolean consent,MultipartFile file){
  if(!consent)throw new IllegalArgumentException("Debes confirmar la autorizacion de uso de imagen."); String clean=title==null?"":title.trim();if(clean.isEmpty()||clean.length()>120)throw new IllegalArgumentException("Escribe un titulo de hasta 120 caracteres.");
  Tenant tenant=tenantRepository.findById(tenantId).orElseThrow(()->new IllegalArgumentException("Negocio no encontrado.")); Branch branch=branchRepository.findByIdAndTenant_Id(branchId,tenantId).orElseThrow(()->new IllegalArgumentException("Sede no encontrada.")); AppUser user=appUserRepository.findByIdAndTenant_Id(userId,tenantId).orElseThrow(()->new IllegalArgumentException("Profesional no encontrado."));
  if(!roleRepository.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(userId,tenantId,branchId,RoleType.BARBER))throw new IllegalStateException("No tienes perfil profesional activo en esta sede.");
  ServiceEntity service=serviceId==null?null:serviceRepository.findByIdAndTenant_IdAndDeletedAtIsNull(serviceId,tenantId).orElseThrow(()->new IllegalArgumentException("Servicio no encontrado."));
  String type=mediaType==null?"IMAGE":mediaType.trim().toUpperCase();
  if(!List.of("IMAGE","VIDEO").contains(type))throw new IllegalArgumentException("Tipo de medio invalido.");
  String plan=tenant.getPlan()==null?"BASIC":tenant.getPlan().trim().toUpperCase();
  int totalLimit=switch(plan){case "STARTER"->50;case "PRO"->200;case "PREMIUM","ENTERPRISE"->500;default->10;};
  int videoLimit=switch(plan){case "STARTER"->10;case "PRO"->50;case "PREMIUM","ENTERPRISE"->150;default->0;};
  long current=repository.countByTenant_IdAndProfessional_IdAndStatusNot(tenantId,userId,ShowcaseStatus.ARCHIVED);
  if(current>=totalLimit)throw new IllegalStateException("Alcanzaste el limite de "+totalLimit+" trabajos activos de tu plan.");
  if("VIDEO".equals(type)){long videos=repository.countByTenant_IdAndProfessional_IdAndMediaTypeAndStatusNot(tenantId,userId,"VIDEO",ShowcaseStatus.ARCHIVED);if(videos>=videoLimit)throw new IllegalStateException(videoLimit==0?"Tu plan no incluye videos en la vitrina.":"Alcanzaste el limite de videos de tu plan.");}
  if("VIDEO".equals(type)&&(durationSeconds==null||durationSeconds<1||durationSeconds>90))throw new IllegalArgumentException("El video debe durar entre 1 y 90 segundos.");
  var upload="VIDEO".equals(type)?storage.uploadShowcaseVideo(tenantId,userId,file):storage.uploadShowcaseImage(tenantId,userId,file);
  String thumbnail="VIDEO".equals(type)?storage.videoThumbnailUrl(upload.getSecureUrl()):upload.getSecureUrl();
  ProfessionalShowcase item=ProfessionalShowcase.builder().tenant(tenant).branch(branch).professional(user).service(service).title(clean).description(description==null?null:description.trim()).mediaType(type).imageUrl(upload.getSecureUrl()).thumbnailUrl(thumbnail).imagePublicId(upload.getPublicId()).durationSeconds("VIDEO".equals(type)?durationSeconds:null).status(ShowcaseStatus.PENDING_APPROVAL).clientImageConsent(true).build(); return ShowcaseResponse.from(repository.save(item));
 }
 @Transactional public ShowcaseResponse restoreMine(Long tenantId,Long userId,Long id){ProfessionalShowcase x=owned(tenantId,userId,id);if(x.getStatus()!=ShowcaseStatus.ARCHIVED)throw new IllegalStateException("Solo puedes restaurar trabajos archivados.");x.setStatus(ShowcaseStatus.PENDING_APPROVAL);x.setArchivedAt(null);x.setPublishedAt(null);x.setModeratedAt(null);x.setModeratedBy(null);x.setRejectionReason(null);return ShowcaseResponse.from(repository.save(x));}
 @Transactional public void deleteMine(Long tenantId,Long userId,Long id){ProfessionalShowcase x=owned(tenantId,userId,id);if(x.getStatus()!=ShowcaseStatus.ARCHIVED)throw new IllegalStateException("Archiva el trabajo antes de eliminarlo.");storage.deleteShowcaseMedia(x.getImagePublicId(),x.getMediaType());repository.delete(x);}
 private ProfessionalShowcase owned(Long tenantId,Long userId,Long id){ProfessionalShowcase x=repository.findByIdAndTenant_Id(id,tenantId).orElseThrow(()->new IllegalArgumentException("Trabajo no encontrado."));if(!x.getProfessional().getId().equals(userId))throw new IllegalStateException("No puedes modificar este trabajo.");return x;} @Transactional public ShowcaseResponse archiveMine(Long tenantId,Long userId,Long id){ProfessionalShowcase x=repository.findByIdAndTenant_Id(id,tenantId).orElseThrow(()->new IllegalArgumentException("Trabajo no encontrado."));if(!x.getProfessional().getId().equals(userId))throw new IllegalStateException("No puedes archivar este trabajo.");x.setStatus(ShowcaseStatus.ARCHIVED);x.setArchivedAt(LocalDateTime.now());return ShowcaseResponse.from(repository.save(x));}
 @Transactional(readOnly=true) public List<ShowcaseResponse> ownerList(Long tenantId,String status){List<ProfessionalShowcase> data=status==null||status.isBlank()?repository.findByTenant_IdOrderByCreatedAtDesc(tenantId):repository.findByTenant_IdAndStatusOrderByCreatedAtDesc(tenantId,ShowcaseStatus.valueOf(status.trim().toUpperCase()));return data.stream().map(ShowcaseResponse::from).toList();}
 @Transactional public ShowcaseResponse moderate(Long tenantId,Long actorId,Long id,String status,String reason){ShowcaseStatus target=ShowcaseStatus.valueOf(status.trim().toUpperCase());if(target!=ShowcaseStatus.PUBLISHED&&target!=ShowcaseStatus.REJECTED)throw new IllegalArgumentException("Estado de moderacion invalido.");ProfessionalShowcase x=repository.findByIdAndTenant_Id(id,tenantId).orElseThrow(()->new IllegalArgumentException("Trabajo no encontrado."));AppUser actor=appUserRepository.findByIdAndTenant_Id(actorId,tenantId).orElseThrow();x.setStatus(target);x.setModeratedBy(actor);x.setModeratedAt(LocalDateTime.now());x.setRejectionReason(target==ShowcaseStatus.REJECTED?(reason==null?"No aprobado":reason.trim()):null);x.setPublishedAt(target==ShowcaseStatus.PUBLISHED?LocalDateTime.now():null);return ShowcaseResponse.from(repository.save(x));}
 @Transactional(readOnly=true) public List<ShowcaseResponse> published(Long tenantId,Long branchId,Long professionalId){List<ProfessionalShowcase> data=professionalId==null?repository.findByTenant_IdAndBranch_IdAndStatusOrderByPublishedAtDesc(tenantId,branchId,ShowcaseStatus.PUBLISHED):repository.findByTenant_IdAndBranch_IdAndProfessional_IdAndStatusOrderByPublishedAtDesc(tenantId,branchId,professionalId,ShowcaseStatus.PUBLISHED);return data.stream().limit(60).map(ShowcaseResponse::from).toList();}
}