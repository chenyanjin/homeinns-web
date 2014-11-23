package com.homeinns.web.controller;
import com.homeinns.web.common.ConstantKey;
import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.oltu.oauth2.as.request.OAuthAuthzRequest;
import org.apache.oltu.oauth2.as.response.OAuthASResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.error.OAuthError;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Authorization Code 授权码模式
 * Created by Irving on 2014/11/22.
 * Impl OAth2  http://oauth.net/2/
 */
@Controller
@RequestMapping("/oauth2")
public class AuthzController {

    private static Logger logger = LoggerFactory.getLogger(AuthzController.class);
    private Cache cache ;
    @Autowired
    public AuthzController(CacheManager cacheManager) {

        this.cache = cacheManager.getCache("oauth2-cache");
    }
     /* *
     * 构建OAuth2授权请求 [需要client_id与redirect_uri绝对地址]
     * @param request
     * @param response
     * @return
     * @throws OAuthSystemException
     * @throws IOException
     * @url  http://localhost:8080/oauth2/authorize?client_id={AppKey}&response_type=code&redirect_uri={YourSiteUrl}
     * @test http://localhost:8080/oauth2/authorize?client_id=fbed1d1b4b1449daa4bc49397cbe2350&response_type=code&redirect_uri=http://baidu.comx
     */
    @RequestMapping(value = "/authorize")
    public Object authorize(HttpServletRequest request, HttpServletResponse response,Model model)
            throws OAuthSystemException, IOException {
        PrintWriter out = null;
        try {
            out = response.getWriter();
            //构建OAuth请求
            OAuthAuthzRequest oauthRequest = new OAuthAuthzRequest(request);
            //验证redirecturl格式是否合法
            if (oauthRequest.getRedirectURI()==null||!oauthRequest.getRedirectURI().contains("http")) {
                return new ResponseEntity(
                        ConstantKey.INVALID_CALLBACK, HttpStatus.valueOf(oauthRequest.getResponseStatus()));
            }
            //验证appkey是否正确
            if (!validateOAuth2AppKey(oauthRequest)){
                OAuthResponse oauthResponse = OAuthASResponse
                                              .errorResponse(HttpServletResponse.SC_OK)
                                              .setError(OAuthError.TokenResponse.INVALID_CLIENT)
                                              .setErrorDescription(ConstantKey.INVALID_CLIENT)
                                              .buildJSONMessage();
                out.write(oauthResponse.getBody());
                out.flush();
                out.close();
                return;
            }
            //验证用户是否已登录
            if(request.getSession().getAttribute(ConstantKey.MEMBER_SESSION)==null) {
                //用户登录
                if(!login(request)) {
                    //查询客户端Appkey应用的信息
                    String clientName= "Just Test App";//oauthClientService.findByClientId(oauthRequest.getClientId());
                    model.addAttribute("client",clientName);
                    //登录失败跳转到登陆页面
                    //response.(request.getRequestURI());
                    return;
                }
            }
           //生成授权码
           String authorizationCode = new OAuthIssuerImpl(new MD5Generator()).authorizationCode();
           //把授权码存入缓存
           cache.put(authorizationCode,request.getSession(true).getId());
           //构建oauth2授权返回信息
           OAuthResponse oauthResponse = OAuthASResponse
                                       .authorizationResponse(request, HttpServletResponse.SC_FOUND)
                                        //UUIDValueGenerator OR MD5Generator
                                       .setCode(authorizationCode)
                                       .location(oauthRequest.getParam(OAuth.OAUTH_REDIRECT_URI))
                                       .buildQueryMessage();
            response.sendRedirect(oauthResponse.getLocationUri());
        } catch(OAuthProblemException ex) {
            //处理异常
            /*
            final OAuthResponse oauthResponse = OAuthASResponse
                                                .errorResponse(HttpServletResponse.SC_FOUND)
                                                .error(ex)
                                                .location(ex.getRedirectUri())
                                                .buildQueryMessage();
            response.sendRedirect(oauthResponse.getLocationUri());
            */
            final OAuthResponse oauthResponse = OAuthResponse
                    .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
                    .error(ex)
                    .buildJSONMessage();
            response.setStatus(oauthResponse.getResponseStatus());
            out.print(oauthResponse.getBody());
            out.flush();
            out.close();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        finally
        {
            if (null != out){ out.close();}
        }
    }


    /**
     * 用户登录
     * @param request
     * @return
     */
    private boolean login(HttpServletRequest request) {
        if("get".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String userName = request.getParameter("username");
        String pwd = request.getParameter("pwd");
        if(StringUtils.isEmpty(userName) || StringUtils.isEmpty(pwd)) {
            return false;
        }
        try {
            //登录成功
            request.getSession().setAttribute(ConstantKey.MEMBER_SESSION,"Irving");
            return true;
        } catch (Exception ex) {
            request.setAttribute("error", "login error:" + ex.getClass().getName());
            return false;
        }
    }


    /**
     * 验证ClientID 是否正确
     * @param oauthRequest
     * @return
     */
    public boolean validateOAuth2AppKey(OAuthAuthzRequest oauthRequest) {
        //客户端Appkey
        ArrayList arrayKeys = new  ArrayList();
        arrayKeys.add("fbed1d1b4b1449daa4bc49397cbe2350");
        arrayKeys.add("a85b033590714fafb20db1d11aed5497");
        arrayKeys.add("d23e06a97e2d4887b504d2c6fdf42c0b");
        return arrayKeys.contains(oauthRequest.getClientId());
    }
}