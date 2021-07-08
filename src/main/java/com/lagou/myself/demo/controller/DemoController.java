package com.lagou.myself.demo.controller;



import com.lagou.myself.demo.service.IDemoService;
import com.lagou.myself.mvcframework.annotations.LagouAutowired;
import com.lagou.myself.mvcframework.annotations.LagouController;
import com.lagou.myself.mvcframework.annotations.LagouRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@LagouController
@LagouRequestMapping("/demo")
public class DemoController {


    @LagouAutowired
    private IDemoService demoService;


    /**
     * URL: /demo/query?name=lisi
     * @param request
     * @param response
     * @param name
     * @return
     */
    @LagouRequestMapping("/query")
    public String query(HttpServletRequest request, HttpServletResponse response,String name) {
        return demoService.get(name);
    }
}
