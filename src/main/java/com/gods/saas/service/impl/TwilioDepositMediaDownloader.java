package com.gods.saas.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only authenticated media belonging to this Twilio account and inbound message may be downloaded. */
@Service
public class TwilioDepositMediaDownloader {
    static final int MAX_BYTES=5*1024*1024;
    @Value("${whatsapp.twilio.account-sid:}") private String accountSid;
    @Value("${whatsapp.twilio.auth-token:}") private String token;

    public boolean validSource(String url,String messageSid) {
        try {
            URI uri=URI.create(url);
            String prefix="/2010-04-01/Accounts/"+accountSid+"/Messages/"+messageSid+"/Media/";
            return "https".equals(uri.getScheme()) && "api.twilio.com".equals(uri.getHost()) && uri.getUserInfo()==null
                    && uri.getPort()==-1 && uri.getQuery()==null && uri.getFragment()==null
                    && uri.getPath().matches(java.util.regex.Pattern.quote(prefix)+"ME[0-9a-fA-F]{32}");
        } catch (RuntimeException invalid) {return false;}
    }
    public MultipartFile download(String url,String sid,String contentType) throws IOException {
        if (!validSource(url,sid) || accountSid==null || token==null || token.isBlank()) throw new IOException("INVALID_MEDIA_SOURCE");
        URI current=URI.create(url);
        for (int hop=0;hop<4;hop++) {
            HttpURLConnection connection=(HttpURLConnection)current.toURL().openConnection();
            connection.setConnectTimeout(2000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
            if (hop==0) connection.setRequestProperty("Authorization","Basic "+Base64.getEncoder().encodeToString((accountSid+":"+token).getBytes(StandardCharsets.UTF_8)));
            try {
                int status=connection.getResponseCode();
                if (status>=300 && status<400) {
                    String location=connection.getHeaderField("Location");
                    URI next=location==null?null:current.resolve(location);
                    if (next==null || !"https".equals(next.getScheme()) || next.getUserInfo()!=null || next.getPort()!=-1 || !trustedHost(next.getHost())) throw new IOException("INVALID_MEDIA_REDIRECT");
                    current=next;continue;
                }
                if (status!=200) throw new IOException("MEDIA_HTTP_"+status);
                if (connection.getContentLengthLong()>MAX_BYTES) throw new IOException("MEDIA_TOO_LARGE");
                try (InputStream input=connection.getInputStream()) {
                    byte[] bytes=input.readNBytes(MAX_BYTES+1);
                    if (bytes.length==0 || bytes.length>MAX_BYTES) throw new IOException("INVALID_MEDIA_SIZE");
                    String extension=switch(contentType) {case "image/png" -> "png";case "image/webp" -> "webp";default -> "jpg";};
                    return new Image(bytes,contentType,"comprobante."+extension);
                }
            } finally {connection.disconnect();}
        }
        throw new IOException("MEDIA_TOO_MANY_REDIRECTS");
    }
    private boolean trustedHost(String host) {
        return host!=null && (host.equals("api.twilio.com") || host.endsWith(".twilio.com") || host.endsWith(".twiliocdn.com")
                || host.endsWith(".amazonaws.com") || host.endsWith(".cloudfront.net"));
    }
    private record Image(byte[] bytes,String type,String filename) implements MultipartFile {
        public String getName(){return "file";} public String getOriginalFilename(){return filename;}
        public String getContentType(){return type;} public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes.clone();}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        public void transferTo(File destination)throws IOException{try(var output=new FileOutputStream(destination)){output.write(bytes);}}
    }
}
