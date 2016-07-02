package com.jflop.server.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.*;

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
    private HttpServletRequest request;

    @Autowired
    private AdminDAO dao;

    @RequestMapping(method = GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getAgents() {
        return ResponseEntity.ok(dao.getAgents(accountId()));
    }

    @RequestMapping(method = POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity createAgent(@RequestParam("name") String name) throws URISyntaxException {
        JFAgent agent = dao.createAgent(accountId(), name);
        return ResponseEntity.created(new URI(request.getRequestURI() + "/" + agent.id)).build();
    }

    @RequestMapping(method = PUT, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity updateAgent(@PathVariable("id") String agentId, @RequestParam("name") String name)  {
        try {
            dao.updateAgent(accountId(), agentId, name);
            return ResponseEntity.ok().build();
        } catch (NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequestMapping(method = DELETE, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity deleteAgent(@PathVariable("id") String agentId)  {
        try {
            dao.deleteAgent(accountId(), agentId);
            return ResponseEntity.ok().build();
        } catch (NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String accountId() {
        return (String) request.getAttribute(AdminSecurityInterceptor.ACCOUNT_ID_ATTRIBUTE);
    }
}
