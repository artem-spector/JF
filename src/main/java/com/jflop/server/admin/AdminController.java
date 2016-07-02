package com.jflop.server.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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

    @Autowired
    private AdminDAO dao;

    @RequestMapping(method = GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getAgentsStatus(@ModelAttribute(AdminSecurityInterceptor.ACCOUNT_ID_ATTRIBUTE) String accountId) {
        return ResponseEntity.ok(dao.getAgents(accountId));
    }
}
