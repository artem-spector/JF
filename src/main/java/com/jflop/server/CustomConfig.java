package com.jflop.server;


import com.jflop.server.admin.AdminController;
import com.jflop.server.admin.AdminSecurityInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configure custom interceptors
 *
 * @author artem
 *         Date: 7/2/16
 */
@Configuration
public class CustomConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private AdminSecurityInterceptor adminSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration registration = registry.addInterceptor(adminSecurityInterceptor);
        registration.addPathPatterns(AdminController.AGENTS_PATH, AdminController.AGENTS_PATH + "/*");
    }
}
