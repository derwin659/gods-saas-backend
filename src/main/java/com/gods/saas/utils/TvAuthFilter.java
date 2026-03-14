package com.gods.saas.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


    @Component
    public class TvAuthFilter extends OncePerRequestFilter {

        private static final String TV_KEY = "TV_SECRET_ABC123";

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            String path = request.getRequestURI();

            // 📺 SOLO endpoints TV
            if (path.startsWith("/api/ia/tv/")) {

                String tvKey = request.getHeader("X-TV-KEY");

                if (tvKey == null || !TV_KEY.equals(tvKey)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("Invalid TV Key");
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }
    }



