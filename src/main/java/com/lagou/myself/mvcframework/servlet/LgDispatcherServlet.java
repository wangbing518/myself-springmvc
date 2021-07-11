package com.lagou.myself.mvcframework.servlet;

import com.lagou.myself.mvcframework.annotations.LagouController;
import com.lagou.myself.mvcframework.annotations.LagouService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * 其实是作为前端控制器
 * 作用： 1.加载指定的配置文件 springmvc.properties
 * 2.进行包扫描和扫描注解
 * 3.IOC容器进行相应的bean的初始化和相应的依赖注入
 * 4.springMVC相关组件的初始化
 *
 * @ClassName LgDispatcherServlet
 * @Description
 * @Author wb
 * @Date 2021/7/8 0008 下午 3:36
 */
public class LgDispatcherServlet extends HttpServlet {

    private Properties properties=new Properties();

    // 缓存扫描到的类的全限定类名
    private List<String> classNames=new ArrayList<>();

    private Map<String, Object> ioc=new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件springmvc.properties
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);
        //2.扫描相关的类，扫描注解
        doScan(properties.getProperty("scanPackage"));
        //3.初始bean对象(实现IOC容器,基于注解)

        //4.实现依赖注入

        //5.构造一个handleMapping 处理器映射器，将配置好的url和Method建立映射关系

        //等待请求进入，处理请求

    }

    /**
     * 接受请求
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    /**
     * 处理请求
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    /**
     * 加载配置文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation){
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描
     * @param scanPackage
     */
    private void doScan(String scanPackage){
        String scanPackagePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        File pack = new File(scanPackage);
        File[] files = pack.listFiles();

        for (File file:files){
            if (file.isDirectory()){//判断是否是目录 scanpackage
                doScan(scanPackage+"."+file.getName());
            }else if(file.getName().endsWith(".class")){
                String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * IOC容器
     * 基于classNames缓存的类的全限定类名，以及反射技术，完成对象创建和管理
     */
    private void doInstance(){
        if (classNames.size()==0){
            return;
        }
        try {
            for (int i=0;i<=classNames.size();i++){
                String className = classNames.get(i);//com.lagou.demo.controller.DemoController
                //反射
                Class<?> aClass = Class.forName(className);
                //区分controller，区分service
                if (aClass.isAnnotationPresent(LagouController.class)){// A.isAnnotationPresent(B.class)；意思就是：注释B是否在此A上。如果在则返回true；不在则返回false。
                    // controller的id此处不做过多处理，不取value了，就拿类的⾸字⺟⼩写作为id，保存到ioc中
                    String simpleName = aClass.getSimpleName();
                    String lowerFirstSimpleName = lowerFirst(simpleName);
                    Object o = aClass.newInstance();//实例化这个对象
                    ioc.put(lowerFirstSimpleName,o);
                }else if (aClass.isAnnotationPresent(LagouService.class)){
                    LagouService annotation = aClass.getAnnotation(LagouService.class);
                    //获取注解value值
                    String beanName = annotation.value();
                    //如果指定了ID 就以指定的为准
                    if (!"".endsWith(beanName.trim())){
                        ioc.put(beanName,aClass.newInstance());
                    }else {
                        beanName=lowerFirst(aClass.getSimpleName());
                        ioc.put(beanName,aClass.newInstance());
                    }
                    // service层往往是有接⼝的，⾯向接⼝开发，此时再以接⼝名为id，放⼊⼀份对象到ioc中，便于后期根据接⼝类型注⼊
                    Class<?>[] interfaces = aClass.getInterfaces();
                    for (int j=0;j<interfaces.length;j++){
                        Class<?> anInterface = interfaces[j];
                        // 以接⼝的全限定类名作为id放⼊
                        String interfaceName = anInterface.getName();
                        ioc.put(interfaceName,aClass.newInstance());
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 首字母小写
     * @param str
     * @return
     */
    public String lowerFirst(String str){
        char[] chars = str.toCharArray();
        if ('A'<=chars[0]||'Z'>=chars[0]){
            chars[0] +=32;
        }
        return String.valueOf(chars);
    }
}