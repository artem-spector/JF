package com.jflop.server.admin;

import com.jflop.server.take2.admin.AdminDAO;
import com.jflop.server.take2.admin.data.AccountData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Validates the authenticated user for admin functionality
 *
 * @author artem
 *         Date: 7/2/16
 */
@Component
public class AdminSecurityInterceptor extends HandlerInterceptorAdapter {

    public static final String AUTH_HEADER = "jf-auth";
    public static final String ACCOUNT_ID_ATTRIBUTE = "accountId";

    @Autowired
    private AdminDAO dao;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader(AUTH_HEADER);
        String accountId = header == null ? null : getAccountId(header);
        if (accountId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        request.setAttribute(ACCOUNT_ID_ATTRIBUTE, accountId);
        return true;
    }

    private String getAccountId(String accountName) {
        AccountData account = dao.findAccountByName(accountName);
        if (account == null) {
            account = dao.createAccount(accountName);
        }
        return account.accountId;
    }
}
