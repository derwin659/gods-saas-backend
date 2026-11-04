package com.gods.saas.service.impl;

import com.gods.saas.exception.BusinessException;
import java.net.URI;
import java.util.Locale;

/** Validates official Google destinations without fetching or following redirects. */
public final class GoogleReviewLinks {
    private GoogleReviewLinks() {}
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value=raw.trim();
        if (value.length()>1000 || value.chars().anyMatch(Character::isISOControl)) throw invalid();
        try {
            URI uri=URI.create(value);
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            String path=uri.getPath()==null?"":uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) throw invalid();
            boolean allowed=(host.equals("g.page") && path.matches("/(?:r/)?[^/]+/review/?"))
                || (host.equals("maps.app.goo.gl") && path.matches("/[A-Za-z0-9_-]+/?"))
                || ((host.equals("google.com") || host.equals("www.google.com") || host.equals("maps.google.com")) && (path.equals("/maps") || path.startsWith("/maps/")))
                || (host.equals("search.google.com") && path.equals("/local/writereview") && uri.getRawQuery()!=null && uri.getRawQuery().matches("(?:.*&)?placeid=[^&]+(?:&.*)?"));
            if (!allowed) throw invalid();
            return value;
        } catch (IllegalArgumentException invalidUri) { throw invalid(); }
    }
    public static String forBranch(com.gods.saas.domain.model.Branch branch) {
        if(branch==null || !Boolean.TRUE.equals(branch.getActivo()) || branch.getTenant()==null || Boolean.FALSE.equals(branch.getTenant().getActive())) return null;
        return safe(branch.getGoogleReviewUrl());
    }
    public static String safe(String raw) {try{return normalize(raw);}catch(BusinessException invalid){return null;}}
    private static BusinessException invalid() {return new BusinessException("Pega un enlace HTTPS de reseñas de Google: g.page/.../review, search.google.com/local/writereview o Google Maps.");}
}
