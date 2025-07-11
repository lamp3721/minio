package org.example.miniodemo.view;// File: com.example.config.SpaRoutingConfiguration.java
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 解决 SPA (Single Page Application) 刷新页面404的问题。
 * 此控制器匹配优先级较低，仅当其他 @RestController 或 @Controller
 * 无法处理请求时，它才会生效。
 */
@Controller
public class SpaRoutingController {

    /**
     * 捕获并转发后台管理应用（/admin/**）的所有前端路由。
     * @return 内部转发到后台应用的入口 index.html
     */
    @RequestMapping(value = "/view/{path:[^.]*}")
    public String forwardAdmin() {
        return "forward:/view/index.html";
    }

    /**
     * 捕获所有没有被其他控制器处理的路径（全局兜底）。
     */
    @RequestMapping(value = "/{path:^(?!.*\\.).*$}")
    public String forwardAll() {
        return "forward:/view/index.html";
    }

}