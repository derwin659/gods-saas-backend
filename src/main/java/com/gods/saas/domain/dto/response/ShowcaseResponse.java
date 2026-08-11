package com.gods.saas.domain.dto.response;
import com.gods.saas.domain.model.ProfessionalShowcase;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class ShowcaseResponse {
 private Long id,tenantId,branchId,professionalUserId,serviceId;
 private String branchName,professionalName,professionalPhotoUrl,serviceName,title,description,mediaType,imageUrl,thumbnailUrl,status,rejectionReason,originType,visibilityScope,category;
 private List<Long> branchIds; private Integer durationSeconds,sortOrder; private boolean clientImageConsent,featured; private LocalDateTime createdAt,publishedAt;
 public static ShowcaseResponse from(ProfessionalShowcase x){
  String n=x.getProfessional()==null?null:((x.getProfessional().getNombre()==null?"":x.getProfessional().getNombre())+" "+(x.getProfessional().getApellido()==null?"":x.getProfessional().getApellido())).trim();
  return ShowcaseResponse.builder().id(x.getId()).tenantId(x.getTenant().getId()).branchId(x.getBranch()==null?null:x.getBranch().getId()).branchName(x.getBranch()==null?null:x.getBranch().getNombre()).professionalUserId(x.getProfessional()==null?null:x.getProfessional().getId()).professionalName(n).professionalPhotoUrl(x.getProfessional()==null?null:x.getProfessional().getPhotoUrl()).serviceId(x.getService()==null?null:x.getService().getId()).serviceName(x.getService()==null?null:x.getService().getNombre()).title(x.getTitle()).description(x.getDescription()).mediaType(x.getMediaType()).imageUrl(x.getImageUrl()).thumbnailUrl(x.getThumbnailUrl()).durationSeconds(x.getDurationSeconds()).status(x.getStatus().name()).rejectionReason(x.getRejectionReason()).clientImageConsent(x.isClientImageConsent()).createdAt(x.getCreatedAt()).publishedAt(x.getPublishedAt()).originType(x.getOriginType()).visibilityScope(x.getVisibilityScope()).category(x.getCategory()).featured(x.isFeatured()).sortOrder(x.getSortOrder()).branchIds(x.getSelectedBranches()==null?List.of():x.getSelectedBranches().stream().map(b->b.getId()).sorted().toList()).build();
 }
}