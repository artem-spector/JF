package com.jflop.server.rest;


import com.jflop.server.rest.admin.AdminController;
import com.jflop.server.rest.admin.AdminSecurityInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configure custom interceptors, resource handlers, etc.
 *
 * @author artem
 *         Date: 7/2/16
 */
@Configuration
@EnableScheduling
public class CustomConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private AdminSecurityInterceptor adminSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration registration = registry.addInterceptor(adminSecurityInterceptor);
        registration.addPathPatterns(AdminController.AGENTS_PATH, AdminController.AGENTS_PATH + "/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/console/**").addResourceLocations("classpath:/web/");
    }
}
