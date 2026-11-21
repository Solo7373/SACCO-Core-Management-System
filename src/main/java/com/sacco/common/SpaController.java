package com.sacco.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes to the embedded single-page application so a
 * browser refresh or a direct link such as /loans still loads the React app.
 * API and static asset paths are intentionally not mapped here.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/members",
            "/members/*",
            "/accounts",
            "/savings",
            "/shares",
            "/loans",
            "/collections",
            "/collateral",
            "/reports",
            "/users",
            "/dashboard",
            "/inventory",
            "/compliance",
            "/backups",
            "/setup",
            "/settings",
            "/reminders"
    })
    public String index() {
        return "forward:/index.html";
    }
}
