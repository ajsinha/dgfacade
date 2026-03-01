/*
 * Copyright © 2025-2030, All Rights Reserved
 * Proprietary and confidential.
 */
package com.dgfacade.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects project-wide metadata (author, copyright, GitHub, version)
 * into every Thymeleaf model so templates never hard-code these values.
 *
 * <p>All values are read from {@code application.properties} under the
 * {@code dgfacade.*} namespace.</p>
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Value("${dgfacade.author.name:}")
    private String authorName;

    @Value("${dgfacade.author.email:}")
    private String authorEmail;

    @Value("${dgfacade.author.github:}")
    private String authorGithub;

    @Value("${dgfacade.copyright:}")
    private String copyrightText;

    @Value("${dgfacade.github:}")
    private String githubUrl;

    @Value("${dgfacade.version:}")
    private String appVersion;

    @ModelAttribute("authorName")
    public String authorName() { return authorName; }

    @ModelAttribute("authorEmail")
    public String authorEmail() { return authorEmail; }

    @ModelAttribute("authorGithub")
    public String authorGithub() { return authorGithub; }

    @ModelAttribute("copyrightText")
    public String copyrightText() { return copyrightText; }

    @ModelAttribute("githubUrl")
    public String githubUrl() { return githubUrl; }

    @ModelAttribute("appVersion")
    public String appVersion() { return appVersion; }
}
