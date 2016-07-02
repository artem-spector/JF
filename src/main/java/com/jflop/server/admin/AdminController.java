package com.jflop.server.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * REST API for agents administration
 *
 * @author artem
 *         Date: 7/2/16
 */

@RestController
@RequestMapping(path = AdminController.AGENTS_PATH)
public class AdminController {

    public static final String AGENTS_PATH = "/agents";

    @RequestMapping(method = GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getAgentsStatus() {
        Map<String, Object> agents = new HashMap<>();
        return ResponseEntity.ok(agents);
    }
}
