package com.gods.saas.service;
import com.gods.saas.service.impl.CloudinaryStorageService;
import com.gods.saas.service.impl.ShowcaseUploadRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcademyVideoValidationTest {
 private CloudinaryStorageService storage() { return new CloudinaryStorageService("test","test","test",mock(ShowcaseUploadRateLimitService.class)); }
 @Test void rejectsEmptyOrDisguisedMp4WithoutUploading() {
   assertThrows(IllegalArgumentException.class, () -> storage().uploadAcademyVideo("lesson",new MockMultipartFile("video","empty.mp4","video/mp4",new byte[0])));
   assertThrows(IllegalArgumentException.class, () -> storage().uploadAcademyVideo("lesson",new MockMultipartFile("video","fake.mp4","video/mp4","not a video".getBytes())));
 }
 @Test void rejectsVideosAboveExistingLimit() {
   var file=mock(MultipartFile.class);when(file.isEmpty()).thenReturn(false);when(file.getSize()).thenReturn(36L*1024*1024);
   assertThrows(IllegalArgumentException.class, () -> storage().uploadAcademyVideo("lesson",file));
 }
 @Test void onlyMp4IsAcceptedForCrossPlatformPlayback() {
   assertThrows(IllegalArgumentException.class, () -> storage().uploadAcademyVideo("lesson",new MockMultipartFile("video","demo.webm","video/webm",new byte[]{0x1A,0x45,(byte)0xDF,(byte)0xA3})));
 }
}