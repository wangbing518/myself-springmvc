package com.lagou.myself.demo.service.impl;


import com.lagou.myself.demo.service.IDemoService;
import com.lagou.myself.mvcframework.annotations.LagouService;

@LagouService("demoService")
public class DemoServiceImpl implements IDemoService {
    @Override
    public String get(String name) {
        System.out.println("service 实现类中的name参数：" + name) ;
        return name;
    }
}
