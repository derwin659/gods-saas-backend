package com.gods.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.AcademyLesson;
import com.gods.saas.domain.repository.AcademyLessonRepository;
import com.gods.saas.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademyServiceTest {
 AcademyLessonRepository repository;
 CloudinaryStorageService storage;
 AdminPermissionService permissions;
 AcademyService service;
 final Map<String,AcademyLesson> rows = new HashMap<>();
 @BeforeEach void setup() {
   repository=mock(AcademyLessonRepository.class);storage=mock(CloudinaryStorageService.class);permissions=mock(AdminPermissionService.class);
   service=new AcademyService(repository,new ObjectMapper(),storage,permissions);
   when(repository.findAll()).thenAnswer(call -> new ArrayList<>(rows.values()));
   when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
   when(repository.saveAndFlush(any())).thenAnswer(call -> {
     AcademyLesson row=call.getArgument(0);row.setVersion(row.getVersion()==null?0:row.getVersion()+1);rows.put(row.getId(),row);return row;
   });
 }
 AcademyService.Draft draft(String title,long version,List<String> roles,String permission) {
   return new AcademyService.Draft(title,"Caja",roles,permission,3,"Aprende esta función",List.of("Revisa los datos"),0,version);
 }
 @Test void draftEditsOnlyBecomeVisibleAfterPublishing() {
   var first=service.create(draft("Original",0,List.of("OWNER"),null));
   assertTrue(service.catalog("OWNER","web").lessons().isEmpty());
   var published=service.publish(first.id(),first.version());
   var edited=service.update(first.id(),draft("Corregido",published.version(),List.of("OWNER"),null));
   assertEquals("Original",service.catalog("OWNER","web").lessons().getFirst().title());
   assertTrue(edited.hasChanges());
   var live=service.publish(edited.id(),edited.version());
   assertEquals("Corregido",service.catalog("OWNER","web").lessons().getFirst().title());
   service.unpublish(live.id(),live.version());
   assertTrue(service.catalog("OWNER","web").lessons().isEmpty());
 }
 @Test void serverFiltersRolesAndAdminPermissions() {
   var lesson=service.create(draft("Caja",0,List.of("OWNER","ADMIN","CASHIER"),"CASH_ACCESS"));
   service.publish(lesson.id(),lesson.version());
   assertTrue(service.catalog("ADMIN","web").lessons().isEmpty());
   when(permissions.hasCurrentUserPermission("CASH_ACCESS")).thenReturn(true);
   assertEquals(1,service.catalog("ADMIN","web").lessons().size());
   assertTrue(service.catalog("CLIENT","mobile").lessons().isEmpty());
   assertEquals(1,service.catalog("CASHIER","mobile").lessons().size());
   for(String role:List.of("CLIENT","BARBER","CASHIER")) assertThrows(ResponseStatusException.class,() -> service.catalog(role,"web"));
 }
 @Test void replacingVideoCannotChangePublishedVideoBeforePublish() {
   var lesson=service.create(draft("Video",0,List.of("OWNER"),null));
   var file=new MockMultipartFile("video","demo.mp4","video/mp4",new byte[]{1});
   when(storage.uploadAcademyVideo(eq(lesson.id()),any())).thenReturn(new CloudinaryStorageService.UploadResult("https://res.cloudinary.com/demo/video/upload/first.mp4","academy/first"));
   var uploaded=service.upload(lesson.id(),lesson.version(),file);
   var live=service.publish(uploaded.id(),uploaded.version());
   when(storage.uploadAcademyVideo(eq(lesson.id()),any())).thenReturn(new CloudinaryStorageService.UploadResult("https://res.cloudinary.com/demo/video/upload/next.mp4","academy/next"));
   var replacement=service.upload(live.id(),live.version(),file);
   assertTrue(replacement.draft().videoUrl().endsWith("next.mp4"));
   assertTrue(service.catalog("OWNER","web").lessons().getFirst().videoUrl().endsWith("first.mp4"));
 }
 @Test void staleEditsFailBeforeUploading() {
   var lesson=service.create(draft("Video",0,List.of("OWNER"),null));
   service.publish(lesson.id(),lesson.version());
   assertThrows(ResponseStatusException.class, () -> service.upload(lesson.id(),lesson.version(),null));
   verifyNoInteractions(storage);
 }
 @Test void rejectsUnknownRolesAndPermissions() {
   assertThrows(ResponseStatusException.class, () -> service.create(draft("Prueba",0,List.of("SUPER_ADMIN"),null)));
   assertThrows(ResponseStatusException.class, () -> service.create(draft("Prueba",0,List.of("ADMIN"),"MADE_UP_PERMISSION")));
 }
}