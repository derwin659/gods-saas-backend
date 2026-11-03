package com.gods.saas.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Getter @Setter @Component @ConfigurationProperties(prefix="whatsapp.onboarding")
public class WhatsappOnboardingProperties {
    private boolean enabled;
    private String appId="";
    private String appSecret="";
    private String configId="";
    private String solutionId="";
    private String graphVersion="v24.0";
    private String webUrl="";
    private String encryptionKey="";
    private boolean https(String value) {
        try { var uri=java.net.URI.create(value);return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null && (uri.getPath().isEmpty() || uri.getPath().equals("/")); }
        catch (RuntimeException bad) { return false; }
    }
    public boolean configured() {
        return enabled && appId.matches("[0-9]{5,30}") && !appSecret.isBlank() && configId.matches("[0-9]{5,30}")
            && solutionId.matches("[0-9]{5,30}") && graphVersion.matches("v[0-9]{1,2}\\.[0-9]") && https(webUrl);
    }
}
