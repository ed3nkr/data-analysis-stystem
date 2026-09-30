package com.reviewsales.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reviewsales.auth.CurrentOwnerArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 프로토타입 웹(static/index.html): OAuth 성공 후 돌아오는 /login/success 도 같은 페이지로 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login/success").setViewName("forward:/index.html");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentOwnerArgumentResolver());
    }
}
