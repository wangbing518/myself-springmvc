package com.lagou.myself.mvcframework.servlet;

import com.lagou.myself.mvcframework.annotations.LagouAutowired;
import com.lagou.myself.mvcframework.annotations.LagouController;
import com.lagou.myself.mvcframework.annotations.LagouRequestMapping;
import com.lagou.myself.mvcframework.annotations.LagouService;
import com.lagou.myself.mvcframework.pojo.Handler;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private Properties properties = new Properties();

    // 缓存扫描到的类的全限定类名
    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件springmvc.properties
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);
        //2.扫描相关的类，扫描注解
        doScan(properties.getProperty("scanPackage"));
        //3.初始bean对象(实现IOC容器,基于注解)
        doInstance();
        //4.实现依赖注入
        doAutoWired();
        //5.构造一个handleMapping 处理器映射器，将配置好的url和Method建立映射关系
        initHandlerMapping();
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
        //处理请求: 根据url 找到对应的Method方法，进行调用
        Handler handler = getHandler(req);
        //获取url
        //反射调用,需要传入对象，传入参数，此处无法完成调用，没有把对象缓存起来，也没有参数！改造initHandlerMapping（）
       if (handler==null){
           resp.getWriter().write("404 not found");
           return;
       }
       //参数绑定
        //获取所有参数类型的数组，这个数组的长度就是我们最后要传入的args数组的长度
        Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();
        //根据上述数组长度重新见一个新的参数数组(新的数组要传入反射调用的)
        Object[] paraValues=new Object[parameterTypes.length];
        //以下就是为了向参数数组中簺值，而且还得保证参数的顺序和方法中的形参顺序一致
        Map<String, String[]> parameterMap = req.getParameterMap();
        //遍历request中的所有参数(填充除了request,response之外的参数)
        for (Map.Entry<String,String[]> param:parameterMap.entrySet() ){
            String value = StringUtils.join(param.getValue(), ",");
            //如果参数和方法中的参数匹配上了，填充数据
            if (!handler.getParamIndexMapping().containsKey(param.getKey())){
                continue;
            }
            //方法形参确实有该参数，找到他的索引位置，对应的把参数值放入paraValues
            Integer index = handler.getParamIndexMapping().get(param.getKey());
            paraValues[index]=value;//把前台传递过来的参数值填充到对应的位置去
        }
        Integer requestIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName());
        paraValues[requestIndex]=req;
        Integer reponseIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName());
        paraValues[reponseIndex]=resp;
        //最终调用handler的method属性
        try {
            handler.getMethod().invoke(handler.getController(),paraValues);
        }catch (IllegalAccessException e){
            e.printStackTrace();
        }catch (InvocationTargetException e){
            e.printStackTrace();
        }
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String requestURI = req.getRequestURI();
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(requestURI);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描
     *
     * @param scanPackage
     */
    private void doScan(String scanPackage) {
        String scanPackagePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        File pack = new File(scanPackage);
        File[] files = pack.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {//判断是否是目录 scanpackage
                doScan(scanPackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * IOC容器
     * 基于classNames缓存的类的全限定类名，以及反射技术，完成对象创建和管理
     */
    private void doInstance() {
        if (classNames.size() == 0) {
            return;
        }
        try {
            for (int i = 0; i <= classNames.size(); i++) {
                String className = classNames.get(i);//com.lagou.demo.controller.DemoController
                //反射
                Class<?> aClass = Class.forName(className);
                //区分controller，区分service
                if (aClass.isAnnotationPresent(LagouController.class)) {// A.isAnnotationPresent(B.class)；意思就是：注释B是否在此A上。如果在则返回true；不在则返回false。
                    // controller的id此处不做过多处理，不取value了，就拿类的⾸字⺟⼩写作为id，保存到ioc中
                    String simpleName = aClass.getSimpleName();
                    String lowerFirstSimpleName = lowerFirst(simpleName);
                    Object o = aClass.newInstance();//实例化这个对象
                    ioc.put(lowerFirstSimpleName, o);
                } else if (aClass.isAnnotationPresent(LagouService.class)) {
                    LagouService annotation = aClass.getAnnotation(LagouService.class);
                    //获取注解value值
                    String beanName = annotation.value();
                    //如果指定了ID 就以指定的为准
                    if (!"".endsWith(beanName.trim())) {
                        ioc.put(beanName, aClass.newInstance());
                    } else {
                        beanName = lowerFirst(aClass.getSimpleName());
                        ioc.put(beanName, aClass.newInstance());
                    }
                    // service层往往是有接⼝的，⾯向接⼝开发，此时再以接⼝名为id，放⼊⼀份对象到ioc中，便于后期根据接⼝类型注⼊
                    Class<?>[] interfaces = aClass.getInterfaces();
                    for (int j = 0; j < interfaces.length; j++) {
                        Class<?> anInterface = interfaces[j];
                        // 以接⼝的全限定类名作为id放⼊
                        String interfaceName = anInterface.getName();
                        ioc.put(interfaceName, aClass.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    public String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        if ('A' <= chars[0] || 'Z' >= chars[0]) {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    //实现依赖注入
    private void doAutoWired() {
        if (ioc.isEmpty()) {
            return;
        }
        //遍历ioc所有对象，查看对象中的字段，是否有@LagouAutowired注解，如果有则需要维护依赖注入关系
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //获取bean对象中的字段信息  getDeclaredFields()获得某个类的所有声明的字段，即包括public、private和proteced，但是不包括父类的申明字段
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();

            for (int i = 0; i < declaredFields.length; i++) {
                Field declaredField = declaredFields[i];
                if (!declaredField.isAnnotationPresent(LagouAutowired.class)) {// A.isAnnotationPresent(B.class)；意思就是：注释B是否在此A上。如果在则返回true；不在则返回false。
                    continue;
                }
                LagouAutowired annotation = declaredField.getAnnotation(LagouAutowired.class);
                // 需要注⼊的bean的id
                String beanName = annotation.value();
                if ("".equals(beanName)) {
                    beanName = declaredField.getType().getName();
                }
                // 开启赋值
                declaredField.setAccessible(true);

                try {
                    declaredField.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 构造一个HandlerMapping处理器映射器
     * 最关键的环节
     * 目的：将url和method建⽴关联
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取ioc中当前遍历的对象的class类型
            Class<?> aClass = entry.getValue().getClass();
            if (!aClass.isAnnotationPresent(LagouController.class)) {
                continue;
            }
            String baseUrl = "";
            if (aClass.isAssignableFrom(LagouRequestMapping.class)) {
                LagouRequestMapping annotation = aClass.getAnnotation(LagouRequestMapping.class);
                baseUrl = annotation.value();
            }
            Method[] methods = aClass.getMethods();
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];
                //没有标识LagouRequestMapping，就不处理
                if (!method.isAnnotationPresent(LagouRequestMapping.class)) {
                    continue;
                }
                LagouRequestMapping annotation = method.getAnnotation(LagouRequestMapping.class);
                String methodUrl = annotation.value();
                String url = baseUrl + methodUrl;
                Handler handler = new Handler(entry.getValue(), method, Pattern.compile(url));
                Parameter[] parameters = method.getParameters();
                for (int k = 0; k < parameters.length; k++) {
                    Parameter parameter = parameters[k];
                    //TODO 比对区别
                    if (parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class) {
                        handler.getParamIndexMapping().put(parameter.getType().getSimpleName(), k);
                    } else {
                        handler.getParamIndexMapping().put(parameter.getName(), k);
                    }
                }
                handlerMapping.add(handler);
            }
        }
    }


}