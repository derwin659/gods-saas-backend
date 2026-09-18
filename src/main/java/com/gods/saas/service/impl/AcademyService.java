package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.enums.AdminPermissionKey;
import com.gods.saas.domain.model.AcademyLesson;
import com.gods.saas.domain.repository.AcademyLessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class AcademyService {
 public static final Set<String> ROLES = Set.of("OWNER","ADMIN","CLIENT","BARBER","CASHIER");
 private final AcademyLessonRepository repository;
 private final ObjectMapper mapper;
 private final CloudinaryStorageService storage;
 private final AdminPermissionService permissions;
 public record Content(String id,String title,String category,List<String> roles,String permission,
     int minutes,String summary,List<String> steps,String videoUrl,String videoPublicId,String captionUrl,int sortOrder) {}
 public record Draft(String title,String category,List<String> roles,String permission,int minutes,
     String summary,List<String> steps,int sortOrder,long version) {}
 public record AdminLesson(String id,Content draft,boolean published,boolean hasChanges,long version) {}
 public record Revision(long version) {}
 public record Catalog(int version,Map<String,String> roles,List<Content> lessons) {}

 public List<AdminLesson> listAdmin() {
   return repository.findAll().stream().map(this::adminView)
     .sorted(Comparator.comparingInt(item -> item.draft().sortOrder())).toList();
 }
 public Catalog catalog(String role,String platform) {
   if (!Set.of("web","mobile").contains(platform)) throw bad("Plataforma no válida");
   if (!ROLES.contains(role) || (platform.equals("web") && !Set.of("OWNER","ADMIN").contains(role)))
     throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Academy no está disponible para este rol en la web");
   List<Content> lessons = repository.findAll().stream().filter(row -> row.getPublishedJson()!=null)
     .map(row -> decode(row.getPublishedJson()))
     .filter(item -> item.roles().contains(role))
     .filter(item -> !role.equals("ADMIN") || item.permission()==null || permissions.hasCurrentUserPermission(item.permission()))
     .sorted(Comparator.comparingInt(Content::sortOrder).thenComparing(Content::id)).toList();
   return new Catalog(1,Map.of("OWNER","Dueño","ADMIN","Administrador","CLIENT","Cliente","BARBER","Profesional","CASHIER","Trabajador de caja"),lessons);
 }
 @Transactional public AdminLesson create(Draft input) {
   validate(input);
   var row = new AcademyLesson();row.setId("lesson-"+UUID.randomUUID());
   row.setDraftJson(encode(content(row.getId(),input,null,null)));
   return save(row);
 }
 @Transactional public AdminLesson update(String id,Draft input) {
   validate(input); var row = require(id,input.version()); var previous=decode(row.getDraftJson());
   row.setDraftJson(encode(content(id,input,previous.videoUrl(),previous.videoPublicId())));
   return save(row);
 }
 @Transactional public AdminLesson upload(String id,long version,MultipartFile file) {
   var row=require(id,version); var previous=decode(row.getDraftJson());
   CloudinaryStorageService.UploadResult upload;
   try { upload=storage.uploadAcademyVideo(id,file); }
   catch (IllegalArgumentException error) { throw bad(error.getMessage()); }
   if(TransactionSynchronizationManager.isSynchronizationActive()) {
     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
       @Override public void afterCompletion(int status) {
         if(status==STATUS_ROLLED_BACK) {
           try { storage.deleteShowcaseMedia(upload.getPublicId(),"VIDEO"); }
           catch(RuntimeException error) { log.warn("Academy upload needs cleanup: {}",upload.getPublicId()); }
         }
       }
     });
   }
   row.setDraftJson(encode(new Content(id,previous.title(),previous.category(),previous.roles(),previous.permission(),previous.minutes(),previous.summary(),previous.steps(),upload.getSecureUrl(),upload.getPublicId(),null,previous.sortOrder())));
   // Keep prior published assets so draft uploads cannot break the live lesson.
   return save(row);
 }
 @Transactional public AdminLesson publish(String id,long version) {
   var row=require(id,version);row.setPublishedJson(row.getDraftJson());return save(row);
 }
 @Transactional public AdminLesson unpublish(String id,long version) {
   var row=require(id,version);row.setPublishedJson(null);return save(row);
 }
 private Content content(String id,Draft input,String url,String publicId) {
   return new Content(id,input.title().trim(),input.category().trim(),List.copyOf(new LinkedHashSet<>(input.roles())),
     input.permission()==null || input.permission().isBlank()?null:input.permission(),input.minutes(),input.summary().trim(),
     input.steps().stream().map(String::trim).toList(),url,publicId,null,input.sortOrder());
 }
 private void validate(Draft input) {
   if(input==null) throw bad("Completa la lección");
   text(input.title(),160,"título");text(input.category(),80,"funcionalidad");text(input.summary(),600,"resumen");
   if(input.roles()==null || input.roles().isEmpty() || input.roles().stream().anyMatch(role -> role==null || !ROLES.contains(role))) throw bad("Selecciona roles válidos");
   if(input.minutes()<1 || input.minutes()>120) throw bad("Duración: entre 1 y 120 minutos");
   if(input.steps()==null || input.steps().isEmpty() || input.steps().size()>30) throw bad("Agrega entre 1 y 30 pasos");
   for(String step:input.steps()) text(step,2000,"paso");
   if(input.permission()!=null && !input.permission().isBlank()) {
     try { AdminPermissionKey.valueOf(input.permission()); } catch(IllegalArgumentException error) { throw bad("Permiso desconocido"); }
   }
 }
 private void text(String value,int max,String field) { if(value==null || value.isBlank() || value.length()>max) throw bad("Revisa el "+field+" (máximo "+max+" caracteres)"); }
 private AcademyLesson require(String id,long version) {
   var row=repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Lección no encontrada"));
   if(!Objects.equals(row.getVersion(),version)) throw new ResponseStatusException(HttpStatus.CONFLICT,"La lección cambió en otra sesión. Recarga antes de guardar");
   return row;
 }
 private AdminLesson save(AcademyLesson row) { row.setUpdatedAt(Instant.now());return adminView(repository.saveAndFlush(row)); }
 private AdminLesson adminView(AcademyLesson row) { return new AdminLesson(row.getId(),decode(row.getDraftJson()),row.getPublishedJson()!=null,!Objects.equals(row.getDraftJson(),row.getPublishedJson()),row.getVersion()==null?0:row.getVersion()); }
 private Content decode(String json) { try { return mapper.readValue(json,Content.class); } catch(JsonProcessingException error) { throw new IllegalStateException("Catálogo inválido",error); } }
 private String encode(Content content) { try { return mapper.writeValueAsString(content); } catch(JsonProcessingException error) { throw new IllegalStateException("No se pudo guardar la lección",error); } }
 private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
