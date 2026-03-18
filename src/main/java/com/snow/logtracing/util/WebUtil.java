/*
 *      Copyright (c) 2018-2028, DreamLu All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the dreamlu.net developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: DreamLu 卢春梦 (596392912@qq.com)
 */
package com.snow.logtracing.util;

import com.alibaba.fastjson2.JSON;
import com.snow.logtracing.constants.StringPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Predicate;


/**
 * Miscellaneous utilities for web applications.
 *
 * @author chen
 */
@Slf4j
public class WebUtil extends org.springframework.web.util.WebUtils {

    public static final String USER_AGENT_HEADER = "user-agent";
    public static final String LOCAL_HOST = "127.0.0.1";

    /**
     * 读取cookie
     *
     * @param name cookie name
     * @return cookie value
     */
    @Nullable
    public static String getCookieVal(String name) {
        HttpServletRequest request = WebUtil.getRequest();
        Assert.notNull(request, "request from RequestContextHolder is null");
        return getCookieVal(request, name);
    }

    /**
     * 读取cookie
     *
     * @param request HttpServletRequest
     * @param name    cookie name
     * @return cookie value
     */
    @Nullable
    public static String getCookieVal(HttpServletRequest request, String name) {
        Cookie cookie = getCookie(request, name);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * 清除 某个指定的cookie
     *
     * @param response HttpServletResponse
     * @param key      cookie key
     */
    public static void removeCookie(HttpServletResponse response, String key) {
        setCookie(response, key, null, 0);
    }

    /**
     * 设置cookie
     *
     * @param response        HttpServletResponse
     * @param name            cookie name
     * @param value           cookie value
     * @param maxAgeInSeconds maxage
     */
    public static void setCookie(HttpServletResponse response, String name, @Nullable String value, int maxAgeInSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath(StringPool.SLASH);
        cookie.setMaxAge(maxAgeInSeconds);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 获取 HttpServletRequest
     *
     * @return {HttpServletRequest}
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return (requestAttributes == null) ? null : ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
     * 返回json
     *
     * @param response HttpServletResponse
     * @param result   结果对象
     */
    public static void renderJson(HttpServletResponse response, Object result) {
        renderJson(response, result, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 返回json
     *
     * @param response    HttpServletResponse
     * @param result      结果对象
     * @param contentType contentType
     */
    public static void renderJson(HttpServletResponse response, Object result, String contentType) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType(contentType);
        try (PrintWriter out = response.getWriter()) {
            out.append(JSON.toJSONString(result));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 获取ip
     *
     * @return {String}
     */
    public static String getIP() {
        return getIP(WebUtil.getRequest());
    }

    /**
     * 获取 服务器 hostname
     *
     * @return hostname
     */
    public static String getHostName() {
        String hostname;
        try {
            InetAddress address = InetAddress.getLocalHost();
            // force a best effort reverse DNS lookup
            hostname = address.getHostName();
            if (StringUtils.isEmpty(hostname)) {
                hostname = address.toString();
            }
        } catch (UnknownHostException ignore) {
            hostname = LOCAL_HOST;
        }
        return hostname;
    }


    private static final String[] IP_HEADER_NAMES = new String[]{
            "x-forwarded-for",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private static final Predicate<String> IP_PREDICATE = (ip) -> StringUtil.isBlank(ip) || StringPool.UNKNOWN.equalsIgnoreCase(ip);

    /**
     * 获取ip
     *
     * @param request HttpServletRequest
     * @return {String}
     */
    @Nullable
    public static String getIP(@Nullable HttpServletRequest request) {
        if (request == null) {
            return StringPool.EMPTY;
        }
        String ip = null;
        for (String ipHeader : IP_HEADER_NAMES) {
            ip = request.getHeader(ipHeader);
            if (!IP_PREDICATE.test(ip)) {
                break;
            }
        }
        if (IP_PREDICATE.test(ip)) {
            ip = request.getRemoteAddr();
        }
        String result = StringUtil.isBlank(ip) ? null : StringUtil.splitTrim(ip, StringPool.COMMA)[0];
        // 处理IPv6本地回环地址，转换为IPv4格式
        if ("0:0:0:0:0:0:0:1".equals(result) || "::1".equals(result)) {
            result = LOCAL_HOST;
        }
        return result;
    }

    /**
     * 获取请求头的值
     *
     * @param name 请求头名称
     * @return 请求头
     */
    public static String getHeader(String name) {
        HttpServletRequest request = getRequest();
        return Objects.requireNonNull(request).getHeader(name);
    }

    /**
     * 获取请求头的值
     *
     * @param name 请求头名称
     * @return 请求头
     */
    public static Enumeration<String> getHeaders(String name) {
        HttpServletRequest request = getRequest();
        return Objects.requireNonNull(request).getHeaders(name);
    }

    /**
     * 获取所有的请求头
     *
     * @return 请求头集合
     */
    public static Enumeration<String> getHeaderNames() {
        HttpServletRequest request = getRequest();
        return Objects.requireNonNull(request).getHeaderNames();
    }

    /**
     * 获取请求参数
     *
     * @param name 请求参数名
     * @return 请求参数
     */
    public static String getParameter(String name) {
        HttpServletRequest request = getRequest();
        return Objects.requireNonNull(request).getParameter(name);
    }

    /**
     * 获取 request 请求体
     *
     * @param servletInputStream servletInputStream
     * @return body
     */
    public static String getRequestBody(ServletInputStream servletInputStream) {
        if (servletInputStream == null) {
            return StringPool.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        try {
            // 检查流是否已完成或不可读
            if (servletInputStream.isFinished()) {
                log.debug("ServletInputStream already finished, returning empty string");
                return StringPool.EMPTY;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(servletInputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read request body: {}", e.getMessage());
        }
        return sb.toString();
    }

    /**
     * 获取 request 请求内容
     *
     * @param request request
     * @return {String}
     */
    public static String getRequestContent(HttpServletRequest request) {
        try {
            StringBuilder result = new StringBuilder();

            // 1. 解析 Query 参数
            String queryString = request.getQueryString();
            if (StringUtil.isNotBlank(queryString)) {
                String query = new String(queryString.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                        .replaceAll("&amp;", "&").replaceAll("%22", "\"");
                result.append(query);
            }

            // 2. 解析 Body 参数
            String charEncoding = request.getCharacterEncoding();
            if (charEncoding == null) {
                charEncoding = StringPool.UTF_8;
            }
            byte[] buffer = getRequestBody(request.getInputStream()).getBytes();
            String bodyStr = new String(buffer, charEncoding).trim();

            // 如果 body 为空，尝试从 parameterNames 获取
            if (StringUtil.isBlank(bodyStr)) {
                StringBuilder sb = new StringBuilder();
                Enumeration<String> parameterNames = request.getParameterNames();
                while (parameterNames.hasMoreElements()) {
                    String key = parameterNames.nextElement();
                    String value = request.getParameter(key);
                    StringUtil.appendBuilder(sb, key, "=", value, "&");
                }
                bodyStr = StringUtil.removeSuffix(sb.toString(), "&");
            }

            // 3. 拼接结果
            if (StringUtil.isNotBlank(bodyStr)) {
                if (result.length() > 0) {
                    result.append(" | body: ");
                }
                result.append(bodyStr.replaceAll("&amp;", "&"));
            }

            return result.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return StringPool.EMPTY;
        }
    }

}

