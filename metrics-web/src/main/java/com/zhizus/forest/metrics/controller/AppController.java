package com.zhizus.forest.metrics.controller;

import com.alibaba.fastjson.JSON;
import com.zhizus.forest.metrics.JsonResult;
import com.zhizus.forest.metrics.bean.App;
import com.zhizus.forest.metrics.bean.User;
import com.zhizus.forest.metrics.service.AppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * Created by Dempe on 2017/1/5.
 */
@Controller
@RequestMapping("/app")
public class AppController {

    @Autowired
    private AppService appService;

    @RequestMapping("/save.do")
    @ResponseBody
    public String save(@RequestParam String serviceName, HttpSession session) {
        User user = (User) session.getAttribute("user");
        App app = new App();
        app.setServiceName(serviceName);
        app.setCreateBy(user.getName());
        String appKey = UUID.randomUUID().toString().replace("-", "");
        app.setAppKey(appKey);
        appService.save(app);
        return JSON.toJSONString(app);
    }

    @RequestMapping("/list.do")
    @ResponseBody
    public String list(HttpSession session) {
        return JSON.toJSONString(appService.find());
    }

    @RequestMapping("/index.do")
    public String index() {
        return "forest/app";
    }

    @RequestMapping("/deleteByServiceName.do")
    @ResponseBody
    public String deleteByServiceName(@RequestParam String serviceName) {
        appService.deleteByServiceName(serviceName);
        return JsonResult.successResult().toJSONString();

    }

}
