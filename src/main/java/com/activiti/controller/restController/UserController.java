package com.activiti.controller.restController;

import com.activiti.common.aop.ApiAnnotation;
import com.activiti.common.utils.CommonUtil;
import com.activiti.common.utils.ConstantsUtils;
import com.activiti.mapper.UserMapper;
import com.activiti.pojo.schedule.ScheduleDto;
import com.activiti.pojo.user.JudgementLs;
import com.activiti.pojo.user.StudentWorkInfo;
import com.activiti.pojo.user.User;
import com.activiti.pojo.user.UserRole;
import com.activiti.service.CommonService;
import com.activiti.service.JudgementService;
import com.activiti.service.ScheduleService;
import com.activiti.service.UserService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by 12490 on 2017/8/1.
 */
@RequestMapping("/api/user")
@RestController
public class UserController {
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private CommonUtil commonUtil;
    @Autowired
    private UserService userService;
    @Autowired
    private JudgementService judgementService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CommonService commonService;

    /*
     *  根据Email获取用户信息
     */

    @RequestMapping("/getUserInfo")
    @ResponseBody
    @ApiAnnotation
    public Object getUserInfo(@RequestParam(value = "email", required = true) String email) throws Exception {
        return userService.findUserInfo(email);
    }

    /**
     * 提交作业
     *
     * @param courseCode
     * @param workDetail
     * @param request
     * @return
     */
    @RequestMapping("/commitWork")
    @ResponseBody
    @ApiAnnotation
    public Object commitWork(@RequestParam(name = "courseCode") String courseCode,
                             @RequestParam(name = "workDetail") String workDetail, HttpServletRequest request) throws Exception {
        String email = request.getSession().getAttribute(ConstantsUtils.sessionEmail).toString();
        Date deadline = scheduleService.selectScheduleTime(courseCode).getJudgeStartTime();
        if (commonUtil.compareDate(new Date(), deadline))
            throw new Exception("提交作业截至时间:" + CommonUtil.dateToString(deadline));
        StudentWorkInfo studentWorkInfo = new StudentWorkInfo(courseCode, email, workDetail, new Date());
        User user = new User(commonUtil.getRandomUserName(), email, courseCode);
        userService.insertUser(user);
        studentWorkInfo.setLastCommitTime(new Date());
        userService.insertUserWork(studentWorkInfo);
        return studentWorkInfo;
    }

    /**
     * 查询学生提交的作业
     *
     * @param courseCode
     * @return
     */
    @RequestMapping("/selectStudentWorkInfo")
    @ResponseBody
    @ApiAnnotation
    public Object selectStudentWorkInfo(
            @RequestParam(value = "courseCode", required = false) String courseCode,
            @RequestParam(value = "page", required = false, defaultValue = "1") long page,
            @RequestParam(value = "limit", required = false, defaultValue = "1") int limit,
            @RequestParam(value = "count", required = false) boolean count,
            HttpServletRequest request) {
        String email = request.getSession().getAttribute(ConstantsUtils.sessionEmail).toString();
        if (null != courseCode && !"".equals(courseCode)) {
            StudentWorkInfo studentWorkInfo = new StudentWorkInfo();
            studentWorkInfo.setCourseCode(courseCode);
            studentWorkInfo.setEmailAddress(email);
            return userService.selectStudentWorkInfo(studentWorkInfo);
        } else if (count) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("count", userMapper.countStudentWorkInfo(email));
            return jsonObject;
        } else {
            return userMapper.selectStudentWorkInfoPage(email, (page - 1) * limit, limit);
        }
    }

    /**
     * 查询需要评论的作业
     *
     * @param courseCode
     * @return
     */
    @RequestMapping("/selectWorkListToJudge")
    @ResponseBody
    @ApiAnnotation
    public Object selectWorkListToJudge(@RequestParam(value = "courseCode") String courseCode, HttpServletRequest request) throws UnsupportedEncodingException {
        String email = request.getSession().getAttribute(ConstantsUtils.sessionEmail).toString();
        String tableName = commonUtil.generateTableName(courseCode);
        StudentWorkInfo studentWorkInfo = new StudentWorkInfo();
        studentWorkInfo.setCourseCode(courseCode);
        ScheduleDto scheduleDto = scheduleService.selectScheduleTime(courseCode);
        String githubAddress = scheduleDto.getGithubAddress();
        String content = new String(Base64.decodeBase64(commonService.getQAFromGitHub(githubAddress).get("content").toString().getBytes()), "utf-8");
        JSONObject response = JSONObject.parseObject(content);
        int studentId = judgementService.selectChaosId(email, tableName);
        int countWork = judgementService.countAllWorks(tableName);
        int judgeTimes = scheduleDto.getJudgeTimes();
        List<Integer> initList = new ArrayList<>();
        for (int i = 1; i <= judgeTimes; i++) {
            int id = studentId + i;
            int result = id > countWork ? id - countWork : id;
            initList.add(result);
        }
        List<StudentWorkInfo> workInfoList = new ArrayList<>();
        initList.forEach(index -> {
            workInfoList.add(userService.selectStudentWorkInfo(new StudentWorkInfo(courseCode,
                    judgementService.selectStudentWorkInfoChaosById(String.valueOf(index), tableName))));
        });
        response.put("workList", workInfoList);
        return response;
    }

    /**
     * 提交互评作业
     *
     * @param judge
     * @param courseCode
     * @param request
     * @return
     */
    @RequestMapping("/commitJudgementInfo")
    @ResponseBody
    @ApiAnnotation
    public Object commitJudgementInfo(@RequestParam(value = "judge") String judge,
                                      @RequestParam(value = "courseCode") String courseCode,
                                      HttpServletRequest request) {
        String email = request.getSession().getAttribute(ConstantsUtils.sessionEmail).toString();
        ScheduleDto scheduleDto = scheduleService.selectScheduleTime(courseCode);
        int judgeLimitTimes = scheduleDto.getJudgeTimes();
        JSONObject judgeList = JSON.parseObject(judge);
        List<JudgementLs> judgementLsList = new ArrayList<>();
        judgeList.keySet().forEach(key -> {
            JSONObject jsonObject = (JSONObject) judgeList.get(key);
            judgementLsList.add(new JudgementLs(courseCode,
                    email, key, Double.valueOf(jsonObject.get("grade").toString())));
            List<JudgementLs> judgementLsList1 = judgementService.selectJudgementLs(new JudgementLs(courseCode, key));  //查询和这个人相关的互评流水
            if (judgementLsList1 != null && judgementLsList1.size() + 1 == judgeLimitTimes) {  //这个人被别人评价次数够了，计算他的最终分数
                double finalGrade = commonUtil.getMiddleNum(Double.valueOf(jsonObject.get("grade").toString()), judgementLsList1);
                judgementService.updateStuGrade(new StudentWorkInfo(courseCode, key, finalGrade));  //更新成绩
            }
        });
        judgementService.insertJudgementLs(judgementLsList);   //插入互评流水
        judgementService.updateStuJudgeTime(new StudentWorkInfo(courseCode, email, new Date()));  //更新这名用户参与互评的时间
        return "提交评论结果成功";
    }

    /**
     * 删除管理员用户
     *
     * @param email
     * @return
     */
    @ResponseBody
    @RequestMapping("/deleteUserRole")
    @ApiAnnotation
    public Object deleteUserRole(@RequestParam(value = "email", required = true) String email) {
        return userService.deleteUserRole(email);
    }

    /**
     * 添加管理员用户
     *
     * @param email
     * @param id
     * @param remarks
     * @return
     */
    @ResponseBody
    @RequestMapping("/addUserRole")
    @ApiAnnotation
    public Object addUserRole(@RequestParam(value = "email", required = true) String email,
                              @RequestParam(value = "id", required = true) int id,
                              @RequestParam(value = "remarks", required = true) String remarks) throws Exception {
        if (!commonUtil.emailFormat(email))
            throw new Exception("邮箱格式不正确");
        return userService.insertUserRole(new UserRole(id, email, remarks));
    }
}

