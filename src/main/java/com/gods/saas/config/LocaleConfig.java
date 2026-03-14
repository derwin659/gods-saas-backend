package com.gods.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

    @Configuration
    public class LocaleConfig {

        @Bean
        public LocaleResolver localeResolver() {
            AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();

            // Idioma por defecto del sistema (si el tenant no define otro)
            resolver.setDefaultLocale(new Locale("es"));

            return resolver;
        }
    }

